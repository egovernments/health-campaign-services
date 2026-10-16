const mockConfig = {
    basesecret: "unit-test-secret",
    host: {
        attendanceHost: "http://attendance.local",
        healthIndividualHost: "http://individual.local/",
        hrmsHost: "http://hrms.local/"
    },
    paths: {
        attendanceRegisterSearch: "/attendance/v1/_search",
        healthIndividualSearch: "individual/v1/_search",
        hrmsEmployeeSearch: "health-hrms/employees/_search"
    },
    hrms: {
        hrmsParallelSearchLimit: 100
    },
    appTimezone: "UTC",
    attendanceRegister: {
        registerSearchPageLimit: 200,
        registerSearchReferenceIdChunkSize: 100
    }
};

jest.mock("../config", () => ({
    __esModule: true,
    default: mockConfig
}));

jest.mock("../utils/logger", () => ({
    logger: { info: jest.fn(), debug: jest.fn(), warn: jest.fn(), error: jest.fn() }
}));

jest.mock("../api/coreApis", () => ({
    searchBoundaryRelationshipData: jest.fn()
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
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getRelatedDataWithCampaign } from "../utils/genericUtils";
import { httpRequest } from "../utils/request";

const WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
const MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
const APPROVER_SHEET = "HCM_REGISTER_APPROVER_SHEET";
const REGISTER_CODE_COLUMN = "HCM_ATTENDANCE_REGISTER_CODE";
const REGISTER_ID_COLUMN = "HCM_ATTENDANCE_REGISTER_ID";
const BOUNDARY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_NAME";

describe("attendanceRegisterUserBulkMapping-generateClass", () => {
    const mockSearchCampaign = jest.mocked(searchProjectTypeCampaignService);
    const mockGetRelatedData = jest.mocked(getRelatedDataWithCampaign);
    const mockHttpRequest = jest.mocked(httpRequest);
    const mockBoundaryRelationship = jest.mocked(searchBoundaryRelationshipData);

    const boundaryTree = (nodes: any[]) => ({ TenantBoundary: [{ boundary: nodes }] } as any);
    const boundaryRow = (code: string, projectId: string | null) => ({
        type: "boundary",
        uniqueIdentifier: code,
        uniqueIdAfterProcess: projectId,
        status: "completed",
        isDeleted: false,
        data: {}
    });
    const routeRelatedData = (boundaryRows: any[], attendeeRows: any[] = [], userRows: any[] = []) =>
        mockGetRelatedData.mockImplementation(async (type: string) => {
            if (type === "boundary") return boundaryRows as any;
            if (type === "attendanceRegisterAttendee") return attendeeRows as any;
            if (type === "user") return userRows as any;
            return [] as any;
        });

    beforeEach(() => {
        jest.clearAllMocks();
        mockConfig.attendanceRegister.registerSearchPageLimit = 200;
        mockConfig.attendanceRegister.registerSearchReferenceIdChunkSize = 100;
    });

    it("classifies CAMPAIGN_SUPERVISOR into approver sheet for bulk mapping", () => {
        const sheet = (TemplateClass as any).classifyCampaignUserToSheet({
            HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "CAMPAIGN_SUPERVISOR"
        });
        expect(sheet).toBe(APPROVER_SHEET);
    });

    it("generates 3 attendee tabs using only actual mapped rows (no empty-register seeds)", async () => {
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
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR",
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

        expect(workerRows).toHaveLength(1);
        expect(markerRows).toHaveLength(1);
        expect(approverRows).toHaveLength(0);

        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-001");
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_NAME).toBe("Register 001");
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_UUID).toBe("reg-uuid-1");
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_ID).toBe("REG-001");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBe("TEAM-1");

        expect(markerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-001");
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-2");
        expect(markerRows[0].UserName).toBe("bob.marker");
        expect(markerRows[0].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBeUndefined();
        expect(mockHttpRequest).toHaveBeenCalledWith(
            expect.stringContaining("/attendance/v1/_search"),
            expect.anything(),
            expect.objectContaining({
                tenantId: "bednet",
                campaignNumber: "CMP-1",
                includeAttendee: true,
                includeStaff: true
            })
        );
        expect(mockHttpRequest).not.toHaveBeenCalledWith(
            expect.anything(),
            expect.anything(),
            expect.objectContaining({ localityCode: "ADMIN" })
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

        expect((sheetMap[WORKER_SHEET].data as any[])).toHaveLength(0);
        expect((sheetMap[APPROVER_SHEET].data as any[])).toHaveLength(0);
    });

    it("drops marker rows that do not carry any role columns", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-no-role",
                campaignNumber: "CMP-NOROLE",
                startDate: Date.UTC(2026, 1, 1),
                endDate: Date.UTC(2026, 1, 15),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [{ id: "reg-no-role", serviceCode: "REG-NOROLE", name: "Register No Role", localityCode: "ADMIN" }]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: "CMP-NOROLE",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-marker-no-role",
                status: "completed",
                uniqueIdAfterProcess: "reg-no-role_ind-99_marker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-NOROLE",
                    _sheetName: MARKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-99",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Roleless Marker",
                    UserName: "roleless.marker"
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-no-role", requestInfo: {} },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        expect(markerRows).toHaveLength(0);
    });

    it("resolves register from serviceCode-prefixed uniqueIdAfterProcess when register columns are absent", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-2b",
                campaignNumber: "CMP-2B",
                startDate: Date.UTC(2026, 1, 1),
                endDate: Date.UTC(2026, 1, 15),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [{ id: "reg-uuid-123", serviceCode: "REG-123", name: "Register 123", localityCode: "ADMIN" }]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: "CMP-2B",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-123",
                status: "completed",
                uniqueIdAfterProcess: "REG-123_user.123_worker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-123",
                    HCM_ADMIN_CONSOLE_USER_NAME: "User 123",
                    UserName: "user.123"
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-2b", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(1);
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-123");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-123");
        expect(workerRows[0].UserName).toBe("user.123");
    });

    it("keeps enrollment empty when campaign start date is unavailable and normalizes de-enrollment format", async () => {
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
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe("");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe("20-03-2026");
    });

    it("prefills enrollment using the next calendar day when start epoch collapses to created day", async () => {
        const campaignStart = 1789669800000; // 18-09-2026 00:00 in +05:30, appears as 17-09-2026 in UTC
        const campaignEnd = Date.UTC(2026, 10, 1);
        const createdTime = 1789652368751;

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-tz-shift",
                campaignNumber: "CMP-TZ-SHIFT",
                startDate: campaignStart,
                endDate: campaignEnd,
                boundaries: [{ code: "ADMIN" }],
                auditDetails: { createdTime },
                deliveryRules: [{
                    cycles: [{
                        startDate: campaignStart,
                        endDate: campaignEnd
                    }]
                }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [{ id: "reg-uuid-tz", serviceCode: "REG-TZ", name: "Register TZ", localityCode: "ADMIN" }]
        } as any);

        mockGetRelatedData.mockResolvedValue([
            {
                campaignNumber: "CMP-TZ-SHIFT",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-worker-tz",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-tz_ind-tz_worker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-TZ",
                    _sheetName: WORKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-tz",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Timezone Worker",
                    UserName: "timezone.worker",
                    HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "TEAM-TZ",
                    HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE: ""
                }
            }
        ] as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-tz-shift", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(1);
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe("18-09-2026");
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
        expect(workerRows).toHaveLength(1);
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-501");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Anaya Patel");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-501");
        expect(workerRows[0].UserName).toBe("anaya.patel");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBe("TEAM-501");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe("01-03-2026");
        expect(workerRows[0].HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE).toBe("20-03-2026");

        expect((sheetMap[MARKER_SHEET].data as any[])).toHaveLength(0);
        expect((sheetMap[APPROVER_SHEET].data as any[])).toHaveLength(0);
    });

    it("uses localized boundary names for frontline workers built from live attendance", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-boundary-localized",
                campaignNumber: "CMP-BOUNDARY-LOCALIZED",
                startDate: Date.UTC(2026, 2, 1),
                endDate: Date.UTC(2026, 2, 31),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockGetRelatedData.mockResolvedValue([] as any);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-boundary-1",
                            serviceCode: "REG-B1",
                            name: "Register Boundary 1",
                            localityCode: "WARD-1",
                            attendees: [
                                {
                                    individualId: "ind-b1",
                                    tag: "TEAM-B1",
                                    enrollmentDate: Date.UTC(2026, 2, 5),
                                    denrollmentDate: null
                                }
                            ],
                            staff: []
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        {
                            id: "ind-b1",
                            name: { givenName: "Boundary", familyName: "Worker" },
                            userDetails: { username: "boundary.worker" }
                        }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-boundary-localized", requestInfo: {} },
            { "WARD-1": "Ward One" }
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(1);
        expect(workerRows[0][BOUNDARY_COLUMN]).toBe("Ward One");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY).toBe("WARD-1");
    });

    it("retains campaign-user coverage for all registers even when live attendance is partial", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-partial",
                campaignNumber: "CMP-PARTIAL",
                startDate: Date.UTC(2026, 3, 1),
                endDate: Date.UTC(2026, 3, 30),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-template",
                uniqueIdAfterProcess: "ind-template",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-template",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Template User",
                    UserName: "template.user",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN",
                    HCM_ATTENDANCE_ATTENDEE_TEAM_CODE: "TEAM-T"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-partial-1",
                            serviceCode: "REG-P1",
                            name: "Register P1",
                            localityCode: "ADMIN",
                            attendees: [{ individualId: "ind-live-1", tag: "TEAM-L1", enrollmentDate: Date.UTC(2026, 3, 2), denrollmentDate: null }],
                            staff: []
                        },
                        {
                            id: "reg-partial-2",
                            serviceCode: "REG-P2",
                            name: "Register P2",
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
                        { id: "ind-live-1", name: { givenName: "Live", familyName: "User" }, userDetails: { username: "live.user" } }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-partial", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const registerCodes = Array.from(new Set(workerRows.map((row) => row.HCM_ATTENDANCE_REGISTER_CODE)));

        expect(registerCodes).toEqual(["REG-P1", "REG-P2"]);
        expect(workerRows.some((row) =>
            row.HCM_ATTENDANCE_REGISTER_CODE === "REG-P2"
            && row.HCM_ADMIN_CONSOLE_USER_WORKER_ID === "ind-template"
        )).toBe(true);
    });

    it("prefills approver rows (but not marker rows) from campaign users when register staff is absent", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-staff-empty",
                campaignNumber: "CMP-STAFF-EMPTY",
                startDate: Date.UTC(2026, 4, 1),
                endDate: Date.UTC(2026, 4, 31),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-login",
                uniqueIdAfterProcess: "ind-login",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-login",
                    HCM_ADMIN_CONSOLE_USER_NAME: "BEDNET-CM-1",
                    UserName: "BEDNET-CM-1",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN"
                }
            },
            {
                uniqueIdentifier: "ind-appr",
                uniqueIdAfterProcess: "ind-appr",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-appr",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Approver User",
                    UserName: "approver.user",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "PROXIMITY_SUPERVISOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN"
                }
            },
            {
                uniqueIdentifier: "ind-worker",
                uniqueIdAfterProcess: "ind-worker",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-worker",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Worker User",
                    UserName: "worker.user",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN"
                }
            }
        ]);

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [
                {
                    id: "reg-empty-1",
                    serviceCode: "REG-E1",
                    name: "Register Empty 1",
                    localityCode: "ADMIN",
                    attendees: [],
                    staff: []
                }
            ]
        } as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-staff-empty", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        const approverRows = sheetMap[APPROVER_SHEET].data as Record<string, string>[];

        expect(workerRows).toHaveLength(1);
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-worker");
        expect(markerRows).toHaveLength(0);
        expect(approverRows).toHaveLength(1);
        expect(approverRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-appr");
    });

    it("does not cross-join campaign users across registers when boundary differs", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-boundary-split",
                campaignNumber: "CMP-SPLIT",
                startDate: Date.UTC(2026, 4, 1),
                endDate: Date.UTC(2026, 4, 31),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-w1",
                uniqueIdAfterProcess: "ind-w1",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-w1",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Worker One",
                    UserName: "worker.one",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "WARD-1"
                }
            },
            {
                uniqueIdentifier: "ind-w2",
                uniqueIdAfterProcess: "ind-w2",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-w2",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Worker Two",
                    UserName: "worker.two",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "WARD-2"
                }
            },
            {
                uniqueIdentifier: "ind-w3",
                uniqueIdAfterProcess: "ind-w3",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-w3",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Worker Three",
                    UserName: "worker.three",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "WARD-2"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-split-1",
                            serviceCode: "REG-S1",
                            name: "Register Split 1",
                            localityCode: "WARD-1",
                            attendees: [],
                            staff: []
                        },
                        {
                            id: "reg-split-2",
                            serviceCode: "REG-S2",
                            name: "Register Split 2",
                            localityCode: "WARD-2",
                            attendees: [],
                            staff: []
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return { Individual: [] } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-split", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const register1Workers = workerRows
            .filter((row) => row.HCM_ATTENDANCE_REGISTER_CODE === "REG-S1")
            .map((row) => row.HCM_ADMIN_CONSOLE_USER_WORKER_ID)
            .sort();
        const register2Workers = workerRows
            .filter((row) => row.HCM_ATTENDANCE_REGISTER_CODE === "REG-S2")
            .map((row) => row.HCM_ADMIN_CONSOLE_USER_WORKER_ID)
            .sort();

        expect(workerRows).toHaveLength(3);
        expect(register1Workers).toEqual(["ind-w1"]);
        expect(register2Workers).toEqual(["ind-w2", "ind-w3"]);
    });

    it("maps approvers from ancestor boundaries while keeping worker boundary matching strict", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-approver-ancestor",
                campaignNumber: "CMP-APPROVER-ANCESTOR",
                startDate: Date.UTC(2026, 4, 1),
                endDate: Date.UTC(2026, 4, 31),
                boundaries: [{ code: "STATE-1" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-worker-parent",
                uniqueIdAfterProcess: "ind-worker-parent",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-worker-parent",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Worker Parent",
                    UserName: "worker.parent",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "LGA-1"
                }
            },
            {
                uniqueIdentifier: "ind-approver-parent",
                uniqueIdAfterProcess: "ind-approver-parent",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-approver-parent",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Approver Parent",
                    UserName: "approver.parent",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "PROXIMITY_SUPERVISOR",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "LGA-1"
                }
            }
        ]);

        mockBoundaryRelationship.mockResolvedValue(boundaryTree([
            {
                code: "LGA-1",
                boundaryType: "LGA",
                children: [
                    { code: "DH-1", boundaryType: "DISTRIBUTION_HUB", children: [] }
                ]
            }
        ]));

        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [
                {
                    id: "reg-ancestor-1",
                    serviceCode: "REG-A1",
                    name: "Register Ancestor 1",
                    localityCode: "DH-1",
                    attendees: [],
                    staff: []
                }
            ]
        } as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-approver-ancestor", hierarchyType: "ADMIN", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const approverRows = sheetMap[APPROVER_SHEET].data as Record<string, string>[];

        expect(workerRows).toHaveLength(0);
        expect(approverRows).toHaveLength(1);
        expect(approverRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-approver-parent");
        expect(approverRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-A1");
        expect(mockBoundaryRelationship).toHaveBeenCalledTimes(1);
        expect(mockBoundaryRelationship).toHaveBeenCalledWith(
            "bednet",
            "ADMIN",
            false,
            true,
            false,
            "DH-1",
            {}
        );
    });

    it("supplements partial stored mappings with live attendance rows for missing registers", async () => {
        const campaignEnd = Date.UTC(2026, 3, 30);

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-5",
                campaignNumber: "CMP-5",
                startDate: undefined,
                endDate: campaignEnd,
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [
            {
                campaignNumber: "CMP-5",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-worker-5",
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
            }
        ], [
            {
                uniqueIdentifier: "ind-9",
                uniqueIdAfterProcess: "ind-9",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-9",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR"
                }
            },
            {
                uniqueIdentifier: "ind-10",
                uniqueIdAfterProcess: "ind-10",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-10",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-uuid-1",
                            serviceCode: "REG-001",
                            name: "Register 001",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: []
                        },
                        {
                            id: "reg-uuid-2",
                            serviceCode: "REG-002",
                            name: "Register 002",
                            localityCode: "ADMIN",
                            attendees: [
                                {
                                    individualId: "ind-9",
                                    tag: "TEAM-9",
                                    enrollmentDate: Date.UTC(2026, 3, 2),
                                    denrollmentDate: null
                                }
                            ],
                            staff: [
                                {
                                    userId: "ind-10",
                                    staffType: "OWNER",
                                    enrollmentDate: Date.UTC(2026, 3, 3),
                                    denrollmentDate: null,
                                    additionalDetails: { staffName: "Marker Nine" }
                                }
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        {
                            id: "ind-9",
                            name: { givenName: "Nina", familyName: "Khan" },
                            userDetails: { username: "nina.khan" }
                        },
                        {
                            id: "ind-10",
                            name: { givenName: "Mark", familyName: "Owner" },
                            userDetails: { username: "mark.owner" }
                        }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-5", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        const approverRows = sheetMap[APPROVER_SHEET].data as Record<string, string>[];

        expect(workerRows).toHaveLength(2);
        expect(workerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-001");
        expect(workerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-1");
        expect(workerRows[1].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-002");
        expect(workerRows[1].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-9");
        expect(workerRows[1].UserName).toBe("nina.khan");
        expect(workerRows[1].HCM_ATTENDANCE_ATTENDEE_TEAM_CODE).toBe("TEAM-9");

        expect(markerRows).toHaveLength(1);
        expect(markerRows[0].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-002");
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-10");
        expect(markerRows[0].UserName).toBe("mark.owner");

        expect(approverRows).toHaveLength(0);
    });

    it("supplements missing frontline worker rows even when registers already appear in marker rows", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-5b",
                campaignNumber: "CMP-5B",
                startDate: Date.UTC(2026, 4, 1),
                endDate: Date.UTC(2026, 4, 30),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [
            {
                campaignNumber: "CMP-5B",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-marker-1",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-1_ind-m1_marker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-001",
                    _sheetName: MARKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-m1",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Stored Marker One",
                    UserName: "stored.marker.one"
                }
            },
            {
                campaignNumber: "CMP-5B",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-marker-2",
                status: "completed",
                uniqueIdAfterProcess: "reg-uuid-2_ind-m2_marker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-002",
                    _sheetName: MARKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-m2",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Stored Marker Two",
                    UserName: "stored.marker.two"
                }
            }
        ], [
            {
                uniqueIdentifier: "ind-w1",
                uniqueIdAfterProcess: "ind-w1",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-w1",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR"
                }
            },
            {
                uniqueIdentifier: "ind-w2",
                uniqueIdAfterProcess: "ind-w2",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-w2",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR"
                }
            },
            {
                uniqueIdentifier: "ind-m1",
                uniqueIdAfterProcess: "ind-m1",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-m1",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            },
            {
                uniqueIdentifier: "ind-m2",
                uniqueIdAfterProcess: "ind-m2",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-m2",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-uuid-1",
                            serviceCode: "REG-001",
                            name: "Register 001",
                            localityCode: "ADMIN",
                            attendees: [{ individualId: "ind-w1", tag: "TEAM-1", enrollmentDate: Date.UTC(2026, 4, 2), denrollmentDate: null }],
                            staff: [{ userId: "ind-m1", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 4, 2), denrollmentDate: null }]
                        },
                        {
                            id: "reg-uuid-2",
                            serviceCode: "REG-002",
                            name: "Register 002",
                            localityCode: "ADMIN",
                            attendees: [{ individualId: "ind-w2", tag: "TEAM-2", enrollmentDate: Date.UTC(2026, 4, 3), denrollmentDate: null }],
                            staff: [{ userId: "ind-m2", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 4, 3), denrollmentDate: null }]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-w1", name: { givenName: "Worker", familyName: "One" }, userDetails: { username: "worker.one" } },
                        { id: "ind-w2", name: { givenName: "Worker", familyName: "Two" }, userDetails: { username: "worker.two" } },
                        { id: "ind-m1", name: { givenName: "Marker", familyName: "One" }, userDetails: { username: "marker.one" } },
                        { id: "ind-m2", name: { givenName: "Marker", familyName: "Two" }, userDetails: { username: "marker.two" } },
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-5b", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];

        expect(workerRows).toHaveLength(2);
        expect(workerRows.map((row) => row.HCM_ATTENDANCE_REGISTER_CODE)).toEqual(["REG-001", "REG-002"]);
        expect(workerRows.map((row) => row.HCM_ADMIN_CONSOLE_USER_WORKER_ID)).toEqual(["ind-w1", "ind-w2"]);

        expect(markerRows).toHaveLength(2);
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Marker One");
        expect(markerRows[1].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Marker Two");
    });

    it("prefers stored marker mappings over live staff mappings when stored roles are present", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-stored-priority",
                campaignNumber: "CMP-STORED-PRIORITY",
                startDate: Date.UTC(2026, 4, 1),
                endDate: Date.UTC(2026, 4, 30),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [
            {
                campaignNumber: "CMP-STORED-PRIORITY",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-marker-stored",
                status: "completed",
                uniqueIdAfterProcess: "reg-priority-1_ind-stored-marker_marker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-PRIORITY-1",
                    _sheetName: MARKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-stored-marker",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Stored Marker",
                    UserName: "stored.marker",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ], []);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-priority-1",
                            serviceCode: "REG-PRIORITY-1",
                            name: "Register Priority 1",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: [
                                { userId: "ind-live-marker", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 4, 2), denrollmentDate: null }
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-live-marker", name: { givenName: "Live", familyName: "Marker" }, userDetails: { username: "live.marker" } },
                        { id: "ind-stored-marker", name: { givenName: "Stored", familyName: "Marker" }, userDetails: { username: "stored.marker" } }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-stored-priority", requestInfo: {} },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        expect(markerRows).toHaveLength(1);
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-stored-marker");
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Stored Marker");
        expect(markerRows[0].UserName).toBe("stored.marker");
    });

    it("ignores stored rows stamped for old register instances that reuse service codes", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-register-recreated",
                campaignNumber: "CMP-REGISTER-RECREATED",
                startDate: Date.UTC(2026, 4, 1),
                endDate: Date.UTC(2026, 4, 30),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [
            {
                campaignNumber: "CMP-REGISTER-RECREATED",
                type: "attendanceRegisterAttendee",
                uniqueIdentifier: "row-marker-old-instance",
                status: "completed",
                uniqueIdAfterProcess: "reg-old-uuid_ind-old-marker_marker",
                isDeleted: false,
                denrollmentDate: null,
                data: {
                    _registerServiceCode: "REG-REC-1",
                    _sheetName: MARKER_SHEET,
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-old-marker",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Old Instance Marker",
                    UserName: "old.marker",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ], []);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-current-uuid",
                            serviceCode: "REG-REC-1",
                            name: "Register Recreated",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: [
                                { userId: "ind-live-marker", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 4, 2), denrollmentDate: null }
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-live-marker", name: { givenName: "Live", familyName: "Marker" }, userDetails: { username: "live.marker" } },
                        { id: "ind-old-marker", name: { givenName: "Old", familyName: "Marker" }, userDetails: { username: "old.marker" } }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-register-recreated", requestInfo: {} },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        expect(markerRows).toHaveLength(1);
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-live-marker");
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_NAME).toBe("Live Marker");
        expect(markerRows[0].UserName).toBe("live.marker");
    });

    it("maps attendance staff to marker/approver sheets by staffType even when user roles differ", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-role",
                campaignNumber: "CMP-ROLE",
                startDate: Date.UTC(2026, 5, 1),
                endDate: Date.UTC(2026, 5, 30),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-admin",
                uniqueIdAfterProcess: "ind-admin",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-admin",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "ADMIN"
                }
            },
            {
                uniqueIdentifier: "ind-marker",
                uniqueIdAfterProcess: "ind-marker",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-marker",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR"
                }
            },
            {
                uniqueIdentifier: "ind-approver",
                uniqueIdAfterProcess: "ind-approver",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-approver",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-role-1",
                            serviceCode: "REG-ROLE-1",
                            name: "Register Role 1",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: [
                                { userId: "ind-admin", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 5, 2), denrollmentDate: null },
                                { userId: "ind-marker", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 5, 2), denrollmentDate: null },
                                { userId: "ind-approver", staffType: "APPROVER", enrollmentDate: Date.UTC(2026, 5, 2), denrollmentDate: null },
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-admin", name: { givenName: "Admin", familyName: "User" }, userDetails: { username: "admin.user" } },
                        { id: "ind-marker", name: { givenName: "Marker", familyName: "User" }, userDetails: { username: "marker.user" } },
                        { id: "ind-approver", name: { givenName: "Approver", familyName: "User" }, userDetails: { username: "approver.user" } },
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-role", requestInfo: {} },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        const approverRows = sheetMap[APPROVER_SHEET].data as Record<string, string>[];

        expect(markerRows).toHaveLength(2);
        expect(markerRows.map((row) => row.HCM_ADMIN_CONSOLE_USER_WORKER_ID).sort()).toEqual(["ind-admin", "ind-marker"]);
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_ROLE).toBe("TEAM_SUPERVISOR");
        expect(markerRows[1].HCM_ADMIN_CONSOLE_USER_ROLE).toBe("TEAM_SUPERVISOR");

        expect(approverRows).toHaveLength(1);
        expect(approverRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-approver");
        expect(approverRows[0].HCM_ADMIN_CONSOLE_USER_ROLE).toBe("PROXIMITY_SUPERVISOR");
    });

    it("populates marker and approver tabs register-wise from attendance staff when campaign user roles are unavailable", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-staff-fallback",
                campaignNumber: "CMP-STAFF-FALLBACK",
                startDate: Date.UTC(2026, 6, 1),
                endDate: Date.UTC(2026, 6, 31),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], []);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-staff-1",
                            serviceCode: "REG-S1",
                            name: "Register Staff 1",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: [
                                { userId: "ind-m1", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 6, 2), denrollmentDate: null },
                                { userId: "ind-a1", staffType: "APPROVER", enrollmentDate: Date.UTC(2026, 6, 2), denrollmentDate: null }
                            ]
                        },
                        {
                            id: "reg-staff-2",
                            serviceCode: "REG-S2",
                            name: "Register Staff 2",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: [
                                { userId: "ind-m2", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 6, 3), denrollmentDate: null },
                                { userId: "ind-a2", staffType: "APPROVER", enrollmentDate: Date.UTC(2026, 6, 3), denrollmentDate: null }
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-m1", name: { givenName: "Marker", familyName: "One" }, userDetails: { username: "marker.one" } },
                        { id: "ind-a1", name: { givenName: "Approver", familyName: "One" }, userDetails: { username: "approver.one" } },
                        { id: "ind-m2", name: { givenName: "Marker", familyName: "Two" }, userDetails: { username: "marker.two" } },
                        { id: "ind-a2", name: { givenName: "Approver", familyName: "Two" }, userDetails: { username: "approver.two" } },
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-staff-fallback", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        const approverRows = sheetMap[APPROVER_SHEET].data as Record<string, string>[];

        expect(workerRows).toHaveLength(0);

        expect(markerRows).toHaveLength(2);
        expect(markerRows.map((row) => row.HCM_ATTENDANCE_REGISTER_CODE)).toEqual(["REG-S1", "REG-S2"]);
        expect(markerRows.map((row) => row.HCM_ADMIN_CONSOLE_USER_WORKER_ID)).toEqual(["ind-m1", "ind-m2"]);
        expect(markerRows.map((row) => row.HCM_ADMIN_CONSOLE_USER_ROLE)).toEqual(["TEAM_SUPERVISOR", "TEAM_SUPERVISOR"]);

        expect(approverRows).toHaveLength(2);
        expect(approverRows.map((row) => row.HCM_ATTENDANCE_REGISTER_CODE)).toEqual(["REG-S1", "REG-S2"]);
        expect(approverRows.map((row) => row.HCM_ADMIN_CONSOLE_USER_WORKER_ID)).toEqual(["ind-a1", "ind-a2"]);
        expect(approverRows.map((row) => row.HCM_ADMIN_CONSOLE_USER_ROLE)).toEqual(["PROXIMITY_SUPERVISOR", "PROXIMITY_SUPERVISOR"]);
    });

    it("skips requester owner marker rows when requester has no mapped campaign roles", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-requester-owner",
                campaignNumber: "CMP-REQUESTER-OWNER",
                startDate: Date.UTC(2026, 7, 1),
                endDate: Date.UTC(2026, 7, 31),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-dh",
                uniqueIdAfterProcess: "ind-dh",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-dh",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "WAREHOUSE_MANAGER"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-req-1",
                            serviceCode: "REG-REQ-1",
                            name: "Register Requester",
                            localityCode: "ADMIN",
                            attendees: [],
                            staff: [
                                { userId: "ind-requester", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 7, 2), denrollmentDate: null },
                                { userId: "ind-dh", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 7, 2), denrollmentDate: null }
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-requester", name: { givenName: "SMC", familyName: "Owner" }, userDetails: { username: "BEDNET-CM-1" } },
                        { id: "ind-dh", name: { givenName: "DH", familyName: "Owner" }, userDetails: { username: "USR-957854" } }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            {
                tenantId: "bednet",
                campaignId: "cmp-requester-owner",
                requestInfo: {
                    userInfo: {
                        userName: "BEDNET-CM-1"
                    }
                }
            },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        expect(markerRows).toHaveLength(1);
        expect(markerRows[0].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-dh");
        expect(markerRows[0].UserName).toBe("USR-957854");
    });

    it("backfills missing register markers from campaign users after requester owner rows are skipped", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-requester-owner-multi",
                campaignNumber: "CMP-REQUESTER-OWNER-MULTI",
                startDate: Date.UTC(2026, 7, 1),
                endDate: Date.UTC(2026, 7, 31),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        routeRelatedData([], [], [
            {
                uniqueIdentifier: "ind-fallback-marker",
                uniqueIdAfterProcess: "ind-fallback-marker",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-fallback-marker",
                    HCM_ADMIN_CONSOLE_USER_NAME: "Fallback Marker",
                    UserName: "fallback.marker",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN-1",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            },
            {
                uniqueIdentifier: "ind-dh",
                uniqueIdAfterProcess: "ind-dh",
                type: "user",
                data: {
                    HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-dh",
                    HCM_ADMIN_CONSOLE_USER_NAME: "DH Marker",
                    UserName: "USR-957854",
                    HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN-2",
                    HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR"
                }
            }
        ]);

        mockHttpRequest.mockImplementation(async (url: string) => {
            if (url.includes("/attendance/v1/_search")) {
                return {
                    attendanceRegister: [
                        {
                            id: "reg-req-1",
                            serviceCode: "REG-REQ-1",
                            name: "Register Requester 1",
                            localityCode: "ADMIN-1",
                            attendees: [],
                            staff: [
                                { userId: "ind-requester", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 7, 2), denrollmentDate: null }
                            ]
                        },
                        {
                            id: "reg-req-2",
                            serviceCode: "REG-REQ-2",
                            name: "Register Requester 2",
                            localityCode: "ADMIN-2",
                            attendees: [],
                            staff: [
                                { userId: "ind-requester", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 7, 2), denrollmentDate: null },
                                { userId: "ind-dh", staffType: "OWNER", enrollmentDate: Date.UTC(2026, 7, 2), denrollmentDate: null }
                            ]
                        }
                    ]
                } as any;
            }
            if (url.includes("individual/v1/_search")) {
                return {
                    Individual: [
                        { id: "ind-requester", name: { givenName: "SMC", familyName: "Owner" }, userDetails: { username: "BEDNET-CM-1" } },
                        { id: "ind-dh", name: { givenName: "DH", familyName: "Owner" }, userDetails: { username: "USR-957854" } }
                    ]
                } as any;
            }
            return { attendanceRegister: [] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            {
                tenantId: "bednet",
                campaignId: "cmp-requester-owner-multi",
                requestInfo: {
                    userInfo: {
                        userName: "BEDNET-CM-1"
                    }
                }
            },
            {}
        );

        const markerRows = sheetMap[MARKER_SHEET].data as Record<string, string>[];
        expect(markerRows).toHaveLength(2);
        expect(markerRows.map((row) => row.HCM_ATTENDANCE_REGISTER_CODE).sort()).toEqual(["REG-REQ-1", "REG-REQ-2"]);
        expect(markerRows.map((row) => row.UserName)).not.toContain("BEDNET-CM-1");
        expect(markerRows.find((row) => row.HCM_ATTENDANCE_REGISTER_CODE === "REG-REQ-1")?.UserName).toBe("fallback.marker");
        expect(markerRows.find((row) => row.HCM_ATTENDANCE_REGISTER_CODE === "REG-REQ-2")?.UserName).toBe("USR-957854");
    });

    it("ignores localityCode and searches registers campaign-wide", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                campaignNumber: "CMP-L",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);
        mockBoundaryRelationship.mockResolvedValue(boundaryTree([
            { code: "WARD-22", children: [{ code: "WARD-22-DH-1", children: [] }] }
        ]));
        routeRelatedData([
            boundaryRow("WARD-22", "prj-ward22"),
            boundaryRow("WARD-22-DH-1", "prj-dh1"),
            boundaryRow("OTHER-WARD", "prj-other")
        ]);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-locality", hierarchyType: "ADMIN", requestInfo: {}, additionalDetails: { localityCode: "WARD-22" } },
            {}
        );

        expect(mockBoundaryRelationship).not.toHaveBeenCalled();
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        expect(mockHttpRequest).toHaveBeenCalledWith(
            expect.stringContaining("/attendance/v1/_search"),
            expect.anything(),
            expect.objectContaining({
                tenantId: "bednet",
                campaignNumber: "CMP-L",
                includeAttendee: true,
                includeStaff: true
            })
        );
        expect((mockHttpRequest.mock.calls[0][2] as any).referenceIds).toBeUndefined();
    });

    it("uses campaign-wide search even when localityCode points to a parent boundary", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: "CMP-D", startDate: Date.UTC(2026, 0, 1), endDate: Date.UTC(2026, 0, 2), boundaries: [] }]
        } as any);
        mockBoundaryRelationship.mockResolvedValue(boundaryTree([
            { code: "WARD-1", children: [{ code: "WARD-1-DH-1", children: [] }] }
        ]));
        routeRelatedData([boundaryRow("WARD-1-DH-1", "prj-deep")]);
        mockHttpRequest.mockResolvedValue({
            attendanceRegister: [
                { id: "reg-deep", serviceCode: "REG-DEEP", name: "Deep", localityCode: "WARD-1-DH-1", attendees: [], staff: [] }
            ]
        } as any);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-deep", hierarchyType: "ADMIN", requestInfo: {}, additionalDetails: { localityCode: "WARD-1" } },
            {}
        );

        expect(mockBoundaryRelationship).not.toHaveBeenCalled();
        expect((mockHttpRequest.mock.calls[0][2] as any).campaignNumber).toBe("CMP-D");
        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows.map((row) => row[REGISTER_CODE_COLUMN])).toEqual([]);
    });

    it("batches the register search when the subtree has more project ids than the chunk size", async () => {
        mockConfig.attendanceRegister.registerSearchReferenceIdChunkSize = 2;
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: "CMP-B", startDate: Date.UTC(2026, 0, 1), endDate: Date.UTC(2026, 0, 2), boundaries: [] }]
        } as any);
        mockBoundaryRelationship.mockResolvedValue(boundaryTree([
            { code: "W", children: [{ code: "W-1" }, { code: "W-2" }, { code: "W-3" }, { code: "W-4" }] }
        ]));
        routeRelatedData([
            boundaryRow("W-1", "p1"), boundaryRow("W-2", "p2"),
            boundaryRow("W-3", "p3"), boundaryRow("W-4", "p4")
        ]);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-batch", hierarchyType: "ADMIN", requestInfo: {}, additionalDetails: { localityCode: "W" } },
            {}
        );

        expect(mockBoundaryRelationship).not.toHaveBeenCalled();
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        const params = mockHttpRequest.mock.calls[0][2] as any;
        expect(params.campaignNumber).toBe("CMP-B");
        expect(params.referenceIds).toBeUndefined();
    });

    it("skips boundaries whose project has not been created yet", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: "CMP-P", startDate: Date.UTC(2026, 0, 1), endDate: Date.UTC(2026, 0, 2), boundaries: [] }]
        } as any);
        mockBoundaryRelationship.mockResolvedValue(boundaryTree([
            { code: "W", children: [{ code: "W-1" }, { code: "W-2" }] }
        ]));
        routeRelatedData([boundaryRow("W-1", "p1"), boundaryRow("W-2", null)]);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-pending", hierarchyType: "ADMIN", requestInfo: {}, additionalDetails: { localityCode: "W" } },
            {}
        );

        expect(mockBoundaryRelationship).not.toHaveBeenCalled();
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        const params = mockHttpRequest.mock.calls[0][2] as any;
        expect(params.campaignNumber).toBe("CMP-P");
        expect(params.referenceIds).toBeUndefined();
    });

    it("returns no registers when the locality subtree has no created projects but still keeps campaign-wide discovery", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: "CMP-N", startDate: Date.UTC(2026, 0, 1), endDate: Date.UTC(2026, 0, 2), boundaries: [] }]
        } as any);
        mockBoundaryRelationship.mockResolvedValue(boundaryTree([{ code: "W-EMPTY", children: [] }]));
        routeRelatedData([]);

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-empty", hierarchyType: "ADMIN", requestInfo: {}, additionalDetails: { localityCode: "W-EMPTY" } },
            {}
        );

        expect(mockBoundaryRelationship).not.toHaveBeenCalled();
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        expect((mockHttpRequest.mock.calls[0][2] as any).campaignNumber).toBe("CMP-N");
        expect((sheetMap[WORKER_SHEET].data as any[]).length).toBe(0);
    });

    it("does not need campaign projectId for a locality-scoped download", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{ campaignNumber: "CMP-NOPRJ", startDate: Date.UTC(2026, 0, 1), endDate: Date.UTC(2026, 0, 2), boundaries: [] }]
        } as any);
        mockBoundaryRelationship.mockResolvedValue(boundaryTree([{ code: "WARD-9", children: [] }]));
        routeRelatedData([boundaryRow("WARD-9", "prj-ward9")]);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-noprj", hierarchyType: "ADMIN", requestInfo: {}, additionalDetails: { localityCode: "WARD-9" } },
            {}
        );

        expect(mockBoundaryRelationship).not.toHaveBeenCalled();
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        const params = mockHttpRequest.mock.calls[0][2] as any;
        expect(params.campaignNumber).toBe("CMP-NOPRJ");
        expect(params.referenceIds).toBeUndefined();
    });

    it("searches once by campaignNumber when no localityCode is supplied, never per boundary", async () => {
        const boundaries = Array.from({ length: 1000 }, (_unused, index) => ({ code: `WARD-${index}` }));
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-wide",
                campaignNumber: "CMP-WIDE",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries
            }]
        } as any);
        mockGetRelatedData.mockResolvedValue([] as any);
        mockHttpRequest.mockResolvedValue({ attendanceRegister: [] } as any);

        await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-wide", requestInfo: {} },
            {}
        );

        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        expect(mockHttpRequest).toHaveBeenCalledWith(
            expect.stringContaining("/attendance/v1/_search"),
            expect.anything(),
            expect.objectContaining({ tenantId: "bednet", campaignNumber: "CMP-WIDE", offset: 0 })
        );
    });

    it("paginates a campaign-wide search to exhaustion with no register ceiling", async () => {
        mockConfig.attendanceRegister.registerSearchPageLimit = 2;
        const totalPages = 12;

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-pages",
                campaignNumber: "CMP-PAGES",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries: []
            }]
        } as any);
        mockGetRelatedData.mockResolvedValue([] as any);

        mockHttpRequest.mockImplementation(async (_url: string, _body: any, params: any) => {
            if (params?.campaignNumber !== "CMP-PAGES") return { attendanceRegister: [] } as any;
            const page = Math.floor((params.offset || 0) / 2);
            if (page >= totalPages) return { attendanceRegister: [] } as any;
            const isLastPage = page === totalPages - 1;
            const registers = [{
                id: `reg-uuid-${page}-a`,
                serviceCode: `REG-${page}-A`,
                name: `Register ${page} A`,
                localityCode: `WARD-${page}`,
                attendees: [],
                staff: []
            }];
            if (!isLastPage) {
                registers.push({
                    id: `reg-uuid-${page}-b`,
                    serviceCode: `REG-${page}-B`,
                    name: `Register ${page} B`,
                    localityCode: `WARD-${page}`,
                    attendees: [],
                    staff: []
                });
            }
            return { attendanceRegister: registers } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-pages", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(0);
        expect(mockHttpRequest).toHaveBeenCalledTimes(totalPages + 1);
    });

    it("continues campaign-wide paging when service returns fewer rows than requested limit", async () => {
        mockConfig.attendanceRegister.registerSearchPageLimit = 5;

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-cap",
                campaignNumber: "CMP-CAP",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries: []
            }]
        } as any);
        mockGetRelatedData.mockResolvedValue([] as any);

        const allRegisters = [
            { id: "reg-cap-1", serviceCode: "REG-CAP-1", name: "Register 1", localityCode: "WARD-1", attendees: [], staff: [] },
            { id: "reg-cap-2", serviceCode: "REG-CAP-2", name: "Register 2", localityCode: "WARD-2", attendees: [], staff: [] },
            { id: "reg-cap-3", serviceCode: "REG-CAP-3", name: "Register 3", localityCode: "WARD-3", attendees: [], staff: [] },
            { id: "reg-cap-4", serviceCode: "REG-CAP-4", name: "Register 4", localityCode: "WARD-4", attendees: [], staff: [] },
            { id: "reg-cap-5", serviceCode: "REG-CAP-5", name: "Register 5", localityCode: "WARD-5", attendees: [], staff: [] }
        ];

        mockHttpRequest.mockImplementation(async (_url: string, _body: any, params: any) => {
            if (params?.campaignNumber !== "CMP-CAP") return { attendanceRegister: [] } as any;
            const offset = Number(params?.offset || 0);
            const page = allRegisters.slice(offset, offset + 2); // service-imposed cap: max 2 rows per call
            return { attendanceRegister: page } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-cap", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows).toHaveLength(0);
        expect(mockHttpRequest).toHaveBeenCalledTimes(4); // offsets: 0,2,4,5
    });

    it("dedupes registers repeated across pages and skips deleted ones", async () => {
        mockConfig.attendanceRegister.registerSearchPageLimit = 2;

        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-dupe",
                campaignNumber: "CMP-DUPE",
                startDate: Date.UTC(2026, 0, 1),
                endDate: Date.UTC(2026, 0, 2),
                boundaries: []
            }]
        } as any);
        mockGetRelatedData.mockResolvedValue([] as any);

        const duplicate = {
            id: "reg-uuid-1",
            serviceCode: "REG-001",
            name: "Register 001",
            localityCode: "WARD-1",
            attendees: [],
            staff: []
        };

        mockHttpRequest.mockImplementation(async (_url: string, _body: any, params: any) => {
            if (params?.campaignNumber !== "CMP-DUPE") return { attendanceRegister: [] } as any;
            if ((params.offset || 0) === 0) {
                return {
                    attendanceRegister: [
                        duplicate,
                        {
                            id: "reg-uuid-deleted",
                            serviceCode: "REG-DELETED",
                            name: "Register Deleted",
                            localityCode: "WARD-2",
                            isDeleted: true,
                            attendees: [],
                            staff: []
                        }
                    ]
                } as any;
            }
            return { attendanceRegister: [duplicate] } as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-dupe", requestInfo: {} },
            {}
        );

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows.map((row) => row[REGISTER_CODE_COLUMN])).toEqual([]);
    });

});
