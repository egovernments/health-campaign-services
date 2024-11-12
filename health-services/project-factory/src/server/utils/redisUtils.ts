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
        if(config?.cacheValues?.cacheEnabled){
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
    logger.info(`Successfully connected to Redis!  host :: ${config.host.redisHost} & port :: ${config.cacheValues.redisPort}` );
    // You can add additional code here to perform actions after a successful connection
  });
  
  // Listen for errors
  redis.on('error', (err) => {
  logger.info(`failed connecting to Redis!  host :: ${config.host.redisHost} & port :: ${config.cacheValues.redisPort}` );
  logger.error("Redis connection error:", err);
  });
  


async function deleteRedisCacheKeysWithPrefix(prefix: any) {
    try {
        const keys = await redis.keys(`${prefix}*`);
        logger.info("cache keys to be deleted" + keys);// Get all keys with the specified prefix
        if (keys.length > 0) {
            await redis.del(keys); // Delete all matching keys
            console.log(`Deleted keys with prefix "${prefix}":`, keys);
        } else {
            console.log(`No keys found with prefix "${prefix}"`);
        }
    } catch (error) {
        console.error("Error deleting keys:", error);
        throw error;
    }
}

export { redis, checkRedisConnection , deleteRedisCacheKeysWithPrefix};
