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

import { attendanceSheetNames, sheetDataRowStatuses } from "../config/constants";
import { httpRequest } from "../utils/request";
import {
    bulkAttendanceColumnKeys,
    collectBulkWorkerIds,
    fetchIndividualProfilesById,
    getBulkRowsBySheetName,
    groupRowsByRegister,
    normalizeBulkRowsForAttendeeFlow,
} from "../utils/attendanceRegisterUserBulkMappingUtils";

describe("attendanceRegisterUserBulkMapping utils", () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it("normalizes 3-tab rows, resolves usernames from workerId, and skips blank seed rows", () => {
        const wholeSheetData = {
            HCM_REGISTER_WORKER_SHEET: [
                {
                    "!row#number!": 3,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-1",
                    [bulkAttendanceColumnKeys.workerId]: "ind-worker",
                    [bulkAttendanceColumnKeys.userName]: "Worker Name",
                    [bulkAttendanceColumnKeys.teamCode]: "TEAM-A",
                },
                {
                    "!row#number!": 4,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-2",
                },
            ],
            HCM_REGISTER_MARKER_SHEET: [
                {
                    "!row#number!": 5,
                    [bulkAttendanceColumnKeys.registerCode]: "REG-1",
                    [bulkAttendanceColumnKeys.workerId]: "ind-marker",
                    [bulkAttendanceColumnKeys.userName]: "Marker Name",
                },
            ],
            HCM_REGISTER_APPROVER_SHEET: [
                {
                    "!row#number!": 6,
                    [bulkAttendanceColumnKeys.workerId]: "ind-approver",
                    [bulkAttendanceColumnKeys.userName]: "Approver Name",
                },
            ],
        };

        const allRowsBySheetName = getBulkRowsBySheetName(wholeSheetData, {});
        const workerIds = collectBulkWorkerIds(allRowsBySheetName);
        expect(workerIds).toEqual(["ind-worker", "ind-marker", "ind-approver"]);

        const profiles = new Map([
            ["ind-worker", { username: "usr-worker", displayName: "Worker Name" }],
            ["ind-marker", { username: "usr-marker", displayName: "Marker Name" }],
            ["ind-approver", { username: "usr-approver", displayName: "Approver Name" }],
        ]);

        const { actionableRowsBySheetName, resolvedIndividualIds } = normalizeBulkRowsForAttendeeFlow(
            allRowsBySheetName,
            profiles
        );

        expect(actionableRowsBySheetName.get(attendanceSheetNames.WORKER)).toHaveLength(1);
        expect(actionableRowsBySheetName.get(attendanceSheetNames.MARKER)).toHaveLength(1);
        expect(actionableRowsBySheetName.get(attendanceSheetNames.APPROVER)).toHaveLength(0);

        const workerRow = (allRowsBySheetName.get(attendanceSheetNames.WORKER) || [])[0];
        expect(workerRow.UserName).toBe("usr-worker");
        expect(workerRow[bulkAttendanceColumnKeys.teamCode]).toBe("TEAM-A");

        const blankSeedRow = (allRowsBySheetName.get(attendanceSheetNames.WORKER) || [])[1];
        expect(blankSeedRow["#status#"]).toBe(sheetDataRowStatuses.SKIPPED);

        const invalidApproverRow = (allRowsBySheetName.get(attendanceSheetNames.APPROVER) || [])[0];
        expect(invalidApproverRow["#status#"]).toBe(sheetDataRowStatuses.INVALID);
        expect(invalidApproverRow["#errorDetails#"]).toBe("Register code is required");

        const groupedByRegister = groupRowsByRegister(actionableRowsBySheetName);
        expect(groupedByRegister.size).toBe(1);
        const reg1 = groupedByRegister.get("REG-1");
        expect((reg1?.get(attendanceSheetNames.WORKER) || []).length).toBe(1);
        expect((reg1?.get(attendanceSheetNames.MARKER) || []).length).toBe(1);

        expect(resolvedIndividualIds).toEqual({
            "usr-worker": "ind-worker",
            "usr-marker": "ind-marker",
            "usr-approver": "ind-approver",
        });
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
