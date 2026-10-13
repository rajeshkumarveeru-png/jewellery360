package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** One search box for the whole shop: customers, stock (tag / barcode / HUID / name), bills, orders and repairs. */
@Service
@RequiredArgsConstructor
public class SearchService {
    private final CustomerRepository customers;
    private final JewelleryItemRepository items;
    private final SaleRepository sales;
    private final CustomOrderRepository orders;
    private final RepairOrderRepository repairs;
    private final PermissionService permissions;

    @Transactional(readOnly = true)
    public Map<String, Object> search(TenantContext.Ctx ctx, String q) {
        List<Map<String, Object>> results = new ArrayList<>();
        String text = q == null ? "" : q.trim();
        if (text.length() < 2) return Map.of("query", text, "results", results);
        String low = text.toLowerCase(Locale.ROOT);
        String like = "%" + low + "%";
        Long c = ctx.companyId(), b = ctx.branchId();

        if (permissions.can(ctx.me(), "CUSTOMERS"))
            for (Customer cu : customers.search(c, like, PageRequest.of(0, 6)))
                results.add(hit("CUSTOMER", cu.getId(), cu.getName(), join(cu.getPhone(), cu.getGstin()), "CUSTOMERS"));

        if (permissions.can(ctx.me(), "STOCK") || permissions.can(ctx.me(), "BILLING"))
            for (JewelleryItem i : items.search(c, b, like, PageRequest.of(0, 8)))
                results.add(hit("ITEM", i.getId(), i.getTagNo() + " · " + i.getProduct().getName(),
                        join(i.getPurity().getName(), i.getNetWeight().toPlainString() + " g", i.getHuid() == null ? null : "HUID " + i.getHuid(), status(i.getStatus())), "STOCK"));

        if (permissions.can(ctx.me(), "BILLING")) {
            var today = GoldRateService.today();
            for (Sale s : sales.list(c, b, today.minusYears(5), today, null, like, PageRequest.of(0, 6)))
                results.add(hit("BILL", s.getId(), s.getInvoiceNo(), join(s.getCustomer().getName(), s.getSaleDate().toString(), "₹" + s.getTotal().setScale(0, java.math.RoundingMode.HALF_UP)), "BILLING"));
        }

        if (permissions.can(ctx.me(), "SERVICES")) {
            int n = 0;
            for (CustomOrder o : orders.list(c, b, null)) {
                if (n >= 5) break;
                if (matches(low, o.getOrderNo(), o.getCustomer().getName(), o.getCustomer().getPhone(), o.getDescription())) {
                    results.add(hit("ORDER", o.getId(), o.getOrderNo(), join(o.getCustomer().getName(), o.getDescription(), o.getStatus()), "SERVICES"));
                    n++;
                }
            }
            n = 0;
            for (RepairOrder r : repairs.list(c, b, null)) {
                if (n >= 5) break;
                if (matches(low, r.getOrderNo(), r.getCustomer().getName(), r.getCustomer().getPhone(), r.getItemDescription(), r.getItemTag(), r.getHuid())) {
                    results.add(hit("REPAIR", r.getId(), r.getOrderNo(), join(r.getCustomer().getName(), r.getItemDescription(), r.getStatus()), "SERVICES"));
                    n++;
                }
            }
        }
        return Map.of("query", text, "results", results);
    }

    private static boolean matches(String q, String... fields) {
        for (String f : fields) if (f != null && f.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private static String status(String s) {
        return switch (s) { case "IN_STOCK" -> "In stock"; case "SOLD" -> "Sold"; case "RETURNED_TO_SUPPLIER" -> "Returned to supplier"; default -> "Removed"; };
    }

    private static String join(String... parts) {
        StringJoiner j = new StringJoiner(" · ");
        for (String p : parts) if (p != null && !p.isBlank()) j.add(p);
        return j.toString();
    }

    private static Map<String, Object> hit(String type, Long id, String title, String subtitle, String page) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("title", title);
        m.put("subtitle", subtitle);
        m.put("page", page);
        return m;
    }
}
