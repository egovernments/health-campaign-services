import { Kafka, logLevel, EachMessagePayload, ConfigResourceTypes } from 'kafkajs';
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
import { getConsumerTopicPattern, stripTopicPrefix, validateConsumerTopicPrefix, getStartupTopicsToCreate } from '../utils/kafkaTopicUtils';


const kafka = new Kafka({
    clientId: 'project-factory-consumer',
    brokers: config?.host?.KAFKA_BROKER_HOST?.split(',').map(b => b.trim()),
    logLevel: logLevel.NOTHING,
    // Client-level retry governs the cluster metadata operations used by admin createTopics and by
    // consumer.connect()/subscribe() (the consumer's own `retry` only covers the fetch loop). Raising
    // it lets startup ride out the brief leadership election triggered by bulk topic creation instead
    // of throwing KafkaJSNumberOfRetriesExceeded.
    retry: { retries: config?.kafka?.KAFKA_CONSUMER_RETRIES, initialRetryTime: 1000 },
});

async function ensureTopicsExist(topics: string[]) {
    // Base topic names that carry full CampaignDetails (complete boundaries[], up to 35k entries).
    // In central instance mode the caller passes tenant-prefixed names ({state}-{base}); the
    // isLargeTopic matcher handles both bare and prefixed forms so no extra logic is needed here.
    const largeMessageBaseTopics = new Set([
        config.kafka.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
        config.kafka.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
        config.kafka.KAFKA_START_ADMIN_CONSOLE_TASK_TOPIC,
        config.kafka.KAFKA_START_ADMIN_CONSOLE_MAPPING_TASK_TOPIC,
    ]);
    const isLargeTopic = (t: string): boolean =>
        largeMessageBaseTopics.has(t) ||
        [...largeMessageBaseTopics].some(base => t.endsWith(`-${base}`));

    const largeMessageConfigEntries = [
        { name: 'max.message.bytes', value: String(config.kafka.KAFKA_TOPIC_LARGE_MESSAGE_MAX_BYTES) },
        { name: 'compression.type', value: 'gzip' },
    ];

    const admin = kafka.admin();
    try {
        await admin.connect();
        const existing = new Set(await admin.listTopics());
        const missing = topics.filter(t => !existing.has(t));
        if (missing.length > 0) {
            await admin.createTopics({
                topics: missing.map(topic => ({
                    topic,
                    ...(isLargeTopic(topic) ? { configEntries: largeMessageConfigEntries } : {}),
                })),
                waitForLeaders: true,
            });
            logger.info(`KAFKA :: ADMIN :: Created missing topics: ${missing.join(', ')}`);
        }
        // Update already-existing large-message topics that don't yet have the target config.
        const existingLarge = topics.filter(t => existing.has(t) && isLargeTopic(t));
        if (existingLarge.length > 0) {
            const describeResult = await admin.describeConfigs({
                resources: existingLarge.map(name => ({
                    type: ConfigResourceTypes.TOPIC,
                    name,
                    configNames: ['max.message.bytes'],
                })),
                includeSynonyms: false,
            });
            const targetMaxBytes = String(config.kafka.KAFKA_TOPIC_LARGE_MESSAGE_MAX_BYTES);
            const topicsNeedingUpdate = describeResult.resources
                .filter(r => {
                    const entry = r.configEntries.find(e => e.configName === 'max.message.bytes');
                    return !entry || entry.configValue !== targetMaxBytes;
                })
                .map(r => r.resourceName);
            if (topicsNeedingUpdate.length > 0) {
                await admin.alterConfigs({
                    validateOnly: false,
                    resources: topicsNeedingUpdate.map(name => ({
                        type: ConfigResourceTypes.TOPIC,
                        name,
                        configEntries: largeMessageConfigEntries,
                    })),
                });
                logger.info(`KAFKA :: ADMIN :: Set max.message.bytes=${config.kafka.KAFKA_TOPIC_LARGE_MESSAGE_MAX_BYTES} + gzip on topics: ${topicsNeedingUpdate.join(', ')}`);
            }
        }
    } catch (err) {
        logger.warn(`KAFKA :: ADMIN :: Could not ensure topics exist: ${err}`);
    } finally {
        await admin.disconnect();
    }
}

const groupId = config?.kafka?.CONSUMER_GROUP_ID;

// Raise the per-partition fetch limit so the consumer can read large messages
// (default ~1 MB would silently block oversized messages).
const consumer = kafka.consumer({
    groupId,
    maxBytesPerPartition: config?.kafka?.KAFKA_CONSUMER_MAX_BYTES_PER_PARTITION,
});

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

/**
 * Builds a map of base topic name -> handler function.
 * Message routing uses stripTopicPrefix() on the incoming topic
 * to resolve the base topic and look up the handler.
 */
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

        // Produce-only topics that are never subscribed here but must exist with the
        // 4MB + gzip config before the service starts producing large campaign payloads.
        const produceOnlyLargeTopics = [
            config.kafka.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
            config.kafka.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC,
        ];

        // In central instance, expand to tenant-prefixed topics so every required topic
        // exists before the regex subscription is evaluated (KafkaJS only matches topics
        // that exist at subscribe time and does not rediscover new ones afterwards).
        await ensureTopicsExist([
            ...getStartupTopicsToCreate(baseTopics),
            ...getStartupTopicsToCreate(produceOnlyLargeTopics),
        ]);

        await consumer.connect();
        for (const baseTopic of baseTopics) {
            const topicPattern = getConsumerTopicPattern(baseTopic);
            await consumer.subscribe({ topic: topicPattern, fromBeginning: false });
            logger.info(`KAFKA :: LISTENER :: Subscribed to topic: ${String(topicPattern)}`);
        }

        await consumer.run({
            eachMessage: async (payload: EachMessagePayload) => {
                const { topic, message } = payload;
                await acquireSemaphore();
                processMessageKJS(topic, message, topicHandlerMap)
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


async function processMessageKJS(
    topic: string,
    message: { value: Buffer | null },
    topicHandlerMap: Map<string, (msg: any) => Promise<void>>
) {
    try {
        const messageObject = JSON.parse(message.value?.toString() || '{}');

        // message shape varies: RequestInfo (campaign) vs requestInfo (task) vs top-level correlationId (failure)
        const requestInfo = messageObject.RequestInfo ?? messageObject.requestInfo;
        const correlationId: string | null = requestInfo?.correlationId ?? messageObject.correlationId ?? null;
        const tenantId: string | null = requestInfo?.userInfo?.tenantId ?? messageObject.tenantId ?? null;

        await requestContextStore.run({ correlationId, tenantId }, async () => {
            logger.info(`KAFKA :: LISTENER :: Received a message from topic ${topic}`);
            logger.debug(`KAFKA :: LISTENER :: Message: ${getFormattedStringForDebug(messageObject)}`);

            // Strip prefix from incoming topic to resolve the base topic for routing
            const baseTopic = stripTopicPrefix(topic);
            const handler = topicHandlerMap.get(baseTopic);
            if (handler) {
                await handler(messageObject);
            } else {
                logger.warn(`Unhandled topic: ${topic}`);
            }

            logger.info(`KAFKA :: LISTENER :: Processed message from topic ${topic}`);
        });
    } catch (error) {
        logger.error(`KAFKA :: LISTENER :: Error processing message: ${error}`);
        console.error(error);
    }
}
