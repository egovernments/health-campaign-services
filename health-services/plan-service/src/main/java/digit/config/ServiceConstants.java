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

    public static final String VEHICLE_ID_NOT_FOUND_IN_MDMS_CODE = "VEHICLE_ID_NOT_FOUND_IN_MDMS";
    public static final String VEHICLE_ID_NOT_FOUND_IN_MDMS_MESSAGE = "Vehicle Id is not present in MDMS";

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

    public static final String NAME_VALIDATION_LIST_EMPTY_CODE = "NAME_VALIDATION_LIST_EMPTY";
    public static final String NAME_VALIDATION_LIST_EMPTY_MESSAGE = "Name Validation list from MDMS is empty";

    public static final String NAME_VALIDATION_FAILED_CODE = "NAME_VALIDATION_FAILED";
    public static final String NAME_VALIDATION_FAILED_MESSAGE = "Name Validation failed";

    public static final String INVALID_PLAN_ID_CODE = "INVALID_PLAN_ID";
    public static final String INVALID_PLAN_ID_MESSAGE = "Plan id provided is invalid";

    public static final String CYCLIC_ACTIVITY_DEPENDENCY_CODE = "CYCLIC_ACTIVITY_DEPENDENCY";
    public static final String CYCLIC_ACTIVITY_DEPENDENCY_MESSAGE = "Cyclic activity dependency found";

    public static final String INVALID_ACTIVITY_DEPENDENCY_CODE = "INVALID_ACTIVITY_DEPENDENCY";
    public static final String INVALID_ACTIVITY_DEPENDENCY_MESSAGE = "Activity dependency is invalid";

    public static final String ACTIVITIES_CANNOT_BE_NULL_CODE = "ACTIVITIES_CANNOT_BE_NULL";
    public static final String ACTIVITIES_CANNOT_BE_NULL_MESSAGE = "Activities list in Plan cannot be null";

    public static final String DUPLICATE_ACTIVITY_CODES = "DUPLICATE_ACTIVITY_CODES";
    public static final String DUPLICATE_ACTIVITY_CODES_MESSAGE = "Activity codes within the plan should be unique";

    public static final String PLAN_ACTIVITIES_MANDATORY_CODE = "PLAN_ACTIVITIES_MANDATORY";
    public static final String PLAN_ACTIVITIES_MANDATORY_MESSAGE = "Activities are mandatory if execution plan id is not provided";

    public static final String PLAN_ACTIVITIES_NOT_ALLOWED_CODE = "PLAN_ACTIVITIES_NOT_ALLOWED";
    public static final String PLAN_ACTIVITIES_NOT_ALLOWED_MESSAGE = "Activities are not allowed if execution plan id is provided";

    public static final String INVALID_ACTIVITY_DATES_CODE = "INVALID_ACTIVITY_DATES";
    public static final String INVALID_ACTIVITY_DATES_MESSAGE = "Planned end date cannot be before planned start date";

    public static final String PLAN_RESOURCES_MANDATORY_CODE = "PLAN_RESOURCES_MANDATORY";
    public static final String PLAN_RESOURCES_MANDATORY_MESSAGE = "Resources are mandatory if plan configuration id is not provided";

    public static final String PLAN_RESOURCES_NOT_ALLOWED_CODE = "PLAN_RESOURCES_NOT_ALLOWED";
    public static final String PLAN_RESOURCES_NOT_ALLOWED_MESSAGE = "Resources are not allowed if plan configuration id is provided";

    public static final String INVALID_RESOURCE_ACTIVITY_LINKAGE_CODE = "INVALID_RESOURCE_ACTIVITY_LINKAGE";
    public static final String INVALID_RESOURCE_ACTIVITY_LINKAGE_MESSAGE = "Resource-Activity linkage is invalid";

    public static final String INVALID_TARGET_ACTIVITY_LINKAGE_CODE = "INVALID_TARGET_ACTIVITY_LINKAGE";
    public static final String INVALID_TARGET_ACTIVITY_LINKAGE_MESSAGE = "Target-Activity linkage is invalid";

    public static final String DUPLICATE_TARGET_UUIDS_CODE = "DUPLICATE_TARGET_UUIDS";
    public static final String DUPLICATE_TARGET_UUIDS_MESSAGE = "Target UUIDs should be unique";

    public static final String DUPLICATE_RESOURCE_UUIDS_CODE = "DUPLICATE_RESOURCE_UUIDS";
    public static final String DUPLICATE_RESOURCE_UUIDS_MESSAGE = "Resource UUIDs should be unique";

    public static final String DUPLICATE_ACTIVITY_UUIDS_CODE = "DUPLICATE_ACTIVITY_UUIDS";
    public static final String DUPLICATE_ACTIVITY_UUIDS_MESSAGE = "Activity UUIDs should be unique";


    //mdms constants
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_MASTER_ASSUMPTION = "HypothesisAssumptions";
    public static final String MDMS_MASTER_UPLOAD_CONFIGURATION = "UploadConfiguration";
    public static final String MDMS_MASTER_RULE_CONFIGURE_INPUTS = "RuleConfigureInputs";
    public static final String MDMS_MASTER_SCHEMAS = "Schemas";
    public static final String MDMS_MASTER_METRIC = "Metric";
    public static final String MDMS_MASTER_UOM = "Uom";
    public static final String MDMS_CODE = "mdms";
    public static final String MDMS_MASTER_NAME_VALIDATION= "MicroplanNamingRegex";

    public static final String JSON_ROOT_PATH = "$.";

    public static final String DOT_SEPARATOR = ".";

    public static final String DOT_REGEX = "\\.";

    public static final String FILTER_CODE = "$.*.code";

    public static final String FILTER_ID = "$.*.id";

    public static final String FILTER_TO_GET_ALL_IDS = "*.id";

    public static final String FILTER_DATA = "$.*.data";

    public static final String LOCALITY_CODE = "Locality";

    public static final String MDMS_SCHEMA_SECTION = "section";

    public static final String MDMS_SCHEMA_TYPE = "type";

    public static final String MDMS_SCHEMA_SCHEMA = "schema";

    public static final String MDMS_SCHEMA_PROPERTIES = "Properties";

    public static final String MDMS_SCHEMA_PROPERTIES_IS_RULE_CONFIGURE_INPUT = "isRuleConfigureInputs";

    public static final String MDMS_SCHEMA_PROPERTIES_IS_REQUIRED = "isRequired";

    public static final String MDMS_SCHEMA_VEHICLE_DETAILS = "VehicleDetails";

    public static final String BOUNDARY_CODE = "boundaryCode";

    public static final String FILTER_ALL_ASSUMPTIONS = "[*].assumptionCategories[*].assumptions[*]";

    public static final String NAME_VALIDATION_DATA = "Data";

    public static final String VEHICLE_ID_FIELD = "vehicleIds";

    public static final String MDMS_HCM_ADMIN_CONSOLE = "HCM-ADMIN-CONSOLE";
    public static final String MDMS_MASTER_HIERARCHY_CONFIG = "hierarchyConfig";
    public static final String MDMS_MASTER_HIERARCHY= "hierarchy";
    public static final String MDMS_MASTER_LOWEST_HIERARCHY= "lowestHierarchy";

    public static final String ERROR_WHILE_FETCHING_FROM_FACILITY = "Exception occurred while fetching facility details from facility service ";

    public static final String INVALID_PLAN_FACILITY_ID_CODE = "INVALID_PLAN_FACILITY_ID";
    public static final String INVALID_PLAN_FACILITY_ID_MESSAGE = "Plan facility id provided is invalid";

    public static final String ERROR_WHILE_FETCHING_FROM_PROJECT_FACTORY = "Exception occurred while fetching campaign details from ProjectFactory service ";

    public static final String NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE = "NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID";
    public static final String NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE = "Invalid or incorrect campaign id. No campaign details found for provided campaign id.";

    public static final String INVALID_SERVICE_BOUNDARY_CODE = "INVALID_SERVICE_BOUNDARY";
    public static final String INVALID_SERVICE_BOUNDARY_MESSAGE = "The provided service boundary is invalid";

    public static final String INVALID_RESIDING_BOUNDARY_CODE = "INVALID_RESIDING_BOUNDARY";
    public static final String INVALID_RESIDING_BOUNDARY_MESSAGE = "The provided residing boundary is invalid";

    public static final String HIERARCHY_NOT_FOUND_IN_MDMS_CODE = "HIERARCHY_NOT_FOUND_IN_MDMS";
    public static final String HIERARCHY_NOT_FOUND_IN_MDMS_MESSAGE = "Hierarchy key not found in mdms";

}
