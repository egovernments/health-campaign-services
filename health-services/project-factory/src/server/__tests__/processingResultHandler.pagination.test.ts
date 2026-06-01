/**
 * processingResultHandler.pagination.test.ts
 *
 * Integration-style tests that drive handleProcessingResult with the REAL
 * excelIngestionUtils (searchSheetData / getSheetDataCount / forEachSheetDataPage)
 * against a mocked httpRequest that paginates synthetic sheet data.
 *
 * Goal: prove the per-type reads (boundary / facility / user) produce identical
 * results whether the sheet arrives in one page or several — i.e. pagination
 * never drops, duplicates, or mis-cascades rows, and bounded-memory processing
 * preserves behavior (no breaking change).
 *
 * totalRowsProcessed is kept 0 here so the persistence gate is skipped; the gate
 * (count-based) is covered separately in processingResultHandler.gate.test.ts.
 */

// A tiny page size (set in the config mock below) makes small fixtures span
// multiple pages, exercising the pagination loop.

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
jest.mock('../api/coreApis', () => ({
    searchMDMSDataViaV2Api: jest.fn().mockResolvedValue({}),
    searchBoundaryRelationshipData: jest.fn().mockResolvedValue({}),
}));
jest.mock('../api/campaignApis', () => ({ confirmProjectParentCreation: jest.fn().mockResolvedValue({}) }));
jest.mock('../utils/onGoingCampaignUpdateUtils', () => ({
    fetchProjectsWithBoundaryCodeAndReferenceId: jest.fn().mockResolvedValue([]),
}));
jest.mock('../utils/mailUtils', () => ({ triggerUserCredentialEmailFlow: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/transforms/projectTypeUtils', () => ({
    enrichProjectDetailsFromCampaignDetails: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/localisationUtils', () => ({
    getLocalisationModuleName: jest.fn().mockReturnValue('hcm-admin-console'),
}));
jest.mock('../controllers/localisationController/localisation.controller', () => ({
    __esModule: true,
    default: {
        getInstance: jest.fn().mockReturnValue({
            getLocalisedData: jest.fn().mockResolvedValue([]),
        }),
    },
}));
jest.mock('../utils/campaignUtils', () => ({
    populateBoundariesRecursively: jest.fn().mockResolvedValue(undefined),
    getLocalizedName: (k: string) => k,
    enrichAndPersistCampaignWithError: jest.fn(),
    enrichAndPersistCampaignForCreateViaFlow2: jest.fn(),
    userCredGeneration: jest.fn().mockResolvedValue(undefined),
    markAllToCreateResourcesAsCompleted: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn().mockResolvedValue([]),
    getMappingDataRelatedToCampaign: jest.fn().mockResolvedValue([]),
    prepareProcessesInDb: jest.fn().mockResolvedValue(undefined),
    getRelatedDataWithUniqueIdentifiers: jest.fn().mockResolvedValue([]),
    checkCampaignDataCompletionStatus: jest.fn().mockResolvedValue({ allCompleted: true, anyFailed: false }),
    checkCampaignMappingCompletionStatus: jest.fn().mockResolvedValue({ allCompleted: true }),
    throwError: jest.fn().mockImplementation((_m: any, _s: any, _c: any, d: any) => { throw new Error(d); }),
    getCurrentProcesses: jest.fn().mockResolvedValue([]),
    pollUntilCount: jest.fn().mockResolvedValue(undefined),
    pollUntilCountFn: jest.fn().mockResolvedValue(undefined),
    deleteCampaignDataFailedAndInvalid: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: {
            KAFKA_SAVE_SHEET_DATA_TOPIC: 'save-sheet',
            KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet',
            KAFKA_SAVE_MAPPING_DATA_TOPIC: 'save-map',
            KAFKA_UPDATE_MAPPING_DATA_TOPIC: 'update-map',
            KAFKA_USER_CREATE_BATCH_TOPIC: 'user-batch',
            KAFKA_HCM_PROCESSING_RESULT_TOPIC: 'result',
        },
        host: { hrmsHost: 'http://hrms/', healthIndividualHost: 'http://individual/', excelIngestionHost: 'http://ei/' },
        paths: {
            hrmsEmployeeCreate: 'hrms/create',
            healthIndividualSearch: 'individual/search',
            excelIngestionSheetSearch: 'ei/search',
        },
        localisation: { defaultLocale: 'en_IN' },
        DB_CONFIG: { DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
        // Page size 1 → small fixtures span multiple pages.
        excelIngestion: { sheetFetchPageSize: 1, persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
    },
}));

import { produceModifiedMessages } from '../kafka/Producer';
import {
    getRelatedDataWithCampaign,
    getRelatedDataWithUniqueIdentifiers,
    getMappingDataRelatedToCampaign,
} from '../utils/genericUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { searchMDMSDataViaV2Api, searchBoundaryRelationshipData } from '../api/coreApis';
import { httpRequest } from '../utils/request';

const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const getRelatedMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const getRelatedWithUidMock = getRelatedDataWithUniqueIdentifiers as jest.MockedFunction<typeof getRelatedDataWithUniqueIdentifiers>;
const getMappingMock = getMappingDataRelatedToCampaign as jest.MockedFunction<typeof getMappingDataRelatedToCampaign>;
const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const mdmsMock = searchMDMSDataViaV2Api as jest.MockedFunction<typeof searchMDMSDataViaV2Api>;
const boundaryRelMock = searchBoundaryRelationshipData as jest.MockedFunction<typeof searchBoundaryRelationshipData>;
const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;

// Builds an httpRequest mock that paginates per-sheet fixtures by offset/limit.
// sheetRows: map of "matcher substring" -> array of rowjson objects.
function installSheetRouter(sheetRows: Record<string, any[]>, totalCountForGate = 1) {
    httpMock.mockImplementation(async (_url: string, body: any) => {
        const crit = body?.SheetDataSearchCriteria;
        if (!crit) return { Individual: [] } as any;          // individual search etc.
        if (crit.limit === 1 && !crit.sheetName) {            // count gate (presence/persistence)
            return { SheetDataDetails: { Data: [{}], TotalCount: totalCountForGate, SheetWiseCounts: [] } } as any;
        }
        const sn: string = crit.sheetName || '';
        let rows: any[] = [];
        for (const key of Object.keys(sheetRows)) {
            if (sn.includes(key)) { rows = sheetRows[key]; break; }
        }
        const data = rows
            .slice(crit.offset, crit.offset + crit.limit)
            .map((rowjson, i) => ({ rowjson, rowNumber: crit.offset + i + 3 }));
        return { SheetDataDetails: { Data: data, TotalCount: rows.length, SheetWiseCounts: [] } } as any;
    });
}

const baseCampaign = {
    id: 'cmp-id', campaignNumber: 'CMP-1', campaignName: 'Test',
    auditDetails: { createdBy: 'u' }, hierarchyType: 'H',
    boundaries: [], status: 'inprogress', projectType: 'PT',
};

const baseMsg = (extra: any = {}) => ({
    id: '1', tenantId: 'tn', referenceId: 'cmp-id', fileStoreId: 'fs-1',
    type: 'unified-console', status: 'completed',
    additionalDetails: {
        boundarySheetStatus: 'valid', facilitySheetStatus: 'valid',
        userSheetStatus: 'valid', totalRowsProcessed: 0,
    },
    requestInfo: { userInfo: { uuid: 'u' } },
    ...extra,
});

const collectByTopic = (topic: string) => {
    const out: any[] = [];
    produceMock.mockImplementation(async (msg: any, t: string) => {
        if (t === topic) out.push(...(msg?.datas || []));
    });
    return out;
};

describe('Facility processing — paginated multi-page parity', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [baseCampaign] } as any);
        getRelatedMock.mockResolvedValue([] as any);
        getRelatedWithUidMock.mockResolvedValue([] as any);
        getMappingMock.mockResolvedValue([] as any);
        mdmsMock.mockResolvedValue({} as any);   // boundary returns 0 early
    });

    it('persists every new facility across 3 single-row pages', async () => {
        const facilityRows = [
            { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F1', HCM_ADMIN_CONSOLE_FACILITY_CODE: 'C1' },
            { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F2', HCM_ADMIN_CONSOLE_FACILITY_CODE: 'C2' },
            { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F3', HCM_ADMIN_CONSOLE_FACILITY_CODE: 'C3' },
        ];
        installSheetRouter({ FACILIT: facilityRows, USER: [], BOUNDARY: [] });
        const saved = collectByTopic('save-sheet');

        const { handleProcessingResult } = await import('../utils/processingResultHandler');
        try { await handleProcessingResult(baseMsg()); } catch (_) { /* ignore downstream */ }

        const names = saved
            .filter((d: any) => d.type === 'facility')
            .map((d: any) => d.uniqueIdentifier)
            .sort();
        expect(names).toEqual(['F1', 'F2', 'F3']);
    });
});

describe('User processing — two-pass paginated parity', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [baseCampaign] } as any);
        getRelatedMock.mockResolvedValue([] as any);
        getRelatedWithUidMock.mockResolvedValue([] as any);
        getMappingMock.mockResolvedValue([] as any);
        mdmsMock.mockResolvedValue({} as any);
    });

    it('collects phones across all pages into the single cross-campaign lookup', async () => {
        const userRows = [
            { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '+91-1', HCM_ADMIN_CONSOLE_USER_USAGE: 'Active', '#status#': 'VALID' },
            { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '+91-2', HCM_ADMIN_CONSOLE_USER_USAGE: 'Active', '#status#': 'VALID' },
            { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '+91-3', HCM_ADMIN_CONSOLE_USER_USAGE: 'Active', '#status#': 'VALID' },
        ];
        installSheetRouter({ USER: userRows, FACILIT: [], BOUNDARY: [] });
        const saved = collectByTopic('save-sheet');

        const { handleProcessingResult } = await import('../utils/processingResultHandler');
        try { await handleProcessingResult(baseMsg()); } catch (_) { /* ignore downstream */ }

        // All three phones persisted as new users
        const phones = saved
            .filter((d: any) => d.type === 'user')
            .map((d: any) => d.uniqueIdentifier)
            .sort();
        expect(phones).toEqual(['+91-1', '+91-2', '+91-3']);

        // The cross-campaign lookup must have been called once with ALL phones
        // (proves Pass A collected phones across every page before the lookup).
        expect(getRelatedWithUidMock).toHaveBeenCalledTimes(1);
        const phoneArg = (getRelatedWithUidMock.mock.calls[0] as any)[1];
        expect([...phoneArg].sort()).toEqual(['+91-1', '+91-2', '+91-3']);
    });

    it('preserves an invalid user (page 2) from being demapped — invalidUserPhones union across pages', async () => {
        const userRows = [
            { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '+91-1', HCM_ADMIN_CONSOLE_USER_USAGE: 'Active', '#status#': 'VALID' },
            { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '+91-9', HCM_ADMIN_CONSOLE_USER_USAGE: 'Active', '#status#': 'INVALID' },
        ];
        installSheetRouter({ USER: userRows, FACILIT: [], BOUNDARY: [] });

        // Existing mapping for the invalid user must NOT be demapped
        getMappingMock.mockResolvedValue([
            { uniqueIdentifierForData: '+91-9', boundaryCode: 'B-OLD', status: 'mapped' },
        ] as any);

        const demapped = collectByTopic('update-map');

        const { handleProcessingResult } = await import('../utils/processingResultHandler');
        try { await handleProcessingResult(baseMsg()); } catch (_) { /* ignore downstream */ }

        const demappedPhones = demapped.map((d: any) => d.uniqueIdentifierForData);
        expect(demappedPhones).not.toContain('+91-9');
    });
});

describe('Boundary processing — paginated cascade parity', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        getRelatedMock.mockResolvedValue([] as any);
        getMappingMock.mockResolvedValue([] as any);
        getRelatedWithUidMock.mockResolvedValue([] as any);
        searchCampaignMock.mockResolvedValue({
            CampaignDetails: [{
                ...baseCampaign,
                boundaries: [
                    { code: 'ROOT', includeAllChildren: true },
                    { code: 'C1', parent: 'ROOT', includeAllChildren: true },
                    { code: 'C2', parent: 'ROOT', includeAllChildren: true },
                ],
            }],
        } as any);
        mdmsMock.mockResolvedValue({
            mdms: [{ data: { beneficiaries: [{ columns: ['TARGET_1'] }] } }],
        } as any);
        boundaryRelMock.mockResolvedValue({
            TenantBoundary: [{ boundary: [{ code: 'ROOT' }] }],
        } as any);
    });

    it('cascades leaf targets to the parent across multi-page boundary data', async () => {
        const boundaryRows = [
            { HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'C1', TARGET_1: 10 },
            { HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'C2', TARGET_1: 20 },
        ];
        installSheetRouter({ BOUNDARY: boundaryRows, FACILIT: [], USER: [] });
        const saved = collectByTopic('save-sheet');

        const { handleProcessingResult } = await import('../utils/processingResultHandler');
        try { await handleProcessingResult(baseMsg()); } catch (_) { /* ignore downstream */ }

        const boundaryEntries = saved.filter((d: any) => d.type === 'boundary');
        const byCode: Record<string, any> = {};
        boundaryEntries.forEach((e: any) => { byCode[e.uniqueIdentifier] = e.data; });

        // Leaves keep their own targets; ROOT is the cascaded sum (10 + 20)
        expect(byCode['C1']?.TARGET_1).toBe(10);
        expect(byCode['C2']?.TARGET_1).toBe(20);
        expect(byCode['ROOT']?.TARGET_1).toBe(30);
    });
});
