/**
 * waitForMappingRowsToSettle.test.ts
 *
 * Verifies the mapping-persistence gate that fixes the dispatch race (Bug 3):
 *   - returns the total once the mapping-row count has plateaued for the stall window
 *   - returns 0 for a genuinely-empty campaign (feeds the Bug 2 empty-completion branch)
 *   - keeps waiting (does not resolve) while the count is still growing
 */

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
    checkCampaignMappingCompletionStatus: jest.fn(),
    throwError: jest.fn(),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    pollUntilCount: jest.fn().mockResolvedValue([]),
    pollUntilCountFn: jest.fn().mockResolvedValue(undefined),
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/campaignFailureHandler', () => ({
    sendCampaignFailureMessage: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn().mockResolvedValue({}),
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
    default: { getInstance: jest.fn().mockReturnValue({ getLocalisedData: jest.fn().mockResolvedValue([]) }) },
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

import { waitForMappingRowsToSettle } from '../utils/processingResultHandler';
import { checkCampaignMappingCompletionStatus } from '../utils/genericUtils';
import config from '../config';

const checkMock = checkCampaignMappingCompletionStatus as jest.MockedFunction<typeof checkCampaignMappingCompletionStatus>;

const status = (totalMappings: number) => ({
    allCompleted: false,
    anyFailed: false,
    totalMappings,
    completedMappings: 0,
    failedMappings: 0,
    pendingMappings: totalMappings,
});

const STALL = config.resourceCreationConfig.mappingPersistenceStallTimeoutMs;
const POLL = config.resourceCreationConfig.mappingPersistencePollIntervalMs;

describe('waitForMappingRowsToSettle', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        jest.useFakeTimers();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    it('returns 0 when the mapping total stays at 0 across the stall window', async () => {
        checkMock.mockResolvedValue(status(0) as any);

        const promise = waitForMappingRowsToSettle('CMP-1', 'tenant');
        await jest.advanceTimersByTimeAsync(STALL + POLL);

        await expect(promise).resolves.toBe(0);
    });

    it('returns the final total once the count grows then plateaus', async () => {
        checkMock
            .mockResolvedValueOnce(status(10) as any)
            .mockResolvedValueOnce(status(20) as any)
            .mockResolvedValueOnce(status(30) as any)
            .mockResolvedValue(status(30) as any);

        const promise = waitForMappingRowsToSettle('CMP-2', 'tenant');
        await jest.advanceTimersByTimeAsync(3 * POLL + STALL);

        await expect(promise).resolves.toBe(30);
    });

    it('keeps waiting while the count is still growing', async () => {
        let n = 0;
        checkMock.mockImplementation(async () => status((n += 10)) as any);

        let settled = false;
        const promise = waitForMappingRowsToSettle('CMP-3', 'tenant').then((v) => { settled = true; return v; });

        await jest.advanceTimersByTimeAsync(STALL * 5);

        expect(settled).toBe(false);

        checkMock.mockResolvedValue(status(n) as any);
        await jest.advanceTimersByTimeAsync(STALL + POLL);
        await expect(promise).resolves.toBeGreaterThan(0);
    });
});
