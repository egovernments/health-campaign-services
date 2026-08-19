import config from "../config";

/** Flat override via PERSISTENCE_WAIT_TIME_MS wins; otherwise scale with row count above a 5s floor. */
export function getPersistenceWaitTime(count: number): number {
  if (config.persistenceWaitTimeMs > 0) return config.persistenceWaitTimeMs;
  return Math.max(5000, (count || 0) * 8);
}
