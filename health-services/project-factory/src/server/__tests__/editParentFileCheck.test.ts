jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() }, getFormattedStringForDebug: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({
    throwError: jest.fn((_m: string, status: number, code: string, description: string) => { throw Object.assign(new Error(code), { status, code, description }); })
}));
jest.mock('../utils/resourceDetailsUtils', () => ({ searchResourceDetailsFromDB: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn(), defaultheader: {} }));
jest.mock('../api/campaignApis', () => ({}));
jest.mock('../utils/campaignUtils', () => ({}));
jest.mock('../api/genericApis', () => ({}));
jest.mock('../service/campaignManageService', () => ({}));
jest.mock('../utils/boundaryUtils', () => ({}));
jest.mock('../utils/targetUtils', () => ({}));
jest.mock('../utils/onGoingCampaignUpdateUtils', () => ({}));
jest.mock('../validators/microplanValidators', () => ({}));
jest.mock('../utils/microplanUtils', () => ({}));
jest.mock('../utils/campaignMappingUtils', () => ({}));
jest.mock('../api/healthApis', () => ({}));
jest.mock('../utils/excelUtils', () => ({}));
jest.mock('../utils/localisationUtils', () => ({}));
jest.mock('../api/coreApis', () => ({}));

import { validateEditFile } from '../validators/campaignValidators';
import { searchResourceDetailsFromDB } from '../utils/resourceDetailsUtils';
import { httpRequest } from '../utils/request';
import config from '../config';

const searchMock = searchResourceDetailsFromDB as jest.Mock;
const httpMock = httpRequest as jest.Mock;
const UNIFIED = 'unified-console-resources';
const request = (resources: any[] | undefined, { parentId = 'PARENT-1' as string | null, action = 'create', parentResources = [] as any[] } = {}) => ({
    body: {
        RequestInfo: { userInfo: { uuid: 'u1' } },
        CampaignDetails: { tenantId: 'dev', id: 'CHILD-1', parentId, action, resources },
        parentCampaign: { id: 'PARENT-1', resources: parentResources },
    }
});
const parentRow = (filestoreid: string, status = 'completed') => ({ filestoreid, status, type: UNIFIED, isactive: true });
const check = (fileStoreId: string, status = 'completed', validationStatus = 'valid', totalErrors = 0) =>
    ({ type: 'unified-console-validation', referenceId: 'CHILD-1', fileStoreId, status, additionalDetails: { validationStatus, totalErrors } });
const checks = (...list: any[]) => ({ ProcessingDetails: list });

describe('validateEditFile: an edit is not submitted with the file of the campaign it edits', () => {
    beforeEach(() => { searchMock.mockReset(); httpMock.mockReset(); httpMock.mockResolvedValue(checks(check('EDIT-FILE'))); });

    it('does nothing for a campaign that is not an edit, or for a draft save', async () => {
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'F-1' }], { parentId: null }), 'update')).resolves.toBeUndefined();
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }], { action: 'draft' }), 'update')).resolves.toBeUndefined();
        expect(searchMock).not.toHaveBeenCalled();
        expect(httpMock).not.toHaveBeenCalled();
    });

    it("refuses an edit whose file is the parent's active file, with a message telling the user what to do", async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        const error = await validateEditFile(request([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }]), 'update').catch((e: any) => e);
        expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });
        expect(error.description).toContain('is the file of the campaign being edited');
        expect(searchMock).toHaveBeenCalledWith({ tenantId: 'dev', campaignId: 'PARENT-1', type: [UNIFIED] });
    });

    it('refuses an edit whose file is listed on the parent campaign (type unified-console), even with no resource row', async () => {
        searchMock.mockResolvedValue([]);
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }], { parentResources: [{ type: 'unified-console', filestoreId: 'PARENT-FILE' }] }),
            'update')).rejects.toMatchObject({ code: 'VALIDATION_ERROR' });
    });

    it("refuses a create of an edit with no file when the parent's completed file would be copied to it", async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        await expect(validateEditFile(request([]), 'create')).rejects.toMatchObject({ code: 'VALIDATION_ERROR' });
    });

    it('accepts a create with no file when the parent file is not completed (nothing would be copied)', async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE', 'failed')]);
        await expect(validateEditFile(request([]), 'create')).resolves.toBeUndefined();
    });

    it('leaves an update with no file in the body as before (its file can be stored on its own rows)', async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        await expect(validateEditFile(request(undefined), 'update')).resolves.toBeUndefined();
        expect(httpMock).not.toHaveBeenCalled();
    });

    it('leaves edits of campaigns without a unified file (the three-file campaigns) as before', async () => {
        searchMock.mockResolvedValue([]);
        await expect(validateEditFile(request([{ type: 'facility', filestoreId: 'F' }], { parentResources: [{ type: 'facility', filestoreId: 'F' }] }),
            'create')).resolves.toBeUndefined();
        expect(httpMock).not.toHaveBeenCalled();
    });
});

describe('validateEditFile: the file of an edit passed its upload check for this edit', () => {
    beforeEach(() => { searchMock.mockReset(); httpMock.mockReset(); searchMock.mockResolvedValue([parentRow('PARENT-FILE')]); });

    it('accepts an edit whose new file has a completed, valid upload check with no errors for this edit', async () => {
        httpMock.mockResolvedValue(checks(check('OTHER-FILE', 'failed', 'invalid'), check('EDIT-FILE')));
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update')).resolves.toBeUndefined();
        const [url, body] = httpMock.mock.calls[0];
        expect(url).toBe(`${config.host.excelIngestionHost}${config.paths.excelIngestionProcessSearch}`);
        expect(body.ProcessingSearchCriteria).toEqual({ tenantId: 'dev', referenceIds: ['CHILD-1'], types: ['unified-console-validation'], limit: 100, offset: 0 });
    });

    it.each([
        ['the check failed (e.g. a copy of the parent sheet: IMMUTABLE_IDENTITY_MISMATCH)', check('EDIT-FILE', 'failed', undefined as any)],
        ['the check found the file invalid', check('EDIT-FILE', 'completed', 'invalid', 3)],
        ['the check is completed but not valid, with no errors counted', check('EDIT-FILE', 'completed', 'invalid', 0)],
        ['the check is valid but counted errors', check('EDIT-FILE', 'completed', 'valid', 2)],
        ['only another file was checked', check('ANOTHER-FILE')],
    ])('refuses an edit whose file has no passed check: %s', async (_label, record) => {
        httpMock.mockResolvedValue(checks(record));
        const error = await validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update').catch((e: any) => e);
        expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });
        expect(error.description).toContain('has not passed the upload check for this edit');
    });

    it.each(['pending', 'inprogress'])('tells the user to wait when the check of the file is still %s', async (status) => {
        httpMock.mockResolvedValue(checks(check('EDIT-FILE', 'failed', undefined as any), check('EDIT-FILE', status, undefined as any)));
        const error = await validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update').catch((e: any) => e);
        expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });
        expect(error.description).toContain('still running');
    });

    it('does not count a running check of another file', async () => {
        httpMock.mockResolvedValue(checks(check('ANOTHER-FILE', 'inprogress', undefined as any)));
        const error = await validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update').catch((e: any) => e);
        expect(error.description).toContain('has not passed the upload check');
    });

    it('lets the Submit go ahead, as before, when the edit has more checks than are read (10 full pages)', async () => {
        httpMock.mockResolvedValue(checks(...Array.from({ length: 100 }, (_, i) => check(`F-${i}`))));
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update')).resolves.toBeUndefined();
        expect(httpMock).toHaveBeenCalledTimes(10);
    });

    it('reads the next page when the first page is full', async () => {
        httpMock.mockResolvedValueOnce(checks(...Array.from({ length: 100 }, (_, i) => check(`F-${i}`)))).mockResolvedValueOnce(checks(check('EDIT-FILE')));
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update')).resolves.toBeUndefined();
        expect(httpMock).toHaveBeenCalledTimes(2);
        expect(httpMock.mock.calls[1][1].ProcessingSearchCriteria.offset).toBe(100);
    });

    it('lets the Submit go ahead, as before, when excel-ingestion cannot be read', async () => {
        httpMock.mockRejectedValue(new Error('connect ECONNREFUSED'));
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update')).resolves.toBeUndefined();
    });

    it('does not read upload checks on a create (the edit does not exist yet)', async () => {
        await expect(validateEditFile(request([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'create')).resolves.toBeUndefined();
        expect(httpMock).not.toHaveBeenCalled();
    });
});
