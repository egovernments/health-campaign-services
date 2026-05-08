import { handleProcessingResult } from '../utils/processingResultHandler';
import * as genericUtils from '../utils/genericUtils';
import { logger } from '../utils/logger';
import { additionalDetailKeys } from '../config/constants';
import * as campaignManageService from '../service/campaignManageService';

// Mock dependencies
jest.mock('../utils/genericUtils');
jest.mock('../service/campaignManageService');
jest.mock('../utils/logger');

describe('processingResultHandler: Validation gate reads per-sheet fields', () => {

    beforeEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Scenario B1: boundary=invalid, others=valid
     * Expected: throws VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE
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

        await expect(handleProcessingResult(messageObject)).rejects.toThrow();
        // Should throw VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE
    });

    /**
     * Scenario B2: facility=invalid, others=valid
     * Expected: throws VALIDATION_ERROR_UNIFIED_CONSOLE_TEMPLATE
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

        await expect(handleProcessingResult(messageObject)).rejects.toThrow();
    });

    /**
     * Scenario B3: user=invalid, boundary=valid, facility=valid, validationStatus=invalid
     * Expected: does NOT throw; calls processCampaignUsersFromExcelData
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

        // Mock campaign and utils
        (campaignManageService.searchProjectTypeCampaignService as jest.Mock).mockResolvedValue({
            CampaignDetails: [{ id: 'campaign-3', tenantId: 'test-tenant', parentId: null }],
        });

        (genericUtils.getRelatedDataWithCampaign as jest.Mock).mockResolvedValue([]);

        // Should not throw
        await handleProcessingResult(messageObject);

        // Logger should be called with proceed message
        expect(logger.info).toHaveBeenCalledWith(
            expect.stringContaining('Proceeding with campaign despite user-sheet validation errors')
        );
    });

    /**
     * Scenario B4: per-sheet keys absent, validationStatus=invalid
     * Expected: throws (legacy fallback)
     */
    it('B4: should hard-block legacy flow when validationStatus is invalid with no per-sheet keys', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-4',
            fileStoreId: 'file-4',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.validationStatus]: 'invalid',
                // No per-sheet keys present
            },
        };

        await expect(handleProcessingResult(messageObject)).rejects.toThrow();
    });

    /**
     * Scenario B5: per-sheet keys absent, validationStatus=valid
     * Expected: does not throw
     */
    it('B5: should allow processing when no per-sheet keys and validationStatus=valid', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-5',
            fileStoreId: 'file-5',
            status: 'completed',
            additionalDetails: {
                [additionalDetailKeys.validationStatus]: 'valid',
                // No per-sheet keys present
                totalRowsProcessed: 0,
            },
        };

        (campaignManageService.searchProjectTypeCampaignService as jest.Mock).mockResolvedValue({
            CampaignDetails: [{ id: 'campaign-5', tenantId: 'test-tenant', parentId: null }],
        });

        (genericUtils.getRelatedDataWithCampaign as jest.Mock).mockResolvedValue([]);

        await handleProcessingResult(messageObject);
        // Should not throw
    });

    /**
     * Scenario B6: all per-sheet=valid
     * Expected: does not throw
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

        (campaignManageService.searchProjectTypeCampaignService as jest.Mock).mockResolvedValue({
            CampaignDetails: [{ id: 'campaign-6', tenantId: 'test-tenant', parentId: null }],
        });

        (genericUtils.getRelatedDataWithCampaign as jest.Mock).mockResolvedValue([]);

        await handleProcessingResult(messageObject);
        // Should not throw
    });

    /**
     * Scenario B7: any per-sheet present but status="failed"
     * Expected: throws PROCESSING_FAILED
     */
    it('B7: should throw PROCESSING_FAILED when messageObject.status is not completed', async () => {
        const messageObject = {
            tenantId: 'test-tenant',
            referenceId: 'campaign-7',
            fileStoreId: 'file-7',
            status: 'failed',
            additionalDetails: {
                [additionalDetailKeys.userSheetStatus]: 'valid',
            },
        };

        await expect(handleProcessingResult(messageObject)).rejects.toThrow();
    });

    /**
     * Scenario B8: user=invalid path is taken
     * Expected: logger.info called with "Proceeding…" message containing campaignNumber
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

        (campaignManageService.searchProjectTypeCampaignService as jest.Mock).mockResolvedValue({
            CampaignDetails: [{ id: 'campaign-8', tenantId: 'test-tenant', parentId: null }],
        });

        (genericUtils.getRelatedDataWithCampaign as jest.Mock).mockResolvedValue([]);

        await handleProcessingResult(messageObject);

        // Verify logger.info was called with the proceed message
        expect(logger.info).toHaveBeenCalledWith(
            expect.stringContaining('Proceeding with campaign despite user-sheet validation errors')
        );
        expect(logger.info).toHaveBeenCalledWith(
            expect.stringContaining('campaign-8')
        );
    });
});
