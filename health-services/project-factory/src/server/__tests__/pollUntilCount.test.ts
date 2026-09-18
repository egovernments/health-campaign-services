/**
 * pollUntilCount.test.ts
 *
 * Tests the array-based pollUntilCount<T>, including the progress-based
 * (`stallTimeoutMs`) waiting mode added so the background boundary/user
 * persistence polls are no longer failed on an absolute deadline while rows
 * are still landing — they fail only if the count stops growing.
 *
 * Only ../utils/db and ../utils/logger need mocking for genericUtils to import
 * cleanly (same pattern as checkCampaignMappingCompletionStatus.test.ts).
 */

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn(),
    getTableName: (name: string) => name,
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

import { pollUntilCount } from '../utils/genericUtils';

const arr = (n: number) => Array.from({ length: n }, (_, i) => ({ id: i }));

describe('pollUntilCount<T> — absolute (legacy) mode', () => {
    beforeEach(() => jest.clearAllMocks());

    it('returns the fetched array once its length meets expected', async () => {
        const fetchFn = jest.fn().mockResolvedValue(arr(5));

        const result = await pollUntilCount(fetchFn, 5, { pollIntervalMs: 1, timeoutMs: 1000 });

        expect(result).toHaveLength(5);
        expect(fetchFn).toHaveBeenCalledTimes(1);
    });

    it('keeps polling until the array grows to expected, then returns it', async () => {
        const fetchFn = jest
            .fn()
            .mockResolvedValueOnce(arr(2))
            .mockResolvedValueOnce(arr(4))
            .mockResolvedValueOnce(arr(7));

        const result = await pollUntilCount(fetchFn, 7, { pollIntervalMs: 1, timeoutMs: 2000 });

        expect(result).toHaveLength(7);
        expect(fetchFn).toHaveBeenCalledTimes(3);
    });

    it('treats a null fetch result as length 0', async () => {
        const fetchFn = jest
            .fn()
            .mockResolvedValueOnce(null)
            .mockResolvedValueOnce(arr(3));

        const result = await pollUntilCount(fetchFn, 3, { pollIntervalMs: 1, timeoutMs: 2000 });

        expect(result).toHaveLength(3);
        expect(fetchFn).toHaveBeenCalledTimes(2);
    });

    it('throws a persistence-timeout error when the array never reaches expected', async () => {
        const fetchFn = jest.fn().mockResolvedValue(arr(2));

        await expect(
            pollUntilCount(fetchFn, 100, { pollIntervalMs: 1, timeoutMs: 30, label: 'boundary data' })
        ).rejects.toThrow(/Persistence timeout: boundary data/);
    });
});

describe('pollUntilCount<T> — stall (progress-based) mode', () => {
    beforeEach(() => jest.clearAllMocks());

    it('returns the array while it keeps growing (never stalls)', async () => {
        const fetchFn = jest
            .fn()
            .mockResolvedValueOnce(arr(1))
            .mockResolvedValueOnce(arr(3))
            .mockResolvedValueOnce(arr(5));

        const result = await pollUntilCount(fetchFn, 5, { pollIntervalMs: 1, stallTimeoutMs: 50 });

        expect(result).toHaveLength(5);
        expect(fetchFn).toHaveBeenCalledTimes(3);
    });

    it('throws a stall error when the array length plateaus', async () => {
        const fetchFn = jest.fn().mockResolvedValue(arr(3)); // never grows toward 100

        await expect(
            pollUntilCount(fetchFn, 100, { pollIntervalMs: 1, stallTimeoutMs: 30, label: 'user data' })
        ).rejects.toThrow(/Persistence stalled: user data stuck at 3\/100/);
    });

    it('resets the stall clock on growth, failing only after growth stops', async () => {
        let call = 0;
        const fetchFn = jest.fn(async () => arr(Math.min(++call, 30)));

        await expect(
            pollUntilCount(fetchFn, 1000, { pollIntervalMs: 1, stallTimeoutMs: 10 })
        ).rejects.toThrow(/Persistence stalled/);

        // Must have polled past the 30-row climb — proving the clock reset on each growth.
        expect(fetchFn.mock.calls.length).toBeGreaterThan(30);
    });

    it('returns immediately in stall mode when the array already meets expected', async () => {
        const fetchFn = jest.fn().mockResolvedValue(arr(10));

        const result = await pollUntilCount(fetchFn, 10, { pollIntervalMs: 1, stallTimeoutMs: 30 });

        expect(result).toHaveLength(10);
        expect(fetchFn).toHaveBeenCalledTimes(1);
    });
});
