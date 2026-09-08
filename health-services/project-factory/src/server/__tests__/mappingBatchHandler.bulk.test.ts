import { handleMappingBatch } from '../utils/mappingBatchHandler';
import { mappingStatuses } from '../config/constants';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
}));

jest.mock('../api/genericApis', () => ({
    createProjectResource: jest.fn(),
    createProjectFacility: jest.fn(),
    createStaff: jest.fn(),
    createStaffBulk: jest.fn().mockResolvedValue(undefined),
    createProjectFacilityBulk: jest.fn().mockResolvedValue(undefined),
    createProjectResourceBulk: jest.fn().mockResolvedValue(undefined),
    searchProjectResourcesByProjects: jest.fn().mockResolvedValue(new Map()),
    searchProjectFacilitiesByProjects: jest.fn().mockResolvedValue(new Map()),
    searchProjectStaffByProjects: jest.fn().mockResolvedValue(new Map()),
}));

jest.mock('../utils/db', () => ({
    executeQuery: jest.fn().mockResolvedValue({ rows: [], rowCount: 0 }),
    getTableName: (name: string) => name,
}));

jest.mock('../utils/mappingGenerationUtils', () => ({
    getCurrentMappingGeneration: jest.fn().mockResolvedValue(null),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/campaignFailureHandler', () => ({
    sendCampaignFailureMessage: jest.fn(),
}));

jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: {
            KAFKA_UPDATE_MAPPING_DATA_TOPIC: 'update-mapping',
            KAFKA_DELETE_MAPPING_DATA_TOPIC: 'delete-mapping',
        },
        mapping: {
            kafkaBatchSize: 30,
            persistBatchSize: 100,
            projectSearchChunkSize: 100,
            searchPageSize: 100,
            createConcurrency: 2,
            maxRetries: 3,
            bulkCreateChunkSize: 2,
            staffBulkEnabled: true,
            bulkConfirmPollIntervalMs: 1,
            bulkConfirmMaxAttempts: 3,
        },
        DB_CONFIG: { DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME: 'eg_cm_campaign_mapping_data' },
    },
}));

import { getRelatedDataWithCampaign } from '../utils/genericUtils';
import {
    createStaff, createStaffBulk, createProjectFacilityBulk, createProjectResourceBulk,
    searchProjectStaffByProjects, searchProjectFacilitiesByProjects, searchProjectResourcesByProjects,
} from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import { executeQuery } from '../utils/db';
import config from '../config';

const executeQueryMock = executeQuery as jest.MockedFunction<typeof executeQuery>;
const getRelatedDataWithCampaignMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const createStaffMock = createStaff as jest.MockedFunction<typeof createStaff>;
const createStaffBulkMock = createStaffBulk as jest.MockedFunction<typeof createStaffBulk>;
const createProjectFacilityBulkMock = createProjectFacilityBulk as jest.MockedFunction<typeof createProjectFacilityBulk>;
const createProjectResourceBulkMock = createProjectResourceBulk as jest.MockedFunction<typeof createProjectResourceBulk>;
const searchProjectStaffByProjectsMock = searchProjectStaffByProjects as jest.MockedFunction<typeof searchProjectStaffByProjects>;
const searchProjectFacilitiesByProjectsMock = searchProjectFacilitiesByProjects as jest.MockedFunction<typeof searchProjectFacilitiesByProjects>;
const searchProjectResourcesByProjectsMock = searchProjectResourcesByProjects as jest.MockedFunction<typeof searchProjectResourcesByProjects>;
const produceModifiedMessagesMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;

const baseMessage = {
    tenantId: 'tn',
    campaignId: 'cmp-id-1',
    campaignNumber: 'CMP-1',
    useruuid: 'u-uuid',
    batchNumber: 1,
    totalBatches: 1,
    requestInfo: { userInfo: { uuid: 'u-uuid' } },
};

/** All mapping rows persisted to the update topic across every produce call. */
function persistedRows(): any[] {
    return produceModifiedMessagesMock.mock.calls
        .filter(([, topic]) => topic === 'update-mapping')
        .flatMap(([payload]: any) => payload?.datas ?? []);
}

beforeEach(() => {
    jest.clearAllMocks();
    // mockReset (not just clear) so mockResolvedValueOnce queues / implementations from a
    // previous test never leak into the next — clearAllMocks does not reset those.
    searchProjectStaffByProjectsMock.mockReset().mockResolvedValue(new Map());
    searchProjectFacilitiesByProjectsMock.mockReset().mockResolvedValue(new Map());
    searchProjectResourcesByProjectsMock.mockReset().mockResolvedValue(new Map());
    createStaffBulkMock.mockReset().mockResolvedValue(undefined);
    createProjectFacilityBulkMock.mockReset().mockResolvedValue(undefined);
    createProjectResourceBulkMock.mockReset().mockResolvedValue(undefined);
    // The confirm-poll and persist wait use setTimeout; resolve immediately in tests.
    jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => { cb(); return 0 as any; }) as any);
});

afterEach(() => {
    (global.setTimeout as any).mockRestore?.();
});

describe('mappingBatchHandler — bulk staff mapping', () => {
    beforeEach(() => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([
                { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' },
                { uniqueIdentifier: '+91-2', uniqueIdAfterProcess: 'usr-svc-2' },
            ] as any);
            return Promise.resolve([] as any);
        });
    });

    // Fresh objects per test — handleMappingBatch mutates the mapping rows (sets status/mappingId),
    // so a shared array would leak state (already-mapped rows) into the next test.
    const staffMappings = () => [
        { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' },
        { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-2' },
    ];

    it('bulk-creates staff and never calls the per-row create', async () => {
        // pre-pass empty, confirm search returns both server ids
        searchProjectStaffByProjectsMock
            .mockResolvedValueOnce(new Map())
            .mockResolvedValue(new Map([['usr-svc-1|project-1', 'staff-1'], ['usr-svc-2|project-1', 'staff-2']]));

        await handleMappingBatch({ ...baseMessage, mappings: staffMappings() });

        expect(createStaffBulkMock).toHaveBeenCalledTimes(1);
        expect(createStaffMock).not.toHaveBeenCalled();
        const entities = createStaffBulkMock.mock.calls[0][0];
        expect(entities).toEqual([
            { tenantId: 'tn', projectId: 'project-1', userId: 'usr-svc-1', startDate: null, endDate: null },
            { tenantId: 'tn', projectId: 'project-1', userId: 'usr-svc-2', startDate: null, endDate: null },
        ]);
    });

    it('confirms via search and records the real server mappingId', async () => {
        // call 1 = adopt pre-pass (empty); subsequent calls = confirm-by-search (both found)
        let n = 0;
        searchProjectStaffByProjectsMock.mockImplementation(async () => {
            n++;
            return n === 1 ? new Map() : new Map([['usr-svc-1|project-1', 'staff-1'], ['usr-svc-2|project-1', 'staff-2']]);
        });

        await handleMappingBatch({ ...baseMessage, mappings: staffMappings() });

        const rows = persistedRows().filter(r => r.type === 'user');
        expect(rows).toHaveLength(2);
        expect(rows.every(r => r.status === mappingStatuses.mapped)).toBe(true);
        expect(rows.find(r => r.uniqueIdentifierForData === '+91-1').mappingId).toBe('staff-1');
        expect(rows.find(r => r.uniqueIdentifierForData === '+91-2').mappingId).toBe('staff-2');
    });

    it('leaves an unconfirmed row toBeMapped (never mapped on a fabricated id, never failed on lag)', async () => {
        // pre-pass empty; only usr-svc-1 ever becomes searchable — usr-svc-2 never confirms
        let n = 0;
        searchProjectStaffByProjectsMock.mockImplementation(async () => {
            n++;
            return n === 1 ? new Map() : new Map([['usr-svc-1|project-1', 'staff-1']]);
        });

        await handleMappingBatch({ ...baseMessage, mappings: staffMappings() });

        const rows = persistedRows().filter(r => r.type === 'user');
        // only the confirmed row is persisted as mapped; the unconfirmed one is left for the reconciler
        expect(rows.map(r => r.uniqueIdentifierForData)).toEqual(['+91-1']);
        expect(rows[0].status).toBe(mappingStatuses.mapped);
        expect(searchProjectStaffByProjectsMock.mock.calls.length).toBeGreaterThan(2); // retried until budget exhausted
    });

    it('chunks bulk creates by bulkCreateChunkSize', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([
                { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' },
                { uniqueIdentifier: '+91-2', uniqueIdAfterProcess: 'usr-svc-2' },
                { uniqueIdentifier: '+91-3', uniqueIdAfterProcess: 'usr-svc-3' },
            ] as any);
            return Promise.resolve([] as any);
        });
        let n = 0;
        searchProjectStaffByProjectsMock.mockImplementation(async () => {
            n++;
            return n === 1 ? new Map() : new Map([
                ['usr-svc-1|project-1', 's1'], ['usr-svc-2|project-1', 's2'], ['usr-svc-3|project-1', 's3'],
            ]);
        });

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' },
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-2' },
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-3' },
            ],
        });

        // 3 rows, chunk size 2 -> 2 bulk calls (2 + 1)
        expect(createStaffBulkMock).toHaveBeenCalledTimes(2);
    });
});

describe('mappingBatchHandler — bulk facility & resource mapping', () => {
    it('bulk-creates facilities with root tenant and facilityId', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'facility') return Promise.resolve([
                { uniqueIdentifier: 'FN-1', uniqueIdAfterProcess: 'fac-1', data: { HCM_ADMIN_CONSOLE_FACILITY_CODE: 'fac-1' } },
            ] as any);
            return Promise.resolve([] as any);
        });
        let n = 0;
        searchProjectFacilitiesByProjectsMock.mockImplementation(async () => {
            n++;
            return n === 1 ? new Map() : new Map([['fac-1|project-1', 'pf-1']]);
        });

        await handleMappingBatch({
            ...baseMessage, tenantId: 'ng.kaduna',
            mappings: [{ type: 'facility', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'FN-1' }],
        });

        expect(createProjectFacilityBulkMock).toHaveBeenCalledTimes(1);
        const entities = createProjectFacilityBulkMock.mock.calls[0][0];
        expect(entities[0]).toMatchObject({ tenantId: 'ng', projectId: 'project-1', facilityId: 'fac-1' });
        const row = persistedRows().find(r => r.type === 'facility');
        expect(row.status).toBe(mappingStatuses.mapped);
        expect(row.mappingId).toBe('pf-1');
    });

    it('bulk-creates resources with productVariantId', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            return Promise.resolve([] as any);
        });
        let n = 0;
        searchProjectResourcesByProjectsMock.mockImplementation(async () => {
            n++;
            return n === 1 ? new Map() : new Map([['PVAR-1|project-1', 'pr-1']]);
        });

        await handleMappingBatch({
            ...baseMessage,
            mappings: [{ type: 'resource', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'PVAR-1' }],
        });

        expect(createProjectResourceBulkMock).toHaveBeenCalledTimes(1);
        const entities = createProjectResourceBulkMock.mock.calls[0][0];
        expect(entities[0]).toMatchObject({ projectId: 'project-1', resource: { productVariantId: 'PVAR-1', type: 'DRUG' } });
        const row = persistedRows().find(r => r.type === 'resource');
        expect(row.mappingId).toBe('pr-1');
    });
});

describe('mappingBatchHandler — bulk mapping edge cases', () => {
    // two phones resolving to the SAME userId => same entityId|projectId key
    beforeEach(() => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([
                { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' },
                { uniqueIdentifier: '+91-2', uniqueIdAfterProcess: 'usr-svc-1' },
            ] as any);
            return Promise.resolve([] as any);
        });
    });

    const dupMappings = () => [
        { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' },
        { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-2' },
    ];

    it('#1 dedupes rows with the same entityId|projectId — one created, the sibling failed (not silently dropped)', async () => {
        let n = 0;
        searchProjectStaffByProjectsMock.mockImplementation(async () => {
            n++;
            return n === 1 ? new Map() : new Map([['usr-svc-1|project-1', 'staff-1']]);
        });

        await handleMappingBatch({ ...baseMessage, mappings: dupMappings() });

        // only one distinct entity is bulk-created
        expect(createStaffBulkMock).toHaveBeenCalledTimes(1);
        expect(createStaffBulkMock.mock.calls[0][0]).toHaveLength(1);

        const rows = persistedRows().filter(r => r.type === 'user');
        const mapped = rows.filter(r => r.status === mappingStatuses.mapped);
        const failed = rows.filter(r => r.status === mappingStatuses.failed);
        expect(mapped.map(r => r.uniqueIdentifierForData)).toEqual(['+91-1']);
        expect(failed.map(r => r.uniqueIdentifierForData)).toEqual(['+91-2']);
        // duplicate failure reason is captured in lastError (executeQuery UPDATE)
        expect(executeQueryMock).toHaveBeenCalled();
    });

    it('#2 floors bulkConfirmMaxAttempts to 1 so a created row still confirms when configured to 0', async () => {
        const original = config.mapping.bulkConfirmMaxAttempts;
        (config.mapping as any).bulkConfirmMaxAttempts = 0;
        try {
            getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
                if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
                if (type === 'user') return Promise.resolve([{ uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' }] as any);
                return Promise.resolve([] as any);
            });
            let n = 0;
            searchProjectStaffByProjectsMock.mockImplementation(async () => {
                n++;
                return n === 1 ? new Map() : new Map([['usr-svc-1|project-1', 'staff-1']]);
            });

            await handleMappingBatch({
                ...baseMessage,
                mappings: [{ type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' }],
            });

            const row = persistedRows().find(r => r.type === 'user');
            expect(row.status).toBe(mappingStatuses.mapped);
            expect(row.mappingId).toBe('staff-1');
        } finally {
            (config.mapping as any).bulkConfirmMaxAttempts = original;
        }
    });

    it('#3 a chunk whose bulk POST throws marks its rows failed and does not abort the batch', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([
                { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' },
                { uniqueIdentifier: '+91-2', uniqueIdAfterProcess: 'usr-svc-2' },
            ] as any);
            return Promise.resolve([] as any);
        });
        createStaffBulkMock.mockRejectedValue(new Error('503 upstream'));

        // must resolve (not throw)
        await expect(handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' },
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-2' },
            ],
        })).resolves.toBeUndefined();

        const rows = persistedRows().filter(r => r.type === 'user');
        expect(rows).toHaveLength(2);
        expect(rows.every(r => r.status === mappingStatuses.failed)).toBe(true);
        // no confirm search fired for a chunk that never posted (pre-pass only)
        expect(searchProjectStaffByProjectsMock).toHaveBeenCalledTimes(1);
        expect(executeQueryMock).toHaveBeenCalled(); // lastError persisted
    });

    it('#4 a confirm-search that throws does not abort the batch — it retries and leaves the row toBeMapped', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([{ uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' }] as any);
            return Promise.resolve([] as any);
        });
        let n = 0;
        searchProjectStaffByProjectsMock.mockImplementation(async () => {
            n++;
            if (n === 1) return new Map();          // pre-pass ok
            throw new Error('search timeout');       // every confirm attempt throws
        });

        await expect(handleMappingBatch({
            ...baseMessage,
            mappings: [{ type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' }],
        })).resolves.toBeUndefined();

        // bulk was created but never confirmed -> row not marked mapped/failed
        expect(createStaffBulkMock).toHaveBeenCalledTimes(1);
        expect(persistedRows().filter(r => r.type === 'user' && r.status === mappingStatuses.mapped)).toHaveLength(0);
        // pre-pass (1) + all confirm attempts (bulkConfirmMaxAttempts = 3) = 4 calls
        expect(searchProjectStaffByProjectsMock).toHaveBeenCalledTimes(4);
    });

    it('#4b a confirm-search that throws once then succeeds still maps the row', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([{ uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' }] as any);
            return Promise.resolve([] as any);
        });
        let n = 0;
        searchProjectStaffByProjectsMock.mockImplementation(async () => {
            n++;
            if (n === 1) return new Map();                 // pre-pass
            if (n === 2) throw new Error('transient');     // first confirm throws
            return new Map([['usr-svc-1|project-1', 'staff-1']]); // next confirm succeeds
        });

        await handleMappingBatch({
            ...baseMessage,
            mappings: [{ type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' }],
        });

        const row = persistedRows().find(r => r.type === 'user');
        expect(row.status).toBe(mappingStatuses.mapped);
        expect(row.mappingId).toBe('staff-1');
    });
});

describe('mappingBatchHandler — STAFF_MAPPING_BULK gate', () => {
    afterEach(() => { (config.mapping as any).staffBulkEnabled = true; });

    it('staff uses the synchronous per-row create (not bulk) when staffBulkEnabled is false, even with chunk size > 0', async () => {
        (config.mapping as any).staffBulkEnabled = false;
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user') return Promise.resolve([{ uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usr-svc-1' }] as any);
            return Promise.resolve([] as any);
        });
        createStaffMock.mockResolvedValue({ ProjectStaff: { id: 'staff-sync-1' } } as any);

        await handleMappingBatch({
            ...baseMessage,
            mappings: [{ type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1' }],
        });

        expect(createStaffMock).toHaveBeenCalledTimes(1);   // per-row sync
        expect(createStaffBulkMock).not.toHaveBeenCalled();  // never bulk
        const row = persistedRows().find(r => r.type === 'user');
        expect(row.status).toBe(mappingStatuses.mapped);
        expect(row.mappingId).toBe('staff-sync-1');
    });

    it('facility still uses bulk when staffBulkEnabled is false (gate is staff-only)', async () => {
        (config.mapping as any).staffBulkEnabled = false;
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'facility') return Promise.resolve([{ uniqueIdentifier: 'FN-1', uniqueIdAfterProcess: 'fac-1', data: { HCM_ADMIN_CONSOLE_FACILITY_CODE: 'fac-1' } }] as any);
            return Promise.resolve([] as any);
        });
        let n = 0;
        searchProjectFacilitiesByProjectsMock.mockImplementation(async () => { n++; return n === 1 ? new Map() : new Map([['fac-1|project-1', 'pf-1']]); });

        await handleMappingBatch({
            ...baseMessage,
            mappings: [{ type: 'facility', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'FN-1' }],
        });

        expect(createProjectFacilityBulkMock).toHaveBeenCalledTimes(1); // facility still bulk
    });
});
