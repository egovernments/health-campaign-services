jest.mock("../config", () => ({
    default: {
        host: { healthIndividualHost: "http://localhost:8084/" },
        paths: { healthIndividualSearch: "individual/v1/_search" },
    },
    __esModule: true,
}));

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), warn: jest.fn(), error: jest.fn(), debug: jest.fn() },
}));

jest.mock("../utils/campaignUtils", () => ({
    getLocalizedName: jest.fn((key: string) => key),
}));

jest.mock("../utils/request", () => ({
    httpRequest: jest.fn(),
}));

jest.mock("../utils/sheetManageUtils", () => ({
    validateResourceDetailsBeforeProcess: jest.fn().mockResolvedValue(undefined),
}));

const attendeeProcessMock = jest.fn();
jest.mock("../processFlowClasses/attendanceRegisterAttendee-processClass", () => ({
    TemplateClass: {
        process: (...args: unknown[]) => attendeeProcessMock(...args),
    },
}));

import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import { httpRequest } from "../utils/request";
import { TemplateClass } from "../processFlowClasses/attendanceRegisterUserBulkMapping-processClass";
import { bulkAttendanceColumnKeys } from "../utils/attendanceRegisterUserBulkMappingUtils";

describe("attendanceRegisterUserBulkMapping process class", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        attendeeProcessMock.mockReset();
    });

    it("normalizes 3-tab bulk rows, delegates actionable rows, and keeps seed rows skipped", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [{ id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "John" } }],
        });

        let skipPreValidationSeen: unknown;
        attendeeProcessMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            skipPreValidationSeen = _resourceDetails?.additionalDetails?.skipPreValidation;
            const workerRows = localizedSheetData?.HCM_REGISTER_WORKER_SHEET || [];
            for (const row of workerRows) {
                row["#status#"] = "UPDATED";
                row[bulkAttendanceColumnKeys.teamCode] = "TEAM-NEW";
            }
            return {};
        });

        const resourceDetails: any = {
            tenantId: "bednet",
            additionalDetails: {},
            requestInfo: { apiId: "hcm" },
        };
        const wholeSheetData: any = {
            HCM_REGISTER_WORKER_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-001",
                    [bulkAttendanceColumnKeys.userName]: "John",
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                    [bulkAttendanceColumnKeys.teamCode]: "TEAM-OLD",
                },
                {
                    "!row#number!": 4,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-002",
                },
            ],
            HCM_REGISTER_MARKER_SHEET: [],
            HCM_REGISTER_APPROVER_SHEET: [],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});

        expect(validateResourceDetailsBeforeProcess).toHaveBeenCalledWith(
            "attendanceRegisterUserBulkMappingValidation",
            resourceDetails,
            {}
        );
        expect(attendeeProcessMock).toHaveBeenCalledTimes(1);
        expect(skipPreValidationSeen).toBe(true);

        const delegatedSheetData = attendeeProcessMock.mock.calls[0][1];
        expect((delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || []).length).toBe(1);
        expect((delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || [])[0]?.HCM_ATTENDANCE_REGISTER_ID).toBe("REG-001");
        expect((delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || [])[0]?.UserName).toBe("usr-1");
        expect((delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || [])[0]?.[bulkAttendanceColumnKeys.teamCode]).toBe("TEAM-NEW");

        expect(result.HCM_REGISTER_WORKER_SHEET.data[0]["#status#"]).toBe("UPDATED");
        expect(result.HCM_REGISTER_WORKER_SHEET.data[0][bulkAttendanceColumnKeys.teamCode]).toBe("TEAM-NEW");
        expect(result.HCM_REGISTER_WORKER_SHEET.data[1]["#status#"]).toBe("SKIPPED");
        expect(resourceDetails.additionalDetails?.resolvedIndividualIds).toEqual({ "usr-1": "ind-1" });
        expect(resourceDetails.additionalDetails?.skipPreValidation).toBeUndefined();
    });

    it("delegates marker rows to marker sheet without role re-routing", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [{ id: "ind-2", userDetails: { username: "usr-2" }, name: { givenName: "Marker" } }],
        });

        attendeeProcessMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            const markerRows = localizedSheetData?.HCM_REGISTER_MARKER_SHEET || [];
            for (const row of markerRows) {
                row["#status#"] = "CREATED";
            }
            return {};
        });

        const resourceDetails: any = {
            tenantId: "bednet",
            additionalDetails: {},
            requestInfo: { apiId: "hcm" },
        };
        const wholeSheetData: any = {
            HCM_REGISTER_WORKER_SHEET: [],
            HCM_REGISTER_MARKER_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-001",
                    [bulkAttendanceColumnKeys.userName]: "Marker",
                    [bulkAttendanceColumnKeys.workerId]: "ind-2",
                },
            ],
            HCM_REGISTER_APPROVER_SHEET: [],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});
        const delegatedSheetData = attendeeProcessMock.mock.calls[0][1];

        expect((delegatedSheetData?.HCM_REGISTER_MARKER_SHEET || []).length).toBe(1);
        expect((delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || []).length).toBe(0);
        expect(result.HCM_REGISTER_MARKER_SHEET.data[0]["#status#"]).toBe("CREATED");
    });

    it("keeps actionable rows from every register when delegating to attendee process", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [
                { id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "One" } },
                { id: "ind-2", userDetails: { username: "usr-2" }, name: { givenName: "Two" } },
            ],
        });

        attendeeProcessMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            for (const row of localizedSheetData?.HCM_REGISTER_WORKER_SHEET || []) {
                row["#status#"] = "UPDATED";
            }
            return {};
        });

        const resourceDetails: any = {
            tenantId: "bednet",
            additionalDetails: {},
            requestInfo: { apiId: "hcm" },
        };
        const wholeSheetData: any = {
            HCM_REGISTER_WORKER_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-001",
                    [bulkAttendanceColumnKeys.userName]: "One",
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                },
                {
                    "!row#number!": 4,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-002",
                    [bulkAttendanceColumnKeys.userName]: "Two",
                    [bulkAttendanceColumnKeys.workerId]: "ind-2",
                },
            ],
            HCM_REGISTER_MARKER_SHEET: [],
            HCM_REGISTER_APPROVER_SHEET: [],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});
        const delegatedSheetData = attendeeProcessMock.mock.calls[0][1];
        const delegatedRows = delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || [];

        expect(attendeeProcessMock).toHaveBeenCalledTimes(1);
        expect(delegatedRows).toHaveLength(2);
        expect(delegatedRows.map((row: any) => row.HCM_ATTENDANCE_REGISTER_ID)).toEqual(["REG-001", "REG-002"]);
        expect(result.HCM_REGISTER_WORKER_SHEET.data[0]["#status#"]).toBe("UPDATED");
        expect(result.HCM_REGISTER_WORKER_SHEET.data[1]["#status#"]).toBe("UPDATED");
        expect(resourceDetails.additionalDetails?.resolvedIndividualIds).toEqual({
            "usr-1": "ind-1",
            "usr-2": "ind-2",
        });
    });
});
