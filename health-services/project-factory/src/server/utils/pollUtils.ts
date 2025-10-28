import { defaultRequestInfo } from "../api/coreApis"; // Import default request metadata
import { getFormattedStringForDebug, logger } from "./logger"; // Import logger for logging information and errors
import { createDataService, downloadDataService, searchDataService } from "../service/dataManageService";
import { throwError } from "./genericUtils";

/**
 * Downloads a campaign template based on campaign ID, tenant ID, type, and hierarchy.
 * @param campaignId The unique identifier for the campaign.
 * @param tenantId The tenant identifier for which the template is downloaded.
 * @param type The type of the template to be downloaded.
 * @param hierarchy The hierarchy type associated with the campaign.
 * @returns The response containing the template download details.
 */
export const downloadTemplate = async (
  campaignId: string,
  tenantId: string,
  type: string,
  hierarchy: string,
  requestBody?: any
) => {
  // Use request body info if provided, otherwise fall back to default
  const searchBody = requestBody ? {
    RequestInfo: requestBody.RequestInfo
  } : {
    ...defaultRequestInfo, // Include default request metadata
  };

  const params = {
    tenantId: tenantId, // Tenant information for the request
    type: type, // Specify the template type
    hierarchyType: hierarchy, // Specify the hierarchy type
    campaignId: campaignId, // Campaign identifier
  };

  logger.info(
    `Received a request to download the template for campaign ID: ${campaignId} & type: ${type} `
  );
  const request: any = {
    body: { ...searchBody },
    query: { ...params }
  }

  const downloadResponse: any = await downloadDataService(request);

  logger.debug(`Received response : ${getFormattedStringForDebug(downloadResponse)}`);
  return downloadResponse; // Return the API response containing template details
};

/**
 * Polls a function at regular intervals until a condition is met or the maximum retries are reached.
 * @param functionToBePolledFor The function to be executed for polling.
 * @param conditionForTermination A callback function that evaluates whether polling should stop.
 * @param pollInterval The interval (in milliseconds) between each poll attempt. Default is 2000ms.
 * @param maxRetries The maximum number of retries before terminating polling. Default is 50.
 * @returns A promise that resolves with the function response if the condition is met, or rejects if retries are exhausted.
 */
const pollForTemplateGeneration = async (
  functionToBePolledFor: Function,
  conditionForTermination: Function,
  pollInterval: number = 2500,
  maxRetries: number = 20
) => {
  let retries = 0; // Initialize the retry counter
  logger.info("received a request for Polling ");
  if (!functionToBePolledFor || !conditionForTermination) {
    return null;
  }
  logger.info("request was valid so Polling ");

  return new Promise((resolve, reject) => {
    const poll = async () => {
      try {
        if (retries >= maxRetries) {
          // Reject if maximum retries are reached
          reject(new Error("Max  retries reached"));
          return;
        }

        const functionResponse = await functionToBePolledFor(); // Execute the polling function

        if (conditionForTermination(functionResponse)) {
          // Check if the termination condition is met
          logger.info("Polling completed");
          resolve(functionResponse);
          return;
        } else {
          // Increment retries and continue polling after the specified interval
          retries++;
          logger.info("Polling continuing");
          setTimeout(poll, pollInterval);
        }
      } catch (error) {
        // Handle errors by retrying after the specified interval
        retries++;
        setTimeout(poll, pollInterval);
      }
    };

    // Start polling
    poll().catch(reject);

    // Set a timeout to ensure the entire polling operation doesn't exceed a maximum duration
    const timeoutDuration = (maxRetries + 1) * pollInterval;
    setTimeout(() => {
      if (retries < maxRetries) {
        logger.error("Polling timeout: Max retries reached");
        reject(new Error("Polling timeout: Max retries reached"));
      }
    }, timeoutDuration);
  });
};



const conditionForTermination = (downloadResponse: any) => {
  logger.info(`current status ${downloadResponse?.[0]?.status}`)
  return downloadResponse?.[0]?.status === "completed" && downloadResponse?.[0]?.fileStoreid;
}

const conditionForTermination2 = (downloadResponse: any) => {
  logger.info(`current status ${downloadResponse?.[0]?.status}`)
  return downloadResponse?.[0]?.status === "completed" && downloadResponse?.[0]?.processedFilestoreId;
}

export const createAndPollForCompletion = async (request: any) => {
  try {
    // Step 1: Create data
    logger.info("Creating data...");
    const resourceDetails = await createDataService(request);
    const resourceId = resourceDetails?.id;

    if (!resourceId) {
      throwError("DATA", 500, "DATA_CREATE_ERROR", `Failed to retrieve resource ID from creation response for type ${request?.body?.ResourceDetails?.type}`);
    }

    logger.info(`Created resource with ID: ${resourceId} of type ${request?.body?.ResourceDetails?.type}`);

    // Step 2: Poll for completion
    const polledResponse = await pollForTemplateGeneration(
      () => searchData(resourceId, request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.type),
      conditionForTermination2,
      3000,
      30
    );

    logger.info("Polling completed successfully", polledResponse);
    return polledResponse;
  } catch (error: any) {
    logger.error("Error during creation or polling", error);
    throw error;
  }
};



async function searchData(resourceId: any, tenantId: any, type: any) {
  const SearchCriteria = {
    id: [resourceId],
    tenantId: tenantId,
    type: type
  };
  const searchBody = { ...defaultRequestInfo, SearchCriteria }
  const request: any = {
    body: { ...searchBody }
  }

  const searchResponse: any = await searchDataService(request);
  return searchResponse;
}



export const getTheGeneratedResource = async (
  campaignId: string,
  tenantId: string,
  type: string,
  hierarchy: string,
  requestBody?: any
) => {
  try {
    // Await the response from polling for template generation
    const polledResponse: any = await pollForTemplateGeneration(
      () => downloadTemplate(campaignId, tenantId, type, hierarchy, requestBody),
      conditionForTermination
    );

    // Log the polled response for debugging
    logger.debug(polledResponse);
    logger.debug(`polledResponse  : ${getFormattedStringForDebug(polledResponse)}`);

    // Return the fileStoreid from the response, ensuring the correct format
    return polledResponse?.[0]?.fileStoreid;

  } catch (error: any) {
    // Log any error that occurs during polling or processing
    logger.error(`Error while fetching the generated resource: ${error?.message}`);
  }
}
