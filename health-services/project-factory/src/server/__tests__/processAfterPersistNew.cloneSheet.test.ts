jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn(),
}));
jest.mock('../utils/redisUtils', () => ({
    redis: {},
    checkRedisConnection: jest.fn().mockResolvedValue(true),
    getCache: jest.fn(),
    setCache: jest.fn(),
    deleteCache: jest.fn(),
}));
jest.mock('../config/dbPoolConfig', () => ({
    __esModule: true,
    default: { query: jest.fn() },
}));
jest.mock('../utils/request', () => ({
    httpRequest: jest.fn().mockResolvedValue({}),
    defaultheader: {},
}));
jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/db', () => ({
    executeQuery: jest.fn().mockResolvedValue({ rows: [] }),
    getTableName: jest.fn((t: string) => t),
}));
jest.mock('../api/campaignApis', () => ({
    getCampaignSearchResponse: jest.fn().mockResolvedValue({}),
    getHierarchy: jest.fn().mockResolvedValue([]),
}));
jest.mock('../api/genericApis', () => ({
    getCampaignNumber: jest.fn(),
    createAndUploadFile: jest.fn(),
    getTargetSheetDataAfterCode: jest.fn(),
    callMdmsTypeSchema: jest.fn(),
    getSheetDataFromWorksheet: jest.fn(),
    getTargetWorkbook: jest.fn(),
    getSheetData: jest.fn(),
    searchMDMS: jest.fn().mockResolvedValue({}),
}));
jest.mock('../api/coreApis', () => ({
    createMdmsData: jest.fn(),
    searchBoundaryRelationshipData: jest.fn(),
    searchMDMSDataViaV2Api: jest.fn(),
}));
jest.mock('../api/serviceRequestApis', () => ({
    createServiceDefinition: jest.fn(),
    searchServiceDefinitions: jest.fn(),
}));
jest.mock('../utils/genericUtils', () => ({
    addDataToSheet: jest.fn(),
    createBoundaryDataMainSheet: jest.fn(),
    createReadMeSheet: jest.fn(),
    getConfigurableColumnHeadersFromSchemaForTargetSheet: jest.fn(),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    getLocalizedHeaders: jest.fn(),
    getLocalizedMessagesHandler: jest.fn(),
    getMdmsDataBasedOnCampaignType: jest.fn(),
    getRelatedDataWithCampaign: jest.fn().mockResolvedValue([]),
    prepareProcessesInDb: jest.fn().mockResolvedValue(undefined),
    replicateRequest: jest.fn(),
    searchAllGeneratedResources: jest.fn().mockResolvedValue([]),
    throwError: jest.fn((_m: string, status: number, code: string, description: string) => {
        throw Object.assign(new Error(code), { status, code, description });
    }),
}));
jest.mock('../utils/resourceDetailsUtils', () => ({
    searchResourceDetailsFromDB: jest.fn().mockResolvedValue([]),
    toResourceDetailsResponse: jest.fn(),
}));
jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
    updateProjectTypeCampaignService: jest.fn(),
}));
jest.mock('../service/sheetManageService', () => ({
    generateDataService: jest.fn(),
}));
jest.mock('../validators/campaignValidators', () => ({
    validateBoundaryOfResouces: jest.fn(),
}));
jest.mock('../utils/localisationUtils', () => ({
    getLocaleFromRequest: jest.fn().mockReturnValue('en_IN'),
    getLocaleFromRequestInfo: jest.fn().mockReturnValue('en_IN'),
}));
jest.mock('../controllers/localisationController/localisation.controller', () => ({
    __esModule: true,
    default: { getInstance: jest.fn(() => ({ getLocalisedData: jest.fn().mockResolvedValue([]) })) },
}));
jest.mock('../utils/mailUtils', () => ({
    triggerUserCredentialEmailFlow: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../config/resourceTypeRegistry', () => ({
    getResourceConfigsByPhase: jest.fn().mockReturnValue([]),
    getPhase2Types: jest.fn().mockReturnValue([]),
    hasDependenciesMet: jest.fn().mockReturnValue(true),
}));

import { processAfterPersistNew } from '../utils/campaignUtils';
import { httpRequest } from '../utils/request';
import { prepareProcessesInDb } from '../utils/genericUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';

const httpRequestMock = httpRequest as jest.Mock;
const prepareProcessesMock = prepareProcessesInDb as jest.Mock;
const searchCampaignMock = searchProjectTypeCampaignService as jest.Mock;

describe('processAfterPersistNew clone sheet handling (#2386)', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({
            CampaignDetails: [
                {
                    campaignNumber: 'CMP-SRC-001',
                    resources: [
                        { type: 'unified-console-resources', filestoreId: 'SRC-SHEET-999' },
                    ],
                },
            ],
        });
    });

    it('does not borrow source unified sheet when a cloned campaign has no unified-console-resources', async () => {
        const request: any = {
            body: {
                RequestInfo: { userInfo: { uuid: 'user-1' } },
                CampaignDetails: {
                    id: 'clone-id-1',
                    tenantId: 'qa',
                    campaignNumber: 'CMP-CLONE-001',
                    action: 'create',
                    parentId: null,
                    additionalDetails: { cloneFrom: 'CMP-SRC-001' },
                    resources: [],
                },
            },
        };

        await processAfterPersistNew(request, 'create');

        expect(searchCampaignMock).not.toHaveBeenCalled();
        expect(request.body.CampaignDetails.resources).toEqual([]);
        expect(httpRequestMock).not.toHaveBeenCalled();
        expect(prepareProcessesMock).not.toHaveBeenCalled();
    });

    it('processes a cloned campaign via unified template flow only when it carries its own unified sheet', async () => {
        const request: any = {
            body: {
                RequestInfo: { userInfo: { uuid: 'user-1' } },
                CampaignDetails: {
                    id: 'clone-id-2',
                    tenantId: 'qa',
                    campaignNumber: 'CMP-CLONE-002',
                    projectType: 'MR-DN',
                    hierarchyType: 'ADMIN',
                    action: 'create',
                    parentId: null,
                    additionalDetails: { cloneFrom: 'CMP-SRC-001' },
                    resources: [
                        { type: 'unified-console-resources', filestoreId: 'CLONE-OWN-123' },
                    ],
                },
            },
        };

        await processAfterPersistNew(request, 'create');

        expect(searchCampaignMock).not.toHaveBeenCalled();
        expect(httpRequestMock).toHaveBeenCalledTimes(1);
        expect(httpRequestMock.mock.calls[0][1].ResourceDetails.fileStoreId).toBe('CLONE-OWN-123');
    });

    it('runs regular flow for a cloned campaign carrying explicit regular resources without borrowing unified sheet', async () => {
        const request: any = {
            body: {
                RequestInfo: { userInfo: { uuid: 'user-1' } },
                CampaignDetails: {
                    id: 'clone-id-3',
                    tenantId: 'qa',
                    campaignNumber: 'CMP-CLONE-003',
                    action: 'create',
                    parentId: null,
                    additionalDetails: { cloneFrom: 'CMP-SRC-001' },
                    resources: [
                        { type: 'boundary', filestoreId: 'B-1' },
                        { type: 'facility', filestoreId: 'F-1' },
                        { type: 'user', filestoreId: 'U-1' },
                    ],
                },
            },
        };

        const immediateSpy = jest.spyOn(global, 'setImmediate').mockImplementation(() => 0 as any);
        try {
            await processAfterPersistNew(request, 'create');

            expect(searchCampaignMock).not.toHaveBeenCalled();
            expect(httpRequestMock).not.toHaveBeenCalled();
            expect(prepareProcessesMock).toHaveBeenCalledWith('CMP-CLONE-003', 'qa', 'user-1');
            expect(immediateSpy).toHaveBeenCalledTimes(1);
        } finally {
            immediateSpy.mockRestore();
        }
    });
});
