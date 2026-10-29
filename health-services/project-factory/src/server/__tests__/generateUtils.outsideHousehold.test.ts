jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../utils/genericUtils', () => ({ getLocalizedMessagesHandler: jest.fn(), processGenerate: jest.fn() }));
jest.mock('../service/sheetManageService', () => ({ generateDataService: jest.fn() }));
jest.mock('../utils/localisationUtils', () => ({ getLocaleFromRequestInfo: jest.fn(), getLocalisationModuleName: jest.fn() }));
jest.mock('../api/genericApis', () => ({ getBoundarySheetData: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({ checkIfSourceIsMicroplan: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../config', () => ({ __esModule: true, default: { localisation: { defaultLocale: 'en_IN' }, host: {}, paths: {} } }));

import { isGenerationTriggerNeeded } from '../utils/generateUtils';

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
