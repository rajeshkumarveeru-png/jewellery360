package com.jewellery360.controller;

import com.jewellery360.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest r) {
        return auth.login(r.identifier(), r.password());
    }

    /**
     * Public company-registration endpoint.
     *
     * /register is the canonical endpoint used by the current frontend.
     * /register-company is retained as a backward-compatible alias so an
     * older frontend build cannot silently fail with HTTP 404.
     */
    @PostMapping({"/register", "/register-company"})
    public Map<String, Object> register(@RequestBody RegisterRequest r) {
        return auth.registerCompany(
                r.companyName(),
                r.username(),
                r.email(),
                r.phone(),
                r.password(),
                r.confirmPassword()
        );
    }

    @PostMapping("/password-reset/admin")
    public Map<String, Object> adminReset(@RequestBody AdminResetRequest r) {
        return auth.requestAdminReset(r.identifier(), r.password(), r.confirmPassword());
    }

    @PostMapping("/password-reset/otp/request")
    public Map<String, Object> otpRequest(@RequestBody IdentifierRequest r) {
        return auth.requestOtp(r.identifier());
    }

    @PostMapping("/password-reset/otp/verify")
    public Map<String, Object> otpVerify(@RequestBody OtpResetRequest r) {
        return auth.verifyOtp(r.challengeId(), r.otp(), r.newPassword());
    }

    public record LoginRequest(String identifier, String password) {}
    public record IdentifierRequest(String identifier) {}
    public record RegisterRequest(
            String companyName,
            String username,
            String email,
            String phone,
            String password,
            String confirmPassword
    ) {}
    public record AdminResetRequest(String identifier, String password, String confirmPassword) {}
    public record OtpResetRequest(Long challengeId, String otp, String newPassword) {}
}
