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
        },
        DB_CONFIG: { DB_CAMPAIGN_MAPPING_DATA_TABLE_NAME: 'eg_cm_campaign_mapping_data' },
    },
}));

import { getRelatedDataWithCampaign } from '../utils/genericUtils';
import { createProjectFacility, createProjectResource, createStaff, searchProjectResourcesByProjects, searchProjectFacilitiesByProjects, searchProjectStaffByProjects } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import { sendCampaignFailureMessage } from '../utils/campaignFailureHandler';
import { getCurrentMappingGeneration } from '../utils/mappingGenerationUtils';
import { executeQuery } from '../utils/db';

const getRelatedDataWithCampaignMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const createStaffMock = createStaff as jest.MockedFunction<typeof createStaff>;
const createProjectFacilityMock = createProjectFacility as jest.MockedFunction<typeof createProjectFacility>;
const createProjectResourceMock = createProjectResource as jest.MockedFunction<typeof createProjectResource>;
const searchProjectResourcesByProjectsMock = searchProjectResourcesByProjects as jest.MockedFunction<typeof searchProjectResourcesByProjects>;
const searchProjectFacilitiesByProjectsMock = searchProjectFacilitiesByProjects as jest.MockedFunction<typeof searchProjectFacilitiesByProjects>;
const searchProjectStaffByProjectsMock = searchProjectStaffByProjects as jest.MockedFunction<typeof searchProjectStaffByProjects>;
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
        const { httpRequest } = require('../utils/request');
        (httpRequest as jest.Mock).mockRejectedValueOnce(new Error('staff search exploded'));

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
