jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() }, getFormattedStringForDebug: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({
    throwError: jest.fn((_m: string, status: number, code: string, description: string) => { throw Object.assign(new Error(code), { status, code, description }); })
}));
jest.mock('../utils/resourceDetailsUtils', () => ({ searchResourceDetailsFromDB: jest.fn() }));
jest.mock('../utils/request', () => ({}));
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

import { validateEditFileIsNotParents } from '../validators/campaignValidators';
import { searchResourceDetailsFromDB } from '../utils/resourceDetailsUtils';

const searchMock = searchResourceDetailsFromDB as jest.Mock;
const UNIFIED = 'unified-console-resources';
const edit = (resources: any[] | undefined, parentId: string | null = 'PARENT-1') =>
    ({ tenantId: 'dev', id: 'CHILD-1', parentId, action: 'create', resources });
const request = (parentResources: any[] = []) => ({ body: { parentCampaign: { id: 'PARENT-1', resources: parentResources } } });
const parentRow = (filestoreid: string, status = 'completed') => ({ filestoreid, status, type: UNIFIED, isactive: true });

describe('validateEditFileIsNotParents (an edit must not be submitted with the file of the campaign it edits)', () => {
    beforeEach(() => searchMock.mockReset());

    it('does nothing for a campaign that is not an edit', async () => {
        await expect(validateEditFileIsNotParents(request(), edit([{ type: UNIFIED, filestoreId: 'F-1' }], null), 'update')).resolves.toBeUndefined();
        expect(searchMock).not.toHaveBeenCalled();
    });

    it("refuses an edit whose file is the parent's active file, with a message telling the user what to do", async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        const error = await validateEditFileIsNotParents(request(), edit([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }]), 'update').catch((e: any) => e);
        expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR' });
        expect(error.description).toContain('Download the template from this edit screen');
        expect(searchMock).toHaveBeenCalledWith({ tenantId: 'dev', campaignId: 'PARENT-1', type: [UNIFIED] });
    });

    it("refuses an edit whose file is listed on the parent campaign (type unified-console), even with no resource row", async () => {
        searchMock.mockResolvedValue([]);
        await expect(validateEditFileIsNotParents(request([{ type: 'unified-console', filestoreId: 'PARENT-FILE' }]),
            edit([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }]), 'update')).rejects.toMatchObject({ code: 'VALIDATION_ERROR' });
    });

    it('accepts an edit with a newly uploaded file', async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        await expect(validateEditFileIsNotParents(request([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }]),
            edit([{ type: UNIFIED, filestoreId: 'EDIT-FILE' }]), 'update')).resolves.toBeUndefined();
    });

    it("refuses a create of an edit with no file when the parent's completed file would be copied to it", async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        await expect(validateEditFileIsNotParents(request(), edit([]), 'create')).rejects.toMatchObject({ code: 'VALIDATION_ERROR' });
    });

    it('leaves an update with no file in the body as before (its file can be stored on its own rows)', async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE')]);
        await expect(validateEditFileIsNotParents(request(), edit(undefined), 'update')).resolves.toBeUndefined();
    });

    it('accepts a create with no file when the parent file is not completed (nothing would be copied)', async () => {
        searchMock.mockResolvedValue([parentRow('PARENT-FILE', 'failed')]);
        await expect(validateEditFileIsNotParents(request(), edit([]), 'create')).resolves.toBeUndefined();
    });

    it('leaves edits of campaigns without a unified file (the three-file campaigns) as before', async () => {
        searchMock.mockResolvedValue([]);
        await expect(validateEditFileIsNotParents(request([{ type: 'facility', filestoreId: 'F' }]),
            edit([{ type: 'facility', filestoreId: 'F' }]), 'create')).resolves.toBeUndefined();
    });
});
