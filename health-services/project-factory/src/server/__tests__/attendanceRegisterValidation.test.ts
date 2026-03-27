// Tests for attendanceRegisterValidation-processClass — Bug 5:
// getCampaignDetails uses raw throw new Error() instead of throwError(),
// so errors lose .status and .code — task processor misclassifies them.


const mockSearchProjectTypeCampaignService = jest.fn();
const mockThrowError = jest.fn((module: string, status: number, code: string, msg?: string) => {
  throw Object.assign(new Error(msg || code), { status, code });
});

jest.mock('../service/campaignManageService', () => ({
  searchProjectTypeCampaignService: mockSearchProjectTypeCampaignService,
}));
jest.mock('../utils/genericUtils', () => ({
  throwError: mockThrowError,
  getLocalizedName: jest.fn((key: string) => key),
  getDifferentDistrictTabs: jest.fn(() => []),
  throwErrorViaWrapper: jest.fn(),
}));
jest.mock('../utils/campaignUtils', () => ({
  getLocalizedName: jest.fn((key: string) => key),
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
jest.mock('../config', () => ({ default: {}, __esModule: true }));
jest.mock('../config/constants', () => ({
  sheetDataRowStatuses: { valid: 'valid', invalid: 'invalid' },
}));
jest.mock('../validators/campaignValidators', () => ({
  validateDatasWithSchema: jest.fn(),
}));
jest.mock('../utils/db', () => ({
  executeQuery: jest.fn(),
  getTableName: jest.fn(),
}));

// Import the class under test AFTER all mocks are declared
import { TemplateClass } from '../processFlowClasses/attendanceRegisterValidation-processClass';

// Access private static method via prototype for direct testing
// TypeScript doesn't expose private statics; use (TemplateClass as any) cast
const getCampaignDetails = (resourceDetails: any) =>
  (TemplateClass as any).getCampaignDetails(resourceDetails);

beforeEach(() => {
  jest.clearAllMocks();
});

describe('attendanceRegisterValidation-processClass.getCampaignDetails', () => {
  test('throws with status 400 when both campaignId and campaignNumber absent', async () => {
    const err = await getCampaignDetails({ tenantId: 'ng' }).catch((e: any) => e);

    // Before fix: raw Error has no .status → undefined
    // After fix: throwError sets .status = 400
    expect(err.status).toBe(400);
  });

  test('throws with code MISSING_CAMPAIGN_IDENTIFIER when both absent', async () => {
    const err = await getCampaignDetails({ tenantId: 'ng' }).catch((e: any) => e);

    // Before fix: raw Error has no .code → undefined
    // After fix: throwError sets .code = 'MISSING_CAMPAIGN_IDENTIFIER'
    expect(err.code).toBe('MISSING_CAMPAIGN_IDENTIFIER');
  });

  test('throws 400 CAMPAIGN_NOT_FOUND when campaign not in search response', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({ CampaignDetails: [] });

    const err = await getCampaignDetails({ tenantId: 'ng', campaignId: 'uuid-1' }).catch((e: any) => e);

    // Before fix: raw Error has no .status → undefined
    // After fix: throwError sets .status = 400
    expect(err.status).toBe(400);
    expect(err.code).toBe('CAMPAIGN_NOT_FOUND');
  });

  test('searches by campaignId when provided', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({
      CampaignDetails: [{ id: 'uuid-1', campaignNumber: 'HCM-001' }],
    });

    const result = await getCampaignDetails({ tenantId: 'ng', campaignId: 'uuid-1' });

    expect(mockSearchProjectTypeCampaignService).toHaveBeenCalledWith(
      expect.objectContaining({ ids: ['uuid-1'], tenantId: 'ng' })
    );
    expect(result.id).toBe('uuid-1');
  });

  test('searches by campaignNumber when only campaignNumber provided', async () => {
    mockSearchProjectTypeCampaignService.mockResolvedValue({
      CampaignDetails: [{ id: 'uuid-1', campaignNumber: 'HCM-001' }],
    });

    const result = await getCampaignDetails({ tenantId: 'ng', campaignNumber: 'HCM-001' });

    expect(mockSearchProjectTypeCampaignService).toHaveBeenCalledWith(
      expect.objectContaining({ campaignNumber: 'HCM-001', tenantId: 'ng' })
    );
    expect(mockSearchProjectTypeCampaignService).not.toHaveBeenCalledWith(
      expect.objectContaining({ ids: expect.anything() })
    );
    expect(result.campaignNumber).toBe('HCM-001');
  });
});
