/**
 * processingResultHandler.posts.test.ts
 *
 * Posts sheet (CLF / transit posts) rows are converted into facility rows and go through the
 * facility path: same campaign data rows (type 'facility'), same mapping list, one link diff over
 * facilities and posts together. Post rows are keyed by clientReferenceId, never by name; facility
 * (non-post) rows stay keyed by name, exactly as today.
 *
 * Drives processCampaignFacilitiesFromExcelData directly against the REAL excelIngestionUtils
 * pagination (mocked httpRequest) — it owns the whole facility+post slice end to end, so there is no
 * need to go through the full handleProcessingResult chain.
 */

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn(),
    setCache: jest.fn(),
    deleteCache: jest.fn(),
}));
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
        basesecret: 'test-base-secret',
        kafka: {
            KAFKA_SAVE_SHEET_DATA_TOPIC: 'save-sheet',
            KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet',
            KAFKA_SAVE_MAPPING_DATA_TOPIC: 'save-map',
            KAFKA_UPDATE_MAPPING_DATA_TOPIC: 'update-map',
        },
        host: { facilityHost: 'http://facility/' },
        paths: { facilitySearch: 'facility/v1/_search', facilityDelete: 'facility/v1/_delete' },
        localisation: { defaultLocale: 'en_IN' },
        DB_CONFIG: { DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
        // Page size 1 → posts span multiple pages too.
        excelIngestion: { sheetFetchPageSize: 1, persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
        facility: { persistBatchSize: 100, creationBatchSize: 100, kafkaCreateBatchSize: 30, searchBatchSize: 50 },
        sheetData: { persistBatchSize: 100 },
    },
}));

import { produceModifiedMessages } from '../kafka/Producer';
import { getRelatedDataWithCampaign, getMappingDataRelatedToCampaign } from '../utils/genericUtils';
import { httpRequest } from '../utils/request';
import { logger } from '../utils/logger';
import {
    convertPostRowsToFacilityRows,
    normalizeForMatch,
    processCampaignFacilitiesFromExcelData,
} from '../utils/processingResultHandler';
import { DataTransformer } from '../utils/transFormUtil';
import { transformConfigs } from '../config/transformConfigs';

const produceMock = jest.mocked(produceModifiedMessages);
const getRelatedMock = jest.mocked(getRelatedDataWithCampaign);
const getMappingMock = jest.mocked(getMappingDataRelatedToCampaign);
const httpMock = jest.mocked(httpRequest);
const warnMock = jest.mocked(logger.warn);

const POSTS_SHEET = 'HCM_ADMIN_CONSOLE_POSTS_LIST';
const campaignDetails = { id: 'cmp-id', campaignNumber: 'CMP-1', campaignName: 'Test' };
const localizationMap = {};

// Paginates per-sheet fixtures by offset/limit; an unknown sheet name returns no rows.
function installSheetRouter(sheetRows: Record<string, any[]>): string[] {
    const requestedSheets: string[] = [];
    httpMock.mockImplementation(async (_url: string, body: any) => {
        const crit = body?.SheetDataSearchCriteria;
        if (!crit) return {} as any;
        const sn: string = crit.sheetName || '';
        requestedSheets.push(sn);
        let rows: any[] = [];
        for (const key of Object.keys(sheetRows)) {
            if (sn.includes(key)) { rows = sheetRows[key]; break; }
        }
        const data = rows
            .slice(crit.offset, crit.offset + crit.limit)
            .map((rowjson, i) => ({ rowjson, rowNumber: crit.offset + i + 3 }));
        return { SheetDataDetails: { Data: data, TotalCount: rows.length, SheetWiseCounts: [] } } as any;
    });
    return requestedSheets;
}

function collectAllTopics(): Record<string, any[]> {
    const out: Record<string, any[]> = {};
    produceMock.mockImplementation(async (msg: any, topic: string) => {
        (out[topic] ||= []).push(...(msg?.datas || []));
    });
    return out;
}

const facilityRow = (name: string, boundary: string, usage = 'Active') => ({
    HCM_ADMIN_CONSOLE_FACILITY_NAME: name,
    HCM_ADMIN_CONSOLE_FACILITY_TYPE: 'Warehouse',
    HCM_ADMIN_CONSOLE_FACILITY_STATUS: 'Permanent',
    HCM_ADMIN_CONSOLE_FACILITY_USAGE: usage,
    HCM_ADMIN_CONSOLE_BOUNDARY_CODE: boundary,
});

// Defaults to a pre-filled clientReferenceId equal to the name, like a real re-upload. Tests of a
// PM-added row with no clientReferenceId use newPostRow instead.
const postRow = (name: string, boundary: string, usage = 'Active', extra: Record<string, unknown> = {}) => ({
    HCM_ADMIN_CONSOLE_POST_TYPE: 'CLF',
    HCM_ADMIN_CONSOLE_POST_CATEGORY: 'CAT_A',
    HCM_ADMIN_CONSOLE_POST_NAME: name,
    HCM_ADMIN_CONSOLE_POST_STATUS: 'Permanent',
    HCM_ADMIN_CONSOLE_POST_USAGE: usage,
    HCM_ADMIN_CONSOLE_BOUNDARY_CODE: boundary,
    HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: name,
    ...extra,
});
const newPostRow = (name: string, boundary: string, usage = 'Active', extra: Record<string, unknown> = {}) =>
    postRow(name, boundary, usage, { HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: '', ...extra });

const mapKeys = (rows: any[] = []) => rows.map((m: any) => `${m.uniqueIdentifierForData}#${m.boundaryCode}`).sort();
const facilityMappingSearches = () => getMappingMock.mock.calls.filter((c: any[]) => c[0] === 'facility');
const setFacilityLinks = (links: any[]) =>
    getMappingMock.mockImplementation(async (type: any) => (type === 'facility' ? links : []) as any);

describe('convertPostRowsToFacilityRows', () => {
    afterEach(() => jest.clearAllMocks());

    describe('row conversion', () => {
        it('maps post keys onto facility keys and keeps category, boundary and clientReferenceId', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2') }], new Map());
            expect(converted.rowjson).toMatchObject({
                HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P1',
                HCM_ADMIN_CONSOLE_FACILITY_TYPE: 'CLF',
                HCM_ADMIN_CONSOLE_FACILITY_STATUS: 'Permanent',
                HCM_ADMIN_CONSOLE_FACILITY_USAGE: 'Active',
                HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B2',
                HCM_ADMIN_CONSOLE_POST_CATEGORY: 'CAT_A',
                HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: 'P1',
            });
        });

        it('passes TRANSIT_POST through as the facility type', () => {
            const [converted] = convertPostRowsToFacilityRows(
                [{ rowjson: postRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_POST_TYPE: 'TRANSIT_POST' }) }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_TYPE).toBe('TRANSIT_POST');
        });

        it('sets no facility code on a post row (post code is never pre-filled any more)', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2') }], new Map());
            expect(converted.rowjson).not.toHaveProperty('HCM_ADMIN_CONSOLE_FACILITY_CODE');
        });

        it('sets no capacity on a post row', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2') }], new Map());
            expect(converted.rowjson).not.toHaveProperty('HCM_ADMIN_CONSOLE_FACILITY_CAPACITY');
        });

        it('reads the rowJson spelling too', () => {
            const converted = convertPostRowsToFacilityRows([{ rowJson: postRow('P1', 'B2') }], new Map());
            expect(converted).toHaveLength(1);
        });

        it('skips a row without a post name with a warning', () => {
            const converted = convertPostRowsToFacilityRows([{ rowjson: postRow('', 'B2') }], new Map());
            expect(converted).toHaveLength(0);
            expect(warnMock).toHaveBeenCalledWith('No post name found in row');
        });
    });

    describe('usage', () => {
        it('keeps Active as Active', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2', 'Active') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Active');
        });

        it('keeps Inactive as Inactive', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2', 'Inactive') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
        });

        it('treats the old Include / Remove values as Inactive (only Active links a post)', () => {
            const [include] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2', 'Include') }], new Map());
            const [remove] = convertPostRowsToFacilityRows([{ rowjson: postRow('P2', 'B2', 'Remove') }], new Map());
            expect(include.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
            expect(remove.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
        });

        it('keeps the optional frontline worker columns on the converted row', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2', 'Active', {
                HCM_ADMIN_CONSOLE_POST_WORKER_NAME: 'Asha', HCM_ADMIN_CONSOLE_POST_WORKER_PHONE: 9876543210 }) }], new Map());
            expect(converted.rowjson).toMatchObject({
                HCM_ADMIN_CONSOLE_POST_WORKER_NAME: 'Asha', HCM_ADMIN_CONSOLE_POST_WORKER_PHONE: 9876543210 });
        });

        it('maps any other usage value to Inactive too — the schema enum is what rejects it', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2', 'SOMETHING_ELSE') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
        });
    });

    describe('clientReferenceId resolution', () => {
        it('keeps the pre-filled clientReferenceId', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: 'CRID-1' }) }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toBe('CRID-1');
        });

        it('reuses the stored clientReferenceId of a matching normalised (type, boundary, name) when the row has none', () => {
            const storedIndex = new Map([[normalizeForMatch('CLF', 'B2', 'P1'), 'CRID-OLD']]);
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: newPostRow('P1', 'B2') }], storedIndex);
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
        });

        it('the match is case-insensitive and ignores extra spacing', () => {
            const storedIndex = new Map([[normalizeForMatch('clf', 'b2', 'p 1'), 'CRID-OLD']]);
            const [converted] = convertPostRowsToFacilityRows(
                [{ rowjson: newPostRow('P  1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_POST_TYPE: 'CLF' }) }], storedIndex);
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
        });

        it('generates a new id when the row has none and no stored row matches', () => {
            const [converted] = convertPostRowsToFacilityRows([{ rowjson: newPostRow('P1', 'B2') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toEqual(expect.any(String));
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).not.toBe('');
        });

        it('two rows with the same (type, boundary, name) share one post, the same way facility rows with the same name share one facility', () => {
            const storedIndex = new Map([[normalizeForMatch('CLF', 'B2', 'P1'), 'CRID-OLD']]);
            const [a, b] = convertPostRowsToFacilityRows(
                [{ rowjson: newPostRow('P1', 'B2') }, { rowjson: newPostRow('P1', 'B2') }], storedIndex);
            expect(a.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
            expect(b.rowjson.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
        });
    });
});

describe('processCampaignFacilitiesFromExcelData with the posts sheet', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        getRelatedMock.mockResolvedValue([] as any);
        setFacilityLinks([] as any);
    });

    describe('sheet reading', () => {
        it('reads the posts sheet by its localisation key', async () => {
            const requested = installSheetRouter({ FACILIT: [] });
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(requested).toContain(POSTS_SHEET);
        });
    });

    describe('campaign data rows', () => {
        it('persists post rows as facility rows carrying the post marker, keyed by clientReferenceId', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], POSTS: [postRow('P1', 'B2'), postRow('P2', 'B3', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);

            const saved = (topics['save-sheet'] || []).filter((d: any) => d.type === 'facility');
            expect(saved.map((d: any) => d.uniqueIdentifier).sort()).toEqual(['F1', 'P1', 'P2']);
            const p2 = saved.find((d: any) => d.uniqueIdentifier === 'P2');
            expect(p2.data.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
            expect(p2.data.HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID).toBe('P2');
            expect(p2.status).toBe('pending');
        });
    });

    describe('one combined mapping set', () => {
        it('runs the facility link diff once over facilities and posts', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], POSTS: [postRow('P1', 'B2')] });
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(facilityMappingSearches()).toHaveLength(1);
        });

        it('creates links for Active facilities and Include posts in one batch', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], POSTS: [postRow('P1', 'B2'), postRow('P2', 'B3', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['save-map'])).toEqual(['F1#B1', 'P1#B2']);
        });

        it('keeps both kinds of existing links that are still in the sheets', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], POSTS: [postRow('P1', 'B2')] });
            setFacilityLinks([
                { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
                { uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped' },
            ] as any);
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(topics['update-map'] || []).toHaveLength(0);
            expect(topics['save-map'] || []).toHaveLength(0);
        });

        it('demaps an excluded post and a facility gone from the sheet (both sheets contributed: full diff)', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], POSTS: [postRow('P1', 'B2', 'Inactive')] });
            setFacilityLinks([
                { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
                { uniqueIdentifierForData: 'F-OLD', boundaryCode: 'B9', status: 'mapped' },
                { uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped' },
            ] as any);
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['F-OLD#B9', 'P1#B2']);
        });
    });

    describe('facility sheet with no links (§task 3: per-sheet demap guard)', () => {
        const storedRows = [
            { uniqueIdentifier: 'F1', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F1', HCM_ADMIN_CONSOLE_FACILITY_USAGE: 'Active', HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B1' } },
            { uniqueIdentifier: 'P-OLD', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P-OLD', HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: 'P-OLD' } },
        ];
        const storedLinks = [
            { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
            { uniqueIdentifierForData: 'P-OLD', boundaryCode: 'B5', status: 'mapped' },
        ];

        beforeEach(() => {
            getRelatedMock.mockImplementation(async (type: any) => (type === 'facility' ? storedRows : []) as any);
            setFacilityLinks(storedLinks);
        });

        it('processes posts when the facility sheet has no rows', async () => {
            installSheetRouter({ FACILIT: [], POSTS: [postRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);

            const saved = (topics['save-sheet'] || []).filter((d: any) => d.type === 'facility');
            expect(saved.map((d: any) => d.uniqueIdentifier)).toEqual(['P1']);
            expect(mapKeys(topics['save-map'])).toEqual(['P1#B2']);
        });

        it('demaps only post links when the facility sheet has no rows', async () => {
            installSheetRouter({ FACILIT: [], POSTS: [postRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('demaps only post links when no facility row is Active', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1', 'Inactive')], POSTS: [postRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('demaps an excluded post when the facility sheet has no rows, even with no stored campaign_data row for it', async () => {
            setFacilityLinks([...storedLinks, { uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped' }] as any);
            installSheetRouter({ FACILIT: [], POSTS: [postRow('P1', 'B2', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5', 'P1#B2']);
        });

        it('keeps post links unchanged when the posts sheet is absent (facility sheet alone contributed)', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            // F1 stays (still in the sheet); P-OLD is frozen (posts sheet absent this upload), not demapped.
            expect(topics['update-map'] || []).toHaveLength(0);
        });

        it('keeps post links unchanged when the posts sheet is present but empty', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], POSTS: [] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(topics['update-map'] || []).toHaveLength(0);
        });
    });

    describe('campaigns without a posts sheet', () => {
        it('does nothing when both sheets are empty', async () => {
            installSheetRouter({ FACILIT: [] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(facilityMappingSearches()).toHaveLength(0);
            expect((topics['save-sheet'] || []).filter((d: any) => d.type === 'facility')).toHaveLength(0);
        });

        it('keeps the facility-only link diff unchanged (today\'s behaviour, posts sheet never uploaded)', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            setFacilityLinks([{ uniqueIdentifierForData: 'F-OLD', boundaryCode: 'B9', status: 'mapped' }] as any);
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['save-map'])).toEqual(['F1#B1']);
            expect(mapKeys(topics['update-map'])).toEqual(['F-OLD#B9']);
        });

        it('keeps facility links when no facility row is Active and there are no posts', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1', 'Inactive')] });
            setFacilityLinks([{ uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' }] as any);
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData('tn', 'fs-1', localizationMap, campaignDetails);
            expect(facilityMappingSearches()).toHaveLength(0);
        });
    });
});

// Real DataTransformer + the real FacilityUnified config (as used by facilityBatchHandler) — proves
// the transform itself stamps the Post marker, category and clientReferenceId, and only on a row
// that went through convertPostRowsToFacilityRows; a plain facility row gets neither.
describe('transformBulkFacility (real transform, FacilityUnified config)', () => {
    const requestInfo = { userInfo: { uuid: 'u1' } } as any;

    function buildTransformer() {
        const cfg = JSON.parse(JSON.stringify(transformConfigs.FacilityUnified));
        cfg.metadata.tenantId = 'mz';
        return new DataTransformer(cfg);
    }

    it('stamps the marker, category and clientReferenceId only on the post row', async () => {
        const [postRowJson] = convertPostRowsToFacilityRows([{ rowjson: postRow('P1', 'B2') }], new Map());
        const [facilityOut, postOut] = await buildTransformer().transform(
            [facilityRow('F1', 'B1'), postRowJson.rowjson], requestInfo
        );

        expect(facilityOut.Facility).not.toHaveProperty('clientReferenceId');
        expect(facilityOut.Facility).not.toHaveProperty('additionalFields');

        expect(postOut.Facility.clientReferenceId).toBe('P1');
        expect(postOut.Facility.additionalFields).toEqual({
            schema: 'Post',
            version: 1,
            fields: [{ key: 'postCategory', value: 'CAT_A' }],
        });
    });

    it('stamps the marker with an empty fields array when the category is blank', async () => {
        const [postRowJson] = convertPostRowsToFacilityRows(
            [{ rowjson: postRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_POST_CATEGORY: '  ' }) }], new Map());
        const [postOut] = await buildTransformer().transform([postRowJson.rowjson], requestInfo);
        expect(postOut.Facility.additionalFields).toEqual({ schema: 'Post', version: 1, fields: [] });
    });
});

describe('buildPostLinkAdditionalFields (frontline worker on the post link)', () => {
    // Imported lazily so the module-level mocks above are in place first
    const { buildPostLinkAdditionalFields } = require('../utils/mappingBatchHandler');

    it('puts name and phone on the link of a post row, keyed by its uniqueIdentifier', () => {
        const result = buildPostLinkAdditionalFields([{
            uniqueIdentifier: 'CRID-1',
            data: { HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: 'CRID-1',
                    HCM_ADMIN_CONSOLE_POST_WORKER_NAME: ' Asha ', HCM_ADMIN_CONSOLE_POST_WORKER_PHONE: 9876543210 },
        }]);
        expect(result).toEqual({ 'CRID-1': { schema: 'PostAssignment', version: 1, fields: [
            { key: 'frontlineWorkerName', value: 'Asha' },
            { key: 'frontlineWorkerPhone', value: '9876543210' },
        ] } });
    });

    it('keeps only the worker fields that are filled', () => {
        const result = buildPostLinkAdditionalFields([{
            uniqueIdentifier: 'CRID-2',
            data: { HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: 'CRID-2', HCM_ADMIN_CONSOLE_POST_WORKER_PHONE: '9123456780' },
        }]);
        expect(result['CRID-2'].fields).toEqual([{ key: 'frontlineWorkerPhone', value: '9123456780' }]);
    });

    it('adds nothing for a post without a worker, or for a facility row', () => {
        const result = buildPostLinkAdditionalFields([
            { uniqueIdentifier: 'CRID-3', data: { HCM_ADMIN_CONSOLE_POST_CLIENT_REFERENCE_ID: 'CRID-3', HCM_ADMIN_CONSOLE_POST_WORKER_NAME: '  ' } },
            { uniqueIdentifier: 'Warehouse A', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'Warehouse A', HCM_ADMIN_CONSOLE_POST_WORKER_NAME: 'X' } },
        ]);
        expect(result).toEqual({});
    });
});
