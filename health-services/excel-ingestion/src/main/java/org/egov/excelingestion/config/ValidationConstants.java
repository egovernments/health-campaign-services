package org.egov.excelingestion.config;

public class ValidationConstants {
    
    // Column names (4 underscores to avoid collision)
    public static final String STATUS_COLUMN_NAME = "HCM_ADMIN_CONSOLE____ROW_STATUS";
    public static final String ERROR_DETAILS_COLUMN_NAME = "HCM_ADMIN_CONSOLE____ERROR_DETAILS";
    
    // Status values
    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_INVALID = "INVALID";
    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_ERROR = "ERROR";
    
    private ValidationConstants() {
        // Private constructor to prevent instantiation
    }
}