/**
 * Unit tests for resourceDetailsService.ts
 *
 * Tests that:
 * 1. Campaign-wide creating guard blocks add/update when any resource is processing
 * 2. Reuploading a stuck toCreate resource succeeds (no RESOURCE_ALREADY_QUEUED)
 * 3. Reuploading a resource in creating status is still blocked (RESOURCE_PROCESSING)
 */

// --- Mocks (must be before imports) ---

const mockExecuteQuery = jest.fn();
const mockGetTableName = jest.fn().mockReturnValue('ba.eg_cm_resource_details');

jest.mock('../utils/db', () => ({
    executeQuery: (...args: any[]) => mockExecuteQuery(...args),
    getTableName: (...args: any[]) => mockGetTableName(...args)
}));

jest.mock('../utils/resourceDetailsUtils', () => ({
    searchResourceDetailsFromDB: jest.fn(),
    getResourceDetailById: jest.fn(),
    findActiveResourceByUpsertKey: jest.fn(),
    countResourcesByType: jest.fn().mockResolvedValue(0),
    countTotalResourceDetails: jest.fn().mockResolvedValue(0),
    toResourceDetailsResponse: jest.fn(row => row),
    ResourceDetailRow: {},
    hasAnyCreatingResource: jest.fn().mockResolvedValue(false)
}));

jest.mock('../config/resourceTypeRegistry', () => ({
    getResourceConfigOrDefault: jest.fn().mockReturnValue({}),
    isRegisteredType: jest.fn().mockReturnValue(false),
    isSharedAcrossCampaignFamily: jest.fn().mockReturnValue(false)
}));

jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../config', () => ({
    default: {
        kafka: {
            KAFKA_CREATE_RESOURCE_DETAILS_TOPIC: 'create-resource-topic',
            KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC: 'update-resource-topic'
        },
        DB_CONFIG: {
            DB_CAMPAIGN_DETAILS_TABLE_NAME: 'eg_cm_campaign_details',
            DB_RESOURCE_DETAILS_TABLE_NAME: 'eg_cm_resource_details'
        }
    },
    __esModule: true
}));

jest.mock('../utils/logger', () => ({
    logger: {
        info: jest.fn(),
        error: jest.fn(),
        warn: jest.fn()
    }
}));

const mockThrowError = jest.fn().mockImplementation((_domain: any, _status: any, code: any, message: any) => {
    const err = new Error(message);
    (err as any).code = code;
    throw err;
});

const mockGetCampaignIdsByCampaignNumber = jest.fn();

jest.mock('../utils/genericUtils', () => ({
    throwError: (...args: any[]) => mockThrowError(...args),
    getCampaignIdsByCampaignNumber: (...args: any[]) => mockGetCampaignIdsByCampaignNumber(...args)
}));

jest.mock('uuid', () => ({
    v4: jest.fn().mockReturnValue('new-uuid')
}));

// --- Imports after mocks ---

import { createResourceDetail } from '../service/resourceDetailsService';
import { updateResourceDetail, searchResourceDetails } from '../service/resourceDetailsService';
import { hasAnyCreatingResource, findActiveResourceByUpsertKey, getResourceDetailById, searchResourceDetailsFromDB, countTotalResourceDetails } from '../utils/resourceDetailsUtils';
import { isSharedAcrossCampaignFamily } from '../config/resourceTypeRegistry';
import { produceModifiedMessages } from '../kafka/Producer';

const mockHasAnyCreating = hasAnyCreatingResource as jest.Mock;
const mockFindActiveResource = findActiveResourceByUpsertKey as jest.Mock;
const mockGetResourceById = getResourceDetailById as jest.Mock;
const mockProduce = produceModifiedMessages as jest.Mock;
const mockSearchFromDB = searchResourceDetailsFromDB as jest.Mock;
const mockCountTotal = countTotalResourceDetails as jest.Mock;
const mockIsShared = isSharedAcrossCampaignFamily as jest.Mock;

// Campaign DB row helpers
function mockCampaignStatus(status: string) {
    mockExecuteQuery.mockResolvedValue({ rows: [{ status, campaignnumber: 'CN-001' }] });
}

const EXISTING_TOCREATE_ROW = {
    id: 'existing-id',
    tenantid: 'default',
    campaignid: 'campaign-1',
    type: 'facility',
    parentresourceid: null,
    filestoreid: 'fs-old',
    processedfilestoreid: null,
    filename: null,
    status: 'toCreate',
    action: 'create',
    isactive: true,
    hierarchytype: null,
    additionaldetails: {},
    createdby: 'user-1',
    lastmodifiedby: 'user-1',
    createdtime: 1000000,
    lastmodifiedtime: 1000000
};

const EXISTING_CREATING_ROW = { ...EXISTING_TOCREATE_ROW, status: 'creating' };
const EXISTING_COMPLETED_ROW = { ...EXISTING_TOCREATE_ROW, status: 'completed' };

const CREATE_INPUT = {
    tenantId: 'default',
    campaignId: 'campaign-1',
    type: 'facility',
    fileStoreId: 'fs-new',
    filename: 'file.xlsx',
    additionalDetails: {}
};

const UPDATE_INPUT = {
    id: 'existing-id',
    tenantId: 'default',
    campaignId: 'campaign-1',
    fileStoreId: 'fs-new'
};

describe('resourceDetailsService — campaign-wide creating guard', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockHasAnyCreating.mockResolvedValue(false);
        mockFindActiveResource.mockResolvedValue(null);
        mockCampaignStatus('created'); // inprogress
    });

    it('T7: createResourceDetail blocked when any resource is creating', async () => {
        mockHasAnyCreating.mockResolvedValue(true);

        await expect(createResourceDetail(CREATE_INPUT, 'user-1'))
            .rejects.toThrow('Cannot add resources while campaign resources are currently being processed');

        expect(mockThrowError).toHaveBeenCalledWith(
            'COMMON', 409, 'CAMPAIGN_RESOURCE_PROCESSING', expect.any(String)
        );
    });

    it('T8: updateResourceDetail blocked when any resource is creating', async () => {
        mockHasAnyCreating.mockResolvedValue(true);
        mockGetResourceById.mockResolvedValue(EXISTING_COMPLETED_ROW);

        await expect(updateResourceDetail(UPDATE_INPUT, 'user-1'))
            .rejects.toThrow('Cannot update resources while campaign resources are currently being processed');

        expect(mockThrowError).toHaveBeenCalledWith(
            'COMMON', 409, 'CAMPAIGN_RESOURCE_PROCESSING', expect.any(String)
        );
    });

    it('T9: createResourceDetail proceeds when no resources are creating', async () => {
        mockHasAnyCreating.mockResolvedValue(false);
        mockFindActiveResource.mockResolvedValue(null);

        await createResourceDetail(CREATE_INPUT, 'user-1');

        expect(mockThrowError).not.toHaveBeenCalledWith(
            expect.anything(), expect.anything(), 'CAMPAIGN_RESOURCE_PROCESSING', expect.any(String)
        );
        // New resource create message sent
        expect(mockProduce).toHaveBeenCalledWith(
            expect.objectContaining({ ResourceDetails: expect.objectContaining({ status: 'toCreate' }) }),
            'create-resource-topic',
            'default'
        );
    });
});

describe('resourceDetailsService — toCreate upsert (no RESOURCE_ALREADY_QUEUED)', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        mockHasAnyCreating.mockResolvedValue(false);
        mockCampaignStatus('created'); // inprogress
    });

    it('T10: reuploading toCreate resource on inprogress campaign succeeds', async () => {
        mockFindActiveResource.mockResolvedValue(EXISTING_TOCREATE_ROW);

        await createResourceDetail(CREATE_INPUT, 'user-1');

        // Should NOT throw RESOURCE_ALREADY_QUEUED
        const queuedCall = mockThrowError.mock.calls.find(
            (args: any[]) => args[2] === 'RESOURCE_ALREADY_QUEUED'
        );
        expect(queuedCall).toBeUndefined();

        // Should deactivate old (update topic) then create new (create topic)
        const deactivateCall = mockProduce.mock.calls.find(
            (args: any[]) => args[1] === 'update-resource-topic'
        );
        expect(deactivateCall).toBeDefined();

        const createCall = mockProduce.mock.calls.find(
            (args: any[]) => args[1] === 'create-resource-topic'
        );
        expect(createCall).toBeDefined();
        expect(createCall[0].ResourceDetails.status).toBe('toCreate');
    });

    it('T11: reuploading a resource in creating status still throws RESOURCE_PROCESSING', async () => {
        mockFindActiveResource.mockResolvedValue(EXISTING_CREATING_ROW);

        await expect(createResourceDetail(CREATE_INPUT, 'user-1'))
            .rejects.toThrow();

        expect(mockThrowError).toHaveBeenCalledWith(
            'COMMON', 409, 'RESOURCE_PROCESSING', expect.any(String)
        );
    });

    it('T12: reuploading completed resource on inprogress campaign succeeds', async () => {
        mockFindActiveResource.mockResolvedValue(EXISTING_COMPLETED_ROW);

        await createResourceDetail(CREATE_INPUT, 'user-1');

        const createCall = mockProduce.mock.calls.find(
            (args: any[]) => args[1] === 'create-resource-topic'
        );
        expect(createCall).toBeDefined();
        expect(createCall[0].ResourceDetails.status).toBe('toCreate');
    });
});

describe('searchResourceDetails — campaign-family resolution for shared types', () => {
    function registerRow(over: Partial<any> = {}): any {
        return {
            id: 'r-1',
            tenantid: 'ba',
            campaignid: 'parent-id',
            type: 'attendanceRegister',
            parentresourceid: null,
            filestoreid: 'fs',
            processedfilestoreid: 'pfs',
            filename: null,
            status: 'completed',
            action: 'create',
            isactive: true,
            hierarchytype: null,
            additionaldetails: {},
            createdby: 'u',
            lastmodifiedby: 'u',
            createdtime: 1000,
            lastmodifiedtime: 2000,
            ...over
        };
    }

    beforeEach(() => {
        jest.clearAllMocks();
        mockIsShared.mockReturnValue(false);
        mockCountTotal.mockResolvedValue(0);
        mockSearchFromDB.mockResolvedValue([]);
        // getCampaignStatusFromDB → campaignNumber present
        mockExecuteQuery.mockResolvedValue({ rows: [{ status: 'created', campaignnumber: 'CN-001' }] });
    });

    it('resolves attendanceRegister across the family when searched by a child campaignId', async () => {
        mockIsShared.mockReturnValue(true);
        mockGetCampaignIdsByCampaignNumber.mockResolvedValue(['parent-id', 'child-id', 'grandchild-id']);
        const row = registerRow();
        mockSearchFromDB.mockResolvedValue([row]);

        const result = await searchResourceDetails(
            { tenantId: 'ba', campaignId: 'grandchild-id', type: ['attendanceRegister'], isActive: true } as any
        );

        expect(mockGetCampaignIdsByCampaignNumber).toHaveBeenCalledWith('CN-001', 'ba');
        expect(mockSearchFromDB).toHaveBeenCalledWith(
            expect.objectContaining({ campaignIds: ['parent-id', 'child-id', 'grandchild-id'] }),
            { sortBy: 'lastmodifiedtime', sortOrder: 'DESC' }
        );
        expect(result.TotalCount).toBe(1);
        expect(result.ResourceDetails).toEqual([row]);
    });

    it('resolves attendanceRegisterAttendee across the family too', async () => {
        mockIsShared.mockReturnValue(true);
        mockGetCampaignIdsByCampaignNumber.mockResolvedValue(['parent-id', 'child-id']);
        const attendee = registerRow({ type: 'attendanceRegisterAttendee', parentresourceid: 'reg-1' });
        mockSearchFromDB.mockResolvedValue([attendee]);

        const result = await searchResourceDetails(
            { tenantId: 'ba', campaignId: 'child-id', type: ['attendanceRegisterAttendee'], isActive: true } as any
        );

        expect(result.TotalCount).toBe(1);
        expect(result.ResourceDetails[0].type).toBe('attendanceRegisterAttendee');
    });

    it('dedupes by (type, parentResourceId) keeping the newest (rows arrive lastmodifiedtime DESC)', async () => {
        mockIsShared.mockReturnValue(true);
        mockGetCampaignIdsByCampaignNumber.mockResolvedValue(['parent-id', 'child-id']);
        const newest = registerRow({ id: 'a-new', campaignid: 'child-id', parentresourceid: 'reg-1', type: 'attendanceRegisterAttendee', lastmodifiedtime: 5000 });
        const older = registerRow({ id: 'a-old', campaignid: 'parent-id', parentresourceid: 'reg-1', type: 'attendanceRegisterAttendee', lastmodifiedtime: 3000 });
        const otherParent = registerRow({ id: 'b', parentresourceid: 'reg-2', type: 'attendanceRegisterAttendee', lastmodifiedtime: 4000 });
        mockSearchFromDB.mockResolvedValue([newest, otherParent, older]);

        const result = await searchResourceDetails(
            { tenantId: 'ba', campaignId: 'child-id', type: ['attendanceRegisterAttendee'], isActive: true } as any
        );

        expect(result.TotalCount).toBe(2);
        expect(result.ResourceDetails.map((r: any) => r.id)).toEqual(['a-new', 'b']);
    });

    it('non-shared type stays strict campaignId — no family expansion', async () => {
        mockIsShared.mockReturnValue(false);

        await searchResourceDetails(
            { tenantId: 'ba', campaignId: 'child-id', type: ['user'], isActive: true } as any
        );

        expect(mockGetCampaignIdsByCampaignNumber).not.toHaveBeenCalled();
        expect(mockSearchFromDB).toHaveBeenCalledWith(
            expect.not.objectContaining({ campaignIds: expect.anything() }),
            undefined
        );
    });

    it('falls back to strict path when campaignNumber cannot be resolved', async () => {
        mockIsShared.mockReturnValue(true);
        mockExecuteQuery.mockResolvedValue({ rows: [] });

        await searchResourceDetails(
            { tenantId: 'ba', campaignId: 'child-id', type: ['attendanceRegister'], isActive: true } as any
        );

        expect(mockGetCampaignIdsByCampaignNumber).not.toHaveBeenCalled();
        expect(mockCountTotal).toHaveBeenCalled();
    });
});
