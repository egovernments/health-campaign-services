import { NextFunction, Request, Response } from "express";
import { httpRequest, defaultheader } from "./request";
import config from "../config/index";
import { getErrorCodes, mappingStatuses } from "../config/constants";
import { v4 as uuidv4 } from 'uuid';
import { produceModifiedMessages } from "../kafka/Producer";
import { generateHierarchyList, getAllFacilities, getCampaignSearchResponse, getHierarchy } from "../api/campaignApis";
import { getBoundarySheetData, getSheetData, createAndUploadFile, createExcelSheet, getTargetSheetData, callMdmsTypeSchema, getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement } from "../api/genericApis";
import { logger } from "./logger";
import { checkIfSourceIsMicroplan, getConfigurableColumnHeadersBasedOnCampaignType, getDifferentTabGeneratedBasedOnConfig, getLocalizedName, getLocalizedNameOnlyIfMessagePresent } from "./campaignUtils";
import Localisation from "../controllers/localisationController/localisation.controller";
import { executeQuery, getTableName } from "./db";
import { generatedResourceTransformer } from "./transforms/searchResponseConstructor";
import { allProcesses, generatedResourceStatuses, headingMapping, processStatuses, resourceDataStatuses } from "../config/constants";
import { getLocaleFromRequest, getLocaleFromRequestInfo, getLocalisationModuleName } from "./localisationUtils";
import { getBoundaryColumnName, getBoundaryTabName, getLatLongMapForBoundaryCodes } from "./boundaryUtils";
import { getBoundaryDataService, searchDataService } from "../service/dataManageService";
import { addDataToSheet, enrichUsageColumnForFacility, formatWorksheet, getNewExcelWorkbook, protectSheet, updateFontNameToRoboto } from "./excelUtils";
import createAndSearch from "../config/createAndSearch";
import { generateDynamicTargetHeaders } from "./targetUtils";
import { buildSearchCriteria, checkAndGiveIfParentCampaignAvailable, getCreatedResourceIds, modifyProcessedSheetData } from "./onGoingCampaignUpdateUtils";
import { getReadMeConfigForMicroplan, getRolesForMicroplan, getUserDataFromMicroplanSheet, isMicroplanRequest } from "./microplanUtils";
import _ from "lodash";
import { fetchFileFromFilestore, searchMDMSDataViaV1Api } from "../api/coreApis";
const NodeCache = require("node-cache");

const updateGeneratedResourceTopic = config?.kafka?.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC;
const createGeneratedResourceTopic = config?.kafka?.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC;

/*
  stdTTL: (default: 0) the standard ttl as number in seconds for every generated
   cache element. 0 = unlimited

  checkperiod: (default: 600) The period in seconds, as a number, used for the automatic
   delete check interval. 0 = no periodic check.

   30 mins caching
*/

const appCache = new NodeCache({ stdTTL: 1800000, checkperiod: 300 });

/* 
Send The Error Response back to client with proper response code 
*/
const throwErrorViaRequest = (message: any = "Internal Server Error") => {
  if (message?.message || message?.code) {
    let error: any = new Error(message?.message || message?.code);
    error = Object.assign(error, { status: message?.status || 500 });
    logger.error("Error : " + error + " " + (message?.description || ""));
    throw error;
  }
  else {
    let error: any = new Error(message);
    error = Object.assign(error, { status: 500 });
    logger.error("Error : " + error);
    throw error;
  }
};

function shutdownGracefully() {
  logger.info('Shutting down gracefully...');
  // Perform any cleanup tasks here, like closing database connections
  process.exit(1); // Exit with a non-zero code to indicate an error
}

function capitalizeFirstLetter(str: string | undefined) {
  if (!str) return str;
  return str.charAt(0).toUpperCase() + str.slice(1);
}

const throwError = (module = "COMMON", status = 500, code = "UNKNOWN_ERROR", description: any = null) => {
  const errorResult: any = getErrorCodes(module, code);
  status = errorResult?.code == "UNKNOWN_ERROR" ? 500 : status;
  let error: any = new Error(capitalizeFirstLetter(errorResult?.message));
  error = Object.assign(error, { status, code: errorResult?.code, description: capitalizeFirstLetter(description) });
  logger.error(error);
  throw error;
};

const replicateRequest = (originalRequest: Request, requestBody: any, requestQuery?: any) => {
  const newRequest = {
    ...originalRequest,
    body: _.cloneDeep(requestBody), // Deep clone using lodash
    query: requestQuery ? _.cloneDeep(requestQuery) : _.cloneDeep(originalRequest.query)
  };
  return newRequest;
};


/* 
Error Object
*/
const getErrorResponse = (
  code = "INTERNAL_SERVER_ERROR",
  message = "Some Error Occured!!",
  description: any = null
) => ({
  ResponseInfo: null,
  Errors: [
    {
      code: code,
      message: message,
      description: description,
      params: null,
    },
  ],
});

/* 
Send The Response back to client with proper response code and response info
*/
const sendResponse = (
  response: Response,
  responseBody: any,
  req: Request,
  code: number = 200
) => {
  /* if (code != 304) {
    appCache.set(req.headers.cachekey, { ...responseBody });
  } else {
    logger.info("CACHED RESPONSE FOR :: " + req.headers.cachekey);
  }
  */
  logger.info("Send back the response to the client");
  response.status(code).send({
    ...getResponseInfo(code),
    ...responseBody,
  });
};

/* 
Response Object
*/
const getResponseInfo = (code: Number) => ({
  ResponseInfo: {
    apiId: "egov-bff",
    ver: "0.0.1",
    ts: new Date().getTime(),
    status: "successful",
    desc: code == 304 ? "cached-response" : "new-response",
  },
});

/* 
Fallback Middleware function for returning 404 error for undefined paths
*/
const invalidPathHandler = (
  request: any,
  response: any,
  next: NextFunction
) => {
  response.status(404);
  response.send(getErrorResponse("INVALID_PATH", "invalid path"));
};

/*
Error handling Middleware function for logging the error message
*/
const errorLogger = (
  error: Error,
  request: any,
  response: any,
  next: NextFunction
) => {
  logger.error(error.stack);
  logger.error(`error ${error.message}`);
  next(error); // calling next middleware
};

/*
Error handling Middleware function reads the error message and sends back a response in JSON format
*/
const errorResponder = (
  error: any,
  request: any,
  response: Response,
  status: any = 500,
  next: any = null
) => {
  if (error?.status) {
    status = error?.status;
  }
  const code = error?.code || (status === 500 ? "INTERNAL_SERVER_ERROR" : (status === 400 ? "BAD_REQUEST" : "UNKNOWN_ERROR"));
  response.header("Content-Type", "application/json");
  const errorMessage = trimError(error.message || "Some Error Occurred!!");
  const errorDescription = error.description || null;
  const errorResponse = getErrorResponse(code, errorMessage, errorDescription);
  response.status(status).send(errorResponse);
};


const trimError = (e: any) => {
  if (typeof e === "string") {
    e = e.trim();
    while (e.startsWith("Error:")) {
      e = e.substring(6);
      e = e.trim();
    }
  }
  return e;
}

/* Fetches data from the database */
async function searchGeneratedResources(searchQuery : any, locale : any) {
  try {
    const { type, tenantId, hierarchyType, id, status, campaignId } = searchQuery;
    const tableName = getTableName(config?.DB_CONFIG.DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    let queryString = `SELECT * FROM ${tableName} WHERE `;
    let queryConditions: string[] = [];
    let queryValues: any[] = [];
    if (id) {
      queryConditions.push(`id = $${queryValues.length + 1}`);
      queryValues.push(id);
    }
    if (type) {
      queryConditions.push(`type = $${queryValues.length + 1}`);
      queryValues.push(type);
    }

    if (hierarchyType) {
      queryConditions.push(`hierarchyType = $${queryValues.length + 1}`);
      queryValues.push(hierarchyType);
    }
    if (tenantId) {
      queryConditions.push(`tenantId = $${queryValues.length + 1}`);
      queryValues.push(tenantId);
    }
    if (campaignId) {
      queryConditions.push(`campaignId = $${queryValues.length + 1}`);
      queryValues.push(campaignId);
    }
    if (status) {
      const statusArray = status.split(',').map((s: any) => s.trim());
      const statusConditions = statusArray.map((_: any, index: any) => `status = $${queryValues.length + index + 1}`);
      queryConditions.push(`(${statusConditions.join(' OR ')})`);
      queryValues.push(...statusArray);
    }
    if (locale) {
      queryConditions.push(`locale = $${queryValues.length + 1}`);
      queryValues.push(locale);
    }

    queryString += queryConditions.join(" AND ");

    // Add sorting and limiting
    queryString += " ORDER BY createdTime DESC OFFSET 0 LIMIT 1";

    const queryResult = await executeQuery(queryString, queryValues);
    return generatedResourceTransformer(queryResult?.rows);
  } catch (error: any) {
    console.log(error)
    logger.error(`Error fetching data from the database: ${error.message}`);
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", error?.message);
    return null; // Return null in case of an error
  }
}

async function searchAllGeneratedResources(searchQuery: any, locale: any) {
  try {
    const { type, tenantId, hierarchyType, id, status, campaignId } = searchQuery;
    const tableName = getTableName(config?.DB_CONFIG.DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    let queryString = `SELECT * FROM ${tableName} WHERE `;
    let queryConditions: string[] = [];
    let queryValues: any[] = [];
    if (id) {
      queryConditions.push(`id = $${queryValues.length + 1}`);
      queryValues.push(id);
    }
    if (type) {
      queryConditions.push(`type = $${queryValues.length + 1}`);
      queryValues.push(type);
    }

    if (hierarchyType) {
      queryConditions.push(`hierarchyType = $${queryValues.length + 1}`);
      queryValues.push(hierarchyType);
    }
    if (tenantId) {
      queryConditions.push(`tenantId = $${queryValues.length + 1}`);
      queryValues.push(tenantId);
    }
    if (campaignId) {
      queryConditions.push(`campaignId = $${queryValues.length + 1}`);
      queryValues.push(campaignId);
    }
    if (status) {
      const statusArray = status.split(',').map((s: any) => s.trim());
      const statusConditions = statusArray.map((_: any, index: any) => `status = $${queryValues.length + index + 1}`);
      queryConditions.push(`(${statusConditions.join(' OR ')})`);
      queryValues.push(...statusArray);
    }
    if (locale) {
      queryConditions.push(`locale = $${queryValues.length + 1}`);
      queryValues.push(locale);
    }
    queryString += queryConditions.join(" AND ");

    const queryResult = await executeQuery(queryString, queryValues);
    return generatedResourceTransformer(queryResult?.rows);
  } catch (error: any) {
    console.log(error)
    logger.error(`Error fetching data from the database: ${error.message}`);
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", error?.message);
    return null; // Return null in case of an error
  }
}

async function enrichAuditDetails(responseData: any) {
  return responseData.map((item: any) => {
    return {
      ...item,
      count: parseInt(item.count),
      auditDetails: {
        ...item.auditDetails,
        lastModifiedTime: parseInt(item.auditDetails.lastModifiedTime),
        createdTime: parseInt(item.auditDetails.createdTime)
      }
    };
  });
}

async function generateNewRequestObject(request: any) {
  const { type } = request.query;
  const additionalDetails = type === 'boundary'
    ? { Filters: request?.body?.Filters ?? null }
    : {};
  const newEntry = {
    id: uuidv4(),
    fileStoreid: null,
    type: type,
    status: generatedResourceStatuses.inprogress,
    hierarchyType: request?.query?.hierarchyType,
    tenantId: request?.query?.tenantId,
    auditDetails: {
      lastModifiedTime: Date.now(),
      createdTime: Date.now(),
      createdBy: request?.body?.RequestInfo?.userInfo.uuid,
      lastModifiedBy: request?.body?.RequestInfo?.userInfo.uuid,
    },
    additionalDetails: additionalDetails,
    count: null,
    campaignId: request?.query?.campaignId,
    locale: request?.body?.RequestInfo?.msgId?.split('|')[1] || null
  };
  return [newEntry];
}
async function updateExistingResourceExpired(modifiedResponse: any[], request: any) {
  return modifiedResponse.map((item: any) => {
    const newItem = { ...item };
    newItem.status = generatedResourceStatuses.expired;
    newItem.auditDetails.lastModifiedTime = Date.now();
    newItem.auditDetails.lastModifiedBy = request?.body?.RequestInfo?.userInfo?.uuid;
    return newItem;
  });
}
async function getFinalUpdatedResponse(result: any, responseData: any, request: any) {
  return responseData.map((item: any) => {
    return {
      ...item,
      tenantId: request?.query?.tenantId,
      count: parseInt(request?.body?.generatedResourceCount || null),
      auditDetails: {
        ...item.auditDetails,
        lastModifiedTime: Date.now(),
        createdTime: Date.now(),
        lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid
      },
      fileStoreid: result?.[0]?.fileStoreId,
      status: resourceDataStatuses.completed
    };
  });
}



async function fullProcessFlowForNewEntry(newEntryResponse: any, generatedResource: any, request: any, enableCaching = false, filteredBoundary?: any) {
  const tenantId = request?.query?.tenantId;
  try {
    const { type, hierarchyType } = request?.query;
    generatedResource = { generatedResource: newEntryResponse }
    // send message to create toppic
    logger.info(`processing the generate request for type ${type}`)
    await produceModifiedMessages(generatedResource, createGeneratedResourceTopic, request?.query?.tenantId);
    const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(hierarchyType));
    const localizationMapModule = await getLocalizedMessagesHandler(request, request?.query?.tenantId);
    const localizationMap = { ...localizationMapHierarchy, ...localizationMapModule };
    let fileUrlResponse: any;
    if (type != 'boundaryManagement' && request?.query?.campaignId != 'default' && type != 'boundaryGeometryManagement') {
      const responseFromCampaignSearch = await getCampaignSearchResponse(request);
      const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
      logger.info(`checks for parent campaign for type: ${type}`)
      await checkAndGiveIfParentCampaignAvailable(request, campaignObject);
    }
    if (request?.body?.parentCampaignObject) {
      const resourcesOfParentCampaign = request?.body?.parentCampaignObject?.resources;
      const createdResourceId = getCreatedResourceIds(resourcesOfParentCampaign, type);
      logger.info(` found createdResourceId as ${createdResourceId} `);
      if(createdResourceId && Array.isArray(createdResourceId) && createdResourceId.length > 0) {
        const searchCriteria = buildSearchCriteria(request, createdResourceId, type);
        const responseFromDataSearch = await searchDataService(replicateRequest(request, searchCriteria));

        const processedFileStoreIdForUSerOrFacility = responseFromDataSearch?.[0]?.processedFilestoreId;
        fileUrlResponse = await fetchFileFromFilestore(processedFileStoreIdForUSerOrFacility, request?.query?.tenantId);
      }

    }
    if (type === 'boundary') {
      // get boundary data from boundary relationship search api
      logger.info("Generating Boundary Data")
      const boundaryDataSheetGeneratedBeforeDifferentTabSeparation = await getBoundaryDataService(request, enableCaching);
      logger.info(`Boundary data generated successfully: ${JSON.stringify(boundaryDataSheetGeneratedBeforeDifferentTabSeparation)}`);
      // get boundary sheet data after being generated
      var boundaryDataSheetGeneratedAfterDifferentTabSeparation = boundaryDataSheetGeneratedBeforeDifferentTabSeparation;
      logger.info("generating different tabs logic ")
      boundaryDataSheetGeneratedAfterDifferentTabSeparation = await getDifferentTabGeneratedBasedOnConfig(request, boundaryDataSheetGeneratedBeforeDifferentTabSeparation, localizationMap, fileUrlResponse)
      logger.info(`Different tabs based on level configured generated, ${JSON.stringify(boundaryDataSheetGeneratedAfterDifferentTabSeparation)}`)
      const finalResponse = await getFinalUpdatedResponse(boundaryDataSheetGeneratedAfterDifferentTabSeparation, newEntryResponse, request);
      const generatedResourceNew: any = { generatedResource: finalResponse }
      // send to update topic
      await produceModifiedMessages(generatedResourceNew, updateGeneratedResourceTopic, request?.query?.tenantId);
      request.body.generatedResource = finalResponse;
    }
    else if (type == 'boundaryManagement' || type === 'boundaryGeometryManagement') {
      // get boundary data from boundary relationship search api
      logger.info("Generating Boundary Data")
      const boundaryDataSheetGeneratedBeforeDifferentTabSeparation = await getBoundaryDataService(request, false);
      logger.info(`Boundary data generated successfully: ${JSON.stringify(boundaryDataSheetGeneratedBeforeDifferentTabSeparation)}`);
      // get boundary sheet data after being generated
      const finalResponse = await getFinalUpdatedResponse(boundaryDataSheetGeneratedBeforeDifferentTabSeparation, newEntryResponse, request);
      const generatedResourceNew: any = { generatedResource: finalResponse }
      // send to update topic
      await produceModifiedMessages(generatedResourceNew, updateGeneratedResourceTopic, request?.query?.tenantId);
      request.body.generatedResource = finalResponse;
      logger.info("generation completed for boundary management create flow")
    }
    else if (type == "facilityWithBoundary" || type == 'userWithBoundary') {
      await processGenerateRequest(request, localizationMap, filteredBoundary, fileUrlResponse);
      const finalResponse = await getFinalUpdatedResponse(request?.body?.fileDetails, newEntryResponse, request);
      const generatedResourceNew: any = { generatedResource: finalResponse }
      await produceModifiedMessages(generatedResourceNew, updateGeneratedResourceTopic, request?.query?.tenantId);
      request.body.generatedResource = finalResponse;
    }
  }
  catch (error: any) {
    console.log(error)
    await handleGenerateError(newEntryResponse, generatedResource, error, tenantId);
  }
}

function sortCampaignDetails(campaignDetails: any) {
  campaignDetails.sort((a: any, b: any) => {
    // If a is a child of b, a should come after b
    if (a.parentBoundaryCode === b.boundaryCode) return 1;
    // If b is a child of a, a should come before b
    if (a.boundaryCode === b.parentBoundaryCode) return -1;
    // Otherwise, maintain the order
    return 0;
  });
  return campaignDetails;
}
// Function to correct the totals and target values of parents
function correctParentValues(campaignDetails: any) {
  // Create a map to store parent-child relationships and their totals/targets
  const parentMap: any = {};
  campaignDetails.forEach((detail: any) => {
    if (!detail.parentBoundaryCode) return; // Skip if it's not a child
    if (!parentMap[detail.parentBoundaryCode]) {
      parentMap[detail.parentBoundaryCode] = { total: 0, target: 0 };
    }
    parentMap[detail.parentBoundaryCode].total += detail.targets[0].total;
    parentMap[detail.parentBoundaryCode].target += detail.targets[0].target;
  });

  // Update parent values with the calculated totals and targets
  campaignDetails.forEach((detail: any) => {
    if (!detail.parentBoundaryCode) return; // Skip if it's not a child
    const parent = parentMap[detail.parentBoundaryCode];
    const target = detail.targets[0];
    target.total = parent.total;
    target.target = parent.target;
  });

  return campaignDetails;
}

function setHiddenColumns(request: any, schema: any, localizationMap?: { [key: string]: string }) {
  // from schema.properties find the key whose value have value.hideColumn == true
  const hiddenColumns = Object.entries(schema.properties).filter(([key, value]: any) => value.hideColumn == true).map(([key, value]: any) => getLocalizedName(key, localizationMap));
  logger.info(`Columns to hide ${JSON.stringify(hiddenColumns)}`);
  request.body.hiddenColumns = hiddenColumns;
}

async function getResourceDistributionStrategyTypes(request: any) {
  const { RequestInfo = {} } = request?.body || {};
  const requestBody = {
    RequestInfo,
    MdmsCriteria: {
      tenantId: request?.query?.tenantId,
      schemaCode: "hcm-microplanning.ResourceDistributionStrategy"
    }
  };
  const url = config.host.mdmsV2 + config.paths.mdms_v2_search;
  const response = await httpRequest(url, requestBody, undefined, undefined, undefined);
  if (response?.mdms && Array.isArray(response?.mdms)) {
    const resourceDistributionStrategyTypes = response?.mdms?.map((mdms: any) => mdms?.data?.resourceDistributionStrategyCode);
    return resourceDistributionStrategyTypes;
  }
  else {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error occured during resource distribution strategy type search");
  }
}

async function getSchemaBasedOnSource(request: any, isSourceMicroplan: boolean, resourceDistributionStrategy: string) {
  const tenantId = request?.query?.tenantId;
  let schema: any;
  if (isSourceMicroplan) {
    const resourceDistributionStrategyTypes = await getResourceDistributionStrategyTypes(request);
    if (resourceDistributionStrategyTypes.includes(resourceDistributionStrategy)) {
      schema = await callMdmsTypeSchema(tenantId, false, "facility", `MP-FACILITY-${resourceDistributionStrategy}`);
    }
    else {
      throwError("CAMPAIGN", 500, "INVALID_RESOURCE_DISTRIBUTION_STRATEGY", `Invalid resource distribution strategy: ${resourceDistributionStrategy} ; Allowed resource distribution strategies: ${resourceDistributionStrategyTypes}`);
    }
  } else {
    schema = await callMdmsTypeSchema(tenantId, false, "facility", "all");
  }
  return schema;
}

async function createFacilitySheet(request: any, allFacilities: any[], localizationMap?: { [key: string]: string }) {
  const responseFromCampaignSearch = await getCampaignSearchResponse(request);
  const isSourceMicroplan = checkIfSourceIsMicroplan(responseFromCampaignSearch?.CampaignDetails?.[0]);
  request.body.isSourceMicroplan = isSourceMicroplan;
  let schema: any = await getSchemaBasedOnSource(request, isSourceMicroplan, responseFromCampaignSearch?.CampaignDetails?.[0]?.additionalDetails?.resourceDistributionStrategy);
  const keys = schema?.columns;
  // setDropdownFromSchema(request, schema, localizationMap);
  setHiddenColumns(request, schema, localizationMap);
  const headers = ["HCM_ADMIN_CONSOLE_FACILITY_CODE", ...keys]
  let localizedHeaders;
  if (isSourceMicroplan) {
    localizedHeaders = getLocalizedHeadersForMicroplan(responseFromCampaignSearch, headers, localizationMap);

  }
  else {
    localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  }
  const facilities = allFacilities.map((facility: any) => {
    return [
      facility?.id,
      facility?.name,
      facility?.usage,
      facility?.isPermanent ? "Permanent" : "Temporary",
      facility?.storageCapacity,
      ""
    ]
  })
  logger.info("facilities generation done ");
  logger.debug(`facility response ${JSON.stringify(facilities)}`)
  const facilitySheetData: any = await createExcelSheet(facilities, localizedHeaders);
  return { schema, facilitySheetData };
}



function setAndFormatHeaders(worksheet: any, mainHeader: any, headerSet: any) {

  // Ensure mainHeader is an array
  if (!Array.isArray(mainHeader)) {
    mainHeader = [mainHeader];
  }
  // headerSet.add(mainHeader)
  const headerRow = worksheet.addRow(mainHeader);

  // Color the header cell
  headerRow.eachCell((cell: any) => {
    cell.fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: 'f25449' } // Header cell color
    };
    cell.alignment = { vertical: 'middle', horizontal: 'center', wrapText: true }; // Center align and wrap text
    cell.font = { bold: true };
  });
}

async function createReadMeSheet(request: any, workbook: any, mainHeader: any, localizationMap = {}) {
  const isSourceMicroplan = await isMicroplanRequest(request);
  let readMeConfig: any;
  if (isSourceMicroplan) {
    readMeConfig = await getReadMeConfigForMicroplan(request);
  }
  else {
    readMeConfig = await getReadMeConfig(request?.query?.tenantId, request?.query?.type);
  }
  const headerSet = new Set();
  const datas = readMeConfig.texts
    .filter((text: any) => text?.inSheet) // Filter out texts with inSheet set to false
    .flatMap((text: any) => {
      const descriptions = text.descriptions.map((description: any) => {
        return getLocalizedName(description.text, localizationMap);
      });
      headerSet.add(getLocalizedName(text.header, localizationMap));
      return [getLocalizedName(text.header, localizationMap), ...descriptions, ""];
    });

  // Create the worksheet and add the main header
  const worksheet = workbook.addWorksheet(getLocalizedName("HCM_README_SHEETNAME", localizationMap));

  setAndFormatHeaders(worksheet, mainHeader, headerSet);

  formatWorksheet(worksheet, datas, headerSet);

  updateFontNameToRoboto(worksheet);

  return worksheet;
}


function createBoundaryDataMainSheet(request: any, boundaryData: any, differentTabsBasedOnLevel: any, hierarchy: any, localizationMap?: any) {
  const uniqueDistrictsForMainSheet: string[] = [];
  const districtLevelRowBoundaryCodeMap = new Map();
  const mainSheetData: any[] = [];
  const headersForMainSheet = differentTabsBasedOnLevel ? hierarchy.slice(0, hierarchy.indexOf(differentTabsBasedOnLevel) + 1) : [];
  const localizedHeadersForMainSheet = getLocalizedHeaders(headersForMainSheet, localizationMap);
  const localizedBoundaryCode = getLocalizedName(getBoundaryColumnName(), localizationMap);
  localizedHeadersForMainSheet.push(localizedBoundaryCode);
  mainSheetData.push([...localizedHeadersForMainSheet]);
  for (const data of boundaryData) {
    const modifiedData = modifyDataBasedOnDifferentTab(data, differentTabsBasedOnLevel, localizedHeadersForMainSheet, localizationMap);
    const rowData = Object.values(modifiedData);
    const districtIndex = modifiedData[differentTabsBasedOnLevel] !== '' ? rowData.indexOf(data[differentTabsBasedOnLevel]) : -1;
    if (districtIndex == -1) {
      mainSheetData.push(rowData);
    } else {
      const districtLevelRow = rowData.slice(0, districtIndex + 1);
      if (!uniqueDistrictsForMainSheet.includes(districtLevelRow.join('#'))) {
        uniqueDistrictsForMainSheet.push(districtLevelRow.join('#'));
        districtLevelRowBoundaryCodeMap.set(districtLevelRow.join('#'), data[getLocalizedName(getBoundaryColumnName(), localizationMap)]);
        mainSheetData.push(rowData);
      }
    }
  }
  return [mainSheetData, uniqueDistrictsForMainSheet, districtLevelRowBoundaryCodeMap]
}


function getLocalizedHeaders(headers: any, localizationMap?: { [key: string]: string }) {
  const messages = headers.map((header: any) => (localizationMap ? localizationMap[header] || header : header));
  return messages;
}

function getLocalizedHeadersForMicroplan(responseFromCampaignSearch: any, headers: any, localizationMap?: { [key: string]: string }) {

  const projectType = responseFromCampaignSearch?.CampaignDetails?.[0]?.projectType;

  headers = headers.map((header: string) => {
    if (header === 'HCM_ADMIN_CONSOLE_FACILITY_CAPACITY_MICROPLAN') {
      return `${header}_${projectType}`;
    }
    return header;
  });

  const messages = headers.map((header: any) => (localizationMap ? localizationMap[header] || header : header));
  return messages;
}

export async function getReadMeConfig(tenantId: string , type : string) {
  const MdmsCriteria = {
    MdmsCriteria: { // ✅ Now it matches `MDMSv1RequestCriteria`
      tenantId,
      moduleDetails: [
        {
          moduleName: "HCM-ADMIN-CONSOLE",
          masterDetails: [{ name: "ReadMeConfig" }],
        },
      ],
    },
  };
  // const mdmsResponse = await callMdmsData(request, "HCM-ADMIN-CONSOLE", "ReadMeConfig", request?.query?.tenantId);
  const mdmsResponse = await searchMDMSDataViaV1Api(MdmsCriteria);
  if (mdmsResponse?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig) {
    const readMeConfigsArray = mdmsResponse?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig
    for (const readMeConfig of readMeConfigsArray) {
      if (readMeConfig?.type == type) {
        return readMeConfig
      }
    }
    throwError("MDMS", 500, "INVALID_README_CONFIG", `Readme config for type ${type} not found.`);
    return {}
  }
  else {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", `Some error occured during readme config mdms search.`);
    return {};
  }
}


function changeFirstRowColumnColour(facilitySheet: any, color: any, columnNumber = 1) {
  // Color the first column header of the facility sheet orange
  const headerRow = facilitySheet.getRow(1); // Assuming the first row is the header
  const firstHeaderCell = headerRow.getCell(columnNumber);
  firstHeaderCell.fill = {
    type: 'pattern',
    pattern: 'solid',
    fgColor: { argb: color }
  };
}

function hideUniqueIdentifierColumn(sheet: any, column: any) {
  if (column) {
    sheet.getColumn(column).hidden = true
  }
}


async function createFacilityAndBoundaryFile(facilitySheetData: any, boundarySheetData: any, request: any, localizationMap?: any, fileUrl?: any, schema?: any) {
  const workbook = getNewExcelWorkbook();

  // Add facility sheet to the workbook
  const localizedFacilityTab = getLocalizedName(config?.facility?.facilityTab, localizationMap);
  const type = request?.query?.type;
  const headingInSheet = headingMapping?.[type];
  const localizedHeading = getLocalizedName(headingInSheet, localizationMap);

  // Create and add ReadMe sheet
  await createReadMeSheet(request, workbook, localizedHeading, localizationMap);

  // Add facility sheet data
  const facilitySheet = workbook.addWorksheet(localizedFacilityTab);
  addDataToSheet(request, facilitySheet, facilitySheetData, undefined, undefined, true, false, localizationMap, fileUrl, schema);
  enrichUsageColumnForFacility(facilitySheet, localizationMap);
  hideUniqueIdentifierColumn(facilitySheet, createAndSearch?.["facility"]?.uniqueIdentifierColumn);
  changeFirstRowColumnColour(facilitySheet, 'E06666');

  await handledropdownthings(facilitySheet, schema, localizationMap);
  protectSheet(facilitySheet);
  await handleHiddenColumns(facilitySheet, request.body?.hiddenColumns);

  // Add boundary sheet to the workbook
  const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
  const boundarySheet = workbook.addWorksheet(localizedBoundaryTab);
  addDataToSheet(request, boundarySheet, boundarySheetData, 'F3842D', 30, false, true);

  // Create and upload the fileData at row
  const fileDetails = await createAndUploadFile(workbook, request);
  request.body.fileDetails = fileDetails;
}

export async function handledropdownthings(sheet: any, schema: any, localizationMap: any) {
  logger.info(sheet.rowCount)
  const dropdowns = Object.entries(schema?.properties || {})
    .filter(([key, value]: any) => Array.isArray(value.enum) && value.enum.length > 0)
    .reduce((result: any, [key, value]: any) => {
      // Transform the key using localisedValue function
      const newKey: any = getLocalizedName(key, localizationMap);
      result[newKey] = value.enum;
      return result;
    }, {});
  if (dropdowns && Object.keys(dropdowns)?.length > 0) {
    logger.info(`Managing dropdowns: ${JSON.stringify(dropdowns)}`);
    for (const key of Object.keys(dropdowns)) {
      let dropdownColumnIndex = -1;
      if (dropdowns[key]) {
        logger.info(`Processing dropdown key: ${key} with values: ${dropdowns[key]}`);
        const firstRow = sheet.getRow(1);
        firstRow.eachCell({ includeEmpty: true }, (cell: any, colNumber: any) => {
          if (cell.value === key) {
            dropdownColumnIndex = colNumber;
            logger.info(`Found column index for dropdown "${key}": ${dropdownColumnIndex}`);
          }
        });

        // If dropdown column index is found, set multi-select dropdown for subsequent rows
        if (dropdownColumnIndex !== -1) {
          logger.info(`Setting dropdown for column index: ${dropdownColumnIndex}`);
          sheet.getColumn(dropdownColumnIndex).eachCell({ includeEmpty: true }, (cell: any, rowNumber: any) => {
            if (rowNumber > 1) {
              if (cell.protection?.locked) { // Check if the cell is locked
                cell.protection = { locked: false };
                // Set dropdown list with no typing allowed
                cell.dataValidation = {
                  type: 'list',
                  formulae: [`"${dropdowns[key].join(',')}"`],
                  showDropDown: true,
                  error: 'Please select a value from the dropdown list.',
                  errorStyle: 'stop',
                  showErrorMessage: true,
                  errorTitle: 'Invalid Entry'
                };
                cell.protection = { locked: true }; // Lock the cell again after adding dropdown
              }
              else {
                cell.dataValidation = {
                  type: 'list',
                  formulae: [`"${dropdowns[key].join(',')}"`],
                  showDropDown: true,
                  error: 'Please select a value from the dropdown list.',
                  errorStyle: 'stop',
                  showErrorMessage: true,
                  errorTitle: 'Invalid Entry',
                  allowBlank: true  // Allow blank entries
                };
              }
            }
          });
        } else {
          logger.info(`Dropdown column index not found for key: ${key}`);
        }
      }
    }
  } else {
    logger.info("No dropdowns provided.");
  }
}

export async function handledropdownthingsUnLocalised(sheet: any, schema: any) {
  logger.info(sheet.rowCount)
  const dropdowns = Object.entries(schema?.properties || {})
    .filter(([key, value]: any) => Array.isArray(value.enum) && value.enum.length > 0)
    .reduce((result: any, [key, value]: any) => {
      // Transform the key using localisedValue function
      result[key] = value.enum;
      return result;
    }, {});
  if (dropdowns && Object.keys(dropdowns)?.length > 0) {
    logger.info(`Managing dropdowns: ${JSON.stringify(dropdowns)}`);
    for (const key of Object.keys(dropdowns)) {
      let dropdownColumnIndex = -1;
      if (dropdowns[key]) {
        logger.info(`Processing dropdown key: ${key} with values: ${dropdowns[key]}`);
        const firstRow = sheet.getRow(1);
        firstRow.eachCell({ includeEmpty: true }, (cell: any, colNumber: any) => {
          if (cell.value === key) {
            dropdownColumnIndex = colNumber;
            logger.info(`Found column index for dropdown "${key}": ${dropdownColumnIndex}`);
          }
        });

        // If dropdown column index is found, set multi-select dropdown for subsequent rows
        if (dropdownColumnIndex !== -1) {
          logger.info(`Setting dropdown for column index: ${dropdownColumnIndex}`);
          sheet.getColumn(dropdownColumnIndex).eachCell({ includeEmpty: true }, (cell: any, rowNumber: any) => {
            if (rowNumber > 2) {
              if (cell.protection?.locked) { // Check if the cell is locked
                cell.protection = { locked: false };
                // Set dropdown list with no typing allowed
                cell.dataValidation = {
                  type: 'list',
                  formulae: [`"${dropdowns[key].join(',')}"`],
                  showDropDown: true,
                  error: 'Please select a value from the dropdown list.',
                  errorStyle: 'stop',
                  showErrorMessage: true,
                  errorTitle: 'Invalid Entry'
                };
                cell.protection = { locked: true }; // Lock the cell again after adding dropdown
              }
              else {
                cell.dataValidation = {
                  type: 'list',
                  formulae: [`"${dropdowns[key].join(',')}"`],
                  showDropDown: true,
                  error: 'Please select a value from the dropdown list.',
                  errorStyle: 'stop',
                  showErrorMessage: true,
                  errorTitle: 'Invalid Entry',
                  allowBlank: true  // Allow blank entries
                };
              }
            }
          });
        } else {
          logger.info(`Dropdown column index not found for key: ${key}`);
        }
      }
    }
  } else {
    logger.info("No dropdowns provided.");
  }
}

async function handleHiddenColumns(sheet: any, hiddenColumns: any) {
  // logger.info(sheet)
  logger.info("hiddenColumns", hiddenColumns);
  if (hiddenColumns) {
    for (const columnName of hiddenColumns) {
      const firstRow = sheet.getRow(1);
      let colIndex = -1;
      firstRow.eachCell({ includeEmpty: true }, (cell: any, colNumber: any) => {
        if (cell.value === columnName) {
          colIndex = colNumber;
        }
        if (colIndex !== -1) {
          sheet.getColumn(colIndex).hidden = true
        }
      });
    }
  }
}






async function createUserAndBoundaryFile(userSheetData: any, boundarySheetData: any, request: any, schema: any, localizationMap?: { [key: string]: string }, fileUrl?: any) {
  const workbook = getNewExcelWorkbook();
  const localizedUserTab = getLocalizedName(config?.user?.userTab, localizationMap);
  const type = request?.query?.type;
  const headingInSheet = headingMapping?.[type]
  const localisedHeading = getLocalizedName(headingInSheet, localizationMap)
  await createReadMeSheet(request, workbook, localisedHeading, localizationMap);

  const userSheet = workbook.addWorksheet(localizedUserTab);
  addDataToSheet(request, userSheet, userSheetData, undefined, undefined, true, false, localizationMap, fileUrl, schema);
  hideUniqueIdentifierColumn(userSheet, createAndSearch?.["user"]?.uniqueIdentifierColumn);

  let receivedDropdowns = request.body?.dropdowns;
  logger.info("started adding dropdowns in user", JSON.stringify(receivedDropdowns))

  // if (!receivedDropdowns || Object.keys(receivedDropdowns)?.length == 0) {
  //   logger.info("No dropdowns found");
  //   receivedDropdowns = setDropdownFromSchema(request, schema, localizationMap);
  //   logger.info("refetched drodowns", JSON.stringify(receivedDropdowns))
  // }
  await handledropdownthings(userSheet, schema, localizationMap);
  protectSheet(userSheet);
  await handleHiddenColumns(userSheet, request.body?.hiddenColumns);
  // Add boundary sheet to the workbook
  const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap)
  const boundarySheet = workbook.addWorksheet(localizedBoundaryTab);
  addDataToSheet(request, boundarySheet, boundarySheetData, 'F3842D', 30, false, true);

  const fileDetails = await createAndUploadFile(workbook, request)
  request.body.fileDetails = fileDetails;
}


async function generateFacilityAndBoundarySheet(tenantId: string, request: any, localizationMap?: { [key: string]: string }, filteredBoundary?: any, fileUrl?: any) {
  const type = request?.query?.type || request?.body?.ResourceDetails?.type;
  const typeWithoutWith = type.includes('With') ? type.split('With')[0] : type;
  // Get facility and boundary data
  logger.info("Generating facilities started");
  const allFacilities = await getAllFacilities(tenantId);
  request.body.generatedResourceCount = allFacilities?.length;
  logger.info(`Facilities generation completed and found ${allFacilities?.length} facilities`);
  let facilitySheetDataFinal: any;
  const localizedFacilityTab = getLocalizedName(config?.facility?.facilityTab, localizationMap);
  let schemaFinal: any;
  if (fileUrl) {
    /* fetch facility from processed file 
    and generate facility sheet data */
    schemaFinal = await callMdmsTypeSchema(tenantId, true, typeWithoutWith, "all");
    const processedFacilitySheetData = await getSheetData(fileUrl, localizedFacilityTab, false, undefined, localizationMap);
    const modifiedProcessedFacilitySheetData = modifyProcessedSheetData(typeWithoutWith, processedFacilitySheetData, schemaFinal, localizationMap);
    facilitySheetDataFinal = modifiedProcessedFacilitySheetData;
    // setDropdownFromSchema(request, schema, localizationMap);
  }
  else {
    const { schema, facilitySheetData }: any = await createFacilitySheet(request, allFacilities, localizationMap);
    facilitySheetDataFinal = facilitySheetData;
    schemaFinal = schema;
  }
  // request.body.Filters = { tenantId: tenantId, hierarchyType: request?.query?.hierarchyType, includeChildren: true }
  if (filteredBoundary && filteredBoundary.length > 0) {
    logger.info("proceed with the filtered boundary data")
    await createFacilityAndBoundaryFile(facilitySheetDataFinal, filteredBoundary, request, localizationMap, fileUrl, schemaFinal);
  }
  else {
    const boundarySheetData: any = await getBoundarySheetData(request, localizationMap,true);
    await createFacilityAndBoundaryFile(facilitySheetDataFinal, boundarySheetData, request, localizationMap, fileUrl, schemaFinal);
  }
}

function addMultiSelectColumn(properties: any, headers: string[]) {
  const newHeaders: string[] = [];
  for (const header of headers) {
    if (properties?.[header]?.multiSelectDetails) {
      const maxColumns = properties?.[header]?.multiSelectDetails?.maxSelections;
      for (let i = 1; i <= maxColumns; i++) {
        newHeaders.push(`${header}_MULTISELECT_${i}`);
      }
      newHeaders.push(header);
    } else {
      newHeaders.push(header);
    }
  }

  // Clear and replace original array
  headers.length = 0;
  headers.push(...newHeaders);
}

async function generateUserSheet(request: any, localizationMap?: { [key: string]: string }, filteredBoundary?: any, userData?: any, fileUrl?: any) {
  const tenantId = request?.query?.tenantId;
  const type = request?.query?.type || request?.body?.ResourceDetails?.type;
  const typeWithoutWith = type.includes('With') ? type.split('With')[0] : type;
  let schema: any;
  const isUpdate = fileUrl ? true : false;
  schema = await callMdmsTypeSchema(tenantId, isUpdate, typeWithoutWith);
  // setDropdownFromSchema(request, schema, localizationMap);
  const headers = schema?.columns;
  setHiddenColumns(request, schema, localizationMap);
  addMultiSelectColumn(schema?.properties, headers);
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  const localizedUserTab = getLocalizedName(config?.user?.userTab, localizationMap);
  let userSheetData: any;
  // const localizedUserTab = getLocalizedName(config?.user?.userTab, localizationMap);
  logger.info("Generated an empty user template");
  if (fileUrl) {
    /* fetch facility from processed file 
    and generate facility sheet data */
    const processedUserSheetData = await getSheetData(fileUrl, localizedUserTab, false, undefined, localizationMap);
    const modifiedProcessedUserSheetData = modifyProcessedSheetData(typeWithoutWith, processedUserSheetData, schema, localizationMap);
    userSheetData = modifiedProcessedUserSheetData;
  }
  else {
    userSheetData = await createExcelSheet(userData, localizedHeaders);
  }
  if (filteredBoundary && filteredBoundary.length > 0) {
    logger.info("proceed with the filtered boundary data")
    await createUserAndBoundaryFile(userSheetData, filteredBoundary, request, schema, localizationMap, fileUrl);
  }
  else {
    const boundarySheetData: any = await getBoundarySheetData(request, localizationMap,true);
    await createUserAndBoundaryFile(userSheetData, boundarySheetData, request, schema, localizationMap, fileUrl);
  }
}


async function getCustomSheetData(request: any, type: any, sheetName: any) {
  const { RequestInfo = {} } = request?.body || {};
  const requestBody = {
    RequestInfo,
    MdmsCriteria: {
      tenantId: request?.query?.tenantId,
      uniqueIdentifiers: [
        `${sheetName}.${type}`
      ],
      schemaCode: "HCM-ADMIN-CONSOLE.customSheetData"
    }
  };
  const url = config.host.mdmsV2 + config.paths.mdms_v2_search;
  const header = {
    ...defaultheader,
    cachekey: `mdmsv2Seacrh${requestBody?.MdmsCriteria?.tenantId}${sheetName}${type}.${sheetName}${requestBody?.MdmsCriteria?.schemaCode}`
  }
  const response = await httpRequest(url, requestBody, undefined, undefined, undefined, header);
  if (!response?.mdms?.[0]?.data) {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error occured during customSheet config search");
  }
  return response?.mdms?.[0]?.data;
}

function getConvertedSheetData(allRows: any) {
  const headersSet = new Set();
  allRows.forEach((row: any) => {
    Object.keys(row).forEach(header => headersSet.add(header));
  });
  const headers: any = Array.from(headersSet);

  // Map rows to include all headers, filling missing values with ""
  const sheetData = allRows.map((row: any) =>
    headers.map((header: any) => row[header] || "")
  );

  sheetData.unshift(headers);
  return sheetData;
}
async function makeCustomSheetData(request: any, type: any, sheetName: any, workbook: any, localizationMap: any) {
  const data = await getCustomSheetData(request, type, sheetName);
  const sheetNameAfterTranslation = getLocalizedName(sheetName, localizationMap);
  const customSheet = workbook.addWorksheet(sheetNameAfterTranslation);
  const allRows: any = []
  for (const rows of data?.data) {
    const rowData: any = {}
    for (const row of rows) {
      rowData[getLocalizedName(row?.column, localizationMap)] = getLocalizedName(row?.value, localizationMap);
    }
    allRows.push(rowData);
  }
  const sheetData = getConvertedSheetData(allRows);
  addDataToSheet(request, customSheet, sheetData, undefined, undefined);
  customSheet.protect('passwordhere', {
    selectLockedCells: true,
    selectUnlockedCells: true
  });
}

async function generateUserSheetForMicroPlan(
  request: any,
  rolesForMicroplan: string[],
  userData?: any,
  localizationMap?: any,
  fileUrl?: any
) {
  const { tenantId, type } = request?.query;
  const schema = await callMdmsTypeSchema(tenantId, false, "user", "microplan");
  // setDropdownFromSchema(request, schema, localizationMap);
  const headers = schema?.columns;
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);

  logger.info("Generated an empty user template");

  const workbook = getNewExcelWorkbook();
  const userSheetData = await createExcelSheet(userData, localizedHeaders); // Create data only once

  // Create and add ReadMe sheet
  const headingInSheet = headingMapping?.[type];
  const localizedHeading = getLocalizedName(headingInSheet, localizationMap);
  await createReadMeSheet(request, workbook, localizedHeading, localizationMap);


  await makeCustomSheetData(request, request?.query?.type, "USER_MICROPLAN_SHEET_ROLES", workbook, localizationMap);

  // Loop through the rolesForMicroplan array to create sheets for each role
  for (const role of rolesForMicroplan) {
    // Create a sheet for each role, using the role name as the sheet name
    const userSheet: any = workbook.addWorksheet(role);
    addDataToSheet(request, userSheet, userSheetData, undefined, undefined, true, false, localizationMap, fileUrl, schema);
    await handledropdownthings(userSheet, schema, localizationMap);
    protectSheet(userSheet);
    await handleHiddenColumns(userSheet, request.body?.hiddenColumns);
  }

  // Create and upload the workbook file
  const fileDetails = await createAndUploadFile(workbook, request);
  request.body.fileDetails = fileDetails;
}




async function generateUserAndBoundarySheet(request: any, localizationMap?: { [key: string]: string }, filteredBoundary?: any, fileUrl?: any) {
  const userData: any[] = [];
  const tenantId = request?.query?.tenantId;
  const isSourceMicroplan = await isMicroplanRequest(request);
  if (isSourceMicroplan) {
    const rolesForMicroplan = await getRolesForMicroplan(tenantId, localizationMap);
    await generateUserSheetForMicroPlan(request, rolesForMicroplan, userData, localizationMap, fileUrl);
  }
  else {
    await generateUserSheet(request, localizationMap, filteredBoundary, userData, fileUrl);
  }
}

async function processGenerateRequest(request: any, localizationMap?: { [key: string]: string }, filteredBoundary?: any, fileUrl?: string) {
  const { type, tenantId } = request.query
  if (type == "facilityWithBoundary") {
    await generateFacilityAndBoundarySheet(String(tenantId), request, localizationMap, filteredBoundary, fileUrl);
  }
  if (type == "userWithBoundary") {
    await generateUserAndBoundarySheet(request, localizationMap, filteredBoundary, fileUrl);
  }
}

async function processGenerateForNew(request: any, generatedResource: any, newEntryResponse: any, enableCaching = false, filteredBoundary?: any) {
  request.body.generatedResource = newEntryResponse;
  logger.info("Generate flow :: processing new request");
  await fullProcessFlowForNewEntry(newEntryResponse, generatedResource, request, enableCaching, filteredBoundary);
  return request.body.generatedResource;
}

async function handleGenerateError(newEntryResponse: any, generatedResource: any, error: any, tenantId: string) {
  newEntryResponse.map((item: any) => {
    item.status = generatedResourceStatuses.failed, item.additionalDetails = {
      ...item.additionalDetails,
      error: {
        status: error.status,
        code: error.code,
        description: error.description,
        message: error.message
      }
    }
  })
  generatedResource = { generatedResource: newEntryResponse };
  logger.error(String(error));
  await produceModifiedMessages(generatedResource, updateGeneratedResourceTopic, tenantId);
}

async function updateAndPersistGenerateRequest(newEntryResponse: any, oldEntryResponse: any, responseData: any, request: any, enableCaching = false, filteredBoundary?: any) {
  const { forceUpdate } = request.query;
  const forceUpdateBool: boolean = forceUpdate === 'true';
  let generatedResource: any;
  if (forceUpdateBool && responseData.length > 0) {
    generatedResource = { generatedResource: oldEntryResponse };
    // send message to update topic 
    await produceModifiedMessages(generatedResource, updateGeneratedResourceTopic, request?.query?.tenantId);
    request.body.generatedResource = oldEntryResponse;
  }
  if (responseData.length === 0 || forceUpdateBool) {
    processGenerateForNew(request, generatedResource, newEntryResponse, enableCaching, filteredBoundary)
  }
  else {
    request.body.generatedResource = responseData
  }
}
/* 

*/
async function processGenerate(request: any, enableCaching = false, filteredBoundary?: any) {
  // fetch the data from db  to check any request already exists
  const responseData = await searchGeneratedResources(request?.query, getLocaleFromRequestInfo(request?.body?.RequestInfo));
  // modify response from db 
  const modifiedResponse = await enrichAuditDetails(responseData);
  // generate new random id and make filestore id null
  const newEntryResponse = await generateNewRequestObject(request);
  // make old data status as expired
  const oldEntryResponse = await updateExistingResourceExpired(modifiedResponse, request);
  // generate data 
  await updateAndPersistGenerateRequest(newEntryResponse, oldEntryResponse, responseData, request, enableCaching, filteredBoundary);
}
/*
TODO add comments @nitish-egov

*/
async function enrichResourceDetails(request: any) {
  request.body.ResourceDetails.id = uuidv4();
  request.body.ResourceDetails.processedFileStoreId = null;
  if (request?.body?.ResourceDetails?.action == "create") {
    request.body.ResourceDetails.status = resourceDataStatuses.accepted
  }
  else {
    request.body.ResourceDetails.status = resourceDataStatuses.started
  }

  request.body.ResourceDetails.auditDetails = {
    createdBy: request?.body?.RequestInfo?.userInfo?.uuid,
    createdTime: Date.now(),
    lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid,
    lastModifiedTime: Date.now()
  }
  if (request.body.ResourceDetails.type === 'boundary') {
    request.body.ResourceDetails.campaignId = null;
  }
  const persistMessage: any = { ResourceDetails: request.body.ResourceDetails };
  await produceModifiedMessages(persistMessage, config?.kafka?.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC, request?.body?.ResourceDetails?.tenantId);
}

function modifyBoundaryData(boundaryData: any[], localizationMap?: any) {
  // Initialize arrays to store data
  const withBoundaryCode: { key: string, value: string }[][] = [];
  const withoutBoundaryCode: { key: string, value: string }[][] = [];

  // Get the key for the boundary code
  const boundaryCodeKey = getLocalizedName(config?.boundary?.boundaryCode, localizationMap);

  // Process each object in boundaryData
  boundaryData.forEach((obj: any) => {
    // Convert object entries to an array of {key, value} objects
    const row: any = Object.entries(obj)
      .filter(([key, value]: [string, any]) => value !== null && value !== undefined) // Filter out null or undefined values
      .map(([key, value]: [string, any]) => {
        // Check if the current key is the "Boundary Code" key
        if (key === boundaryCodeKey) {
          // Keep the "Boundary Code" value as is without transformation
          return { key, value: value.toString() };
        } else {
          // Transform other values
          return { key, value: value.toString().replace(/_/g, ' ').trim() };
        }
      });

    // Determine whether the object has a boundary code property
    const hasBoundaryCode = obj.hasOwnProperty(boundaryCodeKey);

    // Push the row to the appropriate array based on whether it has a boundary code property
    if (hasBoundaryCode) {
      withBoundaryCode.push(row);
    } else {
      withoutBoundaryCode.push(row);
    }
  });

  // Return the arrays
  return [withBoundaryCode, withoutBoundaryCode];
}

async function getDataFromSheetFromNormalCampaign(type: any, fileStoreId: any, tenantId: any, createAndSearchConfig: any, optionalSheetName?: any, localizationMap?: { [key: string]: string }) {
  const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: tenantId, fileStoreIds: fileStoreId }, "get");
  if (!fileResponse?.fileStoreIds?.[0]?.url) {
    throwError("FILE", 500, "DOWNLOAD_URL_NOT_FOUND");
  }
  if (type == 'boundaryWithTarget') {
    return await getTargetSheetData(fileResponse?.fileStoreIds?.[0]?.url, true, true, localizationMap);
  }
  return await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, createAndSearchConfig?.parseArrayConfig?.sheetName || optionalSheetName, true, createAndSearchConfig, localizationMap)

}

function createHeaderToHierarchyMap(
  sheetHeaders: string[],
  hierarchy: string[]
): { [key: string]: string } {
  const map: { [key: string]: string } = {};
  let hierarchyIndex = 0;

  for (const header of sheetHeaders) {
    if (hierarchyIndex < hierarchy.length) {
      map[header] = hierarchy[hierarchyIndex++];
    }
  }

  return map;
}


async function getDataFromSheet(request: any, fileStoreId: any, tenantId: any, createAndSearchConfig: any, optionalSheetName?: any, localizationMap?: { [key: string]: string }) {
  const isSourceMicroplan = request?.body?.ResourceDetails?.additionalDetails?.source == "microplan";
  const type = request?.body?.ResourceDetails?.type;
  if (isSourceMicroplan) {
    if (type == 'user') {
      return await getUserDataFromMicroplanSheet(request, fileStoreId, tenantId, createAndSearchConfig, localizationMap);
    }
    else {
      return await getDataFromSheetFromNormalCampaign(type, fileStoreId, tenantId, createAndSearchConfig, optionalSheetName, localizationMap);
    }
  }
  else {
    return await getDataFromSheetFromNormalCampaign(type, fileStoreId, tenantId, createAndSearchConfig, optionalSheetName, localizationMap);
  }
}

async function getDataSheetReady(boundaryData: any, request: any, localizationMap?: { [key: string]: string }) {
  const type = request?.query?.type;
  const boundaryType = boundaryData?.[0].boundaryType;
  const boundaryList = generateHierarchyList(boundaryData)
  const locale = getLocaleFromRequest(request);
  const region = locale.split('_')[1];
  const frenchMessagesMap: any = await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(request?.query?.hierarchyType), true, `fr_${region}`);
  const portugeseMessagesMap: any = await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(request?.query?.hierarchyType), true, `pt_${region}`);
  if (!Array.isArray(boundaryList) || boundaryList.length === 0) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary list is empty or not an array.");
  }

  const hierarchy = await getHierarchy(request?.query?.tenantId, request?.query?.hierarchyType);
  const startIndex = boundaryType ? hierarchy.indexOf(boundaryType) : -1;
  const reducedHierarchy = startIndex !== -1 ? hierarchy.slice(startIndex) : hierarchy;
  const modifiedReducedHierarchy = getLocalizedHeaders(reducedHierarchy.map(ele => `${request?.query?.hierarchyType}_${ele}`.toUpperCase()), localizationMap);
  // get Campaign Details from Campaign Search Api
  var configurableColumnHeadersBasedOnCampaignType: any[] = []
  if (type == "boundary") {
    configurableColumnHeadersBasedOnCampaignType = await getConfigurableColumnHeadersBasedOnCampaignType(request, localizationMap);
  }
  if (type == "boundaryManagement" || type == "boundaryGeometryManagement") {
    configurableColumnHeadersBasedOnCampaignType = await getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement(request, localizationMap);
  }
  const headers = (type !== "facilityWithBoundary" && type !== "userWithBoundary")
    ? [
      ...modifiedReducedHierarchy,
      ...configurableColumnHeadersBasedOnCampaignType
    ]
    : [
      ...modifiedReducedHierarchy,
      getBoundaryColumnName()
    ];
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  var boundaryCodeList: any[] = [];
  var data = boundaryList.map(boundary => {
    const boundaryParts = boundary.split(',');
    const boundaryCode = boundaryParts[boundaryParts.length - 1];
    boundaryCodeList.push(boundaryCode);
    const rowData = boundaryParts.concat(Array(Math.max(0, reducedHierarchy.length - boundaryParts.length)).fill(''));
    // localize the boundary codes
    const mappedRowData = rowData.map((cell: any, index: number) =>
      index === reducedHierarchy.length ? '' : cell !== '' ? getLocalizedName(cell, localizationMap) : ''
    );
    const boundaryCodeIndex = reducedHierarchy.length;
    mappedRowData[boundaryCodeIndex] = boundaryCode;
    if (type === "boundaryManagement") {
      const frenchTranslation = getLocalizedNameOnlyIfMessagePresent(boundaryCode, frenchMessagesMap) || '';
      const portugeseTranslation = getLocalizedNameOnlyIfMessagePresent(boundaryCode, portugeseMessagesMap) || '';
      mappedRowData.push(frenchTranslation);
      mappedRowData.push(portugeseTranslation);
    }
    return mappedRowData;
  });
  if (type == "boundaryManagement") {
    logger.info("Processing data for boundaryManagement type")
    const latLongBoundaryMap = await getLatLongMapForBoundaryCodes(request, boundaryCodeList);
    for (let d of data) {
      const boundaryCode = d[d.length - 1];  // Assume last element is the boundary code

      if (latLongBoundaryMap[boundaryCode]) {
        const [latitude = null, longitude = null] = latLongBoundaryMap[boundaryCode];  // Destructure lat/long
        d.push(latitude);   // Append latitude
        d.push(longitude);  // Append longitude
      }
    }
  }
  const sheetRowCount = data.length;
  if (type != "facilityWithBoundary") {
    request.body.generatedResourceCount = sheetRowCount;
  }
  return await createExcelSheet(data, localizedHeaders);
}

function modifyDataBasedOnDifferentTab(boundaryData: any, differentTabsBasedOnLevel: any, localizedHeadersForMainSheet: any, localizationMap?: any) {
  const newData: any = {};

  for (const key of localizedHeadersForMainSheet) {
    newData[key] = boundaryData[key] || '';
    if (key === differentTabsBasedOnLevel) break;
  }

  const localizedBoundaryCode = getLocalizedName(getBoundaryColumnName(), localizationMap);
  newData[localizedBoundaryCode] = boundaryData[localizedBoundaryCode] || '';

  return newData;
}


async function getLocalizedMessagesHandler(request: any, tenantId: any, module = config.localisation.localizationModule, overrideCache = false, locale?: string) {
  const localisationcontroller = Localisation.getInstance();
  if (!locale) {
    locale = getLocaleFromRequest(request);
  }
  const localizationResponse = await localisationcontroller.getLocalisedData(module, locale, tenantId, overrideCache);
  return localizationResponse;
}

async function getLocalizedMessagesHandlerViaLocale(locale: string, tenantId: any, module = config.localisation.localizationModule, overrideCache = false) {
  const localisationcontroller = Localisation.getInstance();
  const localizationResponse = await localisationcontroller.getLocalisedData(module, locale, tenantId, overrideCache);
  return localizationResponse;
}



async function translateSchema(
  schema: any,
  localizationMap?: { [key: string]: string }) {
  const translatedSchema = {
    ...schema,
    properties: Object.entries(schema?.properties || {}).reduce((acc, [key, value]) => {
      const localizedMessage = getLocalizedName(key, localizationMap);
      acc[localizedMessage] = value;
      return acc;
    }, {} as { [key: string]: any }),

    required: (schema?.required || [])
      .map((key: string) => getLocalizedName(key, localizationMap)),

    unique: (schema?.unique || [])
      .map((key: string) => getLocalizedName(key, localizationMap))
  };

  return translatedSchema;
}


function findMapValue(map: Map<any, any>, key: any): any | null {
  let foundValue = null;
  map.forEach((value, mapKey) => {
    if (mapKey.key === key.key && mapKey.value === key.value) {
      foundValue = value;
    }
  });
  return foundValue;
}

function extractFrenchOrPortugeseLocalizationMap(
  boundaryData: any[][],
  isFrench: boolean,
  isPortugese: boolean,
  localizationMap: any
): Map<{ key: string; value: string }, string> {
  const resultMap = new Map<{ key: string; value: string }, string>();

  boundaryData.forEach(row => {
    const boundaryCodeObj = row.find(obj => obj.key === getLocalizedName(config?.boundary?.boundaryCode, localizationMap));
    const boundaryCode = boundaryCodeObj?.value;

    if (!boundaryCode) return;

    if (isFrench) {
      const frenchMessageObj = row.find(obj => obj.key === getLocalizedName("HCM_ADMIN_CONSOLE_FRENCH_LOCALIZATION_MESSAGE", localizationMap));
      resultMap.set({
        key: "french",
        value: frenchMessageObj?.value || ""
      }, boundaryCode);
    } else if (isPortugese) {
      const portugeseMessageObj = row.find(obj => obj.key === getLocalizedName("HCM_ADMIN_CONSOLE_PORTUGESE_LOCALIZATION_MESSAGE", localizationMap));
      resultMap.set({
        key: "portugese",
        value: portugeseMessageObj?.value || ""
      }, boundaryCode);
    }
  });

  return resultMap;
}


function getDifferentDistrictTabs(boundaryData: any, differentTabsBasedOnLevel: any) {
  const uniqueDistrictsForMainSheet: string[] = [];
  const differentDistrictTabs: any[] = [];
  for (const data of boundaryData) {
    const rowData = Object.values(data);
    const districtValue = data[differentTabsBasedOnLevel];
    const districtIndex = districtValue !== '' ? rowData.indexOf(districtValue) : -1;

    if (districtIndex != -1) {
      const districtLevelRow = rowData.slice(0, districtIndex + 1);
      const districtKey = districtLevelRow.join('#');

      if (!uniqueDistrictsForMainSheet.includes(districtKey)) {
        uniqueDistrictsForMainSheet.push(districtKey);
      }
    }
  }
  for (const uniqueData of uniqueDistrictsForMainSheet) {
    differentDistrictTabs.push(uniqueData.slice(uniqueData.lastIndexOf('#') + 1));
  }
  return differentDistrictTabs;
}


async function getConfigurableColumnHeadersFromSchemaForTargetSheet(request: any, hierarchy: any, boundaryData: any, differentTabsBasedOnLevel: any, campaignObject: any, localizationMap?: any) {
  const districtIndex = hierarchy.indexOf(differentTabsBasedOnLevel);
  let headers: any;
  const isSourceMicroplan = checkIfSourceIsMicroplan(campaignObject);
  if (isSourceMicroplan) {
    logger.info(`Source is Microplan.`);
    headers = getLocalizedHeaders(hierarchy, localizationMap);
  }
  else {
    headers = getLocalizedHeaders(hierarchy.slice(districtIndex), localizationMap);
  }
  const headerColumnsAfterHierarchy = await generateDynamicTargetHeaders(request, campaignObject, localizationMap);
  const localizedHeadersAfterHierarchy = getLocalizedHeaders(headerColumnsAfterHierarchy, localizationMap);
  headers = [...headers, getLocalizedName(config?.boundary?.boundaryCode, localizationMap), ...localizedHeadersAfterHierarchy]
  return getLocalizedHeaders(headers, localizationMap);
}


async function getMdmsDataBasedOnCampaignType(request: any, localizationMap?: any) {
  const responseFromCampaignSearch = await getCampaignSearchResponse(request);
  const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
  let campaignType = campaignObject.projectType;
  const isSourceMicroplan = checkIfSourceIsMicroplan(campaignObject);
  campaignType = (isSourceMicroplan) ? `${config?.prefixForMicroplanCampaigns}-${campaignType}` : campaignType;
  const mdmsResponse = await callMdmsTypeSchema(request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId, false, request?.query?.type || request?.body?.ResourceDetails?.type, campaignType)
  return mdmsResponse;
}


function appendProjectTypeToCapacity(schema: any, projectType: string): any {
  const updatedSchema = JSON.parse(JSON.stringify(schema)); // Deep clone the schema

  const capacityKey = 'HCM_ADMIN_CONSOLE_FACILITY_CAPACITY_MICROPLAN';
  const newCapacityKey = `${capacityKey}_${projectType}`;

  // Update properties
  if (updatedSchema.properties[capacityKey]) {
    updatedSchema.properties[newCapacityKey] = {
      ...updatedSchema.properties[capacityKey],
      name: `${updatedSchema.properties[capacityKey].name}_${projectType}`
    };
    delete updatedSchema.properties[capacityKey];
  }

  // Update required
  updatedSchema.required = updatedSchema.required.map((item: string) =>
    item === capacityKey ? newCapacityKey : item
  );

  // Update columns
  updatedSchema.columns = updatedSchema.columns.map((item: string) =>
    item === capacityKey ? newCapacityKey : item
  );

  // Update unique
  updatedSchema.unique = updatedSchema.unique.map((item: string) =>
    item === capacityKey ? newCapacityKey : item
  );

  // Update errorMessage
  if (updatedSchema.errorMessage[capacityKey]) {
    updatedSchema.errorMessage[newCapacityKey] = updatedSchema.errorMessage[capacityKey];
    delete updatedSchema.errorMessage[capacityKey];
  }

  // Update columnsNotToBeFreezed
  updatedSchema.columnsNotToBeFreezed = updatedSchema.columnsNotToBeFreezed.map((item: string) =>
    item === capacityKey ? newCapacityKey : item
  );

  return updatedSchema;
}

function modifyBoundaryDataHeadersWithMap(
  boundaryData: any[],
  headerToHierarchyMap: { [originalHeader: string]: string }
) {
  return boundaryData.map((row) => {
    const updatedRow: { [key: string]: any } = {};

    for (const key in row) {
      if (Object.prototype.hasOwnProperty.call(row, key)) {
        const newKey = headerToHierarchyMap[key];
        updatedRow[newKey || key] = row[key];
      }
    }

    return updatedRow;
  });
}

export async function getRelatedDataWithCampaign(type: string, campaignNumber: string, tenantId: string, status ?: string, uniqueIdentifier ?: string) {
  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, tenantId);
  let queryString = `SELECT * FROM ${tableName} WHERE type = $1 AND campaignNumber = $2`;
  if(status) queryString += ` AND status = $3`;
  const arrayStatements = [type, campaignNumber];
  if(status) arrayStatements.push(status);
  if(uniqueIdentifier) queryString += ` AND uniqueIdentifier = $4`;
  if(uniqueIdentifier) arrayStatements.push(uniqueIdentifier);
  let relatedData = await executeQuery(queryString, arrayStatements);
  if(!relatedData?.rows) return [];
  let rows = [];
  for(let i = 0; i < relatedData?.rows?.length; i++) {
    rows.push({
      campaignNumber : relatedData?.rows[i]?.campaignnumber,
      type : relatedData?.rows[i]?.type,
      data : relatedData?.rows[i]?.data,
      uniqueIdentifier : relatedData?.rows[i]?.uniqueidentifier,
      status : relatedData?.rows[i]?.status,
      uniqueIdAfterProcess : relatedData?.rows[i]?.uniqueidafterprocess
    })
  }
  return rows;
}

export async function getRelatedDataWithUniqueIdentifiers(type : string, uniqueIdentifiers : any[], tenantId : string, status ?: string ){
  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, tenantId);
  let queryString = `SELECT * FROM ${tableName} WHERE type = $1`;
  if (uniqueIdentifiers?.length > 0) queryString += ` AND uniqueIdentifier = ANY($2)`;
  if (status) queryString += ` AND status = $3`;
  const arrayStatements = [type, uniqueIdentifiers];
  if (status) arrayStatements.push(status);
  let relatedData = await executeQuery(queryString, arrayStatements);
  if (!relatedData?.rows) return [];
  let rows = [];
  for (let i = 0; i < relatedData?.rows?.length; i++) {
    rows.push({
      campaignNumber: relatedData?.rows[i]?.campaignnumber,
      type: relatedData?.rows[i]?.type,
      data: relatedData?.rows[i]?.data,
      uniqueIdentifier: relatedData?.rows[i]?.uniqueidentifier,
      status: relatedData?.rows[i]?.status,
      uniqueIdAfterProcess: relatedData?.rows[i]?.uniqueidafterprocess
    })
  }
  return rows;
}

export async function getMappingDataRelatedToCampaign(type: string, campaignNumber: string,tenantId: string, status?: string) {
  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME, tenantId);
  let queryString = `SELECT * FROM ${tableName} WHERE type = $1 AND campaignNumber = $2`;
  if (status) queryString += ` AND status = $3`;
  const arrayStatements = [type, campaignNumber];
  if (status) arrayStatements.push(status);
  let relatedData = await executeQuery(queryString, arrayStatements);
  if (!relatedData?.rows) return [];
  let rows = [];
  for (let i = 0; i < relatedData?.rows?.length; i++) {
    rows.push({
      campaignNumber: relatedData?.rows[i]?.campaignnumber,
      type: relatedData?.rows[i]?.type,
      boundaryCode: relatedData?.rows[i]?.boundarycode,
      uniqueIdentifierForData: relatedData?.rows[i]?.uniqueidentifierfordata,
      status: relatedData?.rows[i]?.status,
      mappingId: relatedData?.rows[i]?.mappingid
    })
  }
  return rows;
}

export async function getCurrentProcesses(campaignNumber: string, tenantId: string, processName ?: string, status ?: string) {
  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_PROCESS_DATA_TABLE_NAME, tenantId);
  let queryString = `SELECT * FROM ${tableName} WHERE campaignNumber = $1`;
  if (processName) queryString += ` AND processName = $2`;
  if (status) queryString += ` AND status = $3`;
  const arrayStatements = [campaignNumber];
  if (processName) arrayStatements.push(processName);
  if (status) arrayStatements.push(status);
  let relatedData = await executeQuery(queryString, arrayStatements);
  if (!relatedData?.rows) return [];
  let rows = [];
  for (let i = 0; i < relatedData?.rows?.length; i++) {
    rows.push({
      campaignNumber: relatedData?.rows[i]?.campaignnumber,
      processName : relatedData?.rows[i]?.processname,
      status: relatedData?.rows[i]?.status
    })
  }
  return rows;
}

export async function getCampaignDataRowsWithUniqueIdentifiers(type: string, uniqueIdentifiers: any[], tenantId: string, status ?: string) {
  if(uniqueIdentifiers?.length === 0) return [];
  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, tenantId);
  let queryString = `SELECT * FROM ${tableName} WHERE type = $1 AND uniqueIdentifier = ANY($2)`;
  if(status) queryString += ` AND status = $3`;
  const arrayStatements = [type, uniqueIdentifiers];
  if(status) arrayStatements.push(status);
  let relatedData = await executeQuery(queryString, arrayStatements);
  if(!relatedData?.rows) return [];
  let rows = [];
  for(let i = 0; i < relatedData?.rows?.length; i++) {
    rows.push({
      campaignNumber : relatedData?.rows[i]?.campaignnumber,
      type : relatedData?.rows[i]?.type,
      data : relatedData?.rows[i]?.data,
      uniqueIdentifier : relatedData?.rows[i]?.uniqueidentifier,
      status : relatedData?.rows[i]?.status,
      uniqueIdAfterProcess : relatedData?.rows[i]?.uniqueidafterprocess
    })
  }
  return rows;
}


export async function prepareProcessesInDb(campaignNumber: any, tenantId: string) {
  logger.info("Preparing processes in DB...");
  let allCurrentProcesses = await getCurrentProcesses(campaignNumber, tenantId);
  for (let i = 0; i < allCurrentProcesses?.length; i++) {
      allCurrentProcesses[i].status = processStatuses.pending;
  }
  produceModifiedMessages({ processes: allCurrentProcesses }, config.kafka.KAFKA_UPDATE_PROCESS_DATA_TOPIC, tenantId);
  let allProcessesJson: any = JSON.parse(JSON.stringify(allProcesses))
  let newProcesses = [];
  for (let processKey in allProcesses) {
    let isProcessNameAvailableInAllCurrentProcesses = allCurrentProcesses.find((process: any) => process?.processName == allProcessesJson[processKey]);
    if (!isProcessNameAvailableInAllCurrentProcesses) {
      newProcesses.push({
        campaignNumber: campaignNumber,
        processName: allProcessesJson[processKey],
        status: processStatuses.pending
      })
    }
  }
  produceModifiedMessages({ processes: newProcesses }, config.kafka.KAFKA_SAVE_PROCESS_DATA_TOPIC, tenantId);
  // wait for 2 second
  logger.info("Waiting for 10 seconds for processes to get updated...");
  await new Promise(resolve => setTimeout(resolve, 10000));
}

/**
 * Build base query for campaign data with WHERE conditions
 */
function buildCampaignDataBaseQuery(searchParams: {
  tenantId: string;
  type?: string;
  campaignNumber?: string;
  status?: string;
  uniqueIdentifiers?: string[];
}) {
  const { tenantId, type, campaignNumber, status, uniqueIdentifiers } = searchParams;

  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, tenantId);

  let whereClause = `FROM ${tableName} WHERE 1=1`;
  const queryParams: any[] = [];
  let paramIndex = 1;

  if (type) {
    whereClause += ` AND type = $${paramIndex}`;
    queryParams.push(type);
    paramIndex++;
  }

  if (campaignNumber) {
    whereClause += ` AND campaignNumber = $${paramIndex}`;
    queryParams.push(campaignNumber);
    paramIndex++;
  }

  if (status) {
    whereClause += ` AND status = $${paramIndex}`;
    queryParams.push(status);
    paramIndex++;
  }

  if (uniqueIdentifiers && uniqueIdentifiers.length > 0) {
    whereClause += ` AND uniqueIdentifier = ANY($${paramIndex})`;
    queryParams.push(uniqueIdentifiers);
    paramIndex++;
  }

  return { whereClause, queryParams, paramIndex };
}

/**
 * Build base query for mapping data with WHERE conditions
 */
function buildMappingDataBaseQuery(searchParams: {
  tenantId: string;
  type?: string;
  campaignNumber?: string;
  status?: string;
  boundaryCode?: string;
  uniqueIdentifierForData?: string;
}) {
  const { tenantId, type, campaignNumber, status, boundaryCode, uniqueIdentifierForData } = searchParams;

  const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME, tenantId);

  let whereClause = `FROM ${tableName} WHERE 1=1`;
  const queryParams: any[] = [];
  let paramIndex = 1;

  if (type) {
    whereClause += ` AND type = $${paramIndex}`;
    queryParams.push(type);
    paramIndex++;
  }

  if (campaignNumber) {
    whereClause += ` AND campaignNumber = $${paramIndex}`;
    queryParams.push(campaignNumber);
    paramIndex++;
  }

  if (status) {
    whereClause += ` AND status = $${paramIndex}`;
    queryParams.push(status);
    paramIndex++;
  }

  if (boundaryCode) {
    whereClause += ` AND boundaryCode = $${paramIndex}`;
    queryParams.push(boundaryCode);
    paramIndex++;
  }

  if (uniqueIdentifierForData) {
    whereClause += ` AND uniqueIdentifierForData = $${paramIndex}`;
    queryParams.push(uniqueIdentifierForData);
    paramIndex++;
  }

  return { whereClause, queryParams, paramIndex };
}

/**
 * Search campaign data with optional pagination
 */
export async function searchCampaignData(searchParams: {
  tenantId: string;
  type?: string;
  campaignNumber?: string;
  status?: string;
  uniqueIdentifiers?: string[];
  offset?: number;
  limit?: number;
}) {
  const { offset, limit } = searchParams;

  // Build base query
  const { whereClause, queryParams, paramIndex } = buildCampaignDataBaseQuery(searchParams);

  // Build data query with ordering
  let dataQuery = `SELECT * ${whereClause}`;
  let finalParams = [...queryParams];

  // Add pagination if provided
  if (offset !== undefined && limit !== undefined) {
    dataQuery += ` LIMIT $${paramIndex} OFFSET $${paramIndex + 1}`;
    finalParams.push(limit, offset);
  }

  // Build count query
  const countQuery = `SELECT COUNT(*) ${whereClause}`;

  // Execute both queries
  const [dataResult, countResult] = await Promise.all([
    executeQuery(dataQuery, finalParams),
    executeQuery(countQuery, queryParams)
  ]);

  const totalCount = parseInt(countResult?.rows?.[0]?.count || '0');

  // Transform results
  const rows = dataResult?.rows?.map((row: any) => ({
    id: row?.id,
    campaignNumber: row?.campaignnumber,
    type: row?.type,
    data: row?.data,
    uniqueIdentifier: row?.uniqueidentifier,
    status: row?.status,
    uniqueIdAfterProcess: row?.uniqueidafterprocess,
    createdBy: row?.createdby,
    createdTime: row?.createdtime,
    lastModifiedBy: row?.lastmodifiedby,
    lastModifiedTime: row?.lastmodifiedtime
  })) || [];

  const result: any = {
    data: rows,
    totalCount
  };

  // Add pagination info if pagination was used
  if (offset !== undefined && limit !== undefined) {
    result.pagination = {
      offset,
      limit,
      totalPages: Math.ceil(totalCount / limit),
      currentPage: Math.floor(offset / limit) + 1
    };
  }

  return result;
}

/**
 * Search mapping data with optional pagination
 */
export async function searchMappingData(searchParams: {
  tenantId: string;
  type?: string;
  campaignNumber?: string;
  status?: string;
  boundaryCode?: string;
  uniqueIdentifierForData?: string;
  offset?: number;
  limit?: number;
}) {
  const { offset, limit } = searchParams;

  // Build base query
  const { whereClause, queryParams, paramIndex } = buildMappingDataBaseQuery(searchParams);

  // Build data query with ordering
  let dataQuery = `SELECT * ${whereClause}`;
  let finalParams = [...queryParams];

  // Add pagination if provided
  if (offset !== undefined && limit !== undefined) {
    dataQuery += ` LIMIT $${paramIndex} OFFSET $${paramIndex + 1}`;
    finalParams.push(limit, offset);
  }

  // Build count query
  const countQuery = `SELECT COUNT(*) ${whereClause}`;

  // Execute both queries
  const [dataResult, countResult] = await Promise.all([
    executeQuery(dataQuery, finalParams),
    executeQuery(countQuery, queryParams)
  ]);

  const totalCount = parseInt(countResult?.rows?.[0]?.count || '0');

  // Transform results
  const rows = dataResult?.rows?.map((row: any) => ({
    id: row?.id,
    campaignNumber: row?.campaignnumber,
    type: row?.type,
    boundaryCode: row?.boundarycode,
    uniqueIdentifierForData: row?.uniqueidentifierfordata,
    status: row?.status,
    mappingId: row?.mappingid,
    createdBy: row?.createdby,
    createdTime: row?.createdtime,
    lastModifiedBy: row?.lastmodifiedby,
    lastModifiedTime: row?.lastmodifiedtime
  })) || [];

  const result: any = {
    data: rows,
    totalCount
  };

  // Add pagination info if pagination was used
  if (offset !== undefined && limit !== undefined) {
    result.pagination = {
      offset,
      limit,
      totalPages: Math.ceil(totalCount / limit),
      currentPage: Math.floor(offset / limit) + 1
    };
  }

  return result;
}



/**
 * Fast check for campaign data completion status
 * Returns: { allCompleted: boolean, anyFailed: boolean, totalRows: number, completedRows: number, failedRows: number }
 */
export async function checkCampaignDataCompletionStatus(campaignNumber: string, tenantId: string) {
  try {
    const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, tenantId);
    
    // Fast query to get status counts - only select minimal fields for performance
    const queryString = `
      SELECT 
        status,
        COUNT(*) as count
      FROM ${tableName} 
      WHERE campaignNumber = $1 
      GROUP BY status
    `;
    
    const result = await executeQuery(queryString, [campaignNumber]);
    
    let totalRows = 0;
    let completedRows = 0;
    let failedRows = 0;
    
    // Process the grouped results using constants
    result?.rows?.forEach((row: any) => {
      const count = parseInt(row.count);
      totalRows += count;
      
      if (row.status === resourceDataStatuses.completed) {
        completedRows = count;
      } else if (row.status === resourceDataStatuses.failed) {
        failedRows = count;
      }
    });
    
    const allCompleted = totalRows > 0 && completedRows === totalRows;
    const anyFailed = failedRows > 0;
    
    return {
      allCompleted,
      anyFailed,
      totalRows,
      completedRows,
      failedRows,
      pendingRows: totalRows - completedRows - failedRows
    };
    
  } catch (error) {
    logger.error('Error checking campaign data completion status:', error);
    throw error;
  }
}

/**
 * Fast check for campaign mapping completion status
 * Returns: { allCompleted: boolean, anyFailed: boolean, totalMappings: number, completedMappings: number, failedMappings: number }
 */
export async function checkCampaignMappingCompletionStatus(campaignNumber: string, tenantId: string) {
  try {
    const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME, tenantId);
    
    // Fast query to get mapping status counts - only select minimal fields for performance
    const queryString = `
      SELECT 
        status,
        COUNT(*) as count
      FROM ${tableName} 
      WHERE campaignNumber = $1 
      GROUP BY status
    `;
    
    const result = await executeQuery(queryString, [campaignNumber]);
    
    let totalMappings = 0;
    let completedMappings = 0;
    let failedMappings = 0;
    
    // Process the grouped results using constants
    result.rows.forEach((row: any) => {
      const count = parseInt(row.count);
      totalMappings += count;
      
      if (row.status === mappingStatuses.mapped || row.status === mappingStatuses.deMapped) {
        completedMappings += count;
      } else if (row.status === mappingStatuses.failed) {
        failedMappings += count;
      }
    });
    
    const allCompleted = totalMappings > 0 && completedMappings === totalMappings;
    const anyFailed = failedMappings > 0;
    
    return {
      allCompleted,
      anyFailed,
      totalMappings,
      completedMappings,
      failedMappings,
      pendingMappings: totalMappings - completedMappings - failedMappings
    };
    
  } catch (error) {
    logger.error('Error checking campaign mapping completion status:', error);
    throw error;
  }
}

export {
  errorResponder,
  errorLogger,
  invalidPathHandler,
  getResponseInfo,
  throwError,
  throwErrorViaRequest,
  sendResponse,
  appCache,
  searchGeneratedResources,
  generateNewRequestObject,
  updateExistingResourceExpired,
  getFinalUpdatedResponse,
  fullProcessFlowForNewEntry,
  correctParentValues,
  sortCampaignDetails,
  processGenerateRequest,
  processGenerate,
  getDataFromSheet,
  enrichResourceDetails,
  modifyBoundaryData,
  searchAllGeneratedResources,
  getDataSheetReady,
  modifyDataBasedOnDifferentTab,
  translateSchema,
  getLocalizedMessagesHandler,
  getLocalizedHeaders,
  createReadMeSheet,
  findMapValue,
  replicateRequest,
  getDifferentDistrictTabs,
  addDataToSheet,
  changeFirstRowColumnColour,
  getConfigurableColumnHeadersFromSchemaForTargetSheet,
  createBoundaryDataMainSheet,
  getMdmsDataBasedOnCampaignType,
  shutdownGracefully,
  appendProjectTypeToCapacity,
  createFacilityAndBoundaryFile,
  hideUniqueIdentifierColumn,
  createHeaderToHierarchyMap,
  modifyBoundaryDataHeadersWithMap,
  extractFrenchOrPortugeseLocalizationMap,
  getLocalizedMessagesHandlerViaLocale
};
