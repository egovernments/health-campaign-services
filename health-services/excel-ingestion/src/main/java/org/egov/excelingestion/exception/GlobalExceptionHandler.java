package org.egov.excelingestion.exception;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;

/**
 * Global exception handler for excel-ingestion service
 * Formats CustomException responses to match health services error model
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle CustomException and format response according to health services pattern
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(CustomException ex) {
        log.error("CustomException occurred: {} - {}", ex.getCode(), ex.getMessage(), ex);
        
        // Extract description from error message or exception cause
        String description = ex.getMessage();
        String message = ex.getMessage();
        // If message contains "::: " it means we combined error message with original exception
        if (description != null && description.contains("::: ")) {
            String[] parts = description.split("::: ", 2);
            description = parts.length > 1 ? parts[1] : description;
            message = parts[0];
        } else if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            description = ex.getCause().getMessage();
        }
        
        // Create simple error without using Error class to avoid tracerModel serialization
        Map<String, Object> error = Map.of(
                "errorCode", ex.getCode(),
                "errorMessage", message,
                "description", description
        );
        
        // Create failed response info
        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId("excel-ingestion")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .status("failed")
                .build();
        
        Map<String, Object> response = Map.of(
                "ResponseInfo", responseInfo,
                "Errors", Collections.singletonList(error)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);
        
        // Extract description from exception details or fallback to message
        String description = null;
        if (ex.getCause() != null) {
            description = ex.getCause().getMessage();
        }
        description = description != null ? description : ex.getMessage();
        
        // Create simple error without using Error class to avoid tracerModel serialization
        Map<String, Object> error = Map.of(
                "errorCode", ErrorConstants.INTERNAL_SERVER_ERROR,
                "errorMessage", ErrorConstants.INTERNAL_SERVER_ERROR_MESSAGE,
                "description", description
        );
        
        // Create failed response info
        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId("excel-ingestion")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .status("failed")
                .build();
        
        Map<String, Object> response = Map.of(
                "ResponseInfo", responseInfo,
                "Errors", Collections.singletonList(error)
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}