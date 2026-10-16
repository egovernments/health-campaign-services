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

function givenUserRows(rows: unknown[]): void {
    mockUserRows.mockResolvedValue(rows);
}

function buildRequest(searchCriteria: any = { tenantId: "bo", campaignNumber: "CMP-1" }, pagination?: any) {
    return {
        body: {
            RequestInfo: { msgId: "m1" },
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
            expect(mockFetchUuidsByUserName).toHaveBeenCalledWith(["dist.1"], "bo", { msgId: "m1" });
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
});
