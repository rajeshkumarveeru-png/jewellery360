package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.domain.*;
import com.jewellery360.repository.OldGoldItemRepository;
import com.jewellery360.repository.OldGoldTransactionRepository;
import com.jewellery360.repository.PaymentRepository;
import com.jewellery360.repository.PurityMasterRepository;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import com.jewellery360.support.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.jewellery360.support.Num.*;

/**
 * Old gold taken from customers. Valuation is always shown line by line:
 * net weight = gross - stone; value = net x rate x (1 - deduction %).
 * EXCHANGE keeps the value for a bill (today's or a later one); PURCHASE pays the customer straight away.
 */
@Service
@RequiredArgsConstructor
public class OldGoldService {
    private final OldGoldTransactionRepository transactions;
    private final OldGoldItemRepository lines;
    private final PurityMasterRepository purities;
    private final PaymentRepository payments;
    private final CustomerService customers;
    private final PaymentService paymentService;
    private final GoldRateService goldRates;
    private final PricingService pricing;
    private final NumberingService numbering;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    public static final class Line {
        String description, purity;
        BigDecimal fineness, gross, stone, net, rate, deduction, amount;
    }

    public Line parse(TenantContext.Ctx ctx, JsonNode n, Map<Long, GoldRate> rates) {
        Line o = new Line();
        o.description = Optional.ofNullable(Num.text(n, "description", 500)).orElse("Old gold");
        Long purityId = Num.id(n, "purityId");
        BigDecimal defaultRate = BigDecimal.ZERO;
        if (purityId != null) {
            PurityMaster pm = purities.findById(purityId).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, pm.getCompany());
            o.purity = pm.getName();
            o.fineness = pm.getFineness();
            GoldRate r = rates.get(pm.getId());
            if (r != null) defaultRate = r.getRatePerGram();
        } else {
            o.purity = Optional.ofNullable(Num.text(n, "purity", 50)).orElseThrow(() -> Errors.bad("Pick the purity of the old gold."));
            o.fineness = Num.dec(n, "fineness");
        }
        o.gross = Num.nonNegative(Num.dec(n, "grossWeight"), "Old gold weight");
        o.stone = Num.nonNegative(Num.dec(n, "stoneWeight"), "Stone weight");
        BigDecimal net = Num.decOrNull(n, "netWeight");
        o.net = net == null ? o.gross.subtract(o.stone) : net;
        if (o.gross.signum() <= 0 || o.net.signum() <= 0 || o.net.compareTo(o.gross) > 0) throw Errors.bad("Check the old gold weights (net must be more than 0 and not more than gross).");
        BigDecimal rate = Num.decOrNull(n, "rate");
        o.rate = rate == null ? defaultRate : rate;
        if (o.rate.signum() <= 0) throw Errors.bad("Enter the rate for the old gold (" + o.purity + ").");
        o.deduction = Num.nonNegative(Num.dec(n, "deductionPercent"), "Deduction");
        if (o.deduction.compareTo(HUNDRED) >= 0) throw Errors.bad("Deduction must be less than 100%.");
        o.amount = pricing.oldGoldValue(o.net, o.rate, o.deduction);
        return o;
    }

    public List<Line> parseAll(TenantContext.Ctx ctx, JsonNode list) {
        List<Line> out = new ArrayList<>();
        if (list == null || !list.isArray()) return out;
        Map<Long, GoldRate> rates = goldRates.rateMap(ctx.branchId(), GoldRateService.today());
        for (JsonNode n : list) out.add(parse(ctx, n, rates));
        return out;
    }

    public static BigDecimal total(List<Line> l) { return l.stream().map(x -> x.amount).reduce(BigDecimal.ZERO, BigDecimal::add); }

    OldGoldTransaction save(TenantContext.Ctx ctx, Customer customer, String type, List<Line> items, String notes) {
        OldGoldTransaction t = new OldGoldTransaction();
        t.setCompany(ctx.company());
        t.setBranch(ctx.branch());
        t.setCustomer(customer);
        t.setTransactionType(type);
        t.setTransactionDate(GoldRateService.today());
        t.setTransactionNo(numbering.next(ctx.company(), "OG", t.getTransactionDate()));
        t.setNotes(notes);
        t.setCreatedBy(tenant.user(ctx.me()));
        transactions.save(t);
        for (Line o : items) {
            OldGoldItem it = new OldGoldItem();
            it.setTransaction(t);
            it.setDescription(o.description);
            it.setPurity(o.purity);
            it.setFineness(nz(o.fineness));
            it.setGrossWeight(r3(o.gross));
            it.setStoneWeight(r3(o.stone));
            it.setNetWeight(r3(o.net));
            it.setRate(r3(o.rate));
            it.setDeductionPercent(r3(o.deduction));
            it.setAmount(o.amount);
            lines.save(it);
            t.setTotalGrossWeight(t.getTotalGrossWeight().add(it.getGrossWeight()));
            t.setTotalNetWeight(t.getTotalNetWeight().add(it.getNetWeight()));
            t.setTotalAmount(t.getTotalAmount().add(it.getAmount()));
        }
        return t;
    }

    /** Value preview: {items:[...]} -> per-line valuation and total, nothing saved. */
    @Transactional(readOnly = true)
    public Map<String, Object> preview(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requirePage(ctx.me(), "OLD_GOLD");
        List<Line> l = parseAll(ctx, body.get("items"));
        Map<String, Object> m = Views.map();
        m.put("lines", l.stream().map(OldGoldService::lineView).toList());
        m.put("total", total(l));
        return m;
    }

    /**
     * Standalone old gold: {customerId | customer:{name, phone}, type: PURCHASE | EXCHANGE, items:[...], payoutMode, notes}.
     * PURCHASE pays the customer now; EXCHANGE keeps the value open for a bill.
     */
    @Transactional
    public Map<String, Object> create(TenantContext.Ctx ctx, JsonNode body) {
        permissions.requireWrite(ctx.me(), "OLD_GOLD");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Customer c;
        Long cid = Num.id(body, "customerId");
        if (cid != null) c = customers.get(ctx, cid);
        else if (body.get("customer") != null && Num.text(body.get("customer"), "name") != null) c = customers.get(ctx, (Long) customers.create(ctx, body.get("customer")).get("id"));
        else throw Errors.bad("Pick the customer (old gold purchases need the seller's name and phone).");
        String type = Optional.ofNullable(Num.text(body, "type")).orElse("PURCHASE").toUpperCase(Locale.ROOT);
        if (!List.of("PURCHASE", "EXCHANGE").contains(type)) throw Errors.bad("Type must be PURCHASE or EXCHANGE.");
        List<Line> l = parseAll(ctx, body.get("items"));
        if (l.isEmpty()) throw Errors.bad("Add at least one old gold item.");
        OldGoldTransaction t = save(ctx, c, type, l, Num.text(body, "notes", 1000));
        if ("PURCHASE".equals(type)) {
            paymentService.record(ctx, Payment.PAYOUT, PaymentService.moneyMode(Num.text(body, "payoutMode")), t.getTotalAmount(),
                    x -> { x.setCustomer(c); x.setOldGold(t); }, Num.text(body, "reference", 160), "Old gold bought from customer");
            t.setUsedAmount(t.getTotalAmount());
            t.setStatus("SETTLED");
        }
        audit.log(ctx, "OLD_GOLD_" + type, "OldGoldTransaction", t.getId(), null, Map.of("transactionNo", t.getTransactionNo(),
                "netWeight", t.getTotalNetWeight(), "amount", t.getTotalAmount()));
        return detail(ctx, t.getId());
    }

    /** Pays out (or moves to advance) whatever is left of an open exchange: {mode: CASH|UPI|...|ADVANCE}. */
    @Transactional
    public Map<String, Object> settle(TenantContext.Ctx ctx, Long id, JsonNode body) {
        permissions.requireWrite(ctx.me(), "OLD_GOLD");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        OldGoldTransaction t = transactions.lock(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, t.getCompany(), t.getBranch());
        BigDecimal left = t.available();
        if (left.signum() <= 0) throw Errors.bad("Nothing is left to settle on " + t.getTransactionNo() + ".");
        String mode = Optional.ofNullable(Num.text(body, "mode")).orElse("CASH").toUpperCase(Locale.ROOT);
        if ("ADVANCE".equals(mode)) paymentService.createAdvance(ctx, t.getCustomer(), left, null, "OLD_GOLD", null, t.getTransactionNo(), "Old gold value kept as advance");
        else paymentService.record(ctx, Payment.PAYOUT, PaymentService.moneyMode(mode), left, x -> { x.setCustomer(t.getCustomer()); x.setOldGold(t); }, null, "Old gold value paid out");
        t.setUsedAmount(t.getTotalAmount());
        t.setStatus("SETTLED");
        audit.log(ctx, "OLD_GOLD_SETTLE", "OldGoldTransaction", t.getId(), null, Map.of("amount", left, "mode", mode));
        return detail(ctx, id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(TenantContext.Ctx ctx, LocalDate from, LocalDate to, boolean openOnly) {
        permissions.requirePage(ctx.me(), "OLD_GOLD");
        LocalDate end = to == null ? GoldRateService.today() : to;
        LocalDate start = from == null ? end.minusDays(90) : from;
        return transactions.between(ctx.companyId(), ctx.branchId(), start, end).stream()
                .filter(t -> !openOnly || "OPEN".equals(t.getStatus())).map(OldGoldService::summary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(TenantContext.Ctx ctx, Long id) {
        OldGoldTransaction t = transactions.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, t.getCompany(), t.getBranch());
        Map<String, Object> m = summary(t);
        m.put("customer", Views.customer(t.getCustomer()));
        m.put("notes", t.getNotes());
        List<Map<String, Object>> its = new ArrayList<>();
        for (OldGoldItem i : lines.findByTransactionIdOrderById(id)) {
            Map<String, Object> x = Views.map();
            x.put("description", i.getDescription());
            x.put("purity", i.getPurity());
            x.put("fineness", i.getFineness());
            x.put("grossWeight", i.getGrossWeight());
            x.put("stoneWeight", i.getStoneWeight());
            x.put("netWeight", i.getNetWeight());
            x.put("rate", i.getRate());
            x.put("deductionPercent", i.getDeductionPercent());
            x.put("amount", i.getAmount());
            its.add(x);
        }
        m.put("items", its);
        return m;
    }

    static Map<String, Object> summary(OldGoldTransaction t) {
        Map<String, Object> m = Views.map();
        m.put("id", t.getId());
        m.put("transactionNo", t.getTransactionNo());
        m.put("type", t.getTransactionType());
        m.put("date", t.getTransactionDate());
        m.put("customer", Views.customerBrief(t.getCustomer()));
        m.put("grossWeight", t.getTotalGrossWeight());
        m.put("netWeight", t.getTotalNetWeight());
        m.put("amount", t.getTotalAmount());
        m.put("used", t.getUsedAmount());
        m.put("available", t.available());
        m.put("status", t.getStatus());
        return m;
    }

    static Map<String, Object> lineView(Line o) {
        Map<String, Object> x = Views.map();
        x.put("description", o.description);
        x.put("purity", o.purity);
        x.put("grossWeight", o.gross);
        x.put("stoneWeight", o.stone);
        x.put("netWeight", o.net);
        x.put("rate", o.rate);
        x.put("deductionPercent", o.deduction);
        x.put("amount", o.amount);
        return x;
    }
}
