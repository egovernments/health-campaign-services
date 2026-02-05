package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";
    
    // Status Constants
    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    
    // Table Constants
    public static final String PROCESS_TABLE_NAME = "eg_ex_in_excel_processing";
    
    private ProcessingConstants() {
        // Private constructor to prevent instantiation
    }
}