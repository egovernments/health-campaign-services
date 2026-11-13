/**
 * Idempotency test for handleMappingTaskForCampaign (legacy classic-flow mapping path)
 * under at-least-once Kafka delivery.
 *
 * This create path (startResourceMapping / startFacilityMappingAndDemapping /
 * startUserMappingAndDemapping) has no adopt-existing pre-pass, so a crash-redelivered
 * mapping task could create duplicate project staff/facility/resource records. The handler
 * re-reads live process status and skips if the process already completed.
 */

jest.mock('../config', () => ({ __esModule: true, default: { kafka: { KAFKA_UPDATE_PROCESS_DATA_TOPIC: 'update-process' }, batchSize: 100 } }));
jest.mock('../utils/logger', () => ({ logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() } }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));
jest.mock('../kafka/Producer', () => ({ produceModifiedMessages: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/genericUtils', () => ({ getCurrentProcesses: jest.fn(), throwError: jest.fn() }));
jest.mock('../utils/campaignUtils', () => ({
    enrichAndPersistCampaignWithError: jest.fn().mockResolvedValue(undefined),
    enrichAndPersistCampaignWithErrorProcessingTask: jest.fn().mockResolvedValue(undefined),
}));
jest.mock('../api/genericApis', () => ({
    createProjectFacilityHelper: jest.fn(), createProjectResourceHelper: jest.fn(), createProjectStaffHelper: jest.fn(),
}));
jest.mock('../utils/resourceMappingUtils', () => ({ startResourceMapping: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/userMappingUtils', () => ({ startUserMappingAndDemapping: jest.fn().mockResolvedValue(undefined) }));
jest.mock('../utils/facilityMappingUtils', () => ({ startFacilityMappingAndDemapping: jest.fn().mockResolvedValue(undefined) }));

import { handleMappingTaskForCampaign } from '../utils/campaignMappingUtils';
import { getCurrentProcesses } from '../utils/genericUtils';
import { produceModifiedMessages } from '../kafka/Producer';
import { startResourceMapping } from '../utils/resourceMappingUtils';
import { allProcesses } from '../config/constants';

const mockGetCurrentProcesses = getCurrentProcesses as jest.Mock;
const mockProduce = produceModifiedMessages as jest.Mock;
const mockStartResourceMapping = startResourceMapping as jest.Mock;

function buildMessage() {
    return {
        CampaignDetails: { id: 'camp-1', tenantId: 'mz', campaignNumber: 'CMP-1' },
        task: { processName: allProcesses.resourceMapping, status: 'pending', auditDetails: {}, campaignNumber: 'CMP-1' },
        requestInfo: { userInfo: { uuid: 'u1' } },
    };
}

describe('handleMappingTaskForCampaign idempotency (at-least-once)', () => {
    beforeEach(() => jest.clearAllMocks());

    it('process already completed (redelivery) — skips mapping and does not re-run start*', async () => {
        mockGetCurrentProcesses.mockResolvedValue([{ processName: allProcesses.resourceMapping, status: 'completed' }]);

        await handleMappingTaskForCampaign(buildMessage());

        expect(mockGetCurrentProcesses).toHaveBeenCalledWith('CMP-1', 'mz', allProcesses.resourceMapping, 'completed');
        expect(mockStartResourceMapping).not.toHaveBeenCalled();
        expect(mockProduce).not.toHaveBeenCalled();
    });

    it('process not yet completed — runs the mapping start function', async () => {
        mockGetCurrentProcesses.mockResolvedValue([]);

        await handleMappingTaskForCampaign(buildMessage());

        expect(mockStartResourceMapping).toHaveBeenCalled();
    });
});
