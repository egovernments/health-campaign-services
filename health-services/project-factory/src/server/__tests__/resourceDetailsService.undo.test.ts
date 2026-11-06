jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../utils/db', () => ({ executeQuery: jest.fn(), getTableName: (name: string) => name }));
jest.mock('../utils/genericUtils', () => ({
    throwError: jest.fn(),
    getCampaignIdsByCampaignNumber: jest.fn(),
    pollUntilCountFn: jest.fn(async (fn: () => Promise<number>, expected: number) => {
        const count = await fn();
        if (count !== expected) throw new Error(`stalled at ${count}/${expected}`);
    }),
}));

import { undoSavedResources } from '../service/resourceDetailsService';
import { executeQuery } from '../utils/db';
import { TenantId } from '../config/models/brandedTypes';

const executeQueryMock = executeQuery as jest.MockedFunction<typeof executeQuery>;
const tn = 'tn' as TenantId;

describe('undoSavedResources', () => {
    beforeEach(() => jest.clearAllMocks());

    it('waits until the rows are stored, then turns the new rows off and the replaced rows on in one statement', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [{ created: '2', replaced: '1' }] } as any).mockResolvedValue({ rows: [], rowCount: 3 } as any);
        await undoSavedResources([{ createdId: 'NEW-1', replacedId: 'OLD-1' }, { createdId: 'NEW-2' }], tn, 'u1');
        expect(executeQueryMock).toHaveBeenCalledTimes(2);
        const [wait, swap] = executeQueryMock.mock.calls;
        expect(wait[1]).toEqual([['NEW-1', 'NEW-2'], ['OLD-1']]);
        expect(String(swap[0])).toContain('SET isactive = (id = ANY($2::text[]))');
        expect(swap[1]?.slice(0, 3)).toEqual([['NEW-1', 'NEW-2'], ['OLD-1'], 'u1']);
    });

    it('waits for each row once when one save replaced the same row twice', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [{ created: '2', replaced: '1' }] } as any).mockResolvedValue({ rows: [], rowCount: 3 } as any);
        await undoSavedResources([{ createdId: 'NEW-1', replacedId: 'OLD-1' }, { createdId: 'NEW-2', replacedId: 'OLD-1' }], tn, 'u1');
        expect(executeQueryMock.mock.calls[0][1]).toEqual([['NEW-1', 'NEW-2'], ['OLD-1']]);
        expect(executeQueryMock).toHaveBeenCalledTimes(2);
    });

    it('does not switch anything while the rows are not stored yet', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [{ created: '1', replaced: '0' }] } as any);
        await expect(undoSavedResources([{ createdId: 'NEW-1', replacedId: 'OLD-1' }], tn, 'u1')).rejects.toThrow('stalled');
        expect(executeQueryMock).toHaveBeenCalledTimes(1);
    });

    it('turns only the new rows off when nothing was replaced, and does nothing for no change', async () => {
        executeQueryMock.mockResolvedValueOnce({ rows: [{ created: '1', replaced: '0' }] } as any).mockResolvedValue({ rows: [], rowCount: 1 } as any);
        await undoSavedResources([{ createdId: 'NEW-1' }], tn, 'u1');
        expect(executeQueryMock).toHaveBeenCalledTimes(2);
        expect(executeQueryMock.mock.calls[1][1]?.slice(0, 2)).toEqual([['NEW-1'], []]);
        executeQueryMock.mockClear();
        await undoSavedResources([], tn, 'u1');
        expect(executeQueryMock).not.toHaveBeenCalled();
    });
});
