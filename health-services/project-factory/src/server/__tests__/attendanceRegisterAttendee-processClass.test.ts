/**
 * TDD tests for collectAttendeeOperation and collectStaffOperation
 * in attendanceRegisterAttendee-processClass.ts
 *
 * Tests the status assignment logic — specifically that existing attendees/staff
 * get CREATED status (with final data) instead of EXISTING.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────

jest.mock('../config', () => ({
    default: {
        host: {},
        paths: {},
        kafka: {},
        hrms: { hrmsParallelSearchLimit: 5 },
        attendanceRegister: { serviceCodeParallelSearchLimit: 5, batchSize: 50 },
    },
    __esModule: true,
}));

jest.mock('../utils/logger', () => ({
    logger: {
        info: jest.fn(),
        debug: jest.fn(),
        error: jest.fn(),
        warn: jest.fn(),
    },
}));

jest.mock('../utils/campaignUtils', () => ({
    getLocalizedName: jest.fn((key: string) => key),
}));

jest.mock('../utils/sheetManageUtils', () => ({
    validateResourceDetailsBeforeProcess: jest.fn(),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn().mockResolvedValue([]),
    throwError: jest.fn(),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

import { sheetDataRowStatuses } from "../config/constants";
import { TemplateClass } from "../processFlowClasses/attendanceRegisterAttendee-processClass";

const ENROLLMENT_EPOCH = 1700000000000;   // 2023-11-14
const DEENROLLMENT_EPOCH = 1700100000000; // ~2023-11-15
const OLD_ENROLLMENT_EPOCH = 1690000000000; // 2023-07-22

// ─── Helpers ────────────────────────────────────────────────────────────────

function makeRow(overrides: Record<string, any> = {}): any {
    return {
        UserName: "user1",
        HCM_ATTENDANCE_REGISTER_ID: "REG-001",
        HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: null,
        HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE: null,
        HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "",
        ...overrides,
    };
}

function callCollectAttendeeOperation(
    existing: any,
    enrollmentDateEpoch: number | null,
    deEnrollmentDateEpoch: number | null,
    teamCode: string,
    row: any
): { attendeesToCreate: any[]; attendeesToUpdate: any[] } {
    const attendeesToCreate: any[] = [];
    const attendeesToUpdate: any[] = [];
    (TemplateClass as any).collectAttendeeOperation(
        existing,
        enrollmentDateEpoch,
        deEnrollmentDateEpoch,
        teamCode,
        "tenant1",
        "register-uuid-1",
        "individual-1",
        row,
        attendeesToCreate,
        attendeesToUpdate
    );
    return { attendeesToCreate, attendeesToUpdate };
}

function callCollectStaffOperation(
    existing: any,
    enrollmentDateEpoch: number | null,
    deEnrollmentDateEpoch: number | null,
    staffType: string,
    row: any
): { staffToCreate: any[]; staffToUpdate: any[] } {
    const staffToCreate: any[] = [];
    const staffToUpdate: any[] = [];
    (TemplateClass as any).collectStaffOperation(
        existing,
        enrollmentDateEpoch,
        deEnrollmentDateEpoch,
        "tenant1",
        "register-uuid-1",
        "individual-1",
        staffType,
        row,
        staffToCreate,
        staffToUpdate
    );
    return { staffToCreate, staffToUpdate };
}

// ═══════════════════════════════════════════════════════════════════════════
// A. collectAttendeeOperation
// ═══════════════════════════════════════════════════════════════════════════

describe("collectAttendeeOperation", () => {

    // ── New attendees (existing = null) ──────────────────────────────────

    test("A1: New attendee with both dates → pushed to create list", () => {
        const row = makeRow();
        const { attendeesToCreate } = callCollectAttendeeOperation(
            null, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "T1", row
        );
        expect(attendeesToCreate).toHaveLength(1);
        expect(attendeesToCreate[0].payload).toMatchObject({
            registerId: "register-uuid-1",
            individualId: "individual-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            tag: "T1",
            tenantId: "tenant1",
        });
        expect(row["#status#"]).toBeUndefined();
    });

    test("A2: New attendee with enrollment only → pushed to create list", () => {
        const row = makeRow();
        const { attendeesToCreate } = callCollectAttendeeOperation(
            null, ENROLLMENT_EPOCH, null, "T1", row
        );
        expect(attendeesToCreate).toHaveLength(1);
        expect(attendeesToCreate[0].payload.enrollmentDate).toBe(ENROLLMENT_EPOCH);
        expect(attendeesToCreate[0].payload.denrollmentDate).toBeUndefined();
        expect(attendeesToCreate[0].payload.tag).toBe("T1");
    });

    test("A3: New attendee with de-enrollment only → enrollmentDate falls back to deEnrollmentDate", () => {
        const row = makeRow();
        const { attendeesToCreate } = callCollectAttendeeOperation(
            null, null, DEENROLLMENT_EPOCH, "", row
        );
        expect(attendeesToCreate).toHaveLength(1);
        expect(attendeesToCreate[0].payload.enrollmentDate).toBe(DEENROLLMENT_EPOCH);
        expect(attendeesToCreate[0].payload.denrollmentDate).toBe(DEENROLLMENT_EPOCH);
        expect(attendeesToCreate[0].payload.tag).toBeUndefined(); // empty string → no tag
    });

    test("A4: New attendee with no dates → SKIPPED", () => {
        const row = makeRow();
        callCollectAttendeeOperation(null, null, null, "", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.SKIPPED);
    });

    // ── Existing de-enrolled attendees ───────────────────────────────────

    test("A5: Existing de-enrolled, row has no dates → CREATED with existing data populated", () => {
        const row = makeRow();
        const existing = {
            id: "att-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            tag: "T1",
        };
        const { attendeesToCreate, attendeesToUpdate } = callCollectAttendeeOperation(
            existing, null, null, "", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe(ENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"]).toBe("T1");
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToUpdate).toHaveLength(0);
    });

    test("A6: Existing de-enrolled, row has dates → CREATED with existing data overwritten", () => {
        const row = makeRow({
            HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: "14-11-2023",
            HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE: "15-11-2023",
            HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "T1",
        });
        const existing = {
            id: "att-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            tag: "T2",
        };
        const { attendeesToCreate, attendeesToUpdate } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "T1", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe(ENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"]).toBe("T2"); // from existing.tag
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToUpdate).toHaveLength(0);
    });

    // ── Existing active attendees, no changes ────────────────────────────

    test("A7: Existing active, no changes → CREATED (row data already matches)", () => {
        const row = makeRow({
            HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: ENROLLMENT_EPOCH,
            HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "T1",
        });
        const existing = {
            id: "att-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            tag: "T1",
        };
        const { attendeesToCreate, attendeesToUpdate } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, null, "T1", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToUpdate).toHaveLength(0);
    });

    // ── Existing active attendees, with changes → UPDATE (unchanged) ─────

    test("A8: Existing active, enrollment changed → pushed to update list", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: OLD_ENROLLMENT_EPOCH };
        const { attendeesToUpdate } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, null, "", row
        );
        expect(attendeesToUpdate).toHaveLength(1);
        expect(attendeesToUpdate[0].payload.enrollmentDate).toBe(ENROLLMENT_EPOCH);
        expect(row["#status#"]).toBeUndefined(); // status set later after API call
    });

    test("A9: Existing active, de-enrollment added → pushed to update list", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: ENROLLMENT_EPOCH };
        const { attendeesToUpdate } = callCollectAttendeeOperation(
            existing, null, DEENROLLMENT_EPOCH, "", row
        );
        expect(attendeesToUpdate).toHaveLength(1);
        expect(attendeesToUpdate[0].payload.denrollmentDate).toBe(DEENROLLMENT_EPOCH);
    });

    test("A10: Existing active, team code changed → pushed to update list", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: ENROLLMENT_EPOCH, tag: "T1" };
        const { attendeesToUpdate } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, null, "T2", row
        );
        expect(attendeesToUpdate).toHaveLength(1);
        expect(attendeesToUpdate[0].payload.tag).toBe("T2");
    });

    test("A11: Existing active, multiple fields changed → single update payload", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: OLD_ENROLLMENT_EPOCH, tag: "T1" };
        const { attendeesToUpdate } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "T2", row
        );
        expect(attendeesToUpdate).toHaveLength(1);
        const payload = attendeesToUpdate[0].payload;
        expect(payload.enrollmentDate).toBe(ENROLLMENT_EPOCH);
        expect(payload.denrollmentDate).toBe(DEENROLLMENT_EPOCH);
        expect(payload.tag).toBe("T2");
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// B. collectStaffOperation
// ═══════════════════════════════════════════════════════════════════════════

describe("collectStaffOperation", () => {

    // ── New staff ────────────────────────────────────────────────────────

    test("B1: New staff with both dates → pushed to create list", () => {
        const row = makeRow();
        const { staffToCreate } = callCollectStaffOperation(
            null, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "OWNER", row
        );
        expect(staffToCreate).toHaveLength(1);
        expect(staffToCreate[0].payload).toMatchObject({
            registerId: "register-uuid-1",
            userId: "individual-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            staffType: "OWNER",
            tenantId: "tenant1",
        });
    });

    test("B2: New staff with enrollment only → pushed to create list", () => {
        const row = makeRow();
        const { staffToCreate } = callCollectStaffOperation(
            null, ENROLLMENT_EPOCH, null, "APPROVER", row
        );
        expect(staffToCreate).toHaveLength(1);
        expect(staffToCreate[0].payload.staffType).toBe("APPROVER");
        expect(staffToCreate[0].payload.denrollmentDate).toBeUndefined();
    });

    test("B3: New staff with no dates → SKIPPED", () => {
        const row = makeRow();
        callCollectStaffOperation(null, null, null, "OWNER", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.SKIPPED);
    });

    // ── Existing de-enrolled staff ───────────────────────────────────────

    test("B4: Existing de-enrolled staff, row has no dates → CREATED with existing data", () => {
        const row = makeRow();
        const existing = {
            id: "staff-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            staffType: "OWNER",
        };
        const { staffToCreate, staffToUpdate } = callCollectStaffOperation(
            existing, null, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe(ENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToUpdate).toHaveLength(0);
    });

    test("B5: Existing de-enrolled staff, row has dates → CREATED with existing data overwritten", () => {
        const row = makeRow({
            HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: "14-11-2023",
            HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE: "15-11-2023",
        });
        const existing = {
            id: "staff-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            staffType: "OWNER",
        };
        const { staffToCreate, staffToUpdate } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe(ENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToUpdate).toHaveLength(0);
    });

    // ── Existing active staff, no changes ────────────────────────────────

    test("B6: Existing active staff, no changes → CREATED", () => {
        const row = makeRow();
        const existing = {
            id: "staff-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            staffType: "OWNER",
        };
        const { staffToCreate, staffToUpdate } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToUpdate).toHaveLength(0);
    });

    // ── Existing active staff, with changes → UPDATE ─────────────────────

    test("B7: Existing active staff, enrollment changed → pushed to update list", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: OLD_ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToUpdate } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, null, "OWNER", row
        );
        expect(staffToUpdate).toHaveLength(1);
        expect(staffToUpdate[0].payload.enrollmentDate).toBe(ENROLLMENT_EPOCH);
    });

    test("B8: Existing active staff, de-enrollment added → pushed to update list", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToUpdate } = callCollectStaffOperation(
            existing, null, DEENROLLMENT_EPOCH, "OWNER", row
        );
        expect(staffToUpdate).toHaveLength(1);
        expect(staffToUpdate[0].payload.denrollmentDate).toBe(DEENROLLMENT_EPOCH);
    });

    test("B9: Existing active staff, staffType changed → pushed to update list", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToUpdate } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, null, "APPROVER", row
        );
        expect(staffToUpdate).toHaveLength(1);
        expect(staffToUpdate[0].payload.staffType).toBe("APPROVER");
    });

    test("B10: Existing de-enrolled staff, missing staffType on existing → CREATED with data", () => {
        const row = makeRow();
        const existing = {
            id: "staff-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            // staffType intentionally missing
        };
        const { staffToCreate, staffToUpdate } = callCollectStaffOperation(
            existing, null, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe(ENROLLMENT_EPOCH);
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToUpdate).toHaveLength(0);
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// C. Edge cases — data integrity
// ═══════════════════════════════════════════════════════════════════════════

describe("Edge cases — data integrity", () => {

    test("C1: Existing attendee de-enrolled, tag is empty string → team code column not populated", () => {
        const row = makeRow();
        const existing = {
            id: "att-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            tag: "",
        };
        callCollectAttendeeOperation(existing, null, null, "", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"]).toBe(""); // unchanged from makeRow default
    });

    test("C2: Existing attendee de-enrolled, tag is undefined → team code column not populated", () => {
        const row = makeRow();
        const existing = {
            id: "att-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            // tag intentionally undefined
        };
        callCollectAttendeeOperation(existing, null, null, "", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"]).toBe(""); // unchanged
    });

    test("C3: Existing attendee de-enrolled, enrollmentDate is epoch 0 → still populated", () => {
        const row = makeRow();
        const existing = {
            id: "att-1",
            enrollmentDate: 0,
            denrollmentDate: DEENROLLMENT_EPOCH,
            tag: "T1",
        };
        callCollectAttendeeOperation(existing, null, null, "", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        // enrollmentDate=0 is falsy but valid epoch — should still be populated
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe(0);
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
    });

    test("C4: Existing staff de-enrolled, enrollmentDate missing but denrollmentDate exists → only deenrollment populated", () => {
        const row = makeRow();
        const existing = {
            id: "staff-1",
            // enrollmentDate intentionally missing
            denrollmentDate: DEENROLLMENT_EPOCH,
            staffType: "OWNER",
        };
        callCollectStaffOperation(existing, null, null, "OWNER", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBeNull(); // unchanged from makeRow
        expect(row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe(DEENROLLMENT_EPOCH);
    });
});
