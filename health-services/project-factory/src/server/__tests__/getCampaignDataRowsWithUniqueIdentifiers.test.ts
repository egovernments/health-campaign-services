/**
 * getCampaignDataRowsWithUniqueIdentifiers scoping tests.
 *
 * eg_cm_campaign_data's primary key is (campaignNumber, uniqueIdentifier, type) — uniqueIdentifier
 * is only unique per campaign, not tenant-wide. Any redelivery/idempotency check built on this
 * function must pass campaignNumber, or it will match completed rows from an unrelated campaign
 * that happens to reuse the same identifiers (the facility-batch stall this covers).
 */

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn(),
    getTableName: (name: string) => name,
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

import { getCampaignDataRowsWithUniqueIdentifiers } from '../utils/genericUtils';
import { executeQuery } from '../utils/db';

const executeQueryMock = executeQuery as jest.MockedFunction<typeof executeQuery>;

describe('getCampaignDataRowsWithUniqueIdentifiers', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('returns [] without querying when no identifiers are given', async () => {
        const rows = await getCampaignDataRowsWithUniqueIdentifiers('facility', [], 'mz');
        expect(rows).toEqual([]);
        expect(executeQueryMock).not.toHaveBeenCalled();
    });

    it('filters by type and uniqueIdentifier only when status and campaignNumber are omitted', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await getCampaignDataRowsWithUniqueIdentifiers('facility', ['a', 'b'], 'mz');

        const [query, params] = executeQueryMock.mock.calls[0];
        expect(query).not.toMatch(/campaignNumber/i);
        expect(query).not.toMatch(/status/i);
        expect(params).toEqual(['facility', ['a', 'b']]);
    });

    it('adds a campaignNumber filter and parameter when campaignNumber is supplied', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await getCampaignDataRowsWithUniqueIdentifiers('facility', ['a', 'b'], 'mz', undefined, 'CMP-1');

        const [query, params] = executeQueryMock.mock.calls[0];
        expect(query).toMatch(/AND campaignNumber = \$3/);
        expect(params).toEqual(['facility', ['a', 'b'], 'CMP-1']);
    });

    it('adds both campaignNumber and status filters, in that order, when both are supplied', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await getCampaignDataRowsWithUniqueIdentifiers('facility', ['a', 'b'], 'mz', 'completed', 'CMP-1');

        const [query, params] = executeQueryMock.mock.calls[0];
        expect(query).toMatch(/AND campaignNumber = \$3 AND status = \$4/);
        expect(params).toEqual(['facility', ['a', 'b'], 'CMP-1', 'completed']);
    });

    it('does not surface a row from a different campaign that reuses the same uniqueIdentifier', async () => {
        // Simulates the DB correctly filtering out CMP-2's completed row when CMP-1 asks,
        // since the campaignNumber predicate is now part of the query sent to the DB.
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        const rows = await getCampaignDataRowsWithUniqueIdentifiers(
            'facility', ['shared-facility-code'], 'mz', 'completed', 'CMP-1'
        );

        expect(rows).toEqual([]);
        const [, params] = executeQueryMock.mock.calls[0];
        expect(params).toContain('CMP-1');
    });

    it('maps returned rows to camelCase fields', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [{
                campaignnumber: 'CMP-1', type: 'facility', data: { name: 'X' },
                uniqueidentifier: 'a', status: 'completed', uniqueidafterprocess: 'FAC-1',
            }],
        } as any);

        const rows = await getCampaignDataRowsWithUniqueIdentifiers('facility', ['a'], 'mz', 'completed', 'CMP-1');

        expect(rows).toEqual([{
            campaignNumber: 'CMP-1', type: 'facility', data: { name: 'X' },
            uniqueIdentifier: 'a', status: 'completed', uniqueIdAfterProcess: 'FAC-1',
        }]);
    });
});
