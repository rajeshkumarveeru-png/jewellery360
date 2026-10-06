package com.jewellery360.service;

import com.jewellery360.domain.AppUser;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.repository.UserPermissionRepository;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * What a user may open and do.
 *  - APP_ADMIN and COMPANY_ADMIN: everything (in their scope).
 *  - Every other role: the pages and powers the company admin granted on the Users page. Without a saved list the defaults of the role apply.
 * Permissions are read from the database on every check, so taking access away works immediately - also for a user who is signed in.
 *
 * Keys: PAGE_<MODULE> (PAGE_OVERVIEW, PAGE_BILLING, PAGE_GOLD_RATES ...) and the powers
 *   MANAGE_RECORDS - add / edit / delete records in the pages the user can open (billing, payments, customers, services and old gold may always be added)
 *   SEE_PROFIT     - the profit report
 *   EDIT_SETTINGS  - save changes in Settings
 */
@Service
@RequiredArgsConstructor
public class PermissionService {
    private final UserPermissionRepository userPermissions;
    private final AppUserRepository users;

    /** Pages in menu order. */
    public static final List<String> PAGES = List.of("OVERVIEW", "BILLING", "JEWELLERY", "GOLD & RATES", "INVENTORY", "PURCHASES", "OLD GOLD",
            "CUSTOMERS", "SERVICES", "PAYMENTS", "REPORTS", "WHATSAPP", "SETTINGS", "AUDIT LOGS");
    public static final List<String> POWERS = List.of("MANAGE_RECORDS", "SEE_PROFIT", "EDIT_SETTINGS");
    /** Pages where adding a record is part of the daily work, so it needs no extra power. */
    private static final Set<String> TRANSACTIONAL = Set.of("BILLING", "PAYMENTS", "CUSTOMERS", "SERVICES", "OLD GOLD", "WHATSAPP");

    private static final Map<String, Set<String>> ROLE_MODULES = new HashMap<>();
    private static final Map<String, Set<String>> ROLE_POWERS = new HashMap<>();
    static {
        ROLE_MODULES.put("MANAGER", Set.of("OVERVIEW", "BILLING", "JEWELLERY", "GOLD & RATES", "INVENTORY", "PURCHASES", "OLD GOLD", "CUSTOMERS", "SERVICES", "PAYMENTS", "REPORTS", "AUDIT LOGS", "WHATSAPP"));
        ROLE_MODULES.put("CASHIER", Set.of("OVERVIEW", "BILLING", "CUSTOMERS", "PAYMENTS", "AUDIT LOGS"));
        ROLE_MODULES.put("SALESMAN", Set.of("OVERVIEW", "BILLING", "CUSTOMERS", "SERVICES", "AUDIT LOGS"));
        ROLE_MODULES.put("INVENTORY_MANAGER", Set.of("OVERVIEW", "JEWELLERY", "INVENTORY", "PURCHASES", "OLD GOLD", "REPORTS", "AUDIT LOGS"));
        ROLE_MODULES.put("ACCOUNTANT", Set.of("OVERVIEW", "CUSTOMERS", "PAYMENTS", "REPORTS", "AUDIT LOGS"));
        ROLE_MODULES.put("VIEWER", Set.of("OVERVIEW", "CUSTOMERS", "INVENTORY", "REPORTS"));
        // cashiers and salesmen can bill and add customers, but not edit / delete records and not see profit
        ROLE_POWERS.put("MANAGER", Set.of("MANAGE_RECORDS", "SEE_PROFIT"));
        ROLE_POWERS.put("INVENTORY_MANAGER", Set.of("MANAGE_RECORDS"));
        ROLE_POWERS.put("ACCOUNTANT", Set.of("MANAGE_RECORDS", "SEE_PROFIT"));
    }

    public static String pageKey(String module) {
        return "PAGE_" + module.toUpperCase().replace(" & ", "_").replace(" ", "_");
    }

    private static boolean isAdmin(String role) {
        return "APP_ADMIN".equals(role) || "COMPANY_ADMIN".equals(role);
    }

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
        Set<String> pages = ROLE_MODULES.getOrDefault(role, Set.of("OVERVIEW"));
        for (String p : PAGES) if (pages.contains(p)) keys.add(pageKey(p));
        for (String power : POWERS) if (ROLE_POWERS.getOrDefault(role, Set.of()).contains(power)) keys.add(power);
        return keys;
    }

    /** Only known keys, in a stable order, and always at least one page. */
    public static List<String> sanitize(Collection<String> requested) {
        Set<String> wanted = new LinkedHashSet<>();
        if (requested != null) for (String k : requested) if (k != null) wanted.add(k.trim().toUpperCase());
        List<String> out = new ArrayList<>();
        for (String k : all()) if (wanted.contains(k)) out.add(k);
        boolean anyPage = out.stream().anyMatch(k -> k.startsWith("PAGE_"));
        if (!anyPage) out.add(0, pageKey("OVERVIEW"));
        return out;
    }

    public static String serialize(Collection<String> keys) {
        return String.join(",", sanitize(keys));
    }

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

    public boolean hasPower(AuthenticatedUser me, String power) {
        return me != null && effective(me).contains(power);
    }

    public boolean canModule(AuthenticatedUser u, String module) {
        if (u == null) return false;
        if (isAdmin(u.getRole())) return true;
        return effective(u).contains(pageKey(module));
    }

    public boolean has(AuthenticatedUser u, String module, String action) {
        if (!canModule(u, module)) return false;
        if (isAdmin(u.getRole())) return true;
        String code = pageKey(module).substring(5) + "_" + action.toUpperCase();
        var explicit = userPermissions.findByUserIdAndPermission_Code(u.getUserId(), code);
        if (explicit.isPresent()) return explicit.get().isGranted();
        String act = action.toUpperCase();
        if ("VIEW".equals(act)) return true;
        if ("VIEWER".equals(u.getRole())) return false;
        boolean manage = effective(u).contains("MANAGE_RECORDS");
        if ("CREATE".equals(act) || "SEND".equals(act)) return manage || TRANSACTIONAL.contains(module.toUpperCase());
        return manage; // UPDATE, DELETE and anything else
    }

    public void requireModule(AuthenticatedUser u, String module) {
        if (!canModule(u, module)) throw forbidden("You do not have permission for " + module);
    }

    public void requireAction(AuthenticatedUser u, String module, String action) {
        if (!has(u, module, action)) throw forbidden("Permission denied: " + module + "/" + action);
    }

    public void requireWrite(AuthenticatedUser u, String module) {
        requireAction(u, module, "CREATE");
    }

    public void requireAppAdmin(AuthenticatedUser u) {
        if (!"APP_ADMIN".equals(u.getRole())) throw forbidden("APP_ADMIN only");
    }

    public void requireProfit(AuthenticatedUser u) {
        if (!isAdmin(u.getRole()) && !hasPower(u, "SEE_PROFIT")) throw forbidden("You do not have permission to see profit details");
    }

    public boolean canEditSettings(AuthenticatedUser u) {
        return u != null && (isAdmin(u.getRole()) || hasPower(u, "EDIT_SETTINGS"));
    }

    private ResponseStatusException forbidden(String s) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, s);
    }
}
