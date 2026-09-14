import { RequestInfo } from "../config/models/requestInfoSchema";
import { BoundaryModels, MDMSModels } from "../models";
import config from "../config";
import { defaultheader, httpRequest } from "../utils/request";
import { logger } from "../utils/logger";


/** Searches MDMS v2, optionally sending a cache-key header so repeated schema lookups are served from Redis. */
const searchMDMSDataViaV2Api = async (
  criteria: MDMSModels.MDMSv2RequestCriteria,
  cacheEnabled: boolean = false,
  requestInfo?: RequestInfo
): Promise<MDMSModels.MDMSv2Response> => {
  const apiUrl = `${config.host.mdmsV2}${config.paths.mdms_v2_search}`;

  const mdms = criteria?.MdmsCriteria || criteria;
  if (!mdms?.tenantId || !mdms?.schemaCode) {
    throw new Error("Invalid MDMS criteria: tenantId and schemaCode are required.");
  }

  const headers: Record<string, string> = { ...defaultheader };

  if (cacheEnabled) {
    const uniqueIdsPart = Array.isArray(mdms.uniqueIdentifiers)
      ? mdms.uniqueIdentifiers.join(",")
      : "";
    headers.cachekey = `mdmsv2Seacrh${mdms.tenantId}${uniqueIdsPart}${mdms.schemaCode}`;
  }

  const requestBody = {
    MdmsCriteria: mdms,
    RequestInfo: requestInfo
  };

  const response: MDMSModels.MDMSv2Response = await httpRequest(
    apiUrl,
    requestBody,
    undefined,
    undefined,
    undefined,
    headers
  );

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
  SchemaDefCriteria: MDMSModels.MDMSSchemaRequestCriteria,
  requestInfo?: RequestInfo
): Promise<MDMSModels.MDMSSchemaResponse> => {
  const requestBody = {
    ...SchemaDefCriteria,
    RequestInfo: requestInfo,
  };

  const url = config.host.mdmsV2 + config.paths.mdmsSchema;

  const response: MDMSModels.MDMSSchemaResponse = await httpRequest(
    url,
    requestBody,
    { tenantId: SchemaDefCriteria?.SchemaDefCriteria?.tenantId }
  );

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
  MdmsCriteria: MDMSModels.MDMSv1RequestCriteria,
  requestInfo?: RequestInfo
): Promise<MDMSModels.MDMSv1Response> => {
  const requestBody = {
    ...MdmsCriteria,
    RequestInfo: requestInfo,
  };

  const url = config.host.mdmsV2 + config.paths.mdms_v1_search;

  const response: MDMSModels.MDMSv1Response = await httpRequest(
    url,
    requestBody,
    { tenantId: MdmsCriteria.MdmsCriteria.tenantId }
  );

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
  requestInfo?: RequestInfo
): Promise<BoundaryModels.BoundaryEntityResponse> => {
  const requestBody = {
    RequestInfo: requestInfo,
  };

  const url = config.host.boundaryHost + config.paths.boundaryServiceSearch;

  const response: BoundaryModels.BoundaryEntityResponse = await httpRequest(
    url,
    requestBody,
    { tenantId, offset, limit, codes }
  );

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
  isCache?: boolean,
  codes?: string,
  requestInfo?: RequestInfo
): Promise<BoundaryModels.BoundaryHierarchyRelationshipResponse> => {
  const requestBody = {
    RequestInfo: requestInfo,
  };
  const headers: any = {
    ...defaultheader,
    ...(isCache && {
      cachekey: `boundaryRelationShipSearch${hierarchyType}${tenantId}${codes || ""}${includeChildren || ""}`,
    }),
  };

  const url = config.host.boundaryHost + config.paths.boundaryRelationship;
  const params = {
    tenantId,
    hierarchyType,
    includeChildren,
    includeParents,
    ...(codes && { codes })
  };

  const response: BoundaryModels.BoundaryHierarchyRelationshipResponse = await httpRequest(
    url,
    requestBody,
    params,
    undefined,
    undefined,
    headers
  );

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
  BoundaryTypeHierarchySearchCriteria: BoundaryModels.BoundaryHierarchyDefinitionSearchCriteria,
  requestInfo?: RequestInfo
): Promise<BoundaryModels.BoundaryHierarchyDefinitionResponse> => {
  const requestBody = {
    ...BoundaryTypeHierarchySearchCriteria,
    RequestInfo: requestInfo,
  };

  const url = config.host.boundaryHost + config.paths.boundaryHierarchy;

  const response: BoundaryModels.BoundaryHierarchyDefinitionResponse = await httpRequest(
    url,
    requestBody,
  );

  return response;
};



/** Resolves a filestore id to its downloadable URL via egov-filestore. */
const fetchFileFromFilestore = async (filestoreId: string, tenantId: string) => {

  try {
    const reqParamsForFetchingFile = {
      tenantId: tenantId,
      fileStoreIds: filestoreId
    };
    const fileResponse = await httpRequest(
      `${config?.host?.filestore}${config?.paths?.filestorefetch}`,
      {},
      reqParamsForFetchingFile,
      "get"
    );
    return fileResponse?.fileStoreIds?.[0].url;
  } catch (error) {
    console.error("Error fetching file URLs:", error);
    throw error;
  }
}

/**
 * Creates or updates MDMS data for a specific schema code in the given tenant.
 * 
 * 
 * @param tenantId - The unique identifier for the tenant.
 * @param schemaCode - The schema code for which data is being created or updated.
 * @param data - The data to be stored in MDMS.
 * @param useruuid - The UUID of the user performing the operation.
 * @returns Promise resolving when the MDMS data creation is complete.
 */
async function createMdmsData(
  tenantId: string,
  schemaCode: string,
  data: any,
  requestInfo: RequestInfo
): Promise<void> {
  const RequestInfo = requestInfo;

  const requestBody = {
    RequestInfo,
    Mdms: {
      tenantId,
      schemaCode,
      data
    },
  };

  const url = `${config?.host?.mdmsV2}${config?.paths?.mdms_v2_create}/${schemaCode}`;
  await httpRequest(url, requestBody);

  logger.info(`Created data for ${schemaCode} in MDMS for tenant ${tenantId}`);
}

export { searchMDMSDataViaV2Api, searchMDMSSchema, searchMDMSDataViaV1Api, searchBoundaryEntity, searchBoundaryRelationshipData, searchBoundaryRelationshipDefinition, fetchFileFromFilestore, createMdmsData };
