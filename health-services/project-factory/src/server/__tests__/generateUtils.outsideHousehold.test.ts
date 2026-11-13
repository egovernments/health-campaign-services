jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../utils/genericUtils', () => ({ getLocalizedMessagesHandler: jest.fn(), processGenerate: jest.fn() }));
jest.mock('../service/sheetManageService', () => ({ generateDataService: jest.fn() }));
jest.mock('../utils/localisationUtils', () => ({ getLocaleFromRequestInfo: jest.fn(), getLocalisationModuleName: jest.fn() }));
jest.mock('../api/genericApis', () => ({ getBoundarySheetData: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({ checkIfSourceIsMicroplan: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../config', () => ({ __esModule: true, default: { localisation: { defaultLocale: 'en_IN' }, host: {}, paths: {} } }));

import { callGenerateIfBoundariesOrCampaignTypeDiffer, generationDetailsFromUpdate, isGenerationTriggerNeeded, isOutsideHouseholdChoiceDifferentFromSource, isOutsideHouseholdDeliveryPersisted, keepCloneLineage, cloneSourceFileToReattach, dropCloneSourceDeliveryDates, dropCloneSourceDeliveryRules, fileBeforeOutsideHouseholdChange, keepFileForOutsideHouseholdChange, KEPT_FILE_KEY } from '../utils/generateUtils';
import config from '../config';
import { httpRequest } from '../utils/request';

const boundaries = [{ code: 'B1', isRoot: true, includeAllChildren: true }];
const campaign = (deliveryMethods?: unknown, extra: Record<string, unknown> = {}) => ({
    projectType: 'POLIO', boundaries,
    additionalDetails: deliveryMethods === undefined ? {} : { cycleData: { deliveryMethods } },
    ...extra,
});
const request = (existing: any, incoming: any) => ({ body: { ExistingCampaignDetails: existing, CampaignDetails: incoming } });

describe('isGenerationTriggerNeeded with the Outside Household delivery method', () => {
    describe('regenerates', () => {
        it('when the update adds OUTSIDEHOUSEHOLD', () => {
            expect(isGenerationTriggerNeeded(request(campaign(['HOUSEHOLDSTRATEGY']), campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']))).trigger).toBe(true);
        });

        it('when the saved campaign had no list yet and the update chooses OUTSIDEHOUSEHOLD', () => {
            expect(isGenerationTriggerNeeded(request(campaign(), campaign(['OUTSIDEHOUSEHOLD']))).trigger).toBe(true);
        });

        it('when the update removes OUTSIDEHOUSEHOLD', () => {
            expect(isGenerationTriggerNeeded(request(campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), campaign(['HOUSEHOLDSTRATEGY']))).trigger).toBe(true);
        });

        it('reads {code} objects and compares upper-cased without separators', () => {
            expect(isGenerationTriggerNeeded(request(campaign(['HOUSEHOLDSTRATEGY']), campaign([{ code: 'outside-household' }]))).trigger).toBe(true);
        });
    });

    describe('does not regenerate', () => {
        it('when OUTSIDEHOUSEHOLD stays, whatever else changes in the list', () => {
            expect(isGenerationTriggerNeeded(request(campaign(['OUTSIDEHOUSEHOLD']), campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']))).trigger).toBe(false);
        });

        it('when neither side holds OUTSIDEHOUSEHOLD', () => {
            expect(isGenerationTriggerNeeded(request(campaign(), campaign(['HOUSEHOLDSTRATEGY']))).trigger).toBe(false);
        });

        it('when the update carries no deliveryMethods list (partial update)', () => {
            expect(isGenerationTriggerNeeded(request(campaign(['OUTSIDEHOUSEHOLD']), campaign())).trigger).toBe(false);
        });

        it('when an entry is not a readable code', () => {
            expect(isGenerationTriggerNeeded(request(campaign([]), campaign([null, 7, { name: 'OUTSIDEHOUSEHOLD' }]))).trigger).toBe(false);
        });
    });

    describe('existing triggers unchanged', () => {
        it('still regenerates when boundaries change', () => {
            const changed = campaign(['OUTSIDEHOUSEHOLD'], { boundaries: [{ code: 'B2', isRoot: true, includeAllChildren: true }] });
            expect(isGenerationTriggerNeeded(request(campaign(['OUTSIDEHOUSEHOLD']), changed)).trigger).toBe(true);
        });

        it('still regenerates when the campaign type changes', () => {
            expect(isGenerationTriggerNeeded(request(campaign(), campaign(undefined, { projectType: 'MR-DN' }))).trigger).toBe(true);
        });
    });
});

describe('isOutsideHouseholdDeliveryPersisted (generation waits for the stored Outside Household choice)', () => {
    it('is false while the stored campaign does not have the OUTSIDEHOUSEHOLD the update adds', () => {
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), campaign(['HOUSEHOLDSTRATEGY']))).toBe(false);
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['OUTSIDEHOUSEHOLD']), campaign())).toBe(false);
    });

    it('is false while the stored campaign still has the OUTSIDEHOUSEHOLD the update removes', () => {
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['HOUSEHOLDSTRATEGY']), campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']))).toBe(false);
    });

    it('is true once the stored campaign matches the update, also as {code} objects', () => {
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), campaign([{ code: 'OUTSIDEHOUSEHOLD' }]))).toBe(true);
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['HOUSEHOLDSTRATEGY']), campaign(['HOUSEHOLDSTRATEGY']))).toBe(true);
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['HOUSEHOLDSTRATEGY']), campaign())).toBe(true);
    });

    it('is true when the update carries no deliveryMethods list, so other updates never wait', () => {
        expect(isOutsideHouseholdDeliveryPersisted(campaign(), campaign(['OUTSIDEHOUSEHOLD']))).toBe(true);
        expect(isOutsideHouseholdDeliveryPersisted({}, undefined)).toBe(true);
    });

    it('is false when the stored campaign could not be read and the update has OUTSIDEHOUSEHOLD', () => {
        expect(isOutsideHouseholdDeliveryPersisted(campaign(['OUTSIDEHOUSEHOLD']), undefined)).toBe(false);
    });
});

describe('generationDetailsFromUpdate (the update decides the Outside Household sheet, not a stored copy)', () => {
    it('sends the update\'s deliveryMethods and projectType', () => {
        expect(generationDetailsFromUpdate(campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'])))
            .toEqual({ deliveryMethods: ['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], projectType: 'POLIO' });
    });

    it('sends a list without OUTSIDEHOUSEHOLD as it is, so a removed method drops the sheet', () => {
        expect(generationDetailsFromUpdate(campaign(['HOUSEHOLDSTRATEGY']))).toEqual({ deliveryMethods: ['HOUSEHOLDSTRATEGY'], projectType: 'POLIO' });
    });

    it('leaves deliveryMethods out when the update carries no list, so excel-ingestion reads the stored campaign', () => {
        expect(generationDetailsFromUpdate(campaign())).toEqual({ projectType: 'POLIO' });
        expect(generationDetailsFromUpdate({})).toEqual({});
    });
});

describe('callGenerateIfBoundariesOrCampaignTypeDiffer for a unified campaign', () => {
    afterEach(() => jest.clearAllMocks());

    it('passes the update\'s deliveryMethods and projectType in the generate request', async () => {
        const update = { ...campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), id: 'cmp-1', tenantId: 'dev', hierarchyType: 'H',
            additionalDetails: { isUnifiedCampaign: true, cycleData: { deliveryMethods: ['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'] } } };
        await callGenerateIfBoundariesOrCampaignTypeDiffer({ body: { CampaignDetails: update, RequestInfo: {} } });
        const body: any = jest.mocked(httpRequest).mock.calls[0][1];
        expect(body.GenerateResource.referenceId).toBe('cmp-1');
        expect(body.GenerateResource.additionalDetails).toEqual({ deliveryMethods: ['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], projectType: 'POLIO' });
    }, 10000);
});

describe('isOutsideHouseholdChoiceDifferentFromSource (a clone and the campaign it was copied from)', () => {
    beforeEach(() => { (config as any).mapping = { detachStaleResourcesOnConfigChange: true }; });
    afterAll(() => { delete (config as any).mapping; });

    it('is true when the clone adds OUTSIDEHOUSEHOLD the source did not have', () => {
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), campaign(['HOUSEHOLDSTRATEGY']))).toBe(true);
    });

    it('is true when the clone drops OUTSIDEHOUSEHOLD the source had', () => {
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['HOUSEHOLDSTRATEGY']), campaign(['OUTSIDEHOUSEHOLD']))).toBe(true);
    });

    it('is false when both use it, also Outside Household only against both', () => {
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['OUTSIDEHOUSEHOLD']), campaign(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']))).toBe(false);
    });

    it('is false when neither uses it, and when a source without a list meets a clone without it', () => {
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['HOUSEHOLDSTRATEGY']), campaign(['HOUSEHOLDSTRATEGY']))).toBe(false);
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['HOUSEHOLDSTRATEGY']), campaign())).toBe(false);
    });

    it('is false when the clone carries no list, or there is no source: the clone keeps the source file as before', () => {
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(), campaign(['OUTSIDEHOUSEHOLD']))).toBe(false);
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['HOUSEHOLDSTRATEGY']), undefined)).toBe(false);
    });

    it('is false when the switch is off', () => {
        (config as any).mapping.detachStaleResourcesOnConfigChange = false;
        expect(isOutsideHouseholdChoiceDifferentFromSource(campaign(['HOUSEHOLDSTRATEGY']), campaign(['OUTSIDEHOUSEHOLD']))).toBe(false);
    });
});

describe('keepCloneLineage (the console final create sends additionalDetails without the clone keys)', () => {
    const saved = { additionalDetails: { cloneFrom: 'CMP-SRC', clonedCampaignId: 'src-id', cycleData: { deliveryMethods: ['OUTSIDEHOUSEHOLD'] } } };
    beforeEach(() => { (config as any).mapping = { keepCloneLineageOnUpdate: true }; });
    afterAll(() => { delete (config as any).mapping; });

    it('fills cloneFrom and clonedCampaignId the save left out, keeping everything else it sent', () => {
        const incoming: any = { additionalDetails: { key: 2, cycleData: { deliveryMethods: ['HOUSEHOLDSTRATEGY'] } } };
        expect(keepCloneLineage(incoming, saved)).toEqual(['cloneFrom', 'clonedCampaignId']);
        expect(incoming.additionalDetails).toEqual({ key: 2, cycleData: { deliveryMethods: ['HOUSEHOLDSTRATEGY'] }, cloneFrom: 'CMP-SRC', clonedCampaignId: 'src-id' });
    });

    it('creates additionalDetails when the save has none', () => {
        const incoming: any = {};
        keepCloneLineage(incoming, saved);
        expect(incoming.additionalDetails).toEqual({ cloneFrom: 'CMP-SRC', clonedCampaignId: 'src-id' });
    });

    it('never changes a key the save sends, and treats null or empty as left out', () => {
        const incoming: any = { additionalDetails: { cloneFrom: 'CMP-OTHER', clonedCampaignId: null } };
        expect(keepCloneLineage(incoming, saved)).toEqual(['clonedCampaignId']);
        expect(incoming.additionalDetails).toEqual({ cloneFrom: 'CMP-OTHER', clonedCampaignId: 'src-id' });
    });

    it('changes nothing for a campaign that is not a clone, or when the switch is off', () => {
        const notClone: any = { additionalDetails: { key: 2 } };
        expect(keepCloneLineage(notClone, { additionalDetails: { key: 1 } })).toEqual([]);
        expect(notClone.additionalDetails).toEqual({ key: 2 });
        (config as any).mapping.keepCloneLineageOnUpdate = false;
        const incoming: any = { additionalDetails: {} };
        expect(keepCloneLineage(incoming, saved)).toEqual([]);
        expect(incoming.additionalDetails).toEqual({});
    });
});

describe('cloneSourceFileToReattach (a clone whose Outside Household choice is back to its source\'s)', () => {
    const UNIFIED = 'unified-console-resources';
    const source = { ...campaign(['OUTSIDEHOUSEHOLD']), resources: [{ type: UNIFIED, filestoreId: 'SRC-FILE', filename: 'src.xlsx' }] };
    const clone = (deliveryMethods: unknown, extra: Record<string, unknown> = {}) =>
        ({ ...campaign(deliveryMethods), additionalDetails: { cloneFrom: 'CMP-SRC', cycleData: { deliveryMethods } }, resources: [], ...extra });
    beforeEach(() => { (config as any).mapping = { detachStaleResourcesOnConfigChange: true }; });
    afterAll(() => { delete (config as any).mapping; });

    it('gives back the source file when the save changed the choice and it agrees with the source again', () => {
        expect(cloneSourceFileToReattach(clone(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD']), source, true))
            .toEqual({ type: UNIFIED, filestoreId: 'SRC-FILE', filename: 'src.xlsx', additionalDetails: {} });
    });

    it('gives nothing when the save did not change the choice', () => {
        expect(cloneSourceFileToReattach(clone(['OUTSIDEHOUSEHOLD']), source, false)).toBeNull();
    });

    it('gives nothing while the clone still disagrees with the source', () => {
        expect(cloneSourceFileToReattach(clone(['HOUSEHOLDSTRATEGY']), source, true)).toBeNull();
    });

    it('gives nothing when the save brings a unified file of its own', () => {
        expect(cloneSourceFileToReattach(clone(['OUTSIDEHOUSEHOLD'], { resources: [{ type: UNIFIED, filestoreId: 'OWN' }] }), source, true)).toBeNull();
    });

    it('gives nothing without a readable deliveryMethods list, for an edit, or when the source has no file', () => {
        expect(cloneSourceFileToReattach({ ...clone(['OUTSIDEHOUSEHOLD']), additionalDetails: { cloneFrom: 'CMP-SRC' } }, source, true)).toBeNull();
        expect(cloneSourceFileToReattach(clone(['OUTSIDEHOUSEHOLD'], { parentId: 'p1' }), source, true)).toBeNull();
        expect(cloneSourceFileToReattach(clone(['OUTSIDEHOUSEHOLD']), { ...source, resources: [] }, true)).toBeNull();
    });

    it('gives nothing for a campaign that is not a clone, or when the switch is off', () => {
        expect(cloneSourceFileToReattach({ ...campaign(['OUTSIDEHOUSEHOLD']), resources: [] }, source, true)).toBeNull();
        (config as any).mapping.detachStaleResourcesOnConfigChange = false;
        expect(cloneSourceFileToReattach(clone(['OUTSIDEHOUSEHOLD']), source, true)).toBeNull();
    });
});

describe('dropCloneSourceDeliveryDates (a new clone does not keep its source\'s delivery dates)', () => {
    const clone = (): any => ({
        projectType: 'POLIO',
        additionalDetails: {
            cloneFrom: 'CMP-SRC', clonedCampaignId: 'src-id',
            cycleData: {
                deliveryMethods: ['OUTSIDEHOUSEHOLD'],
                cycleConfgureDate: { cycle: 1, deliveries: 1 },
                cycleData: [{ key: 1, fromDate: '2026-10-30T00:00:00.000Z', toDate: '2026-11-06T00:00:00.000Z' }],
            },
        },
        deliveryRules: [{ code: 'POLIO', cycles: [{ id: 1, startDate: 1793298600000, endDate: 1793989799000, deliveries: [{ id: 1, doseCriteria: [{ condition: '0<=ageandage<=59' }] }] }] }],
    });
    beforeEach(() => { (config as any).mapping = { clearCloneDeliveryDates: true, deliveryConditionCampaignTypes: ['POLIO'] }; });
    afterAll(() => { delete (config as any).mapping; });

    it('keeps the dates of a clone whose campaign type is not listed (POLIO only by default)', () => {
        const c = { ...clone(), projectType: 'MR-DN' };
        expect(dropCloneSourceDeliveryDates(c)).toBe(0);
        expect(c.deliveryRules[0].cycles[0].startDate).toBe(1793298600000);
        expect(dropCloneSourceDeliveryDates({ ...clone(), projectType: undefined })).toBe(0);
        expect(dropCloneSourceDeliveryDates({ ...clone(), projectType: ' polio ' })).toBe(4);
    });

    it('removes the cycle dates in cycleData and deliveryRules and keeps everything else', () => {
        const c = clone();
        expect(dropCloneSourceDeliveryDates(c)).toBe(4);
        expect(c.additionalDetails.cycleData).toEqual({ deliveryMethods: ['OUTSIDEHOUSEHOLD'], cycleConfgureDate: { cycle: 1, deliveries: 1 }, cycleData: [{ key: 1 }] });
        expect(c.deliveryRules[0].cycles[0]).toEqual({ id: 1, deliveries: [{ id: 1, doseCriteria: [{ condition: '0<=ageandage<=59' }] }] });
        expect(c.additionalDetails.cloneFrom).toBe('CMP-SRC');
    });

    it('changes nothing for a campaign that is not a clone, an edit, or when the switch is off', () => {
        const notClone = clone(); delete notClone.additionalDetails.cloneFrom; delete notClone.additionalDetails.clonedCampaignId;
        expect(dropCloneSourceDeliveryDates(notClone)).toBe(0);
        const edit = { ...clone(), parentId: 'parent-1' };
        expect(dropCloneSourceDeliveryDates(edit)).toBe(0);
        (config as any).mapping.clearCloneDeliveryDates = false;
        const off = clone();
        expect(dropCloneSourceDeliveryDates(off)).toBe(0);
        expect(off.deliveryRules[0].cycles[0].startDate).toBe(1793298600000);
    });

    it('handles a clone without cycle data or delivery rules', () => {
        expect(dropCloneSourceDeliveryDates({ additionalDetails: { cloneFrom: 'CMP-SRC' } })).toBe(0);
    });
});

describe('dropCloneSourceDeliveryRules (a new clone of another campaign type does not keep its source\'s delivery rules)', () => {
    const clone = (projectType = 'MR-DN'): any => ({
        projectType, action: 'draft',
        additionalDetails: {
            cloneFrom: 'CMP-SRC', clonedCampaignId: 'src-id', beneficiaryType: 'INDIVIDUAL',
            cycleData: {
                cycleConfgureDate: { cycle: 2, deliveries: 1 },
                cycleData: [{ key: 1, fromDate: '2026-10-30T00:00:00.000Z', toDate: '2026-11-06T00:00:00.000Z' }],
            },
        },
        deliveryRules: [{ code: 'MR-DN', cycles: [{ id: 1, startDate: 1793298600000, endDate: 1793989799000, deliveries: [{ id: 1, doseCriteria: [{ condition: '9<=ageandage<=59' }] }] }] }],
    });
    beforeEach(() => { (config as any).mapping = { clearCloneDeliveryDates: true, deliveryConditionCampaignTypes: ['POLIO'] }; });
    afterAll(() => { delete (config as any).mapping; });

    it('removes the delivery rules and cycle data of a clone whose campaign type is not listed, and keeps the rest', () => {
        const c = clone();
        expect(dropCloneSourceDeliveryRules(c)).toBe(true);
        expect(c.deliveryRules).toEqual([]);
        expect(c.additionalDetails).toEqual({ cloneFrom: 'CMP-SRC', clonedCampaignId: 'src-id', beneficiaryType: 'INDIVIDUAL', cycleData: {} });
        // a clone that came with no rules is left in the same empty state and reports nothing removed
        expect(dropCloneSourceDeliveryRules(c)).toBe(false);
        expect(c.deliveryRules).toEqual([]);
    });

    it('keeps the delivery rules of a POLIO clone (its dates are removed separately)', () => {
        const c = clone(' polio ');
        expect(dropCloneSourceDeliveryRules(c)).toBe(false);
        expect(c.deliveryRules[0].cycles[0].deliveries[0].doseCriteria[0].condition).toBe('9<=ageandage<=59');
        expect(c.additionalDetails.cycleData.cycleConfgureDate).toEqual({ cycle: 2, deliveries: 1 });
    });

    it('changes nothing for a campaign that is not a clone, an edit, a final create, a missing type, or when the switch is off', () => {
        const notClone = clone(); delete notClone.additionalDetails.cloneFrom; delete notClone.additionalDetails.clonedCampaignId;
        const edit = { ...clone(), parentId: 'parent-1' };
        const finalCreate = { ...clone(), action: 'create' };
        const noType = { ...clone(), projectType: undefined };
        for (const c of [notClone, edit, finalCreate, noType]) {
            expect(dropCloneSourceDeliveryRules(c)).toBe(false);
            expect(c.deliveryRules).toHaveLength(1);
        }
        (config as any).mapping.clearCloneDeliveryDates = false;
        const off = clone();
        expect(dropCloneSourceDeliveryRules(off)).toBe(false);
        expect(off.deliveryRules).toHaveLength(1);
    });
});

describe('fileBeforeOutsideHouseholdChange (the new template keeps the data of the file uploaded before)', () => {
    const UNIFIED = 'unified-console-resources';
    const camp = (methods: string[], codes: string[], files: string[], hierarchyType = 'H'): any => ({
        projectType: 'POLIO', hierarchyType, boundaries: codes.map(code => ({ code })), resources: files.map(f => ({ type: UNIFIED, filestoreId: f })),
        additionalDetails: { cycleData: { deliveryMethods: methods } },
    });
    const req = (existing: any, incoming: any) => ({ body: { ExistingCampaignDetails: existing, CampaignDetails: incoming } });
    beforeEach(() => { (config as any).mapping = { keepDataOnOutsideHouseholdChange: true, deliveryConditionCampaignTypes: ['POLIO'] }; });
    afterAll(() => { delete (config as any).mapping; });

    it('gives nothing for a campaign type that is not listed (POLIO only by default)', () => {
        const other = (c: any) => ({ ...c, projectType: 'MR-DN' });
        expect(fileBeforeOutsideHouseholdChange(req(other(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1'])), other(camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F1']))))).toBeNull();
    });

    it('gives the file of a campaign that drops or adds Outside Household on the same area', () => {
        expect(fileBeforeOutsideHouseholdChange(req(camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A', 'B'], ['F1']), camp(['HOUSEHOLDSTRATEGY'], ['B', 'A'], ['F1'])))).toBe('F1');
        expect(fileBeforeOutsideHouseholdChange(req(camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F1']), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], [])))).toBe('F1');
    });

    it('gives nothing when the choice did not change, the area or hierarchy changed, or the save brings a new file', () => {
        expect(fileBeforeOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1']), camp(['OUTSIDEHOUSEHOLD', 'HOUSEHOLDSTRATEGY'], ['A'], ['F1'])))).toBeNull();
        expect(fileBeforeOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1']), camp(['HOUSEHOLDSTRATEGY'], ['A', 'C'], ['F1'])))).toBeNull();
        expect(fileBeforeOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1']), camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F1'], 'H2')))).toBeNull();
        expect(fileBeforeOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1']), camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F2'])))).toBeNull();
        expect(fileBeforeOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], []), camp(['HOUSEHOLDSTRATEGY'], ['A'], [])))).toBeNull();
    });

    it('gives nothing when the switch is off', () => {
        (config as any).mapping.keepDataOnOutsideHouseholdChange = false;
        expect(fileBeforeOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1']), camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F1'])))).toBeNull();
    });

    // The save that changes the choice detaches the file; the campaign keeps its id for the next change
    const kept = (c: any, fileStoreId: string, campaignId = 'c1') => ({ ...c, id: 'c1', additionalDetails: { ...c.additionalDetails, [KEPT_FILE_KEY]: { campaignId, fileStoreId } } });

    it('gives the kept file when Outside Household is added back and no file is attached any more', () => {
        expect(fileBeforeOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], [])))).toBe('F1');
        // the console sending the detached file back is not a new file
        expect(fileBeforeOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], ['F1'])))).toBe('F1');
        // an attached file comes first
        expect(fileBeforeOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F2']), 'F1'), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], [])))).toBe('F2');
    });

    it('does not give a kept file copied from another campaign, or after the area changed', () => {
        expect(fileBeforeOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1', 'source'), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], [])))).toBeNull();
        expect(fileBeforeOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A', 'B'], [])))).toBeNull();
        expect(fileBeforeOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], ['F2'])))).toBeNull();
    });

    describe('keepFileForOutsideHouseholdChange', () => {
        const keptOn = (c: any) => c.additionalDetails?.[KEPT_FILE_KEY];

        it('saves the file the template keeps, with the campaign id', () => {
            const incoming = { ...camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F1']), id: 'c1' };
            expect(keepFileForOutsideHouseholdChange(req({ ...camp(['HOUSEHOLDSTRATEGY', 'OUTSIDEHOUSEHOLD'], ['A'], ['F1']), id: 'c1' }, incoming), 'F1')).toBe('F1');
            expect(keptOn(incoming)).toEqual({ campaignId: 'c1', fileStoreId: 'F1' });
            expect(incoming.additionalDetails.cycleData.deliveryMethods).toEqual(['HOUSEHOLDSTRATEGY']);
        });

        it('carries the saved file over a save that leaves it out, and replaces a stale one the console sends', () => {
            const incoming = camp(['HOUSEHOLDSTRATEGY'], ['A'], []);
            expect(keepFileForOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), incoming), null)).toBe('F1');
            expect(keptOn(incoming)).toEqual({ campaignId: 'c1', fileStoreId: 'F1' });
            const stale = kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F0');
            keepFileForOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), stale), null);
            expect(keptOn(stale)).toEqual({ campaignId: 'c1', fileStoreId: 'F1' });
        });

        it('drops it when the area changes or a new file comes, and leaves a campaign with none untouched', () => {
            const newArea = kept(camp(['HOUSEHOLDSTRATEGY'], ['A', 'B'], []), 'F1');
            expect(keepFileForOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), newArea), null)).toBeNull();
            expect(newArea.additionalDetails).not.toHaveProperty(KEPT_FILE_KEY);
            expect(newArea.additionalDetails.cycleData).toBeDefined();
            const newFile = camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F2']);
            expect(keepFileForOutsideHouseholdChange(req(kept(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), 'F1'), newFile), null)).toBeNull();
            expect(newFile.additionalDetails).not.toHaveProperty(KEPT_FILE_KEY);
            const none: any = { id: 'c1' };
            expect(keepFileForOutsideHouseholdChange(req(camp(['HOUSEHOLDSTRATEGY'], ['A'], []), none), null)).toBeNull();
            expect(none).toEqual({ id: 'c1' });
        });

        it('does nothing when the switch is off', () => {
            (config as any).mapping.keepDataOnOutsideHouseholdChange = false;
            const incoming = { ...camp(['HOUSEHOLDSTRATEGY'], ['A'], ['F1']), id: 'c1' };
            expect(keepFileForOutsideHouseholdChange(req(camp(['OUTSIDEHOUSEHOLD'], ['A'], ['F1']), incoming), 'F1')).toBeNull();
            expect(keptOn(incoming)).toBeUndefined();
        });
    });

    it('passes the file to excel-ingestion with the update details', () => {
        expect(generationDetailsFromUpdate({ projectType: 'POLIO', additionalDetails: { cycleData: { deliveryMethods: ['HOUSEHOLDSTRATEGY'] } } }, 'F1'))
            .toEqual({ deliveryMethods: ['HOUSEHOLDSTRATEGY'], projectType: 'POLIO', previousFileStoreId: 'F1' });
        expect(generationDetailsFromUpdate({ projectType: 'POLIO' })).toEqual({ projectType: 'POLIO' });
        expect(generationDetailsFromUpdate({ projectType: 'POLIO' }, 'F2', ['F1', 'F0']))
            .toEqual({ projectType: 'POLIO', previousFileStoreId: 'F2', earlierFileStoreIds: ['F1', 'F0'] });
        expect(generationDetailsFromUpdate({ projectType: 'POLIO' }, null, ['F1'])).toEqual({ projectType: 'POLIO' });
        expect(generationDetailsFromUpdate({ projectType: 'POLIO' }, 'F2', [])).toEqual({ projectType: 'POLIO', previousFileStoreId: 'F2' });
    });
});
