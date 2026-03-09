import { Kafka, logLevel, LogEntry } from 'kafkajs';
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { shutdownGracefully, throwError } from '../utils/genericUtils';
import config from '../config';

let kafka: Kafka;
let producer: ReturnType<Kafka['producer']>;
let isProducerReady = false;

const createKafkaClientAndProducer = async () => {
    // Disconnect old producer and remove its listeners before creating a new one
    if (producer) {
        producer.removeAllListeners();
        try {
            await producer.disconnect();
        } catch {
            // ignore disconnect errors during cleanup
        }
    }

    kafka = new Kafka({
        retry: {
            retries: 5,
            initialRetryTime: 300,
            maxRetryTime: 30000
        },
        clientId: 'project-factory-producer',
        brokers: config?.host?.KAFKA_BROKER_HOST?.split(',').map(b => b.trim()),
        logLevel: logLevel.INFO,
        logCreator: (level) => (log: LogEntry) => {
            if (log.namespace === 'kafka.network' && log.log.message && log.log.message.includes('retry')) {
                logger.info(`[KafkaJS Retry] ${log.log.message}`);
            }
            if (level >= logLevel.INFO && log.log.message) {
                logger.info(`[KafkaJS] ${log.log.message}`);
            }
        }
    });
    producer = kafka.producer();
    try {
        await producer.connect();
        isProducerReady = true;
        logger.info('Producer is ready');
        await checkBrokerAvailability();
    } catch (err) {
        logger.error('Producer connection error:', err);
        shutdownGracefully();
    }
    // Listen for disconnects/errors (fresh listeners on the new producer)
    producer.on('producer.disconnect', () => {
        logger.error('Producer disconnected');
        isProducerReady = false;
        shutdownGracefully();
    });
    producer.on('producer.network.request_timeout', (err: any) => {
        logger.error('Producer network request timeout:', err);
        shutdownGracefully();
    });
};

// Function to check broker availability by listing all brokers
const checkBrokerAvailability = async () => {
    try {
        const admin = kafka.admin();
        await admin.connect();
        const brokerMetadata = await admin.describeCluster();
        const brokers = brokerMetadata.brokers || [];
        const brokerCount = brokers.length;
        logger.info('Broker count:' + String(brokerCount));
        if (brokerCount <= 0) {
            logger.error('No brokers found. Shutting down the service.');
            await admin.disconnect();
            shutdownGracefully();
        } else {
            logger.info('Brokers are available:', brokers);
            await admin.disconnect();
        }
    } catch (err) {
        logger.error('Error checking broker availability:', err);
        shutdownGracefully();
    }
};

// Initialize producer on module load
createKafkaClientAndProducer();

const MAX_SEND_RETRIES = 3;
const INITIAL_RETRY_DELAY_MS = 1000;

const sendWithReconnect = async (payloads: any[]): Promise<void> => {
    const { topic, messages, key } = payloads[0];
    const msgPayload = [key ? { key, value: messages } : { value: messages }];

    for (let attempt = 1; attempt <= MAX_SEND_RETRIES; attempt++) {
        if (!isProducerReady) {
            logger.error(`Producer is not ready. Reconnecting (attempt ${attempt}/${MAX_SEND_RETRIES})...`);
            await createKafkaClientAndProducer();
        }
        try {
            await producer.send({ topic, messages: msgPayload });
            logger.info('Message sent successfully');
            return;
        } catch (err) {
            logger.error(`Error sending message (attempt ${attempt}/${MAX_SEND_RETRIES}):`, err);
            logger.debug(`Was trying to send: ${getFormattedStringForDebug(payloads)}`);

            if (attempt >= MAX_SEND_RETRIES) {
                logger.error('Max send retries exhausted. Giving up.');
                throw err;
            }

            // Reconnect before next attempt with exponential backoff
            const delay = INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt - 1);
            logger.info(`Waiting ${delay}ms before retry...`);
            await new Promise(res => setTimeout(res, delay));
            try {
                await producer.disconnect();
            } catch {
                // ignore disconnect errors during cleanup
            }
            await createKafkaClientAndProducer();
        }
    }
};


async function produceModifiedMessages(modifiedMessages: any, topic: any, tenantId: string , key?: string
): Promise<void> {
    try {
        if(config.isEnvironmentCentralInstance) {
            // If tenantId has no ".", default to tenantId itself
            const firstTenantPartAfterSplit = tenantId.includes(".") ? tenantId.split(".")[0] : tenantId;
            topic = `${firstTenantPartAfterSplit}-${topic}`;
        }
        logger.info(`KAFKA :: PRODUCER :: A message sent to topic ${topic}`);
        logger.debug(`KAFKA :: PRODUCER :: Message ${JSON.stringify(modifiedMessages)}`);
        const payloads = [
            {
                topic: topic,
                messages: JSON.stringify(modifiedMessages),
                key: key || null
            },
        ];

        await sendWithReconnect(payloads);
    } catch (error) {
        logger.error(`KAFKA :: PRODUCER :: Exception caught: ${JSON.stringify(error)}`);
        throwError("COMMON", 400, "KAFKA_ERROR", "Some error occurred in Kafka"); // Re-throw the error after logging it
    }
}

export { produceModifiedMessages };
