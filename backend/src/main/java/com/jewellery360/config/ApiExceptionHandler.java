package com.jewellery360.config;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/** Turns the common database failures into short, clear messages instead of a stack trace or a generic 500. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> concurrent(Exception e) {
        return body(HttpStatus.CONFLICT, "Someone else changed this record at the same time. Please refresh and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        log.warn("Data integrity violation: {}", detail);
        String msg = "This conflicts with an existing record.";
        if (detail.contains("uk_item_company_tag")) msg = "This tag number is already used.";
        else if (detail.contains("uk_item_company_barcode")) msg = "This barcode is already used.";
        else if (detail.contains("uk_sale_company_client_ref")) msg = "This bill was already saved.";
        else if (detail.contains("_company_name") || detail.contains("_company_code") || detail.contains("_company_sku")) msg = "A record with this name or code already exists.";
        else if (detail.contains("violates foreign key")) msg = "This record is still used elsewhere and cannot be changed this way.";
        return body(HttpStatus.CONFLICT, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "The request could not be read. Check the numbers and dates you entered.");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now().toString(), "status", status.value(),
                "error", status.getReasonPhrase(), "message", message));
    }
}
