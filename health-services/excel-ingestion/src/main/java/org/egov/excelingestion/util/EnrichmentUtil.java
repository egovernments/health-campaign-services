package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
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
}