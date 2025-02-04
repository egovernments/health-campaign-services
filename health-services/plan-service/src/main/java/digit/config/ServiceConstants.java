package digit.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String ERROR_WHILE_FETCHING_FROM_USER_SERVICE = "Exception occurred while fetching user details from user service: ";

    public static final String ERROR_WHILE_FETCHING_FROM_PROJECT_FACTORY = "Exception occurred while fetching campaign details from project factory: ";

    public static final String ERROR_WHILE_FETCHING_FROM_CENSUS = "Exception occurred while fetching records from census: ";

    public static final String ERROR_WHILE_FETCHING_BOUNDARY_DETAILS = "Exception occurred while fetching boundary relationship from boundary service: ";

    public static final String ERROR_WHILE_FETCHING_BOUNDARY_HIERARCHY_DETAILS = "Exception occurred while fetching boundary hierarchy details from boundary service: ";

    public static final String ERROR_WHILE_FETCHING_DATA_FROM_HRMS = "Exception occurred while fetching employee from hrms: ";

    public static final String RES_MSG_ID = "uief87324";
    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";
    public static final String ID = "id";

    public static final String ROOT_PREFIX = "ROOT";

    public static final String USERINFO_MISSING_CODE = "USERINFO_MISSING";
    public static final String USERINFO_MISSING_MESSAGE = "UserInfo is missing in Request Info ";

    public static final String ASSUMPTION_VALUE_NOT_FOUND_CODE = "ASSUMPTION_VALUE_NOT_FOUND";
    public static final String ASSUMPTION_VALUE_NOT_FOUND_MESSAGE = "Operation's Assumption value is not present in allowed columns, previous outputs, or active Assumption Keys ";

    public static final String ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_CODE = "ASSUMPTION_KEY_NOT_FOUND_IN_MDMS";
    public static final String ASSUMPTION_KEY_NOT_FOUND_IN_MDMS_MESSAGE = "Assumption Key is not present in MDMS - ";

    public static final String DUPLICATE_ASSUMPTION_KEY_CODE = "DUPLICATE_ASSUMPTION_KEY";
    public static final String DUPLICATE_ASSUMPTION_KEY_MESSAGE = "Duplicate Assumption key found : ";

    public static final String VEHICLE_ID_NOT_FOUND_IN_MDMS_CODE = "VEHICLE_ID_NOT_FOUND_IN_MDMS";
    public static final String VEHICLE_ID_NOT_FOUND_IN_MDMS_MESSAGE = "Vehicle Id is not present in MDMS";

    public static final String VEHICLE_IDS_INVALID_DATA_TYPE_CODE = "VEHICLE_IDS_INVALID_DATA_TYPE";
    public static final String VEHICLE_IDS_INVALID_DATA_TYPE_MESSAGE = "Vehicle IDs should be a list of strings.";

    public static final String TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_CODE = "TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS";
    public static final String TEMPLATE_IDENTIFIER_NOT_FOUND_IN_MDMS_MESSAGE = "Template Identifier is not present in MDMS ";

    public static final String REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_CODE = "REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND";
    public static final String REQUIRED_TEMPLATE_IDENTIFIER_NOT_FOUND_MESSAGE = "Required Template Identifier is not present in Files ";

    public static final String ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_CODE = "ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER";
    public static final String ONLY_ONE_FILE_OF_REQUIRED_TEMPLATE_IDENTIFIER_MESSAGE = "Only one file of the required template identifier should be present ";

    public static final String INPUT_KEY_NOT_FOUND_CODE = "INPUT_KEY_NOT_FOUND";
    public static final String INPUT_KEY_NOT_FOUND_MESSAGE = "Operation's Input key is not present in allowed columns or previous outputs - ";

    public static final String TENANT_ID_EMPTY_CODE = "TENANT_ID_EMPTY";
    public static final String TENANT_ID_EMPTY_MESSAGE = "Tenant Id cannot be empty, TenantId should be present";

    public static final String PLAN_CONFIG_ID_EMPTY_CODE = "PLAN_CONFIG_ID_EMPTY";
    public static final String PLAN_CONFIG_ID_EMPTY_MESSAGE = "Plan config Id cannot be empty.";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String INVALID_EMPLOYEE_ID_CODE = "INVALID_EMPLOYEE_ID";
    public static final String INVALID_EMPLOYEE_ID_MESSAGE = "Invalid or incorrect employee id.";

    public static final String NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE = "NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID";
    public static final String NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE = "Invalid or incorrect campaign id. No campaign details found for provided campaign id.";

    public static final String NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_CODE = "NO_CENSUS_FOUND_FOR_GIVEN_DETAILS";
    public static final String NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_MESSAGE = "Census records does not exists for the given details: ";


    public static final String INVALID_ROOT_EMPLOYEE_JURISDICTION_CODE = "INVALID_ROOT_EMPLOYEE_JURISDICTION";
    public static final String INVALID_ROOT_EMPLOYEE_JURISDICTION_MESSAGE = "The root employee's jurisdiction should be at highest hierarchy";

    public static final String INVALID_EMPLOYEE_JURISDICTION_CODE = "INVALID_EMPLOYEE_JURISDICTION";
    public static final String INVALID_EMPLOYEE_JURISDICTION_MESSAGE = "The employee's jurisdiction can't be at highest or lowest hierarchy";

    public static final String INVALID_JURISDICTION_CODE = "INVALID_JURISDICTION";
    public static final String INVALID_JURISDICTION_MESSAGE = "The employee's jurisdiction provided is invalid";

    public static final String INVALID_HIERARCHY_LEVEL_CODE = "INVALID_HIERARCHY_LEVEL";
    public static final String INVALID_HIERARCHY_LEVEL_MESSAGE = "The hierarchy level provided is invalid";

    public static final String INVALID_EMPLOYEE_ROLE_CODE = "INVALID_EMPLOYEE_ROLE";
    public static final String INVALID_EMPLOYEE_ROLE_MESSAGE = "The employee's role provided is invalid";

    public static final String SEARCH_CRITERIA_EMPTY_CODE = "SEARCH_CRITERIA_EMPTY";
    public static final String SEARCH_CRITERIA_EMPTY_MESSAGE = "Search criteria cannot be empty";

    public static final String INVALID_PLAN_CONFIG_ID_CODE = "INVALID_PLAN_CONFIG_ID";
    public static final String INVALID_PLAN_CONFIG_ID_MESSAGE = "Plan config id provided is invalid";

    public static final String INVALID_PLAN_EMPLOYEE_ASSIGNMENT_CODE = "INVALID_PLAN_EMPLOYEE_ASSIGNMENT";
    public static final String INVALID_PLAN_EMPLOYEE_ASSIGNMENT_MESSAGE = "Plan employee assignment to be updated doesn't exists";

    public static final String PLAN_CONFIGURATION_ALREADY_EXISTS_CODE = "PLAN_CONFIGURATION_ALREADY_EXISTS";
    public static final String PLAN_CONFIGURATION_ALREADY_EXISTS_MESSAGE = "Plan Configuration for the provided name and campaign id already exists";

    public static final String PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXISTS_CODE = "PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXISTS";
    public static final String PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXISTS_MESSAGE = "Plan employee assignment for the provided details already exists";

    public static final String PLAN_FACILITY_LINKAGE_ALREADY_EXISTS_CODE = "PLAN_FACILITY_LINKAGE_ALREADY_EXISTS";
    public static final String PLAN_FACILITY_LINKAGE_ALREADY_EXISTS_MESSAGE = "Plan facility linkage for the provided facilityId and planConfigId already exists";

    public static final String PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY_CODE = "PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY";
    public static final String PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY_MESSAGE = "Plan employee assignment id cannot be empty";

    public static final String PLAN_EMPLOYEE_ASSIGNMENT_NOT_FOUND_CODE = "PLAN_EMPLOYEE_ASSIGNMENT_FOR_BOUNDARY_NOT_FOUND";
    public static final String PLAN_EMPLOYEE_ASSIGNMENT_NOT_FOUND_MESSAGE = "No plan-employee assignment found for the provided boundary - ";

    public static final String JURISDICTION_NOT_FOUND_CODE = "JURISDICTION_NOT_FOUND";
    public static final String JURISDICTION_NOT_FOUND_MESSAGE = "Employee doesn't have the jurisdiction to take action for the provided locality.";

    public static final String NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_CODE = "NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE";
    public static final String NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_MESSAGE = "Invalid or incorrect boundaryCode. No boundary data found.";

    public static final String METRIC_NOT_FOUND_IN_MDMS_CODE = "METRIC_NOT_FOUND_IN_MDMS";
    public static final String METRIC_NOT_FOUND_IN_MDMS_MESSAGE = "Metric key not found in MDMS";

    public static final String METRIC_UNIT_NOT_FOUND_IN_MDMS_CODE = "METRIC_UNIT_NOT_FOUND_IN_MDMS";
    public static final String METRIC_UNIT_NOT_FOUND_IN_MDMS_MESSAGE = "Metric Details' Unit not found in MDMS";

    public static final String INACTIVE_OPERATION_USED_AS_INPUT_CODE = "INACTIVE_OPERATION_USED_AS_INPUT";
    public static final String INACTIVE_OPERATION_USED_AS_INPUT_MESSAGE = "Inactive operation output used. ";

    public static final String JSONPATH_ERROR_CODE = "JSONPATH_ERROR";
    public static final String JSONPATH_ERROR_MESSAGE = "Failed to parse mdms response with given Jsonpath" ;

    public static final String PARSING_ERROR_CODE = "PARSING_ERROR";
    public static final String PARSING_ERROR_MESSAGE = "Failed to parse additionalDetails object" ;

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

    public static final String ADDITIONAL_DETAILS_MISSING_CODE = "ADDITIONAL_DETAILS_MISSING";
    public static final String ADDITIONAL_DETAILS_MISSING_MESSAGE = "Additional details are missing in the plan configuration request.";

    public static final String PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_CODE = "PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT";
    public static final String PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_MESSAGE = "Key is not present in json object - ";

    public static final String ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE = "ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS";
    public static final String ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE = "Exception occurred while updating additional details  : ";

    public static final String WORKFLOW_INTEGRATION_ERROR_CODE = "WORKFLOW_INTEGRATION_ERROR";
    public static final String WORKFLOW_INTEGRATION_ERROR_MESSAGE = "Exception occured while integrating with workflow : ";

    public static final String PROCESS_INSTANCE_NOT_FOUND_CODE = "PROCESS_INSTANCE_NOT_FOUND";
    public static final String PROCESS_INSTANCE_NOT_FOUND_MESSAGE = "No process instance found with businessId: ";

    public static final String FILES_NOT_FOUND_CODE = "FILES_NOT_FOUND";
    public static final String FILES_NOT_FOUND_MESSAGE = "Files are not present in Plan Configuration.";

    public static final String ASSUMPTIONS_NOT_FOUND_CODE = "ASSUMPTIONS_NOT_FOUND";
    public static final String ASSUMPTIONS_NOT_FOUND_MESSAGE = "Assumptions are not present in Plan Configuration.";

    public static final String OPERATIONS_NOT_FOUND_CODE = "OPERATIONS_NOT_FOUND";
    public static final String OPERATIONS_NOT_FOUND_MESSAGE = "Operations are not present in Plan Configuration.";

    public static final String NO_BUSINESS_SERVICE_DATA_FOUND_CODE = "NO_BUSINESS_SERVICE_DATA_FOUND";
    public static final String NO_BUSINESS_SERVICE_DATA_FOUND_MESSAGE = "Invalid or incorrect businessService. No business service data found.";

    //mdms constants
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_ADMIN_CONSOLE_MODULE_NAME = "HCM-ADMIN-CONSOLE";
    public static final String MDMS_MASTER_HIERARCHY_CONFIG = "hierarchyConfig";
    public static final String MDMS_MASTER_HIERARCHY_SCHEMA = "HierarchySchema";
    public static final String MDMS_MASTER_ASSUMPTION = "HypothesisAssumptions";
    public static final String MDMS_MASTER_UPLOAD_CONFIGURATION = "UploadConfiguration";
    public static final String MDMS_MASTER_RULE_CONFIGURE_INPUTS = "RuleConfigureInputs";
    public static final String MDMS_MASTER_SCHEMAS = "Schemas";
    public static final String MDMS_MASTER_METRIC = "Metric";
    public static final String MDMS_MASTER_UOM = "Uom";
    public static final String MDMS_MASTER_NAME_VALIDATION= "MicroplanNamingRegex";
    public static final String MDMS_SCHEMA_ADMIN_SCHEMA = "adminSchema";
    public static final String BOUNDARY = "boundary";
    public static final String HIERARCHY_TYPE = "hierarchyType";

    //MDMS field Constants
    public static final String PROPERTIES = "properties";
    public static final String NUMBER_PROPERTIES = "numberProperties";
    public static final String STRING_PROPERTIES = "stringProperties";
    public static final String NAME = "name";

    public static final String MICROPLAN_PREFIX = "MP-";

    public static final String JSON_ROOT_PATH = "$.";

    public static final String DOT_SEPARATOR = ".";

    public static final String PIPE_SEPARATOR = "|";

    public static final String DOT_REGEX = "\\.";

    public static final String PIPE_REGEX = "\\|";

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

    public static final String FILTER_ALL_ASSUMPTIONS = ".assumptionCategories[*].assumptions[*]";

    public static final String HIERARCHY_CONFIG_FOR_MICROPLAN = "[?(@.type == 'microplan')]";

    public static final String HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN = "highestHierarchy";

    public static final String LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN = "lowestHierarchy";

    public static final String NAME_VALIDATION_DATA = "Data";

    // Constants for constructing logical expressions or queries
    public static final String AND = " && ";
    public static final String EQUALS = " == ";
    public static final String SINGLE_QUOTE = "'";

    // JSON field constants for campaign details
    public static final String JSON_FIELD_CAMPAIGN_TYPE = "campaignType";
    public static final String JSON_FIELD_DISTRIBUTION_PROCESS = "DistributionProcess";
    public static final String JSON_FIELD_REGISTRATION_PROCESS = "RegistrationProcess";
    public static final String JSON_FIELD_RESOURCE_DISTRIBUTION_STRATEGY_CODE = "resourceDistributionStrategyCode";
    public static final String JSON_FIELD_IS_REGISTRATION_AND_DISTRIBUTION_TOGETHER = "isRegistrationAndDistributionHappeningTogetherOrSeparately";
    public static final String JSON_FIELD_VEHICLE_ID = "vehicleIds";

    // JSON path constants for campaign details
    public static final String JSONPATH_FILTER_PREFIX = "[?(";
    public static final String JSONPATH_FILTER_SUFFIX = ")]";
    public static final String JSON_PATH_FILTER_CAMPAIGN_TYPE = "@.campaignType";
    public static final String JSON_PATH_FILTER_DISTRIBUTION_PROCESS = "@.DistributionProcess";
    public static final String JSON_PATH_FILTER_REGISTRATION_PROCESS = "@.RegistrationProcess";
    public static final String JSON_PATH_FILTER_RESOURCE_DISTRIBUTION_STRATEGY_CODE = "@.resourceDistributionStrategyCode";
    public static final String JSON_PATH_FILTER_IS_REGISTRATION_AND_DISTRIBUTION_TOGETHER = "@.isRegistrationAndDistributionHappeningTogetherOrSeparately";

    // Workflow Constants
    public static final String PLAN_CONFIGURATION_BUSINESS_SERVICE = "PLAN_CONFIGURATION";

    public static final String PLAN_ESTIMATION_BUSINESS_SERVICE = "PLAN_ESTIMATION";

    public static final String MODULE_NAME_VALUE = "plan-service";

    public static final String DRAFT_STATUS = "DRAFT";

    public static final String SETUP_COMPLETED_ACTION = "INITIATE";

    public static final String URI_TENANT_ID_PARAM = "tenantId";

    public static final String URI_BUSINESS_SERVICE_PARAM = "businessService";

    public static final String URI_BUSINESS_SERVICE_QUERY_TEMPLATE = "?tenantId={tenantId}&businessServices={businessService}";

    public static final String APPROVE_CENSUS_DATA_ACTION = "APPROVE_CENSUS_DATA";

    public static final String FINALIZE_CATCHMENT_MAPPING_ACTION = "FINALIZE_CATCHMENT_MAPPING";

    public static final String APPROVE_ESTIMATIONS_ACTION = "APPROVE_ESTIMATIONS";

    public static final String VALIDATED_STATUS = "VALIDATED";

    //Query constants
    public static final String PERCENTAGE_WILDCARD = "%";

    public static final String MDMS_MASTER_HIERARCHY= "hierarchy";

    public static final String ERROR_WHILE_FETCHING_FROM_FACILITY = "Exception occurred while fetching facility details from facility service ";

    public static final String ERROR_WHILE_FETCHING_BUSINESS_SERVICE_DETAILS = "Exception occurred while fetching business service details: ";

    public static final String INVALID_PLAN_FACILITY_ID_CODE = "INVALID_PLAN_FACILITY_ID";
    public static final String INVALID_PLAN_FACILITY_ID_MESSAGE = "Plan facility id provided is invalid";

    public static final String INVALID_SERVICE_BOUNDARY_CODE = "INVALID_SERVICE_BOUNDARY";
    public static final String INVALID_SERVICE_BOUNDARY_MESSAGE = "The provided service boundary is invalid";

    public static final String INVALID_RESIDING_BOUNDARY_CODE = "INVALID_RESIDING_BOUNDARY";
    public static final String INVALID_RESIDING_BOUNDARY_MESSAGE = "The provided residing boundary is invalid";

    public static final String CANNOT_APPROVE_CENSUS_DATA_CODE = "CANNOT_APPROVE_CENSUS_DATA";
    public static final String CANNOT_APPROVE_CENSUS_DATA_MESSAGE = "Census data can't be approved until all the census records are validated";

    public static final String CANNOT_APPROVE_ESTIMATIONS_CODE = "CANNOT_APPROVE_ESTIMATIONS";
    public static final String CANNOT_APPROVE_ESTIMATIONS_MESSAGE = "Estimations can't be approved until all the estimations are validated";

    public static final String CANNOT_FINALIZE_CATCHMENT_MAPPING_CODE = "CANNOT_FINALIZE_CATCHMENT_MAPPING";
    public static final String CANNOT_FINALIZE_CATCHMENT_MAPPING_MESSAGE = "Catchment mapping can't be finalized until all boundaries have facility assigned";

    public static final String HIERARCHY_NOT_FOUND_IN_MDMS_CODE = "HIERARCHY_NOT_FOUND_IN_MDMS";
    public static final String HIERARCHY_NOT_FOUND_IN_MDMS_MESSAGE = "Hierarchy key not found in mdms";

    public static final String FAILED_MESSAGE = "Failed to push message to topic";

    public static final String FACILITY_NAME_SEARCH_PARAMETER_KEY = "facilityName";

    public static final String FACILITY_ID_SEARCH_PARAMETER_KEY = "facilityId";

    public static final String FACILITY_STATUS_SEARCH_PARAMETER_KEY = "facilityStatus";

    public static final String FACILITY_TYPE_SEARCH_PARAMETER_KEY = "facilityType";

    public static final String TERRAIN_CONDITION_SEARCH_PARAMETER_KEY = "accessibilityDetails|terrain|code";

    public static final String ROAD_CONDITION_SEARCH_PARAMETER_KEY = "accessibilityDetails|roadCondition|code";

    public static final String SECURITY_Q1_SEARCH_PARAMETER_KEY = "securityDetails|1|code";

    public static final String SECURITY_Q2_SEARCH_PARAMETER_KEY = "securityDetails|2|code";

    public static final String COMMA_DELIMITER = ",";

    public static final String SERVING_POPULATION_CODE = "servingPopulation";

    public static final String CONFIRMED_TARGET_POPULATION_AGE_3TO11 = "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION_AGE_3TO11";

    public static final String CONFIRMED_TARGET_POPULATION_AGE_12TO59 = "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION_AGE_12TO59";

    public static final String CONFIRMED_TARGET_POPULATION = "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION";

    public static final String ADDITIONAL_DETAILS_QUERY = " additional_details @> CAST( ? AS jsonb )";

    public static final String JSONB_QUERY_FORMAT = "additional_details @> ?::jsonb";

    public static final String AND_CONDITION = " AND ";

    public static final String OR_CONDITION = " OR ";
}
