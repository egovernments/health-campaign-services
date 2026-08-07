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
  // Heartbeat + reconciler for boundary "process"/create runs abandoned by a pod restart.
  // The create runs as a detached in-process promise (boundaryApis.ts processCreate, no await); a
  // mid-run pod restart leaves the resource stuck at data-accepted forever and the UI polls it
  // indefinitely. While a create runs we tick lastModifiedTime (heartbeat, metadata only); a sweep
  // then marks a resource 'failed' once its ticks stop for stalenessMs (the owning pod died). A live
  // run keeps ticking, so it is never false-failed regardless of TOTAL run time — even a long 50k
  // create ticks every heartbeatMs through the (awaited) relationship fan-out and slow-persister
  // drain. stalenessMs therefore need only exceed the longest SYNCHRONOUS codegen burst (which blocks
  // the event loop and can delay a tick), NOT the whole run; the 10 min default is a large safety
  // margin for 50k+/slow pods. A truly dead run is marked failed ~stalenessMs after its pod died.
  orphanReconcile: {
    enabled: process.env.BOUNDARY_ORPHAN_RECONCILE_ENABLED !== "false", // default ON
    heartbeatMs: parseInt(process.env.BOUNDARY_RESOURCE_HEARTBEAT_MS || "30000"),      // tick every 30s
    stalenessMs: parseInt(process.env.BOUNDARY_ORPHAN_STALENESS_MS || "600000"),       // fail after ~10 min of no tick (20x heartbeat; safe for 50k+/slow pods)
    sweepIntervalMs: parseInt(process.env.BOUNDARY_ORPHAN_SWEEP_INTERVAL_MS || "60000"), // sweep every 60s
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
    // Optional blind settle after the upsert chunks, retained only as an escape hatch. The
    // consistency guarantee is the verify-gate below (read-back poll until every name is visible),
    // not this sleep — upserts are synchronous, so the first read normally already sees everything.
    localizationWaitTimeInBoundaryCreation: parseInt(process.env.LOCALIZATION_WAIT_TIME_IN_BOUNDARY_CREATION || "0"),
    localizationChunkSizeForBoundaryCreation: parseInt(process.env.LOCALIZATION_CHUNK_SIZE_FOR_BOUNDARY_CREATION || "2000"),
    localizationUpsertConcurrency: parseInt(process.env.LOCALIZATION_UPSERT_CONCURRENCY || "5"),
    // Verify-gate: after cache-burst, poll the read-back until all intended names are present,
    // bounded by the timeout. On timeout the run still completes with the localisationIncomplete
    // flag (unchanged semantics) — the gate only defers, it never fails the run.
    localisationVerifyPollIntervalMs: parseInt(process.env.LOCALISATION_VERIFY_POLL_INTERVAL_MS || "3000"),
    localisationVerifyTimeoutMs: parseInt(process.env.LOCALISATION_VERIFY_TIMEOUT_MS || "30000"),
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
    boundaryRelationshipBulkCreate: process.env.EGOV_BOUNDARY_RELATIONSHIP_BULK_CREATE || "boundary-service/boundary-relationships/bulk/_create",
  },
  // Values configuration
  values: {
    //module name
    unfrozeTillRow: process.env.UNFROZE_TILL_ROW || "5010",
    maxHttpRetries: process.env.MAX_HTTP_RETRIES || "4",
    // Transport-level errors that should auto-retry. "socket hang up" was the only one matched before;
    // a stale pooled keep-alive socket surfaces as "write EPIPE" / "(read )ECONNRESET", so cover those too.
    autoRetryIfHttpError: process.env.AUTO_RETRY_IF_HTTP_ERROR || "socket hang up,write EPIPE,read ECONNRESET,ECONNRESET,EPIPE" /* substring-matched against the failing error code */,
    // Idle timeout (ms) for pooled keep-alive sockets in the shared axios agent (see utils/request.ts).
    httpSocketIdleTimeoutMs: process.env.HTTP_SOCKET_IDLE_TIMEOUT_MS || "60000",
    // Max concurrent boundary-relationship creates within a single dependency wave (siblings).
    // Kept modest by default so parallel creates don't flood the asynchronous persister.
    relationshipCreateConcurrency: process.env.RELATIONSHIP_CREATE_CONCURRENCY || "10",
    // Chunk size for the boundary-service bulk relationship API (the last two hierarchy levels).
    bulkRelationshipChunkSize: process.env.BULK_RELATIONSHIP_CHUNK_SIZE || "100",
    // Transient-retry budget for /bulk/_create (records awaiting parent/entity persistence).
    bulkRelationshipRetryAttempts: process.env.BULK_RELATIONSHIP_RETRY_ATTEMPTS || "30",
    bulkRelationshipRetryDelayMs: process.env.BULK_RELATIONSHIP_RETRY_DELAY_MS || "2000",
    // Bounded gate before a bulk upload is marked completed: boundary-service's persister commits
    // relationships asynchronously, so the DB can lag the accepted creates by minutes at 50k scale.
    // The gate polls the relationship search until every intended relationship is visible; on
    // timeout the run still completes (the gate only defers "completed", it never fails the run).
    persistenceDrainTimeoutMs: process.env.PERSISTENCE_DRAIN_TIMEOUT_MS || "600000",
    persistenceDrainPollIntervalMs: process.env.PERSISTENCE_DRAIN_POLL_INTERVAL_MS || "5000",
    // Delay before the post-upload template regeneration is triggered. The persistence-drain gate
    // has already proven every relationship searchable by the time this is scheduled, so this only
    // buffers the redis prefix-delete / status-flip ordering — it is not a consistency wait.
    generateTriggerDelayMs: process.env.GENERATE_TRIGGER_DELAY_MS || "5000",
    validateCampaignIdInMetadata: process.env.VALIDATE_CAMPAIGN_ID_IN_METADATA === "true"
  },
};


export default config;