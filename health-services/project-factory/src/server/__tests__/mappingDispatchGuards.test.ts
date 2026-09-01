/**
 * Guards on what the reconciler is allowed to dispatch in a cycle.
 *
 * Both rules exist to make a half-applied worker move fail in the recoverable direction:
 *  - a row whose project does not exist yet is held back rather than dispatched into certain failure
 *  - a worker's de-map never travels in the same cycle as their own map, so they cannot end up with
 *    no area at all
 *
 * The filters are exercised directly here. They are pure set logic over the pending rows, so they can
 * be asserted without standing up Kafka, the database or the poller.
 */

const toBeMapped = "toBeMapped";
const toBeDeMapped = "toBeDeMapped";

interface Row {
    type: string;
    status: string;
    uniqueIdentifierForData?: string;
    boundaryCode?: string;
}

/** Mirrors the readiness hold-back applied in runMappingReconciler. */
function applyReadinessHoldBack(pending: Row[], resolvedBoundaries: Set<string> | null): Row[] {
    if (!resolvedBoundaries) return pending;
    return pending.filter(
        (row) =>
            row?.status !== toBeMapped ||
            !row?.boundaryCode ||
            resolvedBoundaries.has(row.boundaryCode)
    );
}

/**
 * Mirrors the map/de-map pairing guard applied in runMappingReconciler. `allPending` is the FULL
 * pending set, deliberately not the already-filtered dispatchable list: a map row removed by the
 * readiness hold-back must still block its own de-map, or the worker loses their area entirely.
 */
function applyPairingGuard(dispatchable: Row[], allPending: Row[] = dispatchable): Row[] {
    const pendingMapIdentifiers = new Set<string>(
        allPending
            .filter((row) => row?.status === toBeMapped && row?.uniqueIdentifierForData)
            .map((row) => `${row?.type ?? ""}#${row.uniqueIdentifierForData}`)
    );
    if (pendingMapIdentifiers.size === 0) return dispatchable;
    return dispatchable.filter(
        (row) =>
            row?.status !== toBeDeMapped ||
            !row?.uniqueIdentifierForData ||
            !pendingMapIdentifiers.has(`${row?.type ?? ""}#${row.uniqueIdentifierForData}`)
    );
}

describe("readiness hold-back", () => {
    it("dispatches everything when the precondition was satisfied", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "B1" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p2", boundaryCode: "B2" },
        ];
        expect(applyReadinessHoldBack(rows, null)).toHaveLength(2);
    });

    it("holds back a map row whose project is not created yet", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "READY" },
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p2", boundaryCode: "NOT_READY" },
        ];
        const out = applyReadinessHoldBack(rows, new Set(["READY"]));
        expect(out.map((r) => r.boundaryCode)).toEqual(["READY"]);
    });

    it("never holds back a de-map row — its project already exists", () => {
        const rows: Row[] = [
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "NOT_READY" },
        ];
        expect(applyReadinessHoldBack(rows, new Set())).toHaveLength(1);
    });

    it("keeps a row that carries no boundary code", () => {
        const rows: Row[] = [{ type: "resource", status: toBeMapped }];
        expect(applyReadinessHoldBack(rows, new Set())).toHaveLength(1);
    });
});

describe("map/de-map pairing guard", () => {
    it("defers a worker's de-map while their own map is still pending", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "NEW" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "OLD" },
        ];
        const out = applyPairingGuard(rows);
        expect(out).toHaveLength(1);
        expect(out[0].status).toBe(toBeMapped);
    });

    it("dispatches the de-map once no map is pending for that worker", () => {
        const rows: Row[] = [
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "OLD" },
        ];
        expect(applyPairingGuard(rows)).toHaveLength(1);
    });

    it("does not defer an unrelated worker's de-map", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "NEW" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p2", boundaryCode: "OLD" },
        ];
        const out = applyPairingGuard(rows);
        expect(out).toHaveLength(2);
    });

    it("scopes the pairing by type, so a facility does not block a user of the same identifier", () => {
        const rows: Row[] = [
            { type: "facility", status: toBeMapped, uniqueIdentifierForData: "same", boundaryCode: "NEW" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "same", boundaryCode: "OLD" },
        ];
        expect(applyPairingGuard(rows)).toHaveLength(2);
    });

    it("keeps a de-map row that has no identifier to pair on", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1" },
            { type: "resource", status: toBeDeMapped },
        ];
        expect(applyPairingGuard(rows)).toHaveLength(2);
    });

    it("defers every de-map for a worker who has several stale areas", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "NEW" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "OLD_A" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "OLD_B" },
        ];
        const out = applyPairingGuard(rows);
        expect(out).toHaveLength(1);
        expect(out[0].boundaryCode).toBe("NEW");
    });
});

describe("both guards together — the sideways move", () => {
    it("holds the whole move when the destination project is not ready, so nothing half-applies", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_B" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_A" },
        ];
        const afterReadiness = applyReadinessHoldBack(rows, new Set());
        const out = applyPairingGuard(afterReadiness, rows);
        expect(out).toHaveLength(0);
    });

    it("applies the add first and defers the remove when the destination is ready", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_B" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_A" },
        ];
        const afterReadiness = applyReadinessHoldBack(rows, new Set(["VILLAGE_B"]));
        const out = applyPairingGuard(afterReadiness, rows);
        expect(out).toEqual([
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_B" },
        ]);
    });
});
