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

// Configuration object containing various environment variables
const config = {
  // Default sheet name for boundary data
  sheetName: process.env.BOUNDARY_MAIN_SHEET_NAME || "Boundary Data",
  // Default criteria for generating different tabs
  generateDifferentTabsOnBasisOf: process.env.SPLIT_BOUNDARIES_ON || "District",
  // default configurable number of data of boundary type on which generate different tabs
  numberOfBoundaryDataOnWhichWeSplit: process.env.SPLIT_BOUNDARIES_ON_LENGTH || "2",
  // Authentication token
  auth_token: process.env.AUTH_TOKEN,
  // Wait time for generic create
  waitTime: process.env.WAIT_FOR_GENERIC_CREATE || "30000",
  // Kafka broker host
  KAFKA_BROKER_HOST: process.env.KAFKA_BROKER_HOST || "kafka-v2.kafka-cluster:9092",
  // Kafka topics
  KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "save-project-campaign-details",
  KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "update-project-campaign-details",
  KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC: process.env.KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC || "update-campaign-details",
  KAFKA_CREATE_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC || "create-resource-details",
  KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC || "update-resource-details",
  KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC: process.env.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC || "create-resource-activity",
  KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC || "update-generated-resource-details",
  KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC: process.env.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC || "create-generated-resource-details",
  // Default hierarchy type
  hierarchyType: "NITISH",
  // Database configuration
  DB_USER: process.env.DB_USER || "postgres",
  DB_HOST: process.env.DB_HOST?.split(':')[0] || "localhost",
  DB_NAME: process.env.DB_NAME || "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD || "postgres",
  DB_PORT: process.env.DB_PORT || "5432",
  // Application configuration
  app: {
    port: parseInt(process.env.APP_PORT || "8080") || 8080,
    host: HOST,
    contextPath: process.env.CONTEXT_PATH || "/project-factory",
  },
  // Host configuration
  host: {
    serverHost: HOST,
    // URLs for various services
    mdms: process.env.EGOV_MDMS_HOST || "https://unified-dev.digit.org/",
    filestore: process.env.EGOV_FILESTORE_SERVICE_HOST || "https://unified-dev.digit.org/",
    projectFactoryBff: "http://localhost:8080/",
    idGenHost: process.env.EGOV_IDGEN_HOST || "https://unified-dev.digit.org/",
    facilityHost: process.env.EGOV_FACILITY_HOST || "https://unified-dev.digit.org/",
    boundaryHost: process.env.EGOV_BOUNDARY_HOST || "https://unified-dev.digit.org/",
    projectHost: process.env.EGOV_PROJECT_HOST || "https://unified-dev.digit.org/",
    userHost: process.env.EGOV_USER_HOST || "https://unified-dev.digit.org/",
    productHost: process.env.EGOV_PRODUCT_HOST || "https://unified-dev.digit.org/",
  },
  // Paths for different services
  paths: {
    filestore: process.env.FILE_STORE_SERVICE_END_POINT || "filestore/v1/files",
    mdms_search: process.env.EGOV_MDMS_SEARCH_ENDPOINT || "egov-mdms-service/v2/_search",
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
    facilityBulkCreate: process.env.EGOV_FACILITY_BULK_CREATE || "facility/v1/bulk/_create"
  },
  // Values configuration
  values: {
    parsingTemplate: "HCM.ParsingTemplate",
    transfromTemplate: "HCM.TransformTemplate",
    campaignType: "HCM.HCMTemplate",
    APIResource: "HCM.APIResourceTemplate3",
    idgen: {
      format: process.env.CMP_IDGEN_FORMAT || "CMP-[cy:yyyy-MM-dd]-[SEQ_EG_CMP_ID]",
      idName: process.env.CMP_IDGEN_IDNAME || "campaign.number"
    },
    matchFacilityData: false,
    retryCount: process.env.CREATE_RESOURCE_RETRY_COUNT || "3"
  },
  // Default search template
  SEARCH_TEMPLATE: "HCM.APIResourceTemplate3"
};

// Exporting getErrorCodes function and config object
export { getErrorCodes };
export default config;