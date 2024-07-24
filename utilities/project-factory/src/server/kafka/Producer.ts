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

// Function to send a test message to check broker availability
const checkBrokerAvailability = () => {
    const payloads = [
        {
            topic: config.kafka.KAFKA_TEST_TOPIC,
            messages: JSON.stringify({ message: 'Test message to check broker availability' }),
        },
    ];

    producer.send(payloads, (err, data) => {
        if (err) {
            if (err.message && err.message.toLowerCase().includes('broker not available')) {
                logger.error('Broker not available. Shutting down the service.');
                shutdownGracefully();
            } else {
                logger.error('Error sending test message:', err);
            }
        } else {
            logger.info('Test message sent successfully:', data);
        }
    });
};

// Event listener for 'ready' event, indicating that the producer is ready to send messages
producer.on('ready', () => {
    logger.info('Producer is ready'); // Log message indicating producer is ready
    checkBrokerAvailability(); // Check broker availability by sending a test message
});

// Event listener for 'error' event, indicating that the producer encountered an error
producer.on('error', (err) => {
    logger.error('Producer is in error state'); // Log message indicating producer is in error state
    console.error(err.stack || err); // Log the error stack or message
    shutdownGracefully();
});

export { producer }; // Exporting the producer instance for external use
