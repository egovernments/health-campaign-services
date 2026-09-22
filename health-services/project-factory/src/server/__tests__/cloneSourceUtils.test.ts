jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));

import { resolveCloneSourceCampaign, unifiedSheetOf } from '../utils/cloneSourceUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';

const mockSearch = jest.mocked(searchProjectTypeCampaignService);
const src = { id: 'p-uuid', campaignNumber: 'CMP-P', resources: [{ type: 'unified-console-resources', filestoreId: 'f1' }] };

describe('resolveCloneSourceCampaign — the one lookup shared by create-prep, launch validation and borrow', () => {
    afterEach(() => jest.clearAllMocks());

    it('prefers the id when both keys are present', async () => {
        mockSearch.mockResolvedValue({ CampaignDetails: [src] } as any);
        expect(await resolveCloneSourceCampaign('dev', 'CMP-P', 'p-uuid')).toEqual(src);
        expect(mockSearch).toHaveBeenCalledWith({ tenantId: 'dev', ids: ['p-uuid'] });
    });

    it('falls back to the campaign number when only cloneFrom is known', async () => {
        mockSearch.mockResolvedValue({ CampaignDetails: [src] } as any);
        expect(await resolveCloneSourceCampaign('dev', 'CMP-P', undefined)).toEqual(src);
        expect(mockSearch).toHaveBeenCalledWith({ tenantId: 'dev', campaignNumber: 'CMP-P' });
    });

    it('returns null with no lineage and makes no search', async () => {
        expect(await resolveCloneSourceCampaign('dev', undefined, undefined)).toBeNull();
        expect(mockSearch).not.toHaveBeenCalled();
    });

    it('returns null when nothing resolves or the search throws', async () => {
        mockSearch.mockResolvedValueOnce({ CampaignDetails: [] } as any);
        expect(await resolveCloneSourceCampaign('dev', 'CMP-P', 'p-uuid')).toBeNull();
        mockSearch.mockRejectedValueOnce(new Error('boom'));
        expect(await resolveCloneSourceCampaign('dev', 'CMP-P', 'p-uuid')).toBeNull();
    });

    it('unifiedSheetOf picks the unified workbook and ignores other resource types', () => {
        expect(unifiedSheetOf(src)).toEqual(src.resources[0]);
        expect(unifiedSheetOf({ resources: [{ type: 'attendanceRegister', filestoreId: 'a' }] })).toBeNull();
        expect(unifiedSheetOf(null)).toBeNull();
    });
});
