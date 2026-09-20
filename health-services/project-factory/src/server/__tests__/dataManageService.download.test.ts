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
    validateDownloadRequest: jest.fn().mockResolvedValue(undefined),
    validateSearchRequest: jest.fn(),
}));

jest.mock("../validators/genericValidator", () => ({
    validateGenerateRequest: jest.fn(),
}));

jest.mock("../utils/localisationUtils", () => ({
    getLocaleFromRequestInfo: jest.fn().mockReturnValue("en_BEDNET"),
    getLocalisationModuleName: jest.fn().mockReturnValue("hcm-admin-schemas"),
}));

jest.mock("../utils/boundaryUtils", () => ({
    getBoundaryTabName: jest.fn().mockReturnValue("HCM_ADMIN_CONSOLE_BOUNDARY_DATA"),
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
    callGenerate: jest.fn().mockResolvedValue(undefined),
}));

jest.mock("../service/sheetManageService", () => ({
    generateDataService: jest.fn(),
}));

import { downloadDataService } from "../service/dataManageService";
import { searchGeneratedResources, searchAllGeneratedResources } from "../utils/genericUtils";
import { validateDownloadRequest } from "../validators/campaignValidators";
import { generateDataService as generateTemplateDataService } from "../service/sheetManageService";
import { callGenerate } from "../utils/generateUtils";

const mockSearchGeneratedResources = searchGeneratedResources as jest.Mock;
const mockSearchAllGeneratedResources = searchAllGeneratedResources as jest.Mock;
const mockValidateDownloadRequest = validateDownloadRequest as jest.Mock;
const mockGenerateTemplateDataService = generateTemplateDataService as jest.Mock;
const mockCallGenerate = callGenerate as jest.Mock;

const OLD_RESOURCE = {
    id: "old-gen-id",
    status: "completed",
    type: "attendanceRegisterUserBulkMapping",
    fileStoreid: "old-file",
    additionalDetails: {},
    auditDetails: { createdTime: 1, lastModifiedTime: 1, createdBy: "u", lastModifiedBy: "u" },
};

const NEW_RESOURCE = {
    id: "new-gen-id",
    status: "inprogress",
    type: "attendanceRegisterUserBulkMapping",
    fileStoreid: null,
    additionalDetails: {},
    auditDetails: { createdTime: Date.now(), lastModifiedTime: Date.now(), createdBy: "u", lastModifiedBy: "u" },
};

const COMPLETED_NEW_RESOURCE = {
    ...NEW_RESOURCE,
    status: "completed",
    fileStoreid: "new-file",
};

function buildRequest(overrides: Record<string, any> = {}) {
    return {
        query: {
            type: "attendanceRegisterUserBulkMapping",
            tenantId: "bednet",
            hierarchyType: "ADMIN",
            campaignId: "cmp-1",
            localityCode: "loc-1",
            ...overrides.query,
        },
        body: {
            RequestInfo: {
                apiId: "Rainmaker",
                msgId: "abc|en_BEDNET",
                userInfo: {
                    uuid: "user-1",
                },
            },
            ...overrides.body,
        },
    } as any;
}

describe("downloadDataService always-fresh behavior", () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockValidateDownloadRequest.mockResolvedValue(undefined);
        mockConfig.generatedResource.reuseWindowMs = 30000;
        mockSearchAllGeneratedResources.mockResolvedValue([]);
    });

    it("always regenerates bulk template when id is not provided", async () => {
        mockSearchAllGeneratedResources.mockResolvedValue([OLD_RESOURCE]);
        mockSearchGeneratedResources.mockResolvedValue([COMPLETED_NEW_RESOURCE]);
        mockGenerateTemplateDataService.mockResolvedValue(NEW_RESOURCE);

        const request = buildRequest();
        const result = await downloadDataService(request);

        expect(mockGenerateTemplateDataService).toHaveBeenCalledWith(
            {
                type: "attendanceRegisterUserBulkMapping",
                tenantId: "bednet",
                hierarchyType: "ADMIN",
                campaignId: "cmp-1",
                localityCode: "loc-1",
            },
            "user-1",
            "en_BEDNET",
            request.body.RequestInfo
        );
        expect(result).toEqual([COMPLETED_NEW_RESOURCE]);
        expect(mockCallGenerate).not.toHaveBeenCalled();
    });

    it("does not regenerate when explicit generated resource id is provided", async () => {
        mockSearchGeneratedResources.mockResolvedValue([OLD_RESOURCE]);

        const result = await downloadDataService(
            buildRequest({ query: { id: "old-gen-id" } })
        );

        expect(result).toEqual([OLD_RESOURCE]);
        expect(mockGenerateTemplateDataService).not.toHaveBeenCalled();
        expect(mockCallGenerate).not.toHaveBeenCalled();
    });

    it("uses legacy generator for types not supported by sheet-manage generation", async () => {
        const old = { ...OLD_RESOURCE, type: "facilityWithBoundary" };
        const newer = { ...COMPLETED_NEW_RESOURCE, type: "facilityWithBoundary", id: "new-facility-gen-id" };
        mockSearchAllGeneratedResources.mockResolvedValue([old]);
        mockSearchGeneratedResources.mockResolvedValue([newer]);

        const result = await downloadDataService(
            buildRequest({ query: { type: "facilityWithBoundary" } })
        );

        expect(mockCallGenerate).toHaveBeenCalledTimes(1);
        const generatedRequest = mockCallGenerate.mock.calls[0][0];
        expect(generatedRequest.query.forceUpdate).toBe("true");
        expect(result).toEqual([newer]);
        expect(mockGenerateTemplateDataService).not.toHaveBeenCalled();
    });

    it("reuses active in-progress resource instead of creating another one", async () => {
        const inProgressExisting = {
            ...NEW_RESOURCE,
            id: "existing-inprogress-id",
            fileStoreid: null,
            additionalDetails: { localityCode: "loc-1" },
            auditDetails: { createdTime: Date.now(), lastModifiedTime: Date.now(), createdBy: "u", lastModifiedBy: "u" },
        };
        const completedExisting = {
            ...inProgressExisting,
            status: "completed",
            fileStoreid: "existing-completed-file",
        };
        mockSearchAllGeneratedResources.mockResolvedValue([inProgressExisting]);
        mockSearchGeneratedResources.mockResolvedValue([completedExisting]);

        const result = await downloadDataService(buildRequest());

        expect(result).toEqual([completedExisting]);
        expect(mockGenerateTemplateDataService).not.toHaveBeenCalled();
        expect(mockCallGenerate).not.toHaveBeenCalled();
    });

    it("does not reuse an in-progress generation belonging to a different locality", async () => {
        const otherLocalityInProgress = {
            ...NEW_RESOURCE,
            id: "other-locality-inprogress-id",
            fileStoreid: null,
            additionalDetails: { localityCode: "loc-2" },
            auditDetails: { createdTime: Date.now(), lastModifiedTime: Date.now(), createdBy: "u", lastModifiedBy: "u" },
        };
        mockSearchAllGeneratedResources.mockResolvedValue([otherLocalityInProgress]);
        mockSearchGeneratedResources.mockResolvedValue([COMPLETED_NEW_RESOURCE]);
        mockGenerateTemplateDataService.mockResolvedValue(NEW_RESOURCE);

        const result = await downloadDataService(buildRequest());

        expect(mockGenerateTemplateDataService).toHaveBeenCalledWith(
            expect.objectContaining({ localityCode: "loc-1" }),
            "user-1",
            "en_BEDNET",
            expect.anything()
        );
        expect(result).not.toEqual([otherLocalityInProgress]);
    });

    it("does not reuse an in-progress generation that records no locality when one is requested", async () => {
        const unknownLocalityInProgress = {
            ...NEW_RESOURCE,
            id: "unknown-locality-inprogress-id",
            fileStoreid: null,
            additionalDetails: {},
            auditDetails: { createdTime: Date.now(), lastModifiedTime: Date.now(), createdBy: "u", lastModifiedBy: "u" },
        };
        mockSearchAllGeneratedResources.mockResolvedValue([unknownLocalityInProgress]);
        mockSearchGeneratedResources.mockResolvedValue([COMPLETED_NEW_RESOURCE]);
        mockGenerateTemplateDataService.mockResolvedValue(NEW_RESOURCE);

        await downloadDataService(buildRequest());

        expect(mockGenerateTemplateDataService).toHaveBeenCalled();
    });

    it("does not reuse a locality-scoped generation for a campaign-wide request", async () => {
        const localityInProgress = {
            ...NEW_RESOURCE,
            id: "locality-inprogress-id",
            fileStoreid: null,
            additionalDetails: { localityCode: "loc-1" },
            auditDetails: { createdTime: Date.now(), lastModifiedTime: Date.now(), createdBy: "u", lastModifiedBy: "u" },
        };
        mockSearchAllGeneratedResources.mockResolvedValue([localityInProgress]);
        mockSearchGeneratedResources.mockResolvedValue([COMPLETED_NEW_RESOURCE]);
        mockGenerateTemplateDataService.mockResolvedValue(NEW_RESOURCE);

        await downloadDataService(buildRequest({ query: { localityCode: undefined } }));

        expect(mockGenerateTemplateDataService).toHaveBeenCalledWith(
            expect.not.objectContaining({ localityCode: expect.anything() }),
            "user-1",
            "en_BEDNET",
            expect.anything()
        );
    });

    it("reuses a campaign-wide in-progress generation for a campaign-wide request", async () => {
        const campaignWideInProgress = {
            ...NEW_RESOURCE,
            id: "campaign-wide-inprogress-id",
            fileStoreid: null,
            additionalDetails: {},
            auditDetails: { createdTime: Date.now(), lastModifiedTime: Date.now(), createdBy: "u", lastModifiedBy: "u" },
        };
        const completedExisting = {
            ...campaignWideInProgress,
            status: "completed",
            fileStoreid: "campaign-wide-file",
        };
        mockSearchAllGeneratedResources.mockResolvedValue([campaignWideInProgress]);
        mockSearchGeneratedResources.mockResolvedValue([completedExisting]);

        const result = await downloadDataService(buildRequest({ query: { localityCode: undefined } }));

        expect(result).toEqual([completedExisting]);
        expect(mockGenerateTemplateDataService).not.toHaveBeenCalled();
        expect(mockCallGenerate).not.toHaveBeenCalled();
    });

    it("regenerates when latest in-progress resource is stale", async () => {
        const staleInProgress = {
            ...NEW_RESOURCE,
            id: "stale-inprogress-id",
            additionalDetails: { localityCode: "loc-1" },
            auditDetails: { createdTime: 1, lastModifiedTime: 1, createdBy: "u", lastModifiedBy: "u" },
        };
        mockSearchAllGeneratedResources.mockResolvedValue([staleInProgress]);
        mockSearchGeneratedResources.mockResolvedValue([COMPLETED_NEW_RESOURCE]);
        mockGenerateTemplateDataService.mockResolvedValue(NEW_RESOURCE);

        const result = await downloadDataService(buildRequest());

        expect(mockGenerateTemplateDataService).toHaveBeenCalledTimes(1);
        expect(result).toEqual([COMPLETED_NEW_RESOURCE]);
    });
});
