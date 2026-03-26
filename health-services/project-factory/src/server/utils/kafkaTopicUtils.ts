import config from '../config';

/**
 * Returns the tenant-prefixed topic name when central instance is enabled.
 * Used by the PRODUCER to prefix topics based on the request's tenantId.
 * When disabled, returns the base topic unchanged.
 *
 * Mirrors the pattern used by getTableName in utils/db/index.ts
 */
export function getTopicName(baseTopic: string, tenantId: string): string {
    if (config.isEnvironmentCentralInstance) {
        const stateCode = tenantId.includes(".") ? tenantId.split(".")[0] : tenantId;
        return `${stateCode}-${baseTopic}`;
    }
    return baseTopic;
}

/**
 * Returns a regex pattern for subscribing to a topic on the CONSUMER side.
 * When kafkaConsumerTopicPrefix is set (e.g., "(ba|ke|cg)-"), subscribes
 * using a regex that matches any of the configured state prefixes.
 * When empty, returns the base topic as-is (exact match).
 *
 * @returns RegExp when prefix is set, string when not
 */
export function getConsumerTopicPattern(baseTopic: string): string | RegExp {
    const prefix = config.kafkaConsumerTopicPrefix;
    if (prefix) {
        return new RegExp(`^${prefix}${escapeForRegex(baseTopic)}$`);
    }
    return baseTopic;
}

/**
 * Strips the consumer topic prefix from an incoming topic name to get the base topic.
 * Used for message routing — the handler map is keyed by base topic names.
 */
export function stripTopicPrefix(topic: string): string {
    const prefix = config.kafkaConsumerTopicPrefix;
    if (prefix) {
        const prefixMatch = topic.match(new RegExp(`^${prefix}`));
        if (prefixMatch) {
            return topic.slice(prefixMatch[0].length);
        }
    }
    return topic;
}

/**
 * Validates that kafkaConsumerTopicPrefix is set when central instance is enabled.
 * Should be called at service startup.
 * @throws Error if central instance is enabled but prefix is empty
 */
export function validateConsumerTopicPrefix(): void {
    if (config.isEnvironmentCentralInstance && !config.kafkaConsumerTopicPrefix) {
        throw new Error(
            'KAFKA_CONSUMER_TOPIC_PREFIX must be set when IS_ENVIRONMENT_CENTRAL_INSTANCE is true. ' +
            'Example: "(ba|ke|cg)-" for multi-state or "ng-" for single state.'
        );
    }
}

/**
 * Escapes special regex characters in a string (except already-regex prefix patterns).
 */
function escapeForRegex(str: string): string {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
