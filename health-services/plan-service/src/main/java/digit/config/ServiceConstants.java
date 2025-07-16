package digit.config;


import org.springframework.stereotype.Component;


@Component
public class ServiceConstants {

    // API request constants
    public static final String RES_MSG_ID = "uief87324";
    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";
    public static final String ID = "id";

    public static final String ROOT_PREFIX = "ROOT";

    //mdms constants
    public static final String MDMS_PLAN_MODULE_NAME = "hcm-microplanning";
    public static final String MDMS_ADMIN_CONSOLE_MODULE_NAME = "HCM-ADMIN-CONSOLE";
    public static final String MDMS_MASTER_HIERARCHY_SCHEMA = "HierarchySchema";
    public static final String MDMS_MASTER_ASSUMPTION = "HypothesisAssumptions";
    public static final String MDMS_MASTER_UPLOAD_CONFIGURATION = "UploadConfiguration";
    public static final String MDMS_MASTER_RULE_CONFIGURE_INPUTS = "RuleConfigureInputs";
    public static final String MDMS_MASTER_METRIC = "Metric";
    public static final String MDMS_MASTER_UOM = "Uom";
    public static final String MDMS_MASTER_NAME_VALIDATION= "MicroplanNamingRegex";
    public static final String MDMS_MASTER_ADMIN_SCHEMA = "adminSchema";
    public static final String MDMS_MASTER_VEHICLE_DETAILS = "VehicleDetails";
    public static final String BOUNDARY = "boundary";
    public static final String HIERARCHY_TYPE = "hierarchyType";

    //MDMS field Constants
    public static final String PROPERTIES = "properties";
    public static final String NUMBER_PROPERTIES = "numberProperties";
    public static final String STRING_PROPERTIES = "stringProperties";
    public static final String NAME = "name";

    //constants for create json paths
    public static final String MICROPLAN_PREFIX = "MP-";
    public static final String JSON_ROOT_PATH = "$.";
    public static final String DOT_SEPARATOR = ".";
    public static final String PIPE_SEPARATOR = "|"; // literal
    public static final String DOT_REGEX = "\\.";
    public static final String PIPE_REGEX = "\\|"; // regex-safe

    public static final String FILTER_UOMCODE = ".*.uomCode";
    public static final String FILTER_CODE = ".*.code";
    public static final String FILTER_DATA = "$.*.data";
    public static final String FILTER_ALL_ASSUMPTIONS = ".assumptionCategories[*].assumptions[*]";
    public static final String HIERARCHY_CONFIG_FOR_MICROPLAN = "[?(@.type == 'microplan')]";


    public static final String HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN = "highestHierarchy";
    public static final String LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN = "lowestHierarchy";

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

    // Constants for constructing logical expressions or queries
    public static final String AND = " && ";
    public static final String EQUALS = " == ";
    public static final String SINGLE_QUOTE = "'";
    public static final String COMMA_DELIMITER = ",";
    public static final String PERCENTAGE_WILDCARD = "%";
    public static final String AND_CONDITION = " AND ";
    public static final String OR_CONDITION = " OR ";
    public static final String PAGINATION_LIMIT_PARAM = "limit";
    public static final String PAGINATION_OFFSET_PARAM = "offset";

    //Query constants
    public static final String FACILITY_ID_SEARCH_PARAMETER_KEY = "facilityId";
    public static final String FACILITY_STATUS_SEARCH_PARAMETER_KEY = "facilityStatus";
    public static final String FACILITY_TYPE_SEARCH_PARAMETER_KEY = "facilityType";
    public static final String TERRAIN_CONDITION_SEARCH_PARAMETER_KEY = "accessibilityDetails|terrain|code";
    public static final String ROAD_CONDITION_SEARCH_PARAMETER_KEY = "accessibilityDetails|roadCondition|code";
    public static final String SECURITY_Q1_SEARCH_PARAMETER_KEY = "securityDetails|1|code";
    public static final String SECURITY_Q2_SEARCH_PARAMETER_KEY = "securityDetails|2|code";
    public static final String SERVING_POPULATION_CODE = "servingPopulation";
    public static final String CONFIRMED_TARGET_POPULATION_AGE_3TO11 = "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION_AGE_3TO11";
    public static final String CONFIRMED_TARGET_POPULATION_AGE_12TO59 = "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION_AGE_12TO59";
    public static final String CONFIRMED_TARGET_POPULATION = "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION";
    public static final String JSONB_QUERY_FORMAT = "additional_details @> ?::jsonb";

    // facility detail constants
    public static final String FACILITY_USAGE_KEY = "facilityUsage";
    public static final String CAPACITY_KEY = "capacity";
    public static final String FACILITY_STATUS_KEY = "facilityStatus";
    public static final String FACILITY_TYPE_KEY = "facilityType";
    public static final String IS_PERMANENT_KEY = "isPermanent";
    public static final String SERVING_POPULATION_KEY = "servingPopulation";

}
