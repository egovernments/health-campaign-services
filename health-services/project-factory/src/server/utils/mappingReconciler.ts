import { RequestInfo } from "../config/models/requestInfoSchema";
import { logger } from './logger';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { getRelatedDataWithCampaign, getMappingDataRelatedToCampaign, checkCampaignMappingCompletionStatus, throwError, getCurrentProcesses, pollUntilCountFn } from './genericUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { mappingStatuses, campaignStatuses, allProcesses, processStatuses } from '../config/constants';
import { v4 as uuidv4 } from 'uuid';
import config from '../config';
import { sendCampaignFailureMessage } from './campaignFailureHandler';
import { executeQuery, getTableName } from './db';
import { bumpMappingGeneration } from './mappingGenerationUtils';

/**
 * Mark all mapping processes as completed
 */
async function markMappingProcessesAsCompleted(
    campaignNumber: string,
    tenantId: string,
    userUuid: string
): Promise<void> {
    try {
        logger.info(`Marking mapping processes as completed for campaign: ${campaignNumber}`);

        const mappingProcessTypes = [
            allProcesses.facilityMapping,
            allProcesses.userMapping,
            allProcesses.resourceMapping
        ];

        const processesToUpdate = [];
        const currentTime = Date.now();

        for (const processType of mappingProcessTypes) {
            const processes = await getCurrentProcesses(campaignNumber, tenantId, processType);

            for (const process of processes) {
                if (process.status !== processStatuses.completed) {
                    process.status = processStatuses.completed;
                    process.auditDetails = {
                        createdBy: process.auditDetails?.createdBy || userUuid,
                        createdTime: process.auditDetails?.createdTime || currentTime,
                        lastModifiedBy: userUuid,
                        lastModifiedTime: currentTime
                    };
                    processesToUpdate.push(process);
                }
            }
        }

        if (processesToUpdate.length > 0) {
            logger.info(`Updating ${processesToUpdate.length} mapping processes to completed status`);
            await produceModifiedMessages(
                { processes: processesToUpdate },
                config.kafka.KAFKA_UPDATE_PROCESS_DATA_TOPIC,
                tenantId
            );
        } else {
            logger.info('All mapping processes are already completed');
        }

    } catch (error) {
        logger.error(`Error marking mapping processes as completed: ${error}`);
        throwError('COMMON', 500, 'PROCESS_UPDATE_ERROR', 'Error updating the statuses of mapping processes');
    }
}

/**
 * Resets retryable failed mapping rows for another reconcile cycle. Each failure
 * status carries its own direction (`failed` → map, `deMapFailed` → demap), so the
 * reset is direction-preserving with no inference.
 */
async function resetRetryableFailedMappings(campaignNumber: string, tenantId: string): Promise<number> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME, tenantId);
    const resetPairs: [string, string][] = [
        [mappingStatuses.failed, mappingStatuses.toBeMapped],
        [mappingStatuses.deMapFailed, mappingStatuses.toBeDeMapped],
    ];
    let resetCount = 0;
    for (const [fromStatus, toStatus] of resetPairs) {
        const result = await executeQuery(
            `UPDATE ${tableName}
             SET status = $1,
                 retryCount = retryCount + 1
             WHERE campaignNumber = $2 AND status = $3 AND retryCount < $4`,
            [toStatus, campaignNumber, fromStatus, config.mapping.maxRetries]
        );
        resetCount += result?.rowCount ?? 0;
    }
    return resetCount;
}

/**
 * Fetches every pending mapping row (toBeMapped + toBeDeMapped, all types) once
 * per cycle — shared by the readiness check and the dispatcher.
 */
async function fetchAllPendingMappings(campaignNumber: string, tenantId: string): Promise<any[]> {
    const allMappings: any[] = [];
    for (const type of ['resource', 'facility', 'user']) {
        const toBeMapped = await getMappingDataRelatedToCampaign(type, campaignNumber, tenantId, mappingStatuses.toBeMapped);
        const toBeDeMapped = await getMappingDataRelatedToCampaign(type, campaignNumber, tenantId, mappingStatuses.toBeDeMapped);
        allMappings.push(...toBeMapped, ...toBeDeMapped);
    }
    return allMappings;
}

/**
 * Waits until every boundary referenced by a pending map-direction row resolves to
 * a created project — replaces blind sleeps with a verified precondition. Fails
 * open: a stall here only means unresolved rows will fail retryably and reconcile
 * next cycle.
 */
async function waitForProjectLookupReadiness(campaignNumber: string, tenantId: string, pendingMappings: any[]): Promise<void> {
    try {
        const neededBoundaryCodes = new Set<string>();
        pendingMappings.forEach((row: any) => {
            if (row?.status === mappingStatuses.toBeMapped && row?.boundaryCode) {
                neededBoundaryCodes.add(row.boundaryCode);
            }
        });
        if (neededBoundaryCodes.size === 0) return;

        await pollUntilCountFn(async () => {
            const boundaries = await getRelatedDataWithCampaign('boundary', campaignNumber, tenantId);
            const resolved = new Set(
                boundaries.filter((b: any) => b?.uniqueIdAfterProcess).map((b: any) => b.uniqueIdentifier)
            );
            let count = 0;
            neededBoundaryCodes.forEach(code => { if (resolved.has(code)) count++; });
            return count;
        }, neededBoundaryCodes.size, {
            label: 'project lookup readiness',
            stallTimeoutMs: config.excelIngestion.persistenceStallTimeoutMs,
            pollIntervalMs: config.excelIngestion.persistencePollIntervalMs
        });
    } catch (error) {
        logger.warn(`Project lookup readiness incomplete for ${campaignNumber}; unresolved mappings will retry next cycle: ${error}`);
    }
}

async function isCampaignMarkedFailed(campaignId: string, tenantId: string): Promise<boolean> {
    try {
        const campaignResponse = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        return campaignResponse?.CampaignDetails?.[0]?.status === campaignStatuses.failed;
    } catch (campaignCheckError) {
        logger.warn(`Could not check campaign status, continuing reconciliation: ${campaignCheckError}`);
        return false;
    }
}

/**
 * Applies the per-type blocking policy exactly once, from terminal states only:
 * facility/resource terminal failures block the campaign; user failures are
 * non-blocking (parallel to user data failures in monitorCampaignDataCompletion).
 */
async function concludeMappingReconciliation(
    campaignNumber: string,
    tenantId: string,
    campaignId: string,
    userUuid: string,
    overall: any
): Promise<void> {
    if (await isCampaignMarkedFailed(campaignId, tenantId)) {
        logger.info(`Campaign ${campaignNumber} is marked as failed — skipping mapping conclusion.`);
        return;
    }

    const [facilityStatus, resourceStatus, userStatus] = await Promise.all([
        checkCampaignMappingCompletionStatus(campaignNumber, tenantId, 'facility'),
        checkCampaignMappingCompletionStatus(campaignNumber, tenantId, 'resource'),
        checkCampaignMappingCompletionStatus(campaignNumber, tenantId, 'user'),
    ]);

    logger.info(`Campaign ${campaignNumber} mapping reconciliation concluded: ${overall.completedMappings}/${overall.totalMappings} completed, ${overall.failedMappings} terminally failed`);

    if (facilityStatus.terminallyFailedMappings > 0 || resourceStatus.terminallyFailedMappings > 0) {
        const failureError = new Error(`Mapping failed after retries: facility ${facilityStatus.terminallyFailedMappings} failed, resource ${resourceStatus.terminallyFailedMappings} failed`);
        logger.error(`Campaign ${campaignNumber} has hard-blocking terminal mapping failures. Marking campaign as failed.`);
        await sendCampaignFailureMessage(campaignId, tenantId, failureError);
        throw failureError;
    }

    if (userStatus.failedMappings > 0) {
        logger.warn(`Campaign ${campaignNumber} has user-mapping failures: ${userStatus.failedMappings} out of ${userStatus.totalMappings}. Failures are non-blocking and visible in the credential sheet.`);
    }

    await markMappingProcessesAsCompleted(campaignNumber, tenantId, userUuid);
}

/**
 * Dispatches pre-fetched pending mappings in generation-stamped Kafka batches.
 */
async function dispatchMappingBatches(
    campaignDetails: any,
    useruuid: string,
    tenantId: string,
    allMappings: any[],
    requestInfo?: RequestInfo,
    generation?: number | null
): Promise<number> {
    try {
        const campaignNumber = campaignDetails.campaignNumber;

        if (allMappings.length === 0) {
            logger.info('No mappings found to process');
            return 0;
        }

        logger.info(`Found ${allMappings.length} total mappings to process for campaign: ${campaignNumber}`);

        const BATCH_SIZE = config.mapping.kafkaBatchSize;
        const totalBatches = Math.ceil(allMappings.length / BATCH_SIZE);

        for (let i = 0; i < allMappings.length; i += BATCH_SIZE) {
            const batch = allMappings.slice(i, i + BATCH_SIZE);
            const batchNumber = Math.floor(i / BATCH_SIZE) + 1;

            const batchMessage = {
                tenantId,
                campaignNumber,
                campaignId: campaignDetails.id,
                useruuid,
                mappings: batch,
                batchNumber,
                totalBatches,
                requestInfo,
                generation: generation ?? null
            };

            logger.info(`Sending mapping batch ${batchNumber}/${totalBatches} to Kafka: ${batch.length} mappings`);

            // Random partition key by design: uniqueness comes from dispatch
            // disjointness + fencing, never from partition affinity.
            const partitionKey = uuidv4();

            await produceModifiedMessages(
                batchMessage,
                config.kafka.KAFKA_MAPPING_BATCH_TOPIC,
                tenantId,
                partitionKey
            );
        }

        logger.info(`All ${totalBatches} mapping batches sent to Kafka for processing`);
        return allMappings.length;

    } catch (error) {
        logger.error('Error starting mappings in batches:', error);
        throw error;
    }
}

/**
 * Convergence-driven mapping lifecycle: the mapping table is desired state,
 * health-project is actual state. Each cycle resets retryable failures, verifies
 * the project lookup precondition, dispatches generation-fenced batches, and
 * observes progress with a stall-based wait — a stalled or lost batch is simply
 * re-dispatched next cycle. Failure is judged only from terminal states at the end.
 */
export async function runMappingReconciler(
    campaignDetails: any,
    useruuid: string,
    tenantId: string,
    requestInfo: RequestInfo | undefined,
    campaignAlreadyFailed: { value: boolean }
): Promise<void> {
    const campaignNumber = campaignDetails.campaignNumber;
    const campaignId = campaignDetails.id;
    const maxCycles = config.mapping.maxReconcileCycles;

    for (let cycle = 1; cycle <= maxCycles; cycle++) {
        if (await isCampaignMarkedFailed(campaignId, tenantId)) {
            logger.info(`Campaign ${campaignNumber} is already marked as failed. Stopping mapping reconciliation.`);
            campaignAlreadyFailed.value = true;
            return;
        }

        const resetCount = await resetRetryableFailedMappings(campaignNumber, tenantId);
        if (resetCount > 0) {
            logger.info(`Mapping cycle ${cycle}/${maxCycles}: reset ${resetCount} retryable failed mappings`);
        }

        const pendingMappings = await fetchAllPendingMappings(campaignNumber, tenantId);

        await waitForProjectLookupReadiness(campaignNumber, tenantId, pendingMappings);

        const generation = await bumpMappingGeneration(tenantId, campaignNumber);
        const dispatchedCount = await dispatchMappingBatches(campaignDetails, useruuid, tenantId, pendingMappings, requestInfo, generation);

        const snapshot = await checkCampaignMappingCompletionStatus(campaignNumber, tenantId);
        if (snapshot.totalMappings === 0 && dispatchedCount === 0) {
            logger.info(`No mappings found for campaign ${campaignNumber} — nothing to reconcile`);
            return;
        }

        try {
            await pollUntilCountFn(async () => {
                const status = await checkCampaignMappingCompletionStatus(campaignNumber, tenantId);
                logger.info(`Campaign ${campaignNumber} mapping cycle ${cycle}/${maxCycles}: ${status.completedMappings}/${status.totalMappings} completed, ${status.failedMappings} failed, ${status.pendingMappings} pending`);
                return status.completedMappings + status.failedMappings;
            }, snapshot.totalMappings, {
                label: `mapping resolution (cycle ${cycle}/${maxCycles})`,
                stallTimeoutMs: config.mapping.reconcileStallTimeoutMs,
                pollIntervalMs: config.resourceCreationConfig.waitTimeOfEachAttemptOfResourceCreationOrMappping
            });
        } catch (stallError) {
            logger.warn(`Mapping cycle ${cycle}/${maxCycles} stalled; unresolved rows will be re-dispatched next cycle: ${stallError}`);
            if (await isCampaignMarkedFailed(campaignId, tenantId)) {
                logger.info(`Campaign ${campaignNumber} was marked as failed during mapping observation. Stopping reconciliation.`);
                campaignAlreadyFailed.value = true;
                return;
            }
            continue;
        }

        if (await isCampaignMarkedFailed(campaignId, tenantId)) {
            logger.info(`Campaign ${campaignNumber} was marked as failed during mapping observation. Stopping reconciliation.`);
            campaignAlreadyFailed.value = true;
            return;
        }

        const status = await checkCampaignMappingCompletionStatus(campaignNumber, tenantId);
        if (status.pendingMappings === 0 && status.retryableFailedMappings === 0) {
            await concludeMappingReconciliation(campaignNumber, tenantId, campaignId, useruuid, status);
            return;
        }
        logger.info(`Mapping cycle ${cycle}/${maxCycles} ended with ${status.retryableFailedMappings} retryable failures; reconciling again`);
    }

    const finalStatus = await checkCampaignMappingCompletionStatus(campaignNumber, tenantId);
    const exhaustedError = new Error(`Mapping reconciliation exhausted ${maxCycles} cycles: ${finalStatus.pendingMappings} pending, ${finalStatus.failedMappings} failed of ${finalStatus.totalMappings}`);
    logger.error(`Campaign ${campaignNumber} mapping reconciliation exhausted its cycle budget`);
    await sendCampaignFailureMessage(campaignId, tenantId, exhaustedError);
    throw exhaustedError;
}
