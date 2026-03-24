import { Kafka, logLevel, LogEntry } from 'kafkajs';
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { shutdownGracefully, throwError } from '../utils/genericUtils';
import config from '../config';

let kafka: Kafka;
let producer: ReturnType<Kafka['producer']>;
let isProducerReady = false;
let initializationPromise: Promise<void> | null = null;

const createKafkaClientAndProducer = async () => {
    // Single-flight pattern: if initialization is already in progress, wait for it
    if (initializationPromise) {
        return initializationPromise;
    }

    initializationPromise = (async () => {
        kafka = new Kafka({
            retry: {
                retries: 5,
                initialRetryTime: 300,
                maxRetryTime: 30000
            },
            clientId: 'boundary-management-producer',
            brokers: config?.host?.KAFKA_BROKER_HOST?.split(',').map(b => b.trim()),
            logLevel: logLevel.INFO,
            logCreator: (level) => (log: LogEntry) => {
                if (log.namespace === 'kafka.network' && log.log.message && log.log.message.includes('retry')) {
                    logger.info(`[KafkaJS Retry] ${log.log.message}`);
                }
                // Optionally, log all KafkaJS logs at INFO or higher
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
        // Listen for disconnects/errors
        producer.on('producer.disconnect', () => {
            logger.error('Producer disconnected');
            isProducerReady = false;
            shutdownGracefully();
        });
        producer.on('producer.network.request_timeout', (err: any) => {
            logger.error('Producer network request timeout:', err);
            shutdownGracefully();
        });
    })();

    try {
        await initializationPromise;
    } finally {
        // Clear the promise after initialization completes (success or failure)
        initializationPromise = null;
    }
};

// Function to check broker availability by listing all brokers
const checkBrokerAvailability = async () => {
    const admin = kafka.admin();
    try {
        await admin.connect();
        const brokerMetadata = await admin.describeCluster();
        const brokers = brokerMetadata.brokers || [];
        const brokerCount = brokers.length;
        logger.info('Broker count:' + String(brokerCount));
        if (brokerCount <= 0) {
            logger.error('No brokers found. Shutting down the service.');
            shutdownGracefully();
        } else {
            logger.info('Brokers are available:', brokers);
        }
    } catch (err) {
        logger.error('Error checking broker availability:', err);
        shutdownGracefully();
    } finally {
        // Always disconnect admin client to prevent connection leaks
        try {
            await admin.disconnect();
        } catch (disconnectErr) {
            logger.error('Error disconnecting admin client:', disconnectErr);
        }
    }
};

// Initialize producer on module load
createKafkaClientAndProducer();

/**
 * Sends Kafka messages with automatic reconnection handling.
 * @param payloads - Array of message payloads. For API compatibility, accepts an array
 *                   but currently only processes the first element (payloads[0]).
 *                   All production callers pass single-element arrays.
 */
const sendWithReconnect = async (payloads: any[]): Promise<void> => {
    // Runtime guard: warn if multiple payloads are passed
    if (payloads.length > 1) {
        logger.warn(`sendWithReconnect received ${payloads.length} payloads but only the first will be processed. This may indicate unintended usage.`);
    }

    if (!isProducerReady) {
        logger.error('Producer is not ready. Attempting to reconnect...');
        await createKafkaClientAndProducer();
    }
    const { topic, messages, key } = payloads[0];
    try {
        await producer.send({
            topic,
            messages: [
                key ? { key, value: messages } : { value: messages }
            ],
        });
        logger.info('Message sent successfully');
    } catch (err) {
        logger.error('Error sending message:', err);
        logger.debug(`Was trying to send: ${getFormattedStringForDebug(payloads)}`);
        // Attempt to reconnect and retry
        logger.error('Reconnecting producer and retrying...');
        try {
            await producer.disconnect();
        } catch {}
    
        await createKafkaClientAndProducer();
        await new Promise(res => setTimeout(res, 2000));
        try {
            await producer.send({
                topic,
                messages: [
                    key ? { key, value: messages } : { value: messages }
                ],
            });
            logger.info('Message sent successfully after reconnection');
        } catch (err2) {
            logger.error('Failed to send message after reconnection:', err2);
            throw err2;
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
