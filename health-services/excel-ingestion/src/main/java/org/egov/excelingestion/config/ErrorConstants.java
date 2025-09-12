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
    
    public static final String FILE_DOWNLOAD_ERROR = "FILE_DOWNLOAD_ERROR";
    public static final String FILE_DOWNLOAD_ERROR_MESSAGE = "Error while downloading file from file store";
    
    public static final String FILE_URL_RETRIEVAL_ERROR = "FILE_URL_RETRIEVAL_ERROR";
    public static final String FILE_URL_RETRIEVAL_ERROR_MESSAGE = "Could not retrieve file URL from file store";
    
    public static final String FILE_NOT_FOUND_ERROR = "FILE_NOT_FOUND_ERROR";
    public static final String FILE_NOT_FOUND_ERROR_MESSAGE = "File not found in file store";
    
    // Excel Generation Errors
    public static final String EXCEL_GENERATION_ERROR = "EXCEL_GENERATION_ERROR";
    public static final String EXCEL_GENERATION_ERROR_MESSAGE = "Error while generating Excel file";
    
    // Excel Processing Errors
    public static final String EXCEL_PROCESSING_ERROR = "EXCEL_PROCESSING_ERROR";
    public static final String EXCEL_PROCESSING_ERROR_MESSAGE = "Error processing Excel file";
    public static final String EXCEL_PROCESSING_ERROR_DESCRIPTION = "An error occurred while processing the Excel file: {0}";
    
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
    
    // Schema Configuration Errors
    public static final String PROCESSING_TYPE_NOT_SUPPORTED = "PROCESSING_TYPE_NOT_SUPPORTED";
    public static final String PROCESSING_TYPE_NOT_SUPPORTED_MESSAGE = "Processing type '{0}' is not supported. Supported types: {1}";
    
    public static final String GENERATION_TYPE_NOT_SUPPORTED = "GENERATION_TYPE_NOT_SUPPORTED";
    public static final String GENERATION_TYPE_NOT_SUPPORTED_MESSAGE = "Generation type '{0}' is not supported. Supported types: {1}";
    
    public static final String SHEET_NOT_CONFIGURED = "SHEET_NOT_CONFIGURED";
    public static final String SHEET_NOT_CONFIGURED_MESSAGE = "Unknown sheet name '{0}' for processing type '{1}'. Expected sheet names: {2}";
    
    public static final String SCHEMA_NOT_FOUND_IN_MDMS = "SCHEMA_NOT_FOUND_IN_MDMS";
    public static final String SCHEMA_NOT_FOUND_IN_MDMS_MESSAGE = "Schema '{0}' not found in MDMS for tenant '{1}'. Please verify the schema exists in MDMS";
    
    public static final String REQUIRED_SHEET_MISSING = "REQUIRED_SHEET_MISSING";
    public static final String REQUIRED_SHEET_MISSING_MESSAGE = "Required sheet '{0}' is missing from the Excel file. Expected sheets: {1}";
    
    // Validation Errors
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String VALIDATION_ERROR_MESSAGE = "Validation error: {0}";
    
    public static final String INVALID_TENANT_ID = "INVALID_TENANT_ID";
    public static final String INVALID_TENANT_ID_MESSAGE = "Invalid tenant ID provided: {0}";
    
    public static final String INVALID_HIERARCHY_TYPE = "INVALID_HIERARCHY_TYPE";
    public static final String INVALID_HIERARCHY_TYPE_MESSAGE = "Invalid hierarchy type provided: {0}";
    
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_REQUEST_MESSAGE = "Invalid request: {0}";
    
    public static final String PROCESSOR_NOT_FOUND = "PROCESSOR_NOT_FOUND";
    public static final String PROCESSOR_NOT_FOUND_MESSAGE = "No processor found for type: {0}";
    
    // Campaign Config Errors
    public static final String MISMATCH_ROWS_FOR_LEVELS = "MISMATCH_ROWS_FOR_LEVELS";
    public static final String MISMATCH_ROWS_FOR_LEVELS_MESSAGE = "Number of rows ({0}) is less than boundary hierarchy levels ({1})";
    
    public static final String BOUNDARY_LEVELS_NOT_FOUND = "BOUNDARY_LEVELS_NOT_FOUND";
    public static final String BOUNDARY_LEVELS_NOT_FOUND_MESSAGE = "Boundary levels not found in hierarchy definition";
    
    // Excel Data Populator Errors
    public static final String SCHEMA_CONVERSION_ERROR = "SCHEMA_CONVERSION_ERROR";
    public static final String SCHEMA_CONVERSION_ERROR_MESSAGE = "Error while converting schema to column definitions";
    
    public static final String SHEET_COPY_ERROR = "SHEET_COPY_ERROR";
    public static final String SHEET_COPY_ERROR_MESSAGE = "Error while copying sheet to workbook";
    
    // Generic Errors
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String INTERNAL_SERVER_ERROR_MESSAGE = "An internal server error occurred";
    
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String NETWORK_ERROR_MESSAGE = "Network error while calling external service: {0}";
    
    public static final String EXTERNAL_SERVICE_ERROR = "EXTERNAL_SERVICE_ERROR";
    public static final String EXTERNAL_SERVICE_ERROR_MESSAGE = "External service error";
    
    // Processor Errors
    public static final String PROCESSOR_CLASS_NOT_FOUND = "PROCESSOR_CLASS_NOT_FOUND";
    public static final String PROCESSOR_CLASS_NOT_FOUND_MESSAGE = "Processor class not found: {0}";
    
    public static final String PROCESSOR_EXECUTION_ERROR = "PROCESSOR_EXECUTION_ERROR";
    public static final String PROCESSOR_EXECUTION_ERROR_MESSAGE = "Error executing processor: {0}";
    
    // Generator Class Errors
    public static final String GENERATOR_CLASS_NOT_FOUND = "GENERATOR_CLASS_NOT_FOUND";
    public static final String GENERATOR_CLASS_NOT_FOUND_MESSAGE = "Generator class not found: {0}";
    
    public static final String GENERATOR_EXECUTION_ERROR = "GENERATOR_EXECUTION_ERROR";
    public static final String GENERATOR_EXECUTION_ERROR_MESSAGE = "Error executing generator: {0}";
    
    // Sheet Data Temp Operations Errors
    public static final String SHEET_DATA_SEARCH_ERROR = "SHEET_DATA_SEARCH_ERROR";
    public static final String SHEET_DATA_SEARCH_ERROR_MESSAGE = "Error searching sheet data: {0}";
    
    public static final String SHEET_DATA_DELETE_ERROR = "SHEET_DATA_DELETE_ERROR";
    public static final String SHEET_DATA_DELETE_ERROR_MESSAGE = "Error deleting sheet data: {0}";
    
    public static final String SHEET_DATA_INVALID_TENANT = "SHEET_DATA_INVALID_TENANT";
    public static final String SHEET_DATA_INVALID_TENANT_MESSAGE = "Tenant ID is required and cannot be empty";
    
    public static final String SHEET_DATA_NO_CRITERIA = "SHEET_DATA_NO_CRITERIA";
    public static final String SHEET_DATA_NO_CRITERIA_MESSAGE = "At least one search criteria (referenceId, fileStoreId, or sheetName) must be provided";
    
    public static final String SHEET_DATA_INVALID_LIMIT = "SHEET_DATA_INVALID_LIMIT";
    public static final String SHEET_DATA_INVALID_LIMIT_MESSAGE = "Limit must be between 1 and 1000";
    
    public static final String SHEET_DATA_INVALID_OFFSET = "SHEET_DATA_INVALID_OFFSET";
    public static final String SHEET_DATA_INVALID_OFFSET_MESSAGE = "Offset cannot be negative";
    
    public static final String SHEET_DATA_DELETE_MISSING_PARAMS = "SHEET_DATA_DELETE_MISSING_PARAMS";
    public static final String SHEET_DATA_DELETE_MISSING_PARAMS_MESSAGE = "Both referenceId and fileStoreId are required for deletion";
    
    public static final String SHEET_DATA_NOT_FOUND = "SHEET_DATA_NOT_FOUND";
    public static final String SHEET_DATA_NOT_FOUND_MESSAGE = "No sheet data found for the given criteria";
}