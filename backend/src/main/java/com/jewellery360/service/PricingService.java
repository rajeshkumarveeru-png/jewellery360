package com.jewellery360.service;

import com.jewellery360.support.Errors;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static com.jewellery360.support.Num.*;

/**
 * The one jewellery price calculation, used for the live preview and for the saved bill.
 *
 * <pre>
 * Metal value   = net weight x rate per gram           (fixed-price pieces: the MRP)
 * + Wastage     = metal value x wastage %
 * + Making      = FIXED rupees | PER_GRAM x net weight | PERCENT of metal value
 * + Stone       + Other charges
 * = Line value  -> sum = Subtotal
 * - Discount    (before tax, shared over the lines in proportion to their value)
 * = Taxable     + GST (GST, or CGST + SGST) = total, rounded to the nearest rupee
 * </pre>
 */
@Service
public class PricingService {
    public static final List<String> MAKING_TYPES = List.of("FIXED", "PER_GRAM", "PERCENT");

    public record LineInput(BigDecimal netWeight, BigDecimal rate, BigDecimal wastagePercent, String makingType, BigDecimal makingValue,
                            BigDecimal stoneCharge, BigDecimal otherCharge, BigDecimal fixedPrice) {}

    public record LineResult(BigDecimal metalValue, BigDecimal wastageValue, BigDecimal makingCharge, BigDecimal stoneCharge,
                             BigDecimal otherCharge, BigDecimal lineValue, BigDecimal discount, BigDecimal taxable, BigDecimal gst, BigDecimal total) {}

    public record Tax(boolean enabled, String mode, BigDecimal rate, BigDecimal cgstRate, BigDecimal sgstRate) {
        public BigDecimal totalRate() { return !enabled ? BigDecimal.ZERO : "CGST_SGST".equals(mode) ? nz(cgstRate).add(nz(sgstRate)) : nz(rate); }
        public String effectiveMode() { return enabled ? mode : "NONE"; }
    }

    public record BillResult(List<LineResult> lines, BigDecimal metalValue, BigDecimal wastageValue, BigDecimal makingValue, BigDecimal stoneValue,
                             BigDecimal otherValue, BigDecimal subtotal, BigDecimal discount, BigDecimal taxable, BigDecimal gst, BigDecimal cgst,
                             BigDecimal sgst, BigDecimal roundOff, BigDecimal total) {}

    public LineResult line(LineInput in) {
        String type = in.makingType() == null ? "FIXED" : in.makingType();
        if (!MAKING_TYPES.contains(type)) throw Errors.bad("Making charge type must be FIXED, PER_GRAM or PERCENT.");
        BigDecimal metal;
        BigDecimal wastage;
        BigDecimal making;
        if (positive(in.fixedPrice())) {
            metal = r2(in.fixedPrice());
            wastage = BigDecimal.ZERO;
            making = BigDecimal.ZERO;
        } else {
            metal = r2(nz(in.netWeight()).multiply(nz(in.rate())));
            wastage = r2(pct(metal, in.wastagePercent()));
            making = switch (type) {
                case "PER_GRAM" -> r2(nz(in.netWeight()).multiply(nz(in.makingValue())));
                case "PERCENT" -> r2(pct(metal, in.makingValue()));
                default -> r2(in.makingValue());
            };
        }
        BigDecimal stone = r2(in.stoneCharge());
        BigDecimal other = r2(in.otherCharge());
        BigDecimal value = metal.add(wastage).add(making).add(stone).add(other);
        return new LineResult(metal, wastage, making, stone, other, value, BigDecimal.ZERO, value, BigDecimal.ZERO, value);
    }

    public BillResult bill(List<LineResult> raw, BigDecimal discountIn, Tax tax) {
        BigDecimal subtotal = raw.stream().map(LineResult::lineValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = r2(nonNegative(discountIn, "Discount"));
        if (discount.compareTo(subtotal) > 0) throw Errors.bad("Discount cannot be more than the bill value.");
        BigDecimal rate = tax.totalRate();

        List<LineResult> lines = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        BigDecimal gstSum = BigDecimal.ZERO;
        for (int i = 0; i < raw.size(); i++) {
            LineResult l = raw.get(i);
            BigDecimal share = i == raw.size() - 1 ? discount.subtract(given)
                    : subtotal.signum() == 0 ? BigDecimal.ZERO : r2(discount.multiply(l.lineValue()).divide(subtotal, 6, RoundingMode.HALF_UP));
            given = given.add(share);
            BigDecimal taxable = l.lineValue().subtract(share);
            BigDecimal gst = r2(pct(taxable, rate));
            gstSum = gstSum.add(gst);
            lines.add(new LineResult(l.metalValue(), l.wastageValue(), l.makingCharge(), l.stoneCharge(), l.otherCharge(), l.lineValue(),
                    share, taxable, gst, taxable.add(gst)));
        }
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO;
        if (tax.enabled() && "CGST_SGST".equals(tax.mode()) && rate.signum() > 0) {
            cgst = r2(gstSum.multiply(nz(tax.cgstRate())).divide(rate, 6, RoundingMode.HALF_UP));
            sgst = gstSum.subtract(cgst);
        }
        BigDecimal exact = taxable.add(gstSum);
        BigDecimal total = exact.setScale(0, RoundingMode.HALF_UP);
        return new BillResult(lines, sum(raw, LineResult::metalValue), sum(raw, LineResult::wastageValue), sum(raw, LineResult::makingCharge),
                sum(raw, LineResult::stoneCharge), sum(raw, LineResult::otherCharge), subtotal, discount, taxable, gstSum, cgst, sgst,
                total.subtract(exact), total.setScale(3, RoundingMode.HALF_UP));
    }

    /** Value of old gold: net weight x rate x (1 - deduction %). */
    public BigDecimal oldGoldValue(BigDecimal net, BigDecimal rate, BigDecimal deductionPercent) {
        BigDecimal gross = nz(net).multiply(nz(rate));
        return r2(gross.subtract(pct(gross, deductionPercent)));
    }

    private static BigDecimal sum(List<LineResult> l, java.util.function.Function<LineResult, BigDecimal> f) {
        return l.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
