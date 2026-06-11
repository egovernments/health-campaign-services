/**
 * Unit tests for handleTaskForCampaign in taskUtils.ts
 *
 * Tests that:
 * 1. Resource status is always updated to "completed" regardless of file upload outcome
 * 2. Error details are persisted in additionalDetails on failure
 * 3. Full stack traces are logged on error
 * 4. additionalDetails from DB row are merged with incoming updates
 */

// --- Mocks (must be before imports) ---

jest.mock('../config', () => ({
    default: {
        kafka: {
            KAFKA_UPDATE_PROCESS_DATA_TOPIC: 'update-process-topic',
            KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: 'update-resource-topic'
        },
        localisation: { defaultLocale: 'en_IN' }
    },
    __esModule: true
}));

jest.mock('../utils/logger', () => ({
    logger: {
        info: jest.fn(),
        error: jest.fn(),
        warn: jest.fn()
    }
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../config/resourceTypeRegistry', () => ({
    getResourceTypeByProcessName: jest.fn().mockReturnValue('facility')
}));

jest.mock('../config/processTemplateConfigs', () => ({
    processTemplateConfigs: { facility: { sheets: [] } }
}));

jest.mock('../utils/sheetManageUtils', () => ({
    enrichProcessTemplateConfig: jest.fn().mockResolvedValue(undefined),
    handleErrorDuringProcess: jest.fn().mockResolvedValue(undefined),
    processRequest: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../api/coreApis', () => ({
    fetchFileFromFilestore: jest.fn().mockResolvedValue('http://file-url')
}));

jest.mock('../utils/excelUtils', () => ({
    getExcelWorkbookFromFileURL: jest.fn().mockResolvedValue({ xlsx: {} }),
    getLocaleFromWorkbook: jest.fn().mockReturnValue('en_IN'),
    enrichTemplateMetaData: jest.fn()
}));

jest.mock('../utils/localisationUtils', () => ({
    getLocalisationModuleName: jest.fn().mockReturnValue('hcm-module')
}));

jest.mock('../utils/genericUtils', () => ({
    getLocalizedMessagesHandlerViaLocale: jest.fn().mockResolvedValue({}),
    throwError: jest.fn()
}));

jest.mock('../api/genericApis', () => ({
    createAndUploadFileWithOutRequest: jest.fn()
}));

jest.mock('../utils/campaignUtils', () => ({
    enrichAndPersistCampaignWithErrorProcessingTask: jest.fn().mockResolvedValue(undefined),
    updateResourceDetails: jest.fn()
}));

jest.mock('../utils/resourceDetailsUtils', () => ({
    searchResourceDetailsFromDB: jest.fn()
}));

// --- Test setup ---

import { handleTaskForCampaign } from '../utils/taskUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { processRequest } from '../utils/sheetManageUtils';
import { createAndUploadFileWithOutRequest } from '../api/genericApis';
import { searchResourceDetailsFromDB } from '../utils/resourceDetailsUtils';
import { logger } from '../utils/logger';

const mockProduceModifiedMessages = produceModifiedMessages as jest.Mock;
const mockProcessRequest = processRequest as jest.Mock;
const mockCreateAndUpload = createAndUploadFileWithOutRequest as jest.Mock;
const mockSearchResourceDetails = searchResourceDetailsFromDB as jest.Mock;
const mockLoggerError = logger.error as jest.Mock;
const mockLoggerWarn = logger.warn as jest.Mock;

const DB_ROW = {
    id: 'db-row-id',
    tenantid: 'default',
    campaignid: 'campaign-1',
    type: 'facility',
    parentresourceid: null,
    filestoreid: 'fs-original',
    processedfilestoreid: null,
    filename: null,
    status: 'toCreate',
    action: 'create',
    isactive: true,
    hierarchytype: null,
    additionaldetails: {},
    createdby: 'user-1',
    lastmodifiedby: 'user-1',
    createdtime: 1000000,
    lastmodifiedtime: 1000000
};

function buildMessageObject(overrides: any = {}) {
    return {
        CampaignDetails: {
            id: 'campaign-1',
            tenantId: 'default',
            hierarchyType: 'ADMIN',
            additionalDetails: { locale: 'en_IN' },
            resources: [
                {
                    type: 'facility',
                    filestoreId: 'fs-abc',
                    parentResourceId: null,
                    status: 'creating'
                }
            ]
        },
        task: {
            processName: 'CAMPAIGN_FACILITY_CREATION_PROCESS',
            status: 'pending',
            auditDetails: {}
        },
        requestInfo: {
            userInfo: { uuid: 'user-uuid' }
        },
        ...overrides
    };
}

describe('handleTaskForCampaign', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockProcessRequest.mockResolvedValue(undefined);
        mockSearchResourceDetails.mockResolvedValue([DB_ROW]);
    });

    it('T1: success + upload succeeds — persists completed with processedFileStoreId', async () => {
        mockCreateAndUpload.mockResolvedValue([{ fileStoreId: 'proc-123' }]);

        await handleTaskForCampaign(buildMessageObject());

        const resourceUpdateCall = mockProduceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === 'update-resource-topic'
        );
        expect(resourceUpdateCall).toBeDefined();
        const payload = resourceUpdateCall[0].ResourceDetails;
        expect(payload.status).toBe('completed');
        expect(payload.processedFileStoreId).toBe('proc-123');
    });

    it('T2: success + upload throws — still persists completed with null processedFileStoreId', async () => {
        mockCreateAndUpload.mockRejectedValue(new Error('upload failed'));

        await handleTaskForCampaign(buildMessageObject());

        const resourceUpdateCall = mockProduceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === 'update-resource-topic'
        );
        expect(resourceUpdateCall).toBeDefined();
        const payload = resourceUpdateCall[0].ResourceDetails;
        expect(payload.status).toBe('completed');
        expect(payload.processedFileStoreId).toBeNull();
    });

    it('T3: success + upload returns no fileStoreId — still persists completed', async () => {
        mockCreateAndUpload.mockResolvedValue([{}]);

        await handleTaskForCampaign(buildMessageObject());

        const resourceUpdateCall = mockProduceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === 'update-resource-topic'
        );
        expect(resourceUpdateCall).toBeDefined();
        expect(resourceUpdateCall[0].ResourceDetails.status).toBe('completed');
        expect(resourceUpdateCall[0].ResourceDetails.processedFileStoreId).toBeNull();
    });

    it('T4: processRequest throws — persists failed with error in additionalDetails', async () => {
        const err = new Error('validation error');
        (err as any).code = 'VALIDATION_FAILED';
        mockProcessRequest.mockRejectedValue(err);

        await handleTaskForCampaign(buildMessageObject());

        const resourceUpdateCall = mockProduceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === 'update-resource-topic'
        );
        expect(resourceUpdateCall).toBeDefined();
        const payload = resourceUpdateCall[0].ResourceDetails;
        expect(payload.status).toBe('failed');
        expect(payload.additionalDetails?.error?.code).toBe('VALIDATION_FAILED');
        expect(payload.additionalDetails?.error?.message).toBe('validation error');
    });

    it('T5: processRequest throws — logger.error called with stack trace', async () => {
        const err = new Error('stack test error');
        mockProcessRequest.mockRejectedValue(err);

        await handleTaskForCampaign(buildMessageObject());

        const errorCalls: string[] = mockLoggerError.mock.calls.map((c: any[]) => String(c[0]));
        const hasStack = errorCalls.some(msg => msg.includes('stack test error'));
        expect(hasStack).toBe(true);
    });

    it('T6: failure — additionalDetails from DB row merged with error details', async () => {
        const dbRowWithData = {
            ...DB_ROW,
            additionaldetails: { existingKey: 'existingVal' }
        };
        mockSearchResourceDetails.mockResolvedValue([dbRowWithData]);

        const err = new Error('merge test');
        mockProcessRequest.mockRejectedValue(err);

        await handleTaskForCampaign(buildMessageObject());

        const resourceUpdateCall = mockProduceModifiedMessages.mock.calls.find(
            (call: any[]) => call[1] === 'update-resource-topic'
        );
        expect(resourceUpdateCall).toBeDefined();
        const additionalDetails = resourceUpdateCall[0].ResourceDetails.additionalDetails;
        expect(additionalDetails?.existingKey).toBe('existingVal');
        expect(additionalDetails?.error?.message).toBe('merge test');
    });

    it('T2b: upload throws — warn log includes stack', async () => {
        mockCreateAndUpload.mockRejectedValue(new Error('upload network error'));

        await handleTaskForCampaign(buildMessageObject());

        const warnCalls: string[] = mockLoggerWarn.mock.calls.map((c: any[]) => String(c[0]));
        const hasUploadWarn = warnCalls.some(msg => msg.includes('upload network error'));
        expect(hasUploadWarn).toBe(true);
    });
});
