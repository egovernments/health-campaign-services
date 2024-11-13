import Redis from "ioredis";
import config from "../config";
import { logger } from "./logger";

const redis = new Redis({
    host: config.host.redisHost,
    port: parseInt(config.cacheValues.redisPort),
    retryStrategy(times) {
        if (times > 1) {
            return null; // Stop retrying after one attempt
        }
        return 500; // Delay before retrying (in milliseconds)
    },
    maxRetriesPerRequest: 1, // Allow only 1 retry per request
    reconnectOnError(err) {
        return false; // Do not reconnect on errors
    },
});

async function checkRedisConnection(): Promise<boolean> {
    try {
        if (config?.cacheValues?.cacheEnabled) {
            await redis.ping();
        }
        return true;

    } catch (error) {
        console.error("Redis connection error:", error);
        return false;
    }
}

// Listen for the 'connect' event
redis.on('connect', () => {
    logger.info(`Successfully connected to Redis!  host :: ${config.host.redisHost} & port :: ${config.cacheValues.redisPort}`);
    // You can add additional code here to perform actions after a successful connection
});

// Listen for errors
redis.on('error', (err) => {
    logger.info(`failed connecting to Redis!  host :: ${config.host.redisHost} & port :: ${config.cacheValues.redisPort}`);
    logger.error("Redis connection error:", err);
});



async function deleteRedisCacheKeysWithPrefix(prefix: any) {
    try {
        // Use SCAN instead of KEYS to avoid performance issues
        let cursor = '0';
        let keysToDelete: any = [];

        do {
            const result = await redis.scan(cursor, 'MATCH', `${prefix}*`, 'COUNT', '100');
            cursor = result[0];
            const keys = result[1];

            if (keys.length > 0) {
                keysToDelete = keysToDelete.concat(keys);
                logger.info("Cache keys found to be deleted: " + keys);
            }
        } while (cursor !== '0');

        if (keysToDelete.length > 0) {
            await redis.del(...keysToDelete);
            console.log(`Deleted keys with prefix "${prefix}":`, keysToDelete);
        } else {
            console.log(`No keys found with prefix "${prefix}"`);
        }
    } catch (error) {
        console.error("Error deleting keys:", error);
        throw error;
    }
}


export { redis, checkRedisConnection, deleteRedisCacheKeysWithPrefix };
