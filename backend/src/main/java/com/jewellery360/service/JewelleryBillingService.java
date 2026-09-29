package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JewelleryBillingService {
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final CustomerRepository customers;
    private final JewelleryItemRepository items;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final PaymentRepository payments;
    private final StockRepository stocks;
    private final StockMovementRepository movements;
    private final AppUserRepository users;
    private final AuditService audit;
    private final PermissionService permissions;

    @Transactional
    public Map<String,Object> create(AuthenticatedUser me, Request r, Long headerCompanyId, Long headerBranchId) {
        permissions.requireWrite(me, "BILLING");
        Context ctx = context(me, headerCompanyId, headerBranchId);
        if (r.items() == null || r.items().isEmpty()) throw bad("At least one jewellery item is required");

        Customer customer = customers.findById(r.customerId()).orElseThrow(() -> bad("Customer not found"));
        if (!Objects.equals(customer.getCompany().getId(), ctx.company().getId())) throw forbidden("Customer is outside company scope");
        if (ctx.branch().getId() != null && customer.getBranch() != null && !Objects.equals(customer.getBranch().getId(), ctx.branch().getId())) throw forbidden("Customer is outside branch scope");

        Set<Long> seen = new HashSet<>();
        List<PreparedLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal weightedRateNumerator = BigDecimal.ZERO;
        BigDecimal commonGoldRate = n(r.goldRate());

        for (ItemRequest rLine : r.items()) {
            if (rLine == null || rLine.jewelleryItemId() == null) throw bad("Every invoice line must have a jewellery item");
            if (!seen.add(rLine.jewelleryItemId())) throw bad("The same jewellery item cannot be added twice to one invoice");

            JewelleryItem item = items.findById(rLine.jewelleryItemId()).orElseThrow(() -> bad("Jewellery item not found: " + rLine.jewelleryItemId()));
            if (!Objects.equals(item.getCompany().getId(), ctx.company().getId()) || !Objects.equals(item.getBranch().getId(), ctx.branch().getId())) throw forbidden("Jewellery item is outside branch scope: " + rLine.jewelleryItemId());
            if (!"IN_STOCK".equalsIgnoreCase(item.getStatus())) throw bad("Jewellery item is not available for sale: " + item.getTag().getTagNo());

            BigDecimal gross = n(rLine.grossWeight());
            BigDecimal stone = n(rLine.stoneWeight());
            BigDecimal net = rLine.netWeight() == null ? gross.subtract(stone) : n(rLine.netWeight());
            if (gross.compareTo(BigDecimal.ZERO) <= 0 || net.compareTo(BigDecimal.ZERO) <= 0) throw bad("Invalid weight for item " + item.getTag().getTagNo());
            BigDecimal effectiveGoldRate = commonGoldRate.compareTo(BigDecimal.ZERO) > 0 ? commonGoldRate : n(rLine.goldRate());
            if (effectiveGoldRate.compareTo(BigDecimal.ZERO) <= 0) throw bad("Gold rate is required for item " + item.getTag().getTagNo());

            BigDecimal gold = net.multiply(effectiveGoldRate);
            BigDecimal wastage = gold.multiply(n(rLine.wastagePercent())).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            BigDecimal lineSubtotal = gold.add(wastage).add(n(rLine.makingCharge())).add(n(rLine.stoneCharge())).add(n(rLine.otherCharge()));
            subtotal = subtotal.add(lineSubtotal);
            totalNet = totalNet.add(net);
            weightedRateNumerator = weightedRateNumerator.add(net.multiply(effectiveGoldRate));
            lines.add(new PreparedLine(rLine, item, gross, stone, net, effectiveGoldRate, gold, wastage, lineSubtotal));
        }

        BigDecimal gstPercent = n(r.gstPercent());
        BigDecimal gst = subtotal.multiply(gstPercent).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        BigDecimal grossInvoice = subtotal.add(gst);
        BigDecimal discount = n(r.discount()).max(BigDecimal.ZERO).min(grossInvoice);
        BigDecimal total = grossInvoice.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal paymentAmount = n(r.paymentAmount()).max(BigDecimal.ZERO);
        if (paymentAmount.compareTo(total) > 0) paymentAmount = total;

        Sale sale = new Sale();
        sale.setCompany(ctx.company());
        sale.setBranch(ctx.branch());
        sale.setCustomer(customer);
        sale.setInvoiceNo(r.invoiceNo());
        sale.setSaleDate(r.saleDate() == null ? LocalDate.now() : r.saleDate());
        sale.setStatus("COMPLETED");
        sale.setGoldRate(totalNet.compareTo(BigDecimal.ZERO) > 0 ? weightedRateNumerator.divide(totalNet, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setGst(gst);
        sale.setTotal(total);
        sale.setPaymentStatus(paymentAmount.compareTo(BigDecimal.ZERO) <= 0 ? "UNPAID" : paymentAmount.compareTo(total) >= 0 ? "PAID" : "PARTIAL");
        sale.setCreatedBy(users.findById(me.getUserId()).orElse(null));
        sale = sales.save(sale);

        for (PreparedLine line : lines) {
            BigDecimal lineGrossWithGst = line.lineSubtotal().multiply(BigDecimal.valueOf(100).add(gstPercent)).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            BigDecimal allocatedDiscount = grossInvoice.compareTo(BigDecimal.ZERO) > 0
                    ? discount.multiply(lineGrossWithGst).divide(grossInvoice, 3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal lineGst = line.lineSubtotal().multiply(gstPercent).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineGrossWithGst.subtract(allocatedDiscount).max(BigDecimal.ZERO);

            SaleItem si = new SaleItem();
            si.setSale(sale);
            si.setJewelleryItem(line.item());
            si.setTagNo(line.item().getTag().getTagNo());
            si.setGrossWeight(line.gross());
            si.setStoneWeight(line.stone());
            si.setNetWeight(line.net());
            si.setPurity(line.item().getPurity().getName());
            si.setGoldRate(line.rate());
            si.setWastageValue(line.wastage());
            si.setMakingCharge(n(line.request().makingCharge()));
            si.setStoneCharge(n(line.request().stoneCharge()));
            si.setOtherCharge(n(line.request().otherCharge()));
            si.setGst(lineGst);
            si.setTotal(lineTotal);
            saleItems.save(si);

            line.item().setStatus("SOLD");
            items.save(line.item());
            Stock stock = stocks.findByCompanyId(ctx.company().getId()).stream()
                    .filter(s -> s.getBranch() != null && Objects.equals(s.getBranch().getId(), ctx.branch().getId()))
                    .filter(s -> s.getJewelleryItem() != null && Objects.equals(s.getJewelleryItem().getId(), line.item().getId()))
                    .findFirst().orElse(null);
            if (stock != null) { stock.setStatus("SOLD"); stocks.save(stock); }

            StockMovement movement = new StockMovement();
            movement.setCompany(ctx.company()); movement.setBranch(ctx.branch()); movement.setJewelleryItem(line.item());
            movement.setMovementType("SALE"); movement.setQuantity(BigDecimal.ONE); movement.setReferenceType("SALE"); movement.setReferenceId(sale.getId());
            movement.setMovementDate(sale.getSaleDate()); movement.setCreatedBy(users.findById(me.getUserId()).orElse(null));
            movements.save(movement);
        }

        if (paymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            Payment p = new Payment();
            p.setCompany(ctx.company()); p.setBranch(ctx.branch()); p.setCustomer(customer); p.setSale(sale);
            p.setPaymentNo(r.paymentNo() == null || r.paymentNo().isBlank() ? "PAY-" + System.currentTimeMillis() : r.paymentNo());
            p.setPaymentType("RECEIPT"); p.setPaymentMode(r.paymentMode() == null ? "CASH" : r.paymentMode()); p.setAmount(paymentAmount);
            p.setPaymentDate(sale.getSaleDate()); p.setCreatedBy(users.findById(me.getUserId()).orElse(null)); payments.save(p);
        }

        audit.log(me, "CREATE", "SALE", sale.getId(), null, Map.of("invoiceNo", sale.getInvoiceNo(), "total", total, "itemCount", lines.size()));
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("id", sale.getId());
        response.put("invoiceNo", sale.getInvoiceNo());
        response.put("itemCount", lines.size());
        response.put("goldValue", lines.stream().map(PreparedLine::gold).reduce(BigDecimal.ZERO, BigDecimal::add));
        response.put("wastageValue", lines.stream().map(PreparedLine::wastage).reduce(BigDecimal.ZERO, BigDecimal::add));
        response.put("subtotal", subtotal);
        response.put("gst", gst);
        response.put("discount", discount);
        response.put("total", total);
        response.put("paymentStatus", sale.getPaymentStatus());
        return response;
    }

    public Map<String,Object> calculate(List<ItemRequest> itemRequests, BigDecimal goldRate, BigDecimal gstPercent, BigDecimal discount) {
        if (itemRequests == null || itemRequests.isEmpty()) throw bad("At least one jewellery item is required");
        BigDecimal subtotal = BigDecimal.ZERO, goldValue = BigDecimal.ZERO, wastageValue = BigDecimal.ZERO;
        BigDecimal commonGoldRate=n(goldRate);
        for (ItemRequest r : itemRequests) {
            BigDecimal gross=n(r.grossWeight()), stone=n(r.stoneWeight()), net=r.netWeight()==null?gross.subtract(stone):n(r.netWeight());
            BigDecimal effectiveGoldRate=commonGoldRate.compareTo(BigDecimal.ZERO)>0?commonGoldRate:n(r.goldRate());
            if(effectiveGoldRate.compareTo(BigDecimal.ZERO)<=0) throw bad("Gold rate is required");
            BigDecimal gold=net.multiply(effectiveGoldRate);
            BigDecimal wastage=gold.multiply(n(r.wastagePercent())).divide(BigDecimal.valueOf(100),3,RoundingMode.HALF_UP);
            goldValue=goldValue.add(gold); wastageValue=wastageValue.add(wastage);
            subtotal=subtotal.add(gold).add(wastage).add(n(r.makingCharge())).add(n(r.stoneCharge())).add(n(r.otherCharge()));
        }
        BigDecimal gst=subtotal.multiply(n(gstPercent)).divide(BigDecimal.valueOf(100),3,RoundingMode.HALF_UP);
        BigDecimal safeDiscount=n(discount).max(BigDecimal.ZERO).min(subtotal.add(gst));
        BigDecimal total=subtotal.add(gst).subtract(safeDiscount).max(BigDecimal.ZERO);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("goldValue",goldValue);out.put("wastageValue",wastageValue);out.put("subtotal",subtotal);out.put("gst",gst);out.put("discount",safeDiscount);out.put("total",total);out.put("itemCount",itemRequests.size());
        return out;
    }

    @Transactional
    public Map<String,Object> cancel(AuthenticatedUser me, Long id, Long headerCompanyId, Long headerBranchId) {
        permissions.requireWrite(me, "BILLING");
        Context ctx = context(me, headerCompanyId, headerBranchId);
        Sale sale = sales.findById(id).orElseThrow(() -> bad("Sale not found"));
        if (!Objects.equals(sale.getCompany().getId(),ctx.company().getId()) || !Objects.equals(sale.getBranch().getId(),ctx.branch().getId())) throw forbidden("Sale is outside branch scope");
        if ("CANCELLED".equalsIgnoreCase(sale.getStatus())) return Map.of("id",id,"status","CANCELLED");
        sale.setStatus("CANCELLED"); sales.save(sale);
        saleItems.findBySaleId(id).forEach(si -> {
            JewelleryItem item=si.getJewelleryItem(); item.setStatus("IN_STOCK"); items.save(item);
            stocks.findByCompanyId(ctx.company().getId()).stream().filter(s -> s.getBranch()!=null && Objects.equals(s.getBranch().getId(),ctx.branch().getId())).filter(s -> s.getJewelleryItem()!=null && Objects.equals(s.getJewelleryItem().getId(),item.getId())).forEach(s->{s.setStatus("IN_STOCK");stocks.save(s);});
            StockMovement m=new StockMovement();m.setCompany(ctx.company());m.setBranch(ctx.branch());m.setJewelleryItem(item);m.setMovementType("SALE_CANCEL");m.setQuantity(BigDecimal.ONE);m.setReferenceType("SALE");m.setReferenceId(id);m.setMovementDate(LocalDate.now());m.setCreatedBy(users.findById(me.getUserId()).orElse(null));movements.save(m);
        });
        audit.log(me,"CANCEL","SALE",id,null,Map.of("status","CANCELLED"));
        return Map.of("id",id,"status","CANCELLED");
    }

    private Context context(AuthenticatedUser me, Long companyId, Long branchId) {
        if ("APP_ADMIN".equals(me.getRole())) {
            if (companyId==null || branchId==null) throw bad("APP_ADMIN requires X-Company-Id and X-Branch-Id context");
        } else { companyId=me.getCompanyId(); branchId=me.getBranchId(); if(companyId==null||branchId==null) throw bad("Company and branch scope are required"); }
        Company c=companies.findById(companyId).orElseThrow(()->bad("Company not found")); Branch b=branches.findById(branchId).orElseThrow(()->bad("Branch not found"));
        if(!Objects.equals(b.getCompany().getId(),c.getId())) throw bad("Branch does not belong to company");
        if(!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(companyId,me.getCompanyId())) throw forbidden("Company access denied");
        return new Context(c,b);
    }
    private BigDecimal n(BigDecimal x){return x==null?BigDecimal.ZERO:x;}
    private ResponseStatusException bad(String x){return new ResponseStatusException(HttpStatus.BAD_REQUEST,x);}
    private ResponseStatusException forbidden(String x){return new ResponseStatusException(HttpStatus.FORBIDDEN,x);}
    private record Context(Company company, Branch branch){}
    private record PreparedLine(ItemRequest request, JewelleryItem item, BigDecimal gross, BigDecimal stone, BigDecimal net, BigDecimal rate, BigDecimal gold, BigDecimal wastage, BigDecimal lineSubtotal){}
    public record ItemRequest(Long jewelleryItemId, BigDecimal grossWeight, BigDecimal stoneWeight, BigDecimal netWeight, BigDecimal goldRate, BigDecimal wastagePercent, BigDecimal makingCharge, BigDecimal stoneCharge, BigDecimal otherCharge){}
    public record Request(Long customerId, List<ItemRequest> items, String invoiceNo, LocalDate saleDate, BigDecimal goldRate, BigDecimal gstPercent, BigDecimal discount, BigDecimal paymentAmount, String paymentMode, String paymentNo){}
}
