package org.egov.excelingestion.generator;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationResult;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceRegisterUserBulkMappingSheetGeneratorTest {

    @Mock
    private MDMSService mdmsService;
    @Mock
    private CampaignService campaignService;
    @Mock
    private ServiceRequestRepository serviceRequestRepository;
    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private CustomExceptionHandler exceptionHandler;
    @Mock
    private SchemaColumnDefUtil schemaColumnDefUtil;

    private AttendanceRegisterUserBulkMappingSheetGenerator generator;

    @BeforeEach
    void setUp() {
        when(config.getServerZoneId()).thenReturn(ZoneId.of("UTC"));
        when(config.getHealthIndividualHost()).thenReturn("http://individual.local/");
        when(config.getHealthIndividualSearchPath()).thenReturn("individual/v1/_search");
        when(config.getDefaultHeaderColor()).thenReturn("#93c47d");
        generator = new AttendanceRegisterUserBulkMappingSheetGenerator(
                mdmsService,
                campaignService,
                serviceRequestRepository,
                config,
                exceptionHandler,
                schemaColumnDefUtil
        );
    }

    @Test
    void buildBulkRows_dedupesMappingsAndAddsRegisterOnlyRows() {
        AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData register1 =
                new AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData("reg-uuid-1", "REG-001", "Register 001");
        AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData register2 =
                new AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData("reg-uuid-2", "REG-002", "Register 002");

        Map<String, AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData> byServiceCode = new HashMap<>();
        byServiceCode.put(register1.serviceCode, register1);
        byServiceCode.put(register2.serviceCode, register2);

        Map<String, AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData> byId = new HashMap<>();
        byId.put(register1.id, register1);
        byId.put(register2.id, register2);

        Map<String, Object> row1 = attendeeRow(
                "row-1",
                "reg-uuid-1_ind-1_worker",
                null,
                Map.of(
                        "_registerServiceCode", "REG-001",
                        "HCM_ADMIN_CONSOLE_USER_WORKER_ID", "W-1",
                        "HCM_ADMIN_CONSOLE_USER_NAME", "Alice Worker",
                        "HCM_ADMIN_CONSOLE_USER_ROLE", "DISTRIBUTOR",
                        "HCM_ATTENDANCE_ATTENDEE_TEAM_CODE", "TEAM-1",
                        "HCM_ADMIN_CONSOLE_BOUNDARY_NAME", "Boundary A"
                )
        );

        Map<String, Object> row2 = attendeeRow(
                "row-2",
                "reg-uuid-1_ind-1_worker",
                null,
                Map.of(
                        "_registerServiceCode", "REG-001",
                        "HCM_ADMIN_CONSOLE_USER_WORKER_ID", "W-1",
                        "HCM_ADMIN_CONSOLE_USER_NAME", "Alice Worker",
                        "HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1", "REGISTRAR",
                        "HCM_ADMIN_CONSOLE_BOUNDARY_NAME", "Boundary A"
                )
        );

        List<Map<String, Object>> rows = generator.buildBulkRows(
                List.of(register1, register2),
                List.of(row1, row2),
                byServiceCode,
                byId,
                "01-01-2026",
                "10-01-2026"
        );

        assertEquals(2, rows.size());

        Map<String, Object> first = rows.get(0);
        assertEquals("REG-001", first.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("Register 001", first.get("HCM_ATTENDANCE_REGISTER_NAME"));
        assertEquals("reg-uuid-1", first.get("HCM_ATTENDANCE_REGISTER_UUID"));
        assertEquals("Alice Worker", first.get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("W-1", first.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("DISTRIBUTOR, REGISTRAR", first.get("HCM_ADMIN_CONSOLE_USER_ROLE"));
        assertEquals("TEAM-1", first.get("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"));
        assertEquals("01-01-2026", first.get("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"));
        assertEquals("10-01-2026", first.get("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"));

        Map<String, Object> second = rows.get(1);
        assertEquals("REG-002", second.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("", second.get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("", second.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("", second.get("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"));
    }

    @Test
    void buildBulkRows_usesUniqueIdAfterProcessWhenRegisterCodeMissing() {
        AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData register =
                new AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData("reg-uuid-99", "REG-099", "Register 099");

        Map<String, AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData> byServiceCode = new HashMap<>();
        byServiceCode.put(register.serviceCode, register);

        Map<String, AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData> byId = new HashMap<>();
        byId.put(register.id, register);

        long syncedDeEnrollment = 1770422400000L; // 07-02-2026 UTC
        Map<String, Object> attendee = attendeeRow(
                "row-9",
                "reg-uuid-99_ind-9_worker",
                syncedDeEnrollment,
                Map.of(
                        "HCM_ADMIN_CONSOLE_USER_WORKER_ID", "W-9",
                        "HCM_ADMIN_CONSOLE_USER_NAME", "Bob Marker",
                        "HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_1", "TEAM_SUPERVISOR",
                        "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY", "BOUNDARY-9"
                )
        );

        List<Map<String, Object>> rows = generator.buildBulkRows(
                List.of(register),
                List.of(attendee),
                byServiceCode,
                byId,
                "01-02-2026",
                "15-02-2026"
        );

        assertEquals(1, rows.size());
        assertEquals("REG-099", rows.get(0).get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("Bob Marker", rows.get(0).get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("TEAM_SUPERVISOR", rows.get(0).get("HCM_ADMIN_CONSOLE_USER_ROLE"));
        assertEquals("07-02-2026", rows.get(0).get("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"));
    }

    @Test
    void buildRowsFromAttendanceState_fallsBackWhenCampaignRowsMissing() {
        AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData registerWithAttendee =
                new AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData(
                        "reg-uuid-501",
                        "REG-501",
                        "Register 501",
                        "ADMIN",
                        List.of(Map.of(
                                "individualId", "ind-501",
                                "tag", "TEAM-501",
                                "enrollmentDate", 1772668800000L,   // 05-03-2026 UTC
                                "denrollmentDate", 1773705600000L   // 17-03-2026 UTC
                        )),
                        Collections.emptyList()
                );

        AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData registerWithoutMappings =
                new AttendanceRegisterUserBulkMappingSheetGenerator.RegisterData(
                        "reg-uuid-777",
                        "REG-777",
                        "Register 777",
                        "ADMIN",
                        Collections.emptyList(),
                        Collections.emptyList()
                );

        Map<String, Object> individualResponse = new HashMap<>();
        individualResponse.put("Individual", List.of(Map.of(
                "id", "ind-501",
                "name", Map.of("givenName", "Anaya", "familyName", "Patel"),
                "userDetails", Map.of("username", "anaya.patel")
        )));

        when(serviceRequestRepository.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(individualResponse);

        List<Map<String, Object>> rows = generator.buildRowsFromAttendanceState(
                List.of(registerWithAttendee, registerWithoutMappings),
                "bednet",
                null,
                "01-03-2026",
                "31-03-2026"
        );

        assertEquals(2, rows.size());

        Map<String, Object> mapped = rows.get(0);
        assertEquals("REG-501", mapped.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("Anaya Patel", mapped.get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("ind-501", mapped.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("WORKER", mapped.get("HCM_ADMIN_CONSOLE_USER_ROLE"));
        assertEquals("TEAM-501", mapped.get("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"));
        assertEquals("ADMIN", mapped.get("HCM_ADMIN_CONSOLE_BOUNDARY_NAME"));
        assertEquals("01-03-2026", mapped.get("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"));
        assertEquals("17-03-2026", mapped.get("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"));

        Map<String, Object> registerOnly = rows.get(1);
        assertEquals("REG-777", registerOnly.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("", registerOnly.get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("", registerOnly.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("", registerOnly.get("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"));
    }

    @Test
    void generateSheetData_usesLocalityCodeForCampaignRegisterSearch() {
        when(config.getAttendanceRegisterSearchUrl()).thenReturn("http://attendance.local/attendance/v1/_search");
        when(mdmsService.searchMDMS(any(), eq("bednet"), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), eq(1), eq(0)))
                .thenReturn(List.of(Map.of(
                        "data", Map.of("properties", Map.of("stringProperties", List.of()))
                )));
        when(schemaColumnDefUtil.convertSchemaToColumnDefs(any())).thenReturn(Collections.emptyList());

        CampaignSearchResponse.CampaignDetail campaign = CampaignSearchResponse.CampaignDetail.builder()
                .id("cmp-123")
                .projectId("prj-123")
                .campaignNumber("CMP-123")
                .boundaries(List.of(CampaignSearchResponse.BoundaryDetail.builder().code("LOC-123").build()))
                .build();
        when(campaignService.searchCampaignById(eq("cmp-123"), eq("bednet"), any())).thenReturn(campaign);
        when(campaignService.searchCampaignDataByType(
                eq("attendanceRegisterAttendee"),
                eq(ProcessingConstants.STATUS_COMPLETED),
                eq("CMP-123"),
                eq("bednet"),
                any()
        )).thenReturn(Collections.emptyList());

        when(serviceRequestRepository.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(Map.of("attendanceRegister", Collections.emptyList()));

        SheetGenerationConfig sheetConfig = SheetGenerationConfig.builder()
                .sheetName("HCM_REGISTER_WORKER_SHEET")
                .schemaName("attendance-register-attendee-worker")
                .build();
        GenerateResource resource = GenerateResource.builder()
                .tenantId("bednet")
                .type("attendanceRegisterUserBulkMapping")
                .hierarchyType("ADMIN")
                .referenceId("cmp-123")
                .referenceType("campaign")
                .build();

        generator.generateSheetData(sheetConfig, resource, new RequestInfo(), Collections.emptyMap());

        ArgumentCaptor<StringBuilder> urlCaptor = ArgumentCaptor.forClass(StringBuilder.class);
        verify(serviceRequestRepository, atLeastOnce()).fetchResult(urlCaptor.capture(), any(), eq(Map.class));
        assertTrue(
                urlCaptor.getAllValues().stream().anyMatch((uri) ->
                        uri.toString().contains("referenceId=prj-123")
                                && uri.toString().contains("localityCode=LOC-123"))
        );
    }

    @Test
    void generateSheetData_fallsBackToCampaignIdWhenProjectIdMissing() {
        when(config.getAttendanceRegisterSearchUrl()).thenReturn("http://attendance.local/attendance/v1/_search");
        when(mdmsService.searchMDMS(any(), eq("bednet"), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), eq(1), eq(0)))
                .thenReturn(List.of(Map.of(
                        "data", Map.of("properties", Map.of("stringProperties", List.of()))
                )));
        when(schemaColumnDefUtil.convertSchemaToColumnDefs(any())).thenReturn(Collections.emptyList());

        CampaignSearchResponse.CampaignDetail campaign = CampaignSearchResponse.CampaignDetail.builder()
                .id("cmp-fallback")
                .campaignNumber("CMP-FALLBACK")
                .boundaries(List.of(CampaignSearchResponse.BoundaryDetail.builder().code("LOC-F").build()))
                .build();
        when(campaignService.searchCampaignById(eq("cmp-fallback"), eq("bednet"), any())).thenReturn(campaign);
        when(campaignService.searchCampaignDataByType(
                eq("attendanceRegisterAttendee"),
                eq(ProcessingConstants.STATUS_COMPLETED),
                eq("CMP-FALLBACK"),
                eq("bednet"),
                any()
        )).thenReturn(Collections.emptyList());

        when(serviceRequestRepository.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(Map.of("attendanceRegister", Collections.emptyList()));

        SheetGenerationConfig sheetConfig = SheetGenerationConfig.builder()
                .sheetName("HCM_REGISTER_WORKER_SHEET")
                .schemaName("attendance-register-attendee-worker")
                .build();
        GenerateResource resource = GenerateResource.builder()
                .tenantId("bednet")
                .type("attendanceRegisterUserBulkMapping")
                .hierarchyType("ADMIN")
                .referenceId("cmp-fallback")
                .referenceType("campaign")
                .build();

        generator.generateSheetData(sheetConfig, resource, new RequestInfo(), Collections.emptyMap());

        ArgumentCaptor<StringBuilder> urlCaptor = ArgumentCaptor.forClass(StringBuilder.class);
        verify(serviceRequestRepository, atLeastOnce()).fetchResult(urlCaptor.capture(), any(), eq(Map.class));
        assertTrue(
                urlCaptor.getAllValues().stream().anyMatch((uri) ->
                        uri.toString().contains("referenceId=cmp-fallback")
                                && uri.toString().contains("localityCode=LOC-F"))
        );
    }

    @Test
    void generateSheetData_threeTabModeSplitsRowsBySheetAndAddsRegisterColumns() {
        when(config.getAttendanceRegisterSearchUrl()).thenReturn("http://attendance.local/attendance/v1/_search");
        when(mdmsService.searchMDMS(any(), eq("bednet"), eq(ProcessingConstants.MDMS_SCHEMA_CODE), any(), eq(1), eq(0)))
                .thenReturn(List.of(Map.of(
                        "data", Map.of("properties", Map.of("stringProperties", List.of()))
                )));
        when(schemaColumnDefUtil.convertSchemaToColumnDefs(any())).thenReturn(List.of(
                ColumnDef.builder().name("HCM_ADMIN_CONSOLE_USER_WORKER_ID").type("string").orderNumber(10).build(),
                ColumnDef.builder().name("HCM_ADMIN_CONSOLE_USER_NAME").type("string").orderNumber(11).build(),
                ColumnDef.builder().name("UserName").type("string").orderNumber(12).build(),
                ColumnDef.builder().name("HCM_ADMIN_CONSOLE_USER_ROLE").type("string").orderNumber(13).build(),
                ColumnDef.builder().name("HCM_ADMIN_CONSOLE_BOUNDARY_NAME").type("string").orderNumber(14).build(),
                ColumnDef.builder().name("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY").type("string").orderNumber(15).build(),
                ColumnDef.builder().name("HCM_ATTENDANCE_REGISTER_ID").type("string").orderNumber(16).hideColumn(false).build(),
                ColumnDef.builder().name("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE").type("string").orderNumber(17).build(),
                ColumnDef.builder().name("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE").type("string").orderNumber(18).build(),
                ColumnDef.builder().name("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE").type("string").orderNumber(19).build()
        ));

        CampaignSearchResponse.CampaignDetail campaign = CampaignSearchResponse.CampaignDetail.builder()
                .id("cmp-123")
                .projectId("prj-123")
                .campaignNumber("CMP-123")
                .startDate(1777593600000L) // 01-05-2026 UTC
                .endDate(1778976000000L)   // 17-05-2026 UTC
                .boundaries(List.of(CampaignSearchResponse.BoundaryDetail.builder().code("ADMIN").build()))
                .build();
        when(campaignService.searchCampaignById(eq("cmp-123"), eq("bednet"), any())).thenReturn(campaign);
        when(campaignService.searchCampaignDataByType(
                eq("attendanceRegisterAttendee"),
                eq(ProcessingConstants.STATUS_COMPLETED),
                eq("CMP-123"),
                eq("bednet"),
                any()
        )).thenReturn(List.of(
                attendeeRow("row-worker", "reg-uuid-1_ind-1_worker", null, new HashMap<String, Object>() {{
                    put("_sheetName", "HCM_REGISTER_WORKER_SHEET");
                    put("_registerServiceCode", "REG-001");
                    put("HCM_ADMIN_CONSOLE_USER_WORKER_ID", "W-1");
                    put("HCM_ADMIN_CONSOLE_USER_NAME", "Worker One");
                    put("UserName", "worker.one");
                    put("HCM_ADMIN_CONSOLE_USER_ROLE", "DISTRIBUTOR");
                    put("HCM_ADMIN_CONSOLE_BOUNDARY_NAME", "ADMIN");
                    put("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE", "10/05/2026");
                    put("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE", "17-05-2026");
                    put("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE", "TEAM-A");
                }}),
                attendeeRow("row-marker", "reg-uuid-1_ind-2_worker", null, new HashMap<String, Object>() {{
                    put("_registerServiceCode", "REG-001");
                    put("HCM_ADMIN_CONSOLE_USER_WORKER_ID", "M-1");
                    put("HCM_ADMIN_CONSOLE_USER_NAME", "Marker One");
                    put("UserName", "marker.one");
                    put("HCM_ADMIN_CONSOLE_USER_ROLE", "TEAM_SUPERVISOR");
                    put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY", "ADMIN");
                    put("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE", "2026-05-01");
                }})
        ));

        when(serviceRequestRepository.fetchResult(any(StringBuilder.class), any(), eq(Map.class)))
                .thenReturn(Map.of("attendanceRegister", List.of(Map.of(
                        "id", "reg-uuid-1",
                        "serviceCode", "REG-001",
                        "name", "Register 001",
                        "localityCode", "ADMIN",
                        "attendees", Collections.emptyList(),
                        "staff", Collections.emptyList()
                ))));

        GenerateResource resource = GenerateResource.builder()
                .tenantId("bednet")
                .type("attendanceRegisterUserBulkMapping")
                .hierarchyType("ADMIN")
                .referenceId("cmp-123")
                .referenceType("campaign")
                .build();

        SheetGenerationResult workerResult = generator.generateSheetData(
                SheetGenerationConfig.builder()
                        .sheetName("HCM_REGISTER_WORKER_SHEET")
                        .schemaName("attendance-register-attendee-worker")
                        .build(),
                resource,
                new RequestInfo(),
                Collections.emptyMap()
        );
        SheetGenerationResult markerResult = generator.generateSheetData(
                SheetGenerationConfig.builder()
                        .sheetName("HCM_REGISTER_MARKER_SHEET")
                        .schemaName("attendance-register-attendee-marker")
                        .build(),
                resource,
                new RequestInfo(),
                Collections.emptyMap()
        );
        SheetGenerationResult approverResult = generator.generateSheetData(
                SheetGenerationConfig.builder()
                        .sheetName("HCM_REGISTER_APPROVER_SHEET")
                        .schemaName("attendance-register-attendee-approver")
                        .build(),
                resource,
                new RequestInfo(),
                Collections.emptyMap()
        );

        assertEquals(1, workerResult.getData().size());
        Map<String, Object> workerRow = workerResult.getData().get(0);
        assertEquals("REG-001", workerRow.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("W-1", workerRow.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("TEAM-A", workerRow.get("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"));
        assertEquals("01-05-2026", workerRow.get("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"));
        assertEquals("DISTRIBUTOR", workerRow.get("HCM_ADMIN_CONSOLE_USER_ROLE"));

        assertEquals(1, markerResult.getData().size());
        Map<String, Object> markerRow = markerResult.getData().get(0);
        assertEquals("M-1", markerRow.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("TEAM_SUPERVISOR", markerRow.get("HCM_ADMIN_CONSOLE_USER_ROLE"));
        assertFalse(markerRow.containsKey("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"));

        // No approver mapping in campaign_data => seed row for this register
        assertEquals(1, approverResult.getData().size());
        Map<String, Object> approverSeed = approverResult.getData().get(0);
        assertEquals("REG-001", approverSeed.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("", approverSeed.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
        assertEquals("", approverSeed.get("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"));
        assertEquals("", approverSeed.get("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"));

        List<ColumnDef> workerColumns = workerResult.getColumnDefs();
        assertEquals("HCM_ATTENDANCE_REGISTER_CODE", workerColumns.get(0).getName());
        assertEquals("HCM_ATTENDANCE_REGISTER_NAME", workerColumns.get(1).getName());
        assertEquals("HCM_ATTENDANCE_REGISTER_UUID", workerColumns.get(2).getName());
        assertEquals("#93c47d", workerColumns.get(0).getColorHex());
        assertEquals("#93c47d", workerColumns.get(1).getColorHex());
        assertEquals("#93c47d", workerColumns.get(2).getColorHex());
        ColumnDef registerIdColumn = workerColumns.stream()
                .filter(col -> "HCM_ATTENDANCE_REGISTER_ID".equals(col.getName()))
                .findFirst()
                .orElse(null);
        assertTrue(registerIdColumn != null && registerIdColumn.isHideColumn());
    }

    private Map<String, Object> attendeeRow(String uniqueIdentifier,
                                            String uniqueIdAfterProcess,
                                            Long denrollmentDate,
                                            Map<String, Object> data) {
        Map<String, Object> row = new HashMap<>();
        row.put("uniqueIdentifier", uniqueIdentifier);
        row.put("uniqueIdAfterProcess", uniqueIdAfterProcess);
        row.put("denrollmentDate", denrollmentDate);
        row.put("isDeleted", false);
        row.put("data", data);
        return row;
    }
}
