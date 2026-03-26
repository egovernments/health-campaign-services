import { RequestInfo } from "../config/models/requestInfoSchema";
import { logger } from './logger';
import { httpRequest } from './request';
import config from '../config';

interface WorkerData {
    name: string;
    payeePhoneNumber: string;
    paymentProvider: string;
    payeeName: string;
    bankAccount: string;
    bankCode: string;
    individualId: string;
    tenantId: string;
    id?: string;
}

/** Shape of a worker record returned by the worker registry API */
interface WorkerRegistryRecord {
    id: string;
    individualIds: string[];
    name?: string;
    payeePhoneNumber?: string;
    paymentProvider?: string;
    payeeName?: string;
    bankAccount?: string;
    bankCode?: string;
    rowVersion: number;
    tenantId: string;
    additionalDetails?: Record<string, unknown>;
    auditDetails?: Record<string, unknown>;
    isDeleted?: boolean;
    photoId?: string;
    signatureId?: string;
}

/** Payload for creating a new worker (no id/rowVersion yet) */
interface WorkerCreatePayload {
    name: string;
    payeePhoneNumber: string;
    paymentProvider: string;
    payeeName: string;
    bankAccount: string;
    bankCode: string;
    individualIds: string[];
    tenantId: string;
    additionalDetails: Record<string, unknown>;
}

/** Result of createOrUpdateWorkers — includes partial successes and errors */
interface WorkerRegistryResult {
    individualIdToWorkerIdMap: Map<string, string>;
    errors: string[];
}

function toErrorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

/**
 * Search workers by individual IDs
 */
async function searchWorkersByIndividualIds(
    individualIds: string[],
    tenantId: string,
    requestInfo: RequestInfo
): Promise<WorkerRegistryRecord[]> {
    if (!individualIds.length) return [];

    const url = config.host.workerRegistryHost + config.paths.workerRegistrySearch;
    const requestBody = {
        RequestInfo: requestInfo,
        workerSearch: {
            individualId: individualIds,
            tenantId,
        },
    };

    try {
        const response = await httpRequest(url, requestBody);
        return response?.workers || [];
    } catch (error: unknown) {
        logger.error("Worker registry search failed:", error);
        throw new Error(`Worker search failed: ${toErrorMessage(error)}`);
    }
}

/**
 * Search workers by registry IDs (id field)
 */
async function searchWorkersByIds(
    ids: string[],
    tenantId: string,
    requestInfo: RequestInfo
): Promise<WorkerRegistryRecord[]> {
    if (!ids.length) return [];

    const url = config.host.workerRegistryHost + config.paths.workerRegistrySearch;
    const requestBody = {
        RequestInfo: requestInfo,
        workerSearch: {
            id: ids,
            tenantId,
        },
    };

    try {
        const response = await httpRequest(url, requestBody);
        return response?.workers || [];
    } catch (error: unknown) {
        logger.error("Worker registry search by id failed:", error);
        throw new Error(`Worker search by id failed: ${toErrorMessage(error)}`);
    }
}

/**
 * Create or update workers in the worker registry based on whether they already exist.
 * Uses O(n) lookup via Map for existing workers.
 */
async function createOrUpdateWorkers(
    workerDataList: WorkerData[],
    requestInfo: RequestInfo
): Promise<WorkerRegistryResult> {
    const individualIdToWorkerIdMap = new Map<string, string>();
    const errors: string[] = [];
    if (!workerDataList.length) return { individualIdToWorkerIdMap, errors };

    const tenantId = workerDataList[0].tenantId;

    // Separate workers with explicit workerId from those without
    const workersByIdList = workerDataList.filter(w => !!w.id);
    const workersByIndividualIdList = workerDataList.filter(w => !w.id);

    const workersToCreate: WorkerCreatePayload[] = [];
    const workersToUpdate: WorkerRegistryRecord[] = [];

    // Handle workers with explicit workerId
    if (workersByIdList.length) {
        try {
            const workerIds = workersByIdList.map(w => w.id as string);
            const foundWorkers = await searchWorkersByIds(workerIds, tenantId, requestInfo);
            const foundWorkerMap = new Map<string, WorkerRegistryRecord>();
            for (const worker of foundWorkers) {
                if (worker.id) {
                    foundWorkerMap.set(worker.id, worker);
                }
            }

            for (const data of workersByIdList) {
                const existingWorker = foundWorkerMap.get(data.id as string);
                if (existingWorker) {
                    // Merge individualId into worker's individualIds (dedup)
                    const existingIds: string[] = existingWorker.individualIds || [];
                    const mergedIds = existingIds.includes(data.individualId)
                        ? existingIds
                        : [...existingIds, data.individualId];
                    workersToUpdate.push({
                        ...existingWorker,
                        individualIds: mergedIds,
                        ...(data.name != null ? { name: data.name } : {}),
                        ...(!!data.payeePhoneNumber ? { payeePhoneNumber: data.payeePhoneNumber } : {}),
                        ...(!!data.paymentProvider ? { paymentProvider: data.paymentProvider } : {}),
                        ...(!!data.payeeName ? { payeeName: data.payeeName } : {}),
                        ...(!!data.bankAccount ? { bankAccount: data.bankAccount } : {}),
                        ...(!!data.bankCode ? { bankCode: data.bankCode } : {}),
                        rowVersion: existingWorker.rowVersion || 1,
                    });
                } else {
                    // Worker not found by id — fall through to individualId-based path
                    workersByIndividualIdList.push(data);
                }
            }
        } catch (error: unknown) {
            const msg = `Worker search by id failed: ${toErrorMessage(error)}`;
            logger.error(msg, error);
            errors.push(msg);
        }
    }

    // Handle workers using individualId lookup (original path)
    if (workersByIndividualIdList.length) {
        try {
            const individualIds = workersByIndividualIdList.map(w => w.individualId);
            const existingWorkers = await searchWorkersByIndividualIds(individualIds, tenantId, requestInfo);

            // O(n) map: individualId → existing worker
            const existingWorkerMap = new Map<string, WorkerRegistryRecord>();
            for (const worker of existingWorkers) {
                if (worker.individualIds?.length) {
                    for (const id of worker.individualIds) {
                        existingWorkerMap.set(id, worker);
                    }
                }
            }

            for (const data of workersByIndividualIdList) {
                const existingWorker = existingWorkerMap.get(data.individualId);

                if (existingWorker) {
                    workersToUpdate.push({
                        ...existingWorker,
                        ...(data.name != null ? { name: data.name } : {}),
                        ...(!!data.payeePhoneNumber ? { payeePhoneNumber: data.payeePhoneNumber } : {}),
                        ...(!!data.paymentProvider ? { paymentProvider: data.paymentProvider } : {}),
                        ...(!!data.payeeName ? { payeeName: data.payeeName } : {}),
                        ...(!!data.bankAccount ? { bankAccount: data.bankAccount } : {}),
                        ...(!!data.bankCode ? { bankCode: data.bankCode } : {}),
                        rowVersion: existingWorker.rowVersion || 1,
                    });
                } else {
                    workersToCreate.push({
                        name: data.name,
                        payeePhoneNumber: data.payeePhoneNumber,
                        paymentProvider: data.paymentProvider,
                        payeeName: data.payeeName,
                        bankAccount: data.bankAccount,
                        bankCode: data.bankCode,
                        individualIds: [data.individualId],
                        tenantId: data.tenantId,
                        additionalDetails: {},
                    });
                }
            }
        } catch (error: unknown) {
            const msg = `Worker search by individualId failed: ${toErrorMessage(error)}`;
            logger.error(msg, error);
            errors.push(msg);
        }
    }

    if (workersToCreate.length) {
        const url = config.host.workerRegistryHost + config.paths.workerRegistryBulkCreate;
        const requestBody = {
            RequestInfo: requestInfo,
            workers: workersToCreate,
        };

        try {
            const response = await httpRequest(url, requestBody);
            for (const worker of response?.workers || []) {
                if (worker?.id && worker?.individualIds?.length) {
                    for (const indId of worker.individualIds) {
                        individualIdToWorkerIdMap.set(indId, worker.id);
                    }
                }
            }
            logger.info(`Created ${workersToCreate.length} workers in worker registry`);
        } catch (error: unknown) {
            const msg = `Worker bulk create failed: ${toErrorMessage(error)}`;
            logger.error(msg, error);
            errors.push(msg);
        }
    }

    if (workersToUpdate.length) {
        const url = config.host.workerRegistryHost + config.paths.workerRegistryBulkUpdate;
        const requestBody = {
            RequestInfo: requestInfo,
            workers: workersToUpdate,
        };

        try {
            const response = await httpRequest(url, requestBody);
            for (const worker of response?.workers || []) {
                if (worker?.id && worker?.individualIds?.length) {
                    for (const indId of worker.individualIds) {
                        individualIdToWorkerIdMap.set(indId, worker.id);
                    }
                }
            }
            logger.info(`Updated ${workersToUpdate.length} workers in worker registry`);
        } catch (error: unknown) {
            const msg = `Worker bulk update failed: ${toErrorMessage(error)}`;
            logger.error(msg, error);
            errors.push(msg);
        }
    }

    return { individualIdToWorkerIdMap, errors };
}

export { WorkerData, WorkerRegistryRecord, WorkerRegistryResult, WorkerCreatePayload, searchWorkersByIndividualIds, searchWorkersByIds, createOrUpdateWorkers };
