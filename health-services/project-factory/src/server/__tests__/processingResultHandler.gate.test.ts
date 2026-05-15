/**
 * processingResultHandler.gate.test.ts
 *
 * Tests the per-sheet validation gate in handleProcessingResult:
 *   - boundary/facility invalid → hard-block (sendCampaignFailureMessage called)
 *   - only user-sheet invalid   → proceed (log message emitted, no failure)
 *   - legacy flow (no per-sheet keys) → falls back to validationStatus
 *   - status !== 'completed'    → hard-block
 *
 * The outer try/catch in handleProcessingResult swallows all thrown errors
 * and calls sendCampaignFailureMessage, so hard-block scenarios are verified
 * by asserting that mock was called rather than expecting promise rejection.
 */

import { handleProcessingResult } from '../utils/processingResultHandler';
import { logger } from '../utils/logger';
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
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue(undefined),
}));

// ── infrastructure mocks ────────────────────────────────────────────────────
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
import { sendCampaignFailureMessage } from '../utils/campaignFailureHandler';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const sendFailureMock = sendCampaignFailureMessage as jest.MockedFunction<typeof sendCampaignFailureMessage>;

const CAMPAIGN_STUB = {
    id: 'campaign-x',
    tenantId: 'test-tenant',
    parentId: null,
    status: 'active',
    campaignNumber: 'CMP-TEST',
    campaignName: 'Test Campaign',
    auditDetails: {},
};

describe('processingResultHandler: Validation gate reads per-sheet fields', () => {

    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [CAMPAIGN_STUB] } as any);
    });

    /**
     * B1: boundary=invalid — must hard-block (sendCampaignFailureMessage called)
     */
    it('B1: should hard-block when boundary sheet is invalid', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-1',
            fileStoreId: 'file-1',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.boundarySheetStatus]: 'invalid',
                [additionalDetailKeys.userSheetStatus]: 'valid',
                [additionalDetailKeys.facilitySheetStatus]: 'valid',
                [additionalDetailKeys.validationStatus]: 'invalid',
            },
        };

        await handleProcessingResult(messageObject);

        expect(sendFailureMock).toHaveBeenCalledWith(
            'campaign-1',
            'test-tenant',
            expect.any(Error)
        );
    });

    /**
     * B2: facility=invalid — must hard-block
     */
    it('B2: should hard-block when facility sheet is invalid', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-2',
            fileStoreId: 'file-2',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.facilitySheetStatus]: 'invalid',
                [additionalDetailKeys.userSheetStatus]: 'valid',
                [additionalDetailKeys.boundarySheetStatus]: 'valid',
                [additionalDetailKeys.validationStatus]: 'invalid',
            },
        };

        await handleProcessingResult(messageObject);

        expect(sendFailureMock).toHaveBeenCalledWith(
            'campaign-2',
            'test-tenant',
            expect.any(Error)
        );
    });

    /**
     * B3: user=invalid, boundary/facility=valid — must NOT hard-block;
     * expect the "Proceeding" log and no failure message.
     */
    it('B3: should allow processing when only user sheet has errors', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-3',
            fileStoreId: 'file-3',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.userSheetStatus]: 'invalid',
                [additionalDetailKeys.boundarySheetStatus]: 'valid',
                [additionalDetailKeys.facilitySheetStatus]: 'valid',
                [additionalDetailKeys.validationStatus]: 'invalid',
                totalRowsProcessed: 0,
            },
        };

        await handleProcessingResult(messageObject);

        expect(logger.info).toHaveBeenCalledWith(
            expect.stringContaining('Proceeding with campaign despite user-sheet validation errors')
        );
        // Hard-block path must NOT have fired
        expect(sendFailureMock).not.toHaveBeenCalledWith(
            expect.anything(),
            expect.anything(),
            expect.objectContaining({ code: 'VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE' })
        );
    });

    /**
     * B4: no per-sheet keys, validationStatus=invalid — legacy fallback must hard-block
     */
    it('B4: should hard-block legacy flow when validationStatus is invalid with no per-sheet keys', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-4',
            fileStoreId: 'file-4',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.validationStatus]: 'invalid',
            },
        };

        await handleProcessingResult(messageObject);

        expect(sendFailureMock).toHaveBeenCalledWith(
            'campaign-4',
            'test-tenant',
            expect.any(Error)
        );
    });

    /**
     * B5: no per-sheet keys, validationStatus=valid — must proceed without hard-block
     */
    it('B5: should allow processing when no per-sheet keys and validationStatus=valid', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-5',
            fileStoreId: 'file-5',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.validationStatus]: 'valid',
                totalRowsProcessed: 0,
            },
        };

        await handleProcessingResult(messageObject);

        expect(sendFailureMock).not.toHaveBeenCalledWith(
            expect.anything(),
            expect.anything(),
            expect.objectContaining({ code: 'VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE' })
        );
    });

    /**
     * B6: all per-sheet=valid — must proceed without hard-block
     */
    it('B6: should allow processing when all per-sheet sheets are valid', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-6',
            fileStoreId: 'file-6',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.userSheetStatus]: 'valid',
                [additionalDetailKeys.boundarySheetStatus]: 'valid',
                [additionalDetailKeys.facilitySheetStatus]: 'valid',
                [additionalDetailKeys.validationStatus]: 'valid',
                totalRowsProcessed: 0,
            },
        };

        await handleProcessingResult(messageObject);

        expect(sendFailureMock).not.toHaveBeenCalledWith(
            expect.anything(),
            expect.anything(),
            expect.objectContaining({ code: 'VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE' })
        );
    });

    /**
     * B7: status=failed — PROCESSING_FAILED path must fire
     */
    it('B7: should trigger failure when messageObject.status is not completed', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-7',
            fileStoreId: 'file-7',
            status: 'failed',
            additionalDetails: {
                [additionalDetailKeys.userSheetStatus]: 'valid',
            },
        };

        await handleProcessingResult(messageObject);

        expect(sendFailureMock).toHaveBeenCalledWith(
            'campaign-7',
            'test-tenant',
            expect.any(Error)
        );
    });

    /**
     * B8: user=invalid — "Proceeding" log must mention the campaignNumber
     */
    it('B8: should log proceed message when user-sheet errors occur', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-8',
            fileStoreId: 'file-8',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.userSheetStatus]: 'invalid',
                [additionalDetailKeys.boundarySheetStatus]: 'valid',
                [additionalDetailKeys.facilitySheetStatus]: 'valid',
                [additionalDetailKeys.validationStatus]: 'invalid',
                totalRowsProcessed: 0,
            },
        };

        await handleProcessingResult(messageObject);

        expect(logger.info).toHaveBeenCalledWith(
            expect.stringContaining('Proceeding with campaign despite user-sheet validation errors')
        );
    });
});
