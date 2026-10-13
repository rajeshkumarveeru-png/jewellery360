package com.jewellery360.config;

import com.jewellery360.domain.*;
import com.jewellery360.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates the platform administrator ("admin") on the very first start. The password comes from
 * APP_BOOTSTRAP_ADMIN_PASSWORD; without it a random one-time password is generated and written to the log once.
 * There is no built-in default password, so a fresh production install never has a known login.
 */
@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean CommandLineRunner seedAdmin(AppUserRepository users, PasswordEncoder encoder,
                                      @Value("${app.bootstrap.admin-password:}") String configured) {
        return args -> {
            if (users.existsByUsernameIgnoreCaseAndDeletedFalse("admin")) return;
            boolean generated = configured == null || configured.isBlank();
            String password = generated ? randomPassword() : configured;
            AppUser u = new AppUser();
            u.setUsername("admin");
            u.setEmail("admin@jewellery360.local");
            u.setPasswordHash(encoder.encode(password));
            u.setRole(AppRole.APP_ADMIN);
            u.setEnabled(true);
            u.setDeleted(false);
            users.save(u);
            if (generated)
                log.warn("Created the platform administrator 'admin' with the one-time password: {}  - sign in and change it now. "
                        + "(Set APP_BOOTSTRAP_ADMIN_PASSWORD to choose it instead.)", password);
            else log.info("Created the platform administrator 'admin' with the password from APP_BOOTSTRAP_ADMIN_PASSWORD.");
        };
    }

    private static String randomPassword() {
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
