/**
 * The credential sheet keeps every created user's worker registry id ("User ID") in the file, with the column hidden.
 */

jest.mock('../config', () => ({ default: { host: {}, paths: {}, kafka: {} }, __esModule: true }));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../utils/campaignUtils', () => ({ getLocalizedName: jest.fn((key: string) => key), populateBoundariesRecursively: jest.fn() }));
jest.mock('../utils/genericUtils', () => ({ getRelatedDataWithCampaign: jest.fn() }));
jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../api/coreApis', () => ({ searchBoundaryRelationshipData: jest.fn(), searchBoundaryRelationshipDefinition: jest.fn() }));
jest.mock('../utils/cryptUtils', () => ({ decrypt: jest.fn((value: string) => value) }));
jest.mock('../utils/workerRegistryUtils', () => ({ searchWorkersByIds: jest.fn() }));

import { TemplateClass } from '../generateFlowClasses/userCredential-generateClass';
import { getRelatedDataWithCampaign } from '../utils/genericUtils';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { userCredentialFields, userDataFields } from '../config/constants';

const WORKER_ID = 'WR-demo-1';

describe('user credential sheet', () => {
    beforeEach(() => {
        (searchProjectTypeCampaignService as jest.Mock).mockResolvedValue({ CampaignDetails: [{ campaignNumber: 'CMP-1' }] });
        (getRelatedDataWithCampaign as jest.Mock).mockImplementation(async (_type: string, _number: string, _tenant: string, status: string) =>
            status === 'completed'
                ? [{ data: { [userDataFields.workerId]: WORKER_ID, [userCredentialFields.userName]: 'USR-1', [userCredentialFields.password]: 'pw' } }]
                : []);
    });

    it('hides the User ID column and still writes its value', async () => {
        const sheetMap: any = await TemplateClass.generate({}, { tenantId: 'mz', type: 'userCredential', campaignId: 'c-1' }, {});
        const userSheet = sheetMap['HCM_ADMIN_CONSOLE_USER_LIST'];

        expect(userSheet.dynamicColumns[userDataFields.workerId]).toEqual({ hideColumn: true });
        expect(userSheet.data[0][userDataFields.workerId]).toBe(WORKER_ID);
    });

    it('keeps the password and boundary name columns shown', async () => {
        const sheetMap: any = await TemplateClass.generate({}, { tenantId: 'mz', type: 'userCredential', campaignId: 'c-1' }, {});
        const columns = sheetMap['HCM_ADMIN_CONSOLE_USER_LIST'].dynamicColumns;

        expect(columns[userCredentialFields.password]).toEqual({ hideColumn: false });
        expect(columns[userDataFields.boundaryName]).toEqual({ hideColumn: false });
    });
});
