package org.egov.excelingestion.config;

/**
 * Error constants for excel-ingestion service following health services pattern
 */
public class ErrorConstants {

    // Private constructor to prevent instantiation
    private ErrorConstants() {}

    // MDMS Service Errors
    public static final String MDMS_SERVICE_ERROR = "MDMS_SERVICE_ERROR";
    public static final String MDMS_SERVICE_ERROR_MESSAGE = "Error while fetching data from MDMS service";
    
    public static final String MDMS_DATA_NOT_FOUND = "MDMS_DATA_NOT_FOUND";
    public static final String MDMS_DATA_NOT_FOUND_MESSAGE = "Required MDMS data not found for schema: {0}";
    
    // Boundary Service Errors  
    public static final String BOUNDARY_SERVICE_ERROR = "BOUNDARY_SERVICE_ERROR";
    public static final String BOUNDARY_SERVICE_ERROR_MESSAGE = "Error while fetching boundary data";
    
    public static final String BOUNDARY_HIERARCHY_NOT_FOUND = "BOUNDARY_HIERARCHY_NOT_FOUND";
    public static final String BOUNDARY_HIERARCHY_NOT_FOUND_MESSAGE = "Boundary hierarchy not found for type: {0}";
    
    // Localization Service Errors
    public static final String LOCALIZATION_SERVICE_ERROR = "LOCALIZATION_SERVICE_ERROR";
    public static final String LOCALIZATION_SERVICE_ERROR_MESSAGE = "Error while fetching localization data";
    
    // File Store Service Errors
    public static final String FILE_STORE_SERVICE_ERROR = "FILE_STORE_SERVICE_ERROR";
    public static final String FILE_STORE_SERVICE_ERROR_MESSAGE = "Error while uploading file to file store";
    
    // Excel Generation Errors
    public static final String EXCEL_GENERATION_ERROR = "EXCEL_GENERATION_ERROR";
    public static final String EXCEL_GENERATION_ERROR_MESSAGE = "Error while generating Excel file";
    
    public static final String INVALID_SCHEMA_FORMAT = "INVALID_SCHEMA_FORMAT";
    public static final String INVALID_SCHEMA_FORMAT_MESSAGE = "Invalid schema format received from MDMS";
    
    public static final String SHEET_CREATION_ERROR = "SHEET_CREATION_ERROR";
    public static final String SHEET_CREATION_ERROR_MESSAGE = "Error while creating sheet: {0}";
    
    public static final String CAMPAIGN_CONFIG_CREATION_ERROR = "CAMPAIGN_CONFIG_CREATION_ERROR";
    public static final String CAMPAIGN_CONFIG_CREATION_ERROR_MESSAGE = "Error while creating campaign configuration sheet";
    
    // Configuration Errors
    public static final String INVALID_CONFIGURATION = "INVALID_CONFIGURATION";
    public static final String INVALID_CONFIGURATION_MESSAGE = "Invalid configuration provided: {0}";
    
    public static final String MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD";
    public static final String MISSING_REQUIRED_FIELD_MESSAGE = "Required field missing: {0}";
    
    // Validation Errors
    public static final String INVALID_TENANT_ID = "INVALID_TENANT_ID";
    public static final String INVALID_TENANT_ID_MESSAGE = "Invalid tenant ID provided: {0}";
    
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_REQUEST_MESSAGE = "Invalid request: {0}";
    
    public static final String PROCESSOR_NOT_FOUND = "PROCESSOR_NOT_FOUND";
    public static final String PROCESSOR_NOT_FOUND_MESSAGE = "No processor found for type: {0}";
    
    // Campaign Config Errors
    public static final String MISMATCH_ROWS_FOR_LEVELS = "MISMATCH_ROWS_FOR_LEVELS";
    public static final String MISMATCH_ROWS_FOR_LEVELS_MESSAGE = "Number of rows ({0}) is less than boundary hierarchy levels ({1})";
    
    public static final String BOUNDARY_LEVELS_NOT_FOUND = "BOUNDARY_LEVELS_NOT_FOUND";
    public static final String BOUNDARY_LEVELS_NOT_FOUND_MESSAGE = "Boundary levels not found in hierarchy definition";
    
    // Generic Errors
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String INTERNAL_SERVER_ERROR_MESSAGE = "An internal server error occurred";
    
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String NETWORK_ERROR_MESSAGE = "Network error while calling external service: {0}";
}