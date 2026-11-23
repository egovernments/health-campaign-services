jest.mock('../api/coreApis', () => ({ searchMDMSDataViaV2Api: jest.fn() }));

import { searchMDMSDataViaV2Api } from '../api/coreApis';
import { campaignTypeOffersOutsideHousehold } from '../utils/deliveryMethodUtils';

const record = (deliveryMethods: unknown, isActive = true): any => ({ isActive, data: { code: 'T', deliveryMethods } });

describe('campaignTypeOffersOutsideHousehold', () => {
    afterEach(() => jest.clearAllMocks());

    describe('reads the projectTypes record of the campaign type', () => {
        it('asks MDMS for the active record with that code at the campaign tenant', async () => {
            jest.mocked(searchMDMSDataViaV2Api).mockResolvedValueOnce({ mdms: [] });
            await campaignTypeOffersOutsideHousehold('dev', ' Immunisation ');
            expect(searchMDMSDataViaV2Api).toHaveBeenCalledWith({
                MdmsCriteria: { tenantId: 'dev', schemaCode: 'HCM-PROJECT-TYPES.projectTypes', filters: { code: 'Immunisation' }, isActive: true }
            });
        });

        it('is true when deliveryMethods list OUTSIDEHOUSEHOLD as a {code} object, as the console stores them', async () => {
            jest.mocked(searchMDMSDataViaV2Api).mockResolvedValueOnce({ mdms: [record([{ code: 'HOUSEHOLDSTRATEGY', order: 1 }, { code: 'OUTSIDEHOUSEHOLD', order: 2 }])] });
            expect(await campaignTypeOffersOutsideHousehold('dev', 'POLIO')).toBe(true);
        });

        it('is true for a plain code, compared as excel-ingestion compares it', async () => {
            jest.mocked(searchMDMSDataViaV2Api).mockResolvedValueOnce({ mdms: [record(['outside-household'])] });
            expect(await campaignTypeOffersOutsideHousehold('dev', 'SMC')).toBe(true);
        });

        it('is false when the record lists no OUTSIDEHOUSEHOLD, has no deliveryMethods, is inactive, or does not exist', async () => {
            for (const mdms of [[record([{ code: 'HOUSEHOLDSTRATEGY' }])], [record(undefined)], [record(['OUTSIDEHOUSEHOLD'], false)], []]) {
                jest.mocked(searchMDMSDataViaV2Api).mockResolvedValueOnce({ mdms });
                expect(await campaignTypeOffersOutsideHousehold('dev', 'MR-DN')).toBe(false);
            }
        });
    });

    describe('edge cases', () => {
        it('is false without a tenant or campaign type, and does not call MDMS', async () => {
            for (const [tenantId, projectType] of [[undefined, 'POLIO'], [' ', 'POLIO'], ['dev', ''], ['dev', '  '], ['dev', 42]]) {
                expect(await campaignTypeOffersOutsideHousehold(tenantId, projectType)).toBe(false);
            }
            expect(searchMDMSDataViaV2Api).not.toHaveBeenCalled();
        });

        it('lets a failed MDMS call throw', async () => {
            jest.mocked(searchMDMSDataViaV2Api).mockRejectedValueOnce(new Error('MDMS down'));
            await expect(campaignTypeOffersOutsideHousehold('dev', 'POLIO')).rejects.toThrow('MDMS down');
        });
    });
});
