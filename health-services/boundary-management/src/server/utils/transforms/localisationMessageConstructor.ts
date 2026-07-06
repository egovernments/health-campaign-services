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
  if (!tenantId || !request) {
    logger.error("tenantId or request is not defined");
    return;
  }
  const MAX_RETRIES = 3; // Maximum number of retries for a chunk

  // Chunks are independent upserts, so they run in parallel with bounded concurrency instead
  // of serially — on a 50k create the serial loop made localisation the dominant phase.
  const concurrency = config.localisation.localizationUpsertConcurrency;
  const chunks: any[][] = [];
  for (let i = 0; i < messages.length; i += chunkSize) {
    chunks.push(messages.slice(i, i + chunkSize));
  }
  logger.info(`Total messages count ${messages.length}; uploading ${chunks.length} chunk(s) with concurrency ${concurrency}`);
  const localisation = Localisation.getInstance();

  const uploadChunk = async (chunk: any[], chunkNo: number) => {
    let retries = 0;
    while (retries <= MAX_RETRIES) {
      try {
        logger.info(`Uploading chunk ${chunkNo}/${chunks.length} of size ${chunk.length}`);
        await localisation.createLocalisation(chunk, tenantId, request?.body?.RequestInfo);
        logger.info(`Successfully uploaded chunk ${chunkNo}`);
        return;
      } catch (error: any) {
        retries += 1;
        logger.info(`Retrying chunk ${chunkNo}, Attempt ${retries}`);
        logger.error(`Error uploading chunk ${chunkNo}, Attempt ${retries}: ${error.message}`);
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }
    // Exhausted retries: log and move on — the completeness verification pass afterwards
    // detects and flags whatever this chunk left missing.
    logger.error(`Failed to upload chunk ${chunkNo} after ${MAX_RETRIES} retries`);
    logger.warn(`Skipping chunk ${chunkNo} after exhausting retries`);
  };

  for (let i = 0; i < chunks.length; i += concurrency) {
    const batch = chunks.slice(i, i + concurrency);
    await Promise.all(batch.map((chunk, j) => uploadChunk(chunk, i + j + 1)));
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