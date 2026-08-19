/**
 * A register deleted in the attendance service can be re-created under the same serviceCode, so the
 * stored-row lookup must not hand the new register its predecessor's attendees.
 */

jest.mock('../config', () => ({ default: { host: {}, paths: {}, kafka: {} }, __esModule: true }));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), error: jest.fn(), warn: jest.fn() },
}));

jest.mock('../utils/campaignUtils', () => ({ getLocalizedName: jest.fn((key: string) => key) }));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
    throwError: jest.fn(),
}));

jest.mock('../service/campaignManageService', () => ({ searchProjectTypeCampaignService: jest.fn() }));
jest.mock('../api/coreApis', () => ({ searchBoundaryRelationshipData: jest.fn() }));
jest.mock('../utils/cryptUtils', () => ({ decrypt: jest.fn() }));
jest.mock('../utils/request', () => ({ httpRequest: jest.fn() }));

import { TemplateClass } from '../generateFlowClasses/attendanceRegisterAttendee-generateClass';

const SERVICE_CODE = 'MZ_01_REG';
const CURRENT_UUID = 'reg-uuid-new';

const storedRow = (overrides: Record<string, unknown> = {}) => ({
    data: { _registerServiceCode: SERVICE_CODE, _sheetName: 'HCM_REGISTER_WORKER_SHEET', UserName: 'user-1' },
    uniqueIdAfterProcess: null as string | null,
    denrollmentDate: null as number | null,
    ...overrides,
});

const rowsFor = (rows: any[], uuid = CURRENT_UUID) =>
    TemplateClass.storedRowsForRegister(rows, SERVICE_CODE, uuid);

describe('storedRowsForRegister', () => {
    it('keeps rows stamped with the register being generated', () => {
        const rows = [storedRow({ uniqueIdAfterProcess: `${CURRENT_UUID}_ind-1_worker` })];

        expect(rowsFor(rows)).toHaveLength(1);
    });

    it('drops rows stamped with a different register, which shared the serviceCode', () => {
        const rows = [storedRow({ uniqueIdAfterProcess: 'reg-uuid-old_ind-1_worker' })];

        expect(rowsFor(rows)).toHaveLength(0);
    });

    it('drops unstamped rows once any row carries a stamp, since they may be the deleted register\'s', () => {
        const rows = [
            storedRow({ uniqueIdAfterProcess: `${CURRENT_UUID}_ind-1_worker` }),
            storedRow({ uniqueIdAfterProcess: null, data: { _registerServiceCode: SERVICE_CODE, UserName: 'legacy' } }),
        ];

        const kept = rowsFor(rows);

        expect(kept).toHaveLength(1);
        expect(kept[0].uniqueIdAfterProcess).toBe(`${CURRENT_UUID}_ind-1_worker`);
    });

    it('keeps unstamped rows when nothing is stamped, so campaigns older than the stamp still generate', () => {
        const rows = [storedRow(), storedRow({ data: { _registerServiceCode: SERVICE_CODE, UserName: 'legacy-2' } })];

        expect(rowsFor(rows)).toHaveLength(2);
    });

    it('ignores rows belonging to another register entirely', () => {
        const rows = [storedRow({ data: { _registerServiceCode: 'MZ_02_REG', UserName: 'other' } })];

        expect(rowsFor(rows)).toHaveLength(0);
    });

    it('falls back to the serviceCode alone when the register carries no id', () => {
        const rows = [
            storedRow({ uniqueIdAfterProcess: 'reg-uuid-old_ind-1_worker' }),
            storedRow(),
        ];

        expect(rowsFor(rows, '')).toHaveLength(2);
    });
});
