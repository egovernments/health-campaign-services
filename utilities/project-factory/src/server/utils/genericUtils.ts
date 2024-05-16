import { NextFunction, Request, Response } from "express";
import { httpRequest } from "./request";
import config, { getErrorCodes } from "../config/index";
import { v4 as uuidv4 } from 'uuid';
import { produceModifiedMessages } from '../Kafka/Listener'
import { generateHierarchyList, getAllFacilities, getHierarchy } from "../api/campaignApis";
import { searchMDMS, getCount, getBoundarySheetData, getSheetData, createAndUploadFile, createExcelSheet, getTargetSheetData, callMdmsData } from "../api/genericApis";
import * as XLSX from 'xlsx';
import FormData from 'form-data';
import { logger } from "./logger";
import dataManageController from "../controllers/dataManage/dataManage.controller";
import { convertSheetToDifferentTabs, getBoundaryDataAfterGeneration, getLocalizedName } from "./campaignUtils";
import localisationController from "../controllers/localisationController/localisation.controller";
import { executeQuery } from "./db";
import { generatedResourceTransformer } from "./transforms/searchResponseConstructor";
import { generatedResourceStatuses, headingMapping, resourceDataStatuses } from "../config/constants";
import { getLocaleFromRequest } from "./localisationUtils";
import { getBoundaryColumnName, getBoundaryTabName } from "./boundaryUtils";
const NodeCache = require("node-cache");
const _ = require('lodash');

const updateGeneratedResourceTopic = config.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC;
const createGeneratedResourceTopic = config.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC;

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
Sets the cahce response
*/
const cacheResponse = (res: Response, key: string) => {
  if (key != null) {
    appCache.set(key, { ...res });
    logger.info("CACHED RESPONSE FOR :: " + key);
  }
};

/* 
gets the cahce response
*/
const getCachedResponse = (key: string) => {
  if (key != null) {
    const data = appCache.get(key);
    if (data) {
      logger.info("CACHE STATUS :: " + JSON.stringify(appCache.getStats()));
      logger.info("RETURNS THE CACHED RESPONSE FOR :: " + key);
      return data;
    }
  }
  return null;
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

async function generateXlsxFromJson(request: any, response: any, simplifiedData: any) {
  const ws = XLSX.utils.json_to_sheet(simplifiedData);

  // Create a new workbook
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet 1');
  const buffer = XLSX.write(wb, { bookType: 'xlsx', type: 'buffer' });
  const formData = new FormData();
  formData.append('file', buffer, 'filename.xlsx');
  formData.append('tenantId', request?.body?.RequestInfo?.userInfo?.tenantId);
  formData.append('module', 'HCM-ADMIN-CONSOLE-PROCESS');

  var fileCreationResult = await httpRequest(config.host.filestore + config.paths.filestore, formData, undefined, undefined, undefined,
    {
      'Content-Type': 'multipart/form-data',
      'auth-token': request?.body?.RequestInfo?.authToken
    }
  );
  const responseData = fileCreationResult?.files;
  logger.info("Response data after File Creation : " + JSON.stringify(responseData));
  return responseData;
}

async function generateActivityMessage(tenantId: any, requestBody: any, requestPayload: any, responsePayload: any, type: any, url: any, status: any) {
  const activityMessage = {
    id: uuidv4(),
    status: status,
    retryCount: 0,
    tenantId: tenantId,
    type: type,
    url: url,
    requestPayload: requestPayload,
    responsePayload: responsePayload,
    auditDetails: {
      createdBy: requestBody?.RequestInfo?.userInfo?.uuid,
      lastModifiedBy: requestBody?.RequestInfo?.userInfo?.uuid,
      createdTime: Date.now(),
      lastModifiedTime: Date.now()
    },
    additionalDetails: {},
    resourceDetailsId: null
  }
  return activityMessage;
}

/* Fetches data from the database */
async function getResponseFromDb(request: any, response: any) {
  try {
    const { type } = request.query;
    const { tenantId, hierarchyType } = request.query;
    const status = generatedResourceStatuses.completed;
    let queryResult: any;
    let queryString: string;
    let queryValues: any[] = [];

    queryString = "SELECT * FROM health.eg_cm_generated_resource_details WHERE ";
    // query for download with id
    if (request?.query?.id) {
      queryString += "id = $1 AND type = $2 AND hierarchytype = $3 AND tenantid = $4 ";
      queryValues = [request.query.id, type, hierarchyType, tenantId];
    }
    else {
      if (type == 'boundary' && request?.body?.Filters !== undefined) {
        queryString += "type = $1 AND hierarchytype = $2 AND  tenantid = $3  AND status =$4 ";
        if (request.body.Filters === null) {
          queryString += " AND (additionaldetails->'Filters' IS NULL OR additionaldetails->'Filters' = 'null')";
          queryValues = [type, hierarchyType, tenantId, status];
        } else {
          queryString += " AND additionaldetails->'Filters' @> $5::jsonb";
          queryValues = [type, hierarchyType, tenantId, status, request.body.Filters];
        }
      }
      else {
        queryString += " type = $1 AND hierarchytype = $2 AND tenantid = $3 AND status = $4";
        queryValues = [type, hierarchyType, tenantId, status];
      }
    }
    queryResult = await executeQuery(queryString, queryValues);
    return generatedResourceTransformer(queryResult?.rows);
  }
  catch (error: any) {
    logger.error(`Error fetching data from the database: ${error.message}`);
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", error?.message);
    return null; // Return null in case of an error
  }
}

async function getModifiedResponse(responseData: any) {
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

async function getNewEntryResponse(request: any) {
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
    count: null
  };
  return [newEntry];
}
async function getOldEntryResponse(modifiedResponse: any[], request: any) {
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

async function callSearchApi(request: any, response: any) {
  try {
    let result: any;
    const { type } = request.query;
    result = await searchMDMS([type], config.SEARCH_TEMPLATE, request.body.RequestInfo, response);
    const filter = request?.body?.Filters;
    const requestBody = { "RequestInfo": request?.body?.RequestInfo, filter };
    const responseData = result?.mdms?.[0]?.data;
    if (!responseData || responseData.length === 0) {
      return errorResponder({ message: "Invalid ApiResource Type. Check Logs" }, request, response);
    }
    const host = responseData?.host;
    const url = responseData?.searchConfig?.url;
    var queryParams: any = {};
    for (const searchItem of responseData?.searchConfig?.searchBody) {
      if (searchItem.isInParams) {
        queryParams[searchItem.path] = searchItem.value;
      }
      else if (searchItem.isInBody) {
        _.set(requestBody, `${searchItem.path}`, searchItem.value);
      }
    }
    const countknown = responseData?.searchConfig?.isCountGiven === true;
    let responseDatas: any[] = [];
    const searchPath = responseData?.searchConfig?.keyName;
    let fetchedData: any;
    let responseObject: any;

    if (countknown) {
      const count = await getCount(responseData, request, response);
      let noOfTimesToFetchApi = Math.ceil(count / queryParams.limit);
      for (let i = 0; i < noOfTimesToFetchApi; i++) {
        responseObject = await httpRequest(host + url, requestBody, queryParams, undefined, undefined, undefined);
        fetchedData = _.get(responseObject, searchPath);
        fetchedData.forEach((item: any) => {
          responseDatas.push(item);
        });
        queryParams.offset = (parseInt(queryParams.offset) + parseInt(queryParams.limit)).toString();
      }
    }

    else {
      while (true) {
        responseObject = await httpRequest(host + url, requestBody, queryParams, undefined, undefined, undefined);
        fetchedData = _.get(responseObject, searchPath);
        fetchedData.forEach((item: any) => {
          responseDatas.push(item);
        });
        queryParams.offset = (parseInt(queryParams.offset) + parseInt(queryParams.limit)).toString();
        if (fetchedData.length < parseInt(queryParams.limit)) {
          break;
        }
      }
    }
    return responseDatas;
  }
  catch (e: any) {
    logger.error(String(e))
    return errorResponder({ message: String(e) + "    Check Logs" }, request, response);
  }
}

async function fullProcessFlowForNewEntry(newEntryResponse: any, request: any, response: any, localizationMap?: { [key: string]: string }) {
  try {
    const type = request?.query?.type;
    const generatedResource: any = { generatedResource: newEntryResponse }
    // send message to create toppic
    produceModifiedMessages(generatedResource, createGeneratedResourceTopic);
    if (type === 'boundary') {
      const dataManagerController = new dataManageController();
      // get boundary data from boundary relationship search api
      const result = await dataManagerController.getBoundaryData(request, response);
      let updatedResult = result;
      // get boundary sheet data after being generated
      const boundaryData = await getBoundaryDataAfterGeneration(result, request, localizationMap);
      const differentTabsBasedOnLevel = getLocalizedName(config.generateDifferentTabsOnBasisOf, localizationMap);
      const isKeyOfThatTypePresent = boundaryData.some((data: any) => data.hasOwnProperty(differentTabsBasedOnLevel));
      const boundaryTypeOnWhichWeSplit = boundaryData.filter((data: any) => data[differentTabsBasedOnLevel] !== null && data[differentTabsBasedOnLevel] !== undefined);
      if (isKeyOfThatTypePresent && boundaryTypeOnWhichWeSplit.length >= parseInt(config.numberOfBoundaryDataOnWhichWeSplit)) {
        updatedResult = await convertSheetToDifferentTabs(request, boundaryData, differentTabsBasedOnLevel, localizationMap);
      }
      // final upodated response to be sent to update topic 
      const finalResponse = await getFinalUpdatedResponse(updatedResult, newEntryResponse, request);
      const generatedResourceNew: any = { generatedResource: finalResponse }
      // send to update topic
      produceModifiedMessages(generatedResourceNew, updateGeneratedResourceTopic);
      request.body.generatedResource = finalResponse;
    }
    else if (type == "facilityWithBoundary" || type == 'userWithBoundary') {
      await processGenerateRequest(request, localizationMap);
      const finalResponse = await getFinalUpdatedResponse(request?.body?.fileDetails, newEntryResponse, request);
      const generatedResourceNew: any = { generatedResource: finalResponse }
      produceModifiedMessages(generatedResourceNew, updateGeneratedResourceTopic);
      request.body.generatedResource = finalResponse;
    }
    else {
      const responseDatas = await callSearchApi(request, response);
      const modifiedDatas = await modifyData(request, response, responseDatas);
      const result = await generateXlsxFromJson(request, response, modifiedDatas);
      const finalResponse = await getFinalUpdatedResponse(result, newEntryResponse, request);
      const generatedResourceNew: any = { generatedResource: finalResponse }
      produceModifiedMessages(generatedResourceNew, updateGeneratedResourceTopic);
    }
  } catch (error) {
    throw error;
  }
}
async function modifyData(request: any, response: any, responseDatas: any) {
  try {
    let result: any;
    const hostHcmBff = config.host.projectFactoryBff.endsWith('/') ? config.host.projectFactoryBff.slice(0, -1) : config.host.projectFactoryBff;
    const { type } = request.query;
    result = await searchMDMS([type], config.SEARCH_TEMPLATE, request.body.RequestInfo, response);
    const modifiedParsingTemplate = result?.mdms?.[0]?.data?.modificationParsingTemplateName;
    if (!request.body.HCMConfig) {
      request.body.HCMConfig = {};
    }
    const batchSize = 50;
    const totalBatches = Math.ceil(responseDatas.length / batchSize);
    const allUpdatedData = [];

    for (let i = 0; i < totalBatches; i++) {
      const batchData = responseDatas.slice(i * batchSize, (i + 1) * batchSize);
      const batchRequestBody = { ...request.body };
      batchRequestBody.HCMConfig.parsingTemplate = modifiedParsingTemplate;
      batchRequestBody.HCMConfig.data = batchData;

      try {
        const processResult = await httpRequest(`${hostHcmBff}${config.app.contextPath}/bulk/_process`, batchRequestBody, undefined, undefined, undefined, undefined);
        if (processResult.Error) {
          throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", processResult.Error);
        }
        allUpdatedData.push(...processResult.updatedDatas);
      } catch (error: any) {
        throw error;
      }
    }
    return allUpdatedData;
  }
  catch (e: any) {
    throw e;
  }
}
function generateAuditDetails(request: any) {
  const createdBy = request?.body?.RequestInfo?.userInfo?.uuid;
  const lastModifiedBy = request?.body?.RequestInfo?.userInfo?.uuid;
  const auditDetails = {
    createdBy: createdBy,
    lastModifiedBy: lastModifiedBy,
    createdTime: Date.now(),
    lastModifiedTime: Date.now()
  }
  return auditDetails;
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

async function createFacilitySheet(request: any, allFacilities: any[], localizationMap?: { [key: string]: string }) {
  const tenantId = request?.query?.tenantId;
  const mdmsResponse = await callMdmsData(request, config.moduleName, config.facilitySchemaMasterName, tenantId);
  const keys = mdmsResponse.MdmsRes[config?.moduleName].facilitySchema[0].required;
  const headers = ["HCM_ADMIN_CONSOLE_FACILITY_CODE", ...keys]
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);

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
  logger.info("facilities : " + JSON.stringify(facilities));
  const localizedFacilityTab = getLocalizedName(config.facilityTab, localizationMap);
  const facilitySheetData: any = await createExcelSheet(facilities, localizedHeaders, localizedFacilityTab);
  return facilitySheetData;
}

async function createReadMeSheet(request: any, workbook: any, mainHeader: any, localizationMap?: any) {
  const readMeConfig = await getReadMeConfig(request);
  const maxCharsBeforeLineBreak = 100; // Set the maximum number of characters before line break
  const datas = readMeConfig.texts.flatMap((text: any) => {
    let stepText = ''; // Initialize step text for each text element
    let stepCount = 1; // Initialize the step counter
    const descriptions = text.descriptions.map((description: any) => {
      let textWithLineBreaks = '';
      let remainingText = getLocalizedName(description.text, localizationMap);
      while (remainingText.length > maxCharsBeforeLineBreak) {
        let breakIndex = remainingText.lastIndexOf(' ', maxCharsBeforeLineBreak);
        if (breakIndex === -1) breakIndex = maxCharsBeforeLineBreak;
        textWithLineBreaks += remainingText.substring(0, breakIndex) + '\n';
        remainingText = remainingText.substring(breakIndex).trim();
      }
      textWithLineBreaks += remainingText;
      // If step is required, add step text before description
      if (description.isStepRequired) {
        stepText = `Step ${stepCount}: `;
        stepCount++;
        return stepText + textWithLineBreaks;
      }
      else {
        return textWithLineBreaks;
      }
    });
    return [getLocalizedName(text.header, localizationMap), ...descriptions, "", "", "", ""];
  });

  // Ensure mainHeader is an array
  if (!Array.isArray(mainHeader)) {
    mainHeader = [mainHeader];
  }

  const worksheet = XLSX.utils.aoa_to_sheet([mainHeader, "", "", ...datas.map((data: any) => [data])]);

  // Set the width of column A to 130
  const wscols = [{ wch: 130 }];
  worksheet['!cols'] = wscols;
  const readMeSheetName = getLocalizedName("HCM_README_SHEETNAME", localizationMap);
  XLSX.utils.book_append_sheet(workbook, worksheet, readMeSheetName);
}




function getLocalizedHeaders(headers: any, localizationMap?: { [key: string]: string }) {
  const messages = headers.map((header: any) => (localizationMap ? localizationMap[header] || header : header));
  return messages;
}



function modifyRequestForLocalisation(request: any, tenanId: string) {
  const { RequestInfo } = request?.body;
  const query = {
    "tenantId": tenanId,
    "locale": getLocaleFromRequest(request),
    "module": config.localizationModule
  };
  const updatedRequest = { ...request };
  updatedRequest.body = { RequestInfo };
  updatedRequest.query = query;
  return updatedRequest;
}

async function getReadMeConfig(request: any) {
  const mdmsResponse = await callMdmsData(request, "HCM-ADMIN-CONSOLE", "ReadMeConfig", request?.query?.tenantId);
  if (mdmsResponse?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig) {
    const readMeConfigsArray = mdmsResponse?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig
    for (const readMeConfig of readMeConfigsArray) {
      if (readMeConfig?.type == request?.query?.type) {
        return readMeConfig
      }
    }
    throwError("MDMS", 500, "INVALID_README_CONFIG", `Readme config for type ${request?.query?.type} not found.`);
    return {}
  }
  else {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", `Some error occured during readme config mdms search.`);
    return {};
  }
}

async function createFacilityAndBoundaryFile(facilitySheetData: any, boundarySheetData: any, request: any, localizationMap?: { [key: string]: string }) {
  const workbook = XLSX.utils.book_new();
  // Add facility sheet to the workbook
  const localizedFacilityTab = getLocalizedName(config?.facilityTab, localizationMap);
  const type = request?.query?.type;
  const headingInSheet = headingMapping?.[type]
  const localisedHeading = getLocalizedName(headingInSheet, localizationMap)
  await createReadMeSheet(request, workbook, localisedHeading, localizationMap);
  XLSX.utils.book_append_sheet(workbook, facilitySheetData.ws, localizedFacilityTab);
  // Add boundary sheet to the workbook
  const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap)
  XLSX.utils.book_append_sheet(workbook, boundarySheetData.ws, localizedBoundaryTab);
  const fileDetails = await createAndUploadFile(workbook, request)
  request.body.fileDetails = fileDetails;
}

async function createUserAndBoundaryFile(userSheetData: any, boundarySheetData: any, request: any, localizationMap?: { [key: string]: string }) {
  const workbook = XLSX.utils.book_new();
  const localizedUserTab = getLocalizedName(config.userTab, localizationMap);
  const type = request?.query?.type;
  const headingInSheet = headingMapping?.[type]
  const localisedHeading = getLocalizedName(headingInSheet, localizationMap)
  await createReadMeSheet(request, workbook, localisedHeading, localizationMap);
  // Add facility sheet to the workbook
  XLSX.utils.book_append_sheet(workbook, userSheetData.ws, localizedUserTab);
  // Add boundary sheet to the workbook
  const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap)
  XLSX.utils.book_append_sheet(workbook, boundarySheetData.ws, localizedBoundaryTab);
  const fileDetails = await createAndUploadFile(workbook, request)
  request.body.fileDetails = fileDetails;
}


async function generateFacilityAndBoundarySheet(tenantId: string, request: any, localizationMap?: { [key: string]: string }) {
  // Get facility and boundary data
  const allFacilities = await getAllFacilities(tenantId, request.body);
  request.body.generatedResourceCount = allFacilities.length;
  const facilitySheetData: any = await createFacilitySheet(request, allFacilities, localizationMap);
  // request.body.Filters = { tenantId: tenantId, hierarchyType: request?.query?.hierarchyType, includeChildren: true }
  const boundarySheetData: any = await getBoundarySheetData(request);
  await createFacilityAndBoundaryFile(facilitySheetData, boundarySheetData, request, localizationMap);
}
async function generateUserAndBoundarySheet(request: any, localizationMap?: { [key: string]: string }) {
  const userData: any[] = [];
  const tenantId = request?.query?.tenantId;
  const mdmsResponse = await callMdmsData(request, config.moduleName, config.userSchemaMasterName, tenantId)
  const headers = mdmsResponse.MdmsRes[config.moduleName].userSchema[0].required;
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  const localizedUserTab = getLocalizedName(config.userTab, localizationMap);
  const userSheetData = await createExcelSheet(userData, localizedHeaders, localizedUserTab);
  const boundarySheetData: any = await getBoundarySheetData(request);
  await createUserAndBoundaryFile(userSheetData, boundarySheetData, request, localizationMap);
}
async function processGenerateRequest(request: any, localizationMap?: { [key: string]: string }) {
  const { type, tenantId } = request.query
  if (type == "facilityWithBoundary") {
    await generateFacilityAndBoundarySheet(String(tenantId), request, localizationMap);
  }
  if (type == "userWithBoundary") {
    await generateUserAndBoundarySheet(request, localizationMap);
  }
}

async function processGenerateForNew(request: any, response: any, generatedResource: any, newEntryResponse: any, localizationMap?: { [key: string]: string }) {
  request.body.generatedResource = newEntryResponse;
  try {
    await fullProcessFlowForNewEntry(newEntryResponse, request, response, localizationMap);
  } catch (error: any) {
    handleGenerateError(newEntryResponse, generatedResource, error);
  }
}

function handleGenerateError(newEntryResponse: any, generatedResource: any, error: any) {
  newEntryResponse.map((item: any) => { item.status = generatedResourceStatuses.failed, item.additionalDetails = { ...item.additionalDetails, error: error.message || String(error) } })
  generatedResource = { generatedResource: newEntryResponse };
  logger.error(String(error));
  produceModifiedMessages(generatedResource, updateGeneratedResourceTopic);
}

async function updateAndPersistGenerateRequest(newEntryResponse: any, oldEntryResponse: any, responseData: any, request: any, response: any, localizationMap?: { [key: string]: string }) {
  const { forceUpdate } = request.query;
  const forceUpdateBool: boolean = forceUpdate === 'true';
  let generatedResource: any;
  if (forceUpdateBool && responseData.length > 0) {
    generatedResource = { generatedResource: oldEntryResponse };
    // send message to update topic 
    produceModifiedMessages(generatedResource, updateGeneratedResourceTopic);
    request.body.generatedResource = oldEntryResponse;
  }
  if (responseData.length === 0 || forceUpdateBool) {
    processGenerateForNew(request, response, generatedResource, newEntryResponse, localizationMap)
  }
  else {
    request.body.generatedResource = responseData
  }
}
/* 

*/
async function processGenerate(request: any, response: any, localizationMap?: { [key: string]: string }) {
  // fetch the data from db 
  const responseData = await getResponseFromDb(request, response);
  // modify response from db 
  const modifiedResponse = await getModifiedResponse(responseData);
  // generate new random id and make filestore id null
  const newEntryResponse = await getNewEntryResponse(request);
  // make old data status as expired
  const oldEntryResponse = await getOldEntryResponse(modifiedResponse, request);
  // generate data 
  await updateAndPersistGenerateRequest(newEntryResponse, oldEntryResponse, responseData, request, response, localizationMap);
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
  produceModifiedMessages(request?.body, config.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
}

function getFacilityIds(data: any) {
  return data.map((obj: any) => obj["id"])
}

function matchData(request: any, datas: any, searchedDatas: any, createAndSearchConfig: any) {
  const uid = createAndSearchConfig.uniqueIdentifier;
  const errors = []
  for (const data of datas) {
    const searchData = searchedDatas.find((searchedData: any) => searchedData[uid] == data[uid]);

    if (!searchData) {
      errors.push({ status: "INVALID", rowNumber: data["!row#number!"], errorDetails: `Data with ${uid} ${data[uid]} not found in searched data.` })
    }
    else if (createAndSearchConfig?.matchEachKey) {
      const keys = Object.keys(data);
      var errorString = "";
      var errorFound = false;
      for (const key of keys) {
        if (searchData.hasOwnProperty(key) && searchData[key] !== data[key] && key != "!row#number!") {
          errorString += `Value mismatch for key "${key}. Expected: "${data[key]}", Found: "${searchData[key]}"`
          errorFound = true;
        }
      }
      if (errorFound) {
        errors.push({ status: "MISMATCHING", rowNumber: data["!row#number!"], errorDetails: errorString })
      }
      else {
        errors.push({ status: "VALID", rowNumber: data["!row#number!"], errorDetails: "" })
      }
    }
    else {
      errors.push({ status: "VALID", rowNumber: data["!row#number!"], errorDetails: "" })
    }
  }
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
}

function modifyBoundaryData(boundaryData: unknown[]) {
  let withBoundaryCode: string[][] = [];
  let withoutBoundaryCode: string[][] = [];

  for (const obj of boundaryData) {
    const row: string[] = [];
    if (typeof obj === 'object' && obj !== null) {
      for (const value of Object.values(obj)) {
        if (value !== null && value !== undefined) {
          row.push(value.toString()); // Convert value to string and push to row
        }
      }

      if (obj.hasOwnProperty('Boundary Code')) {
        withBoundaryCode.push(row);
      } else {
        withoutBoundaryCode.push(row);
      }
    }
  }

  return [withBoundaryCode, withoutBoundaryCode];
}

async function getDataFromSheet(request: any, fileStoreId: any, tenantId: any, createAndSearchConfig: any, optionalSheetName?: any, localizationMap?: { [key: string]: string }) {
  const type = request?.body?.ResourceDetails?.type;
  const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: tenantId, fileStoreIds: fileStoreId }, "get");
  if (!fileResponse?.fileStoreIds?.[0]?.url) {
    throwError("FILE", 500, "DOWNLOAD_URL_NOT_FOUND");
  }
  if (type == 'boundaryWithTarget') {
    return await getTargetSheetData(fileResponse?.fileStoreIds?.[0]?.url, true, true, localizationMap);
  }
  return await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, createAndSearchConfig?.parseArrayConfig?.sheetName || optionalSheetName, true, createAndSearchConfig, localizationMap)
}

async function getBoundaryRelationshipData(request: any, params: any) {
  const url = `${config.host.boundaryHost}${config.paths.boundaryRelationship}`;
  const boundaryRelationshipResponse = await httpRequest(url, request.body, params);
  return boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
}

async function getDataSheetReady(boundaryData: any, request: any, localizationMap?: { [key: string]: string }) {
  const type = request?.query?.type;
  const boundaryType = boundaryData?.[0].boundaryType;
  const boundaryList = generateHierarchyList(boundaryData)
  if (!Array.isArray(boundaryList) || boundaryList.length === 0) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary list is empty or not an array.");
  }
  const boundaryCodes = boundaryList.map(boundary => boundary.split(',').pop());

  // Chunk the boundary codes into groups of 20
  const chunkSize = 20;
  const boundaryCodeChunks = [];
  for (let i = 0; i < boundaryCodes.length; i += chunkSize) {
    boundaryCodeChunks.push(boundaryCodes.slice(i, i + chunkSize));
  }

  const boundaryCodeNameMapping: { [key: string]: string } = {};

  // Fetch data for each chunk of boundary codes
  for (const chunk of boundaryCodeChunks) {
    const string = chunk.join(', ');
    const boundaryEntityResponse = await httpRequest(config.host.boundaryHost + config.paths.boundaryServiceSearch, request.body, { tenantId: request?.query?.tenantId, codes: string });

    // Populate boundaryCodeNameMapping with the retrieved data
    boundaryEntityResponse?.Boundary?.forEach((data: any) => {
      boundaryCodeNameMapping[data?.code] = data?.additionalDetails?.name;
    });
  }
  const hierarchy = await getHierarchy(request, request?.query?.tenantId, request?.query?.hierarchyType);
  const startIndex = boundaryType ? hierarchy.indexOf(boundaryType) : -1;
  const reducedHierarchy = startIndex !== -1 ? hierarchy.slice(startIndex) : hierarchy;
  const modifiedReducedHierarchy = reducedHierarchy.map(ele => `${request?.query?.hierarchyType}_${ele}`.toUpperCase())
  const headers = (type !== "facilityWithBoundary" && type !== "userWithBoundary")
    ? [
      ...modifiedReducedHierarchy,
      getBoundaryColumnName(),
      "Target at the Selected Boundary level"
    ]
    : [
      ...modifiedReducedHierarchy,
      getBoundaryColumnName()
    ];
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  const data = boundaryList.map(boundary => {
    const boundaryParts = boundary.split(',');
    const boundaryCode = boundaryParts[boundaryParts.length - 1];
    const rowData = boundaryParts.concat(Array(Math.max(0, reducedHierarchy.length - boundaryParts.length)).fill(''));
    const mappedRowData = rowData.map((cell: any, index: number) =>
      index === reducedHierarchy.length ? '' : cell !== '' ? boundaryCodeNameMapping[cell] || cell : ''
    );
    const boundaryCodeIndex = reducedHierarchy.length;
    mappedRowData[boundaryCodeIndex] = boundaryCode;
    return mappedRowData;
  });
  const sheetRowCount = data.length;
  if (type != "facilityWithBoundary") {
    request.body.generatedResourceCount = sheetRowCount;
  }
  const localizedBoundaryTab = getLocalizedName(getBoundaryTabName(), localizationMap);
  return await createExcelSheet(data, localizedHeaders, localizedBoundaryTab);
}

function modifyTargetData(data: any) {
  const dataArray: any[] = [];
  Object.keys(data).forEach(key => {
    data[key].forEach((item: any) => {
      dataArray.push(item);
    });
  });
  return dataArray;
}

function calculateKeyIndex(obj: any, hierachy: any[], localizationMap?: any) {
  const keys = Object.keys(obj);
  const localizedBoundaryCode = getLocalizedName(getBoundaryColumnName(), localizationMap)
  const boundaryCodeIndex = keys.indexOf(localizedBoundaryCode);
  const keyBeforeBoundaryCode = keys[boundaryCodeIndex - 1];
  return hierachy.indexOf(keyBeforeBoundaryCode);
}

function modifyDataBasedOnDifferentTab(boundaryData: any, differentTabsBasedOnLevel: any, localizationMap?: any) {
  const newData: any = {};
  let boundaryCode: string | undefined;

  for (const key in boundaryData) {
    newData[key] = boundaryData[key];
    if (key === differentTabsBasedOnLevel) {
      break;
    }
  }
  const localizedBoundaryCode = getLocalizedName(getBoundaryColumnName(), localizationMap);
  boundaryCode = boundaryData[localizedBoundaryCode];
  if (boundaryCode !== undefined) {
    newData[localizedBoundaryCode] = boundaryCode;
  }
  return newData;
}

async function getLocalizedMessagesHandler(request: any, tenantId: any) {
  const localisationcontroller = new localisationController();
  const response = {};
  const modifiedRequestForLocalization = modifyRequestForLocalisation(request, tenantId);
  const localizationResponse = await localisationcontroller.getLocalizedMessages(modifiedRequestForLocalization, response);
  const localizationMap: { [key: string]: string } = {};
  localizationResponse.messages.forEach((message: any) => {
    localizationMap[message.code] = message.message;
  });
  return localizationMap;
}



async function translateSchema(schema: any, localizationMap?: { [key: string]: string }) {
  const translatedSchema = {
    ...schema,
    properties: Object.entries(schema?.properties || {}).reduce((acc, [key, value]) => {
      const localizedMessage = getLocalizedName(key, localizationMap);
      acc[localizedMessage] = value;
      return acc;
    }, {} as { [key: string]: any }), // Initialize with the correct type
    required: (schema?.required || []).map((key: string) => getLocalizedName(key, localizationMap)),
    unique: (schema?.unique || []).map((key: string) => getLocalizedName(key, localizationMap))
  };

  return translatedSchema;
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
  cacheResponse,
  getCachedResponse,
  generateXlsxFromJson,
  generateAuditDetails,
  generateActivityMessage,
  getResponseFromDb,
  callSearchApi,
  getModifiedResponse,
  getNewEntryResponse,
  getOldEntryResponse,
  getFinalUpdatedResponse,
  fullProcessFlowForNewEntry,
  modifyData,
  correctParentValues,
  sortCampaignDetails,
  processGenerateRequest,
  processGenerate,
  getFacilityIds,
  getDataFromSheet,
  matchData,
  enrichResourceDetails,
  modifyBoundaryData,
  getBoundaryRelationshipData,
  getDataSheetReady,
  modifyTargetData,
  calculateKeyIndex,
  modifyDataBasedOnDifferentTab,
  modifyRequestForLocalisation,
  translateSchema,
  getLocalizedMessagesHandler,
  getLocalizedHeaders,
  createReadMeSheet
};


