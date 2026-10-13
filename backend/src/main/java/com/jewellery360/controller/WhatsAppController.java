package com.jewellery360.controller;

import com.jewellery360.domain.Sale;
import com.jewellery360.domain.WhatsappLog;
import com.jewellery360.repository.SaleRepository;
import com.jewellery360.repository.WhatsappLogRepository;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.PermissionService;
import com.jewellery360.service.TenantContext;
import com.jewellery360.service.WhatsAppService;
import com.jewellery360.support.Errors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Invoice messages. With the WhatsApp Business API configured in Settings the message is sent by the server and logged.
 * Without it, the response carries a wa.me link the counter opens to send from its own WhatsApp - never a fake "sent".
 */
@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {
    private final WhatsAppService whatsapp;
    private final SaleRepository sales;
    private final WhatsappLogRepository logs;
    private final PermissionService permissions;
    private final TenantContext tenant;

    @PostMapping("/sales/{id}")
    @Transactional
    public Map<String, Object> sendSale(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id) {
        TenantContext.Ctx ctx = tenant.of(me);
        permissions.requirePage(me, "BILLING");
        Sale s = sales.findById(id).orElseThrow(TenantContext::notFound);
        tenant.check(ctx, s.getCompany(), s.getBranch());
        String phone = s.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) throw Errors.bad("This customer has no phone number.");
        String text = "Thank you for shopping at " + s.getCompany().getName() + ".\nInvoice " + s.getInvoiceNo() + " dated " + s.getSaleDate()
                + "\nAmount: Rs. " + s.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString()
                + (s.balance().signum() > 0 ? "\nBalance due: Rs. " + s.balance().setScale(2, RoundingMode.HALF_UP).toPlainString() : "\nFully paid.");
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) digits = "91" + digits;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shareUrl", "https://wa.me/" + digits + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        if (!whatsapp.isConfigured(ctx.companyId())) {
            out.put("status", "NOT_CONFIGURED");
            out.put("message", "WhatsApp Business is not set up. Opening WhatsApp on this device instead.");
            return out;
        }
        try {
            whatsapp.sendText(ctx.companyId(), phone, text);
            log(s, "SENT", null);
            out.put("status", "SENT");
            out.put("message", "Invoice sent on WhatsApp.");
        } catch (Exception e) {
            log(s, "FAILED", e.getMessage());
            out.put("status", "FAILED");
            out.put("message", "WhatsApp could not send the message: " + e.getMessage());
        }
        return out;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@AuthenticationPrincipal AuthenticatedUser me) {
        TenantContext.Ctx ctx = tenant.of(me);
        permissions.requirePage(me, "SETTINGS");
        return logs.findTop100ByCompanyIdOrderByCreatedAtDesc(ctx.companyId()).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("at", l.getCreatedAt());
            m.put("phone", l.getPhone());
            m.put("type", l.getMessageType());
            m.put("status", l.getStatus());
            m.put("error", l.getErrorMessage());
            return m;
        }).toList();
    }

    private void log(Sale s, String status, String error) {
        WhatsappLog l = new WhatsappLog();
        l.setCompany(s.getCompany());
        l.setBranch(s.getBranch());
        l.setCustomer(s.getCustomer());
        l.setPhone(s.getCustomer().getPhone());
        l.setMessageType("INVOICE");
        l.setReferenceType("SALE");
        l.setReferenceId(s.getId());
        l.setStatus(status);
        l.setErrorMessage(error == null ? null : error.length() > 1900 ? error.substring(0, 1900) : error);
        logs.save(l);
    }
}
