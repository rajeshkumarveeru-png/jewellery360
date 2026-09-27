package com.jewellery360.service;

import com.jewellery360.repository.UserPermissionRepository;
import com.jewellery360.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class PermissionService {
    private final UserPermissionRepository userPermissions;
    private static final Map<String,Set<String>> ROLE_MODULES = new HashMap<>();
    static {
        ROLE_MODULES.put("APP_ADMIN",Set.of("ALL"));
        ROLE_MODULES.put("COMPANY_ADMIN",Set.of("ALL_COMPANY"));
        ROLE_MODULES.put("MANAGER",Set.of("OVERVIEW","BILLING","JEWELLERY","GOLD & RATES","INVENTORY","PURCHASES","OLD GOLD","CUSTOMERS","SERVICES","PAYMENTS","REPORTS","AUDIT LOGS","WHATSAPP"));
        ROLE_MODULES.put("CASHIER",Set.of("OVERVIEW","BILLING","CUSTOMERS","PAYMENTS","AUDIT LOGS"));
        ROLE_MODULES.put("SALESMAN",Set.of("OVERVIEW","BILLING","CUSTOMERS","SERVICES","AUDIT LOGS"));
        ROLE_MODULES.put("INVENTORY_MANAGER",Set.of("OVERVIEW","JEWELLERY","INVENTORY","PURCHASES","OLD GOLD","REPORTS","AUDIT LOGS"));
        ROLE_MODULES.put("ACCOUNTANT",Set.of("OVERVIEW","CUSTOMERS","PAYMENTS","REPORTS","AUDIT LOGS"));
        ROLE_MODULES.put("VIEWER",Set.of("OVERVIEW","CUSTOMERS","INVENTORY","REPORTS"));
    }
    public boolean canModule(AuthenticatedUser u,String module){
        if(u==null)return false;
        if("APP_ADMIN".equals(u.getRole())||"COMPANY_ADMIN".equals(u.getRole())) return true;
        return ROLE_MODULES.getOrDefault(u.getRole(),Set.of()).contains(module.toUpperCase());
    }
    public boolean has(AuthenticatedUser u,String module,String action){
        if(!canModule(u,module)) return false;
        if("APP_ADMIN".equals(u.getRole())) return true;
        String normalized=module.toUpperCase().replace(" & ","_").replace(" ","_");
        String code=normalized+"_"+action.toUpperCase();
        var explicit=userPermissions.findByUserIdAndPermission_Code(u.getUserId(),code);
        return explicit.map(x->x.isGranted()).orElse(!"VIEWER".equals(u.getRole()) || "VIEW".equals(action));
    }
    public void requireModule(AuthenticatedUser u,String module){if(!canModule(u,module))throw forbidden("You do not have permission for "+module);}
    public void requireAction(AuthenticatedUser u,String module,String action){if(!has(u,module,action))throw forbidden("Permission denied: "+module+"/"+action);}
    public void requireWrite(AuthenticatedUser u,String module){requireAction(u,module,"CREATE");}
    public void requireAppAdmin(AuthenticatedUser u){if(!"APP_ADMIN".equals(u.getRole()))throw forbidden("APP_ADMIN only");}
    private ResponseStatusException forbidden(String s){return new ResponseStatusException(HttpStatus.FORBIDDEN,s);}
}
