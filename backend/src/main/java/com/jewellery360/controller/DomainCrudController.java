package com.jewellery360.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/domain")
@RequiredArgsConstructor
public class DomainCrudController {
    private final ObjectMapper mapper;
    private final PermissionService permissions;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final CustomerRepository customers;
    private final JewelleryCategoryRepository categories;
    private final JewelleryDesignRepository designs;
    private final JewelleryProductRepository products;
    private final JewelleryTagRepository tags;
    private final JewelleryItemRepository items;
    private final PurityMasterRepository purities;
    private final GoldRateRepository goldRates;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchases;
    private final StockRepository stock;
    private final StockMovementRepository stockMovements;
    private final StockTransferRepository transfers;
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final OldGoldTransactionRepository oldGold;
    private final RepairOrderRepository repairs;
    private final CustomOrderRepository customOrders;
    private final CustomerAdvanceRepository advances;
    private final WhatsappLogRepository whatsappLogs;
    private final NotificationLogRepository notificationLogs;
    private final HttpServletRequest request;

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(
            @AuthenticationPrincipal AuthenticatedUser me) {

        List<Customer> all = list(customers, me, "CUSTOMERS");

        return all.stream()
                .map(this::customerResponse)
                .toList();
    }

    @PostMapping("/customers")
    @Transactional
    public Map<String, Object> customerCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "CUSTOMERS");
        Customer x = convert(n, Customer.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        Customer saved = customers.save(x);
        return customerResponse(saved);
    }

    @PutMapping("/customers/{id}")
    @Transactional
    public Customer customerUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "CUSTOMERS");
        Customer x = get(customers, id);
        checkScope(me, x.getCompany(), x.getBranch());
        merge(n, x);
        x.setCompany(requireCompany(me, n.path("companyId").asLong(0), x.getCompany()));
        x.setBranch(requireBranch(me, n.path("branchId").asLong(0), x.getBranch()));
        return customers.save(x);
    }

    @DeleteMapping("/customers/{id}")
    @Transactional
    public void customerDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "CUSTOMERS");
        Customer x = get(customers, id);
        checkScope(me, x.getCompany(), x.getBranch());
        x.setActive(false);
        customers.save(x);
    }

    @GetMapping("/jewellery/categories")
    public List<JewelleryCategory> categories(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(categories, me, "JEWELLERY");
    }

    @PostMapping("/jewellery/categories")
    @Transactional
    public JewelleryCategory categoryCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "JEWELLERY");
        JewelleryCategory x = convert(n, JewelleryCategory.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), null);
        if (categories.existsByCompanyIdAndCodeIgnoreCase(x.getCompany().getId(), x.getCode()))
            throw conflict("Category code already exists for this company: " + x.getCode());
        return categories.save(x);
    }

    @GetMapping("/jewellery/designs")
    public List<JewelleryDesign> designs(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(designs, me, "JEWELLERY");
    }

    @PostMapping("/jewellery/designs")
    @Transactional
    public JewelleryDesign designCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "JEWELLERY");
        JewelleryDesign x = convert(n, JewelleryDesign.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), null);
        if (designs.existsByCompanyIdAndCodeIgnoreCase(x.getCompany().getId(), x.getCode()))
            throw conflict("Design code already exists for this company: " + x.getCode());
        x.setCategory(categories.findById(requiredId(n, "categoryId")).orElseThrow(() -> bad("Category not found")));
        return designs.save(x);
    }

    @GetMapping("/jewellery/products")
    public List<JewelleryProduct> products(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(products, me, "JEWELLERY");
    }

    @PostMapping("/jewellery/products")
    @Transactional
    public JewelleryProduct productCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "JEWELLERY");
        JewelleryProduct x = convert(n, JewelleryProduct.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), null);
        if (products.existsByCompanyIdAndSkuIgnoreCase(x.getCompany().getId(), x.getSku()))
            throw conflict("Product SKU already exists for this company: " + x.getSku());
        x.setCategory(categories.findById(requiredId(n, "categoryId")).orElseThrow(() -> bad("Category not found")));
        if (n.hasNonNull("designId"))
            x.setDesign(designs.findById(n.get("designId").asLong()).orElseThrow(() -> bad("Design not found")));
        return products.save(x);
    }

    @GetMapping("/jewellery/tags")
    public List<JewelleryTag> tags(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(tags, me, "JEWELLERY");
    }

    @PostMapping("/jewellery/tags")
    @Transactional
    public JewelleryTag tagCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "JEWELLERY");
        JewelleryTag x = convert(n, JewelleryTag.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        if (tags.existsByCompanyIdAndTagNoIgnoreCase(x.getCompany().getId(), x.getTagNo()))
            throw conflict("Tag number already exists for this company: " + x.getTagNo());
        if (tags.existsByCompanyIdAndBarcode(x.getCompany().getId(), x.getBarcode()))
            throw conflict("Barcode already exists for this company: " + x.getBarcode());
        return tags.save(x);
    }

    @GetMapping("/jewellery/items")
    public List<JewelleryItem> items(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(items, me, "JEWELLERY");
    }

    @PostMapping("/jewellery/items")
    @Transactional
    public JewelleryItem itemCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "JEWELLERY");
        JewelleryItem x = convert(n, JewelleryItem.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        JewelleryProduct product = products.findById(requiredId(n, "productId")).orElseThrow(() -> bad("Product not found"));
        JewelleryTag tag = tags.findById(requiredId(n, "tagId")).orElseThrow(() -> bad("Tag not found"));
        PurityMaster purity = purities.findById(requiredId(n, "purityId")).orElseThrow(() -> bad("Purity not found"));
        if (!Objects.equals(product.getCompany().getId(), x.getCompany().getId()) || !Objects.equals(tag.getCompany().getId(), x.getCompany().getId()) || !Objects.equals(purity.getCompany().getId(), x.getCompany().getId()))
            throw forbidden("Referenced jewellery master belongs to another company");
        if (!Objects.equals(tag.getBranch().getId(), x.getBranch().getId()))
            throw forbidden("Tag belongs to another branch");
        x.setProduct(product);
        x.setTag(tag);
        x.setPurity(purity);
        x = items.save(x);
        Stock s = new Stock();
        s.setCompany(x.getCompany());
        s.setBranch(x.getBranch());
        s.setJewelleryItem(x);
        s.setStatus("IN_STOCK");
        stock.save(s);
        return x;
    }

    @PutMapping("/jewellery/items/{id}")
    @Transactional
    public JewelleryItem itemUpdate(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long id,
            @RequestBody JsonNode n) {

        requireUpdate(me, "JEWELLERY");

        // Lock the row while an admin is editing it so two concurrent
        // updates cannot silently overwrite the same inventory item.
        JewelleryItem x = items.findByIdForUpdate(id)
                .orElseThrow(() -> bad("Jewellery stock item not found"));

        checkScope(me, x.getCompany(), x.getBranch());

        // Historical inventory must not be edited after it leaves stock.
        if (!"IN_STOCK".equalsIgnoreCase(String.valueOf(x.getStatus()))) {
            throw bad("Only available stock can be edited.");
        }

        if (n.hasNonNull("productId")) {
            JewelleryProduct product = products.findById(
                    requiredId(n, "productId")
            ).orElseThrow(() -> bad("Product not found"));

            if (!Objects.equals(product.getCompany().getId(), x.getCompany().getId())) {
                throw forbidden("Product belongs to another company");
            }

            x.setProduct(product);
        }

        if (n.hasNonNull("tagId")) {
            JewelleryTag tag = tags.findById(
                    requiredId(n, "tagId")
            ).orElseThrow(() -> bad("Tag not found"));

            if (!Objects.equals(tag.getCompany().getId(), x.getCompany().getId())) {
                throw forbidden("Tag belongs to another company");
            }

            if (tag.getBranch() == null ||
                    !Objects.equals(tag.getBranch().getId(), x.getBranch().getId())) {
                throw forbidden("Tag belongs to another branch");
            }

            if (items.existsByTagIdAndIdNot(tag.getId(), x.getId())) {
                throw conflict("Tag is already assigned to another jewellery item.");
            }

            x.setTag(tag);
        }

        if (n.hasNonNull("purityId")) {
            PurityMaster purity = purities.findById(
                    requiredId(n, "purityId")
            ).orElseThrow(() -> bad("Purity not found"));

            if (!Objects.equals(purity.getCompany().getId(), x.getCompany().getId())) {
                throw forbidden("Purity belongs to another company");
            }

            x.setPurity(purity);
        }

        if (n.has("grossWeight") && !n.get("grossWeight").isNull()) {
            x.setGrossWeight(n.get("grossWeight").decimalValue());
        }

        if (n.has("stoneWeight") && !n.get("stoneWeight").isNull()) {
            x.setStoneWeight(n.get("stoneWeight").decimalValue());
        }

        if (n.has("netWeight") && !n.get("netWeight").isNull()) {
            x.setNetWeight(n.get("netWeight").decimalValue());
        }

        if (n.has("huid")) {
            x.setHuid(n.get("huid").isNull() ? null : n.get("huid").asText().trim());
        }

        if (n.has("wastagePercent") && !n.get("wastagePercent").isNull()) {
            x.setWastagePercent(n.get("wastagePercent").decimalValue());
        }

        if (n.has("makingCharge") && !n.get("makingCharge").isNull()) {
            x.setMakingCharge(n.get("makingCharge").decimalValue());
        }

        if (n.has("stoneValue") && !n.get("stoneValue").isNull()) {
            x.setStoneValue(n.get("stoneValue").decimalValue());
        }

        if (x.getGrossWeight() == null ||
                x.getGrossWeight().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw bad("Gross weight must be greater than 0.");
        }

        if (x.getStoneWeight() == null) {
            x.setStoneWeight(java.math.BigDecimal.ZERO);
        }

        if (x.getStoneWeight().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw bad("Stone weight cannot be negative.");
        }

        if (x.getStoneWeight().compareTo(x.getGrossWeight()) > 0) {
            throw bad("Stone weight cannot exceed gross weight.");
        }

        if (x.getNetWeight() == null ||
                x.getNetWeight().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw bad("Net weight cannot be negative.");
        }

        if (x.getNetWeight().compareTo(x.getGrossWeight()) > 0) {
            throw bad("Net weight cannot exceed gross weight.");
        }

        if (x.getWastagePercent() == null ||
                x.getWastagePercent().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw bad("Wastage cannot be negative.");
        }

        if (x.getMakingCharge() == null ||
                x.getMakingCharge().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw bad("Making charge cannot be negative.");
        }

        if (x.getStoneValue() == null ||
                x.getStoneValue().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw bad("Stone value cannot be negative.");
        }

        x.setUpdatedAt(java.time.Instant.now());

        return items.save(x);
    }

    @GetMapping("/gold-rates")
    public List<GoldRate> goldRates(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(goldRates, me, "GOLD & RATES");
    }

    @PostMapping("/gold-rates")
    @Transactional
    public GoldRate goldRateCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "GOLD & RATES");
        GoldRate x = convert(n, GoldRate.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        Long purityId = requiredId(n, "purityId");
        x.setPurity(purities.findById(purityId).orElseThrow(() -> bad("Purity not found")));
        if (x.getRateDate() == null) x.setRateDate(java.time.LocalDate.now());
        GoldRate existing = goldRates.findByCompanyIdAndBranchIdAndPurityIdAndRateDate(
                x.getCompany().getId(), x.getBranch().getId(), purityId, x.getRateDate()).orElse(null);
        if (existing != null) {
            existing.setRatePerGram(x.getRatePerGram());
            existing.setSource(x.getSource());
            existing.setActive(x.isActive());
            return goldRates.save(existing);
        }
        return goldRates.save(x);
    }

    @GetMapping("/gold-rates/history/{purityId}")
    public List<GoldRate> goldRateHistory(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long purityId) {
        permissions.requireModule(me, "GOLD & RATES");
        Long cid = "APP_ADMIN".equals(me.getRole()) ? Long.parseLong(request.getHeader("X-Company-Id")) : me.getCompanyId();
        Long bid = "APP_ADMIN".equals(me.getRole()) ? Long.parseLong(request.getHeader("X-Branch-Id")) : me.getBranchId();
        if (cid == null) throw bad("Company scope is required");
        return goldRates.findByCompanyId(cid).stream().filter(r -> r.getPurity().getId().equals(purityId) && (bid == null || r.getBranch().getId().equals(bid))).sorted(java.util.Comparator.comparing(GoldRate::getRateDate).reversed()).toList();
    }

    @GetMapping("/purities")
    public List<PurityMaster> purities(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(purities, me, "GOLD & RATES");
    }

    @PostMapping("/purities")
    @Transactional
    public PurityMaster purityCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "GOLD & RATES");
        PurityMaster x = convert(n, PurityMaster.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), null);

        String name = x.getName() == null ? "" : x.getName().trim();
        if (name.isBlank()) throw bad("Purity name is required");
        if (x.getFineness() == null
                || x.getFineness().compareTo(java.math.BigDecimal.ZERO) <= 0
                || x.getFineness().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw bad("Fineness must be greater than 0 and up to 1.000");
        }

        if (purities.existsByCompanyIdAndNameIgnoreCase(x.getCompany().getId(), name)) {
            throw conflict("Purity name already exists for this company: " + name);
        }

        x.setName(name);
        if (x.getKarat() != null) x.setKarat(x.getKarat().trim());
        if (x.getDescription() != null) x.setDescription(x.getDescription().trim());
        return purities.save(x);
    }

    @PutMapping("/purities/{id}")
    @Transactional
    public PurityMaster purityUpdate(@AuthenticationPrincipal AuthenticatedUser me,
                                     @PathVariable Long id,
                                     @RequestBody JsonNode n) {
        requireUpdate(me, "GOLD & RATES");

        PurityMaster x = get(purities, id);
        checkScope(me, x.getCompany(), null);

        if (n.hasNonNull("name")) {
            String name = n.get("name").asText().trim();
            if (name.isBlank()) throw bad("Purity name is required");
            if (purities.existsByCompanyIdAndNameIgnoreCaseAndIdNot(
                    x.getCompany().getId(), name, id)) {
                throw conflict("Purity name already exists for this company: " + name);
            }
            x.setName(name);
        }

        if (n.has("karat")) {
            x.setKarat(n.get("karat").isNull() ? null : n.get("karat").asText().trim());
        }

        if (n.hasNonNull("fineness")) {
            java.math.BigDecimal fineness = new java.math.BigDecimal(n.get("fineness").asText());
            if (fineness.compareTo(java.math.BigDecimal.ZERO) <= 0
                    || fineness.compareTo(java.math.BigDecimal.ONE) > 0) {
                throw bad("Fineness must be greater than 0 and up to 1.000");
            }
            x.setFineness(fineness);
        }

        if (n.has("description")) {
            x.setDescription(n.get("description").isNull()
                    ? null
                    : n.get("description").asText().trim());
        }

        if (n.has("active")) {
            x.setActive(n.get("active").asBoolean());
        }

        return purities.save(x);
    }

    @DeleteMapping("/purities/{id}")
    @Transactional
    public void purityDelete(@AuthenticationPrincipal AuthenticatedUser me,
                              @PathVariable Long id) {
        requireDelete(me, "GOLD & RATES");
        PurityMaster x = get(purities, id);
        checkScope(me, x.getCompany(), null);

        // Soft delete so existing jewellery items and historical gold rates
        // continue to retain their purity reference.
        x.setActive(false);
        purities.save(x);
    }

    @GetMapping("/suppliers")
    public List<Supplier> suppliers(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(suppliers, me, "PURCHASES");
    }

    @PostMapping("/suppliers")
    @Transactional
    public Supplier supplierCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "PURCHASES");
        Supplier x = convert(n, Supplier.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), null);
        return suppliers.save(x);
    }

    @GetMapping("/purchases")
    public List<PurchaseOrder> purchases(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(purchases, me, "PURCHASES");
    }

    @PostMapping("/purchases")
    @Transactional
    public PurchaseOrder purchaseCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "PURCHASES");
        PurchaseOrder x = convert(n, PurchaseOrder.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setSupplier(suppliers.findById(requiredId(n, "supplierId")).orElseThrow());
        return purchases.save(x);
    }

    @GetMapping("/stock")
    public List<Stock> stocks(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(stock, me, "INVENTORY");
    }

    @GetMapping("/stock-movements")
    public List<StockMovement> stockMovements(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(stockMovements, me, "INVENTORY");
    }

    @PostMapping("/stock-movements")
    @Transactional
    public StockMovement stockMovementCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "INVENTORY");
        StockMovement x = convert(n, StockMovement.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setJewelleryItem(items.findById(requiredId(n, "jewelleryItemId")).orElseThrow());
        x.setCreatedBy(null);
        return stockMovements.save(x);
    }

    @GetMapping("/transfers")
    public List<StockTransfer> transfers(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(transfers, me, "INVENTORY");
    }

    @PostMapping("/transfers")
    @Transactional
    public StockTransfer transferCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "INVENTORY");
        StockTransfer x = convert(n, StockTransfer.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), null);
        x.setFromBranch(branches.findById(requiredId(n, "fromBranchId")).orElseThrow());
        x.setToBranch(branches.findById(requiredId(n, "toBranchId")).orElseThrow());
        return transfers.save(x);
    }

    @GetMapping("/sales")
    public List<Sale> sales(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(sales, me, "BILLING");
    }

    @PostMapping("/sales")
    @Transactional
    public Sale saleCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "BILLING");
        Sale x = convert(n, Sale.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setCustomer(customers.findById(requiredId(n, "customerId")).orElseThrow());
        x.setCreatedBy(null);
        return sales.save(x);
    }

    @GetMapping("/payments")
    public List<Payment> payments(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(payments, me, "PAYMENTS");
    }

    @PostMapping("/payments")
    @Transactional
    public Payment paymentCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "PAYMENTS");
        Payment x = convert(n, Payment.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        if (n.hasNonNull("customerId")) x.setCustomer(customers.findById(n.get("customerId").asLong()).orElseThrow());
        if (n.hasNonNull("saleId")) x.setSale(sales.findById(n.get("saleId").asLong()).orElseThrow());
        if (n.hasNonNull("purchaseId")) x.setPurchase(purchases.findById(n.get("purchaseId").asLong()).orElseThrow());
        x.setCreatedBy(null);
        return payments.save(x);
    }

    @GetMapping("/old-gold")
    public List<OldGoldTransaction> oldGold(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(oldGold, me, "OLD GOLD");
    }

    @PostMapping("/old-gold")
    @Transactional
    public OldGoldTransaction oldGoldCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "OLD GOLD");
        OldGoldTransaction x = convert(n, OldGoldTransaction.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setCustomer(customers.findById(requiredId(n, "customerId")).orElseThrow());
        return oldGold.save(x);
    }

    @GetMapping("/repairs")
    public List<RepairOrder> repairs(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(repairs, me, "SERVICES");
    }

    @PostMapping("/repairs")
    @Transactional
    public RepairOrder repairCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "SERVICES");
        RepairOrder x = convert(n, RepairOrder.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setCustomer(customers.findById(requiredId(n, "customerId")).orElseThrow());
        return repairs.save(x);
    }

    @GetMapping("/custom-orders")
    public List<CustomOrder> customOrders(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(customOrders, me, "SERVICES");
    }

    @PostMapping("/custom-orders")
    @Transactional
    public CustomOrder customOrderCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "SERVICES");
        CustomOrder x = convert(n, CustomOrder.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setCustomer(customers.findById(requiredId(n, "customerId")).orElseThrow());
        return customOrders.save(x);
    }

    @GetMapping("/advances")
    public List<CustomerAdvance> advances(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(advances, me, "PAYMENTS");
    }

    @PostMapping("/advances")
    @Transactional
    public CustomerAdvance advanceCreate(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody JsonNode n) {
        requireWrite(me, "PAYMENTS");
        CustomerAdvance x = convert(n, CustomerAdvance.class);
        bindContext(me, x);
        scopeCreate(me, x.getCompany(), x.getBranch());
        x.setCustomer(customers.findById(requiredId(n, "customerId")).orElseThrow());
        return advances.save(x);
    }

    @GetMapping("/whatsapp-logs")
    public List<WhatsappLog> whatsapp(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(whatsappLogs, me, "WHATSAPP");
    }

    @GetMapping("/notifications")
    public List<NotificationLog> notifications(@AuthenticationPrincipal AuthenticatedUser me) {
        return list(notificationLogs, me, "SETTINGS");
    }

    @PutMapping("/jewellery/categories/{id}")
    @Transactional
    public JewelleryCategory categoryUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "JEWELLERY");
        JewelleryCategory x = get(categories, id);
        checkScope(me, x.getCompany(), null);
        merge(n, x);
        if (categories.existsByCompanyIdAndCodeIgnoreCase(x.getCompany().getId(), x.getCode())) {
            List<JewelleryCategory> same = categories.findByCompanyId(x.getCompany().getId()).stream()
                    .filter(c -> c.getCode() != null && c.getCode().equalsIgnoreCase(x.getCode()) && !Objects.equals(c.getId(), x.getId()))
                    .toList();
            if (!same.isEmpty()) throw conflict("Category code already exists for this company: " + x.getCode());
        }
        return categories.save(x);
    }

    @DeleteMapping("/jewellery/categories/{id}")
    @Transactional
    public void categoryDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "JEWELLERY");
        JewelleryCategory x = get(categories, id);
        checkScope(me, x.getCompany(), null);
        try {
            x.getClass().getMethod("setActive", boolean.class).invoke(x, false);
        } catch (Exception e) {
            throw bad("Category cannot be deleted once used; deactivate it in the master data");
        }
        categories.save(x);
    }

    @PutMapping("/jewellery/designs/{id}")
    @Transactional
    public JewelleryDesign designUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "JEWELLERY");
        JewelleryDesign x = get(designs, id);
        checkScope(me, x.getCompany(), null);
        merge(n, x);
        if (n.hasNonNull("categoryId"))
            x.setCategory(categories.findById(requiredId(n, "categoryId")).orElseThrow(() -> bad("Category not found")));
        return designs.save(x);
    }

    @DeleteMapping("/jewellery/designs/{id}")
    @Transactional
    public void designDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "JEWELLERY");
        throw bad("Design deletion is disabled after creation; use status/active lifecycle to preserve history");
    }

    @PutMapping("/jewellery/tags/{id}")
    @Transactional
    public JewelleryTag tagUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "JEWELLERY");
        JewelleryTag x = get(tags, id);
        checkScope(me, x.getCompany(), x.getBranch());
        merge(n, x);
        x.setCompany(x.getCompany());
        return tags.save(x);
    }

    @DeleteMapping("/jewellery/tags/{id}")
    @Transactional
    public void tagDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "JEWELLERY");
        JewelleryTag x = get(tags, id);
        checkScope(me, x.getCompany(), x.getBranch());
        x.setStatus("RETIRED");
        tags.save(x);
    }

    @PutMapping("/gold-rates/{id}")
    @Transactional
    public GoldRate goldRateUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "GOLD & RATES");
        GoldRate x = get(goldRates, id);
        checkScope(me, x.getCompany(), x.getBranch());
        merge(n, x);
        if (n.hasNonNull("purityId"))
            x.setPurity(purities.findById(requiredId(n, "purityId")).orElseThrow(() -> bad("Purity not found")));
        return goldRates.save(x);
    }

    @DeleteMapping("/gold-rates/{id}")
    @Transactional
    public void goldRateDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "GOLD & RATES");
        GoldRate x = get(goldRates, id);
        checkScope(me, x.getCompany(), x.getBranch());
        x.setActive(false);
        goldRates.save(x);
    }

    @PutMapping("/suppliers/{id}")
    @Transactional
    public Supplier supplierUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "PURCHASES");
        Supplier x = get(suppliers, id);
        checkScope(me, x.getCompany(), null);
        merge(n, x);
        return suppliers.save(x);
    }

    @DeleteMapping("/suppliers/{id}")
    @Transactional
    public void supplierDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "PURCHASES");
        throw bad("Supplier deletion is disabled once referenced by purchases; deactivate the supplier instead");
    }

    @PutMapping("/repairs/{id}")
    @Transactional
    public RepairOrder repairCrudUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "SERVICES");
        RepairOrder x = get(repairs, id);
        checkScope(me, x.getCompany(), x.getBranch());
        merge(n, x);
        return repairs.save(x);
    }

    @DeleteMapping("/repairs/{id}")
    @Transactional
    public void repairDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "SERVICES");
        throw bad("Repair deletion is disabled after creation; update status instead");
    }

    @PutMapping("/custom-orders/{id}")
    @Transactional
    public CustomOrder customOrderCrudUpdate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, @RequestBody JsonNode n) {
        requireUpdate(me, "SERVICES");
        CustomOrder x = get(customOrders, id);
        checkScope(me, x.getCompany(), x.getBranch());
        merge(n, x);
        return customOrders.save(x);
    }

    @DeleteMapping("/custom-orders/{id}")
    @Transactional
    public void customOrderDelete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        requireDelete(me, "SERVICES");
        throw bad("Custom-order deletion is disabled after creation; update status instead");
    }

    private <T> List<T> list(JpaRepository<T, Long> repo, AuthenticatedUser me, String module) {
        permissions.requireModule(me, module);
        Long companyId = me.getCompanyId(), branchId = me.getBranchId();
        if ("APP_ADMIN".equals(me.getRole())) {
            String cs = request.getHeader("X-Company-Id"), bs = request.getHeader("X-Branch-Id");
            if (cs == null || bs == null) throw bad("APP_ADMIN requires X-Company-Id and X-Branch-Id context");
            companyId = Long.parseLong(cs);
            branchId = Long.parseLong(bs);
        }
        if (companyId == null) throw bad("Company scope is required");
        List<T> all;
        try {
            all = (List<T>) repo.getClass().getMethod("findByCompanyId", Long.class).invoke(repo, companyId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Scoped repository is not configured", e);
        }
        final Long scopedBranchId = branchId;
        return all.stream().filter(x -> branchAllowed(x, scopedBranchId)).toList();
    }

    private boolean branchAllowed(Object x, Long branchId) {
        if (branchId == null) return true;
        try {
            Object b = x.getClass().getMethod("getBranch").invoke(x);
            if (b == null) return true;
            return Objects.equals(((Branch) b).getId(), branchId);
        } catch (Exception ignored) {
            try {
                Object b = x.getClass().getMethod("getFromBranch").invoke(x);
                Object t = x.getClass().getMethod("getToBranch").invoke(x);
                return (b instanceof Branch bb && Objects.equals(bb.getId(), branchId)) || (t instanceof Branch tt && Objects.equals(tt.getId(), branchId));
            } catch (Exception e) {
                return true;
            }
        }
    }

    private <T> T convert(JsonNode n, Class<T> cls) {
        try {
            return mapper.treeToValue(n, cls);
        } catch (Exception e) {
            throw bad("Invalid request for " + cls.getSimpleName() + ": " + e.getMessage());
        }
    }

    private <T> T merge(JsonNode n, T target) {
        try {
            return mapper.readerForUpdating(target).readValue(n);
        } catch (Exception e) {
            throw bad("Invalid update payload: " + e.getMessage());
        }
    }

    private <T> T get(JpaRepository<T, Long> repo, Long id) {
        return repo.findById(id).orElseThrow(() -> bad("Record not found: " + id));
    }

    private Long requiredId(JsonNode n, String field) {
        if (!n.hasNonNull(field) || n.get(field).asLong() == 0) throw bad(field + " is required");
        return n.get(field).asLong();
    }

    private void requireWrite(AuthenticatedUser me, String module) {
        permissions.requireWrite(me, module);
    }

    private void requireUpdate(AuthenticatedUser me, String module) {
        permissions.requireAction(me, module, "UPDATE");
    }

    private void requireDelete(AuthenticatedUser me, String module) {
        permissions.requireAction(me, module, "DELETE");
    }

    private void bindContext(AuthenticatedUser me, Object entity) {
        Company c;
        Branch b = null;
        if ("APP_ADMIN".equals(me.getRole())) {
            String cs = request.getHeader("X-Company-Id"), bs = request.getHeader("X-Branch-Id");
            if (cs == null || bs == null) throw bad("APP_ADMIN requires X-Company-Id and X-Branch-Id context");
            c = companies.findById(Long.parseLong(cs)).orElseThrow(() -> bad("Company context not found"));
            b = branches.findById(Long.parseLong(bs)).orElseThrow(() -> bad("Branch context not found"));
        } else {
            if (me.getCompanyId() == null) throw bad("Company scope is required");
            c = companies.findById(me.getCompanyId()).orElseThrow(() -> bad("Company not found"));
            if (me.getBranchId() != null)
                b = branches.findById(me.getBranchId()).orElseThrow(() -> bad("Branch not found"));
        }
        try {
            entity.getClass().getMethod("setCompany", Company.class).invoke(entity, c);
        } catch (Exception ignored) {
        }
        try {
            if (b != null) entity.getClass().getMethod("setBranch", Branch.class).invoke(entity, b);
        } catch (Exception ignored) {
        }
    }

    private void scopeCreate(AuthenticatedUser me, Company company, Branch branch) {
        if (company == null) throw bad("companyId is required");
        if ("APP_ADMIN".equals(me.getRole())) {
            String bs = request.getHeader("X-Branch-Id");
            if (bs == null) throw bad("APP_ADMIN requires X-Branch-Id context");
            Branch ctx = branches.findById(Long.parseLong(bs)).orElseThrow(() -> bad("Branch context not found"));
            if (!Objects.equals(ctx.getCompany().getId(), company.getId()))
                throw bad("Branch does not belong to company");
        } else {
            if (!Objects.equals(company.getId(), me.getCompanyId())) throw forbidden("Company access denied");
            if (me.getBranchId() != null && branch != null && !Objects.equals(branch.getId(), me.getBranchId()))
                throw forbidden("Branch access denied");
        }
        if (branch != null && !Objects.equals(branch.getCompany().getId(), company.getId()))
            throw bad("Branch does not belong to company");
    }

    private void checkScope(AuthenticatedUser me, Company company, Branch branch) {
        scopeCreate(me, company, branch);
    }

    private Company requireCompany(AuthenticatedUser me, long id, Company fallback) {
        if (id == 0) return fallback;
        Company c = companies.findById(id).orElseThrow(() -> bad("Company not found"));
        if (!"APP_ADMIN".equals(me.getRole()) && !Objects.equals(c.getId(), me.getCompanyId()))
            throw forbidden("Company access denied");
        return c;
    }

    private Branch requireBranch(AuthenticatedUser me, long id, Branch fallback) {
        if (id == 0) return fallback;
        return branches.findById(id).orElseThrow(() -> bad("Branch not found"));
    }

    private ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private ResponseStatusException forbidden(String m) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, m);
    }

    private ResponseStatusException conflict(String m) {
        return new ResponseStatusException(HttpStatus.CONFLICT, m);
    }

    private Map<String, Object> customerResponse(Customer customer) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("id", customer.getId());
        response.put("version", customer.getVersion());

        response.put(
                "companyId",
                customer.getCompany() == null
                        ? null
                        : customer.getCompany().getId()
        );

        response.put(
                "companyName",
                customer.getCompany() == null
                        ? ""
                        : customer.getCompany().getName()
        );

        response.put(
                "branchId",
                customer.getBranch() == null
                        ? null
                        : customer.getBranch().getId()
        );

        response.put(
                "branchName",
                customer.getBranch() == null
                        ? ""
                        : customer.getBranch().getName()
        );

        response.put("name", customer.getName());
        response.put("phone", customer.getPhone());
        response.put("email", customer.getEmail());
        response.put("address", customer.getAddress());
        response.put("gstin", customer.getGstin());
        response.put("notes", customer.getNotes());
        response.put("active", customer.isActive());
        response.put("createdAt", customer.getCreatedAt());

        return response;
    }
}
