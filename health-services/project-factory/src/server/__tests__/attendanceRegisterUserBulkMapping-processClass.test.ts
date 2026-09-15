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

    it("validates, delegates to attendee process and copies status back to bulk rows", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [{ id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "John" } }],
        });

        let skipPreValidationSeen: unknown;
        attendeeProcessMock.mockImplementation(async (_resourceDetails: any, localizedSheetData: any) => {
            skipPreValidationSeen = _resourceDetails?.additionalDetails?.skipPreValidation;
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
                    [bulkAttendanceColumnKeys.userName]: "John",
                    [bulkAttendanceColumnKeys.workerId]: "ind-1",
                    [bulkAttendanceColumnKeys.role]: "WORKER",
                },
                {
                    "!row#number!": 4,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-002",
                },
            ],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});

        expect(validateResourceDetailsBeforeProcess).toHaveBeenCalledWith(
            "attendanceRegisterUserBulkMappingValidation",
            resourceDetails,
            {}
        );
        expect(attendeeProcessMock).toHaveBeenCalledTimes(1);
        expect(skipPreValidationSeen).toBe(true);
        expect(result.HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET.data[0]["#status#"]).toBe("UPDATED");
        expect(result.HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET.data[1]["#status#"]).toBe("SKIPPED");
        expect(resourceDetails.additionalDetails?.resolvedIndividualIds).toEqual({ "usr-1": "ind-1" });
        expect(resourceDetails.additionalDetails?.skipPreValidation).toBeUndefined();
    });

    it("routes TEAM_SUPERVISOR rows to marker sheet", async () => {
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
            HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-001",
                    [bulkAttendanceColumnKeys.userName]: "Marker",
                    [bulkAttendanceColumnKeys.workerId]: "ind-2",
                    [bulkAttendanceColumnKeys.role]: "TEAM_SUPERVISOR",
                },
            ],
        };

        const result = await TemplateClass.process(resourceDetails, wholeSheetData, {}, {});
        const delegatedSheetData = attendeeProcessMock.mock.calls[0][1];

        expect((delegatedSheetData?.HCM_REGISTER_MARKER_SHEET || []).length).toBe(1);
        expect((delegatedSheetData?.HCM_REGISTER_WORKER_SHEET || []).length).toBe(0);
        expect(result.HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET.data[0]["#status#"]).toBe("CREATED");
    });
});
