package com.jewellery360.service;

import com.jewellery360.domain.AppUser;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * What a user may open and do. This is the server-side check behind every endpoint; hiding a menu entry is never enough.
 *
 *  - APP_ADMIN and COMPANY_ADMIN (the owner): everything in their scope.
 *  - Every other role: the pages and powers the company admin granted on the Users page; without a saved list the
 *    defaults of the role apply. Permissions are read from the database on every check, so removing access works at once.
 *
 * Keys: PAGE_&lt;page&gt; for each page and the powers below. VIEWER is always read-only.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final AppUserRepository users;

    /** Page keys in menu order. */
    public static final List<String> PAGES = List.of("OVERVIEW", "BILLING", "CUSTOMERS", "STOCK", "GOLD_RATES", "OLD_GOLD",
            "PURCHASES", "SERVICES", "MONEY", "REPORTS", "SETTINGS", "AUDIT_LOGS");

    /** MANAGE_RECORDS: add / edit masters, stock, gold rates and purchases. DISCOUNT: give a discount on a bill.
     *  PRICE_OVERRIDE: change rate, making or wastage on a bill line. CANCEL_RETURN: cancel bills, take returns, refund.
     *  SEE_PROFIT: profit and cost figures. EDIT_SETTINGS: change settings. */
    public static final List<String> POWERS = List.of("MANAGE_RECORDS", "DISCOUNT", "PRICE_OVERRIDE", "CANCEL_RETURN", "SEE_PROFIT", "EDIT_SETTINGS");

    private static final Map<String, List<String>> ROLE_PAGES = Map.of(
            "MANAGER", List.of("OVERVIEW", "BILLING", "CUSTOMERS", "STOCK", "GOLD_RATES", "OLD_GOLD", "PURCHASES", "SERVICES", "MONEY", "REPORTS", "AUDIT_LOGS"),
            "CASHIER", List.of("OVERVIEW", "BILLING", "CUSTOMERS", "OLD_GOLD", "MONEY"),
            "SALESMAN", List.of("OVERVIEW", "BILLING", "CUSTOMERS", "STOCK", "SERVICES"),
            "INVENTORY_MANAGER", List.of("OVERVIEW", "STOCK", "GOLD_RATES", "PURCHASES", "SERVICES", "REPORTS"),
            "ACCOUNTANT", List.of("OVERVIEW", "CUSTOMERS", "PURCHASES", "MONEY", "REPORTS", "AUDIT_LOGS"),
            "VIEWER", List.of("OVERVIEW", "CUSTOMERS", "STOCK", "REPORTS"));
    private static final Map<String, List<String>> ROLE_POWERS = Map.of(
            "MANAGER", List.of("MANAGE_RECORDS", "DISCOUNT", "PRICE_OVERRIDE", "CANCEL_RETURN", "SEE_PROFIT"),
            "INVENTORY_MANAGER", List.of("MANAGE_RECORDS"),
            "ACCOUNTANT", List.of("MANAGE_RECORDS", "SEE_PROFIT"));
    /** Keys saved by older versions of the app. */
    private static final Map<String, String> LEGACY_KEYS = Map.of(
            "PAGE_JEWELLERY", "PAGE_STOCK", "PAGE_INVENTORY", "PAGE_STOCK", "PAGE_PAYMENTS", "PAGE_MONEY");

    public static String pageKey(String page) { return "PAGE_" + page; }

    public static boolean isAdmin(String role) { return "APP_ADMIN".equals(role) || "COMPANY_ADMIN".equals(role); }

    private static List<String> all() {
        List<String> keys = new ArrayList<>();
        for (String p : PAGES) keys.add(pageKey(p));
        keys.addAll(POWERS);
        return keys;
    }

    /** The role's defaults as permission keys. */
    public static List<String> defaults(String role) {
        if (isAdmin(role)) return all();
        List<String> keys = new ArrayList<>();
        for (String p : ROLE_PAGES.getOrDefault(role, List.of("OVERVIEW"))) keys.add(pageKey(p));
        keys.addAll(ROLE_POWERS.getOrDefault(role, List.of()));
        return sanitize(keys);
    }

    /** Only known keys, in a stable order, and always at least one page. */
    public static List<String> sanitize(Collection<String> requested) {
        Set<String> wanted = new LinkedHashSet<>();
        if (requested != null) for (String k : requested) {
            if (k == null) continue;
            String key = k.trim().toUpperCase(Locale.ROOT);
            wanted.add(LEGACY_KEYS.getOrDefault(key, key));
        }
        List<String> out = new ArrayList<>();
        for (String k : all()) if (wanted.contains(k)) out.add(k);
        if (out.stream().noneMatch(k -> k.startsWith("PAGE_"))) out.add(0, pageKey("OVERVIEW"));
        return out;
    }

    public static String serialize(Collection<String> keys) { return String.join(",", sanitize(keys)); }

    /** Effective permissions of an account. */
    public List<String> effective(AppUser user) {
        if (user == null) return List.of();
        String role = user.getRole() == null ? "" : user.getRole().name();
        if (isAdmin(role)) return all();
        String saved = user.getPermissions();
        if (saved == null || saved.isBlank()) return defaults(role);
        return sanitize(Arrays.asList(saved.split(",")));
    }

    public List<String> effective(AuthenticatedUser me) {
        if (me == null) return List.of();
        if (isAdmin(me.getRole())) return all();
        return users.findById(me.getUserId()).map(this::effective).orElseGet(() -> defaults(me.getRole()));
    }

    public boolean can(AuthenticatedUser me, String page) {
        return me != null && (isAdmin(me.getRole()) || effective(me).contains(pageKey(page)));
    }

    public boolean has(AuthenticatedUser me, String power) {
        return me != null && (isAdmin(me.getRole()) || effective(me).contains(power));
    }

    public void requirePage(AuthenticatedUser me, String page) {
        if (!can(me, page)) throw forbidden("You do not have access to " + label(page) + ".");
    }

    /** Daily work in a page (bills, receipts, customers, repairs ...): page access and not a read-only account. */
    public void requireWrite(AuthenticatedUser me, String page) {
        requirePage(me, page);
        if ("VIEWER".equals(me.getRole())) throw forbidden("Your account can only view records.");
    }

    /** Master data and stock changes: page access plus "Add, edit and delete records". */
    public void requireManage(AuthenticatedUser me, String page) {
        requireWrite(me, page);
        if (!has(me, "MANAGE_RECORDS")) throw forbidden("You do not have permission to change records in " + label(page) + ".");
    }

    public void requirePower(AuthenticatedUser me, String power) {
        if (!has(me, power)) throw forbidden(switch (power) {
            case "DISCOUNT" -> "You do not have permission to give a discount.";
            case "PRICE_OVERRIDE" -> "You do not have permission to change rates or charges on a bill.";
            case "CANCEL_RETURN" -> "You do not have permission to cancel bills, take returns or refund.";
            case "SEE_PROFIT" -> "You do not have permission to see profit details.";
            case "EDIT_SETTINGS" -> "You do not have permission to change settings.";
            default -> "Permission denied.";
        });
    }

    public void requireAdmin(AuthenticatedUser me) {
        if (me == null || !isAdmin(me.getRole())) throw forbidden("Only the owner / administrator can do this.");
    }

    public void requireAppAdmin(AuthenticatedUser me) {
        if (me == null || !"APP_ADMIN".equals(me.getRole())) throw forbidden("Only the platform administrator can do this.");
    }

    public boolean canEditSettings(AuthenticatedUser me) { return has(me, "EDIT_SETTINGS"); }

    private static String label(String page) {
        return switch (page) {
            case "GOLD_RATES" -> "Gold rates";
            case "OLD_GOLD" -> "Old gold";
            case "SERVICES" -> "Orders & repairs";
            case "AUDIT_LOGS" -> "Audit logs";
            default -> page.charAt(0) + page.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private ResponseStatusException forbidden(String s) { return new ResponseStatusException(HttpStatus.FORBIDDEN, s); }
}
