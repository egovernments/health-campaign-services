import { Kafka, logLevel, KafkaMessage } from 'kafkajs';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger';
import { shutdownGracefully } from '../utils/genericUtils';
import { requestContextStore } from '../utils/requestContext';
import { handleMappingTaskForCampaign } from '../utils/campaignMappingUtils';
import { handleTaskForCampaign } from '../utils/taskUtils';
import { handleProcessingResult } from '../utils/processingResultHandler';
import { handleFacilityBatch } from '../utils/facilityBatchHandler';
import { handleUserBatch } from '../utils/userBatchHandler';
import { handleMappingBatch } from '../utils/mappingBatchHandler';
import { handleCampaignFailure } from '../utils/campaignFailureHandler';
import { getConsumerTopicPattern, stripTopicPrefix, validateConsumerTopicPrefix } from '../utils/kafkaTopicUtils';


const kafka = new Kafka({
    clientId: 'project-factory-consumer',
    brokers: config?.host?.KAFKA_BROKER_HOST?.split(',').map(b => b.trim()),
    logLevel: logLevel.NOTHING,
});

async function ensureTopicsExist(topics: string[]) {
    const admin = kafka.admin();
    try {
        await admin.connect();
        const existing = new Set(await admin.listTopics());
        const missing = topics.filter(t => !existing.has(t));
        if (missing.length > 0) {
            await admin.createTopics({
                topics: missing.map(topic => ({ topic, numPartitions: 1, replicationFactor: 1 })),
                waitForLeaders: true,
            });
            logger.info(`KAFKA :: ADMIN :: Created missing topics: ${missing.join(', ')}`);
        }
    } catch (err) {
        logger.warn(`KAFKA :: ADMIN :: Could not ensure topics exist: ${err}`);
    } finally {
        await admin.disconnect();
    }
}

const groupId = config?.kafka?.CONSUMER_GROUP_ID;

const consumer = kafka.consumer({ groupId });
const dlqProducer = kafka.producer();

function buildTopicHandlerMap(): Map<string, (msg: any) => Promise<void>> {
    const entries: [string, (msg: any) => Promise<void>][] = [
        [config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC, handleTaskForCampaign],
        [config.kafka.KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC, handleMappingTaskForCampaign],
        [config.kafka.KAFKA_HCM_PROCESSING_RESULT_TOPIC, handleProcessingResult],
        [config.kafka.KAFKA_FACILITY_CREATE_BATCH_TOPIC, handleFacilityBatch],
        [config.kafka.KAFKA_USER_CREATE_BATCH_TOPIC, handleUserBatch],
        [config.kafka.KAFKA_MAPPING_BATCH_TOPIC, handleMappingBatch],
        [config.kafka.KAFKA_CAMPAIGN_MARK_FAILED_TOPIC, handleCampaignFailure],
    ];
    return new Map(entries);
}


export async function listener() {
    // Validate prefix configuration at startup
    validateConsumerTopicPrefix();

    try {
        const topicHandlerMap = buildTopicHandlerMap();

        // Build subscription list: handler topics + test topic
        const baseTopics = [
            ...Array.from(topicHandlerMap.keys()),
            config.kafka.KAFKA_TEST_TOPIC,
        ];

        await ensureTopicsExist(baseTopics);

        await consumer.connect();
        await dlqProducer.connect();

        for (const baseTopic of baseTopics) {
            const topicPattern = getConsumerTopicPattern(baseTopic);
            await consumer.subscribe({ topic: topicPattern, fromBeginning: false });
            logger.info(`KAFKA :: LISTENER :: Subscribed to topic: ${String(topicPattern)}`);
        }

        await consumer.run({
            autoCommit: false,
            partitionsConsumedConcurrently: config.kafka.KAFKA_CONSUMER_CONCURRENCY_LIMIT,
            eachMessage: async ({ topic, partition, message }) => {
                try {
                    await processMessageKJS(topic, message, topicHandlerMap);
                } catch (error) {
                    logger.error(`KAFKA :: DLQ :: topic=${topic} error=${error}`);
                    let dlqSent = false;
                    for (let attempt = 1; attempt <= 3; attempt++) {
                        try {
                            await dlqProducer.send({
                                topic: `${topic}-dlq`,
                                messages: [{ value: message.value, headers: { error: String(error) } }],
                            });
                            dlqSent = true;
                            break;
                        } catch (dlqError) {
                            logger.error(`KAFKA :: DLQ :: attempt ${attempt}/3 failed for topic=${topic}: ${dlqError}`);
                        }
                    }
                    if (!dlqSent) {
                        logger.error(`KAFKA :: DLQ :: exhausted retries for topic=${topic}, skipping message to prevent partition stall`);
                    }
                }
                const offsetToCommit = [{ topic, partition, offset: (Number(message.offset) + 1).toString() }];
                for (let attempt = 1; attempt <= 3; attempt++) {
                    try {
                        await consumer.commitOffsets(offsetToCommit);
                        break;
                    } catch (commitError) {
                        if (attempt === 3) throw commitError;
                        logger.error(`KAFKA :: COMMIT :: attempt ${attempt}/3 failed for topic=${topic}: ${commitError}`);
                        await new Promise(resolve => setTimeout(resolve, attempt * 1000));
                    }
                }
            },
        });

        consumer.on(consumer.events.CRASH, async (event) => {
            logger.error(`Consumer crashed: ${event.payload.error}`);
            await dlqProducer.disconnect().catch(() => {});
            shutdownGracefully();
        });
        consumer.on(consumer.events.DISCONNECT, async () => {
            logger.error('Consumer disconnected');
            await dlqProducer.disconnect().catch(() => {});
            shutdownGracefully();
        });
    } catch (err) {
        logger.error(`Consumer Error: ${err}`);
        shutdownGracefully();
    }
}


async function processMessageKJS(
    topic: string,
    message: KafkaMessage,
    topicHandlerMap: Map<string, (msg: any) => Promise<void>>
) {
    const messageObject = JSON.parse(message.value?.toString() || '{}');

    // message shape varies: RequestInfo (campaign) vs requestInfo (task) vs top-level correlationId (failure)
    const requestInfo = messageObject.RequestInfo ?? messageObject.requestInfo;
    const correlationId: string | null = requestInfo?.correlationId ?? messageObject.correlationId ?? null;
    const tenantId: string | null = requestInfo?.userInfo?.tenantId ?? messageObject.tenantId ?? null;

    await requestContextStore.run({ correlationId, tenantId }, async () => {
        logger.info(`KAFKA :: LISTENER :: Received a message from topic ${topic}`);
        logger.debug(`KAFKA :: LISTENER :: Message: ${getFormattedStringForDebug(messageObject)}`);

        const baseTopic = stripTopicPrefix(topic);
        const handler = topicHandlerMap.get(baseTopic);
        if (handler) {
            await handler(messageObject);
        } else {
            logger.warn(`Unhandled topic: ${topic}`);
        }

        logger.info(`KAFKA :: LISTENER :: Processed message from topic ${topic}`);
    });
}
