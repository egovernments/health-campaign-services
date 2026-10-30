jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../utils/genericUtils', () => ({ getLocalizedMessagesHandler: jest.fn(), processGenerate: jest.fn() }));
jest.mock('../service/sheetManageService', () => ({ generateDataService: jest.fn() }));
jest.mock('../utils/localisationUtils', () => ({ getLocaleFromRequestInfo: jest.fn(), getLocalisationModuleName: jest.fn() }));
jest.mock('../api/genericApis', () => ({ getBoundarySheetData: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({ checkIfSourceIsMicroplan: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../config', () => ({ __esModule: true, default: { localisation: { defaultLocale: 'en_IN' }, host: {}, paths: {} } }));

import { callGenerateIfBoundariesOrCampaignTypeDiffer, generationDetailsFromUpdate, isGenerationTriggerNeeded, isOutsideHouseholdChoiceDifferentFromSource, isOutsideHouseholdDeliveryPersisted } from '../utils/generateUtils';
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
