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

jest.mock("../config/index", () => ({
    default: {
        cacheTime: 300,
        cacheValues: { resetCache: false },
    },
    __esModule: true,
}));

jest.mock("../utils/generateUtils", () => ({
    callGenerate: jest.fn().mockResolvedValue(undefined),
}));

jest.mock("../service/sheetManageService", () => ({
    generateDataService: jest.fn(),
}));

import { downloadDataService } from "../service/dataManageService";
import { searchGeneratedResources } from "../utils/genericUtils";
import { validateDownloadRequest } from "../validators/campaignValidators";
import { generateDataService as generateTemplateDataService } from "../service/sheetManageService";
import { callGenerate } from "../utils/generateUtils";

const mockSearchGeneratedResources = searchGeneratedResources as jest.Mock;
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
    });

    it("always regenerates bulk template when id is not provided", async () => {
        mockSearchGeneratedResources.mockResolvedValue([OLD_RESOURCE]);
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
        expect(result).toEqual([NEW_RESOURCE]);
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
        const newer = { ...NEW_RESOURCE, type: "facilityWithBoundary", id: "new-facility-gen-id" };
        mockSearchGeneratedResources.mockResolvedValueOnce([old]).mockResolvedValueOnce([newer]);

        const result = await downloadDataService(
            buildRequest({ query: { type: "facilityWithBoundary" } })
        );

        expect(mockCallGenerate).toHaveBeenCalledTimes(1);
        const generatedRequest = mockCallGenerate.mock.calls[0][0];
        expect(generatedRequest.query.forceUpdate).toBe("true");
        expect(result).toEqual([newer]);
        expect(mockGenerateTemplateDataService).not.toHaveBeenCalled();
    });
});
