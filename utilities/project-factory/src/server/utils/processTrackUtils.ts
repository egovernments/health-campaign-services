import config from './../config';
import { produceModifiedMessages } from '../kafka/Listener';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';
import { processTrackStatuses, processTrackTypes } from '../config/constants';

async function getProcessDetails(id: any, type?: any): Promise<any> {
    let query: string;
    let values: any[];

    if (type !== undefined) {
        // If type is provided, include it in the query
        query = `SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME} WHERE campaignid = $1 AND type = $2;`;
        values = [id, type];
    } else {
        // If type is not provided, only filter by campaignid
        query = `SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME} WHERE campaignid = $1;`;
        values = [id];
    }

    const queryResponse = await executeQuery(query, values);

    if (queryResponse.rows.length === 0) {
        return [];
    }

    return queryResponse.rows
        .filter((result: any) => !(result.type == processTrackTypes.error && result.status == processTrackStatuses.toBeCompleted))  // Filter out rows where type is 'error' and status is not 'failed'
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
}



async function persistTrack(
    campaignId: any,
    type: any,
    status: any,
    details?: any,
    additionalDetails?: any
): Promise<void> {
    let processDetails: any;

    if (campaignId) {
        const processDetailsArray = await getProcessDetails(campaignId, type);
        if (processDetailsArray.length > 0) {
            processDetails = processDetailsArray[0];
            processDetails.lastModifiedTime = Date.now();
            processDetails.details = { ...processDetails?.details, ...details } || {};
            processDetails.additionalDetails = { ...processDetails?.additionalDetails, ...additionalDetails } || {};
            processDetails.type = type;
            processDetails.status = status;
            const produceObject: any = {
                processDetails
            };
            const topic = config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC;
            produceModifiedMessages(produceObject, topic);
        }
    }
}

function createProcessTracks(
    campaignId: any,
) {
    var processDetailsArray = []
    const currentTime = Date.now();
    const processTrackTypesAny: any = processTrackTypes
    for (const key of Object.keys(processTrackTypes)) {
        var processDetail: any = {}
        processDetail.id = uuidv4();
        processDetail.campaignId = campaignId;
        processDetail.type = processTrackTypesAny?.[key];
        processDetail.status = processTrackStatuses.toBeCompleted;
        processDetail.createdTime = currentTime;
        processDetail.lastModifiedTime = currentTime;
        processDetail.details = {};
        processDetail.additionalDetails = {};
        processDetailsArray.push(processDetail);
    }
    const processDetails: any = { processDetails: processDetailsArray }
    const topic = config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC;
    produceModifiedMessages(processDetails, topic);
};

export { persistTrack, getProcessDetails, createProcessTracks };
