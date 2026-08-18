const mockServerTimezone = { value: "UTC" };

jest.mock('../config', () => ({
    default: {
        get appTimezone() { return mockServerTimezone.value; },
    },
    __esModule: true,
}));

import { attendeeIdentity, formatEpochAsSheetDate } from "../utils/attendanceIdentityUtils";

describe("attendeeIdentity", () => {
    it("joins register uuid, individual id and sheet type", () => {
        expect(attendeeIdentity("reg-1", "ind-1", "worker")).toBe("reg-1_ind-1_worker");
    });
});

describe("formatEpochAsSheetDate", () => {
    afterEach(() => { mockServerTimezone.value = "UTC"; });

    it("formats an epoch as dd-MM-yyyy", () => {
        // 13-08-2026 00:00 UTC
        expect(formatEpochAsSheetDate(1786579200000)).toBe("13-08-2026");
    });

    it("pads single-digit days and months", () => {
        // 05-01-2026 00:00 UTC
        expect(formatEpochAsSheetDate(Date.UTC(2026, 0, 5))).toBe("05-01-2026");
    });

    it("resolves the calendar day in the configured timezone, not the pod's", () => {
        mockServerTimezone.value = "Pacific/Kiritimati"; // UTC+14
        // 23:00 UTC on 12-08 is already 13-08 there
        expect(formatEpochAsSheetDate(Date.UTC(2026, 7, 12, 23, 0))).toBe("13-08-2026");
    });

    it("keeps an end-of-day epoch on its own day", () => {
        expect(formatEpochAsSheetDate(Date.UTC(2026, 7, 20, 23, 59, 59, 999))).toBe("20-08-2026");
    });
});
