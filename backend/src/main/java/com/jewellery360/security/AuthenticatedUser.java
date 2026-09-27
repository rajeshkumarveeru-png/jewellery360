package com.jewellery360.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;

@Getter
public class AuthenticatedUser {
    private final Long userId;
    private final Long companyId;
    private final Long branchId;
    private final String username;
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;
    public AuthenticatedUser(Long userId,Long companyId,Long branchId,String username,String role){
        this.userId=userId;this.companyId=companyId;this.branchId=branchId;this.username=username;this.role=role;
        this.authorities=List.of(new SimpleGrantedAuthority("ROLE_"+role));
    }
}
