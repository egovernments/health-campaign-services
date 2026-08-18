import { searchCampaignData } from '../utils/genericUtils';
import { executeQuery } from '../utils/db';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../utils/db', () => ({
    executeQuery: jest.fn(),
    getTableName: jest.fn(() => 'eg_cm_campaign_data'),
}));

const mockExecuteQuery = jest.mocked(executeQuery);

/**
 * excel-ingestion generates the attendance templates and reads these rows over this API, so the
 * attendance sync columns have to be part of the response shape.
 */
describe('searchCampaignData attendance sync fields', () => {
    afterEach(() => jest.clearAllMocks());

    type QueryResult = { rows: Record<string, unknown>[] };

    function stubRow(row: Record<string, unknown>) {
        const resultFor = (query: string): QueryResult =>
            query.includes('COUNT(*)') ? { rows: [{ count: '1' }] } : { rows: [row] };
        mockExecuteQuery.mockImplementation((query: string) =>
            Promise.resolve(resultFor(query)) as ReturnType<typeof executeQuery>
        );
    }

    it('returns denrollmentDate as a number even though BIGINT arrives as a string', async () => {
        // node-postgres hands back int8 as a string; consumers (excel-ingestion) compare it numerically
        stubRow({
            id: 'row-3',
            campaignnumber: 'CMP-001',
            type: 'attendanceRegisterAttendee',
            data: {},
            status: 'completed',
            isdeleted: false,
            denrollmentdate: '1787226402157',
        });

        const result = await searchCampaignData({ tenantId: 'mz', type: 'attendanceRegisterAttendee' });

        expect(result.data[0].denrollmentDate).toBe(1787226402157);
    });

    it('returns isDeleted and denrollmentDate from the row', async () => {
        stubRow({
            id: 'row-1',
            campaignnumber: 'CMP-001',
            type: 'attendanceRegisterAttendee',
            data: { UserName: 'USR-1' },
            uniqueidentifier: 'REG-001_USR-1_worker',
            uniqueidafterprocess: 'register-uuid-1_ind-1_worker',
            status: 'completed',
            isdeleted: true,
            denrollmentdate: 1786579200000,
        });

        const result = await searchCampaignData({ tenantId: 'mz', type: 'attendanceRegisterAttendee' });

        expect(result.data[0].isDeleted).toBe(true);
        expect(result.data[0].denrollmentDate).toBe(1786579200000);
        expect(result.data[0].uniqueIdAfterProcess).toBe('register-uuid-1_ind-1_worker');
    });

    it('defaults isDeleted to false and denrollmentDate to null when the columns are unset', async () => {
        stubRow({
            id: 'row-2',
            campaignnumber: 'CMP-001',
            type: 'attendanceRegisterAttendee',
            data: {},
            status: 'completed',
            isdeleted: null,
            denrollmentdate: null,
        });

        const result = await searchCampaignData({ tenantId: 'mz', type: 'attendanceRegisterAttendee' });

        expect(result.data[0].isDeleted).toBe(false);
        expect(result.data[0].denrollmentDate).toBeNull();
    });
});
