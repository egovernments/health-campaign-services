/**
 * Producer pacing tests for createUsersFromUserData in processingResultHandler.ts.
 *
 * With a small USER_KAFKA_CREATE_BATCH_SIZE a large campaign emits thousands of user-create
 * batch messages; producing them all back-to-back floods the topic instantly. The producer
 * now pauses kafkaProduceWindowDelayMs after every kafkaProduceWindowSize batches. Delay 0
 * (default) means no pacing — behavior unchanged.
 */

jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn().mockResolvedValue(null),
    setCache: jest.fn().mockResolvedValue(undefined),
    deleteCache: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../api/coreApis', () => ({
    searchMDMSDataViaV2Api: jest.fn().mockResolvedValue({}),
    searchBoundaryRelationshipData: jest.fn().mockResolvedValue({}),
}));
jest.mock('../api/campaignApis', () => ({ confirmProjectParentCreation: jest.fn().mockResolvedValue({}) }));
jest.mock('../utils/onGoingCampaignUpdateUtils', () => ({
    fetchProjectsWithBoundaryCodeAndReferenceId: jest.fn().mockResolvedValue([]),
}));
jest.mock('../utils/mailUtils', () => ({ triggerUserCredentialEmailFlow: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/transforms/projectTypeUtils', () => ({
    enrichProjectDetailsFromCampaignDetails: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/localisationUtils', () => ({
    getLocalisationModuleName: jest.fn().mockReturnValue('hcm-admin-console'),
}));
jest.mock('../controllers/localisationController/localisation.controller', () => ({
    __esModule: true,
    default: { getInstance: jest.fn().mockReturnValue({ getLocalisedData: jest.fn().mockResolvedValue([]) }) },
}));
jest.mock('../utils/campaignUtils', () => ({
    populateBoundariesRecursively: jest.fn().mockResolvedValue(undefined),
    getLocalizedName: (k: string) => k,
    enrichAndPersistCampaignWithError: jest.fn(),
    enrichAndPersistCampaignForCreateViaFlow2: jest.fn(),
    userCredGeneration: jest.fn().mockResolvedValue(undefined),
    markAllToCreateResourcesAsCompleted: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn().mockResolvedValue([]),
    getMappingDataRelatedToCampaign: jest.fn().mockResolvedValue([]),
    prepareProcessesInDb: jest.fn().mockResolvedValue(undefined),
    getRelatedDataWithUniqueIdentifiers: jest.fn().mockResolvedValue([]),
    checkCampaignDataCompletionStatus: jest.fn().mockResolvedValue({ allCompleted: true, anyFailed: false }),
    checkCampaignMappingCompletionStatus: jest.fn().mockResolvedValue({ allCompleted: true }),
    throwError: jest.fn(),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    pollUntilCount: jest.fn().mockResolvedValue(undefined),
    pollUntilCountFn: jest.fn().mockResolvedValue(undefined),
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: { KAFKA_USER_CREATE_BATCH_TOPIC: 'user-batch', KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet' },
        excelIngestion: { persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
        user: { kafkaCreateBatchSize: 1, kafkaProduceWindowSize: 2, kafkaProduceWindowDelayMs: 0 },
        DB_CONFIG: { DB_USER: 'u', DB_HOST: 'h', DB_NAME: 'n', DB_PASSWORD: 'p', DB_PORT: 5432, DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
    },
}));

import config from '../config';
import { createUsersFromUserData } from '../utils/processingResultHandler';
import { produceModifiedMessages } from '../kafka/Producer';
import { getRelatedDataWithCampaign } from '../utils/genericUtils';

const produceMock = produceModifiedMessages as jest.Mock;
const getRelatedMock = getRelatedDataWithCampaign as jest.Mock;

const CAMPAIGN = { campaignNumber: 'CMP-1', id: 'camp-1', parentId: null, auditDetails: { createdBy: 'u1' } };
const REQ = { userInfo: { uuid: 'u1' } } as any;

function pendingUsers(n: number) {
    return Array.from({ length: n }, (_, i) => ({
        uniqueIdentifier: `700000${i}`,
        status: 'pending',
        data: { '#status#': 'CREATED' },
    }));
}

describe('createUsersFromUserData producer pacing', () => {
    let setTimeoutSpy: jest.SpyInstance;

    beforeEach(() => {
        jest.clearAllMocks();
        (config as any).user.kafkaCreateBatchSize = 1;   // 1 user per batch → n batches
        (config as any).user.kafkaProduceWindowSize = 2;
        (config as any).user.kafkaProduceWindowDelayMs = 0;
        setTimeoutSpy = jest.spyOn(global, 'setTimeout');
    });
    afterEach(() => setTimeoutSpy.mockRestore());

    const pacingCalls = () => setTimeoutSpy.mock.calls.filter((c: any[]) => c[1] === 5).length;

    it('produces every batch and does not pace when delay is 0 (default)', async () => {
        getRelatedMock.mockResolvedValue(pendingUsers(6));
        await createUsersFromUserData(CAMPAIGN, 'mz', REQ);

        expect(produceMock).toHaveBeenCalledTimes(6);
        expect(pacingCalls()).toBe(0);
    });

    it('pauses after every windowSize batches when delay > 0, but not after the last batch', async () => {
        (config as any).user.kafkaProduceWindowDelayMs = 5;
        getRelatedMock.mockResolvedValue(pendingUsers(6)); // 6 batches, window 2 → pause after 2 and 4 (not 6)
        await createUsersFromUserData(CAMPAIGN, 'mz', REQ);

        expect(produceMock).toHaveBeenCalledTimes(6);
        expect(pacingCalls()).toBe(2);
    });

    it('does not pause when all batches fit within a single window', async () => {
        (config as any).user.kafkaProduceWindowDelayMs = 5;
        (config as any).user.kafkaProduceWindowSize = 100;
        getRelatedMock.mockResolvedValue(pendingUsers(6));
        await createUsersFromUserData(CAMPAIGN, 'mz', REQ);

        expect(produceMock).toHaveBeenCalledTimes(6);
        expect(pacingCalls()).toBe(0);
    });

    it('produces nothing when there are no users to create', async () => {
        getRelatedMock.mockResolvedValue([]);
        await createUsersFromUserData(CAMPAIGN, 'mz', REQ);
        expect(produceMock).not.toHaveBeenCalled();
    });
});
