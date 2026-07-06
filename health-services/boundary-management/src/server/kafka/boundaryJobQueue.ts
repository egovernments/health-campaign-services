import { Kafka, logLevel } from "kafkajs";
import { randomUUID } from "crypto";
import config from "../config";
import { logger } from "../utils/logger";
import { redis } from "../utils/redisUtils";
import { produceModifiedMessages } from "./Producer";
import { throwError } from "../utils/genericUtils";
import { createBulkBoundaryRelationships } from "../api/genericApis";

/**
 * Distributes bulk relationship chunks as jobs over Kafka so the work spreads across
 * boundary-management pods instead of one pod executing every chunk in-process.
 * Each job carries one already-grouped chunk (<= BULK records, same parent). Every pod
 * runs the consumer (same group), processes jobs one at a time ("line by line"), calls
 * boundary-service's /bulk/_create, and records completion in Redis; the orchestrating
 * pod waits on the Redis counters so the level barrier (all localities before villages)
 * is preserved exactly as in the in-process path.
 */

const doneKey = (trackId: string) => `bjob:${trackId}:done`;
const failedKey = (trackId: string) => `bjob:${trackId}:failed`;
const TRACK_TTL_SECONDS = 3600;

export const dispatchBulkChunksAsJobs = async (request: any, chunks: any[][]) => {
  const trackId = randomUUID();
  const tenantId = request?.body?.ResourceDetails?.tenantId;
  const hierarchyType = request?.body?.ResourceDetails?.hierarchyType;
  logger.info(`Boundary jobs :: dispatching ${chunks.length} job(s) of <=${config.values.bulkRelationshipChunkSize} records (trackId ${trackId})`);

  for (let i = 0; i < chunks.length; i++) {
    const job = {
      trackId,
      jobNo: i + 1,
      totalJobs: chunks.length,
      tenantId,
      hierarchyType,
      records: chunks[i],
      RequestInfo: request?.body?.RequestInfo,
    };
    // Fixed cross-pod topic: skip the central-instance tenant prefix so every pod's consumer
    // sees the same topic name. Keyed by parent so same-parent retries stay ordered.
    await produceModifiedMessages(job, config.boundaryJobs.topic, tenantId, chunks[i]?.[0]?.parent || undefined, true);
  }

  // Barrier: wait until every job is accounted for (done counter includes failed jobs), then
  // surface failures the same way the in-process path does — by throwing.
  const startedAt = Date.now();
  while (true) {
    const done = parseInt((await redis.get(doneKey(trackId))) || "0");
    if (done >= chunks.length) break;
    if (Date.now() - startedAt > config.boundaryJobs.completionTimeoutMs) {
      throwError("BOUNDARY", 500, "BOUNDARY_RELATIONSHIP_BULK_CREATE_ERROR",
        `Boundary jobs timed out: ${done}/${chunks.length} completed after ${config.boundaryJobs.completionTimeoutMs}ms (trackId ${trackId})`);
    }
    await new Promise((resolve) => setTimeout(resolve, config.boundaryJobs.completionPollMs));
  }

  const failures = await redis.lrange(failedKey(trackId), 0, -1);
  if (failures.length > 0) {
    const sample = failures.slice(0, 3).join(" | ");
    throwError("BOUNDARY", 500, "BOUNDARY_RELATIONSHIP_BULK_CREATE_ERROR",
      `${failures.length} of ${chunks.length} boundary job(s) failed (trackId ${trackId}): ${sample}`);
  }
  logger.info(`Boundary jobs :: all ${chunks.length} job(s) completed (trackId ${trackId})`);
};

export const startBoundaryJobConsumer = async () => {
  const kafka = new Kafka({
    clientId: "boundary-management-jobs",
    brokers: config?.host?.KAFKA_BROKER_HOST?.split(",").map((b: string) => b.trim()),
    logLevel: logLevel.WARN,
  });
  const consumer = kafka.consumer({ groupId: config.boundaryJobs.groupId });
  await consumer.connect();
  await consumer.subscribe({ topic: config.boundaryJobs.topic, fromBeginning: false });
  await consumer.run({
    // one message at a time per partition — jobs are processed line by line; parallelism
    // comes from partition count spread across pods, not from concurrency inside a pod
    partitionsConsumedConcurrently: config.boundaryJobs.partitionConcurrency,
    eachMessage: async ({ message, partition }) => {
      let job: any;
      try {
        job = JSON.parse(message.value?.toString() || "{}");
      } catch (e: any) {
        logger.error(`Boundary jobs :: unparseable job message skipped (partition ${partition}): ${e.message}`);
        return;
      }
      if (!job?.trackId || !Array.isArray(job.records)) return;
      const requestLike = {
        body: {
          RequestInfo: job.RequestInfo,
          ResourceDetails: { tenantId: job.tenantId, hierarchyType: job.hierarchyType },
        },
      };
      try {
        await createBulkBoundaryRelationships(requestLike, job.records);
        logger.info(`Boundary jobs :: job ${job.jobNo}/${job.totalJobs} done (${job.records.length} records, trackId ${job.trackId})`);
      } catch (e: any) {
        // The job is accounted (done counter) either way; the orchestrator surfaces the failure.
        // Never rethrow: a poison job must not wedge the partition.
        logger.error(`Boundary jobs :: job ${job.jobNo}/${job.totalJobs} FAILED (trackId ${job.trackId}): ${e.message}`);
        await redis.rpush(failedKey(job.trackId), `job ${job.jobNo}: ${e.message}`);
        await redis.expire(failedKey(job.trackId), TRACK_TTL_SECONDS);
      } finally {
        await redis.incr(doneKey(job.trackId));
        await redis.expire(doneKey(job.trackId), TRACK_TTL_SECONDS);
      }
    },
  });
  logger.info(`Boundary jobs :: consumer started (topic ${config.boundaryJobs.topic}, group ${config.boundaryJobs.groupId})`);
};
