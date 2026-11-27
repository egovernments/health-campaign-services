/**
 * The per-user HRMS fallback retries transient failures (e.g. "Failed to obtain
 * JDBC Connection") with backoff and succeeds on a later attempt, while permanent
 * errors (already exists) are not retried.
 */
import { handleUserBatch } from '../utils/userBatchHandler';
import { dataRowStatuses, userDataFields } from '../config/constants';

jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../utils/cryptUtils', () => ({ encrypt: (v: string) => v }));
jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn().mockResolvedValue({ individualIdToWorkerIdMap: new Map(), errors: [] }),
}));
jest.mock('../config/transformConfigs', () => ({ transformConfigs: { employeeHrmsUnified: { metadata: {} } } }));
jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn().mockImplementation(() => ({
        transform: jest.fn().mockResolvedValue([
            { user: { mobileNumber: '900001', userName: 'a', password: 'p' } },
            { user: { mobileNumber: '900002', userName: 'b', password: 'p' } },
        ]),
    })),
}));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: { KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet' },
        host: { hrmsHost: 'http://hrms/', healthIndividualHost: 'http://individual/' },
        paths: { hrmsEmployeeCreate: 'create', healthIndividualSearch: 'individual/search' },
        user: {
            individualSearchBatchSize: 50, hrmsFallbackConcurrency: 5,
            hrmsFallbackMaxRetries: 3, hrmsFallbackBackoffMs: 1, hrmsFallbackWindowDelayMs: 0,
            individualConsistencyPollIntervalMs: 1, individualConsistencyMaxPollAttempts: 1,
        },
    },
}));

import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { produceModifiedMessages } from '../kafka/Producer';
import { httpRequest } from '../utils/request';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;

function buildMsg() {
    const userData: Record<string, any> = {};
    for (const p of ['900001', '900002']) {
        userData[p] = { status: dataRowStatuses.pending, data: { 'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': p, [userDataFields.name]: `U ${p}` }, uniqueIdentifier: p, type: 'user' };
    }
    return { tenantId: 'tn', campaignNumber: 'CMP-1', campaignId: 'c1', useruuid: 'u', batchNumber: 1, totalBatches: 1, requestInfo: { userInfo: { uuid: 'u' } }, userData };
}

describe('per-user fallback retry/backoff', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [{ id: 'c1', status: 'inprogress', hierarchyType: 'H' }] } as any);
        produceMock.mockResolvedValue(undefined);
    });

    it('retries a transient DB error and succeeds; does not retry a permanent conflict', async () => {
        const attempts: Record<string, number> = { '900001': 0, '900002': 0 };
        httpMock.mockImplementation(async (url: any, body: any) => {
            const u = String(url);
            if (u.includes('create')) {
                const emps = body?.Employees || [];
                if (emps.length > 1) throw new Error('bulk failed'); // force fallback
                const p = emps[0].user.mobileNumber;
                attempts[p]++;
                if (p === '900001') {
                    // transient: fail twice, succeed on 3rd attempt
                    if (attempts[p] < 3) { const e: any = new Error('db'); e.response = { data: { errorDetails: [{ message: 'Failed to obtain JDBC Connection' }] } }; throw e; }
                    return { Employees: [{ user: { mobileNumber: p, userServiceUuid: `svc-${p}`, uuid: `ind-${p}` } }] };
                }
                // 900002: permanent conflict — must NOT be retried
                const e: any = new Error('conflict'); e.response = { data: { errorDetails: [{ message: 'User already exists' }] } }; throw e;
            }
            const ind = body?.Individual || {};
            if (ind.id) return { Individual: (ind.id as string[]).map(id => ({ id })) };
            return { Individual: [] };
        });

        const msg: any = buildMsg();
        await handleUserBatch(msg);

        // transient one retried to success
        expect(attempts['900001']).toBe(3);
        expect(msg.userData['900001'].status).toBe(dataRowStatuses.completed);
        expect(msg.userData['900001'].uniqueIdAfterProcess).toBe('svc-900001');
        // permanent conflict tried exactly once (no retry), stays failed
        expect(attempts['900002']).toBe(1);
        expect(msg.userData['900002'].status).toBe(dataRowStatuses.failed);
    });
});
