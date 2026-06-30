/**
 * Branded primitive types — see CLAUDE.md "Type Safety Rules".
 *
 * A branded type is a primitive intersected with a unique phantom tag so the compiler rejects
 * cross-assignment between values that share the same underlying primitive (e.g. a CampaignStatus
 * can never be passed where a MappingStatus is expected). The brand is erased at compile time, so
 * there is zero runtime cost.
 *
 * Grep this file before adding a new brand to avoid duplicates. Extend freely.
 */
export type Brand<T, B extends string> = T & { readonly __brand: B };

export type CampaignStatus = Brand<string, "CampaignStatus">;

export type MappingGeneration = Brand<number, "MappingGeneration">;