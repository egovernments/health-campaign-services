jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn((x: any) => JSON.stringify(x)),
}));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../validators/genericValidator', () => ({
    validateBodyViaSchema: jest.fn(),
    validateCampaignBodyViaSchema: jest.fn(),
    validateHierarchyType: jest.fn(),
}));
jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
    producer: { send: jest.fn(), connect: jest.fn(), disconnect: jest.fn() },
}));
// redisUtils opens a real socket at import time, which the requireActual on genericUtils below pulls in.
jest.mock('../utils/redisUtils', () => ({
    redis: { get: jest.fn(), set: jest.fn(), del: jest.fn() },
    checkRedisConnection: jest.fn().mockResolvedValue(true),
    reconnectRedis: jest.fn(),
}));
jest.mock('../utils/onGoingCampaignUpdateUtils', () => ({
    validateMissingBoundaryFromParent: jest.fn(),
    getBoundariesFromCampaignSearchResponse: jest.fn(),
}));
// validateProjectType resolves the allowed project types from MDMS and treats any other shape as a 500.
jest.mock('../utils/request', () => ({
    httpRequest: jest.fn().mockResolvedValue({ MdmsRes: { 'HCM-PROJECT-TYPES': { projectTypes: ['MR-DN'] } } }),
    defaultheader: jest.fn(() => ({})),
}));
jest.mock('../api/campaignApis', () => ({
    getCampaignSearchResponse: jest.fn(),
    getHeadersOfBoundarySheet: jest.fn(),
    getHierarchy: jest.fn(),
    handleResouceDetailsError: jest.fn(),
}));
jest.mock('../api/genericApis', () => ({ getSheetData: jest.fn(), getTargetWorkbook: jest.fn() }));
jest.mock('../api/healthApis', () => ({ fetchProductVariants: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({
    generateProcessedFileAndPersist: jest.fn(),
    getFinalValidHeadersForTargetSheetAsPerCampaignType: jest.fn(),
    getLocalizedName: jest.fn((k: string) => k),
    searchProjectCampaignResourcData: jest.fn(),
}));
jest.mock('../utils/boundaryUtils', () => ({ getBoundaryColumnName: jest.fn(), getBoundaryTabName: jest.fn() }));
jest.mock('../utils/targetUtils', () => ({
    generateTargetColumnsBasedOnDeliveryConditions: jest.fn(),
    isDynamicTargetTemplateForProjectType: jest.fn(() => false),
    modifyDeliveryConditions: jest.fn(),
}));
jest.mock('../validators/microplanValidators', () => ({
    validateExtraBoundariesForMicroplan: jest.fn(),
    validateLatLongForMicroplanCampaigns: jest.fn(),
    validatePhoneNumberSheetWise: jest.fn(),
    validateRequiredTargetsForMicroplanCampaigns: jest.fn(),
    validateUniqueSheetWise: jest.fn(),
    validateUserForMicroplan: jest.fn(),
}));
jest.mock('../utils/microplanUtils', () => ({
    isMicroplanRequest: jest.fn(() => false),
    planConfigSearch: jest.fn(),
    planFacilitySearch: jest.fn(),
}));
jest.mock('../utils/campaignMappingUtils', () => ({ getPvarIds: jest.fn(() => []) }));
jest.mock('../utils/genericUtils', () => {
    const actual = jest.requireActual('../utils/genericUtils');
    return {
        ...actual,
        getDifferentDistrictTabs: jest.fn(),
        getLocalizedHeaders: jest.fn(),
        getMdmsDataBasedOnCampaignType: jest.fn(),
    };
});

import { validateProjectCampaignRequest } from '../validators/campaignValidators';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';

const mockSearch = jest.mocked(searchProjectTypeCampaignService);

const PARENT_NUMBER = 'CMP-2026-08-17-007802';
const PARENT_ID = 'parent-uuid-1';
const TENANT = 'dev';

const sourceBoundaries = [
    { code: 'MO', includeAllChildren: true, isRoot: true, type: 'Country' },
    { code: 'MO_14_PROVINCE_14', includeAllChildren: true, isRoot: false, type: 'Province' },
    { code: 'MO_14_01_DISTRICT_14', includeAllChildren: true, isRoot: false, type: 'District' },
    { code: 'MO_14_01_01_VILLAGE_14', includeAllChildren: false, isRoot: false, type: 'Village' },
];

const sourceCampaign = {
    id: PARENT_ID,
    campaignNumber: PARENT_NUMBER,
    projectType: 'MR-DN',
    hierarchyType: 'ADMIN',
    boundaries: sourceBoundaries,
};

/** Routes each search shape the create path performs: the campaign-name uniqueness check must find nothing, the clone-source lookup must find the parent. */
const routeSearch = (criteria: any) => {
    if (criteria?.campaignName) return Promise.resolve({ CampaignDetails: [] });
    if (criteria?.ids || criteria?.campaignNumber) return Promise.resolve({ CampaignDetails: [sourceCampaign] });
    return Promise.resolve({ CampaignDetails: [] });
};

const buildCloneRequest = (overrides: any = {}) => ({
    body: {
        RequestInfo: { userInfo: { uuid: 'u1', tenantId: TENANT } },
        CampaignDetails: {
            tenantId: TENANT,
            action: 'draft',
            campaignName: 'Bednet_clone_1',
            hierarchyType: 'ADMIN',
            projectType: 'MR-DN',
            startDate: Date.now() + 3 * 24 * 60 * 60 * 1000,
            endDate: Date.now() + 10 * 24 * 60 * 60 * 1000,
            additionalDetails: { cloneFrom: PARENT_NUMBER },
            ...overrides,
        },
    },
});

describe('clone boundary backfill on create', () => {
    beforeEach(() => mockSearch.mockImplementation(routeSearch as any));
    afterEach(() => jest.clearAllMocks());

    it('backfills the source boundaries when the payload has no boundaries key', async () => {
        const request = buildCloneRequest();

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
    });

    it('honours an explicit empty array rather than treating it as absent', async () => {
        const request = buildCloneRequest({ boundaries: [] });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toEqual([]);
    });

    it('leaves a caller-supplied boundary set untouched', async () => {
        const supplied = [{ code: 'MO', includeAllChildren: true, isRoot: true }];
        const request = buildCloneRequest({ boundaries: supplied });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toEqual(supplied);
    });

    it('does not backfill a non-clone create', async () => {
        const request = buildCloneRequest({ additionalDetails: {} });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toBeUndefined();
    });

    it('does not backfill a child campaign, whose parent boundaries are locked by a different flow', async () => {
        const request = buildCloneRequest({ parentId: 'some-parent' });

        await validateProjectCampaignRequest(request, 'create').catch(() => undefined);

        expect(request.body.CampaignDetails.boundaries).toBeUndefined();
    });

    it('prefers the unambiguous clonedCampaignId lookup over the shared campaignNumber', async () => {
        const request = buildCloneRequest({
            additionalDetails: { cloneFrom: PARENT_NUMBER, clonedCampaignId: PARENT_ID },
        });

        await validateProjectCampaignRequest(request, 'create');

        expect(mockSearch).toHaveBeenCalledWith({ tenantId: TENANT, ids: [PARENT_ID] });
        expect(mockSearch).not.toHaveBeenCalledWith(expect.objectContaining({ campaignNumber: PARENT_NUMBER }));
    });

    it('falls back to campaignNumber when the console did not send clonedCampaignId', async () => {
        const request = buildCloneRequest();

        await validateProjectCampaignRequest(request, 'create');

        expect(mockSearch).toHaveBeenCalledWith({ tenantId: TENANT, campaignNumber: PARENT_NUMBER });
    });

    it('leaves boundaries absent and does not throw when the clone source cannot be resolved', async () => {
        mockSearch.mockImplementation(((criteria: any) =>
            criteria?.campaignName
                ? Promise.resolve({ CampaignDetails: [] })
                : Promise.resolve({ CampaignDetails: [] })) as any);
        const request = buildCloneRequest();

        await expect(validateProjectCampaignRequest(request, 'create')).resolves.not.toThrow();
        expect(request.body.CampaignDetails.boundaries).toBeUndefined();
    });

    it('leaves boundaries absent and does not throw when the clone source lookup fails', async () => {
        mockSearch.mockImplementation(((criteria: any) =>
            criteria?.campaignName
                ? Promise.resolve({ CampaignDetails: [] })
                : Promise.reject(new Error('search exploded'))) as any);
        const request = buildCloneRequest();

        await expect(validateProjectCampaignRequest(request, 'create')).resolves.not.toThrow();
        expect(request.body.CampaignDetails.boundaries).toBeUndefined();
    });
});

const EXISTING_ID = 'existing-uuid-1';

const existingCampaign = {
    id: EXISTING_ID,
    campaignNumber: 'CMP-2026-08-17-009000',
    campaignName: 'Bednet_existing',
    status: 'failed',
    isActive: true,
    projectType: 'MR-DN',
    hierarchyType: 'ADMIN',
    boundaries: sourceBoundaries,
};

/** The My Campaigns retry button reposts a grid row verbatim, and the list search strips boundaries from that row. */
const buildRetryRequest = (overrides: any = {}) => ({
    body: {
        RequestInfo: { userInfo: { uuid: 'u1', tenantId: TENANT } },
        CampaignDetails: {
            id: EXISTING_ID,
            tenantId: TENANT,
            action: 'draft',
            campaignName: 'Bednet_existing',
            hierarchyType: 'ADMIN',
            projectType: 'MR-DN',
            isActive: true,
            // The list response carries a count but not the array itself — that is the whole defect.
            boundaryCount: sourceBoundaries.length,
            additionalDetails: {},
            ...overrides,
        },
    },
});

describe('boundary inheritance on update', () => {
    beforeEach(() =>
        mockSearch.mockImplementation(((criteria: any) =>
            criteria?.campaignName
                ? Promise.resolve({ CampaignDetails: [] })
                : Promise.resolve({ CampaignDetails: [existingCampaign] })) as any),
    );
    afterEach(() => jest.clearAllMocks());

    it('inherits the persisted boundaries when the payload omits them', async () => {
        const request = buildRetryRequest();

        await validateProjectCampaignRequest(request, 'update');

        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
    });

    it('does not overwrite an explicit empty array', async () => {
        const request = buildRetryRequest({ boundaries: [] });

        await validateProjectCampaignRequest(request, 'update');

        expect(request.body.CampaignDetails.boundaries).toEqual([]);
    });

    it('does not overwrite a caller-supplied boundary set', async () => {
        const reduced = [{ code: 'MO', includeAllChildren: true, isRoot: true, type: 'Country' }];
        const request = buildRetryRequest({ boundaries: reduced });

        await validateProjectCampaignRequest(request, 'update');

        expect(request.body.CampaignDetails.boundaries).toEqual(reduced);
    });
});
