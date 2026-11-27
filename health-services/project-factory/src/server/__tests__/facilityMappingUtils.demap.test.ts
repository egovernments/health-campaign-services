jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/genericUtils', () => ({ getMappingDataRelatedToCampaign: jest.fn(), getRelatedDataWithCampaign: jest.fn() }));
jest.mock('../api/genericApis', () => ({ createProjectFacility: jest.fn(), deleteProjectFacilitiesById: jest.fn() }));
jest.mock('../config', () => ({ __esModule: true, default: { kafka: { KAFKA_DELETE_MAPPING_DATA_TOPIC: 'delete-mapping' } } }));

import { startFacilityDemapping } from '../utils/facilityMappingUtils';
import { getMappingDataRelatedToCampaign, getRelatedDataWithCampaign } from '../utils/genericUtils';
import { deleteProjectFacilitiesById } from '../api/genericApis';
import { produceModifiedMessages } from '../kafka/Producer';
import { mappingStatuses } from '../config/constants';

const deleteMock = deleteProjectFacilitiesById as jest.MockedFunction<typeof deleteProjectFacilitiesById>;
const produceMock = produceModifiedMessages as jest.MockedFunction<typeof produceModifiedMessages>;
const campaign = { campaignNumber: 'CMP-1', tenantId: 'tn' };
const row = { type: 'facility', boundaryCode: 'B-1', uniqueIdentifierForData: 'F-1', mappingId: 'pf-1', status: mappingStatuses.toBeDeMapped };

describe('startFacilityDemapping — removes the link by its recorded id', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        (getMappingDataRelatedToCampaign as jest.Mock).mockResolvedValue([row]);
        (getRelatedDataWithCampaign as jest.Mock).mockImplementation((type: string) => Promise.resolve(type === 'boundary'
            ? [{ uniqueIdentifier: 'B-1', uniqueIdAfterProcess: 'project-1' }]
            : [{ uniqueIdentifier: 'F-1', uniqueIdAfterProcess: 'fac-1' }]));
    });

    it.each(['deleted', 'absent'] as const)('drops the mapping row when the link is %s', async (result) => {
        deleteMock.mockResolvedValueOnce(new Map([['pf-1', { result }]]));

        await startFacilityDemapping(campaign, 'u-1', {} as any);

        expect(deleteMock).toHaveBeenCalledWith([{ id: 'pf-1', projectId: 'project-1', facilityId: 'fac-1' }], 'tn', {});
        expect(produceMock).toHaveBeenCalledWith({ datas: [row] }, 'delete-mapping', 'tn');
    });

    it('keeps the mapping row and fails when the id names another link', async () => {
        deleteMock.mockResolvedValueOnce(new Map([['pf-1', { result: 'mismatch' as const, error: 'joins another project' }]]));

        await expect(startFacilityDemapping(campaign, 'u-1', {} as any)).rejects.toThrow('joins another project');
        expect(produceMock).not.toHaveBeenCalled();
    });
});
