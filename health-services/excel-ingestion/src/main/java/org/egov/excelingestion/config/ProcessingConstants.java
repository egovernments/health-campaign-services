package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    // Processing Types
    public static final String MICROPLAN_INGESTION = "microplan-ingestion";
    
    // Sheet Name Keys for Localization
    public static final String FACILITIES_SHEET_KEY = "HCM_ADMIN_CONSOLE_FACILITIES_LIST";
    public static final String USER_LIST_SHEET_KEY = "HCM_ADMIN_CONSOLE_USERS_LIST";
    public static final String BOUNDARY_DATA_SHEET_KEY = "HCM_ADMIN_CONSOLE_BOUNDARY_DATA";
    public static final String README_SHEET_KEY = "HCM_README_SHEETNAME";
    
    // Schema Names (prefixed with processing type)
    public static final String FACILITY_SCHEMA = "facility-microplan-ingestion";
    public static final String USER_SCHEMA = "user-microplan-ingestion";
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";
    
    // Status Constants
    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_PROCESSED = "processed";
    
    private ProcessingConstants() {
        // Private constructor to prevent instantiation
    }
}