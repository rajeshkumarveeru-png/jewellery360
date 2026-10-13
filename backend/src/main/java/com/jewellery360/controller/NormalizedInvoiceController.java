package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.CompanyPropertyService;
import com.jewellery360.service.MarketGoldRateService;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class NormalizedInvoiceController {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm:ss a", Locale.ENGLISH);

    private final SaleRepository sales;
    private final SaleReturnRepository returns;
    private final SaleItemRepository items;
    private final PermissionService permissions;
        private final CompanyPropertyService companyProperties;
    private final CompanyRepository companies;

    /**
     * Invoice PDF. Any signed-in user may open invoices of their OWN company (and branch, for branch-bound users);
     * no separate BILLING-module permission is required, so Reports/Payments users can print what they can already see.
     * The company/branch ownership check in {@link #check} is kept on purpose: it is the tenant-isolation boundary.
     */
    @GetMapping("/sales/{id}/pdf")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<byte[]> salePdf(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                          @RequestParam(required = false) String template, @RequestParam(required = false) String format) {
        Sale s = sales.findById(id).orElseThrow(() -> notFound("Sale not found"));
        check(me, s.getCompany().getId(), s.getBranch().getId());

        List<SaleItem> saleItems = items.findBySaleIdOrderById(id);
        CompanyPropertyService.BusinessProfile profile = companyProperties.getBusinessProfile(s.getCompany());
        // the company's saved choices are the default; ?template= and ?format= let a cashier print another design or paper size
        String paper = format == null || format.isBlank() ? profile.printFormat() : format.trim().toUpperCase(Locale.ROOT);
        String design = InvoiceTemplates.normalize(template == null || template.isBlank() ? profile.invoiceTemplate() : template);
        byte[] pdf;
        if ("50MM".equals(paper) || "80MM".equals(paper)) {
            pdf = ThermalReceipt.render(s, saleItems, profile, s.getCreatedAt().atZone(INDIA), "80MM".equals(paper));
        } else {
            pdf = InvoiceTemplates.render(design, toTemplateData(s, saleItems, s.getCreatedAt().atZone(INDIA), profile));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + safeFileName(s.getInvoiceNo()) + ".pdf")
                .body(pdf);
    }

    /** Sample invoice in any design, for the picker in Settings. Needs no sale; uses the signed-in company's name and contact details. */
    @GetMapping("/preview")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(defaultValue = "CLASSIC") String template,
                                          @RequestHeader(value = "X-Company-Id", required = false) Long companyId) {
        Long cid = "APP_ADMIN".equals(me.getRole()) ? companyId : me.getCompanyId();
        CompanyPropertyService.BusinessProfile profile = cid == null ? null
                : companies.findById(cid).map(companyProperties::getBusinessProfile).orElse(null);
        InvoiceTemplates.Data data = profile == null
                ? InvoiceTemplates.sample("", "", "", "")
                : InvoiceTemplates.sample(profile.companyName(), profile.phone(), profile.email(), profile.gstin());
        byte[] pdf = InvoiceTemplates.render(InvoiceTemplates.normalize(template), data);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=invoice-design-preview.pdf").body(pdf);
    }

    /** Entities -> plain data for the A4 designs (all designs share this one mapping, so they always show the same figures). */
    private static InvoiceTemplates.Data toTemplateData(Sale s, List<SaleItem> rows, ZonedDateTime at,
                                                        CompanyPropertyService.BusinessProfile profile) {
        Branch branch = s.getBranch();
        Company company = s.getCompany();
        String brand = !profile.companyName().isBlank() ? profile.companyName() : (company == null ? "" : nullToEmpty(company.getName()));
        String gstin = firstNonBlank(branch == null ? "" : branch.getGstin(), firstNonBlank(profile.gstin(), company == null ? "" : company.getGstin()));
        String phone = firstNonBlank(branch == null ? "" : branch.getPhone(), firstNonBlank(profile.phone(), company == null ? "" : company.getPhone()));
        String email = firstNonBlank(branch == null ? "" : branch.getEmail(), firstNonBlank(profile.email(), company == null ? "" : company.getEmail()));
        List<InvoiceTemplates.Line> lines = new ArrayList<>();
        BigDecimal gold = BigDecimal.ZERO, wastage = BigDecimal.ZERO, making = BigDecimal.ZERO, stone = BigDecimal.ZERO, other = BigDecimal.ZERO;
        for (SaleItem si : rows) {
            JewelleryItem ji = si.getJewelleryItem();
            String name = ji != null && ji.getProduct() != null ? nullToEmpty(ji.getProduct().getName()) : "Jewellery";
            String design = ji != null && ji.getProduct() != null && ji.getProduct().getDesign() != null ? nullToEmpty(ji.getProduct().getDesign().getName()) : "";
            String barcode = ji != null ? nullToEmpty(ji.getBarcode()) : "";
            BigDecimal net = si.getNetWeight() == null ? BigDecimal.ZERO : si.getNetWeight();
            BigDecimal rate = si.getRate() == null ? BigDecimal.ZERO : si.getRate();
            gold = gold.add(net.multiply(rate));
            wastage = wastage.add(nzBig(si.getWastageValue()));
            making = making.add(nzBig(si.getMakingCharge()));
            stone = stone.add(nzBig(si.getStoneCharge()));
            other = other.add(nzBig(si.getOtherCharge()));
            lines.add(new InvoiceTemplates.Line(name, design, nullToEmpty(si.getTagNo()), barcode, nullToEmpty(si.getPurity()), nzBig(si.getGrossWeight()),
                    nzBig(si.getStoneWeight()), net, rate, nzBig(si.getWastageValue()), nzBig(si.getMakingCharge()), nzBig(si.getStoneCharge()),
                    nzBig(si.getOtherCharge()), nzBig(si.getTotal())));
        }
        // The rates printed are the ones this bill was made with - a reprint never shows a later rate.
        Map<String, BigDecimal> billRates = new LinkedHashMap<>();
        BigDecimal mainRate = BigDecimal.ZERO, mainValue = BigDecimal.ZERO;
        for (SaleItem si : rows) {
            if (nzBig(si.getRate()).signum() == 0) continue;
            billRates.putIfAbsent(nullToEmpty(si.getPurity()), si.getRate());
            if (nzBig(si.getMetalValue()).compareTo(mainValue) > 0) { mainValue = si.getMetalValue(); mainRate = si.getRate(); }
        }
        List<InvoiceTemplates.Rate> rates = new ArrayList<>();
        billRates.forEach((k, v) -> rates.add(new InvoiceTemplates.Rate(k, v)));
        String mode = s.getTaxMode() == null ? "GST" : s.getTaxMode();
        String dateOnly = s.getSaleDate() == null ? "-" : s.getSaleDate().format(DATE);
        return new InvoiceTemplates.Data(brand, branch == null ? "Main Branch" : nullToEmpty(branch.getName()), branch == null ? "" : nullToEmpty(branch.getAddress()),
                gstin, phone, email, branch == null ? "" : nullToEmpty(branch.getWebsite()),
                branch != null && branch.getInvoiceTitle() != null && !branch.getInvoiceTitle().isBlank() ? branch.getInvoiceTitle() : "Tax Invoice",
                branch != null && branch.getInvoiceSubtitle() != null && !branch.getInvoiceSubtitle().isBlank() ? branch.getInvoiceSubtitle() : "Transparent jewellery price breakup",
                nullToEmpty(s.getInvoiceNo()), dateOnly, at.format(TIME),
                s.getCustomer() == null ? "" : nullToEmpty(s.getCustomer().getName()), s.getCustomer() == null ? "" : nullToEmpty(s.getCustomer().getPhone()),
                s.getCustomer() == null ? "" : nullToEmpty(s.getCustomer().getGstin()), s.getCustomer() == null ? "" : nullToEmpty(s.getCustomer().getAddress()),
                lines, gold, wastage, making, stone, other, nzBig(s.getSubtotal()), mode, nzBig(s.getTaxRate()), nzBig(s.getCgstRate()), nzBig(s.getSgstRate()),
                nzBig(s.getGst()), nzBig(s.getDiscount()), nzBig(s.getTotal()), paymentLine(s), mainRate, rates,
                branch == null ? "" : nullToEmpty(branch.getInvoiceFooter()), branch == null ? "" : nullToEmpty(branch.getInvoiceTerms()),
                dateOnly, at.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH)));
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** "Paid Rs. 57,607" or "Due Rs. 27,607 (paid Rs. 30,000)". */
    static String paymentLine(Sale s) {
        if (Sale.CANCELLED.equals(s.getStatus())) return "CANCELLED";
        BigDecimal due = s.balance();
        if (due.signum() <= 0) return "Paid Rs. " + money(s.getPaidAmount());
        return "Due Rs. " + money(due) + (s.getPaidAmount().signum() > 0 ? " (paid Rs. " + money(s.getPaidAmount()) + ")" : "");
    }
    private static BigDecimal nzBig(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }
    private static int digits(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; } }

    @GetMapping("/returns/{id}/pdf")
    public ResponseEntity<byte[]> returnPdf(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        SaleReturn r = returns.findById(id).orElseThrow(() -> notFound("Return not found"));
        check(me, r.getCompany().getId(), r.getBranch().getId());
        List<String> lines = List.of(
                companyProperties.getBusinessProfile(r.getCompany()).companyName().toUpperCase(Locale.ROOT), "SALES RETURN", "Return No: " + r.getReturnNo(),
                "Date: " + r.getReturnDate(), "Original Invoice: " + r.getSale().getInvoiceNo(),
                "Customer: " + r.getSale().getCustomer().getName(), "Amount: Rs. " + money(r.getAmount()),
                "Reason: " + Objects.toString(r.getReason(), "")
        );
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + safeFileName(r.getReturnNo()) + ".pdf")
                .body(SimplePdf.text(lines));
    }

    private void check(AuthenticatedUser me, Long companyId, Long branchId) {
        if (!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(companyId, me.getCompanyId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoice access denied");
        if (!com.jewellery360.service.PermissionService.isAdmin(me.getRole()) && me.getBranchId() != null && !Objects.equals(branchId, me.getBranchId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch access denied");
    }

    private ResponseStatusException notFound(String s) { return new ResponseStatusException(HttpStatus.NOT_FOUND, s); }
    private static String safeFileName(String s) { return (s == null || s.isBlank() ? "invoice" : s).replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String money(BigDecimal n) { return NumberFormat.getNumberInstance(new Locale("en", "IN")).format(n == null ? BigDecimal.ZERO : n.setScale(2, java.math.RoundingMode.HALF_UP)); }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }

    /**
     * 50 mm / 80 mm thermal receipt. Plain monospaced layout so columns line up on any thermal printer; the page height
     * grows with the number of lines. Uses the same sale snapshot (subtotal, tax mode and rates, discount, total) as the A4 invoice,
     * so the GST / CGST / SGST lines always match the company's "Include GST" setting at the time of billing.
     */
    static final class ThermalReceipt {
        private static final float MM = 72f / 25.4f;

        static byte[] render(Sale s, List<SaleItem> rows, CompanyPropertyService.BusinessProfile profile, ZonedDateTime generatedAt, boolean wide) {
            final int cols = wide ? 42 : 28;
            final float font = wide ? 7.2f : 6.6f;
            final float width = (wide ? 80 : 50) * MM;
            final float margin = wide ? 8f : 6f;
            final float leading = font + 3.2f;

            List<String[]> lines = new ArrayList<>();   // {text, "B" if bold}
            Branch branch = s.getBranch();
            Company company = s.getCompany();
            String brand = firstNonBlank(profile.companyName(), company == null ? "" : company.getName());
            String phone = firstNonBlank(branch != null ? branch.getPhone() : "", profile.phone());
            String gstin = firstNonBlank(branch != null ? branch.getGstin() : "", profile.gstin());
            String email = firstNonBlank(branch != null ? branch.getEmail() : "", profile.email());

            for (String part : wrap(brand.toUpperCase(Locale.ROOT), cols)) lines.add(new String[]{center(part, cols), "B"});
            if (branch != null && branch.getName() != null) lines.add(new String[]{center(cut(branch.getName(), cols), cols), ""});
            if (branch != null && branch.getAddress() != null && !branch.getAddress().isBlank()) {
                for (String part : wrap(branch.getAddress(), cols)) lines.add(new String[]{center(part, cols), ""});
            }
            if (!phone.isBlank()) lines.add(new String[]{center("Ph: " + phone, cols), ""});
            if (!email.isBlank()) lines.add(new String[]{center(cut(email, cols), cols), ""});
            if (!gstin.isBlank()) lines.add(new String[]{center("GSTIN: " + gstin, cols), ""});
            lines.add(new String[]{rule(cols), ""});
            String title = branch != null && branch.getInvoiceTitle() != null && !branch.getInvoiceTitle().isBlank() ? branch.getInvoiceTitle() : "TAX INVOICE";
            lines.add(new String[]{center(cut(title, cols), cols), "B"});
            lines.add(new String[]{"Inv : " + cut(s.getInvoiceNo(), cols - 6), ""});
            lines.add(new String[]{"Date: " + (s.getSaleDate() == null ? "-" : s.getSaleDate().format(DATE)), ""});
            lines.add(new String[]{"Time: " + generatedAt.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)), ""});
            if (s.getCustomer() != null) {
                lines.add(new String[]{"Cust: " + cut(nullToDash(s.getCustomer().getName()), cols - 6), ""});
                if (s.getCustomer().getPhone() != null && !s.getCustomer().getPhone().isBlank()) lines.add(new String[]{"Ph  : " + s.getCustomer().getPhone(), ""});
            }
            
            lines.add(new String[]{rule(cols), ""});

            for (SaleItem si : rows) {
                String name = nullToEmpty(si.getDescription());
                lines.add(new String[]{cut(name, cols), "B"});
                lines.add(new String[]{cut("Tag " + nullToEmpty(si.getTagNo()) + " " + nullToEmpty(si.getPurity()), cols), ""});
                lines.add(new String[]{cut("N.Wt " + weightText(si.getNetWeight()) + "g x " + money(si.getRate()), cols), ""});
                lines.add(new String[]{pair("Making+Other", money(nzv(si.getMakingCharge()).add(nzv(si.getOtherCharge())).add(nzv(si.getStoneCharge()))), cols), ""});
                lines.add(new String[]{pair("Item total", money(si.getTotal()), cols), "B"});
            }
            if (rows.isEmpty()) lines.add(new String[]{"No sale items found.", ""});
            lines.add(new String[]{rule(cols), ""});

            lines.add(new String[]{pair("Subtotal", money(s.getSubtotal()), cols), ""});
            if (nzv(s.getDiscount()).signum() > 0) lines.add(new String[]{pair("Discount", "-" + money(s.getDiscount()), cols), ""});
            BigDecimal gst = nzv(s.getGst());
            if ("CGST_SGST".equalsIgnoreCase(s.getTaxMode())) {
                BigDecimal split = nzv(s.getCgstRate()).add(nzv(s.getSgstRate()));
                BigDecimal half = split.signum() == 0 ? BigDecimal.ZERO : gst.multiply(nzv(s.getCgstRate())).divide(split, 2, java.math.RoundingMode.HALF_UP);
                lines.add(new String[]{pair("CGST " + money(s.getCgstRate()) + "%", money(half), cols), ""});
                lines.add(new String[]{pair("SGST " + money(s.getSgstRate()) + "%", money(gst.subtract(half)), cols), ""});
            } else if (!"NONE".equalsIgnoreCase(s.getTaxMode()) && gst.signum() > 0) {
                lines.add(new String[]{pair("GST " + money(s.getTaxRate()) + "%", money(gst), cols), ""});
            }
            lines.add(new String[]{rule(cols), ""});
            lines.add(new String[]{pair("TOTAL Rs.", money(s.getTotal()), cols), "B"});
            if (nzv(s.getRoundOff()).signum() != 0) lines.add(new String[]{pair("Round off", money(s.getRoundOff()), cols), ""});
            lines.add(new String[]{cut(paymentLine(s), cols), ""});
            lines.add(new String[]{rule(cols), ""});
            String footer = branch != null && branch.getInvoiceFooter() != null && !branch.getInvoiceFooter().isBlank() ? branch.getInvoiceFooter() : "Thank you for choosing " + brand + "!";
            for (String part : wrap(footer, cols)) lines.add(new String[]{center(part, cols), ""});

            float height = margin * 2 + lines.size() * leading;
            StringBuilder content = new StringBuilder();
            float y = height - margin - font;
            for (String[] line : lines) {
                content.append(String.format(Locale.US, "BT /F%d %.1f Tf 1 0 0 1 %.1f %.1f Tm (%s) Tj ET\n", "B".equals(line[1]) ? 2 : 1, font, margin, y, escape(line[0])));
                y -= leading;
            }
            return build(width, height, content.toString());
        }

        private static byte[] build(float width, float height, String content) {
            List<String> objs = new ArrayList<>();
            objs.add("<< /Type /Catalog /Pages 2 0 R >>");
            objs.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
            objs.add(String.format(Locale.US, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 4 0 R >>", width, height));
            objs.add("<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + content + "endstream");
            objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>");
            objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier-Bold >>");
            StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objs.size(); i++) {
                offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
                pdf.append(i + 1).append(" 0 obj\n").append(objs.get(i)).append("\nendobj\n");
            }
            int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
            pdf.append("xref\n0 ").append(objs.size() + 1).append("\n0000000000 65535 f \n");
            for (int off : offsets) pdf.append(String.format(Locale.US, "%010d 00000 n \n", off));
            pdf.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        private static String escape(String s) {
            String latin = new String(String.valueOf(s).replace("\u20b9", "Rs.").getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
            return latin.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\r", " ").replace("\n", " ");
        }
        private static BigDecimal nzv(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }
        private static String nullToDash(String s) { return s == null || s.isBlank() ? "-" : s; }
        private static String nullToEmpty(String s) { return s == null ? "" : s; }
        private static String weightText(BigDecimal x) { return nzv(x).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString(); }
        private static String cut(String s, int n) { if (s == null) return ""; return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)) + "."; }
        private static String rule(int cols) { return "-".repeat(cols); }
        private static String center(String s, int cols) { int pad = Math.max(0, (cols - s.length()) / 2); return " ".repeat(pad) + s; }
        private static String pair(String left, String right, int cols) {
            int space = Math.max(1, cols - left.length() - right.length());
            return cut(left, cols - right.length() - 1) + " ".repeat(space) + right;
        }
        private static List<String> wrap(String text, int cols) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String word : String.valueOf(text).trim().split("\\s+")) {
                if (cur.length() > 0 && cur.length() + 1 + word.length() > cols) { out.add(cur.toString()); cur.setLength(0); }
                if (cur.length() > 0) cur.append(' ');
                cur.append(word.length() > cols ? word.substring(0, cols) : word);
            }
            if (cur.length() > 0) out.add(cur.toString());
            return out;
        }
        private static String money(BigDecimal n) { return NumberFormat.getNumberInstance(new Locale("en", "IN")).format(n == null ? BigDecimal.ZERO : n.setScale(2, java.math.RoundingMode.HALF_UP)); }
    }

    static class SimplePdf {
        static byte[] text(List<String> lines){StringBuilder c=new StringBuilder("BT /F1 10 Tf 45 790 Td\n");for(String l:lines)c.append("(").append(esc(l)).append(") Tj 0 -16 Td\n");c.append("ET");String[] o={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>","<< /Length "+c.length()+" >>\nstream\n"+c+"\nendstream","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};StringBuilder p=new StringBuilder("%PDF-1.4\n");List<Integer> off=new ArrayList<>();for(int i=0;i<o.length;i++){off.add(p.length());p.append(i+1).append(" 0 obj\n").append(o[i]).append("\nendobj\n");}int x=p.length();p.append("xref\n0 ").append(o.length+1).append("\n0000000000 65535 f \n");for(int n:off)p.append(String.format("%010d 00000 n \n",n));p.append("trailer\n<< /Size ").append(o.length+1).append(" /Root 1 0 R >>\nstartxref\n").append(x).append("\n%%EOF");return p.toString().getBytes(StandardCharsets.ISO_8859_1);}
        static String esc(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)").replace("\r"," ").replace("\n"," ").replace("₹","Rs.");}
    }
}
