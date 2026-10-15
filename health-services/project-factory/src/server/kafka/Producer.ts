import { Kafka, logLevel, LogEntry, CompressionTypes } from 'kafkajs';
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { shutdownGracefully, throwError } from '../utils/genericUtils';
import config from '../config';
import { getTopicName } from '../utils/kafkaTopicUtils';

let kafka: Kafka;
let producer: ReturnType<Kafka['producer']>;
let isProducerReady = false;

// Compress messages so large campaign-detail payloads (up to ~35k boundaries) stay under the
// broker's max message size. Repetitive boundary JSON compresses heavily; consumers (Java
// persister, KafkaJS) auto-decompress GZIP transparently.
const PRODUCER_COMPRESSION = config?.kafka?.KAFKA_PRODUCER_COMPRESSION_ENABLED
    ? CompressionTypes.GZIP
    : CompressionTypes.None;

const createKafkaClientAndProducer = async () => {
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

createKafkaClientAndProducer();

const sendWithReconnect = async (payloads: any[]): Promise<void> => {
    if (!isProducerReady) {
        logger.error('Producer is not ready. Attempting to reconnect...');
        await createKafkaClientAndProducer();
    }
    const { topic, messages, key } = payloads[0];
    try {
        await producer.send({
            topic,
            compression: PRODUCER_COMPRESSION,
            messages: [
                key ? { key, value: messages } : { value: messages }
            ],
        });
        logger.info('Message sent successfully');
    } catch (err) {
        logger.error('Error sending message:', err);
        logger.debug(`Was trying to send: ${getFormattedStringForDebug(payloads)}`);
        logger.error('Reconnecting producer and retrying...');
        try {
            await producer.disconnect();
        } catch {}
    
        await createKafkaClientAndProducer();
        await new Promise(res => setTimeout(res, 2000));
        try {
            await producer.send({
                topic,
                compression: PRODUCER_COMPRESSION,
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


/** Produce a message to the tenant-resolved topic, reconnecting the producer once on send failure. */
async function produceModifiedMessages(modifiedMessages: any, topic: any, tenantId: string , key?: string
): Promise<void> {
    try {
        topic = getTopicName(topic, tenantId);
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
        throwError("COMMON", 400, "KAFKA_ERROR", "Some error occurred in Kafka");
    }
}

export { produceModifiedMessages };
