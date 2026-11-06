package org.egov.excelingestion.exception;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles validation exceptions and formats them according to health services pattern
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ValidationExceptionHandler {

    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        
        log.error("Validation error occurred: {}", ex.getMessage());
        
        // Extract all field errors
        List<Map<String, Object>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::createErrorFromFieldError)
                .collect(Collectors.toList());
        
        // Create failed response info
        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId("excel-ingestion")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .status("failed")
                .build();
        
        Map<String, Object> response = Map.of(
                "ResponseInfo", responseInfo,
                "Errors", errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        
        log.error("Constraint violation error occurred: {}", ex.getMessage());
        
        // Extract all constraint violations
        List<Map<String, Object>> errors = ex.getConstraintViolations()
                .stream()
                .map(this::createErrorFromConstraintViolation)
                .collect(Collectors.toList());
        
        // Create failed response info
        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId("excel-ingestion")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .status("failed")
                .build();
        
        Map<String, Object> response = Map.of(
                "ResponseInfo", responseInfo,
                "Errors", errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Create error object from field error
     */
    private Map<String, Object> createErrorFromFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String errorCode = fieldError.getDefaultMessage(); // This will be our error code like "INGEST_MISSING_TENANT_ID"
        
        return Map.of(
                "errorCode", errorCode,
                "errorMessage", errorCode,  // Using same as errorCode as requested
                "description", "Validation failed for field: " + field
        );
    }
    
    /**
     * Create error object from constraint violation
     */
    private Map<String, Object> createErrorFromConstraintViolation(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        String errorCode = violation.getMessage(); // This will be our error code
        
        return Map.of(
                "errorCode", errorCode,
                "errorMessage", errorCode,
                "description", "Validation failed for field: " + field
        );
    }
}