// config.js
// Importing necessary module
import { getErrorCodes } from "./constants";
// Defining the HOST variable
const HOST = process.env.EGOV_HOST ||
  "https://unified-dev.digit.org/";
// Checking if HOST is set, if not, exiting the process
if (!HOST) {
  console.log("You need to set the HOST variable");
  process.exit(1);
}


const getDBSchemaName = (dbSchema = "") => {
  // return "health";
  return dbSchema ? (dbSchema == "egov" ? "public" : dbSchema) : "public";
}
// Configuration object containing various environment variables
const config = {
  batchSize: 100,
  cacheTime: 300,
  retryUntilResourceCreationComplete: process.env.RETRY_TILL_RESOURCE_CREATION_COMPLETES || 100,
  isProduction: process.env ? true : false,
  token: "", // add default token if core services are not port forwarded
  enableDynamicTemplateFor: process.env.ENABLE_DYNAMIC_TEMPLATE_FOR || "",
  isCallGenerateWhenDeliveryConditionsDiffer: (process.env.IS_CALL_GENERATE_WHEN_DELIVERY_CONDITIONS_DIFFER === "true") || false,
  prefixForMicroplanCampaigns: "MP",
  excludeHierarchyTypeFromBoundaryCodes: (process.env.EXCLUDE_HIERARCHY_TYPE_FROM_BOUNDARY_CODES === "true") || false,
  excludeBoundaryNameAtLastFromBoundaryCodes: (process.env.EXCLUDE_BOUNDARY_NAME_AT_LAST_FROM_BOUNDARY_CODES === "true") || false,
  masterNameForSchemaOfColumnHeaders: "adminSchema",
  masterNameForSplitBoundariesOn: "HierarchySchema",
  boundary: {
    boundaryCode: process.env.BOUNDARY_CODE_HEADER_NAME || "HCM_ADMIN_CONSOLE_BOUNDARY_CODE",
    boundaryCodeMandatory: 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY',
    boundaryCodeMandatoryForMicroplanFacility: process.env.BOUNDARY_CODE_HEADER_NAME_FACILITY_MICROPLAN || "HCM_ADMIN_CONSOLE_RESIDING_BOUNDARY_CODE_MICROPLAN",
    boundaryCodeOld: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD",
    boundaryTab: process.env.BOUNDARY_TAB_NAME || "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
    // default configurable number of data of boundary type on which generate different tabs
    numberOfBoundaryDataOnWhichWeSplit: process.env.SPLIT_BOUNDARIES_ON_LENGTH || "2",
    boundaryRelationShipDelay: 3500
  },
  facility: {
    facilityTab: process.env.FACILITY_TAB_NAME || "HCM_ADMIN_CONSOLE_FACILITIES",
    facilityCodeColumn: "HCM_ADMIN_CONSOLE_FACILITY_CODE",
    facilityType: "facility"
  },
  user: {
    userTab: process.env.USER_TAB_NAME || "HCM_ADMIN_CONSOLE_USER_LIST",
    userDefaultPassword: process.env.USER_DEFAULT_PASSWORD || "eGov@123",
    userPasswordAutoGenerate: process.env.USER_PASSWORD_AUTO_GENERATE || "true",
    mapUserViaCommonParent: process.env.MAP_USER_VIA_COMMON_PARENT || false,
    phoneNumberLength: 10
  },
  cacheValues: {
    cacheEnabled: process.env.CACHE_ENABLED,
    resetCache: process.env.RESET_CACHE,
    redisPort: process.env.REDIS_PORT || "6379",
  },
  kafka: {
    // Kafka topics
    KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "save-project-campaign-details",
    KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "update-project-campaign-details",
    KAFKA_START_CAMPAIGN_MAPPING_TOPIC: process.env.KAFKA_START_CAMPAIGN_MAPPING_TOPIC || "start-campaign-mapping",
    KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC || "update-campaign-details",
    KAFKA_CREATE_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC || "create-resource-details",
    KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC || "update-resource-details",
    KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC: process.env.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC || "create-resource-activity",
    KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC || "update-generated-resource-details",
    KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC || "create-generated-resource-details",
    KAFKA_SAVE_PROCESS_TRACK_TOPIC: process.env.KAFKA_SAVE_PROCESS_TRACK_TOPIC || "save-process-track",
    KAFKA_UPDATE_PROCESS_TRACK_TOPIC: process.env.KAFKA_UPDATE_PROCESS_TRACK_TOPIC || "update-process-track",
    KAFKA_SAVE_PLAN_FACILITY_TOPIC: process.env.KAFKA_SAVE_PLAN_FACILITY_TOPIC || "project-factory-save-plan-facility",
    KAFKA_SAVE_SHEET_DATA_TOPIC: process.env.KAFKA_SAVE_SHEET_DATA_TOPIC || "save-sheet-data",
    KAFKA_UPDATE_SHEET_DATA_TOPIC: process.env.KAFKA_UPDATE_SHEET_DATA_TOPIC || "update-sheet-data",
    KAFKA_SAVE_MAPPING_DATA_TOPIC: process.env.KAFKA_SAVE_MAPPING_TOPIC || "save-mapping-data",
    KAFKA_UPDATE_MAPPING_DATA_TOPIC: process.env.KAFKA_UPDATE_MAPPING_TOPIC || "update-mapping-data",
    KAFKA_DELETE_MAPPING_DATA_TOPIC: process.env.KAFKA_DELETE_MAPPING_TOPIC || "delete-mapping-data",
    KAFKA_SAVE_PROCESS_DATA_TOPIC: process.env.KAFKA_SAVE_PROCESS_TOPIC || "save-process-data",
    KAFKA_UPDATE_PROCESS_DATA_TOPIC: process.env.KAFKA_UPDATE_PROCESS_TOPIC || "update-process-data",
    KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC: process.env.KAFKA_START_TASK_TOPIC || "start-admin-console-task",
    KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC: process.env.KAFKA_START_MAPPING_TASK_TOPIC || "start-admin-console-mapping-task",
    KAFKA_TEST_TOPIC: "test-topic-project-factory",
  },

  // Database configuration
  DB_CONFIG: {
    DB_USER: process.env.DB_USER || "postgres",
    DB_HOST: process.env.DB_HOST?.split(':')[0] || "localhost",
    DB_NAME: process.env.DB_NAME || "postgres",
    DB_PASSWORD: process.env.DB_PASSWORD || "postgres",
    DB_PORT: process.env.DB_PORT || "5432",
    DB_CAMPAIGN_DETAILS_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_campaign_details`,
    DB_CAMPAIGN_PROCESS_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_campaign_process`,
    DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_generated_resource_details`,
    DB_RESOURCE_DETAILS_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_resource_details`,
    DB_CAMPAIGN_DATA_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_campaign_data`,
    DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_campaign_mapping_data`,
    DB_CAMPAIGN_PROCESS_DATA_TABLE_NAME: `${getDBSchemaName(process.env.DB_SCHEMA)}.eg_cm_campaign_process_data`,
  },
  // Application configuration
  app: {
    port: parseInt(process.env.APP_PORT || "8080") || 8080,
    host: HOST,
    contextPath: process.env.CONTEXT_PATH || "/project-factory",
    logLevel: process.env.APP_LOG_LEVEL || "debug",
    debugLogCharLimit: process.env.APP_MAX_DEBUG_CHAR ? Number(process.env.APP_MAX_DEBUG_CHAR) : 1000,
    defaultTenantId: process.env.DEFAULT_TENANT_ID || "mz",
    incomingRequestPayloadLimit : process.env.INCOMING_REQUEST_PAYLOAD_LIMIT || "2mb",
    maxInFlight : process.env.MAX_INFLIGHT || "15",
    maxEventLoopLagMs : process.env.MAX_EVENT_LOOP_LAG_MS || "100",
  },
  localisation: {
    defaultLocale: process.env.LOCALE || "en_MZ",
    boundaryPrefix: "hcm-boundary",
    localizationModule: process.env.LOCALIZATION_MODULE || "hcm-admin-schemas",
    localizationWaitTimeInBoundaryCreation: parseInt(process.env.LOCALIZATION_WAIT_TIME_IN_BOUNDARY_CREATION || "30000"),
    localizationChunkSizeForBoundaryCreation: parseInt(process.env.LOCALIZATION_CHUNK_SIZE_FOR_BOUNDARY_CREATION || "2000"),
  },
  // targetColumnsForSpecificCampaigns: {
  //   bedNetCampaignColumns: ["HCM_ADMIN_CONSOLE_TARGET"],
  //   smcCampaignColumns: ["HCM_ADMIN_CONSOLE_TARGET_SMC_AGE_3_TO_11", "HCM_ADMIN_CONSOLE_TARGET_SMC_AGE_12_TO_59"]
  // },
  // Host configuration
  host: {
    serverHost: HOST,
    // Kafka broker host
    KAFKA_BROKER_HOST: process.env.KAFKA_BROKER_HOST || "kafka-v2.kafka-cluster:9092",
    redisHost: process.env.REDIS_HOST || "localhost",
    mdms: process.env.EGOV_MDMS_HOST || "https://unified-dev.digit.org/",
    mdmsV2: process.env.EGOV_MDMS_V2_HOST || "https://unified-dev.digit.org/",
    filestore: process.env.EGOV_FILESTORE_SERVICE_HOST || "https://unified-dev.digit.org/",
    projectFactoryBff: "http://localhost:8080/",
    idGenHost: process.env.EGOV_IDGEN_HOST || "https://unified-dev.digit.org/",
    facilityHost: process.env.EGOV_FACILITY_HOST || "https://unified-dev.digit.org/",
    boundaryHost: process.env.EGOV_BOUNDARY_HOST || "https://unified-dev.digit.org/",
    projectHost: process.env.EGOV_PROJECT_HOST || "https://unified-dev.digit.org/",
    userHost: process.env.EGOV_USER_HOST || "https://unified-dev.digit.org/",
    productHost: process.env.EGOV_PRODUCT_HOST || "https://unified-dev.digit.org/",
    hrmsHost: process.env.EGOV_HRMS_HOST || "https://unified-dev.digit.org/",
    localizationHost: process.env.EGOV_LOCALIZATION_HOST || "https://unified-dev.digit.org/",
    healthIndividualHost: process.env.EGOV_HEALTH_INDIVIDUAL_HOST || "https://unified-dev.digit.org/",
    planServiceHost: process.env.EGOV_PLAN_SERVICE_HOST || "https://unified-dev.digit.org/",
    censusServiceHost: process.env.EGOV_CENSUS_HOST || "https://unified-dev.digit.org/",
  },
  // Paths for different services
  paths: {
    filestore: process.env.FILE_STORE_SERVICE_END_POINT || "filestore/v1/files",
    filestorefetch: "filestore/v1/files/url",
    mdms_v2_search: process.env.EGOV_MDMS_V2_SEARCH_ENDPOINT || "mdms-v2/v2/_search",
    mdms_v1_search: process.env.EGOV_MDMS_V1_SEARCH_ENDPOINT || "mdms-v2/v1/_search",
    idGen: process.env.EGOV_IDGEN_PATH || "egov-idgen/id/_generate",
    mdmsSchema: process.env.EGOV_MDMS_SCHEMA_PATH || "egov-mdms-service/schema/v1/_search",
    boundaryRelationship: process.env.EGOV_BOUNDARY_RELATIONSHIP_SEARCHPATH || "boundary-service/boundary-relationships/_search",
    boundaryServiceSearch: process.env.EGOV_BOUNDARY_SERVICE_SEARCHPATH || "boundary-service/boundary/_search",
    boundaryHierarchy: process.env.EGOV_BOUNDARY_HIERARCHY_SEARCHPATH || "boundary-service/boundary-hierarchy-definition/_search",
    projectCreate: process.env.HEALTH_PROJECT_CREATE_PATH || "health-project/v1/_create",
    projectUpdate: process.env.HEALTH_PROJECT_UPDATE_PATH || "health-project/v1/_update",
    projectSearch: process.env.HEALTH_PROJECT_SEARCH_PATH || "health-project/v1/_search",
    staffCreate: process.env.EGOV_PROJECT_STAFF_CREATE_PATH || "health-project/staff/v1/_create",
    projectResourceCreate: process.env.EGOV_PROJECT_RESOURCE_CREATE_PATH || "health-project/resource/v1/_create",
    projectFacilityCreate: process.env.EGOV_PROJECT_RESOURCE_FACILITY_PATH || "health-project/facility/v1/_create",
    userSearch: process.env.EGOV_USER_SEARCH_PATH || "user/_search",
    facilitySearch: process.env.EGOV_FACILITY_SEARCH_PATH || "facility/v1/_search",
    productVariantSearch: process.env.EGOV_PRODUCT_VARIANT_SEARCH_PATH || "product/variant/v1/_search",
    boundaryEntity: process.env.EGOV_BOUNDARY_ENTITY_SEARCHPATH || "boundary-service/boundary/_search",
    facilityCreate: process.env.EGOV_FACILITY_CREATE_PATH || "facility/v1/_create",
    hrmsEmployeeCreate: process.env.EGOV_HRMS_EMPLOYEE_CREATE_PATH || "health-hrms/employees/_create",
    hrmsEmployeeSearch: process.env.EGOV_HRMS_EMPLOYEE_SEARCH_PATH || "health-hrms/employees/_search",
    localizationSearch: process.env.EGOV_LOCALIZATION_SEARCH || "localization/messages/v1/_search",
    localizationCreate: "localization/messages/v1/_upsert",
    cacheBurst: process.env.CACHE_BURST || "localization/messages/cache-bust",
    boundaryRelationshipCreate: "boundary-service/boundary-relationships/_create",
    healthIndividualSearch: process.env.EGOV_HEALTH_INDIVIDUAL_SEARCH || "health-individual/v1/_search",
    projectFacilitySearch: process.env.EGOV_HEALTH_PROJECT_FACILITY_SEARCH || "health-project/facility/v1/_search",
    projectStaffSearch: process.env.EGOV_HEALTH_PROJECT_STAFF_SEARCH || "health-project/staff/v1/_search",
    projectFacilityDelete: process.env.EGOV_HEALTH_PROJECT_FACILITY_BULK_DELETE || "health-project/facility/v1/bulk/_delete",
    projectStaffDelete: process.env.EGOV_HEALTH_PROJECT_STAFF_BULK_DELETE || "health-project/staff/v1/bulk/_delete",
    planFacilitySearch: process.env.EGOV_PLAN_FACILITY_SEARCH || "plan-service/plan/facility/_search",
    planConfigSearch: process.env.EGOV_PLAN_FACILITY_CONFIG_SEARCH || "plan-service/config/_search",
    planSearch: process.env.EGOV_PLAN_SEARCH || "plan-service/plan/_search",
    censusSearch: process.env.EGOV_CENSUS_SEARCH || "census-service/_search"
  },
  // Values configuration
  values: {
    //module name
    skipParentProjectConfirmation: process.env.SKIP_PARENT_PROJECT_CONFIRMATION === "true",
    unfrozeTillRow: process.env.UNFROZE_TILL_ROW || "5010",
    unfrozeTillColumn: process.env.UNFROZE_TILL_COLUMN || "26",
    moduleName: process.env.MODULE_NAME || "HCM-ADMIN-CONSOLE",
    readMeTab: process.env.READ_ME_TAB || "HCM_README_SHEETNAME",
    userMainBoundary: process.env.USER_MAIN_BOUNDARY || "mz",
    userMainBoundaryType: process.env.USER_MAIN_BOUNDARY_TYPE || "Country",
    idgen: {
      format: process.env.CMP_IDGEN_FORMAT || "CMP-[cy:yyyy-MM-dd]-[SEQ_EG_CMP_ID]",
      idName: process.env.CMP_IDGEN_IDNAME || "campaign.number",
      idNameForUserNameGeneration: "username.name",
      formatForUserName: "USR-[SEQ_EG_USER_NAME]"
    },
    retryCount: process.env.CREATE_RESOURCE_RETRY_COUNT || "3",
    notCreateUserIfAlreadyThere: process.env.NOT_CREATE_USER_IF_ALREADY_THERE === "true",
    maxHttpRetries: process.env.MAX_HTTP_RETRIES || "4",
    skipResourceCheckValidationBeforeCreateForLocalTesting: false, // can be set to true for local development 
    autoRetryIfHttpError: process.env.AUTO_RETRY_IF_HTTP_ERROR || "socket hang up" /* can be retry if there is any error for which default retry can be set */,
    latLongColumns: process.env.LAT_LONG_SUBSTRINGS || "HCM_ADMIN_CONSOLE_FACILITY_LATITUDE_OPTIONAL_MICROPLAN,HCM_ADMIN_CONSOLE_FACILITY_LONGITUDE_OPTIONAL_MICROPLAN,HCM_ADMIN_CONSOLE_TARGET_LAT_OPT,HCM_ADMIN_CONSOLE_TARGET_LONG_OPT",
    validateCampaignIdInMetadata: process.env.VALIDATE_CAMPAIGN_ID_IN_METADATA === "true"
  }
};
// Exporting getErrorCodes function and config object
export { getErrorCodes };
export default config;