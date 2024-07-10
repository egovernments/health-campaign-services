import { ConsumerGroup, ConsumerGroupOptions, Message } from 'kafka-node';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger';
import { shutdownGracefully, throwError } from '../utils/genericUtils';
import { handleCampaignMapping, processMapping } from '../utils/campaignMappingUtils';
import { producer } from './Producer';

// Kafka Configuration
const kafkaConfig: ConsumerGroupOptions = {
    kafkaHost: config?.host?.KAFKA_BROKER_HOST,
    groupId: 'project-factory',
    autoCommit: true,
    autoCommitIntervalMs: 5000,
    fromOffset: 'latest',
};

// Topic Names
const topicNames = [
    config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC,
    config.kafka.KAFKA_PROCESS_CAMPAIGN_MAPPING_TOPIC
];

// Consumer Group Initialization
const consumerGroup = new ConsumerGroup(kafkaConfig, topicNames);

// Kafka Listener
export function listener() {
    consumerGroup.on('message', async (message: Message) => {
        try {
            const messageObject = JSON.parse(message.value?.toString() || '{}');

            switch (message.topic) {
                case config.kafka.KAFKA_START_CAMPAIGN_MAPPING_TOPIC:
                    await handleCampaignMapping(messageObject);
                    break;
                case config.kafka.KAFKA_PROCESS_CAMPAIGN_MAPPING_TOPIC:
                    await processMapping(messageObject);
                    break;
                default:
                    logger.warn(`Unhandled topic: ${message.topic}`);
            }

            logger.info(`KAFKA :: LISTENER :: Received a message from topic ${message.topic}`);
            logger.debug(`KAFKA :: LISTENER :: Message: ${getFormattedStringForDebug(messageObject)}`);
        } catch (error) {
            logger.error(`KAFKA :: LISTENER :: Error processing message: ${error}`);
            console.error(error);
        }
    });

    consumerGroup.on('error', (err) => {
        logger.error(`Consumer Error: ${err}`);
        shutdownGracefully();
    });

    consumerGroup.on('offsetOutOfRange', (err) => {
        logger.error(`Offset out of range error: ${err}`);
    });
}



/**
 * Produces modified messages to a specified Kafka topic.
 * @param modifiedMessages An array of modified messages to be produced.
 * @param topic The Kafka topic to which the messages will be produced.
 * @returns A promise that resolves when the messages are successfully produced.
 */
async function produceModifiedMessages(modifiedMessages: any[], topic: any) {
    try {
        logger.info(`KAFKA :: PRODUCER :: a message sent to topic ${topic}`);
        logger.debug(`KAFKA :: PRODUCER :: message ${getFormattedStringForDebug(modifiedMessages)}`);
        const payloads = [
            {
                topic: topic,
                messages: JSON.stringify(modifiedMessages), // Convert modified messages to JSON string
            },
        ];

        // Send payloads to the Kafka producer
        producer.send(payloads, (err: any) => {
            if (err) {
                logger.info('KAFKA :: PRODUCER :: Some Error Occurred ');
                logger.error(`KAFKA :: PRODUCER :: Error :  ${JSON.stringify(err)}`);
            } else {
                logger.info('KAFKA :: PRODUCER :: message sent successfully ');
            }
        });
    } catch (error) {
        logger.error(`KAFKA :: PRODUCER :: Exception caught: ${JSON.stringify(error)}`);
        throwError("COMMON", 400, "KAKFA_ERROR", "Some error occured in kafka"); // Re-throw the error after logging it
    }
}

export { produceModifiedMessages } // Export the produceModifiedMessages function for external use
