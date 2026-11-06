import { handleMappingBatch } from '../utils/mappingBatchHandler';
import { mappingStatuses } from '../config/constants';
import config from '../config';

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
    searchProjectResourcesByProjects: jest.fn().mockResolvedValue(new Map()),
    searchProjectFacilitiesByProjects: jest.fn().mockResolvedValue(new Map()),
    searchProjectStaffByProjects: jest.fn().mockResolvedValue(new Map()),
    deleteProjectFacilitiesById: jest.fn().mockResolvedValue(new Map()),
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

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

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
            outsideHouseholdLinkUpdateConcurrency: 2,
        },
        host: jest.requireActual('../config').default.host,
        paths: jest.requireActual('../config').default.paths,
        DB_CONFIG: { DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME: 'eg_cm_campaign_mapping_data' },
    },
}));

import { getRelatedDataWithCampaign } from '../utils/genericUtils';
import { createProjectFacility, createProjectResource, createStaff, searchProjectResourcesByProjects, searchProjectFacilitiesByProjects, searchProjectStaffByProjects, deleteProjectFacilitiesById } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import { sendCampaignFailureMessage } from '../utils/campaignFailureHandler';
import { getCurrentMappingGeneration } from '../utils/mappingGenerationUtils';
import { executeQuery } from '../utils/db';
import { httpRequest } from '../utils/request';

const getRelatedDataWithCampaignMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const createStaffMock = createStaff as jest.MockedFunction<typeof createStaff>;
const createProjectFacilityMock = createProjectFacility as jest.MockedFunction<typeof createProjectFacility>;
const createProjectResourceMock = createProjectResource as jest.MockedFunction<typeof createProjectResource>;
const searchProjectResourcesByProjectsMock = searchProjectResourcesByProjects as jest.MockedFunction<typeof searchProjectResourcesByProjects>;
const searchProjectFacilitiesByProjectsMock = searchProjectFacilitiesByProjects as jest.MockedFunction<typeof searchProjectFacilitiesByProjects>;
const searchProjectStaffByProjectsMock = searchProjectStaffByProjects as jest.MockedFunction<typeof searchProjectStaffByProjects>;
const deleteProjectFacilitiesByIdMock = deleteProjectFacilitiesById as jest.MockedFunction<typeof deleteProjectFacilitiesById>;
const produceModifiedMessagesMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const sendCampaignFailureMessageMock = sendCampaignFailureMessage as jest.MockedFunction<typeof sendCampaignFailureMessage>;
const getCurrentMappingGenerationMock = getCurrentMappingGeneration as jest.MockedFunction<typeof getCurrentMappingGeneration>;
const executeQueryMock = executeQuery as jest.MockedFunction<typeof executeQuery>;

describe('mappingBatchHandler — user-mapping skipping for failed users', () => {
    const baseMessage = {
        tenantId: 'tn',
        campaignId: 'cmp-id-1',
        campaignNumber: 'CMP-1',
        useruuid: 'u-uuid',
        batchNumber: 1,
        totalBatches: 1,
        requestInfo: { userInfo: { uuid: 'u-uuid' } },
    };

    beforeEach(() => {
        jest.clearAllMocks();
        searchProjectResourcesByProjectsMock.mockResolvedValue(new Map());
        searchProjectFacilitiesByProjectsMock.mockResolvedValue(new Map());
        searchProjectStaffByProjectsMock.mockResolvedValue(new Map());
        getCurrentMappingGenerationMock.mockResolvedValue(null);
        executeQueryMock.mockResolvedValue({ rows: [], rowCount: 0 } as any);
        // persistInBatches has a 5 s wait that blows the default test timeout.
        // Patch setTimeout to resolve immediately for these tests.
        jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => {
            cb();
            return 0 as any;
        }) as any);
    });

    afterEach(() => {
        (global.setTimeout as any).mockRestore?.();
    });

    it('marks user mapping as skipped (not failed) when the user has no uniqueIdAfterProcess', async () => {
        // boundary exists with project id, user row exists but uniqueIdAfterProcess is null (HRMS failed/invalid)
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') {
                return Promise.resolve([
                    { uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' },
                ] as any);
            }
            if (type === 'facility') {
                return Promise.resolve([] as any);
            }
            if (type === 'user') {
                // Failed user — no uniqueIdAfterProcess
                return Promise.resolve([
                    { uniqueIdentifier: '+91-99999', uniqueIdAfterProcess: null },
                ] as any);
            }
            return Promise.resolve([] as any);
        });

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                {
                    type: 'user',
                    status: mappingStatuses.toBeMapped,
                    boundaryCode: 'B-1',
                    uniqueIdentifierForData: '+91-99999',
                },
            ],
        });

        // createStaff should not be called because the user wasn't created
        expect(createStaffMock).not.toHaveBeenCalled();

        // The mapping should be persisted with status=skipped via update topic
        expect(produceModifiedMessagesMock).toHaveBeenCalled();
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'user');
        expect(persisted).toBeDefined();
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.skipped);
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
    });

    it('still marks facility mapping as failed (not skipped) when the boundary lookup fails — facility hard-blocking remains', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary') return Promise.resolve([] as any); // no project
            if (type === 'facility')
                return Promise.resolve([
                    { uniqueIdentifier: 'F-1', uniqueIdAfterProcess: 'fac-1' },
                ] as any);
            if (type === 'user') return Promise.resolve([] as any);
            return Promise.resolve([] as any);
        });

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                {
                    type: 'facility',
                    status: mappingStatuses.toBeMapped,
                    boundaryCode: 'B-missing',
                    uniqueIdentifierForData: 'F-1',
                },
            ],
        });

        expect(createProjectFacilityMock).not.toHaveBeenCalled();
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'facility');
        expect(persisted).toBeDefined();
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.failed);
    });

    it('still marks user mapping failed when the staff API throws after a successful user lookup', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([
                    { uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' },
                ] as any);
            if (type === 'facility') return Promise.resolve([] as any);
            if (type === 'user')
                return Promise.resolve([
                    { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'user-svc-1' },
                ] as any);
            return Promise.resolve([] as any);
        });

        createStaffMock.mockRejectedValueOnce(new Error('hrms staff blew up'));

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                {
                    type: 'user',
                    status: mappingStatuses.toBeMapped,
                    boundaryCode: 'B-1',
                    uniqueIdentifierForData: '+91-1',
                },
            ],
        });

        expect(createStaffMock).toHaveBeenCalledTimes(1);
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'user');
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.failed);
    });
});

describe('mappingBatchHandler — adopt-existing pre-pass', () => {
    const baseMessage = {
        tenantId: 'tn',
        campaignId: 'cmp-id-1',
        campaignNumber: 'CMP-1',
        useruuid: 'u-uuid',
        batchNumber: 1,
        totalBatches: 1,
        requestInfo: { userInfo: { uuid: 'u-uuid' } },
    };

    beforeEach(() => {
        jest.clearAllMocks();
        searchProjectResourcesByProjectsMock.mockResolvedValue(new Map());
        searchProjectFacilitiesByProjectsMock.mockResolvedValue(new Map());
        searchProjectStaffByProjectsMock.mockResolvedValue(new Map());
        getCurrentMappingGenerationMock.mockResolvedValue(null);
        executeQueryMock.mockResolvedValue({ rows: [], rowCount: 0 } as any);
        jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => {
            cb();
            return 0 as any;
        }) as any);
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            return Promise.resolve([] as any);
        });
    });

    afterEach(() => {
        (global.setTimeout as any).mockRestore?.();
    });

    it('adopts an already-existing project resource as mapped without calling create', async () => {
        searchProjectResourcesByProjectsMock.mockResolvedValue(new Map([['pvar-1|project-1', 'existing-pr-id']]));

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'resource', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'pvar-1', campaignNumber: 'CMP-1' },
            ],
        });

        expect(createProjectResourceMock).not.toHaveBeenCalled();
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'resource');
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.mapped);
        expect((persisted as any).datas[0].mappingId).toBe('existing-pr-id');
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
        // Adopted-only batches skip the post-produce persister wait entirely.
        const longWaits = (global.setTimeout as unknown as jest.Mock).mock.calls.filter((c: any[]) => c[1] >= 5000);
        expect(longWaits).toHaveLength(0);
    });

    it('creates the project resource when the pre-pass finds no existing combination', async () => {
        createProjectResourceMock.mockResolvedValue({ ProjectResource: { id: 'new-pr-id' } } as any);

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'resource', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'pvar-1', campaignNumber: 'CMP-1' },
            ],
        });

        expect(createProjectResourceMock).toHaveBeenCalledTimes(1);
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'resource');
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.mapped);
        expect((persisted as any).datas[0].mappingId).toBe('new-pr-id');
    });

    it('adopts an already-existing project staff mapping without calling createStaff', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user')
                return Promise.resolve([{ uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'user-svc-1' }] as any);
            return Promise.resolve([] as any);
        });
        searchProjectStaffByProjectsMock.mockResolvedValue(new Map([['user-svc-1|project-1', 'existing-ps-id']]));

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1', campaignNumber: 'CMP-1' },
            ],
        });

        expect(createStaffMock).not.toHaveBeenCalled();
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'user');
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.mapped);
        expect((persisted as any).datas[0].mappingId).toBe('existing-ps-id');
    });

    it('adopts an already-existing project facility searched under the root tenant', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'facility')
                return Promise.resolve([{ uniqueIdentifier: 'F-1', uniqueIdAfterProcess: 'fac-1' }] as any);
            return Promise.resolve([] as any);
        });
        searchProjectFacilitiesByProjectsMock.mockResolvedValue(new Map([['fac-1|project-1', 'existing-pf-id']]));

        await handleMappingBatch({
            ...baseMessage,
            tenantId: 'tn.sub',
            mappings: [
                { type: 'facility', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'F-1', campaignNumber: 'CMP-1' },
            ],
        });

        expect(createProjectFacilityMock).not.toHaveBeenCalled();
        expect(searchProjectFacilitiesByProjectsMock).toHaveBeenCalledWith(['project-1'], 'tn', expect.anything(), ['fac-1']);
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'facility');
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.mapped);
        expect((persisted as any).datas[0].mappingId).toBe('existing-pf-id');
    });

    it('passes the batch user ids to the staff pre-pass search', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'user')
                return Promise.resolve([{ uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'user-svc-1' }] as any);
            return Promise.resolve([] as any);
        });
        createStaffMock.mockResolvedValue({ ProjectStaff: { id: 'ps-new' } } as any);

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'user', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: '+91-1', campaignNumber: 'CMP-1' },
            ],
        });

        expect(searchProjectStaffByProjectsMock).toHaveBeenCalledWith(['project-1'], 'tn', expect.anything(), ['user-svc-1']);
    });

    it('marks a failed facility demap as deMapFailed (not failed)', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            if (type === 'facility')
                return Promise.resolve([{ uniqueIdentifier: 'F-1', uniqueIdAfterProcess: 'fac-1' }] as any);
            return Promise.resolve([] as any);
        });
        deleteProjectFacilitiesByIdMock.mockResolvedValueOnce(new Map([['pf-1', { result: 'failed' as const, error: 'facility search exploded' }]]));

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'facility', status: mappingStatuses.toBeDeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'F-1', mappingId: 'pf-1', campaignNumber: 'CMP-1' },
            ],
        });

        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas) && arg.datas[0]?.type === 'facility');
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.deMapFailed);
    });

    it('removes every demapped facility link by its recorded id in one call', async () => {
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([
                    { uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' },
                    { uniqueIdentifier: 'B-2', uniqueIdAfterProcess: 'project-2' },
                ] as any);
            if (type === 'facility')
                return Promise.resolve([
                    { uniqueIdentifier: 'F-1', uniqueIdAfterProcess: 'fac-1' },
                    { uniqueIdentifier: 'F-2', uniqueIdAfterProcess: 'fac-2' },
                    { uniqueIdentifier: 'F-3', uniqueIdAfterProcess: 'fac-3' },
                ] as any);
            return Promise.resolve([] as any);
        });
        deleteProjectFacilitiesByIdMock.mockResolvedValueOnce(new Map([
            ['pf-1', { result: 'deleted' as const }],
            ['pf-2', { result: 'absent' as const }],
            ['pf-3', { result: 'mismatch' as const, error: 'joins another project' }],
        ]));
        const { httpRequest } = require('../utils/request');

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'facility', status: mappingStatuses.toBeDeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'F-1', mappingId: 'pf-1', campaignNumber: 'CMP-1' },
                { type: 'facility', status: mappingStatuses.toBeDeMapped, boundaryCode: 'B-2', uniqueIdentifierForData: 'F-2', mappingId: 'pf-2', campaignNumber: 'CMP-1' },
                { type: 'facility', status: mappingStatuses.toBeDeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'F-3', mappingId: 'pf-3', campaignNumber: 'CMP-1' },
                { type: 'facility', status: mappingStatuses.toBeDeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'F-4', mappingId: 'pf-4', campaignNumber: 'CMP-1' },
            ],
        });

        expect(httpRequest).not.toHaveBeenCalled();
        expect(deleteProjectFacilitiesByIdMock).toHaveBeenCalledTimes(1);
        expect(deleteProjectFacilitiesByIdMock.mock.calls[0][0]).toEqual([
            { mapping: expect.anything(), id: 'pf-1', projectId: 'project-1', facilityId: 'fac-1' },
            { mapping: expect.anything(), id: 'pf-2', projectId: 'project-2', facilityId: 'fac-2' },
            { mapping: expect.anything(), id: 'pf-3', projectId: 'project-1', facilityId: 'fac-3' },
        ]);
        const sent = (topic: string) => produceModifiedMessagesMock.mock.calls
            .filter(c => c[1] === topic)
            .flatMap(c => (c[0] as any).datas)
            .filter((m: any) => m?.type === 'facility');
        expect(sent('delete-mapping').map((m: any) => m.mappingId).sort()).toEqual(['pf-1', 'pf-2', 'pf-4']);
        expect(sent('update-mapping').map((m: any) => [m.mappingId, m.status])).toEqual([['pf-3', mappingStatuses.deMapFailed]]);
    });

    it('persists lastError via direct SQL when a create fails', async () => {
        createProjectResourceMock.mockRejectedValue(new Error('project service exploded'));

        await handleMappingBatch({
            ...baseMessage,
            mappings: [
                { type: 'resource', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'pvar-1', campaignNumber: 'CMP-1' },
            ],
        });

        const updateCall = executeQueryMock.mock.calls.find(([sql]) => String(sql).includes('lastError'));
        expect(updateCall).toBeDefined();
        expect((updateCall as any)[1][0]).toBe('project service exploded');
    });
});

describe('mappingBatchHandler — generation fencing', () => {
    // The handler mutates mapping.status in place — build a fresh message per test.
    const baseMessage = () => ({
        tenantId: 'tn',
        campaignId: 'cmp-id-1',
        campaignNumber: 'CMP-1',
        useruuid: 'u-uuid',
        batchNumber: 1,
        totalBatches: 1,
        requestInfo: { userInfo: { uuid: 'u-uuid' } },
        mappings: [
            { type: 'resource', status: mappingStatuses.toBeMapped, boundaryCode: 'B-1', uniqueIdentifierForData: 'pvar-1', campaignNumber: 'CMP-1' },
        ],
    });

    beforeEach(() => {
        jest.clearAllMocks();
        searchProjectResourcesByProjectsMock.mockResolvedValue(new Map());
        searchProjectFacilitiesByProjectsMock.mockResolvedValue(new Map());
        searchProjectStaffByProjectsMock.mockResolvedValue(new Map());
        executeQueryMock.mockResolvedValue({ rows: [], rowCount: 0 } as any);
        jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => {
            cb();
            return 0 as any;
        }) as any);
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            return Promise.resolve([] as any);
        });
        createProjectResourceMock.mockResolvedValue({ ProjectResource: { id: 'new-pr-id' } } as any);
    });

    afterEach(() => {
        (global.setTimeout as any).mockRestore?.();
    });

    it('drops a stale-generation batch without processing', async () => {
        getCurrentMappingGenerationMock.mockResolvedValue(5 as any);

        await handleMappingBatch({ ...baseMessage(), generation: 4 });

        expect(createProjectResourceMock).not.toHaveBeenCalled();
        expect(produceModifiedMessagesMock).not.toHaveBeenCalled();
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
    });

    it('processes a current-generation batch', async () => {
        getCurrentMappingGenerationMock.mockResolvedValue(5 as any);

        await handleMappingBatch({ ...baseMessage(), generation: 5 });

        expect(createProjectResourceMock).toHaveBeenCalledTimes(1);
    });

    it('fails open and processes when no generation is recorded in Redis', async () => {
        getCurrentMappingGenerationMock.mockResolvedValue(null);

        await handleMappingBatch({ ...baseMessage(), generation: 7 });

        expect(createProjectResourceMock).toHaveBeenCalledTimes(1);
    });

    it('processes a legacy batch without a generation field', async () => {
        await handleMappingBatch({ ...baseMessage() });

        expect(getCurrentMappingGenerationMock).not.toHaveBeenCalled();
        expect(createProjectResourceMock).toHaveBeenCalledTimes(1);
    });
});

describe('mappingBatchHandler — bounded create concurrency', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchProjectResourcesByProjectsMock.mockResolvedValue(new Map());
        searchProjectFacilitiesByProjectsMock.mockResolvedValue(new Map());
        searchProjectStaffByProjectsMock.mockResolvedValue(new Map());
        getCurrentMappingGenerationMock.mockResolvedValue(null);
        executeQueryMock.mockResolvedValue({ rows: [], rowCount: 0 } as any);
        jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => {
            cb();
            return 0 as any;
        }) as any);
        getRelatedDataWithCampaignMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }] as any);
            return Promise.resolve([] as any);
        });
    });

    afterEach(() => {
        (global.setTimeout as any).mockRestore?.();
    });

    it('never exceeds config.mapping.createConcurrency parallel create calls', async () => {
        let inFlight = 0;
        let maxInFlight = 0;
        createProjectResourceMock.mockImplementation(async () => {
            inFlight++;
            maxInFlight = Math.max(maxInFlight, inFlight);
            await Promise.resolve();
            inFlight--;
            return { ProjectResource: { id: 'id' } } as any;
        });

        await handleMappingBatch({
            tenantId: 'tn',
            campaignId: 'cmp-id-1',
            campaignNumber: 'CMP-1',
            useruuid: 'u-uuid',
            batchNumber: 1,
            totalBatches: 1,
            requestInfo: { userInfo: { uuid: 'u-uuid' } },
            mappings: Array.from({ length: 5 }, (_, i) => ({
                type: 'resource',
                status: mappingStatuses.toBeMapped,
                boundaryCode: 'B-1',
                uniqueIdentifierForData: `pvar-${i}`,
                campaignNumber: 'CMP-1',
            })),
        });

        expect(createProjectResourceMock).toHaveBeenCalledTimes(5);
        expect(maxInFlight).toBeLessThanOrEqual(2);
    });
});

describe('mappingBatchHandler — site link worker update (toBeUpdated)', () => {
    const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;
    const row = { campaignNumber: 'CMP-1', type: 'facility', uniqueIdentifierForData: 'site-1', boundaryCode: 'B1', status: mappingStatuses.toBeUpdated, mappingId: 'PF-1' };
    const message = (mappings: any[]) => ({ tenantId: 'tn', campaignId: 'cmp-id-1', campaignNumber: 'CMP-1', useruuid: 'u-uuid', batchNumber: 1, totalBatches: 1,
        requestInfo: { userInfo: { uuid: 'u-uuid' } }, mappings });
    const siteData = [{ uniqueIdentifier: 'site-1', uniqueIdAfterProcess: 'F-1', data: { HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: 'site-1',
        HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_NAME: 'New Worker', HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_WORKER_PHONE: 9876500011 } }];
    const link = (fields: any[]) => ({ id: 'PF-1', projectId: 'PRJ-1', facilityId: 'F-1', rowVersion: 1, additionalFields: { schema: 'OutsideHouseholdAssignment', version: 1, fields } });
    const newFields = [{ key: 'frontlineWorkerName', value: 'New Worker' }, { key: 'frontlineWorkerPhone', value: '9876500011' }];
    let updates: any[];
    function wire(search: () => any, update?: () => any) {
        updates = [];
        httpMock.mockImplementation(async (url: string, body: any) => {
            if (url.endsWith(config.paths.projectFacilitySearch)) return search();
            if (url.endsWith(config.paths.projectFacilityUpdate)) { updates.push(body.ProjectFacility); return update ? update() : {}; }
            return {};
        });
    }
    const persisted = () => produceModifiedMessagesMock.mock.calls.filter(([, topic]) => topic === 'update-mapping').flatMap(([msg]: any[]) => msg.datas);
    const lastErrorSql = () => executeQueryMock.mock.calls.filter(([sql]) => String(sql).includes('lastError'));

    beforeEach(() => {
        jest.clearAllMocks();
        getCurrentMappingGenerationMock.mockResolvedValue(null);
        executeQueryMock.mockResolvedValue({ rows: [], rowCount: 0 } as any);
        getRelatedDataWithCampaignMock.mockImplementation(async (type: any) => (type === 'facility' ? siteData : type === 'boundary' ? [{ uniqueIdentifier: 'B1', uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        jest.spyOn(global, 'setTimeout').mockImplementation(((cb: any) => { cb(); return 0 as any; }) as any);
    });
    afterEach(() => { (global.setTimeout as any).mockRestore?.(); });

    it('writes the campaign worker onto the link read by its stored id and marks the row mapped', async () => {
        wire(() => ({ ProjectFacilities: [link([{ key: 'frontlineWorkerName', value: 'Old' }])] }));
        await handleMappingBatch(message([{ ...row }]));
        expect(httpMock.mock.calls.find(([url]) => url.endsWith(config.paths.projectFacilitySearch))?.[1]).toMatchObject({ ProjectFacility: { id: ['PF-1'] } });
        expect(updates).toEqual([expect.objectContaining({ id: 'PF-1', additionalFields: { schema: 'OutsideHouseholdAssignment', version: 1, fields: newFields } })]);
        expect(persisted()).toEqual([expect.objectContaining({ mappingId: 'PF-1', status: mappingStatuses.mapped })]);
        expect(lastErrorSql()).toHaveLength(0);
    });

    it('sends no update when the link already has the worker, and marks the row mapped', async () => {
        wire(() => ({ ProjectFacilities: [link(newFields)] }));
        await handleMappingBatch(message([{ ...row }]));
        expect(updates).toHaveLength(0);
        expect(persisted()).toEqual([expect.objectContaining({ mappingId: 'PF-1', status: mappingStatuses.mapped })]);
    });

    it('marks the row updateFailed with the reason when the update still fails', async () => {
        wire(() => ({ ProjectFacilities: [link([])] }), () => { throw new Error('project service down'); });
        await handleMappingBatch(message([{ ...row }]));
        expect(persisted()).toEqual([expect.objectContaining({ mappingId: 'PF-1', status: mappingStatuses.updateFailed })]);
        expect(lastErrorSql()).toHaveLength(1);
        expect(lastErrorSql()[0][1]).toContain('Frontline worker update failed: project service down');
        expect(sendCampaignFailureMessageMock).not.toHaveBeenCalled();
    });

    it('marks the row updateFailed when the links cannot be read', async () => {
        wire(() => { throw new Error('search down'); });
        await handleMappingBatch(message([{ ...row }]));
        expect(updates).toHaveLength(0);
        expect(persisted()).toEqual([expect.objectContaining({ mappingId: 'PF-1', status: mappingStatuses.updateFailed })]);
        expect(lastErrorSql()[0][1]).toContain('Could not read the site links: search down');
    });

    it('marks the row failed without its link id when its link is gone, so the reconciler creates the link again', async () => {
        wire(() => ({ ProjectFacilities: [] }));
        await handleMappingBatch(message([{ ...row }]));
        expect(updates).toHaveLength(0);
        expect(persisted()).toEqual([expect.objectContaining({ uniqueIdentifierForData: 'site-1', mappingId: null, status: mappingStatuses.failed })]);
        expect(lastErrorSql()[0][1]).toContain('Site link PF-1 no longer exists; it will be created again with the frontline worker');
    });

    it('marks the row failed without its link id when health-project says the link no longer exists', async () => {
        wire(() => ({ ProjectFacilities: [link([])] }), () => ({ Errors: [{ code: 'NON_EXISTENT_ENTITY', message: '[long dump]' }] }));
        await handleMappingBatch(message([{ ...row }]));
        expect(updates).toHaveLength(1);
        expect(persisted()).toEqual([expect.objectContaining({ mappingId: null, status: mappingStatuses.failed })]);
    });

    it('marks a failure another try cannot change terminal, sends it once, and keeps updating the other links', async () => {
        const rows = ['PF-1', 'PF-2'].map((id, i) => ({ ...row, uniqueIdentifierForData: `site-${i + 1}`, mappingId: id }));
        getRelatedDataWithCampaignMock.mockImplementation(async (type: any) => (type === 'facility'
            ? rows.map((r, i) => ({ uniqueIdentifier: r.uniqueIdentifierForData, uniqueIdAfterProcess: `F-${i + 1}`, data: { ...siteData[0].data, HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: r.uniqueIdentifierForData } }))
            : type === 'boundary' ? [{ uniqueIdentifier: 'B1', uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        const sent: string[] = [];
        httpMock.mockImplementation(async (url: string, body: any) => {
            if (url.endsWith(config.paths.projectFacilitySearch)) return { ProjectFacilities: rows.map((r, i) => ({ ...link([]), id: r.mappingId, facilityId: `F-${i + 1}` })) } as any;
            sent.push(body.ProjectFacility.id);
            return (body.ProjectFacility.id === 'PF-1' ? { Errors: [{ code: 'DUPLICATE_ENTITY', message: '[long dump]' }] } : {}) as any;
        });
        await handleMappingBatch(message(rows.map(r => ({ ...r }))));
        expect(sent.sort()).toEqual(['PF-1', 'PF-2']);
        expect(persisted().map((d: any) => [d.mappingId, d.status]).sort()).toEqual([['PF-1', 'updateFailed'], ['PF-2', 'mapped']]);
        const params = lastErrorSql()[0][1];
        expect(params).toEqual(expect.arrayContaining([
            'Frontline worker update failed: DUPLICATE_ENTITY: another link has the same project and site; remove the extra link, then upload the file again',
            true, config.mapping.maxRetries]));
    });

    it('writes the reasons before the statuses, so the reconciler never resets a row before it is terminal', async () => {
        wire(() => ({ ProjectFacilities: [link([])] }), () => ({ Errors: [{ code: 'DUPLICATE_ENTITY' }] }));
        const order: string[] = [];
        executeQueryMock.mockImplementation((async (sql: any) => { if (String(sql).includes('lastError')) order.push('reason'); return { rows: [], rowCount: 0 }; }) as any);
        produceModifiedMessagesMock.mockImplementationOnce((async (_msg: any, topic: any) => { if (topic === 'update-mapping') order.push('status'); }) as any);
        await handleMappingBatch(message([{ ...row }]));
        expect(order).toEqual(['reason', 'status']);
    });

    it('updates at most the configured number of links at a time and keeps going after a failure', async () => {
        const rows = ['PF-1', 'PF-2', 'PF-3'].map((id, i) => ({ ...row, uniqueIdentifierForData: `site-${i + 1}`, mappingId: id }));
        getRelatedDataWithCampaignMock.mockImplementation(async (type: any) => (type === 'facility'
            ? rows.map((r, i) => ({ uniqueIdentifier: r.uniqueIdentifierForData, uniqueIdAfterProcess: `F-${i + 1}`, data: { ...siteData[0].data, HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID: r.uniqueIdentifierForData } }))
            : type === 'boundary' ? [{ uniqueIdentifier: 'B1', uniqueIdAfterProcess: 'PRJ-1' }] : []) as any);
        let inFlight = 0, maxInFlight = 0;
        const updated: string[] = [];
        httpMock.mockImplementation(async (url: string, body: any) => {
            if (url.endsWith(config.paths.projectFacilitySearch)) return { ProjectFacilities: rows.map((r, i) => ({ ...link([]), id: r.mappingId, facilityId: `F-${i + 1}` })) } as any;
            inFlight++; maxInFlight = Math.max(maxInFlight, inFlight);
            await Promise.resolve(); await Promise.resolve();
            inFlight--;
            if (body.ProjectFacility.id === 'PF-1') throw new Error('one link failed');
            updated.push(body.ProjectFacility.id); return {} as any;
        });
        await handleMappingBatch(message(rows.map(r => ({ ...r }))));
        expect(updated.sort()).toEqual(['PF-2']);
        expect(maxInFlight).toBe(2);
        expect(persisted().map((d: any) => [d.mappingId, d.status]).sort()).toEqual([['PF-1', 'updateFailed'], ['PF-2', 'mapped'], ['PF-3', 'updateFailed']]);
        expect(lastErrorSql()[0][1]).toEqual(expect.arrayContaining(['Frontline worker update not attempted after an earlier failure']));
    });

    it('leaves the retries to the reconciler: no httpRequest retry on the link search and update', async () => {
        wire(() => ({ ProjectFacilities: [link([])] }));
        await handleMappingBatch(message([{ ...row }]));
        const search = httpMock.mock.calls.find(([url]) => url.endsWith(config.paths.projectFacilitySearch))!;
        const update = httpMock.mock.calls.find(([url]) => url.endsWith(config.paths.projectFacilityUpdate))!;
        expect(search[7]).toBeUndefined();
        expect(update[7]).toBe(false);
    });

    it('marks a row with no link id mapped (there is no link to write) without reading any link', async () => {
        wire(() => ({ ProjectFacilities: [] }));
        await handleMappingBatch(message([{ ...row, mappingId: null }]));
        expect(httpMock.mock.calls.filter(([url]) => url.endsWith(config.paths.projectFacilitySearch) || url.endsWith(config.paths.projectFacilityUpdate))).toHaveLength(0);
        expect(persisted()).toEqual([expect.objectContaining({ uniqueIdentifierForData: 'site-1', status: mappingStatuses.mapped })]);
    });

    it('writes the status of every row that shares a link id', async () => {
        wire(() => ({ ProjectFacilities: [link([])] }));
        await handleMappingBatch(message([{ ...row }, { ...row, boundaryCode: 'B2' }]));
        expect(updates).toHaveLength(1);
        expect(persisted().map((d: any) => [d.boundaryCode, d.status]).sort()).toEqual([['B1', 'mapped'], ['B2', 'mapped']]);
    });
});
