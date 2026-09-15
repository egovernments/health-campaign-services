jest.mock("../config", () => ({
    default: {
        host: { healthIndividualHost: "http://localhost:8084/" },
        paths: { healthIndividualSearch: "individual/v1/_search" },
    },
    __esModule: true,
}));

jest.mock("../utils/request", () => ({
    httpRequest: jest.fn(),
}));

jest.mock("../utils/campaignUtils", () => ({
    getLocalizedName: jest.fn((key: string) => key),
}));

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), warn: jest.fn(), error: jest.fn(), debug: jest.fn() },
}));

import { sheetDataRowStatuses, attendanceSheetNames } from "../config/constants";
import { httpRequest } from "../utils/request";
import {
    bulkAttendanceColumnKeys,
    fetchIndividualProfilesById,
    projectBulkRowsToAttendanceSheets,
} from "../utils/attendanceRegisterUserBulkMappingUtils";

describe("attendanceRegisterUserBulkMapping utils", () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it("projects rows into worker/marker/approver sheets based on role", () => {
        const rows = [
            {
                "!row#number!": 3,
                [bulkAttendanceColumnKeys.registerCode]: "REG-1",
                [bulkAttendanceColumnKeys.workerId]: "ind-worker",
                [bulkAttendanceColumnKeys.userName]: "Worker Name",
                [bulkAttendanceColumnKeys.role]: "WORKER",
            },
            {
                "!row#number!": 4,
                [bulkAttendanceColumnKeys.registerCode]: "REG-1",
                [bulkAttendanceColumnKeys.workerId]: "ind-marker",
                [bulkAttendanceColumnKeys.userName]: "Marker Name",
                [bulkAttendanceColumnKeys.role]: "TEAM_SUPERVISOR",
            },
            {
                "!row#number!": 5,
                [bulkAttendanceColumnKeys.registerCode]: "REG-1",
                [bulkAttendanceColumnKeys.workerId]: "ind-approver",
                [bulkAttendanceColumnKeys.userName]: "Approver Name",
                [bulkAttendanceColumnKeys.role]: "PROXIMITY_SUPERVISOR",
            },
        ];

        const profiles = new Map([
            ["ind-worker", { username: "usr-worker", displayName: "Worker Name" }],
            ["ind-marker", { username: "usr-marker", displayName: "Marker Name" }],
            ["ind-approver", { username: "usr-approver", displayName: "Approver Name" }],
        ]);

        const { rowsBySheetName, projections, resolvedIndividualIds } = projectBulkRowsToAttendanceSheets(rows, profiles);

        expect(rowsBySheetName.get(attendanceSheetNames.WORKER)).toHaveLength(1);
        expect(rowsBySheetName.get(attendanceSheetNames.MARKER)).toHaveLength(1);
        expect(rowsBySheetName.get(attendanceSheetNames.APPROVER)).toHaveLength(1);
        expect(projections).toHaveLength(3);
        expect(resolvedIndividualIds).toEqual({
            "usr-worker": "ind-worker",
            "usr-marker": "ind-marker",
            "usr-approver": "ind-approver",
        });
    });

    it("marks register-only rows as SKIPPED and missing worker id rows as INVALID", () => {
        const rows = [
            {
                "!row#number!": 3,
                [bulkAttendanceColumnKeys.registerCode]: "REG-1",
            },
            {
                "!row#number!": 4,
                [bulkAttendanceColumnKeys.registerCode]: "REG-1",
                [bulkAttendanceColumnKeys.userName]: "Only Name",
                [bulkAttendanceColumnKeys.role]: "WORKER",
            },
        ];

        const { projections } = projectBulkRowsToAttendanceSheets(rows, new Map());

        expect(rows[0]["#status#"]).toBe(sheetDataRowStatuses.SKIPPED);
        expect(rows[1]["#status#"]).toBe(sheetDataRowStatuses.INVALID);
        expect(rows[1]["#errorDetails#"]).toBe("Worker ID is required");
        expect(projections).toHaveLength(0);
    });

    it("searches individual profiles with tenantId, limit and offset params", async () => {
        jest.mocked(httpRequest).mockResolvedValue({
            Individual: [{ id: "ind-1", userDetails: { username: "usr-1" }, name: { givenName: "User" } }],
        });

        const profiles = await fetchIndividualProfilesById("bednet", ["ind-1"], { apiId: "hcm" } as any);

        expect(profiles.get("ind-1")).toEqual({
            username: "usr-1",
            displayName: "User",
        });
        expect(httpRequest).toHaveBeenCalledWith(
            "http://localhost:8084/individual/v1/_search",
            {
                RequestInfo: { apiId: "hcm" },
                Individual: { id: ["ind-1"] },
            },
            { tenantId: "bednet", limit: 6, offset: 0 },
            "post",
            "",
            undefined,
            false,
            false,
            true
        );
    });
});
