import { TemplateClass } from '../processFlowClasses/user-processClass';
import { dataRowStatuses, sheetDataRowStatuses } from '../config/constants';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
    getRelatedDataWithUniqueIdentifiers: jest.fn(),
    getMappingDataRelatedToCampaign: jest.fn(),
}));
jest.mock('../utils/campaignUtils', () => ({ getLocalizedName: (k: string) => k }));
jest.mock('../utils/sheetManageUtils', () => ({ validateResourceDetailsBeforeProcess: jest.fn() }));
jest.mock('../utils/paymentValidationUtils', () => ({ validatePaymentFields: jest.fn() }));
jest.mock('../utils/transFormUtil', () => ({ DataTransformer: jest.fn() }));
jest.mock('../config/transformConfigs', () => ({ transformConfigs: {} }));
jest.mock('../utils/cryptUtils', () => ({ encrypt: (v: any) => v, decrypt: (v: any) => v }));
jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn(),
    searchWorkersByIds: jest.fn().mockResolvedValue([]),
}));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { healthIndividualHost: 'http://ind/' },
        paths: { healthIndividualSearch: 'search' },
        kafka: { KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet' },
        user: {
            persistBatchSize: 100,
            individualSearchBatchSize: 50,
            individualConsistencyPollIntervalMs: 1,
            individualConsistencyMaxPollAttempts: 1,
            workerCreateBatchLag: 2,
        },
    },
}));

import { httpRequest } from '../utils/request';
import { produceModifiedMessages } from '../kafka/Producer';
import { createOrUpdateWorkers } from '../utils/workerRegistryUtils';

const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;
const persistMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const workerMock = createOrUpdateWorkers as jest.MockedFunction<typeof createOrUpdateWorkers>;

const finalize = (buffered: any) =>
    (TemplateClass as any).finalizeUserBatchWorkers(buffered, { tenantId: 'mz', requestInfo: { userInfo: { uuid: 'u' } } });

// A campaign record plus its worker payload for one individual.
const makeEntry = (individualId: string) => {
    const record: any = { status: dataRowStatuses.completed, data: {} };
    const worker: any = { individualId, name: 'n', tenantId: 'mz' };
    return { record, worker };
};

const buildBuffered = (individualIds: string[]) => {
    const successfulUsers: any[] = [];
    const workerDataList: any[] = [];
    const individualIdToRecords = new Map<string, any[]>();
    for (const id of individualIds) {
        const { record, worker } = makeEntry(id);
        successfulUsers.push(record);
        workerDataList.push(worker);
        individualIdToRecords.set(id, [record]);
    }
    return { successfulUsers, workerDataList, individualIdToRecords };
};

const searchable = (...ids: string[]) => ({ Individual: ids.map(id => ({ id })) });

describe('finalizeUserBatchWorkers', () => {
    afterEach(() => jest.clearAllMocks());

    it('creates workers for all when every individual is searchable, then persists', async () => {
        const buffered = buildBuffered(['i1', 'i2']);
        httpMock.mockResolvedValueOnce(searchable('i1', 'i2') as any);
        workerMock.mockResolvedValueOnce({ individualIdToWorkerIdMap: new Map([['i1', 'w1'], ['i2', 'w2']]), errors: [] } as any);

        await finalize(buffered);

        expect(workerMock).toHaveBeenCalledTimes(1);
        expect(workerMock.mock.calls[0][0].map((w: any) => w.individualId)).toEqual(['i1', 'i2']);
        expect(buffered.individualIdToRecords.get('i1')![0].data['HCM_ADMIN_CONSOLE_USER_WORKER_ID']).toBe('w1');
        expect(buffered.successfulUsers.every(r => r.status === dataRowStatuses.completed)).toBe(true);
        expect(persistMock).toHaveBeenCalledWith({ datas: buffered.successfulUsers }, 'update-sheet', 'mz');
    });

    it('defers still-missing individuals: only searchable ones are sent to worker-registry', async () => {
        const buffered = buildBuffered(['i1', 'i2']);
        httpMock.mockResolvedValueOnce(searchable('i1') as any); // i2 not searchable
        workerMock.mockResolvedValueOnce({ individualIdToWorkerIdMap: new Map([['i1', 'w1']]), errors: [] } as any);

        await finalize(buffered);

        expect(workerMock).toHaveBeenCalledTimes(1);
        expect(workerMock.mock.calls[0][0].map((w: any) => w.individualId)).toEqual(['i1']);
        // i2 deferred → marked retryable-failed, not sent to create
        const i2 = buffered.individualIdToRecords.get('i2')![0];
        expect(i2.status).toBe(dataRowStatuses.failed);
        expect(i2.data['#status#']).toBe(sheetDataRowStatuses.FAILED);
        expect(i2.data['#errorDetails#']).toMatch(/deferred for retry/i);
        // persist still runs for the whole batch
        expect(persistMock).toHaveBeenCalledTimes(1);
    });

    it('when no individual is searchable, never calls worker-registry but still persists', async () => {
        const buffered = buildBuffered(['i1', 'i2']);
        httpMock.mockResolvedValueOnce(searchable() as any); // none found

        await finalize(buffered);

        expect(workerMock).not.toHaveBeenCalled();
        for (const id of ['i1', 'i2']) {
            expect(buffered.individualIdToRecords.get(id)![0].status).toBe(dataRowStatuses.failed);
        }
        expect(persistMock).toHaveBeenCalledTimes(1);
    });

    it('persists even when there are no workers to create', async () => {
        const buffered = { successfulUsers: [{ status: dataRowStatuses.completed, data: {} }], workerDataList: [], individualIdToRecords: new Map() };
        await finalize(buffered);
        expect(httpMock).not.toHaveBeenCalled();
        expect(workerMock).not.toHaveBeenCalled();
        expect(persistMock).toHaveBeenCalledTimes(1);
    });

    it('marks rows failed for workers that come back without an id (partial failure), still persists', async () => {
        const buffered = buildBuffered(['i1', 'i2']);
        httpMock.mockResolvedValueOnce(searchable('i1', 'i2') as any);
        workerMock.mockResolvedValueOnce({ individualIdToWorkerIdMap: new Map([['i1', 'w1']]), errors: ['boom for i2'] } as any);

        await finalize(buffered);

        expect(buffered.individualIdToRecords.get('i1')![0].data['HCM_ADMIN_CONSOLE_USER_WORKER_ID']).toBe('w1');
        expect(buffered.individualIdToRecords.get('i2')![0].status).toBe(dataRowStatuses.failed);
        expect(persistMock).toHaveBeenCalledTimes(1);
    });
});
