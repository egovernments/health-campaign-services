import { NextFunction, Request, Response } from "express";
import { logger } from "./logger";
import { getLocaleFromRequest , getLocaleFromRequestInfo ,getLocalisationModuleName} from "./localisationUtils";
import Localisation from "../controllers/localisationController/localisation.controller";
import config from "../config/index";
import { getErrorCodes ,generatedResourceStatuses} from "../config/constants";
import { v4 as uuidv4 } from 'uuid';
import { resourceDataStatuses } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import {generateHierarchyList} from "../api/boundaryApis";
import {getLocalizedName,getHierarchy , getLocalizedNameOnlyIfMessagePresent,getLatLongMapForBoundaryCodes} from "../utils/boundaryUtils";
import { generatedResourceTransformer } from "./transforms/searchResponseConstructor";
import {getBoundaryDataService} from "../services/boundaryManagementService";
import {getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement , createExcelSheet} from "../api/genericApis";
import {getTableName,executeQuery} from "../utils/db";
const NodeCache = require("node-cache");
import _ from "lodash";
const updateGeneratedResourceTopic = config?.kafka?.KAFKA_UPDATE_GENERATED_BOUNDARY_MANAGEMENT_TOPIC;
const createGeneratedResourceTopic = config?.kafka?.KAFKA_CREATE_GENERATED_BOUNDARY_MANAGEMENT_TOPIC;



/*
  stdTTL: (default: 0) the standard ttl as number in seconds for every generated
   cache element. 0 = unlimited

  checkperiod: (default: 600) The period in seconds, as a number, used for the automatic
   delete check interval. 0 = no periodic check.

   30 mins caching
*/
const appCache = new NodeCache({ stdTTL: 1800000, checkperiod: 300 });


/*
Error handling Middleware function reads the error message and sends back a response in JSON format
*/
export const errorResponder = (
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
  response.setHeader("Content-Type", "application/json");
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

async function getLocalizedMessagesHandler(request: any, tenantId: any, module = config.localisation.localizationModule, overrideCache = false, locale?: string) {
  const localisationcontroller = Localisation.getInstance();
  if (!locale) {
    locale = getLocaleFromRequest(request);
  }
  const localizationResponse = await localisationcontroller.getLocalisedData(module, locale, tenantId, overrideCache);
  return localizationResponse;
}

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

function getLocalizedHeaders(headers: any, localizationMap?: { [key: string]: string }) {
  const messages = headers.map((header: any) => (localizationMap ? localizationMap[header] || header : header));
  return messages;
}

/**
 * Enrich the resource details with uuid, processed file store id, status, audit details, type and campaignId.
 * 
 * @param {any} request - The request object containing the resource details to be enriched.
 * 
 * @returns {Promise<any>} - A promise containing the enriched resource details.
 */
async function enrichResourceDetails(request: any) {
  request.body.ResourceDetails.id = uuidv4();
  request.body.ResourceDetails.processedFileStoreId = null;
  request.body.ResourceDetails.referenceId = request?.body?.ResourceDetails?.referenceId || null;
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

  const persistMessage: any = { ResourceDetails: request.body.ResourceDetails };
  await produceModifiedMessages(persistMessage, config?.kafka?.KAFKA_CREATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC, request?.body?.ResourceDetails?.tenantId);
}
function shutdownGracefully() {
  logger.info('Shutting down gracefully...');
  // Perform any cleanup tasks here, like closing database connections
  process.exit(1); // Exit with a non-zero code to indicate an error
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

/**
 * Process generate request
 * 1. Fetch existing data from db
 * 2. Modify existing data with audit details
 * 3. Generate new random id and make filestore id null
 * 4. Update existing data with expired status
 * 5. Generate new data
 * 6. Update and persist generate request
 * 
 * @param request - request object
 * @param enableCaching - whether to enable caching or not
 * @param filteredBoundary - optional parameter to filter out boundaries
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

async function updateExistingResourceExpired(modifiedResponse: any[], request: any) {
  return modifiedResponse.map((item: any) => {
    const newItem = { ...item };
    newItem.status = generatedResourceStatuses.expired;
    newItem.auditDetails.lastModifiedTime = Date.now();
    newItem.referenceId = newItem?.referenceId || null;
    newItem.auditDetails.lastModifiedBy = request?.body?.RequestInfo?.userInfo?.uuid;
    return newItem;
  });
}

const replicateRequest = (originalRequest: Request, requestBody: any, requestQuery?: any) => {
  const newRequest = {
    ...originalRequest,
    body: _.cloneDeep(requestBody), // Deep clone using lodash
    query: requestQuery ? _.cloneDeep(requestQuery) : _.cloneDeep(originalRequest.query)
  };
  return newRequest;
};

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

async function processGenerateForNew(request: any, generatedResource: any, newEntryResponse: any, enableCaching = false, filteredBoundary?: any) {
  request.body.generatedResource = newEntryResponse;
  logger.info("Generate flow :: processing new request");
  await fullProcessFlowForNewEntry(newEntryResponse, generatedResource, request, enableCaching, filteredBoundary);
  return request.body.generatedResource;
}

async function fullProcessFlowForNewEntry(newEntryResponse: any, generatedResource: any, request: any, enableCaching = false, filteredBoundary?: any) {
  const tenantId = request?.query?.tenantId;
  try {
    const { hierarchyType } = request?.query;
    generatedResource = { generatedResource: newEntryResponse }
    // send message to create toppic
    logger.info(`processing the generate request for type ${hierarchyType}`)
    await produceModifiedMessages(generatedResource, createGeneratedResourceTopic, request?.query?.tenantId);

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
  catch (error: any) {
    console.log(error)
    await handleGenerateError(newEntryResponse, generatedResource, error, tenantId);
  }
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

async function generateNewRequestObject(request: any) {
  const additionalDetails = {};
  const newEntry = {
    id: uuidv4(),
    fileStoreid: null,
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
    referenceId : request?.query?.referenceId ? request.query.referenceId : null,
    locale: request?.body?.RequestInfo?.msgId?.split('|')[1] || null
  };
  return [newEntry];
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

/* Fetches data from the database */
async function searchGeneratedResources(searchQuery: any, locale: any) {
  try {
    const { tenantId, hierarchyType, id, status , referenceId} = searchQuery;
    const tableName = getTableName(config?.DB_CONFIG.DB_GENERATED_TEMPLATE_TABLE_NAME, tenantId);
    let queryString = `SELECT * FROM ${tableName} WHERE `;
    let queryConditions: string[] = [];
    let queryValues: any[] = [];
    if (id) {
      queryConditions.push(`id = $${queryValues.length + 1}`);
      queryValues.push(id);
    }
    // if (type) {
    //   queryConditions.push(`type = $${queryValues.length + 1}`);
    //   queryValues.push(type);
    // }

    if (hierarchyType) {
      queryConditions.push(`hierarchyType = $${queryValues.length + 1}`);
      queryValues.push(hierarchyType);
    }

    if (tenantId) {
      queryConditions.push(`tenantId = $${queryValues.length + 1}`);
      queryValues.push(tenantId);
    } // ✅ closed tenantId block

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

    if (referenceId) {
      queryConditions.push(`referenceId = $${queryValues.length + 1}`);
      queryValues.push(referenceId);
    }

    queryString += queryConditions.join(" AND ");

    // Add sorting and limiting
    queryString += " ORDER BY createdTime DESC";

    const queryResult = await executeQuery(queryString, queryValues);
    return generatedResourceTransformer(queryResult?.rows);
  } catch (error: any) {
    console.log(error);
    logger.error(`Error fetching data from the database: ${error.message}`);
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", error?.message);
    return null; // Return null in case of an error
  }
}


async function getDataSheetReady(boundaryData: any, request: any, localizationMap?: { [key: string]: string }) {
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
  configurableColumnHeadersBasedOnCampaignType = await getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement(request, localizationMap);
  const headers = [
  ...modifiedReducedHierarchy,
  ...configurableColumnHeadersBasedOnCampaignType
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
      const frenchTranslation = getLocalizedNameOnlyIfMessagePresent(boundaryCode, frenchMessagesMap) || '';
      const portugeseTranslation = getLocalizedNameOnlyIfMessagePresent(boundaryCode, portugeseMessagesMap) || '';
      mappedRowData.push(frenchTranslation);
      mappedRowData.push(portugeseTranslation);
    return mappedRowData;
  });
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
  const sheetRowCount = data.length;
  request.body.generatedResourceCount = sheetRowCount;
  return await createExcelSheet(data, localizedHeaders);
}

export async function callGenerate(request: any, type: any, enableCaching = false) {
    logger.info(`calling generate api for type ${type}`);
        await processGenerate(request, enableCaching);
}


/* Fetches data from the database */
async function searchGeneratedBoundaryResources(searchQuery : any, locale : any) {
  try {
    const {tenantId, hierarchyType, id, status, referenceId } = searchQuery;
    const tableName = getTableName(config?.DB_CONFIG.DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    let queryString = `SELECT * FROM ${tableName} WHERE `;
    let queryConditions: string[] = [];
    let queryValues: any[] = [];
    if (id) {
      queryConditions.push(`id = $${queryValues.length + 1}`);
      queryValues.push(id);
    }

    if (hierarchyType) {
      queryConditions.push(`hierarchyType = $${queryValues.length + 1}`);
      queryValues.push(hierarchyType);
    }
    if (tenantId) {
      queryConditions.push(`tenantId = $${queryValues.length + 1}`);
      queryValues.push(tenantId);
    }
    if (referenceId) {
      queryConditions.push(`referenceId = $${queryValues.length + 1}`);
      queryValues.push(referenceId);
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





export {  appCache,errorLogger,invalidPathHandler
  ,sendResponse,getLocalizedMessagesHandler,throwErrorViaRequest,throwError
  ,getLocalizedHeaders ,enrichResourceDetails,shutdownGracefully,createHeaderToHierarchyMap
  ,modifyBoundaryDataHeadersWithMap,modifyBoundaryData,findMapValue,extractFrenchOrPortugeseLocalizationMap
  ,processGenerate ,getDataSheetReady , replicateRequest ,searchGeneratedBoundaryResources
};