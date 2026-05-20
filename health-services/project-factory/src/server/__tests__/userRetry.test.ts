/**
 * Retry behavior tests for handleUserBatch.
 *
 * Covers:
 *  - same-phone-same-name: pre-check absorbs the row; HRMS create is not called.
 *  - same-phone-different-name: row is absorbed AND a warning is logged, errorDetails
 *    captures the discrepancy, HRMS user is kept as source of truth.
 *  - previously-failed row: on retry, HRMS pre-check returns the existing user → row
 *    transitions from failed → completed without a new HRMS create attempt.
 */
import { handleUserBatch } from '../utils/userBatchHandler';
import { dataRowStatuses, sheetDataRowStatuses, campaignDataRowFields, campaignDataRetryFields, errorCodes, userDataFields, userCredentialFields } from '../config/constants';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn(),
}));

jest.mock('../utils/campaignFailureHandler', () => ({
    sendCampaignFailureMessage: jest.fn(),
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn(),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
}));

jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn().mockImplementation(() => ({ transform: jest.fn().mockResolvedValue([]) })),
}));

jest.mock('../config/transformConfigs', () => ({
    transformConfigs: { employeeHrmsUnified: { metadata: {} } },
}));

jest.mock('../utils/cryptUtils', () => ({
    encrypt: (v: string) => v,
}));

jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn().mockResolvedValue({ individualIdToWorkerIdMap: new Map(), errors: [] }),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: { KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet' },
        host: { hrmsHost: 'http://hrms/', healthIndividualHost: 'http://individual/' },
        paths: { hrmsEmployeeCreate: 'create', healthIndividualSearch: 'individual/search' },
    },
}));

import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { produceModifiedMessages } from '../kafka/Producer';
import { httpRequest } from '../utils/request';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;

const PHONE = '+91-1234567890';
const TENANT = 'tn';

function buildCampaign() {
    return { CampaignDetails: [{ id: 'cmp-id-1', status: 'inprogress', hierarchyType: 'HIE' }] };
}

function buildMsg(rowOverrides: any = {}, rowStatus: string = dataRowStatuses.pending) {
    return {
        tenantId: TENANT,
        campaignNumber: 'CMP-1',
        campaignId: 'cmp-id-1',
        useruuid: 'u',
        batchNumber: 1,
        totalBatches: 1,
        requestInfo: { userInfo: { uuid: 'u' } },
        userData: {
            [PHONE]: {
                status: rowStatus,
                data: {
                    'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': PHONE,
                    [userDataFields.name]: 'Ada Lovelace',
                    ...rowOverrides,
                },
                uniqueIdentifier: PHONE,
                type: 'user',
            },
        },
    };
}

describe('handleUserBatch — retry idempotency + mismatch detection', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue(buildCampaign() as any);
        produceMock.mockResolvedValue(undefined);
    });

    it('absorbs a pending row when HRMS already has the same phone + same name (no HRMS create)', async () => {
        httpMock.mockResolvedValueOnce({
            Individual: [
                {
                    mobileNumber: PHONE,
                    userUuid: 'svc-uuid-1',
                    id: 'ind-1',
                    name: { givenName: 'Ada', familyName: 'Lovelace' },
                },
            ],
        });

        const msg: any = buildMsg();
        await handleUserBatch(msg);

        // Pre-check ran (search Individual), HRMS create was NOT called
        const hrmsCreateCalls = httpMock.mock.calls.filter(c => String(c[0]).includes(buildMsg().tenantId) ? false : String(c[0]).includes('create'));
        expect(hrmsCreateCalls.length).toBe(0);

        const row = msg.userData[PHONE];
        expect(row.status).toBe(dataRowStatuses.completed);
        expect(row.uniqueIdAfterProcess).toBe('svc-uuid-1');
        expect(row.data[userCredentialFields.userServiceUuids]).toBe('svc-uuid-1');
        // No mismatch warning was emitted
        const mismatchWarn = loggerMock.warn.mock.calls.find(c => String(c[0]).includes(errorCodes.hrmsPhoneReusedDifferentUser));
        expect(mismatchWarn).toBeUndefined();
        // No mismatch error captured on the row
        expect(row.data[campaignDataRowFields.errorDetails]).toBeUndefined();
    });

    it('absorbs a row when HRMS has the phone under a different name, logs warning, captures discrepancy', async () => {
        httpMock.mockResolvedValueOnce({
            Individual: [
                {
                    mobileNumber: PHONE,
                    userUuid: 'svc-uuid-1',
                    id: 'ind-1',
                    name: { givenName: 'Grace', familyName: 'Hopper' },
                },
            ],
        });

        const msg: any = buildMsg();
        await handleUserBatch(msg);

        const row = msg.userData[PHONE];
        expect(row.status).toBe(dataRowStatuses.completed);
        expect(row.uniqueIdAfterProcess).toBe('svc-uuid-1');

        // Mismatch warning logged with the right code
        const mismatchWarn = loggerMock.warn.mock.calls.find(c => String(c[0]).includes(errorCodes.hrmsPhoneReusedDifferentUser));
        expect(mismatchWarn).toBeDefined();

        // Row carries the discrepancy in errorDetails so operators see it in the credential sheet
        expect(row.data[campaignDataRowFields.errorDetails]).toContain(errorCodes.hrmsPhoneReusedDifferentUser);
        expect(row.data[campaignDataRowFields.errorDetails]).toContain('Grace Hopper');
        expect(row.data[campaignDataRowFields.errorDetails]).toContain('Ada Lovelace');
    });

    it('transitions a previously-failed row to completed on retry when HRMS already has the user', async () => {
        httpMock.mockResolvedValueOnce({
            Individual: [
                {
                    mobileNumber: PHONE,
                    userUuid: 'svc-uuid-1',
                    id: 'ind-1',
                    name: { givenName: 'Ada', familyName: 'Lovelace' },
                },
            ],
        });

        const msg: any = buildMsg({
            [campaignDataRowFields.status]: sheetDataRowStatuses.FAILED,
            [campaignDataRowFields.errorDetails]: 'prior HRMS timeout',
            [campaignDataRetryFields.errorHistory]: [{ attemptedAt: 1, error: 'prior HRMS timeout' }],
        }, dataRowStatuses.failed);

        await handleUserBatch(msg);

        const row = msg.userData[PHONE];
        expect(row.status).toBe(dataRowStatuses.completed);
        expect(row.uniqueIdAfterProcess).toBe('svc-uuid-1');
        // Error history from prior attempt is preserved (not cleared on absorb)
        expect(Array.isArray(row.data[campaignDataRetryFields.errorHistory])).toBe(true);
        expect(row.data[campaignDataRetryFields.errorHistory].length).toBeGreaterThan(0);
    });
});
