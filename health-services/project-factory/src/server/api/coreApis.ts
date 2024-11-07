// Import necessary types and utilities
import { BoundaryModels, MDMSModels } from "../models";
import config from "../config";
import { httpRequest } from "../utils/request";

// Default request information for MDMS API requests
const defaultRequestInfo: any = {
  RequestInfo: {
    apiId: "PROJECTFACTORY", // Identifier for the calling application,
    ...(config.isProduction && config.token && { authToken :config.token}),
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
const searchMDMSDataViaV2Api = async (
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
const searchMDMSSchema = async (
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
const searchMDMSDataViaV1Api = async (
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


/**
 * Searches boundary entities in the MDMS system using specified criteria.
 * 
 * @author jagankumar-egov
 * 
 * @function searchBoundaryEntity
 * @param tenantId - Unique identifier for the tenant.
 * @param codes - Specific codes to filter the boundary entities.
 * @param limit - Maximum number of results to return (default is 100).
 * @param offset - Starting position for fetching results (default is 0).
 * @returns Promise resolving to the boundary entity search response.
 * 
 * @remarks
 * This function constructs and sends a request to the boundary entity service,
 * using the provided criteria to filter and retrieve specific boundary entities.
 * Additional headers contain tenant ID, offset, limit, and codes for filtering.
 * 
 * @example
 * const response = await searchBoundaryEntity("mz", "MOZ", 50, 0);
 */
const searchBoundaryEntity = async (
  tenantId: string,
  codes: string,
  limit: number = 100,
  offset: number = 0,
): Promise<BoundaryModels.BoundaryEntityResponse> => {
  // Prepare request body with default request information
  const requestBody = {
    ...defaultRequestInfo,
  };

  // Construct API URL for boundary entity search
  const url = config.host.boundaryHost + config.paths.boundaryServiceSearch;

  // Execute HTTP request with tenant ID, offset, limit, and codes in headers
  const response: BoundaryModels.BoundaryEntityResponse = await httpRequest(
    url,
    requestBody,
    { tenantId, offset, limit, codes }
  );

  // Return the response containing boundary entity data
  return response;
};

/**
 * Searches boundary hierarchy relationship data within the MDMS system.
 * 
 * @author jagankumar-egov
 * 
 * @function searchBoundaryRelationshipData
 * @param tenantId - Unique identifier for the tenant.
 * @param hierarchyType - Type of hierarchy to search within.
 * @param includeChildren - Whether to include child relationships (default is true).
 * @param includeParents - Whether to include parent relationships (default is true).
 * @returns Promise resolving to the boundary hierarchy relationship response.
 * 
 * @remarks
 * This function queries the boundary relationship API to retrieve hierarchy data
 * based on the specified hierarchy type and inclusion of child or parent entities.
 * 
 * @example
 * const response = await searchBoundaryRelationshipData("mz", "ADMIN", true, false);
 */
const searchBoundaryRelationshipData = async (
  tenantId: string,
  hierarchyType: string,
  includeChildren: boolean = true,
  includeParents: boolean = true,
): Promise<BoundaryModels.BoundaryHierarchyRelationshipResponse> => {
  // Prepare request body with default request information
  const requestBody = {
    ...defaultRequestInfo,
  };

  // Construct API URL for boundary hierarchy relationship search
  const url = config.host.boundaryHost + config.paths.boundaryRelationship;

  // Execute HTTP request with tenant ID, hierarchy type, and inclusion flags in headers
  const response: BoundaryModels.BoundaryHierarchyRelationshipResponse = await httpRequest(
    url,
    requestBody,
    { tenantId, hierarchyType, includeChildren, includeParents }
  );

  // Return the response containing boundary relationship data
  return response;
};

/**
 * Searches boundary hierarchy definitions based on provided search criteria.
 * 
 * @author jagankumar-egov
 * 
 * @function searchBoundaryRelationshipDefinition
 * @param BoundaryTypeHierarchySearchCriteria - Criteria for fetching boundary hierarchy definitions.
 * @returns Promise resolving to the boundary hierarchy definition response.
 * 
 * @remarks
 * This function sends a request to retrieve hierarchy definitions for boundary types,
 * based on specified criteria such as tenant ID and hierarchy parameters.
 * 
 * @example
 * const criteria = { tenantId: "mz", hierarchyCode: "ADMIN" };
 * const response = await searchBoundaryRelationshipDefinition(criteria);
 */
const searchBoundaryRelationshipDefinition = async (
  BoundaryTypeHierarchySearchCriteria: BoundaryModels.BoundaryHierarchyDefinitionSearchCriteria
): Promise<BoundaryModels.BoundaryHierarchyDefinitionResponse> => {
  // Prepare request body with search criteria and default request information
  const requestBody = {
    ...BoundaryTypeHierarchySearchCriteria,
    ...defaultRequestInfo,
  };

  // Construct API URL for boundary hierarchy definition search
  const url = config.host.boundaryHost + config.paths.boundaryHierarchy;

  // Execute HTTP request to fetch boundary hierarchy definitions
  const response: BoundaryModels.BoundaryHierarchyDefinitionResponse = await httpRequest(
    url,
    requestBody,
  );

  // Return the response containing hierarchy definition data
  return response;
};

// Exporting all API functions for MDMS operations
export { searchMDMSDataViaV2Api, searchMDMSSchema, searchMDMSDataViaV1Api ,searchBoundaryEntity, searchBoundaryRelationshipData, searchBoundaryRelationshipDefinition };
