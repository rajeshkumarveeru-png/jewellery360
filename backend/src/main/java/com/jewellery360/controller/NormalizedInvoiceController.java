package com.jewellery360.controller;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
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
    private final MarketGoldRateService marketGoldRates;

    @GetMapping("/sales/{id}/pdf")
    public ResponseEntity<byte[]> salePdf(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        permissions.requireModule(me, "BILLING");
        Sale s = sales.findById(id).orElseThrow(() -> notFound("Sale not found"));
        check(me, s.getCompany().getId(), s.getBranch().getId());

        List<SaleItem> saleItems = items.findBySaleId(id);
        Map<String, Object> market = marketGoldRates.current();
        byte[] pdf = InvoicePdf.render(s, saleItems, market, ZonedDateTime.now(INDIA));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + safeFileName(s.getInvoiceNo()) + ".pdf")
                .body(pdf);
    }

    @GetMapping("/returns/{id}/pdf")
    public ResponseEntity<byte[]> returnPdf(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        permissions.requireModule(me, "BILLING");
        SaleReturn r = returns.findById(id).orElseThrow(() -> notFound("Return not found"));
        check(me, r.getCompany().getId(), r.getBranch().getId());
        List<String> lines = List.of(
                "JEWELLERY360", "SALES RETURN", "Return No: " + r.getReturnNo(),
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
        if (!"APP_ADMIN".equals(me.getRole()) && me.getBranchId() != null && !Objects.equals(branchId, me.getBranchId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch access denied");
    }

    private ResponseStatusException notFound(String s) { return new ResponseStatusException(HttpStatus.NOT_FOUND, s); }
    private static String safeFileName(String s) { return (s == null || s.isBlank() ? "invoice" : s).replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String money(BigDecimal n) { return NumberFormat.getNumberInstance(new Locale("en", "IN")).format(n == null ? BigDecimal.ZERO : n.setScale(2, java.math.RoundingMode.HALF_UP)); }

    static final class InvoicePdf {
        private static final float W = 595f, H = 842f, M = 36f;
        private static final int ROWS_PER_PAGE = 6;

        static byte[] render(Sale sale, List<SaleItem> rows, Map<String, Object> market, ZonedDateTime generatedAt) {
            List<Page> pages = new ArrayList<>();
            int pageCount = Math.max(1, (int) Math.ceil(rows.size() / (double) ROWS_PER_PAGE));
            for (int pageNo = 0; pageNo < pageCount; pageNo++) {
                int from = pageNo * ROWS_PER_PAGE;
                int to = Math.min(rows.size(), from + ROWS_PER_PAGE);
                pages.add(page(sale, rows.subList(from, to), market, generatedAt, pageNo + 1, pageCount, pageNo == pageCount - 1));
            }
            return PdfWriter.write(pages);
        }

        private static Page page(Sale s, List<SaleItem> rows, Map<String, Object> market, ZonedDateTime generatedAt,
                                 int pageNo, int pageCount, boolean last) {
            Page p = new Page();
            float y = H - M;
            Branch branch = s.getBranch();
            Company company = s.getCompany();
            String branchName = branch == null ? "Main Branch" : nullToDash(branch.getName());
            String branchGstin = branch != null && branch.getGstin() != null && !branch.getGstin().isBlank()
                    ? branch.getGstin() : (company == null ? "" : company.getGstin());
            String branchPhone = branch != null && branch.getPhone() != null && !branch.getPhone().isBlank()
                    ? branch.getPhone() : (company == null ? "" : company.getPhone());
            String branchEmail = branch != null && branch.getEmail() != null && !branch.getEmail().isBlank()
                    ? branch.getEmail() : (company == null ? "" : company.getEmail());
            String branchWebsite = branch == null ? "" : nullToEmpty(branch.getWebsite());
            String branchAddress = branch == null ? "" : nullToEmpty(branch.getAddress());
            String invoiceTitle = branch != null && branch.getInvoiceTitle() != null && !branch.getInvoiceTitle().isBlank()
                    ? branch.getInvoiceTitle() : "TAX INVOICE";
            String invoiceSubtitle = branch != null && branch.getInvoiceSubtitle() != null && !branch.getInvoiceSubtitle().isBlank()
                    ? branch.getInvoiceSubtitle() : "Transparent jewellery price breakup";

            // Light, neutral jewellery-invoice header. Avoid a black background while keeping strong visual hierarchy.
            p.roundRect(M, y - 104, W - 2 * M, 104, 0.985f, 0.965f, 0.915f, 0.78f, 0.62f, 0.28f);
            p.text(M + 16, y - 25, 21, true, "JEWELLERY360", 0.55f, 0.35f, 0.10f);
            p.text(M + 16, y - 42, 8.5f, true, cut(branchName, 34), 0.22f, 0.19f, 0.15f);
            p.text(M + 16, y - 56, 7.5f, false, cut(branchAddress, 58), 0.42f, 0.38f, 0.31f);
            p.text(M + 16, y - 70, 7.5f, false, "GSTIN: " + nullToDash(branchGstin) + "   |   Phone: " + nullToDash(branchPhone), 0.42f, 0.38f, 0.31f);
            if (!branchEmail.isBlank() || !branchWebsite.isBlank()) p.text(M + 16, y - 84, 7.2f, false, cut((branchEmail.isBlank()?"":branchEmail) + (branchEmail.isBlank()||branchWebsite.isBlank()?"":"   |   ") + branchWebsite, 55), 0.42f, 0.38f, 0.31f);

            // Core operational values: date/time and today's gold rate are deliberately prominent.
            p.text(338, y - 18, 7, true, "TODAY'S GOLD RATE", 0.55f, 0.35f, 0.10f);
            p.text(338, y - 40, 17, true, generatedAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)), 0.12f, 0.11f, 0.09f);
            p.text(338, y - 62, 20, true, generatedAt.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH)), 0.55f, 0.35f, 0.10f);
            List<Map<String, Object>> rates = marketRates(market);
            float rx = 338;
            int shown = 0;
            if (!rates.isEmpty()) {
                for (Map<String, Object> r : rates) {
                    String karat = Objects.toString(r.get("karat"), Objects.toString(r.get("purity"), "Gold"));
                    p.text(rx, y - 80, 10, true, karat, 0.50f, 0.34f, 0.11f);
                    p.text(rx, y - 94, 8, false, "Rs. " + money(decimal(r.get("ratePerGram"))) + "/g", 0.27f, 0.24f, 0.19f);
                    rx += 72;
                    if (++shown >= 3 || rx > W - M - 45) break;
                }
            } else {
                p.text(rx, y - 86, 9, true, "Market rate unavailable", 0.45f, 0.40f, 0.33f);
            }
            y -= 120;

            // Invoice title + metadata, kept in a clean two-column alignment.
            p.text(M, y, 20, true, cut(invoiceTitle, 28), 0.10f, 0.10f, 0.09f);
            p.text(M, y - 18, 9, false, cut(invoiceSubtitle, 52), 0.42f, 0.38f, 0.32f);
            p.rightText(W - M, y, 10, true, s.getInvoiceNo(), 0.18f, 0.15f, 0.10f);
            p.rightText(W - M, y - 16, 9, false, "Invoice date: " + (s.getSaleDate() == null ? "-" : s.getSaleDate().format(DATE)), 0.40f, 0.36f, 0.31f);
            p.rightText(W - M, y - 31, 8, false, "Generated: " + generatedAt.format(TIME), 0.48f, 0.44f, 0.38f);
            y -= 52;

            // Customer / branch blocks.
            p.roundRect(M, y - 76, 250, 76, 0.98f, 0.96f, 0.91f, 0.84f, 0.73f, 0.50f);
            p.text(M + 12, y - 17, 7, true, "BILL TO", 0.52f, 0.39f, 0.20f);
            p.text(M + 12, y - 35, 12, true, nullToDash(s.getCustomer().getName()), 0.12f, 0.11f, 0.09f);
            p.text(M + 12, y - 51, 8, false, "Phone: " + nullToDash(s.getCustomer().getPhone()), 0.38f, 0.35f, 0.31f);
            p.text(M + 12, y - 65, 8, false, "GSTIN: " + nullToDash(s.getCustomer().getGstin()), 0.38f, 0.35f, 0.31f);

            p.roundRect(309, y - 76, 250, 76, 0.98f, 0.96f, 0.91f, 0.84f, 0.73f, 0.50f);
            p.text(321, y - 17, 7, true, "STORE / BRANCH", 0.52f, 0.39f, 0.20f);
            p.text(321, y - 35, 12, true, cut(branchName, 31), 0.12f, 0.11f, 0.09f);
            p.text(321, y - 51, 8, false, cut(branchAddress, 38), 0.38f, 0.35f, 0.31f);
            p.text(321, y - 65, 8, false, "GSTIN: " + nullToDash(branchGstin) + " | Rate: Rs. " + money(s.getGoldRate()) + "/g", 0.38f, 0.35f, 0.31f);
            y -= 90;

            // Item table with fixed columns and consistent baselines.
            p.fillRect(M, y - 25, W - 2 * M, 25, 0.78f, 0.62f, 0.28f);
            String[] heads = {"ITEM / DESIGN", "TAG / BARCODE", "PURITY", "GROSS", "STONE", "NET", "RATE / G", "MAKING", "TOTAL"};
            float[] xs = {M + 7, M + 128, M + 214, M + 264, M + 304, M + 344, M + 387, M + 445, M + 507};
            for (int i = 0; i < heads.length; i++) p.text(xs[i], y - 16, 6.5f, true, heads[i], 0.99f, 0.98f, 0.95f);
            y -= 25;
            float rowH = 45;
            for (int i = 0; i < rows.size(); i++) {
                SaleItem si = rows.get(i);
                if (i % 2 == 0) p.fillRect(M, y - rowH, W - 2 * M, rowH, 0.995f, 0.985f, 0.96f);
                String product = productName(si);
                String design = designName(si);
                p.text(M + 7, y - 15, 7.5f, true, cut(product, 20), 0.15f, 0.14f, 0.12f);
                p.text(M + 7, y - 28, 6.5f, false, cut(design, 22), 0.43f, 0.39f, 0.33f);
                p.text(M + 128, y - 15, 7, true, cut(si.getTagNo(), 14), 0.15f, 0.14f, 0.12f);
                p.text(M + 128, y - 28, 6.2f, false, cut(tagBarcode(si), 17), 0.43f, 0.39f, 0.33f);
                p.text(M + 214, y - 20, 7, false, cut(si.getPurity(), 10), 0.20f, 0.18f, 0.15f);
                p.text(M + 264, y - 20, 7, false, weight(si.getGrossWeight()), 0.20f, 0.18f, 0.15f);
                p.text(M + 304, y - 20, 7, false, weight(si.getStoneWeight()), 0.20f, 0.18f, 0.15f);
                p.text(M + 344, y - 20, 7, true, weight(si.getNetWeight()), 0.12f, 0.11f, 0.09f);
                p.text(M + 387, y - 20, 7, false, money(si.getGoldRate()), 0.20f, 0.18f, 0.15f);
                p.text(M + 445, y - 20, 7, false, money(si.getMakingCharge()), 0.20f, 0.18f, 0.15f);
                p.rightText(W - M - 5, y - 20, 7.5f, true, money(si.getTotal()), 0.12f, 0.11f, 0.09f);
                p.line(M, y - rowH, W - M, y - rowH, 0.86f, 0.82f, 0.74f);
                y -= rowH;
            }
            if (rows.isEmpty()) { p.text(M + 10, y - 20, 8, false, "No sale items found.", 0.45f, 0.41f, 0.35f); y -= rowH; }

            if (last) {
                y -= 18;
                float breakupH = 160;
                p.roundRect(M, y - breakupH, 315, breakupH, 0.98f, 0.97f, 0.94f, 0.83f, 0.76f, 0.63f);
                p.text(M + 12, y - 18, 8, true, "PRICE & TAX BREAKUP", 0.52f, 0.39f, 0.20f);
                float by = y - 36;
                p.row(M + 12, M + 300, by, "Gold value", goldValue(rows), false); by -= 15;
                p.row(M + 12, M + 300, by, "Wastage", wastage(rows), false); by -= 15;
                p.row(M + 12, M + 300, by, "Making charge", sumMaking(rows), false); by -= 15;
                p.row(M + 12, M + 300, by, "Stone charge", sumStone(rows), false); by -= 15;
                p.row(M + 12, M + 300, by, "Other charges", sumOther(rows), false); by -= 15;
                p.row(M + 12, M + 300, by, "Subtotal", s.getSubtotal(), true); by -= 17;

                if ("CGST_SGST".equalsIgnoreCase(s.getTaxMode())) {
                    p.row(M + 12, M + 300, by, "CGST @ " + money(s.getCgstRate()) + "%", s.getGst().multiply(s.getCgstRate()).divide(s.getCgstRate().add(s.getSgstRate()).signum() == 0 ? BigDecimal.ONE : s.getCgstRate().add(s.getSgstRate()), 3, java.math.RoundingMode.HALF_UP), false);
                    by -= 15;
                    p.row(M + 12, M + 300, by, "SGST @ " + money(s.getSgstRate()) + "%", s.getGst().multiply(s.getSgstRate()).divide(s.getCgstRate().add(s.getSgstRate()).signum() == 0 ? BigDecimal.ONE : s.getCgstRate().add(s.getSgstRate()), 3, java.math.RoundingMode.HALF_UP), false);
                } else if ("NONE".equalsIgnoreCase(s.getTaxMode())) {
                    p.row(M + 12, M + 300, by, "Tax", BigDecimal.ZERO, false);
                } else {
                    p.row(M + 12, M + 300, by, "GST @ " + money(s.getTaxRate()) + "%", s.getGst(), false);
                }

                float tx = 369;
                p.roundRect(tx, y - breakupH, W - M - tx, breakupH, 0.97f, 0.90f, 0.72f, 0.70f, 0.48f, 0.16f);
                p.text(tx + 14, y - 18, 8, true, "AMOUNT PAYABLE", 0.34f, 0.23f, 0.08f);
                p.text(tx + 14, y - 48, 24, true, "Rs. " + money(s.getTotal()), 0.44f, 0.28f, 0.06f);
                p.text(tx + 14, y - 72, 8, false, "Subtotal: Rs. " + money(s.getSubtotal()), 0.25f, 0.22f, 0.16f);
                if ("CGST_SGST".equalsIgnoreCase(s.getTaxMode())) {
                    p.text(tx + 14, y - 88, 8, false, "CGST: Rs. " + money(s.getGst().multiply(s.getCgstRate()).divide(s.getCgstRate().add(s.getSgstRate()).signum() == 0 ? BigDecimal.ONE : s.getCgstRate().add(s.getSgstRate()), 3, java.math.RoundingMode.HALF_UP)), 0.25f, 0.22f, 0.16f);
                    p.text(tx + 14, y - 104, 8, false, "SGST: Rs. " + money(s.getGst().multiply(s.getSgstRate()).divide(s.getCgstRate().add(s.getSgstRate()).signum() == 0 ? BigDecimal.ONE : s.getCgstRate().add(s.getSgstRate()), 3, java.math.RoundingMode.HALF_UP)), 0.25f, 0.22f, 0.16f);
                    p.text(tx + 14, y - 120, 8, false, "Tax total: Rs. " + money(s.getGst()), 0.25f, 0.22f, 0.16f);
                    p.text(tx + 14, y - 136, 8, false, "Discount: Rs. " + money(s.getDiscount()), 0.25f, 0.22f, 0.16f);
                } else {
                    p.text(tx + 14, y - 88, 8, false, ("NONE".equalsIgnoreCase(s.getTaxMode()) ? "Tax: Rs. 0.00" : "GST @ " + money(s.getTaxRate()) + "%: Rs. " + money(s.getGst())), 0.25f, 0.22f, 0.16f);
                    p.text(tx + 14, y - 104, 8, false, "Discount: Rs. " + money(s.getDiscount()), 0.25f, 0.22f, 0.16f);
                    p.text(tx + 14, y - 120, 8, false, "Payment status: " + nullToDash(s.getPaymentStatus()), 0.25f, 0.22f, 0.16f);
                    p.text(tx + 14, y - 136, 7, false, "Gold rate captured at billing time", 0.43f, 0.37f, 0.25f);
                }
                y -= breakupH + 18;

                p.text(M, y, 8, true, "CUSTOMER ACKNOWLEDGEMENT", 0.52f, 0.39f, 0.20f);
                p.line(M, y - 34, M + 220, y - 34, 0.70f, 0.66f, 0.58f);
                p.text(M, y - 47, 7, false, "Customer signature", 0.48f, 0.44f, 0.38f);
                p.line(355, y - 34, W - M, y - 34, 0.70f, 0.66f, 0.58f);
                p.text(355, y - 47, 7, false, "Authorised signatory", 0.48f, 0.44f, 0.38f);
                String terms = branch != null ? branch.getInvoiceTerms() : null;
                String footer = branch != null ? branch.getInvoiceFooter() : null;
                String footerText = footer != null && !footer.isBlank() ? footer : "Thank you for choosing Jewellery360. Please retain this invoice for future exchange, service and warranty reference.";
                if (terms != null && !terms.isBlank()) footerText = footerText + " | Terms: " + terms;
                p.text(M, 26, 7, false, cut(footerText, 118), 0.46f, 0.42f, 0.36f);
            }
            p.rightText(W - M, 26, 7, false, "Page " + pageNo + " of " + pageCount, 0.46f, 0.42f, 0.36f);
            return p;
        }

        private static List<Map<String, Object>> marketRates(Map<String, Object> market) {
            Object x = market == null ? null : market.get("marketRates");
            if (!(x instanceof List<?>)) x = market == null ? null : market.get("rates");
            if (!(x instanceof List<?> list)) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object v : list) if (v instanceof Map<?, ?> m) {
                Map<String, Object> n = new LinkedHashMap<>();
                m.forEach((k, val) -> n.put(String.valueOf(k), val));
                out.add(n);
            }
            out.sort(Comparator.comparingInt(a -> karatNumber(Objects.toString(a.get("karat"), Objects.toString(a.get("purity"), "")))));
            return out;
        }
        private static int karatNumber(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch(Exception e){return 0;} }
        private static BigDecimal decimal(Object x){try{return x instanceof BigDecimal b?b:new BigDecimal(String.valueOf(x));}catch(Exception e){return BigDecimal.ZERO;}}
        private static String productName(SaleItem i){return i.getJewelleryItem()!=null&&i.getJewelleryItem().getProduct()!=null?i.getJewelleryItem().getProduct().getName():"Jewellery";}
        private static String designName(SaleItem i){return i.getJewelleryItem()!=null&&i.getJewelleryItem().getProduct()!=null&&i.getJewelleryItem().getProduct().getDesign()!=null?i.getJewelleryItem().getProduct().getDesign().getName():"";}
        private static String tagBarcode(SaleItem i){return i.getJewelleryItem()!=null&&i.getJewelleryItem().getTag()!=null?i.getJewelleryItem().getTag().getBarcode():"";}
        private static String weight(BigDecimal x){return x==null?"0.000":x.setScale(3,java.math.RoundingMode.HALF_UP).toPlainString();}
        private static BigDecimal goldValue(List<SaleItem> r){return r.stream().map(x->nz(x.getNetWeight()).multiply(nz(x.getGoldRate()))).reduce(BigDecimal.ZERO,BigDecimal::add);}
        private static BigDecimal wastage(List<SaleItem> r){return r.stream().map(SaleItem::getWastageValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
        private static BigDecimal sumMaking(List<SaleItem> r){return r.stream().map(SaleItem::getMakingCharge).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
        private static BigDecimal sumStone(List<SaleItem> r){return r.stream().map(SaleItem::getStoneCharge).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
        private static BigDecimal sumOther(List<SaleItem> r){return r.stream().map(SaleItem::getOtherCharge).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
        private static BigDecimal nz(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
        private static String cut(String s,int n){if(s==null)return "";return s.length()<=n?s:s.substring(0,Math.max(0,n-1))+"…";}
        private static String nullToDash(String s){return s==null||s.isBlank()?"—":s;}
        private static String nullToEmpty(String s){return s==null?"":s;}
    }

    static final class Page {
        final StringBuilder c = new StringBuilder();
        void text(float x,float y,float size,boolean bold,String value,float r,float g,float b){if(value==null)return;c.append(String.format(Locale.US,"BT %.3f %.3f %.3f rg /F%d %.1f Tf 1 0 0 1 %.1f %.1f Tm (%s) Tj ET\n",r,g,b,bold?6:5,size,x,y,esc(value)));}
        void rightText(float right,float y,float size,boolean bold,String value,float r,float g,float b){if(value==null)return;float width=value.length()*size*.48f;text(right-width,y,size,bold,value,r,g,b);}
        void line(float x1,float y1,float x2,float y2,float r,float g,float b){c.append(String.format(Locale.US,"%.3f %.3f %.3f RG 0.6 w %.1f %.1f m %.1f %.1f l S\n",r,g,b,x1,y1,x2,y2));}
        void fillRect(float x,float y,float w,float h,float r,float g,float b){c.append(String.format(Locale.US,"%.3f %.3f %.3f rg %.1f %.1f %.1f %.1f re f\n",r,g,b,x,y,w,h));}
        void roundRect(float x,float y,float w,float h,float fr,float fg,float fb,float sr,float sg,float sb){fillRect(x,y,w,h,fr,fg,fb);line(x,y,x+w,y,sr,sg,sb);line(x,y+h,x+w,y+h,sr,sg,sb);line(x,y,x,y+h,sr,sg,sb);line(x+w,y,x+w,y+h,sr,sg,sb);}
        void row(float lx,float rx,float y,String label,BigDecimal value,boolean bold){text(lx,y,7,bold,label,0.25f,0.23f,0.20f);rightText(rx,y,7,bold,"Rs. "+money(value),0.18f,0.16f,0.13f);}
        static String esc(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)").replace("\r"," ").replace("\n"," ").replace("₹","Rs.");}
    }

    static final class PdfWriter {
        static byte[] write(List<Page> pages){
            int fontRegular=pages.size()*0+1; // documentation marker; actual objects are assembled below.
            List<String> objs=new ArrayList<>();
            objs.add("<< /Type /Catalog /Pages 2 0 R >>");
            int pageObjStart=3;
            int contentObjStart=pageObjStart+pages.size();
            int fontRegularObj=contentObjStart+pages.size();
            int fontBoldObj=fontRegularObj+1;
            StringBuilder kids=new StringBuilder();for(int i=0;i<pages.size();i++){if(i>0)kids.append(' ');kids.append(pageObjStart+i).append(" 0 R");}
            objs.add("<< /Type /Pages /Kids ["+kids+"] /Count "+pages.size()+" >>");
            for(int i=0;i<pages.size();i++){
                objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 "+fontRegularObj+" 0 R /F6 "+fontBoldObj+" 0 R >> >> /Contents "+(contentObjStart+i)+" 0 R >>");
            }
            for(Page page:pages){String body=page.c.toString();objs.add("<< /Length "+body.getBytes(StandardCharsets.ISO_8859_1).length+" >>\nstream\n"+body+"endstream");}
            objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
            objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
            StringBuilder pdf=new StringBuilder("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");List<Integer> offsets=new ArrayList<>();
            for(int i=0;i<objs.size();i++){offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);pdf.append(i+1).append(" 0 obj\n").append(objs.get(i)).append("\nendobj\n");}
            int xref=pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;pdf.append("xref\n0 ").append(objs.size()+1).append("\n0000000000 65535 f \n");for(int off:offsets)pdf.append(String.format(Locale.US,"%010d 00000 n \n",off));pdf.append("trailer\n<< /Size ").append(objs.size()+1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    static class SimplePdf {
        static byte[] text(List<String> lines){StringBuilder c=new StringBuilder("BT /F1 10 Tf 45 790 Td\n");for(String l:lines)c.append("(").append(esc(l)).append(") Tj 0 -16 Td\n");c.append("ET");String[] o={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>","<< /Length "+c.length()+" >>\nstream\n"+c+"\nendstream","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};StringBuilder p=new StringBuilder("%PDF-1.4\n");List<Integer> off=new ArrayList<>();for(int i=0;i<o.length;i++){off.add(p.length());p.append(i+1).append(" 0 obj\n").append(o[i]).append("\nendobj\n");}int x=p.length();p.append("xref\n0 ").append(o.length+1).append("\n0000000000 65535 f \n");for(int n:off)p.append(String.format("%010d 00000 n \n",n));p.append("trailer\n<< /Size ").append(o.length+1).append(" /Root 1 0 R >>\nstartxref\n").append(x).append("\n%%EOF");return p.toString().getBytes(StandardCharsets.ISO_8859_1);}
        static String esc(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)").replace("\r"," ").replace("\n"," ").replace("₹","Rs.");}
    }
}
