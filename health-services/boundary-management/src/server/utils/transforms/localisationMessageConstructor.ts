import { logger } from "../logger";
import config from "../../config";
import {
  getLocaleFromRequest,
  getLocalisationModuleName,
} from "../localisationUtils";
import Localisation from "../../controllers/localisationController/localisation.controller";


export const transformAndCreateLocalisation = async (
  boundaryMap: any,
  request: any,
  isFrench:boolean,
  isPortugese : boolean
) => {
  const CHUNK_SIZE = config.localisation.localizationChunkSizeForBoundaryCreation

  try {
    const { tenantId, hierarchyType } = request?.body?.ResourceDetails || {};

    // Get localisation module name based on hierarchy type
    const module = getLocalisationModuleName(hierarchyType);

    // Get locale from request object
    let locale = getLocaleFromRequest(request);
    const [_, suffix] = locale.split("_");
    
    if (isFrench) {
      locale = `fr_${suffix}`;
    } else if (isPortugese) {
      locale = `pt_${suffix}`;
    }

    // Array to store localisation messages
    const localisationMessages: any[] = [];

    // Iterate over boundary map to transform into localisation messages
    boundaryMap.forEach((code: string, boundary: any) => {
      if(boundary.value !== '' && boundary.value !== undefined){
      localisationMessages.push({
        code,
        message: boundary.value,
        module,
        locale,
      });
    }
    });

    logger.info("Localisation message transformed successfully from the boundary map");

    // Call the chunk upload function
    await uploadInChunks(localisationMessages, CHUNK_SIZE, tenantId, request);

    logger.info("All chunks uploaded successfully");

  } catch (error) {
    logger.error("Error during transformation and localisation creation:", error);
    throw error;  // You can further handle this error (e.g., send failure response to client)
  }
}

const uploadInChunks = async (messages: any, chunkSize: any, tenantId: any, request: any) => {
  // Check if messages is a valid array and chunkSize is a positive number
  if (!Array.isArray(messages) || messages.length === 0) {
    logger.error("Invalid or empty messages array provided");
    return;
  }
  if (typeof chunkSize !== 'number' || chunkSize <= 0) {
    logger.error("Invalid chunkSize provided");
    return;
  }
  const MAX_RETRIES = 3; // Maximum number of retries for a chunk
  // Break the messages array into chunks
  for (let i = 0; i < messages.length; i += chunkSize) {
    let retries = 0;
    let success = false;
    const chunk = messages.slice(i, i + chunkSize);
    logger.info(`Total messages count ${messages?.length}`);
    while (retries <= MAX_RETRIES) {
      try {
        logger.info(`Uploading chunk ${Math.floor(i / chunkSize) + 1}/${Math.ceil(messages.length / chunkSize)} of size ${chunkSize}`);

        // Check if tenantId and request are defined
        if (!tenantId || !request) {
          throw new Error("tenantId or request is not defined");
        }

        // Instantiate localisation controller
        const localisation = Localisation.getInstance();

        // Upload the current chunk
        await localisation.createLocalisation(chunk, tenantId, request?.body?.RequestInfo);

        // wait for 3 second
        const waitTime = config.localisation.localizationWaitTimeInBoundaryCreation
        logger.info(`Waiting for ${waitTime / 1000} seconds after each localisation chunk`);
        await new Promise((resolve) => setTimeout(resolve, waitTime));
        await localisation.cacheBurst();

        logger.info(`Successfully uploaded chunk ${Math.floor(i / chunkSize) + 1}`);
        success = true; // Mark as successful
        break;
      } catch (error: any) {
        retries += 1;
        logger.info(`Retrying chunk ${Math.floor(i / chunkSize) + 1}, Attempt ${retries}`);
        logger.error(
          `Error uploading chunk ${Math.floor(i / chunkSize) + 1}, Attempt ${retries}: ${error.message}`
        );

        // If retries are exhausted, log failure and move on
        if (retries > MAX_RETRIES) {
          logger.error(
            `Failed to upload chunk ${Math.floor(i / chunkSize) + 1} after ${MAX_RETRIES} retries`
          );
        }

        // Optional: Add a delay between retries
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }
    if (!success) {
      logger.warn(`Skipping chunk ${Math.floor(i / chunkSize) + 1} after exhausting retries`);
    }
  }
  logger.info("Finished processing all chunks");
};