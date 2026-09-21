jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), error: jest.fn(), warn: jest.fn(), debug: jest.fn() },
    getFormattedStringForDebug: jest.fn((x: any) => JSON.stringify(x)),
}));
jest.mock('../kafka/Producer', () => ({
    produceModifiedMessages: jest.fn().mockResolvedValue(undefined),
}));
// redisUtils opens a real socket at import time, which campaignUtils pulls in transitively.
jest.mock('../utils/redisUtils', () => ({
    redis: { get: jest.fn(), set: jest.fn(), del: jest.fn() },
    checkRedisConnection: jest.fn().mockResolvedValue(true),
    reconnectRedis: jest.fn(),
}));

import { produceModifiedMessages } from '../kafka/Producer';
import config from '../config';
import { enrichAndPersistCampaignForUpdate, preservedCloneLineage } from '../utils/campaignUtils';

const PARENT_NUMBER = 'CMP-2026-08-17-007802';
const PARENT_ID = 'b9aab922-8f1e-4b24-aa95-e4ea1716f115';

const persisted = {
    cloneFrom: PARENT_NUMBER,
    clonedCampaignId: PARENT_ID,
    isUnifiedCampaign: true,
};

describe('preservedCloneLineage', () => {
    afterEach(() => jest.clearAllMocks());

    it('restores lineage the update payload omitted', () => {
        const incoming = { beneficiaryType: 'INDIVIDUAL', key: 4, isUnifiedCampaign: true };

        expect(preservedCloneLineage(incoming, persisted)).toEqual({
            cloneFrom: PARENT_NUMBER,
            clonedCampaignId: PARENT_ID,
        });
    });

    it('keeps the persisted value when the payload sends a different one', () => {
        const incoming = { cloneFrom: 'CMP-SOMETHING-ELSE', clonedCampaignId: 'other-uuid' };

        expect(preservedCloneLineage(incoming, persisted)).toEqual({
            cloneFrom: PARENT_NUMBER,
            clonedCampaignId: PARENT_ID,
        });
    });

    it('adds nothing for a campaign that was never a clone', () => {
        expect(preservedCloneLineage({ key: 4 }, { key: 4 })).toEqual({});
    });

    it('adds nothing when the persisted record is missing entirely', () => {
        expect(preservedCloneLineage({ key: 4 }, undefined)).toEqual({});
    });

    it('ignores blank and null persisted lineage rather than writing empty keys', () => {
        expect(preservedCloneLineage({}, { cloneFrom: '', clonedCampaignId: null })).toEqual({});
    });

    it('preserves clonedCampaignId even when only that one is present', () => {
        expect(preservedCloneLineage({}, { clonedCampaignId: PARENT_ID })).toEqual({
            clonedCampaignId: PARENT_ID,
        });
    });

    it('adds nothing for an ongoing-update child campaign, even if lineage was persisted on it', () => {
        expect(preservedCloneLineage({ key: 4 }, persisted, 'root-uuid')).toEqual({});
    });
});

// The artefact that matters is the additionalDetails on the produced update message, not the helper's
// return value, so these drive the real call site with the payload the console actually sends.
describe('enrichAndPersistCampaignForUpdate keeps clone lineage on the produced message', () => {
    const producedAdditionalDetails = () => {
        expect(produceModifiedMessages).toHaveBeenCalledTimes(1);
        const [message, topic] = (produceModifiedMessages as jest.Mock).mock.calls[0];
        expect(topic).toBe(config.kafka.KAFKA_UPDATE_PROJECT_CAMPAIGN_DETAILS_TOPIC);
        return message.CampaignDetails.additionalDetails;
    };

    // What the console details/finalize builders send (transformUpdateCreateData / transformCreateData).
    const consolePartial = () => ({ beneficiaryType: 'INDIVIDUAL', key: 2, cycleData: {}, isUnifiedCampaign: true });

    const buildUpdateRequest = (incomingAdditionalDetails: any, existingOverrides: any = {}) => ({
        body: {
            RequestInfo: { userInfo: { uuid: 'u1', tenantId: 'dev', locale: 'en_IN' }, msgId: '1|en_IN' },
            CampaignDetails: {
                id: 'clone-uuid',
                tenantId: 'dev',
                action: 'create',
                campaignName: 'Bednet_clone_1',
                projectId: 'proj-1', // already set, so no root-project lookup is attempted
                additionalDetails: incomingAdditionalDetails,
            },
            ExistingCampaignDetails: {
                id: 'clone-uuid',
                tenantId: 'dev',
                campaignNumber: 'CMP-2026-09-09-000001',
                parentId: null,
                projectType: 'MR-DN',
                hierarchyType: 'ADMIN',
                createdBy: 'u0',
                createdTime: 1,
                additionalDetails: { ...persisted, locale: 'en_IN' },
                ...existingOverrides,
            },
        },
    });

    afterEach(() => jest.clearAllMocks());

    it('re-injects cloneFrom and clonedCampaignId that the console finalize omitted', async () => {
        const request = buildUpdateRequest(consolePartial());

        await enrichAndPersistCampaignForUpdate(request as any);

        const produced = producedAdditionalDetails();
        expect(produced).toMatchObject({
            beneficiaryType: 'INDIVIDUAL',
            key: 2,
            isUnifiedCampaign: true,
            cloneFrom: PARENT_NUMBER,
            clonedCampaignId: PARENT_ID,
        });
        expect(produced.locale).toBeTruthy();
    });

    it('lets the persisted lineage win over stale or null values the payload carries (merge order)', async () => {
        const request = buildUpdateRequest({ ...consolePartial(), cloneFrom: 'CMP-STALE', clonedCampaignId: null });

        await enrichAndPersistCampaignForUpdate(request as any);

        const produced = producedAdditionalDetails();
        expect(produced.cloneFrom).toBe(PARENT_NUMBER);
        expect(produced.clonedCampaignId).toBe(PARENT_ID);
    });

    it('does not re-add lineage for an ongoing-update child (carve-out reads the PERSISTED parentId)', async () => {
        const request = buildUpdateRequest(consolePartial(), { parentId: 'root-uuid' });

        await enrichAndPersistCampaignForUpdate(request as any);

        const produced = producedAdditionalDetails();
        expect(produced.cloneFrom).toBeUndefined();
        expect(produced.clonedCampaignId).toBeUndefined();
    });

    it('invents nothing when the persisted record was already wiped', async () => {
        const request = buildUpdateRequest(consolePartial(), { additionalDetails: { beneficiaryType: 'INDIVIDUAL', key: 4 } });

        await enrichAndPersistCampaignForUpdate(request as any);

        const produced = producedAdditionalDetails();
        expect(produced.cloneFrom).toBeUndefined();
        expect(produced.clonedCampaignId).toBeUndefined();
    });
});
