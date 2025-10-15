import { Kafka, logLevel, LogEntry } from 'kafkajs';
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { shutdownGracefully, throwError } from '../utils/genericUtils';
import config from '../config';

let kafka: Kafka;
let producer: ReturnType<Kafka['producer']>;
let isProducerReady = false;

const createKafkaClientAndProducer = async () => {
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

const sendWithReconnect = async (payloads: any[]): Promise<void> => {
    // payloads: [{ topic, messages, key }]
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
