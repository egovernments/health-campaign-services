jest.mock("../config", () => ({
    __esModule: true,
    default: {
        host: {
            attendanceHost: "http://attendance.local",
            healthIndividualHost: "http://individual.local/"
        },
        paths: {
            attendanceRegisterSearch: "/attendance/v1/_search",
            healthIndividualSearch: "individual/v1/_search"
        },
        appTimezone: "UTC"
    }
}));

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), debug: jest.fn(), warn: jest.fn(), error: jest.fn() }
}));

jest.mock("../service/campaignManageService", () => ({
    searchProjectTypeCampaignService: jest.fn()
}));

jest.mock("../utils/request", () => ({
    httpRequest: jest.fn()
}));

jest.mock("../utils/genericUtils", () => ({
    getRelatedDataWithCampaign: jest.fn(),
    throwError: jest.fn((_: string, status: number, code: string, message: string) => {
        const error: any = new Error(message || code);
        error.status = status;
        error.code = code;
        throw error;
    })
}));

import { TemplateClass } from "../generateFlowClasses/attendanceRegisterUserBulkMapping-generateClass";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { httpRequest } from "../utils/request";

const WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
const MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
const APPROVER_SHEET = "HCM_REGISTER_APPROVER_SHEET";
const REGISTER_CODE_COLUMN = "HCM_ATTENDANCE_REGISTER_CODE";
const REGISTER_ID_COLUMN = "HCM_ATTENDANCE_REGISTER_ID";

describe("attendanceRegisterUserBulkMapping-generateClass", () => {
    const mockSearchCampaign = jest.mocked(searchProjectTypeCampaignService);
    const mockGetRelatedData = jest.mocked(getRelatedDataWithCampaign);
    const mockHttpRequest = jest.mocked(httpRequest);

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it("generates 3 attendee tabs with prepended register columns and register seed rows", async () => {
        const campaignStart = Date.UTC(2026, 0, 1);
        const campaignEnd = Date.UTC(2026, 0, 10);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-1",
                campaignNumber: "CMP-1",
                startDate: campaignStart,
                endDate: campaignEnd,
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [
                { id: "reg-uuid-1", serviceCode: "REG-001", name: "Register 001", localityCode: "ADMIN" },
                { id: "reg-uuid-2", serviceCode: "REG-002", name: "Register 002", localityCode: "ADMIN" }
            ]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: "CMP-1",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-worker-1",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-1_ind-1_worker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-001",
                    _sheetName: WORKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-1",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Alice Worker",
                    UserName: "alice.worker",
                    HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "TEAM-1",
                    HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: ""
                }
            },
            {
                campaignNumber: "CMP-1",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-marker-1",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-1_ind-2_marker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-001",
                    _sheetName: MARKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-2",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Bob Marker",
                    UserName: "bob.marker",
                    HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: ""
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-1", requestInfo: {} },
            {}
        );

        expect(sheetMap[WORKER_SHEET].dynamicColumns).toHaveProperty(REGISTER_CODE_COLUMN);
        expect(sheetMap[MARKER_SHEET].dynamicColumns).toHaveProperty(REGISTER_CODE_COLUMN);
        expect(sheetMap[APPROVER_SHEET].dynamicColumns).toHaveProperty(REGISTER_CODE_COLUMN);
        const workerDynamicColumns = sheetMap[WORKER_SHEET].dynamicColumns as Record<string, any>;
        const markerDynamicColumns = sheetMap[MARKER_SHEET].dynamicColumns as Record<string, any>;
        const approverDynamicColumns = sheetMap[APPROVER_SHEET].dynamicColumns as Record<string, any>;
        expect(workerDynamicColumns[REGISTER_ID_COLUMN]?.hideColumn).toBe(true);
        expect(markerDynamicColumns[REGISTER_ID_COLUMN]?.hideColumn).toBe(true);
        expect(approverDynamicColumns[REGISTER_ID_COLUMN]?.hideColumn).toBe(true);
        expect(workerDynamicColumns.HCM_ATTENDANCE_REGISTER_CODE?.color).toBe("#93c47d");
        expect(workerDynamicColumns.HCM_ATTENDANCE_REGISTER_NAME?.color).toBe("#93c47d");
        expect(workerDynamicColumns.HCM_ATTENDANCE_REGISTER_UUID?.color).toBe("#93c47d");

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        const approverRows = sheetMap[APPROVER_SHEET].data as Record<string, string>[];

        expect(workerRows).toHaveLength(2);
        expect(markerRows).toHaveLength(2);
        expect(approverRows).toHaveLength(2);

        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-001");
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_NAME).toBe("Register 001");
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_UUID).toBe("reg-uuid-1");
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_ID).toBe("REG-001");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBe("TEAM-1");

        expect(workerRows[1].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-002");
        expect(workerRows[1].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("");
        expect(workerRows[1].UserName).toBe("");
        expect(workerRows[1].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBe("");

        expect(markerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-001");
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-2");
        expect(markerRows[0].UserName).toBe("bob.marker");
        expect(markerRows[0].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBeUndefined();
        expect(mockHttpRequest).toHaveBeenCalledWith(
            expect.stringContaining("/attendance/v1/_search"),
            expect.anything(),
            expect.objectContaining({
                tenantId: "bednet",
                referenceId: "prj-1",
                localityCode: "ADMIN"
            })
        );
    });

    it("resolves register via uniqueIdAfterProcess and infers tab when _sheetName is absent", async () => {
        const campaignStart = Date.UTC(2026, 1, 1);
        const campaignEnd = Date.UTC(2026, 1, 15);
        const syncedDeenrollment = Date.UTC(2026, 1, 7);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-2",
                campaignNumber: "CMP-2",
                startDate: campaignStart,
                endDate: campaignEnd,
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [{ id: "reg-uuid-99", serviceCode: "REG-099", name: "Register 099", localityCode: "ADMIN" }]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: "CMP-2",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-9",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-99_ind-9_marker",
                isDeleted: false,
                denrollmentDate: syncedDeenrollment,
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "W-9",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Bob Marker",
                    UserName: "bob.marker",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-2", requestInfo: {} },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        expect(markerRows).toHaveLength(1);
        expect(markerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-099");
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Bob Marker");
        expect(markerRows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe("07-02-2026");

        expect((sheetMap[WORKER_SHEET].data as any[])).toHaveLength(1);
        expect((sheetMap[APPROVER_SHEET].data as any[])).toHaveLength(1);
    });

    it("normalizes stored slash-separated dates to dash format in bulk rows", async () => {
        const campaignStart = undefined;
        const campaignEnd = Date.UTC(2026, 2, 31);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-4",
                campaignNumber: "CMP-4",
                startDate: campaignStart,
                endDate: campaignEnd,
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [{ id: "reg-uuid-4", serviceCode: "REG-004", name: "Register 004", localityCode: "ADMIN" }]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: "CMP-4",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-worker-4",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-4_ind-4_worker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-004",
                    _sheetName: WORKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-4",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Date Format User",
                    UserName: "date.format.user",
                    HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "TEAM-4",
                    HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: "05/03/2026",
                    HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE: "20/03/2026",
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-4", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(1);
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe("05-03-2026");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe("20-03-2026");
    });

    it("falls back to live attendance mappings when campaign attendee rows are missing", async () => {
        const campaignStart = Date.UTC(2026, 2, 1);
        const campaignEnd = Date.UTC(2026, 2, 31);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-3",
                campaignNumber: "CMP-3",
                startDate: campaignStart,
                endDate: campaignEnd,
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockGetRelatedData.mockResolvedValue([] as any);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-uuid-501",
                            serviceCode: "REG-501",
                            name: "Register 501",
                            localityCode: "ADMIN",
                            attendees: [
                                {
                                    individualId: "ind-501",
                                    tag: "TEAM-501",
                                    enrollmentDate: Date.UTC(2026, 2, 5),
                                    denrollmentDate: Date.UTC(2026, 2, 20)
                                }
                            ],
                            staff: []
                        },
                        {
                            id: "reg-uuid-777",
                            serviceCode: "REG-777",
                            name: "Register 777",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: []
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        {
                            id: "ind-501",
                            name: { givenName: "Anaya", familyName: "Patel" },
                            userDetails: { username: "anaya.patel" }
                        }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-3", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(2);
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-501");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Anaya Patel");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-501");
        expect(workerRows[0].UserName).toBe("anaya.patel");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBe("TEAM-501");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe("01-03-2026");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe("20-03-2026");

        expect(workerRows[1].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-777");
        expect(workerRows[1].UserName).toBe("");

        expect((sheetMap[MARKER_SHEET].data as any[])).toHaveLength(2);
        expect((sheetMap[APPROVER_SHEET].data as any[])).toHaveLength(2);
    });

    it("prefers request localityCode when provided", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-locality",
                campaignNumber: "CMP-L",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);
        mockGetRelatedData.mockResolvedValue([] as any);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-locality", requestInfo: {}, additionalDetails: { localityCode: "WARD-22" } },
            {}
        );

        expect(mockHttpRequest).toHaveBeenCalledWith(
            expect.stringContaining("/attendance/v1/_search"),
            expect.anything(),
            expect.objectContaining({
                tenantId: "bednet",
                referenceId: "prj-locality",
                localityCode: "WARD-22"
            })
        );
    });

    it("falls back to campaignId when campaign projectId is unavailable", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                campaignNumber: "CMP-F",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);
        mockGetRelatedData.mockResolvedValue([] as any);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-fallback", requestInfo: {} },
            {}
        );

        expect(mockHttpRequest).toHaveBeenCalledWith(
            expect.stringContaining("/attendance/v1/_search"),
            expect.anything(),
            expect.objectContaining({
                tenantId: "bednet",
                referenceId: "cmp-fallback",
                localityCode: "ADMIN"
            })
        );
    });
});
