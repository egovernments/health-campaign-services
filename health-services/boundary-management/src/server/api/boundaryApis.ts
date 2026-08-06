import {boundaryBulkUpload} from "../utils/boundaryUtils"
import {logger} from "../utils/logger";
import config from "../config";
import {produceModifiedMessages} from "../kafka/Producer";
import {resourceDataStatuses} from "../config/constants";

/**
 * Processes generic requests such as create or validate.
 * @param request The HTTP request object.
 */
async function processRequest(
  request: any,
  localizationMap?: { [key: string]: string }
) {
   // Process the request based on the action type
  if (request?.body?.ResourceDetails?.action == "create") {
    await processCreate(request, localizationMap);
  }
}

/**
 * Processes the creation of resources.
 * @param request The HTTP request object.
 */
async function processCreate(request: any, localizationMap?: any) {
    // Process creation of resources
    boundaryBulkUpload(request, localizationMap);
}

async function handleResouceDetailsError(request: any, error: any) {
  var stringifiedError: any;
  if (error?.description || error?.message) {
    stringifiedError = JSON.stringify({
      status: error.status || "",
      code: error.code || "",
      description: error.description || "",
      message: error.message || "",
    });
  } else {
    if (typeof error == "object") stringifiedError = JSON.stringify(error);
    else {
      stringifiedError = error;
    }
  }

  logger.error("Error while processing after validation : " + error);
  if (request?.body?.ResourceDetails) {
    request.body.ResourceDetails.status = "failed";
    request.body.ResourceDetails.processedFileStoreId = request?.body?.ResourceDetails?.processedFileStoreId || null;
    request.body.ResourceDetails.referenceId = request?.body?.ResourceDetails?.referenceId || null;
    request.body.ResourceDetails.additionalDetails = {
      ...request?.body?.ResourceDetails?.additionalDetails,
      error: stringifiedError,
    };
    const persistMessage: any = {
      ResourceDetails: request.body.ResourceDetails,
    };
    if (request?.body?.ResourceDetails?.action == "create") {
      persistMessage.ResourceDetails.additionalDetails = {
        error: stringifiedError,
      };
    }
    await produceModifiedMessages(
      persistMessage,
      config?.kafka?.KAFKA_UPDATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC,
      request?.body?.ResourceDetails?.tenantId
    );
  }
}


/**
 * Ends a run as INVALID because the uploaded sheet failed a data check that is reported back as a marked-up
 * copy of the sheet rather than as a plain error - today that is duplicate rows, highlighted red by the
 * validator. Publishes the terminal update so _process-search returns:
 *   status                = invalid
 *   processedFileStoreId  = the highlighted sheet to download and fix
 *   additionalDetails.error = the same message the hard failure used to carry
 * `additionalDetails.error` is reused deliberately: it is the field the console already reads for a failed
 * or invalid row (see README §6), so no console change is needed to surface the reason.
 */
async function markResourceDetailsInvalid(request: any) {
  const duplicateRowValidation = request?.body?.duplicateRowValidation;
  if (!request?.body?.ResourceDetails || !duplicateRowValidation) return;

  request.body.ResourceDetails.status = resourceDataStatuses.invalid;
  request.body.ResourceDetails.processedFileStoreId = duplicateRowValidation.processedFileStoreId || null;
  request.body.ResourceDetails.referenceId = request?.body?.ResourceDetails?.referenceId || null;
  request.body.ResourceDetails.additionalDetails = {
    ...request?.body?.ResourceDetails?.additionalDetails,
    error: duplicateRowValidation.message,
    duplicateRows: {
      count: duplicateRowValidation.count,
      rowNumbers: duplicateRowValidation.rowNumbers,
    },
  };

  logger.info(
    `Marking resource ${request?.body?.ResourceDetails?.id} invalid: ${duplicateRowValidation.count} duplicate row(s); ` +
    `highlighted sheet is ${request.body.ResourceDetails.processedFileStoreId}`
  );
  await produceModifiedMessages(
    { ResourceDetails: request.body.ResourceDetails },
    config?.kafka?.KAFKA_UPDATE_PROCESSED_BOUNDARY_MANAGEMENT_TOPIC,
    request?.body?.ResourceDetails?.tenantId
  );
}

function generateHierarchyList(data: any[], parentChain: any = []) {
  let result: any[] = [];

  // Iterate over each boundary in the current level
  for (let boundary of data) {
    let currentChain = [...parentChain, boundary.code];

    // Add the current chain to the result
    result.push(currentChain.join(","));

    // If there are children, recursively call the function
    if (boundary.children && boundary.children.length > 0) {
      let childResults = generateHierarchyList(boundary.children, currentChain);
      result = result.concat(childResults);
    }
  }
  return result;
}


export { processRequest ,handleResouceDetailsError,generateHierarchyList,markResourceDetailsInvalid};