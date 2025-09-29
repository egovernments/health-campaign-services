import {boundaryBulkUpload} from "../utils/boundaryUtils"
import {logger} from "../utils/logger";
import config from "../config";
import {produceModifiedMessages} from "../kafka/Producer";

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
      config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC,
      request?.body?.ResourceDetails?.tenantId
    );
  }
  if (
    request?.body?.Activities &&
    Array.isArray(request?.body?.Activities) &&
    request?.body?.Activities.length > 0
  ) {
    logger.info("Waiting for 2 seconds");
    await new Promise((resolve) => setTimeout(resolve, 2000));

    const activities = request?.body?.Activities;
    const chunkPromises = [];
    for (let i = 0; i < activities.length; i += 10) {
      const chunk = activities.slice(i, Math.min(i + 10, activities.length));
      const activityObject: any = { Activities: chunk };
      chunkPromises.push(
        await produceModifiedMessages(
          activityObject,
          config?.kafka?.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC,
          activities?.tenantId || config.app.defaultTenantId
        )
      );
    }
    await Promise.all(chunkPromises);
  }
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


export { processRequest ,handleResouceDetailsError,generateHierarchyList};