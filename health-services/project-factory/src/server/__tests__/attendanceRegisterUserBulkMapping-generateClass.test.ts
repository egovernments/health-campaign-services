jest.mock('../config', () => ({
    __esModule: true,
    default: {
        host: {
            attendanceHost: 'http://attendance.local',
            healthIndividualHost: 'http://individual.local/'
        },
        paths: {
            attendanceRegisterSearch: '/attendance/v1/_search',
            healthIndividualSearch: 'individual/v1/_search'
        },
        appTimezone: 'UTC'
    }
}));

jest.mock('../utils/logger', () => ({
    logger: { info: jest.fn(), debug: jest.fn(), warn: jest.fn(), error: jest.fn() }
}));

jest.mock('../service/campaignManageService', () => ({
    searchProjectTypeCampaignService: jest.fn()
}));

jest.mock('../utils/request', () => ({
    httpRequest: jest.fn()
}));

jest.mock('../utils/genericUtils', () => ({
    getRelatedDataWithCampaign: jest.fn(),
    throwError: jest.fn((_: string, status: number, code: string, message: string) => {
        const error: any = new Error(message || code);
        error.status = status;
        error.code = code;
        throw error;
    })
}));

import { TemplateClass } from '../generateFlowClasses/attendanceRegisterUserBulkMapping-generateClass';
import { searchProjectTypeCampaignService } from '../service/campaignManageService';
import { getRelatedDataWithCampaign } from '../utils/genericUtils';
import { httpRequest } from '../utils/request';

const SHEET = 'HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET';

describe('attendanceRegisterUserBulkMapping-generateClass', () => {
    const mockSearchCampaign = jest.mocked(searchProjectTypeCampaignService);
    const mockGetRelatedData = jest.mocked(getRelatedDataWithCampaign);
    const mockHttpRequest = jest.mocked(httpRequest);

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('dedupes register-user rows, merges roles, and adds register-only rows', async () => {
        const campaignStart = Date.UTC(2026, 0, 1);
        const campaignEnd = Date.UTC(2026, 0, 10);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: 'CMP-1', startDate: campaignStart, endDate: campaignEnd }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [
                { id: 'reg-uuid-1', serviceCode: 'REG-001', name: 'Register 001' },
                { id: 'reg-uuid-2', serviceCode: 'REG-002', name: 'Register 002' }
            ]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: 'CMP-1',
                type: 'attendanceRegisterAttendee',
                uniqueIdentifier: 'row-1',
                status: 'completed',
                uniqueIdAfterProcess: 'reg-uuid-1_ind-1_worker',
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: 'REG-001',
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: 'W-1',
                    HCM_ADMIN_CONSOLE_USER_NAME: 'Alice Worker',
                    HCM_ADMIN_CONSOLE_USER_ROLE: 'DISTRIBUTOR',
                    HCM_ADMIN_CONSOLE_BOUNDARY_NAME: 'Boundary A',
                    HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: ''
                }
            },
            {
                campaignNumber: 'CMP-1',
                type: 'attendanceRegisterAttendee',
                uniqueIdentifier: 'row-2',
                status: 'completed',
                uniqueIdAfterProcess: 'reg-uuid-1_ind-1_worker',
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: 'REG-001',
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: 'W-1',
                    HCM_ADMIN_CONSOLE_USER_NAME: 'Alice Worker',
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: 'REGISTRAR',
                    HCM_ADMIN_CONSOLE_BOUNDARY_NAME: 'Boundary A'
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            { sheets: [{ sheetName: SHEET }] },
            { tenantId: 'bednet', campaignId: 'cmp-1', requestInfo: {} },
            {}
        );

        const rows = sheetMap[SHEET].data as Record<string, string>[];
        expect(rows).toHaveLength(2);

        const first = rows[0];
        expect(first.HCM_ATTENDANCE_REGISTER_CODE).toBe('REG-001');
        expect(first.HCM_ATTENDANCE_REGISTER_NAME).toBe('Register 001');
        expect(first.HCM_ATTENDANCE_REGISTER_UUID).toBe('reg-uuid-1');
        expect(first.HCM_ADMIN_CONSOLE_USER_NAME).toBe('Alice Worker');
        expect(first.HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe('W-1');
        expect(first.HCM_ADMIN_CONSOLE_USER_ROLE).toBe('DISTRIBUTOR, REGISTRAR');
        expect(first.HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe('01-01-2026');
        expect(first.HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe('10-01-2026');

        const second = rows[1];
        expect(second.HCM_ATTENDANCE_REGISTER_CODE).toBe('REG-002');
        expect(second.HCM_ADMIN_CONSOLE_USER_NAME).toBe('');
        expect(second.HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe('');
    });

    it('resolves register using uniqueIdAfterProcess when register code is absent in row data', async () => {
        const campaignStart = Date.UTC(2026, 1, 1);
        const campaignEnd = Date.UTC(2026, 1, 15);
        const syncedDeenrollment = Date.UTC(2026, 1, 7);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: 'CMP-2', startDate: campaignStart, endDate: campaignEnd }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [{ id: 'reg-uuid-99', serviceCode: 'REG-099', name: 'Register 099' }]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: 'CMP-2',
                type: 'attendanceRegisterAttendee',
                uniqueIdentifier: 'row-9',
                status: 'completed',
                uniqueIdAfterProcess: 'reg-uuid-99_ind-9_worker',
                isDeleted: false,
                denrollmentDate: syncedDeenrollment,
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: 'W-9',
                    HCM_ADMIN_CONSOLE_USER_NAME: 'Bob Marker',
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: 'TEAM_SUPERVISOR',
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: 'BOUNDARY-9'
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            { sheets: [{ sheetName: SHEET }] },
            { tenantId: 'bednet', campaignId: 'cmp-2', requestInfo: {} },
            {}
        );

        const rows = sheetMap[SHEET].data as Record<string, string>[];
        expect(rows).toHaveLength(1);
        expect(rows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe('REG-099');
        expect(rows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe('Bob Marker');
        expect(rows[0].HCM_ADMIN_CONSOLE_USER_ROLE).toBe('TEAM_SUPERVISOR');
        expect(rows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe('07-02-2026');
    });

    it('falls back to live attendance mappings when campaign attendee rows are missing', async () => {
        const campaignStart = Date.UTC(2026, 2, 1);
        const campaignEnd = Date.UTC(2026, 2, 31);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: 'CMP-3', startDate: campaignStart, endDate: campaignEnd }]
        } as any);

        mockGetRelatedData.mockResolvedValue([] as any);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes('/attendance/v1/_search')) {
                return {
                    attendanceRegister: [
                        {
                            id: 'reg-uuid-501',
                            serviceCode: 'REG-501',
                            name: 'Register 501',
                            localityCode: 'ADMIN',
                            attendees: [
                                {
                                    individualId: 'ind-501',
                                    enrollmentDate: Date.UTC(2026, 2, 5),
                                    denrollmentDate: Date.UTC(2026, 2, 20)
                                }
                            ],
                            staff: []
                        }
                    ]
                } as any;
            }
            if (url.includes('individual/v1/_search')) {
                return {
                    Individual: [
                        {
                            id: 'ind-501',
                            name: { givenName: 'Anaya', familyName: 'Patel' },
                            userDetails: { username: 'anaya.patel' }
                        }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            { sheets: [{ sheetName: SHEET }] },
            { tenantId: 'bednet', campaignId: 'cmp-3', requestInfo: {} },
            {}
        );

        const rows = sheetMap[SHEET].data as Record<string, string>[];
        expect(rows).toHaveLength(1);
        expect(rows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe('REG-501');
        expect(rows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe('Anaya Patel');
        expect(rows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe('ind-501');
        expect(rows[0].HCM_ADMIN_CONSOLE_USER_ROLE).toBe('WORKER');
        expect(rows[0].HCM_ADMIN_CONSOLE_BOUNDARY_NAME).toBe('ADMIN');
        expect(rows[0].HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe('05-03-2026');
        expect(rows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe('20-03-2026');
    });
});
