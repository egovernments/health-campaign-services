/**
 * createFacilitiesFromFacilityData waits for every facility row to be saved before it reads them.
 *
 * Facility rows reach campaign data through Kafka and the persister, so a single read right after
 * they are sent can see only part of them; the rest would stay pending and the campaign would time out.
 * With the expected row count it polls (as boundaries and users do) and sends batches for all rows.
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
    pollUntilCount: jest.fn(),
    pollUntilCountFn: jest.fn().mockResolvedValue(undefined),
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        basesecret: 'test-base-secret',
        kafka: { KAFKA_FACILITY_CREATE_BATCH_TOPIC: 'facility-batch' },
        excelIngestion: { persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
        facility: { kafkaCreateBatchSize: 2 },
        DB_CONFIG: { DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
    },
}));

import { createFacilitiesFromFacilityData } from '../utils/processingResultHandler';
import { produceModifiedMessages } from '../kafka/Producer';
import { getRelatedDataWithCampaign, pollUntilCount } from '../utils/genericUtils';

const produceMock = jest.mocked(produceModifiedMessages);
const getRelatedMock = jest.mocked(getRelatedDataWithCampaign);
const pollMock = jest.mocked(pollUntilCount);

const CAMPAIGN = { campaignNumber: 'CMP-1', id: 'camp-1', parentId: null, auditDetails: { createdBy: 'u1' } };
const REQ = { userInfo: { uuid: 'u1' } } as any;

function pendingFacilities(n: number): any[] {
    return Array.from({ length: n }, (_, i) => ({ uniqueIdentifier: `Facility ${i}`, status: 'pending', data: {} }));
}

const sentFacilityKeys = (): string[] =>
    produceMock.mock.calls.flatMap((c: any[]) => Object.keys(c[0]?.facilityData || {}));

describe('createFacilitiesFromFacilityData', () => {
    afterEach(() => jest.clearAllMocks());

    describe('with the expected facility row count', () => {
        it('polls campaign data until that many facility rows are saved', async () => {
            pollMock.mockResolvedValue(pendingFacilities(4) as any);
            await createFacilitiesFromFacilityData(CAMPAIGN, 'mz', REQ, 4);
            expect(pollMock).toHaveBeenCalledWith(expect.any(Function), 4,
                { label: 'facility data', stallTimeoutMs: 120000, pollIntervalMs: 10000 });
        });

        it('reads facility rows of this campaign in each poll', async () => {
            pollMock.mockImplementation(async (fetchFn: any) => fetchFn());
            getRelatedMock.mockResolvedValue(pendingFacilities(1) as any);
            await createFacilitiesFromFacilityData(CAMPAIGN, 'mz', REQ, 1);
            expect(getRelatedMock).toHaveBeenCalledWith('facility', 'CMP-1', 'mz');
        });

        it('sends batches for every row returned by the poll', async () => {
            pollMock.mockResolvedValue(pendingFacilities(5) as any);
            await createFacilitiesFromFacilityData(CAMPAIGN, 'mz', REQ, 5);
            expect(sentFacilityKeys()).toHaveLength(5);
            expect(produceMock).toHaveBeenCalledTimes(3);
        });

        it('sends nothing and fails when the rows stop arriving', async () => {
            pollMock.mockRejectedValue(new Error('Persistence stalled: facility data stuck at 2/5 for 120000ms'));
            await expect(createFacilitiesFromFacilityData(CAMPAIGN, 'mz', REQ, 5)).rejects.toThrow('Persistence stalled');
            expect(produceMock).not.toHaveBeenCalled();
        });
    });

    describe('without an expected count', () => {
        it.each([undefined, 0])('reads campaign data once when the count is %s', async (count) => {
            getRelatedMock.mockResolvedValue(pendingFacilities(2) as any);
            await createFacilitiesFromFacilityData(CAMPAIGN, 'mz', REQ, count);
            expect(pollMock).not.toHaveBeenCalled();
            expect(getRelatedMock).toHaveBeenCalledTimes(1);
            expect(sentFacilityKeys()).toHaveLength(2);
        });
    });
});
