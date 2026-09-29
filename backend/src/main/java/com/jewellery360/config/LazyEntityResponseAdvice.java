package com.jewellery360.config;

import com.jewellery360.service.LazyEntityPreparationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Application-wide guard for REST responses containing JPA entities.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class LazyEntityResponseAdvice implements ResponseBodyAdvice<Object> {

    private final LazyEntityPreparationService preparer;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body != null && !(body instanceof byte[])
                && (selectedContentType == null || !MediaType.APPLICATION_OCTET_STREAM.includes(selectedContentType))) {
            preparer.prepare(body);
        }
        return body;
    }
}
