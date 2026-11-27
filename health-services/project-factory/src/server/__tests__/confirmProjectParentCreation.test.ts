jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: (v: any) => String(v),
}));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({
    throwError: jest.fn().mockImplementation((_mod: any, _status: any, code: any, desc: any) => {
        const e: any = new Error(desc || code);
        e.code = code;
        throw e;
    }),
}));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({}));
jest.mock('../service/dataManageService', () => ({ createDataService: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({}));
jest.mock('../utils/excelUtils', () => ({}));
jest.mock('../utils/localisationUtils', () => ({}));
jest.mock('../utils/microplanUtils', () => ({}));
jest.mock('../utils/onGoingCampaignUpdateUtils', () => ({}));
jest.mock('../validators/campaignValidators', () => ({}));
jest.mock('../validators/microplanValidators', () => ({}));
jest.mock('../models', () => ({ MDMSModels: {} }));
jest.mock('../api/coreApis', () => ({}));
jest.mock('../api/genericApis', () => ({ getCampaignNumber: jest.fn() }));
jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: { projectHost: 'http://project/' },
        paths: { projectSearch: 'project/v1/_search' },
        project: { confirmRetries: 3, confirmPollIntervalMs: 1 },
    },
}));

import { confirmProjectParentCreation } from '../api/campaignApis';
import { httpRequest } from '../utils/request';
import { throwError } from '../utils/genericUtils';

const httpMock = httpRequest as jest.MockedFunction<typeof httpRequest>;
const throwMock = throwError as jest.MockedFunction<typeof throwError>;

const found = { Project: [{ id: 'p1' }] };
const empty = { Project: [] };

describe('confirmProjectParentCreation', () => {
    afterEach(() => jest.clearAllMocks());

    it('resolves immediately when the project is searchable on the first attempt', async () => {
        httpMock.mockResolvedValueOnce(found as any);
        await confirmProjectParentCreation('mz', 'u', 'p1', {} as any);
        expect(httpMock).toHaveBeenCalledTimes(1);
        expect(throwMock).not.toHaveBeenCalled();
    });

    it('keeps polling and succeeds once the project becomes searchable', async () => {
        httpMock
            .mockResolvedValueOnce(empty as any)
            .mockResolvedValueOnce(empty as any)
            .mockResolvedValueOnce(found as any);
        await confirmProjectParentCreation('mz', 'u', 'p1', {} as any);
        expect(httpMock).toHaveBeenCalledTimes(3);
        expect(throwMock).not.toHaveBeenCalled();
    });

    it('throws PROJECT_CONFIRMATION_FAILED after exhausting config.project.confirmRetries attempts', async () => {
        httpMock.mockResolvedValue(empty as any);
        await expect(confirmProjectParentCreation('mz', 'u', 'p1', {} as any)).rejects.toThrow(/p1/);
        // one search per configured retry (confirmRetries = 3), no more
        expect(httpMock).toHaveBeenCalledTimes(3);
        expect(throwMock).toHaveBeenCalledWith('PROJECT', 500, 'PROJECT_CONFIRMATION_FAILED', expect.stringContaining('p1'));
    });

    it('does not sleep after the final failed attempt (bounded by confirmRetries)', async () => {
        httpMock.mockResolvedValue(empty as any);
        await expect(confirmProjectParentCreation('mz', 'u', 'pX', {} as any)).rejects.toBeDefined();
        // exactly confirmRetries calls — never confirmRetries + 1
        expect(httpMock).toHaveBeenCalledTimes(3);
    });
});
