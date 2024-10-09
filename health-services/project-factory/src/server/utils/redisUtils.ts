import Redis from "ioredis";
import config from "../config";

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

export { redis, checkRedisConnection };
