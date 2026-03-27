// Tests for campaignManageService — Bug 1: resource upsert silently skipped when campaignNumber absent


const mockFindActiveResourceByUpsertKey = jest.fn();
const mockCreateResourceDetail = jest.fn().mockResolvedValue({ id: 'new-res-1', _resolvedCampaignId: 'uuid-1' });
const mockUpdateResourceDetail = jest.fn().mockResolvedValue({ id: 'res-1', _resolvedCampaignId: 'uuid-1' });
const mockGetCampaignStatusFromDB = jest.fn();
const mockProcessBasedOnAction = jest.fn().mockResolvedValue(undefined);
const mockValidateProjectCampaignRequest = jest.fn().mockResolvedValue(undefined);

jest.mock('../utils/resourceDetailsUtils', () => ({
  findActiveResourceByUpsertKey: mockFindActiveResourceByUpsertKey,
}));
jest.mock('../service/resourceDetailsService', () => ({
  createResourceDetail: mockCreateResourceDetail,
  updateResourceDetail: mockUpdateResourceDetail,
  getCampaignStatusFromDB: mockGetCampaignStatusFromDB,
}));
jest.mock('../utils/campaignUtils', () => ({
  processBasedOnAction: mockProcessBasedOnAction,
  prepareAndProduceCancelMessage: jest.fn().mockResolvedValue(undefined),
  processFetchMicroPlan: jest.fn().mockResolvedValue(undefined),
  searchProjectCampaignResourcData: jest.fn().mockResolvedValue({ responseData: [], totalCount: 0 }),
  updateCampaignAfterSearch: jest.fn().mockResolvedValue(undefined),
  validateAndFetchCampaign: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../validators/campaignValidators', () => ({
  validateProjectCampaignRequest: mockValidateProjectCampaignRequest,
  validateMicroplanRequest: jest.fn().mockResolvedValue(undefined),
  validateAddResourcesRequest: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
jest.mock('../config', () => ({
  default: {
    kafka: {
      KAFKA_CREATE_RESOURCE_DETAILS_TOPIC: 'create-topic',
      KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: 'update-topic',
    },
  },
  __esModule: true,
}));
jest.mock('../kafka/Producer', () => ({
  produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../config/resourceTypeRegistry', () => ({
  getRegistryEntry: jest.fn(() => null),
  prepareProcessesForResourceTypes: jest.fn(() => []),
}));
jest.mock('../utils/genericUtils', () => ({
  prepareProcessesForResourceTypes: jest.fn(() => []),
  getCurrentProcesses: jest.fn(() => []),
  throwError: jest.fn((m: string, s: number, c: string, msg?: string) => {
    throw Object.assign(new Error(msg || c), { status: s, code: c });
  }),
  throwErrorViaWrapper: jest.fn(),
}));

import { updateProjectTypeCampaignService } from '../service/campaignManageService';

const makeRequest = (campaignDetails: Record<string, any>, resources: any[] = []) => ({
  body: {
    RequestInfo: { userInfo: { uuid: 'user-1' } },
    CampaignDetails: {
      id: 'uuid-1',
      tenantId: 'ng',
      resources,
      ...campaignDetails,
    },
  },
} as any);

beforeEach(() => {
  jest.clearAllMocks();
  mockProcessBasedOnAction.mockResolvedValue(undefined);
  mockValidateProjectCampaignRequest.mockResolvedValue(undefined);
  mockFindActiveResourceByUpsertKey.mockResolvedValue(null);
  mockCreateResourceDetail.mockResolvedValue({ id: 'new-res-1', _resolvedCampaignId: 'uuid-1' });
  mockUpdateResourceDetail.mockResolvedValue({ id: 'res-1', _resolvedCampaignId: 'uuid-1' });
});

describe('updateProjectTypeCampaignService — resource upsert block', () => {
  const resource = { type: 'user', filestoreId: 'fs-1', parentResourceId: null };

  test('skips resource upsert when campaignNumber absent AND DB resolution also fails', async () => {
    // No campaignNumber in body, and getCampaignStatusFromDB also returns null
    mockGetCampaignStatusFromDB.mockResolvedValue({ campaignNumber: null, status: 'inprogress' });

    await updateProjectTypeCampaignService(makeRequest({}, [resource]));

    expect(mockCreateResourceDetail).not.toHaveBeenCalled();
    expect(mockUpdateResourceDetail).not.toHaveBeenCalled();
  });

  test('resolves campaignNumber from DB when campaignNumber absent in request body', async () => {
    // This is the Bug 1 test — RED before fix, GREEN after fix
    // campaignNumber NOT in request body, but getCampaignStatusFromDB resolves it
    mockGetCampaignStatusFromDB.mockResolvedValue({ campaignNumber: 'HCM-001', status: 'inprogress' });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    await updateProjectTypeCampaignService(makeRequest({}, [resource]));

    // After fix: createResourceDetail SHOULD be called with resolved campaignNumber
    expect(mockCreateResourceDetail).toHaveBeenCalledWith(
      expect.objectContaining({ campaignNumber: 'HCM-001', campaignId: 'uuid-1' }),
      'user-1'
    );
  });

  test('uses campaignNumber directly from request when provided', async () => {
    mockFindActiveResourceByUpsertKey.mockResolvedValue({ id: 'res-1', type: 'user' });

    await updateProjectTypeCampaignService(
      makeRequest({ campaignNumber: 'HCM-001' }, [resource])
    );

    expect(mockUpdateResourceDetail).toHaveBeenCalled();
    expect(mockCreateResourceDetail).not.toHaveBeenCalled();
  });

  test('continues processing remaining resources on per-resource error', async () => {
    const resources = [
      { type: 'user', filestoreId: 'fs-1', parentResourceId: null },
      { type: 'facility', filestoreId: 'fs-2', parentResourceId: null },
    ];
    mockGetCampaignStatusFromDB.mockResolvedValue({ campaignNumber: 'HCM-001', status: 'inprogress' });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);
    // First resource throws, second succeeds
    mockCreateResourceDetail
      .mockRejectedValueOnce(new Error('first resource failed'))
      .mockResolvedValueOnce({ id: 'new-res-2' });

    // Should NOT throw
    await expect(
      updateProjectTypeCampaignService(makeRequest({}, resources))
    ).resolves.not.toThrow();

    // Both resources should have been attempted
    expect(mockCreateResourceDetail).toHaveBeenCalledTimes(2);
  });
});



describe('updateProjectTypeCampaignService — getCampaignStatusFromDB call count (Bug 3)', () => {
  const resources = [
    { type: 'user', filestoreId: 'fs-1', parentResourceId: null },
    { type: 'facility', filestoreId: 'fs-2', parentResourceId: null },
    { type: 'boundaryWithTarget', filestoreId: 'fs-3', parentResourceId: null },
  ];

  test('getCampaignStatusFromDB NOT called when campaignNumber already in request body', async () => {
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    await updateProjectTypeCampaignService(
      makeRequest({ campaignNumber: 'HCM-001' }, resources)
    );

    // campaignNumber already resolved — initial resolution block should be skipped
    expect(mockGetCampaignStatusFromDB).not.toHaveBeenCalled();
  });

  test('getCampaignStatusFromDB called exactly once when campaignNumber absent (not per-resource)', async () => {
    mockGetCampaignStatusFromDB.mockResolvedValue({ campaignNumber: 'HCM-001', status: 'inprogress' });
    mockFindActiveResourceByUpsertKey.mockResolvedValue(null);

    // 3 resources — but getCampaignStatusFromDB must fire exactly once (initial resolution)
    await updateProjectTypeCampaignService(
      makeRequest({}, resources)
    );

    // Initial resolution: 1 call only — NOT once per resource
    expect(mockGetCampaignStatusFromDB).toHaveBeenCalledTimes(1);
  });
});
