import { Kafka, logLevel, EachMessagePayload } from 'kafkajs';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger';
import { shutdownGracefully } from '../utils/genericUtils';
import { handleCampaignMapping, handleMappingTaskForCampaign } from '../utils/campaignMappingUtils';
import { handleTaskForCampaign } from '../utils/taskUtils';
import { handleProcessingResult } from '../utils/processingResultHandler';
import { handleFacilityBatch } from '../utils/facilityBatchHandler';
import { handleUserBatch } from '../utils/userBatchHandler';


const kafka = new Kafka({
    clientId: 'project-factory-consumer',
    brokers: config?.host?.KAFKA_BROKER_HOST?.split(',').map(b => b.trim()),
    logLevel: logLevel.NOTHING,
});

const groupId = 'project-factory';


const topicNames = [
    config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC,
    config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC,
    config.kafka.KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC,
    config.kafka.KAFKA_TEST_TOPIC,
    config.kafka.KAFKA_HCM_PROCESSING_RESULT_TOPIC,
    config.kafka.KAFKA_FACILITY_CREATE_BATCH_TOPIC,
    config.kafka.KAFKA_USER_CREATE_BATCH_TOPIC
];


const consumer = kafka.consumer({ groupId });

// Add a simple semaphore for concurrency control
const MAX_CONCURRENT = 10;
let currentConcurrent = 0;
const queue: (() => void)[] = [];

function acquireSemaphore() {
    return new Promise<void>((resolve) => {
        if (currentConcurrent < MAX_CONCURRENT) {
            currentConcurrent++;
            resolve();
        } else {
            queue.push(resolve);
            logger.warn(`Kafka listener concurrency limit reached (${MAX_CONCURRENT}). Message will wait.`);
        }
    });
}

function releaseSemaphore() {
    currentConcurrent--;
    if (queue.length > 0) {
        const next = queue.shift();
        if (next) next();
    }
}


export async function listener() {
    try {
        await consumer.connect();
        for (const topic of topicNames) {
            await consumer.subscribe({ topic, fromBeginning: false });
        }

        await consumer.run({
            eachMessage: async (payload: EachMessagePayload) => {
                const { topic, message } = payload;
                await acquireSemaphore();
                processMessageKJS(topic, message)
                    .finally(() => {
                        releaseSemaphore();
                    });
            },
        });

        consumer.on(consumer.events.CRASH, async (event) => {
            logger.error(`Consumer crashed: ${event.payload.error}`);
            shutdownGracefully();
        });
        consumer.on(consumer.events.DISCONNECT, () => {
            logger.error('Consumer disconnected');
            shutdownGracefully();
        });
    } catch (err) {
        logger.error(`Consumer Error: ${err}`);
        shutdownGracefully();
    }
}


async function processMessageKJS(topic: string, message: { value: Buffer | null }) {
    try {
        const messageObject = JSON.parse(message.value?.toString() || '{}');

        switch (topic) {
            case config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC:
                handleCampaignMapping(messageObject);
                break;
            case config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC:
                handleTaskForCampaign(messageObject);
                break;
            case config.kafka.KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC:
                handleMappingTaskForCampaign(messageObject);
                break;
            case config.kafka.KAFKA_HCM_PROCESSING_RESULT_TOPIC:
                handleProcessingResult(messageObject);
                break;
            case config.kafka.KAFKA_FACILITY_CREATE_BATCH_TOPIC:
                handleFacilityBatch(messageObject);
                break;
            case config.kafka.KAFKA_USER_CREATE_BATCH_TOPIC:
                handleUserBatch(messageObject);
                break;
            default:
                logger.warn(`Unhandled topic: ${topic}`);
        }

        logger.info(`KAFKA :: LISTENER :: Received a message from topic ${topic}`);
        logger.debug(`KAFKA :: LISTENER :: Message: ${getFormattedStringForDebug(messageObject)}`);
    } catch (error) {
        logger.error(`KAFKA :: LISTENER :: Error processing message: ${error}`);
        console.error(error);
    }
}
