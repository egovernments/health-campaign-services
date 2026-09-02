import { workerNeedsUpdate } from "../utils/workerRegistryUtils";
import { reconcileAttendanceEnrolments, reconcileAttendanceEnrolmentsForPhones } from "../utils/attendanceEnrolmentUtils";
import { httpRequest } from "../utils/request";

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock("../utils/request", () => ({ httpRequest: jest.fn() }));
jest.mock("../config", () => ({
    __esModule: true,
    default: {
        host: { attendanceHost: "http://attendance/", healthIndividualHost: "http://individual/" },
        paths: {
            attendanceRegisterSearch: "health-attendance/v1/_search",
            attendanceAttendeeCreate: "health-attendance/attendee/v1/_create",
            attendanceAttendeeDelete: "health-attendance/attendee/v1/_delete",
            attendanceAttendeeSearch: "health-attendance/attendee/v1/_search",
            healthIndividualSearch: "health-individual/v1/_search",
        },
        attendanceRegister: { batchSize: 50, attendeeSearchPageSize: 100, enrolmentScanMaxPages: 3 },
        user: { individualSearchBatchSize: 50 },
    },
}));

const mockHttp = jest.mocked(httpRequest);

const base = {
    name: "Asha",
    payeePhoneNumber: "9990001111",
    paymentProvider: "BANK",
    payeeName: "Asha",
    bankAccount: "123456",
    bankCode: "BC1",
    beneficiaryCode: "BEN1",
    individualId: "IND-1",
    tenantId: "dev",
    id: "W-1",
} as any;

const stored = {
    id: "W-1",
    individualIds: ["IND-1"],
    name: "Asha",
    payeePhoneNumber: "9990001111",
    paymentProvider: "BANK",
    payeeName: "Asha",
    bankAccount: "123456",
    bankCode: "BC1",
    beneficiaryCode: "BEN1",
    rowVersion: 1,
    tenantId: "dev",
} as any;

describe("workerNeedsUpdate", () => {
    it("returns false when nothing the sheet controls has changed", () => {
        expect(workerNeedsUpdate(base, stored)).toBe(false);
    });

    it("returns true when the name changed", () => {
        expect(workerNeedsUpdate({ ...base, name: "Asha Devi" }, stored)).toBe(true);
    });

    it("returns true when a payment field changed", () => {
        expect(workerNeedsUpdate({ ...base, bankAccount: "999999" }, stored)).toBe(true);
    });

    it("returns true when there is no stored record yet", () => {
        expect(workerNeedsUpdate(base, undefined)).toBe(true);
    });

    it("treats empty string, null and undefined as equal so an area-only edit is not a write", () => {
        const incoming = { ...base, beneficiaryCode: "" };
        const existing = { ...stored, beneficiaryCode: undefined };
        expect(workerNeedsUpdate(incoming, existing)).toBe(false);
    });

    it("ignores surrounding whitespace rather than treating it as a change", () => {
        expect(workerNeedsUpdate({ ...base, bankCode: "  BC1  " }, stored)).toBe(false);
    });
});

describe("reconcileAttendanceEnrolments", () => {
    const CN = "CMP-1";
    const OLD = { id: "REG-OLD", localityCode: "VILLAGE_A", registerNumber: "R/OLD", campaignNumber: CN } as any;
    const NEW = { id: "REG-NEW", localityCode: "VILLAGE_B", registerNumber: "R/NEW", campaignNumber: CN } as any;
    const ATT = { id: "ATT-1", individualId: "IND-1", registerId: "REG-OLD", enrollmentDate: 1, denrollmentDate: null };

    const moves = [{ individualId: "IND-1", targetLocalityCode: "VILLAGE_B" }] as any;
    const call = () => reconcileAttendanceEnrolments(moves, CN as any, "dev" as any, {});
    /** call order: 1 register search, 2 attendee scan page, then create, then delete */
    const seq = (registers: any[], attendees: any[]) => {
        mockHttp.mockReset();
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: registers } as any)
            .mockResolvedValueOnce({ attendees } as any)
            .mockResolvedValue({} as any);
    };
    const urls = () => mockHttp.mock.calls.map((c) => String(c[0]));

    afterEach(() => jest.clearAllMocks());

    it("does nothing when there are no moves", async () => {
        mockHttp.mockReset();
        await reconcileAttendanceEnrolments([], CN as any, "dev" as any, {});
        expect(mockHttp).not.toHaveBeenCalled();
    });

    it("enrols at the destination BEFORE end-dating the old enrolment", async () => {
        seq([OLD, NEW], [ATT]);
        await call();
        const u = urls();
        expect(u[0]).toContain("v1/_search");
        expect(u[1]).toContain("attendee/v1/_search");
        expect(u[2]).toContain("attendee/v1/_create");
        expect(u[3]).toContain("attendee/v1/_delete");
    });

    it("end-dates rather than deleting, so days already worked stay attributed", async () => {
        seq([OLD, NEW], [ATT]);
        await call();
        const body: any = mockHttp.mock.calls[3][1];
        expect(body.attendees[0].id).toBe("ATT-1");
        expect(typeof body.attendees[0].denrollmentDate).toBe("number");
    });

    it("is a no-op when the worker is already at the target area", async () => {
        seq([{ ...OLD, localityCode: "VILLAGE_B" }], [ATT]);
        await call();
        expect(urls().some((u) => u.includes("_create"))).toBe(false);
    });

    it("does not enrol a worker who has no live enrolment", async () => {
        seq([OLD, NEW], []);
        await call();
        expect(urls().some((u) => u.includes("_create"))).toBe(false);
    });

    it("ignores an already end-dated enrolment when deciding what is live", async () => {
        seq([OLD, NEW], [{ ...ATT, denrollmentDate: 999 }]);
        await call();
        expect(urls().some((u) => u.includes("_create"))).toBe(false);
    });

    it("leaves the enrolment alone when no register exists at the target area", async () => {
        seq([OLD], [ATT]);
        await call();
        expect(urls().some((u) => u.includes("_create") || u.includes("_delete"))).toBe(false);
    });

    it("REGRESSION: never uses a register belonging to another campaign", async () => {
        // The attendance search ignores campaignNumber, so a foreign register at the SAME locality
        // code can come back. Acting on it would move the worker's pay to another campaign's register.
        const foreign = { id: "REG-FOREIGN", localityCode: "VILLAGE_B", campaignNumber: "CMP-OTHER" } as any;
        seq([OLD, foreign], [ATT]);
        await call();
        expect(urls().some((u) => u.includes("_create"))).toBe(false);
    });

    it("REGRESSION: reads enrolments from the attendee search, not from register.attendees", async () => {
        // register.attendees is not reliably populated by the register search; trusting it made every
        // worker look unenrolled and the realignment silently do nothing.
        const withInlineAttendees = { ...OLD, attendees: [ATT] } as any;
        seq([withInlineAttendees, NEW], []);   // inline present, attendee search says none
        await call();
        expect(urls().some((u) => u.includes("_create"))).toBe(false);
    });

    it("aborts without changing anything when the attendee scan hits its page cap", async () => {
        mockHttp.mockReset();
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any);
        const fullPage = Array.from({ length: 100 }, (_, i) => ({
            id: `A${i}`, individualId: "OTHER", registerId: "REG-OLD", denrollmentDate: null }));
        mockHttp.mockResolvedValue({ attendees: fullPage } as any);
        await call();
        expect(urls().some((u) => u.includes("_create") || u.includes("_delete"))).toBe(false);
    });

    it("does not strand the worker when enrolling at the destination fails", async () => {
        mockHttp.mockReset();
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({ attendees: [ATT] } as any)
            .mockRejectedValueOnce(new Error("attendance down"));
        await call();
        expect(urls().some((u) => u.includes("_delete"))).toBe(false);
    });

    it("never throws when the register search fails", async () => {
        mockHttp.mockReset();
        mockHttp.mockRejectedValueOnce(new Error("search down"));
        await expect(call()).resolves.toBeUndefined();
    });

    it("never throws when the attendee scan fails", async () => {
        mockHttp.mockReset();
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockRejectedValueOnce(new Error("scan down"));
        await expect(call()).resolves.toBeUndefined();
    });

    it("never throws when end-dating fails, leaving two enrolments rather than none", async () => {
        mockHttp.mockReset();
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({ attendees: [ATT] } as any)
            .mockResolvedValueOnce({} as any)
            .mockRejectedValueOnce(new Error("delete down"));
        await expect(call()).resolves.toBeUndefined();
    });

    it("scans attendees once for many workers rather than once per worker", async () => {
        const many = Array.from({ length: 25 }, (_, i) => ({
            individualId: `IND-${i}`, targetLocalityCode: "VILLAGE_B" })) as any;
        const attendees = many.map((m: any, i: number) => ({
            id: `ATT-${i}`, individualId: m.individualId, registerId: "REG-OLD", denrollmentDate: null }));
        mockHttp.mockReset();
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({ attendees } as any)
            .mockResolvedValue({} as any);
        await reconcileAttendanceEnrolments(many, CN as any, "dev" as any, {});
        expect(urls().filter((u) => u.includes("v1/_search") && !u.includes("attendee")).length).toBe(1);
        expect(urls().filter((u) => u.includes("attendee/v1/_search")).length).toBe(1);
    });
});

describe("reconcileAttendanceEnrolmentsForPhones (unified-console path)", () => {
    const CN = "CMP-1";
    const OLD = { id: "REG-OLD", localityCode: "VILLAGE_A", campaignNumber: CN } as any;
    const NEW = { id: "REG-NEW", localityCode: "VILLAGE_B", campaignNumber: CN } as any;
    const ATT = { id: "ATT-1", individualId: "IND-1", registerId: "REG-OLD", enrollmentDate: 1, denrollmentDate: null };
    const urls = () => jest.mocked(httpRequest).mock.calls.map((c) => String(c[0]));
    const call = (m: Map<string, string[]>) =>
        reconcileAttendanceEnrolmentsForPhones(m, CN as any, "dev" as any, {});

    afterEach(() => jest.clearAllMocks());

    it("resolves the phone to its individual, then enrols at the destination BEFORE end-dating", async () => {
        jest.mocked(httpRequest).mockReset();
        jest.mocked(httpRequest)
            .mockResolvedValueOnce({ Individual: [{ id: "IND-1", mobileNumber: "9119900044" }] } as any)
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({ attendees: [ATT] } as any)
            .mockResolvedValue({} as any);
        await call(new Map([["9119900044", ["VILLAGE_B"]]]));
        const u = urls();
        expect(u[0]).toContain("health-individual/v1/_search");
        expect(u[3]).toContain("attendee/v1/_create");
        expect(u[4]).toContain("attendee/v1/_delete");
    });

    it("skips a phone carrying several target boundaries without any lookup", async () => {
        jest.mocked(httpRequest).mockReset();
        await call(new Map([["9119900044", ["VILLAGE_A", "VILLAGE_B"]]]));
        expect(jest.mocked(httpRequest)).not.toHaveBeenCalled();
    });

    it("skips a phone whose individual cannot be found, touching nothing", async () => {
        jest.mocked(httpRequest).mockReset();
        jest.mocked(httpRequest).mockResolvedValueOnce({ Individual: [] } as any);
        await call(new Map([["9119900044", ["VILLAGE_B"]]]));
        expect(urls().some((u) => u.includes("attendance"))).toBe(false);
    });

    it("never throws when the individual lookup fails", async () => {
        jest.mocked(httpRequest).mockReset();
        jest.mocked(httpRequest).mockRejectedValueOnce(new Error("individual down"));
        await expect(call(new Map([["9119900044", ["VILLAGE_B"]]]))).resolves.toBeUndefined();
    });
});
