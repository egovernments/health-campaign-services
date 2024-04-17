// config.js
// const env = process.env.NODE_ENV; // 'dev' or 'test'

import { getErrorCodes } from "./constants";

const HOST = process.env.EGOV_HOST ||
  // "http://127.0.0.1:8080/" ||
  "https://unified-dev.digit.org/";

if (!HOST) {
  console.log("You need to set the HOST variable");
  process.exit(1);
}

const config = {
  auth_token: process.env.AUTH_TOKEN,
  waitTime: process.env.WAIT_FOR_GENERIC_CREATE || "30000",
  KAFKA_BROKER_HOST:
    // "localhost:9092" ||
    // "localhost:9093" ||
    process.env.KAFKA_BROKER_HOST || "kafka-v2.kafka-cluster:9092",
  KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC:
    process.env.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "save-project-campaign-details",
  KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC:
    process.env.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC || "update-project-campaign-details",
  KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC:
    process.env.KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC || "update-campaign-details",
  KAFKA_CREATE_RESOURCE_DETAILS_TOPIC:
    process.env.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC || "create-resource-details",
  KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC:
    process.env.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC || "create-resource-activity",
  KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC:
    process.env.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC || "update-generated-resource-details",
  KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC:
    process.env.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC || "create-generated-resource-details",
  hierarchyType: "NITISH",
  DB_USER:
    process.env.DB_USER || "postgres",
  DB_HOST: process.env.DB_HOST?.split(':')[0] || "localhost",
  DB_NAME: process.env.DB_NAME || "postgres",
  DB_PASSWORD: process.env.DB_PASSWORD || "postgres",
  DB_PORT: process.env.DB_PORT || "5432",
  app: {
    port: parseInt(process.env.APP_PORT || "8080") || 8080,
    host: HOST,
    contextPath: process.env.CONTEXT_PATH || "/project-factory",
  },
  host: {
    serverHost: HOST,
    mdms: process.env.EGOV_MDMS_HOST
      // || "http://localhost:8084/"
      || "https://unified-dev.digit.org/",
    filestore: process.env.EGOV_FILESTORE_SERVICE_HOST
      // || "http://localhost:8083/"
      || "https://unified-dev.digit.org/",
    projectFactoryBff: "http://localhost:8080/",
    idGenHost: process.env.EGOV_IDGEN_HOST
      // || "http://localhost:8085/"
      || "https://unified-dev.digit.org/",
    facilityHost: process.env.EGOV_FACILITY_HOST
      // || "http://localhost:8086/"
      || "https://unified-dev.digit.org/",
    boundaryHost: process.env.EGOV_BOUNDARY_HOST
      // || "http://localhost:8087/"
      || "https://unified-dev.digit.org/",
    projectHost: process.env.EGOV_PROJECT_HOST
      // || "http://localhost:8088/"
      || "https://unified-dev.digit.org/",
    userHost: process.env.EGOV_USER_HOST
      // || "http://localhost:8089/"
      || "https://unified-dev.digit.org/",
    productHost: process.env.EGOV_PRODUCT_HOST
      // || "http://localhost:8090/"
      || "https://unified-dev.digit.org/",

  },
  paths: {
    filestore: process.env.FILE_STORE_SERVICE_END_POINT
      || "filestore/v1/files",
    mdms_search: process.env.EGOV_MDMS_SEARCH_ENDPOINT
      || "egov-mdms-service/v2/_search",
    // mdms_search: "mdms-v2/v2/_search",
    idGen: process.env.EGOV_IDGEN_PATH
      || "egov-idgen/id/_generate",
    mdmsSchema: process.env.EGOV_MDMS_SCHEMA_PATH
      || "egov-mdms-service/schema/v1/_search",
    boundaryRelationship: process.env.EGOV_BOUNDARY_RELATIONSHIP_SEARCHPATH
      || "boundary-service/boundary-relationships/_search",
    boundaryServiceSearch: process.env.EGOV_BOUNDARY_SERVICE_SEARCHPATH
      || "boundary-service/boundary/_search",
    boundaryHierarchy: process.env.EGOV_BOUNDARY_HIERARCHY_SEARCHPATH
      || "boundary-service/boundary-hierarchy-definition/_search",
    projectCreate: process.env.HEALTH_PROJECT_CREATE_PATH
      || "health-project/v1/_create",
    projectUpdate: process.env.HEALTH_PROJECT_UPDATE_PATH
      || "health-project/v1/_update",
    projectSearch: process.env.HEALTH_PROJECT_SEARCH_PATH
      || "health-project/v1/_search",
    staffCreate: process.env.EGOV_PROJECT_STAFF_CREATE_PATH
      || "health-project/staff/v1/_create",
    projectResourceCreate: process.env.EGOV_PROJECT_RESOURCE_CREATE_PATH
      || "health-project/resource/v1/_create",
    projectFacilityCreate: process.env.EGOV_PROJECT_RESOURCE_FACILITY_PATH
      || "health-project/facility/v1/_create",
    userSearch: process.env.EGOV_USER_SEARCH_PATH
      || "user/_search",
    facilitySearch: process.env.EGOV_FACILITY_SEARCH_PATH
      || "facility/v1/_search",
    productVariantSearch: process.env.EGOV_PRODUCT_VARIANT_SEARCH_PATH
      || "product/variant/v1/_search",
    boundaryEntity: process.env.EGOV_BOUNDARY_ENTITY_SEARCHPATH
      || "boundary-service/boundary/_search",
    facilityBulkCreate: process.env.EGOV_FACILITY_BULK_CREATE
      || "facility/v1/bulk/_create"
  },
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
  SEARCH_TEMPLATE: "HCM.APIResourceTemplate3"
};

export { getErrorCodes };
export default config;
