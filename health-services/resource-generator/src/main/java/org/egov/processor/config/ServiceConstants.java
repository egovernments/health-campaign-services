package org.egov.processor.config;


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

    public static final String DISTRIBUTION_PROCESS = "DistributionProcess";

    public static final String REGISTRATION_PROCESS = "RegistrationProcess";
    public static final String BOUNDARY_CODE = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";

    //File constants
    public static final String FILE_NAME = "output.xls";
    public static final String FILE_TYPE = "boundaryWithTarget";
    public static final String FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS_IN_PROGRESS = "EstimationsInprogress";
    public static final String FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS = "Estimations";
    public static final String FILE_TEMPLATE_IDENTIFIER_POPULATION = "Population";
    public static final String FILE_TEMPLATE_IDENTIFIER_DRAFT_INPROGRESS = "DraftInprogress";
    public static final String FILE_TEMPLATE_IDENTIFIER_DRAFT_COMPLETE = "DraftComplete";
    public static final String FILE_TEMPLATE_IDENTIFIER_BOUNDARY = "boundaryWithTarget";
    public static final String FILE_TEMPLATE_IDENTIFIER_FACILITY = "Facilities";
    public static final String INPUT_IS_NOT_VALID = "File does not contain valid input for row ";

    //Mdms constants and masters
    public static final String MDMS_SCHEMA_TITLE =  "title";
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_MASTER_SCHEMAS = "Schemas";
    public static final String MDMS_MASTER_ADMIN_SCHEMA = "adminSchema";
    public static final String MDMS_CAMPAIGN_TYPE = "campaignType";
    public static final String MDMS_SCHEMA_ADMIN_SCHEMA = "adminSchema";
    public static final String MDMS_MASTER_MIXED_STRATEGY = "MixedStrategyOperationLogic";
    public static final String MDMS_ADMIN_CONSOLE_MODULE_NAME = "HCM-ADMIN-CONSOLE";
    public static final String BOUNDARY = "boundary";
    public static final String DOT_SEPARATOR = ".";
    public static final String MICROPLAN_PREFIX = "MP-";
    public static final Double BRIGHTEN_FACTOR = 1.1;
    public static final String ACCESSIBILITY_DETAILS = "accessibilityDetails";
    public static final String SECURITY_DETAILS = "securityDetails";
    public static final String EMPTY_STRING = "";
    public static final String NOT_APPLICABLE = "N/A";
    public static final String FIXED_POST_YES = "yes";
    public static final String LOG_PLACEHOLDER = "{}";

    //MDMS field Constants
    public static final String DATA = "data";
    public static final String PROPERTIES = "properties";
    public static final String NUMBER_PROPERTIES = "numberProperties";
    public static final String STRING_PROPERTIES = "stringProperties";
    public static final String NAME = "name";

    //Error messages
    public static final String ERROR_WHILE_UPDATING_PLAN_CONFIG = "Exception occurred while updating plan configuration.";
    public static final String ERROR_WHILE_SEARCHING_PLAN = "Exception occurred while searching plans.";
    public static final String ERROR_WHILE_SEARCHING_PLAN_FACILITY = "Exception occurred while searching plan facility : ";
    public static final String ERROR_WHILE_CREATING_CELL_STYLE = "Failed to create cell style : ";

    public static final String VALIDATE_STRING_REGX = "^(?!\\d+$).+$";
    public static final String VALIDATE_NUMBER_REGX = "^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$";
    public static final String VALIDATE_BOOLEAN_REGX = "^(?i)(true|false)$";
    
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
    public static final String FACILITY_NAME = "facilityName";
    public static final String FACILITY_ID = "facilityId";
    public static final String HCM_MICROPLAN_SERVING_FACILITY = "HCM_MICROPLAN_SERVING_FACILITY";
    public static final String FIXED_POST = "fixedPost";
    public static final String ACTION_CREATE = "create";
    public static final String SOURCE_KEY = "source";
    public static final String MICROPLAN_SOURCE_KEY = "microplan";
    public static final String MICROPLAN_ID_KEY = "microplanId";

    //Census additional field constants
    public static final String UPLOADED_KEY = "UPLOADED_";
    public static final String CONFIRMED_KEY = "CONFIRMED_";
    public static final String CODE = "code";
    public static final String TOTAL_POPULATION = "HCM_ADMIN_CONSOLE_TOTAL_POPULATION";
    public static final String LATITUDE = "HCM_ADMIN_CONSOLE_TARGET_LAT_OPT";
    public static final String LONGITUDE = "HCM_ADMIN_CONSOLE_TARGET_LONG_OPT";
    public static final String LONGITUDE_KEY = "longitude";
    public static final String LATITUDE_KEY = "latitude";

    //Excel header row styling constants
    public static final String HEX_BACKGROUND_COLOR = "93C47D"; // Background color in HEX format (RRGGBB) for Excel header rows
    public static final boolean FREEZE_CELL = true; // Controls whether cells should be locked for editing
    public static final int COLUMN_WIDTH = 40; //Default column width in characters (1-255)
    public static final int COLUMN_PADDING = 512;

    public static final String DRAFT_STATUS = "DRAFT";
 }
