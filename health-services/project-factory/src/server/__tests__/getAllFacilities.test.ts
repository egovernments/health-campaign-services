import { getAllFacilities } from '../api/campaignApis';
import { httpRequest } from '../utils/request';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn(),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/redisUtils', () => ({
    getCache: jest.fn().mockResolvedValue(null),
    setCache: jest.fn().mockResolvedValue(undefined),
    deleteCache: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../kafka/Producer', () => ({
    producer: { connect: jest.fn(), send: jest.fn() },
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { facilityHost: 'http://facility/' },
        paths: { facilitySearch: 'facility/v1/_search' },
        facility: { searchBatchSize: 50 },
        DB_CONFIG: {
            DB_USER: 'u', DB_HOST: 'h', DB_NAME: 'n', DB_PASSWORD: 'p', DB_PORT: '5432',
        },
    },
}));

describe('getAllFacilities', () => {
    afterEach(() => jest.clearAllMocks());

    it('keeps two facilities that share a name but have different ids (keyed by id, not name)', async () => {
        jest.mocked(httpRequest).mockResolvedValueOnce({
            Facilities: [
                { id: 'F-1', name: 'Clinic A', address: { locality: { code: 'b1' } } },
                { id: 'F-2', name: 'Clinic A', address: { locality: { code: 'b2' } } },
            ],
        });

        const result = await getAllFacilities('mz', {} as any);

        expect(result).toHaveLength(2);
        expect(result.map((f: any) => f.id).sort()).toEqual(['F-1', 'F-2']);
    });

    it('skips facilities without an id', async () => {
        jest.mocked(httpRequest).mockResolvedValueOnce({
            Facilities: [
                { id: 'F-1', name: 'Clinic A' },
                { name: 'No Id Facility' },
            ],
        });

        const result = await getAllFacilities('mz', {} as any);

        expect(result).toHaveLength(1);
        expect(result[0].id).toBe('F-1');
    });
});
