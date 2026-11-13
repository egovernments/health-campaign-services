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
// This is a retry-GATE, not just a check: on a miss it re-bursts the localization cache and polls
// the read-back until every name is visible or the bounded window elapses. Upserts are synchronous
// (a 200 means committed), so the first read normally already sees everything and the gate costs
// one search (~1s) — this is what replaced the blind post-upsert settle as the consistency
// guarantee. Only if the window elapses is the incompleteness flag recorded on the resource
// (rather than failing the whole run, since the boundaries themselves were created), so a partial
// localisation is surfaced, not silent.
const verifyLocalisationCompleteness = async (
  localisation: any,
  module: string,
  locale: string,
  tenantId: string,
  expectedMessages: any[],
  request: any
) => {
  const pollMs = config.localisation.localisationVerifyPollIntervalMs;
  const timeoutMs = config.localisation.localisationVerifyTimeoutMs;
  const deadline = Date.now() + timeoutMs;
  let missing: any[] = expectedMessages;
  let attempt = 0;
  while (true) {
    attempt++;
    try {
      const afterMap = (await localisation.getLocalisedData(module, locale, tenantId, true)) || {};
      missing = expectedMessages.filter(
        (m: any) => afterMap[m.code] === undefined || afterMap[m.code] === null
      );
      if (missing.length === 0) {
        logger.info(`Localisation complete for ${module}/${locale}: all ${expectedMessages.length} names present (verify attempt ${attempt})`);
        return;
      }
    } catch (e: any) {
      // A failed read-back must not fail an already-successful run — keep polling until deadline.
      logger.warn(`Localisation verify read-back failed for ${module}/${locale} (attempt ${attempt}): ${e?.message}`);
    }
    if (Date.now() >= deadline) break;
    logger.info(
      `Localisation verify: ${missing.length}/${expectedMessages.length} names not yet visible for ${module}/${locale}; re-bursting cache and retrying in ${pollMs} ms`
    );
    await new Promise((resolve) => setTimeout(resolve, pollMs));
    try {
      await localisation.cacheBurst();
    } catch (e: any) {
      logger.warn(`cacheBurst during localisation verify retry failed: ${e?.message}`);
    }
  }
  const sample = missing.slice(0, 10).map((m: any) => m.code).join(", ");
  logger.error(
    `Localisation INCOMPLETE for ${module}/${locale}: ${missing.length} of ${expectedMessages.length} names missing after ${timeoutMs} ms verify window (e.g. ${sample})`
  );
  if (request?.body?.ResourceDetails) {
    const additionalDetails = request.body.ResourceDetails.additionalDetails || {};
    request.body.ResourceDetails.additionalDetails = {
      ...additionalDetails,
      localisationIncomplete: true,
      localisationMissingCount: (additionalDetails.localisationMissingCount || 0) + missing.length,
    };
  }
}

// Upload a single chunk with the existing per-chunk retry semantics (MAX_RETRIES=3, 1s backoff).
// Extracted from the former serial loop so it can be driven by the bounded promise-pool below;
// the retry/error/logging behaviour per chunk is unchanged.
const uploadSingleChunk = async (
  chunk: any[],
  chunkNumber: number,
  totalChunks: number,
  chunkSize: number,
  tenantId: any,
  request: any
) => {
  const MAX_RETRIES = 3; // Maximum number of retries for a chunk
  let retries = 0;
  let success = false;
  while (retries <= MAX_RETRIES) {
    try {
      logger.info(`Uploading chunk ${chunkNumber}/${totalChunks} of size ${chunkSize}`);

      // Check if tenantId and request are defined
      if (!tenantId || !request) {
        throw new Error("tenantId or request is not defined");
      }

      // Instantiate localisation controller
      const localisation = Localisation.getInstance();

      // Upload the current chunk
      await localisation.createLocalisation(chunk, tenantId, request?.body?.RequestInfo);

      // NOTE: The localisation messages are only read back later by the generate step (which runs after its
      // own delay), so the settle is done ONCE after all chunks (see after the pool) instead.
      logger.info(`Successfully uploaded chunk ${chunkNumber}`);
      success = true; // Mark as successful
      break;
    } catch (error: any) {
      retries += 1;
      logger.info(`Retrying chunk ${chunkNumber}, Attempt ${retries}`);
      logger.error(
        `Error uploading chunk ${chunkNumber}, Attempt ${retries}: ${error.message}`
      );

      // If retries are exhausted, log failure and move on
      if (retries > MAX_RETRIES) {
        logger.error(
          `Failed to upload chunk ${chunkNumber} after ${MAX_RETRIES} retries`
        );
      }

      // Optional: Add a delay between retries
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
  if (!success) {
    logger.warn(`Skipping chunk ${chunkNumber} after exhausting retries`);
  }
};

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
  logger.info(`Total messages count ${messages?.length}`);

  // Break the messages array into chunks up front (chunk size preserved).
  const chunks: any[][] = [];
  for (let i = 0; i < messages.length; i += chunkSize) {
    chunks.push(messages.slice(i, i + chunkSize));
  }
  const totalChunks = chunks.length;

  // Bounded parallelism: upload up to `localizationUpsertConcurrency` chunks in flight at once
  // (default 5 — deliberately modest so egov-localization is not hammered). Upserts are idempotent
  // and per-chunk retry is self-contained, so chunk ORDER does not matter. This replaces the former
  // strictly-serial for-loop, which never consumed the concurrency knob.
  const configuredConcurrency = config.localisation.localizationUpsertConcurrency;
  const concurrency = Math.max(
    1,
    Math.min(
      Number.isFinite(configuredConcurrency) && configuredConcurrency > 0 ? configuredConcurrency : 1,
      totalChunks
    )
  );
  logger.info(`Uploading ${totalChunks} localisation chunk(s) with concurrency ${concurrency}`);

  let nextChunkIndex = 0;
  const worker = async () => {
    // Each worker pulls the next unclaimed chunk index until the queue is drained.
    while (true) {
      const idx = nextChunkIndex++;
      if (idx >= totalChunks) break;
      await uploadSingleChunk(chunks[idx], idx + 1, totalChunks, chunkSize, tenantId, request);
    }
  };
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  // Optional blind settle AFTER all chunks, default 0 (skipped). The "localisation is fresh before
  // it is read back" guarantee now comes from the verify retry-gate that runs after this function
  // (cache-burst, then poll the read-back until every name is visible) — upserts are synchronous,
  // so sleeping here buys nothing the gate does not already prove. Escape hatch:
  // LOCALIZATION_WAIT_TIME_IN_BOUNDARY_CREATION > 0 re-enables the sleep.
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