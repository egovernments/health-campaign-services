package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";

    // Column Keys
    public static final String BOUNDARY_CODE_COLUMN_KEY = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    public static final String REGISTER_ID_COLUMN_KEY = "HCM_ATTENDANCE_REGISTER_ID";
    
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