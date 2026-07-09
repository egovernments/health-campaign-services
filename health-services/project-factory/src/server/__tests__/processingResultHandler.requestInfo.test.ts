/**
 * processingResultHandler.requestInfo.test.ts
 *
 * Regression tests for the creator-email individual lookup in handleProcessingResult.
 *
 * The hcm-processing-result Kafka message only carries `requestInfo` when produced
 * by excel-ingestion >= PR #2018. Older producers (e.g. the image deployed in the
 * hcm-demo env) omit it, so `messageObject.requestInfo` is undefined and the
 * individual/v1/_search body used to be sent WITHOUT RequestInfo — which the
 * individual service rejects with `NotNull.individualSearchRequest.requestInfo`.
 *
 * The fix falls back to a minimal system RequestInfo built from the campaign
 * creator uuid so the (non-blocking) lookup always passes @NotNull validation,
 * while still preferring the real requestInfo when the message carries one.
 */

import { handleProcessingResult } from '../utils/processingResultHandler';
import { additionalDetailKeys } from '../config/constants';

// ── core service mocks ──────────────────────────────────────────────────────
jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn().mockResolvedValue([]),
    getMappingDataRelatedToCampaign: jest.fn().mockResolvedValue([]),
    prepareProcessesInDb: jest.fn().mockResolvedValue(undefined),
    getRelatedDataWithUniqueIdentifiers: jest.fn().mockResolvedValue([]),
    checkCampaignDataCompletionStatus: jest.fn().mockResolvedValue({ allCompleted: true, anyFailed: false }),
    checkCampaignMappingCompletionStatus: jest.fn().mockResolvedValue({ allCompleted: true }),
    throwError: jest.fn().mockImplementation((_module: any, status: any, code: any, description: any) => {
        throw Object.assign(new Error(description || code), { status, code });
    }),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    pollUntilCount: jest.fn().mockResolvedValue(undefined),
    pollUntilCountFn: jest.fn().mockResolvedValue(undefined),
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/campaignFailureHandler', () => ({
    sendCampaignFailureMessage: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn().mockResolvedValue({ Individual: [] }),
}));

jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn(),
    setCache: jest.fn(),
    deleteCache: jest.fn(),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../api/coreApis', () => ({
    searchMDMSDataViaV2Api: jest.fn().mockResolvedValue({}),
    searchBoundaryRelationshipData: jest.fn().mockResolvedValue({}),
}));

jest.mock('../api/campaignApis', () => ({
    confirmProjectParentCreation: jest.fn().mockResolvedValue({}),
}));

jest.mock('../utils/campaignUtils', () => ({
    populateBoundariesRecursively: jest.fn().mockResolvedValue(undefined),
    getLocalizedName: jest.fn((k: string) => k),
    enrichAndPersistCampaignWithError: jest.fn().mockResolvedValue(undefined),
    enrichAndPersistCampaignForCreateViaFlow2: jest.fn().mockResolvedValue(undefined),
    userCredGeneration: jest.fn().mockResolvedValue(undefined),
    markAllToCreateResourcesAsCompleted: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/localisationUtils', () => ({
    getLocalisationModuleName: jest.fn().mockReturnValue('hcm-admin-console'),
}));

jest.mock('../controllers/localisationController/localisation.controller', () => ({
    __esModule: true,
    default: {
        getInstance: jest.fn().mockReturnValue({
            getLocalisationData: jest.fn().mockResolvedValue([]),
        }),
    },
}));

jest.mock('../utils/excelIngestionUtils', () => ({
    searchSheetData: jest.fn().mockResolvedValue([]),
    getSheetDataCount: jest.fn().mockResolvedValue(0),
    forEachSheetDataPage: jest.fn().mockResolvedValue(0),
    getSheetFetchPageSize: jest.fn().mockReturnValue(2000),
}));

jest.mock('../utils/onGoingCampaignUpdateUtils', () => ({
    fetchProjectsWithBoundaryCodeAndReferenceId: jest.fn().mockResolvedValue([]),
}));

jest.mock('../utils/mailUtils', () => ({
    triggerUserCredentialEmailFlow: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/transforms/projectTypeUtils', () => ({
    enrichProjectDetailsFromCampaignDetails: jest.fn().mockResolvedValue(undefined),
}));

// ── imports that must come after all mocks ───────────────────────────────────
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { httpRequest } from '../utils/request';
import config from '../config';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const httpRequestMock = httpRequest as jest.MockedFunction<typeof httpRequest>;

const CREATOR_UUID = 'creator-uuid-123';

// Campaign whose creator uuid drives the individual lookup + the RequestInfo fallback.
const CAMPAIGN_WITH_CREATOR = {
    id: 'campaign-x',
    tenantId: 'demo',
    parentId: null,
    status: 'active',
    campaignNumber: 'CMP-TEST',
    campaignName: 'Test Campaign',
    auditDetails: { createdBy: CREATOR_UUID },
};

// A message that hard-blocks right after the creator lookup (boundary invalid),
// so the handler exits fast — the individual search has already been dispatched.
function buildMessage(requestInfo?: any): any {
    return {
        tenantId: 'demo',
        referenceId: 'campaign-x',
        fileStoreId: 'file-1',
        status: 'completed',
        ...(requestInfo ? { requestInfo } : {}),
        additionalDetails: {
            [additionalDetailKeys.boundarySheetStatus]: 'invalid',
            [additionalDetailKeys.userSheetStatus]: 'valid',
            [additionalDetailKeys.facilitySheetStatus]: 'valid',
            [additionalDetailKeys.validationStatus]: 'invalid',
        },
    };
}

// Extract the body passed to the individual/v1/_search lookup (type=EMPLOYEE).
function getIndividualSearchBody(): any {
    const searchUrl = config.host.healthIndividualHost + config.paths.healthIndividualSearch;
    const call = httpRequestMock.mock.calls.find(
        (c) => c[0] === searchUrl && (c[1] as any)?.Individual?.type === 'EMPLOYEE'
    );
    return call ? (call[1] as any) : undefined;
}

describe('handleProcessingResult: creator-email individual lookup RequestInfo', () => {

    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [CAMPAIGN_WITH_CREATOR] } as any);
        httpRequestMock.mockResolvedValue({ Individual: [] } as any);
    });

    it('falls back to a system RequestInfo built from the campaign creator when the message has no requestInfo', async () => {
        await handleProcessingResult(buildMessage());

        const body = getIndividualSearchBody();
        expect(body).toBeDefined();
        expect(body.RequestInfo).toEqual({ userInfo: { uuid: CREATOR_UUID } });
        expect(body.Individual.userUuid).toEqual([CREATOR_UUID]);
    });

    it('prefers the real requestInfo from the message when present (excel-ingestion >= PR #2018)', async () => {
        const realRequestInfo = {
            apiId: 'pf',
            authToken: 'tok-abc',
            userInfo: { uuid: 'real-user', tenantId: 'demo' },
        };

        await handleProcessingResult(buildMessage(realRequestInfo));

        const body = getIndividualSearchBody();
        expect(body).toBeDefined();
        expect(body.RequestInfo).toEqual(realRequestInfo);
    });

    it('never sends the individual lookup with a null/undefined RequestInfo', async () => {
        await handleProcessingResult(buildMessage());

        const body = getIndividualSearchBody();
        expect(body).toBeDefined();
        expect(body.RequestInfo).not.toBeNull();
        expect(body.RequestInfo).not.toBeUndefined();
    });

    it('injects a creator userInfo when the message requestInfo is present but its userInfo is missing', async () => {
        const requestInfoWithoutUser = { apiId: 'pf', authToken: 'tok-abc' };

        await handleProcessingResult(buildMessage(requestInfoWithoutUser));

        const body = getIndividualSearchBody();
        expect(body).toBeDefined();
        // preserves the incoming fields, but guarantees a userInfo the individual service accepts
        expect(body.RequestInfo).toEqual({ apiId: 'pf', authToken: 'tok-abc', userInfo: { uuid: CREATOR_UUID } });
        expect(body.RequestInfo.userInfo).toEqual({ uuid: CREATOR_UUID });
    });
});
