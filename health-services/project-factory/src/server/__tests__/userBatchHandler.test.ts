import { handleUserBatch } from '../utils/userBatchHandler';
import { dataRowStatuses, sheetDataRowStatuses, campaignStatuses, campaignDataRowFields } from '../config/constants';

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
        kafka: {
            KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet',
        },
        host: { hrmsHost: 'http://hrms/' },
        paths: { hrmsEmployeeCreate: 'create' },
    },
}));

import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { sendCampaignFailureMessage } from '../utils/campaignFailureHandler';
import { produceModifiedMessages } from '../kafka/Producer';

const searchCampaignMock = searchProjectTypeCampaignService as jest.MockedFunction<typeof searchProjectTypeCampaignService>;
const sendFailureMock = sendCampaignFailureMessage as jest.MockedFunction<typeof sendCampaignFailureMessage>;
const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;

describe('handleUserBatch — outer catch is non-blocking for user-side errors', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    function buildMsg(overrides: any = {}) {
        return {
            tenantId: 'tn',
            campaignNumber: 'CMP-1',
            campaignId: 'cmp-id-1',
            useruuid: 'u',
            batchNumber: 1,
            totalBatches: 1,
            requestInfo: { userInfo: { uuid: 'u' } },
            userData: {
                '+91-1': {
                    status: dataRowStatuses.pending,
                    data: { 'HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER': '+91-1' },
                    uniqueIdentifier: '+91-1',
                    type: 'user',
                },
            },
            ...overrides,
        };
    }

    it('does not call sendCampaignFailureMessage when persistence of failed rows succeeds', async () => {
        // Force the inner flow to throw before HRMS call by returning no campaign
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [] } as any);
        produceMock.mockResolvedValue(undefined);

        await handleUserBatch(buildMsg() as any);

        // Outer catch ran, marked rows as failed, persisted them, and did NOT escalate
        expect(produceMock).toHaveBeenCalled();
        expect(sendFailureMock).not.toHaveBeenCalled();
    });

    it('marks rows with FAILED status and errorDetails when HRMS-side error occurs', async () => {
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [] } as any);
        produceMock.mockResolvedValue(undefined);

        const msg: any = buildMsg();
        await handleUserBatch(msg);

        const recs = msg.userData;
        expect(recs['+91-1'].status).toBe(dataRowStatuses.failed);
        expect(recs['+91-1'].data[campaignDataRowFields.status]).toBe(sheetDataRowStatuses.FAILED);
        expect(recs['+91-1'].data[campaignDataRowFields.errorDetails]).toBeTruthy();
    });

    it('escalates to sendCampaignFailureMessage only when persistence itself fails', async () => {
        searchCampaignMock.mockResolvedValue({ CampaignDetails: [] } as any);
        produceMock.mockRejectedValueOnce(new Error('kafka down'));

        await handleUserBatch(buildMsg() as any);

        expect(sendFailureMock).toHaveBeenCalledTimes(1);
    });

    it('skips processing when campaign is already failed', async () => {
        searchCampaignMock.mockResolvedValue({
            CampaignDetails: [{ status: campaignStatuses.failed }],
        } as any);

        await handleUserBatch(buildMsg() as any);

        // No row updates published, no failure escalation
        expect(produceMock).not.toHaveBeenCalled();
        expect(sendFailureMock).not.toHaveBeenCalled();
    });
});
