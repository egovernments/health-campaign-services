package org.egov.projectfactory.exception;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.projectfactory.config.ErrorConstants;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;

/**
 * Global exception handler. Formats {@link CustomException} (from the tracer library)
 * into the health-services error response shape. Mirrors the excel-ingestion handler
 * so error responses stay consistent across the absorbed services.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String API_ID = "project-factory-saga";
    private static final String VERSION = "1.0";

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(CustomException ex) {
        log.error("CustomException occurred: {} - {}", ex.getCode(), ex.getMessage(), ex);

        String description = ex.getMessage();
        String message = ex.getMessage();
        if (description != null && description.contains("::: ")) {
            String[] parts = description.split("::: ", 2);
            description = parts.length > 1 ? parts[1] : description;
            message = parts[0];
        } else if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            description = ex.getCause().getMessage();
        }

        return buildResponse(ex.getCode(), message, description);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);

        String description = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        return buildResponse(ErrorConstants.INTERNAL_SERVER_ERROR,
                ErrorConstants.INTERNAL_SERVER_ERROR_MESSAGE, description);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(String code, String message, String description) {
        Map<String, Object> error = Map.of(
                "errorCode", code,
                "errorMessage", message == null ? "" : message,
                "description", description == null ? "" : description
        );

        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId(API_ID)
                .ver(VERSION)
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
