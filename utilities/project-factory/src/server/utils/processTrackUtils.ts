import config from './../config';
import { produceModifiedMessages } from '../kafka/Listener';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';
import { processTrackStatuses, processTrackTypes } from '../config/constants';
import { logger } from './logger';

async function getProcessDetails(id: any, type?: any): Promise<any> {
    let query: string;
    let values: any[];

    logger.info(`Fetching process details for campaignId: ${id}${type ? `, type: ${type}` : ''}`);

    if (type !== undefined) {
        // If type is provided, include it in the query
        query = `
            SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME}
            WHERE campaignid = $1 AND type = $2
            ORDER BY lastmodifiedtime ASC;
        `;
        values = [id, type];
    } else {
        // If type is not provided, only filter by campaignid
        query = `
            SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME}
            WHERE campaignid = $1
            ORDER BY lastmodifiedtime ASC;
        `;
        values = [id];
    }

    const queryResponse = await executeQuery(query, values);


    if (queryResponse.rows.length === 0) {
        logger.info('No process details found');
        return [];
    }

    const filteredRows = queryResponse.rows
        .filter((result: any) => !(result.type == processTrackTypes.error && result.status === processTrackStatuses.toBeCompleted))
        .map((result: any) => ({
            id: result.id,
            campaignId: result.campaignid,
            type: result.type,
            status: result.status,
            details: result.details,
            additionalDetails: result.additionaldetails,
            createdTime: parseInt(result.createdtime, 10),
            lastModifiedTime: parseInt(result.lastmodifiedtime, 10)
        }));


    return filteredRows;
}

async function persistTrack(
    campaignId: any,
    type: any,
    status: any,
    details?: any,
    additionalDetails?: any
): Promise<void> {
    if (!campaignId) {
        logger.info('campaignId is missing, aborting persistTrack');
        return;
    }

    logger.info(`Persisting track for campaignId: ${campaignId}, type: ${type}, status: ${status}`);

    let processDetailsArray: any[] = [];
    if (status === processTrackStatuses.failed) {
        processDetailsArray = await getProcessDetails(campaignId);
    } else {
        processDetailsArray = await getProcessDetails(campaignId, type);
    }

    if (processDetailsArray.length === 0) {
        logger.info('No process details found, nothing to persist');
        return;
    }

    let processDetails: any | undefined;

    if (status === processTrackStatuses.failed) {
        const inProgressArray = processDetailsArray.filter(
            (pd: any) => pd.status === processTrackStatuses.inprogress
        );
        if (inProgressArray.length > 0) {
            processDetails = inProgressArray[inProgressArray.length - 1];
            logger.info(`Using last in-progress process detail for failed status`);
        } else {
            const errorProcessArray = processDetailsArray.filter(
                (pd: any) => pd.type === processTrackTypes.error
            );
            if (errorProcessArray.length > 0) {
                processDetails = errorProcessArray[0];
                logger.info(`Using first error process detail for failed status`);
            }
        }
    } else {
        processDetails = processDetailsArray[0];
    }

    if (processDetails) {
        updateProcessDetails(processDetails, type, status, details, additionalDetails);
        const produceObject: any = { processDetails };
        const topic = config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC;
        produceModifiedMessages(produceObject, topic);
    }
}

function updateProcessDetails(
    processDetails: any,
    type: any,
    status: any,
    details?: any,
    additionalDetails?: any
) {
    processDetails.lastModifiedTime = Date.now();
    processDetails.details = { ...processDetails.details, ...details } || {};
    processDetails.additionalDetails = { ...processDetails.additionalDetails, ...additionalDetails } || {};
    processDetails.type = type;
    processDetails.status = status;
}

function createProcessTracks(
    campaignId: any,
) {
    logger.info(`Creating process tracks for campaignId: ${campaignId}`);

    const processDetailsArray = [];
    const currentTime = Date.now();
    const processTrackTypesAny: any = processTrackTypes;

    for (const key of Object.keys(processTrackTypes)) {
        const processDetail: any = {
            id: uuidv4(),
            campaignId,
            type: processTrackTypesAny?.[key],
            status: processTrackStatuses.toBeCompleted,
            createdTime: currentTime,
            lastModifiedTime: currentTime,
            details: {},
            additionalDetails: {}
        };
        processDetailsArray.push(processDetail);
    }

    logger.info(`Created ${processDetailsArray.length} process tracks`);

    const processDetails: any = { processDetails: processDetailsArray };
    const topic = config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC;
    produceModifiedMessages(processDetails, topic);
}

export { persistTrack, getProcessDetails, createProcessTracks };
