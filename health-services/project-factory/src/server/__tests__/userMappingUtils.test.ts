import { startUserMapping, startUserDemapping } from '../utils/userMappingUtils';
import { mappingStatuses } from '../config/constants';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../utils/genericUtils', () => ({
    getMappingDataRelatedToCampaign: jest.fn(),
    getRelatedDataWithCampaign: jest.fn(),
    throwError: jest.fn(),
}));

jest.mock('../api/genericApis', () => ({
    createStaff: jest.fn(),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
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
    },
}));

import {
    getMappingDataRelatedToCampaign,
    getRelatedDataWithCampaign,
} from '../utils/genericUtils';
import { createStaff } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';

const getMappingDataMock = getMappingDataRelatedToCampaign as jest.MockedFunction<typeof getMappingDataRelatedToCampaign>;
const getRelatedDataMock = getRelatedDataWithCampaign as jest.MockedFunction<typeof getRelatedDataWithCampaign>;
const createStaffMock = createStaff as jest.MockedFunction<typeof createStaff>;
const produceModifiedMessagesMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;

describe('startUserMapping (legacy path) — non-blocking on user-only failures', () => {
    const campaignDetails = {
        campaignNumber: 'CMP-1',
        tenantId: 'tn',
        startDate: 0,
        endDate: 0,
    };
    const requestInfo: any = { userInfo: { uuid: 'u' } };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('marks mapping skipped when user has no uniqueIdAfterProcess', async () => {
        getMappingDataMock.mockResolvedValueOnce([
            {
                uniqueIdentifierForData: '+91-1',
                boundaryCode: 'B-1',
                status: mappingStatuses.toBeMapped,
            },
        ] as any);
        getRelatedDataMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([
                    { uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' },
                ] as any);
            if (type === 'user')
                return Promise.resolve([
                    { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: null },
                ] as any);
            return Promise.resolve([] as any);
        });

        await startUserMapping(campaignDetails, 'u', requestInfo);

        expect(createStaffMock).not.toHaveBeenCalled();
        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas));
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.skipped);
    });

    it('does not throw when staff API fails for one user — continues with the rest', async () => {
        getMappingDataMock.mockResolvedValueOnce([
            { uniqueIdentifierForData: '+91-1', boundaryCode: 'B-1', status: mappingStatuses.toBeMapped },
            { uniqueIdentifierForData: '+91-2', boundaryCode: 'B-1', status: mappingStatuses.toBeMapped },
        ] as any);
        getRelatedDataMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([
                    { uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' },
                ] as any);
            if (type === 'user')
                return Promise.resolve([
                    { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usvc-1' },
                    { uniqueIdentifier: '+91-2', uniqueIdAfterProcess: 'usvc-2' },
                ] as any);
            return Promise.resolve([] as any);
        });

        // First call fails, second succeeds
        createStaffMock
            .mockRejectedValueOnce(new Error('hrms staff blew up'))
            .mockResolvedValueOnce({ ProjectStaff: { id: 'ps-2' } } as any);

        await expect(startUserMapping(campaignDetails, 'u', requestInfo)).resolves.toBeUndefined();

        // Both mappings should be persisted (one failed, one mapped)
        const persistedRecords = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .filter((arg: any) => Array.isArray(arg?.datas))
            .flatMap((arg: any) => arg.datas);

        const statuses = persistedRecords.map((r: any) => r.status).sort();
        expect(statuses).toEqual([mappingStatuses.failed, mappingStatuses.mapped].sort());
    });

    it('marks a failed demap as deMapFailed so the reconciler retries it in the demap direction', async () => {
        getMappingDataMock.mockResolvedValueOnce([
            { uniqueIdentifierForData: '+91-1', boundaryCode: 'B-1', status: mappingStatuses.toBeDeMapped, mappingId: 'ps-1' },
        ] as any);
        getRelatedDataMock.mockImplementation((type: string) => {
            if (type === 'boundary')
                return Promise.resolve([
                    { uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' },
                ] as any);
            if (type === 'user')
                return Promise.resolve([
                    { uniqueIdentifier: '+91-1', uniqueIdAfterProcess: 'usvc-1' },
                ] as any);
            return Promise.resolve([] as any);
        });
        const { httpRequest } = require('../utils/request');
        (httpRequest as jest.Mock).mockRejectedValueOnce(new Error('staff search exploded'));

        await expect(startUserDemapping(campaignDetails, 'u', requestInfo)).resolves.toBeUndefined();

        const persisted = produceModifiedMessagesMock.mock.calls
            .map(c => c[0])
            .find((arg: any) => Array.isArray(arg?.datas));
        expect((persisted as any).datas[0].status).toBe(mappingStatuses.deMapFailed);
    });
});
