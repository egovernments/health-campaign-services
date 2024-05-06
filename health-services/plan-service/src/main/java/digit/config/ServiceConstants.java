package digit.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String RES_MSG_ID = "uief87324";
    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";

    public static final String URL = "url";
    public static final String URL_SHORTENING_ERROR_CODE = "URL_SHORTENING_ERROR";
    public static final String URL_SHORTENING_ERROR_MESSAGE = "Unable to shorten url: ";

    public static final String DOB_FORMAT_Y_M_D = "yyyy-MM-dd";
    public static final String DOB_FORMAT_D_M_Y = "dd/MM/yyyy";
    public static final String ILLEGAL_ARGUMENT_EXCEPTION_CODE = "IllegalArgumentException";
    public static final String OBJECTMAPPER_UNABLE_TO_CONVERT = "ObjectMapper not able to convertValue in userCall";
    public static final String DOB_FORMAT_D_M_Y_H_M_S = "dd-MM-yyyy HH:mm:ss";
    public static final String CREATED_DATE = "createdDate";
    public static final String LAST_MODIFIED_DATE = "lastModifiedDate";
    public static final String DOB = "dob";
    public static final String PWD_EXPIRY_DATE = "pwdExpiryDate";
    public static final String INVALID_DATE_FORMAT_CODE = "INVALID_DATE_FORMAT";
    public static final String INVALID_DATE_FORMAT_MESSAGE = "Failed to parse date format in user";
    public static final String CITIZEN_UPPER = "CITIZEN";
    public static final String CITIZEN_LOWER = "Citizen";
    public static final String USER = "user";

    public static final String PARSING_ERROR = "PARSING ERROR";
    public static final String FAILED_TO_PARSE_BUSINESS_SERVICE_SEARCH = "Failed to parse response of workflow business service search";
    public static final String BUSINESS_SERVICE_NOT_FOUND = "BUSINESSSERVICE_NOT_FOUND";
    public static final String THE_BUSINESS_SERVICE = "The businessService ";
    public static final String NOT_FOUND = " is not found";
    public static final String TENANTID = "?tenantId=";
    public static final String BUSINESS_SERVICES = "&businessServices=";

    public static final String USERINFO_MISSING_CODE = "USERINFO_MISSING";
    public static final String USERINFO_MISSING_MESSAGE = "UserInfo is missing in Request Info ";

    public static final String ASSUMPTION_VALUE_NOT_FOUND_CODE = "ASSUMPTION_VALUE_NOT_FOUND";
    public static final String ASSUMPTION_VALUE_NOT_FOUND_MESSAGE = "Operation's Assumption value not found in assumptions list ";

    public static final String FILESTORE_ID_INVALID_CODE = "FILESTORE_ID_INVALID";
    public static final String FILESTORE_ID_INVALID_MESSAGE = "Resource mapping does not have a Valid File Store Id ";

    public static final String ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE = "ASSUMPTION_KEY_NOT_FOUND_IN_MDMS";
    public static final String ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE = "Assumption Key is not present in MDMS";

    public static final String TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE = "TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS";
    public static final String TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE = "Template Identifier is not present in MDMS";

    public static final String INPUT_KEY_NOT_FOUND_CODE = "INPUT_KEY_NOT_FOUND";
    public static final String INPUT_KEY_NOT_FOUND_MESSAGE = "Operation's Input key not present in MDMS";

    public static final String LOCALITY_NOT_PRESENT_IN_MAPPED_TO_CODE = "LOCALITY_NOT_PRESENT_IN_MAPPED_TO";
    public static final String LOCALITY_NOT_PRESENT_IN_MAPPED_TO_MESSAGE = "Resource Mapping's MappedTo must contain 'Locality'";

    public static final String MAPPED_TO_VALIDATION_ERROR_CODE = "MAPPED_TO_VALIDATION_ERROR";

    public static final String TENANT_NOT_FOUND_IN_MDMS_CODE = "TENANT_ID_NOT_FOUND_IN_MDMS";
    public static final String TENANT_NOT_FOUND_IN_MDMS_MESSAGE = "Tenant Id is not present in MDMS";

    public static final String TENANT_ID_EMPTY_CODE = "TENANT_ID_EMPTY";
    public static final String TENANT_ID_EMPTY_MESSAGE = "Tenant Id cannot be empty, TenantId should be present";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String SEARCH_CRITERIA_EMPTY_CODE = "SEARCH_CRITERIA_EMPTY";
    public static final String SEARCH_CRITERIA_EMPTY_MESSAGE = "Search criteria cannot be empty";

    public static final String INVALID_PLAN_CONFIG_ID_CODE = "INVALID_PLAN_CONFIG_ID";
    public static final String INVALID_PLAN_CONFIG_ID_MESSAGE = "Plan config id provided is invalid";

    public static final String REQUEST_UUID_EMPTY_CODE = "REQUEST_UUID_EMPTY";
    public static final String REQUEST_UUID_EMPTY_MESSAGE = "Request UUID is empty";
    public static final String USER_UUID_MISMATCH_CODE = "USER_UUID_MISMATCH";
    public static final String USER_UUID_MISMATCH_MESSAGE = "Not Authorized to search with provided useruuid";
    public static final String JSONPATH_ERROR_CODE = "JSONPATH_ERROR";
    public static final String JSONPATH_ERROR_MESSAGE = "Failed to parse mdms response with given Jsonpath" ;

    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_MASTER_ASSUMPTION = "HypothesisAssumptions";
    public static final String MDMS_MASTER_UPLOAD_CONFIGURATION = "UploadConfiguration";
    public static final String MDMS_MASTER_RULE_CONFIGURE_INPUTS = "RuleConfigureInputs";
    public static final String MDMS_MASTER_SCHEMS = "Schemas";

    public static final String MDSM_MASTER_TENANTS = "tenants";
    public static final String MDMS_TENANT_MODULE_NAME = "tenant";

    public static final String DOT_SEPARATOR = ".";

    public static final String DOT_REGEX = "\\.";

    public static final String FILTER_CODE = "$.*.code";

    public static final String FILTER_ID = "$.*.id";

    public static final String FILTER_DATA = "$.*.data";

    public static final String LOCALITY_CODE = "Locality";

    public static final String MDMS_SCHEMA_SECTION = "section";

    public static final String MDMS_SCHEMA_TYPE = "type";

    public static final String MDMS_SCHEMA_SCHEMA = "schema";

    public static final String MDMS_SCHEMA_PROPERTIES = "Properties";

    public static final String MDMS_SCHEMA_PROPERTIES_IS_RULE_CONFIGURE_INPUT = "isRuleConfigureInputs";

}
