package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

import static com.jewellery360.support.Num.*;

/**
 * Every report is a plain table: {key, title, from, to, columns:[{key,label,type}], rows:[...], totals:{...}} so the web and
 * mobile apps render and export all of them the same way. Figures come straight from bills, payments and stock - nothing
 * is estimated.
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    public record Def(String key, String title, String group, boolean needsProfit) {}

    public static final List<Def> REPORTS = List.of(
            new Def("sales-daily", "Sales by day", "Sales", false),
            new Def("sales-category", "Sales by category", "Sales", false),
            new Def("sales-product", "Sales by product", "Sales", false),
            new Def("sales-purity", "Weight sold by purity", "Sales", false),
            new Def("sales-branch", "Sales by branch", "Sales", false),
            new Def("sales-staff", "Sales by staff", "Sales", false),
            new Def("gst", "GST summary", "Sales", false),
            new Def("returns", "Returns", "Sales", false),
            new Def("stock-purity", "Stock by purity (weight & value)", "Stock", false),
            new Def("stock-category", "Stock by category", "Stock", false),
            new Def("stock-ageing", "Stock ageing", "Stock", false),
            new Def("dead-stock", "Dead stock (not sold in 180+ days)", "Stock", false),
            new Def("old-gold", "Old gold bought / exchanged", "Stock", false),
            new Def("profit", "Profit by bill", "Finance", true),
            new Def("profit-category", "Profit by category", "Finance", true),
            new Def("outstanding", "Customer dues", "Finance", false),
            new Def("supplier-dues", "Supplier dues", "Finance", false),
            new Def("payment-modes", "Collections by payment mode", "Finance", false),
            new Def("expenses", "Expenses by category", "Finance", false),
            new Def("pending-work", "Pending orders & repairs", "Operations", false));

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final SaleReturnRepository returns;
    private final JewelleryItemRepository items;
    private final OldGoldTransactionRepository oldGold;
    private final PurchaseOrderRepository purchases;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final CustomOrderRepository orders;
    private final RepairOrderRepository repairs;
    private final GoldRateService rates;
    private final PermissionService permissions;

    public List<Map<String, Object>> catalogue(TenantContext.Ctx ctx) {
        boolean profit = permissions.has(ctx.me(), "SEE_PROFIT");
        return REPORTS.stream().filter(d -> profit || !d.needsProfit())
                .map(d -> Map.<String, Object>of("key", d.key(), "title", d.title(), "group", d.group())).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(TenantContext.Ctx ctx, String key, LocalDate from, LocalDate to) {
        permissions.requirePage(ctx.me(), "REPORTS");
        Def def = REPORTS.stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow(() -> Errors.bad("Unknown report."));
        if (def.needsProfit()) permissions.requirePower(ctx.me(), "SEE_PROFIT");
        LocalDate t = to == null ? GoldRateService.today() : to;
        LocalDate f = from == null ? t.withDayOfMonth(1) : from;
        if (t.isBefore(f)) throw Errors.bad("'To' date is before 'from' date.");
        if (ChronoUnit.DAYS.between(f, t) > 1100) throw Errors.bad("Pick a range of three years or less.");
        Table tb = new Table(def, f, t);
        Long c = ctx.companyId(), b = ctx.branchId();
        switch (key) {
            case "sales-daily" -> salesDaily(tb, c, b, f, t);
            case "sales-category" -> salesGrouped(tb, c, b, f, t, si -> si.getJewelleryItem().getProduct().getCategory().getName(), "Category");
            case "sales-product" -> salesGrouped(tb, c, b, f, t, si -> si.getJewelleryItem().getProduct().getName(), "Product");
            case "sales-purity" -> salesGrouped(tb, c, b, f, t, SaleItem::getPurity, "Purity");
            case "sales-branch" -> salesBy(tb, c, b, f, t, s -> s.getBranch().getName(), "Branch");
            case "sales-staff" -> salesBy(tb, c, b, f, t, s -> s.getCreatedBy() == null ? "-" : s.getCreatedBy().getUsername(), "Staff");
            case "gst" -> gst(tb, c, b, f, t);
            case "returns" -> returnsReport(tb, c, b, f, t);
            case "stock-purity" -> stockPurity(tb, ctx);
            case "stock-category" -> stockCategory(tb, c, b);
            case "stock-ageing" -> stockAgeing(tb, c, b);
            case "dead-stock" -> deadStock(tb, c, b, permissions.has(ctx.me(), "SEE_PROFIT"));
            case "old-gold" -> oldGoldReport(tb, c, b, f, t);
            case "profit" -> profit(tb, c, b, f, t);
            case "profit-category" -> profitCategory(tb, c, b, f, t);
            case "outstanding" -> outstanding(tb, c, b);
            case "supplier-dues" -> supplierDues(tb, c);
            case "payment-modes" -> paymentModes(tb, c, b, f, t);
            case "expenses" -> expenseReport(tb, c, b, f, t);
            case "pending-work" -> pendingWork(tb, c, b);
            default -> throw Errors.bad("Unknown report.");
        }
        return tb.out();
    }

    /* ------------------------------------------------------------------ sales */

    private void salesDaily(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("date", "Date", "date").col("bills", "Bills", "int").col("subtotal", "Before discount", "money").col("discount", "Discount", "money")
          .col("taxable", "Taxable", "money").col("gst", "GST", "money").col("total", "Bill total", "money").col("returned", "Returned", "money")
          .col("net", "Net sales", "money").col("due", "Still due", "money");
        TreeMap<LocalDate, Map<String, Object>> byDay = new TreeMap<>();
        for (Sale s : sales.completedBetween(c, b, f, t)) {
            Map<String, Object> r = byDay.computeIfAbsent(s.getSaleDate(), d -> Table.row("date", d));
            Table.add(r, "bills", 1);
            Table.add(r, "subtotal", s.getSubtotal());
            Table.add(r, "discount", s.getDiscount());
            Table.add(r, "taxable", s.getTaxableValue());
            Table.add(r, "gst", s.getGst());
            Table.add(r, "total", s.getTotal());
            Table.add(r, "returned", s.getReturnedAmount());
            Table.add(r, "net", s.getTotal().subtract(s.getReturnedAmount()));
            Table.add(r, "due", s.balance().max(BigDecimal.ZERO));
        }
        byDay.values().forEach(tb::row);
        tb.sum("bills", "subtotal", "discount", "taxable", "gst", "total", "returned", "net", "due");
    }

    private void salesGrouped(Table tb, Long c, Long b, LocalDate f, LocalDate t, Function<SaleItem, String> key, String label) {
        tb.col("name", label, "text").col("pieces", "Pieces", "int").col("grossWeight", "Gross wt (g)", "weight").col("netWeight", "Net wt (g)", "weight")
          .col("making", "Making", "money").col("taxable", "Taxable", "money").col("total", "Value incl. GST", "money");
        Map<String, Map<String, Object>> m = new TreeMap<>();
        for (SaleItem si : saleItems.soldBetween(c, b, f, t)) {
            Map<String, Object> r = m.computeIfAbsent(key.apply(si), k -> Table.row("name", k));
            Table.add(r, "pieces", 1);
            Table.add(r, "grossWeight", si.getGrossWeight());
            Table.add(r, "netWeight", si.getNetWeight());
            Table.add(r, "making", si.getMakingCharge());
            Table.add(r, "taxable", si.getTaxableValue());
            Table.add(r, "total", si.getTotal());
        }
        m.values().stream().sorted(Comparator.comparing(r -> ((BigDecimal) r.get("taxable")).negate())).forEach(tb::row);
        tb.sum("pieces", "grossWeight", "netWeight", "making", "taxable", "total");
    }

    private void salesBy(Table tb, Long c, Long b, LocalDate f, LocalDate t, Function<Sale, String> key, String label) {
        tb.col("name", label, "text").col("bills", "Bills", "int").col("net", "Net sales", "money").col("avg", "Average bill", "money").col("due", "Still due", "money");
        Map<String, Map<String, Object>> m = new TreeMap<>();
        for (Sale s : sales.completedBetween(c, b, f, t)) {
            Map<String, Object> r = m.computeIfAbsent(key.apply(s), k -> Table.row("name", k));
            Table.add(r, "bills", 1);
            Table.add(r, "net", s.getTotal().subtract(s.getReturnedAmount()));
            Table.add(r, "due", s.balance().max(BigDecimal.ZERO));
        }
        for (Map<String, Object> r : m.values()) r.put("avg", r2(((BigDecimal) r.get("net")).divide(BigDecimal.valueOf(((Number) r.get("bills")).longValue()), 2, java.math.RoundingMode.HALF_UP)));
        m.values().stream().sorted(Comparator.comparing(r -> ((BigDecimal) r.get("net")).negate())).forEach(tb::row);
        tb.sum("bills", "net", "due");
    }

    private void gst(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("invoiceNo", "Bill", "text").col("date", "Date", "date").col("customer", "Customer", "text").col("gstin", "Customer GSTIN", "text")
          .col("taxable", "Taxable", "money").col("cgst", "CGST", "money").col("sgst", "SGST", "money").col("igst", "GST / IGST", "money").col("total", "Total", "money");
        for (Sale s : sales.completedBetween(c, b, f, t).stream().sorted(Comparator.comparing(Sale::getSaleDate).thenComparing(Sale::getId)).toList()) {
            boolean split = "CGST_SGST".equals(s.getTaxMode());
            BigDecimal half = r2(s.getGst().divide(BigDecimal.valueOf(2), 3, java.math.RoundingMode.HALF_UP));
            Map<String, Object> r = Table.row("invoiceNo", s.getInvoiceNo());
            r.put("date", s.getSaleDate());
            r.put("customer", s.getCustomer().getName());
            r.put("gstin", s.getCustomer().getGstin());
            r.put("taxable", s.getTaxableValue());
            r.put("cgst", split ? half : BigDecimal.ZERO);
            r.put("sgst", split ? s.getGst().subtract(half) : BigDecimal.ZERO);
            r.put("igst", split ? BigDecimal.ZERO : s.getGst());
            r.put("total", s.getTotal());
            tb.row(r);
        }
        tb.sum("taxable", "cgst", "sgst", "igst", "total");
        tb.note("Bills are shown at their original value; returns are listed in the Returns report.");
    }

    private void returnsReport(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("returnNo", "Return", "text").col("date", "Date", "date").col("invoiceNo", "Bill", "text").col("customer", "Customer", "text")
          .col("reason", "Reason", "text").col("amount", "Amount", "money");
        for (SaleReturn r : returns.between(c, b, f, t)) {
            Map<String, Object> row = Table.row("returnNo", r.getReturnNo());
            row.put("date", r.getReturnDate());
            row.put("invoiceNo", r.getSale().getInvoiceNo());
            row.put("customer", r.getSale().getCustomer().getName());
            row.put("reason", r.getReason());
            row.put("amount", r.getAmount());
            tb.row(row);
        }
        tb.sum("amount");
    }

    /* ------------------------------------------------------------------ stock */

    private List<JewelleryItem> inStock(Long c, Long b) { return items.list(c, b, JewelleryItem.IN_STOCK, Pageable.unpaged()); }

    private void stockPurity(Table tb, TenantContext.Ctx ctx) {
        tb.col("purity", "Purity", "text").col("pieces", "Pieces", "int").col("grossWeight", "Gross wt (g)", "weight").col("netWeight", "Net wt (g)", "weight")
          .col("fineWeight", "Fine wt (g)", "weight").col("rate", "Today's rate / g", "money").col("metalValue", "Metal value today", "money");
        boolean cost = permissions.has(ctx.me(), "SEE_PROFIT");
        if (cost) tb.col("cost", "Cost value", "money");
        Map<Long, Map<Long, GoldRate>> rateCache = new HashMap<>();
        LocalDate today = GoldRateService.today();
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        boolean missingRate = false;
        for (JewelleryItem i : inStock(ctx.companyId(), ctx.branchId())) {
            Map<String, Object> r = m.computeIfAbsent(i.getPurity().getName(), k -> Table.row("purity", k));
            Table.add(r, "pieces", 1);
            Table.add(r, "grossWeight", i.getGrossWeight());
            Table.add(r, "netWeight", i.getNetWeight());
            Table.add(r, "fineWeight", r3(i.getNetWeight().multiply(i.getPurity().getFineness())));
            GoldRate g = rateCache.computeIfAbsent(i.getBranch().getId(), id -> rates.rateMap(id, today)).get(i.getPurity().getId());
            if (g == null) missingRate = true;
            else {
                if (ctx.branchId() != null) r.put("rate", g.getRatePerGram());
                Table.add(r, "metalValue", r2(i.getNetWeight().multiply(g.getRatePerGram())));
            }
            if (cost) Table.add(r, "cost", i.getCostPrice());
        }
        m.values().forEach(tb::row);
        tb.sum("pieces", "grossWeight", "netWeight", "fineWeight", "metalValue", "cost");
        if (missingRate) tb.note("Some purities have no saved rate yet, so their metal value is left out.");
    }

    private void stockCategory(Table tb, Long c, Long b) {
        tb.col("category", "Category", "text").col("pieces", "Pieces", "int").col("netWeight", "Net wt (g)", "weight");
        for (Object[] r : items.stockByCategory(c, b)) {
            Map<String, Object> row = Table.row("category", r[0]);
            row.put("pieces", ((Number) r[1]).longValue());
            row.put("netWeight", r[2]);
            tb.row(row);
        }
        tb.sum("pieces", "netWeight");
    }

    private static final int[] AGE_LIMITS = {30, 90, 180, 365};
    private static final String[] AGE_LABELS = {"0-30 days", "31-90 days", "91-180 days", "181-365 days", "Over 1 year"};

    private void stockAgeing(Table tb, Long c, Long b) {
        tb.col("bucket", "Age in stock", "text").col("pieces", "Pieces", "int").col("netWeight", "Net wt (g)", "weight").col("share", "% of pieces", "pct");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String l : AGE_LABELS) { Map<String, Object> r = Table.row("bucket", l); r.put("pieces", 0L); r.put("netWeight", BigDecimal.ZERO); rows.add(r); }
        List<JewelleryItem> list = inStock(c, b);
        for (JewelleryItem i : list) {
            long age = Views.ageDays(i.getCreatedAt());
            int k = 0;
            while (k < AGE_LIMITS.length && age > AGE_LIMITS[k]) k++;
            Table.add(rows.get(k), "pieces", 1);
            Table.add(rows.get(k), "netWeight", i.getNetWeight());
        }
        for (Map<String, Object> r : rows) {
            long n = ((Number) r.get("pieces")).longValue();
            r.put("share", list.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(n * 100.0 / list.size()).setScale(1, java.math.RoundingMode.HALF_UP));
            tb.row(r);
        }
        tb.sum("pieces", "netWeight");
    }

    private void deadStock(Table tb, Long c, Long b, boolean cost) {
        tb.col("tagNo", "Tag", "text").col("product", "Item", "text").col("category", "Category", "text").col("purity", "Purity", "text")
          .col("netWeight", "Net wt (g)", "weight").col("days", "Days in stock", "int").col("branch", "Branch", "text");
        if (cost) tb.col("cost", "Cost", "money");
        inStock(c, b).stream().filter(i -> Views.ageDays(i.getCreatedAt()) > 180)
                .sorted(Comparator.comparing(JewelleryItem::getCreatedAt)).forEach(i -> {
                    Map<String, Object> r = Table.row("tagNo", i.getTagNo());
                    r.put("product", i.getProduct().getName());
                    r.put("category", i.getProduct().getCategory().getName());
                    r.put("purity", i.getPurity().getName());
                    r.put("netWeight", i.getNetWeight());
                    r.put("days", Views.ageDays(i.getCreatedAt()));
                    r.put("branch", i.getBranch().getName());
                    if (cost) r.put("cost", i.getCostPrice());
                    tb.row(r);
                });
        tb.sum("netWeight", "cost");
    }

    private void oldGoldReport(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("no", "Voucher", "text").col("date", "Date", "date").col("type", "Type", "text").col("customer", "Customer", "text")
          .col("grossWeight", "Gross wt (g)", "weight").col("netWeight", "Net wt (g)", "weight").col("amount", "Value", "money");
        for (OldGoldTransaction o : oldGold.between(c, b, f, t)) {
            Map<String, Object> r = Table.row("no", o.getTransactionNo());
            r.put("date", o.getTransactionDate());
            r.put("type", o.getTransactionType());
            r.put("customer", o.getCustomer().getName());
            r.put("grossWeight", o.getTotalGrossWeight());
            r.put("netWeight", o.getTotalNetWeight());
            r.put("amount", o.getTotalAmount());
            tb.row(r);
        }
        tb.sum("grossWeight", "netWeight", "amount");
    }

    /* ------------------------------------------------------------------ finance */

    private void profit(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("invoiceNo", "Bill", "text").col("date", "Date", "date").col("customer", "Customer", "text").col("sale", "Sale value (excl. GST)", "money")
          .col("cost", "Cost", "money").col("profit", "Profit", "money").col("margin", "Margin %", "pct");
        Map<Long, Map<String, Object>> m = new LinkedHashMap<>();
        boolean missingCost = false;
        for (SaleItem si : saleItems.soldBetween(c, b, f, t)) {
            Sale s = si.getSale();
            Map<String, Object> r = m.computeIfAbsent(s.getId(), id -> {
                Map<String, Object> x = Table.row("invoiceNo", s.getInvoiceNo());
                x.put("date", s.getSaleDate());
                x.put("customer", s.getCustomer().getName());
                return x;
            });
            Table.add(r, "sale", si.getTaxableValue());
            Table.add(r, "cost", si.getJewelleryItem().getCostPrice());
            if (si.getJewelleryItem().getCostPrice().signum() == 0) missingCost = true;
        }
        for (Map<String, Object> r : m.values()) { margin(r); tb.row(r); }
        tb.sum("sale", "cost", "profit");
        margin(tb.totals);
        if (missingCost) tb.note("Some sold pieces have no cost price entered, so their profit is overstated.");
        tb.note("Returned pieces are excluded.");
    }

    private void profitCategory(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("category", "Category", "text").col("pieces", "Pieces", "int").col("sale", "Sale value (excl. GST)", "money")
          .col("cost", "Cost", "money").col("profit", "Profit", "money").col("margin", "Margin %", "pct");
        Map<String, Map<String, Object>> m = new TreeMap<>();
        for (SaleItem si : saleItems.soldBetween(c, b, f, t)) {
            Map<String, Object> r = m.computeIfAbsent(si.getJewelleryItem().getProduct().getCategory().getName(), k -> Table.row("category", k));
            Table.add(r, "pieces", 1);
            Table.add(r, "sale", si.getTaxableValue());
            Table.add(r, "cost", si.getJewelleryItem().getCostPrice());
        }
        for (Map<String, Object> r : m.values()) { margin(r); tb.row(r); }
        tb.sum("pieces", "sale", "cost", "profit");
        margin(tb.totals);
    }

    private static BigDecimal dec(Object v) {
        return v instanceof BigDecimal d ? d : v instanceof Number n ? BigDecimal.valueOf(n.longValue()) : BigDecimal.ZERO;
    }

    private static void margin(Map<String, Object> r) {
        // totals of an empty report are plain 0 (Integer), not BigDecimal
        BigDecimal sale = dec(r.get("sale")), cost = dec(r.get("cost"));
        BigDecimal p = r2(sale.subtract(cost));
        r.put("profit", p);
        r.put("margin", sale.signum() == 0 ? BigDecimal.ZERO : p.multiply(HUNDRED).divide(sale, 1, java.math.RoundingMode.HALF_UP));
    }

    private void outstanding(Table tb, Long c, Long b) {
        tb.col("invoiceNo", "Bill", "text").col("date", "Date", "date").col("customer", "Customer", "text").col("phone", "Phone", "text")
          .col("total", "Bill total", "money").col("paid", "Paid", "money").col("due", "Due", "money").col("days", "Days", "int");
        LocalDate today = GoldRateService.today();
        for (Sale s : sales.unpaid(c, b)) {
            Map<String, Object> r = Table.row("invoiceNo", s.getInvoiceNo());
            r.put("date", s.getSaleDate());
            r.put("customer", s.getCustomer().getName());
            r.put("phone", s.getCustomer().getPhone());
            r.put("total", s.getTotal().subtract(s.getReturnedAmount()));
            r.put("paid", s.getPaidAmount());
            r.put("due", s.balance());
            r.put("days", ChronoUnit.DAYS.between(s.getSaleDate(), today));
            tb.row(r);
        }
        tb.sum("total", "paid", "due");
    }

    private void supplierDues(Table tb, Long c) {
        tb.col("supplier", "Supplier", "text").col("due", "We owe", "money");
        for (Object[] r : purchases.outstandingBySupplier(c)) {
            Map<String, Object> row = Table.row("supplier", r[1]);
            row.put("due", r2((BigDecimal) r[2]));
            tb.row(row);
        }
        tb.sum("due");
    }

    private void paymentModes(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("mode", "Mode", "text").col("in", "Received", "money").col("out", "Paid out", "money").col("net", "Net", "money").col("count", "Entries", "int");
        Map<String, Map<String, Object>> m = new TreeMap<>();
        for (Payment p : payments.between(c, b, f, t)) {
            if (!p.isMoney()) continue;
            Map<String, Object> r = m.computeIfAbsent(p.getPaymentMode(), k -> { Map<String, Object> x = Table.row("mode", k); x.put("in", BigDecimal.ZERO); x.put("out", BigDecimal.ZERO); return x; });
            Table.add(r, p.direction() > 0 ? "in" : "out", p.getAmount());
            Table.add(r, "count", 1);
        }
        for (Map<String, Object> r : m.values()) { r.put("net", r2(((BigDecimal) r.get("in")).subtract((BigDecimal) r.get("out")))); tb.row(r); }
        tb.sum("in", "out", "net", "count");
        tb.note("Advance and old-gold adjustments on bills move no money and are not counted here.");
    }

    private void expenseReport(Table tb, Long c, Long b, LocalDate f, LocalDate t) {
        tb.col("category", "Category", "text").col("count", "Entries", "int").col("amount", "Amount", "money");
        Map<String, Map<String, Object>> m = new TreeMap<>();
        for (Expense e : expenses.between(c, b, f, t)) {
            Map<String, Object> r = m.computeIfAbsent(e.getCategory(), k -> Table.row("category", k));
            Table.add(r, "count", 1);
            Table.add(r, "amount", e.getAmount());
        }
        m.values().forEach(tb::row);
        tb.sum("count", "amount");
    }

    private void pendingWork(Table tb, Long c, Long b) {
        tb.col("kind", "Type", "text").col("no", "Number", "text").col("date", "Received", "date").col("due", "Due", "date").col("customer", "Customer", "text")
          .col("details", "Details", "text").col("status", "Status", "text").col("overdue", "Overdue", "text");
        LocalDate today = GoldRateService.today();
        for (CustomOrder o : orders.list(c, b, null)) {
            if (!List.of("ORDERED", "IN_PROGRESS", "READY").contains(o.getStatus())) continue;
            Map<String, Object> r = Table.row("kind", "Order");
            r.put("no", o.getOrderNo()); r.put("date", o.getOrderDate()); r.put("due", o.getDeliveryDate());
            r.put("customer", o.getCustomer().getName()); r.put("details", o.getDescription()); r.put("status", o.getStatus());
            r.put("overdue", o.getDeliveryDate() != null && o.getDeliveryDate().isBefore(today) && !"READY".equals(o.getStatus()) ? "Yes" : "");
            tb.row(r);
        }
        for (RepairOrder o : repairs.list(c, b, null)) {
            if (!List.of("RECEIVED", "IN_REPAIR", "READY").contains(o.getStatus())) continue;
            Map<String, Object> r = Table.row("kind", "Repair");
            r.put("no", o.getOrderNo()); r.put("date", o.getReceivedDate()); r.put("due", o.getExpectedDate());
            r.put("customer", o.getCustomer().getName()); r.put("details", o.getItemDescription() + " - " + o.getProblem()); r.put("status", o.getStatus());
            r.put("overdue", o.getExpectedDate() != null && o.getExpectedDate().isBefore(today) && !"READY".equals(o.getStatus()) ? "Yes" : "");
            tb.row(r);
        }
    }

    /* ------------------------------------------------------------------ table */

    static final class Table {
        final Def def;
        final LocalDate from, to;
        final List<Map<String, Object>> columns = new ArrayList<>();
        final List<Map<String, Object>> rows = new ArrayList<>();
        final Map<String, Object> totals = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();

        Table(Def def, LocalDate from, LocalDate to) { this.def = def; this.from = from; this.to = to; }

        Table col(String key, String label, String type) { columns.add(Map.of("key", key, "label", label, "type", type)); return this; }
        void row(Map<String, Object> r) { rows.add(r); }
        void note(String n) { notes.add(n); }

        static Map<String, Object> row(String k, Object v) { Map<String, Object> m = new LinkedHashMap<>(); m.put(k, v); return m; }

        static void add(Map<String, Object> r, String k, Object v) {
            if (v instanceof Integer i) r.merge(k, (long) i, (a, b) -> ((Number) a).longValue() + ((Number) b).longValue());
            else if (v instanceof BigDecimal d) r.merge(k, d, (a, b) -> ((BigDecimal) a).add((BigDecimal) b));
        }

        void sum(String... keys) {
            for (String k : keys) {
                if (columns.stream().noneMatch(c -> c.get("key").equals(k))) continue;
                Object total = null;
                for (Map<String, Object> r : rows) {
                    Object v = r.get(k);
                    if (v instanceof BigDecimal d) total = total == null ? d : ((BigDecimal) total).add(d);
                    else if (v instanceof Number n) total = total == null ? n.longValue() : ((Number) total).longValue() + n.longValue();
                }
                totals.put(k, total == null ? 0 : total);
            }
        }

        Map<String, Object> out() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", def.key());
            m.put("title", def.title());
            m.put("from", from);
            m.put("to", to);
            m.put("columns", columns);
            m.put("rows", rows);
            m.put("totals", totals);
            m.put("notes", notes);
            return m;
        }
    }
}
