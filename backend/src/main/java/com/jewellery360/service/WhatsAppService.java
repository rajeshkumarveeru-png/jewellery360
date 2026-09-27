package com.jewellery360.service;

import com.jewellery360.domain.AppUser;
import com.jewellery360.domain.Property;
import com.jewellery360.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class WhatsAppService {
    private final PropertyRepository properties;
    private final RestClient rest=RestClient.create();

    public void send(AppUser user,String text){
        if(user.getCompany()==null || user.getPhone()==null || user.getPhone().isBlank()) return;
        String enabled=value(user,CompanyPropertyService.WHATSAPP_ENABLED,"false");
        if(!Boolean.parseBoolean(enabled)) return;
        String token=value(user,CompanyPropertyService.WHATSAPP_ACCESS_TOKEN,"");
        String phoneId=value(user,CompanyPropertyService.WHATSAPP_PHONE_NUMBER_ID,"");
        String graph=value(user,CompanyPropertyService.WHATSAPP_GRAPH_VERSION,"v23.0");
        if(token.isBlank()||phoneId.isBlank()) return;
        String to=user.getPhone().replaceAll("\\D","");
        String body="{\"messaging_product\":\"whatsapp\",\"to\":\""+to+"\",\"type\":\"text\",\"text\":{\"body\":\""+escape(text)+"\"}}";
        rest.post().uri("https://graph.facebook.com/"+graph+"/"+phoneId+"/messages")
            .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .body(body).retrieve().toBodilessEntity();
    }
    public boolean isConfigured(Long companyId){
        if (companyId == null) return false;
        String enabled = properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_ENABLED)
                .map(Property::getValue).orElse("false");
        String token = properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_ACCESS_TOKEN)
                .map(Property::getValue).orElse("");
        String phoneId = properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_PHONE_NUMBER_ID)
                .map(Property::getValue).orElse("");
        return Boolean.parseBoolean(enabled) && !token.isBlank() && !phoneId.isBlank();
    }

    public void sendText(Long companyId,String phone,String text){
        String enabled=properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_ENABLED).map(Property::getValue).orElse("false");
        if(!Boolean.parseBoolean(enabled)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"WhatsApp integration is not enabled for this company");
        String token=properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_ACCESS_TOKEN).map(Property::getValue).orElse("");
        String phoneId=properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_PHONE_NUMBER_ID).map(Property::getValue).orElse("");
        String graph=properties.findByCompanyIdAndKeyAndActiveTrue(companyId,CompanyPropertyService.WHATSAPP_GRAPH_VERSION).map(Property::getValue).orElse("v23.0");
        if(token.isBlank()||phoneId.isBlank()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"WhatsApp credentials are not configured");
        String to=normalizePhone(phone);
        String body="{\"messaging_product\":\"whatsapp\",\"to\":\""+to+"\",\"type\":\"text\",\"text\":{\"body\":\""+escape(text)+"\"}}";
        rest.post().uri("https://graph.facebook.com/"+graph+"/"+phoneId+"/messages").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }



    private String normalizePhone(String phone){
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() == 10) digits = "91" + digits;
        if (!digits.matches("91\\d{10}")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A valid Indian WhatsApp mobile number is required."
            );
        }
        return digits;
    }

    private String value(AppUser u,String key,String def){return properties.findByCompanyIdAndKeyAndActiveTrue(u.getCompany().getId(),key).map(Property::getValue).orElse(def);}
    private String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}
