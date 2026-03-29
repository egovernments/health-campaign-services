// Tests for campaignValidators — Bug 3: validation silently skipped when both identifiers absent
// CV-1..CV-4

const mockSearchProjectTypeCampaignService = jest.fn();
const mockSearchBoundaryRelationshipDefinition = jest.fn();
const mockFetchFileFromFilestore = jest.fn();
const mockThrowError = jest.fn((module: string, status: number, code: string, msg?: string) => {
  throw Object.assign(new Error(msg || code), { status, code });
});

jest.mock('../service/campaignManageService', () => ({
  searchProjectTypeCampaignService: mockSearchProjectTypeCampaignService,
}));
jest.mock('../api/coreApis', () => ({
  searchBoundaryRelationshipDefinition: mockSearchBoundaryRelationshipDefinition,
  fetchFileFromFilestore: mockFetchFileFromFilestore,
  searchBoundaryRelationshipData: jest.fn(),
}));
jest.mock('../utils/genericUtils', () => ({
  throwError: mockThrowError,
  throwErrorViaWrapper: jest.fn(),
  getLocalizedMessagesHandlerViaLocale: jest.fn().mockResolvedValue({}),
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
jest.mock('../config', () => ({
  default: {},
  __esModule: true,
}));
jest.mock('../config/processTemplateConfigs', () => ({
  processTemplateConfigs: { user: {}, facility: {}, boundaryWithTarget: {} },
}));
jest.mock('../config/createAndSearch', () => ({}));
jest.mock('../utils/db', () => ({
  executeQuery: jest.fn(),
  getTableName: jest.fn((_name: string, tenantId: string) => `${tenantId}.table`),
}));
jest.mock('../utils/resourceDetailsUtils', () => ({
  findActiveResourceByUpsertKey: jest.fn(),
  searchResourceDetailsFromDB: jest.fn(),
}));
jest.mock('../kafka/Producer', () => ({
  produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/request', () => ({
  defaultheader: jest.fn(() => ({})),
  httpRequest: jest.fn(),
}));
jest.mock('../api/campaignApis', () => ({
  getCampaignSearchResponse: jest.fn(),
  getHeadersOfBoundarySheet: jest.fn(),
  getHierarchy: jest.fn(),
  handleResouceDetailsError: jest.fn(),
}));
jest.mock('../utils/campaignUtils', () => ({
  generateProcessedFileAndPersist: jest.fn(),
  getFinalValidHeadersForTargetSheetAsPerCampaignType: jest.fn(),
  getLocalizedName: jest.fn(),
  searchProjectCampaignResourcData: jest.fn().mockResolvedValue({ responseData: [], totalCount: 0 }),
  getDifferentDistrictTabs: jest.fn(() => []),
}));

import { validateResourceDetails } from '../validators/campaignValidators';

const makeResourceDetails = (overrides: Record<string, any> = {}) => ({
  type: 'user',
  hierarchyType: 'ADMIN',
  tenantId: 'ng',
  fileStoreId: 'fs-1',
  ...overrides,
});

beforeEach(() => {
  jest.clearAllMocks();
  // Default: hierarchy lookup passes
  mockSearchBoundaryRelationshipDefinition.mockResolvedValue({
    BoundaryHierarchy: [{ boundaryHierarchy: [{ boundaryType: 'country' }] }],
  });
  // Default: file lookup passes
  mockFetchFileFromFilestore.mockResolvedValue('http://file-url');
});

describe('validateResourceDetails', () => {
  test('CV-1: throws 400 when both campaignId and campaignNumber absent', async () => {
    await expect(validateResourceDetails(makeResourceDetails() as any))
      .rejects.toMatchObject({ status: 400 });

    expect(mockSearchProjectTypeCampaignService).not.toHaveBeenCalled();
  });

  test('CV-2: calls validateCampaignViaId when only campaignId provided', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({
      CampaignDetails: [{ id: 'uuid-1' }],
    });

    await validateResourceDetails(makeResourceDetails({ campaignId: 'uuid-1' }) as any);

    expect(mockSearchProjectTypeCampaignService).toHaveBeenCalledWith(
      expect.objectContaining({ ids: ['uuid-1'], tenantId: 'ng' })
    );
  });

  test('CV-3: calls validateCampaignViaNumber when only campaignNumber provided', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({
      CampaignDetails: [{ id: 'uuid-1' }],
    });

    await validateResourceDetails(makeResourceDetails({ campaignNumber: 'HCM-001' }) as any);

    expect(mockSearchProjectTypeCampaignService).toHaveBeenCalledWith(
      expect.objectContaining({ campaignNumber: 'HCM-001', tenantId: 'ng' })
    );
    expect(mockSearchProjectTypeCampaignService).not.toHaveBeenCalledWith(
      expect.objectContaining({ ids: expect.anything() })
    );
  });

  test('CV-4: validateCampaignViaNumber — throws when campaign not found', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({ CampaignDetails: [] });

    await expect(
      validateResourceDetails(makeResourceDetails({ campaignNumber: 'HCM-MISSING' }) as any)
    ).rejects.toMatchObject({ code: 'VALIDATION_ERROR_CAMPAIGN_NUMBER' });
  });
});
