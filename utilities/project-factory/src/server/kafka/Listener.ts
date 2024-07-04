import { Message, ConsumerGroup, ConsumerGroupOptions } from 'kafka-node';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger'; // Importing logger utility for logging
import { producer } from './Producer'; // Importing producer from the Producer module
import { processCampaignMapping } from '../utils/campaignMappingUtils';
import { enrichAndPersistCampaignWithError } from '../utils/campaignUtils';
import { shutdownGracefully, throwError } from '../utils/genericUtils';



// Replace with the correct Kafka broker(s) and topic name
const kafkaConfig: ConsumerGroupOptions = {
    kafkaHost: config?.host?.KAFKA_BROKER_HOST, // Use the correct broker address and port
    groupId: 'project-factory',
    autoCommit: true,
    autoCommitIntervalMs: 5000,
    fromOffset: 'latest',

};

const topicName = config?.kafka?.KAFKA_START_CAMPAIGN_MAPPING_TOPIC;

// Create a Kafka client
// const kafkaClient = new KafkaClient(kafkaConfig);

// Create a Kafka consumer
// const consumer = new Consumer(kafkaClient, [{ topic: topicName, partition: 0 }], { autoCommit: true });


const consumerGroup = new ConsumerGroup(kafkaConfig, topicName)

// Exported listener function
export function listener() {
    // Set up a message event handler
    consumerGroup.on('message', async (message: Message) => {
        try {
            // Parse the message value as an array of objects
            const messageObject: any = JSON.parse(message.value?.toString() || '{}');
            try {
                // await processCampaignMapping(messageObject);
                logger.info("Received a messageObject for campaign mapping : ");
                logger.debug("Message Object of campaign mapping ::  " + getFormattedStringForDebug(messageObject));
                await processCampaignMapping(messageObject);
            } catch (error: any) {
                console.log(error)
                logger.error(error)
                await enrichAndPersistCampaignWithError(messageObject, error)
            }
            logger.info(`KAFKA :: LISTENER :: Received a message`);
            logger.debug(`KAFKA :: LISTENER :: message ${getFormattedStringForDebug(messageObject)}`);
        } catch (error) {
            logger.info('KAFKA :: LISTENER :: Some Error Occurred '); // Log successful message production
            logger.error(`KAFKA :: LISTENER :: Error :  ${JSON.stringify(error)}`); // Log producer error
            console.log(error)
        }
    });

    // Set up error event handlers
    consumerGroup.on('error', (err) => {
        console.error(`Consumer Error: ${err}`);
        shutdownGracefully();
    });

    consumerGroup.on('offsetOutOfRange', (err) => {
        console.error(`Offset out of range error: ${err}`);
    });
}


/**
 * Produces modified messages to a specified Kafka topic.
 * @param modifiedMessages An array of modified messages to be produced.
 * @param topic The Kafka topic to which the messages will be produced.
 * @returns A promise that resolves when the messages are successfully produced.
 */
function produceModifiedMessages(modifiedMessages: any[], topic: any) {
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
        producer.send(payloads, (err) => {
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
