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

    it("groups rows by register and delegates validation per register", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [
                { id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "A" } },
                { id: "ind-2", userDetails: { username: "usr-2" }, name: { givenName: "B" } },
            ],
        });

        attendeeValidationMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            const workerRows = localizedSheetData?.HCM_REGISTER_WORKER_SHEET || [];
            for (const row of workerRows) {
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
            HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-001",
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                    [bulkAttendanceColumnKeys.userName]: "A",
                    [bulkAttendanceColumnKeys.role]: "WORKER",
                },
                {
                    "!row#number!": 4,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-002",
                    [bulkAttendanceColumnKeys.workerId]: "ind-2",
                    [bulkAttendanceColumnKeys.userName]: "B",
                    [bulkAttendanceColumnKeys.role]: "WORKER",
                },
            ],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});

        expect(attendeeValidationMock).toHaveBeenCalledTimes(2);
        expect(result.HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET.data[0]["#status#"]).toBe("UPDATED");
        expect(result.HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET.data[1]["#status#"]).toBe("UPDATED");
        expect(resourceDetails.additionalDetails?.resolvedIndividualIds).toEqual({
            "usr-1": "ind-1",
            "usr-2": "ind-2",
        });
    });

    it("propagates invalid rows into sheetErrors for pre-validation gate", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [{ id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "A" } }],
        });

        attendeeValidationMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            const workerRows = localizedSheetData?.HCM_REGISTER_WORKER_SHEET || [];
            for (const row of workerRows) {
                row["#status#"] = "INVALID";
                row["#errorDetails#"] = "Register not found";
            }
            return {};
        });

        const resourceDetails: any = {
            tenantId: "bednet",
            additionalDetails: {},
            requestInfo: { apiId: "hcm" },
        };
        const wholeSheetData: any = {
            HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-001",
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                    [bulkAttendanceColumnKeys.userName]: "A",
                    [bulkAttendanceColumnKeys.role]: "WORKER",
                },
            ],
        };

        await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});

        expect(resourceDetails.additionalDetails.sheetErrors).toEqual([
            {
                sheetName: "HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET",
                errorDetails: "Register not found",
            },
        ]);
    });
});
