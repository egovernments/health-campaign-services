/**
 * Tests for markAdoptedUserRecordCompleted in userBatchHandler.ts
 * Fix #1: a user row whose phone already exists in HRMS ("adopted") must be driven to a
 * terminal COMPLETED state — row status = completed AND sheet #status# = EXISTING — so a retry
 * of a partially-created campaign converges (pendingRows → 0) instead of leaving rows 'pending'.
 */

// ── Mocks (must be before imports) ──────────────────────────────────────────
jest.mock('../config', () => ({ default: { host: {}, paths: {}, kafka: {} }, __esModule: true }));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/db/index', () => ({
    executeQuery: jest.fn(),
    getTableName: jest.fn((t: string) => t),
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn(),
}));

jest.mock('../utils/campaignFailureHandler', () => ({
    sendCampaignFailureMessage: jest.fn(),
}));

jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn(),
}));

jest.mock('../utils/cryptUtils', () => ({
    encrypt: jest.fn((v: string) => `enc(${v})`),
}));

import { markAdoptedUserRecordCompleted, CampaignRecord } from '../utils/userBatchHandler';
import { dataRowStatuses, sheetDataRowStatuses, campaignDataRowFields, userCredentialFields } from '../config/constants';

function makeRecord(overrides: Partial<CampaignRecord> = {}): CampaignRecord {
    return {
        status: dataRowStatuses.pending,
        data: { HCM_ADMIN_CONSOLE_USER_NAME: 'Test User', HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER: '7000000001' },
        ...overrides,
    } as CampaignRecord;
}

describe('markAdoptedUserRecordCompleted', () => {
    describe('terminal status', () => {
        it('sets row status to completed', () => {
            const rec = makeRecord();
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-1');
            expect(rec.status).toBe(dataRowStatuses.completed);
        });

        it('sets sheet #status# to EXISTING (symmetric with the created path)', () => {
            const rec = makeRecord();
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-1');
            expect(rec.data[campaignDataRowFields.status]).toBe(sheetDataRowStatuses.EXISTING);
        });

        it('records the adopted serviceUuid on both the data column and uniqueIdAfterProcess', () => {
            const rec = makeRecord();
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-1');
            expect(rec.data[userCredentialFields.userServiceUuids]).toBe('svc-uuid-1');
            expect(rec.uniqueIdAfterProcess).toBe('svc-uuid-1');
        });
    });

    describe('idempotency & preservation', () => {
        it('promotes a previously-failed (retry) row to completed', () => {
            const rec = makeRecord({ status: dataRowStatuses.failed });
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-2');
            expect(rec.status).toBe(dataRowStatuses.completed);
            expect(rec.data[campaignDataRowFields.status]).toBe(sheetDataRowStatuses.EXISTING);
        });

        it('is idempotent under redelivery — reapplying yields the same terminal state', () => {
            const rec = makeRecord();
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-3');
            const first = JSON.parse(JSON.stringify(rec));
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-3');
            expect(rec).toEqual(first);
        });

        it('preserves unrelated data fields (name/phone not dropped)', () => {
            const rec = makeRecord();
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-4');
            expect(rec.data.HCM_ADMIN_CONSOLE_USER_NAME).toBe('Test User');
            expect(rec.data.HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER).toBe('7000000001');
        });
    });
});
