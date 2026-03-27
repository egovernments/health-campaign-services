// Tests for taskUtils.persistResourceDetailUpdate — Bug 6: no-op when campaignNumber is null


const mockSearchResourceDetailsFromDB = jest.fn();
const mockProduceModifiedMessages = jest.fn().mockResolvedValue(undefined);

jest.mock('../utils/resourceDetailsUtils', () => ({
  searchResourceDetailsFromDB: mockSearchResourceDetailsFromDB,
}));
jest.mock('../kafka/Producer', () => ({
  produceModifiedMessages: mockProduceModifiedMessages,
}));
jest.mock('../config', () => ({
  default: {
    kafka: {
      KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: 'update-resource-details',
    },
  },
  __esModule: true,
}));
jest.mock('../utils/logger', () => ({
  logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));
// Stub heavy imports pulled in transitively by taskUtils
jest.mock('../utils/campaignUtils', () => ({
  enrichAndPersistCampaignWithErrorProcessingTask: jest.fn(),
  updateResourceDetails: jest.fn(),
}));
jest.mock('../api/coreApis', () => ({
  fetchFileFromFilestore: jest.fn(),
}));
jest.mock('../api/genericApis', () => ({
  createAndUploadFileWithOutRequest: jest.fn(),
}));
jest.mock('../utils/sheetManageUtils', () => ({
  enrichProcessTemplateConfig: jest.fn(),
  handleErrorDuringProcess: jest.fn(),
  processRequest: jest.fn(),
}));
jest.mock('../utils/excelUtils', () => ({
  getExcelWorkbookFromFileURL: jest.fn(),
  getLocaleFromWorkbook: jest.fn(() => 'en_IN'),
  enrichTemplateMetaData: jest.fn(),
}));
jest.mock('../utils/localisationUtils', () => ({
  getLocalisationModuleName: jest.fn(() => 'module'),
}));
jest.mock('../utils/genericUtils', () => ({
  getLocalizedMessagesHandlerViaLocale: jest.fn().mockResolvedValue({}),
  throwError: jest.fn((m: string, s: number, c: string, msg?: string) => {
    throw Object.assign(new Error(msg || c), { status: s, code: c });
  }),
  throwErrorViaWrapper: jest.fn(),
}));
jest.mock('../config/processTemplateConfigs', () => ({
  processTemplateConfigs: { user: { sheets: [] } },
}));
jest.mock('../config/resourceTypeRegistry', () => ({
  getResourceTypeByProcessName: jest.fn(() => 'user'),
  getResourceConfigOrDefault: jest.fn(() => ({})),
}));

import { persistResourceDetailUpdate } from '../utils/taskUtils';

const makeDbRow = (overrides: Record<string, any> = {}) => ({
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
});

describe('persistResourceDetailUpdate', () => {
  test('returns early without DB call when campaignNumber is null', async () => {
    await persistResourceDetailUpdate('ng', 'user', null, { status: 'completed' }, 'user-1', null as any);

    expect(mockSearchResourceDetailsFromDB).not.toHaveBeenCalled();
    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('returns early without DB call when campaignNumber is empty string', async () => {
    await persistResourceDetailUpdate('ng', 'user', null, { status: 'completed' }, 'user-1', '');

    expect(mockSearchResourceDetailsFromDB).not.toHaveBeenCalled();
    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
  });

  test('queries DB by campaignNumber and produces update message when row found', async () => {
    mockSearchResourceDetailsFromDB.mockResolvedValue([makeDbRow()]);

    await persistResourceDetailUpdate('ng', 'user', null, { status: 'completed', processedFileStoreId: 'pfs-1' }, 'user-1', 'HCM-001');

    expect(mockSearchResourceDetailsFromDB).toHaveBeenCalledWith(
      expect.objectContaining({
        tenantId: 'ng',
        campaignNumber: 'HCM-001',
        type: ['user'],
        isActive: true,
      })
    );
    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(1);
    const [payload] = mockProduceModifiedMessages.mock.calls[0];
    expect(payload.ResourceDetails.status).toBe('completed');
    expect(payload.ResourceDetails.processedFileStoreId).toBe('pfs-1');
  });

  test('retries 3 times when row not found, then gives up', async () => {
    jest.useFakeTimers();
    mockSearchResourceDetailsFromDB.mockResolvedValue([]);

    const promise = persistResourceDetailUpdate('ng', 'user', null, { status: 'completed' }, 'user-1', 'HCM-001');

    // Advance timers to skip 2s delays between retries
    for (let i = 0; i < 3; i++) {
      await Promise.resolve();
      jest.advanceTimersByTime(2000);
      await Promise.resolve();
    }
    await promise;

    expect(mockSearchResourceDetailsFromDB).toHaveBeenCalledTimes(3);
    expect(mockProduceModifiedMessages).not.toHaveBeenCalled();
    jest.useRealTimers();
  });

  test('succeeds on second retry when first attempt returns empty', async () => {
    jest.useFakeTimers();
    mockSearchResourceDetailsFromDB
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([makeDbRow()]);

    const promise = persistResourceDetailUpdate('ng', 'user', null, { status: 'completed' }, 'user-1', 'HCM-001');

    // Allow first attempt + 2s delay + second attempt
    await Promise.resolve();
    jest.advanceTimersByTime(2000);
    await Promise.resolve();
    await promise;

    expect(mockSearchResourceDetailsFromDB).toHaveBeenCalledTimes(2);
    expect(mockProduceModifiedMessages).toHaveBeenCalledTimes(1);
    jest.useRealTimers();
  });

  test('includes parentResourceId filter in criteria when non-null', async () => {
    mockSearchResourceDetailsFromDB.mockResolvedValue([makeDbRow({ parentresourceid: 'parent-reg-1' })]);

    await persistResourceDetailUpdate('ng', 'attendanceRegisterAttendee', 'parent-reg-1', { status: 'completed' }, 'user-1', 'HCM-001');

    expect(mockSearchResourceDetailsFromDB).toHaveBeenCalledWith(
      expect.objectContaining({ parentResourceId: 'parent-reg-1' })
    );
  });
});
