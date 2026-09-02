/**
 * Tests for handleUserBoundaryMappings — demap is sheet-presence-driven:
 * only phones explicitly present in the upload (Active/Inactive) may be
 * demapped; phones absent from the sheet keep their mappings (incl. staff
 * adopted from health-project that project-factory never created).
 */

jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn().mockResolvedValue(null),
    setCache: jest.fn().mockResolvedValue(undefined),
    deleteCache: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../utils/cryptUtils', () => ({ encrypt: (v: string) => v, decrypt: (v: string) => `dec-${v}` }));
jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn().mockResolvedValue({ individualIdToWorkerIdMap: new Map(), errors: [] }),
    searchWorkersByIds: jest.fn().mockResolvedValue([]),
}));
jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn().mockImplementation(() => ({ transform: jest.fn().mockResolvedValue([]) })),
}));
jest.mock('../config/transformConfigs', () => ({
    transformConfigs: { employeeHrms: { metadata: {} }, employeeHrmsUnified: { metadata: {} } },
}));
jest.mock('../utils/sheetManageUtils', () => ({
    validateResourceDetailsBeforeProcess: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/paymentValidationUtils', () => ({
    validatePaymentFields: jest.fn().mockReturnValue({ valid: true, errors: [] }),
}));
jest.mock('../utils/campaignUtils', () => ({
    getLocalizedName: (k: string) => k,
    enrichAndPersistCampaignWithError: jest.fn(),
    enrichAndPersistCampaignForCreateViaFlow2: jest.fn(),
    userCredGeneration: jest.fn().mockResolvedValue(undefined),
    markAllToCreateResourcesAsCompleted: jest.fn().mockResolvedValue(undefined),
    populateBoundariesRecursively: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
    getRelatedDataWithUniqueIdentifiers: jest.fn(),
    getMappingDataRelatedToCampaign: jest.fn(),
    prepareProcessesInDb: jest.fn().mockResolvedValue(undefined),
    checkCampaignDataCompletionStatus: jest.fn(),
    checkCampaignMappingCompletionStatus: jest.fn(),
    throwError: jest.fn().mockImplementation((mod: any, code: any, errCode: any, msg: any) => { throw new Error(msg); }),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    pollUntilCount: jest.fn().mockImplementation((fn: any) => fn()),
    pollUntilCountFn: jest.fn(),
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue({ deletedCount: 0 }),
    getCampaignDataRowsWithUniqueIdentifiers: jest.fn().mockResolvedValue([]),
}));
jest.mock('../utils/mappingReconciler', () => ({
    runMappingReconciler: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/attendanceEnrolmentUtils', () => ({
    reconcileAttendanceEnrolmentsForPhones: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: {
            KAFKA_SAVE_SHEET_DATA_TOPIC: 'save-sheet',
            KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet',
            KAFKA_SAVE_MAPPING_DATA_TOPIC: 'save-map',
            KAFKA_UPDATE_MAPPING_DATA_TOPIC: 'update-map',
        },
        host: {},
        paths: {},
        localisation: { defaultLocale: 'en_IN' },
        DB_CONFIG: { DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
        excelIngestion: { sheetFetchPageSize: 2000, persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
        sheetData: { persistBatchSize: 100 },
    },
}));

import { handleUserBoundaryMappings } from '../utils/processingResultHandler';
import { getMappingDataRelatedToCampaign } from '../utils/genericUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { mappingStatuses } from '../config/constants';
import { reconcileAttendanceEnrolmentsForPhones } from '../utils/attendanceEnrolmentUtils';
import config from '../config';

const getMappingMock = getMappingDataRelatedToCampaign as jest.MockedFunction<typeof getMappingDataRelatedToCampaign>;
const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;

function existingMapping(phone: string, boundaryCode: string, status: string = mappingStatuses.mapped) {
    return {
        campaignNumber: 'CMP-1',
        type: 'user',
        uniqueIdentifierForData: phone,
        boundaryCode,
        status,
        mappingId: 'mid-1',
    };
}

function producedTo(topic: string): any[] {
    return produceMock.mock.calls
        .filter(([, t]) => t === topic)
        .flatMap(([msg]) => (msg as any).datas);
}

describe('handleUserBoundaryMappings — sheet-presence-driven demap', () => {
    beforeEach(() => jest.clearAllMocks());

    it('preserves mappings for users absent from the sheet (regression: adopted external staff)', async () => {
        getMappingMock.mockResolvedValue([existingMapping('+91-1', 'B001')] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn', [], new Set(), new Set());

        expect(producedTo('update-map')).toHaveLength(0);
        expect(producedTo('save-map')).toHaveLength(0);
    });

    it('demaps all mappings of a user whose sheet row is Inactive', async () => {
        getMappingMock.mockResolvedValue([
            existingMapping('+91-1', 'B001'),
            existingMapping('+91-1', 'B002'),
        ] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn', [], new Set(), new Set(['+91-1']));

        const demapped = producedTo('update-map');
        expect(demapped).toHaveLength(2);
        expect(demapped.every((m: any) => m.status === mappingStatuses.toBeDeMapped)).toBe(true);
        expect(demapped.map((m: any) => m.boundaryCode).sort()).toEqual(['B001', 'B002']);
    });

    it('demaps only the stale boundary on a boundary move (B001 → B002)', async () => {
        getMappingMock.mockResolvedValue([existingMapping('+91-1', 'B001')] as any);

        await handleUserBoundaryMappings(
            'CMP-1', 'tn',
            [{ phoneNumber: '+91-1', boundaryCode: 'B002', active: true }],
            new Set(), new Set(['+91-1'])
        );

        const demapped = producedTo('update-map');
        expect(demapped).toHaveLength(1);
        expect(demapped[0].boundaryCode).toBe('B001');
        expect(demapped[0].status).toBe(mappingStatuses.toBeDeMapped);

        const created = producedTo('save-map');
        expect(created).toHaveLength(1);
        expect(created[0].boundaryCode).toBe('B002');
        expect(created[0].status).toBe(mappingStatuses.toBeMapped);
    });

    it('does nothing for an Active user whose mapping already exists', async () => {
        getMappingMock.mockResolvedValue([existingMapping('+91-1', 'B001')] as any);

        await handleUserBoundaryMappings(
            'CMP-1', 'tn',
            [{ phoneNumber: '+91-1', boundaryCode: 'B001', active: true }],
            new Set(), new Set(['+91-1'])
        );

        expect(producedTo('update-map')).toHaveLength(0);
        expect(producedTo('save-map')).toHaveLength(0);
    });

    it('is a no-op for an Inactive user who was never mapped', async () => {
        getMappingMock.mockResolvedValue([] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn', [], new Set(), new Set(['+91-1']));

        expect(producedTo('update-map')).toHaveLength(0);
        expect(producedTo('save-map')).toHaveLength(0);
    });

    it('preserves mappings for sheet-invalid users even when listed as present', async () => {
        getMappingMock.mockResolvedValue([existingMapping('+91-1', 'B001')] as any);

        await handleUserBoundaryMappings(
            'CMP-1', 'tn', [],
            new Set(['+91-1']), new Set(['+91-1'])
        );

        expect(producedTo('update-map')).toHaveLength(0);
    });

    it('does not re-demap a mapping already marked toBeDeMapped', async () => {
        getMappingMock.mockResolvedValue([
            existingMapping('+91-1', 'B001', mappingStatuses.toBeDeMapped),
        ] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn', [], new Set(), new Set(['+91-1']));

        expect(producedTo('update-map')).toHaveLength(0);
    });

    it('revives a skipped mapping to toBeMapped on retry (user now created)', async () => {
        getMappingMock.mockResolvedValue([
            existingMapping('+91-1', 'B001', mappingStatuses.skipped),
        ] as any);

        await handleUserBoundaryMappings(
            'CMP-1', 'tn',
            [{ phoneNumber: '+91-1', boundaryCode: 'B001', active: true }],
            new Set(), new Set(['+91-1'])
        );

        const revived = producedTo('update-map');
        expect(revived).toHaveLength(1);
        expect(revived[0]).toMatchObject({
            uniqueIdentifierForData: '+91-1',
            boundaryCode: 'B001',
            status: mappingStatuses.toBeMapped,
        });
        // Must update the existing row, not create a duplicate
        expect(producedTo('save-map')).toHaveLength(0);
    });

    it('does not revive a skipped mapping for a user absent from the sheet', async () => {
        getMappingMock.mockResolvedValue([
            existingMapping('+91-1', 'B001', mappingStatuses.skipped),
        ] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn', [], new Set(), new Set());

        expect(producedTo('update-map')).toHaveLength(0);
        expect(producedTo('save-map')).toHaveLength(0);
    });

    it('creates toBeMapped rows for new active users without touching others', async () => {
        getMappingMock.mockResolvedValue([existingMapping('+91-9', 'B009')] as any);

        await handleUserBoundaryMappings(
            'CMP-1', 'tn',
            [{ phoneNumber: '+91-2', boundaryCode: 'B002', active: true }],
            new Set(), new Set(['+91-2'])
        );

        const created = producedTo('save-map');
        expect(created).toHaveLength(1);
        expect(created[0]).toMatchObject({
            uniqueIdentifierForData: '+91-2',
            boundaryCode: 'B002',
            status: mappingStatuses.toBeMapped,
        });
        expect(producedTo('update-map')).toHaveLength(0);
    });
});

describe('handleUserBoundaryMappings — attendance realignment wiring (unified path)', () => {
    // The flag-ON dev e2e on 2 Sep 2026 caught the realignment wired ONLY into user-processClass,
    // which the unified-console path never executes — so it silently never ran. These tests pin the
    // wiring to THIS handler, the one the unified path actually uses.
    const realignMock = reconcileAttendanceEnrolmentsForPhones as jest.MockedFunction<typeof reconcileAttendanceEnrolmentsForPhones>;

    beforeEach(() => jest.clearAllMocks());
    afterEach(() => { delete (config as any).attendanceRegister; });

    it('REGRESSION: with the flag ON, a boundary move hands the changed phone to the realigner', async () => {
        (config as any).attendanceRegister = { realignEnrolmentsOnAreaChange: true };
        getMappingMock.mockResolvedValue([existingMapping('9111', 'B001')] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn',
            [{ phoneNumber: '9111', boundaryCode: 'B002', active: true }],
            new Set(), new Set(['9111']), { userInfo: { uuid: 'u1' } } as any);

        expect(realignMock).toHaveBeenCalledTimes(1);
        const [phoneMap, cn, tenant] = realignMock.mock.calls[0];
        expect([...phoneMap.keys()]).toEqual(['9111']);
        expect(phoneMap.get('9111')).toEqual(['B002']);
        expect(String(cn)).toBe('CMP-1');
        expect(String(tenant)).toBe('tn');
    });

    it('does not invoke the realigner for a phone whose boundaries did not change', async () => {
        (config as any).attendanceRegister = { realignEnrolmentsOnAreaChange: true };
        getMappingMock.mockResolvedValue([existingMapping('9111', 'B001')] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn',
            [{ phoneNumber: '9111', boundaryCode: 'B001', active: true }],
            new Set(), new Set(['9111']));

        expect(realignMock).not.toHaveBeenCalled();
    });

    it('with the flag OFF (or absent) the realigner is never invoked', async () => {
        getMappingMock.mockResolvedValue([existingMapping('9111', 'B001')] as any);

        await handleUserBoundaryMappings('CMP-1', 'tn',
            [{ phoneNumber: '9111', boundaryCode: 'B002', active: true }],
            new Set(), new Set(['9111']));

        expect(realignMock).not.toHaveBeenCalled();
    });
});
