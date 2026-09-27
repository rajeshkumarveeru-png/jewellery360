package com.jewellery360.security;

import com.jewellery360.domain.AppUser;
import com.jewellery360.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final AppUserRepository users;
    public JwtAuthenticationFilter(JwtService jwt,AppUserRepository users){this.jwt=jwt;this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        String h=req.getHeader("Authorization");
        if(h!=null && h.startsWith("Bearer ")){
            try{
                Claims c=jwt.parse(h.substring(7));
                Long uid=((Number)c.get("uid")).longValue();
                AppUser u=users.findById(uid).orElse(null);
                if(u!=null&&u.isEnabled()&&!u.isDeleted()){
                    var p=new AuthenticatedUser(u.getId(),u.getCompany()==null?null:u.getCompany().getId(),
                        u.getBranch()==null?null:u.getBranch().getId(),u.getUsername(),u.getRole().name());
                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
                }
            }catch(Exception ignored){}
        }
        chain.doFilter(req,res);
    }
}
