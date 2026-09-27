package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
        Customer customer = customers.findById(r.customerId()).orElseThrow(() -> bad("Customer not found"));
        if (!Objects.equals(customer.getCompany().getId(), ctx.company().getId())) throw forbidden("Customer is outside company scope");
        if (ctx.branch().getId() != null && customer.getBranch() != null && !Objects.equals(customer.getBranch().getId(), ctx.branch().getId())) throw forbidden("Customer is outside branch scope");

        JewelleryItem item = items.findById(r.jewelleryItemId()).orElseThrow(() -> bad("Jewellery item not found"));
        if (!Objects.equals(item.getCompany().getId(), ctx.company().getId()) || !Objects.equals(item.getBranch().getId(), ctx.branch().getId())) throw forbidden("Jewellery item is outside branch scope");
        if (!"IN_STOCK".equalsIgnoreCase(item.getStatus())) throw bad("Jewellery item is not available for sale: " + item.getStatus());

        BigDecimal gross = n(r.grossWeight());
        BigDecimal stone = n(r.stoneWeight());
        BigDecimal net = r.netWeight() == null ? gross.subtract(stone) : n(r.netWeight());
        BigDecimal gold = net.multiply(n(r.goldRate()));
        BigDecimal wastage = gold.multiply(n(r.wastagePercent())).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        BigDecimal subtotal = gold.add(wastage).add(n(r.makingCharge())).add(n(r.stoneCharge())).add(n(r.otherCharge()));
        BigDecimal gst = subtotal.multiply(n(r.gstPercent())).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(gst);

        Sale sale = new Sale();
        sale.setCompany(ctx.company()); sale.setBranch(ctx.branch()); sale.setCustomer(customer);
        sale.setInvoiceNo(r.invoiceNo()); sale.setSaleDate(r.saleDate() == null ? LocalDate.now() : r.saleDate());
        sale.setStatus("COMPLETED"); sale.setGoldRate(n(r.goldRate())); sale.setSubtotal(subtotal);
        sale.setDiscount(n(r.discount())); sale.setGst(gst); sale.setTotal(total); sale.setPaymentStatus("UNPAID");
        sale.setCreatedBy(users.findById(me.getUserId()).orElse(null));
        sale = sales.save(sale);

        SaleItem si = new SaleItem();
        si.setSale(sale); si.setJewelleryItem(item); si.setTagNo(item.getTag().getTagNo());
        si.setGrossWeight(gross); si.setStoneWeight(stone); si.setNetWeight(net);
        si.setPurity(item.getPurity().getName()); si.setGoldRate(n(r.goldRate())); si.setWastageValue(wastage);
        si.setMakingCharge(n(r.makingCharge())); si.setStoneCharge(n(r.stoneCharge())); si.setOtherCharge(n(r.otherCharge()));
        si.setGst(gst); si.setTotal(total); saleItems.save(si);

        item.setStatus("SOLD"); items.save(item);
        Stock stock = stocks.findByCompanyId(ctx.company().getId()).stream()
                .filter(s -> s.getBranch()!=null && Objects.equals(s.getBranch().getId(), ctx.branch().getId()))
                .filter(s -> s.getJewelleryItem()!=null && Objects.equals(s.getJewelleryItem().getId(), item.getId()))
                .findFirst().orElse(null);
        if (stock != null) { stock.setStatus("SOLD"); stocks.save(stock); }
        StockMovement movement = new StockMovement();
        movement.setCompany(ctx.company()); movement.setBranch(ctx.branch()); movement.setJewelleryItem(item);
        movement.setMovementType("SALE"); movement.setQuantity(BigDecimal.ONE); movement.setReferenceType("SALE"); movement.setReferenceId(sale.getId());
        movement.setMovementDate(sale.getSaleDate()); movement.setCreatedBy(users.findById(me.getUserId()).orElse(null));
        movements.save(movement);

        if (r.paymentAmount() != null && r.paymentAmount().compareTo(BigDecimal.ZERO) > 0) {
            Payment p = new Payment(); p.setCompany(ctx.company()); p.setBranch(ctx.branch()); p.setCustomer(customer); p.setSale(sale);
            p.setPaymentNo(r.paymentNo() == null || r.paymentNo().isBlank() ? "PAY-" + System.currentTimeMillis() : r.paymentNo());
            p.setPaymentType("RECEIPT"); p.setPaymentMode(r.paymentMode() == null ? "CASH" : r.paymentMode()); p.setAmount(r.paymentAmount());
            p.setPaymentDate(sale.getSaleDate()); p.setCreatedBy(users.findById(me.getUserId()).orElse(null)); payments.save(p);
            sale.setPaymentStatus(r.paymentAmount().compareTo(total) >= 0 ? "PAID" : "PARTIAL"); sales.save(sale);
        }
        audit.log(me, "CREATE", "SALE", sale.getId(), null, Map.of("invoiceNo",sale.getInvoiceNo(),"total",total,"itemId",item.getId()));
        return Map.of("id",sale.getId(),"invoiceNo",sale.getInvoiceNo(),"subtotal",subtotal,"gst",gst,"total",total,"paymentStatus",sale.getPaymentStatus());
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
    public record Request(Long customerId, Long jewelleryItemId, String invoiceNo, LocalDate saleDate, BigDecimal grossWeight, BigDecimal stoneWeight, BigDecimal netWeight, BigDecimal goldRate, BigDecimal wastagePercent, BigDecimal makingCharge, BigDecimal stoneCharge, BigDecimal otherCharge, BigDecimal gstPercent, BigDecimal discount, BigDecimal paymentAmount, String paymentMode, String paymentNo){}
}
