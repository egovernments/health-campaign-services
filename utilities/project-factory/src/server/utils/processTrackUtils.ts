import config from './../config';
import { produceModifiedMessages } from '../kafka/Listener';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';
import { processTrackStatuses, processTrackTypes } from '../config/constants';
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

    return queryResponse.rows.map((result: any) => ({
        id: result.id,
        campaignId: result.campaignid,
        type: result.type,
        status: result.status,
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

    if (status === processTrackStatuses.failed) {
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
        produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC);
    } else {
        const processDetailsArray = await getProcessDetails(campaignId, type);

        if (processDetailsArray.length === 0) {
            logger.info('No process details found, nothing to persist');
            return;
        }

        const processDetails = processDetailsArray[0];
        updateProcessDetails(processDetails, type, status, details, additionalDetails);
        const produceMessage: any = { processDetails };
        produceModifiedMessages(produceMessage, config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC);
    }
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
    const currentTime = Date.now();

    Object.keys(processTrackTypes).forEach(key => {
        const type: any = (processTrackTypes as any)[key];
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

export { persistTrack, getProcessDetails, createProcessTracks };
