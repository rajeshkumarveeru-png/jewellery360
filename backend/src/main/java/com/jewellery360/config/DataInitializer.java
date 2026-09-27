package com.jewellery360.config;

import com.jewellery360.domain.*;
import com.jewellery360.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean CommandLineRunner seedAdmin(AppUserRepository users,PasswordEncoder encoder){
        return args -> {
            if(!users.existsByUsernameIgnoreCaseAndDeletedFalse("admin")){
                AppUser u=new AppUser();u.setUsername("admin");u.setEmail("admin@jewellery360.local");
                u.setPasswordHash(encoder.encode("admin123"));u.setRole(AppRole.APP_ADMIN);u.setEnabled(true);u.setDeleted(false);users.save(u);
            }
        };
    }
}
