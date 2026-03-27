// Tests for resourceDetailsService — Bug 5: dead code in updateResourceDetail; Bug 3 interaction


const mockExecuteQuery = jest.fn();
const mockGetTableName = jest.fn((_name: string, tenantId: string) => `${tenantId}.eg_cm_campaign_details`);
const mockProduceModifiedMessages = jest.fn().mockResolvedValue(undefined);
const mockFindActiveResourceByUpsertKey = jest.fn();
const mockGetResourceDetailById = jest.fn();
const mockCountResourcesByType = jest.fn().mockResolvedValue(0);
const mockCountTotalResourceDetails = jest.fn().mockResolvedValue(0);
const mockSearchResourceDetailsFromDB = jest.fn().mockResolvedValue([]);
const mockToResourceDetailsResponse = jest.fn((row: any) => row);
const mockThrowError = jest.fn((module: string, status: number, code: string, msg?: string) => {
  throw Object.assign(new Error(msg || code), { status, code });
});

jest.mock('../utils/db', () => ({
  executeQuery: mockExecuteQuery,
  getTableName: mockGetTableName,
}));
jest.mock('../kafka/Producer', () => ({
  produceModifiedMessages: mockProduceModifiedMessages,
}));
jest.mock('../utils/resourceDetailsUtils', () => ({
  findActiveResourceByUpsertKey: mockFindActiveResourceByUpsertKey,
  getResourceDetailById: mockGetResourceDetailById,
  countResourcesByType: mockCountResourcesByType,
  countTotalResourceDetails: mockCountTotalResourceDetails,
  searchResourceDetailsFromDB: mockSearchResourceDetailsFromDB,
  toResourceDetailsResponse: mockToResourceDetailsResponse,
}));
jest.mock('../utils/genericUtils', () => ({
  throwError: mockThrowError,
  throwErrorViaWrapper: jest.fn(),
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
jest.mock('../config', () => ({
  default: {
    DB_CONFIG: {
      DB_CAMPAIGN_DETAILS_TABLE_NAME: 'eg_cm_campaign_details',
      DB_RESOURCE_DETAILS_TABLE_NAME: 'eg_cm_resource_details',
    },
    kafka: {
      KAFKA_CREATE_RESOURCE_DETAILS_TOPIC: 'create-topic',
      KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: 'update-topic',
    },
  },
  __esModule: true,
}));
jest.mock('../config/resourceTypeRegistry', () => ({
  getResourceConfigOrDefault: jest.fn(() => ({ parentType: null, allowMultiplePerParent: false })),
  isRegisteredType: jest.fn(() => false),
}));
jest.mock('../config/constants', () => ({
  campaignStatuses: { started: 'started', cancelled: 'cancelled', inprogress: 'inprogress' },
  resourceStatuses: { toCreate: 'toCreate', creating: 'creating', created: 'created' },
}));
jest.mock('uuid', () => ({ v4: jest.fn(() => 'new-uuid-1') }));

import { createResourceDetail, updateResourceDetail, searchResourceDetails } from '../service/resourceDetailsService';

const makeCampaignRow = (overrides: Record<string, any> = {}) => ({
  status: 'inprogress',
  campaignnumber: 'HCM-001',
  id: 'uuid-1',
  ...overrides,
});

const makeResourceRow = (overrides: Record<string, any> = {}) => ({
  id: 'res-1',
  tenantid: 'ng',
  campaignid: 'uuid-1',
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
  mockProduceModifiedMessages.mockResolvedValue(undefined);
  mockFindActiveResourceByUpsertKey.mockResolvedValue(null);
  mockGetResourceDetailById.mockResolvedValue(null);
  mockCountResourcesByType.mockResolvedValue(0);
  mockSearchResourceDetailsFromDB.mockResolvedValue([]);
  mockCountTotalResourceDetails.mockResolvedValue(0);
});

describe('createResourceDetail', () => {
  test('throws 400 when both campaignId and campaignNumber absent', async () => {
    await expect(
      createResourceDetail({ tenantId: 'ng', type: 'user', fileStoreId: 'fs-1' } as any, 'user-1')
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });

    expect(mockExecuteQuery).not.toHaveBeenCalled();
    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('throws 400 when campaignId given but DB has no campaignnumber (legacy row)', async () => {
    // Campaign exists but campaignnumber column is NULL
    mockExecuteQuery.mockResolvedValue({ rows: [makeCampaignRow({ campaignnumber: null })] });

    await expect(
      createResourceDetail({ tenantId: 'ng', campaignId: 'uuid-1', type: 'user', fileStoreId: 'fs-1' } as any, 'user-1')
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });

    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('succeeds when campaignId provided and campaignNumber resolved from DB', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [makeCampaignRow()] });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    const result = await createResourceDetail(
      { tenantId: 'ng', campaignId: 'uuid-1', type: 'user', fileStoreId: 'fs-1' } as any,
      'user-1'
    );

    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(1);
    expect(result.campaignNumber).toBe('HCM-001');
    expect(result._resolvedCampaignId).toBe('uuid-1');
  });

  test('succeeds when campaignNumber provided directly (no campaignId)', async () => {
    // getCampaignStatusByNumber — returns by campaignnumber lookup
    mockExecuteQuery.mockResolvedValue({ rows: [{ id: 'uuid-1', status: 'inprogress' }] });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    const result = await createResourceDetail(
      { tenantId: 'ng', campaignNumber: 'HCM-001', type: 'user', fileStoreId: 'fs-1' } as any,
      'user-1'
    );

    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(1);
    expect(result.campaignNumber).toBe('HCM-001');
  });
});

describe('updateResourceDetail', () => {
  test('throws 400 when both identifiers absent', async () => {
    await expect(
      updateResourceDetail({ id: 'res-1', tenantId: 'ng', fileStoreId: 'fs-2' } as any, 'user-1')
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });

    expect(mockExecuteQuery).not.toHaveBeenCalled();
  });

  test('throws 400 when campaignNumber cannot be resolved (legacy row with NULL)', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [makeCampaignRow({ campaignnumber: null })] });

    await expect(
      updateResourceDetail({ id: 'res-1', tenantId: 'ng', campaignId: 'uuid-1', fileStoreId: 'fs-2' } as any, 'user-1')
    ).rejects.toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });

    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('dead code check — replacement resource uses resolvedCampaignNumber, not existing.campaignnumber', async () => {
    // Campaign DB returns resolved campaignNumber='HCM-001'
    mockExecuteQuery.mockResolvedValue({ rows: [makeCampaignRow()] });
    // Existing resource has campaignnumber=null (legacy scenario); status='created' to avoid inprogress guard
    mockGetResourceDetailById.mockResolvedValue(makeResourceRow({ campaignnumber: null, isactive: true, status: 'created' }));

    const result = await updateResourceDetail(
      { id: 'res-1', tenantId: 'ng', campaignId: 'uuid-1', fileStoreId: 'fs-2' } as any,
      'user-1'
    );

    // Must use resolved 'HCM-001' NOT null from existing row (dead code path)
    expect(result.campaignNumber).toBe('HCM-001');
    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(2); // deactivate + create
  });
});

describe('searchResourceDetails', () => {
  test('resolves campaignNumber from campaignId before searching DB', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [makeCampaignRow()] });
    mockSearchResourceDetailsFromDB.mockResolvedValue([]);
    mockCountTotalResourceDetails.mockResolvedValue(0);

    await searchResourceDetails({ tenantId: 'ng', campaignId: 'uuid-1', isActive: true } as any);

    expect(mockSearchResourceDetailsFromDB).toHaveBeenCalledWith(
      expect.objectContaining({ campaignNumber: 'HCM-001' }),
      undefined
    );
  });
});



describe('createResourceDetail — campaignNumber not found (Bug 2)', () => {
  test('throws 400 CAMPAIGN_NOT_FOUND when campaignNumber provided but campaign not in DB', async () => {
    // getCampaignStatusByNumber returns null campaignId → campaign not found
    mockExecuteQuery.mockResolvedValue({ rows: [] });

    await expect(
      createResourceDetail(
        { tenantId: 'ng', campaignNumber: 'INVALID-001', type: 'user', fileStoreId: 'fs-1' } as any,
        'user-1'
      )
    ).rejects.toMatchObject({ status: 400, code: 'CAMPAIGN_NOT_FOUND' });

    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('succeeds when campaignNumber provided and campaign found in DB', async () => {
    // getCampaignStatusByNumber returns a valid campaignId
    mockExecuteQuery.mockResolvedValue({ rows: [{ id: 'uuid-1', status: 'inprogress' }] });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    const result = await createResourceDetail(
      { tenantId: 'ng', campaignNumber: 'HCM-001', type: 'user', fileStoreId: 'fs-1' } as any,
      'user-1'
    );

    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(1);
    expect(result.campaignNumber).toBe('HCM-001');
    expect(result._resolvedCampaignId).toBe('uuid-1');
  });
});

describe('updateResourceDetail — campaignNumber not found (Bug 2)', () => {
  test('throws 400 CAMPAIGN_NOT_FOUND when campaignNumber provided but campaign not in DB', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [] });

    await expect(
      updateResourceDetail(
        { id: 'res-1', tenantId: 'ng', campaignNumber: 'INVALID-001', fileStoreId: 'fs-2' } as any,
        'user-1'
      )
    ).rejects.toMatchObject({ status: 400, code: 'CAMPAIGN_NOT_FOUND' });

    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('succeeds when campaignNumber provided and campaign found; resolvedCampaignId populated', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [{ id: 'uuid-1', status: 'inprogress' }] });
    mockGetResourceDetailById.mockResolvedValue(
      makeResourceRow({ campaignnumber: 'HCM-001', isactive: true, status: 'created' })
    );

    const result = await updateResourceDetail(
      { id: 'res-1', tenantId: 'ng', campaignNumber: 'HCM-001', fileStoreId: 'fs-2' } as any,
      'user-1'
    );

    expect(result._resolvedCampaignId).toBe('uuid-1');
    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(2); // deactivate + create
  });
});



describe('createResourceDetail — campaignNumber path priority (Bug 3)', () => {
  test('when campaignNumber provided, does NOT call getCampaignStatusFromDB (uses getCampaignStatusByNumber)', async () => {
    // getCampaignStatusByNumber path: SELECT id, status FROM ... WHERE campaignnumber = $1
    // In mock, executeQuery returns a row with id + status (not campaignnumber column)
    mockExecuteQuery.mockResolvedValue({ rows: [{ id: 'uuid-1', status: 'inprogress' }] });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    await createResourceDetail(
      { tenantId: 'ng', campaignId: 'uuid-1', campaignNumber: 'HCM-001', type: 'user', fileStoreId: 'fs-1' } as any,
      'user-1'
    );

    // With both campaignId+campaignNumber provided, should use campaignNumber path
    // Verify: the SQL used is getCampaignStatusByNumber (SELECT id, status WHERE campaignnumber = $1)
    // not getCampaignStatusFromDB (SELECT status, campaignnumber WHERE id = $1)
    const [sql] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('campaignnumber = $1');
    expect(sql).not.toContain('id = $1');
  });

  test('when only campaignId provided (no campaignNumber), calls getCampaignStatusFromDB', async () => {
    mockExecuteQuery.mockResolvedValue({ rows: [{ status: 'inprogress', campaignnumber: 'HCM-001' }] });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    await createResourceDetail(
      { tenantId: 'ng', campaignId: 'uuid-1', type: 'user', fileStoreId: 'fs-1' } as any,
      'user-1'
    );

    // campaignId-only path: SELECT status, campaignnumber WHERE id = $1
    const [sql] = mockExecuteQuery.mock.calls[0];
    expect(sql).toContain('id = $1');
  });
});
