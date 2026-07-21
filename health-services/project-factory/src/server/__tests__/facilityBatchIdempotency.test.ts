/**
 * Idempotency tests for handleFacilityBatch under at-least-once Kafka delivery.
 *
 * The facility service mints a fresh id on every _create with no natural key, so a
 * crash-redelivered batch (whose payload carries dispatch-time status) must NOT re-create
 * facilities that a prior attempt already created. The handler re-reads live DB status via
 * getCampaignDataRowsWithUniqueIdentifiers and creates only rows not already completed.
 */

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
}));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../utils/campaignFailureHandler', () => ({ sendCampaignFailureMessage: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({ getCampaignDataRowsWithUniqueIdentifiers: jest.fn() }));
jest.mock('../config/transformConfigs', () => ({ transformConfigs: { FacilityUnified: { metadata: {} } } }));
jest.mock('../utils/transFormUtil', () => ({
    DataTransformer: jest.fn().mockImplementation(() => ({
        transform: jest.fn().mockImplementation((rows: any[]) =>
            Promise.resolve(rows.map((r: any) => ({ Facility: { name: r?.HCM_ADMIN_CONSOLE_FACILITY_NAME } })))
        ),
    })),
}));

jest.mock('../config', () => ({
    __esModule: true,
    default: {
        kafka: { KAFKA_UPDATE_SHEET_DATA_TOPIC: 'update-sheet' },
        host: { facilityHost: 'http://facility/' },
        paths: { facilityCreate: 'facility/_create' },
    },
}));

import { handleFacilityBatch } from '../utils/facilityBatchHandler';
import { httpRequest } from '../utils/request';
import { produceModifiedMessages } from '../kafka/Producer';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { getCampaignDataRowsWithUniqueIdentifiers } from '../utils/genericUtils';

const mockHttpRequest = httpRequest as jest.Mock;
const mockProduce = produceModifiedMessages as jest.Mock;
const mockSearchCampaign = searchProjectTypeCampaignService as jest.Mock;
const mockGetRows = getCampaignDataRowsWithUniqueIdentifiers as jest.Mock;

function facilityRecord(id: string) {
    return { type: 'facility', status: 'pending', data: { HCM_ADMIN_CONSOLE_FACILITY_NAME: `Facility ${id}` }, uniqueIdAfterProcess: null };
}

function buildMessage(ids: string[]) {
    const facilityData: Record<string, any> = {};
    ids.forEach(id => { facilityData[id] = facilityRecord(id); });
    return {
        tenantId: 'mz', campaignNumber: 'CMP-1', campaignId: 'camp-1', useruuid: 'u1',
        facilityData, batchNumber: 1, totalBatches: 1,
        requestInfo: { userInfo: { uuid: 'u1' } } as any,
    };
}

describe('handleFacilityBatch idempotency (at-least-once)', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockSearchCampaign.mockResolvedValue({ CampaignDetails: [{ status: 'inprogress', hierarchyType: 'NIGERIA' }] });
        mockHttpRequest.mockImplementation((_url: string, body: any) =>
            Promise.resolve({ Facility: { id: `FAC-${body?.Facility?.name}`, name: body?.Facility?.name } })
        );
    });

    it('creates all facilities on a clean run (no rows already completed)', async () => {
        mockGetRows.mockResolvedValue([]);
        await handleFacilityBatch(buildMessage(['a', 'b', 'c']));
        expect(mockHttpRequest).toHaveBeenCalledTimes(3);
    });

    it('redelivery: all rows already completed — creates nothing and returns', async () => {
        mockGetRows.mockResolvedValue([
            { uniqueIdentifier: 'a' }, { uniqueIdentifier: 'b' }, { uniqueIdentifier: 'c' },
        ]);
        await handleFacilityBatch(buildMessage(['a', 'b', 'c']));
        expect(mockHttpRequest).not.toHaveBeenCalled();
    });

    it('partial redelivery: only rows not already completed are created', async () => {
        mockGetRows.mockResolvedValue([{ uniqueIdentifier: 'a' }]);
        await handleFacilityBatch(buildMessage(['a', 'b', 'c']));
        expect(mockHttpRequest).toHaveBeenCalledTimes(2);
        const createdNames = mockHttpRequest.mock.calls.map((c: any[]) => c[1]?.Facility?.name).sort();
        expect(createdNames).toEqual(['Facility b', 'Facility c']);
    });

    it('re-reads live status scoped to the row type, completed status, and this campaignNumber', async () => {
        mockGetRows.mockResolvedValue([]);
        await handleFacilityBatch(buildMessage(['a', 'b']));
        expect(mockGetRows).toHaveBeenCalledWith('facility', ['a', 'b'], 'mz', 'completed', 'CMP-1');
    });

    it('does not adopt a row just because another campaign completed the same uniqueIdentifier', async () => {
        // getCampaignDataRowsWithUniqueIdentifiers is scoped by campaignNumber (see call above),
        // so a same-named facility completed under a different campaign is never returned here —
        // this campaign's row is still created rather than being stranded as "already done".
        mockGetRows.mockResolvedValue([]);
        await handleFacilityBatch(buildMessage(['shared-facility-code']));
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
    });

    it('persists created facilities via the sheet-data topic', async () => {
        mockGetRows.mockResolvedValue([]);
        await handleFacilityBatch(buildMessage(['a']));
        const sheetCall = mockProduce.mock.calls.find((c: any[]) => c[1] === 'update-sheet');
        expect(sheetCall).toBeDefined();
        expect(sheetCall[0].datas[0].status).toBe('completed');
    });
});
