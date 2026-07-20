const HOST = process.env.EGOV_HOST ||
  "https://unified-dev.digit.org/";
if (!HOST) {
  console.log("You need to set the HOST variable");
  process.exit(1);
}

// Application configuration
const config = {
  batchSize: process.env.BATCH_SIZE ? parseInt(process.env.BATCH_SIZE, 10) : 100,
  cacheTime: 300,
  isProduction: process.env ? true : false,
  token: "",
  enableDynamicTemplateFor: process.env.ENABLE_DYNAMIC_TEMPLATE_FOR || "",
  prefixForMicroplanCampaigns: "MP",
  appTimezone: process.env.APP_TIMEZONE || "UTC",
  excludeHierarchyTypeFromBoundaryCodes: (process.env.EXCLUDE_HIERARCHY_TYPE_FROM_BOUNDARY_CODES === "true") || false,
  excludeBoundaryNameAtLastFromBoundaryCodes: (process.env.EXCLUDE_BOUNDARY_NAME_AT_LAST_FROM_BOUNDARY_CODES === "true") || false,
  isEnvironmentCentralInstance: process.env.IS_ENVIRONMENT_CENTRAL_INSTANCE === "true",
  kafkaConsumerTopicPrefix: process.env.KAFKA_CONSUMER_TOPIC_PREFIX || "",
  centralInstanceTenantIds: process.env.CENTRAL_INSTANCE_TENANT_IDS || "",
  masterNameForSplitBoundariesOn: "HierarchySchema",
  basesecret: process.env.BASE_SECRET,
      // Boundary configuration
    boundary: {
    boundaryCode: process.env.BOUNDARY_CODE_HEADER_NAME || "HCM_ADMIN_CONSOLE_BOUNDARY_CODE",
    boundaryCodeMandatory: 'HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY',
    boundaryCodeMandatoryForMicroplanFacility: process.env.BOUNDARY_CODE_HEADER_NAME_FACILITY_MICROPLAN || "HCM_ADMIN_CONSOLE_RESIDING_BOUNDARY_CODE_MICROPLAN",
    boundaryCodeOld: "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_OLD",
    boundaryTab: process.env.BOUNDARY_TAB_NAME || "HCM_ADMIN_CONSOLE_BOUNDARY_DATA",

    numberOfBoundaryDataOnWhichWeSplit: process.env.SPLIT_BOUNDARIES_ON_LENGTH || "2",

    mappingPersistBatchSize: process.env.BOUNDARY_MAPPING_PERSIST_BATCH_SIZE ? parseInt(process.env.BOUNDARY_MAPPING_PERSIST_BATCH_SIZE, 10) : 100,

    persistBatchSize: process.env.BOUNDARY_PERSIST_BATCH_SIZE ? parseInt(process.env.BOUNDARY_PERSIST_BATCH_SIZE, 10) : 100,
    syncRetryDelayMs: process.env.BOUNDARY_SYNC_RETRY_DELAY_MS ? parseInt(process.env.BOUNDARY_SYNC_RETRY_DELAY_MS, 10) : 4000,
  },
      // Project configuration
    project: {

    creationBatchSize: process.env.PROJECT_CREATION_BATCH_SIZE ? parseInt(process.env.PROJECT_CREATION_BATCH_SIZE, 10) : 100,
    bulkCreateChunkSize: process.env.PROJECT_BULK_CREATE_CHUNK_SIZE ? parseInt(process.env.PROJECT_BULK_CREATE_CHUNK_SIZE, 10) : 0,

    bulkCreateConcurrency: process.env.PROJECT_BULK_CREATE_CONCURRENCY ? parseInt(process.env.PROJECT_BULK_CREATE_CONCURRENCY, 10) : 5,

    searchPageSize: process.env.PROJECT_SEARCH_PAGE_SIZE ? parseInt(process.env.PROJECT_SEARCH_PAGE_SIZE, 10) : 100,
    confirmRetries: process.env.PROJECT_CONFIRM_RETRIES ? parseInt(process.env.PROJECT_CONFIRM_RETRIES, 10) : 15,
    confirmPollIntervalMs: process.env.PROJECT_CONFIRM_POLL_INTERVAL_MS ? parseInt(process.env.PROJECT_CONFIRM_POLL_INTERVAL_MS, 10) : 2000,
    createPaceDelayMs: process.env.PROJECT_CREATE_PACE_DELAY_MS ? parseInt(process.env.PROJECT_CREATE_PACE_DELAY_MS, 10) : 0,
  },
      // Facility configuration
    facility: {
    facilityTab: process.env.FACILITY_TAB_NAME || "HCM_ADMIN_CONSOLE_FACILITIES",
    facilityCodeColumn: "HCM_ADMIN_CONSOLE_FACILITY_CODE",
    facilityType: "facility",

    persistBatchSize: process.env.FACILITY_PERSIST_BATCH_SIZE ? parseInt(process.env.FACILITY_PERSIST_BATCH_SIZE, 10) : 100,

    creationBatchSize: process.env.FACILITY_CREATION_BATCH_SIZE ? parseInt(process.env.FACILITY_CREATION_BATCH_SIZE, 10) : 100,

    kafkaCreateBatchSize: process.env.FACILITY_KAFKA_CREATE_BATCH_SIZE ? parseInt(process.env.FACILITY_KAFKA_CREATE_BATCH_SIZE, 10) : 30,

    searchBatchSize: process.env.FACILITY_SEARCH_BATCH_SIZE ? parseInt(process.env.FACILITY_SEARCH_BATCH_SIZE, 10) : 50,
  },
      // User configuration
    user: {
    userTab: process.env.USER_TAB_NAME || "HCM_ADMIN_CONSOLE_USER_LIST",
    userDefaultPassword: process.env.USER_DEFAULT_PASSWORD || "eGov@123",
    userPasswordAutoGenerate: process.env.USER_PASSWORD_AUTO_GENERATE === "true",
    phoneNumberLength: process.env.PHONE_NUMBER_LENGTH ? parseInt(process.env.PHONE_NUMBER_LENGTH, 10) : 10,

    mappingPersistBatchSize: process.env.USER_MAPPING_PERSIST_BATCH_SIZE ? parseInt(process.env.USER_MAPPING_PERSIST_BATCH_SIZE, 10) : 100,

    persistBatchSize: process.env.USER_PERSIST_BATCH_SIZE ? parseInt(process.env.USER_PERSIST_BATCH_SIZE, 10) : 100,

    creationBatchSize: process.env.USER_CREATION_BATCH_SIZE ? parseInt(process.env.USER_CREATION_BATCH_SIZE, 10) : 100,

    kafkaCreateBatchSize: process.env.USER_KAFKA_CREATE_BATCH_SIZE ? parseInt(process.env.USER_KAFKA_CREATE_BATCH_SIZE, 10) : 20,
    kafkaProduceWindowSize: process.env.USER_KAFKA_PRODUCE_WINDOW_SIZE ? parseInt(process.env.USER_KAFKA_PRODUCE_WINDOW_SIZE, 10) : 100,
    kafkaProduceWindowDelayMs: process.env.USER_KAFKA_PRODUCE_WINDOW_DELAY_MS ? parseInt(process.env.USER_KAFKA_PRODUCE_WINDOW_DELAY_MS, 10) : 0,
    hrmsFallbackConcurrency: process.env.USER_HRMS_FALLBACK_CONCURRENCY ? parseInt(process.env.USER_HRMS_FALLBACK_CONCURRENCY, 10) : 5,

    hrmsFallbackMaxRetries: process.env.USER_HRMS_FALLBACK_MAX_RETRIES ? parseInt(process.env.USER_HRMS_FALLBACK_MAX_RETRIES, 10) : 2,
    hrmsFallbackBackoffMs: process.env.USER_HRMS_FALLBACK_BACKOFF_MS ? parseInt(process.env.USER_HRMS_FALLBACK_BACKOFF_MS, 10) : 500,

    hrmsFallbackWindowDelayMs: process.env.USER_HRMS_FALLBACK_WINDOW_DELAY_MS ? parseInt(process.env.USER_HRMS_FALLBACK_WINDOW_DELAY_MS, 10) : 200,
    KAFKA_CONSUMER_MAX_CONCURRENT: process.env.KAFKA_CONSUMER_MAX_CONCURRENT ? parseInt(process.env.KAFKA_CONSUMER_MAX_CONCURRENT, 10) : 5,

    searchBatchSize: process.env.USER_SEARCH_BATCH_SIZE ? parseInt(process.env.USER_SEARCH_BATCH_SIZE, 10) : 50,

    searchConcurrency: process.env.USER_SEARCH_CONCURRENCY ? parseInt(process.env.USER_SEARCH_CONCURRENCY, 10) : 10,

    validationSearchBatchSize: process.env.USER_VALIDATION_SEARCH_BATCH_SIZE ? parseInt(process.env.USER_VALIDATION_SEARCH_BATCH_SIZE, 10) : 50,

    individualSearchBatchSize: process.env.USER_INDIVIDUAL_SEARCH_BATCH_SIZE ? parseInt(process.env.USER_INDIVIDUAL_SEARCH_BATCH_SIZE, 10) : 50,
    individualConsistencyPollIntervalMs: process.env.USER_INDIVIDUAL_CONSISTENCY_POLL_INTERVAL_MS ? parseInt(process.env.USER_INDIVIDUAL_CONSISTENCY_POLL_INTERVAL_MS, 10) : 2000,
    individualConsistencyMaxPollAttempts: process.env.USER_INDIVIDUAL_CONSISTENCY_MAX_POLL_ATTEMPTS ? parseInt(process.env.USER_INDIVIDUAL_CONSISTENCY_MAX_POLL_ATTEMPTS, 10) : 5,
    workerCreateBatchLag: process.env.USER_WORKER_CREATE_BATCH_LAG ? parseInt(process.env.USER_WORKER_CREATE_BATCH_LAG, 10) : 2,
  },
      // Worker registry configuration
    workerRegistry: {

    searchBatchSize: process.env.WORKER_REGISTRY_SEARCH_BATCH_SIZE ? parseInt(process.env.WORKER_REGISTRY_SEARCH_BATCH_SIZE, 10) : 50,

    updateBatchSize: process.env.WORKER_REGISTRY_UPDATE_BATCH_SIZE ? parseInt(process.env.WORKER_REGISTRY_UPDATE_BATCH_SIZE, 10) : 100,
  },
      // Mapping configuration
    mapping: {

    kafkaBatchSize: process.env.MAPPING_KAFKA_BATCH_SIZE ? parseInt(process.env.MAPPING_KAFKA_BATCH_SIZE, 10) : 30,

    persistBatchSize: process.env.MAPPING_PERSIST_BATCH_SIZE ? parseInt(process.env.MAPPING_PERSIST_BATCH_SIZE, 10) : 100,
    projectSearchChunkSize: process.env.MAPPING_PROJECT_SEARCH_CHUNK_SIZE ? parseInt(process.env.MAPPING_PROJECT_SEARCH_CHUNK_SIZE, 10) : 100,

    searchPageSize: process.env.MAPPING_SEARCH_PAGE_SIZE ? parseInt(process.env.MAPPING_SEARCH_PAGE_SIZE, 10) : 100,

    createConcurrency: process.env.MAPPING_CREATE_CONCURRENCY ? parseInt(process.env.MAPPING_CREATE_CONCURRENCY, 10) : 10,
    bulkCreateChunkSize: process.env.MAPPING_BULK_CREATE_CHUNK_SIZE ? parseInt(process.env.MAPPING_BULK_CREATE_CHUNK_SIZE, 10) : 100,
    staffBulkEnabled: process.env.STAFF_MAPPING_BULK === "1" || process.env.STAFF_MAPPING_BULK === "true",
    bulkConfirmPollIntervalMs: process.env.MAPPING_BULK_CONFIRM_POLL_INTERVAL_MS ? parseInt(process.env.MAPPING_BULK_CONFIRM_POLL_INTERVAL_MS, 10) : 2000,
    bulkConfirmMaxAttempts: process.env.MAPPING_BULK_CONFIRM_MAX_ATTEMPTS ? parseInt(process.env.MAPPING_BULK_CONFIRM_MAX_ATTEMPTS, 10) : 5,

    maxRetries: process.env.MAPPING_MAX_RETRY_COUNT ? parseInt(process.env.MAPPING_MAX_RETRY_COUNT, 10) : 3,

    maxReconcileCycles: process.env.MAPPING_MAX_RECONCILE_CYCLES ? parseInt(process.env.MAPPING_MAX_RECONCILE_CYCLES, 10) : 5,

    reconcileStallTimeoutMs: process.env.MAPPING_RECONCILE_STALL_TIMEOUT_MS ? parseInt(process.env.MAPPING_RECONCILE_STALL_TIMEOUT_MS, 10) : 300000,
  },
      // Resource configuration
    resource: {

    activityBatchSize: process.env.RESOURCE_ACTIVITY_BATCH_SIZE ? parseInt(process.env.RESOURCE_ACTIVITY_BATCH_SIZE, 10) : 10,
  },
  productVariant: {

    searchBatchSize: process.env.PRODUCT_VARIANT_SEARCH_BATCH_SIZE ? parseInt(process.env.PRODUCT_VARIANT_SEARCH_BATCH_SIZE, 10) : 100,
  },
  sheetData: {

    persistBatchSize: process.env.SHEET_DATA_PERSIST_BATCH_SIZE ? parseInt(process.env.SHEET_DATA_PERSIST_BATCH_SIZE, 10) : 100,
  },
      // Attendance configuration
    attendanceRegister: {
    defaultEventType: process.env.ATTENDANCE_REGISTER_DEFAULT_EVENT_TYPE || "Training",
    defaultSessions: process.env.ATTENDANCE_REGISTER_DEFAULT_SESSIONS ? parseInt(process.env.ATTENDANCE_REGISTER_DEFAULT_SESSIONS, 10) : 1,
    batchSize: process.env.ATTENDANCE_BATCH_SIZE ? parseInt(process.env.ATTENDANCE_BATCH_SIZE, 10) : 50,
    serviceCodeParallelSearchLimit: process.env.ATTENDANCE_SERVICE_CODE_PARALLEL_SEARCH_LIMIT ? parseInt(process.env.ATTENDANCE_SERVICE_CODE_PARALLEL_SEARCH_LIMIT, 10) : 50,
    attendeeSearchPageSize: process.env.ATTENDANCE_ATTENDEE_SEARCH_PAGE_SIZE ? parseInt(process.env.ATTENDANCE_ATTENDEE_SEARCH_PAGE_SIZE, 10) : 100,
    staffSearchPageSize: process.env.ATTENDANCE_STAFF_SEARCH_PAGE_SIZE ? parseInt(process.env.ATTENDANCE_STAFF_SEARCH_PAGE_SIZE, 10) : 100,

    attendeePersistBatchSize: process.env.ATTENDANCE_ATTENDEE_PERSIST_BATCH_SIZE ? parseInt(process.env.ATTENDANCE_ATTENDEE_PERSIST_BATCH_SIZE, 10) : 100,

    registerPersistBatchSize: process.env.ATTENDANCE_REGISTER_PERSIST_BATCH_SIZE ? parseInt(process.env.ATTENDANCE_REGISTER_PERSIST_BATCH_SIZE, 10) : 100,

    registerApiBatchSize: process.env.ATTENDANCE_REGISTER_API_BATCH_SIZE ? parseInt(process.env.ATTENDANCE_REGISTER_API_BATCH_SIZE, 10) : 100,
  },
      // HRMS configuration
    hrms: {
    hrmsParallelSearchLimit: process.env.HRMS_PARALLEL_SEARCH_LIMIT ? parseInt(process.env.HRMS_PARALLEL_SEARCH_LIMIT, 10) : 100,

    searchByUuidBatchSize: process.env.HRMS_SEARCH_BY_UUID_BATCH_SIZE ? parseInt(process.env.HRMS_SEARCH_BY_UUID_BATCH_SIZE, 10) : 50,

    searchByUsernameBatchSize: process.env.HRMS_SEARCH_BY_USERNAME_BATCH_SIZE ? parseInt(process.env.HRMS_SEARCH_BY_USERNAME_BATCH_SIZE, 10) : 50,
  },
  cacheValues: {
    cacheEnabled: process.env.CACHE_ENABLED,
    resetCache: process.env.RESET_CACHE,
    redisPort: process.env.REDIS_PORT || "6379",
  },
      // Kafka configuration
    kafka: {
    CONSUMER_GROUP_ID: process.env.KAFKA_CONSUMER_GROUP_ID || "project-factory",
    KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "save-project-campaign-details",
    KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "update-project-campaign-details",
    KAFKA_CREATE_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC || "create-resource-details",
    KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC || "update-resource-details",
    KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC: process.env.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC || "create-resource-activity",
    KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC || "update-generated-resource-details",
    KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC || "create-generated-resource-details",
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
    KAFKA_TEST_TOPIC: process.env.KAFKA_TEST_TOPIC || "test-topic-project-factory",
    KAFKA_HCM_PROCESSING_RESULT_TOPIC: process.env.KAFKA_HCM_PROCESSING_RESULT_TOPIC || "hcm-processing-result",
    KAFKA_FACILITY_CREATE_BATCH_TOPIC: process.env.KAFKA_FACILITY_CREATE_BATCH_TOPIC || "hcm-facility-create-batch",
    KAFKA_USER_CREATE_BATCH_TOPIC: process.env.KAFKA_USER_CREATE_BATCH_TOPIC || "hcm-user-create-batch",
    KAFKA_MAPPING_BATCH_TOPIC: process.env.KAFKA_MAPPING_BATCH_TOPIC || "hcm-mapping-batch",
    KAFKA_CAMPAIGN_MARK_FAILED_TOPIC: process.env.KAFKA_CAMPAIGN_MARK_FAILED_TOPIC || "hcm-campaign-mark-failed",
    KAFKA_NOTIFICATION_EMAIL_TOPIC: process.env.KAFKA_NOTIFICATION_EMAIL_TOPIC || "egov.core.notification.email",
    KAFKA_NON_CENTRAL_INSTANCE_TOPICS: process.env.KAFKA_NON_CENTRAL_INSTANCE_TOPICS || "egov.core.notification.email",
    KAFKA_CONSUMER_MAX_BYTES_PER_PARTITION: parseInt(process.env.KAFKA_CONSUMER_MAX_BYTES_PER_PARTITION || "5242880", 10) || 5242880,
    KAFKA_PRODUCER_COMPRESSION_ENABLED: (process.env.KAFKA_PRODUCER_COMPRESSION_ENABLED || "true").toLowerCase() !== "false",
    KAFKA_TOPIC_LARGE_MESSAGE_MAX_BYTES: parseInt(process.env.KAFKA_TOPIC_LARGE_MESSAGE_MAX_BYTES || "4194304", 10),
    KAFKA_CONSUMER_RETRIES: parseInt(process.env.KAFKA_CONSUMER_RETRIES || "10", 10) || 10,
  },

      // Database configuration
    DB_CONFIG: {
    DB_SCHEMA: process.env.DB_SCHEMA || "egov",
    DB_USER: process.env.DB_USER || "postgres",
    DB_HOST: process.env.DB_HOST?.split(':')[0] || "localhost",
    DB_NAME: process.env.DB_NAME || "postgres",
    DB_PASSWORD: process.env.DB_PASSWORD || "postgres",
    DB_PORT: process.env.DB_PORT || "5432",
    DB_CAMPAIGN_DATA_TABLE_NAME: "eg_cm_campaign_data",
    DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME: "eg_cm_campaign_mapping_data",
    DB_CAMPAIGN_PROCESS_DATA_TABLE_NAME: "eg_cm_campaign_process_data",
    DB_CAMPAIGN_DETAILS_TABLE_NAME: "eg_cm_campaign_details",
    DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME: "eg_cm_generated_resource_details",
    DB_RESOURCE_DETAILS_TABLE_NAME: "eg_cm_resource_details"
  },
      // Application settings
    app: {
    port: parseInt(process.env.APP_PORT || "8080") || 8080,
    host: HOST,
    contextPath: process.env.CONTEXT_PATH || "/project-factory",
    logLevel: process.env.APP_LOG_LEVEL || "debug",
    debugLogCharLimit: process.env.APP_MAX_DEBUG_CHAR ? Number(process.env.APP_MAX_DEBUG_CHAR) : 1000,
    incomingRequestPayloadLimit: process.env.INCOMING_REQUEST_PAYLOAD_LIMIT || "2mb"
  },
  localisation: {
    defaultLocale: process.env.LOCALE || "en_MZ",
    boundaryPrefix: "hcm-boundary",
    localizationModule: process.env.LOCALIZATION_MODULE || "hcm-admin-schemas",
    localizationWaitTimeInBoundaryCreation: parseInt(process.env.LOCALIZATION_WAIT_TIME_IN_BOUNDARY_CREATION || "30000"),
    localizationChunkSizeForBoundaryCreation: parseInt(process.env.LOCALIZATION_CHUNK_SIZE_FOR_BOUNDARY_CREATION || "2000"),

    messageChunkSize: process.env.LOCALIZATION_MESSAGE_CHUNK_SIZE ? parseInt(process.env.LOCALIZATION_MESSAGE_CHUNK_SIZE, 10) : 100,
  },

      // External service hosts
    host: {
    serverHost: HOST,
    KAFKA_BROKER_HOST: process.env.KAFKA_BROKER_HOST || "kafka-v2.kafka-cluster:9092",
    redisHost: process.env.REDIS_HOST || "localhost",
    mdms: process.env.EGOV_MDMS_HOST || "https://unified-dev.digit.org/",
    mdmsV2: process.env.EGOV_MDMS_V2_HOST || "https://unified-dev.digit.org/",
    filestore: process.env.EGOV_FILESTORE_SERVICE_HOST || "https://unified-dev.digit.org/",
    projectFactoryBff: "http://localhost:8080/",
    idGenHost: process.env.EGOV_IDGEN_HOST || "https://unified-dev.digit.org/",
    facilityHost: process.env.EGOV_FACILITY_HOST || "https://unified-dev.digit.org/",
    boundaryHost: process.env.EGOV_BOUNDARY_HOST || "https://unified-dev.digit.org/",
    excelIngestionHost: process.env.EXCEL_INGESTION_HOST || "https://unified-dev.digit.org/",
    projectHost: process.env.EGOV_PROJECT_HOST || "https://unified-dev.digit.org/",
    userHost: process.env.EGOV_USER_HOST || "https://unified-dev.digit.org/",
    productHost: process.env.EGOV_PRODUCT_HOST || "https://unified-dev.digit.org/",
    hrmsHost: process.env.EGOV_HRMS_HOST || "https://unified-dev.digit.org/",
    localizationHost: process.env.EGOV_LOCALIZATION_HOST || "https://unified-dev.digit.org/",
    healthIndividualHost: process.env.EGOV_HEALTH_INDIVIDUAL_HOST || "https://unified-dev.digit.org/",
    planServiceHost: process.env.EGOV_PLAN_SERVICE_HOST || "https://unified-dev.digit.org/",
    censusServiceHost: process.env.EGOV_CENSUS_HOST || "https://unified-dev.digit.org/",
    workerRegistryHost: process.env.EGOV_WORKER_REGISTRY_HOST || "https://unified-dev.digit.org/",
    attendanceHost: process.env.EGOV_ATTENDANCE_HOST || "https://unified-dev.digit.org/",
  },
      // API endpoint paths
    paths: {
    filestore: process.env.FILE_STORE_SERVICE_END_POINT || "filestore/v1/files",
    filestorefetch: "filestore/v1/files/url",
    mdms_v2_search: process.env.EGOV_MDMS_V2_SEARCH_ENDPOINT || "egov-mdms-service/v2/_search",
    mdms_v2_create: process.env.EGOV_MDMS_V2_CREATE_ENDPOINT || "egov-mdms-service/v2/_create",
    mdms_v2_update: process.env.EGOV_MDMS_V2_UPDATE_ENDPOINT || "egov-mdms-service/v2/_update",
    mdms_v1_search: process.env.EGOV_MDMS_V1_SEARCH_ENDPOINT || "egov-mdms-service/v1/_search",
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
    staffBulkCreate: process.env.EGOV_PROJECT_STAFF_BULK_CREATE_PATH || "health-project/staff/v1/bulk/_create",
    projectResourceBulkCreate: process.env.EGOV_PROJECT_RESOURCE_BULK_CREATE_PATH || "health-project/resource/v1/bulk/_create",
    projectFacilityBulkCreate: process.env.EGOV_PROJECT_FACILITY_BULK_CREATE_PATH || "health-project/facility/v1/bulk/_create",
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
    projectResourceSearch: process.env.EGOV_PROJECT_RESOURCE_SEARCH_PATH || "health-project/resource/v1/_search",
    projectStaffSearch: process.env.EGOV_HEALTH_PROJECT_STAFF_SEARCH || "health-project/staff/v1/_search",
    projectFacilityDelete: process.env.EGOV_HEALTH_PROJECT_FACILITY_BULK_DELETE || "health-project/facility/v1/bulk/_delete",
    projectStaffDelete: process.env.EGOV_HEALTH_PROJECT_STAFF_BULK_DELETE || "health-project/staff/v1/bulk/_delete",
    planFacilitySearch: process.env.EGOV_PLAN_FACILITY_SEARCH || "plan-service/plan/facility/_search",
    planConfigSearch: process.env.EGOV_PLAN_FACILITY_CONFIG_SEARCH || "plan-service/config/_search",
    planSearch: process.env.EGOV_PLAN_SEARCH || "plan-service/plan/_search",
    censusSearch: process.env.EGOV_CENSUS_SEARCH || "census-service/_search",
    workerRegistryBulkCreate: process.env.EGOV_WORKER_REGISTRY_BULK_CREATE || "worker/v1/bulk/_create",
    workerRegistryBulkUpdate: process.env.EGOV_WORKER_REGISTRY_BULK_UPDATE || "worker/v1/bulk/_update",
    workerRegistrySearch: process.env.EGOV_WORKER_REGISTRY_SEARCH || "worker/v1/_search",
    excelIngestionSheetSearch: process.env.EXCEL_INGESTION_SHEET_SEARCH || "excel-ingestion/v1/data/sheet/_search",
    excelIngestionProcess: process.env.EXCEL_INGESTION_PROCESS || "excel-ingestion/v1/data/process/_create",
    excelIngestionGenerate: process.env.EXCEL_INGESTION_GENERATE || "excel-ingestion/v1/data/generate/_init",
    excelIngestionGenerateSearch: process.env.EXCEL_INGESTION_GENERATE_SEARCH || "excel-ingestion/v1/data/generate/_search",
    attendanceRegisterCreate: process.env.ATTENDANCE_REGISTER_CREATE_PATH || "health-attendance/v1/_create",
    attendanceRegisterSearch: process.env.ATTENDANCE_REGISTER_SEARCH_PATH || "health-attendance/v1/_search",
    attendanceRegisterUpdate: process.env.ATTENDANCE_REGISTER_UPDATE_PATH || "health-attendance/v1/_update",
    attendanceAttendeeCreate: process.env.ATTENDANCE_ATTENDEE_CREATE_PATH || "health-attendance/attendee/v1/_create",
    attendanceAttendeeDelete: process.env.ATTENDANCE_ATTENDEE_DELETE_PATH || "health-attendance/attendee/v1/_delete",
    attendanceAttendeeUpdateTag: process.env.ATTENDANCE_ATTENDEE_UPDATE_TAG_PATH || "health-attendance/attendee/v1/_updateTag",
    attendanceAttendeeSearch: process.env.ATTENDANCE_ATTENDEE_SEARCH_PATH || "health-attendance/attendee/v1/_search",
    attendanceStaffCreate: process.env.ATTENDANCE_STAFF_CREATE_PATH || "health-attendance/staff/v1/_create",
    attendanceStaffDelete: process.env.ATTENDANCE_STAFF_DELETE_PATH || "health-attendance/staff/v1/_delete",
    attendanceStaffSearch: process.env.ATTENDANCE_STAFF_SEARCH_PATH || "health-attendance/staff/v1/_search",
  },
      // General application values
    values: {
    skipParentProjectConfirmation: process.env.SKIP_PARENT_PROJECT_CONFIRMATION === "true",
    unfrozeTillRow: process.env.UNFROZE_TILL_ROW || "5010",
    unfrozeTillColumn: process.env.UNFROZE_TILL_COLUMN || "26",
    moduleName: process.env.MODULE_NAME || "HCM-ADMIN-CONSOLE",
    formConfigTemplateName: process.env.FORM_CONFIG_TEMPLATE_NAME || "FormConfigTemplate",
    formConfigName: process.env.FORM_CONFIG_NAME || "FormConfig",
    emailNotificationId: process.env.EMAIL_NOTIFICATION_ID || "support@egov.org.in" ,
    egovLogoLink: process.env.EGOV_LOGO_LINK || "https://digit-sandbox-prod-s3.s3.ap-south-1.amazonaws.com/assets/Reverse+Orange+%26+White.png",
    readMeTab: process.env.READ_ME_TAB || "HCM_README_SHEETNAME",
    userMainBoundary: process.env.USER_MAIN_BOUNDARY || "mz",
    userMainBoundaryType: process.env.USER_MAIN_BOUNDARY_TYPE || "Country",
    idgen: {
      format: process.env.CMP_IDGEN_FORMAT || "CMP-[cy:yyyy-MM-dd]-[SEQ_EG_CMP_ID]",
      idName: process.env.CMP_IDGEN_IDNAME || "campaign.number",
      idNameForUserNameGeneration: "username.name",
      formatForUserName: "USR-[SEQ_EG_USER_NAME]"
    },
    notCreateUserIfAlreadyThere: process.env.NOT_CREATE_USER_IF_ALREADY_THERE === "true",
    maxHttpRetries: process.env.MAX_HTTP_RETRIES || "4",
    autoRetryIfHttpError: process.env.AUTO_RETRY_IF_HTTP_ERROR || "socket hang up" ,
    latLongColumns: process.env.LAT_LONG_SUBSTRINGS || "HCM_ADMIN_CONSOLE_FACILITY_LATITUDE_OPTIONAL_MICROPLAN,HCM_ADMIN_CONSOLE_FACILITY_LONGITUDE_OPTIONAL_MICROPLAN,HCM_ADMIN_CONSOLE_TARGET_LAT_OPT,HCM_ADMIN_CONSOLE_TARGET_LONG_OPT",
    validateCampaignIdInMetadata: process.env.VALIDATE_CAMPAIGN_ID_IN_METADATA === "true",
  },
  resourceCreationConfig: {
    maxAttemptsForResourceCreationOrMapping: Number(process.env.MAX_RESOURCE_CREATION_ATTEMPTS || 200),

    waitTimeOfEachAttemptOfResourceCreationOrMappping: Number(process.env.WAIT_TIME_OF_EACH_ATTEMPT_MS || 40000),
  },
      // Excel ingestion configuration
    excelIngestion: {

    sheetFetchPageSize: process.env.EXCEL_INGESTION_PAGE_SIZE ? parseInt(process.env.EXCEL_INGESTION_PAGE_SIZE, 10) : 2000,
    persistenceStallTimeoutMs: process.env.EXCEL_INGESTION_PERSISTENCE_STALL_TIMEOUT_MS ? parseInt(process.env.EXCEL_INGESTION_PERSISTENCE_STALL_TIMEOUT_MS, 10) : 120000,
    persistencePollIntervalMs: process.env.EXCEL_INGESTION_PERSISTENCE_POLL_INTERVAL_MS ? parseInt(process.env.EXCEL_INGESTION_PERSISTENCE_POLL_INTERVAL_MS, 10) : 10000,
  }
};

if (!config.basesecret) {
  throw new Error('BASE_SECRET is undefined. Please set "BASE_SECRET" in env.');
}

export default config;