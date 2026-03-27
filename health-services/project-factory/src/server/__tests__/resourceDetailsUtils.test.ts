// Tests for resourceDetailsUtils — Bug 4: old rows with campaignnumber=NULL invisible to upsert checks


jest.mock('../utils/db', () => ({
  executeQuery: jest.fn(),
  getTableName: jest.fn((_name: string, tenantId: string) => `${tenantId}.eg_cm_resource_details`),
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
jest.mock('../config', () => ({
  default: {
    DB_CONFIG: {
      DB_RESOURCE_DETAILS_TABLE_NAME: 'eg_cm_resource_details',
    },
    isEnvironmentCentralInstance: false,
  },
  __esModule: true,
}));

import { executeQuery } from '../utils/db';
import {
  searchResourceDetailsFromDB,
  countTotalResourceDetails,
  findActiveResourceByUpsertKey,
} from '../utils/resourceDetailsUtils';

const mockExecuteQuery = executeQuery as jest.Mock;

const makeDbRow = (overrides: Record<string, any> = {}) => ({
  id: 'res-1',
  tenantid: 'ng',
  campaignid: 'uuid-123',
  campaignnumber: 'HCM-001',
  type: 'user',
  parentresourceid: null,
  filestoreid: 'fs-1',
  processedfilestoreid: null,
  filename: null,
  status: 'toCreate',
  action: 'create',
  isactive: true,
  hierarchytype: null,
  additionaldetails: {},
  createdby: 'user-1',
  lastmodifiedby: 'user-1',
  createdtime: 1000,
  lastmodifiedtime: 1000,
  ...overrides,
});

beforeEach(() => {
  jest.clearAllMocks();
  mockExecuteQuery.mockResolvedValue({ rows: [], rowCount: 0 });
});

describe('searchResourceDetailsFromDB', () => {
  test('throws when no campaignNumber, campaignId, or ids provided', async () => {
    await expect(searchResourceDetailsFromDB({ tenantId: 'ng' } as any))
      .rejects.toThrow('searchResourceDetailsFromDB requires campaignNumber, campaignId, or ids');
  });

  test('queries by campaignnumber column when campaignNumber provided', async () => {
    await searchResourceDetailsFromDB({ tenantId: 'ng', campaignNumber: 'HCM-001' });

    expect(mockExecuteQuery).toHaveBeenCalledTimes(1);
    const [sql, values] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('campaignnumber = $2');
    expect(values).toContain('HCM-001');
    expect(values).toContain('ng');
  });

  test('falls back to campaignid column when only campaignId provided', async () => {
    await searchResourceDetailsFromDB({ tenantId: 'ng', campaignId: 'uuid-123' });

    const [sql, values] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('campaignid = $2');
    expect(values).toContain('uuid-123');
    expect(sql).not.toContain('campaignnumber = $');
  });

  test('ids-only criteria does not require campaign identifier', async () => {
    await expect(
      searchResourceDetailsFromDB({ tenantId: 'ng', ids: ['res-1'] })
    ).resolves.not.toThrow();
  });
});

describe('countTotalResourceDetails', () => {
  test('throws when no campaignNumber, campaignId, or ids provided', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [{ count: '0' }] });
    await expect(countTotalResourceDetails({ tenantId: 'ng' } as any))
      .rejects.toThrow('countTotalResourceDetails requires campaignNumber, campaignId, or ids');
  });
});

describe('findActiveResourceByUpsertKey', () => {
  test('queries by campaignnumber column, not campaignid', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [] });

    await findActiveResourceByUpsertKey('ng', 'HCM-001', 'user', null);

    const [sql, values] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('campaignnumber = $2');
    expect(values).toEqual(['ng', 'HCM-001', 'user']);
  });

  test('returns null when DB returns row with campaignnumber=NULL (legacy row not found by value)', async () => {
    // Simulates the situation BEFORE backfill: DB query for a specific campaignnumber value
    // will NOT return rows that have campaignnumber=NULL, so we get empty result
    mockExecuteQuery.mockResolvedValue({ rows: [] });

    const result = await findActiveResourceByUpsertKey('ng', 'HCM-001', 'user', null);

    expect(result).toBeNull();
  });

  test('returns row when DB returns row with matching campaignnumber', async () => {
    const row = makeDbRow({ campaignnumber: 'HCM-001' });
    mockExecuteQuery.mockResolvedValue({ rows: [row] });

    const result = await findActiveResourceByUpsertKey('ng', 'HCM-001', 'user', null);

    expect(result).toBeDefined();
    expect(result?.campaignnumber).toBe('HCM-001');
  });

  test('includes parentresourceid in query when non-null', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [] });

    await findActiveResourceByUpsertKey('ng', 'HCM-001', 'attendanceRegisterAttendee', 'parent-reg-1');

    const [sql, values] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('parentresourceid = $4');
    expect(values).toEqual(['ng', 'HCM-001', 'attendanceRegisterAttendee', 'parent-reg-1']);
  });

  test('uses IS NULL for parentresourceid when null', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [] });

    await findActiveResourceByUpsertKey('ng', 'HCM-001', 'user', null);

    const [sql] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('parentresourceid IS NULL');
  });
});
