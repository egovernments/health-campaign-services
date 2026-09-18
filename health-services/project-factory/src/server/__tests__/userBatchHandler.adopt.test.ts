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

import { markAdoptedUserRecordCompleted, selectReconcilableUserRows, CampaignRecord, ExistingHrmsUser } from '../utils/userBatchHandler';
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

        it('applies the existing login username when provided', () => {
            const rec = makeRecord();
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-1', 'testuser124');
            expect(rec.data[userCredentialFields.userName]).toBe('testuser124');
        });

        it('leaves the uploaded username untouched when no existing username is resolved', () => {
            const rec = makeRecord({ data: { [userCredentialFields.userName]: 'from-sheet' } } as any);
            markAdoptedUserRecordCompleted(rec, 'svc-uuid-1'); // no userName arg
            expect(rec.data[userCredentialFields.userName]).toBe('from-sheet');
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

function pendingRow(phone: string): CampaignRecord {
    return { status: dataRowStatuses.pending, uniqueIdentifier: phone, data: { x: '1' } } as CampaignRecord;
}
function existing(uuid: string, userName?: string): ExistingHrmsUser {
    return { serviceUuid: uuid, individualId: `ind-${uuid}`, existingName: 'N', userName };
}

describe('selectReconcilableUserRows', () => {
    it('reconciles only pending rows whose phone exists in HRMS', () => {
        const rows = [pendingRow('700001'), pendingRow('700002'), pendingRow('700003')];
        const map = { '700001': existing('u1'), '700003': existing('u3') }; // 700002 absent

        const result = selectReconcilableUserRows(rows, map);

        expect(result.map(r => r.uniqueIdentifier)).toEqual(['700001', '700003']);
        result.forEach(r => {
            expect(r.status).toBe(dataRowStatuses.completed);
            expect(r.data[campaignDataRowFields.status]).toBe(sheetDataRowStatuses.EXISTING);
        });
    });

    it('does not touch a row whose phone is absent from HRMS (stays pending)', () => {
        const rows = [pendingRow('700002')];
        const result = selectReconcilableUserRows(rows, {});
        expect(result).toHaveLength(0);
        expect(rows[0].status).toBe(dataRowStatuses.pending);
    });

    it('ignores a map entry with no serviceUuid', () => {
        const rows = [pendingRow('700001')];
        const result = selectReconcilableUserRows(rows, { '700001': { serviceUuid: '', individualId: '', existingName: '' } });
        expect(result).toHaveLength(0);
        expect(rows[0].status).toBe(dataRowStatuses.pending);
    });

    it('returns empty for no pending rows', () => {
        expect(selectReconcilableUserRows([], { '700001': existing('u1') })).toEqual([]);
    });

    it('stamps the adopted serviceUuid onto reconciled rows', () => {
        const rows = [pendingRow('700001')];
        const result = selectReconcilableUserRows(rows, { '700001': existing('u1') });
        expect(result[0].uniqueIdAfterProcess).toBe('u1');
        expect(result[0].data[userCredentialFields.userServiceUuids]).toBe('u1');
    });

    it('stamps the existing username onto reconciled rows when resolved', () => {
        const rows = [pendingRow('700001')];
        const result = selectReconcilableUserRows(rows, { '700001': existing('u1', 'testuser1') });
        expect(result[0].data[userCredentialFields.userName]).toBe('testuser1');
    });
});
