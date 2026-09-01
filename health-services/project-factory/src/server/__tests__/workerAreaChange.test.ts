import { workerNeedsUpdate } from "../utils/workerRegistryUtils";
import { reconcileAttendanceEnrolments } from "../utils/attendanceEnrolmentUtils";
import { httpRequest } from "../utils/request";

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock("../utils/request", () => ({ httpRequest: jest.fn() }));
jest.mock("../config", () => ({
    __esModule: true,
    default: {
        host: { attendanceHost: "http://attendance/" },
        paths: {
            attendanceRegisterSearch: "health-attendance/v1/_search",
            attendanceAttendeeCreate: "health-attendance/attendee/v1/_create",
            attendanceAttendeeDelete: "health-attendance/attendee/v1/_delete",
        },
        attendanceRegister: { batchSize: 50 },
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
    const OLD = { id: "REG-OLD", localityCode: "VILLAGE_A", registerNumber: "R/OLD", attendees: [
        { id: "ATT-1", individualId: "IND-1", registerId: "REG-OLD", enrollmentDate: 1, denrollmentDate: null },
    ] } as any;
    const NEW = { id: "REG-NEW", localityCode: "VILLAGE_B", registerNumber: "R/NEW", attendees: [] } as any;

    const moves = [{ individualId: "IND-1", targetLocalityCode: "VILLAGE_B" }] as any;
    const call = () => reconcileAttendanceEnrolments(moves, "CMP-1" as any, "dev" as any, {});

    afterEach(() => jest.clearAllMocks());

    it("does nothing when there are no moves", async () => {
        await reconcileAttendanceEnrolments([], "CMP-1" as any, "dev" as any, {});
        expect(mockHttp).not.toHaveBeenCalled();
    });

    it("enrols on the destination BEFORE end-dating the old row", async () => {
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({} as any)
            .mockResolvedValueOnce({} as any);
        await call();
        const urls = mockHttp.mock.calls.map((c) => String(c[0]));
        expect(urls[1]).toContain("attendee/v1/_create");
        expect(urls[2]).toContain("attendee/v1/_delete");
    });

    it("end-dates the old enrolment rather than deleting it, preserving worked days", async () => {
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({} as any)
            .mockResolvedValueOnce({} as any);
        await call();
        const deleteBody: any = mockHttp.mock.calls[2][1];
        expect(deleteBody.attendees[0].id).toBe("ATT-1");
        expect(typeof deleteBody.attendees[0].denrollmentDate).toBe("number");
    });

    it("is a no-op when the worker is already enrolled at the target area", async () => {
        const already = { ...OLD, localityCode: "VILLAGE_B" };
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [already] } as any);
        await call();
        expect(mockHttp).toHaveBeenCalledTimes(1);
    });

    it("does not enrol a worker who has no live enrolment", async () => {
        const noAttendees = { ...OLD, attendees: [] };
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [noAttendees, NEW] } as any);
        await call();
        expect(mockHttp).toHaveBeenCalledTimes(1);
    });

    it("ignores an already de-enrolled row when deciding what is live", async () => {
        const ended = { ...OLD, attendees: [{ ...OLD.attendees[0], denrollmentDate: 999 }] };
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [ended, NEW] } as any);
        await call();
        expect(mockHttp).toHaveBeenCalledTimes(1);
    });

    it("leaves the old enrolment intact when no register exists at the target area", async () => {
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [OLD] } as any);
        await call();
        expect(mockHttp).toHaveBeenCalledTimes(1);
        expect(mockHttp.mock.calls.some((c) => String(c[0]).includes("_delete"))).toBe(false);
    });

    it("does not strand the worker when enrolment at the destination fails", async () => {
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockRejectedValueOnce(new Error("attendance down"));
        await call();
        expect(mockHttp.mock.calls.some((c) => String(c[0]).includes("_delete"))).toBe(false);
    });

    it("never throws when the register search fails", async () => {
        mockHttp.mockRejectedValueOnce(new Error("search down"));
        await expect(call()).resolves.toBeUndefined();
    });

    it("never throws when end-dating fails, leaving two enrolments rather than none", async () => {
        mockHttp
            .mockResolvedValueOnce({ attendanceRegister: [OLD, NEW] } as any)
            .mockResolvedValueOnce({} as any)
            .mockRejectedValueOnce(new Error("delete down"));
        await expect(call()).resolves.toBeUndefined();
    });

    it("searches registers once for many workers rather than once per worker", async () => {
        const many = Array.from({ length: 25 }, (_, i) => ({
            individualId: `IND-${i}`, targetLocalityCode: "VILLAGE_B",
        })) as any;
        const attendees = many.map((m: any, i: number) => ({
            id: `ATT-${i}`, individualId: m.individualId, registerId: "REG-OLD", denrollmentDate: null,
        }));
        mockHttp.mockResolvedValue({} as any);
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [{ ...OLD, attendees }, NEW] } as any);
        await reconcileAttendanceEnrolments(many, "CMP-1" as any, "dev" as any, {});
        const searches = mockHttp.mock.calls.filter((c) => String(c[0]).includes("v1/_search"));
        expect(searches).toHaveLength(1);
    });

    it("skips a deleted register as a destination", async () => {
        const deletedTarget = { ...NEW, isDeleted: true };
        mockHttp.mockResolvedValueOnce({ attendanceRegister: [OLD, deletedTarget] } as any);
        await call();
        expect(mockHttp).toHaveBeenCalledTimes(1);
    });
});
