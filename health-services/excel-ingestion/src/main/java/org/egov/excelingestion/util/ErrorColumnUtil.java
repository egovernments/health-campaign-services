package org.egov.excelingestion.util;

import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for creating error and status column definitions
 * Ensures consistent formatting across all validation processors
 */
@Component
public class ErrorColumnUtil {

    /**
     * Creates column definitions for error and status columns
     * Matches the styling used in ValidationService.addValidationColumns
     * 
     * @param localizationMap Map for localized messages
     * @return List containing error and status column definitions
     */
    public List<ColumnDef> createErrorColumnDefs(Map<String, String> localizationMap) {
        List<ColumnDef> errorColumns = new ArrayList<>();
        
        // Status column definition - matches ValidationService styling
        ColumnDef statusColumnDef = new ColumnDef();
        statusColumnDef.setTechnicalName(ValidationConstants.STATUS_COLUMN_NAME);
        statusColumnDef.setName(ValidationConstants.STATUS_COLUMN_NAME); // Name will be localized by Excel populator
        statusColumnDef.setType("string");
        statusColumnDef.setShowInProcessed(true);
        statusColumnDef.setWrapText(false); // No wrap text for status column
        statusColumnDef.setWidth(20); // ~20 characters as per ValidationService (5000/250)
        statusColumnDef.setColorHex("#FFFF00"); // Yellow background as per applyYellowHeaderStyle
        statusColumnDef.setFreezeColumn(true); // Lock/freeze the status column
        statusColumnDef.setOrderNumber(9998); // Ensure it comes near the end
        errorColumns.add(statusColumnDef);
        
        // Error details column definition - matches ValidationService styling  
        ColumnDef errorColumnDef = new ColumnDef();
        errorColumnDef.setTechnicalName(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
        errorColumnDef.setName(ValidationConstants.ERROR_DETAILS_COLUMN_NAME); // Name will be localized by Excel populator
        errorColumnDef.setType("string");
        errorColumnDef.setShowInProcessed(true);
        errorColumnDef.setWrapText(true); // Wrap text for long error messages
        errorColumnDef.setWidth(40); // ~40 characters as per ValidationService (10000/250)
        errorColumnDef.setColorHex("#FFFF00"); // Yellow background as per applyYellowHeaderStyle
        errorColumnDef.setFreezeColumn(true); // Lock/freeze the error details column
        errorColumnDef.setOrderNumber(9999); // Ensure it comes at the very end
        errorColumns.add(errorColumnDef);
        
        return errorColumns;
    }
    
    /**
     * Creates a single status column definition
     */
    public ColumnDef createStatusColumnDef() {
        ColumnDef statusColumnDef = new ColumnDef();
        statusColumnDef.setTechnicalName(ValidationConstants.STATUS_COLUMN_NAME);
        statusColumnDef.setName(ValidationConstants.STATUS_COLUMN_NAME);
        statusColumnDef.setType("string");
        statusColumnDef.setShowInProcessed(true);
        statusColumnDef.setWrapText(false); // No wrap text for status column
        statusColumnDef.setWidth(20);
        statusColumnDef.setColorHex("#FFFF00");
        statusColumnDef.setFreezeColumn(true); // Lock/freeze the status column
        statusColumnDef.setOrderNumber(9998);
        return statusColumnDef;
    }
    
    /**
     * Creates a single error details column definition
     */
    public ColumnDef createErrorDetailsColumnDef() {
        ColumnDef errorColumnDef = new ColumnDef();
        errorColumnDef.setTechnicalName(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
        errorColumnDef.setName(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
        errorColumnDef.setType("string");
        errorColumnDef.setShowInProcessed(true);
        errorColumnDef.setWrapText(true);
        errorColumnDef.setWidth(40);
        errorColumnDef.setColorHex("#FFFF00");
        errorColumnDef.setFreezeColumn(true); // Lock/freeze the error details column
        errorColumnDef.setOrderNumber(9999);
        return errorColumnDef;
    }
}