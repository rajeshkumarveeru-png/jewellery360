package com.jewellery360.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A4 invoice designs. Pure Java (no Spring / JPA types) so every design can be rendered and checked on its own.
 *
 * Designs: CLASSIC (gold boxes), MODERN (minimal, no fills), ROYAL (double frame, serif), COMPACT (dense grid for long bills),
 * FORMAL (black and white GST tax invoice with tax summary and amount in words).
 *
 * Text uses the standard PDF fonts (Latin-1 only). {@link #clean(String)} maps the rupee sign, dashes, ellipsis and quotes to
 * plain ASCII; any other character outside Latin-1 (for example Tamil) is shown as "?" - embedding a Unicode font is not done here.
 */
final class InvoiceTemplates {
    private InvoiceTemplates() {}

    static final float W = 595f, H = 842f, M = 36f;
    static final String[] KEYS = {"CLASSIC", "MODERN", "ROYAL", "COMPACT", "FORMAL"};

    /* ------------------------------------------------------------------ data */
    record Line(String name, String design, String tag, String barcode, String purity, BigDecimal gross, BigDecimal stone, BigDecimal net,
                BigDecimal rate, BigDecimal wastage, BigDecimal making, BigDecimal stoneCharge, BigDecimal otherCharge, BigDecimal total) {}

    record Rate(String karat, BigDecimal perGram) {}

    record Data(String brand, String branchName, String branchAddress, String gstin, String phone, String email, String website,
                String title, String subtitle, String invoiceNo, String invoiceDate, String generated,
                String customerName, String customerPhone, String customerGstin, String customerAddress,
                List<Line> lines, BigDecimal goldValue, BigDecimal wastage, BigDecimal making, BigDecimal stoneCharge, BigDecimal other,
                BigDecimal subtotal, String taxMode, BigDecimal taxRate, BigDecimal cgstRate, BigDecimal sgstRate, BigDecimal gst,
                BigDecimal discount, BigDecimal total, String paymentStatus, BigDecimal goldRate, List<Rate> rates,
                String footer, String terms, String dateOnly, String timeOnly) {}

    static String normalize(String key) {
        String k = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        for (String s : KEYS) if (s.equals(k)) return k;
        return "CLASSIC";
    }

    static byte[] render(String template, Data d) {
        String t = normalize(template);
        int per = switch (t) { case "MODERN" -> 8; case "ROYAL" -> 6; case "COMPACT" -> 17; case "FORMAL" -> 9; default -> 6; };
        List<Line> all = d.lines() == null ? List.of() : d.lines();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) per));
        List<Pg> out = new ArrayList<>();
        for (int i = 0; i < pages; i++) {
            Pg p = new Pg();
            List<Line> rows = all.subList(Math.min(all.size(), i * per), Math.min(all.size(), (i + 1) * per));
            boolean last = i == pages - 1;
            switch (t) {
                case "MODERN" -> modern(p, d, rows, i + 1, pages, last, i * per);
                case "ROYAL" -> royal(p, d, rows, i + 1, pages, last, i * per);
                case "COMPACT" -> compact(p, d, rows, i + 1, pages, last, i * per);
                case "FORMAL" -> formal(p, d, rows, i + 1, pages, last, i * per);
                default -> classic(p, d, rows, i + 1, pages, last, i * per);
            }
            out.add(p);
        }
        return write(out);
    }

    /** Three sample items for the design preview in Settings (no sale needed). */
    static Data sample(String brand, String phone, String email, String gstin) {
        String[][] items = {{"22K Temple Gold Bangle", "Temple", "T101", "22K", "10.500", "0.500", "13675", "500"},
                {"Diamond Solitaire Ring", "Solitaire", "T102", "18K", "4.250", "0.250", "11190", "1500"},
                {"Mangalsutra Chain", "Chain", "T103", "22K", "16.000", "0.000", "13675", "800"}};
        List<Line> ls = new ArrayList<>();
        BigDecimal gold = BigDecimal.ZERO, making = BigDecimal.ZERO, sub = BigDecimal.ZERO;
        for (String[] it : items) {
            BigDecimal gross = new BigDecimal(it[4]), stone = new BigDecimal(it[5]), net = gross.subtract(stone), rate = new BigDecimal(it[6]), mk = new BigDecimal(it[7]);
            BigDecimal g = net.multiply(rate), tot = g.add(mk);
            gold = gold.add(g); making = making.add(mk); sub = sub.add(tot);
            ls.add(new Line(it[0], it[1], it[2], "B" + it[2].substring(1), it[3], gross, stone, net, rate, BigDecimal.ZERO, mk, BigDecimal.ZERO, BigDecimal.ZERO, tot));
        }
        BigDecimal gst = sub.multiply(new BigDecimal("3")).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        return new Data(empty(brand) ? "Your Jewellers" : brand, "Main Branch", "12, Gandhi Road, Your City - 600001", empty(gstin) ? "33ABCDE1234F1Z5" : gstin,
                empty(phone) ? "98765 43210" : phone, empty(email) ? "accounts@yourjewellers.in" : email, "", "Tax Invoice", "Transparent jewellery price breakup",
                "INV-SAMPLE-0001", "05 Oct 2026", "05 Oct 2026, 10:30 AM", "Sample Customer", "98765 00000", "", "21, Temple Street, Your City",
                ls, gold, BigDecimal.ZERO, making, BigDecimal.ZERO, BigDecimal.ZERO, sub, "GST", new BigDecimal("3"), new BigDecimal("1.5"), new BigDecimal("1.5"),
                gst, BigDecimal.ZERO, sub.add(gst), "PAID", new BigDecimal("13675"),
                List.of(new Rate("24K", new BigDecimal("14920")), new Rate("22K", new BigDecimal("13675")), new Rate("18K", new BigDecimal("11190"))),
                "", "", "05 Oct 2026", "10:30 AM");
    }

    /* ------------------------------------------------------------------ palette */
    private static final float[] INK = {.10f, .10f, .11f}, MUTE = {.42f, .42f, .44f}, LIGHT = {.86f, .86f, .87f}, WHITE = {1, 1, 1};
    private static final float[] GOLD = {.78f, .62f, .28f}, GOLD_D = {.55f, .35f, .10f}, CREAM = {.985f, .965f, .915f}, CREAM2 = {.97f, .90f, .72f};
    private static final float[] TEAL = {.05f, .45f, .42f}, TEAL_L = {.90f, .96f, .95f};
    private static final float[] MAROON = {.45f, .08f, .13f}, GOLD_R = {.80f, .64f, .30f}, IVORY = {.995f, .985f, .955f};
    private static final float[] NAVY = {.10f, .19f, .36f}, NAVY_L = {.92f, .94f, .97f};

    /* ------------------------------------------------------------------ 1. CLASSIC */
    private static void classic(Pg p, Data d, List<Line> rows, int pageNo, int pages, boolean last, int offset) {
        float y = H - M;
        p.fill(M, y - 104, W - 2 * M, 104, CREAM);
        p.stroke(M, y - 104, W - 2 * M, 104, GOLD, .9f);
        String brand = clean(d.brand()).toUpperCase(Locale.ROOT);
        p.text(M + 16, y - 29, brand.length() > 24 ? 15 : 21, Font.HB, cut(brand, 34), GOLD_D);
        p.text(M + 16, y - 45, 9, Font.HB, cut(d.branchName(), 40), INK);
        p.text(M + 16, y - 58, 7.5f, Font.H, cut(d.branchAddress(), 70), MUTE);
        p.text(M + 16, y - 71, 7.5f, Font.H, "GSTIN: " + dash(d.gstin()) + "   |   Phone: " + dash(d.phone()), MUTE);
        String contact = join("   |   ", d.email(), d.website());
        if (!contact.isEmpty()) p.text(M + 16, y - 84, 7.2f, Font.H, cut(contact, 70), MUTE);
        p.text(338, y - 20, 7, Font.HB, "TODAY'S GOLD RATE", GOLD_D);
        p.text(338, y - 40, 15, Font.HB, d.dateOnly(), INK);
        p.text(338, y - 60, 18, Font.HB, d.timeOnly(), GOLD_D);
        float rx = 338;
        int shown = 0;
        for (Rate r : d.rates()) {
            if (shown++ >= 3) break;
            p.text(rx, y - 79, 9, Font.HB, r.karat(), GOLD_D);
            p.text(rx, y - 92, 7.5f, Font.H, "Rs. " + money(r.perGram()) + "/g", INK);
            rx += 76;
        }
        y -= 138;
        p.text(M, y, 20, Font.HB, cut(d.title(), 30), INK);
        p.text(M, y - 16, 9, Font.H, cut(d.subtitle(), 60), MUTE);
        p.right(W - M, y, 10, Font.HB, d.invoiceNo(), INK);
        p.right(W - M, y - 15, 9, Font.H, "Invoice date: " + d.dateOnly(), MUTE);
        p.right(W - M, y - 28, 8, Font.H, "Generated: " + d.generated(), MUTE);
        y -= 50;
        float bh = 74;
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? M : 309;
            p.fill(x, y - bh, 250, bh, CREAM);
            p.stroke(x, y - bh, 250, bh, GOLD, .6f);
            p.text(x + 12, y - 16, 7, Font.HB, i == 0 ? "BILL TO" : "STORE / BRANCH", GOLD_D);
            p.text(x + 12, y - 34, 12, Font.HB, cut(i == 0 ? d.customerName() : d.branchName(), 30), INK);
            p.text(x + 12, y - 49, 8, Font.H, cut(i == 0 ? "Phone: " + dash(d.customerPhone()) : d.branchAddress(), 48), MUTE);
            p.text(x + 12, y - 62, 8, Font.H, cut(i == 0 ? "GSTIN: " + dash(d.customerGstin()) : "GSTIN: " + dash(d.gstin()) + "  |  Rate: Rs. " + money(d.goldRate()) + "/g", 52), MUTE);
        }
        y -= bh + 14;
        // table
        p.fill(M, y - 24, W - 2 * M, 24, GOLD);
        float[] x = {M + 8, M + 124, M + 194, M + 246, M + 286, M + 326, M + 364, M + 412};  // leaves a clear gap before the right-aligned AMOUNT column
        String[] hd = {"ITEM / DESIGN", "TAG / BARCODE", "PURITY", "GROSS g", "STONE g", "NET g", "RATE / g", "MAKING"};
        for (int i = 0; i < hd.length; i++) p.text(x[i], y - 15, 6.5f, Font.HB, hd[i], WHITE);
        p.right(W - M - 8, y - 15, 6.5f, Font.HB, "AMOUNT", WHITE);
        y -= 24;
        float rh = 38;
        int n = 0;
        for (Line l : rows) {
            if (n++ % 2 == 0) p.fill(M, y - rh, W - 2 * M, rh, CREAM);
            p.text(x[0], y - 15, 8, Font.HB, cut(l.name(), 24), INK);
            p.text(x[0], y - 27, 6.5f, Font.H, cut(l.design(), 28), MUTE);
            p.text(x[1], y - 15, 7.5f, Font.HB, cut(l.tag(), 14), INK);
            p.text(x[1], y - 27, 6.5f, Font.H, cut(l.barcode(), 18), MUTE);
            p.text(x[2], y - 21, 7.5f, Font.H, cut(l.purity(), 9), INK);
            p.text(x[3], y - 21, 7.5f, Font.H, wt(l.gross()), INK);
            p.text(x[4], y - 21, 7.5f, Font.H, wt(l.stone()), INK);
            p.text(x[5], y - 21, 7.5f, Font.HB, wt(l.net()), INK);
            p.text(x[6], y - 21, 7.5f, Font.H, money(l.rate()), INK);
            p.text(x[7], y - 21, 7.5f, Font.H, money(l.making()), INK);
            p.right(W - M - 8, y - 21, 8, Font.HB, money(l.total()), INK);
            p.line(M, y - rh, W - M, y - rh, LIGHT, .5f);
            y -= rh;
        }
        if (rows.isEmpty()) { p.text(M + 8, y - 20, 8, Font.H, "No items on this invoice.", MUTE); y -= 38; }
        if (last) {
            y -= 16;
            float bk = 150;
            p.fill(M, y - bk, 318, bk, CREAM);
            p.stroke(M, y - bk, 318, bk, GOLD, .6f);
            p.text(M + 12, y - 17, 8, Font.HB, "PRICE & TAX BREAKUP", GOLD_D);
            float by = y - 35;
            by = sumRow(p, M + 12, M + 306, by, "Gold value", d.goldValue(), false);
            by = sumRow(p, M + 12, M + 306, by, "Wastage", d.wastage(), false);
            by = sumRow(p, M + 12, M + 306, by, "Making charge", d.making(), false);
            by = sumRow(p, M + 12, M + 306, by, "Stone charge", d.stoneCharge(), false);
            by = sumRow(p, M + 12, M + 306, by, "Other charges", d.other(), false);
            by = sumRow(p, M + 12, M + 306, by, "Subtotal", d.subtotal(), true);
            taxRows(p, d, M + 12, M + 306, by);
            float tx = 372;
            p.fill(tx, y - bk, W - M - tx, bk, CREAM2);
            p.stroke(tx, y - bk, W - M - tx, bk, GOLD, .9f);
            p.text(tx + 14, y - 18, 8, Font.HB, "AMOUNT PAYABLE", GOLD_D);
            p.text(tx + 14, y - 50, 22, Font.HB, "Rs. " + money(d.total()), GOLD_D);
            p.text(tx + 14, y - 76, 8, Font.H, "Subtotal: Rs. " + money(d.subtotal()), INK);
            p.text(tx + 14, y - 91, 8, Font.H, "Tax: Rs. " + money(taxTotal(d)), INK);
            p.text(tx + 14, y - 106, 8, Font.H, "Discount: Rs. " + money(d.discount()), INK);
            p.text(tx + 14, y - 121, 8, Font.H, "Payment: " + dash(d.paymentStatus()), INK);
            p.text(tx + 14, y - 136, 6.5f, Font.H, "Gold rate captured at billing time", MUTE);
            y -= bk + 16;
            p.text(M, y, 7.5f, Font.HB, "CUSTOMER ACKNOWLEDGEMENT", GOLD_D);
            p.line(M, y - 30, M + 220, y - 30, MUTE, .6f);
            p.text(M, y - 41, 7, Font.H, "Customer signature", MUTE);
            p.line(W - M - 200, y - 30, W - M, y - 30, MUTE, .6f);
            p.text(W - M - 200, y - 41, 7, Font.H, "Authorised signatory", MUTE);
        }
        footer(p, d, pageNo, pages, GOLD_D);
    }

    /* ------------------------------------------------------------------ 2. MODERN */
    private static void modern(Pg p, Data d, List<Line> rows, int pageNo, int pages, boolean last, int offset) {
        float y = H - M - 6;
        p.fill(M, y + 4, 38, 3.5f, TEAL);
        p.text(M, y - 24, 22, Font.HB, cut(clean(d.brand()), 26), INK);
        p.text(M, y - 40, 8.5f, Font.H, cut(d.branchName() + (empty(d.branchAddress()) ? "" : "  -  " + d.branchAddress()), 78), MUTE);
        p.text(M, y - 52, 8, Font.H, cut(join("   |   ", empty(d.gstin()) ? "" : "GSTIN " + d.gstin(), d.phone(), d.email(), d.website()), 90), MUTE);
        p.right(W - M, y - 20, 26, Font.H, "INVOICE", LIGHT);
        p.right(W - M, y - 38, 10.5f, Font.HB, d.invoiceNo(), INK);
        p.right(W - M, y - 51, 8.5f, Font.H, d.dateOnly() + "  " + d.timeOnly(), MUTE);
        y -= 74;
        p.line(M, y, W - M, y, LIGHT, .8f);
        y -= 22;
        p.text(M, y, 7, Font.HB, "BILLED TO", TEAL);
        p.text(M, y - 15, 13, Font.HB, cut(d.customerName(), 34), INK);
        p.text(M, y - 28, 8.5f, Font.H, "Phone: " + dash(d.customerPhone()), MUTE);
        p.text(M, y - 40, 8.5f, Font.H, "GSTIN: " + dash(d.customerGstin()), MUTE);
        p.text(310, y, 7, Font.HB, "DETAILS", TEAL);
        p.text(310, y - 15, 9, Font.H, "Gold rate", MUTE);
        p.right(W - M, y - 15, 9, Font.HB, "Rs. " + money(d.goldRate()) + " / g", INK);
        p.text(310, y - 28, 9, Font.H, "Payment", MUTE);
        p.right(W - M, y - 28, 9, Font.HB, dash(d.paymentStatus()), INK);
        p.text(310, y - 41, 9, Font.H, "Document", MUTE);
        p.right(W - M, y - 41, 9, Font.HB, cut(d.title(), 26), INK);
        y -= 66;
        float[] x = {M, M + 20, M + 222, M + 276, M + 322, M + 366, M + 406};
        String[] hd = {"#", "ITEM", "PURITY", "GROSS g", "STONE g", "NET g", "RATE / g"};
        for (int i = 0; i < hd.length; i++) p.text(x[i], y, 6.8f, Font.HB, hd[i], MUTE);
        p.right(W - M, y, 6.8f, Font.HB, "AMOUNT", MUTE);
        p.line(M, y - 7, W - M, y - 7, INK, 1f);
        y -= 7;
        float rh = 36;
        int n = offset;
        for (Line l : rows) {
            n++;
            p.text(x[0], y - 17, 8, Font.H, String.valueOf(n), MUTE);
            p.text(x[1], y - 16, 8.5f, Font.HB, cut(l.name(), 34), INK);
            p.text(x[1], y - 27, 7, Font.H, cut(join("  |  ", "Tag " + l.tag(), l.design()), 52), MUTE);
            p.text(x[2], y - 21, 8, Font.H, cut(l.purity(), 9), INK);
            p.text(x[3], y - 21, 8, Font.H, wt(l.gross()), INK);
            p.text(x[4], y - 21, 8, Font.H, wt(l.stone()), INK);
            p.text(x[5], y - 21, 8, Font.HB, wt(l.net()), INK);
            p.text(x[6], y - 21, 8, Font.H, money(l.rate()), INK);
            p.right(W - M, y - 21, 8.5f, Font.HB, money(l.total()), INK);
            p.line(M, y - rh, W - M, y - rh, LIGHT, .5f);
            y -= rh;
        }
        if (rows.isEmpty()) { p.text(M, y - 22, 8.5f, Font.H, "No items on this invoice.", MUTE); y -= 36; }
        if (last) {
            y -= 22;
            float lx = 330, rx = W - M;
            y = kv(p, lx, rx, y, "Gold value", d.goldValue(), MUTE, INK, 9);
            y = kv(p, lx, rx, y, "Wastage", d.wastage(), MUTE, INK, 9);
            y = kv(p, lx, rx, y, "Making + stone + other", d.making().add(d.stoneCharge()).add(d.other()), MUTE, INK, 9);
            y = kv(p, lx, rx, y, "Subtotal", d.subtotal(), MUTE, INK, 9);
            y = taxKv(p, d, lx, rx, y, 9);
            if (d.discount().signum() > 0) y = kv(p, lx, rx, y, "Discount", d.discount().negate(), MUTE, INK, 9);
            p.line(lx, y + 5, rx, y + 5, INK, 1f);
            p.text(lx, y - 14, 10, Font.HB, "TOTAL", INK);
            p.right(rx, y - 16, 18, Font.HB, "Rs. " + money(d.total()), TEAL);
            p.text(M, y - 14, 7, Font.HB, "AMOUNT IN WORDS", TEAL);
            for (String s : wrap(words(d.total()), 56, 2)) { p.text(M, y - 27, 8.5f, Font.H, s, INK); y -= 11; }
            y -= 40;
            p.line(W - M - 180, y, W - M, y, MUTE, .6f);
            p.right(W - M, y - 11, 7.5f, Font.H, "Authorised signatory", MUTE);
        }
        footer(p, d, pageNo, pages, TEAL);
    }

    /* ------------------------------------------------------------------ 3. ROYAL */
    private static void royal(Pg p, Data d, List<Line> rows, int pageNo, int pages, boolean last, int offset) {
        p.stroke(22, 22, W - 44, H - 44, MAROON, 1.6f);
        p.stroke(27, 27, W - 54, H - 54, GOLD_R, .6f);
        float cx = W / 2, y = H - 56;
        p.center(cx, y - 14, 26, Font.TB, cut(clean(d.brand()).toUpperCase(Locale.ROOT), 30), MAROON);
        p.diamond(cx - 70, y - 28, 3, GOLD_R); p.diamond(cx, y - 28, 4, MAROON); p.diamond(cx + 70, y - 28, 3, GOLD_R);
        p.line(cx - 160, y - 28, cx - 78, y - 28, GOLD_R, .8f); p.line(cx + 78, y - 28, cx + 160, y - 28, GOLD_R, .8f);
        p.center(cx, y - 44, 10, Font.TB, cut(d.branchName(), 50), INK);
        p.center(cx, y - 57, 8.5f, Font.T, cut(d.branchAddress(), 90), MUTE);
        p.center(cx, y - 69, 8.5f, Font.T, cut(join("   |   ", empty(d.gstin()) ? "" : "GSTIN: " + d.gstin(), empty(d.phone()) ? "" : "Phone: " + d.phone(), d.email()), 100), MUTE);
        y -= 92;
        p.line(46, y, cx - 80, y, MAROON, 1f); p.line(cx + 80, y, W - 46, y, MAROON, 1f);
        p.center(cx, y - 4, 13, Font.TB, cut(clean(d.title()).toUpperCase(Locale.ROOT), 24), MAROON);
        y -= 26;
        p.text(46, y, 9, Font.TB, "Invoice No:", INK); p.text(104, y, 9, Font.T, d.invoiceNo(), INK);
        String stamp = d.dateOnly() + "   " + d.timeOnly();
        p.right(W - 46, y, 9, Font.T, stamp, INK);
        p.right(W - 46 - width(stamp, Font.T, 9) - 6, y, 9, Font.TB, "Date:", INK);
        y -= 12;
        float bw = (W - 92 - 14) / 2, bh = 70;
        for (int i = 0; i < 2; i++) {
            float bx = 46 + i * (bw + 14);
            p.stroke(bx, y - bh, bw, bh, GOLD_R, .8f);
            p.fill(bx, y - 15, bw, 15, MAROON);
            p.text(bx + 8, y - 11, 8, Font.TB, i == 0 ? "BILLED TO" : "PAYMENT & RATE", GOLD_R);
            if (i == 0) {
                p.text(bx + 8, y - 31, 11.5f, Font.TB, cut(d.customerName(), 28), INK);
                p.text(bx + 8, y - 45, 8.5f, Font.T, "Phone: " + dash(d.customerPhone()), MUTE);
                p.text(bx + 8, y - 57, 8.5f, Font.T, "GSTIN: " + dash(d.customerGstin()), MUTE);
            } else {
                p.text(bx + 8, y - 31, 9, Font.T, "Gold rate captured", MUTE);
                p.text(bx + 8, y - 45, 12, Font.TB, "Rs. " + money(d.goldRate()) + " / g", INK);
                p.text(bx + 8, y - 58, 8.5f, Font.T, "Payment status: " + dash(d.paymentStatus()), MUTE);
            }
        }
        y -= bh + 14;
        float[] x = {50, 72, 232, 292, 336, 380, 424};
        p.fill(46, y - 22, W - 92, 22, MAROON);
        String[] hd = {"#", "DESCRIPTION", "PURITY", "GROSS g", "STONE g", "NET g", "RATE / g"};
        for (int i = 0; i < hd.length; i++) p.text(x[i], y - 14, 7, Font.TB, hd[i], GOLD_R);
        p.right(W - 52, y - 14, 7, Font.TB, "AMOUNT", GOLD_R);
        y -= 22;
        float rh = 40;
        int n = offset;
        for (Line l : rows) {
            n++;
            p.text(x[0], y - 22, 8.5f, Font.T, String.valueOf(n), MUTE);
            p.text(x[1], y - 17, 9, Font.TB, cut(l.name(), 32), INK);
            p.text(x[1], y - 29, 7.5f, Font.T, cut(join("  |  ", "Tag " + l.tag(), l.design()), 50), MUTE);
            p.text(x[2], y - 23, 8.5f, Font.T, cut(l.purity(), 9), INK);
            p.text(x[3], y - 23, 8.5f, Font.T, wt(l.gross()), INK);
            p.text(x[4], y - 23, 8.5f, Font.T, wt(l.stone()), INK);
            p.text(x[5], y - 23, 8.5f, Font.TB, wt(l.net()), INK);
            p.text(x[6], y - 23, 8.5f, Font.T, money(l.rate()), INK);
            p.right(W - 52, y - 23, 9, Font.TB, money(l.total()), INK);
            p.line(46, y - rh, W - 46, y - rh, GOLD_R, .5f);
            y -= rh;
        }
        if (rows.isEmpty()) { p.text(50, y - 22, 9, Font.T, "No items on this invoice.", MUTE); y -= 40; }
        if (last) {
            y -= 14;
            float bk = 128;
            p.stroke(46, y - bk, 292, bk, GOLD_R, .8f);
            p.text(56, y - 16, 8, Font.TB, "BREAKUP", MAROON);
            float by = y - 32;
            by = sumRowSerif(p, 56, 328, by, "Gold value", d.goldValue(), false);
            by = sumRowSerif(p, 56, 328, by, "Wastage", d.wastage(), false);
            by = sumRowSerif(p, 56, 328, by, "Making + stone + other", d.making().add(d.stoneCharge()).add(d.other()), false);
            by = sumRowSerif(p, 56, 328, by, "Subtotal", d.subtotal(), true);
            for (String[] r : taxLines(d)) { p.text(56, by, 8.5f, Font.T, r[0], INK); p.right(328, by, 8.5f, Font.T, "Rs. " + r[1], INK); by -= 13; }
            float tx = 352;
            p.fill(tx, y - bk, W - 46 - tx, bk, IVORY);
            p.stroke(tx, y - bk, W - 46 - tx, bk, MAROON, 1.3f);
            p.text(tx + 12, y - 18, 8, Font.TB, "AMOUNT PAYABLE", MAROON);
            p.text(tx + 12, y - 48, 21, Font.TB, "Rs. " + money(d.total()), MAROON);
            if (d.discount().signum() > 0) p.text(tx + 12, y - 70, 8.5f, Font.T, "After discount of Rs. " + money(d.discount()), MUTE);
            int wy = 88;
            for (String s : wrap(words(d.total()), 34, 3)) { p.text(tx + 12, y - wy, 8, Font.TI, s, INK); wy += 10; }
            y -= bk + 14;
            p.line(50, y - 24, 230, y - 24, MUTE, .6f);
            p.text(50, y - 35, 7.5f, Font.T, "Customer signature", MUTE);
            p.line(W - 230, y - 24, W - 50, y - 24, MUTE, .6f);
            p.right(W - 50, y - 35, 7.5f, Font.T, "Authorised signatory", MUTE);
        }
        p.center(cx, 46, 7.5f, Font.TI, cut(footerText(d), 120), MUTE);
        p.right(W - 50, 36, 7, Font.T, "Page " + pageNo + " of " + pages, MUTE);
    }

    /* ------------------------------------------------------------------ 4. COMPACT */
    private static void compact(Pg p, Data d, List<Line> rows, int pageNo, int pages, boolean last, int offset) {
        float y = H - M;
        p.fill(M, y - 46, W - 2 * M, 46, NAVY);
        p.text(M + 12, y - 20, 15, Font.HB, cut(clean(d.brand()).toUpperCase(Locale.ROOT), 32), WHITE);
        p.text(M + 12, y - 35, 7.5f, Font.H, cut(join("  |  ", d.branchName(), empty(d.gstin()) ? "" : "GSTIN " + d.gstin(), d.phone()), 90), WHITE);
        p.right(W - M - 12, y - 19, 11, Font.HB, d.invoiceNo(), WHITE);
        p.right(W - M - 12, y - 34, 8, Font.H, d.dateOnly() + "  " + d.timeOnly(), WHITE);
        y -= 46;
        p.fill(M, y - 28, W - 2 * M, 28, NAVY_L);
        p.text(M + 10, y - 12, 6.5f, Font.HB, "CUSTOMER", NAVY);
        p.text(M + 10, y - 23, 9, Font.HB, cut(d.customerName(), 26), INK);
        p.text(M + 180, y - 12, 6.5f, Font.HB, "PHONE / GSTIN", NAVY);
        p.text(M + 180, y - 23, 8, Font.H, cut(dash(d.customerPhone()) + "  |  " + dash(d.customerGstin()), 40), INK);
        p.text(M + 400, y - 12, 6.5f, Font.HB, "GOLD RATE", NAVY);
        p.text(M + 400, y - 23, 8.5f, Font.HB, "Rs. " + money(d.goldRate()) + "/g", INK);
        y -= 40;
        float[] cw = {18, 126, 52, 36, 36, 36, 36, 46, 44, 40, 53};
        String[] hd = {"#", "ITEM", "TAG", "PURITY", "GROSS", "STONE", "NET", "RATE", "WASTAGE", "MAKING", "AMOUNT"};
        float tw = W - 2 * M, x0 = M;
        p.fill(x0, y - 18, tw, 18, NAVY);
        float cx = x0;
        float[] xs = new float[cw.length];
        float sum = 0; for (float f : cw) sum += f;
        float k = tw / sum;
        for (int i = 0; i < cw.length; i++) { xs[i] = cx; float w = cw[i] * k; if (i >= 4) p.right(cx + w - 3, y - 12, 6.3f, Font.HB, hd[i], WHITE); else p.text(cx + 3, y - 12, 6.3f, Font.HB, hd[i], WHITE); cx += w; }
        y -= 18;
        float rh = 22;
        int n = offset;
        for (Line l : rows) {
            n++;
            if (n % 2 == 0) p.fill(x0, y - rh, tw, rh, NAVY_L);
            float[] e = new float[cw.length + 1];
            e[0] = x0; for (int i = 0; i < cw.length; i++) e[i + 1] = e[i] + cw[i] * k;
            p.text(e[0] + 3, y - 14, 7, Font.H, String.valueOf(n), MUTE);
            p.text(e[1] + 3, y - 10, 7.4f, Font.HB, cut(l.name(), 30), INK);
            p.text(e[1] + 3, y - 18, 6, Font.H, cut(l.design(), 36), MUTE);
            p.text(e[2] + 3, y - 14, 7, Font.H, cut(l.tag(), 9), INK);
            p.text(e[3] + 3, y - 14, 7, Font.H, cut(l.purity(), 6), INK);
            p.right(e[5] - 3, y - 14, 7, Font.H, wt(l.gross()), INK);
            p.right(e[6] - 3, y - 14, 7, Font.H, wt(l.stone()), INK);
            p.right(e[7] - 3, y - 14, 7, Font.HB, wt(l.net()), INK);
            p.right(e[8] - 3, y - 14, 7, Font.H, money(l.rate()), INK);
            p.right(e[9] - 3, y - 14, 7, Font.H, money(l.wastage()), INK);
            p.right(e[10] - 3, y - 14, 7, Font.H, money(l.making()), INK);
            p.right(e[11] - 3, y - 14, 7.4f, Font.HB, money(l.total()), INK);
            p.line(x0, y - rh, x0 + tw, y - rh, LIGHT, .4f);
            for (int i = 1; i < e.length - 1; i++) p.line(e[i], y, e[i], y - rh, LIGHT, .3f);
            y -= rh;
        }
        p.stroke(x0, y, tw, 0.01f, NAVY, .8f);
        if (rows.isEmpty()) { p.text(M + 4, y - 14, 8, Font.H, "No items on this invoice.", MUTE); y -= 22; }
        if (last) {
            y -= 14;
            float lx = W - M - 230;
            p.fill(lx, y - 112, 230, 112, NAVY_L);
            p.stroke(lx, y - 112, 230, 112, NAVY, .7f);
            float by = y - 15;
            by = kv(p, lx + 10, W - M - 10, by, "Gold value", d.goldValue(), MUTE, INK, 8);
            by = kv(p, lx + 10, W - M - 10, by, "Wastage + making + other", d.wastage().add(d.making()).add(d.stoneCharge()).add(d.other()), MUTE, INK, 8);
            by = kv(p, lx + 10, W - M - 10, by, "Subtotal", d.subtotal(), MUTE, INK, 8);
            by = taxKv(p, d, lx + 10, W - M - 10, by, 8);
            if (d.discount().signum() > 0) by = kv(p, lx + 10, W - M - 10, by, "Discount", d.discount().negate(), MUTE, INK, 8);
            p.fill(lx, y - 112, 230, 24, NAVY);
            p.text(lx + 10, y - 104, 9, Font.HB, "TOTAL", WHITE);
            p.right(W - M - 10, y - 105, 12, Font.HB, "Rs. " + money(d.total()), WHITE);
            p.text(M, y - 14, 6.5f, Font.HB, "AMOUNT IN WORDS", NAVY);
            int yy = 26;
            for (String s : wrap(words(d.total()), 62, 2)) { p.text(M, y - yy, 8, Font.H, s, INK); yy += 10; }
            p.text(M, y - 62, 6.5f, Font.HB, "PAYMENT", NAVY);
            p.text(M, y - 73, 8, Font.H, dash(d.paymentStatus()), INK);
            if (!empty(d.terms())) { p.text(M, y - 92, 6.5f, Font.HB, "TERMS", NAVY); int ty = 103; for (String s : wrap(d.terms(), 70, 2)) { p.text(M, y - ty, 7, Font.H, s, MUTE); ty += 9; } }
            p.line(W - M - 160, y - 150, W - M, y - 150, MUTE, .6f);
            p.right(W - M, y - 160, 7, Font.H, "Authorised signatory", MUTE);
        }
        footer(p, d, pageNo, pages, NAVY);
    }

    /* ------------------------------------------------------------------ 5. FORMAL (GST tax invoice) */
    private static void formal(Pg p, Data d, List<Line> rows, int pageNo, int pages, boolean last, int offset) {
        float x0 = M, tw = W - 2 * M, y = H - M;
        p.center(W / 2, y - 12, 15, Font.HB, "TAX INVOICE", INK);
        p.center(W / 2, y - 24, 7.5f, Font.H, "(Original for recipient)", MUTE);
        y -= 34;
        p.stroke(x0, y - 74, tw, 74, INK, .9f);
        p.line(x0 + 300, y, x0 + 300, y - 74, INK, .6f);
        p.text(x0 + 8, y - 14, 13, Font.HB, cut(clean(d.brand()).toUpperCase(Locale.ROOT), 30), INK);
        p.text(x0 + 8, y - 27, 8, Font.HB, cut(d.branchName(), 50), INK);
        p.text(x0 + 8, y - 38, 7.8f, Font.H, cut(d.branchAddress(), 62), MUTE);
        p.text(x0 + 8, y - 50, 7.8f, Font.H, "GSTIN: " + dash(d.gstin()), INK);
        p.text(x0 + 8, y - 61, 7.8f, Font.H, cut(join("   |   ", empty(d.phone()) ? "" : "Phone: " + d.phone(), d.email()), 66), MUTE);
        String[][] meta = {{"Invoice No.", d.invoiceNo()}, {"Date", d.dateOnly()}, {"Time", d.timeOnly()}, {"Gold rate / g", "Rs. " + money(d.goldRate())}};
        for (int i = 0; i < meta.length; i++) { p.text(x0 + 308, y - 13 - i * 15.5f, 7.5f, Font.H, meta[i][0], MUTE); p.text(x0 + 372, y - 13 - i * 15.5f, 8.2f, Font.HB, cut(meta[i][1], 28), INK); }
        y -= 74;
        p.stroke(x0, y - 56, tw, 56, INK, .9f);
        p.line(x0 + 300, y, x0 + 300, y - 56, INK, .6f);
        p.text(x0 + 8, y - 12, 7, Font.HB, "BUYER (BILL TO)", MUTE);
        p.text(x0 + 8, y - 26, 11, Font.HB, cut(d.customerName(), 34), INK);
        p.text(x0 + 8, y - 38, 8, Font.H, cut(empty(d.customerAddress()) ? "Address: -" : d.customerAddress(), 70), MUTE);
        p.text(x0 + 8, y - 49, 8, Font.H, "Phone: " + dash(d.customerPhone()) + "    GSTIN: " + dash(d.customerGstin()), INK);
        p.text(x0 + 308, y - 12, 7, Font.HB, "PAYMENT STATUS", MUTE);
        p.text(x0 + 308, y - 26, 11, Font.HB, dash(d.paymentStatus()), INK);
        p.text(x0 + 308, y - 40, 7.5f, Font.H, cut(d.title(), 40), MUTE);
        y -= 56;
        float[] cw = {22, 150, 50, 40, 40, 42, 46, 44, 50};
        String[] hd = {"Sl", "Description of goods", "HSN", "Purity", "Gross g", "Net g", "Rate / g", "Making", "Taxable value"};
        float sum = 0; for (float f : cw) sum += f;
        float k = tw / sum;
        float[] e = new float[cw.length + 1];
        e[0] = x0; for (int i = 0; i < cw.length; i++) e[i + 1] = e[i] + cw[i] * k;
        p.fill(x0, y - 20, tw, 20, LIGHT);
        p.stroke(x0, y - 20, tw, 20, INK, .8f);
        for (int i = 0; i < cw.length; i++) { if (i >= 4) p.right(e[i + 1] - 3, y - 13, 6.5f, Font.HB, hd[i], INK); else p.text(e[i] + 3, y - 13, 6.5f, Font.HB, hd[i], INK); }
        y -= 20;
        float rh = 30;
        int n = offset;
        for (Line l : rows) {
            n++;
            p.text(e[0] + 4, y - 18, 8, Font.H, String.valueOf(n), INK);
            p.text(e[1] + 3, y - 13, 8, Font.HB, cut(l.name(), 31), INK);
            p.text(e[1] + 3, y - 24, 6.5f, Font.H, cut(join("  |  ", "Tag " + l.tag(), l.design()), 42), MUTE);
            p.text(e[2] + 3, y - 18, 7.5f, Font.H, "7113", INK);
            p.text(e[3] + 3, y - 18, 7.5f, Font.H, cut(l.purity(), 6), INK);
            p.right(e[5] - 3, y - 18, 7.5f, Font.H, wt(l.gross()), INK);
            p.right(e[6] - 3, y - 18, 7.5f, Font.HB, wt(l.net()), INK);
            p.right(e[7] - 3, y - 18, 7.5f, Font.H, money(l.rate()), INK);
            p.right(e[8] - 3, y - 18, 7.5f, Font.H, money(l.making()), INK);
            p.right(e[9] - 3, y - 18, 7.8f, Font.HB, money(l.total()), INK);
            y -= rh;
        }
        float tableTop = y + rows.size() * rh + 20;
        float tableH = Math.max(rows.size() * rh, 30);
        if (rows.isEmpty()) { p.text(e[1] + 3, y - 18, 8, Font.H, "No items on this invoice.", MUTE); y -= 30; }
        p.stroke(x0, y, tw, tableH, INK, .8f);
        for (int i = 1; i < e.length - 1; i++) p.line(e[i], tableTop, e[i], tableTop - 20 - tableH, INK, .6f);
        if (last) {
            y -= 12;
            p.stroke(x0, y - 78, 300, 78, INK, .8f);
            p.text(x0 + 8, y - 12, 7, Font.HB, "AMOUNT IN WORDS", MUTE);
            int yy = 25;
            for (String s : wrap(words(d.total()), 58, 3)) { p.text(x0 + 8, y - yy, 8.5f, Font.HB, s, INK); yy += 11; }
            p.text(x0 + 8, y - 62, 6.8f, Font.H, "Gold rate captured at billing time.", MUTE);
            float tx = x0 + 300;
            p.stroke(tx, y - 78, tw - 300, 78, INK, .8f);
            float by = y - 14;
            by = kv(p, tx + 8, W - M - 8, by, "Taxable value", d.subtotal(), MUTE, INK, 8.2f);
            by = taxKvFormal(p, d, tx + 8, W - M - 8, by);
            if (d.discount().signum() > 0) by = kv(p, tx + 8, W - M - 8, by, "Discount", d.discount().negate(), MUTE, INK, 8.2f);
            p.fill(tx, y - 78, tw - 300, 20, INK);
            p.text(tx + 8, y - 72, 9, Font.HB, "TOTAL", WHITE);
            p.right(W - M - 8, y - 72, 11, Font.HB, "Rs. " + money(d.total()), WHITE);
            y -= 90;
            p.stroke(x0, y - 70, tw, 70, INK, .8f);
            p.text(x0 + 8, y - 12, 7, Font.HB, "DECLARATION", MUTE);
            int ty = 24;
            for (String s : wrap("We declare that this invoice shows the actual price of the goods described and that all particulars are true and correct. " + nz(d.terms()), 78, 3)) { p.text(x0 + 8, y - ty, 7.2f, Font.H, s, MUTE); ty += 10; }
            p.right(W - M - 8, y - 14, 8, Font.HB, "for " + cut(clean(d.brand()), 30), INK);
            p.line(W - M - 170, y - 54, W - M - 8, y - 54, INK, .6f);
            p.right(W - M - 8, y - 64, 7.2f, Font.H, "Authorised signatory", MUTE);
        }
        footer(p, d, pageNo, pages, INK);
    }

    /* ------------------------------------------------------------------ shared drawing helpers */
    private static float sumRow(Pg p, float lx, float rx, float y, String label, BigDecimal v, boolean bold) {
        p.text(lx, y, 7.8f, bold ? Font.HB : Font.H, label, INK);
        p.right(rx, y, 7.8f, bold ? Font.HB : Font.H, "Rs. " + money(v), INK);
        return y - (bold ? 17 : 15);
    }

    private static float sumRowSerif(Pg p, float lx, float rx, float y, String label, BigDecimal v, boolean bold) {
        p.text(lx, y, 8.5f, bold ? Font.TB : Font.T, label, INK);
        p.right(rx, y, 8.5f, bold ? Font.TB : Font.T, "Rs. " + money(v), INK);
        return y - 14;
    }

    private static float kv(Pg p, float lx, float rx, float y, String label, BigDecimal v, float[] lc, float[] vc, float size) {
        p.text(lx, y, size, Font.H, label, lc);
        p.right(rx, y, size, Font.HB, (v.signum() < 0 ? "- " : "") + "Rs. " + money(v.abs()), vc);
        return y - (size + 6);
    }

    /** tax lines as {label, amount} - respects GST / CGST+SGST / no tax exactly as stored on the sale */
    private static List<String[]> taxLines(Data d) {
        List<String[]> out = new ArrayList<>();
        String mode = d.taxMode() == null ? "GST" : d.taxMode();
        if ("NONE".equalsIgnoreCase(mode) || d.gst().signum() == 0 && d.taxRate().signum() == 0 && d.cgstRate().signum() == 0) {
            out.add(new String[]{"Tax", money(BigDecimal.ZERO)});
        } else if ("CGST_SGST".equalsIgnoreCase(mode)) {
            BigDecimal split = d.cgstRate().add(d.sgstRate());
            BigDecimal cgst = split.signum() == 0 ? BigDecimal.ZERO : d.gst().multiply(d.cgstRate()).divide(split, 2, RoundingMode.HALF_UP);
            out.add(new String[]{"CGST @ " + money(d.cgstRate()) + "%", money(cgst)});
            out.add(new String[]{"SGST @ " + money(d.sgstRate()) + "%", money(d.gst().subtract(cgst))});
        } else {
            out.add(new String[]{"GST @ " + money(d.taxRate()) + "%", money(d.gst())});
        }
        return out;
    }

    private static float taxRows(Pg p, Data d, float lx, float rx, float y) {
        for (String[] r : taxLines(d)) { p.text(lx, y, 7.8f, Font.H, r[0], INK); p.right(rx, y, 7.8f, Font.H, "Rs. " + r[1], INK); y -= 14; }
        return y;
    }

    private static float taxKv(Pg p, Data d, float lx, float rx, float y, float size) {
        for (String[] r : taxLines(d)) { p.text(lx, y, size, Font.H, r[0], MUTE); p.right(rx, y, size, Font.HB, "Rs. " + r[1], INK); y -= size + 6; }
        return y;
    }

    private static float taxKvFormal(Pg p, Data d, float lx, float rx, float y) { return taxKv(p, d, lx, rx, y, 8.2f); }

    private static BigDecimal taxTotal(Data d) { return d.gst(); }

    private static void footer(Pg p, Data d, int pageNo, int pages, float[] color) {
        p.line(M, 40, W - M, 40, LIGHT, .6f);
        p.text(M, 28, 7, Font.H, cut(footerText(d), 120), MUTE);
        p.right(W - M, 28, 7, Font.HB, "Page " + pageNo + " of " + pages, color);
    }

    private static String footerText(Data d) {
        String f = empty(d.footer()) ? "Thank you for choosing " + clean(d.brand()) + ". Please retain this invoice for exchange, service and warranty." : d.footer();
        return f;
    }

    /* ------------------------------------------------------------------ text + number helpers */
    /** Indian digit grouping (12,34,567.89) done by hand: the JVM's en-IN locale data is not always installed. */
    static String money(BigDecimal n) {
        BigDecimal v = (n == null ? BigDecimal.ZERO : n).setScale(2, RoundingMode.HALF_UP);
        boolean neg = v.signum() < 0;
        String[] parts = v.abs().toPlainString().split("\\.");
        String ip = parts[0];
        String grouped;
        if (ip.length() <= 3) {
            grouped = ip;
        } else {
            String tail = ip.substring(ip.length() - 3);
            String head = ip.substring(0, ip.length() - 3);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) b.append(',');
                b.append(head.charAt(i));
            }
            grouped = b + "," + tail;
        }
        return (neg ? "-" : "") + grouped + "." + (parts.length > 1 ? parts[1] : "00");
    }

    private static String wt(BigDecimal n) { return (n == null ? BigDecimal.ZERO : n).setScale(3, RoundingMode.HALF_UP).toPlainString(); }
    private static boolean empty(String s) { return s == null || s.isBlank(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String dash(String s) { return empty(s) ? "-" : s; }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String s : parts) if (!empty(s)) { if (b.length() > 0) b.append(sep); b.append(s.trim()); }
        return b.toString();
    }

    static String cut(String s, int n) {
        String v = clean(s);
        return v.length() <= n ? v : v.substring(0, Math.max(0, n - 3)) + "...";
    }

    /** Standard PDF fonts are Latin-1: map the common typographic characters to ASCII and everything else outside Latin-1 to '?'. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\u20b9' -> b.append("Rs.");
                case '\u2014', '\u2013', '\u2212' -> b.append('-');
                case '\u2026' -> b.append("...");
                case '\u2018', '\u2019' -> b.append('\'');
                case '\u201c', '\u201d' -> b.append('"');
                case '\u00a0' -> b.append(' ');
                case '\r', '\n', '\t' -> b.append(' ');
                default -> b.append(c < 256 ? c : '?');
            }
        }
        return b.toString();
    }

    static List<String> wrap(String text, int cols, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : clean(text).trim().split("\\s+")) {
            if (cur.length() > 0 && cur.length() + 1 + w.length() > cols) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.size() > maxLines) { List<String> cut = new ArrayList<>(out.subList(0, maxLines)); cut.set(maxLines - 1, cut(cut.get(maxLines - 1) + " ...", cols)); return cut; }
        return out;
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
            "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private static String below100(int n) { return n < 20 ? ONES[n] : TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]); }

    private static String below1000(int n) {
        String s = n >= 100 ? ONES[n / 100] + " Hundred" + (n % 100 == 0 ? "" : " ") : "";
        return s + (n % 100 == 0 ? "" : below100(n % 100));
    }

    /** Indian grouping: Crore, Lakh, Thousand. Example: 140852.50 -> "Rupees One Lakh Forty Thousand Eight Hundred Fifty Two and Fifty Paise Only". */
    static String words(BigDecimal amount) {
        BigDecimal a = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP).abs();
        long rupees = a.toBigInteger().longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        if (rupees == 0 && paise == 0) return "Rupees Zero Only";
        StringBuilder b = new StringBuilder();
        long crore = rupees / 10000000L; rupees %= 10000000L;
        long lakh = rupees / 100000L; rupees %= 100000L;
        long thousand = rupees / 1000L; rupees %= 1000L;
        if (crore > 0) b.append(crore >= 1000 ? String.valueOf(crore) : below1000((int) crore)).append(" Crore ");
        if (lakh > 0) b.append(below100((int) lakh)).append(" Lakh ");
        if (thousand > 0) b.append(below100((int) thousand)).append(" Thousand ");
        if (rupees > 0) b.append(below1000((int) rupees));
        String r = b.toString().trim();
        String out = r.isEmpty() ? "" : "Rupees " + r;
        if (paise > 0) out += (out.isEmpty() ? "" : " and ") + below100(paise) + " Paise";
        return out + " Only";
    }

    /* ------------------------------------------------------------------ PDF writer */
    enum Font {
        H("F1", "Helvetica", 1.00f), HB("F2", "Helvetica-Bold", 1.00f), T("F3", "Times-Roman", .88f), TB("F4", "Times-Bold", .92f),
        TI("F5", "Times-Italic", .86f), C("F6", "Courier", 1f), CB("F7", "Courier-Bold", 1f);
        final String res, base; final float scale;
        Font(String res, String base, float scale) { this.res = res; this.base = base; this.scale = scale; }
    }

    private static final int[] HW = {278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556,
            556, 556, 278, 278, 584, 584, 584, 556, 1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722,
            667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556, 333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833,
            556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};

    static float width(String s, Font f, float size) {
        float w = 0;
        for (char c : clean(s).toCharArray()) {
            if (f == Font.C || f == Font.CB) { w += 600; continue; }
            int i = c - 32;
            int base = i >= 0 && i < HW.length ? HW[i] : 556;
            w += base * (f == Font.HB ? 1.06f : 1f);
        }
        return w / 1000f * size * f.scale;
    }

    static final class Pg {
        final StringBuilder c = new StringBuilder();

        private static String col(float[] k) { return String.format(Locale.US, "%.3f %.3f %.3f", k[0], k[1], k[2]); }

        private static String esc(String s) {
            return clean(s).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }

        void text(float x, float y, float size, Font f, String s, float[] k) {
            if (s == null || s.isEmpty()) return;
            c.append(String.format(Locale.US, "BT %s rg /%s %.1f Tf 1 0 0 1 %.2f %.2f Tm (%s) Tj ET\n", col(k), f.res, size, x, y, esc(s)));
        }

        void right(float xr, float y, float size, Font f, String s, float[] k) { text(xr - width(s, f, size), y, size, f, s, k); }

        void center(float xc, float y, float size, Font f, String s, float[] k) { text(xc - width(s, f, size) / 2f, y, size, f, s, k); }

        void fill(float x, float y, float w, float h, float[] k) { c.append(String.format(Locale.US, "%s rg %.2f %.2f %.2f %.2f re f\n", col(k), x, y, w, h)); }

        void stroke(float x, float y, float w, float h, float[] k, float lw) { c.append(String.format(Locale.US, "%s RG %.2f w %.2f %.2f %.2f %.2f re S\n", col(k), lw, x, y, w, h)); }

        void line(float x1, float y1, float x2, float y2, float[] k, float lw) { c.append(String.format(Locale.US, "%s RG %.2f w %.2f %.2f m %.2f %.2f l S\n", col(k), lw, x1, y1, x2, y2)); }

        void diamond(float cx, float cy, float r, float[] k) {
            c.append(String.format(Locale.US, "%s rg %.2f %.2f m %.2f %.2f l %.2f %.2f l %.2f %.2f l f\n", col(k), cx - r, cy, cx, cy + r, cx + r, cy, cx, cy - r));
        }
    }

    private static byte[] write(List<Pg> pages) {
        Font[] fonts = Font.values();
        List<String> objs = new ArrayList<>();
        objs.add("<< /Type /Catalog /Pages 2 0 R >>");
        int firstPage = 3, firstContent = firstPage + pages.size(), firstFont = firstContent + pages.size();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) kids.append(i > 0 ? " " : "").append(firstPage + i).append(" 0 R");
        objs.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
        StringBuilder res = new StringBuilder("<< /Font << ");
        for (int i = 0; i < fonts.length; i++) res.append('/').append(fonts[i].res).append(' ').append(firstFont + i).append(" 0 R ");
        res.append(">> >>");
        for (int i = 0; i < pages.size(); i++)
            objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources " + res + " /Contents " + (firstContent + i) + " 0 R >>");
        for (Pg p : pages) {
            String body = p.c.toString();
            objs.add("<< /Length " + body.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + body + "endstream");
        }
        for (Font f : fonts) objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /" + f.base + " /Encoding /WinAnsiEncoding >>");
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
}
