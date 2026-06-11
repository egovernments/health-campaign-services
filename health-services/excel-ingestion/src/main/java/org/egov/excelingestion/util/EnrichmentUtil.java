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
    public void logValidationErrors(String referenceId, String sheetName, List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        for (ValidationError error : errors) {
            if (ValidationConstants.STATUS_INVALID.equals(error.getStatus()) ||
                    ValidationConstants.STATUS_ERROR.equals(error.getStatus())) {
                log.error("VALIDATION_ERROR | referenceId={} | sheet={} | row={} | column={} | status={} | error={}",
                        referenceId, sheetName, error.getRowNumber(), error.getColumnName(),
                        error.getStatus(), error.getErrorDetails());
            }
        }
    }

    public void enrichErrorAndStatusInAdditionalDetails(ProcessResource resource, List<ValidationError> validationErrors) {
        enrichErrorAndStatusInAdditionalDetails(resource, validationErrors, null);
    }

    public void enrichErrorAndStatusInAdditionalDetails(ProcessResource resource, List<ValidationError> validationErrors, String sheetKind) {
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

            // Set per-sheet status if sheetKind is provided
            if (sheetKind != null && !sheetKind.isEmpty()) {
                String perSheetStatus = currentErrorCount > 0 ? ValidationConstants.STATUS_INVALID : ValidationConstants.STATUS_VALID;
                String statusKey = getPerSheetStatusKey(sheetKind);

                // Only set the per-sheet status if not already set (once invalid, stays invalid)
                if (!additionalDetails.containsKey(statusKey)) {
                    additionalDetails.put(statusKey, perSheetStatus);
                } else {
                    String existing = (String) additionalDetails.get(statusKey);
                    // Keep INVALID if already set, otherwise update to new status
                    if (!ValidationConstants.STATUS_INVALID.equals(existing)) {
                        additionalDetails.put(statusKey, perSheetStatus);
                    }
                }
            }

            // Determine overall validation status (AND logic: invalid if any sheet is invalid)
            String currentValidationStatus = computeOverallValidationStatus(additionalDetails);
            additionalDetails.put("validationStatus", currentValidationStatus);

            log.info("Enriched additionalDetails for resource {}: sheet={}, errors={}, total errors={}, validation status={}",
                    resource.getId(), sheetKind, currentErrorCount, totalErrorCount, currentValidationStatus);

        } catch (Exception e) {
            log.error("Error enriching additionalDetails with error count and status: {}", e.getMessage(), e);
        }
    }

    private String getPerSheetStatusKey(String sheetKind) {
        switch (sheetKind) {
            case ValidationConstants.SHEET_KIND_USER:
                return ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS;
            case ValidationConstants.SHEET_KIND_BOUNDARY:
                return ValidationConstants.ADDITIONAL_DETAILS_BOUNDARY_SHEET_STATUS;
            case ValidationConstants.SHEET_KIND_FACILITY:
                return ValidationConstants.ADDITIONAL_DETAILS_FACILITY_SHEET_STATUS;
            default:
                return null;
        }
    }

    private String computeOverallValidationStatus(Map<String, Object> additionalDetails) {
        // Check per-sheet statuses if they exist
        String userStatus = (String) additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_USER_SHEET_STATUS);
        String boundaryStatus = (String) additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_BOUNDARY_SHEET_STATUS);
        String facilityStatus = (String) additionalDetails.get(ValidationConstants.ADDITIONAL_DETAILS_FACILITY_SHEET_STATUS);

        // If any per-sheet status is set and is invalid, overall is invalid
        if (ValidationConstants.STATUS_INVALID.equals(userStatus) ||
            ValidationConstants.STATUS_INVALID.equals(boundaryStatus) ||
            ValidationConstants.STATUS_INVALID.equals(facilityStatus)) {
            return ValidationConstants.STATUS_INVALID;
        }

        // If any per-sheet status is valid, continue checking
        if (userStatus != null || boundaryStatus != null || facilityStatus != null) {
            // All per-sheet statuses are either valid or not set
            return ValidationConstants.STATUS_VALID;
        }

        // Fallback: use totalErrors for backward compatibility (no per-sheet keys set)
        Object totalErrors = additionalDetails.get("totalErrors");
        if (totalErrors instanceof Number && ((Number) totalErrors).longValue() > 0) {
            return ValidationConstants.STATUS_INVALID;
        }

        return ValidationConstants.STATUS_VALID;
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