import { waitForIndividualsSearchable } from '../utils/userBatchHandler';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/transFormUtil', () => ({ DataTransformer: jest.fn() }));
jest.mock('../config/transformConfigs', () => ({ transformConfigs: {} }));
jest.mock('../utils/cryptUtils', () => ({ encrypt: (v: string) => v }));
jest.mock('../utils/workerRegistryUtils', () => ({ createOrUpdateWorkers: jest.fn() }));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { healthIndividualHost: 'http://ind/' },
        paths: { healthIndividualSearch: 'search' },
        user: {
            individualSearchBatchSize: 2,
            individualConsistencyPollIntervalMs: 1000,
            individualConsistencyMaxPollAttempts: 3,
        },
    },
}));

import { httpRequest } from '../utils/request';

const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;
const reqInfo: any = { userInfo: { uuid: 'u', tenantId: 'mz' } };
const indResp = (...ids: string[]) => ({ Individual: ids.map(id => ({ id })) });

describe('waitForIndividualsSearchable', () => {
    afterEach(() => jest.clearAllMocks());

    describe('no-op cases', () => {
        it('returns empty without any search when given no ids', async () => {
            const res = await waitForIndividualsSearchable([], 'mz', reqInfo);
            expect(res.missing).toEqual([]);
            expect(res.found.size).toBe(0);
            expect(httpMock).not.toHaveBeenCalled();
        });

        it('ignores falsy ids and dedups before searching', async () => {
            httpMock.mockResolvedValueOnce(indResp('a'));
            const res = await waitForIndividualsSearchable(['a', 'a', ''], 'mz', reqInfo);
            expect(res.missing).toEqual([]);
            expect(httpMock).toHaveBeenCalledTimes(1);
        });
    });

    describe('first-attempt success', () => {
        it('resolves on the first attempt when all ids are searchable', async () => {
            httpMock.mockResolvedValueOnce(indResp('a', 'b'));
            const res = await waitForIndividualsSearchable(['a', 'b'], 'mz', reqInfo);
            expect(res.found.has('a')).toBe(true);
            expect(res.found.has('b')).toBe(true);
            expect(res.missing).toEqual([]);
            expect(httpMock).toHaveBeenCalledTimes(1);
        });

        it('chunks the search by individualSearchBatchSize', async () => {
            httpMock.mockResolvedValueOnce(indResp('a', 'b')).mockResolvedValueOnce(indResp('c'));
            const res = await waitForIndividualsSearchable(['a', 'b', 'c'], 'mz', reqInfo);
            expect(res.missing).toEqual([]);
            expect(httpMock).toHaveBeenCalledTimes(2);
        });
    });

    describe('retry behaviour', () => {
        beforeEach(() => jest.useFakeTimers());
        afterEach(() => jest.useRealTimers());

        it('retries and succeeds once the lagging individual becomes searchable', async () => {
            httpMock.mockResolvedValueOnce(indResp('a')).mockResolvedValueOnce(indResp('b'));
            const promise = waitForIndividualsSearchable(['a', 'b'], 'mz', reqInfo);
            await jest.advanceTimersByTimeAsync(1000);
            const res = await promise;
            expect(res.missing).toEqual([]);
            expect(res.found.size).toBe(2);
            expect(httpMock).toHaveBeenCalledTimes(2);
        });

        it('only re-searches the still-missing ids on later attempts', async () => {
            httpMock.mockResolvedValueOnce(indResp('a')).mockResolvedValueOnce(indResp('b'));
            const promise = waitForIndividualsSearchable(['a', 'b'], 'mz', reqInfo);
            await jest.advanceTimersByTimeAsync(1000);
            await promise;
            expect((httpMock.mock.calls[1][1] as any).Individual.id).toEqual(['b']);
        });

        it('fails open with the missing ids after exhausting all attempts', async () => {
            httpMock.mockResolvedValue(indResp('a'));
            const promise = waitForIndividualsSearchable(['a', 'b'], 'mz', reqInfo);
            await jest.advanceTimersByTimeAsync(3000);
            const res = await promise;
            expect(res.missing).toEqual(['b']);
            expect(res.found.has('a')).toBe(true);
            expect(httpMock).toHaveBeenCalledTimes(3);
        });
    });

    describe('error resilience', () => {
        beforeEach(() => jest.useFakeTimers());
        afterEach(() => jest.useRealTimers());

        it('treats a search error as non-fatal and recovers on a later attempt', async () => {
            httpMock.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(indResp('a'));
            const promise = waitForIndividualsSearchable(['a'], 'mz', reqInfo);
            await jest.advanceTimersByTimeAsync(1000);
            const res = await promise;
            expect(res.missing).toEqual([]);
            expect(res.found.has('a')).toBe(true);
        });
    });
});
