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

    public static final String USERINFO_MISSING_CODE = "USERINFO_MISSING";
    public static final String USERINFO_MISSING_MESSAGE = "UserInfo is missing in Request Info ";

    public static final String ASSUMPTION_VALUE_NOT_FOUND_CODE = "ASSUMPTION_VALUE_NOT_FOUND";
    public static final String ASSUMPTION_VALUE_NOT_FOUND_MESSAGE = "Operation's Assumption value not found in active assumptions list ";

    public static final String FILESTORE_ID_INVALID_CODE = "FILESTORE_ID_INVALID";
    public static final String FILESTORE_ID_INVALID_MESSAGE = " Resource mapping does not have a Valid File Store Id ";

    public static final String ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE = "ASSUMPTION_KEY_NOT_FOUND_IN_MDMS";
    public static final String ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE = "Assumption Key is not present in MDMS ";

    public static final String TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE = "TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS";
    public static final String TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE = "Template Identifier is not present in MDMS ";

    public static final String REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_CODE = "REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND";
    public static final String REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_MESSAGE = "Required Template Identifier is not present in Files ";

    public static final String ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_CODE = "ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER";
    public static final String ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE = "Only one file of the required template identifier should be present ";

    public static final String INPUT_KEY_NOT_FOUND_CODE = "INPUT_KEY_NOT_FOUND";
    public static final String INPUT_KEY_NOT_FOUND_MESSAGE = "Operation's Input key not present in MDMS ";

    public static final String LOCALITY_NOT_PRESENT_IN_MAPPED_TO_CODE = "LOCALITY_NOT_PRESENT_IN_MAPPED_TO";
    public static final String LOCALITY_NOT_PRESENT_IN_MAPPED_TO_MESSAGE = "Resource Mapping's MappedTo must contain 'Locality' ";

    public static final String DUPLICATE_MAPPED_TO_VALIDATION_ERROR_CODE = "DUPLICATE_MAPPED_TO_VALIDATION_ERROR";
    public static final String DUPLICATE_MAPPED_TO_VALIDATION_ERROR_MESSAGE = "Duplicate MappedTo found in Resource Mapping";

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

    public static final String METRIC_NOT_FOUND_IN_MDMS_CODE = "METRIC_NOT_FOUND_IN_MDMS";
    public static final String METRIC_NOT_FOUND_IN_MDMS_MESSAGE = "Metric key not found in MDMS";

    public static final String METRIC_UNIT_NOT_FOUND_IN_MDMS_CODE = "METRIC_UNIT_NOT_FOUND_IN_MDMS";
    public static final String METRIC_UNIT_NOT_FOUND_IN_MDMS_MESSAGE = "Metric Details' Unit not found in MDMS";

    public static final String INACTIVE_OPERATION_USED_AS_INPUT_CODE = "INACTIVE_OPERATION_USED_AS_INPUT";
    public static final String INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE = "Inactive operation output used. ";

    public static final String JSONPATH_ERROR_CODE = "JSONPATH_ERROR";
    public static final String JSONPATH_ERROR_MESSAGE = "Failed to parse mdms response with given Jsonpath" ;

    public static final String BOUNDARY_CODE_MAPPING_NOT_FOUND_CODE = "BOUNDARY_CODE_MAPPING_NOT_FOUND";
    public static final String BOUNDARY_CODE_MAPPING_NOT_FOUND_MESSAGE = "Boundary Code Mapping is required column is not found.";

    public static final String INVALID_PLAN_ID_CODE = "INVALID_PLAN_ID";
    public static final String INVALID_PLAN_ID_MESSAGE = "Plan id provided is invalid";
    //mdms constants
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_MASTER_ASSUMPTION = "HypothesisAssumptions";
    public static final String MDMS_MASTER_UPLOAD_CONFIGURATION = "UploadConfiguration";
    public static final String MDMS_MASTER_RULE_CONFIGURE_INPUTS = "RuleConfigureInputs";
    public static final String MDMS_MASTER_SCHEMAS = "Schemas";
    public static final String MDMS_MASTER_METRIC = "Metric";
    public static final String MDMS_MASTER_UOM = "Uom";

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

    public static final String MDMS_SCHEMA_PROPERTIES_IS_REQUIRED = "isRequired";
    public static final String BOUNDARY_CODE = "boundaryCode";

}
