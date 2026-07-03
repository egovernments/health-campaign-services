/**
 * pollUntilCountFn.test.ts
 *
 * Unit tests for the number-polling sibling of pollUntilCount.
 * pollUntilCountFn polls a function that returns a COUNT (number | null)
 * and resolves once that count reaches the expected value — unlike
 * pollUntilCount which measures an array's length (and therefore silently
 * caps at the fetch `limit`). This is the fix for the >5000-rows persistence
 * gate that previously hung until timeout and failed the campaign.
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

import { pollUntilCountFn } from '../utils/genericUtils';

describe('pollUntilCountFn', () => {
    beforeEach(() => jest.clearAllMocks());

    it('resolves immediately when the first count already meets the expected value', async () => {
        const fetchCount = jest.fn().mockResolvedValue(10);

        await expect(
            pollUntilCountFn(fetchCount, 10, { pollIntervalMs: 1, timeoutMs: 1000 })
        ).resolves.toBeUndefined();

        expect(fetchCount).toHaveBeenCalledTimes(1);
    });

    it('resolves immediately for expectedCount=0 (0 >= 0) without polling', async () => {
        const fetchCount = jest.fn().mockResolvedValue(0);

        await pollUntilCountFn(fetchCount, 0, { pollIntervalMs: 1, timeoutMs: 1000 });

        expect(fetchCount).toHaveBeenCalledTimes(1);
    });

    it('keeps polling and resolves once the count reaches expected', async () => {
        const fetchCount = jest
            .fn()
            .mockResolvedValueOnce(1000)
            .mockResolvedValueOnce(4000)
            .mockResolvedValueOnce(7000);

        await pollUntilCountFn(fetchCount, 7000, { pollIntervalMs: 1, timeoutMs: 2000 });

        expect(fetchCount).toHaveBeenCalledTimes(3);
    });

    it('REGRESSION (>5000): resolves when the true count exceeds 5000', async () => {
        // The old pollUntilCount measured Data.length (capped at limit=5000),
        // so a 7000-row campaign never satisfied the gate and timed out.
        // pollUntilCountFn compares the true count instead.
        const fetchCount = jest.fn().mockResolvedValue(7000);

        await expect(
            pollUntilCountFn(fetchCount, 7000, { pollIntervalMs: 1, timeoutMs: 1000 })
        ).resolves.toBeUndefined();
    });

    it('treats a null count as 0 (not ready) and keeps polling', async () => {
        const fetchCount = jest
            .fn()
            .mockResolvedValueOnce(null)
            .mockResolvedValueOnce(null)
            .mockResolvedValueOnce(5);

        await pollUntilCountFn(fetchCount, 5, { pollIntervalMs: 1, timeoutMs: 2000 });

        expect(fetchCount).toHaveBeenCalledTimes(3);
    });

    it('throws a persistence-timeout error when the count never reaches expected', async () => {
        const fetchCount = jest.fn().mockResolvedValue(3);

        await expect(
            pollUntilCountFn(fetchCount, 100, { pollIntervalMs: 1, timeoutMs: 30, label: 'ingestion sheet data' })
        ).rejects.toThrow(/Persistence timeout: ingestion sheet data/);
    });
});

describe('pollUntilCountFn — stall (progress-based) timeout', () => {
    beforeEach(() => jest.clearAllMocks());

    it('resolves while the count keeps increasing (never stalls)', async () => {
        const fetchCount = jest
            .fn()
            .mockResolvedValueOnce(1)
            .mockResolvedValueOnce(2)
            .mockResolvedValueOnce(3)
            .mockResolvedValueOnce(4)
            .mockResolvedValueOnce(5);

        await expect(
            pollUntilCountFn(fetchCount, 5, { pollIntervalMs: 1, stallTimeoutMs: 50 })
        ).resolves.toBeUndefined();

        expect(fetchCount).toHaveBeenCalledTimes(5);
    });

    it('throws a stall error when the count plateaus for stallTimeoutMs', async () => {
        const fetchCount = jest.fn().mockResolvedValue(3); // never grows toward 100

        await expect(
            pollUntilCountFn(fetchCount, 100, { pollIntervalMs: 1, stallTimeoutMs: 30, label: 'ingestion sheet data' })
        ).rejects.toThrow(/Persistence stalled: ingestion sheet data stuck at 3\/100/);
    });

    it('resets the stall clock on progress, failing only after progress stops', async () => {
        // Count climbs to 30 (well past the stall window in elapsed time) then plateaus.
        // The stall can only fire AFTER progress stops, so it must poll > 30 times —
        // proving the clock was reset on every increase rather than firing mid-climb.
        let call = 0;
        const fetchCount = jest.fn(async () => {
            call += 1;
            return Math.min(call, 30);
        });

        await expect(
            pollUntilCountFn(fetchCount, 1000, { pollIntervalMs: 1, stallTimeoutMs: 10 })
        ).rejects.toThrow(/Persistence stalled/);

        expect(fetchCount.mock.calls.length).toBeGreaterThan(30);
    });

    it('resolves immediately in stall mode when the count already meets expected', async () => {
        const fetchCount = jest.fn().mockResolvedValue(10);

        await pollUntilCountFn(fetchCount, 10, { pollIntervalMs: 1, stallTimeoutMs: 30 });

        expect(fetchCount).toHaveBeenCalledTimes(1);
    });
});
