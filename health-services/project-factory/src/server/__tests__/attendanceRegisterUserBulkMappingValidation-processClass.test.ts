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

const attendeeValidationMock = jest.fn();
jest.mock("../processFlowClasses/attendanceRegisterAttendeeValidation-processClass", () => ({
    TemplateClass: {
        process: (...args: unknown[]) => attendeeValidationMock(...args),
    },
}));

import { httpRequest } from "../utils/request";
import { TemplateClass } from "../processFlowClasses/attendanceRegisterUserBulkMappingValidation-processClass";
import { bulkAttendanceColumnKeys } from "../utils/attendanceRegisterUserBulkMappingUtils";

describe("attendanceRegisterUserBulkMapping validation class", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        attendeeValidationMock.mockReset();
    });

    it("groups rows by register across 3 tabs and delegates validation per register", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [
                { id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "A" } },
                { id: "ind-2", userDetails: { username: "usr-2" }, name: { givenName: "B" } },
            ],
        });

        let delegatedTeamCode = "";
        attendeeValidationMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            const workerRows = localizedSheetData?.HCM_REGISTER_WORKER_SHEET || [];
            for (const row of workerRows) {
                if (!delegatedTeamCode) delegatedTeamCode = row[bulkAttendanceColumnKeys.teamCode];
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
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                    [bulkAttendanceColumnKeys.userName]: "A",
                    [bulkAttendanceColumnKeys.teamCode]: "TEAM-1",
                },
                {
                    "!row#number!": 4,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-002",
                    [bulkAttendanceColumnKeys.workerId]: "ind-2",
                    [bulkAttendanceColumnKeys.userName]: "B",
                },
            ],
            HCM_REGISTER_MARKER_SHEET: [],
            HCM_REGISTER_APPROVER_SHEET: [],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});

        expect(attendeeValidationMock).toHaveBeenCalledTimes(2);
        expect(delegatedTeamCode).toBe("TEAM-1");
        expect(result.HCM_REGISTER_WORKER_SHEET.data[0]["#status#"]).toBe("UPDATED");
        expect(result.HCM_REGISTER_WORKER_SHEET.data[1]["#status#"]).toBe("UPDATED");
        expect(resourceDetails.additionalDetails?.resolvedIndividualIds).toEqual({
            "usr-1": "ind-1",
            "usr-2": "ind-2",
        });
    });

    it("propagates invalid rows into sheetErrors with source tab name", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [],
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
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                    [bulkAttendanceColumnKeys.userName]: "A",
                },
            ],
            HCM_REGISTER_MARKER_SHEET: [],
            HCM_REGISTER_APPROVER_SHEET: [],
        };

        await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});

        expect(resourceDetails.additionalDetails.sheetErrors).toEqual([
            {
                sheetName: "HCM_REGISTER_WORKER_SHEET",
                errorDetails: "Register code is required",
            },
        ]);
    });
});
