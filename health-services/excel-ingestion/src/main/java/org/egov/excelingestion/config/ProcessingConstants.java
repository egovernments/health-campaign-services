package org.egov.excelingestion.config;

public class ProcessingConstants {
    
    // Processing Types
    public static final String MICROPLAN_INGESTION = "microplan-ingestion";
    
    // Sheet Name Keys for Localization
    public static final String FACILITIES_SHEET_KEY = "HCM_ADMIN_CONSOLE_FACILITIES_LIST";
    public static final String USER_LIST_SHEET_KEY = "HCM_ADMIN_CONSOLE_USER_LIST";
    public static final String BOUNDARY_DATA_SHEET_KEY = "HCM_ADMIN_CONSOLE_BOUNDARY_DATA";
    public static final String README_SHEET_KEY = "HCM_README_SHEETNAME";
    
    // Schema Names
    public static final String FACILITY_SCHEMA = "facility";
    public static final String USER_SCHEMA = "user";
    
    // MDMS Configuration
    public static final String MDMS_SCHEMA_CODE = "HCM-ADMIN-CONSOLE.schemas";
    
    private ProcessingConstants() {
        // Private constructor to prevent instantiation
    }
}