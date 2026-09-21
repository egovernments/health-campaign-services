const mockConfig = {
    basesecret: "unit-test-secret",
    host: {
        attendanceHost: "http://attendance.local",
        healthIndividualHost: "http://individual.local/"
    },
    paths: {
        attendanceRegisterSearch: "/attendance/v1/_search",
        healthIndividualSearch: "individual/v1/_search"
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

        expect((sheetMap[WORKER_SHEET].data as any[])).toHaveLength(1);
        expect((sheetMap[APPROVER_SHEET].data as any[])).toHaveLength(1);
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

    it("expands campaign users for every register and uses campaign start date for enrollment", async () => {
        mockSearchCampaign.mockResolvedValue({
            CampaignDetails: [{
                projectId: "prj-users",
                campaignNumber: "CMP-USERS",
                startDate: "2026-06-15T00:00:00.000Z",
                endDate: Date.UTC(2026, 6, 1),
                boundaries: [{ code: "ADMIN" }]
            }]
        } as any);

        mockHttpRequest.mockResolvedValue({
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
                    attendees: [],
                    staff: []
                }
            ]
        } as any);

        mockGetRelatedData.mockImplementation(async (type: string) => {
            if (type === "attendanceRegisterAttendee") return [] as any;
            if (type === "user") {
                return [
                    {
                        type: "user",
                        status: "completed",
                        data: {
                            HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-1",
                            HCM_ADMIN_CONSOLE_USER_NAME: "Alice Worker",
                            UserName: "alice.worker",
                            Password: "",
                            HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "DISTRIBUTOR",
                            HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN"
                        }
                    },
                    {
                        type: "user",
                        status: "completed",
                        data: {
                            HCM_ADMIN_CONSOLE_USER_WORKER_ID: "ind-2",
                            HCM_ADMIN_CONSOLE_USER_NAME: "Mina Marker",
                            UserName: "mina.marker",
                            Password: "",
                            HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1: "TEAM_SUPERVISOR",
                            HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY: "ADMIN"
                        }
                    }
                ] as any;
            }
            return [] as any;
        });

        const sheetMap = await TemplateClass.generate(
            {},
            { tenantId: "bednet", campaignId: "cmp-users", requestInfo: {} },
            {}
        );

        const allMappedRows = [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET]
            .flatMap((sheetName) => sheetMap[sheetName].data as Record<string, string>[])
            .filter((row) =>
                Boolean(row.HCM_ADMIN_CONSOLE_USER_WORKER_ID || row.UserName || row.HCM_ADMIN_CONSOLE_USER_NAME)
            );

        expect(allMappedRows).toHaveLength(4); // 2 registers x 2 users
        allMappedRows.forEach((row) => {
            expect(row.HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE).toBe("15-06-2026");
        });
        expect(allMappedRows.filter((row) => row.HCM_ATTENDANCE_REGISTER_CODE === "REG-001")).toHaveLength(2);
        expect(allMappedRows.filter((row) => row.HCM_ATTENDANCE_REGISTER_CODE === "REG-002")).toHaveLength(2);
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

        mockGetRelatedData.mockResolvedValue([
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
        ] as any);

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

        expect(markerRows).toHaveLength(2);
        expect(markerRows[1].HCM_ATTENDANCE_REGISTER_CODE).toBe("REG-002");
        expect(markerRows[1].HCM_ADMIN_CONSOLE_USER_WORKER_ID).toBe("ind-10");
        expect(markerRows[1].UserName).toBe("mark.owner");

        expect(approverRows).toHaveLength(2);
    });

    it("searches registers by the project ids of the requested locality subtree", async () => {
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

        expect(mockBoundaryRelationship).toHaveBeenCalledWith(
            "bednet", "ADMIN", true, false, true, "WARD-22", expect.anything()
        );
        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        const params = mockHttpRequest.mock.calls[0][2] as any;
        expect(params.referenceIds.split(",").sort()).toEqual(["prj-dh1", "prj-ward22"]);
        expect(params.localityCode).toBeUndefined();
        expect(params.referenceId).toBeUndefined();
        expect(params.campaignNumber).toBeUndefined();
    });

    it("finds registers created on a descendant boundary, not just the requested one", async () => {
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

        const workerRows = sheetMap[WORKER_SHEET].data as Record<string, string>[];
        expect(workerRows.map((row) => row[REGISTER_CODE_COLUMN])).toEqual(["REG-DEEP"]);
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

        expect(mockHttpRequest).toHaveBeenCalledTimes(2);
        const sent = mockHttpRequest.mock.calls.map((call) => (call[2] as any).referenceIds);
        expect(sent).toEqual(["p1,p2", "p3,p4"]);
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

        expect(mockHttpRequest).toHaveBeenCalledTimes(1);
        expect((mockHttpRequest.mock.calls[0][2] as any).referenceIds).toBe("p1");
    });

    it("returns no registers and makes no register call when the subtree has no created projects", async () => {
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

        expect(mockHttpRequest).not.toHaveBeenCalled();
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

        expect((mockHttpRequest.mock.calls[0][2] as any).referenceIds).toBe("prj-ward9");
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
        expect(workerRows).toHaveLength(totalPages * 2 - 1);
        expect(mockHttpRequest).toHaveBeenCalledTimes(totalPages);
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
        expect(workerRows.map((row) => row[REGISTER_CODE_COLUMN])).toEqual(["REG-001"]);
    });

});
