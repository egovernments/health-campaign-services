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

    if (localisationMessages.length === 0) {
      logger.info(`No localisation messages to upsert for module ${module}, locale ${locale}`);
      return;
    }

    const localisation = Localisation.getInstance();

    // Delta upsert: only create localisation for names that are new or changed, so an update
    // re-upserts the delta instead of every name (Issue 5 — localisation was the largest phase
    // on a 2k-on-50k update). On a fresh create the existing map is empty, so all are upserted.
    let existingMap: any = {};
    try {
      existingMap = (await localisation.getLocalisedData(module, locale, tenantId, true)) || {};
    } catch (e: any) {
      logger.warn(`Could not fetch existing localisation for ${module}/${locale}; upserting all. ${e?.message}`);
      existingMap = {};
    }
    const messagesToUpsert = localisationMessages.filter(
      (m: any) => existingMap[m.code] !== m.message
    );
    logger.info(
      `Localisation for ${module}/${locale}: ${messagesToUpsert.length} new/changed of ${localisationMessages.length} total`
    );

    // Call the chunk upload function (delta only)
    await uploadInChunks(messagesToUpsert, CHUNK_SIZE, tenantId, request);

    logger.info("All chunks uploaded successfully");

    // Completeness verification (Issue 1): confirm every intended name is now present. An
    // interrupted upsert previously left names missing silently, which later fossilizes codes
    // as names on the regenerate -> re-upload cycle. Detect and flag it instead of hiding it.
    await verifyLocalisationCompleteness(localisation, module, locale, tenantId, localisationMessages, request);

  } catch (error) {
    logger.error("Error during transformation and localisation creation:", error);
    throw error;  // You can further handle this error (e.g., send failure response to client)
  }
}

// Verify that every intended boundary name is present in localisation after the upsert phase.
// Records an incompleteness flag on the resource (rather than failing the whole run, since the
// boundaries themselves were created) so a partial localisation is surfaced, not silent.
const verifyLocalisationCompleteness = async (
  localisation: any,
  module: string,
  locale: string,
  tenantId: string,
  expectedMessages: any[],
  request: any
) => {
  try {
    const afterMap = (await localisation.getLocalisedData(module, locale, tenantId, true)) || {};
    const missing = expectedMessages.filter(
      (m: any) => afterMap[m.code] === undefined || afterMap[m.code] === null
    );
    if (missing.length > 0) {
      const sample = missing.slice(0, 10).map((m: any) => m.code).join(", ");
      logger.error(
        `Localisation INCOMPLETE for ${module}/${locale}: ${missing.length} of ${expectedMessages.length} names missing after upsert (e.g. ${sample})`
      );
      if (request?.body?.ResourceDetails) {
        const additionalDetails = request.body.ResourceDetails.additionalDetails || {};
        request.body.ResourceDetails.additionalDetails = {
          ...additionalDetails,
          localisationIncomplete: true,
          localisationMissingCount: (additionalDetails.localisationMissingCount || 0) + missing.length,
        };
      }
    } else {
      logger.info(`Localisation complete for ${module}/${locale}: all ${expectedMessages.length} names present`);
    }
  } catch (e: any) {
    logger.warn(`Could not verify localisation completeness for ${module}/${locale}: ${e?.message}`);
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

        // NOTE: The localisation messages are only read back later by the generate step (which runs after its
        // own delay), so the settle is now done ONCE after all chunks (see after the loop) instead.
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
  // Single settle + cache-burst AFTER all chunks (replaces the former per-chunk 30s wait). One
  // settle preserves the "localisation is fresh before it is read back" guarantee (the generate
  // step reads it later, after its own delay) while removing the wait that was multiplied by the
  // number of chunks. Tunable via LOCALIZATION_WAIT_TIME_IN_BOUNDARY_CREATION (set 0 to skip).
  const settleTime = config.localisation.localizationWaitTimeInBoundaryCreation;
  if (settleTime > 0) {
    logger.info(`Waiting ${settleTime / 1000}s once after all ${Math.ceil(messages.length / chunkSize)} localisation chunks, then cache-burst`);
    await new Promise((resolve) => setTimeout(resolve, settleTime));
  }
  try {
    await Localisation.getInstance().cacheBurst();
  } catch (e: any) {
    logger.warn(`Final cacheBurst failed: ${e?.message}`);
  }
  logger.info("Finished processing all chunks");
};