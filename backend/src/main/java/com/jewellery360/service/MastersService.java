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
import java.util.function.Predicate;

/** Small reference lists: categories, designs, products, purities, suppliers and karigars. */
@Service
@RequiredArgsConstructor
public class MastersService {
    private final JewelleryCategoryRepository categories;
    private final JewelleryDesignRepository designs;
    private final JewelleryProductRepository products;
    private final PurityMasterRepository purities;
    private final SupplierRepository suppliers;
    private final KarigarRepository karigars;
    private final SkuService sku;
    private final PermissionService permissions;
    private final TenantContext tenant;
    private final AuditService audit;

    /** Everything the stock and billing screens need, in one call. */
    @Transactional(readOnly = true)
    public Map<String, Object> all(TenantContext.Ctx ctx) {
        Map<String, Object> m = Views.map();
        m.put("categories", categories.findByCompanyIdOrderByNameAsc(ctx.companyId()).stream().map(this::category).toList());
        m.put("designs", designs.findByCompanyIdOrderByNameAsc(ctx.companyId()).stream().map(this::design).toList());
        m.put("products", products.findByCompanyIdOrderByNameAsc(ctx.companyId()).stream().map(this::product).toList());
        m.put("purities", purities.findByCompanyIdOrderByMetalAscFinenessDesc(ctx.companyId()).stream().map(Views::purity).toList());
        if (permissions.can(ctx.me(), "PURCHASES") || permissions.can(ctx.me(), "MONEY"))
            m.put("suppliers", suppliers.findByCompanyIdOrderByNameAsc(ctx.companyId()).stream().map(this::supplier).toList());
        if (permissions.can(ctx.me(), "SERVICES"))
            m.put("karigars", karigars.findByCompanyIdOrderByNameAsc(ctx.companyId()).stream().map(this::karigar).toList());
        return m;
    }

    /* ------------------------------------------------------------------ categories / designs / products */

    @Transactional
    public Map<String, Object> saveCategory(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        JewelleryCategory c = id == null ? new JewelleryCategory() : own(ctx, categories.findById(id));
        if (id == null) c.setCompany(ctx.company());
        String name = required(n, "name", 120, "Category name");
        c.setName(name);
        String code = Optional.ofNullable(Num.text(n, "code", 40)).map(s -> s.toUpperCase(Locale.ROOT))
                .orElseGet(() -> c.getCode() != null ? c.getCode() : codeFrom(name, x -> categories.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), x).isPresent()));
        categories.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), code).filter(o -> !o.getId().equals(c.getId()))
                .ifPresent(o -> { throw Errors.conflict("Category code " + code + " is already used by " + o.getName() + "."); });
        c.setCode(code);
        if (n.has("active")) c.setActive(n.get("active").asBoolean());
        categories.save(c);
        audit.log(ctx, id == null ? "CATEGORY_CREATE" : "CATEGORY_UPDATE", "JewelleryCategory", c.getId(), null, category(c));
        return category(c);
    }

    @Transactional
    public Map<String, Object> saveDesign(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        JewelleryDesign d = id == null ? new JewelleryDesign() : own(ctx, designs.findById(id));
        if (id == null) d.setCompany(ctx.company());
        String name = required(n, "name", 180, "Design name");
        d.setName(name);
        d.setCategory(own(ctx, categories.findById(Num.requireId(n, "categoryId", "Category"))));
        String code = Optional.ofNullable(Num.text(n, "code", 60)).map(s -> s.toUpperCase(Locale.ROOT))
                .orElseGet(() -> d.getCode() != null ? d.getCode() : codeFrom(name, x -> designs.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), x).isPresent()));
        designs.findByCompanyIdAndCodeIgnoreCase(ctx.companyId(), code).filter(o -> !o.getId().equals(d.getId()))
                .ifPresent(o -> { throw Errors.conflict("Design code " + code + " is already used."); });
        d.setCode(code);
        d.setDescription(Num.text(n, "description", 2000));
        if (n.has("active")) d.setActive(n.get("active").asBoolean());
        designs.save(d);
        audit.log(ctx, id == null ? "DESIGN_CREATE" : "DESIGN_UPDATE", "JewelleryDesign", d.getId(), null, design(d));
        return design(d);
    }

    @Transactional
    public Map<String, Object> saveProduct(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "STOCK");
        JewelleryProduct p = id == null ? new JewelleryProduct() : own(ctx, products.findById(id));
        if (id == null) p.setCompany(ctx.company());
        p.setName(required(n, "name", 180, "Product name"));
        p.setCategory(own(ctx, categories.findById(Num.requireId(n, "categoryId", "Category"))));
        Long designId = Num.id(n, "designId");
        p.setDesign(designId == null ? null : own(ctx, designs.findById(designId)));
        String s = Num.text(n, "sku", 80);
        if (s == null) s = p.getSku() != null ? p.getSku() : sku.nextProductSku(ctx.company());
        String finalSku = s.toUpperCase(Locale.ROOT);
        products.findByCompanyIdAndSkuIgnoreCase(ctx.companyId(), finalSku).filter(o -> !o.getId().equals(p.getId()))
                .ifPresent(o -> { throw Errors.conflict("SKU " + finalSku + " is already used by " + o.getName() + "."); });
        p.setSku(finalSku);
        String hsn = Num.text(n, "hsnCode", 10);
        p.setHsnCode(hsn == null ? Optional.ofNullable(p.getHsnCode()).orElse("7113") : hsn);
        p.setDescription(Num.text(n, "description", 2000));
        if (n.has("active")) p.setActive(n.get("active").asBoolean());
        products.save(p);
        audit.log(ctx, id == null ? "PRODUCT_CREATE" : "PRODUCT_UPDATE", "JewelleryProduct", p.getId(), null, product(p));
        return product(p);
    }

    /* ------------------------------------------------------------------ purities */

    @Transactional
    public Map<String, Object> savePurity(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "GOLD_RATES");
        PurityMaster p = id == null ? new PurityMaster() : own(ctx, purities.findById(id));
        if (id == null) p.setCompany(ctx.company());
        String name = required(n, "name", 50, "Purity name");
        boolean dup = id == null ? purities.existsByCompanyIdAndNameIgnoreCase(ctx.companyId(), name)
                : purities.existsByCompanyIdAndNameIgnoreCaseAndIdNot(ctx.companyId(), name, id);
        if (dup) throw Errors.conflict("Purity " + name + " already exists.");
        p.setName(name);
        String metal = Optional.ofNullable(Num.text(n, "metal")).orElse(p.getMetal() == null ? "GOLD" : p.getMetal()).toUpperCase(Locale.ROOT);
        if (!List.of("GOLD", "SILVER", "PLATINUM").contains(metal)) throw Errors.bad("Metal must be GOLD, SILVER or PLATINUM.");
        p.setMetal(metal);
        p.setKarat(Num.text(n, "karat", 10));
        BigDecimal fineness = Num.dec(n, "fineness");
        if (fineness.signum() <= 0 || fineness.compareTo(BigDecimal.ONE) > 0) throw Errors.bad("Fineness must be between 0 and 1 (for example 0.916 for 22K).");
        p.setFineness(fineness.setScale(4, java.math.RoundingMode.HALF_UP));
        p.setDescription(Num.text(n, "description", 500));
        if (n.has("active")) p.setActive(n.get("active").asBoolean());
        purities.save(p);
        audit.log(ctx, id == null ? "PURITY_CREATE" : "PURITY_UPDATE", "PurityMaster", p.getId(), null, Views.purity(p));
        return Views.purity(p);
    }

    /* ------------------------------------------------------------------ suppliers / karigars */

    @Transactional
    public Map<String, Object> saveSupplier(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "PURCHASES");
        Supplier s = id == null ? new Supplier() : own(ctx, suppliers.findById(id));
        if (id == null) s.setCompany(ctx.company());
        String name = required(n, "name", 180, "Supplier name");
        if (!name.equalsIgnoreCase(Objects.toString(s.getName(), "")) && suppliers.existsByCompanyIdAndNameIgnoreCase(ctx.companyId(), name))
            throw Errors.conflict("Supplier " + name + " already exists.");
        s.setName(name);
        s.setPhone(Num.text(n, "phone", 25));
        s.setEmail(Num.text(n, "email", 180));
        s.setAddress(Num.text(n, "address", 600));
        s.setGstin(Optional.ofNullable(Num.text(n, "gstin", 30)).map(x -> x.toUpperCase(Locale.ROOT)).orElse(null));
        if (n.has("active")) s.setActive(n.get("active").asBoolean());
        suppliers.save(s);
        audit.log(ctx, id == null ? "SUPPLIER_CREATE" : "SUPPLIER_UPDATE", "Supplier", s.getId(), null, supplier(s));
        return supplier(s);
    }

    @Transactional
    public Map<String, Object> saveKarigar(TenantContext.Ctx ctx, Long id, JsonNode n) {
        permissions.requireManage(ctx.me(), "SERVICES");
        Karigar k = id == null ? new Karigar() : own(ctx, karigars.findById(id));
        if (id == null) k.setCompany(ctx.company());
        String name = required(n, "name", 150, "Karigar name");
        if (!name.equalsIgnoreCase(Objects.toString(k.getName(), "")) && karigars.existsByCompanyIdAndNameIgnoreCase(ctx.companyId(), name))
            throw Errors.conflict("Karigar " + name + " already exists.");
        k.setName(name);
        k.setPhone(Num.text(n, "phone", 25));
        k.setAddress(Num.text(n, "address", 500));
        k.setNotes(Num.text(n, "notes", 1000));
        if (n.has("active")) k.setActive(n.get("active").asBoolean());
        karigars.save(k);
        audit.log(ctx, id == null ? "KARIGAR_CREATE" : "KARIGAR_UPDATE", "Karigar", k.getId(), null, karigar(k));
        return karigar(k);
    }

    /* ------------------------------------------------------------------ views + helpers */

    Map<String, Object> category(JewelleryCategory c) {
        return new LinkedHashMap<>(Map.of("id", c.getId(), "name", c.getName(), "code", c.getCode(), "active", c.isActive()));
    }

    Map<String, Object> design(JewelleryDesign d) {
        Map<String, Object> m = Views.map();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("code", d.getCode());
        m.put("categoryId", d.getCategory().getId());
        m.put("category", d.getCategory().getName());
        m.put("description", d.getDescription());
        m.put("active", d.isActive());
        return m;
    }

    Map<String, Object> product(JewelleryProduct p) {
        Map<String, Object> m = Views.map();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("sku", p.getSku());
        m.put("hsnCode", p.getHsnCode());
        m.put("categoryId", p.getCategory().getId());
        m.put("category", p.getCategory().getName());
        m.put("designId", p.getDesign() == null ? null : p.getDesign().getId());
        m.put("design", p.getDesign() == null ? null : p.getDesign().getName());
        m.put("description", p.getDescription());
        m.put("active", p.isActive());
        return m;
    }

    Map<String, Object> supplier(Supplier s) {
        Map<String, Object> m = Views.map();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("phone", s.getPhone());
        m.put("email", s.getEmail());
        m.put("address", s.getAddress());
        m.put("gstin", s.getGstin());
        m.put("active", s.isActive());
        return m;
    }

    Map<String, Object> karigar(Karigar k) {
        Map<String, Object> m = Views.map();
        m.put("id", k.getId());
        m.put("name", k.getName());
        m.put("phone", k.getPhone());
        m.put("address", k.getAddress());
        m.put("notes", k.getNotes());
        m.put("active", k.isActive());
        return m;
    }

    private <T> T own(TenantContext.Ctx ctx, Optional<T> found) {
        T x = found.orElseThrow(TenantContext::notFound);
        try {
            Company c = (Company) x.getClass().getMethod("getCompany").invoke(x);
            tenant.checkCompany(ctx, c);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return x;
    }

    private static String required(JsonNode n, String field, int max, String label) {
        String v = Num.text(n, field, max);
        if (v == null) throw Errors.bad(label + " is required.");
        return v;
    }

    /** "Gold Bangles" -> "GOLD-BANGLES" (made unique with a number when needed). */
    static String codeFrom(String name, Predicate<String> taken) {
        String base = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "ITEM";
        if (base.length() > 30) base = base.substring(0, 30);
        String code = base;
        for (int i = 2; taken.test(code); i++) code = base + "-" + i;
        return code;
    }
}
