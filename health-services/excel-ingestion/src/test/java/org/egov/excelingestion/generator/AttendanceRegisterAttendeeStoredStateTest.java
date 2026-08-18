package org.egov.excelingestion.generator;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.CryptoService;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The generated attendee sheet must carry the enrolment state project-factory already holds for the
 * register: otherwise a regenerated template comes back blank and a de-enrolled person reads as
 * enrollable again.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceRegisterAttendeeStoredStateTest {

    private static final String TENANT = "mz";
    private static final String CAMPAIGN_NUMBER = "CMP-2026-08-17-000001";
    private static final String SERVICE_CODE = "REG-001";
    private static final String REGISTER_ID = "register-uuid-1";
    private static final String WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
    private static final String MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
    private static final String USERNAME = "USR-0001";
    // 13-08-2026 00:00 UTC, formatted in the configured server zone (UTC below), so no TZ flakiness
    private static final long DEENROL_EPOCH = 1786579200000L;
    private static final String DEENROL_SHEET_DATE = "13-08-2026";

    @Mock private MDMSService mdmsService;
    @Mock private MDMSConfigService mdmsConfigService;
    @Mock private CampaignService campaignService;
    @Mock private BoundaryService boundaryService;
    @Mock private CryptoService cryptoService;
    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ExcelIngestionConfig config;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private SchemaColumnDefUtil schemaColumnDefUtil;

    private AttendanceRegisterAttendeeSheetGenerator generator;
    private Method fetchStoredAttendees;
    private Method buildDataRows;

    @BeforeEach
    void setUp() throws Exception {
        generator = new AttendanceRegisterAttendeeSheetGenerator(mdmsService, mdmsConfigService,
                campaignService, boundaryService, cryptoService, serviceRequestRepository, config,
                exceptionHandler, schemaColumnDefUtil);

        // Credentials are already plaintext in these tests, so decryption is the identity
        when(cryptoService.bulkDecrypt(any(), any())).thenAnswer(call -> call.getArgument(0));
        when(config.getServerZoneId()).thenReturn(java.time.ZoneId.of("UTC"));

        fetchStoredAttendees = AttendanceRegisterAttendeeSheetGenerator.class.getDeclaredMethod(
                "fetchStoredAttendees", String.class, String.class, String.class, String.class,
                String.class, RequestInfo.class);
        fetchStoredAttendees.setAccessible(true);

        Class<?> storedAttendeeType = Class.forName(
                "org.egov.excelingestion.generator.AttendanceRegisterAttendeeSheetGenerator$StoredAttendee");
        buildDataRows = AttendanceRegisterAttendeeSheetGenerator.class.getDeclaredMethod(
                "buildDataRows", List.class, String.class, Map.class, RequestInfo.class,
                boolean.class, Map.class);
        buildDataRows.setAccessible(true);
        // Referenced only to fail loudly if the inner type is renamed
        assertEquals("StoredAttendee", storedAttendeeType.getSimpleName());
    }

    @Test
    void storedRow_stampsEnrolmentDeEnrolmentAndTeamCode() throws Exception {
        stub(List.of(storedRow(SERVICE_CODE, WORKER_SHEET, REGISTER_ID + "_ind-1_worker",
                "01-08-2026", "", "TEAM-1", DEENROL_EPOCH)));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("01-08-2026", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
        assertEquals(DEENROL_SHEET_DATE, row.get(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY));
        assertEquals("TEAM-1", row.get(ProcessingConstants.TEAM_CODE_COLUMN_KEY));
    }

    @Test
    void syncedDateArrivingAsAStringIsStillStamped() throws Exception {
        // project-factory returns the BIGINT column as a string, so a number check alone drops it
        Map<String, Object> stored = storedRow(SERVICE_CODE, WORKER_SHEET, REGISTER_ID + "_ind-1_worker",
                "01-08-2026", "", "", null);
        stored.put(ProcessingConstants.DEENROLLMENT_DATE_KEY, String.valueOf(DEENROL_EPOCH));
        stub(List.of(stored));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals(DEENROL_SHEET_DATE, row.get(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void unparseableSyncedDateFallsBackToTheStoredCell() throws Exception {
        Map<String, Object> stored = storedRow(SERVICE_CODE, WORKER_SHEET, REGISTER_ID + "_ind-1_worker",
                "01-08-2026", "20-08-2026", "", null);
        stored.put(ProcessingConstants.DEENROLLMENT_DATE_KEY, "not-an-epoch");
        stub(List.of(stored));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("20-08-2026", row.get(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void withoutSyncedDate_theStoredSheetValueIsKept() throws Exception {
        stub(List.of(storedRow(SERVICE_CODE, WORKER_SHEET, REGISTER_ID + "_ind-1_worker",
                "01-08-2026", "20-08-2026", "", null)));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("20-08-2026", row.get(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void noStoredRow_leavesTheDatesBlank() throws Exception {
        stub(List.of());

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
        assertEquals("", row.get(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY));
        assertEquals("", row.get(ProcessingConstants.TEAM_CODE_COLUMN_KEY));
    }

    @Test
    void rowOfAnEarlierRegisterWithTheSameServiceCode_isIgnored() throws Exception {
        stub(List.of(storedRow(SERVICE_CODE, WORKER_SHEET, "older-register-uuid_ind-1_worker",
                "01-08-2026", "", "TEAM-1", DEENROL_EPOCH)));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
        assertEquals("", row.get(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void unstampedRow_isKeptBecauseItPredatesTheIdentityStamp() throws Exception {
        stub(List.of(storedRow(SERVICE_CODE, WORKER_SHEET, null, "01-08-2026", "", "", null)));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("01-08-2026", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void rowOfAnotherSheet_isIgnored() throws Exception {
        stub(List.of(storedRow(SERVICE_CODE, MARKER_SHEET, REGISTER_ID + "_ind-1_marker",
                "01-08-2026", "", "", DEENROL_EPOCH)));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void rowOfAnotherRegister_isIgnored() throws Exception {
        stub(List.of(storedRow("REG-002", WORKER_SHEET, REGISTER_ID + "_ind-1_worker",
                "01-08-2026", "", "", DEENROL_EPOCH)));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
    }

    @Test
    void markerSheet_getsDatesWithoutATeamCodeColumn() throws Exception {
        stub(List.of(storedRow(SERVICE_CODE, MARKER_SHEET, REGISTER_ID + "_ind-1_marker",
                "01-08-2026", "", "TEAM-1", null)));

        Map<String, Object> row = buildRow(MARKER_SHEET, false);

        assertEquals("01-08-2026", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
        assertEquals(false, row.containsKey(ProcessingConstants.TEAM_CODE_COLUMN_KEY));
    }

    @Test
    void storedRowWithoutData_isSkippedWithoutFailing() throws Exception {
        Map<String, Object> malformed = new HashMap<>();
        malformed.put(ProcessingConstants.CAMPAIGN_DATA_KEY, null);
        stub(List.of(malformed));

        Map<String, Object> row = buildRow(WORKER_SHEET, true);

        assertEquals("", row.get(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY));
    }

    private void stub(List<Map<String, Object>> storedRows) {
        when(campaignService.searchCampaignDataByType(
                eq(ProcessingConstants.ATTENDANCE_REGISTER_ATTENDEE_TYPE), anyString(),
                eq(CAMPAIGN_NUMBER), eq(TENANT), any())).thenReturn(new ArrayList<>(storedRows));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRow(String sheetName, boolean includeTeamCode) throws Exception {
        RequestInfo requestInfo = RequestInfo.builder().build();
        Object stored = fetchStoredAttendees.invoke(generator, CAMPAIGN_NUMBER, SERVICE_CODE,
                REGISTER_ID, sheetName, TENANT, requestInfo);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) buildDataRows.invoke(generator,
                List.of(campaignUser()), SERVICE_CODE, Map.of(), requestInfo, includeTeamCode, stored);

        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private Map<String, Object> campaignUser() {
        Map<String, Object> data = new HashMap<>();
        data.put(ProcessingConstants.USERNAME_COLUMN_KEY, USERNAME);
        data.put("Password", "secret");
        data.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY", "MZ_01");
        data.put("HCM_ADMIN_CONSOLE_USER_NAME", "Field Worker");
        Map<String, Object> entry = new HashMap<>();
        entry.put(ProcessingConstants.CAMPAIGN_DATA_KEY, data);
        return entry;
    }

    private Map<String, Object> storedRow(String serviceCode, String sheetName, String identity,
                                          String enrolmentDate, String deEnrolmentCell, String teamCode,
                                          Long syncedDeEnrolmentDate) {
        Map<String, Object> data = new HashMap<>();
        data.put(ProcessingConstants.USERNAME_COLUMN_KEY, USERNAME);
        data.put(ProcessingConstants.STORED_REGISTER_SERVICE_CODE_KEY, serviceCode);
        data.put(ProcessingConstants.STORED_SHEET_NAME_KEY, sheetName);
        data.put(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY, enrolmentDate);
        data.put(ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY, deEnrolmentCell);
        data.put(ProcessingConstants.TEAM_CODE_COLUMN_KEY, teamCode);

        Map<String, Object> entry = new HashMap<>();
        entry.put(ProcessingConstants.CAMPAIGN_DATA_KEY, data);
        entry.put(ProcessingConstants.CAMPAIGN_DATA_UNIQUE_ID_AFTER_PROCESS_KEY, identity);
        entry.put(ProcessingConstants.DEENROLLMENT_DATE_KEY, syncedDeEnrolmentDate);
        return entry;
    }

}
