package com.jewellery360.service;

import com.jewellery360.domain.*;
import com.jewellery360.repository.*;
import com.jewellery360.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final AppUserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final UserRequestRepository requests;
    private final PropertyRepository properties;
    private final OtpChallengeRepository otps;
    private final WhatsAppService whatsapp;
    private final PermissionService permissionService;

    @Transactional
    public Map<String, Object> login(String identifier, String password) {
        String value = clean(identifier);

        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your username, email, or phone number.");
        }

        AppUser u = null;
        if (value.contains("@")) {
            u = users.findByEmailIgnoreCaseAndDeletedFalse(value.toLowerCase(Locale.ROOT)).orElse(null);
        } else {
            // Username is globally unique, so it is always an unambiguous login identifier.
            u = users.findByUsernameIgnoreCaseAndDeletedFalse(value).orElse(null);
            // If there is no username match, also allow the registered WhatsApp/phone number.
            if (u == null) {
                String phone = normalizePhone(value);
                if (phone.matches("[0-9]{10,15}")) {
                    u = users.findByPhoneAndDeletedFalse(phone).orElse(null);
                }
            }
        }
        if (u == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username, email, phone number, or password.");
        }

        if (!u.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is pending administrator approval or has been deactivated."
            );
        }

        if (password == null || !encoder.matches(password, u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username, email, phone number, or password.");
        }

        return Map.of(
                "token",
                jwt.create(
                        u.getId(),
                        u.getCompany() == null ? null : u.getCompany().getId(),
                        u.getUsername(),
                        u.getRole().name()
                ),
                "user",
                userResponse(u)
        );
    }

    /**
     * Creates a Company + fixed Main Branch + COMPANY_ADMIN account and a
     * NEW_ACCOUNT approval request. The account is deliberately disabled
     * until APP_ADMIN approves it.
     */
    @Transactional
    public Map<String, Object> registerCompany(
            String companyName,
            String username,
            String email,
            String phone,
            String password,
            String confirmPassword
    ) {
        String cleanCompany = clean(companyName);
        String cleanUsername = clean(username);
        String cleanEmail = clean(email).toLowerCase(Locale.ROOT);
        String cleanPhone = normalizePhone(phone);
        String cleanPassword = password == null ? "" : password;
        String cleanConfirm = confirmPassword == null ? "" : confirmPassword;

        if (cleanCompany.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company name is required.");
        if (cleanUsername.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required.");
        if (!cleanUsername.matches(".*[A-Za-z].*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must contain at least one letter (a number-only username could be mistaken for a mobile number).");
        }
        if (!cleanUsername.matches("[A-Za-z0-9._-]{3,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be 3-80 characters and may contain letters, numbers, dot, underscore, or hyphen.");
        }
        if (!isValidEmail(cleanEmail)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        if (cleanPhone.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WhatsApp phone number is required.");
        if (!cleanPhone.matches("[0-9]{10,15}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid WhatsApp phone number with 10-15 digits.");
        if (cleanPassword.length() < 8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters.");
        if (!cleanPassword.equals(cleanConfirm)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match.");

        if (users.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This email is already registered. Use a different email address.");
        }
        if (users.existsByPhone(cleanPhone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This WhatsApp phone number is already registered. Use a different phone number.");
        }
        if (companies.existsByNameIgnoreCase(cleanCompany)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A company with this name already exists.");
        }

        Company company = new Company();
        company.setName(cleanCompany);
        company.setCode("J360-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        company.setPhone(cleanPhone);
        company.setEmail(cleanEmail);
        company.setActive(false);
        companies.save(company);

        // Username is a platform-wide login identifier. It must be unique across all companies.
        if (users.existsByUsernameIgnoreCaseAndDeletedFalse(cleanUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This username is already registered. Use a different username.");
        }

        Branch branch = new Branch();
        branch.setCompany(company);
        // Every new company starts with one fixed operating branch. The public registration form does not allow changing it.
        branch.setName("Main Branch");
        branch.setCode("BR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        branch.setPhone(cleanPhone);
        branch.setActive(false);
        branches.save(branch);

        AppUser user = new AppUser();
        user.setUsername(cleanUsername);
        user.setEmail(cleanEmail);
        user.setPhone(cleanPhone);
        user.setPasswordHash(encoder.encode(cleanPassword));
        // Registration always creates the company owner/admin. The role is never supplied by the public form.
        user.setRole(AppRole.COMPANY_ADMIN);
        user.setCompany(company);
        user.setBranch(branch);
        user.setEnabled(false);
        users.save(user);

        UserRequest request = new UserRequest();
        request.setRequestType("NEW_ACCOUNT");
        request.setStatus("PENDING");
        request.setTargetUser(user);
        request.setCompany(company);
        request.setMessage("New Company Admin registration awaiting App Admin approval.");
        requests.save(request);

        log.info("REGISTER: pending company registration created requestId={}, userId={}, companyId={}, branchId={}, username={}",
                request.getId(), user.getId(), company.getId(), branch.getId(), user.getUsername());

        return Map.of(
                "pendingApproval", true,
                "requestId", request.getId(),
                "message", "Company registration submitted successfully. An App Admin must approve the request before you can sign in."
        );
    }

    @Transactional
    public Map<String, Object> requestAdminReset(String identifier, String password, String confirm) {
        AppUser u = findActive(identifier);
        if (!password.equals(confirm) || password.length() < 8)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and match confirmation"
            );
        if (requests.existsByTargetUserIdAndRequestTypeAndStatus(u.getId(), "PASSWORD_RESET", "PENDING"))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A reset request is already pending"
            );
        UserRequest r = new UserRequest();
        r.setRequestType("PASSWORD_RESET");
        r.setStatus("PENDING");
        r.setTargetUser(u);
        r.setCompany(u.getCompany());
        r.setRequestedBy(u);
        r.setPendingPasswordHash(encoder.encode(password));
        r.setMessage("Password reset request awaiting administrator approval.");
        requests.save(r);
        return Map.of("message", "Password reset request submitted for administrator approval.");
    }

    @Transactional
    public Map<String, Object> requestOtp(String identifier) {
        AppUser u = findActive(identifier);
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        OtpChallenge o = new OtpChallenge();
        o.setUser(u);
        o.setOtpHash(encoder.encode(code));
        o.setExpiresAt(Instant.now().plusSeconds(300));
        otps.save(o);
        whatsapp.send(u, "Your Jewellery360 password reset OTP is " + code + ". It expires in 5 minutes.");
        return Map.of(
                "message",
                "If WhatsApp OTP is enabled, the OTP has been sent to the registered phone.",
                "challengeId",
                o.getId()
        );
    }

    @Transactional
    public Map<String, Object> verifyOtp(Long challengeId, String code, String newPassword) {
        OtpChallenge o = otps.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid OTP request"
                ));
        if (o.isConsumed()
                || o.getExpiresAt().isBefore(Instant.now())
                || o.getAttempts() >= 5
                || !encoder.matches(code, o.getOtpHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or expired OTP"
            );
        }
        if (newPassword == null || newPassword.length() < 8)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters"
            );
        o.setConsumed(true);
        otps.save(o);
        o.getUser().setPasswordHash(encoder.encode(newPassword));
        users.save(o.getUser());
        return Map.of("message", "Password reset successfully.");
    }

    /** Live "is it already taken?" answers for the create-company and create-user forms (public, no personal data is returned). */
    @Transactional(readOnly = true)
    public Map<String, Object> availability(String username, String email, String phone, String company) {
        Map<String, Object> m = new LinkedHashMap<>();
        String u = clean(username);
        if (!u.isBlank()) m.put("usernameTaken", users.existsByUsernameIgnoreCaseAndDeletedFalse(u));
        String e = clean(email).toLowerCase(Locale.ROOT);
        if (!e.isBlank()) m.put("emailTaken", users.existsByEmailIgnoreCase(e));
        String p = normalizePhone(phone);
        if (!p.isBlank()) m.put("phoneTaken", users.existsByPhone(p));
        String c = clean(company);
        if (!c.isBlank()) m.put("companyTaken", companies.existsByNameIgnoreCase(c));
        return m;
    }

    /** First step of "Forgot password": is there such an account, and can it receive a WhatsApp OTP? */
    @Transactional(readOnly = true)
    public Map<String, Object> validateResetIdentifier(String identifier) {
        AppUser u = findActive(identifier);
        if (!u.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is not active yet. Ask your administrator to approve it first.");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", "Account verified. You can now create your new password.");
        m.put("hasPhone", u.getPhone() != null && !u.getPhone().isBlank());
        return m;
    }

    private AppUser findActive(String id) {
        String value = clean(id);
        AppUser user = null;
        if (value.contains("@")) {
            user = users.findByEmailIgnoreCaseAndDeletedFalse(value.toLowerCase(Locale.ROOT)).orElse(null);
        } else {
            user = users.findByUsernameIgnoreCaseAndDeletedFalse(value).orElse(null);
            if (user == null) {
                String phone = normalizePhone(value);
                if (phone.matches("[0-9]{10,15}")) {
                    user = users.findByPhoneAndDeletedFalse(phone).orElse(null);
                }
            }
        }
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid username, email, or phone number");
        return user;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private boolean isValidEmail(String value) {
        return value != null && value.matches("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userResponse(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("role", u.getRole().name());
        m.put("enabled", u.isEnabled());
        m.put("companyId", u.getCompany() == null ? null : u.getCompany().getId());
        m.put("companyName", u.getCompany() == null ? null : u.getCompany().getName());
        m.put("branchId", u.getBranch() == null ? null : u.getBranch().getId());
        m.put("branchName", u.getBranch() == null ? null : u.getBranch().getName());
        m.put("permissions", permissionService.effective(u));
        m.put("createdAt", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
        return m;
    }
}
