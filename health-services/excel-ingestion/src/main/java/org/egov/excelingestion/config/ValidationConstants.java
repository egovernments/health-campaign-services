package org.egov.excelingestion.config;

public class ValidationConstants {
    
    // Column names (4 underscores to avoid collision)
    public static final String STATUS_COLUMN_NAME = "HCM_ADMIN_CONSOLE____ROW_STATUS";
    public static final String ERROR_DETAILS_COLUMN_NAME = "HCM_ADMIN_CONSOLE____ERROR_DETAILS";
    
    // Status values
    public static final String STATUS_VALID = "valid";
    public static final String STATUS_INVALID = "invalid";
    public static final String STATUS_CREATED = "created";
    public static final String STATUS_ERROR = "error";
    
    // Multi-select validation constants
    public static final String MULTI_SELECT_SEPARATOR = ",";
    public static final String HCM_VALIDATION_DUPLICATE_SELECTIONS = "HCM_VALIDATION_DUPLICATE_SELECTIONS";
    public static final String HCM_VALIDATION_REQUIRED_MULTI_SELECT = "HCM_VALIDATION_REQUIRED_MULTI_SELECT";
    
    // Error message constants
    public static final String HCM_VALIDATION_FAILED_NO_DETAILS = "HCM_VALIDATION_FAILED_NO_DETAILS";
    
    // Search validation constants
    public static final String INGEST_INVALID_LIMIT = "INGEST_INVALID_LIMIT";
    public static final String INGEST_INVALID_OFFSET = "INGEST_INVALID_OFFSET";
    
    private ValidationConstants() {
        // Private constructor to prevent instantiation
    }
}