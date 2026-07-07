/**
 * When the bulk HRMS create fails, the per-user fallback must run in bounded
 * concurrency windows (config.user.hrmsFallbackConcurrency) rather than firing
 * the whole batch at once — the unbounded Promise.allSettled previously caused a
 * thundering herd that exhausted the downstream DB connection pool.
 */
import { handleUserBatch } from '../utils/userBatchHandler';
import { dataRowStatuses, userDataFields } from '../config/constants';

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../config/transformConfigs', () => ({ transformConfigs: { employeeHrmsUnified: { metadata: {} } } }));
jest.mock('../utils/cryptUtils', () => ({ encrypt: (v: string) => v }));
jest.mock('../utils/workerRegistryUtils', () => ({
    createOrUpdateWorkers: jest.fn().mockResolvedValue({ individualIdToWorkerIdMap: new Map(), errors: [] }),
}));

const PHONES = ['700001', '700002', '700003', '700004', '700005', '700006'];
jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn().mockImplementation(() => ({
        transform: jest.fn().mockResolvedValue(
            ['700001', '700002', '700003', '700004', '700005', '700006'].map(p => ({
                user: { mobileNumber: p, userName: `u${p}`, password: 'p' },
            }))
        ),
    })),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: { KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet' },
        host: { hrmsHost: 'http://hrms/', healthIndividualHost: 'http://individual/' },
        paths: { hrmsEmployeeCreate: 'create', healthIndividualSearch: 'individual/search' },
        user: {
            individualSearchBatchSize: 50, hrmsFallbackConcurrency: 2,
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
    PHONES.forEach(p => {
        userData[p] = {
            status: dataRowStatuses.pending,
            data: { 'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': p, [userDataFields.name]: `User ${p}` },
            uniqueIdentifier: p, type: 'user',
        };
    });
    return {
        tenantId: 'tn', campaignNumber: 'CMP-1', campaignId: 'c1', useruuid: 'u',
        batchNumber: 1, totalBatches: 1, requestInfo: { userInfo: { uuid: 'u' } }, userData,
    };
}

describe('createUsersViaHrmsApi per-user fallback — bounded concurrency', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [{ id: 'c1', status: 'inprogress', hierarchyType: 'H' }] } as any);
        produceMock.mockResolvedValue(undefined);
    });

    it('never exceeds hrmsFallbackConcurrency concurrent per-user creates, and creates all users', async () => {
        let inFlight = 0, maxInFlight = 0, perUserCalls = 0;
        httpMock.mockImplementation(async (url: any, body: any) => {
            const u = String(url);
            if (u.includes('create')) {
                const emps = body?.Employees || [];
                if (emps.length > 1) throw new Error('bulk failed'); // force fallback
                // per-user path — track concurrency
                perUserCalls++;
                inFlight++; maxInFlight = Math.max(maxInFlight, inFlight);
                await new Promise(r => setImmediate(r));
                inFlight--;
                const p = emps[0].user.mobileNumber;
                return { Employees: [{ user: { mobileNumber: p, userServiceUuid: `svc-${p}`, uuid: `ind-${p}` } }] };
            }
            // individual search: pre-check (empty) and consistency poll (found)
            const ind = body?.Individual || {};
            if (ind.id) return { Individual: (ind.id as string[]).map(id => ({ id })) };
            return { Individual: [] };
        });

        const msg: any = buildMsg();
        await handleUserBatch(msg);

        expect(perUserCalls).toBe(6);
        expect(maxInFlight).toBeLessThanOrEqual(2);
        expect(maxInFlight).toBeGreaterThan(0);
        // all six rows created successfully via fallback
        for (const p of PHONES) {
            expect(msg.userData[p].status).toBe(dataRowStatuses.completed);
            expect(msg.userData[p].uniqueIdAfterProcess).toBe(`svc-${p}`);
        }
    });
});
