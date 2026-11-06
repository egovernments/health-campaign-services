/**
 * processingResultHandler.sites.test.ts
 *
 * Outside Household sheet (Outside Household Places / Transit Posts) rows are converted into facility rows and go through the
 * facility path: same campaign data rows (type 'facility'), same mapping list, one link diff over
 * facilities and sites together. Site rows are keyed by clientReferenceId, never by name; facility
 * (non-site) rows stay keyed by name, exactly as today.
 *
 * Drives processCampaignFacilitiesFromExcelData directly against the REAL excelIngestionUtils
 * pagination (mocked httpRequest) — it owns the whole facility+site slice end to end, so there is no
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
    markSiteLinkWorkerUpdatesFailed: jest.fn().mockResolvedValue(new Set()),
    markSiteLinkWorkerUpdatesFailedBySite: jest.fn().mockResolvedValue(0),
    markSiteLinkWorkerUpdatesDone: jest.fn().mockResolvedValue(0),
    resetFailedSiteLinkWorkerUpdates: jest.fn().mockResolvedValue(0),
    markSiteLinksToCreateAgain: jest.fn().mockResolvedValue(0),
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
        host: jest.requireActual('../config').default.host,
        paths: jest.requireActual('../config').default.paths,
        mapping: { searchPageSize: 100, outsideHouseholdLinkUpdateConcurrency: 2, outsideHouseholdLinkRereadAttempts: 2, outsideHouseholdLinkRereadWaitMs: 0, outsideHouseholdUnlinkOnDeliveryRemoval: true, outsideHouseholdIgnoreSheetWhenNotSelected: true, outsideHouseholdReuseCloneSourceSites: true },
        localisation: { defaultLocale: 'en_IN' },
        DB_CONFIG: { DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
        // Page size 1 → sites span multiple pages too.
        excelIngestion: { sheetFetchPageSize: 1, persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
        facility: { persistBatchSize: 100, creationBatchSize: 100, kafkaCreateBatchSize: 30, searchBatchSize: 50 },
        sheetData: { persistBatchSize: 100 },
    },
}));

import { produceModifiedMessages } from '../kafka/Producer';
import { getRelatedDataWithCampaign, getMappingDataRelatedToCampaign, markSiteLinkWorkerUpdatesFailed, markSiteLinkWorkerUpdatesFailedBySite, markSiteLinkWorkerUpdatesDone, resetFailedSiteLinkWorkerUpdates, markSiteLinksToCreateAgain, pollUntilCountFn } from '../utils/genericUtils';
import config from '../config';
import { httpRequest } from '../utils/request';
import { logger } from '../utils/logger';
import {
    convertOutsideHouseholdRowsToFacilityRows,
    isOutsideHouseholdDeliveryDropped,
    isOutsideHouseholdNotSelected,
    buildStoredOutsideHouseholdNormalizedIndex,
    outsideHouseholdMatchKey,
    processCampaignFacilitiesFromExcelData,
    reusableOtherCampaignUsers,
} from '../utils/processingResultHandler';
import { DataTransformer } from '../utils/transFormUtil';
import { transformConfigs } from '../config/transformConfigs';
import { TenantId } from '../config/models/brandedTypes';

const produceMock = jest.mocked(produceModifiedMessages);
const getRelatedMock = jest.mocked(getRelatedDataWithCampaign);
const getMappingMock = jest.mocked(getMappingDataRelatedToCampaign);
const httpMock = jest.mocked(httpRequest);
const warnMock = jest.mocked(logger.warn);

const OUTSIDE_HOUSEHOLD_SHEET = 'HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_LIST';
const campaignDetails = { id: 'cmp-id', campaignNumber: 'CMP-1', campaignName: 'Test' };
const localizationMap = {};
const tn = 'tn' as TenantId;

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
// PM-added row with no clientReferenceId use newOutsideHouseholdRow instead.
const outsideHouseholdRow = (name: string, boundary: string, usage = 'Active', extra: Record<string, unknown> = {}) => ({
    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_TYPE: 'OUTSIDE_HOUSEHOLD_PLACES',
    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: 'CAT_A',
    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_NAME: name,
    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_STATUS: 'Permanent',
    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_USAGE: usage,
    HCM_ADMIN_CONSOLE_BOUNDARY_CODE: boundary,
    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: name,
    ...extra,
});
const newOutsideHouseholdRow = (name: string, boundary: string, usage = 'Active', extra: Record<string, unknown> = {}) =>
    outsideHouseholdRow(name, boundary, usage, { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: '', ...extra });

const mapKeys = (rows: any[] = []) => rows.map((m: any) => `${m.uniqueIdentifierForData}#${m.boundaryCode}`).sort();
const facilityMappingSearches = () => getMappingMock.mock.calls.filter((c: any[]) => c[0] === 'facility');
const setFacilityLinks = (links: any[]) =>
    getMappingMock.mockImplementation(async (type: any) => (type === 'facility' ? links : []) as any);

describe('convertOutsideHouseholdRowsToFacilityRows', () => {
    afterEach(() => jest.clearAllMocks());

    describe('row conversion', () => {
        it('maps site keys onto facility keys and keeps category, boundary and clientReferenceId', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2') }], new Map());
            expect(converted.rowjson).toMatchObject({
                HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P1',
                HCM_ADMIN_CONSOLE_FACILITY_TYPE: 'OUTSIDE_HOUSEHOLD_PLACES',
                HCM_ADMIN_CONSOLE_FACILITY_STATUS: 'Permanent',
                HCM_ADMIN_CONSOLE_FACILITY_USAGE: 'Active',
                HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B2',
                HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: 'CAT_A',
                HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'P1',
            });
        });

        it('passes TRANSIT_POST through as the facility type', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows(
                [{ rowjson: outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_TYPE: 'TRANSIT_POST' }) }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_TYPE).toBe('TRANSIT_POST');
        });

        it('sets no facility code on a site row (site code is never pre-filled any more)', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2') }], new Map());
            expect(converted.rowjson).not.toHaveProperty('HCM_ADMIN_CONSOLE_FACILITY_CODE');
        });

        it('sets no capacity on a site row', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2') }], new Map());
            expect(converted.rowjson).not.toHaveProperty('HCM_ADMIN_CONSOLE_FACILITY_CAPACITY');
        });

        it('reads the rowJson spelling too', () => {
            const converted = convertOutsideHouseholdRowsToFacilityRows([{ rowJson: outsideHouseholdRow('P1', 'B2') }], new Map());
            expect(converted).toHaveLength(1);
        });

        it('skips a row without a site name with a warning', () => {
            const converted = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('', 'B2') }], new Map());
            expect(converted).toHaveLength(0);
            expect(warnMock).toHaveBeenCalledWith('No site name found in row');
        });
    });

    describe('usage', () => {
        it('keeps Active as Active', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', 'Active') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Active');
        });

        it('keeps Inactive as Inactive', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', 'Inactive') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
        });

        it('treats the old Include / Remove values as Inactive (only Active links a site)', () => {
            const [include] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', 'Include') }], new Map());
            const [remove] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P2', 'B2', 'Remove') }], new Map());
            expect(include.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
            expect(remove.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
        });

        it('keeps the optional frontline worker columns on the converted row', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', 'Active', {
                HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'Asha', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876543210 }) }], new Map());
            expect(converted.rowjson).toMatchObject({
                HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'Asha', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876543210 });
        });

        it('maps any other usage value to Inactive too — the schema enum is what rejects it', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', 'SOMETHING_ELSE') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
        });
    });

    describe('clientReferenceId resolution', () => {
        it('keeps the pre-filled clientReferenceId', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'CRID-1' }) }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toBe('CRID-1');
        });

        it('reuses the stored clientReferenceId of a matching normalised (type, category, boundary, name) when the row has none', () => {
            const storedIndex = new Map([[outsideHouseholdMatchKey('OUTSIDE_HOUSEHOLD_PLACES', 'CAT_A', 'B2', 'P1'), 'CRID-OLD']]);
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: newOutsideHouseholdRow('P1', 'B2') }], storedIndex);
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
        });

        it('the match is case-insensitive and ignores extra spacing', () => {
            const storedIndex = new Map([[outsideHouseholdMatchKey('outside_household_places', ' cat_a ', 'b2', 'p 1'), 'CRID-OLD']]);
            const [converted] = convertOutsideHouseholdRowsToFacilityRows(
                [{ rowjson: newOutsideHouseholdRow('P  1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_TYPE: 'OUTSIDE_HOUSEHOLD_PLACES' }) }], storedIndex);
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
        });

        it('generates a new id when the row has none and no stored row matches', () => {
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: newOutsideHouseholdRow('P1', 'B2') }], new Map());
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toEqual(expect.any(String));
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).not.toBe('');
        });

        it('two rows with the same (type, category, boundary, name) share one site, the same way facility rows with the same name share one facility', () => {
            const storedIndex = new Map([[outsideHouseholdMatchKey('OUTSIDE_HOUSEHOLD_PLACES', 'CAT_A', 'B2', 'P1'), 'CRID-OLD']]);
            const [a, b] = convertOutsideHouseholdRowsToFacilityRows(
                [{ rowjson: newOutsideHouseholdRow('P1', 'B2') }, { rowjson: newOutsideHouseholdRow('P1', 'B2') }], storedIndex);
            expect(a.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
            expect(b.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toBe('CRID-OLD');
        });

        it('a stored site with another category is a different site: the row gets a new id', () => {
            const storedIndex = new Map([[outsideHouseholdMatchKey('OUTSIDE_HOUSEHOLD_PLACES', 'CAT_B', 'B2', 'P1'), 'CRID-OLD']]);
            const [converted] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: newOutsideHouseholdRow('P1', 'B2') }], storedIndex);
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).not.toBe('CRID-OLD');
            expect(converted.rowjson.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toEqual(expect.any(String));
        });
    });

    describe('buildStoredOutsideHouseholdNormalizedIndex', () => {
        it('keys stored site rows by type, category, boundary and name, and skips deleted rows and facility rows', () => {
            const stored = new Map<any, any>([
                ['CRID-1', { data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'CRID-1', HCM_ADMIN_CONSOLE_FACILITY_TYPE: 'OUTSIDE_HOUSEHOLD_PLACES',
                    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: 'CAT_A', HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B2', HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P1' } }],
                ['CRID-2', { isDeleted: true, data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'CRID-2', HCM_ADMIN_CONSOLE_FACILITY_TYPE: 'OUTSIDE_HOUSEHOLD_PLACES',
                    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: 'CAT_B', HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B2', HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P1' } }],
                ['F1', { data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F1', HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B2' } }],
            ]);
            const index = buildStoredOutsideHouseholdNormalizedIndex(stored);
            expect(Array.from(index.entries())).toEqual([[outsideHouseholdMatchKey('OUTSIDE_HOUSEHOLD_PLACES', 'CAT_A', 'B2', 'P1'), 'CRID-1']]);
        });
    });
});

describe('a campaign that does not use Outside Household, e.g. a clone that borrowed its source file', () => {
    const using = (deliveryMethods: unknown, extra: Record<string, unknown> = {}) =>
        ({ ...campaignDetails, parentId: null, additionalDetails: { cycleData: { deliveryMethods } }, ...extra });

    beforeEach(() => {
        jest.clearAllMocks();
        getRelatedMock.mockResolvedValue([] as any);
        setFacilityLinks([] as any);
    });

    it('does not read the Outside Household sheet, so its rows are neither saved nor linked', async () => {
        const requested = installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
        const topics = collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, using(['HOUSEHOLDSTRATEGY']));
        expect(requested).not.toContain(OUTSIDE_HOUSEHOLD_SHEET.substring(0, 31));
        expect((topics['save-sheet'] || []).filter((d: any) => d.type === 'facility').map((d: any) => d.uniqueIdentifier)).toEqual(['F1']);
        expect(mapKeys(topics['save-map'])).toEqual(['F1#B1']);
    });

    it('reads the sheet as before when the campaign uses Outside Household', async () => {
        installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
        const topics = collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, using(['HOUSEHOLDSTRATEGY', { code: 'OUTSIDEHOUSEHOLD' }]));
        expect(mapKeys(topics['save-map'])).toEqual(['F1#B1', 'P1#B2']);
    });

    it('reads the sheet as before when the campaign carries no deliveryMethods list', async () => {
        const requested = installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
        collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(requested).toContain(OUTSIDE_HOUSEHOLD_SHEET.substring(0, 31));
    });

    it('reads the sheet as before when the switch is off', async () => {
        (config as any).mapping.outsideHouseholdIgnoreSheetWhenNotSelected = false;
        try {
            const requested = installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, using(['HOUSEHOLDSTRATEGY']));
            expect(requested).toContain(OUTSIDE_HOUSEHOLD_SHEET.substring(0, 31));
        } finally {
            (config as any).mapping.outsideHouseholdIgnoreSheetWhenNotSelected = true;
        }
    });

    describe('isOutsideHouseholdNotSelected', () => {
        it('is true for a readable list without OUTSIDEHOUSEHOLD and no rule naming it', () => {
            expect(isOutsideHouseholdNotSelected(using(['HOUSEHOLDSTRATEGY']))).toBe(true);
            expect(isOutsideHouseholdNotSelected(using([{ code: 'HOUSEHOLDSTRATEGY' }]))).toBe(true);
        });

        it('is false when OUTSIDEHOUSEHOLD is in the list or in a delivery rule', () => {
            expect(isOutsideHouseholdNotSelected(using(['outside-household']))).toBe(false);
            expect(isOutsideHouseholdNotSelected(using(['HOUSEHOLDSTRATEGY'], {
                deliveryRules: [{ cycles: [{ deliveries: [{ deliveryMethod: 'OUTSIDEHOUSEHOLD' }] }] }],
            }))).toBe(false);
        });

        it('is false without a list, with an empty list, or with an unreadable entry', () => {
            expect(isOutsideHouseholdNotSelected(campaignDetails)).toBe(false);
            expect(isOutsideHouseholdNotSelected(using([]))).toBe(false);
            expect(isOutsideHouseholdNotSelected(using(['HOUSEHOLDSTRATEGY', 42]))).toBe(false);
        });
    });
});

describe('a clone reuses the sites of its source for rows without a clientReferenceId', () => {
    const SRC = 'CMP-SRC';
    const sourceSite = (crid: string, name: string, boundary: string, category = 'CAT_A') => ({
        uniqueIdentifier: crid,
        data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: crid, HCM_ADMIN_CONSOLE_FACILITY_TYPE: 'OUTSIDE_HOUSEHOLD_PLACES',
            HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: category, HCM_ADMIN_CONSOLE_BOUNDARY_CODE: boundary, HCM_ADMIN_CONSOLE_FACILITY_NAME: name },
    });
    const clone = (extra: Record<string, unknown> = {}) =>
        ({ ...campaignDetails, parentId: null, additionalDetails: { cloneFrom: SRC, cycleData: { deliveryMethods: ['OUTSIDEHOUSEHOLD'] } }, ...extra });
    const sourceReads = () => getRelatedMock.mock.calls.filter((c: any[]) => c[1] === SRC);
    const savedSiteIds = (topics: any) => (topics['save-sheet'] || []).filter((d: any) => d.type === 'facility' && d.data?.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).map((d: any) => d.uniqueIdentifier);

    beforeEach(() => {
        jest.clearAllMocks();
        setFacilityLinks([] as any);
        getRelatedMock.mockImplementation(async (_type: any, number: any) =>
            (number === SRC ? [sourceSite('SRC-CRID', 'P1', 'B2'), sourceSite('SRC-OTHER', 'P1', 'B2', 'CAT_B')] : []) as any);
    });

    it('a row the source typed in (no clientReferenceId) reuses the source site, so that site is linked, not a copy', async () => {
        installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [newOutsideHouseholdRow('P1', 'B2')] });
        const topics = collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, clone());
        expect(savedSiteIds(topics)).toEqual(['SRC-CRID']);
        expect(mapKeys(topics['save-map'])).toEqual(['F1#B1', 'SRC-CRID#B2']);
    });

    it('matches on category too: another category of the same name is a different site', async () => {
        installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [newOutsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: 'CAT_B' })] });
        const topics = collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, clone());
        expect(savedSiteIds(topics)).toEqual(['SRC-OTHER']);
    });

    it('the clone\'s own saved site wins over the source\'s', async () => {
        getRelatedMock.mockImplementation(async (_type: any, number: any) =>
            (number === SRC ? [sourceSite('SRC-CRID', 'P1', 'B2')] : [sourceSite('OWN-CRID', 'P1', 'B2')]) as any);
        installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [newOutsideHouseholdRow('P1', 'B2')] });
        const topics = collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, clone());
        expect(savedSiteIds(topics)).toEqual([]);                    // OWN-CRID is already stored: an update, not a new row
        expect(mapKeys(topics['save-map'])).toEqual(['F1#B1', 'OWN-CRID#B2']);
    });

    it('does not read a source for a campaign that is not a clone, for an edit, or when the switch is off', async () => {
        for (const details of [campaignDetails, clone({ parentId: 'parent-id' })]) {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [newOutsideHouseholdRow('P1', 'B2')] });
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, details);
        }
        (config as any).mapping.outsideHouseholdReuseCloneSourceSites = false;
        try {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [newOutsideHouseholdRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, clone());
            expect(savedSiteIds(topics)).not.toContain('SRC-CRID');
        } finally {
            (config as any).mapping.outsideHouseholdReuseCloneSourceSites = true;
        }
        expect(sourceReads()).toHaveLength(0);
    });

    it('creates a new site as before when the source cannot be read', async () => {
        getRelatedMock.mockImplementation(async (_type: any, number: any) => {
            if (number === SRC) throw new Error('db down');
            return [] as any;
        });
        installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [newOutsideHouseholdRow('P1', 'B2')] });
        const topics = collectAllTopics();
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, clone());
        const ids = savedSiteIds(topics);
        expect(ids).toHaveLength(1);
        expect(ids[0]).not.toBe('SRC-CRID');
    });
});

describe('isOutsideHouseholdDeliveryDropped', () => {
    const site = new Map<any, any>([['P1', { data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'P1' } }]]);
    const facilityOnly = new Map<any, any>([['F1', { data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F1' } }]]);
    const edit = (deliveryMethods?: unknown, extra: Record<string, unknown> = {}) =>
        ({ parentId: 'parent-id', additionalDetails: { cycleData: { deliveryMethods } }, ...extra });
    const rulesWith = (deliveryMethod: unknown) => ({ deliveryRules: [{ cycles: [{ deliveries: [{ deliveryMethod }] }] }] });

    afterEach(() => { (config as any).mapping.outsideHouseholdUnlinkOnDeliveryRemoval = true; });

    it('is true for an edit whose list no longer holds OUTSIDEHOUSEHOLD, with stored sites', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY']), site)).toBe(true);
    });

    it('reads {code} objects too', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit([{ code: 'HOUSEHOLDSTRATEGY' }]), site)).toBe(true);
    });

    it('is false while OUTSIDEHOUSEHOLD is still in the list, in any case or with separators', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), site)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped(edit(['outside-household']), site)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped(edit([{ code: 'Outside_Household' }]), site)).toBe(false);
    });

    it('is false for a campaign that is not an edit (first create, clone)', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY'], { parentId: null }), site)).toBe(false);
    });

    it('is false when the list is missing or empty', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(undefined), site)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped(edit([]), site)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped({ parentId: 'parent-id' }, site)).toBe(false);
    });

    it('is false when any entry of the list cannot be read', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY', { name: 'x' }]), site)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY', null]), site)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY', 7]), site)).toBe(false);
    });

    it('is false while a delivery in the rules still names OUTSIDEHOUSEHOLD', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY'], rulesWith('OUTSIDEHOUSEHOLD')), site)).toBe(false);
    });

    it('is true when the rules name other delivery methods only', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY'], rulesWith('HOUSEHOLDSTRATEGY')), site)).toBe(true);
    });

    it('is false when the campaign never had sites', () => {
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY']), facilityOnly)).toBe(false);
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY']), new Map())).toBe(false);
    });

    it('is false when the switch is off', () => {
        (config as any).mapping.outsideHouseholdUnlinkOnDeliveryRemoval = false;
        expect(isOutsideHouseholdDeliveryDropped(edit(['HOUSEHOLDSTRATEGY']), site)).toBe(false);
    });
});

describe('processCampaignFacilitiesFromExcelData with the Outside Household sheet', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        getRelatedMock.mockResolvedValue([] as any);
        setFacilityLinks([] as any);
    });

    describe('sheet reading', () => {
        it('reads the Outside Household sheet by its localisation key', async () => {
            const requested = installSheetRouter({ FACILIT: [] });
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(requested).toContain(OUTSIDE_HOUSEHOLD_SHEET.substring(0, 31)); // sheet names are cut to Excel's 31 characters
        });
    });

    describe('campaign data rows', () => {
        it('persists site rows as facility rows keyed by clientReferenceId', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2'), outsideHouseholdRow('P2', 'B3', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);

            const saved = (topics['save-sheet'] || []).filter((d: any) => d.type === 'facility');
            expect(saved.map((d: any) => d.uniqueIdentifier).sort()).toEqual(['F1', 'P1', 'P2']);
            const p2 = saved.find((d: any) => d.uniqueIdentifier === 'P2');
            expect(p2.data.HCM_ADMIN_CONSOLE_FACILITY_USAGE).toBe('Inactive');
            expect(p2.data.HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID).toBe('P2');
            expect(p2.status).toBe('pending');
        });
    });

    describe('one combined mapping set', () => {
        it('runs the facility link diff once over facilities and sites', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(facilityMappingSearches()).toHaveLength(1);
        });

        it('creates links for Active facilities and Include sites in one batch', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2'), outsideHouseholdRow('P2', 'B3', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['save-map'])).toEqual(['F1#B1', 'P1#B2']);
        });

        it('keeps both kinds of existing links that are still in the sheets', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            setFacilityLinks([
                { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
                { uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped' },
            ] as any);
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(topics['update-map'] || []).toHaveLength(0);
            expect(topics['save-map'] || []).toHaveLength(0);
        });

        it('demaps an excluded site and a facility gone from the sheet (both sheets contributed: full diff)', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Inactive')] });
            setFacilityLinks([
                { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
                { uniqueIdentifierForData: 'F-OLD', boundaryCode: 'B9', status: 'mapped' },
                { uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped' },
            ] as any);
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['F-OLD#B9', 'P1#B2']);
        });
    });

    describe('facility sheet with no links (§task 3: per-sheet demap guard)', () => {
        const storedRows = [
            { uniqueIdentifier: 'F1', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F1', HCM_ADMIN_CONSOLE_FACILITY_USAGE: 'Active', HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B1' } },
            { uniqueIdentifier: 'P-OLD', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P-OLD', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'P-OLD' } },
        ];
        const storedLinks = [
            { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
            { uniqueIdentifierForData: 'P-OLD', boundaryCode: 'B5', status: 'mapped' },
        ];

        beforeEach(() => {
            getRelatedMock.mockImplementation(async (type: any) => (type === 'facility' ? storedRows : []) as any);
            setFacilityLinks(storedLinks);
        });

        it('processes sites when the facility sheet has no rows', async () => {
            installSheetRouter({ FACILIT: [], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);

            const saved = (topics['save-sheet'] || []).filter((d: any) => d.type === 'facility');
            expect(saved.map((d: any) => d.uniqueIdentifier)).toEqual(['P1']);
            expect(mapKeys(topics['save-map'])).toEqual(['P1#B2']);
        });

        it('demaps only site links when the facility sheet has no rows', async () => {
            installSheetRouter({ FACILIT: [], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('demaps only site links when no facility row is Active', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1', 'Inactive')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('demaps an excluded site when the facility sheet has no rows, even with no stored campaign_data row for it', async () => {
            setFacilityLinks([...storedLinks, { uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped' }] as any);
            installSheetRouter({ FACILIT: [], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5', 'P1#B2']);
        });

        it('keeps site links unchanged when the Outside Household sheet is absent (facility sheet alone contributed)', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            // F1 stays (still in the sheet); P-OLD is frozen (Outside Household sheet absent this upload), not demapped.
            expect(topics['update-map'] || []).toHaveLength(0);
        });

        it('keeps site links unchanged when the Outside Household sheet is present but empty', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(topics['update-map'] || []).toHaveLength(0);
        });
    });

    describe('edit that removed the Outside Household delivery method', () => {
        const withMethods = (deliveryMethods: unknown[]) =>
            ({ ...campaignDetails, parentId: 'parent-id', additionalDetails: { cycleData: { deliveryMethods } } });
        const withoutOutsideHousehold = withMethods(['HOUSEHOLDSTRATEGY']);
        const storedRows = [
            { uniqueIdentifier: 'F1', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'F1', HCM_ADMIN_CONSOLE_FACILITY_USAGE: 'Active', HCM_ADMIN_CONSOLE_BOUNDARY_CODE: 'B1' } },
            { uniqueIdentifier: 'P-OLD', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'P-OLD', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'P-OLD' } },
        ];
        const storedLinks = [
            { uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' },
            { uniqueIdentifierForData: 'P-OLD', boundaryCode: 'B5', status: 'mapped' },
        ];

        beforeEach(() => {
            getRelatedMock.mockImplementation(async (type: any) => (type === 'facility' ? storedRows : []) as any);
            setFacilityLinks(storedLinks);
        });

        it('demaps every stored site link and keeps the facility links still in the sheet', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withoutOutsideHousehold);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
            expect(topics['save-map'] || []).toHaveLength(0);
        });

        it('does not read an Outside Household sheet left in an old file', async () => {
            const requested = installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')], OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withoutOutsideHousehold);
            expect(requested).not.toContain(OUTSIDE_HOUSEHOLD_SHEET.substring(0, 31));
            expect(topics['save-map'] || []).toHaveLength(0);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('demaps the stored sites but keeps facility links when no facility row is Active', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1', 'Inactive')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withoutOutsideHousehold);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('demaps the stored sites when the facility sheet has no rows', async () => {
            installSheetRouter({ FACILIT: [] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withoutOutsideHousehold);
            expect(mapKeys(topics['update-map'])).toEqual(['P-OLD#B5']);
        });

        it('keeps site links when OUTSIDEHOUSEHOLD is still a delivery method, also as a {code} object', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withMethods([{ code: 'OUTSIDEHOUSEHOLD' }]));
            expect(topics['update-map'] || []).toHaveLength(0);
        });

        it('keeps site links for a campaign that is not an edit', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, { ...withoutOutsideHousehold, parentId: null });
            expect(topics['update-map'] || []).toHaveLength(0);
        });

        it('keeps site links and reads the sheet as before when the switches are off', async () => {
            (config as any).mapping.outsideHouseholdUnlinkOnDeliveryRemoval = false;
            (config as any).mapping.outsideHouseholdIgnoreSheetWhenNotSelected = false;
            try {
                const requested = installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
                const topics = collectAllTopics();
                await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withoutOutsideHousehold);
                expect(topics['update-map'] || []).toHaveLength(0);
                expect(requested).toContain(OUTSIDE_HOUSEHOLD_SHEET.substring(0, 31));
            } finally {
                (config as any).mapping.outsideHouseholdUnlinkOnDeliveryRemoval = true;
                (config as any).mapping.outsideHouseholdIgnoreSheetWhenNotSelected = true;
            }
        });

        it('changes nothing when the stored site was never linked, and leaves facility links alone', async () => {
            setFacilityLinks([{ uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' }] as any);
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, withoutOutsideHousehold);
            expect(topics['update-map'] || []).toHaveLength(0);
            expect(topics['save-map'] || []).toHaveLength(0);
        });
    });

    describe('campaigns without a Outside Household sheet', () => {
        it('does nothing when both sheets are empty', async () => {
            installSheetRouter({ FACILIT: [] });
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(facilityMappingSearches()).toHaveLength(0);
            expect((topics['save-sheet'] || []).filter((d: any) => d.type === 'facility')).toHaveLength(0);
        });

        it('keeps the facility-only link diff unchanged (today\'s behaviour, Outside Household sheet never uploaded)', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1')] });
            setFacilityLinks([{ uniqueIdentifierForData: 'F-OLD', boundaryCode: 'B9', status: 'mapped' }] as any);
            const topics = collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(mapKeys(topics['save-map'])).toEqual(['F1#B1']);
            expect(mapKeys(topics['update-map'])).toEqual(['F-OLD#B9']);
        });

        it('keeps facility links when no facility row is Active and there are no sites', async () => {
            installSheetRouter({ FACILIT: [facilityRow('F1', 'B1', 'Inactive')] });
            setFacilityLinks([{ uniqueIdentifierForData: 'F1', boundaryCode: 'B1', status: 'mapped' }] as any);
            collectAllTopics();
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(facilityMappingSearches()).toHaveLength(0);
        });
    });
});

// Real DataTransformer + the real FacilityUnified config (as used by facilityBatchHandler) — proves
// the transform itself sets the site type as usage, plus the category and clientReferenceId, and only on a row
// that went through convertOutsideHouseholdRowsToFacilityRows; a plain facility row gets neither.
describe('transformBulkFacility (real transform, FacilityUnified config)', () => {
    const requestInfo = { userInfo: { uuid: 'u1' } } as any;

    function buildTransformer() {
        const cfg = JSON.parse(JSON.stringify(transformConfigs.FacilityUnified));
        cfg.metadata.tenantId = 'mz';
        return new DataTransformer(cfg);
    }

    it('sets usage = site type, category and clientReferenceId only on the site row (no additionalFields marker)', async () => {
        const [outsideHouseholdRowJson] = convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2') }], new Map());
        const [facilityOut, outsideHouseholdOut] = await buildTransformer().transform(
            [facilityRow('F1', 'B1'), outsideHouseholdRowJson.rowjson], requestInfo
        );

        expect(facilityOut.Facility).not.toHaveProperty('clientReferenceId');
        expect(facilityOut.Facility).not.toHaveProperty('additionalFields');

        expect(outsideHouseholdOut.Facility.clientReferenceId).toBe('P1');
        expect(outsideHouseholdOut.Facility.usage).toBe('OUTSIDE_HOUSEHOLD_PLACES');
        expect(outsideHouseholdOut.Facility.additionalFields).toEqual({
            fields: [{ key: 'outsideHouseholdCategory', value: 'CAT_A' }],
        });
    });

    it('sets no additionalFields when the category is blank', async () => {
        const [outsideHouseholdRowJson] = convertOutsideHouseholdRowsToFacilityRows(
            [{ rowjson: outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY: '  ' }) }], new Map());
        const [outsideHouseholdOut] = await buildTransformer().transform([outsideHouseholdRowJson.rowjson], requestInfo);
        expect(outsideHouseholdOut.Facility).not.toHaveProperty('additionalFields');
    });
});

describe('buildOutsideHouseholdLinkAdditionalFields (frontline worker on the site link)', () => {
    // Imported lazily so the module-level mocks above are in place first
    const { buildOutsideHouseholdLinkAdditionalFields } = require('../utils/mappingBatchHandler');

    it('puts name and phone on the link of a site row, keyed by its uniqueIdentifier', () => {
        const result = buildOutsideHouseholdLinkAdditionalFields([{
            uniqueIdentifier: 'CRID-1',
            data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'CRID-1',
                    HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: ' Asha ', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876543210 },
        }]);
        expect(result).toEqual({ 'CRID-1': { schema: 'OutsideHouseholdAssignment', version: 1, fields: [
            { key: 'frontlineWorkerName', value: 'Asha' },
            { key: 'frontlineWorkerPhone', value: '9876543210' },
        ] } });
    });

    it('keeps only the worker fields that are filled', () => {
        const result = buildOutsideHouseholdLinkAdditionalFields([{
            uniqueIdentifier: 'CRID-2',
            data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'CRID-2', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: '9123456780' },
        }]);
        expect(result['CRID-2'].fields).toEqual([{ key: 'frontlineWorkerPhone', value: '9123456780' }]);
    });

    it('marks the link of a site without a worker too (empty fields), and leaves facility rows alone', () => {
        const result = buildOutsideHouseholdLinkAdditionalFields([
            { uniqueIdentifier: 'CRID-3', data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'CRID-3', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: '  ' } },
            { uniqueIdentifier: 'Warehouse A', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: 'Warehouse A', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'X' } },
        ]);
        expect(result).toEqual({ 'CRID-3': { schema: 'OutsideHouseholdAssignment', version: 1, fields: [] } });
    });
});

describe('edit: frontline worker changed on an Active site with an existing link', () => {
    const storedSite = (usage: string, worker: Record<string, unknown>) => ({
        uniqueIdentifier: 'P1', uniqueIdAfterProcess: 'F-1',
        data: { ...convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow('P1', 'B2', usage, worker) }], new Map())[0].rowjson },
    });
    const existingLink = (fields: any[]) => ({
        id: 'PF-1', tenantId: 'tn', projectId: 'PRJ-1', facilityId: 'F-1', rowVersion: 1,
        additionalFields: { schema: 'OutsideHouseholdAssignment', version: 1, fields },
    });
    const oldWorker = { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'Old Worker', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876500001 };
    const oldFields = [{ key: 'frontlineWorkerName', value: 'Old Worker' }, { key: 'frontlineWorkerPhone', value: '9876500001' }];

    function install(sheetRows: Record<string, any[]>, links: any[]): any[] {
        const updates: any[] = [];
        installSheetRouter(sheetRows);
        const sheetImpl = httpMock.getMockImplementation()!;
        httpMock.mockImplementation(async (url: string, body: any, ...rest: any[]) => {
            if (url.endsWith(config.paths.projectFacilityUpdate)) { updates.push(body.ProjectFacility); return {} as any; }
            if (url.endsWith(config.paths.projectFacilitySearch)) return { ProjectFacilities: links } as any;
            return sheetImpl(url, body, ...rest);
        });
        return updates;
    }

    beforeEach(() => {
        jest.clearAllMocks();
        setFacilityLinks([{ uniqueIdentifierForData: 'P1', boundaryCode: 'B2', status: 'mapped', mappingId: 'PF-1' }] as any);
        collectAllTopics();
    });

    it('writes the new worker onto the existing link', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const updates = install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', {
            HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876500011 })] },
            [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails, { userInfo: { uuid: 'u1' } } as any);
        expect(updates).toHaveLength(1);
        expect(updates[0]).toMatchObject({ id: 'PF-1', rowVersion: 1, additionalFields: { schema: 'OutsideHouseholdAssignment', version: 1,
            fields: [{ key: 'frontlineWorkerName', value: 'New Worker' }, { key: 'frontlineWorkerPhone', value: '9876500011' }] } });
    });

    it('clears the worker on the link when it is removed from the row', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const updates = install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active')] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(updates).toHaveLength(1);
        expect(updates[0].additionalFields).toEqual({ schema: 'OutsideHouseholdAssignment', version: 1, fields: [] });
    });

    it('sends no update when the worker did not change', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const updates = install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', oldWorker)] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(updates).toHaveLength(0);
    });

    it('sends no update for an Inactive site (it has no link to update)', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Inactive', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const updates = install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Inactive', {
            HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker' })] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(updates).toHaveLength(0);
    });

    it('leaves a link that already has the new worker alone (a retry repeats nothing)', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const newWorker = { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876500011 };
        const updates = install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', newWorker)] },
            [existingLink([{ key: 'frontlineWorkerName', value: 'New Worker' }, { key: 'frontlineWorkerPhone', value: '9876500011' }])]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(updates).toHaveLength(0);
    });

    it('asks httpRequest to retry the link search, and sends a link update that passes once, without httpRequest\'s retry', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', {
            HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker' })] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        const linkPaths = [config.paths.projectFacilitySearch, config.paths.projectFacilityUpdate];
        const linkCalls = httpMock.mock.calls.filter((c: any[]) => linkPaths.some((p: string) => String(c[0]).endsWith(p)));
        expect(linkCalls.map((c: any[]) => c[0])).toEqual(linkPaths.map((p: string) => `${config.host.projectHost}${p}`));
        expect(linkCalls[0][7]).toBe(true);
        expect([linkCalls[1][7], linkCalls[1][8]]).toEqual([false, true]);
    });

    it('updates the links of several changed sites, at most the configured number at a time', async () => {
        const names = ['P1', 'P2', 'P3'];
        const stored = names.map((name, i) => ({ uniqueIdentifier: name, uniqueIdAfterProcess: `F-${i + 1}`,
            data: { ...convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow(name, 'B2', 'Active', oldWorker) }], new Map())[0].rowjson } }));
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? stored : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        setFacilityLinks(names.map((name, i) => ({ uniqueIdentifierForData: name, boundaryCode: 'B2', status: 'mapped', mappingId: `PF-${i + 1}` })) as any);
        installSheetRouter({ OUTSIDE_HOUSE: names.map(name => outsideHouseholdRow(name, 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: `New ${name}` })) });
        const sheetImpl = httpMock.getMockImplementation()!;
        let inFlight = 0, maxInFlight = 0;
        const updated: string[] = [];
        httpMock.mockImplementation(async (url: string, body: any, ...rest: any[]) => {
            if (url.endsWith(config.paths.projectFacilityUpdate)) {
                inFlight++; maxInFlight = Math.max(maxInFlight, inFlight);
                await new Promise(resolve => setImmediate(resolve));
                inFlight--; updated.push(body.ProjectFacility.id);
                return {} as any;
            }
            if (url.endsWith(config.paths.projectFacilitySearch)) {
                return { ProjectFacilities: names.map((_, i) => ({ ...existingLink(oldFields), id: `PF-${i + 1}`, facilityId: `F-${i + 1}` })) } as any;
            }
            return sheetImpl(url, body, ...rest);
        });
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(updated.sort()).toEqual(['PF-1', 'PF-2', 'PF-3']);
        expect(maxInFlight).toBe(2);
    });

    function failingUpdate(searchFails = false) {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        installSheetRouter({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker' })] });
        const sheetImpl = httpMock.getMockImplementation()!;
        httpMock.mockImplementation(async (url: string, body: any, ...rest: any[]) => {
            if (url.endsWith(config.paths.projectFacilityUpdate)) throw new Error('link update failed');
            if (url.endsWith(config.paths.projectFacilitySearch)) {
                if (searchFails) throw new Error('link search failed');
                return { ProjectFacilities: [existingLink(oldFields)] } as any;
            }
            return sheetImpl(url, body, ...rest);
        });
    }

    it('leaves a failed link update to the mapping retry instead of failing the run', async () => {
        failingUpdate();
        jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set(['PF-1']));
        await expect(processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails)).resolves.toBeUndefined();
        expect(markSiteLinkWorkerUpdatesFailed).toHaveBeenCalledWith('CMP-1', tn,
            [{ mappingId: 'PF-1', error: 'Frontline worker update failed: link update failed', terminal: false }]);
        expect(jest.mocked(pollUntilCountFn).mock.calls.some(([, , opts]: any[]) => String(opts?.label).includes('stored frontline workers'))).toBe(true);
    });

    it('stops after the first group with a failure and records the links not attempted after it', async () => {
        const names = ['P1', 'P2', 'P3'];
        getRelatedMock.mockImplementation(async (type: any) => (type === 'facility'
            ? names.map((name, i) => ({ uniqueIdentifier: name, uniqueIdAfterProcess: `F-${i + 1}`,
                data: { ...convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow(name, 'B2', 'Active', oldWorker) }], new Map())[0].rowjson } }))
            : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        installSheetRouter({ OUTSIDE_HOUSE: names.map(name => outsideHouseholdRow(name, 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: `New ${name}` })) });
        const sheetImpl = httpMock.getMockImplementation()!;
        const attempted: string[] = [];
        httpMock.mockImplementation(async (url: string, body: any, ...rest: any[]) => {
            if (url.endsWith(config.paths.projectFacilityUpdate)) {
                attempted.push(body.ProjectFacility.id);
                if (body.ProjectFacility.id === 'PF-1') throw new Error('down');
                return {} as any;
            }
            if (url.endsWith(config.paths.projectFacilitySearch)) {
                return { ProjectFacilities: names.map((_, i) => ({ ...existingLink(oldFields), id: `PF-${i + 1}`, facilityId: `F-${i + 1}` })) } as any;
            }
            return sheetImpl(url, body, ...rest);
        });
        jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set(['PF-1', 'PF-3']));
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(Array.from(new Set(attempted)).sort()).toEqual(['PF-1', 'PF-2']);
        expect(markSiteLinkWorkerUpdatesFailed).toHaveBeenCalledWith('CMP-1', tn, [
            { mappingId: 'PF-1', error: 'Frontline worker update failed: down', terminal: false },
            { mappingId: 'PF-3', error: 'Frontline worker update not attempted after an earlier failure' }]);
    });

    it('marks the links written now as done when this upload queued earlier failures again', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        jest.mocked(resetFailedSiteLinkWorkerUpdates).mockResolvedValueOnce(1);
        install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker' })] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(markSiteLinkWorkerUpdatesDone).toHaveBeenCalledWith('CMP-1', tn, ['PF-1']);
    });

    it('does not touch the mapping rows on the plain success path', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker' })] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(markSiteLinkWorkerUpdatesDone).not.toHaveBeenCalled();
        expect(markSiteLinkWorkerUpdatesFailed).not.toHaveBeenCalled();
        expect(jest.mocked(pollUntilCountFn).mock.calls.some(([, , opts]: any[]) => String(opts?.label).includes('stored frontline workers'))).toBe(false);
    });

    it('still fails the run when a failed link has no mapping row to mark', async () => {
        failingUpdate();
        jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set());
        await expect(processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails)).rejects.toThrow('PF-1');
    });

    it('leaves every changed site to the mapping retry when the links cannot be read', async () => {
        failingUpdate(true);
        jest.mocked(markSiteLinkWorkerUpdatesFailedBySite).mockResolvedValueOnce(1);
        await expect(processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails)).resolves.toBeUndefined();
        expect(markSiteLinkWorkerUpdatesFailedBySite).toHaveBeenCalledWith('CMP-1', tn, ['P1'], 'Could not read the site links: link search failed');
        expect(markSiteLinkWorkerUpdatesFailed).not.toHaveBeenCalled();
    });

    it('still fails the run when the links cannot be read and no mapping row can be marked', async () => {
        failingUpdate(true);
        jest.mocked(markSiteLinkWorkerUpdatesFailedBySite).mockResolvedValueOnce(0);
        await expect(processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails)).rejects.toThrow('link search failed');
    });

    it('asks for the retry of earlier failed worker updates on every upload', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', oldWorker)] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(resetFailedSiteLinkWorkerUpdates).toHaveBeenCalledWith('CMP-1', tn, [{ uniqueIdentifierForData: 'P1', boundaryCode: 'B2' }]);
    });

    describe('failures another try cannot change, and links that are gone', () => {
        const withBoundary = (stored: any[]) => getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? stored : type === 'boundary' ? [{ uniqueIdentifier: 'B2', uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const newWorkerRow = () => ({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker' })] });
        const newFields = [{ key: 'frontlineWorkerName', value: 'New Worker' }];
        function route(search: (body: any) => any, update: (body: any) => any): any[] {
            const sent: any[] = [];
            installSheetRouter(newWorkerRow());
            const sheetImpl = httpMock.getMockImplementation()!;
            httpMock.mockImplementation(async (url: string, body: any, ...rest: any[]) => {
                if (url.endsWith(config.paths.projectFacilityUpdate)) { sent.push(body.ProjectFacility); return update(body.ProjectFacility); }
                if (url.endsWith(config.paths.projectFacilitySearch)) return search(body.ProjectFacility);
                return sheetImpl(url, body, ...rest);
            });
            return sent;
        }

        it('sends no update to two links with the same project and site, and records the pair as terminal', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            const sent = route(() => ({ ProjectFacilities: [existingLink(oldFields), { ...existingLink(oldFields), id: 'PF-2' }] }), () => ({}));
            jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set(['PF-1']));
            await expect(processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails)).resolves.toBeUndefined();
            expect(sent).toHaveLength(0);
            const error = 'Frontline worker update failed: DUPLICATE_ENTITY: links PF-1, PF-2 have the same project and site; remove the extra link, then upload the file again';
            expect(markSiteLinkWorkerUpdatesFailed).toHaveBeenCalledWith('CMP-1', tn, [
                { mappingId: 'PF-1', error, terminal: true }, { mappingId: 'PF-2', error, terminal: true }]);
        });

        it('still fails the run when neither link of a duplicate pair has a mapping row', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            route(() => ({ ProjectFacilities: [existingLink(oldFields), { ...existingLink(oldFields), id: 'PF-2' }] }), () => ({}));
            jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set());
            await expect(processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails)).rejects.toThrow('PF-1, PF-2');
        });

        it('records a refusal another try cannot change as terminal after one send, and keeps updating the other links', async () => {
            const stored = ['P1', 'P2'].map((name, i) => ({ uniqueIdentifier: name, uniqueIdAfterProcess: `F-${i + 1}`,
                data: { ...convertOutsideHouseholdRowsToFacilityRows([{ rowjson: outsideHouseholdRow(name, 'B2', 'Active', oldWorker) }], new Map())[0].rowjson } }));
            withBoundary(stored);
            installSheetRouter({ OUTSIDE_HOUSE: ['P1', 'P2'].map(name => outsideHouseholdRow(name, 'B2', 'Active', { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: `New ${name}` })) });
            const sheetImpl = httpMock.getMockImplementation()!;
            const sent: string[] = [];
            httpMock.mockImplementation(async (url: string, body: any, ...rest: any[]) => {
                if (url.endsWith(config.paths.projectFacilityUpdate)) {
                    sent.push(body.ProjectFacility.id);
                    return (body.ProjectFacility.id === 'PF-1' ? { Errors: [{ code: 'NON_EXISTENT_RELATED_ENTITY', message: '[long dump]' }] } : {}) as any;
                }
                if (url.endsWith(config.paths.projectFacilitySearch)) {
                    return { ProjectFacilities: ['P1', 'P2'].map((_, i) => ({ ...existingLink(oldFields), id: `PF-${i + 1}`, facilityId: `F-${i + 1}` })) } as any;
                }
                return sheetImpl(url, body, ...rest);
            });
            jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set(['PF-1']));
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(sent.sort()).toEqual(['PF-1', 'PF-2']);
            expect(markSiteLinkWorkerUpdatesFailed).toHaveBeenCalledWith('CMP-1', tn, [{ mappingId: 'PF-1',
                error: 'Frontline worker update failed: NON_EXISTENT_RELATED_ENTITY: the project or the site of the link was not found', terminal: true }]);
        });

        it('marks the row of a link health-project says no longer exists to be created again', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            route(() => ({ ProjectFacilities: [existingLink(oldFields)] }), () => ({ Errors: [{ code: 'NON_EXISTENT_ENTITY' }] }));
            jest.mocked(markSiteLinksToCreateAgain).mockResolvedValueOnce(0).mockResolvedValueOnce(1);
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(markSiteLinksToCreateAgain).toHaveBeenLastCalledWith('CMP-1', tn, { linkIds: ['PF-1'] },
                'Site link no longer exists; it will be created again with the frontline worker');
            expect(markSiteLinkWorkerUpdatesFailed).not.toHaveBeenCalled();
        });

        it('marks the rows of a changed site whose link was not found to be created again, and waits for its stored worker', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            const sent = route(() => ({ ProjectFacilities: [] }), () => ({}));
            jest.mocked(markSiteLinksToCreateAgain).mockResolvedValueOnce(1);
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(sent).toHaveLength(0);
            expect(markSiteLinksToCreateAgain).toHaveBeenCalledWith('CMP-1', tn, { uniqueIdentifiers: ['P1'], boundaryCodes: ['B2'], foundLinkIds: [] },
                'Site link no longer exists; it will be created again with the frontline worker');
            expect(jest.mocked(pollUntilCountFn).mock.calls.some(([, , opts]: any[]) => String(opts?.label).includes('stored frontline workers'))).toBe(true);
        });

        it('reads a link refused for a stale row version again and resends it with the new row version', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            let calls = 0;
            const sent = route((body) => (body?.id ? { ProjectFacilities: [{ ...existingLink(oldFields), rowVersion: 2 }] } : { ProjectFacilities: [existingLink(oldFields)] }),
                () => (++calls === 1 ? { Errors: [{ code: 'MISMATCHED_ROW_VERSION' }] } : {}));
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(sent.map((link: any) => link.rowVersion)).toEqual([1, 2]);
            expect(markSiteLinkWorkerUpdatesFailed).not.toHaveBeenCalled();
        });

        it('counts a link refused for a stale row version as done when it already has the new worker', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            const sent = route((body) => (body?.id ? { ProjectFacilities: [{ ...existingLink(newFields), rowVersion: 2 }] } : { ProjectFacilities: [existingLink(oldFields)] }),
                () => ({ Errors: [{ code: 'MISMATCHED_ROW_VERSION' }] }));
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(sent).toHaveLength(1);
            expect(markSiteLinkWorkerUpdatesFailed).not.toHaveBeenCalled();
        });

        it('leaves a link still refused for a stale row version after the re-reads to the mapping retry, without httpRequest\'s retry', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            const sent = route(() => ({ ProjectFacilities: [existingLink(oldFields)] }), () => ({ Errors: [{ code: 'MISMATCHED_ROW_VERSION' }] }));
            jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set(['PF-1']));
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(sent).toHaveLength(1 + config.mapping.outsideHouseholdLinkRereadAttempts);
            expect(httpMock.mock.calls.filter((c: any[]) => String(c[0]).endsWith(config.paths.projectFacilityUpdate)).every((c: any[]) => c[7] === false)).toBe(true);
            expect(markSiteLinkWorkerUpdatesFailed).toHaveBeenCalledWith('CMP-1', tn, [{ mappingId: 'PF-1',
                error: 'Frontline worker update failed: MISMATCHED_ROW_VERSION: the link kept changing while it was updated', terminal: false }]);
        });

        it('sends a temporary failure once more with httpRequest\'s retry before leaving it to the mapping retry', async () => {
            withBoundary([storedSite('Active', oldWorker)]);
            const sent = route(() => ({ ProjectFacilities: [existingLink(oldFields)] }), () => ({ Errors: [{ code: 'NETWORK_ERROR' }] }));
            jest.mocked(markSiteLinkWorkerUpdatesFailed).mockResolvedValueOnce(new Set(['PF-1']));
            await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
            expect(sent).toHaveLength(2);
            expect(httpMock.mock.calls.filter((c: any[]) => String(c[0]).endsWith(config.paths.projectFacilityUpdate)).map((c: any[]) => c[7])).toEqual([false, true]);
            expect(markSiteLinkWorkerUpdatesFailed).toHaveBeenCalledWith('CMP-1', tn, [{ mappingId: 'PF-1',
                error: 'Frontline worker update failed: NETWORK_ERROR: health-project could not reach the facility service', terminal: false }]);
        });
    });

    it('does not queue the link of a site this upload sets Inactive', async () => {
        getRelatedMock.mockImplementation(async (type: any) =>
            (type === 'facility' ? [storedSite('Active', oldWorker)] : type === 'boundary' ? [{ uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        install({ OUTSIDE_HOUSE: [outsideHouseholdRow('P1', 'B2', 'Inactive', oldWorker)] }, [existingLink(oldFields)]);
        await processCampaignFacilitiesFromExcelData(tn, 'fs-1', localizationMap, campaignDetails);
        expect(resetFailedSiteLinkWorkerUpdates).toHaveBeenCalledWith('CMP-1', tn, []);
    });
});

describe('reusableOtherCampaignUsers (clone with its own sheet)', () => {
    const users = [
        { campaignNumber: 'CMP-SOURCE', data: { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '9000000001' } },
        { campaignNumber: 'CMP-OTHER', data: { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '9000000002' } },
        { campaignNumber: 'CMP-CLONE', data: { HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '9000000003' } },
    ];

    it('reuses users of any other campaign when the sheet is the source\'s file', () => {
        expect(reusableOtherCampaignUsers(users, 'CMP-CLONE', false).map((u: any) => u.campaignNumber)).toEqual(['CMP-SOURCE', 'CMP-OTHER']);
    });

    it('reuses only the source campaign\'s users when the clone has its own sheet', () => {
        expect(reusableOtherCampaignUsers(users, 'CMP-CLONE', true, 'CMP-SOURCE').map((u: any) => u.campaignNumber)).toEqual(['CMP-SOURCE']);
    });

    it('reuses none when the clone has its own sheet and no source is given (switch off)', () => {
        expect(reusableOtherCampaignUsers(users, 'CMP-CLONE', true)).toEqual([]);
    });
});
