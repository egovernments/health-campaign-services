jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../utils/campaignUtils', () => ({
    dropCloneSourceFileIfOutsideHouseholdChoiceDiffers: jest.fn(),
    reattachCloneSourceFileIfChoiceAgrees: jest.fn(),
    prepareAndProduceCancelMessage: jest.fn(),
    processBasedOnAction: jest.fn(),
    processFetchMicroPlan: jest.fn(),
    searchProjectCampaignResourcData: jest.fn(),
    updateCampaignAfterSearch: jest.fn(),
    validateAndFetchCampaign: jest.fn(),
}));
jest.mock('../validators/campaignValidators', () => ({
    validateMicroplanRequest: jest.fn(),
    validateProjectCampaignRequest: jest.fn(),
    validateAddResourcesRequest: jest.fn(),
}));
jest.mock('../service/resourceDetailsService', () => ({
    createResourceDetail: jest.fn(),
    updateResourceDetail: jest.fn(),
    getCampaignStatusFromDB: jest.fn(),
    deactivateAllResourcesForCampaign: jest.fn(),
}));
jest.mock('../utils/resourceDetailsUtils', () => ({ findActiveResourceByUpsertKey: jest.fn(), searchResourceDetailsFromDB: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({ prepareProcessesForResourceTypes: jest.fn(), getCurrentProcesses: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../config/resourceTypeRegistry', () => ({ getRegistryEntry: jest.fn() }));
jest.mock('../utils/generateUtils', () => ({ isGenerationTriggerNeeded: jest.fn(), isOutsideHouseholdDeliveryChanged: jest.fn(), keepCloneLineage: jest.fn(() => []), dropCloneSourceDeliveryDates: jest.fn(() => 0), fileBeforeOutsideHouseholdChange: jest.fn(() => null), keepFileForOutsideHouseholdChange: jest.fn(() => null) }));

import config from '../config';
import { createProjectTypeCampaignService, updateProjectTypeCampaignService } from '../service/campaignManageService';
import { dropCloneSourceFileIfOutsideHouseholdChoiceDiffers, processBasedOnAction, reattachCloneSourceFileIfChoiceAgrees, searchProjectCampaignResourcData } from '../utils/campaignUtils';
import { validateProjectCampaignRequest } from '../validators/campaignValidators';
import { createResourceDetail, deactivateAllResourcesForCampaign, updateResourceDetail } from '../service/resourceDetailsService';
import { findActiveResourceByUpsertKey, searchResourceDetailsFromDB } from '../utils/resourceDetailsUtils';
import { dropCloneSourceDeliveryDates, fileBeforeOutsideHouseholdChange, isGenerationTriggerNeeded, isOutsideHouseholdDeliveryChanged, keepCloneLineage } from '../utils/generateUtils';

const UNIFIED = 'unified-console-resources';
const existing = { id: 'c1', tenantId: 'tn', hierarchyType: 'H', resources: [{ type: UNIFIED, filestoreId: 'OLD' }] };
const request = (resources: any[]) => ({
    body: { RequestInfo: { userInfo: { uuid: 'u1' } }, CampaignDetails: { id: 'c1', tenantId: 'tn', hierarchyType: 'H', resources } },
}) as any;
const attachedFileIds = () => [
    ...(createResourceDetail as jest.Mock).mock.calls.map((c: any[]) => c[0].fileStoreId),
    ...(updateResourceDetail as jest.Mock).mock.calls.map((c: any[]) => c[0].fileStoreId),
];

let savedResources: any[] | undefined;

beforeEach(() => {
    jest.clearAllMocks();
    (config as any).mapping.detachStaleResourcesOnConfigChange = true;
    savedResources = undefined;
    (validateProjectCampaignRequest as jest.Mock).mockImplementation(async (r: any) => { r.body.ExistingCampaignDetails = existing; });
    (processBasedOnAction as jest.Mock).mockImplementation(async (r: any) => { savedResources = r.body.CampaignDetails.resources; });
    (searchResourceDetailsFromDB as jest.Mock).mockResolvedValue([{ filestoreid: 'OLD', type: UNIFIED }]);
    (findActiveResourceByUpsertKey as jest.Mock).mockResolvedValue(null);
    (searchProjectCampaignResourcData as jest.Mock).mockResolvedValue({ responseData: [{ id: 'c1' }], totalCount: 1 });
});

describe('updateProjectTypeCampaignService after the Outside Household choice changes', () => {
    beforeEach(() => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: true });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(true);
    });

    it('does not attach the old file again, neither in the resource table nor on the saved campaign', async () => {
        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OLD' }]));

        expect(deactivateAllResourcesForCampaign).toHaveBeenCalledWith('c1', 'tn', 'u1');
        expect(savedResources).toEqual([]);
        expect(attachedFileIds()).toEqual([]);
    });

    it('sends the earlier files, active or not and newest first, so the Outside Household rows can come back', async () => {
        (fileBeforeOutsideHouseholdChange as jest.Mock).mockReturnValueOnce('F2');
        (searchResourceDetailsFromDB as jest.Mock).mockImplementation(async (criteria: any) => criteria.isActive === false
            ? [{ filestoreid: 'F1', createdtime: 20 }, { filestoreid: 'F0', createdtime: 10 }]
            : [{ filestoreid: 'F2', type: UNIFIED, createdtime: 30 }, { filestoreid: 'F1', createdtime: 25 }]);
        const r = request([{ type: UNIFIED, filestoreId: 'F2' }]);

        await updateProjectTypeCampaignService(r);

        expect(r.body.fileBeforeOutsideHouseholdChange).toBe('F2');
        expect(r.body.earlierFilesBeforeOutsideHouseholdChange).toEqual(['F1', 'F0']);
        for (const isActive of [false, true]) {
            expect(searchResourceDetailsFromDB).toHaveBeenCalledWith(
                expect.objectContaining({ tenantId: 'tn', campaignId: 'c1', type: [UNIFIED], parentResourceId: null, isActive }),
                expect.objectContaining({ sortBy: 'createdtime', sortOrder: 'DESC' }));
        }
    });

    it('still keeps the file from before when the earlier files cannot be read', async () => {
        (fileBeforeOutsideHouseholdChange as jest.Mock).mockReturnValueOnce('F2');
        (searchResourceDetailsFromDB as jest.Mock).mockImplementation(async (criteria: any) => {
            if (criteria.isActive === false) throw new Error('db down');
            return [{ filestoreid: 'F2', type: UNIFIED }];
        });
        const r = request([{ type: UNIFIED, filestoreId: 'F2' }]);

        await updateProjectTypeCampaignService(r);

        expect(r.body.fileBeforeOutsideHouseholdChange).toBe('F2');
        expect(r.body.earlierFilesBeforeOutsideHouseholdChange).toEqual([]);
    });

    it('attaches a file uploaded in the same save', async () => {
        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OLD' }, { type: UNIFIED, filestoreId: 'NEW' }]));

        expect(savedResources).toEqual([{ type: UNIFIED, filestoreId: 'NEW' }]);
        expect(attachedFileIds()).toEqual(['NEW']);
    });

    it('attaches the files the save sends, as before, when the switch is off', async () => {
        (config as any).mapping.detachStaleResourcesOnConfigChange = false;

        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OLD' }]));

        expect(savedResources).toEqual([{ type: UNIFIED, filestoreId: 'OLD' }]);
        expect(attachedFileIds()).toEqual(['OLD']);
    });
});

describe('updateProjectTypeCampaignService for other saves (unchanged behaviour)', () => {
    it('a boundary or campaign type change still attaches the files the save sends', async () => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: true });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(false);

        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OLD' }]));

        expect(deactivateAllResourcesForCampaign).toHaveBeenCalled();
        expect(savedResources).toEqual([{ type: UNIFIED, filestoreId: 'OLD' }]);
        expect(attachedFileIds()).toEqual(['OLD']);
    });

    it('a save without a configuration change deactivates nothing and keeps the file', async () => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: false });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(false);

        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OLD' }]));

        expect(deactivateAllResourcesForCampaign).not.toHaveBeenCalled();
        expect(savedResources).toEqual([{ type: UNIFIED, filestoreId: 'OLD' }]);
        expect(attachedFileIds()).toEqual(['OLD']);
    });
});

describe('createProjectTypeCampaignService', () => {
    it('checks a clone against its source before the campaign is saved', async () => {
        const create = request([{ type: UNIFIED, filestoreId: 'SOURCE' }]);

        await createProjectTypeCampaignService(create);

        expect(dropCloneSourceFileIfOutsideHouseholdChoiceDiffers).toHaveBeenCalledWith(create.body.CampaignDetails);
        expect((dropCloneSourceFileIfOutsideHouseholdChoiceDiffers as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
        expect(dropCloneSourceDeliveryDates).toHaveBeenCalledWith(create.body.CampaignDetails);
        expect((dropCloneSourceDeliveryDates as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
    });
});

describe('updateProjectTypeCampaignService keeps the clone lineage', () => {
    it('passes the save and the saved campaign to keepCloneLineage before the campaign is saved', async () => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: false });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(false);
        const update = request([{ type: UNIFIED, filestoreId: 'OLD' }]);

        await updateProjectTypeCampaignService(update);

        expect(keepCloneLineage).toHaveBeenCalledWith(update.body.CampaignDetails, existing);
        expect((keepCloneLineage as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
    });
});

describe('updateProjectTypeCampaignService gives a clone its source file back', () => {
    it('attaches the file the re-attach step adds, after the old files are dropped and before the campaign is saved', async () => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: true });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(true);
        let resourcesSeenByReattach: any[] | undefined;
        (reattachCloneSourceFileIfChoiceAgrees as jest.Mock).mockImplementation(async (r: any) => {
            resourcesSeenByReattach = [...r.body.CampaignDetails.resources];
            r.body.CampaignDetails.resources = [...r.body.CampaignDetails.resources, { type: UNIFIED, filestoreId: 'SRC-FILE' }];
            return true;
        });

        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OLD' }]));

        expect(resourcesSeenByReattach).toEqual([]);   // the old file was already dropped
        expect((reattachCloneSourceFileIfChoiceAgrees as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
        expect(savedResources).toEqual([{ type: UNIFIED, filestoreId: 'SRC-FILE' }]);
        expect(attachedFileIds()).toEqual(['SRC-FILE']);
    });
});
