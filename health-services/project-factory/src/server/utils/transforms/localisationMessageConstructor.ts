import {
  getLocaleFromRequest,
  getLocalisationModuleName,
} from "../localisationUtils";
import Localisation from "../../controllers/localisationController/localisation.controller";
import { logger } from "../logger";

/**
 * Transforms boundary map into localisation messages and creates localisation entries.
 * @param boundaryMap - Map of boundary keys and codes.
 * @param hierarchyType - Type of hierarchy for the localisation module.
 * @param request - Request object containing necessary information.
 */

export const transformAndCreateLocalisation = async (
  boundaryMap: any,
  request: any
) => {
  const CHUNK_SIZE = 200;  // Adjust this size based on your API limits and performance

  try {
    const { tenantId, hierarchyType } = request?.body?.ResourceDetails || {};

    // Get localisation module name based on hierarchy type
    const module = getLocalisationModuleName(hierarchyType);

    // Get locale from request object
    const locale = getLocaleFromRequest(request);

    // Array to store localisation messages
    const localisationMessages: any[] = [];

    // Iterate over boundary map to transform into localisation messages
    boundaryMap.forEach((code: string, boundary: any) => {
      localisationMessages.push({
        code,
        message: boundary.value,
        module,
        locale,
      });
    });

    logger.info("Localisation message transformed successfully from the boundary map");

    // Instantiate localisation controller
    const localisation = Localisation.getInstance();

    // Function to process data in chunks
    const uploadInChunks = async (messages: any[], chunkSize: number) => {
      // Break the messages array into chunks
      for (let i = 0; i < messages.length; i += chunkSize) {
        const chunk = messages.slice(i, i + chunkSize);

        try {
          logger.info(`Uploading chunk ${i / chunkSize + 1}/${Math.ceil(messages.length / chunkSize)}`);

          // Upload the current chunk
          await localisation.createLocalisation(chunk, tenantId, request);

          logger.info(`Successfully uploaded chunk ${i / chunkSize + 1}`);
        } catch (error) {
          logger.error(`Error uploading chunk ${i / chunkSize + 1}:`, error);
          throw error;  // Optionally: handle error (e.g., retry mechanism) here instead of throwing
        }
      }
    };

    // Call the chunk upload function
    await uploadInChunks(localisationMessages, CHUNK_SIZE);

    logger.info("All chunks uploaded successfully");

  } catch (error) {
    logger.error("Error during transformation and localisation creation:", error);
    throw error;  // You can further handle this error (e.g., send failure response to client)
  }
};

