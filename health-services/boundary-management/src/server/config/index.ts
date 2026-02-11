// Defining the HOST variable
const HOST = process.env.EGOV_HOST ||
  "https://unified-dev.digit.org/" || "localhost:8080";
// Checking if HOST is set, if not, exiting the process
if (!HOST) {
  console.log("You need to set the HOST variable");
  process.exit(1);
}

// Configuration object containing various environment variables
const config = {
  batchSize: 100,
  cacheTime: 300,
  isProduction: process.env ? true : false,
  token: "", // add default token if core services are not port forwarded
  // isCallGenerateWhenDeliveryConditionsDiffer: (process.env.IS_CALL_GENERATE_WHEN_DELIVERY_CONDITIONS_DIFFER === "true") || false,
  excludeHierarchyTypeFromBoundaryCodes: (process.env.EXCLUDE_HIERARCHY_TYPE_FROM_BOUNDARY_CODES === "true") || false,
  excludeBoundaryNameAtLastFromBoundaryCodes: (process.env.EXCLUDE_BOUNDARY_NAME_AT_LAST_FROM_BOUNDARY_CODES === "true") || false,
  isEnvironmentCentralInstance: process.env.IS_ENVIRONMENT_CENTRAL_INSTANCE === "true",
  boundary: {
    boundaryCode: process.env.BOUNDARY_CODE_HEADER_NAME || "HCM_ADMIN_CONSOLE_BOUNDARY_CODE",
    boundaryCodeMandatory: 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY',
    boundaryCodeMandatoryForMicroplanFacility: process.env.BOUNDARY_CODE_HEADER_NAME_FACILITY_MICROPLAN || "HCM_ADMIN_CONSOLE_RESIDING_BOUNDARY_CODE_MICROPLAN",
    boundaryCodeOld: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD",
    boundaryTab: process.env.BOUNDARY_TAB_NAME || "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",
    // default configurable number of data of boundary type on which generate different tabs
    numberOfBoundaryDataOnWhichWeSplit: process.env.SPLIT_BOUNDARIES_ON_LENGTH || "2"
  },
  cacheValues: {
    cacheEnabled: process.env.CACHE_ENABLED,
    resetCache: process.env.RESET_CACHE,
    redisPort: process.env.REDIS_PORT || "6379",
  },
  kafka: {
    // Kafka topics
    KAFKA_CREATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC: process.env.KAFKA_CREATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC || "create-processed-boundary-management",
    KAFKA_UPDATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC: process.env.KAFKA_UPDATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC || "update-processed-boundary-management",
    KAFKA_UPDATE_GENERATED_BOUNDARY_MANAGEMENT_TOPIC: process.env.KAFKA_UPDATE_GENERATED_BOUNDARY_MANAGEMENT_TOPIC || "update-generated-boundary-management",
    KAFKA_CREATE_GENERATED_BOUNDARY_MANAGEMENT_TOPIC: process.env.KAFKA_CREATE_GENERATED_BOUNDARY_MANAGEMENT_TOPIC || "create-generated-boundary-management",
    KAFKA_TEST_TOPIC: "test-topic-project-factory",
  },

  // Database configuration
  DB_CONFIG: {
    DB_SCHEMA: process.env.DB_SCHEMA || "egov",
    DB_USER: process.env.DB_USER || "postgres",
    DB_HOST: process.env.DB_HOST?.split(':')[0] || "localhost",
    DB_NAME: process.env.DB_NAME || "postgres",
    DB_PASSWORD: process.env.DB_PASSWORD || "postgres",
    DB_PORT: process.env.DB_PORT || "5432",
    DB_GENERATED_TEMPLATE_TABLE_NAME: "eg_bm_generated_template",
    DB_PROCESSED_TEMPLATE_TABLE_NAME: "eg_bm_processed_template",
    DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME: "eg_bm_generated_template",
    DB_RESOURCE_DETAILS_TABLE_NAME: "eg_bm_processed_template"
  },
  // Application configuration
  app: {
    port: parseInt(process.env.APP_PORT || "8080") || 8080,
    host: HOST,
    contextPath: process.env.CONTEXT_PATH || "/boundary-management",
    logLevel: process.env.APP_LOG_LEVEL || "debug",
    debugLogCharLimit: process.env.APP_MAX_DEBUG_CHAR ? Number(process.env.APP_MAX_DEBUG_CHAR) : 1000,
    defaultTenantId: process.env.DEFAULT_TENANT_ID ,
    incomingRequestPayloadLimit: process.env.INCOMING_REQUEST_PAYLOAD_LIMIT || "2mb",
    maxInFlight: process.env.MAX_INFLIGHT || "15",
    maxEventLoopLagMs: process.env.MAX_EVENT_LOOP_LAG_MS || "100",
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
    boundaryHost: process.env.EGOV_BOUNDARY_HOST || "https://unified-dev.digit.org/",
    localizationHost: process.env.EGOV_LOCALIZATION_HOST || "https://unified-dev.digit.org/",
  },
  // Paths for different services
  paths: {
    filestore: process.env.FILE_STORE_SERVICE_END_POINT || "filestore/v1/files",
    filestorefetch: "filestore/v1/files/url",
    mdms_v2_search: process.env.EGOV_MDMS_V2_SEARCH_ENDPOINT || "egov-mdms-service/v2/_search",
    mdms_v1_search: process.env.EGOV_MDMS_V1_SEARCH_ENDPOINT || "egov-mdms-service/v1/_search",
    mdmsSchema: process.env.EGOV_MDMS_SCHEMA_PATH || "egov-mdms-service/schema/v1/_search",
    boundaryRelationship: process.env.EGOV_BOUNDARY_RELATIONSHIP_SEARCHPATH || "boundary-service/boundary-relationships/_search",
    boundaryServiceSearch: process.env.EGOV_BOUNDARY_SERVICE_SEARCHPATH || "boundary-service/boundary/_search",
    boundaryHierarchy: process.env.EGOV_BOUNDARY_HIERARCHY_SEARCHPATH || "boundary-service/boundary-hierarchy-definition/_search",
    boundaryEntity: process.env.EGOV_BOUNDARY_ENTITY_SEARCHPATH || "boundary-service/boundary/_search",
    localizationSearch: process.env.EGOV_LOCALIZATION_SEARCH || "localization/messages/v1/_search",
    localizationCreate: "localization/messages/v1/_upsert",
    cacheBurst: process.env.CACHE_BURST || "localization/messages/cache-bust",
    boundaryRelationshipCreate: "boundary-service/boundary-relationships/_create",
  },
  // Values configuration
  values: {
    //module name
    unfrozeTillRow: process.env.UNFROZE_TILL_ROW || "5010",
    maxHttpRetries: process.env.MAX_HTTP_RETRIES || "4",
    autoRetryIfHttpError: process.env.AUTO_RETRY_IF_HTTP_ERROR || "socket hang up" /* can be retry if there is any error for which default retry can be set */,
    validateCampaignIdInMetadata: process.env.VALIDATE_CAMPAIGN_ID_IN_METADATA === "true"
  },
};


export default config;