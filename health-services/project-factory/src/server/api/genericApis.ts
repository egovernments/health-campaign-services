import { RequestInfo } from "../config/models/requestInfoSchema";
import config from "../config";
import FormData from "form-data";
import { defaultheader, httpRequest } from "../utils/request";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { getDataSheetReady, getLocalizedHeaders,throwError } from "../utils/genericUtils";
import { generateFilteredBoundaryData, getConfigurableColumnHeadersBasedOnCampaignType, getFiltersFromCampaignSearchResponse, getLocalizedName, processDataForTargetCalculation } from '../utils/campaignUtils';
import { getCampaignSearchResponse, getHierarchy } from './campaignApis';
const _ = require('lodash');
import { enrichTemplateMetaData, getExcelWorkbookFromFileURL } from "../utils/excelUtils";
import { searchBoundaryRelationshipData, searchMDMSDataViaV2Api } from "./coreApis";
import { getLocaleFromRequestInfo } from "../utils/localisationUtils";

// Dedicated axios instance for filestore uploads — isolated connection pool,
// never shared with attendance or other service calls, preventing pool poisoning
const filestoreAxiosInstance = require("axios").default.create({
  timeout: 0,
  maxContentLength: Infinity,
  maxBodyLength: Infinity,
});
import { MDMSModels } from "../models";

const getTargetWorkbook = async (fileUrl: string, localizationMap?: any) => {
  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, "");

  const mainSheetName = workbook.getWorksheet(1).name;
  const localizedMainSheet = getLocalizedName(mainSheetName, localizationMap);

  if (!workbook.getWorksheet(localizedMainSheet)) {
    throwError(
      "FILE",
      400,
      "INVALID_SHEETNAME",
      `Sheet with name "${localizedMainSheet}" is not present in the file.`
    );
  }

  return workbook;
};

/** Converts a raw 2D sheet array into row objects keyed by the header row. */
export function getJsonData(sheetData: any, getRow = false, getSheetName = false, sheetName = "sheet1") {
  const jsonData: any[] = [];
  const headers = sheetData[0];

  for (let i = 1; i < sheetData.length; i++) {
    const rowData: any = {};
    const row = sheetData[i];
    if (row) {
      for (let j = 0; j < headers.length; j++) {
        const key = headers[j];
        const value = row[j] === undefined || row[j] === "" ? "" : row[j];
        if (value || value === 0) {
          rowData[key] = value;
        }
      }
      if (Object.keys(rowData).length > 0) {
        if (getRow) rowData["!row#number!"] = i + 1;
        if (getSheetName) rowData["!sheet#name!"] = sheetName;
        jsonData.push(rowData);
      }
    }
  };
  return jsonData;
}

/** Like getJsonData but starts at row index 2, skipping the extra unlocalised-key header row these sheets carry. */
export function getJsonDataWithUnlocalisedKey(sheetData: any, getRow = false, getSheetName = false, sheetName = "sheet1") {
  const jsonData: any[] = [];
  const headers = sheetData[0];

  for (let i = 2; i < sheetData.length; i++) {
    const rowData: any = {};
    const row = sheetData[i];
    if (row) {
      for (let j = 0; j < headers.length; j++) {
        const key = headers[j];
        const value = row[j] === undefined || row[j] === "" ? "" : row[j];
        if (value || value === 0) {
          rowData[key] = value;
        }
      }
      if (Object.keys(rowData).length > 0) {
        if (getRow) rowData["!row#number!"] = i + 1;
        if (getSheetName) rowData["!sheet#name!"] = sheetName;
        jsonData.push(rowData);
      }
    }
  };
  return jsonData;
}

function getSheetDataFromWorksheet(worksheet: any) {
  var sheetData: any[][] = [];

  worksheet?.eachRow({ includeEmpty: true }, (row: any, rowNumber: any) => {
    const rowData: any[] = [];

    row.eachCell({ includeEmpty: true }, (cell: any, colNumber: any) => {
      const cellValue = getRawCellValue(cell);
      rowData[colNumber - 1] = typeof cellValue === 'string' ? cellValue.trim() : cellValue; // ExcelJS colNumber is 1-based; store 0-based
    });

    if (rowData.some(value => value !== null && value !== undefined)) {
      sheetData[rowNumber - 1] = rowData; // ExcelJS rowNumber is 1-based; store 0-based
    }
  });
  return sheetData;
}

const getSheetData = async (
  fileUrl: string,
  sheetName: string,
  getRow = false,
  createAndSearchConfig?: any,
  localizationMap?: { [key: string]: string }
) => {
  const localizedSheetName = getLocalizedName(sheetName, localizationMap);
  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedSheetName);

  const worksheet: any = workbook.getWorksheet(localizedSheetName);

  const sheetData = getSheetDataFromWorksheet(worksheet);
  const jsonData = getJsonData(sheetData, getRow);
  return jsonData;
};

/** Extracts a plain scalar from an ExcelJS cell, unwrapping richText/hyperlink/formula/error/date object forms. */
function getRawCellValue(cell: any) {
  if (cell.value && typeof cell.value === 'object') {
    if ('richText' in cell.value) {
      return cell.value.richText.map((rt: any) => rt.text).join('');
    }
    else if ('hyperlink' in cell.value) {
      if (cell?.value?.text?.richText?.length > 0) {
        return cell.value.text.richText.map((t: any) => t.text).join('');
      }
      else {
        return cell.value.text;
      }
    }
    else if ('formula' in cell.value) {
      return cell.value.result;
    }
    else if ('sharedFormula' in cell.value) {
      return cell.value.result;
    }
    else if ('error' in cell.value) {
      return cell.value.error;
    } else if (cell.value instanceof Date) {
      return cell.value.toISOString();
    }
    else {
      return cell.value;
    }
  }
  return cell.value;
}

const getTargetSheetData = async (
  fileUrl: string,
  getRow = false,
  getSheetName = false,
  localizationMap?: any
) => {
  const workbook = await getTargetWorkbook(fileUrl, localizationMap);
  const sheetNames: string[] = [];
  workbook.eachSheet((worksheet: any) => {
    sheetNames.push(worksheet.name);
  });
  const localizedSheetNames = getLocalizedHeaders(sheetNames, localizationMap);

  const workbookData: { [key: string]: any[] } = {};

  for (const sheetName of localizedSheetNames) {
    const worksheet = workbook.getWorksheet(sheetName);
    const sheetData = getSheetDataFromWorksheet(worksheet);
    workbookData[sheetName] = getJsonData(sheetData, getRow, getSheetName, sheetName);
  }
  return workbookData;
};

const getTargetSheetDataAfterCode = async (
  request: any,
  fileUrl: string,
  getRow = false,
  getSheetName = false,
  codeColumnName = "Boundary Code",
  localizationMap?: any
) => {
  const workbook = await getTargetWorkbook(fileUrl, localizationMap);
  const sheetNames: string[] = [];
  workbook.eachSheet((worksheet: any) => {
    sheetNames.push(worksheet.name);
  });
  const localizedSheetNames = getLocalizedHeaders(sheetNames, localizationMap);

  const workbookData: { [key: string]: any[] } = {};

  for (const sheetName of localizedSheetNames) {
    const worksheet = workbook.getWorksheet(sheetName);
    const sheetData = getSheetDataFromWorksheet(worksheet);
    const jsonData = getJsonData(sheetData, true, true, sheetName);

    // Locate the boundary-code column by matching the header row against codeColumnName
    const firstRow = sheetData[0];
    let boundaryCodeColumnIndex = -1;
    for (let colIndex = 1; colIndex < firstRow.length; colIndex++) {
      if (firstRow[colIndex] === codeColumnName) {
        boundaryCodeColumnIndex = colIndex;
        break;
      }
    }

    if (boundaryCodeColumnIndex === -1) {
      console.warn(`Column "${codeColumnName}" not found in sheet "${sheetName}".`);
      continue;
    }
    const processedData = await processDataForTargetCalculation(request, jsonData, codeColumnName, localizationMap);

    workbookData[sheetName] = processedData;
  }

  return workbookData;
};


const searchMDMS: any = async (
  uniqueIdentifiers: any[],
  schemaCode: string,
  requestinfo: any
) => {
  if (!uniqueIdentifiers) {
    return;
  }

  const apiUrl = config.host.mdmsV2 + config.paths.mdms_v2_search;

  const data = {
    MdmsCriteria: {
      tenantId: requestinfo?.userInfo?.tenantId,
      uniqueIdentifiers: uniqueIdentifiers,
      schemaCode: schemaCode,
    },
    RequestInfo: requestinfo,
  };

  const result = await httpRequest(
    apiUrl,
    data,
    undefined,
    undefined,
    undefined
  );

  logger.info("Template search Result : " + JSON.stringify(result));

  return result;
};

const getCampaignNumber: any = async (
  requestBody: any,
  idFormat: String,
  idName: string,
  tenantId: string
) => {
  const data = {
    RequestInfo: requestBody?.RequestInfo,
    idRequests: [
      {
        idName: idName,
        tenantId: tenantId,
        format: idFormat,
      },
    ],
  };

  const idGenUrl = config.host.idGenHost + config.paths.idGen;

  const result = await httpRequest(
    idGenUrl,
    data,
    undefined,
    undefined,
    undefined,
    undefined
  );

  if (result?.idResponses?.[0]?.id) {
    return result?.idResponses?.[0]?.id;
  }

  throwError("COMMON", 500, "IDGEN_ERROR");
};

const getSchema: any = async (code: string, RequestInfo: any) => {
  const data = {
    RequestInfo,
    SchemaDefCriteria: {
      tenantId: RequestInfo?.userInfo?.tenantId,
      limit: 200,
      codes: [code],
    },
  };
  const mdmsSearchUrl = config.host.mdmsV2 + config.paths.mdmsSchema;

  try {
    const result = await httpRequest(
      mdmsSearchUrl,
      data,
      undefined,
      undefined,
      undefined,
      undefined
    );
    return result?.SchemaDefinitions?.[0]?.definition;
  } catch (error: any) {
    logger.error("Error: " + error);
    return error;
  }
};

const getCount: any = async (
  responseData: any,
  request: any,
  response: any
) => {
  try {
    const host = responseData?.host;
    const url = responseData?.searchConfig?.countUrl;

    const requestInfo = { RequestInfo: request?.body?.RequestInfo };

    const result = await httpRequest(
      host + url,
      requestInfo,
      undefined,
      undefined,
      undefined,
      undefined
    );

    const count = _.get(result, responseData?.searchConfig?.countPath);

    return count;
  } catch (error: any) {
    logger.error("Error: " + error);
    throw error;
  }
};

/** Serializes the workbook and uploads it to filestore, retrying transient failures up to config.values.maxHttpRetries. */
async function createAndUploadFile(
  updatedWorkbook: any,
  request: any,
  tenantId?: any
) {
  if (request?.body?.RequestInfo && request?.query?.campaignId) {
    enrichTemplateMetaData(updatedWorkbook, getLocaleFromRequestInfo(request?.body?.RequestInfo), request?.query?.campaignId);
  }
  const buffer = await updatedWorkbook.xlsx.writeBuffer();

  const maxAttempts = parseInt(config.values.maxHttpRetries) || 4;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const formData = new FormData();
    formData.append("file", buffer, "filename.xlsx");
    formData.append("tenantId", tenantId ? tenantId : request?.body?.RequestInfo?.userInfo?.tenantId);
    formData.append("module", "HCM-ADMIN-CONSOLE-SERVER");
    try {
      logger.info(`FILESTORE :: REQUEST :: ${config.host.filestore + config.paths.filestore}`);
      const response = await filestoreAxiosInstance.post(
        config.host.filestore + config.paths.filestore,
        formData,
        {
          headers: {
            ...formData.getHeaders(),
            "auth-token": request?.body?.RequestInfo?.authToken || request?.RequestInfo?.authToken,
          },
        }
      );
      const responseData = response?.data?.files;
      if (responseData) return responseData;
    } catch (error: any) {
      const apiError = error?.response?.data?.Errors?.[0]?.message;
      const msg = apiError || error?.message;
      const code = error?.code || error?.response?.status || "unknown";
      const stackLine = error?.stack?.split("\n")[1]?.trim() || "";
      logger.warn(`FILESTORE :: ATTEMPT ${attempt}/${maxAttempts} FAILED :: code=${code} :: ${msg} :: ${stackLine}`);
      if (attempt === maxAttempts) throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
    }
  }
  throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
}

export async function createAndUploadFileWithOutRequest(
  updatedWorkbook: any,
  tenantId: any
) {
  logger.info("Creating form data for file upload...");
  const buffer = await updatedWorkbook.xlsx.writeBuffer();
  logger.info("Form data created.");

  const maxAttempts = parseInt(config.values.maxHttpRetries) || 4;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const formData = new FormData();
    formData.append("file", buffer, "filename.xlsx");
    formData.append("tenantId", tenantId);
    formData.append("module", "HCM-ADMIN-CONSOLE-SERVER");
    try {
      logger.info(`FILESTORE :: REQUEST :: ${config.host.filestore + config.paths.filestore}`);
      const response = await filestoreAxiosInstance.post(
        config.host.filestore + config.paths.filestore,
        formData,
        { headers: { ...formData.getHeaders() } }
      );
      const responseData = response?.data?.files;
      if (responseData) return responseData;
    } catch (error: any) {
      const apiError = error?.response?.data?.Errors?.[0]?.message;
      const msg = apiError || error?.message;
      const code = error?.code || error?.response?.status || "unknown";
      const stackLine = error?.stack?.split("\n")[1]?.trim() || "";
      logger.warn(`FILESTORE :: ATTEMPT ${attempt}/${maxAttempts} FAILED :: code=${code} :: ${msg} :: ${stackLine}`);
      if (attempt === maxAttempts) throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
    }
  }
  throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
}


function generateHierarchyList(data: any[], parentChain: any = []) {
  let result: any[] = [];

  for (let boundary of data) {
    let currentChain = [...parentChain, boundary.code];
    result.push(currentChain.join(","));

    if (boundary.children.length > 0) {
      let childResults = generateHierarchyList(boundary.children, currentChain);
      result = result.concat(childResults);
    }
  }
  return result;
}

/** Flattens boundary type→parent relationships into a top-down ordered list of boundary types. */
function generateHierarchy(boundaries: any[]) {
  const parentMap: any = {};

  for (const boundary of boundaries) {
    parentMap[boundary.boundaryType] = boundary.parentBoundaryType;
  }

  const hierarchyList = [];
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === null) {
        hierarchyList.push(boundaryType);
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
  return hierarchyList;
}

function traverseChildren(parent: any, parentMap: any, hierarchyList: any[]) {
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === parent) {
        hierarchyList.push(boundaryType);
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
}

async function createExcelSheet(data: any, headers: any) {
  var rows = [headers, ...data];
  return rows;
}


/**
 * Asynchronously retrieves boundary sheet data based on the provided request.
 * @param request The HTTP request object.
 * @returns Boundary sheet data.
 */
async function getBoundarySheetData(
  request: any,
  localizationMap?: { [key: string]: string },
  useCache?:boolean
) {
  const hierarchyType = request?.query?.hierarchyType;
  const tenantId = request?.query?.tenantId;
  logger.info(
    `processing boundary data generation for hierarchyType : ${hierarchyType}`
  );
  const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(tenantId, hierarchyType, true, true,useCache);
  const boundaryData = boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
  if (!boundaryData || boundaryData.length === 0) {
    logger.info(`boundary data not found for hierarchyType : ${hierarchyType}`);
    const hierarchy = await getHierarchy(
      request?.query?.tenantId,
      hierarchyType
    );
    const modifiedHierarchy = hierarchy.map((ele) =>
      `${hierarchyType}_${ele}`.toUpperCase()
    );
    const localizedHeadersUptoHierarchy = getLocalizedHeaders(
      modifiedHierarchy,
      localizationMap
    );
    var headerColumnsAfterHierarchy;
    headerColumnsAfterHierarchy = await getConfigurableColumnHeadersBasedOnCampaignType(request, localizationMap);

    const headers = [...localizedHeadersUptoHierarchy, ...headerColumnsAfterHierarchy];
    // create empty sheet if no boundary present in system
    logger.info(`generated a empty template for boundary`);
    return await createExcelSheet(
      boundaryData,
      headers
    );
  } else {
    let Filters: any = {};
    if (request?.body?.Filters && request?.body?.Filters.boundaries && Array.isArray(request?.body?.Filters.boundaries) && request?.body?.Filters.boundaries.length > 0) {
      Filters = {
        Filters: {
          boundaries: request.body.Filters.boundaries.map((boundary: any) => ({
            ...boundary,
            boundaryType: boundary.type
          }))
        }
      };
    }
    const responseFromCampaignSearch =
      await getCampaignSearchResponse(request);
    Filters = await getFiltersFromCampaignSearchResponse(request, responseFromCampaignSearch)
    
    if (Filters?.Filters && Filters.Filters.boundaries && Array.isArray(Filters.Filters.boundaries) && Filters.Filters.boundaries.length > 0) {
      const filteredBoundaryData = await generateFilteredBoundaryData(
        request,
        Filters
      );
      return await getDataSheetReady(
        filteredBoundaryData,
        request,
        localizationMap
      );
    }
    else {
      return await getDataSheetReady(boundaryData, request, localizationMap);
    }
  }
}

/** Creates a single project-staff mapping via health-project. */
export async function createStaff(resouceBody: any) {
  const staffCreateUrl =
    `${config.host.projectHost}` + `${config.paths.staffCreate}`;
  logger.info("Project staff Creation : API :" + config.paths.staffCreate);

  const staffResponse = await httpRequest(
    staffCreateUrl,
    resouceBody,
    undefined,
    "post",
    undefined,
    undefined,
    undefined,
    false
  );
  logger.info("Project Staff mapping created");
  logger.debug(
    "Project Staff mapping response " +
    getFormattedStringForDebug(staffResponse)
  );
  return staffResponse;
}

/**
 * Asynchronously creates project resources based on the provided resource body.
 * @param resouceBody The resource body.
 */
export async function createProjectResource(resouceBody: any) {
  const projectResourceCreateUrl =
    `${config.host.projectHost}` + `${config.paths.projectResourceCreate}`;
  logger.info("Project Resource Creation : API : " + config.paths.projectResourceCreate);

  const projectResourceResponse = await httpRequest(
    projectResourceCreateUrl,
    resouceBody,
    undefined,
    "post",
    undefined,
    undefined,
    undefined,
    false
  );
  logger.debug("Project Resource Created");
  logger.debug(
    "Project Resource Creation response :: " +
    getFormattedStringForDebug(projectResourceResponse)
  );
  return projectResourceResponse;
}

/**
 * Asynchronously creates project facilities based on the provided resource body.
 * @param resouceBody The resource body.
 */
export async function createProjectFacility(resouceBody: any) {
  const projectFacilityCreateUrl =
    `${config.host.projectHost}` + `${config.paths.projectFacilityCreate}`;
  logger.info("Project Facility Creation  : API :" + config.paths.projectFacilityCreate);

  const projectFacilityResponse = await httpRequest(
    projectFacilityCreateUrl,
    resouceBody,
    undefined,
    "post",
    undefined,
    undefined,
    undefined,
    false
  );
  logger.info("Project Facility Created");
  logger.debug(
    "Project Facility Creation response" +
    getFormattedStringForDebug(projectFacilityResponse)
  );
  return projectFacilityResponse;
}

/**
 * Bulk project-staff/facility/resource create. These endpoints are async: the server
 * responds 202 without ids and generates ids in a downstream consumer, so callers must
 * confirm-by-search afterwards to record the real mappingId — never trust a client-side id.
 */
async function bulkCreateProjectMappings(path: string, bodyKey: string, entities: any[], requestInfo: RequestInfo): Promise<void> {
  if (entities.length === 0) return;
  const url = `${config.host.projectHost}${path}`;
  logger.info(`Project mapping bulk create : API : ${path} :: ${entities.length} entities`);
  await httpRequest(url, { RequestInfo: requestInfo, [bodyKey]: entities }, undefined, "post", undefined, undefined, undefined, false);
}

export async function createStaffBulk(entities: any[], requestInfo: RequestInfo): Promise<void> {
  return bulkCreateProjectMappings(config.paths.staffBulkCreate, "ProjectStaff", entities, requestInfo);
}

export async function createProjectFacilityBulk(entities: any[], requestInfo: RequestInfo): Promise<void> {
  return bulkCreateProjectMappings(config.paths.projectFacilityBulkCreate, "ProjectFacilities", entities, requestInfo);
}

export async function createProjectResourceBulk(entities: any[], requestInfo: RequestInfo): Promise<void> {
  return bulkCreateProjectMappings(config.paths.projectResourceBulkCreate, "ProjectResources", entities, requestInfo);
}

async function searchProjectMappingsByProjects(
  path: string,
  bodyKey: string,
  responseKey: string,
  extractKey: (item: any) => string | undefined,
  projectIds: string[],
  tenantId: string,
  requestInfo: RequestInfo,
  entityFilter?: { field: string; ids: string[] }
): Promise<Map<string, string>> {
  const result = new Map<string, string>();
  const distinctProjectIds = Array.from(new Set(projectIds.filter(Boolean)));
  if (distinctProjectIds.length === 0) return result;

  const distinctEntityIds = entityFilter ? Array.from(new Set(entityFilter.ids.filter(Boolean))) : [];
  const entityCriteria = entityFilter && distinctEntityIds.length > 0 ? { [entityFilter.field]: distinctEntityIds } : {};

  const url = `${config.host.projectHost}${path}`;
  const CHUNK_SIZE = config.mapping.projectSearchChunkSize;
  const PAGE_SIZE = config.mapping.searchPageSize;

  for (let i = 0; i < distinctProjectIds.length; i += CHUNK_SIZE) {
    const chunk = distinctProjectIds.slice(i, i + CHUNK_SIZE);
    let offset = 0;
    while (true) {
      const response = await httpRequest(
        url,
        { RequestInfo: requestInfo, [bodyKey]: { projectId: chunk, ...entityCriteria } },
        { tenantId, limit: PAGE_SIZE, offset, includeDeleted: false }
      );
      const items: any[] = response?.[responseKey] || [];
      for (const item of items) {
        const key = extractKey(item);
        if (key && item?.id) result.set(key, item.id);
      }
      if (items.length < PAGE_SIZE) break;
      offset += PAGE_SIZE;
    }
  }
  return result;
}

/**
 * Resource search has no productVariantId filter server-side; projectId-only is acceptable
 * because resource cardinality per project is low.
 */
export async function searchProjectResourcesByProjects(
  projectIds: string[],
  tenantId: string,
  requestInfo: RequestInfo
): Promise<Map<string, string>> {
  return searchProjectMappingsByProjects(
    config.paths.projectResourceSearch,
    "ProjectResource",
    "ProjectResources",
    (item: any) => item?.resource?.productVariantId && item?.projectId ? `${item.resource.productVariantId}|${item.projectId}` : undefined,
    projectIds,
    tenantId,
    requestInfo
  );
}

export async function searchProjectFacilitiesByProjects(
  projectIds: string[],
  tenantId: string,
  requestInfo: RequestInfo,
  facilityIds: string[] = []
): Promise<Map<string, string>> {
  return searchProjectMappingsByProjects(
    config.paths.projectFacilitySearch,
    "ProjectFacility",
    "ProjectFacilities",
    (item: any) => item?.facilityId && item?.projectId ? `${item.facilityId}|${item.projectId}` : undefined,
    projectIds,
    tenantId,
    requestInfo,
    { field: "facilityId", ids: facilityIds }
  );
}

export async function searchProjectStaffByProjects(
  projectIds: string[],
  tenantId: string,
  requestInfo: RequestInfo,
  staffIds: string[] = []
): Promise<Map<string, string>> {
  return searchProjectMappingsByProjects(
    config.paths.projectStaffSearch,
    "ProjectStaff",
    "ProjectStaff",
    (item: any) => item?.userId && item?.projectId ? `${item.userId}|${item.projectId}` : undefined,
    projectIds,
    tenantId,
    requestInfo,
    { field: "staffId", ids: staffIds }
  );
}

const createProjectStaffHelper = (resourceId: any, projectId: any, resouceBody: any, tenantId: any, startDate: any, endDate: any) => {
  try {
    const ProjectStaff = {
      tenantId: tenantId.split(".")?.[0],
      projectId,
      userId: resourceId,
      startDate,
      endDate,
    };
    const newResourceBody = { ...resouceBody, ProjectStaff };
    return createStaff(newResourceBody);
  } catch (error) {
    logger.error(`Failed to create project staff for staffId ${resourceId}:`, error);
    throw error;
  }
};

const createProjectResourceHelper = (resourceId: any, projectId: any, resouceBody: any, tenantId: any, startDate: any, endDate: any) => {
  try {
    const ProjectResource = {
      tenantId: tenantId.split(".")?.[0],
      projectId,
      resource: {
        productVariantId: resourceId,
        type: "DRUG",
        isBaseUnitVariant: false,
      },
      startDate,
      endDate,
    };
    const newResourceBody = { ...resouceBody, ProjectResource };
    return createProjectResource(newResourceBody);
  }
  catch (error) {
    logger.error(`Failed to create project resource for resourceId ${resourceId}:`, error);
    throw error;
  }
};

const createProjectFacilityHelper = (resourceId: any, projectId: any, resouceBody: any, tenantId: any, startDate: any, endDate: any) => {
  try {
    const ProjectFacility = {
      tenantId: tenantId.split(".")?.[0],
      projectId,
      facilityId: resourceId,
    };
    const newResourceBody = { ...resouceBody, ProjectFacility };
    return createProjectFacility(newResourceBody);
  } catch (error) {
    logger.error(`Failed to create facility for facilityId ${resourceId}:`, error);
    throw error;
  }
};




function enrichSchema(data: any, properties: any, required: any, columns: any, unique: any, columnsNotToBeFreezed: any, columnsToBeFreezed: any, columnsToHide: any, errorMessage: any) {

  // Sort by orderNumber, name as tie-breaker
  columns.sort((a: any, b: any) => {
    if (a.orderNumber === b.orderNumber) {
      return a.name.localeCompare(b.name);
    }
    return a.orderNumber - b.orderNumber;
  });

  required.sort((a: any, b: any) => {
    if (a.orderNumber === b.orderNumber) {
      return a.name.localeCompare(b.name);
    }
    return a.orderNumber - b.orderNumber;
  });

  const sortedRequiredColumns = required.map((column: any) => column.name);
  const sortedPropertyNames = columns.map((column: any) => column.name);

  data.properties = properties;
  data.required = sortedRequiredColumns;
  data.columns = sortedPropertyNames;
  data.unique = unique;
  data.errorMessage = errorMessage;
  data.columnsNotToBeFreezed = columnsNotToBeFreezed;
  data.columnsToBeFreezed = columnsToBeFreezed;
  data.columnsToHide = columnsToHide;
}

function convertIntoSchema(data: any, isUpdate: boolean) {
  const properties: any = {};
  const errorMessage: any = {};
  const required: any[] = [];
  let columns: any[] = [];
  const unique: any[] = [];
  const columnsNotToBeFreezed: any[] = [];
  const columnsToBeFreezed: any[] = [];
  const columnsToHide: any[] = [];

  for (const propType of ['enumProperties', 'numberProperties', 'stringProperties']) {
    if (data.properties[propType] && Array.isArray(data.properties[propType]) && data.properties[propType]?.length > 0) {
      for (const property of data.properties[propType]) {
        properties[property?.name] = {
          ...property,
          type: propType === 'stringProperties' ? 'string' : propType === 'numberProperties' ? 'number' : undefined
        };
        if (property?.errorMessage)
          errorMessage[property?.name] = property?.errorMessage;

        if (property?.isRequired && required.indexOf(property?.name) === -1) {
          required.push({ name: property?.name, orderNumber: property?.orderNumber });
        }
        if (property?.isUnique && unique.indexOf(property?.name) === -1) {
          unique.push(property?.name);
        }
        if (!property?.freezeColumn || property?.freezeColumn == false) {
          columnsNotToBeFreezed.push(property?.name);
        }
        if (property?.freezeColumn) {
          columnsToBeFreezed.push(property?.name);
        }
        if (property?.hideColumn) {
          columnsToHide.push(property?.name);
        }

        // If orderNumber is missing, default to a very high number
        if (isUpdate) {
          columns.push({ name: property?.name, orderNumber: property?.orderNumber || 9999999999 });
        }
        else {
          if (!property?.isUpdate) {
            columns.push({ name: property?.name, orderNumber: property?.orderNumber || 9999999999 });
          }
        }
      }
    }
  }

  const descriptionToFieldMap: Record<string, string> = {};

  for (const [key, field] of Object.entries(properties)) {
    const typedField = field as any;

    if (typedField.isRequired) {
      descriptionToFieldMap[typedField.description] = key;
    }
  }
  data.descriptionToFieldMap = descriptionToFieldMap;
  
  
  enrichSchema(data, properties, required, columns, unique, columnsNotToBeFreezed, columnsToBeFreezed, columnsToHide, errorMessage);
  return data;
}

function convertIntoNewSchema(data: any) {
  const properties: any = {};
  const errorMessage: any = {};
  const required: any[] = [];
  let columns: any[] = [];
  const unique: any[] = [];
  const columnsNotToBeFreezed: any[] = [];
  const columnsToBeFreezed: any[] = [];
  const columnsToHide: any[] = [];

  for (const propType of ['enumProperties', 'numberProperties', 'stringProperties']) {
    if (data.properties[propType] && Array.isArray(data.properties[propType]) && data.properties[propType]?.length > 0) {
      for (const property of data.properties[propType]) {
        properties[property?.name] = {
          ...property,
          type: propType === 'stringProperties' ? 'string' : propType === 'numberProperties' ? 'number' : undefined
        };
        if (property?.errorMessage)
          errorMessage[property?.name] = property?.errorMessage;

        if (property?.isRequired && required.indexOf(property?.name) === -1) {
          required.push({ name: property?.name, orderNumber: property?.orderNumber });
        }
        if (property?.isUnique && unique.indexOf(property?.name) === -1) {
          unique.push(property?.name);
        }
        if (!property?.freezeColumn || property?.freezeColumn == false) {
          columnsNotToBeFreezed.push(property?.name);
        }
        if (property?.freezeColumn) {
          columnsToBeFreezed.push(property?.name);
        }
        if (property?.hideColumn) {
          columnsToHide.push(property?.name);
        }
      }
    }
  }

  const descriptionToFieldMap: Record<string, string> = {};

  for (const [key, field] of Object.entries(properties)) {
    const typedField = field as any;

    if (typedField.isRequired) {
      descriptionToFieldMap[typedField.description] = key;
    }
  }
  data.descriptionToFieldMap = descriptionToFieldMap;


  enrichSchema(data, properties, required, columns, unique, columnsNotToBeFreezed, columnsToBeFreezed, columnsToHide, errorMessage);
  return data;
}



async function callMdmsTypeSchema(
  tenantId: string,
  isUpdate: boolean,
  type: any,
  campaignType = "all",
  requestInfo?: RequestInfo
) {
  const RequestInfo = requestInfo;
  const requestBody = {
    RequestInfo,
    MdmsCriteria: {
      tenantId: tenantId,
      uniqueIdentifiers: [
        `${type}.${campaignType}`
      ],
      schemaCode: "HCM-ADMIN-CONSOLE.adminSchema"
    }
  };
  const url = config.host.mdmsV2 + config.paths.mdms_v2_search;
  const header = {
    ...defaultheader,
    cachekey: `mdmsv2Seacrh${requestBody?.MdmsCriteria?.tenantId}${campaignType}${type}.${campaignType}${requestBody?.MdmsCriteria?.schemaCode}`
  }
  const response = await httpRequest(url, requestBody, undefined, undefined, undefined, header);
  if (!response?.mdms?.[0]?.data) {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error occured during schema search");
  }
  return convertIntoSchema(response?.mdms?.[0]?.data, isUpdate);
}

export async function callMdmsSchema(
  tenantId: string,
  type: any
) {
  const MdmsCriteria : MDMSModels.MDMSv2RequestCriteria= {
    MdmsCriteria: {
      tenantId: tenantId,
      schemaCode: "HCM-ADMIN-CONSOLE.schemas",
      uniqueIdentifiers: [
        `${type}`
      ]
    }
  };
  const response = await searchMDMSDataViaV2Api(MdmsCriteria, true);
  if (!response?.mdms?.[0]?.data) {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error occured during schema search for " + type);
  }
  return convertIntoNewSchema(response?.mdms?.[0]?.data);
}

export {
  getSheetData,
  searchMDMS,
  getCampaignNumber,
  getSchema,
  getCount,
  getBoundarySheetData,
  createAndUploadFile,
  createExcelSheet,
  generateHierarchy,
  generateHierarchyList,
  getTargetWorkbook,
  getTargetSheetData,
  getTargetSheetDataAfterCode,
  callMdmsTypeSchema,
  getSheetDataFromWorksheet,
  createProjectStaffHelper,
  createProjectFacilityHelper, createProjectResourceHelper,
};
