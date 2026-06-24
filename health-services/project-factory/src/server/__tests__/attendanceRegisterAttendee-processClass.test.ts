/**
 * TDD tests for collectAttendeeOperation and collectStaffOperation
 * in attendanceRegisterAttendee-processClass.ts
 *
 * Tests the status assignment logic — specifically that existing attendees/staff
 * get CREATED status (with final data) instead of EXISTING.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────

const mockServerTimezone = { value: "Asia/Kolkata" };

jest.mock('../config', () => ({
    default: {
        host: {},
        paths: {},
        kafka: {},
        hrms: { hrmsParallelSearchLimit: 5 },
        attendanceRegister: { serviceCodeParallelSearchLimit: 5, batchSize: 50 },
        get appTimezone() { return mockServerTimezone.value; },
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

// registerData with no boundaries — ensures clamping has no effect
const REGISTER_DATA = {
    register: { startDate: null, endDate: null },
    attendeesMap: new Map(),
    staffMap: new Map(),
};

// registerData whose endDate is 0 — de-enrollment clamps to 0 and is treated as absent
const REGISTER_DATA_ZERO_END = {
    register: { startDate: null, endDate: 0 },
    attendeesMap: new Map(),
    staffMap: new Map(),
};

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
    row: any,
    registerData: any = REGISTER_DATA
): { attendeesToCreate: any[]; attendeesToDelete: any[]; attendeesToUpdateTag: any[] } {
    const attendeesToCreate: any[] = [];
    const attendeesToDelete: any[] = [];
    const attendeesToUpdateTag: any[] = [];
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
        attendeesToDelete,
        attendeesToUpdateTag,
        {},
        registerData
    );
    return { attendeesToCreate, attendeesToDelete, attendeesToUpdateTag };
}

function callCollectStaffOperation(
    existing: any,
    enrollmentDateEpoch: number | null,
    deEnrollmentDateEpoch: number | null,
    staffType: string,
    row: any,
    registerData: any = REGISTER_DATA
): { staffToCreate: any[]; staffToDelete: any[] } {
    const staffToCreate: any[] = [];
    const staffToDelete: any[] = [];
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
        staffToDelete,
        {},
        registerData
    );
    return { staffToCreate, staffToDelete };
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

    test("A3: New attendee with de-enrollment only → enrollment date is required, row marked INVALID", () => {
        const row = makeRow();
        const { attendeesToCreate } = callCollectAttendeeOperation(
            null, null, DEENROLLMENT_EPOCH, "", row
        );
        expect(attendeesToCreate).toHaveLength(0);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.INVALID);
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
        const { attendeesToCreate, attendeesToDelete } = callCollectAttendeeOperation(
            existing, null, null, "", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        // Dates are formatted as dd/MM/yyyy strings in the configured timezone
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe("string");
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe("string");
        expect(row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"]).toBe("T1");
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToDelete).toHaveLength(0);
    });

    test("A6: Existing de-enrolled, row has dates, different tag → tag update queued", () => {
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
        const { attendeesToCreate, attendeesToDelete, attendeesToUpdateTag } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "T1", row
        );
        // Tag update is queued; row status is set by caller after API call
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToDelete).toHaveLength(0);
        expect(attendeesToUpdateTag).toHaveLength(1);
        expect(attendeesToUpdateTag[0].payload.tag).toBe("T1");
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
        const { attendeesToCreate, attendeesToDelete, attendeesToUpdateTag } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, null, "T1", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToDelete).toHaveLength(0);
        expect(attendeesToUpdateTag).toHaveLength(0);
    });

    // ── Existing active attendees — enrollment date is immutable ─────────

    test("A8: Existing active, enrollment changed → INVALID (date immutability)", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: OLD_ENROLLMENT_EPOCH };
        const { attendeesToCreate, attendeesToDelete, attendeesToUpdateTag } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, null, "", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.INVALID);
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToDelete).toHaveLength(0);
        expect(attendeesToUpdateTag).toHaveLength(0);
    });

    test("A9: Existing active, de-enrollment added → pushed to delete list", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: ENROLLMENT_EPOCH };
        const { attendeesToDelete } = callCollectAttendeeOperation(
            existing, null, DEENROLLMENT_EPOCH, "", row
        );
        expect(attendeesToDelete).toHaveLength(1);
        expect(attendeesToDelete[0].payload.denrollmentDate).toBe(DEENROLLMENT_EPOCH);
    });

    test("A10: Existing active, team code changed → pushed to updateTag list", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: ENROLLMENT_EPOCH, tag: "T1" };
        const { attendeesToUpdateTag } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, null, "T2", row
        );
        expect(attendeesToUpdateTag).toHaveLength(1);
        expect(attendeesToUpdateTag[0].payload.tag).toBe("T2");
    });

    test("A11: Existing active, enrollment + de-enrollment + tag changed → INVALID (enrollment date immutable)", () => {
        const row = makeRow();
        const existing = { id: "att-1", enrollmentDate: OLD_ENROLLMENT_EPOCH, tag: "T1" };
        const { attendeesToCreate, attendeesToDelete, attendeesToUpdateTag } = callCollectAttendeeOperation(
            existing, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "T2", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.INVALID);
        expect(attendeesToCreate).toHaveLength(0);
        expect(attendeesToDelete).toHaveLength(0);
        expect(attendeesToUpdateTag).toHaveLength(0);
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
            staffType: "OWNER",
            tenantId: "tenant1",
            denrollmentDate: DEENROLLMENT_EPOCH,
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
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            existing, null, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        // Dates are formatted as dd/MM/yyyy strings in the configured timezone
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe("string");
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe("string");
        expect(staffToCreate).toHaveLength(0);
        expect(staffToDelete).toHaveLength(0);
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
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe("string");
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe("string");
        expect(staffToCreate).toHaveLength(0);
        expect(staffToDelete).toHaveLength(0);
    });

    // ── Existing active staff, no changes ────────────────────────────────

    test("B6: Existing active staff, no changes → CREATED", () => {
        const row = makeRow();
        const existing = {
            id: "staff-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            staffType: "OWNER",
        };
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToDelete).toHaveLength(0);
    });

    // ── Existing active staff — enrollment date is immutable ─────────────

    test("B7: Existing active staff, enrollment changed → INVALID (date immutability)", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: OLD_ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.INVALID);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToDelete).toHaveLength(0);
    });

    test("B8: Existing active staff, de-enrollment added → pushed to delete list", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToDelete } = callCollectStaffOperation(
            existing, null, DEENROLLMENT_EPOCH, "OWNER", row
        );
        expect(staffToDelete).toHaveLength(1);
        expect(staffToDelete[0].payload).toMatchObject({
            registerId: "register-uuid-1",
            userId: "individual-1",
            tenantId: "tenant1",
            denrollmentDate: DEENROLLMENT_EPOCH,
        });
    });

    test("B9: Existing active staff, staffType changed → CREATED (staffType not tracked for active staff)", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            existing, ENROLLMENT_EPOCH, null, "APPROVER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(staffToCreate).toHaveLength(0);
        expect(staffToDelete).toHaveLength(0);
    });

    test("B10: Existing de-enrolled staff, missing staffType on existing → CREATED with data", () => {
        const row = makeRow();
        const existing = {
            id: "staff-1",
            enrollmentDate: ENROLLMENT_EPOCH,
            denrollmentDate: DEENROLLMENT_EPOCH,
            // staffType intentionally missing
        };
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            existing, null, null, "OWNER", row
        );
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe("string");
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe("string");
        expect(staffToCreate).toHaveLength(0);
        expect(staffToDelete).toHaveLength(0);
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

    test("C3: Existing attendee de-enrolled, enrollmentDate is epoch 0 → date formatted as string", () => {
        const row = makeRow();
        const existing = {
            id: "att-1",
            enrollmentDate: 0,
            denrollmentDate: DEENROLLMENT_EPOCH,
            tag: "T1",
        };
        callCollectAttendeeOperation(existing, null, null, "", row);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
        // epoch 0 (1970-01-01) is valid and should be formatted as a date string
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"]).toBe("string");
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe("string");
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
        expect(typeof row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"]).toBe("string");
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// CZ. Zero-clamp de-enrollment → treated as absent (register endDate = 0)
// ═══════════════════════════════════════════════════════════════════════════

describe("Zero-clamp de-enrollment is skipped", () => {

    test("CZ1: New attendee E+D, register endDate 0 → create has no denrollmentDate, no delete", () => {
        const row = makeRow();
        const { attendeesToCreate, attendeesToDelete } = callCollectAttendeeOperation(
            null, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "T1", row, REGISTER_DATA_ZERO_END
        );
        expect(attendeesToCreate).toHaveLength(1);
        expect(attendeesToCreate[0].payload.enrollmentDate).toBe(ENROLLMENT_EPOCH);
        expect(attendeesToCreate[0].payload.denrollmentDate).toBeUndefined();
        expect(attendeesToDelete).toHaveLength(0);
    });

    test("CZ2: New staff E+D, register endDate 0 → create has no denrollmentDate", () => {
        const row = makeRow();
        const { staffToCreate, staffToDelete } = callCollectStaffOperation(
            null, ENROLLMENT_EPOCH, DEENROLLMENT_EPOCH, "OWNER", row, REGISTER_DATA_ZERO_END
        );
        expect(staffToCreate).toHaveLength(1);
        expect(staffToCreate[0].payload.enrollmentDate).toBe(ENROLLMENT_EPOCH);
        expect(staffToCreate[0].payload.denrollmentDate).toBeUndefined();
        expect(staffToDelete).toHaveLength(0);
    });

    test("CZ3: Existing active staff, D clamps to 0 → no de-enroll, row CREATED", () => {
        const row = makeRow();
        const existing = { id: "staff-1", enrollmentDate: ENROLLMENT_EPOCH, staffType: "OWNER" };
        const { staffToDelete } = callCollectStaffOperation(
            existing, null, DEENROLLMENT_EPOCH, "OWNER", row, REGISTER_DATA_ZERO_END
        );
        expect(staffToDelete).toHaveLength(0);
        expect(row["#status#"]).toBe(sheetDataRowStatuses.CREATED);
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// D. parseDate — unit tests for each input type
// ═══════════════════════════════════════════════════════════════════════════

describe("parseDate", () => {
    function parseDate(value: any): number | null {
        return (TemplateClass as any).parseDate(value);
    }
    function epochToDatePartsInTz(epochMs: number): { year: number; month: number; day: number } {
        return (TemplateClass as any).epochToDatePartsInTz(epochMs);
    }

    beforeEach(() => {
        (TemplateClass as any).tzFormatter = null;
        mockServerTimezone.value = "Asia/Kolkata";
    });

    test("D1: Date object → non-null epoch for April 5", () => {
        const result = parseDate(new Date(2026, 3, 5));
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("D2: ISO string → epoch for April 5", () => {
        const result = parseDate("2026-04-05T00:00:00.000Z");
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("D3: Excel serial 46117 (April 5, 2026) → correct epoch", () => {
        const result = parseDate(46117);
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("D4: dd/MM/yyyy string → correct epoch", () => {
        const result = parseDate("05/04/2026");
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("D5: large epoch ms → returned as-is", () => {
        expect(parseDate(1775347200000)).toBe(1775347200000);
    });

    test("D6: invalid string → null", () => {
        expect(parseDate("not-a-date")).toBeNull();
    });

    test("D7: NaN Date → null", () => {
        expect(parseDate(new Date("invalid"))).toBeNull();
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// E. parseDateEndOfDay — end-of-day variant
// ═══════════════════════════════════════════════════════════════════════════

describe("parseDateEndOfDay", () => {
    function parseDateEndOfDay(value: any): number | null {
        return (TemplateClass as any).parseDateEndOfDay(value);
    }
    function epochToDatePartsInTz(epochMs: number): { year: number; month: number; day: number } {
        return (TemplateClass as any).epochToDatePartsInTz(epochMs);
    }
    function parseDate(value: any): number | null {
        return (TemplateClass as any).parseDate(value);
    }

    beforeEach(() => {
        (TemplateClass as any).tzFormatter = null;
        mockServerTimezone.value = "Asia/Kolkata";
    });

    test("E1: Date object → end-of-day epoch (later than midnight)", () => {
        const midnight = parseDate(new Date(2026, 3, 5))!;
        const endOfDay = parseDateEndOfDay(new Date(2026, 3, 5))!;
        expect(endOfDay).toBeGreaterThan(midnight);
        const parts = epochToDatePartsInTz(endOfDay);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("E2: Excel serial 46117 → end-of-day for April 5", () => {
        const midnight = parseDate(46117)!;
        const endOfDay = parseDateEndOfDay(46117)!;
        expect(endOfDay).toBeGreaterThan(midnight);
        const parts = epochToDatePartsInTz(endOfDay);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("E3: ISO string → end-of-day for April 5", () => {
        const midnight = parseDate("2026-04-05T00:00:00.000Z")!;
        const endOfDay = parseDateEndOfDay("2026-04-05T00:00:00.000Z")!;
        expect(endOfDay).toBeGreaterThan(midnight);
    });

    test("E4: large epoch ms → returned as-is", () => {
        expect(parseDateEndOfDay(1775347200000)).toBe(1775347200000);
    });

    test("E5: invalid string → null", () => {
        expect(parseDateEndOfDay("not-a-date")).toBeNull();
    });
});
