import config from './../config';
import { produceModifiedMessages } from '../kafka/Listener';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';

async function getProcessDetails(id: any, getNew: any = false): Promise<any> {
    const query = `SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME} WHERE campaignid = $1`;
    const values = [id];
    const queryResponse = await executeQuery(query, values);
    const currentTime = Date.now()
    if (queryResponse.rows.length === 0 && getNew) {
        if (getNew) {
            return {
                id: uuidv4(),
                campaignId: id,
                details: {},
                additionalDetails: {},
                createdTime: currentTime,
                lastModifiedTime: currentTime,
                isNew: true
            }
        }
        else return {};
    }
    else {
        const result = queryResponse.rows[0];
        result.campaignId = result.campaignid;
        delete result.campaignid;
        result.additionalDetails = result.additionaldetails;
        delete result.additionaldetails;
        result.createdTime = result.createdtime;
        delete result.createdtime;
        result.lastModifiedTime = result.lastmodifiedtime;
        delete result.lastmodifiedtime;
        return result;
    }
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
        processDetails = await getProcessDetails(campaignId, true);
    }

    const lastModifiedTime = Date.now();
    processDetails.lastModifiedTime = processDetails.isNew ? processDetails.lastModifiedTime : lastModifiedTime;
    processDetails.details = { ...processDetails?.details, ...details } || {};
    processDetails.additionalDetails = { ...processDetails?.additionalDetails, ...additionalDetails } || {};
    processDetails.type = type;
    processDetails.status = status;
    const produceObject: any = {
        processDetails
    };

    const topic = processDetails.isNew ? config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC : config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC;
    produceModifiedMessages(produceObject, topic);
}

export { persistTrack, getProcessDetails };
