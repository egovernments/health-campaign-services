import { logger } from '../utils/logger'; // Importing logger utility for logging
import { producer } from './Producer'; // Importing producer from the Producer module

/**
 * Produces modified messages to a specified Kafka topic.
 * @param modifiedMessages An array of modified messages to be produced.
 * @param topic The Kafka topic to which the messages will be produced.
 * @returns A promise that resolves when the messages are successfully produced.
 */
async function produceModifiedMessages(modifiedMessages: any[], topic: any) {
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
                logger.info(`Producer Error: ${JSON.stringify(err)}`); // Log producer error
                reject(err); // Reject promise if there's an error
            } else {
                logger.info('Produced modified messages successfully.'); // Log successful message production
                resolve(); // Resolve promise if messages are successfully produced
            }
        });
    });
}

export { produceModifiedMessages } // Export the produceModifiedMessages function for external use
