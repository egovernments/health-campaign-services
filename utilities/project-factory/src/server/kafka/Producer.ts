import config from '../config'; // Importing configuration settings
import { Producer, KafkaClient } from 'kafka-node'; // Importing Producer and KafkaClient from 'kafka-node' library
import { logger } from "../utils/logger";
import { shutdownGracefully } from '../utils/genericUtils';

// Creating a new Kafka client instance using the configured Kafka broker host
const kafkaClient = new KafkaClient({
    kafkaHost: config?.host?.KAFKA_BROKER_HOST, // Configuring Kafka broker host
    connectRetryOptions: { retries: 1 }, // Configuring connection retry options
});

// Creating a new Kafka producer instance using the Kafka client
const producer = new Producer(kafkaClient, { partitionerType: 2 }); // Using partitioner type 2

// Function to check broker availability
const checkBrokerAvailability = () => {
    kafkaClient.loadMetadataForTopics([], (err, data) => {
        if (err) {
            if (err.message && err.message.toLowerCase().includes('broker not available')) {
                logger.error('Broker not available. Shutting down the service.');
                shutdownGracefully();
            } else {
                logger.error('Error checking broker availability:', err);
            }
        } else {
            logger.info('Broker is available:', data);
        }
    });
};

// Event listener for 'ready' event, indicating that the client is ready to check broker availability
kafkaClient.on('ready', () => {
    logger.info('Kafka client is ready'); // Log message indicating client is ready
    checkBrokerAvailability(); // Check broker availability
});

// Event listener for 'ready' event, indicating that the producer is ready to send messages
producer.on('ready', () => {
    logger.info('Producer is ready'); // Log message indicating producer is ready
    checkBrokerAvailability();
});

// Event listener for 'error' event, indicating that the client encountered an error
kafkaClient.on('error', (err) => {
    logger.error('Kafka client is in error state'); // Log message indicating client is in error state
    console.error(err.stack || err); // Log the error stack or message
    shutdownGracefully();
});

// Event listener for 'error' event, indicating that the producer encountered an error
producer.on('error', (err) => {
    logger.error('Producer is in error state'); // Log message indicating producer is in error state
    console.error(err.stack || err); // Log the error stack or message
    shutdownGracefully();
});

export { producer }; // Exporting the producer instance for external use