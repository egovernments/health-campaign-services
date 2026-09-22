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
jest.mock('../utils/resourceDetailsUtils', () => ({
    ...jest.requireActual('../utils/resourceDetailsUtils'),
    searchResourceDetailsFromDB: jest.fn(async () => []),
}));
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


import { prepareClonePayloadForCreate, validateProjectCampaignRequest, validateProjectCampaignResources } from '../validators/campaignValidators';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';

const mockSearch = jest.mocked(searchProjectTypeCampaignService);

const PARENT_NUMBER = 'CMP-2026-08-17-007802';
const PARENT_ID = 'parent-uuid-1';
const TENANT = 'dev';

const sourceBoundaries = [
    { code: 'MO', includeAllChildren: true, isRoot: true, type: 'Country' },
    { code: 'MO_14_PROVINCE_14', includeAllChildren: true, isRoot: false, type: 'Province' },
    { code: 'MO_14_01_DISTRICT_14', includeAllChildren: true, isRoot: false, type: 'District' },
];

const sourceCampaign = {
    id: PARENT_ID,
    campaignNumber: PARENT_NUMBER,
    projectType: 'MR-DN',
    hierarchyType: 'ADMIN',
    boundaries: sourceBoundaries,
    additionalDetails: { isUnifiedCampaign: true },
    resources: [{ type: 'unified-console-resources', filestoreId: 'parent-unified-file', filename: 'parent.xlsx' }],
};

/** The name-uniqueness check must find nothing; the clone-source lookup must find the parent. */
const routeSearch = (criteria: any) => {
    if (criteria?.campaignName) return Promise.resolve({ CampaignDetails: [] });
    if (criteria?.ids || criteria?.campaignNumber) return Promise.resolve({ CampaignDetails: [sourceCampaign] });
    return Promise.resolve({ CampaignDetails: [] });
};

const parentResources = [
    { type: 'unified-console-resources', filestoreId: 'parent-unified-file', filename: 'parent.xlsx' },
    { type: 'facility', filestoreId: 'parent-facility-file' },
];

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
            additionalDetails: { cloneFrom: PARENT_NUMBER, clonedCampaignId: PARENT_ID },
            ...overrides,
        },
    },
});

describe('a clone is set up to generate its own template', () => {
    beforeEach(() => mockSearch.mockImplementation(routeSearch as any));
    afterEach(() => jest.clearAllMocks());

    it("backfills the parent's boundaries, which is what lets generation trigger", async () => {
        const request = buildCloneRequest();

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
        expect(mockSearch).toHaveBeenCalledWith({ tenantId: TENANT, ids: [PARENT_ID] });
    });

    it('falls back to a campaignNumber lookup when only cloneFrom was sent', async () => {
        const request = buildCloneRequest({ additionalDetails: { cloneFrom: PARENT_NUMBER } });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
        expect(mockSearch).toHaveBeenCalledWith({ tenantId: TENANT, campaignNumber: PARENT_NUMBER });
    });

    it('honours an explicit empty boundary list rather than repairing it', async () => {
        const request = buildCloneRequest({ boundaries: [] });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.boundaries).toEqual([]);
    });

    it('does not throw when the clone source cannot be resolved', async () => {
        mockSearch.mockImplementation((() => Promise.resolve({ CampaignDetails: [] })) as any);
        const request = buildCloneRequest();

        await expect(validateProjectCampaignRequest(request, 'create')).resolves.not.toThrow();
        expect(request.body.CampaignDetails.boundaries).toBeUndefined();
    });

    it("drops the parent's unified workbook so the operator downloads the clone's own template", async () => {
        const request = buildCloneRequest({ resources: parentResources });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual([{ type: 'facility', filestoreId: 'parent-facility-file' }]);
    });

    it('also drops the un-transformed "unified-console" type', async () => {
        const request = buildCloneRequest({ resources: [{ type: 'unified-console', filestoreId: 'parent-unified-file' }] });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual([]);
    });

    it('leaves a campaign that is not a clone completely alone', async () => {
        const request = buildCloneRequest({
            additionalDetails: { beneficiaryType: 'INDIVIDUAL' },
            boundaries: sourceBoundaries,
            resources: parentResources,
        });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual(parentResources);
    });

    it("keeps a unified file that is the clone's OWN (different filestoreId), even on a draft", async () => {
        const request = buildCloneRequest({ resources: [{ type: 'unified-console-resources', filestoreId: 'my-own-file' }] });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual([{ type: 'unified-console-resources', filestoreId: 'my-own-file' }]);
    });

    it('drops nothing when the source has no unified resource to compare against', async () => {
        mockSearch.mockImplementation(((criteria: any) =>
            criteria?.campaignName
                ? Promise.resolve({ CampaignDetails: [] })
                : Promise.resolve({ CampaignDetails: [{ ...sourceCampaign, resources: [] }] })) as any);
        const request = buildCloneRequest({ resources: parentResources });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual(parentResources);
    });

    it('inherits isUnifiedCampaign from the source when the payload omits it', async () => {
        const request = buildCloneRequest();

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.additionalDetails.isUnifiedCampaign).toBe(true);
    });

    it('does not touch the inherited file on a single-shot create+launch (action "create")', async () => {
        // Unit-level: the launch branch of validateCampaignBody needs delivery rules and resources that are
        // out of scope here, so drive the preparation step directly.
        const request = buildCloneRequest({ action: 'create', resources: parentResources });

        await prepareClonePayloadForCreate(request);

        expect(request.body.CampaignDetails.resources).toEqual(parentResources);
        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
    });

    it('makes no lookup at all when there is nothing to prepare', async () => {
        const request = buildCloneRequest({
            boundaries: sourceBoundaries,
            additionalDetails: { cloneFrom: PARENT_NUMBER, clonedCampaignId: PARENT_ID, isUnifiedCampaign: true },
        });

        await prepareClonePayloadForCreate(request);

        expect(mockSearch).not.toHaveBeenCalled();
    });

    it('never drops the inherited file for a clone that carries clonedCampaignId alone (the launch-time borrow keys on cloneFrom)', async () => {
        const request = buildCloneRequest({ additionalDetails: { clonedCampaignId: PARENT_ID }, resources: parentResources });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual(parentResources);
        // boundaries are still backfilled for it — the id-based lookup does not need cloneFrom
        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
    });

    it('never drops the inherited file for a clone that carries cloneFrom alone (excel-ingestion pre-fills from clonedCampaignId)', async () => {
        const request = buildCloneRequest({ additionalDetails: { cloneFrom: PARENT_NUMBER }, resources: parentResources });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual(parentResources);
        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
    });

    it('leaves an ongoing-update child campaign alone', async () => {
        // A child create validates against its parent, which must be an existing active campaign.
        mockSearch.mockImplementation((() =>
            Promise.resolve({ CampaignDetails: [{ ...sourceCampaign, campaignName: 'Bednet_clone_1', status: 'created', isActive: true }] })) as any);
        const request = buildCloneRequest({ parentId: PARENT_ID, boundaries: sourceBoundaries, resources: parentResources });

        await validateProjectCampaignRequest(request, 'create');

        expect(request.body.CampaignDetails.resources).toEqual(parentResources);
        expect(request.body.CampaignDetails.boundaries).toEqual(sourceBoundaries);
    });
});

describe('launch-time resource validation anticipates the borrow for a never-uploaded clone', () => {
    afterEach(() => jest.clearAllMocks());

    const persistedClone = (extra: any = {}) => ({
        id: 'clone-uuid', tenantId: TENANT, parentId: null,
        additionalDetails: { cloneFrom: PARENT_NUMBER, clonedCampaignId: PARENT_ID, isUnifiedCampaign: true },
        ...extra,
    });
    // What the react19 console sends at launch: additionalDetails rebuilt from a fixed key set, no lineage.
    const consoleLaunchPayload = () => ({
        id: 'clone-uuid', tenantId: TENANT, action: 'create',
        additionalDetails: { beneficiaryType: 'INDIVIDUAL', key: 2, cycleData: {}, isUnifiedCampaign: true },
    });
    const withPersisted = (existing: any) => ({ body: { ExistingCampaignDetails: existing } });

    it('passes a CONSOLE-shaped launch (no lineage in the payload) when the persisted clone has a source with a unified sheet', async () => {
        mockSearch.mockImplementation(routeSearch as any);
        await expect(validateProjectCampaignResources([{ type: 'attendanceRegister', filestoreId: 'x' }] as any, withPersisted(persistedClone()), consoleLaunchPayload())).resolves.toBeUndefined();
        expect(mockSearch).toHaveBeenCalledWith({ tenantId: TENANT, ids: [PARENT_ID] });
    });

    it('CONTROL: the same console-shaped payload with no persisted row is rejected (the pass above comes from ExistingCampaignDetails)', async () => {
        mockSearch.mockImplementation(routeSearch as any);
        await expect(validateProjectCampaignResources([] as any, { body: {} }, consoleLaunchPayload())).rejects.toMatchObject({ code: 'VALIDATION_ERROR_MISSING_RESOURCE' });
        expect(mockSearch).not.toHaveBeenCalled();
    });

    it('passes an API launch that carries lineage in the payload itself', async () => {
        mockSearch.mockImplementation(routeSearch as any);
        const payload = { tenantId: TENANT, id: 'clone-uuid', additionalDetails: { cloneFrom: PARENT_NUMBER, clonedCampaignId: PARENT_ID } };
        await expect(validateProjectCampaignResources([] as any, { body: {} }, payload)).resolves.toBeUndefined();
    });

    it('still rejects when the source has no unified workbook to borrow', async () => {
        mockSearch.mockImplementation(((criteria: any) =>
            Promise.resolve({ CampaignDetails: [{ ...sourceCampaign, resources: [] }] })) as any);
        await expect(validateProjectCampaignResources([] as any, withPersisted(persistedClone()), consoleLaunchPayload())).rejects.toMatchObject({ code: 'VALIDATION_ERROR_MISSING_RESOURCE' });
    });

    it('does not apply to an ongoing-update child, even one whose persisted row carries lineage', async () => {
        await expect(validateProjectCampaignResources([] as any, withPersisted(persistedClone({ parentId: 'root-uuid' })), consoleLaunchPayload())).rejects.toMatchObject({ code: 'VALIDATION_ERROR_MISSING_RESOURCE' });
        expect(mockSearch).not.toHaveBeenCalled();
    });

    it('does not apply to a clone that carries clonedCampaignId only (the borrow keys on cloneFrom)', async () => {
        const existing = persistedClone({ additionalDetails: { clonedCampaignId: PARENT_ID } });
        await expect(validateProjectCampaignResources([] as any, withPersisted(existing), consoleLaunchPayload())).rejects.toMatchObject({ code: 'VALIDATION_ERROR_MISSING_RESOURCE' });
        expect(mockSearch).not.toHaveBeenCalled();
    });

    it('does not apply to a non-clone campaign', async () => {
        await expect(validateProjectCampaignResources([] as any, withPersisted({ id: 'x', tenantId: TENANT, additionalDetails: {} }), { tenantId: TENANT, additionalDetails: {} })).rejects.toMatchObject({ code: 'VALIDATION_ERROR_MISSING_RESOURCE' });
        expect(mockSearch).not.toHaveBeenCalled();
    });

    it('never throws from the source lookup itself', async () => {
        mockSearch.mockRejectedValue(new Error('search exploded'));
        await expect(validateProjectCampaignResources([] as any, withPersisted(persistedClone()), consoleLaunchPayload())).rejects.toMatchObject({ code: 'VALIDATION_ERROR_MISSING_RESOURCE' });
    });
});
