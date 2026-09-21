import { TemplateClass } from '../processFlowClasses/user-processClass';
import { dataRowStatuses, sheetDataRowStatuses } from '../config/constants';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../utils/sheetManageUtils', () => ({
    validateResourceDetailsBeforeProcess: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/paymentValidationUtils', () => ({
    validatePaymentFields: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/campaignUtils', () => ({
    getLocalizedName: (k: string) => k, // pass-through localization
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
    getRelatedDataWithUniqueIdentifiers: jest.fn(),
    getMappingDataRelatedToCampaign: jest.fn(),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn().mockImplementation(() => ({ transform: jest.fn().mockResolvedValue([]) })),
}));

jest.mock('../config/transformConfigs', () => ({
    transformConfigs: { employeeHrms: { metadata: {} } },
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/cryptUtils', () => ({
    encrypt: (v: any) => v,
    // Make decrypt deterministic so the test asserts the decrypted column.
    decrypt: (v: any) => (v ? `decrypted-${v}` : v),
}));

jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn().mockResolvedValue({ individualIdToWorkerIdMap: new Map(), errors: [] }),
    searchWorkersByIds: jest.fn().mockResolvedValue([]),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: {
            KAFKA_SAVE_SHEET_DATA_TOPIC: 'save-sheet',
            KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet',
        },
        batchSize: 100,
        project: { creationBatchSize: 20 },
        boundary: { mappingPersistBatchSize: 100, persistBatchSize: 100 },
        facility: { persistBatchSize: 100, creationBatchSize: 100, kafkaCreateBatchSize: 30, searchBatchSize: 50 },
        user: { mappingPersistBatchSize: 100, persistBatchSize: 100, creationBatchSize: 100, kafkaCreateBatchSize: 30, searchBatchSize: 50, validationSearchBatchSize: 50, individualSearchBatchSize: 50 },
        workerRegistry: { searchBatchSize: 50, updateBatchSize: 100 },
        mapping: { kafkaBatchSize: 30, persistBatchSize: 100 },
        attendanceRegister: { attendeePersistBatchSize: 100, registerPersistBatchSize: 100, registerApiBatchSize: 100 },
        resource: { activityBatchSize: 10 },
        productVariant: { searchBatchSize: 100 },
        sheetData: { persistBatchSize: 100 },
    },
}));

import {
    searchProjectTypeCampaignService,
} from '../service/campaignManageService';
import {
    getRelatedDataWithCampaign,
    getRelatedDataWithUniqueIdentifiers,
    getMappingDataRelatedToCampaign,
} from '../utils/genericUtils';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const getRelatedDataWithCampaignMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const getRelatedDataWithUniqueIdentifiersMock = getRelatedDataWithUniqueIdentifiers as jest.MockedFunction<typeof getRelatedDataWithUniqueIdentifiers>;
const getMappingDataRelatedToCampaignMock = getMappingDataRelatedToCampaign as jest.MockedFunction<typeof getMappingDataRelatedToCampaign>;

describe('TemplateClass.process — credential sheet preserves INVALID/FAILED discriminator', () => {
    const resourceDetails: any = {
        tenantId: 'tn',
        type: 'user',
        campaignId: 'cmp-id',
        hierarchyType: 'h',
        requestInfo: { userInfo: { uuid: 'u' } },
    };
    const localizationMap = {} as Record<string, string>;
    const wholeSheetData = { 'HCM_ADMIN_CONSOLE_USER_LIST': [] };

    beforeEach(() => {
        jest.clearAllMocks();
        // process() awaits a 5s setTimeout for persistence — short-circuit it.
        jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => {
            cb();
            return 0 as any;
        }) as any);
        searchCampaignMock.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: 'CMP-1', auditDetails: { createdBy: 'u' }, id: 'cmp-id' }],
        } as any);
        getRelatedDataWithUniqueIdentifiersMock.mockResolvedValue([] as any);
        getMappingDataRelatedToCampaignMock.mockResolvedValue([] as any);
    });

    afterEach(() => {
        (global.setTimeout as any).mockRestore?.();
    });

    it('keeps #status#=INVALID for sheet-validation-failed rows and #status#=FAILED for HRMS-failed rows', async () => {
        const invalidRow = {
            status: dataRowStatuses.failed,
            data: {
                'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': '+91-1',
                '#status#': sheetDataRowStatuses.INVALID,
                '#errorDetails#': 'phone format wrong',
            },
            uniqueIdentifier: '+91-1',
        };
        const hrmsFailedRow = {
            status: dataRowStatuses.failed,
            data: {
                'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': '+91-2',
                '#status#': sheetDataRowStatuses.FAILED,
                '#errorDetails#': 'hrms 500',
            },
            uniqueIdentifier: '+91-2',
        };
        const completedRow = {
            status: dataRowStatuses.completed,
            data: {
                'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': '+91-3',
                'UserName': 'enc-name',
                'Password': 'enc-pwd',
            },
            uniqueIdentifier: '+91-3',
        };

        // process() calls getRelatedDataWithCampaign 3 times:
        //   1. line 38 — existingUsersForCampaign (return empty so flow continues)
        //   2. inside createUserFromTableData — return empty so no creation attempted
        //   3. line 56 — allCurrentUsers, the read whose output goes into the sheet
        getRelatedDataWithCampaignMock
            .mockResolvedValueOnce([] as any)
            .mockResolvedValueOnce([] as any)
            .mockResolvedValueOnce([invalidRow, hrmsFailedRow, completedRow] as any)
            .mockResolvedValue([] as any);

        const sheetMap = await TemplateClass.process(resourceDetails, wholeSheetData, localizationMap, {});

        const data = (sheetMap as any)['HCM_ADMIN_CONSOLE_USER_LIST'].data as any[];
        const byPhone: Record<string, any> = {};
        data.forEach(r => { byPhone[r['HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER']] = r; });

        expect(byPhone['+91-1']['#status#']).toBe(sheetDataRowStatuses.INVALID);
        expect(byPhone['+91-1']['#errorDetails#']).toBe('phone format wrong');
        expect(byPhone['+91-2']['#status#']).toBe(sheetDataRowStatuses.FAILED);
        expect(byPhone['+91-2']['#errorDetails#']).toBe('hrms 500');
        expect(byPhone['+91-3']['#status#']).toBe(sheetDataRowStatuses.CREATED);
        expect(byPhone['+91-3']['UserName']).toBe('decrypted-enc-name');
        expect(byPhone['+91-3']['Password']).toBe('decrypted-enc-pwd');
    });

    it('falls back to #status#=FAILED for legacy failed rows that have no discriminator', async () => {
        const legacyFailedRow = {
            status: dataRowStatuses.failed,
            data: { 'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': '+91-9' },
            uniqueIdentifier: '+91-9',
        };

        getRelatedDataWithCampaignMock
            .mockResolvedValueOnce([] as any)
            .mockResolvedValueOnce([] as any)
            .mockResolvedValueOnce([legacyFailedRow] as any)
            .mockResolvedValue([] as any);

        const sheetMap = await TemplateClass.process(resourceDetails, wholeSheetData, localizationMap, {});
        const data = (sheetMap as any)['HCM_ADMIN_CONSOLE_USER_LIST'].data as any[];
        expect(data[0]['#status#']).toBe(sheetDataRowStatuses.FAILED);
    });
});
