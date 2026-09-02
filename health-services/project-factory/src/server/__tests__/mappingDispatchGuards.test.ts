/**
 * Guards on what the reconciler is allowed to dispatch in a cycle.
 *
 * Both rules exist to make a half-applied worker move fail in the recoverable direction:
 *  - a row whose project does not exist yet is held back rather than dispatched into certain failure
 *  - a worker's de-map never travels in the same cycle as their own map, so they cannot end up with
 *    no area at all
 *
 * The two filters are IMPORTED from the module the reconciler itself calls, never re-implemented here.
 * An earlier version of this file mirrored the logic, and a mirror cannot fail: the copy and the source
 * drifted, and the pairing guard shipped unscoped — deferring live facility re-assignments — with all
 * twelve tests green. They live in their own Kafka/DB-free module so importing them costs nothing.
 */

import { applyReadinessHoldBack, deferPairedUserDeMaps } from "../utils/mappingDispatchGuards";

const toBeMapped = "toBeMapped";
const toBeDeMapped = "toBeDeMapped";

interface Row {
    type: string;
    status: string;
    uniqueIdentifierForData?: string;
    boundaryCode?: string;
}

const applyPairingGuard = (dispatchable: Row[], allPending: Row[] = dispatchable): Row[] =>
    deferPairedUserDeMaps(dispatchable, allPending) as Row[];

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

/**
 * The guard is for the user area-change flow only. Facility and resource re-assignment are live,
 * unconditional, pre-existing flows: a withheld row stays pending, so the observation poll cannot reach
 * its target and burns a full reconcileStallTimeoutMs (5 min by default) before the next cycle. Deferring
 * them would be a five-minute regression on a flow this change set has no business touching.
 */
describe("REGRESSION: the pairing guard must never defer a non-user de-map", () => {
    it("dispatches a facility's de-map even while that same facility has a pending map", () => {
        const rows: Row[] = [
            { type: "facility", status: toBeMapped, uniqueIdentifierForData: "Health Post 1", boundaryCode: "VILLAGE_B" },
            { type: "facility", status: toBeDeMapped, uniqueIdentifierForData: "Health Post 1", boundaryCode: "VILLAGE_A" },
        ];
        expect(applyPairingGuard(rows)).toHaveLength(2);
    });

    it("dispatches a resource's de-map even while that same resource has a pending map", () => {
        const rows: Row[] = [
            { type: "resource", status: toBeMapped, uniqueIdentifierForData: "PVAR-1", boundaryCode: "VILLAGE_B" },
            { type: "resource", status: toBeDeMapped, uniqueIdentifierForData: "PVAR-1", boundaryCode: "VILLAGE_A" },
        ];
        expect(applyPairingGuard(rows)).toHaveLength(2);
    });

    it("defers only the user half of a mixed cycle, leaving facility rows untouched", () => {
        const rows: Row[] = [
            { type: "user", status: toBeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_B" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "p1", boundaryCode: "VILLAGE_A" },
            { type: "facility", status: toBeMapped, uniqueIdentifierForData: "Health Post 1", boundaryCode: "VILLAGE_B" },
            { type: "facility", status: toBeDeMapped, uniqueIdentifierForData: "Health Post 1", boundaryCode: "VILLAGE_A" },
        ];
        const out = applyPairingGuard(rows);
        expect(out).toHaveLength(3);
        expect(out.filter((r) => r.type === "facility")).toHaveLength(2);
        expect(out.filter((r) => r.type === "user" && r.status === toBeDeMapped)).toHaveLength(0);
    });

    it("a facility map does not block a user de-map that shares its identifier", () => {
        const rows: Row[] = [
            { type: "facility", status: toBeMapped, uniqueIdentifierForData: "same", boundaryCode: "NEW" },
            { type: "user", status: toBeDeMapped, uniqueIdentifierForData: "same", boundaryCode: "OLD" },
        ];
        expect(applyPairingGuard(rows)).toHaveLength(2);
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
