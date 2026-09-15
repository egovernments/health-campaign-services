package org.egov.excelingestion.generator;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        assertEquals("01-01-2026", first.get("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"));
        assertEquals("10-01-2026", first.get("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"));

        Map<String, Object> second = rows.get(1);
        assertEquals("REG-002", second.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("", second.get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("", second.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
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
        assertEquals("ADMIN", mapped.get("HCM_ADMIN_CONSOLE_BOUNDARY_NAME"));
        assertEquals("05-03-2026", mapped.get("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"));
        assertEquals("17-03-2026", mapped.get("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"));

        Map<String, Object> registerOnly = rows.get(1);
        assertEquals("REG-777", registerOnly.get("HCM_ATTENDANCE_REGISTER_CODE"));
        assertEquals("", registerOnly.get("HCM_ADMIN_CONSOLE_USER_NAME"));
        assertEquals("", registerOnly.get("HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
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
