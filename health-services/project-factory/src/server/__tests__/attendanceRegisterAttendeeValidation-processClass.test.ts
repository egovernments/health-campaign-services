/**
 * Tests for parseDate, date range validation, and midnightEpochInTz
 * in attendanceRegisterAttendeeValidation-processClass.ts
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────

const mockServerTimezone = { value: "Asia/Kolkata" };

jest.mock('../config', () => ({
    default: {
        host: {},
        paths: {},
        kafka: {},
        hrms: { hrmsParallelSearchLimit: 5 },
        attendanceRegister: { attendeeSearchPageSize: 100, staffSearchPageSize: 100 },
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

import { TemplateClass } from "../processFlowClasses/attendanceRegisterAttendeeValidation-processClass";

// ─── Helpers ────────────────────────────────────────────────────────────────

function parseDate(value: any): number | null {
    return (TemplateClass as any).parseDate(value);
}

function midnightEpochInTz(year: number, month: number, day: number): number {
    return (TemplateClass as any).midnightEpochInTz(year, month, day);
}

function epochToDatePartsInTz(epochMs: number): { year: number; month: number; day: number } {
    return (TemplateClass as any).epochToDatePartsInTz(epochMs);
}

function compareDateParts(
    a: { year: number; month: number; day: number },
    b: { year: number; month: number; day: number }
): number {
    return (TemplateClass as any).compareDateParts(a, b);
}

function isInRange(parsedEpoch: number, startEpoch: number, endEpoch: number): boolean {
    const parsedParts = epochToDatePartsInTz(parsedEpoch);
    const startParts = epochToDatePartsInTz(startEpoch);
    const endParts = epochToDatePartsInTz(endEpoch);
    return compareDateParts(parsedParts, startParts) >= 0
        && compareDateParts(parsedParts, endParts) <= 0;
}

// Excel serial for a given UTC date
function excelSerial(year: number, month: number, day: number): number {
    const EXCEL_EPOCH_MS = Date.UTC(1899, 11, 30);
    return Math.round((Date.UTC(year, month, day) - EXCEL_EPOCH_MS) / 86_400_000);
}

// ═══════════════════════════════════════════════════════════════════════════
// A. parseDate — unit tests for each input type
// ═══════════════════════════════════════════════════════════════════════════

describe("parseDate", () => {
    beforeEach(() => {
        (TemplateClass as any).tzFormatter = null;
        mockServerTimezone.value = "Asia/Kolkata";
    });

    test("A1: Date object → non-null epoch for April 5", () => {
        const result = parseDate(new Date(2026, 3, 5));
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("A2: ISO string (UTC midnight) → epoch for April 5 in IST", () => {
        const result = parseDate("2026-04-05T00:00:00.000Z");
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("A3: ISO string (midnight IST = 18:30 UTC prev day) → April 5 in IST", () => {
        // 2026-04-04T18:30:00Z = 2026-04-05T00:00:00 IST
        const result = parseDate("2026-04-04T18:30:00.000Z");
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("A4: Excel serial 46117 (April 5, 2026) → correct epoch", () => {
        const serial = excelSerial(2026, 3, 5);
        expect(serial).toBe(46117);
        const result = parseDate(serial);
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("A5: dd-MM-yyyy string → correct epoch", () => {
        const result = parseDate("05-04-2026");
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("A6: dd/MM/yyyy string → correct epoch", () => {
        const result = parseDate("05/04/2026");
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 2026, month: 4, day: 5 });
    });

    test("A7: invalid string → null", () => {
        expect(parseDate("not-a-date")).toBeNull();
    });

    test("A8: large epoch ms → returned as-is", () => {
        const epoch = 1775347200000;
        expect(parseDate(epoch)).toBe(epoch);
    });

    test("A9: NaN Date → null", () => {
        expect(parseDate(new Date("invalid"))).toBeNull();
    });

    test("A10: Excel serial 1 (Dec 31, 1899) → non-null", () => {
        // Excel serial 1 = Dec 30 1899 + 1 day = Dec 31 1899
        // (Excel's 1900 leap year bug means serial 1 is actually Dec 31, 1899 in UTC)
        const result = parseDate(1);
        expect(result).not.toBeNull();
        const parts = epochToDatePartsInTz(result!);
        expect(parts).toEqual({ year: 1899, month: 12, day: 31 });
    });

    test("A11: invalid dd-MM-yyyy (Feb 31) → null", () => {
        expect(parseDate("31-02-2026")).toBeNull();
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// B. Date range validation — integration tests with real register epochs
// ═══════════════════════════════════════════════════════════════════════════

describe("date range validation", () => {
    // Register: April 1 00:00:00 IST → April 16 23:59:59 IST
    const registerStartDate = 1774981800000;
    const registerEndDate = 1776364199000;

    beforeEach(() => {
        (TemplateClass as any).tzFormatter = null;
        mockServerTimezone.value = "Asia/Kolkata";
    });

    test("B1: April 5 as Excel serial → IN RANGE (bug fix verification)", () => {
        const serial = excelSerial(2026, 3, 5); // 46117
        const parsed = parseDate(serial)!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });

    test("B2: April 5 as ISO string → IN RANGE", () => {
        const parsed = parseDate("2026-04-05T00:00:00.000Z")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });

    test("B3: April 5 as dd/MM/yyyy → IN RANGE", () => {
        const parsed = parseDate("05/04/2026")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });

    test("B4: April 5 as Date object → IN RANGE", () => {
        const parsed = parseDate(new Date(2026, 3, 5))!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });

    test("B5: April 1 (start boundary) → IN RANGE", () => {
        const parsed = parseDate("01/04/2026")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });

    test("B6: April 16 (end boundary) → IN RANGE", () => {
        const parsed = parseDate("16/04/2026")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });

    test("B7: April 17 (day after end) → OUT OF RANGE", () => {
        const parsed = parseDate("17/04/2026")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(false);
    });

    test("B8: March 30 (before start) → OUT OF RANGE", () => {
        const parsed = parseDate("30/03/2026")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(false);
    });

    test("B9: March 31 in UTC TZ (register startDate = March 31 in UTC) → IN RANGE", () => {
        (TemplateClass as any).tzFormatter = null;
        mockServerTimezone.value = "UTC";
        // In UTC, register startDate epoch maps to March 31 18:30 → March 31
        const parsed = parseDate("31/03/2026")!;
        expect(isInRange(parsed, registerStartDate, registerEndDate)).toBe(true);
    });
});

// ═══════════════════════════════════════════════════════════════════════════
// C. midnightEpochInTz — verify correct midnight calculation
// ═══════════════════════════════════════════════════════════════════════════

describe("midnightEpochInTz", () => {
    beforeEach(() => {
        (TemplateClass as any).tzFormatter = null;
    });

    test("C1: UTC → midnight UTC (same as Date.UTC)", () => {
        mockServerTimezone.value = "UTC";
        const result = midnightEpochInTz(2026, 3, 5);
        expect(result).toBe(Date.UTC(2026, 3, 5));
    });

    test("C2: IST (UTC+5:30) → midnight IST = April 4 18:30 UTC", () => {
        mockServerTimezone.value = "Asia/Kolkata";
        const result = midnightEpochInTz(2026, 3, 5);
        expect(result).toBe(Date.UTC(2026, 3, 4, 18, 30));
    });

    test("C3: WAT (UTC+1) → midnight WAT = April 4 23:00 UTC", () => {
        mockServerTimezone.value = "Africa/Lagos";
        const result = midnightEpochInTz(2026, 3, 5);
        expect(result).toBe(Date.UTC(2026, 3, 4, 23, 0));
    });
});

// Fix #5 (PR #2018): username dedup must be O(n) via a Set (no O(n^2) includes) and preserve order.
describe("collectUniqueUsernames", () => {
    function collect(rows: Array<Record<string, any>>): string[] {
        const allRows = rows.map(row => ({ row }));
        return (TemplateClass as any).collectUniqueUsernames(allRows);
    }

    it("dedupes while preserving first-seen order", () => {
        const result = collect([
            { UserName: "alice" },
            { UserName: "bob" },
            { UserName: "alice" },
            { UserName: "carol" },
            { UserName: "bob" },
        ]);
        expect(result).toEqual(["alice", "bob", "carol"]);
    });

    it("ignores empty, null and undefined usernames", () => {
        const result = collect([
            { UserName: "alice" },
            { UserName: "" },
            { UserName: null },
            { UserName: undefined },
            {},
            { UserName: "bob" },
        ]);
        expect(result).toEqual(["alice", "bob"]);
    });

    it("trims values so padded duplicates collapse", () => {
        const result = collect([{ UserName: "  alice  " }, { UserName: "alice" }]);
        expect(result).toEqual(["alice"]);
    });

    it("returns an empty array for no rows", () => {
        expect(collect([])).toEqual([]);
    });

    it("handles 50k UNIQUE usernames correctly and stays O(n), not O(n^2)", () => {
        // All-distinct is the O(n^2) worst case: the dedup list grows to n, so the old
        // `usernames.includes(x)` scan would do ~n^2/2 comparisons (~2s for 50k here),
        // while the Set-based version stays ~tens of ms. We assert BOTH correctness (all
        // 50k kept) and a generous elapsed-time bound — a jest timeout alone can't catch a
        // synchronous slow loop, so we measure directly.
        const rows = Array.from({ length: 50_000 }, (_, i) => ({ UserName: `u${i}` }));
        const start = Date.now();
        const result = collect(rows);
        const elapsedMs = Date.now() - start;
        expect(result).toHaveLength(50_000);
        expect(elapsedMs).toBeLessThan(1500); // huge headroom for O(n); trips on a true O(n^2) regression
    });
});
