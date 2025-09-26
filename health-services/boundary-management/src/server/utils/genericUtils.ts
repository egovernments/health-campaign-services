import { NextFunction, Request, Response } from "express";
import { logger } from "./logger";
import { getLocaleFromRequest } from "./localisationUtils";
import Localisation from "../controllers/localisationController/localisation.controller";
import config from "../config/index";
import { getErrorCodes} from "../config/constants";
import { v4 as uuidv4 } from 'uuid';
import { resourceDataStatuses } from "../config/constants";
import { produceModifiedMessages } from "../kafka/Producer";
import {getLocalizedName} from "../utils/boundaryUtils";
const NodeCache = require("node-cache");



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
  await produceModifiedMessages(persistMessage, config?.kafka?.KAFKA_PROCESS_RESOURCE_DETAILS_TOPIC, request?.body?.ResourceDetails?.tenantId);
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

export { errorResponder ,appCache,errorLogger,invalidPathHandler
  ,sendResponse,getLocalizedMessagesHandler,throwErrorViaRequest,throwError
  ,getLocalizedHeaders ,enrichResourceDetails,shutdownGracefully,createHeaderToHierarchyMap
  ,modifyBoundaryDataHeadersWithMap,modifyBoundaryData,findMapValue,extractFrenchOrPortugeseLocalizationMap
};