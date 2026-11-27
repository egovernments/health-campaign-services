const mockConfig = { isEnvironmentCentralInstance: true, DB_CONFIG: { DB_SCHEMA: 'health' } };
jest.mock('../config', () => ({ __esModule: true, default: mockConfig }));
jest.mock('../config/dbPoolConfig', () => ({ __esModule: true, default: { query: jest.fn() } }));
jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../utils/genericUtils', () => ({
    throwError: jest.fn((module: string, status: number, code: string) => {
        throw Object.assign(new Error(code), { status, code });
    }),
}));

import { getTableName } from '../utils/db';

describe('getTableName', () => {
    afterEach(() => {
        mockConfig.isEnvironmentCentralInstance = true;
        jest.clearAllMocks();
    });

    describe('central instance', () => {
        it('uses a state-level tenantId as the schema', () => {
            expect(getTableName('eg_cm_campaign_details', 'mz')).toBe('mz.eg_cm_campaign_details');
        });

        it('uses the first part of a city-level tenantId as the schema', () => {
            expect(getTableName('eg_cm_campaign_details', 'ng.kaduna')).toBe('ng.eg_cm_campaign_details');
        });

        it('rejects a tenantId that is not a valid schema name', () => {
            expect(() => getTableName('eg_cm_campaign_details', '(SELECT pg_sleep(10)) x CROSS JOIN mz'))
                .toThrow('VALIDATION_ERROR');
        });

        it('rejects a city-level tenantId whose schema part is invalid', () => {
            expect(() => getTableName('eg_cm_campaign_details', 'mz;--.kaduna')).toThrow('VALIDATION_ERROR');
        });

        it('rejects an empty tenantId', () => {
            expect(() => getTableName('eg_cm_campaign_details', '')).toThrow('VALIDATION_ERROR');
        });
    });

    describe('non-central instance', () => {
        it('ignores tenantId and uses the configured schema', () => {
            mockConfig.isEnvironmentCentralInstance = false;
            expect(getTableName('eg_cm_campaign_details', 'anything at all')).toBe('health.eg_cm_campaign_details');
        });
    });
});
