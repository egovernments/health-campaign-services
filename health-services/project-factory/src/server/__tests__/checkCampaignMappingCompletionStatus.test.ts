import { checkCampaignMappingCompletionStatus } from '../utils/genericUtils';
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
        expect(params).toEqual(['CMP-1', null]);
    });

    it('passes the supplied type as the second SQL parameter', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [] } as any);

        await checkCampaignMappingCompletionStatus('CMP-1', 'tn', 'user');

        const [, params] = executeQueryMock.mock.calls[0];
        expect(params).toEqual(['CMP-1', 'user']);
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
