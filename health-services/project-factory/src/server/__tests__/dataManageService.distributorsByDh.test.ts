jest.mock("../api/campaignApis", () => ({
    processGenericRequest: jest.fn(),
}));

jest.mock("../api/genericApis", () => ({
    createAndUploadFile: jest.fn(),
    getBoundarySheetData: jest.fn(),
}));

jest.mock("../utils/campaignUtils", () => ({
    getLocalizedName: jest.fn((key: string) => key),
    getResourceDetails: jest.fn().mockResolvedValue(null),
    processDataSearchRequest: jest.fn(),
    isCampaignIdOfMicroplan: jest.fn().mockResolvedValue(false),
}));

const mockThrowError = jest.fn((module = "COMMON", status = 500, code = "UNKNOWN_ERROR", description: any = null) => {
    const error: any = new Error(description || code);
    error.status = status;
    error.code = code;
    error.description = description;
    throw error;
});

jest.mock("../utils/genericUtils", () => ({
    addDataToSheet: jest.fn(),
    enrichResourceDetails: jest.fn(),
    getLocalizedMessagesHandler: jest.fn(),
    searchGeneratedResources: jest.fn(),
    searchAllGeneratedResources: jest.fn(),
    processGenerate: jest.fn(),
    throwError: (...args: any[]) => mockThrowError(...args),
    searchCampaignData: jest.fn(),
    searchMappingData: jest.fn(),
    getRelatedDataWithCampaign: jest.fn(),
}));

jest.mock("../utils/logger", () => ({
    getFormattedStringForDebug: jest.fn((v: any) => JSON.stringify(v)),
    logger: {
        info: jest.fn(),
        debug: jest.fn(),
        warn: jest.fn(),
        error: jest.fn(),
    },
}));

jest.mock("../validators/campaignValidators", () => ({
    validateCreateRequest: jest.fn(),
    validateDownloadRequest: jest.fn(),
    validateSearchRequest: jest.fn(),
}));

jest.mock("../validators/genericValidator", () => ({
    validateGenerateRequest: jest.fn(),
}));

jest.mock("../utils/localisationUtils", () => ({
    getLocaleFromRequestInfo: jest.fn(),
    getLocalisationModuleName: jest.fn(),
}));

jest.mock("../utils/boundaryUtils", () => ({
    getBoundaryTabName: jest.fn(),
}));

jest.mock("../utils/excelUtils", () => ({
    getNewExcelWorkbook: jest.fn(),
}));

jest.mock("../utils/redisUtils", () => ({
    redis: { get: jest.fn(), set: jest.fn(), expire: jest.fn() },
    checkRedisConnection: jest.fn().mockResolvedValue(false),
}));

const mockConfig = {
    cacheTime: 300,
    cacheValues: { resetCache: false },
    generatedResource: { reuseWindowMs: 30000 },
};

jest.mock("../config/index", () => ({
    default: mockConfig,
    __esModule: true,
}));

jest.mock("../utils/generateUtils", () => ({
    callGenerate: jest.fn(),
}));

jest.mock("../service/sheetManageService", () => ({
    generateDataService: jest.fn(),
}));

jest.mock("../generateFlowClasses/attendanceRegisterUserBulkMapping-generateClass", () => ({
    TemplateClass: { generate: jest.fn() },
}));

jest.mock("../utils/cryptUtils", () => ({
    decrypt: jest.fn((value: string) => {
        if (value === "enc:boom") throw new Error("bad cipher");
        return value.startsWith("enc:") ? value.slice(4) : value;
    }),
}));
jest.mock("../utils/userBatchHandler", () => ({
    fetchUserUuidsByUserName: jest.fn(),
}));
jest.mock("../service/campaignManageService", () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

import { searchDistributorsByDhService } from "../service/dataManageService";
import { TemplateClass } from "../generateFlowClasses/attendanceRegisterUserBulkMapping-generateClass";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { fetchUserUuidsByUserName } from "../utils/userBatchHandler";

const mockGenerate = TemplateClass.generate as jest.Mock;
const mockSearchCampaign = searchProjectTypeCampaignService as jest.Mock;
const mockUserRows = getRelatedDataWithCampaign as jest.Mock;
const mockFetchUuidsByUserName = fetchUserUuidsByUserName as jest.Mock;

const WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
const DH_ONE = "ITN_NI_01_27_GIDAN_LAWAN";
const DH_TWO = "ITN_NI_01_25_ALI_BRICKLER";
const uuidFor = (n: number): string => `b0000000-0000-4000-8000-${String(n).padStart(12, "0")}`;

function workerRow(overrides: Record<string, string> = {}): Record<string, string> {
    return {
        HCM_ADMIN_CONSOLE_USER_ROLE: "DISTRIBUTOR",
        HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-1",
        UserName: "dist.1",
        HCM_ADMIN_CONSOLE_USER_NAME: "Dist 1",
        HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: DH_ONE,
        HCM_ADMIN_CONSOLE_BOUNDARY_NAME: "Gidan Lawan",
        ...overrides,
    };
}

function userRow(workerId: string, uuid: string | null, extra: Record<string, unknown> = {}) {
    return {
        data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: workerId },
        uniqueIdAfterProcess: uuid,
        isDeleted: false,
        ...extra,
    };
}

const REQUESTER_UUID = "e0000000-0000-4000-8000-000000000001";
const REQUEST_INFO = { msgId: "m1", userInfo: { uuid: REQUESTER_UUID } };
// The default requester is a DH user of every DH the suite uses, so DH-agnostic tests still see all of them.
const SUITE_DH_CODES = [DH_ONE, DH_TWO, "DH_A", "DH_B", "DH_C", "DH_Z"];

function requesterRows(dhCodes: string[] = SUITE_DH_CODES, extra: Record<string, unknown> = {}) {
    return dhCodes.map((dhCode) => ({
        data: {
            HCM_ADMIN_CONSOLE_USER_ROLE: "WAREHOUSE_MANAGER,TEAM_SUPERVISOR",
            HCM_ADMIN_CONSOLE_USER_WORKER_ID: "REQ-1",
            UserName: "enc:req.wmt",
            HCM_ADMIN_CONSOLE_USER_NAME: "Requester",
            HCM_ADMIN_CONSOLE_BOUNDARY_CODE: dhCode,
        },
        uniqueIdAfterProcess: REQUESTER_UUID,
        isDeleted: false,
        ...extra,
    }));
}

function givenUserRows(rows: unknown[], requesterDhCodes: string[] = SUITE_DH_CODES): void {
    mockUserRows.mockResolvedValue([...rows, ...requesterRows(requesterDhCodes)]);
}

function buildRequest(searchCriteria: any = { tenantId: "bo", campaignNumber: "CMP-1" }, pagination?: any, requestInfo: any = REQUEST_INFO) {
    return {
        body: {
            RequestInfo: requestInfo,
            SearchCriteria: searchCriteria,
            ...(pagination ? { Pagination: pagination } : {}),
        },
    };
}

function givenWorkerRows(rows: Record<string, string>[]): void {
    mockGenerate.mockResolvedValue({ [WORKER_SHEET]: { data: rows } });
}

describe("searchDistributorsByDhService", () => {
    beforeEach(() => {
        mockSearchCampaign.mockResolvedValue({ CampaignDetails: [{ id: "camp-1", campaignName: "Camp One", campaignNumber: "CMP-1", hierarchyType: "ITN" }] });
        givenWorkerRows([]);
        mockFetchUuidsByUserName.mockResolvedValue(new Map<string, string>());
        givenUserRows([
            userRow("W-1", uuidFor(1)),
            userRow("W-2", uuidFor(2)),
            userRow("W-3", uuidFor(3)),
        ]);
    });

    afterEach(() => jest.clearAllMocks());

    describe("request validation", () => {
        it("rejects a request without SearchCriteria", async () => {
            await expect(searchDistributorsByDhService({ body: {} })).rejects.toMatchObject({
                status: 400,
                code: "VALIDATION_ERROR",
                description: "SearchCriteria is required",
            });
        });

        it("rejects a request without tenantId", async () => {
            await expect(searchDistributorsByDhService(buildRequest({ campaignNumber: "CMP-1" }))).rejects.toMatchObject({
                status: 400,
                description: "tenantId is required in SearchCriteria",
            });
        });

        it("rejects a request without campaignNumber", async () => {
            await expect(searchDistributorsByDhService(buildRequest({ tenantId: "bo" }))).rejects.toMatchObject({
                status: 400,
                description: "campaignNumber is required in SearchCriteria",
            });
        });

        it("rejects localityCodes that is not an array", async () => {
            const request = buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: "DH_01" });
            await expect(searchDistributorsByDhService(request)).rejects.toMatchObject({
                status: 400,
                description: "localityCodes must be an array",
            });
        });

        it("rejects when the campaign does not exist", async () => {
            mockSearchCampaign.mockResolvedValue({ CampaignDetails: [] });
            await expect(searchDistributorsByDhService(buildRequest())).rejects.toMatchObject({
                status: 400,
                code: "CAMPAIGN_NOT_FOUND",
            });
            expect(mockGenerate).not.toHaveBeenCalled();
        });

        it("rejects when neither the request nor the campaign has a hierarchyType", async () => {
            mockSearchCampaign.mockResolvedValue({ CampaignDetails: [{ id: "camp-1" }] });
            await expect(searchDistributorsByDhService(buildRequest())).rejects.toMatchObject({
                status: 400,
                description: "hierarchyType is required in SearchCriteria or campaign",
            });
        });

        it("rejects an out-of-range pagination limit", async () => {
            await expect(searchDistributorsByDhService(buildRequest(undefined, { limit: 1001, offset: 0 })))
                .rejects.toMatchObject({ status: 400, description: "limit must be between 1 and 1000" });
        });

        it("rejects a negative pagination offset", async () => {
            await expect(searchDistributorsByDhService(buildRequest(undefined, { limit: 10, offset: -1 })))
                .rejects.toMatchObject({ status: 400, description: "offset cannot be negative" });
        });
    });

    describe("hierarchyType resolution", () => {
        it("uses the campaign hierarchyType when the request has none", async () => {
            await searchDistributorsByDhService(buildRequest());
            expect(mockGenerate.mock.calls[0][1]).toMatchObject({ tenantId: "bo", campaignId: "camp-1", hierarchyType: "ITN" });
        });

        it("prefers the hierarchyType sent in the request", async () => {
            await searchDistributorsByDhService(buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", hierarchyType: "ADMIN" }));
            expect(mockGenerate.mock.calls[0][1]).toMatchObject({ hierarchyType: "ADMIN" });
        });
    });

    describe("grouping distributors by DH", () => {
        it("groups distributors under their DH code, sorted by DH code", async () => {
            givenWorkerRows([
                workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", UserName: "dist.2", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 2", HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: DH_TWO, HCM_ADMIN_CONSOLE_BOUNDARY_NAME: "Ali Brickler" }),
                workerRow(),
                workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-3", UserName: "dist.3", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 3" }),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(2);
            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO, DH_ONE]);
            const gidan: any = result.DHDistributors.find((g: any) => g.dhCode === DH_ONE);
            expect(gidan.dhName).toBe("Gidan Lawan");
            expect(gidan.distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1), uuidFor(3)]);
            expect(gidan.distributors[0]).toEqual({ uuid: uuidFor(1), userName: "dist.1", name: "Dist 1" });
        });

        it("excludes workers that are not distributors", async () => {
            givenWorkerRows([
                workerRow(),
                workerRow({ HCM_ADMIN_CONSOLE_USER_ROLE: "REGISTRAR", HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-9" }),
                workerRow({ HCM_ADMIN_CONSOLE_USER_ROLE: "", HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-8" }),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1)]);
        });

        it("detects the distributor role from the multiselect role columns", async () => {
            givenWorkerRows([
                workerRow({ HCM_ADMIN_CONSOLE_USER_ROLE: "", HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_2: "distributor" }),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors).toHaveLength(1);
        });

        it("still returns a distributor who also holds other roles", async () => {
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_ROLE: "DISTRIBUTOR,REGISTRAR" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors).toEqual([{ uuid: uuidFor(1), userName: "dist.1", name: "Dist 1" }]);
        });

        it("does not expose workerId or roles", async () => {
            givenWorkerRows([workerRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(Object.keys(result.DHDistributors[0].distributors[0]).sort()).toEqual(["name", "userName", "uuid"]);
        });

        it("lists a worker once per DH even when mapped to several registers", async () => {
            givenWorkerRows([workerRow({ HCM_ATTENDANCE_REGISTER_CODE: "reg-a" }), workerRow({ HCM_ATTENDANCE_REGISTER_CODE: "reg-b" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors).toHaveLength(1);
        });

        it("falls back to HCM_ADMIN_CONSOLE_BOUNDARY_CODE and to the code as DH name", async () => {
            givenWorkerRows([
                workerRow({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "", HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_TWO, HCM_ADMIN_CONSOLE_BOUNDARY_NAME: "" }),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0]).toMatchObject({ dhCode: DH_TWO, dhName: DH_TWO });
        });

        it("skips distributors without any DH code", async () => {
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("returns an empty result when the worker sheet is missing", async () => {
            mockGenerate.mockResolvedValue({});

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });
    });

    describe("localityCodes filter", () => {
        beforeEach(() => {
            givenWorkerRows([
                workerRow(),
                workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: DH_TWO }),
            ]);
        });

        it("returns only the requested DH codes", async () => {
            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [DH_TWO] })
            );

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO]);
            expect(result.TotalCount).toBe(1);
        });

        it("treats an empty localityCodes array as no filter", async () => {
            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [] })
            );

            expect(result.TotalCount).toBe(2);
        });

        it("returns nothing for a DH that has no distributors", async () => {
            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: ["UNKNOWN_DH"] })
            );

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });
    });

    describe("pagination", () => {
        beforeEach(() => {
            givenWorkerRows(
                ["DH_A", "DH_B", "DH_C"].map((dh, i) =>
                    workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: `W-${i}`, HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: dh })
                )
            );
        });

        it("returns every DH and echoes the group count as limit when no Pagination is sent", async () => {
            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors).toHaveLength(3);
            expect(result.Pagination).toEqual({ offset: 0, limit: 3 });
        });

        it("pages through DH groups while TotalCount stays the full count", async () => {
            const result = await searchDistributorsByDhService(buildRequest(undefined, { limit: 2, offset: 1 }));

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual(["DH_B", "DH_C"]);
            expect(result.TotalCount).toBe(3);
            expect(result.Pagination).toEqual({ offset: 1, limit: 2 });
        });

        it("defaults the limit to 100 when Pagination omits it", async () => {
            const result = await searchDistributorsByDhService(buildRequest(undefined, { offset: 0 }));

            expect(result.Pagination).toEqual({ offset: 0, limit: 100 });
        });
    });

    describe("campaign lookup by number", () => {
        it("searches the campaign by number and generates with the resolved campaign id", async () => {
            await searchDistributorsByDhService(buildRequest());

            expect(mockSearchCampaign.mock.calls[0][0]).toEqual({ tenantId: "bo", campaignNumber: "CMP-1" });
            expect(mockGenerate.mock.calls[0][1]).toMatchObject({ campaignId: "camp-1" });
        });

        it("prefers the parent campaign when child campaigns share the number", async () => {
            mockSearchCampaign.mockResolvedValue({
                CampaignDetails: [
                    { id: "child-1", parentId: "camp-1", campaignNumber: "CMP-1", hierarchyType: "ITN" },
                    { id: "camp-1", campaignNumber: "CMP-1", hierarchyType: "ITN" },
                ],
            });

            await searchDistributorsByDhService(buildRequest());

            expect(mockGenerate.mock.calls[0][1]).toMatchObject({ campaignId: "camp-1" });
        });
    });

    describe("distributor uuid", () => {
        it("takes the user-service uuid from the user row matching the worker id", async () => {
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe(uuidFor(2));
            expect(mockUserRows).toHaveBeenCalledWith("user", "CMP-1", "bo", "completed");
        });

        it("falls back to the UserService Uuids field when uniqueIdAfterProcess is empty", async () => {
            givenUserRows([userRow("W-1", null, { data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-1", "UserService Uuids": uuidFor(7) } })]);
            givenWorkerRows([workerRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe(uuidFor(7));
        });

        it("matches by decrypted userName when the sheet worker id is not the upload worker id", async () => {
            givenUserRows([userRow("BA-DC-1", uuidFor(5), { data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: "BA-DC-1", UserName: "enc:dist.1" } })]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "individual-uuid-9" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe(uuidFor(5));
        });

        it("prefers the worker id match over the userName match", async () => {
            givenUserRows([
                userRow("W-1", uuidFor(1), { data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-1", UserName: "enc:other" } }),
                userRow("W-9", uuidFor(9), { data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-9", UserName: "enc:dist.1" } }),
            ]);
            givenWorkerRows([workerRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe(uuidFor(1));
        });

        it("ignores a userName that cannot be decrypted", async () => {
            givenUserRows([userRow("W-5", uuidFor(5), { data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-5", UserName: "enc:boom" } })]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "unknown" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe("");
        });

        it("does not match a deleted user row by userName", async () => {
            givenUserRows([userRow("W-5", uuidFor(5), { isDeleted: true, data: { HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-5", UserName: "enc:dist.1" } })]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "unknown" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe("");
        });

        it("asks egov-user by userName for distributors the campaign data could not resolve", async () => {
            givenUserRows([]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "individual-9" })]);
            mockFetchUuidsByUserName.mockResolvedValue(new Map([["dist.1", uuidFor(77)]]));

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe(uuidFor(77));
            expect(mockFetchUuidsByUserName).toHaveBeenCalledWith(["dist.1"], "bo", REQUEST_INFO);
        });

        it("does not call egov-user when every distributor already has a uuid", async () => {
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-1" })]);

            await searchDistributorsByDhService(buildRequest());

            expect(mockFetchUuidsByUserName).not.toHaveBeenCalled();
        });

        it("keeps the uuid empty when egov-user does not know the userName", async () => {
            givenUserRows([]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "individual-9" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe("");
        });

        it("never asks egov-user for a distributor without a userName", async () => {
            givenUserRows([]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "individual-9", UserName: "" })]);

            await searchDistributorsByDhService(buildRequest());

            expect(mockFetchUuidsByUserName).not.toHaveBeenCalled();
        });

        it("never falls back to the workerId when no user row exists, even if it looks like a uuid", async () => {
            givenUserRows([]);
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: uuidFor(42) })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0]).toEqual({ uuid: "", userName: "dist.1", name: "Dist 1" });
        });

        it("returns an empty uuid when the user row has not been processed yet", async () => {
            givenUserRows([userRow("W-1", null)]);
            givenWorkerRows([workerRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe("");
        });

        it("ignores deleted user rows", async () => {
            givenUserRows([userRow("W-1", uuidFor(1), { isDeleted: true })]);
            givenWorkerRows([workerRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0].uuid).toBe("");
        });

        it("returns 500 distributors in one page", async () => {
            const rows = Array.from({ length: 500 }, (_, i) => workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: uuidFor(1000 + i), UserName: `d${i}`, HCM_ADMIN_CONSOLE_USER_NAME: `D ${i}` }));
            givenWorkerRows(rows);

            const result = await searchDistributorsByDhService(buildRequest(undefined, { limit: 10, offset: 0 }));

            expect(result.DHDistributors[0].distributors).toHaveLength(500);
        });
    });

    describe("campaign without attendance registers", () => {
        function distributorUserRow(dataOverrides: Record<string, unknown> = {}, uuid: string | null = uuidFor(1), extra: Record<string, unknown> = {}) {
            return {
                data: {
                    HCM_ADMIN_CONSOLE_USER_ROLE: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-1",
                    UserName: "enc:dist.1",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Dist 1",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_ONE,
                    ...dataOverrides,
                },
                uniqueIdAfterProcess: uuid,
                isDeleted: false,
                ...extra,
            };
        }

        beforeEach(() => givenWorkerRows([]));

        it("lists distributors from the processed user rows when the generator returns no workers", async () => {
            givenUserRows([distributorUserRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(1);
            expect(result.DHDistributors[0]).toEqual({
                dhCode: DH_ONE,
                dhName: DH_ONE,
                distributors: [{ uuid: uuidFor(1), userName: "dist.1", name: "Dist 1" }],
            });
        });

        it("lists distributors when the generator returns an empty sheet map", async () => {
            mockGenerate.mockResolvedValue({});
            givenUserRows([distributorUserRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(1);
        });

        it("groups by DH, sorts by DH code and ignores non-distributors, deleted rows and rows without a DH code", async () => {
            givenUserRows([
                distributorUserRow({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_TWO, HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", UserName: "enc:dist.2" }, uuidFor(2)),
                distributorUserRow(),
                distributorUserRow({ HCM_ADMIN_CONSOLE_USER_ROLE: "REGISTRAR", HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-9" }, uuidFor(9)),
                distributorUserRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-8" }, uuidFor(8), { isDeleted: true }),
                distributorUserRow({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE: "", HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-7" }, uuidFor(7)),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO, DH_ONE]);
            expect(result.DHDistributors.flatMap((g: any) => g.distributors.map((d: any) => d.uuid))).toEqual([uuidFor(2), uuidFor(1)]);
        });

        it("applies the localityCodes filter", async () => {
            givenUserRows([
                distributorUserRow(),
                distributorUserRow({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_TWO, HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2" }, uuidFor(2)),
            ]);

            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [DH_TWO] })
            );

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO]);
        });

        it("falls back to UserService Uuids and then to egov-user for the uuid", async () => {
            givenUserRows([
                distributorUserRow({ "UserService Uuids": uuidFor(7) }, null),
                distributorUserRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", UserName: "enc:dist.2" }, null),
            ]);
            mockFetchUuidsByUserName.mockResolvedValue(new Map([["dist.2", uuidFor(22)]]));

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors.map((d: any) => d.uuid)).toEqual([uuidFor(7), uuidFor(22)]);
            expect(mockFetchUuidsByUserName).toHaveBeenCalledWith(["dist.2"], "bo", REQUEST_INFO);
        });

        it("returns an empty result when the campaign has no distributor user rows", async () => {
            givenUserRows([distributorUserRow({ HCM_ADMIN_CONSOLE_USER_ROLE: "REGISTRAR" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("tolerates a userName that cannot be decrypted", async () => {
            givenUserRows([distributorUserRow({ UserName: "enc:boom" })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors[0]).toEqual({ uuid: uuidFor(1), userName: "", name: "Dist 1" });
        });
    });

    describe("campaign with attendance registers keeps the generator result", () => {
        const unmappedDistributor = (dataOverrides: Record<string, unknown> = {}, uuid: string | null = uuidFor(50)) => ({
            data: {
                HCM_ADMIN_CONSOLE_USER_ROLE: "DISTRIBUTOR",
                HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-50",
                HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_TWO,
                UserName: "enc:other",
                HCM_ADMIN_CONSOLE_USER_NAME: "Other",
                ...dataOverrides,
            },
            uniqueIdAfterProcess: uuid,
            isDeleted: false,
        });

        it("keeps the generator dhName instead of the code", async () => {
            givenWorkerRows([workerRow()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].dhName).toBe("Gidan Lawan");
        });

        it("returns the same entries as before when every distributor is already listed", async () => {
            givenWorkerRows([workerRow(), workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", UserName: "dist.2", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 2", HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: DH_TWO, HCM_ADMIN_CONSOLE_BOUNDARY_NAME: "Ali Brickler" })]);
            givenUserRows([
                { ...unmappedDistributor({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-1", UserName: "enc:dist.1", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 1", HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_ONE }, uuidFor(1)) },
                { ...unmappedDistributor({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", UserName: "enc:dist.2", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 2" }, uuidFor(2)) },
            ]);
            const before = await searchDistributorsByDhService(buildRequest());

            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))]);
            const generatorOnly = await searchDistributorsByDhService(buildRequest());

            expect(before).toEqual(generatorOnly);
        });

        it("adds a distributor the mapping did not cover, leaving the mapped entry unchanged", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1)), unmappedDistributor()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(2);
            const gidan: any = result.DHDistributors.find((g: any) => g.dhCode === DH_ONE);
            expect(gidan).toEqual({ dhCode: DH_ONE, dhName: "Gidan Lawan", distributors: [{ uuid: uuidFor(1), userName: "dist.1", name: "Dist 1" }] });
            const added: any = result.DHDistributors.find((g: any) => g.dhCode === DH_TWO);
            expect(added.distributors).toEqual([{ uuid: uuidFor(50), userName: "other", name: "Other" }]);
        });

        it("appends an unmapped distributor after the mapped ones in the same DH", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1)), unmappedDistributor({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_ONE })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors).toHaveLength(1);
            expect(result.DHDistributors[0].distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1), uuidFor(50)]);
            expect(result.DHDistributors[0].dhName).toBe("Gidan Lawan");
        });

        it("does not list a mapped user again under the DH from the user row", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([
                userRow("W-1", uuidFor(1)),
                unmappedDistributor({ HCM_ADMIN_CONSOLE_BOUNDARY_CODE: DH_TWO, UserName: "enc:dist.1", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 1" }, uuidFor(1)),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
            expect(result.DHDistributors[0].distributors).toHaveLength(1);
        });

        it("does not list a mapped user again when the user row has no uuid but the same userName", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1)), unmappedDistributor({ UserName: "enc:dist.1" }, null)]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(1);
        });

        it("applies the localityCodes filter to the added distributors", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1)), unmappedDistributor()]);

            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [DH_ONE] })
            );

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
        });

        it("adds each unmapped distributor only once", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1)), unmappedDistributor(), unmappedDistributor()]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(2);
            const added: any = result.DHDistributors.find((g: any) => g.dhCode === DH_TWO);
            expect(added.distributors).toHaveLength(1);
        });
    });

    describe("source combinations (generator list vs direct user rows)", () => {
        const directDistributor = (workerId: string, uuid: string | null, userName: string, dh: string, extra: Record<string, unknown> = {}) => ({
            data: {
                HCM_ADMIN_CONSOLE_USER_ROLE: "DISTRIBUTOR",
                HCM_ADMIN_CONSOLE_USER_WORKER_ID: workerId,
                UserName: `enc:${userName}`,
                HCM_ADMIN_CONSOLE_USER_NAME: userName,
                HCM_ADMIN_CONSOLE_BOUNDARY_CODE: dh,
            },
            uniqueIdAfterProcess: uuid,
            isDeleted: false,
            ...extra,
        });
        const generatorOnlyResult = async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1))]);
            return searchDistributorsByDhService(buildRequest());
        };

        it("generator has a list, user rows are an empty array: generator list is returned unchanged", async () => {
            const expected = await generatorOnlyResult();
            givenUserRows([]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.TotalCount).toBe(1);
            expect(result.DHDistributors[0].dhName).toBe(expected.DHDistributors[0].dhName);
            expect(result.DHDistributors[0].distributors[0]).toMatchObject({ userName: "dist.1", name: "Dist 1" });
        });

        it("generator has a list, user rows are undefined: the requester cannot be found so the list is empty and nothing throws", async () => {
            givenWorkerRows([workerRow()]);
            mockUserRows.mockResolvedValue(undefined);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("generator has a list, user rows hold only non-distributors: generator list is returned unchanged", async () => {
            const expected = await generatorOnlyResult();
            const registrar = directDistributor("W-9", uuidFor(9), "reg.9", DH_TWO);
            registrar.data.HCM_ADMIN_CONSOLE_USER_ROLE = "REGISTRAR";
            givenUserRows([userRow("W-1", uuidFor(1)), registrar]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toEqual(expected);
        });

        it("generator has a list, direct rows are all deleted: generator list is returned unchanged", async () => {
            const expected = await generatorOnlyResult();
            givenUserRows([userRow("W-1", uuidFor(1)), directDistributor("W-9", uuidFor(9), "dist.9", DH_TWO, { isDeleted: true })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toEqual(expected);
        });

        it("generator has a list, direct rows have no DH code: generator list is returned unchanged", async () => {
            const expected = await generatorOnlyResult();
            givenUserRows([userRow("W-1", uuidFor(1)), directDistributor("W-9", uuidFor(9), "dist.9", "")]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toEqual(expected);
        });

        it("generator is empty and user rows are empty: result is empty", async () => {
            givenWorkerRows([]);
            givenUserRows([]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("generator is empty, direct rows have distributors: they are listed (campaign without registers)", async () => {
            givenWorkerRows([]);
            givenUserRows([directDistributor("W-1", uuidFor(1), "dist.1", DH_ONE), directDistributor("W-2", uuidFor(2), "dist.2", DH_TWO)]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO, DH_ONE]);
            expect(result.TotalCount).toBe(2);
        });

        it("both have entries, partly overlapping: overlap once, missing ones added, mapped ones untouched", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([
                userRow("W-1", uuidFor(1)),
                directDistributor("W-1", uuidFor(1), "dist.1", DH_ONE),
                directDistributor("W-2", uuidFor(2), "dist.2", DH_ONE),
                directDistributor("W-3", uuidFor(3), "dist.3", DH_TWO),
            ]);

            const result = await searchDistributorsByDhService(buildRequest());

            const one: any = result.DHDistributors.find((g: any) => g.dhCode === DH_ONE);
            const two: any = result.DHDistributors.find((g: any) => g.dhCode === DH_TWO);
            expect(one.dhName).toBe("Gidan Lawan");
            expect(one.distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1), uuidFor(2)]);
            expect(two.distributors.map((d: any) => d.uuid)).toEqual([uuidFor(3)]);
        });

        it("a direct user without uuid is still added and then resolved through egov-user", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([userRow("W-1", uuidFor(1)), directDistributor("W-2", null, "dist.2", DH_TWO)]);
            mockFetchUuidsByUserName.mockResolvedValue(new Map([["dist.2", uuidFor(22)]]));

            const result = await searchDistributorsByDhService(buildRequest());

            const two: any = result.DHDistributors.find((g: any) => g.dhCode === DH_TWO);
            expect(two.distributors).toEqual([{ uuid: uuidFor(22), userName: "dist.2", name: "dist.2" }]);
        });

        it("pagination counts the merged DH groups", async () => {
            givenWorkerRows([workerRow()]);
            givenUserRows([
                userRow("W-1", uuidFor(1)),
                directDistributor("W-2", uuidFor(2), "dist.2", DH_TWO),
                directDistributor("W-3", uuidFor(3), "dist.3", "DH_Z"),
            ]);

            const result = await searchDistributorsByDhService(buildRequest(undefined, { limit: 2, offset: 1 }));

            expect(result.TotalCount).toBe(3);
            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO, DH_ONE]);
        });

        it("a generator failure is not hidden by the direct rows", async () => {
            mockGenerate.mockRejectedValue(new Error("attendance service down"));
            givenUserRows([directDistributor("W-1", uuidFor(1), "dist.1", DH_ONE)]);

            await expect(searchDistributorsByDhService(buildRequest())).rejects.toThrow("attendance service down");
        });

        it("a failure reading the user rows is not swallowed", async () => {
            givenWorkerRows([workerRow()]);
            mockUserRows.mockRejectedValue(new Error("db down"));

            await expect(searchDistributorsByDhService(buildRequest())).rejects.toThrow("db down");
        });
    });

    describe("requester DH restriction", () => {
        const OTHER_UUID = "e0000000-0000-4000-8000-0000000000ff";
        const distributorAt = (workerId: string, uuid: string, userName: string, dh: string) => ({
            data: {
                HCM_ADMIN_CONSOLE_USER_ROLE: "DISTRIBUTOR",
                HCM_ADMIN_CONSOLE_USER_WORKER_ID: workerId,
                UserName: `enc:${userName}`,
                HCM_ADMIN_CONSOLE_USER_NAME: userName,
                HCM_ADMIN_CONSOLE_BOUNDARY_CODE: dh,
            },
            uniqueIdAfterProcess: uuid,
            isDeleted: false,
        });
        const requestAs = (uuid: string | undefined, searchCriteria?: any, pagination?: any) =>
            buildRequest(searchCriteria, pagination, uuid === undefined ? { msgId: "m1" } : { msgId: "m1", userInfo: { uuid } });

        beforeEach(() => {
            givenWorkerRows([
                workerRow(),
                workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-2", UserName: "dist.2", HCM_ADMIN_CONSOLE_USER_NAME: "Dist 2", HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: DH_TWO, HCM_ADMIN_CONSOLE_BOUNDARY_NAME: "Ali Brickler" }),
            ]);
        });

        it("a DH user sees only the distributors under their own DH", async () => {
            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))], [DH_ONE]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
            expect(result.DHDistributors[0].distributors).toEqual([{ uuid: uuidFor(1), userName: "dist.1", name: "Dist 1" }]);
            expect(result.TotalCount).toBe(1);
        });

        it("returns an empty list when the request has no requester uuid", async () => {
            givenUserRows([userRow("W-1", uuidFor(1))]);

            const result = await searchDistributorsByDhService(requestAs(undefined));

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("returns an empty list when the requester is not a campaign user", async () => {
            givenUserRows([userRow("W-1", uuidFor(1))]);

            const result = await searchDistributorsByDhService(requestAs(OTHER_UUID));

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("returns an empty list when the requester's user row is deleted", async () => {
            mockUserRows.mockResolvedValue([userRow("W-1", uuidFor(1)), ...requesterRows([DH_ONE], { isDeleted: true })]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("returns an empty list when the requester's row has no DH code", async () => {
            mockUserRows.mockResolvedValue([userRow("W-1", uuidFor(1)), ...requesterRows([""])]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("finds the requester through the UserService Uuids field", async () => {
            const requester = { ...requesterRows([DH_ONE])[0], uniqueIdAfterProcess: null };
            requester.data = { ...requester.data, "UserService Uuids": REQUESTER_UUID } as typeof requester.data;
            mockUserRows.mockResolvedValue([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2)), requester]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
        });

        it("uses the _MANDATORY boundary code column of the requester when present", async () => {
            const requester = requesterRows([DH_TWO])[0];
            requester.data = { ...requester.data, HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: DH_ONE } as typeof requester.data;
            mockUserRows.mockResolvedValue([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2)), requester]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
        });

        it("a requester with several DH rows sees all of those DHs", async () => {
            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))], [DH_ONE, DH_TWO]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO, DH_ONE]);
        });

        it("localityCodes inside the requester's DH narrow the list further", async () => {
            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))], [DH_ONE, DH_TWO]);

            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [DH_TWO] })
            );

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_TWO]);
        });

        it("localityCodes outside the requester's DH return an empty list, never another DH", async () => {
            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))], [DH_ONE]);

            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [DH_TWO] })
            );

            expect(result).toMatchObject({ DHDistributors: [], TotalCount: 0 });
        });

        it("localityCodes with the requester's DH and another DH return only the requester's DH", async () => {
            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))], [DH_ONE]);

            const result = await searchDistributorsByDhService(
                buildRequest({ tenantId: "bo", campaignNumber: "CMP-1", localityCodes: [DH_ONE, DH_TWO] })
            );

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
        });

        it("does not generate the attendance sheet when the requester has no DH", async () => {
            givenUserRows([userRow("W-1", uuidFor(1))]);

            await searchDistributorsByDhService(requestAs(OTHER_UUID));

            expect(mockGenerate).not.toHaveBeenCalled();
        });

        it("does not call egov-user when the requester has no DH", async () => {
            givenWorkerRows([workerRow({ HCM_ADMIN_CONSOLE_USER_WORKER_ID: "individual-9" })]);
            givenUserRows([]);

            await searchDistributorsByDhService(requestAs(OTHER_UUID));

            expect(mockFetchUuidsByUserName).not.toHaveBeenCalled();
        });

        it("restricts the distributors added from user rows to the requester's DH as well", async () => {
            givenWorkerRows([]);
            givenUserRows(
                [distributorAt("W-1", uuidFor(1), "dist.1", DH_ONE), distributorAt("W-2", uuidFor(2), "dist.2", DH_TWO)],
                [DH_ONE]
            );

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors.map((g: any) => g.dhCode)).toEqual([DH_ONE]);
            expect(result.DHDistributors[0].distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1)]);
        });

        it("adds an unmapped distributor of the requester's DH but not one of another DH", async () => {
            givenUserRows(
                [userRow("W-1", uuidFor(1)), distributorAt("W-9", uuidFor(9), "dist.9", DH_ONE), distributorAt("W-8", uuidFor(8), "dist.8", DH_TWO)],
                [DH_ONE]
            );

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors).toHaveLength(1);
            expect(result.DHDistributors[0].distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1), uuidFor(9)]);
        });

        it("does not list the requester as a distributor when the requester is not one", async () => {
            givenWorkerRows([]);
            givenUserRows([distributorAt("W-1", uuidFor(1), "dist.1", DH_ONE)], [DH_ONE]);

            const result = await searchDistributorsByDhService(buildRequest());

            expect(result.DHDistributors[0].distributors.map((d: any) => d.uuid)).toEqual([uuidFor(1)]);
        });

        it("pagination and TotalCount describe the restricted list", async () => {
            givenUserRows([userRow("W-1", uuidFor(1)), userRow("W-2", uuidFor(2))], [DH_ONE]);

            const result = await searchDistributorsByDhService(buildRequest(undefined, { limit: 5, offset: 0 }));

            expect(result.TotalCount).toBe(1);
            expect(result.Pagination).toEqual({ offset: 0, limit: 5 });
        });

        it("an empty result still validates the pagination limit", async () => {
            givenUserRows([]);

            await expect(searchDistributorsByDhService(requestAs(OTHER_UUID, undefined, { limit: 1001, offset: 0 })))
                .rejects.toMatchObject({ status: 400, description: "limit must be between 1 and 1000" });
        });

        it("still rejects an unknown campaign before looking at the requester", async () => {
            mockSearchCampaign.mockResolvedValue({ CampaignDetails: [] });

            await expect(searchDistributorsByDhService(buildRequest())).rejects.toMatchObject({ code: "CAMPAIGN_NOT_FOUND" });
        });
    });
});
