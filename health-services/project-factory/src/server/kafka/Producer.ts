import { Producer, KafkaClient } from 'kafka-node';
import { getFormattedStringForDebug, logger } from "../utils/logger";
import { shutdownGracefully, throwError } from '../utils/genericUtils';
import config from '../config';

let kafkaClient: KafkaClient;
let producer: Producer;

const createKafkaClientAndProducer = () => {
    kafkaClient = new KafkaClient({
        kafkaHost: config?.host?.KAFKA_BROKER_HOST,
        connectRetryOptions: { retries: 1 },
    });

    // Event listener for 'error' event, indicating that the client encountered an error
    kafkaClient.on('error', (err: any) => {
        logger.error('Kafka client is in error state'); // Log message indicating client is in error state
        console.error(err.stack || err); // Log the error stack or message
        shutdownGracefully();
    });

    producer = new Producer(kafkaClient, { partitionerType: 2 });

    producer.on('ready', () => {
        logger.info('Producer is ready');
        checkBrokerAvailability();
    });

    producer.on('error', (err: any) => {
        logger.error('Producer is in error state');
        console.error(err);
        shutdownGracefully();
    });
};

// Function to check broker availability by listing all brokers
const checkBrokerAvailability = () => {
    kafkaClient.loadMetadataForTopics([], (err: any, data: any) => {
        if (err) {
            logger.error('Error checking broker availability:', err);
            shutdownGracefully();
        } else {
            const brokers = data[1]?.metadata || {};
            const brokerCount = Object.keys(brokers).length;
            logger.info('Broker count:' + String(brokerCount));

            if (brokerCount <= 0) {
                logger.error('No brokers found. Shutting down the service.');
                shutdownGracefully();
            } else {
                logger.info('Brokers are available:', brokers);
            }
        }
    });
};


createKafkaClientAndProducer();

const sendWithReconnect = (payloads: any[]): Promise<void> => {
    return new Promise((resolve, reject) => {
        // Send the message initially
        producer.send(payloads, async (err: any) => {
            if (err) {
                logger.error('Error sending message:', err);
                logger.debug(`Was trying to send: ${getFormattedStringForDebug(payloads)}`);

                // Attempt to reconnect and retry
                logger.error('Reconnecting producer and retrying...');
                producer.close(() => {
                    createKafkaClientAndProducer();
                    setTimeout(() => {
                        // Retry sending after reconnection
                        producer.send(payloads, (err: any) => {
                            if (err) {
                                logger.error('Failed to send message after reconnection:', err);
                                return reject(err); // Final failure, reject promise
                            } else {
                                logger.info('Message sent successfully after reconnection');
                                resolve();
                            }
                        });
                    }, 2000); // wait before retrying after reconnect
                });
            } else {
                logger.info('Message sent successfully');
                resolve();
            }
        });
    });
};


async function produceModifiedMessages(modifiedMessages: any, topic: any, tenantId: string): Promise<void> {
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
            },
        ];

        await sendWithReconnect(payloads);
    } catch (error) {
        logger.error(`KAFKA :: PRODUCER :: Exception caught: ${JSON.stringify(error)}`);
        throwError("COMMON", 400, "KAFKA_ERROR", "Some error occurred in Kafka"); // Re-throw the error after logging it
    }
}

export { produceModifiedMessages };
