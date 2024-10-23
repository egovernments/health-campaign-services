package org.egov.processor.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String TENANTID_REPLACER = "{tenantId}";

    public static final String TENANTID = "tenantId";

    public static final String FILESTORE_ID_REPLACER = "{fileStoreId}";

    public static final String FILES = "files";

    public static final String FILESTORE_ID = "fileStoreId";

    public static final String MODULE = "module";

    public static final String MICROPLANNING_MODULE = "microplan";

    public static final String PROPERTIES = "properties";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE = "Exception occurred while fetching plan configuration from plan service ";

    public static final String NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_CODE = "NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM";
    public static final String NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_MESSAGE = "Not able to fetch byte stream from a multipart file";
    
    public static final String BOUNDARY_CODE = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    public static final String TOTAL_POPULATION = "HCM_ADMIN_CONSOLE_TOTAL_POPULATION";

    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY = "Exception occurred while fetching plan configuration from plan service for Locality ";
    public static final String ERROR_WHILE_PUSHING_TO_PLAN_SERVICE_FOR_LOCALITY = "Exception occurred while fetching plan configuration from plan service for Locality ";
    public static final String ERROR_WHILE_SEARCHING_CAMPAIGN = "Exception occurred while searching/updating campaign.";

    public static final String FILE_NAME = "output.xls";
    public static final String FILE_TYPE = "boundaryWithTarget";
    public static final String FILE_TEMPLATE_IDENTIFIER = "Population";
    public static final String INPUT_IS_NOT_VALID = "File does not contain valid input for row ";
    
    public static final String MDMS_SCHEMA_TYPE =  "type";
    public static final String MDMS_SCHEMA_SECTION =  "section";
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_MASTER_SCHEMAS = "Schemas";
    public static final String MDMS_CAMPAIGN_TYPE = "campaignType";
    
    public static final String ERROR_WHILE_UPDATING_PLAN_CONFIG = "Exception occurred while updating plan configuration.";
    
    public static final String VALIDATE_STRING_REGX = "^(?!\\d+$).+$";
    public static final String VALIDATE_NUMBER_REGX = "^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$";
    public static final String VALIDATE_BOOLEAN_REGX = "^(?i)(true|false)$";
    
    public static final String FILE_TEMPLATE = "Facilities";
    public static final String HIERARCHYTYPE_REPLACER = "{hierarchyType}";
    public static final String FILE_EXTENSION = "excel";
    
    public static final String SCIENTIFIC_NOTATION_INDICATOR = "E";
    public static final String ATTRIBUTE_IS_REQUIRED ="isRequired";
    public static final int DEFAULT_SCALE=2;
    
    public static final String MDMS_LOCALE_SEARCH_MODULE ="rainmaker-microplanning,rainmaker-boundary-undefined,rainmaker-hcm-admin-schemas";
    public static final String ERROR_WHILE_SEARCHING_LOCALE = "Exception occurred while searching locale. ";
    public static final String MDMS_MASTER_COMMON_CONSTANTS = "CommonConstants";

    //override sheet names
    public static final String HCM_ADMIN_CONSOLE_BOUNDARY_DATA = "HCM_ADMIN_CONSOLE_BOUNDARY_DATA";
    public static final String READ_ME_SHEET_NAME = "readMeSheetName";

    //Workflow constants
    public static final String WORKFLOW_ACTION_INITIATE = "INITIATE";
    public static final String WORKFLOW_COMMENTS_INITIATING_CENSUS = "Initiating census record creation";
    public static final String WORKFLOW_COMMENTS_INITIATING_ESTIMATES = "Initiating plan estimation record creation";

}
