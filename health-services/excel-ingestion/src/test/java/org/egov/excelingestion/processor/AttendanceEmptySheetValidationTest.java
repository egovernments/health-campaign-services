package org.egov.excelingestion.processor;

import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Covers the sheet-level "at least one row" rules for attendance register and attendee uploads.
 * Without these, an empty template validated clean at upload time and only failed later in
 * project-factory on a different screen.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceEmptySheetValidationTest {

    private static final String BOUNDARY_COLUMN = "NIGERIA_VILLAGE";
    private static final String WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
    private static final String MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
    private static final String CAMPAIGN_BOUNDARY_CODE = "VILLAGE_001";

    @Mock private ValidationService validationService;
    @Mock private BoundaryService boundaryService;
    @Mock private CampaignService campaignService;
    @Mock private EnrichmentUtil enrichmentUtil;
    @Mock private ExcelUtil excelUtil;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ExcelIngestionConfig config;
    @Mock private MDMSConfigService mdmsConfigService;

    private AttendanceRegisterValidationProcessor registerProcessor;
    private AttendanceRegisterAttendeeValidationProcessor attendeeProcessor;

    private Method validateRegisterData;
    private Method countEnrolmentRows;

    @BeforeEach
    void setUp() throws Exception {
        registerProcessor = new AttendanceRegisterValidationProcessor(
                validationService, boundaryService, campaignService, enrichmentUtil,
                excelUtil, exceptionHandler, boundaryUtil, serviceRequestRepository, config);

        attendeeProcessor = new AttendanceRegisterAttendeeValidationProcessor(
                validationService, mdmsConfigService, enrichmentUtil, excelUtil,
                serviceRequestRepository, config, exceptionHandler);

        validateRegisterData = AttendanceRegisterValidationProcessor.class.getDeclaredMethod(
                "validateAttendanceRegisterData",
                List.class, ProcessResource.class, RequestInfo.class, List.class, Map.class);
        validateRegisterData.setAccessible(true);

        countEnrolmentRows = AttendanceRegisterAttendeeValidationProcessor.class.getDeclaredMethod(
                "countEnrolmentRows", List.class);
        countEnrolmentRows.setAccessible(true);

        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
                anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Set.of(CAMPAIGN_BOUNDARY_CODE));

        // Mockito defaults int getters to 0, and a zero batch size makes the serviceCode
        // batching loop never advance — stub real values so populated-sheet cases terminate.
        when(config.getAttendanceRegisterSearchBatchSize()).thenReturn(50);
        when(config.getAttendanceRegisterSearchParallelCalls()).thenReturn(1);
        when(config.getAttendanceRegisterSearchUrl()).thenReturn("http://localhost/attendance/v1/_search");
        // A zero window size makes the HRMS lookup loop never advance
        when(config.getHrmsEmployeeSearchParallelCalls()).thenReturn(1);
    }

    // ── Register sheet ────────────────────────────────────────────────────

    @Test
    void registerSheetWithNoRowsIsRejected() throws Exception {
        List<ValidationError> errors = runRegisterValidation(new ArrayList<>());

        assertEquals(1, errors.size());
        assertEquals(ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ATLEAST_ONE_REQUIRED,
                errors.get(0).getErrorDetails());
        assertEquals(ValidationConstants.FIRST_DATA_ROW_NUMBER, errors.get(0).getRowNumber());
        assertEquals(ValidationConstants.STATUS_INVALID, errors.get(0).getStatus());
    }

    @Test
    void registerSheetWithOnlyBlankPaddedRowsIsRejected() throws Exception {
        // A generated template pads thousands of styled-but-valueless rows; the row loop skips
        // them, so a naive sheetData.isEmpty() check would have let this through.
        List<Map<String, Object>> sheetData = List.of(
                registerRow("", "", 3),
                registerRow("", "", 4),
                registerRow("", "", 5));

        List<ValidationError> errors = runRegisterValidation(sheetData);

        assertEquals(1, errors.size());
        assertEquals(ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ATLEAST_ONE_REQUIRED,
                errors.get(0).getErrorDetails());
    }

    @Test
    void registerSheetWithOneValidRowRaisesNoEmptySheetError() throws Exception {
        List<Map<String, Object>> sheetData = List.of(
                registerRow(CAMPAIGN_BOUNDARY_CODE, "REG-001", 3));

        List<ValidationError> errors = runRegisterValidation(sheetData);

        assertTrue(noEmptySheetError(errors, ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ATLEAST_ONE_REQUIRED),
                "a populated sheet must not raise the at-least-one-register error");
    }

    @Test
    void registerSheetUsesLocalisedMessageWhenAvailable() throws Exception {
        String localised = "Au moins un registre est requis.";
        Map<String, String> localisationMap = new HashMap<>();
        localisationMap.put(ValidationConstants.LOC_ATTENDANCE_REGISTER_ATLEAST_ONE_REQUIRED, localised);

        List<ValidationError> errors = runRegisterValidation(new ArrayList<>(), localisationMap);

        assertEquals(1, errors.size());
        assertEquals(localised, errors.get(0).getErrorDetails());
    }

    @Test
    void rowWithOnlyEventTypeGetsPerRowErrorNotEmptySheetError() throws Exception {
        // The user typed something, so telling them the sheet is empty would be wrong — they
        // should get the precise per-row error about the missing boundary / Register ID.
        Map<String, Object> row = registerRow("", "", 3);
        row.put(ProcessingConstants.REGISTER_EVENT_TYPE_COLUMN_KEY, "TRAINING");

        List<ValidationError> errors = runRegisterValidation(List.of(row));

        assertTrue(noEmptySheetError(errors, ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ATLEAST_ONE_REQUIRED),
                "a row carrying Event Type must not be reported as an empty sheet");
        assertEquals(1, errors.size());
        assertEquals(3, errors.get(0).getRowNumber());
    }

    @Test
    void rowWithOnlySessionsCountsAsData() throws Exception {
        Map<String, Object> row = registerRow("", "", 3);
        row.put(ProcessingConstants.REGISTER_SESSIONS_COLUMN_KEY, "2");

        List<ValidationError> errors = runRegisterValidation(List.of(row));

        assertTrue(noEmptySheetError(errors, ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ATLEAST_ONE_REQUIRED),
                "a row carrying Sessions must not be reported as an empty sheet");
    }

    // ── Attendee sheets ───────────────────────────────────────────────────

    @Test
    void errorGoesOnTheSheetTheUserFilledIn() throws Exception {
        // Worker sheet empty, one dateless person on the marker sheet. The message is about that
        // person, so it must not land on the empty worker sheet.
        assertEquals(0, invokeCountRowsWithUser(new ArrayList<>()));
        assertEquals(1, invokeCountRowsWithUser(List.of(attendeeRow("USR-206325", ""))));
    }

    @Test
    void processWorkbookWritesTheErrorOnlyOnTheSheetTheUserFilledIn() {
        Workbook workbook = new XSSFWorkbook();
        Sheet workerSheet = createAttendeeSheet(workbook, WORKER_SHEET);
        Sheet markerSheet = createAttendeeSheet(workbook, MARKER_SHEET);

        // Worker sheet has nobody; the marker sheet names one person with no enrolment date
        when(excelUtil.convertSheetToMapListCached(any(), eq(WORKER_SHEET), any()))
                .thenReturn(new ArrayList<>());
        when(excelUtil.convertSheetToMapListCached(any(), eq(MARKER_SHEET), any()))
                .thenReturn(List.of(attendeeRow("USR-206325", "", 3)));
        when(validationService.addValidationColumns(any(), any()))
                .thenAnswer(invocation -> appendValidationColumns(invocation.getArgument(0)));

        // One resource across both sheets, as the real per-sheet processing does
        ProcessResource resource = ProcessResource.builder()
                .id("resource-1").referenceId("campaign-1").tenantId("demo").fileStoreId("file-1").build();
        RequestInfo requestInfo = RequestInfo.builder().build();

        attendeeProcessor.processWorkbook(workbook, WORKER_SHEET, resource, requestInfo, new HashMap<>());
        attendeeProcessor.processWorkbook(workbook, MARKER_SHEET, resource, requestInfo, new HashMap<>());

        assertFalse(mentionsAtLeastOneRequired(workerSheet),
                "the empty worker sheet must not be told a person there is missing a date");
        assertTrue(mentionsAtLeastOneRequired(markerSheet),
                "the sheet the user filled in is where the missing date has to be fixed");
    }

    @Test
    void emptyWorkbookErrorIsRaisedOnlyOnceAcrossSheets() {
        ProcessResource resource = ProcessResource.builder().id("resource-1").tenantId("demo").build();

        assertFalse(invokeAlreadyReported(resource), "nothing reported before the first empty sheet");
        invokeMarkReported(resource);
        assertTrue(invokeAlreadyReported(resource),
                "second and third attendee sheets must see the error as already reported");
    }

    @Test
    void enrolmentCountIsZeroForEmptySheet() throws Exception {
        assertEquals(0, invokeCountEnrolmentRows(new ArrayList<>()));
    }

    @Test
    void enrolmentCountIgnoresRowsWithoutAUsername() throws Exception {
        List<Map<String, Object>> sheetData = List.of(
                attendeeRow("", "01-09-2026"),
                attendeeRow("   ", "01-09-2026"));

        assertEquals(0, invokeCountEnrolmentRows(sheetData));
    }

    @Test
    void enrolmentCountIgnoresPreFilledUsersWithNoEnrolmentDate() throws Exception {
        // The template lists every campaign user, so a row without a date is a legitimate skip and
        // enrols nobody. An upload made only of these achieves nothing and must be rejected.
        List<Map<String, Object>> sheetData = List.of(
                attendeeRow("USER_1", ""),
                attendeeRow("USER_2", "   "));

        assertEquals(0, invokeCountEnrolmentRows(sheetData));
    }

    @Test
    void enrolmentCountCountsOnlyRowsWithAUsernameAndAnEnrolmentDate() throws Exception {
        List<Map<String, Object>> sheetData = List.of(
                attendeeRow("USER_1", "01-09-2026"),
                attendeeRow("USER_2", ""),
                attendeeRow("", "01-09-2026"),
                attendeeRow("USER_3", "02-09-2026"));

        assertEquals(2, invokeCountEnrolmentRows(sheetData));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ValidationError> runRegisterValidation(List<Map<String, Object>> sheetData) throws Exception {
        return runRegisterValidation(sheetData, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private List<ValidationError> runRegisterValidation(List<Map<String, Object>> sheetData,
                                                        Map<String, String> localisationMap) throws Exception {
        ProcessResource resource = ProcessResource.builder()
                .id("resource-1")
                .referenceId("campaign-1")
                .tenantId("demo")
                .hierarchyType("NIGERIA")
                .build();

        List<ValidationError> errors = new ArrayList<>();
        validateRegisterData.invoke(registerProcessor, sheetData, resource,
                RequestInfo.builder().build(), errors, localisationMap);
        return errors;
    }

    private int invokeCountRowsWithUser(List<Map<String, Object>> sheetData) throws Exception {
        Method m = AttendanceRegisterAttendeeValidationProcessor.class
                .getDeclaredMethod("countRowsWithUser", List.class);
        m.setAccessible(true);
        return (int) m.invoke(attendeeProcessor, sheetData);
    }

    private int invokeCountEnrolmentRows(List<Map<String, Object>> sheetData) throws Exception {
        return (int) countEnrolmentRows.invoke(attendeeProcessor, sheetData);
    }

    private boolean invokeAlreadyReported(ProcessResource resource) {
        try {
            Method m = AttendanceRegisterAttendeeValidationProcessor.class
                    .getDeclaredMethod("alreadyReportedEmptyWorkbook", ProcessResource.class);
            m.setAccessible(true);
            return (boolean) m.invoke(attendeeProcessor, resource);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void invokeMarkReported(ProcessResource resource) {
        try {
            Method m = AttendanceRegisterAttendeeValidationProcessor.class
                    .getDeclaredMethod("markEmptyWorkbookReported", ProcessResource.class);
            m.setAccessible(true);
            m.invoke(attendeeProcessor, resource);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Header row only, which is what a generated attendee sheet with no typed rows looks like. */
    private Sheet createAttendeeSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(ProcessingConstants.USERNAME_COLUMN_KEY);
        header.createCell(1).setCellValue(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY);
        return sheet;
    }

    /** Stands in for ValidationService, which appends the status and error columns to the sheet. */
    private ValidationColumnInfo appendValidationColumns(Sheet sheet) {
        Row header = sheet.getRow(0);
        int statusColumn = header.getLastCellNum();
        header.createCell(statusColumn).setCellValue(ValidationConstants.STATUS_COLUMN_NAME);
        header.createCell(statusColumn + 1).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
        return new ValidationColumnInfo(statusColumn, statusColumn + 1);
    }

    private boolean mentionsAtLeastOneRequired(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String value = ExcelUtil.getCellValueAsString(cell);
                if (value != null
                        && value.contains(ValidationConstants.DEFAULT_ATTENDANCE_ATTENDEE_ATLEAST_ONE_REQUIRED)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, Object> registerRow(String boundaryCode, String registerId, int rowNumber) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(BOUNDARY_COLUMN, boundaryCode);
        row.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, boundaryCode);
        row.put(ProcessingConstants.REGISTER_ID_COLUMN_KEY, registerId);
        row.put(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY, rowNumber);
        return row;
    }

    private Map<String, Object> attendeeRow(String username, String enrolmentDate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(ProcessingConstants.USERNAME_COLUMN_KEY, username);
        row.put(ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY, enrolmentDate);
        return row;
    }

    /** The per-row validation reads the sheet row number, which the real converter always sets. */
    private Map<String, Object> attendeeRow(String username, String enrolmentDate, int rowNumber) {
        Map<String, Object> row = attendeeRow(username, enrolmentDate);
        row.put(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY, rowNumber);
        return row;
    }

    private boolean noEmptySheetError(List<ValidationError> errors, String message) {
        return errors.stream().noneMatch(e -> message.equals(e.getErrorDetails()));
    }
}
