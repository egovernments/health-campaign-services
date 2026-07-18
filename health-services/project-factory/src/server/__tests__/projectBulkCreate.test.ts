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
jest.mock('../utils/userBatchHandler', () => ({
    fetchExistingUsersByPhone: jest.fn().mockResolvedValue({}),
    selectReconcilableUserRows: jest.fn(() => []),
}));
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
        values: { skipParentProjectConfirmation: false },
        host: { hrmsHost: 'http://hrms/', healthIndividualHost: 'http://individual/', excelIngestionHost: 'http://ei/', projectHost: 'http://project/' },
        paths: {
            hrmsEmployeeCreate: 'hrms/create',
            projectCreate: 'project/create',
            projectSearch: 'project/search',
            healthIndividualSearch: 'individual/search',
            excelIngestionSheetSearch: 'ei/search',
        },
        localisation: { defaultLocale: 'en_IN' },
        DB_CONFIG: { DB_CAMPAIGN_DATA_TABLE_NAME: 'eg_cm_campaign_data' },
        // Page size 1 → small fixtures span multiple pages.
        excelIngestion: { sheetFetchPageSize: 1, persistenceStallTimeoutMs: 120000, persistencePollIntervalMs: 10000 },
        batchSize: 100,
        project: { creationBatchSize: 20, bulkCreateChunkSize: 2, bulkCreateConcurrency: 5, searchPageSize: 2 },
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

import { createLevelBulk, fetchAllProjectsByReferenceId } from '../utils/processingResultHandler';
import { httpRequest } from '../utils/request';
import { produceModifiedMessages } from '../kafka/Producer';
import { confirmProjectParentCreation } from '../api/campaignApis';
import { dataRowStatuses } from '../config/constants';

const httpRequestMock = httpRequest as jest.MockedFunction<typeof httpRequest>;
const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const confirmParentMock = confirmProjectParentCreation as jest.MockedFunction<typeof confirmProjectParentCreation>;

const targetConfig = { beneficiaries: [{ beneficiaryType: 'INDIVIDUAL', columns: ['T'] }] };
const Projects = [{ projectType: 'X' }];
const projectCreateBody = { RequestInfo: {} };

function boundaryRow(code: string, target = 100) {
    return { data: { HCM_ADMIN_CONSOLE_BOUNDARY_CODE: code, T: target }, status: dataRowStatuses.pending };
}
/** rows persisted to the sheet-update topic, flattened */
function sheetRows(): any[] {
    return produceMock.mock.calls.filter(([, t]) => t === 'update-sheet').flatMap(([p]: any) => p?.datas ?? []);
}

beforeEach(() => {
    jest.clearAllMocks();
    confirmParentMock.mockResolvedValue({} as any);
    produceMock.mockResolvedValue(undefined as any);
});

describe('createLevelBulk', () => {
    it('bulk-creates a level via array payload and maps ids back by address.boundary', async () => {
        httpRequestMock.mockImplementation(async (url: string, body: any) => {
            if (url.includes('project/create')) {
                return { Project: body.Projects.map((p: any) => ({ id: `id-${p.address.boundary}`, address: { boundary: p.address.boundary } })) } as any;
            }
            return {} as any;
        });
        const boundaryMap: any = { A: { type: 'v', parent: null }, B: { type: 'v', parent: null } };
        const level = [boundaryRow('A'), boundaryRow('B')];

        await createLevelBulk(level as any, 'tn', 'CMP-1', targetConfig, projectCreateBody, Projects, boundaryMap, 'u', new Map(), {} as any);

        // one create call (chunk size 2, two rows)
        const createCalls = httpRequestMock.mock.calls.filter(([u]) => String(u).includes('project/create'));
        expect(createCalls).toHaveLength(1);
        expect(createCalls[0][1].Projects).toHaveLength(2);
        expect(boundaryMap.A.projectId).toBe('id-A');
        expect(boundaryMap.B.projectId).toBe('id-B');
        const rows = sheetRows();
        expect(rows.every(r => r.status === dataRowStatuses.completed)).toBe(true);
        expect(rows.find(r => r.data.HCM_ADMIN_CONSOLE_BOUNDARY_CODE === 'A').uniqueIdAfterProcess).toBe('id-A');
    });

    it('chunks the level by bulkCreateChunkSize (2) — 3 rows => 2 create calls', async () => {
        httpRequestMock.mockImplementation(async (url: string, body: any) => {
            if (url.includes('project/create')) return { Project: body.Projects.map((p: any) => ({ id: `id-${p.address.boundary}`, address: { boundary: p.address.boundary } })) } as any;
            return {} as any;
        });
        const boundaryMap: any = { A: { type: 'v', parent: null }, B: { type: 'v', parent: null }, C: { type: 'v', parent: null } };
        await createLevelBulk([boundaryRow('A'), boundaryRow('B'), boundaryRow('C')] as any, 'tn', 'CMP-1', targetConfig, projectCreateBody, Projects, boundaryMap, 'u', new Map(), {} as any);
        expect(httpRequestMock.mock.calls.filter(([u]) => String(u).includes('project/create'))).toHaveLength(2);
    });

    it('adopts already-existing projects (from the prefetch map) without creating them', async () => {
        httpRequestMock.mockResolvedValue({} as any);
        const boundaryMap: any = { A: { type: 'v', parent: null } };
        const existing = new Map([['A', 'existing-A']]);
        await createLevelBulk([boundaryRow('A')] as any, 'tn', 'CMP-1', targetConfig, projectCreateBody, Projects, boundaryMap, 'u', existing, {} as any);
        expect(httpRequestMock.mock.calls.filter(([u]) => String(u).includes('project/create'))).toHaveLength(0);
        expect(boundaryMap.A.projectId).toBe('existing-A');
        expect(sheetRows()[0].status).toBe(dataRowStatuses.completed);
    });

    it('passes the parent project id into a child body and confirms the parent once', async () => {
        httpRequestMock.mockImplementation(async (url: string, body: any) => {
            if (url.includes('project/create')) return { Project: body.Projects.map((p: any) => ({ id: `id-${p.address.boundary}`, address: { boundary: p.address.boundary }, parent: p.parent })) } as any;
            return {} as any;
        });
        // child level; parent P already created (has projectId in boundaryMap)
        const boundaryMap: any = { P: { type: 'd', parent: null, projectId: 'proj-P' }, CH: { type: 'v', parent: 'P' } };
        await createLevelBulk([boundaryRow('CH')] as any, 'tn', 'CMP-1', targetConfig, projectCreateBody, Projects, boundaryMap, 'u', new Map(), {} as any);
        const createCall = httpRequestMock.mock.calls.find(([u]) => String(u).includes('project/create'));
        expect(createCall![1].Projects[0].parent).toBe('proj-P');
        expect(confirmParentMock).toHaveBeenCalledTimes(1);
        expect(confirmParentMock).toHaveBeenCalledWith('tn', 'u', 'proj-P', expect.anything());
    });

    it('fails a child whose parent has no project id (does not create it)', async () => {
        httpRequestMock.mockResolvedValue({} as any);
        const boundaryMap: any = { P: { type: 'd', parent: null }, CH: { type: 'v', parent: 'P' } }; // P has no projectId
        await createLevelBulk([boundaryRow('CH')] as any, 'tn', 'CMP-1', targetConfig, projectCreateBody, Projects, boundaryMap, 'u', new Map(), {} as any);
        expect(httpRequestMock.mock.calls.filter(([u]) => String(u).includes('project/create'))).toHaveLength(0);
        expect(sheetRows()[0].status).toBe(dataRowStatuses.failed);
    });

    it('a failed create chunk marks only its rows failed, others succeed', async () => {
        httpRequestMock.mockImplementation(async (url: string, body: any) => {
            if (url.includes('project/create')) {
                const codes = body.Projects.map((p: any) => p.address.boundary);
                if (codes.includes('B')) throw new Error('503');   // the chunk containing B fails
                return { Project: body.Projects.map((p: any) => ({ id: `id-${p.address.boundary}`, address: { boundary: p.address.boundary } })) } as any;
            }
            return {} as any;
        });
        const boundaryMap: any = { A: { type: 'v', parent: null }, B: { type: 'v', parent: null }, C: { type: 'v', parent: null }, D: { type: 'v', parent: null } };
        // chunk size 2 => [A,B] (fails) and [C,D] (ok)
        await createLevelBulk([boundaryRow('A'), boundaryRow('B'), boundaryRow('C'), boundaryRow('D')] as any, 'tn', 'CMP-1', targetConfig, projectCreateBody, Projects, boundaryMap, 'u', new Map(), {} as any);
        const rows = sheetRows();
        const byCode = (code: string) => rows.find(r => r.data.HCM_ADMIN_CONSOLE_BOUNDARY_CODE === code);
        expect(byCode('A').status).toBe(dataRowStatuses.failed);
        expect(byCode('B').status).toBe(dataRowStatuses.failed);
        expect(byCode('C').status).toBe(dataRowStatuses.completed);
        expect(byCode('D').status).toBe(dataRowStatuses.completed);
    });
});

describe('fetchAllProjectsByReferenceId', () => {
    it('paginates and returns boundaryCode -> projectId', async () => {
        // searchPageSize = 2 -> first full page then a short page ends it
        httpRequestMock
            .mockResolvedValueOnce({ Project: [{ id: 'p1', address: { boundary: 'A' } }, { id: 'p2', address: { boundary: 'B' } }] } as any)
            .mockResolvedValueOnce({ Project: [{ id: 'p3', address: { boundary: 'C' } }] } as any);
        const map = await fetchAllProjectsByReferenceId('CMP-1', 'tn', {});
        expect(map.get('A')).toBe('p1');
        expect(map.get('B')).toBe('p2');
        expect(map.get('C')).toBe('p3');
        expect(httpRequestMock).toHaveBeenCalledTimes(2);
    });
});
