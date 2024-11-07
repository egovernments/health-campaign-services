// Import necessary types and utilities
import { MDMSModels } from "../models";
import config from "../config";
import { httpRequest } from "../utils/request";

// Default request information for MDMS API requests
const defaultRequestInfo: any = {
  RequestInfo: {
    apiId: "PROJECTFACTORY", // Identifier for the calling application
  },
};

/**
 * Searches MDMS data via the v2 API for specific unique identifiers.
 *
 * @author jagankumar-egov
 * 
 * @param MdmsCriteria - The criteria for the MDMS v2 search, including tenantId and schemaCode.
 * @returns Promise resolving to the MDMS v2 search response containing matched data.
 */
const mdmsSearchDataViaV2Api = async (
  MdmsCriteria: MDMSModels.MDMSv2RequestCriteria
): Promise<MDMSModels.MDMSv2Response> => {
  // Construct the full API URL for the v2 MDMS search
  const apiUrl: string = config.host.mdmsV2 + config.paths.mdms_v2_search;

  // Prepare the data payload for the API request
  const data = {
    MdmsCriteria,
    ...defaultRequestInfo,
  };

  // Make an HTTP request to the MDMS v2 API
  const response: MDMSModels.MDMSv2Response = await httpRequest(apiUrl, data);

  // Return the response from the API
  return response;
};

/**
 * Fetches the schema definitions from MDMS based on specified criteria.
 * 
 * @author jagankumar-egov
 * 
 * @param SchemaDefCriteria - The criteria for fetching schema definitions, including tenantId and limit.
 * @returns Promise resolving to the response containing schema definitions.
 */
const mdmsSearchSchema = async (
  SchemaDefCriteria: MDMSModels.MDMSSchemaRequestCriteria
): Promise<MDMSModels.MDMSSchemaResponse> => {
  // Construct the request body including schema criteria and default request info
  const requestBody = {
    ...SchemaDefCriteria,
    ...defaultRequestInfo,
  };

  // Define the API URL for schema retrieval
  const url = config.host.mdmsV2 + config.paths.mdmsSchema;

  // Make an HTTP request with a tenant ID in headers
  const response: MDMSModels.MDMSSchemaResponse = await httpRequest(
    url,
    requestBody,
    { tenantId: SchemaDefCriteria?.SchemaDefCriteria?.tenantId }
  );

  // Return the schema definitions from the response
  return response;
};

/**
 * Searches MDMS data via the v1 API using given criteria.
 * 
 * @author jagankumar-egov
 * 
 * @param MdmsCriteria - The criteria for the MDMS v1 search, including tenantId and moduleDetails.
 * @returns Promise resolving to the MDMS v1 search response.
 */
const mdmsSearchDataViaV1Api = async (
  MdmsCriteria: MDMSModels.MDMSv1RequestCriteria
): Promise<MDMSModels.MDMSv1Response> => {
  // Construct the request body with v1 search criteria and default request info
  const requestBody = {
    ...MdmsCriteria,
    ...defaultRequestInfo,
  };

  // Define the API URL for MDMS v1 search
  const url = config.host.mdmsV2 + config.paths.mdms_v1_search;

  // Make an HTTP request with tenant ID in headers
  const response: MDMSModels.MDMSv1Response = await httpRequest(
    url,
    requestBody,
    { tenantId: MdmsCriteria.MdmsCriteria.tenantId }
  );

  // Return the search result from MDMS v1
  return response;
};

// Exporting all API functions for MDMS operations
export { mdmsSearchDataViaV2Api, mdmsSearchSchema, mdmsSearchDataViaV1Api };
