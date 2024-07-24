import config from './../config';
import { produceModifiedMessages } from '../kafka/Listener';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';
import { processTrackForUi, processTrackStatuses, processTrackTypes } from '../config/constants';
import { logger } from './logger';

async function getProcessDetails(id: string, type?: string): Promise<any[]> {
    let query: string;
    const values: any[] = [id];

    logger.info(`Fetching process details for campaignId: ${id}${type ? `, type: ${type}` : ''}`);

    if (type) {
        query = `
            SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME}
            WHERE campaignid = $1 AND type = $2
            ORDER BY lastmodifiedtime ASC;
        `;
        values.push(type);
    } else {
        query = `
            SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME}
            WHERE campaignid = $1
            ORDER BY lastmodifiedtime ASC;
        `;
    }

    const queryResponse = await executeQuery(query, values);

    if (queryResponse.rows.length === 0) {
        logger.info('No process details found');
        return [];
    }
    const uiSet = new Set(processTrackForUi.map((item: any) => item));
    return queryResponse.rows.map((result: any) => ({
        id: result.id,
        campaignId: result.campaignid,
        type: result.type,
        status: result.status,
        showInUi: uiSet.has(result.type),
        details: result.details,
        additionalDetails: result.additionaldetails,
        createdTime: parseInt(result.createdtime, 10),
        lastModifiedTime: parseInt(result.lastmodifiedtime, 10),
    }));
}

async function persistTrack(
    campaignId: string,
    type: string,
    status: string,
    details?: Record<string, any>,
    additionalDetails?: Record<string, any>
): Promise<void> {
    if (!campaignId) {
        logger.info('campaignId is missing, aborting persistTrack');
        return;
    }

    logger.info(`Persisting track for campaignId: ${campaignId}, type: ${type}, status: ${status}`);

    if (type == processTrackTypes.error) {
        await handleFailedStatus(campaignId, type, status, details, additionalDetails);
    } else {
        await handleNonFailedStatus(campaignId, type, status, details, additionalDetails);
    }
}

// Handles the case when the status is 'failed'
async function handleFailedStatus(
    campaignId: string,
    type: string,
    status: string,
    details?: Record<string, any>,
    additionalDetails?: Record<string, any>
): Promise<void> {
    const processDetailsArray = await getProcessDetails(campaignId);
    const inProgressProcessDetails = processDetailsArray.filter((processDetail: any) => processDetail.status === processTrackStatuses.inprogress);
    const toBeCompletedProcessDetails = processDetailsArray.filter((processDetail: any) => processDetail.status === processTrackStatuses.toBeCompleted);
    const failedStatusArray = processDetailsArray.filter((processDetail: any) => processDetail.status === processTrackStatuses.failed);
    if (failedStatusArray.length > 0) {
        logger.info('Process already failed, nothing to persist');
        updateToBeCompletedProcess(toBeCompletedProcessDetails, status, details, additionalDetails, config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC);
        return;
    }
    if (inProgressProcessDetails.length > 0) {
        logger.info('Generic fail occured so changing the lastest inprogress status to failed');
        updateAndProduceMessage(inProgressProcessDetails[inProgressProcessDetails.length - 1], status, details, additionalDetails, config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC);
    } else {
        logger.info('No inprogress process found, creating a new processDetail to failed');
        createAndProduceNewProcessDetail(campaignId, type, status, details, additionalDetails, config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC);
    }
    updateToBeCompletedProcess(toBeCompletedProcessDetails, status, details, additionalDetails, config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC);
}

function updateToBeCompletedProcess(
    processDetailsArray: any[],
    status: string,
    details?: Record<string, any>,
    additionalDetails?: Record<string, any>,
    kafkaTopic?: string) {
    details = details || {},
        details.error = "HCM_PROCESS_TRACK_PREVIOUS_PROCESS_FAILED"
    if (processDetailsArray.length > 0) {
        for (let i = 0; i < processDetailsArray.length; i++) {
            updateAndProduceMessage(processDetailsArray[i], status, details, additionalDetails, kafkaTopic);
        }
    }
}

// Handles the case when the status is not 'failed'
async function handleNonFailedStatus(
    campaignId: string,
    type: string,
    status: string,
    details?: Record<string, any>,
    additionalDetails?: Record<string, any>
): Promise<void> {
    const processDetailsArray = await getProcessDetails(campaignId, type);

    if (processDetailsArray.length === 0) {
        logger.info('No process details found, nothing to persist');
        return;
    }

    updateAndProduceMessage(processDetailsArray[0], status, details, additionalDetails, config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC);
}

// Updates an existing process detail and produces the message
function updateAndProduceMessage(
    processDetails: any,
    status: string,
    details?: Record<string, any>,
    additionalDetails?: Record<string, any>,
    kafkaTopic?: string
): void {
    updateProcessDetails(processDetails, processDetails.type, status, details, additionalDetails);
    const produceMessage: any = { processDetails };
    produceModifiedMessages(produceMessage, kafkaTopic);
}

// Creates a new process detail and produces the message
function createAndProduceNewProcessDetail(
    campaignId: string,
    type: string,
    status: string,
    details?: Record<string, any>,
    additionalDetails?: Record<string, any>,
    kafkaTopic?: string
): void {
    const currentTime = Date.now();
    const processDetail: any = {
        id: uuidv4(),
        campaignId,
        type,
        status,
        createdTime: currentTime,
        lastModifiedTime: currentTime,
        details: details || {},
        additionalDetails: additionalDetails || {},
    };

    updateProcessDetails(processDetail, type, status, details, additionalDetails);
    const produceMessage: any = { processDetails: [processDetail] };
    produceModifiedMessages(produceMessage, kafkaTopic);
}


function updateProcessDetails(
    processDetails: any,
    type: string,
    status: string,
    details?: any,
    additionalDetails?: any
) {
    processDetails.lastModifiedTime = Date.now();
    processDetails.details = { ...processDetails.details, ...details };
    processDetails.additionalDetails = { ...processDetails.additionalDetails, ...additionalDetails };
    processDetails.type = type;
    processDetails.status = status;
}

function createProcessTracks(campaignId: string) {
    logger.info(`Creating process tracks for campaignId: ${campaignId}`);

    const processDetailsArray: any[] = [];

    Object.keys(processTrackTypes).forEach(key => {
        const type: any = (processTrackTypes as any)[key];
        const currentTime = Date.now();
        if (type != processTrackTypes.error) {
            const processDetail: any = {
                id: uuidv4(),
                campaignId,
                type,
                status: processTrackStatuses.toBeCompleted,
                createdTime: currentTime,
                lastModifiedTime: currentTime,
                details: {},
                additionalDetails: {}
            };
            processDetailsArray.push(processDetail);
        }
    });

    logger.info(`Created ${processDetailsArray.length} process tracks`);
    const produceMessage: any = { processDetails: processDetailsArray }
    produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC);
}

function getOrderedDetailsArray(toBeCompletedArray: any[]) {
    const order = Object.values(processTrackTypes);
    return toBeCompletedArray.sort((a, b) => order.indexOf(a.type) - order.indexOf(b.type));
}

export function modifyProcessDetails(processDetailsArray: any[]) {
    const toBeCompletedArray = processDetailsArray.filter((item: any) => item.status === processTrackStatuses.toBeCompleted);
    const orderedToBeCompletedArray = getOrderedDetailsArray(toBeCompletedArray);
    const otherArray = processDetailsArray.filter((item: any) => item.status !== processTrackStatuses.toBeCompleted);
    return otherArray.concat(orderedToBeCompletedArray);
}

export { persistTrack, getProcessDetails, createProcessTracks };
