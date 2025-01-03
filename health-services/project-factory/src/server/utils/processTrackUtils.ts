import config from './../config';
import { produceModifiedMessages } from '../kafka/Listener';
import { v4 as uuidv4 } from 'uuid';
import { executeQuery } from './db';

async function getProcessDetails(id: any): Promise<any> {
    const query = `SELECT * FROM ${config?.DB_CONFIG.DB_CAMPAIGN_PROCESS_TABLE_NAME} WHERE id = $1`;
    const values = [id];
    const queryResponse = await executeQuery(query, values);
    return queryResponse.rows[0]; // Assuming only one row is expected
}

async function persistTrack(
    campaignId: any,
    type: any,
    status: any,
    details?: any,
    additionalDetails?: any,
    id?: any
): Promise<void> {
    let processDetails: any;

    if (id) {
        processDetails = await getProcessDetails(id);
        details = { ...processDetails?.details, ...details };
        additionalDetails = { ...processDetails?.additionalDetails, ...additionalDetails };
    }

    const processId = id || uuidv4();
    const createdTime = Date.now();
    const lastModifiedTime = Date.now();

    processDetails = {
        id: processId,
        campaignId,
        type,
        status,
        details,
        additionalDetails,
        createdTime,
        lastModifiedTime
    };

    const produceObject: any = {
        processDetails
    };

    const topic = id ? config?.kafka?.KAFKA_UPDATE_PROCESS_TRACK_TOPIC : config?.kafka?.KAFKA_SAVE_PROCESS_TRACK_TOPIC;
    produceModifiedMessages(produceObject, topic);
}

export { persistTrack };