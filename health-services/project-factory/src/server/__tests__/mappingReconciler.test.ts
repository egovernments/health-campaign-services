/**
 * mappingReconciler.test.ts
 *
 * Tests the convergence-driven mapping reconciler in processingResultHandler:
 *   - happy path → concludes without failure message
 *   - retryable failures → SQL reset + new generation, converges next cycle
 *   - terminal facility/resource failures → campaign failed
 *   - user-only failures → non-blocking
 *   - campaign already failed → early exit via flag
 *   - cycle budget exhaustion → campaign failed with counts
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
    throwError: jest.fn().mockImplementation((_module: any, status: any, code: any, description: any) => {
        throw Object.assign(new Error(description || code), { status, code });
    }),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    pollUntilCount: jest.fn().mockResolvedValue(undefined),
    pollUntilCountFn: jest.fn(),
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

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn().mockResolvedValue({ rows: [], rowCount: 0 }),
    getTableName: (name: string) => name,
}));

jest.mock('../utils/mappingGenerationUtils', () => ({
    bumpMappingGeneration: jest.fn().mockResolvedValue(1),
    getCurrentMappingGeneration: jest.fn().mockResolvedValue(null),
}));

import { runMappingReconciler } from '../utils/mappingReconciler';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { sendCampaignFailureMessage } from '../utils/campaignFailureHandler';
import { checkCampaignMappingCompletionStatus, getMappingDataRelatedToCampaign, pollUntilCountFn } from '../utils/genericUtils';
import { executeQuery } from '../utils/db';
import { bumpMappingGeneration } from '../utils/mappingGenerationUtils';
import { produceModifiedMessages } from '../kafka/Producer';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const sendCampaignFailureMessageMock = sendCampaignFailureMessage as jest.MockedFunction<typeof sendCampaignFailureMessage>;
const checkMappingStatusMock = checkCampaignMappingCompletionStatus as jest.MockedFunction<typeof checkCampaignMappingCompletionStatus>;
const getMappingDataMock = getMappingDataRelatedToCampaign as jest.MockedFunction<typeof getMappingDataRelatedToCampaign>;
const pollUntilCountFnMock = pollUntilCountFn as jest.MockedFunction<typeof pollUntilCountFn>;
const executeQueryMock = executeQuery as jest.MockedFunction<typeof executeQuery>;
const bumpMappingGenerationMock = bumpMappingGeneration as jest.MockedFunction<typeof bumpMappingGeneration>;
const produceModifiedMessagesMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;

const campaignDetails = { id: 'cmp-id-1', campaignNumber: 'CMP-1' };

function mkStatus(overrides: Partial<Record<string, number | boolean>> = {}) {
    return {
        allCompleted: false,
        anyFailed: false,
        totalMappings: 0,
        completedMappings: 0,
        failedMappings: 0,
        terminallyFailedMappings: 0,
        retryableFailedMappings: 0,
        pendingMappings: 0,
        ...overrides,
    } as any;
}

type World = { overall: any; facility: any; resource: any; user: any };

function wireWorld(world: World) {
    checkMappingStatusMock.mockImplementation((_c: string, _t: string, type?: string) =>
        Promise.resolve(type ? world[type as keyof World] : world.overall)
    );
}

describe('runMappingReconciler', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [{ status: 'creating' }] } as any);
        getMappingDataMock.mockResolvedValue([]);
        executeQueryMock.mockResolvedValue({ rows: [], rowCount: 0 } as any);
        bumpMappingGenerationMock.mockResolvedValue(1 as any);
        // Realistic observe: poll the count fn until it reaches expected, capped — then stall.
        pollUntilCountFnMock.mockImplementation(async (fetchCountFn: any, expectedCount: number) => {
            for (let i = 0; i < 5; i++) {
                const count = (await fetchCountFn()) ?? 0;
                if (count >= expectedCount) return;
            }
            throw new Error('stalled');
        });
    });

    it('concludes cleanly when all mappings resolve with no failures', async () => {
        const clean = mkStatus({ totalMappings: 2, completedMappings: 2, allCompleted: true });
        wireWorld({ overall: clean, facility: clean, resource: clean, user: clean });

        const flag = { value: false };
        await runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, flag);

        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
        expect(flag.value).toBe(false);
    });

    it('returns without reconciling when the campaign has no mappings at all', async () => {
        const empty = mkStatus();
        wireWorld({ overall: empty, facility: empty, resource: empty, user: empty });

        await runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, { value: false });

        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
        expect(produceModifiedMessagesMock).not.toHaveBeenCalled();
    });

    it('resets retryable failures via SQL and converges on the next cycle with a new generation', async () => {
        const world: World = {
            overall: mkStatus({ totalMappings: 2, completedMappings: 1, failedMappings: 1, retryableFailedMappings: 1, anyFailed: true }),
            facility: mkStatus(),
            resource: mkStatus({ totalMappings: 2, completedMappings: 1, failedMappings: 1, retryableFailedMappings: 1, anyFailed: true }),
            user: mkStatus(),
        };
        wireWorld(world);

        // The reset runs at the start of every cycle and issues two direction
        // UPDATEs (failed→toBeMapped, deMapFailed→toBeDeMapped): cycle 1's pair is
        // a no-op (failure happens during that cycle); cycle 2's pair resolves it.
        let resetCallCount = 0;
        executeQueryMock.mockImplementation(async (sql: string) => {
            if (String(sql).includes('retryCount = retryCount + 1')) {
                resetCallCount++;
                if (resetCallCount >= 3) {
                    const clean = mkStatus({ totalMappings: 2, completedMappings: 2, allCompleted: true });
                    world.overall = clean;
                    world.resource = clean;
                }
                return { rows: [], rowCount: resetCallCount >= 3 ? 1 : 0 } as any;
            }
            return { rows: [], rowCount: 0 } as any;
        });

        await runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, { value: false });

        const resetCalls = executeQueryMock.mock.calls.filter(([sql]) => String(sql).includes('retryCount = retryCount + 1'));
        expect(resetCalls.length).toBeGreaterThanOrEqual(3);
        expect(bumpMappingGenerationMock).toHaveBeenCalledTimes(2);
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
    });

    it('fails the campaign when facility/resource mappings are terminally failed', async () => {
        const world: World = {
            overall: mkStatus({ totalMappings: 2, completedMappings: 1, failedMappings: 1, terminallyFailedMappings: 1, anyFailed: true }),
            facility: mkStatus({ totalMappings: 1, failedMappings: 1, terminallyFailedMappings: 1, anyFailed: true }),
            resource: mkStatus(),
            user: mkStatus(),
        };
        wireWorld(world);

        await expect(
            runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, { value: false })
        ).rejects.toThrow('Mapping failed after retries');

        expect(sendCampaignFailureMessageMock).toHaveBeenCalledTimes(1);
    });

    it('tolerates user-only terminal failures as non-blocking', async () => {
        const world: World = {
            overall: mkStatus({ totalMappings: 3, completedMappings: 2, failedMappings: 1, terminallyFailedMappings: 1, anyFailed: true }),
            facility: mkStatus(),
            resource: mkStatus(),
            user: mkStatus({ totalMappings: 1, failedMappings: 1, terminallyFailedMappings: 1, anyFailed: true }),
        };
        wireWorld(world);

        await runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, { value: false });

        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
    });

    it('stops without concluding when the campaign is marked failed during the observe phase', async () => {
        const clean = mkStatus({ totalMappings: 2, completedMappings: 2, allCompleted: true });
        wireWorld({ overall: clean, facility: clean, resource: clean, user: clean });

        // Cycle-start check sees a live campaign; the post-observe check sees it failed.
        searchCampaignMock
            .mockResolvedValueOnce({ CampaignDetails: [{ status: 'creating' }] } as any)
            .mockResolvedValue({ CampaignDetails: [{ status: 'failed' }] } as any);

        const flag = { value: false };
        await runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, flag);

        expect(flag.value).toBe(true);
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
        const processUpdates = produceModifiedMessagesMock.mock.calls.filter(c => Array.isArray((c[0] as any)?.processes));
        expect(processUpdates).toHaveLength(0);
    });

    it('exits early and sets the flag when the campaign is already marked failed', async () => {
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [{ status: 'failed' }] } as any);

        const flag = { value: false };
        await runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, flag);

        expect(flag.value).toBe(true);
        expect(bumpMappingGenerationMock).not.toHaveBeenCalled();
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
    });

    it('fails the campaign with precise counts after exhausting the cycle budget on a permanent stall', async () => {
        const stuck = mkStatus({ totalMappings: 2, completedMappings: 1, pendingMappings: 1 });
        wireWorld({ overall: stuck, facility: mkStatus(), resource: mkStatus(), user: mkStatus() });

        await expect(
            runMappingReconciler(campaignDetails, 'u-uuid', 'tn', undefined, { value: false })
        ).rejects.toThrow('Mapping reconciliation exhausted');

        expect(sendCampaignFailureMessageMock).toHaveBeenCalledTimes(1);
        const [, , error] = sendCampaignFailureMessageMock.mock.calls[0];
        expect(String((error as Error).message)).toContain('1 pending');
    });
});
