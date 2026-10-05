package com.jewellery360.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class Phase3WorkflowService {
    private final CompanyPropertyService companyProperties;
    private final CompanyRepository companies; private final BranchRepository branches;
    private final CustomerRepository customers; private final SaleRepository sales; private final SaleItemRepository saleItems;
    private final PaymentRepository payments; private final StockRepository stock; private final StockMovementRepository movements;
    private final JewelleryItemRepository items; private final PurchaseOrderRepository purchases; private final PurchaseItemRepository purchaseItems;
    private final SupplierRepository suppliers; private final OldGoldTransactionRepository oldGold; private final OldGoldItemRepository oldGoldItems;
    private final RepairOrderRepository repairs; private final CustomOrderRepository customOrders; private final CustomerAdvanceRepository advances;
    private final StockTransferRepository transfers; private final StockTransferItemRepository transferItems;
    private final SaleReturnRepository saleReturns; private final SaleReturnItemRepository saleReturnItems;
    private final PurchaseReturnRepository purchaseReturns; private final PurchaseReturnItemRepository purchaseReturnItems;
    private final AuditService audit; private final PermissionService permissions; private final HttpServletRequest request; private final AppUserRepository users;

    public record Context(Company company, Branch branch) {}

    public Context context(AuthenticatedUser me) {
        if ("APP_ADMIN".equals(me.getRole())) {
            Long cid=idHeader("X-Company-Id"), bid=idHeader("X-Branch-Id");
            Company c=companies.findById(cid).orElseThrow(()->bad("Company context not found"));
            Branch b=branches.findById(bid).orElseThrow(()->bad("Branch context not found"));
            if(!Objects.equals(b.getCompany().getId(),c.getId())) throw bad("Branch does not belong to company");
            return new Context(c,b);
        }
        if(me.getCompanyId()==null) throw bad("Company scope is required");
        Company c=companies.findById(me.getCompanyId()).orElseThrow(()->bad("Company not found"));
        Branch b=me.getBranchId()==null?null:branches.findById(me.getBranchId()).orElseThrow(()->bad("Branch not found"));
        return new Context(c,b);
    }
    private Long idHeader(String name){String v=request.getHeader(name); if(v==null||v.isBlank())throw bad("APP_ADMIN requires "+name+" context"); try{return Long.valueOf(v);}catch(NumberFormatException e){throw bad("Invalid "+name);}}
    private void module(AuthenticatedUser me,String m){permissions.requireModule(me,m);}
    private void write(AuthenticatedUser me,String m){permissions.requireWrite(me,m);}
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private ResponseStatusException forbidden(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);}
    private BigDecimal dec(JsonNode n,String f){return n.hasNonNull(f)?n.get(f).decimalValue():BigDecimal.ZERO;}
    private String text(JsonNode n,String f,String d){return n.hasNonNull(f)?n.get(f).asText():d;}
    private Long id(JsonNode n,String f){if(!n.hasNonNull(f))throw bad(f+" is required"); return n.get(f).asLong();}
    private <T>T get(org.springframework.data.jpa.repository.JpaRepository<T,Long> r,Long id,String label){return r.findById(id).orElseThrow(()->bad(label+" not found"));}
    private JewelleryItem itemForUpdate(Long id){return items.findByIdForUpdate(id).orElseThrow(()->bad("Jewellery item not found: "+id));}
    private Sale saleForUpdate(Long id){return sales.findByIdForUpdate(id).orElseThrow(()->bad("Sale not found: "+id));}
    private void scope(AuthenticatedUser me,Company c,Branch b){Context x=context(me); if(!Objects.equals(x.company().getId(),c.getId()))throw forbidden("Company access denied"); if(x.branch()!=null && b!=null&&!Objects.equals(x.branch().getId(),b.getId()))throw forbidden("Branch access denied");}

    @Transactional
    public Map<String,Object> completeSale(AuthenticatedUser me, JsonNode n){
        write(me,"BILLING"); Context ctx=context(me); Long customerId=id(n,"customerId"); Customer customer=get(customers,customerId,"Customer"); if(!Objects.equals(customer.getCompany().getId(),ctx.company().getId()))throw forbidden("Customer belongs to another company");
        JsonNode lines=n.get("items"); if(lines==null||!lines.isArray()||lines.isEmpty())throw bad("items are required");
        Sale s=new Sale(); s.setCompany(ctx.company()); s.setBranch(ctx.branch()); s.setCustomer(customer); s.setInvoiceNo(text(n,"invoiceNo","INV-"+System.currentTimeMillis())); s.setSaleDate(LocalDate.now()); s.setGoldRate(dec(n,"goldRate")); s.setDiscount(dec(n,"discount")); s.setStatus("COMPLETED");
        s=sales.save(s);
        BigDecimal subtotal=BigDecimal.ZERO; List<JewelleryItem> sold=new ArrayList<>();
        for(JsonNode l:lines){JewelleryItem ji=itemForUpdate(id(l,"jewelleryItemId")); if(!Objects.equals(ji.getCompany().getId(),ctx.company().getId())||!Objects.equals(ji.getBranch().getId(),ctx.branch().getId()))throw forbidden("Jewellery item is outside current branch"); if(!"IN_STOCK".equals(ji.getStatus()))throw bad("Item "+ji.getId()+" is not in stock");
            BigDecimal gross=ji.getGrossWeight(), net=ji.getNetWeight(); BigDecimal rate=dec(l,"goldRate"); if(rate.signum()==0)rate=s.getGoldRate(); BigDecimal gold=net.multiply(rate); BigDecimal wastage=gold.multiply(dec(l,"wastagePercent")).divide(BigDecimal.valueOf(100)); BigDecimal making=dec(l,"makingCharge").signum()==0?ji.getMakingCharge():dec(l,"makingCharge"); BigDecimal stone=dec(l,"stoneCharge").signum()==0?ji.getStoneValue():dec(l,"stoneCharge"); BigDecimal other=dec(l,"otherCharge"); BigDecimal base=gold.add(wastage).add(making).add(stone).add(other); BigDecimal gst=base.multiply(dec(l,"gstPercent")).divide(BigDecimal.valueOf(100)); BigDecimal total=base.add(gst);
            SaleItem si=new SaleItem(); si.setSale(s); si.setJewelleryItem(ji); si.setTagNo(ji.getTag().getTagNo()); si.setGrossWeight(gross); si.setStoneWeight(ji.getStoneWeight()); si.setNetWeight(net); si.setPurity(ji.getPurity().getKarat()!=null?ji.getPurity().getKarat():ji.getPurity().getName()); si.setGoldRate(rate); si.setWastageValue(wastage); si.setMakingCharge(making); si.setStoneCharge(stone); si.setOtherCharge(other); si.setGst(gst); si.setTotal(total); saleItems.save(si); subtotal=subtotal.add(total); sold.add(ji);
        }
        CompanyPropertyService.TaxSettings tax=companyProperties.getTaxSettings(ctx.company());
        BigDecimal gstAmount=subtotal.multiply(tax.totalRate()).divide(BigDecimal.valueOf(100),3,java.math.RoundingMode.HALF_UP);
        s.setSubtotal(subtotal); s.setGst(gstAmount); s.setTaxMode(tax.enabled()?tax.mode():"NONE"); s.setTaxRate(tax.enabled()?tax.rate():BigDecimal.ZERO);
        s.setCgstRate(tax.enabled()&&"CGST_SGST".equalsIgnoreCase(tax.mode())?tax.cgstRate():BigDecimal.ZERO); s.setSgstRate(tax.enabled()&&"CGST_SGST".equalsIgnoreCase(tax.mode())?tax.sgstRate():BigDecimal.ZERO);
        s.setTotal(subtotal.add(gstAmount).subtract(s.getDiscount()).max(BigDecimal.ZERO)); BigDecimal received=dec(n,"paymentAmount"); s.setPaymentStatus(received.compareTo(s.getTotal())>=0?"PAID":received.signum()>0?"PARTIAL":"UNPAID"); sales.save(s);
        for(SaleItem si:saleItems.findBySaleId(s.getId())){JewelleryItem ji=si.getJewelleryItem(); ji.setStatus("SOLD"); ji.setUpdatedAt(Instant.now()); items.save(ji); Stock st=stock.findByBranchIdAndJewelleryItemId(ctx.branch().getId(),ji.getId()).orElseThrow(()->bad("Stock not found for item "+ji.getId())); st.setStatus("SOLD"); st.setUpdatedAt(Instant.now()); stock.save(st); movement(me,ctx,ji,"SALE_OUT",s.getId(),"SALE",BigDecimal.ONE,"Sale "+s.getInvoiceNo());}
        if(received.signum()>0) createPayment(me,ctx,customer,s,null,received,text(n,"paymentMode","CASH"),text(n,"paymentReference",null),"SALE_RECEIPT");
        audit.log(me,"SALE_CREATE","Sale",s.getId(),null,Map.of("invoiceNo",s.getInvoiceNo(),"total",s.getTotal(),"paymentStatus",s.getPaymentStatus()));
        return saleSummary(s);
    }

    @Transactional
    public Map<String,Object> returnSale(AuthenticatedUser me,Long saleId,JsonNode n){
        write(me,"BILLING"); Context ctx=context(me); Sale s=saleForUpdate(saleId); scope(me,s.getCompany(),s.getBranch()); if("CANCELLED".equals(s.getStatus()))throw bad("Cancelled sale cannot be returned"); JsonNode arr=n.get("items"); if(arr==null||!arr.isArray()||arr.isEmpty())throw bad("items are required");
        SaleReturn sr=new SaleReturn(); sr.setCompany(ctx.company()); sr.setBranch(ctx.branch()); sr.setSale(s); sr.setReturnNo(text(n,"returnNo","RET-"+System.currentTimeMillis())); sr.setReturnDate(LocalDate.now()); sr.setReason(text(n,"reason",null)); sr.setCreatedBy(getUser(me)); BigDecimal total=BigDecimal.ZERO; sr.setAmount(BigDecimal.ZERO); sr=saleReturns.save(sr);
        for(JsonNode x:arr){SaleItem si=get(saleItems,id(x,"saleItemId"),"Sale item"); if(!Objects.equals(si.getSale().getId(),s.getId()))throw bad("Sale item does not belong to sale"); if(saleReturnItems.existsBySaleItemId(si.getId()))throw bad("Sale item already returned"); JewelleryItem ji=si.getJewelleryItem(); SaleReturnItem ri=new SaleReturnItem();ri.setReturnRecord(sr);ri.setSaleItem(si);ri.setJewelleryItem(ji);ri.setAmount(si.getTotal());ri.setReason(text(x,"reason",sr.getReason()));saleReturnItems.save(ri); total=total.add(si.getTotal()); ji.setStatus("IN_STOCK");ji.setUpdatedAt(Instant.now());items.save(ji);Stock st=stock.findByBranchIdAndJewelleryItemId(s.getBranch().getId(),ji.getId()).orElseThrow(()->bad("Stock not found"));st.setStatus("IN_STOCK");st.setUpdatedAt(Instant.now());stock.save(st);movement(me,ctx,ji,"SALE_RETURN_IN",sr.getId(),"SALE_RETURN",BigDecimal.ONE,"Return "+sr.getReturnNo());}
        sr.setAmount(total);saleReturns.save(sr); List<SaleItem> all=saleItems.findBySaleId(s.getId()); long returned=all.stream().filter(i->saleReturnItems.existsBySaleItemId(i.getId())).count(); if(returned==all.size())s.setStatus("RETURNED");else s.setStatus("PARTIALLY_RETURNED");sales.save(s);audit.log(me,"SALE_RETURN","SaleReturn",sr.getId(),null,Map.of("saleId",saleId,"amount",total));return Map.of("id",sr.getId(),"returnNo",sr.getReturnNo(),"amount",total,"saleStatus",s.getStatus());
    }

    @Transactional
    public Map<String,Object> createTransfer(AuthenticatedUser me,JsonNode n){
        write(me,"INVENTORY"); Context ctx=context(me); Long from=id(n,"fromBranchId"), to=id(n,"toBranchId"); Branch fb=get(branches,from,"From branch"),tb=get(branches,to,"To branch"); if(!Objects.equals(fb.getCompany().getId(),ctx.company().getId())||!Objects.equals(tb.getCompany().getId(),ctx.company().getId()))throw forbidden("Branch outside company"); if(Objects.equals(from,to))throw bad("From and To branch must differ"); JsonNode arr=n.get("items");if(arr==null||!arr.isArray()||arr.isEmpty())throw bad("items are required");
        StockTransfer t=new StockTransfer();t.setCompany(ctx.company());t.setFromBranch(fb);t.setToBranch(tb);t.setTransferNo(text(n,"transferNo","TRF-"+System.currentTimeMillis()));t.setTransferDate(LocalDate.now());t.setStatus("DRAFT");t.setNotes(text(n,"notes",null));t=transfers.save(t);for(JsonNode x:arr){Long iid=id(x,"jewelleryItemId");JewelleryItem ji=get(items,iid,"Jewellery item");Stock st=stock.findByBranchIdAndJewelleryItemId(from,iid).orElseThrow(()->bad("Item is not in source branch"));if(!"IN_STOCK".equals(st.getStatus()))throw bad("Item is not available for transfer: "+iid);StockTransferItem ti=new StockTransferItem();ti.setTransfer(t);ti.setJewelleryItem(ji);ti.setQuantity(BigDecimal.ONE);transferItems.save(ti);}return Map.of("id",t.getId(),"transferNo",t.getTransferNo(),"status",t.getStatus());
    }

    @Transactional
    public Map<String,Object> completeTransfer(AuthenticatedUser me,Long transferId){
        write(me,"INVENTORY"); Context ctx=context(me);StockTransfer t=get(transfers,transferId,"Transfer");scope(me,t.getCompany(),null);if(!"DRAFT".equals(t.getStatus()))throw bad("Only DRAFT transfers can be completed");List<StockTransferItem> its=transferItems.findByTransferId(t.getId());if(its.isEmpty())throw bad("Transfer has no items");for(StockTransferItem ti:its){Long iid=ti.getJewelleryItem().getId();Stock source=stock.findByBranchIdAndJewelleryItemId(t.getFromBranch().getId(),iid).orElseThrow(()->bad("Source stock not found"));if(!"IN_STOCK".equals(source.getStatus()))throw bad("Source item is not available");source.setStatus("TRANSFERRED");source.setUpdatedAt(Instant.now());stock.save(source);Stock dest=stock.findByBranchIdAndJewelleryItemId(t.getToBranch().getId(),iid).orElseGet(()->{Stock x=new Stock();x.setCompany(t.getCompany());x.setBranch(t.getToBranch());x.setJewelleryItem(ti.getJewelleryItem());return x;});dest.setStatus("IN_STOCK");dest.setUpdatedAt(Instant.now());stock.save(dest);ti.getJewelleryItem().setBranch(t.getToBranch());ti.getJewelleryItem().setStatus("IN_STOCK");ti.getJewelleryItem().setUpdatedAt(Instant.now());items.save(ti.getJewelleryItem());movement(me,new Context(t.getCompany(),t.getToBranch()),ti.getJewelleryItem(),"TRANSFER_IN",t.getId(),"STOCK_TRANSFER",BigDecimal.ONE,"Transfer "+t.getTransferNo());}
        t.setStatus("COMPLETED");transfers.save(t);audit.log(me,"STOCK_TRANSFER_COMPLETE","StockTransfer",t.getId(),null,Map.of("transferNo",t.getTransferNo(),"items",its.size()));return Map.of("id",t.getId(),"status",t.getStatus());
    }

    @Transactional
    public Map<String,Object> createPurchase(AuthenticatedUser me,JsonNode n){
        write(me,"PURCHASES");Context ctx=context(me);Supplier supplier=get(suppliers,id(n,"supplierId"),"Supplier");if(!Objects.equals(supplier.getCompany().getId(),ctx.company().getId()))throw forbidden("Supplier belongs to another company");JsonNode arr=n.get("items");if(arr==null||!arr.isArray()||arr.isEmpty())throw bad("items are required");PurchaseOrder p=new PurchaseOrder();p.setCompany(ctx.company());p.setBranch(ctx.branch());p.setSupplier(supplier);p.setPurchaseNo(text(n,"purchaseNo","PUR-"+System.currentTimeMillis()));p.setPurchaseDate(LocalDate.now());p.setStatus("RECEIVED");p.setNotes(text(n,"notes",null));BigDecimal subtotal=BigDecimal.ZERO;p=purchases.save(p);
        for(JsonNode x:arr){PurchaseItem pi=new PurchaseItem();pi.setPurchase(p);pi.setDescription(text(x,"description","Jewellery"));pi.setQuantity(dec(x,"quantity").signum()==0?BigDecimal.ONE:dec(x,"quantity"));pi.setGrossWeight(dec(x,"grossWeight"));pi.setNetWeight(dec(x,"netWeight"));pi.setRate(dec(x,"rate"));pi.setAmount(dec(x,"amount"));if(x.hasNonNull("jewelleryItemId")){JewelleryItem ji=get(items,x.get("jewelleryItemId").asLong(),"Jewellery item");if(!Objects.equals(ji.getCompany().getId(),ctx.company().getId()))throw forbidden("Jewellery item outside company");pi.setJewelleryItem(ji);Stock st=stock.findByBranchIdAndJewelleryItemId(ctx.branch().getId(),ji.getId()).orElseGet(()->{Stock z=new Stock();z.setCompany(ctx.company());z.setBranch(ctx.branch());z.setJewelleryItem(ji);return z;});st.setStatus("IN_STOCK");st.setUpdatedAt(Instant.now());stock.save(st);ji.setBranch(ctx.branch());ji.setStatus("IN_STOCK");items.save(ji);movement(me,ctx,ji,"PURCHASE_IN",p.getId(),"PURCHASE",BigDecimal.ONE,"Purchase "+p.getPurchaseNo());}purchaseItems.save(pi);subtotal=subtotal.add(pi.getAmount());}
        p.setSubtotal(subtotal);p.setGst(dec(n,"gst"));p.setTotal(subtotal.add(p.getGst()));purchases.save(p);audit.log(me,"PURCHASE_RECEIVE","PurchaseOrder",p.getId(),null,Map.of("purchaseNo",p.getPurchaseNo(),"total",p.getTotal()));return Map.of("id",p.getId(),"purchaseNo",p.getPurchaseNo(),"total",p.getTotal(),"status",p.getStatus());
    }

    @Transactional
    public Map<String,Object> returnPurchase(AuthenticatedUser me,Long purchaseId,JsonNode n){
        write(me,"PURCHASES");Context ctx=context(me);PurchaseOrder p=get(purchases,purchaseId,"Purchase");scope(me,p.getCompany(),p.getBranch());JsonNode arr=n.get("items");if(arr==null||!arr.isArray()||arr.isEmpty())throw bad("items are required");PurchaseReturn pr=new PurchaseReturn();pr.setCompany(ctx.company());pr.setBranch(ctx.branch());pr.setPurchase(p);pr.setReturnNo(text(n,"returnNo","PRET-"+System.currentTimeMillis()));pr.setReturnDate(LocalDate.now());pr.setReason(text(n,"reason",null));pr.setCreatedBy(getUser(me));pr=purchaseReturns.save(pr);BigDecimal total=BigDecimal.ZERO;for(JsonNode x:arr){PurchaseItem pi=get(purchaseItems,id(x,"purchaseItemId"),"Purchase item");if(!Objects.equals(pi.getPurchase().getId(),p.getId()))throw bad("Purchase item does not belong to purchase");PurchaseReturnItem ri=new PurchaseReturnItem();ri.setReturnRecord(pr);ri.setPurchaseItem(pi);ri.setJewelleryItem(pi.getJewelleryItem());ri.setAmount(pi.getAmount());ri.setReason(text(x,"reason",pr.getReason()));purchaseReturnItems.save(ri);if(pi.getJewelleryItem()!=null){JewelleryItem ji=pi.getJewelleryItem();Stock st=stock.findByBranchIdAndJewelleryItemId(p.getBranch().getId(),ji.getId()).orElse(null);if(st!=null){st.setStatus("RETURNED");st.setUpdatedAt(Instant.now());stock.save(st);}ji.setStatus("RETURNED");ji.setUpdatedAt(Instant.now());items.save(ji);movement(me,ctx,ji,"PURCHASE_RETURN_OUT",pr.getId(),"PURCHASE_RETURN",BigDecimal.ONE,"Purchase return "+pr.getReturnNo());}total=total.add(pi.getAmount());}pr.setAmount(total);purchaseReturns.save(pr);audit.log(me,"PURCHASE_RETURN","PurchaseReturn",pr.getId(),null,Map.of("purchaseId",purchaseId,"amount",total));return Map.of("id",pr.getId(),"returnNo",pr.getReturnNo(),"amount",total);}

    @Transactional
    public Map<String,Object> createOldGold(AuthenticatedUser me,JsonNode n){
        write(me,"OLD GOLD");Context ctx=context(me);Customer c=get(customers,id(n,"customerId"),"Customer");if(!Objects.equals(c.getCompany().getId(),ctx.company().getId()))throw forbidden("Customer outside company");JsonNode arr=n.get("items");if(arr==null||!arr.isArray()||arr.isEmpty())throw bad("items are required");OldGoldTransaction t=new OldGoldTransaction();t.setCompany(ctx.company());t.setBranch(ctx.branch());t.setCustomer(c);t.setTransactionNo(text(n,"transactionNo","OG-"+System.currentTimeMillis()));t.setTransactionDate(LocalDate.now());t.setTransactionType(text(n,"transactionType","PURCHASE"));t.setStatus("COMPLETED");BigDecimal total=BigDecimal.ZERO;t=oldGold.save(t);for(JsonNode x:arr){OldGoldItem oi=new OldGoldItem();oi.setTransaction(t);oi.setDescription(text(x,"description","Old gold"));oi.setGrossWeight(dec(x,"grossWeight"));oi.setStoneWeight(dec(x,"stoneWeight"));oi.setNetWeight(dec(x,"netWeight"));oi.setPurity(text(x,"purity","22K"));oi.setRate(dec(x,"rate"));oi.setAmount(dec(x,"amount"));oldGoldItems.save(oi);total=total.add(oi.getAmount());}t.setTotalAmount(total);oldGold.save(t);BigDecimal paid=dec(n,"paymentAmount");if(paid.signum()>0)createPayment(me,ctx,c,null,null,paid,text(n,"paymentMode","CASH"),text(n,"paymentReference",null),"OLD_GOLD");audit.log(me,"OLD_GOLD_CREATE","OldGoldTransaction",t.getId(),null,Map.of("transactionNo",t.getTransactionNo(),"amount",total));return Map.of("id",t.getId(),"transactionNo",t.getTransactionNo(),"amount",total);}

    @Transactional public Map<String,Object> updateRepair(AuthenticatedUser me,Long id,JsonNode n){write(me,"SERVICES");RepairOrder r=get(repairs,id,"Repair order");scope(me,r.getCompany(),r.getBranch());if(n.hasNonNull("status"))r.setStatus(n.get("status").asText());if(n.hasNonNull("finalAmount"))r.setFinalAmount(dec(n,"finalAmount"));if(n.hasNonNull("notes"))r.setNotes(n.get("notes").asText());if("COMPLETED".equals(r.getStatus()))r.setCompletedDate(LocalDate.now());repairs.save(r);audit.log(me,"REPAIR_UPDATE","RepairOrder",id,null,Map.of("status",r.getStatus(),"finalAmount",r.getFinalAmount()));return Map.of("id",id,"status",r.getStatus(),"finalAmount",r.getFinalAmount());}
    @Transactional public Map<String,Object> updateCustomOrder(AuthenticatedUser me,Long id,JsonNode n){write(me,"SERVICES");CustomOrder r=get(customOrders,id,"Custom order");scope(me,r.getCompany(),r.getBranch());if(n.hasNonNull("status"))r.setStatus(n.get("status").asText());if(n.hasNonNull("finalAmount"))r.setFinalAmount(dec(n,"finalAmount"));if(n.hasNonNull("deliveryDate"))r.setDeliveryDate(LocalDate.parse(n.get("deliveryDate").asText()));if(n.hasNonNull("notes"))r.setNotes(n.get("notes").asText());customOrders.save(r);audit.log(me,"CUSTOM_ORDER_UPDATE","CustomOrder",id,null,Map.of("status",r.getStatus(),"finalAmount",r.getFinalAmount()));return Map.of("id",id,"status",r.getStatus(),"finalAmount",r.getFinalAmount());}

    @Transactional public Map<String,Object> createAdvance(AuthenticatedUser me,JsonNode n){write(me,"PAYMENTS");Context ctx=context(me);Customer c=get(customers,id(n,"customerId"),"Customer");if(!Objects.equals(c.getCompany().getId(),ctx.company().getId()))throw forbidden("Customer outside company");BigDecimal amount=dec(n,"amount");if(amount.signum()<=0)throw bad("amount must be positive");CustomerAdvance a=new CustomerAdvance();a.setCompany(ctx.company());a.setBranch(ctx.branch());a.setCustomer(c);a.setAdvanceNo(text(n,"advanceNo","ADV-"+System.currentTimeMillis()));a.setAdvanceDate(LocalDate.now());a.setAmount(amount);a.setUtilizedAmount(BigDecimal.ZERO);a.setBalanceAmount(amount);a.setStatus("OPEN");a.setNotes(text(n,"notes",null));a=advances.save(a);audit.log(me,"ADVANCE_CREATE","CustomerAdvance",a.getId(),null,Map.of("advanceNo",a.getAdvanceNo(),"amount",amount));return Map.of("id",a.getId(),"advanceNo",a.getAdvanceNo(),"balanceAmount",a.getBalanceAmount());}

    public Map<String,Object> customerOutstanding(AuthenticatedUser me,Long customerId){module(me,"CUSTOMERS");Context ctx=context(me);Customer c=get(customers,customerId,"Customer");if(!Objects.equals(c.getCompany().getId(),ctx.company().getId()))throw forbidden("Customer outside company");List<Sale> ss=sales.findByCompanyId(ctx.company().getId()).stream().filter(s->Objects.equals(s.getCustomer().getId(),customerId)&&!"CANCELLED".equals(s.getStatus())).toList();BigDecimal billed=ss.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal paid=payments.findByCustomerId(customerId).stream().filter(p->p.getSale()!=null).map(Payment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal returned=saleReturns.findByCompanyId(ctx.company().getId()).stream().filter(r->Objects.equals(r.getSale().getCustomer().getId(),customerId)).map(SaleReturn::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal advances=advancesFor(customerId);BigDecimal due=billed.subtract(paid).subtract(returned).subtract(advances).max(BigDecimal.ZERO);return Map.of("customerId",customerId,"customerName",c.getName(),"billed",billed,"paid",paid,"returns",returned,"advances",advances,"outstanding",due);}
    private BigDecimal advancesFor(Long customerId){return advances.findByCompanyId(customerCompany(customerId)).stream().filter(a->Objects.equals(a.getCustomer().getId(),customerId)).map(CustomerAdvance::getBalanceAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private Long customerCompany(Long customerId){return customers.findById(customerId).map(c->c.getCompany().getId()).orElse(0L);}

    public Map<String,Object> normalizedReport(AuthenticatedUser me,String type){return normalizedReport(me,type,null,null);}
    public Map<String,Object> normalizedReport(AuthenticatedUser me,String type,java.time.LocalDate from,java.time.LocalDate to){module(me,"REPORTS");Context ctx=context(me);String t=type==null?"SALES":type.toUpperCase();Map<String,Object> out=new LinkedHashMap<>();out.put("type",t);if("SALES".equals(t)){List<Sale> rows=sales.findByCompanyId(ctx.company().getId()).stream().filter(s->ctx.branch()==null||Objects.equals(s.getBranch().getId(),ctx.branch().getId())).filter(s->from==null||!s.getSaleDate().isBefore(from)).filter(s->to==null||!s.getSaleDate().isAfter(to)).sorted(Comparator.comparing(Sale::getSaleDate,Comparator.reverseOrder()).thenComparing(Sale::getId,Comparator.reverseOrder())).toList();out.put("from",from==null?null:from.toString());out.put("to",to==null?null:to.toString());out.put("count",rows.size());out.put("total",rows.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO,BigDecimal::add));out.put("rows",rows.stream().limit(200).map(this::saleSummary).toList());}else if("STOCK".equals(t)){List<Stock> rows=stock.findByCompanyId(ctx.company().getId()).stream().filter(s->ctx.branch()==null||Objects.equals(s.getBranch().getId(),ctx.branch().getId())).toList();out.put("count",rows.size());out.put("inStock",rows.stream().filter(s->"IN_STOCK".equals(s.getStatus())).count());out.put("rows",rows.stream().limit(200).map(s->Map.of("id",s.getId(),"itemId",s.getJewelleryItem().getId(),"status",s.getStatus(),"tag",s.getJewelleryItem().getTag().getTagNo(),"branch",s.getBranch().getName())).toList());}else if("CUSTOMERS".equals(t)){List<Customer> rows=customers.findByCompanyId(ctx.company().getId());out.put("count",rows.size());out.put("rows",rows.stream().limit(200).map(c->customerOutstanding(me,c.getId())).toList());}else if("GOLD".equals(t)){out.put("rows",List.of("Use /api/domain/gold-rates for historical gold-rate records"));}else if("PROFIT".equals(t)){out.put("rows",List.of());out.put("note","Profit requires cost basis per tagged item; no profit is fabricated without a recorded purchase cost." );}return out;}

    private Map<String,Object> saleSummary(Sale s){
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",s.getId()); out.put("invoiceNo",s.getInvoiceNo()); out.put("date",s.getSaleDate());
        out.put("status",s.getStatus()); out.put("paymentStatus",s.getPaymentStatus()); out.put("total",s.getTotal());
        out.put("customer",s.getCustomer()==null?"":s.getCustomer().getName());
        out.put("branch",s.getBranch()==null?"":s.getBranch().getName());
        return out;
    }
    private void movement(AuthenticatedUser me,Context c,JewelleryItem ji,String type,Long refId,String refType,BigDecimal qty,String notes){StockMovement m=new StockMovement();m.setCompany(c.company());m.setBranch(c.branch());m.setJewelleryItem(ji);m.setMovementType(type);m.setQuantity(qty);m.setReferenceId(refId);m.setReferenceType(refType);m.setMovementDate(LocalDate.now());m.setNotes(notes);m.setCreatedBy(getUser(me));movements.save(m);}
    private AppUser getUser(AuthenticatedUser me){return me==null?null:users.findById(me.getUserId()).orElse(null);}
    private Payment createPayment(AuthenticatedUser me,Context c,Customer customer,Sale sale,PurchaseOrder purchase,BigDecimal amount,String mode,String ref,String type){Payment p=new Payment();p.setCompany(c.company());p.setBranch(c.branch());p.setCustomer(customer);p.setSale(sale);p.setPurchase(purchase);p.setPaymentNo("PAY-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,6));p.setPaymentType(type);p.setPaymentMode(mode);p.setAmount(amount);p.setPaymentDate(LocalDate.now());p.setReference(ref);p.setCreatedAt(Instant.now());return payments.save(p);}
}
