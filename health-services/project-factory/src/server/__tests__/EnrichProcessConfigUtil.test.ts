// Tests for EnrichProcessConfigUtil — Bug 2: no guard when both campaignId and campaignNumber absent


const mockSearchProjectTypeCampaignService = jest.fn();
const mockSearchBoundaryRelationshipData = jest.fn();
const mockPopulateBoundariesRecursively = jest.fn().mockResolvedValue(undefined);
const mockGetBoundaryOnWhichWeSplit = jest.fn().mockResolvedValue('district');

jest.mock('../service/campaignManageService', () => ({
  searchProjectTypeCampaignService: mockSearchProjectTypeCampaignService,
}));
jest.mock('../api/coreApis', () => ({
  searchBoundaryRelationshipData: mockSearchBoundaryRelationshipData,
}));
jest.mock('../utils/campaignUtils', () => ({
  populateBoundariesRecursively: mockPopulateBoundariesRecursively,
  getBoundaryOnWhichWeSplit: mockGetBoundaryOnWhichWeSplit,
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
jest.mock('../config', () => ({
  default: {},
  __esModule: true,
}));

import { EnrichProcessConfigUtil } from '../utils/EnrichProcessConfigUtil';

const makeCampaignResponse = (overrides: Record<string, any> = {}) => ({
  CampaignDetails: [{
    id: 'uuid-1',
    hierarchyType: 'ADMIN',
    boundaries: [],
    ...overrides,
  }],
});

const makeBoundaryResponse = (boundaries: any[] = []) => ({
  TenantBoundary: [{
    boundary: boundaries.length
      ? [{ code: boundaries[0].code, type: boundaries[0].type, children: [] }]
      : [{ code: 'ROOT', type: 'country', children: [] }],
  }],
});

beforeEach(() => {
  jest.clearAllMocks();
  mockSearchBoundaryRelationshipData.mockResolvedValue(makeBoundaryResponse());
  mockGetBoundaryOnWhichWeSplit.mockResolvedValue('district');
});

describe('EnrichProcessConfigUtil.enrichTargetProcessConfig', () => {
  const util = new EnrichProcessConfigUtil();

  test('throws when both campaignId and campaignNumber absent', async () => {
    const resourceDetails = { tenantId: 'ng' } as any;
    const templateConfig = { sheets: [] };

    await expect(util.enrichTargetProcessConfig(resourceDetails, templateConfig))
      .rejects.toThrow(/Either campaignId or campaignNumber must be present/);

    expect(mockSearchProjectTypeCampaignService).not.toHaveBeenCalled();
  });

  test('searches by ids when campaignId provided', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue(makeCampaignResponse());
    const resourceDetails = { tenantId: 'ng', campaignId: 'uuid-1' } as any;
    const templateConfig = { sheets: [] };

    await util.enrichTargetProcessConfig(resourceDetails, templateConfig);

    expect(mockSearchProjectTypeCampaignService).toHaveBeenCalledWith(
      expect.objectContaining({ tenantId: 'ng', ids: ['uuid-1'] })
    );
  });

  test('searches by campaignNumber when only campaignNumber provided', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue(makeCampaignResponse());
    const resourceDetails = { tenantId: 'ng', campaignNumber: 'HCM-001' } as any;
    const templateConfig = { sheets: [] };

    await util.enrichTargetProcessConfig(resourceDetails, templateConfig);

    expect(mockSearchProjectTypeCampaignService).toHaveBeenCalledWith(
      expect.objectContaining({ tenantId: 'ng', campaignNumber: 'HCM-001' })
    );
    expect(mockSearchProjectTypeCampaignService).not.toHaveBeenCalledWith(
      expect.objectContaining({ ids: expect.anything() })
    );
    // getBoundaryOnWhichWeSplit should use campaignDetails.id as fallback when campaignId not in resourceDetails
    expect(mockGetBoundaryOnWhichWeSplit).toHaveBeenCalledWith('uuid-1', 'ng');
  });

  test('throws when campaign not found', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({ CampaignDetails: [] });
    const resourceDetails = { tenantId: 'ng', campaignId: 'uuid-missing' } as any;

    await expect(util.enrichTargetProcessConfig(resourceDetails, { sheets: [] }))
      .rejects.toThrow('Campaign not found');
  });

  test('pushes matching boundary sheets to templateConfig', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue(
      makeCampaignResponse({ boundaries: [{ code: 'DISTRICT-1', type: 'district', includeAllChildren: true }] })
    );
    mockSearchBoundaryRelationshipData.mockResolvedValue(
      makeBoundaryResponse([{ code: 'DISTRICT-1', type: 'district' }])
    );
    mockGetBoundaryOnWhichWeSplit.mockResolvedValue('district');

    const resourceDetails = { tenantId: 'ng', campaignId: 'uuid-1' } as any;
    const templateConfig = { sheets: [] };

    await util.enrichTargetProcessConfig(resourceDetails, templateConfig);

    expect(templateConfig.sheets).toEqual(
      expect.arrayContaining([{ sheetName: 'DISTRICT-1', lockWholeSheet: true }])
    );
  });
});
