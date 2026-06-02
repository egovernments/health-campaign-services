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
        mapping: { kafkaBatchSize: 30, persistBatchSize: 100 },
    },
}));

import { getRelatedDataWithCampaign } from '../utils/genericUtils';
import { createProjectFacility, createStaff } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import { sendCampaignFailureMessage } from '../utils/campaignFailureHandler';

const getRelatedDataWithCampaignMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const createStaffMock = createStaff as jest.MockedFunction<typeof createStaff>;
const createProjectFacilityMock = createProjectFacility as jest.MockedFunction<typeof createProjectFacility>;
const produceModifiedMessagesMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const sendCampaignFailureMessageMock = sendCampaignFailureMessage as jest.MockedFunction<typeof sendCampaignFailureMessage>;

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
