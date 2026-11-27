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
    validateEditFile: jest.fn(),
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
jest.mock('../utils/generateUtils', () => ({ isGenerationTriggerNeeded: jest.fn(), isOutsideHouseholdDeliveryChanged: jest.fn(), keepCloneLineage: jest.fn(() => []), dropCloneSourceDeliveryDates: jest.fn(() => 0), dropCloneSourceDeliveryRules: jest.fn(() => false), fileBeforeOutsideHouseholdChange: jest.fn(async () => null), keepFileForOutsideHouseholdChange: jest.fn(() => null), keptFile: jest.fn(() => null), bringsNewFile: jest.fn(() => false), latestUnifiedTemplate: jest.fn(), startUnifiedTemplate: jest.fn(), generationDetailsFromUpdate: jest.fn(() => ({ details: true })) }));
jest.mock('../utils/deliveryMethodUtils', () => ({ campaignTypeOffersOutsideHousehold: jest.fn() }));

import config from '../config';
import { createProjectTypeCampaignService, updateProjectTypeCampaignService } from '../service/campaignManageService';
import { dropCloneSourceFileIfOutsideHouseholdChoiceDiffers, processBasedOnAction, reattachCloneSourceFileIfChoiceAgrees, searchProjectCampaignResourcData } from '../utils/campaignUtils';
import { validateEditFile, validateProjectCampaignRequest } from '../validators/campaignValidators';
import { createResourceDetail, deactivateAllResourcesForCampaign, undoSavedResources, updateResourceDetail } from '../service/resourceDetailsService';
import { findActiveResourceByUpsertKey, searchResourceDetailsFromDB } from '../utils/resourceDetailsUtils';
import { bringsNewFile, dropCloneSourceDeliveryDates, dropCloneSourceDeliveryRules, fileBeforeOutsideHouseholdChange, generationDetailsFromUpdate, isGenerationTriggerNeeded, isOutsideHouseholdDeliveryChanged, keepCloneLineage, keptFile, latestUnifiedTemplate, startUnifiedTemplate } from '../utils/generateUtils';
import { campaignTypeOffersOutsideHousehold } from '../utils/deliveryMethodUtils';

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
    (validateEditFile as jest.Mock).mockResolvedValue(undefined);
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

describe('the file of an edit is checked right after the request is validated, before anything is saved', () => {
    it('update: runs the edit file check after the request check and before files are stored or the campaign processed', async () => {
        const order: string[] = [];
        (validateProjectCampaignRequest as jest.Mock).mockImplementation(async (r: any) => { order.push('validate request'); r.body.ExistingCampaignDetails = existing; });
        (validateEditFile as jest.Mock).mockImplementation(async () => { order.push('check edit file'); });
        (createResourceDetail as jest.Mock).mockImplementation(async () => { order.push('store file'); });
        (processBasedOnAction as jest.Mock).mockImplementation(async () => { order.push('process'); });
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: false });
        const update = request([{ type: UNIFIED, filestoreId: 'OWN' }]);

        await updateProjectTypeCampaignService(update);

        expect(validateEditFile).toHaveBeenCalledWith(update, 'update');
        expect(order).toEqual(['validate request', 'check edit file', 'store file', 'process']);
    });

    it('update: a refused edit file stores, deactivates and processes nothing', async () => {
        (validateEditFile as jest.Mock).mockRejectedValueOnce(Object.assign(new Error('VALIDATION_ERROR'), { status: 400 }));
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: true });

        await expect(updateProjectTypeCampaignService(request([{ type: UNIFIED, filestoreId: 'PARENT-FILE' }]))).rejects.toThrow('VALIDATION_ERROR');

        expect(createResourceDetail).not.toHaveBeenCalled();
        expect(updateResourceDetail).not.toHaveBeenCalled();
        expect(deactivateAllResourcesForCampaign).not.toHaveBeenCalled();
        expect(processBasedOnAction).not.toHaveBeenCalled();
    });

    it('create: runs the edit file check after the request check and before the campaign is processed', async () => {
        const create = request([{ type: UNIFIED, filestoreId: 'OWN' }]);

        await createProjectTypeCampaignService(create);

        expect(validateEditFile).toHaveBeenCalledWith(create, 'create');
        expect((validateProjectCampaignRequest as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((validateEditFile as jest.Mock).mock.invocationCallOrder[0]);
        expect((validateEditFile as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
    });
});

describe('createProjectTypeCampaignService', () => {
    it('checks a clone against its source before the campaign is saved', async () => {
        const create = request([{ type: UNIFIED, filestoreId: 'SOURCE' }]);

        await createProjectTypeCampaignService(create);

        expect(dropCloneSourceFileIfOutsideHouseholdChoiceDiffers).toHaveBeenCalledWith(create.body.CampaignDetails);
        expect((dropCloneSourceFileIfOutsideHouseholdChoiceDiffers as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
        expect(dropCloneSourceDeliveryDates).toHaveBeenCalledWith(create.body.CampaignDetails, false);
        expect((dropCloneSourceDeliveryDates as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
        expect(dropCloneSourceDeliveryRules).toHaveBeenCalledWith(create.body.CampaignDetails, false);
        expect((dropCloneSourceDeliveryRules as jest.Mock).mock.invocationCallOrder[0])
            .toBeLessThan((processBasedOnAction as jest.Mock).mock.invocationCallOrder[0]);
    });

    it('reads MDMS only for a new clone, once, and passes the answer to both delivery steps', async () => {
        jest.mocked(campaignTypeOffersOutsideHousehold).mockClear();
        const notClone = request([]);
        await createProjectTypeCampaignService(notClone);
        expect(campaignTypeOffersOutsideHousehold).not.toHaveBeenCalled();

        jest.mocked(campaignTypeOffersOutsideHousehold).mockResolvedValueOnce(true);
        const clone = request([]);
        clone.body.CampaignDetails = { ...clone.body.CampaignDetails, projectType: 'SMC', additionalDetails: { cloneFrom: 'CMP-SRC' } };
        await createProjectTypeCampaignService(clone);
        expect(campaignTypeOffersOutsideHousehold).toHaveBeenCalledTimes(1);
        expect(campaignTypeOffersOutsideHousehold).toHaveBeenCalledWith('tn', 'SMC');
        expect(dropCloneSourceDeliveryDates).toHaveBeenLastCalledWith(clone.body.CampaignDetails, true);
        expect(dropCloneSourceDeliveryRules).toHaveBeenLastCalledWith(clone.body.CampaignDetails, true);
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

describe('updateProjectTypeCampaignService refreshes an edit draft\'s old template', () => {
    const MINUTE = 60 * 1000;
    const flush = () => new Promise((resolve) => setImmediate(resolve));
    const save = (overrides: Record<string, unknown> = {}) => ({
        body: {
            RequestInfo: { userInfo: { uuid: 'u1' } },
            CampaignDetails: { id: 'e1', tenantId: 'tn', hierarchyType: 'H', parentId: 'c1', action: 'draft', resources: [],
                boundaries: [{ code: 'B1' }], additionalDetails: { isUnifiedCampaign: true }, ...overrides },
        },
    }) as any;
    const record = (id: string, state: 'inProgress' | 'completed' | 'failed', startedAt: number, finishedAt = startedAt) =>
        ({ state, id, startedAt, finishedAt }) as any;
    const oldTemplate = () => record('G0', 'completed', Date.now() - 20 * MINUTE, Date.now() - 10 * MINUTE);
    const templateFinished = (agoMs: number, state: 'completed' | 'failed' = 'completed') =>
        jest.mocked(latestUnifiedTemplate).mockResolvedValue(record('G0', state, Date.now() - agoMs - 1000, Date.now() - agoMs));

    beforeEach(() => {
        (config as any).mapping.refreshEditDraftTemplateAfterMs = 2 * MINUTE;
        (config as any).mapping.refreshEditDraftTemplateWatchIntervalMs = 0;
        (config as any).mapping.refreshEditDraftTemplateWatchMaxMs = 10 * MINUTE;
        (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: false });
        (isOutsideHouseholdDeliveryChanged as jest.Mock).mockReturnValue(false);
        (keptFile as jest.Mock).mockReturnValue(null);
        (bringsNewFile as jest.Mock).mockReturnValue(false);
        jest.mocked(startUnifiedTemplate).mockResolvedValue({ id: 'G1', startedAt: Date.now() } as any);
    });

    describe('when a new template is started', () => {
        it('starts one when the draft\'s template finished longer ago than the limit', async () => {
            templateFinished(3 * 24 * 60 * MINUTE);
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(latestUnifiedTemplate).toHaveBeenCalledWith({ userInfo: { uuid: 'u1' } }, 'tn', 'e1');
            expect(generationDetailsFromUpdate).toHaveBeenCalledWith(expect.objectContaining({ id: 'e1' }), null, null);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
            expect(jest.mocked(startUnifiedTemplate).mock.calls[0][0].CampaignDetails.id).toBe('e1');
            expect(jest.mocked(startUnifiedTemplate).mock.calls[0][1]).toEqual({ details: true });
        });

        it('sends the kept file and the earlier files, so the rows copied after an Outside Household change stay', async () => {
            templateFinished(10 * MINUTE);
            (keptFile as jest.Mock).mockReturnValue('KEPT');
            (searchResourceDetailsFromDB as jest.Mock).mockResolvedValue([{ filestoreid: 'KEPT', createdtime: 3 }, { filestoreid: 'F1', createdtime: 2 }]);
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(generationDetailsFromUpdate).toHaveBeenCalledWith(expect.objectContaining({ id: 'e1' }), 'KEPT', ['F1']);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('starts one when the draft has none', async () => {
            jest.mocked(latestUnifiedTemplate).mockResolvedValue({ state: 'none' });
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('starts one when the draft\'s newest template failed, however recently', async () => {
            templateFinished(5 * 1000, 'failed');
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('reads the edit link and the unified flag from the stored campaign when the save leaves them out', async () => {
            templateFinished(10 * MINUTE);
            (validateProjectCampaignRequest as jest.Mock).mockImplementation(async (r: any) => {
                r.body.ExistingCampaignDetails = { ...existing, id: 'e1', parentId: 'c1', additionalDetails: { isUnifiedCampaign: true } };
            });
            await updateProjectTypeCampaignService(save({ parentId: undefined, additionalDetails: {} }));
            await flush();
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });
    });

    describe('when the template is left as it is', () => {
        it('keeps a template that finished within the limit (the edit was just opened), as before', async () => {
            templateFinished(30 * 1000);
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(startUnifiedTemplate).not.toHaveBeenCalled();
        });

        it('does not start another while a template is being made', async () => {
            jest.mocked(latestUnifiedTemplate).mockResolvedValue(record('G0', 'inProgress', Date.now() - 20 * MINUTE));
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(startUnifiedTemplate).not.toHaveBeenCalled();
        });

        it('leaves the upload step\'s save (it brings the file just uploaded) as it is', async () => {
            templateFinished(10 * MINUTE);
            (bringsNewFile as jest.Mock).mockReturnValue(true);
            await updateProjectTypeCampaignService(save({ resources: [{ type: UNIFIED, filestoreId: 'NEW' }] }));
            await flush();
            expect(bringsNewFile).toHaveBeenCalledWith(existing, expect.objectContaining({ id: 'e1' }));
            expect(latestUnifiedTemplate).not.toHaveBeenCalled();
        });

        it('leaves a save that starts a template itself (boundaries, type or Outside Household changed) to that path', async () => {
            (isGenerationTriggerNeeded as jest.Mock).mockReturnValue({ trigger: true });
            templateFinished(10 * MINUTE);
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(latestUnifiedTemplate).not.toHaveBeenCalled();
        });

        it.each([
            ['a new campaign draft', { parentId: null }],
            ['a clone draft', { parentId: null, additionalDetails: { isUnifiedCampaign: true, cloneFrom: 'S1' } }],
            ['the edit\'s final create', { action: 'create' }],
            ['a campaign that is not unified', { additionalDetails: {} }],
            ['a draft without boundaries', { boundaries: [] }],
        ])('leaves %s as it is', async (_name, overrides) => {
            templateFinished(10 * MINUTE);
            await updateProjectTypeCampaignService(save(overrides));
            await flush();
            expect(latestUnifiedTemplate).not.toHaveBeenCalled();
        });

        it('keeps the old behaviour when the switch is 0', async () => {
            (config as any).mapping.refreshEditDraftTemplateAfterMs = 0;
            templateFinished(10 * MINUTE);
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(latestUnifiedTemplate).not.toHaveBeenCalled();
        });
    });

    describe('failures', () => {
        it('still saves, and starts no template, when the template search fails', async () => {
            jest.mocked(latestUnifiedTemplate).mockRejectedValue(new Error('excel-ingestion down'));
            await expect(updateProjectTypeCampaignService(save())).resolves.toBeDefined();
            await flush();
            expect(startUnifiedTemplate).not.toHaveBeenCalled();
        });

        it('still saves when the new template cannot be started', async () => {
            templateFinished(10 * MINUTE);
            jest.mocked(startUnifiedTemplate).mockRejectedValue(new Error('excel-ingestion down'));
            await expect(updateProjectTypeCampaignService(save())).resolves.toBeDefined();
            await flush();
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('does not make the save wait for the template', async () => {
            jest.mocked(latestUnifiedTemplate).mockReturnValue(new Promise(() => undefined));
            await expect(updateProjectTypeCampaignService(save())).resolves.toBeDefined();
            expect(startUnifiedTemplate).not.toHaveBeenCalled();
        });

        it('does not refresh when the save itself fails', async () => {
            templateFinished(10 * MINUTE);
            (processBasedOnAction as jest.Mock).mockRejectedValue(new Error('persist failed'));
            await expect(updateProjectTypeCampaignService(save())).rejects.toThrow('persist failed');
            await flush();
            expect(latestUnifiedTemplate).not.toHaveBeenCalled();
        });
    });

    describe('watching the template it started (starting it expired the one that worked)', () => {
        const STARTED_AT = 1_000_000;
        beforeEach(() => {
            jest.useFakeTimers({ doNotFake: ['setImmediate'] });
            (config as any).mapping.refreshEditDraftTemplateWatchIntervalMs = 10 * 1000;
            jest.mocked(startUnifiedTemplate).mockResolvedValue({ id: 'G1', startedAt: STARTED_AT } as any);
        });
        afterEach(() => jest.useRealTimers());

        const startRefresh = async (...watched: any[]) => {
            const mock = jest.mocked(latestUnifiedTemplate).mockResolvedValueOnce(oldTemplate());
            watched.forEach((answer) => (answer instanceof Error ? mock.mockRejectedValueOnce(answer) : mock.mockResolvedValueOnce(answer)));
            await updateProjectTypeCampaignService(save());
            await flush();
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        };

        it('starts it once more, with the same details, when it fails', async () => {
            await startRefresh(record('G1', 'inProgress', STARTED_AT), record('G1', 'failed', STARTED_AT, STARTED_AT + 5));
            await jest.advanceTimersByTimeAsync(20 * 1000);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(2);
            expect(jest.mocked(startUnifiedTemplate).mock.calls[1][1]).toEqual({ details: true });
            await jest.advanceTimersByTimeAsync(60 * 1000);
            expect(latestUnifiedTemplate).toHaveBeenCalledTimes(3);
        });

        it('stops watching once it completed', async () => {
            await startRefresh(record('G1', 'completed', STARTED_AT, STARTED_AT + 5));
            await jest.advanceTimersByTimeAsync(60 * 1000);
            expect(latestUnifiedTemplate).toHaveBeenCalledTimes(2);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('stops, and does not retry, once a later save started a newer template (even one that failed)', async () => {
            await startRefresh(record('G2', 'failed', STARTED_AT + 50, STARTED_AT + 60));
            await jest.advanceTimersByTimeAsync(60 * 1000);
            expect(latestUnifiedTemplate).toHaveBeenCalledTimes(2);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('keeps waiting while its own template is not visible yet (an older record or none)', async () => {
            await startRefresh({ state: 'none' }, record('G0', 'completed', STARTED_AT - 50), record('G1', 'failed', STARTED_AT, STARTED_AT + 5));
            await jest.advanceTimersByTimeAsync(30 * 1000);
            expect(latestUnifiedTemplate).toHaveBeenCalledTimes(4);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(2);
        });

        it('gives up after the longest wait without starting another', async () => {
            jest.mocked(latestUnifiedTemplate).mockResolvedValue(record('G1', 'inProgress', STARTED_AT));
            await startRefresh();
            await jest.advanceTimersByTimeAsync(20 * MINUTE);
            expect(latestUnifiedTemplate).toHaveBeenCalledTimes(1 + 60);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });

        it('stops quietly when a watch search fails', async () => {
            await startRefresh(new Error('excel-ingestion down'));
            await jest.advanceTimersByTimeAsync(60 * 1000);
            expect(latestUnifiedTemplate).toHaveBeenCalledTimes(2);
            expect(startUnifiedTemplate).toHaveBeenCalledTimes(1);
        });
    });
});
