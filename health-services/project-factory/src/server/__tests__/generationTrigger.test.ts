jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn((x: any) => JSON.stringify(x)),
}));
jest.mock('../utils/genericUtils', () => ({
    getLocalizedMessagesHandler: jest.fn(),
    processGenerate: jest.fn(),
}));
jest.mock('../service/sheetManageService', () => ({ generateDataService: jest.fn() }));
jest.mock('../api/genericApis', () => ({ getBoundarySheetData: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({ checkIfSourceIsMicroplan: jest.fn(() => false) }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn(), defaultheader: jest.fn() }));
jest.mock('../utils/localisationUtils', () => ({
    getLocaleFromRequestInfo: jest.fn(() => 'en_IN'),
    getLocalisationModuleName: jest.fn(() => 'mod'),
}));

import { isGenerationTriggerNeeded } from '../utils/generateUtils';

const boundary = (code: string, extra: any = {}) => ({ code, includeAllChildren: true, isRoot: false, ...extra });

const buildRequest = (campaign: any, baseline?: any) => ({
    body: {
        CampaignDetails: campaign,
        ...(baseline ? { ExistingCampaignDetails: baseline } : {}),
    },
});

describe('isGenerationTriggerNeeded', () => {
    afterEach(() => jest.clearAllMocks());

    describe('newBoundaries on the false branch', () => {
        it('returns newBoundaries even when the trigger is false', () => {
            const boundaries = [boundary('B1'), boundary('B2')];
            const request = buildRequest(
                { boundaries, projectType: 'MR-DN', hierarchyType: 'ADMIN', additionalDetails: { source: 'console' } },
                { boundaries, projectType: 'MR-DN', hierarchyType: 'ADMIN', additionalDetails: { source: 'console' } },
            );

            const result = isGenerationTriggerNeeded(request);

            expect(result.trigger).toBe(false);
            expect(Array.isArray(result.newBoundaries)).toBe(true);
            expect(result.newBoundaries).toHaveLength(2);
        });

        it('excludes insertedAfter boundaries from newBoundaries on the false branch', () => {
            const persisted = [boundary('B1')];
            const request = buildRequest(
                { boundaries: [boundary('B1'), boundary('B9', { insertedAfter: true })], projectType: 'MR-DN', hierarchyType: 'ADMIN' },
                { boundaries: persisted, projectType: 'MR-DN', hierarchyType: 'ADMIN' },
            );

            const result = isGenerationTriggerNeeded(request);

            expect(result.trigger).toBe(false);
            expect(result.newBoundaries).toEqual([expect.objectContaining({ code: 'B1' })]);
        });
    });

    describe('trigger conditions', () => {
        it('triggers when boundaries differ', () => {
            const request = buildRequest(
                { boundaries: [boundary('B1'), boundary('B2')], projectType: 'MR-DN', hierarchyType: 'ADMIN' },
                { boundaries: [boundary('B1')], projectType: 'MR-DN', hierarchyType: 'ADMIN' },
            );

            expect(isGenerationTriggerNeeded(request).trigger).toBe(true);
        });

        it('triggers when hierarchyType alone changes', () => {
            const boundaries = [boundary('B1')];
            const request = buildRequest(
                { boundaries, projectType: 'MR-DN', hierarchyType: 'ADMIN' },
                { boundaries, projectType: 'MR-DN', hierarchyType: 'REVENUE' },
            );

            expect(isGenerationTriggerNeeded(request).trigger).toBe(true);
        });

        it('triggers when projectType alone changes', () => {
            const boundaries = [boundary('B1')];
            const request = buildRequest(
                { boundaries, projectType: 'LLIN-mz', hierarchyType: 'ADMIN' },
                { boundaries, projectType: 'MR-DN', hierarchyType: 'ADMIN' },
            );

            expect(isGenerationTriggerNeeded(request).trigger).toBe(true);
        });

        it('triggers on a create, where there is no baseline to compare against', () => {
            const request = buildRequest({ boundaries: [boundary('B1')], projectType: 'MR-DN', hierarchyType: 'ADMIN' });

            const result = isGenerationTriggerNeeded(request);

            expect(result.trigger).toBe(true);
            expect(result.newBoundaries).toHaveLength(1);
        });
    });

    describe('a clone baseline no longer suppresses the trigger', () => {
        it('ignores CloneSourceForGenerationCheck, which is no longer a baseline source', () => {
            const boundaries = [boundary('B1')];
            const request: any = buildRequest({ boundaries, projectType: 'MR-DN', hierarchyType: 'ADMIN' });
            request.body.CloneSourceForGenerationCheck = { boundaries, projectType: 'MR-DN', hierarchyType: 'ADMIN' };

            expect(isGenerationTriggerNeeded(request).trigger).toBe(true);
        });
    });
});
