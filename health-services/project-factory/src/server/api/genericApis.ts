// Import necessary modules and libraries
import config from "../config"; // Import configuration settings
import FormData from "form-data"; // Import FormData for handling multipart/form-data requests
import { defaultheader, httpRequest } from "../utils/request"; // Import httpRequest function for making HTTP requests
import { getFormattedStringForDebug, logger } from "../utils/logger"; // Import logger for logging
import { correctParentValues, getDataSheetReady, getLocalizedHeaders, sortCampaignDetails, throwError } from "../utils/genericUtils"; // Import utility functions
import { generateFilteredBoundaryData, getConfigurableColumnHeadersBasedOnCampaignType, getFiltersFromCampaignSearchResponse, getLocalizedName, processDataForTargetCalculation } from '../utils/campaignUtils'; // Import utility functions
import { getCampaignSearchResponse, getHierarchy } from './campaignApis';
const _ = require('lodash'); // Import lodash library
import { enrichTemplateMetaData, getExcelWorkbookFromFileURL } from "../utils/excelUtils";
import { processMapping } from "../utils/campaignMappingUtils";
import { defaultRequestInfo, searchBoundaryRelationshipData, searchMDMSDataViaV2Api } from "./coreApis";
import { getLocaleFromRequestInfo } from "../utils/localisationUtils";
import { MDMSModels } from "../models";

//Function to get Workbook with different tabs (for type target)
const getTargetWorkbook = async (fileUrl: string, localizationMap?: any) => {
  // Define headers for HTTP request

  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, "");

  // Get the main sheet name (assuming it's the second sheet)
  const mainSheetName = workbook.getWorksheet(1).name;
  const localizedMainSheet = getLocalizedName(mainSheetName, localizationMap);

  // Check if the main sheet exists in the workbook
  if (!workbook.getWorksheet(localizedMainSheet)) {
    throwError(
      "FILE",
      400,
      "INVALID_SHEETNAME",
      `Sheet with name "${localizedMainSheet}" is not present in the file.`
    );
  }

  // Return the workbook
  return workbook;
};

export function getJsonData(sheetData: any, getRow = false, getSheetName = false, sheetName = "sheet1") {
  const jsonData: any[] = [];
  const headers = sheetData[0]; // Extract the headers from the first row

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

export function getJsonDataWithUnlocalisedKey(sheetData: any, getRow = false, getSheetName = false, sheetName = "sheet1") {
  const jsonData: any[] = [];
  const headers = sheetData[0]; // Extract the headers from the first row

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
      rowData[colNumber - 1] = cellValue; // Store cell value (0-based index)
    });

    // Push non-empty row only
    if (rowData.some(value => value !== null && value !== undefined)) {
      sheetData[rowNumber - 1] = rowData; // Store row data (0-based index)
    }
  });
  return sheetData;
}

// Function to retrieve data from a specific sheet in an Excel file
const getSheetData = async (
  fileUrl: string,
  sheetName: string,
  getRow = false,
  createAndSearchConfig?: any,
  localizationMap?: { [key: string]: string }
) => {
  // Retrieve workbook using the getExcelWorkbookFromFileURL function
  const localizedSheetName = getLocalizedName(sheetName, localizationMap);
  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedSheetName);

  const worksheet: any = workbook.getWorksheet(localizedSheetName);

  // If parsing array configuration is provided, validate first row of each column
  // validateFirstRowColumn(createAndSearchConfig, worksheet, localizationMap);

  // Collect sheet data by iterating through rows and cells
  const sheetData = getSheetDataFromWorksheet(worksheet);
  const jsonData = getJsonData(sheetData, getRow);
  return jsonData;
};

// Helper function to extract raw cell value
function getRawCellValue(cell: any) {
  if (cell.value && typeof cell.value === 'object') {
    if ('richText' in cell.value) {
      // Handle rich text
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
      // Get the result of the formula
      return cell.value.result;
    }
    else if ('sharedFormula' in cell.value) {
      // Get the result of the shared formula
      return cell.value.result;
    }
    else if ('error' in cell.value) {
      // Get the error value
      return cell.value.error;
    } else if (cell.value instanceof Date) {
      // Handle date values
      return cell.value.toISOString();
    }
    else {
      // Return as-is for other object types
      return cell.value;
    }
  }
  return cell.value; // Return raw value for plain strings, numbers, etc.
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

  const workbookData: { [key: string]: any[] } = {}; // Object to store data from each sheet

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

  const workbookData: { [key: string]: any[] } = {}; // Object to store data from each sheet

  for (const sheetName of localizedSheetNames) {
    const worksheet = workbook.getWorksheet(sheetName);
    const sheetData = getSheetDataFromWorksheet(worksheet);
    const jsonData = getJsonData(sheetData, true, true, sheetName);

    // Find the target column index where the first row value matches codeColumnName
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


// Function to search MDMS for specific unique identifiers
const searchMDMS: any = async (
  uniqueIdentifiers: any[],
  schemaCode: string,
  requestinfo: any
) => {
  // Check if unique identifiers are provided
  if (!uniqueIdentifiers) {
    return;
  }

  // Construct API URL for MDMS search
  const apiUrl = config.host.mdmsV2 + config.paths.mdms_v2_search;

  // Construct request data for MDMS search
  const data = {
    MdmsCriteria: {
      tenantId: requestinfo?.userInfo?.tenantId,
      uniqueIdentifiers: uniqueIdentifiers,
      schemaCode: schemaCode,
    },
    RequestInfo: requestinfo,
  };

  // Make HTTP request to MDMS API
  const result = await httpRequest(
    apiUrl,
    data,
    undefined,
    undefined,
    undefined
  );

  // Log search result
  logger.info("Template search Result : " + JSON.stringify(result));

  // Return search result
  return result;
};

// Function to generate a campaign number
const getCampaignNumber: any = async (
  requestBody: any,
  idFormat: String,
  idName: string,
  tenantId: string
) => {
  // Construct request data
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

  // Construct URL for ID generation service
  const idGenUrl = config.host.idGenHost + config.paths.idGen;

  // Make HTTP request to ID generation service
  const result = await httpRequest(
    idGenUrl,
    data,
    undefined,
    undefined,
    undefined,
    undefined
  );

  // Return generated campaign number
  if (result?.idResponses?.[0]?.id) {
    return result?.idResponses?.[0]?.id;
  }

  // Throw error if ID generation fails
  throwError("COMMON", 500, "IDGEN_ERROR");
};

// Function to get schema definition based on code and request info
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

// Function to get count from response data
const getCount: any = async (
  responseData: any,
  request: any,
  response: any
) => {
  try {
    // Extract host and URL from response data
    const host = responseData?.host;
    const url = responseData?.searchConfig?.countUrl;

    // Extract request information
    const requestInfo = { RequestInfo: request?.body?.RequestInfo };

    // Make HTTP request to get count
    const result = await httpRequest(
      host + url,
      requestInfo,
      undefined,
      undefined,
      undefined,
      undefined
    );

    // Extract count from result using lodash
    const count = _.get(result, responseData?.searchConfig?.countPath);

    return count; // Return the count
  } catch (error: any) {
    // Log and throw error if any
    logger.error("Error: " + error);
    throw error;
  }
};

// Function to create Excel sheet and upload it
async function createAndUploadFile(
  updatedWorkbook: any,
  request: any,
  tenantId?: any
) {
  let retries: any = 3;
  // Enrich metadatas
  if (request?.body?.RequestInfo && request?.query?.campaignId) {
    enrichTemplateMetaData(updatedWorkbook, getLocaleFromRequestInfo(request?.body?.RequestInfo), request?.query?.campaignId);
  }
  while (retries--) {
    try {
      // Write the updated workbook to a buffer
      const buffer = await updatedWorkbook.xlsx.writeBuffer();

      // Create form data for file upload
      const formData = new FormData();
      formData.append("file", buffer, "filename.xlsx");
      formData.append(
        "tenantId",
        tenantId ? tenantId : request?.body?.RequestInfo?.userInfo?.tenantId
      );
      formData.append("module", "HCM-ADMIN-CONSOLE-SERVER");

      // Make HTTP request to upload file
      var fileCreationResult = await httpRequest(
        config.host.filestore + config.paths.filestore,
        formData,
        undefined,
        undefined,
        undefined,
        {
          "Content-Type": "multipart/form-data",
          "auth-token": request?.body?.RequestInfo?.authToken || request?.RequestInfo?.authToken,
        }
      );

      // Extract response data
      const responseData = fileCreationResult?.files;
      if (responseData) {
        return responseData;
      }
    }
    catch (error: any) {
      console.error(`Attempt failed:`, error.message);

      // Add a delay before the next retry (2 seconds)
      await new Promise((resolve) => setTimeout(resolve, 5000));
    }
  }
  throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
}

export async function createAndUploadFileWithOutRequest(
  updatedWorkbook: any,
  tenantId: any
) {
  let retries: any = 3;
  while (retries--) {
    try {
      // Write the updated workbook to a buffer
      logger.info("Creating form data for file upload...");
      const buffer = await updatedWorkbook.xlsx.writeBuffer();

      // Create form data for file upload
      const formData = new FormData();
      formData.append("file", buffer, "filename.xlsx");
      formData.append(
        "tenantId",
        tenantId
      );
      formData.append("module", "HCM-ADMIN-CONSOLE-SERVER");
      logger.info("Form data created.");

      // Make HTTP request to upload file
      var fileCreationResult = await httpRequest(
        config.host.filestore + config.paths.filestore,
        formData,
        undefined,
        undefined,
        undefined,
        {
          "Content-Type": "multipart/form-data"
        }
      );

      // Extract response data
      const responseData = fileCreationResult?.files;
      if (responseData) {
        return responseData;
      }
    }
    catch (error: any) {
      console.error(`Attempt failed:`, error.message);

      // Add a delay before the next retry (2 seconds)
      await new Promise((resolve) => setTimeout(resolve, 5000));
    }
  }
  throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
}


// Function to generate a list of hierarchy codes
function generateHierarchyList(data: any[], parentChain: any = []) {
  let result: any[] = [];

  // Iterate over each boundary in the current level
  for (let boundary of data) {
    let currentChain = [...parentChain, boundary.code];

    // Add the current chain to the result
    result.push(currentChain.join(","));

    // If there are children, recursively call the function
    if (boundary.children.length > 0) {
      let childResults = generateHierarchyList(boundary.children, currentChain);
      result = result.concat(childResults);
    }
  }
  return result; // Return the hierarchy list
}

// Function to generate hierarchy from boundaries
function generateHierarchy(boundaries: any[]) {
  // Create an object to store boundary types and their parents
  const parentMap: any = {};

  // Populate the object with boundary types and their parents
  for (const boundary of boundaries) {
    parentMap[boundary.boundaryType] = boundary.parentBoundaryType;
  }

  // Traverse the hierarchy to generate the hierarchy list
  const hierarchyList = [];
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === null) {
        // This boundary type has no parent, add it to the hierarchy list
        hierarchyList.push(boundaryType);
        // Traverse its children recursively
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
  return hierarchyList; // Return the hierarchy list
}

// Recursive function to traverse children and generate hierarchy
function traverseChildren(parent: any, parentMap: any, hierarchyList: any[]) {
  for (const boundaryType in parentMap) {
    if (Object.prototype.hasOwnProperty.call(parentMap, boundaryType)) {
      const parentBoundaryType = parentMap[boundaryType];
      if (parentBoundaryType === parent) {
        // This boundary type has the current parent, add it to the hierarchy list
        hierarchyList.push(boundaryType);
        // Traverse its children recursively
        traverseChildren(boundaryType, parentMap, hierarchyList);
      }
    }
  }
}

// Function to create an Excel sheet
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
  // Retrieve boundary data based on the request parameters
  // const params = {
  //   ...request?.query,
  //   includeChildren: true,
  // };
  const hierarchyType = request?.query?.hierarchyType;
  const tenantId = request?.query?.tenantId;
  logger.info(
    `processing boundary data generation for hierarchyType : ${hierarchyType}`
  );
  // const boundaryData = await getBoundaryRelationshipData(request, params);
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
    // const localizedBoundaryTab = getLocalizedName(
    //   getBoundaryTabName(),
    //   localizationMap
    // );
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
            boundaryType: boundary.type // Adding boundaryType field
          }))
        }
      };
    }
    // logger.info("boundaryData for sheet " + JSON.stringify(boundaryData))
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

export async function createStaff(resouceBody: any) {
  // Create staff
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
  // validateStaffResponse(staffResponse);
  return staffResponse;
}

/**
 * Asynchronously creates project resources based on the provided resource body.
 * @param resouceBody The resource body.
 */
export async function createProjectResource(resouceBody: any) {
  // Create project resources
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
  // validateProjectResourceResponse(projectResourceResponse);
}

/**
 * Asynchronously creates project facilities based on the provided resource body.
 * @param resouceBody The resource body.
 */
export async function createProjectFacility(resouceBody: any) {
  // Create project facilities
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
  // validateProjectFacilityResponse(projectFacilityResponse);
}

// Helper function to create staff
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
    // Log the error if the API call fails
    logger.error(`Failed to create project staff for staffId ${resourceId}:`, error);
    throw error; // Rethrow the error to propagate it
  }
};

// Helper function to create project resource
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
    // Log the error if the API call fails
    logger.error(`Failed to create project resource for resourceId ${resourceId}:`, error);
    throw error; // Rethrow the error to propagate it
  }
};

// Helper function to create project facility
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
    // Log the error if the API call fails
    logger.error(`Failed to create facility for facilityId ${resourceId}:`, error);
    throw error; // Rethrow the error to propagate it
  }
};


/**
 * Asynchronously creates related entities such as staff, resources, and facilities based on the provided resources, tenant ID, project ID, start date, end date, and resource body.
 * @param resources List of resources.
 * @param tenantId The tenant ID.
 * @param projectId The project ID.
 * @param startDate The start date.
 * @param endDate The end date.
 * @param resouceBody The resource body.
 */
async function createRelatedEntity(
  createRelatedEntityArray: any[],
  CampaignDetails: any,
  requestBody: any
) {
  const mappingArray = []
  for (const entity of createRelatedEntityArray) {
    const { tenantId, projectId, startDate, endDate, resouceBody, campaignId, resources } = entity
    for (const resource of resources) {
      const type = resource?.type;
      const mappingObject: any = {
        type,
        tenantId,
        resource,
        projectId,
        startDate,
        endDate,
        resouceBody,
        campaignId,
        CampaignDetails
      }
      mappingArray.push(mappingObject)
    }
  }
  const mappingObject: any = { mappingArray: mappingArray, CampaignDetails: CampaignDetails, RequestInfo: requestBody?.RequestInfo, parentCampaign: requestBody?.parentCampaign }
  await processMapping(mappingObject)
}


/**
 * Asynchronously creates related resources based on the provided request body.
 * @param requestBody The request body.
 */
async function createRelatedResouce(requestBody: any) {
  const id = requestBody?.Campaign?.id;
  sortCampaignDetails(requestBody?.Campaign?.CampaignDetails);
  correctParentValues(requestBody?.Campaign?.CampaignDetails);
  // Create related resources
  const { tenantId } = requestBody?.Campaign;
  const createRelatedEntityArray = [];
  for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
    const resouceBody: any = {
      RequestInfo: requestBody.RequestInfo,
    };
    var { projectId, startDate, endDate, resources } = campaignDetails;
    campaignDetails.id = id;
    startDate = parseInt(startDate);
    endDate = parseInt(endDate);
    createRelatedEntityArray.push({
      resources,
      tenantId,
      projectId,
      startDate,
      endDate,
      resouceBody,
      campaignId: id,
    });
  }
  await createRelatedEntity(
    createRelatedEntityArray,
    requestBody?.CampaignDetails,
    requestBody
  );
}


function enrichSchema(data: any, properties: any, required: any, columns: any, unique: any, columnsNotToBeFreezed: any, columnsToBeFreezed: any, columnsToHide: any, errorMessage: any) {

  // Sort columns based on orderNumber, using name as tie-breaker if orderNumbers are equal
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

  // Extract sorted property names
  const sortedPropertyNames = columns.map((column: any) => column.name);

  // Update data with new properties and required fields
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
    // Cast field to `any` since it is of type `unknown`
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
    // Cast field to `any` since it is of type `unknown`
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
  campaignType = "all"
) {
  const RequestInfo = defaultRequestInfo?.RequestInfo;
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
  createRelatedResouce,
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
