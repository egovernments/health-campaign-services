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
    undoSavedResources: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../utils/resourceDetailsUtils', () => ({ findActiveResourceByUpsertKey: jest.fn(), searchResourceDetailsFromDB: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({ prepareProcessesForResourceTypes: jest.fn(), getCurrentProcesses: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn() }));
jest.mock('../config/resourceTypeRegistry', () => ({ getRegistryEntry: jest.fn() }));
jest.mock('../utils/generateUtils', () => ({ isGenerationTriggerNeeded: jest.fn(), isOutsideHouseholdDeliveryChanged: jest.fn(), keepCloneLineage: jest.fn(() => []), dropCloneSourceDeliveryDates: jest.fn(() => 0), dropCloneSourceDeliveryRules: jest.fn(() => false), fileBeforeOutsideHouseholdChange: jest.fn(() => null), keepFileForOutsideHouseholdChange: jest.fn(() => null) }));

import config from '../config';
import { createProjectTypeCampaignService, updateProjectTypeCampaignService } from '../service/campaignManageService';
import { dropCloneSourceFileIfOutsideHouseholdChoiceDiffers, processBasedOnAction, reattachCloneSourceFileIfChoiceAgrees, searchProjectCampaignResourcData } from '../utils/campaignUtils';
import { validateProjectCampaignRequest } from '../validators/campaignValidators';
import { createResourceDetail, deactivateAllResourcesForCampaign, undoSavedResources, updateResourceDetail } from '../service/resourceDetailsService';
import { findActiveResourceByUpsertKey, searchResourceDetailsFromDB } from '../utils/resourceDetailsUtils';
import { dropCloneSourceDeliveryDates, dropCloneSourceDeliveryRules, fileBeforeOutsideHouseholdChange, isGenerationTriggerNeeded, isOutsideHouseholdDeliveryChanged, keepCloneLineage } from '../utils/generateUtils';

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

describe('updateProjectTypeCampaignService stores the files of a save before processing it', () => {
    it('stores the uploaded file before a Create starts the campaign', async () => {
        const order: string[] = [];
        (createResourceDetail as jest.Mock).mockImplementation(async () => { order.push('store file'); });
        (processBasedOnAction as jest.Mock).mockImplementation(async () => { order.push('process'); });
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: false });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(false);

        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'OWN' }]));

        expect(order).toEqual(['store file', 'process']);
        expect(attachedFileIds()).toEqual(['OWN']);
    });
});

describe('updateProjectTypeCampaignService takes back the files of a save that failed', () => {
    beforeEach(() => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: false });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(false);
    });

    it('takes back a new file when the save fails before the campaign is saved', async () => {
        (createResourceDetail as jest.Mock).mockResolvedValue({ id: 'NEW-ROW' });
        (processBasedOnAction as jest.Mock).mockRejectedValue(new Error('persist failed'));
        await expect(updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'B' }]))).rejects.toThrow('persist failed');
        expect(undoSavedResources).toHaveBeenCalledWith([{ createdId: 'NEW-ROW' }], 'tn', 'u1');
    });

    it('puts the replaced file back when the save fails before the campaign is saved', async () => {
        (findActiveResourceByUpsertKey as jest.Mock).mockResolvedValue({ id: 'OLD-ROW' });
        (updateResourceDetail as jest.Mock).mockResolvedValue({ id: 'NEW-ROW' });
        (processBasedOnAction as jest.Mock).mockRejectedValue(new Error('persist failed'));
        await expect(updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'B' }]))).rejects.toThrow('persist failed');
        expect(undoSavedResources).toHaveBeenCalledWith([{ createdId: 'NEW-ROW', replacedId: 'OLD-ROW' }], 'tn', 'u1');
    });

    it('keeps the files when the save fails after the campaign was saved', async () => {
        (createResourceDetail as jest.Mock).mockResolvedValue({ id: 'NEW-ROW' });
        (processBasedOnAction as jest.Mock).mockImplementation(async (r: any) => { r.body.campaignPersisted = true; throw new Error('template start failed'); });
        await expect(updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'B' }]))).rejects.toThrow('template start failed');
        expect(undoSavedResources).not.toHaveBeenCalled();
    });

    it('keeps the files of a save that works', async () => {
        (createResourceDetail as jest.Mock).mockResolvedValue({ id: 'NEW-ROW' });
        await updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'B' }]));
        expect(undoSavedResources).not.toHaveBeenCalled();
    });

    it('keeps the files of a failed save that also changed the configuration (they were deactivated for it), as before', async () => {
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: true });
        (createResourceDetail as jest.Mock).mockResolvedValue({ id: 'NEW-ROW' });
        (processBasedOnAction as jest.Mock).mockRejectedValue(new Error('persist failed'));
        await expect(updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'B' }]))).rejects.toThrow('persist failed');
        expect(deactivateAllResourcesForCampaign).toHaveBeenCalled();
        expect(undoSavedResources).not.toHaveBeenCalled();
    });

    it('still reports the failure of the save when the files cannot be taken back', async () => {
        (createResourceDetail as jest.Mock).mockResolvedValue({ id: 'NEW-ROW' });
        (processBasedOnAction as jest.Mock).mockRejectedValue(new Error('persist failed'));
        (undoSavedResources as jest.Mock).mockRejectedValueOnce(new Error('db down'));
        await expect(updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'B' }]))).rejects.toThrow('persist failed');
    });
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
        expect(dropCloneSourceDeliveryRules).toHaveBeenCalledWith(create.body.CampaignDetails);
        expect((dropCloneSourceDeliveryRules as jest.Mock).mock.invocationCallOrder[0])
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
