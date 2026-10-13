package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.support.Errors;
import com.jewellery360.support.Num;
import com.jewellery360.support.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.jewellery360.support.Num.*;

/**
 * Job work with karigars. Metal ISSUED to a job and finished work RECEIVED back are both recorded in grams and in fine
 * (pure) grams. Reconciliation per job:
 *   allowed loss = issued fine x wastage allowed %
 *   expected back = issued fine - allowed loss
 *   difference = received fine - expected back   (negative = the karigar still owes metal)
 * Labour is paid as PAYOUT payments linked to the karigar; labour due = labour on received/closed jobs - paid.
 */
@Service
@RequiredArgsConstructor
public class KarigarService {
    private final JobCardRepository jobs;
    private final JobMetalMovementRepository movements;
    private final KarigarRepository karigars;
    private final PurityMasterRepository purities;
    private final CustomOrderRepository orders;
    private final RepairOrderRepository repairs;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final NumberingService numbering;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    /** Karigars with open jobs, metal position and labour due. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> karigars(TenantContext.Ctx ctx) {
        permissions.requirePage(ctx.me(), "SERVICES");
        List<JobCard> all = jobs.list(ctx.companyId(), null, null, null);
        Map<Long, Totals> totals = totals(all);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Karigar k : karigars.findByCompanyIdOrderByNameAsc(ctx.companyId())) {
            List<JobCard> mine = all.stream().filter(j -> j.getKarigar().getId().equals(k.getId())).toList();
            out.add(karigarSummary(k, mine, totals));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> karigar(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "SERVICES");
        Karigar k = loadKarigar(ctx, id);
        List<JobCard> mine = jobs.list(ctx.companyId(), null, k.getId(), null);
        Map<Long, Totals> totals = totals(mine);
        Map<String, Object> m = karigarSummary(k, mine, totals);
        m.put("jobs", mine.stream().map(j -> jobView(j, totals.getOrDefault(j.getId(), new Totals()))).toList());
        m.put("payments", payments.findByKarigarIdOrderByPaymentDateAscIdAsc(k.getId()).stream().map(Views::payment).toList());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> jobs(TenantContext.Ctx ctx, Long karigarId, String status) {
        permissions.requirePage(ctx.me(), "SERVICES");
        String st = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase(Locale.ROOT);
        List<JobCard> list = jobs.list(ctx.companyId(), ctx.branchId(), karigarId, st);
        Map<Long, Totals> totals = totals(list);
        return list.stream().map(j -> jobView(j, totals.getOrDefault(j.getId(), new Totals()))).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> job(TenantContext.Ctx ctx, Long id) {
        permissions.requirePage(ctx.me(), "SERVICES");
        JobCard j = loadJob(ctx, id);
        Map<String, Object> m = jobView(j, totals(List.of(j)).getOrDefault(j.getId(), new Totals()));
        m.put("movements", movements.findByJobCardIdOrderByMovementDateAscIdAsc(j.getId()).stream().map(x -> {
            Map<String, Object> r = Views.map();
            r.put("id", x.getId());
            r.put("type", x.getMovementType());
            r.put("date", x.getMovementDate());
            r.put("weight", x.getWeight());
            r.put("fineWeight", x.getFineWeight());
            r.put("notes", x.getNotes());
            return r;
        }).toList());
        return m;
    }

    /** {karigarId, description, purityId, dueDate, wastageAllowedPercent, labourCharge, customOrderId, repairOrderId, notes, issueWeight} */
    @Transactional
    public Map<String, Object> createJob(TenantContext.Ctx ctx, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        JobCard j = new JobCard();
        j.setCompany(ctx.company());
        j.setBranch(ctx.branch());
        j.setKarigar(loadKarigar(ctx, Num.requireId(n, "karigarId", "karigar")));
        j.setJobDate(GoldRateService.today());
        j.setJobNo(numbering.next(ctx.company(), "JOB", j.getJobDate()));
        String d = Num.text(n, "description", 1000);
        if (d == null) throw Errors.bad("Describe the job.");
        j.setDescription(d);
        PurityMaster p = purities.findById(Num.requireId(n, "purityId", "purity")).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, p.getCompany());
        j.setPurity(p);
        j.setDueDate(Num.date(n, "dueDate"));
        BigDecimal w = Num.nonNegative(Num.dec(n, "wastageAllowedPercent"), "Wastage");
        if (w.compareTo(BigDecimal.valueOf(50)) > 0) throw Errors.bad("Wastage allowed looks too high.");
        j.setWastageAllowedPercent(r3(w));
        j.setLabourCharge(r2(Num.nonNegative(Num.dec(n, "labourCharge"), "Labour")));
        Long oid = Num.id(n, "customOrderId");
        if (oid != null) {
            CustomOrder o = orders.findById(oid).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, o.getCompany());
            j.setCustomOrder(o);
            if ("ORDERED".equals(o.getStatus())) o.setStatus("IN_PROGRESS");
            if (o.getKarigar() == null) o.setKarigar(j.getKarigar());
        }
        Long rid = Num.id(n, "repairOrderId");
        if (rid != null) {
            RepairOrder r = repairs.findById(rid).orElseThrow(TenantContext::notFound);
            tenant.checkCompany(ctx, r.getCompany());
            j.setRepairOrder(r);
            if ("RECEIVED".equals(r.getStatus())) r.setStatus("IN_REPAIR");
            if (r.getKarigar() == null) r.setKarigar(j.getKarigar());
        }
        j.setNotes(Num.text(n, "notes", 1000));
        j.setCreatedBy(tenant.user(ctx.me()));
        jobs.save(j);
        BigDecimal issue = Num.dec(n, "issueWeight");
        if (issue.signum() > 0) move(ctx, j, "ISSUE", issue, "Issued with job");
        audit.log(ctx, "JOB_CREATE", "JobCard", j.getId(), null, Map.of("jobNo", j.getJobNo(), "karigar", j.getKarigar().getName()));
        return job(ctx, j.getId());
    }

    /** {type: ISSUE|RECEIPT, weight, notes} */
    @Transactional
    public Map<String, Object> addMovement(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        JobCard j = loadJob(ctx, id);
        if ("CLOSED".equals(j.getStatus())) throw Errors.bad("This job is closed.");
        String type = Optional.ofNullable(Num.text(n, "type")).orElse("").toUpperCase(Locale.ROOT);
        if (!List.of("ISSUE", "RECEIPT").contains(type)) throw Errors.bad("Type must be ISSUE or RECEIPT.");
        BigDecimal w = Num.dec(n, "weight");
        if (w.signum() <= 0) throw Errors.bad("Weight must be more than 0.");
        move(ctx, j, type, w, Num.text(n, "notes", 500));
        if ("RECEIPT".equals(type) && "OPEN".equals(j.getStatus())) j.setStatus("RECEIVED");
        audit.log(ctx, "JOB_METAL_" + type, "JobCard", j.getId(), null, Map.of("weight", w));
        return job(ctx, j.getId());
    }

    /** Closes the job after checking the metal. A shortfall beyond the allowed wastage needs {acceptShortfall:true}. */
    @Transactional
    public Map<String, Object> close(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        JobCard j = loadJob(ctx, id);
        if ("CLOSED".equals(j.getStatus())) throw Errors.bad("This job is already closed.");
        Totals t = totals(List.of(j)).getOrDefault(j.getId(), new Totals());
        if (t.receivedFine.signum() <= 0) throw Errors.bad("Record the work received back before closing the job.");
        BigDecimal diff = difference(j, t);
        if (diff.signum() < 0 && !Num.bool(n, "acceptShortfall", false))
            throw Errors.bad("The karigar is short by " + diff.negate().toPlainString() + " g fine metal. Confirm to close anyway.");
        if (n.has("labourCharge")) j.setLabourCharge(r2(Num.nonNegative(Num.dec(n, "labourCharge"), "Labour")));
        j.setStatus("CLOSED");
        j.setClosedDate(GoldRateService.today());
        if (j.getCustomOrder() != null && List.of("ORDERED", "IN_PROGRESS").contains(j.getCustomOrder().getStatus())) j.getCustomOrder().setStatus("READY");
        if (j.getRepairOrder() != null && List.of("RECEIVED", "IN_REPAIR").contains(j.getRepairOrder().getStatus())) j.getRepairOrder().setStatus("READY");
        audit.log(ctx, "JOB_CLOSE", "JobCard", j.getId(), null, Map.of("differenceFine", diff, "labour", j.getLabourCharge()));
        return job(ctx, j.getId());
    }

    /** Labour payment to a karigar: {amount, mode, reference, notes}. */
    @Transactional
    public Map<String, Object> pay(TenantContext.Ctx ctx, Long karigarId, JsonNode n) {
        permissions.requireWrite(ctx.me(), "SERVICES");
        if (ctx.branch() == null) throw Errors.bad("Select a branch first.");
        Karigar k = loadKarigar(ctx, karigarId);
        Payment p = paymentService.record(ctx, Payment.PAYOUT, PaymentService.moneyMode(Num.text(n, "mode")), Num.dec(n, "amount"),
                x -> x.setKarigar(k), Num.text(n, "reference", 160), Optional.ofNullable(Num.text(n, "notes", 1000)).orElse("Labour paid"));
        audit.log(ctx, "KARIGAR_PAY", "Karigar", k.getId(), null, Map.of("paymentNo", p.getPaymentNo(), "amount", p.getAmount()));
        return karigar(ctx, k.getId());
    }

    /* ------------------------------------------------------------------ helpers */

    private void move(TenantContext.Ctx ctx, JobCard j, String type, BigDecimal weight, String notes) {
        JobMetalMovement m = new JobMetalMovement();
        m.setJobCard(j);
        m.setMovementType(type);
        m.setMovementDate(GoldRateService.today());
        m.setWeight(r3(weight));
        m.setFineWeight(r3(weight.multiply(j.getPurity().getFineness())));
        m.setNotes(notes);
        m.setCreatedBy(tenant.user(ctx.me()));
        movements.save(m);
    }

    static final class Totals {
        BigDecimal issued = BigDecimal.ZERO, issuedFine = BigDecimal.ZERO, received = BigDecimal.ZERO, receivedFine = BigDecimal.ZERO;
    }

    private Map<Long, Totals> totals(List<JobCard> list) {
        Map<Long, Totals> out = new HashMap<>();
        if (list.isEmpty()) return out;
        for (Object[] r : movements.totals(list.stream().map(JobCard::getId).toList())) {
            Totals t = out.computeIfAbsent((Long) r[0], x -> new Totals());
            if ("ISSUE".equals(r[1])) { t.issued = (BigDecimal) r[2]; t.issuedFine = (BigDecimal) r[3]; }
            else { t.received = (BigDecimal) r[2]; t.receivedFine = (BigDecimal) r[3]; }
        }
        return out;
    }

    private static BigDecimal allowedLoss(JobCard j, Totals t) { return pct(t.issuedFine, j.getWastageAllowedPercent()); }
    private static BigDecimal expected(JobCard j, Totals t) { return r3(t.issuedFine.subtract(allowedLoss(j, t))); }
    private static BigDecimal difference(JobCard j, Totals t) { return r3(t.receivedFine.subtract(expected(j, t))); }

    private Map<String, Object> jobView(JobCard j, Totals t) {
        Map<String, Object> m = Views.map();
        m.put("id", j.getId());
        m.put("jobNo", j.getJobNo());
        m.put("date", j.getJobDate());
        m.put("dueDate", j.getDueDate());
        m.put("overdue", j.getDueDate() != null && j.getDueDate().isBefore(GoldRateService.today()) && "OPEN".equals(j.getStatus()));
        m.put("karigar", j.getKarigar().getName());
        m.put("karigarId", j.getKarigar().getId());
        m.put("description", j.getDescription());
        m.put("purity", j.getPurity().getName());
        m.put("orderNo", j.getCustomOrder() != null ? j.getCustomOrder().getOrderNo() : j.getRepairOrder() != null ? j.getRepairOrder().getOrderNo() : null);
        m.put("wastageAllowedPercent", j.getWastageAllowedPercent());
        m.put("labourCharge", j.getLabourCharge());
        m.put("status", j.getStatus());
        m.put("closedDate", j.getClosedDate());
        m.put("issued", r3(t.issued));
        m.put("issuedFine", r3(t.issuedFine));
        m.put("received", r3(t.received));
        m.put("receivedFine", r3(t.receivedFine));
        m.put("allowedLossFine", allowedLoss(j, t));
        m.put("expectedFine", expected(j, t));
        m.put("differenceFine", t.receivedFine.signum() > 0 || "CLOSED".equals(j.getStatus()) ? difference(j, t) : null);
        m.put("notes", j.getNotes());
        return m;
    }

    private Map<String, Object> karigarSummary(Karigar k, List<JobCard> mine, Map<Long, Totals> totals) {
        BigDecimal metalWithKarigar = BigDecimal.ZERO, shortfall = BigDecimal.ZERO, labour = BigDecimal.ZERO;
        int open = 0;
        for (JobCard j : mine) {
            Totals t = totals.getOrDefault(j.getId(), new Totals());
            if ("CLOSED".equals(j.getStatus())) {
                BigDecimal d = difference(j, t);
                if (d.signum() < 0) shortfall = shortfall.add(d.negate());
            } else {
                open++;
                metalWithKarigar = metalWithKarigar.add(t.issuedFine.subtract(t.receivedFine).max(BigDecimal.ZERO));
            }
            if (!"OPEN".equals(j.getStatus())) labour = labour.add(j.getLabourCharge());
        }
        BigDecimal paid = payments.findByKarigarIdOrderByPaymentDateAscIdAsc(k.getId()).stream()
                .map(p -> p.getAmount().multiply(BigDecimal.valueOf(-p.direction()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = Views.map();
        m.put("id", k.getId());
        m.put("name", k.getName());
        m.put("phone", k.getPhone());
        m.put("active", k.isActive());
        m.put("openJobs", open);
        m.put("metalWithKarigarFine", r3(metalWithKarigar));
        m.put("shortfallFine", r3(shortfall));
        m.put("labourEarned", r2(labour));
        m.put("labourPaid", r2(paid));
        m.put("labourDue", r2(labour.subtract(paid)));
        return m;
    }

    private Karigar loadKarigar(TenantContext.Ctx ctx, Long id) {
        Karigar k = karigars.findById(id).orElseThrow(TenantContext::notFound);
        tenant.checkCompany(ctx, k.getCompany());
        return k;
    }

    private JobCard loadJob(TenantContext.Ctx ctx, Long id) {
        JobCard j = jobs.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, j.getCompany(), j.getBranch());
        return j;
    }

}
