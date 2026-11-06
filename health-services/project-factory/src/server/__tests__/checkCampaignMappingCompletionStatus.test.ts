import { checkCampaignMappingCompletionStatus, markSiteLinkWorkerUpdatesFailed, markSiteLinkWorkerUpdatesFailedBySite, markSiteLinkWorkerUpdatesDone, resetFailedSiteLinkWorkerUpdates, markSiteLinksToCreateAgain, getFailedMappingRows } from '../utils/genericUtils';
import config from '../config';
import { CampaignNumber, TenantId } from '../config/models/brandedTypes';
import { mappingStatuses } from '../config/constants';

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn(),
    getTableName: (name: string) => name,
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

import { executeQuery } from '../utils/db';

const executeQueryMock = executeQuery as jest.MockedFunction<typeof executeQuery>;

describe('checkCampaignMappingCompletionStatus — type filter and skipped status', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('passes a NULL type when no filter is provided (overall query)', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await checkCampaignMappingCompletionStatus('CMP-1', 'tn');

        expect(executeQueryMock).toHaveBeenCalledTimes(1);
        const [, params] = executeQueryMock.mock.calls[0];
        expect(params).toEqual(['CMP-1', null, expect.any(Number)]);
    });

    it('passes the supplied type as the second SQL parameter', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await checkCampaignMappingCompletionStatus('CMP-1', 'tn', 'user');

        const [, params] = executeQueryMock.mock.calls[0];
        expect(params).toEqual(['CMP-1', 'user', expect.any(Number)]);
    });

    it('counts mapped/deMapped/skipped as completed and reports allCompleted when no failures or pending exist', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [
                { status: mappingStatuses.mapped, count: '5' },
                { status: mappingStatuses.deMapped, count: '2' },
                { status: mappingStatuses.skipped, count: '3' },
            ],
        } as any);

        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn', 'user');

        expect(result.totalMappings).toBe(10);
        expect(result.completedMappings).toBe(10);
        expect(result.failedMappings).toBe(0);
        expect(result.pendingMappings).toBe(0);
        expect(result.anyFailed).toBe(false);
        expect(result.allCompleted).toBe(true);
    });

    it('reports anyFailed=true only when status=failed rows exist', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [
                { status: mappingStatuses.mapped, count: '4' },
                { status: mappingStatuses.failed, count: '1' },
                { status: mappingStatuses.skipped, count: '5' },
            ],
        } as any);

        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn');

        expect(result.failedMappings).toBe(1);
        expect(result.anyFailed).toBe(true);
        expect(result.completedMappings).toBe(9); // mapped + skipped
        expect(result.pendingMappings).toBe(0);
    });

    it('counts deMapFailed rows as failed alongside failed rows', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [
                { status: mappingStatuses.mapped, count: '4', terminalcount: '0' },
                { status: mappingStatuses.failed, count: '2', terminalcount: '1' },
                { status: mappingStatuses.deMapFailed, count: '3', terminalcount: '2' },
            ],
        } as any);

        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn');

        expect(result.failedMappings).toBe(5);
        expect(result.terminallyFailedMappings).toBe(3);
        expect(result.retryableFailedMappings).toBe(2);
        expect(result.anyFailed).toBe(true);
        expect(result.pendingMappings).toBe(0);
    });

    it('uses an explicitly passed maxRetries as the terminal threshold parameter', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await checkCampaignMappingCompletionStatus('CMP-1', 'tn', 'user', 7);

        const [, params] = executeQueryMock.mock.calls[0];
        expect(params).toEqual(['CMP-1', 'user', 7]);
    });

    it('splits failed rows into terminal and retryable using the terminalcount column', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [
                { status: mappingStatuses.mapped, count: '4', terminalcount: '0' },
                { status: mappingStatuses.failed, count: '3', terminalcount: '1' },
            ],
        } as any);

        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn');

        expect(result.failedMappings).toBe(3);
        expect(result.terminallyFailedMappings).toBe(1);
        expect(result.retryableFailedMappings).toBe(2);
    });

    it('reports zero terminal failures when no failed rows exist', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [
                { status: mappingStatuses.mapped, count: '5', terminalcount: '0' },
            ],
        } as any);

        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn');

        expect(result.terminallyFailedMappings).toBe(0);
        expect(result.retryableFailedMappings).toBe(0);
    });

    it('treats unknown statuses (e.g. toBeMapped) as pending', async () => {
        executeQueryMock.mockResolvedValueOnce({
            rows: [
                { status: mappingStatuses.toBeMapped, count: '7' },
                { status: mappingStatuses.mapped, count: '3' },
            ],
        } as any);

        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn');

        expect(result.totalMappings).toBe(10);
        expect(result.completedMappings).toBe(3);
        expect(result.failedMappings).toBe(0);
        expect(result.pendingMappings).toBe(7);
        expect(result.allCompleted).toBe(false);
    });
});

describe('site link worker update rows (updateFailed / toBeUpdated)', () => {
    beforeEach(() => jest.clearAllMocks());

    it('counts updateFailed rows as failed, split into terminal and retryable, and toBeUpdated rows as pending', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [
            { status: mappingStatuses.mapped, count: '3', terminalcount: '0' },
            { status: mappingStatuses.updateFailed, count: '2', terminalcount: '1' },
            { status: mappingStatuses.toBeUpdated, count: '1', terminalcount: '0' },
        ] } as any);
        const result = await checkCampaignMappingCompletionStatus('CMP-1', 'tn', 'facility');
        expect(result).toMatchObject({ totalMappings: 6, completedMappings: 3, failedMappings: 2, terminallyFailedMappings: 1, retryableFailedMappings: 1, pendingMappings: 1, anyFailed: true });
    });

    const cmp = 'CMP-1' as CampaignNumber, tn = 'tn' as TenantId;
    const waitingOrMapped = [mappingStatuses.mapped, mappingStatuses.toBeUpdated, mappingStatuses.updateFailed];

    it('marks the facility link rows of the given link ids (mapped or already waiting), with the reason and retryCount 0', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [{ mappingid: 'PF-1' }] } as any);
        const marked = await markSiteLinkWorkerUpdatesFailed(cmp, tn, [{ mappingId: 'PF-1', error: 'e1' }, { mappingId: 'PF-2', error: 'e2' }]);
        const [sql, params] = executeQueryMock.mock.calls[0];
        expect(String(sql)).toContain('retryCount = CASE WHEN v.terminal THEN $6 ELSE 0 END');
        expect(params).toEqual(['CMP-1', mappingStatuses.updateFailed, ['PF-1', 'PF-2'], ['e1', 'e2'], waitingOrMapped, config.mapping.maxRetries, [false, false]]);
        expect(Array.from(marked)).toEqual(['PF-1']);
    });

    it('starts a terminal failure at the retry limit, so the reconciler does not retry it', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);
        await markSiteLinkWorkerUpdatesFailed(cmp, tn, [{ mappingId: 'PF-1', error: 'e1', terminal: true }, { mappingId: 'PF-2', error: 'e2' }]);
        expect(executeQueryMock.mock.calls[0][1]).toEqual(['CMP-1', mappingStatuses.updateFailed, ['PF-1', 'PF-2'], ['e1', 'e2'], waitingOrMapped, config.mapping.maxRetries, [true, false]]);
    });

    it('marks the rows of gone links failed without their link id, so the reconciler creates the links again', async () => {
        executeQueryMock.mockResolvedValueOnce({ rowCount: 1 } as any);
        expect(await markSiteLinksToCreateAgain(cmp, tn, { linkIds: ['PF-1'] }, 'gone')).toBe(1);
        const [sql, params] = executeQueryMock.mock.calls[0];
        expect(String(sql)).toContain('mappingId = NULL');
        expect(String(sql)).toContain('mappingId = ANY($5::text[])');
        expect(params).toEqual(['CMP-1', mappingStatuses.failed, 'gone', waitingOrMapped, ['PF-1']]);
    });

    it('marks the rows of the given sites and boundaries whose link was not among the links found', async () => {
        executeQueryMock.mockResolvedValueOnce({ rowCount: 2 } as any);
        expect(await markSiteLinksToCreateAgain(cmp, tn, { uniqueIdentifiers: ['site-1'], boundaryCodes: ['B1', 'B2'], foundLinkIds: ['PF-9'] }, 'gone')).toBe(2);
        const [sql, params] = executeQueryMock.mock.calls[0];
        expect(String(sql)).toContain('mappingId IS NOT NULL AND NOT (mappingId = ANY($7::text[]))');
        expect(params).toEqual(['CMP-1', mappingStatuses.failed, 'gone', waitingOrMapped, ['site-1'], ['B1', 'B2'], ['PF-9']]);
    });

    it('marks no row to create again when there are no links, sites or boundaries to look at', async () => {
        expect(await markSiteLinksToCreateAgain(cmp, tn, { linkIds: [] }, 'gone')).toBe(0);
        expect(await markSiteLinksToCreateAgain(cmp, tn, { uniqueIdentifiers: [], boundaryCodes: ['B1'], foundLinkIds: [] }, 'gone')).toBe(0);
        expect(await markSiteLinksToCreateAgain(cmp, tn, { uniqueIdentifiers: ['site-1'], boundaryCodes: [], foundLinkIds: [] }, 'gone')).toBe(0);
        expect(executeQueryMock).not.toHaveBeenCalled();
    });

    it('marks nothing and runs no query for nothing to mark', async () => {
        expect((await markSiteLinkWorkerUpdatesFailed(cmp, tn, [])).size).toBe(0);
        expect(await markSiteLinkWorkerUpdatesFailedBySite(cmp, tn, [], 'e')).toBe(0);
        expect(await markSiteLinkWorkerUpdatesDone(cmp, tn, [])).toBe(0);
        expect(await resetFailedSiteLinkWorkerUpdates(cmp, tn, [])).toBe(0);
        expect(executeQueryMock).not.toHaveBeenCalled();
    });

    it('marks the link rows of the given sites when their links could not be read', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [], rowCount: 2 } as any);
        expect(await markSiteLinkWorkerUpdatesFailedBySite(cmp, tn, ['site-1'], 'read failed')).toBe(2);
        expect(executeQueryMock.mock.calls[0][1]).toEqual(['CMP-1', mappingStatuses.updateFailed, ['site-1'], 'read failed', waitingOrMapped]);
        expect(String(executeQueryMock.mock.calls[0][0])).toContain('mappingId IS NOT NULL');
    });

    it('puts waiting rows of the links just written back to mapped', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [], rowCount: 1 } as any);
        expect(await markSiteLinkWorkerUpdatesDone(cmp, tn, ['PF-1'])).toBe(1);
        expect(executeQueryMock.mock.calls[0][1]).toEqual(['CMP-1', mappingStatuses.mapped, ['PF-1'], [mappingStatuses.toBeUpdated, mappingStatuses.updateFailed]]);
    });

    it('resets the updateFailed (and leftover toBeUpdated) rows of the links this upload keeps Active, with retryCount 0', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [], rowCount: 1 } as any);
        expect(await resetFailedSiteLinkWorkerUpdates(cmp, tn, [{ uniqueIdentifierForData: 'site-1', boundaryCode: 'B1' }])).toBe(1);
        const [sql, params] = executeQueryMock.mock.calls[0];
        expect(String(sql)).toContain('retryCount = 0');
        expect(params).toEqual(['CMP-1', mappingStatuses.toBeUpdated, [mappingStatuses.updateFailed, mappingStatuses.toBeUpdated], ['site-1'], ['B1']]);
    });
});

describe('getFailedMappingRows (reasons for the campaign failure message)', () => {
    beforeEach(() => jest.clearAllMocks());

    it('reads at most the limit of failed rows of the given types, with their reasons, and the total', async () => {
        executeQueryMock
            .mockResolvedValueOnce({ rows: [{ type: 'facility', uniqueidentifierfordata: 'site-1', boundarycode: 'B1', status: mappingStatuses.updateFailed, lasterror: 'why' }] } as any)
            .mockResolvedValueOnce({ rows: [{ count: '3' }] } as any);
        const result = await getFailedMappingRows('CMP-1', 'tn', ['facility', 'resource'], 1);
        expect(result).toEqual({ total: 3, rows: [{ type: 'facility', uniqueIdentifierForData: 'site-1', boundaryCode: 'B1', status: mappingStatuses.updateFailed, lastError: 'why' }] });
        const [sql, params] = executeQueryMock.mock.calls[0];
        expect(String(sql)).toContain('LIMIT $4');
        expect(params).toEqual(['CMP-1', ['facility', 'resource'], [mappingStatuses.failed, mappingStatuses.deMapFailed, mappingStatuses.updateFailed], 1]);
    });
});
