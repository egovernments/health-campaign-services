package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for enriching GenerateResource objects
 */
@Component
@Slf4j
public class EnrichmentUtil {


    /**
     * Enrich ProcessResource additionalDetails with row count
     * If existing row counts exist, add to them; if not, create new entries
     * 
     * @param resource The ProcessResource to enrich
     * @param rowCount Number of rows processed in current sheet
     */
    public void enrichRowCountInAdditionalDetails(ProcessResource resource, int rowCount) {
        try {
            if (resource.getAdditionalDetails() == null) {
                resource.setAdditionalDetails(new HashMap<>());
            }
            
            Map<String, Object> additionalDetails = resource.getAdditionalDetails();
            
            // Get existing row count, if any
            Long existingRowCount = 0L;
            if (additionalDetails.containsKey("totalRowsProcessed")) {
                Object existing = additionalDetails.get("totalRowsProcessed");
                if (existing instanceof Number) {
                    existingRowCount = ((Number) existing).longValue();
                }
            }
            
            // Add current rows to existing rows
            Long totalRowCount = existingRowCount + rowCount;
            additionalDetails.put("totalRowsProcessed", totalRowCount);
            
            log.info("Enriched additionalDetails for resource {}: existing rows={}, current rows={}, total rows={}", 
                    resource.getId(), existingRowCount, rowCount, totalRowCount);
            
        } catch (Exception e) {
            log.error("Error enriching additionalDetails with row count: {}", e.getMessage(), e);
        }
    }

    /**
     * Enrich ProcessResource additionalDetails with error count and validation status
     * If existing error counts exist, add to them; if not, create new entries
     * 
     * @param resource The ProcessResource to enrich
     * @param validationErrors List of validation errors from current processing
     */
    public void enrichErrorAndStatusInAdditionalDetails(ProcessResource resource, List<ValidationError> validationErrors) {
        try {
            if (resource.getAdditionalDetails() == null) {
                resource.setAdditionalDetails(new HashMap<>());
            }
            
            Map<String, Object> additionalDetails = resource.getAdditionalDetails();
            
            // Count actual validation errors (exclude valid status entries)
            long currentErrorCount = validationErrors.stream()
                    .filter(error -> ValidationConstants.STATUS_INVALID.equals(error.getStatus()) || 
                                   ValidationConstants.STATUS_ERROR.equals(error.getStatus()))
                    .count();
            
            // Get existing error count, if any
            Long existingErrorCount = 0L;
            if (additionalDetails.containsKey("totalErrors")) {
                Object existing = additionalDetails.get("totalErrors");
                if (existing instanceof Number) {
                    existingErrorCount = ((Number) existing).longValue();
                }
            }
            
            // Add current errors to existing errors
            Long totalErrorCount = existingErrorCount + currentErrorCount;
            additionalDetails.put("totalErrors", totalErrorCount);
            
            // Determine overall validation status
            String currentValidationStatus = totalErrorCount > 0 ? ValidationConstants.STATUS_INVALID : ValidationConstants.STATUS_VALID;
            
            // Update validation status (if there are any errors, overall status becomes INVALID)
            String existingValidationStatus = (String) additionalDetails.get("validationStatus");
            if (existingValidationStatus == null || ValidationConstants.STATUS_VALID.equals(existingValidationStatus)) {
                // If no existing status or existing is VALID, use current status
                additionalDetails.put("validationStatus", currentValidationStatus);
            } else if (ValidationConstants.STATUS_INVALID.equals(existingValidationStatus) && ValidationConstants.STATUS_VALID.equals(currentValidationStatus)) {
                // If existing is INVALID and current is VALID, keep INVALID (once invalid, stays invalid)
                additionalDetails.put("validationStatus", ValidationConstants.STATUS_INVALID);
            } else {
                // For any other case, use current status
                additionalDetails.put("validationStatus", currentValidationStatus);
            }
            
            log.info("Enriched additionalDetails for resource {}: existing errors={}, current errors={}, total errors={}, validation status={}", 
                    resource.getId(), existingErrorCount, currentErrorCount, totalErrorCount, additionalDetails.get("validationStatus"));
            
        } catch (Exception e) {
            log.error("Error enriching additionalDetails with error count and status: {}", e.getMessage(), e);
        }
    }

    /**
     * Enrich GenerateResource additionalDetails with error code and error message
     * This standardizes generate API error handling to match process API pattern
     * 
     * @param resource The GenerateResource to enrich
     * @param exception The exception that occurred during generation
     */
    public void enrichErrorDetailsInAdditionalDetails(GenerateResource resource, Exception exception) {
        try {
            if (resource.getAdditionalDetails() == null) {
                resource.setAdditionalDetails(new HashMap<>());
            }
            
            Map<String, Object> additionalDetails = resource.getAdditionalDetails();
            
            // Extract error code and message from exception
            String errorCode = extractErrorCode(exception);
            String errorMessage = extractErrorMessage(exception);
            
            // Add error details to additionalDetails (similar to process API pattern)
            additionalDetails.put("errorCode", errorCode);
            additionalDetails.put("errorMessage", errorMessage);
            
            log.info("Enriched additionalDetails for generate resource {}: errorCode={}, errorMessage={}", 
                    resource.getId(), errorCode, errorMessage);
            
        } catch (Exception e) {
            log.error("Error enriching additionalDetails with error details: {}", e.getMessage(), e);
        }
    }

    private String extractErrorCode(Exception exception) {
        if (exception == null) {
            return "GENERATION_FAILED";
        }
        
        // Find the root CustomException in the exception chain
        CustomException customException = findRootCustomException(exception);
        if (customException != null) {
            return customException.getCode() != null ? customException.getCode() : "GENERATION_FAILED";
        }
        
        // For other exceptions, return a generic error code
        return "GENERATION_FAILED";
    }

    private String extractErrorMessage(Exception exception) {
        if (exception == null) {
            return "Generation process failed due to unknown error";
        }
        
        // Find the root CustomException in the exception chain
        CustomException customException = findRootCustomException(exception);
        if (customException != null) {
            return customException.getMessage() != null ? customException.getMessage() : "Generation process failed";
        }
        
        // For other exceptions, return the exception message
        return exception.getMessage() != null ? exception.getMessage() : "Generation process failed";
    }
    
    private CustomException findRootCustomException(Exception exception) {
        if (exception == null) {
            return null;
        }
        
        // If it's already a CustomException, return it
        if (exception instanceof CustomException) {
            return (CustomException) exception;
        }
        
        // Check if the cause is a CustomException
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof CustomException) {
                return (CustomException) cause;
            }
            cause = cause.getCause();
        }
        
        // No CustomException found in the exception chain
        return null;
    }
}