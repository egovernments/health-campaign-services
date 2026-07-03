import { redis } from "./redisUtils";
import { logger } from "./logger";
import { MappingGeneration } from "../config/models/brandedTypes";

const GENERATION_KEY_TTL_SECONDS = 7 * 24 * 60 * 60;

function generationKey(tenantId: string, campaignNumber: string): string {
    return `mapping-gen:${tenantId}:${campaignNumber}`;
}

/**
 * Fencing is fail-open: correctness comes from the adopt-existing pre-pass and the
 * server-side unique validators — a null here only means stale batches can't be detected.
 */
export async function bumpMappingGeneration(tenantId: string, campaignNumber: string): Promise<MappingGeneration | null> {
    try {
        const key = generationKey(tenantId, campaignNumber);
        const value = await redis.incr(key);
        await redis.expire(key, GENERATION_KEY_TTL_SECONDS);
        return value as MappingGeneration;
    } catch (error) {
        logger.warn(`Could not bump mapping generation for ${campaignNumber}; fencing disabled for this cycle: ${error}`);
        return null;
    }
}

export async function getCurrentMappingGeneration(tenantId: string, campaignNumber: string): Promise<MappingGeneration | null> {
    try {
        const value = await redis.get(generationKey(tenantId, campaignNumber));
        if (value == null) return null;
        const parsed = parseInt(value, 10);
        return Number.isNaN(parsed) ? null : (parsed as MappingGeneration);
    } catch (error) {
        logger.warn(`Could not read mapping generation for ${campaignNumber}; fencing disabled: ${error}`);
        return null;
    }
}
