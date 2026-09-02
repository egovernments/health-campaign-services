import { mappingStatuses } from '../config/constants';

/**
 * Guards on what the reconciler is allowed to dispatch in one cycle. Kept in their own module, free of
 * Kafka/DB/HTTP imports, so the tests can exercise THIS code rather than a copy of it — an earlier
 * version of the tests mirrored the logic, and a mirror cannot fail: the copy and the source drifted and
 * the pairing guard shipped unscoped with every test green.
 */

/** Mapping-row `type` for user↔boundary rows. Matches the literal written by user-processClass. */
const USER_MAPPING_TYPE = 'user';

/**
 * On a readiness stall, hold back the map-direction rows whose project does not exist yet rather than
 * dispatching them: dispatching one is a guaranteed failure that burns one of only a few retries on a
 * pure timing race, whereas a row left pending is picked up next cycle. De-map rows and rows carrying no
 * boundary are unaffected. `null` means the readiness poll was satisfied — dispatch everything.
 */
export function applyReadinessHoldBack(pendingMappings: any[], resolvedBoundaries: Set<string> | null): any[] {
    if (!resolvedBoundaries) return pendingMappings;
    return pendingMappings.filter((row: any) =>
        row?.status !== mappingStatuses.toBeMapped
        || !row?.boundaryCode
        || resolvedBoundaries.has(row.boundaryCode));
}

/**
 * Pair a worker's move so it can never half-apply in the losing direction: an area change is one
 * toBeMapped plus one toBeDeMapped row for the SAME phone, dispatched into concurrently-consumed
 * batches — an applied de-map with a failed map leaves the worker with NO area. Deferring the de-map
 * while that phone still has a pending map inverts the worst case to "holds BOTH areas": visible and
 * repairable instead of silent.
 *
 * `allPending` is the FULL pending set, never `dispatchable` — a map row held back by readiness must
 * still block its own de-map.
 *
 * SCOPED TO `user` DELIBERATELY — do not widen. facility-processClass emits the identical row shape
 * unconditionally, and an unscoped guard defers every facility re-assignment by a full
 * `reconcileStallTimeoutMs` (default 5 min). The facility half-apply race is left as it was.
 */
export function deferPairedUserDeMaps(dispatchable: any[], allPending: any[] = dispatchable): any[] {
    const pendingUserMapIdentifiers = new Set<string>(
        allPending
            .filter((row: any) => row?.status === mappingStatuses.toBeMapped
                && row?.type === USER_MAPPING_TYPE
                && row?.uniqueIdentifierForData)
            .map((row: any) => String(row.uniqueIdentifierForData))
    );
    if (pendingUserMapIdentifiers.size === 0) return dispatchable;
    return dispatchable.filter((row: any) =>
        row?.status !== mappingStatuses.toBeDeMapped
        || row?.type !== USER_MAPPING_TYPE
        || !row?.uniqueIdentifierForData
        || !pendingUserMapIdentifiers.has(String(row.uniqueIdentifierForData)));
}
