jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn(),
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn(),
    defaultheader: jest.fn(() => ({})),
}));

jest.mock('../utils/genericUtils', () => ({
    getDataSheetReady: jest.fn(),
    getLocalizedHeaders: jest.fn(),
    throwError: jest.fn(),
}));

jest.mock('../utils/campaignUtils', () => ({
    generateFilteredBoundaryData: jest.fn(),
    getConfigurableColumnHeadersBasedOnCampaignType: jest.fn(),
    getFiltersFromCampaignSearchResponse: jest.fn(),
    getLocalizedName: jest.fn(),
    processDataForTargetCalculation: jest.fn(),
}));

jest.mock('../api/campaignApis', () => ({
    getCampaignSearchResponse: jest.fn(),
    getHierarchy: jest.fn(),
}));

jest.mock('../utils/excelUtils', () => ({
    enrichTemplateMetaData: jest.fn(),
    getExcelWorkbookFromFileURL: jest.fn(),
}));

jest.mock('../api/coreApis', () => ({
    searchBoundaryRelationshipData: jest.fn(),
    searchMDMSDataViaV2Api: jest.fn(),
}));

jest.mock('../utils/localisationUtils', () => ({
    getLocaleFromRequestInfo: jest.fn(),
}));

jest.mock('../models', () => ({ MDMSModels: {} }));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: jest.requireActual('../config').default.host,
        paths: jest.requireActual('../config').default.paths,
        mapping: { projectSearchChunkSize: 2, searchPageSize: 3 },
    },
}));

import { searchProjectResourcesByProjects, searchProjectFacilitiesByProjects, searchProjectStaffByProjects, deleteProjectFacilitiesById } from '../api/genericApis';
import { httpRequest } from '../utils/request';
import config from '../config';
import { TenantId } from '../config/models/brandedTypes';

const httpRequestMock = httpRequest as jest.MockedFunction<typeof httpRequest>;
const requestInfo: any = { userInfo: { uuid: 'u-1' } };
const tn = 'tn' as TenantId;

describe('project mapping bulk search helpers', () => {
    afterEach(() => jest.clearAllMocks());

    describe('searchProjectResourcesByProjects', () => {
        it('returns an empty map without any HTTP call when no projectIds are given', async () => {
            const result = await searchProjectResourcesByProjects([], 'tn', requestInfo);

            expect(result.size).toBe(0);
            expect(httpRequestMock).not.toHaveBeenCalled();
        });

        it('maps productVariantId|projectId combinations to existing resource ids', async () => {
            httpRequestMock.mockResolvedValueOnce({
                ProjectResources: [
                    { id: 'pr-1', projectId: 'p-1', resource: { productVariantId: 'pvar-1' } },
                    { id: 'pr-2', projectId: 'p-2', resource: { productVariantId: 'pvar-2' } },
                ],
            });

            const result = await searchProjectResourcesByProjects(['p-1', 'p-2'], 'tn', requestInfo);

            expect(result.get('pvar-1|p-1')).toBe('pr-1');
            expect(result.get('pvar-2|p-2')).toBe('pr-2');
            expect(httpRequestMock).toHaveBeenCalledWith(
                `${config.host.projectHost}${config.paths.projectResourceSearch}`,
                { RequestInfo: requestInfo, ProjectResource: { projectId: ['p-1', 'p-2'] } },
                { tenantId: 'tn', limit: 3, offset: 0, includeDeleted: false }
            );
        });

        it('chunks projectIds by projectSearchChunkSize per search call', async () => {
            httpRequestMock.mockResolvedValue({ ProjectResources: [] });

            await searchProjectResourcesByProjects(['p-1', 'p-2', 'p-3', 'p-4', 'p-5'], 'tn', requestInfo);

            expect(httpRequestMock).toHaveBeenCalledTimes(3);
            expect(httpRequestMock.mock.calls.map(c => (c[1] as any).ProjectResource.projectId)).toEqual([
                ['p-1', 'p-2'],
                ['p-3', 'p-4'],
                ['p-5'],
            ]);
        });

        it('paginates with offset until a page returns fewer rows than the page size', async () => {
            const fullPage = Array.from({ length: 3 }, (_, i) => ({
                id: `pr-${i}`, projectId: 'p-1', resource: { productVariantId: `pvar-${i}` },
            }));
            httpRequestMock
                .mockResolvedValueOnce({ ProjectResources: fullPage })
                .mockResolvedValueOnce({ ProjectResources: [{ id: 'pr-3', projectId: 'p-1', resource: { productVariantId: 'pvar-3' } }] });

            const result = await searchProjectResourcesByProjects(['p-1'], 'tn', requestInfo);

            expect(httpRequestMock).toHaveBeenCalledTimes(2);
            expect((httpRequestMock.mock.calls[0][2] as any).offset).toBe(0);
            expect((httpRequestMock.mock.calls[1][2] as any).offset).toBe(3);
            expect(result.size).toBe(4);
        });

        it('dedupes and drops falsy projectIds before searching', async () => {
            httpRequestMock.mockResolvedValue({ ProjectResources: [] });

            await searchProjectResourcesByProjects(['p-1', 'p-1', undefined as any, ''], 'tn', requestInfo);

            expect(httpRequestMock).toHaveBeenCalledTimes(1);
            expect((httpRequestMock.mock.calls[0][1] as any).ProjectResource.projectId).toEqual(['p-1']);
        });

        it('ignores response rows missing an id or the combination fields', async () => {
            httpRequestMock.mockResolvedValueOnce({
                ProjectResources: [
                    { id: 'pr-1', projectId: 'p-1', resource: { productVariantId: 'pvar-1' } },
                    { projectId: 'p-1', resource: { productVariantId: 'pvar-2' } },
                    { id: 'pr-3', projectId: 'p-1', resource: {} },
                ],
            });

            const result = await searchProjectResourcesByProjects(['p-1'], 'tn', requestInfo);

            expect(result.size).toBe(1);
            expect(result.get('pvar-1|p-1')).toBe('pr-1');
        });
    });

    describe('searchProjectFacilitiesByProjects', () => {
        it('maps facilityId|projectId combinations from the ProjectFacilities response', async () => {
            httpRequestMock.mockResolvedValueOnce({
                ProjectFacilities: [{ id: 'pf-1', projectId: 'p-1', facilityId: 'f-1' }],
            });

            const result = await searchProjectFacilitiesByProjects(['p-1'], 'tn', requestInfo);

            expect(result.get('f-1|p-1')).toBe('pf-1');
            expect((httpRequestMock.mock.calls[0][1] as any).ProjectFacility.projectId).toEqual(['p-1']);
        });

        it('narrows the search body with the given facilityId list', async () => {
            httpRequestMock.mockResolvedValueOnce({ ProjectFacilities: [] });

            await searchProjectFacilitiesByProjects(['p-1'], 'tn', requestInfo, ['f-1', 'f-2', 'f-1']);

            expect((httpRequestMock.mock.calls[0][1] as any).ProjectFacility).toEqual({
                projectId: ['p-1'],
                facilityId: ['f-1', 'f-2'],
            });
        });

        it('omits the entity filter from the body when no facility ids are given', async () => {
            httpRequestMock.mockResolvedValueOnce({ ProjectFacilities: [] });

            await searchProjectFacilitiesByProjects(['p-1'], 'tn', requestInfo);

            expect((httpRequestMock.mock.calls[0][1] as any).ProjectFacility).toEqual({ projectId: ['p-1'] });
        });
    });

    describe('searchProjectStaffByProjects', () => {
        it('maps userId|projectId combinations from the ProjectStaff response', async () => {
            httpRequestMock.mockResolvedValueOnce({
                ProjectStaff: [{ id: 'ps-1', projectId: 'p-1', userId: 'u-1' }],
            });

            const result = await searchProjectStaffByProjects(['p-1'], 'tn', requestInfo);

            expect(result.get('u-1|p-1')).toBe('ps-1');
            expect((httpRequestMock.mock.calls[0][1] as any).ProjectStaff.projectId).toEqual(['p-1']);
        });

        it('narrows the search body with the given staffId list', async () => {
            httpRequestMock.mockResolvedValueOnce({ ProjectStaff: [] });

            await searchProjectStaffByProjects(['p-1'], 'tn', requestInfo, ['u-1', 'u-2']);

            expect((httpRequestMock.mock.calls[0][1] as any).ProjectStaff).toEqual({
                projectId: ['p-1'],
                staffId: ['u-1', 'u-2'],
            });
        });
    });

    describe('deleteProjectFacilitiesById', () => {
        const link = (id: string, projectId: string, facilityId: string) => ({ id, projectId, facilityId });
        const searchCalls = () => httpRequestMock.mock.calls.filter(c => String(c[0]).endsWith(config.paths.projectFacilitySearch));
        const deleteCalls = () => httpRequestMock.mock.calls.filter(c => String(c[0]).endsWith(config.paths.projectFacilityDelete));

        it('makes no call when there is nothing to remove', async () => {
            const result = await deleteProjectFacilitiesById([], tn, requestInfo);

            expect(result.size).toBe(0);
            expect(httpRequestMock).not.toHaveBeenCalled();
        });

        it('reads the links back by id only and deletes the ones that still join the expected project and facility', async () => {
            httpRequestMock
                .mockResolvedValueOnce({ ProjectFacilities: [{ ...link('pf-1', 'p-1', 'f-1'), rowVersion: 1 }, { ...link('pf-2', 'p-2', 'f-2'), rowVersion: 1 }] })
                .mockResolvedValueOnce({});

            const result = await deleteProjectFacilitiesById([link('pf-1', 'p-1', 'f-1'), link('pf-2', 'p-2', 'f-2')], tn, requestInfo);

            expect(searchCalls()[0][1]).toEqual({ RequestInfo: requestInfo, ProjectFacility: { id: ['pf-1', 'pf-2'] } });
            expect(searchCalls()[0][2]).toEqual({ tenantId: 'tn', limit: 2, offset: 0, includeDeleted: false });
            expect(deleteCalls()).toHaveLength(1);
            expect((deleteCalls()[0][1] as any).ProjectFacilities.map((l: any) => l.id)).toEqual(['pf-1', 'pf-2']);
            expect(result.get('pf-1')).toEqual({ result: 'deleted' });
            expect(result.get('pf-2')).toEqual({ result: 'deleted' });
        });

        it('leaves alone a link whose id now joins another project or facility, and reports a link already gone as absent', async () => {
            httpRequestMock
                .mockResolvedValueOnce({ ProjectFacilities: [link('pf-1', 'p-9', 'f-1')] })
                .mockResolvedValueOnce({ ProjectFacilities: [link('pf-3', 'p-3', 'f-3')] })
                .mockResolvedValueOnce({});

            const result = await deleteProjectFacilitiesById(
                [link('pf-1', 'p-1', 'f-1'), link('pf-2', 'p-2', 'f-2'), link('pf-3', 'p-3', 'f-3')], tn, requestInfo);

            expect(result.get('pf-1')?.result).toBe('mismatch');
            expect(result.get('pf-2')).toEqual({ result: 'absent' });
            expect(result.get('pf-3')).toEqual({ result: 'deleted' });
            expect(deleteCalls()).toHaveLength(1);
            expect((deleteCalls()[0][1] as any).ProjectFacilities.map((l: any) => l.id)).toEqual(['pf-3']);
        });

        it('sends a link listed twice once', async () => {
            httpRequestMock.mockResolvedValueOnce({ ProjectFacilities: [link('pf-1', 'p-1', 'f-1')] }).mockResolvedValueOnce({});

            const result = await deleteProjectFacilitiesById([link('pf-1', 'p-1', 'f-1'), link('pf-1', 'p-1', 'f-1')], tn, requestInfo);

            expect((searchCalls()[0][1] as any).ProjectFacility.id).toEqual(['pf-1']);
            expect((deleteCalls()[0][1] as any).ProjectFacilities).toHaveLength(1);
            expect(result.get('pf-1')).toEqual({ result: 'deleted' });
        });

        it('marks only the ids of a chunk whose call failed as failed', async () => {
            httpRequestMock
                .mockRejectedValueOnce(new Error('search down'))
                .mockResolvedValueOnce({ ProjectFacilities: [link('pf-3', 'p-3', 'f-3')] })
                .mockResolvedValueOnce({});

            const result = await deleteProjectFacilitiesById(
                [link('pf-1', 'p-1', 'f-1'), link('pf-2', 'p-2', 'f-2'), link('pf-3', 'p-3', 'f-3')], tn, requestInfo);

            expect(result.get('pf-1')).toEqual({ result: 'failed', error: 'search down' });
            expect(result.get('pf-2')).toEqual({ result: 'failed', error: 'search down' });
            expect(result.get('pf-3')).toEqual({ result: 'deleted' });
        });
    });
});
