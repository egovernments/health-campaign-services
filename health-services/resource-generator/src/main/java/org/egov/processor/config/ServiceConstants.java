package org.egov.processor.config;


import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String ERROR_WHILE_FETCHING_FROM_CENSUS = "Exception occurred while fetching records from census: ";

    public static final String TENANTID_REPLACER = "{tenantId}";

    public static final String TENANTID = "tenantId";

    public static final String FILESTORE_ID_REPLACER = "{fileStoreId}";

    public static final String FILES = "files";

    public static final String FILESTORE_ID = "fileStoreId";

    public static final String MODULE = "module";

    public static final String MICROPLANNING_MODULE = "microplan";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE = "Exception occurred while fetching plan configuration from plan service ";

    public static final String NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_CODE = "NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM";
    public static final String NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_MESSAGE = "Not able to fetch byte stream from a multipart file";

    public static final String FILE_NOT_FOUND_CODE = "FILE_NOT_FOUND";
    public static final String FILE_NOT_FOUND_MESSAGE = "No file with the specified templateIdentifier found - ";

    public static final String UNABLE_TO_CREATE_ADDITIONAL_DETAILS_CODE = "UNABLE_TO_CREATE_ADDITIONAL_DETAILS";
    public static final String UNABLE_TO_CREATE_ADDITIONAL_DETAILS_MESSAGE = "Unable to create additional details for facility creation.";

    public static final String NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_CODE = "NO_PLAN_FOUND_FOR_GIVEN_DETAILS";
    public static final String NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_MESSAGE = "Census records do not exists for the given details: ";

    public static final String NO_PLAN_FOUND_FOR_GIVEN_DETAILS_CODE = "NO_PLAN_FOUND_FOR_GIVEN_DETAILS";
    public static final String NO_PLAN_FOUND_FOR_GIVEN_DETAILS_MESSAGE = "Plan records do not exists for the given details: ";

    public static final String BOUNDARY_CODE = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    public static final String TOTAL_POPULATION = "HCM_ADMIN_CONSOLE_TOTAL_POPULATION";

    public static final String ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE_FOR_LOCALITY = "Exception occurred while fetching plan configuration from plan service for Locality ";
    public static final String ERROR_WHILE_PUSHING_TO_PLAN_SERVICE_FOR_LOCALITY = "Exception occurred while fetching plan configuration from plan service for Locality ";
    public static final String ERROR_WHILE_SEARCHING_CAMPAIGN = "Exception occurred while searching/updating campaign.";
    public static final String ERROR_WHILE_DATA_CREATE_CALL = "Exception occurred while creating data for campaign - ";
    public static final String ERROR_WHILE_CALLING_MICROPLAN_API =
            "Unexpected error while calling fetch from Microplan API for plan config Id: ";

    public static final String FILE_NAME = "output.xls";
    public static final String FILE_TYPE = "boundaryWithTarget";
    public static final String FILE_TEMPLATE_IDENTIFIER_POPULATION = "Population";
    public static final String FILE_TEMPLATE_IDENTIFIER_FACILITY = "Facilities";
    public static final String INPUT_IS_NOT_VALID = "File does not contain valid input for row ";
    
    public static final String MDMS_SCHEMA_TYPE =  "type";
    public static final String MDMS_SCHEMA_SECTION =  "section";
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_MASTER_SCHEMAS = "Schemas";
    public static final String MDMS_CAMPAIGN_TYPE = "campaignType";
    public static final String MDMS_SCHEMA_ADMIN_SCHEMA = "adminSchema";
    public static final String MDMS_ADMIN_CONSOLE_MODULE_NAME = "HCM-ADMIN-CONSOLE";
    public static final String BOUNDARY = "boundary";
    public static final String DOT_SEPARATOR = ".";
    public static final String MICROPLAN_PREFIX = "MP-";

    //MDMS field Constants
    public static final String DATA = "data";
    public static final String PROPERTIES = "properties";
    public static final String NUMBER_PROPERTIES = "numberProperties";
    public static final String STRING_PROPERTIES = "stringProperties";
    public static final String NAME = "name";

    public static final String ERROR_WHILE_UPDATING_PLAN_CONFIG = "Exception occurred while updating plan configuration.";
    public static final String ERROR_WHILE_SEARCHING_PLAN = "Exception occurred while search plans.";

    public static final String VALIDATE_STRING_REGX = "^(?!\\d+$).+$";
    public static final String VALIDATE_NUMBER_REGX = "^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$";
    public static final String VALIDATE_BOOLEAN_REGX = "^(?i)(true|false)$";
    
    public static final String FILE_TEMPLATE = "Facilities";
    public static final String HIERARCHYTYPE_REPLACER = "{hierarchyType}";
    public static final String FILE_EXTENSION = "excel";
    
    public static final String SCIENTIFIC_NOTATION_INDICATOR = "E";
    public static final String ATTRIBUTE_IS_REQUIRED ="isRequired";
    public static final int DEFAULT_SCALE=2;
    
    public static final String MDMS_LOCALE_SEARCH_MODULE ="rainmaker-microplanning,rainmaker-boundary-undefined,hcm-admin-schemas";
    public static final String ERROR_WHILE_SEARCHING_LOCALE = "Exception occurred while searching locale. ";
    public static final String MDMS_MASTER_COMMON_CONSTANTS = "CommonConstants";

    //override sheet names
    public static final String HCM_ADMIN_CONSOLE_BOUNDARY_DATA = "HCM_ADMIN_CONSOLE_BOUNDARY_DATA";
    public static final String READ_ME_SHEET_NAME = "readMeSheetName";

    //Workflow constants
    public static final String WORKFLOW_ACTION_INITIATE = "INITIATE";
    public static final String WORKFLOW_COMMENTS_INITIATING_CENSUS = "Initiating census record creation";
    public static final String WORKFLOW_COMMENTS_INITIATING_ESTIMATES = "Initiating plan estimation record creation";

    //Facility Create constants
    public static final String TYPE_FACILITY = "facility";
    public static final String ACTION_CREATE = "create";
    public static final String SOURCE_KEY = "source";
    public static final String MICROPLAN_SOURCE_KEY = "microplan";
    public static final String MICROPLAN_ID_KEY = "microplanId";

    //Census additional field constants
    public static final String UPLOADED_KEY = "UPLOADED_";
    public static final String CONFIRMED_KEY = "CONFIRMED_";

    //Excel header row styling constants
    public static final String HEX_BACKGROUND_COLOR = "93C47D"; // Constant background color
    public static final boolean FREEZE_CELL = true; // Constant to lock cell
    public static final int COLUMN_WIDTH = 40; // Column width in characters

}
