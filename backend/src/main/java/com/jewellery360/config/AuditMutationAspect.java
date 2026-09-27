package com.jewellery360.config;

import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Map;

@Aspect @Component @RequiredArgsConstructor
public class AuditMutationAspect {
    private final AuditService audit;
    private final HttpServletRequest request;

    @AfterReturning(pointcut="execution(* com.jewellery360.controller..*(..)) && ( @annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(org.springframework.web.bind.annotation.PutMapping) || @annotation(org.springframework.web.bind.annotation.PatchMapping) || @annotation(org.springframework.web.bind.annotation.DeleteMapping) )", returning="result")
    public void record(JoinPoint jp,Object result) {
        if(jp.getTarget().getClass().getSimpleName().equals("AuthController")) return;
        Object principal=SecurityContextHolder.getContext().getAuthentication()==null?null:SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(principal instanceof AuthenticatedUser me)) return;
        String action=jp.getSignature().getName().toUpperCase();
        audit.log(me,"HTTP_MUTATION_"+action,"Controller",null,null,Map.of("method",request.getMethod(),"path",request.getRequestURI()));
    }
}
