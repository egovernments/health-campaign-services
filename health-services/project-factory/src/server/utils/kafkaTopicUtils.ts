import config from '../config';

/**
 * Parses a comma-separated string into a clean list: trims each entry,
 * drops empties (handles whitespace / extra or trailing commas), and dedupes
 * while preserving order.
 */
export function parseTenantIds(raw: string): string[] {
    if (!raw) return [];
    const seen = new Set<string>();
    const result: string[] = [];
    for (const part of raw.split(',')) {
        const id = part.trim();
        if (id && !seen.has(id)) {
            seen.add(id);
            result.push(id);
        }
    }
    return result;
}

/**
 * Returns the configured central-instance tenant ids (single source of truth
 * for startup topic creation and the consumer subscription regex).
 */
export function getCentralInstanceTenantIds(): string[] {
    return parseTenantIds(config.centralInstanceTenantIds);
}

/**
 * Returns the set of topics that must NEVER be tenant-prefixed (e.g. shared
 * email notification topic), parsed from the comma-separated config value.
 */
export function getNonCentralTopics(): Set<string> {
    return new Set(parseTenantIds(config.kafka.KAFKA_NON_CENTRAL_INSTANCE_TOPICS));
}

/**
 * Returns the tenant-prefixed topic name when central instance is enabled.
 * Used by the PRODUCER to prefix topics based on the request's tenantId.
 * When disabled, returns the base topic unchanged.
 *
 * Mirrors the pattern used by getTableName in utils/db/index.ts
 */
export function getTopicName(baseTopic: string, tenantId: string): string {
    if (config.isEnvironmentCentralInstance && !getNonCentralTopics().has(baseTopic)) {
        const stateCode = tenantId.includes(".") ? tenantId.split(".")[0] : tenantId;
        return `${stateCode}-${baseTopic}`;
    }
    return baseTopic;
}

/**
 * Resolves the consumer-side topic prefix pattern (e.g. "(ba|ke|cg)-").
 * An explicit KAFKA_CONSUMER_TOPIC_PREFIX always wins (back-compat); otherwise
 * the pattern is derived from centralInstanceTenantIds so the subscription regex
 * can never drift from the topics created at startup.
 */
export function getEffectiveConsumerPrefix(): string {
    if (config.kafkaConsumerTopicPrefix) {
        return config.kafkaConsumerTopicPrefix;
    }
    if (config.isEnvironmentCentralInstance) {
        const tenants = getCentralInstanceTenantIds();
        if (tenants.length > 0) {
            return `(${tenants.join("|")})-`;
        }
    }
    return "";
}

/**
 * Expands the base topics into the concrete topics that must exist before the
 * consumer subscribes. In central instance each base topic is created per tenant
 * (`<tenant>-<base>`), except non-central topics which stay bare. In non-central
 * mode the base topics are returned unchanged (no prefix).
 */
export function getStartupTopicsToCreate(baseTopics: string[]): string[] {
    if (!config.isEnvironmentCentralInstance) {
        return baseTopics;
    }
    const tenants = getCentralInstanceTenantIds();
    const nonCentral = getNonCentralTopics();
    const seen = new Set<string>();
    const result: string[] = [];
    const add = (topic: string): void => {
        if (!seen.has(topic)) {
            seen.add(topic);
            result.push(topic);
        }
    };
    for (const base of baseTopics) {
        if (nonCentral.has(base) || tenants.length === 0) {
            add(base);
        } else {
            for (const tenant of tenants) add(`${tenant}-${base}`);
        }
    }
    return result;
}

/**
 * Returns a regex pattern for subscribing to a topic on the CONSUMER side.
 * When a prefix is resolved (explicit override or derived from the tenant list),
 * subscribes using a regex that matches any of the configured state prefixes.
 * When empty, returns the base topic as-is (exact match).
 *
 * @returns RegExp when prefix is set, string when not
 */
export function getConsumerTopicPattern(baseTopic: string): string | RegExp {
    const prefix = getEffectiveConsumerPrefix();
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
    const prefix = getEffectiveConsumerPrefix();
    if (prefix) {
        const prefixMatch = topic.match(new RegExp(`^${prefix}`));
        if (prefixMatch) {
            return topic.slice(prefixMatch[0].length);
        }
    }
    return topic;
}

/**
 * Validates that a consumer prefix can be resolved when central instance is enabled —
 * either via explicit KAFKA_CONSUMER_TOPIC_PREFIX or CENTRAL_INSTANCE_TENANT_IDS.
 * Should be called at service startup.
 * @throws Error if central instance is enabled but neither is set
 */
export function validateConsumerTopicPrefix(): void {
    if (config.isEnvironmentCentralInstance && !getEffectiveConsumerPrefix()) {
        throw new Error(
            'CENTRAL_INSTANCE_TENANT_IDS (or KAFKA_CONSUMER_TOPIC_PREFIX) must be set when ' +
            'IS_ENVIRONMENT_CENTRAL_INSTANCE is true. ' +
            'Example: CENTRAL_INSTANCE_TENANT_IDS="ba,ke,cg" or KAFKA_CONSUMER_TOPIC_PREFIX="(ba|ke|cg)-".'
        );
    }
}

/**
 * Escapes special regex characters in a string (except already-regex prefix patterns).
 */
function escapeForRegex(str: string): string {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
