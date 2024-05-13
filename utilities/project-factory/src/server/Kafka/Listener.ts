import { Consumer, KafkaClient, Message } from 'kafka-node';
import config from '../config';
import { getFormattedStringForDebug, logger } from '../utils/logger'; // Importing logger utility for logging
import { producer } from './Producer'; // Importing producer from the Producer module
import { processCampaignMapping } from '../utils/campaignMappingUtils';
import { enrichAndPersistCampaignWithError } from '../utils/campaignUtils';



// Replace with the correct Kafka broker(s) and topic name
const kafkaConfig = {
    kafkaHost: config.KAFKA_BROKER_HOST, // Use the correct broker address and port
    groupId: 'project-factory',
    autoCommit: true,
    autoCommitIntervalMs: 5000,
    fromOffset: 'earliest', // Start reading from the beginning of the topic
};

const topicName = config.KAFKA_START_CAMPAIGN_MAPPING_TOPIC;

// Create a Kafka client
const kafkaClient = new KafkaClient(kafkaConfig);

// Create a Kafka consumer
const consumer = new Consumer(kafkaClient, [{ topic: topicName, partition: 0 }], { autoCommit: true });


// Exported listener function
export function listener() {
    // Set up a message event handler
    consumer.on('message', async (message: Message) => {
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
                enrichAndPersistCampaignWithError(messageObject, error)
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
    consumer.on('error', (err) => {
        console.error(`Consumer Error: ${err}`);
    });

    consumer.on('offsetOutOfRange', (err) => {
        console.error(`Offset out of range error: ${err}`);
    });
}


/**
 * Produces modified messages to a specified Kafka topic.
 * @param modifiedMessages An array of modified messages to be produced.
 * @param topic The Kafka topic to which the messages will be produced.
 * @returns A promise that resolves when the messages are successfully produced.
 */
async function produceModifiedMessages(modifiedMessages: any[], topic: any) {
    logger.info(`KAFKA :: PRODUCER :: a message sent to topic ${topic}`);
    logger.debug(`KAFKA :: PRODUCER :: message ${getFormattedStringForDebug(modifiedMessages)}`);
    return new Promise<void>((resolve, reject) => {
        const payloads = [
            {
                topic: topic,
                messages: JSON.stringify(modifiedMessages), // Convert modified messages to JSON string
            },
        ];

        // Send payloads to the Kafka producer
        producer.send(payloads, (err) => {
            if (err) {
                logger.info('KAFKA :: PRODUCER :: Some Error Occurred '); // Log successful message production
                logger.error(`KAFKA :: PRODUCER :: Error :  ${JSON.stringify(err)}`); // Log producer error
                reject(err); // Reject promise if there's an error
            } else {
                logger.info('KAFKA :: PRODUCER :: message sent successfully '); // Log successful message production
                resolve(); // Resolve promise if messages are successfully produced
            }
        });
    });
}

export { produceModifiedMessages } // Export the produceModifiedMessages function for external use