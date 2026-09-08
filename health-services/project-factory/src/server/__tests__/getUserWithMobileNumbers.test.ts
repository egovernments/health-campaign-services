/**
 * Tests for getUserWithMobileNumbers — individual search-by-mobile-number must
 * run batch-wise (chunked by searchBatchSize) with bounded concurrency
 * (searchConcurrency windows) rather than firing every batch at once.
 */

import { getUserWithMobileNumbers } from '../api/campaignApis';
import { httpRequest } from '../utils/request';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn(),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn().mockResolvedValue(null),
    setCache: jest.fn().mockResolvedValue(undefined),
    deleteCache: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../kafka/Producer', () => ({
    producer: { connect: jest.fn(), send: jest.fn() },
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { healthIndividualHost: 'http://individual/' },
        paths: { healthIndividualSearch: 'health-individual/v1/_search' },
        user: { searchBatchSize: 2, searchConcurrency: 2 },
        values: { notCreateUserIfAlreadyThere: false },
        DB_CONFIG: {
            DB_USER: 'u', DB_HOST: 'h', DB_NAME: 'n', DB_PASSWORD: 'p', DB_PORT: '5432',
        },
    },
}));

const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;

function request() {
    return { body: { RequestInfo: {}, ResourceDetails: { tenantId: 'mz' } } };
}

describe('getUserWithMobileNumbers', () => {
    afterEach(() => jest.clearAllMocks());

    it('returns an empty set when no mobile numbers exist in the individual service', async () => {
        httpMock.mockResolvedValue({ Individual: [] });

        const result = await getUserWithMobileNumbers(request(), ['1', '2', '3'], {});

        expect(result.size).toBe(0);
    });

    it('chunks the search by searchBatchSize — one call per batch of 2', async () => {
        httpMock.mockResolvedValue({ Individual: [] });

        // 5 numbers, batch size 2 → 3 batches → 3 calls
        await getUserWithMobileNumbers(request(), ['1', '2', '3', '4', '5'], {});

        expect(httpMock).toHaveBeenCalledTimes(3);
        // each call carries at most searchBatchSize numbers
        for (const call of httpMock.mock.calls) {
            const body: any = call[1];
            expect(body.Individual.mobileNumber.length).toBeLessThanOrEqual(2);
        }
    });

    it('passes limit derived from searchBatchSize, not a hardcoded value', async () => {
        httpMock.mockResolvedValue({ Individual: [] });

        await getUserWithMobileNumbers(request(), ['1', '2'], {});

        const params: any = httpMock.mock.calls[0][2];
        expect(params.limit).toBe(7); // searchBatchSize (2) + 5
    });

    it('aggregates and deduplicates existing mobile numbers across all batches', async () => {
        httpMock
            .mockResolvedValueOnce({ Individual: [{ mobileNumber: '1' }] })
            .mockResolvedValueOnce({ Individual: [{ mobileNumber: '3' }, { mobileNumber: '3' }] })
            .mockResolvedValueOnce({ Individual: [] });

        const result = await getUserWithMobileNumbers(request(), ['1', '2', '3', '4', '5'], {});

        expect([...result].sort()).toEqual(['1', '3']);
    });

    it('runs batches in bounded concurrency windows, not all at once', async () => {
        let inFlight = 0;
        let maxInFlight = 0;
        httpMock.mockImplementation(async () => {
            inFlight++;
            maxInFlight = Math.max(maxInFlight, inFlight);
            await Promise.resolve();
            inFlight--;
            return { Individual: [] };
        });

        // 8 numbers, batch 2 → 4 batches; concurrency 2 → never more than 2 concurrent
        await getUserWithMobileNumbers(request(), ['1', '2', '3', '4', '5', '6', '7', '8'], {});

        expect(httpMock).toHaveBeenCalledTimes(4);
        expect(maxInFlight).toBeLessThanOrEqual(2);
    });

    it('returns an empty set for an empty input list without calling the service', async () => {
        const result = await getUserWithMobileNumbers(request(), [], {});

        expect(result.size).toBe(0);
        expect(httpMock).not.toHaveBeenCalled();
    });
});
