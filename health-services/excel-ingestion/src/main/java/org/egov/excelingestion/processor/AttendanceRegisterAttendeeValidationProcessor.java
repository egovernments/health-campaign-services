package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.excelingestion.config.ProcessingConstants;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Validator for attendance register attendee sheets (Worker, Marker, Approver).
 * Same processor handles all 3 sheets — validations are identical.
 *
 * Validations (O(n) single pass):
 * 1. Register ID not empty on data rows
 * 2. Register ID matches expected registerId
 * 3. Enrollment date format (dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd, yyyy/MM/dd)
 * 4. De-enrollment date format
 * 5. Enrollment date within register date range
 * 6. De-enrollment date within register date range
 * 7. De-enrollment >= enrollment
 * 8. Truth-table: enrollment date required for new attendees/staff
 * 9. Truth-table: cannot change enrollment date on existing records
 * 10. Truth-table: cannot change de-enrollment date on existing de-enrolled records
 */
@Component
@Slf4j
public class AttendanceRegisterAttendeeValidationProcessor implements IWorkbookProcessor {

    private static final DateTimeFormatter FORMAT_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMAT_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMAT_ISO_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMAT_ISO_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // Column keys — sourced from ProcessingConstants
    private static final String COL_REGISTER_ID = ProcessingConstants.REGISTER_ID_COLUMN_KEY;
    private static final String COL_ENROLLMENT_DATE = ProcessingConstants.ENROLLMENT_DATE_COLUMN_KEY;
    private static final String COL_DEENROLLMENT_DATE = ProcessingConstants.DEENROLLMENT_DATE_COLUMN_KEY;
    private static final String COL_USERNAME = ProcessingConstants.USERNAME_COLUMN_KEY;
    private static final String COL_TEAM_CODE = ProcessingConstants.TEAM_CODE_COLUMN_KEY;

    // Localization keys — sourced from ValidationConstants
    private static final String LOC_INVALID_DATE = ValidationConstants.LOC_ATTENDANCE_INVALID_DATE;
    private static final String LOC_DATE_OUT_OF_RANGE = ValidationConstants.LOC_ATTENDANCE_DATE_OUT_OF_RANGE;
    private static final String LOC_DEENROLL_BEFORE_ENROLL = ValidationConstants.LOC_ATTENDANCE_DEENROLL_BEFORE_ENROLL;
    private static final String LOC_REGISTER_ID_EMPTY = ValidationConstants.LOC_ATTENDANCE_REGISTER_ID_EMPTY;
    private static final String LOC_REGISTER_ID_MISMATCH = ValidationConstants.LOC_ATTENDANCE_REGISTER_ID_MISMATCH;
    private static final String LOC_REGISTER_WRONG_CAMPAIGN = ValidationConstants.LOC_ATTENDANCE_REGISTER_WRONG_CAMPAIGN;
    private static final String LOC_ENROLLMENT_DATE_REQUIRED = ValidationConstants.LOC_ATTENDANCE_ENROLLMENT_DATE_REQUIRED;
    private static final String LOC_CANNOT_CHANGE_ENROLLMENT_DATE = ValidationConstants.LOC_ATTENDANCE_CANNOT_CHANGE_ENROLLMENT_DATE;
    private static final String LOC_CANNOT_CHANGE_DEENROLLMENT_DATE = ValidationConstants.LOC_ATTENDANCE_CANNOT_CHANGE_DEENROLLMENT_DATE;

    // Default error messages — sourced from ValidationConstants
    private static final String DEFAULT_INVALID_DATE = ValidationConstants.DEFAULT_ATTENDANCE_INVALID_DATE;
    private static final String DEFAULT_DATE_OUT_OF_RANGE = ValidationConstants.DEFAULT_ATTENDANCE_DATE_OUT_OF_RANGE;
    private static final String DEFAULT_DEENROLL_BEFORE_ENROLL = ValidationConstants.DEFAULT_ATTENDANCE_DEENROLL_BEFORE_ENROLL;
    private static final String DEFAULT_REGISTER_ID_EMPTY = ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ID_EMPTY;
    private static final String DEFAULT_REGISTER_ID_MISMATCH = ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_ID_MISMATCH;
    private static final String DEFAULT_REGISTER_WRONG_CAMPAIGN = ValidationConstants.DEFAULT_ATTENDANCE_REGISTER_WRONG_CAMPAIGN;
    private static final String DEFAULT_ENROLLMENT_DATE_REQUIRED = ValidationConstants.DEFAULT_ATTENDANCE_ENROLLMENT_DATE_REQUIRED;
    private static final String DEFAULT_CANNOT_CHANGE_ENROLLMENT_DATE = ValidationConstants.DEFAULT_ATTENDANCE_CANNOT_CHANGE_ENROLLMENT_DATE;
    private static final String DEFAULT_CANNOT_CHANGE_DEENROLLMENT_DATE = ValidationConstants.DEFAULT_ATTENDANCE_CANNOT_CHANGE_DEENROLLMENT_DATE;

    private final ValidationService validationService;
    private final EnrichmentUtil enrichmentUtil;
    private final ExcelUtil excelUtil;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelIngestionConfig config;
    private final CustomExceptionHandler exceptionHandler;

    public AttendanceRegisterAttendeeValidationProcessor(
            ValidationService validationService,
            EnrichmentUtil enrichmentUtil,
            ExcelUtil excelUtil,
            ServiceRequestRepository serviceRequestRepository,
            ExcelIngestionConfig config,
            CustomExceptionHandler exceptionHandler) {
        this.validationService = validationService;
        this.enrichmentUtil = enrichmentUtil;
        this.excelUtil = excelUtil;
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public Workbook processWorkbook(Workbook workbook,
                                    String sheetName,
                                    ProcessResource resource,
                                    RequestInfo requestInfo,
                                    Map<String, String> localizationMap) {
        try {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                log.warn("Sheet {} not found in workbook", sheetName);
                return workbook;
            }

            log.info("Starting attendee validation for sheet: {}", sheetName);

            // Convert sheet data to map list (cached)
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);

            // Get expected registerId from additionalDetails
            String expectedRegisterId = extractRegisterId(resource);

            // Fetch attendance register (includes attendees/staff for truth-table validation)
            RegisterContext registerContext = fetchRegisterContext(expectedRegisterId,
                    resource.getTenantId(), requestInfo);

            // Validate campaign ownership — register must belong to the current campaign
            String expectedCampaignId = extractCampaignId(resource);
            if (registerContext != null && !expectedCampaignId.isEmpty()
                    && !registerContext.campaignId.isEmpty()
                    && !registerContext.campaignId.equals(expectedCampaignId)) {
                List<ValidationError> campaignErrors = buildCampaignMismatchErrors(sheetData, localizationMap);
                if (!campaignErrors.isEmpty()) {
                    ValidationColumnInfo columnInfo = checkAndAddErrorColumns(sheet, localizationMap);
                    processValidationErrors(sheet, campaignErrors, columnInfo);
                }
                enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, campaignErrors);
                return workbook;
            }

            // Resolve HRMS usernames → individualIds for truth-table validation
            Map<String, String> usernameToIndividualId = resolveUsernames(sheetData, resource.getTenantId(), requestInfo);

            // Detect if this is a worker sheet (has team code column)
            boolean isWorkerSheet = hasTeamCodeColumn(sheetData);

            // Determine staff type for marker/approver sheets
            String staffType = determineStaffType(sheetName, resource);

            // Single-pass O(n) validation
            List<ValidationError> errors = new ArrayList<>();
            validateAttendeeData(sheetData, expectedRegisterId, registerContext,
                    usernameToIndividualId, isWorkerSheet, staffType, errors, localizationMap);

            log.info("Attendee validation completed for sheet {} with {} errors", sheetName, errors.size());

            // Add error columns if there are validation errors
            if (!errors.isEmpty()) {
                ValidationColumnInfo columnInfo = checkAndAddErrorColumns(sheet, localizationMap);
                processValidationErrors(sheet, errors, columnInfo);
            }

            // Enrich resource with error summary
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);

            return workbook;

        } catch (org.egov.tracer.model.CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing attendee validation for sheet {}: {}", sheetName, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.ATTENDEE_VALIDATION_FAILED,
                    ErrorConstants.ATTENDEE_VALIDATION_FAILED_MESSAGE + ": " + e.getMessage(), e);
            return workbook;
        }
    }

    /**
     * Validate attendee data - O(n) single pass.
     * Performs structural validations (format, range) and truth-table business validations
     * (date immutability, enrollment required).
     */
    private void validateAttendeeData(List<Map<String, Object>> sheetData,
                                       String expectedRegisterId,
                                       RegisterContext registerContext,
                                       Map<String, String> usernameToIndividualId,
                                       boolean isWorkerSheet,
                                       String staffType,
                                       List<ValidationError> errors,
                                       Map<String, String> localizationMap) {

        log.info("Validating {} attendee rows", sheetData.size());

        for (Map<String, Object> row : sheetData) {
            String registerId = getVal(row, COL_REGISTER_ID);
            String enrollmentStr = getVal(row, COL_ENROLLMENT_DATE);
            String deEnrollmentStr = getVal(row, COL_DEENROLLMENT_DATE);
            String userName = getVal(row, COL_USERNAME);
            String teamCode = isWorkerSheet ? getVal(row, COL_TEAM_CODE) : "";

            // Skip fully empty rows (no user data and no dates)
            if (userName.isEmpty() && registerId.isEmpty()
                    && enrollmentStr.isEmpty() && deEnrollmentStr.isEmpty()
                    && teamCode.isEmpty()) {
                continue;
            }

            int rowNumber = ((Number) row.get(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY)).intValue();

            List<String> rowErrors = new ArrayList<>();

            // --- Structural validations (existing) ---

            // 1. Register ID must not be empty
            if (registerId.isEmpty()) {
                rowErrors.add(getLocalizedMessage(localizationMap, LOC_REGISTER_ID_EMPTY,
                        DEFAULT_REGISTER_ID_EMPTY));
            } else {
                // 2. Register ID must match expected UUID or service code
                String expectedServiceCode = (registerContext != null && !registerContext.serviceCode.isEmpty())
                        ? registerContext.serviceCode : null;
                boolean matchesUuid = registerId.equals(expectedRegisterId);
                boolean matchesServiceCode = expectedServiceCode != null && registerId.equals(expectedServiceCode);
                if (!matchesUuid && !matchesServiceCode) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_REGISTER_ID_MISMATCH,
                            DEFAULT_REGISTER_ID_MISMATCH));
                }
            }

            // 3. Validate enrollment date format and range
            LocalDate enrollmentDate = null;
            if (!enrollmentStr.isEmpty()) {
                enrollmentDate = parseDate(enrollmentStr);
                if (enrollmentDate == null) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_INVALID_DATE,
                            DEFAULT_INVALID_DATE));
                } else if (registerContext != null && !isDateInRange(enrollmentDate, registerContext)) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_DATE_OUT_OF_RANGE,
                            DEFAULT_DATE_OUT_OF_RANGE));
                }
            }

            // 4. Validate de-enrollment date format and range
            LocalDate deEnrollmentDate = null;
            if (!deEnrollmentStr.isEmpty()) {
                deEnrollmentDate = parseDate(deEnrollmentStr);
                if (deEnrollmentDate == null) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_INVALID_DATE,
                            DEFAULT_INVALID_DATE));
                } else {
                    if (registerContext != null && !isDateInRange(deEnrollmentDate, registerContext)) {
                        rowErrors.add(getLocalizedMessage(localizationMap, LOC_DATE_OUT_OF_RANGE,
                                DEFAULT_DATE_OUT_OF_RANGE));
                    }

                    // 5. De-enrollment >= enrollment
                    if (enrollmentDate != null && deEnrollmentDate.isBefore(enrollmentDate)) {
                        rowErrors.add(getLocalizedMessage(localizationMap, LOC_DEENROLL_BEFORE_ENROLL,
                                DEFAULT_DEENROLL_BEFORE_ENROLL));
                    }
                }
            }

            // --- Truth-table business validations ---
            // Only run if no structural errors (format issues would make date comparison unreliable)
            if (rowErrors.isEmpty() && registerContext != null) {
                String individualId = usernameToIndividualId.get(userName);
                if (individualId != null && !individualId.isEmpty()) {
                    List<String> businessErrors = validateTruthTableRules(
                            individualId, enrollmentDate, deEnrollmentDate, teamCode,
                            isWorkerSheet, staffType, registerContext, localizationMap);
                    rowErrors.addAll(businessErrors);
                }
            }

            if (!rowErrors.isEmpty()) {
                errors.add(ValidationError.builder()
                        .rowNumber(rowNumber)
                        .errorDetails(String.join("; ", rowErrors))
                        .status(ValidationConstants.STATUS_INVALID)
                        .build());
            }
        }

        log.info("Attendee validation completed. Total errors: {}", errors.size());
    }

    /**
     * Apply truth-table INVALID rules based on existing record state.
     * Covers cases: A2/A4/A7, B3/B6/B10/B13, C3/C5/C7-C9/C13/C14, D3, E3/E6, F3/F5/F7/F8.
     */
    private List<String> validateTruthTableRules(String individualId,
                                                  LocalDate enrollmentDate,
                                                  LocalDate deEnrollmentDate,
                                                  String teamCode,
                                                  boolean isWorkerSheet,
                                                  String staffType,
                                                  RegisterContext registerContext,
                                                  Map<String, String> localizationMap) {
        List<String> errors = new ArrayList<>();

        // Look up existing record
        Map<String, Object> existing;
        if (isWorkerSheet) {
            existing = registerContext.attendeesMap.get(individualId);
        } else {
            String staffKey = individualId + "_" + staffType;
            existing = registerContext.staffMap.get(staffKey);
        }

        if (existing == null) {
            // NEW record — Section A (attendee) or Section D (staff)
            boolean hasEnrollment = enrollmentDate != null;
            boolean hasDeEnrollment = deEnrollmentDate != null;
            boolean hasTeamCode = !teamCode.isEmpty();

            if (!hasEnrollment && !hasDeEnrollment && !hasTeamCode) {
                // A1/D1: skip — no error (row will be skipped during processing)
                return errors;
            }
            if (!hasEnrollment) {
                // A2/A4/A7/D3: INVALID — enrollment date required
                errors.add(getLocalizedMessage(localizationMap, LOC_ENROLLMENT_DATE_REQUIRED,
                        DEFAULT_ENROLLMENT_DATE_REQUIRED));
            }
            return errors;
        }

        // Extract existing dates from attendance service record
        LocalDate existingEnrollmentDate = extractExistingDate(existing, ProcessingConstants.ENROLLMENT_DATE_KEY);
        LocalDate existingDeEnrollmentDate = extractExistingDate(existing, ProcessingConstants.DEENROLLMENT_DATE_KEY);

        if (existingDeEnrollmentDate != null) {
            // DE-ENROLLED record — Section C (attendee) or Section F (staff)
            // Enrollment date checked FIRST (per truth table C9)
            if (enrollmentDate != null && existingEnrollmentDate != null
                    && !enrollmentDate.equals(existingEnrollmentDate)) {
                // C3/C7/C9/C13/F3/F7
                errors.add(getLocalizedMessage(localizationMap, LOC_CANNOT_CHANGE_ENROLLMENT_DATE,
                        DEFAULT_CANNOT_CHANGE_ENROLLMENT_DATE));
                return errors;
            }
            if (deEnrollmentDate != null && !deEnrollmentDate.equals(existingDeEnrollmentDate)) {
                // C5/C8/C14/F5/F8
                errors.add(getLocalizedMessage(localizationMap, LOC_CANNOT_CHANGE_DEENROLLMENT_DATE,
                        DEFAULT_CANNOT_CHANGE_DEENROLLMENT_DATE));
            }
            return errors;
        }

        // ACTIVE record — Section B (attendee) or Section E (staff)
        if (enrollmentDate != null && existingEnrollmentDate != null
                && !enrollmentDate.equals(existingEnrollmentDate)) {
            // B3/B6/B10/B13/E3/E6
            errors.add(getLocalizedMessage(localizationMap, LOC_CANNOT_CHANGE_ENROLLMENT_DATE,
                    DEFAULT_CANNOT_CHANGE_ENROLLMENT_DATE));
        }
        return errors;
    }

    /**
     * Extract a date field from an existing attendee/staff record (epoch millis → LocalDate).
     * Returns null if the field is absent or null.
     */
    private LocalDate extractExistingDate(Map<String, Object> existing, String fieldName) {
        Object value = existing.get(fieldName);
        if (value == null) return null;
        if (value instanceof Number) {
            long epochMillis = ((Number) value).longValue();
            if (epochMillis == 0) return null;
            return epochMillisToLocalDate(epochMillis);
        }
        return null;
    }

    /**
     * Resolve usernames to individualIds via HRMS employee search.
     * Returns map of username → individualId (uuid).
     */
    private Map<String, String> resolveUsernames(List<Map<String, Object>> sheetData,
                                                  String tenantId,
                                                  RequestInfo requestInfo) {
        Map<String, String> result = new HashMap<>();

        // Collect unique non-empty usernames
        Set<String> usernames = new LinkedHashSet<>();
        for (Map<String, Object> row : sheetData) {
            String username = getVal(row, COL_USERNAME);
            if (!username.isEmpty()) {
                usernames.add(username);
            }
        }

        if (usernames.isEmpty()) return result;

        String rootTenantId = tenantId.contains(".") ? tenantId.split("\\.")[0] : tenantId;
        String searchUrl = config.getHrmsEmployeeSearchUrl();
        int parallelLimit = config.getHrmsEmployeeSearchParallelCalls();
        List<String> usernameList = new ArrayList<>(usernames);

        // HRMS supports only 1 username per search call — fire parallel calls in windows
        for (int i = 0; i < usernameList.size(); i += parallelLimit) {
            int end = Math.min(i + parallelLimit, usernameList.size());
            List<String> batch = usernameList.subList(i, end);

            // Fire all calls in this window in parallel using CompletableFuture
            List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
            for (String username : batch) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        StringBuilder url = new StringBuilder(searchUrl);
                        url.append("?tenantId=").append(rootTenantId)
                           .append("&limit=2&offset=0")
                           .append("&codes=").append(username);

                        Map<String, Object> payload = new HashMap<>();
                        payload.put(ProcessingConstants.REQUEST_INFO_KEY, requestInfo);

                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = (Map<String, Object>) serviceRequestRepository.fetchResult(url, payload);

                        if (response != null && response.get(ProcessingConstants.HRMS_EMPLOYEES_RESPONSE_KEY) != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> employees = (List<Map<String, Object>>) response.get(ProcessingConstants.HRMS_EMPLOYEES_RESPONSE_KEY);
                            for (Map<String, Object> emp : employees) {
                                String code = emp.get(ProcessingConstants.HRMS_EMPLOYEE_CODE_KEY) != null
                                        ? String.valueOf(emp.get(ProcessingConstants.HRMS_EMPLOYEE_CODE_KEY)).trim() : "";
                                @SuppressWarnings("unchecked")
                                Map<String, Object> user = (Map<String, Object>) emp.get(ProcessingConstants.HRMS_EMPLOYEE_USER_KEY);
                                String uuid = (user != null && user.get(ProcessingConstants.HRMS_USER_UUID_KEY) != null)
                                        ? String.valueOf(user.get(ProcessingConstants.HRMS_USER_UUID_KEY)).trim() : "";
                                if (!code.isEmpty() && !uuid.isEmpty()) {
                                    return Map.entry(code, uuid);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("HRMS search failed for user {}: {}", username, e.getMessage());
                    }
                    return null;
                }));
            }

            // Wait for all calls in this window to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<Map.Entry<String, String>> future : futures) {
                try {
                    Map.Entry<String, String> entry = future.get();
                    if (entry != null) {
                        result.put(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    log.warn("Error retrieving HRMS result: {}", e.getMessage());
                }
            }
        }

        log.info("Resolved {}/{} usernames via HRMS (parallelLimit={})", result.size(), usernames.size(), parallelLimit);
        return result;
    }

    /**
     * Detect if the current sheet is a worker sheet by checking for team code column.
     */
    private boolean hasTeamCodeColumn(List<Map<String, Object>> sheetData) {
        if (sheetData.isEmpty()) return false;
        return sheetData.get(0).containsKey(COL_TEAM_CODE);
    }

    /**
     * Determine staff type (OWNER for marker, APPROVER for approver sheets).
     * Falls back based on sheet name patterns if no explicit config is available.
     */
    private String determineStaffType(String sheetName, ProcessResource resource) {
        // Check additionalDetails for explicit staffType mapping
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        if (additionalDetails != null && additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_STAFF_TYPE) != null) {
            return String.valueOf(additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_STAFF_TYPE)).trim();
        }
        // Convention: sheet name containing "approver" (case-insensitive) → APPROVER, else OWNER
        if (sheetName.toLowerCase().contains(ProcessingConstants.STAFF_TYPE_APPROVER.toLowerCase())) {
            return ProcessingConstants.STAFF_TYPE_APPROVER;
        }
        return ProcessingConstants.STAFF_TYPE_OWNER;
    }

    /**
     * Parse date string — supports dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd, yyyy/MM/dd
     */
    private LocalDate parseDate(String dateStr) {
        String trimmed = dateStr.trim();
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{
                FORMAT_SLASH, FORMAT_DASH, FORMAT_ISO_DASH, FORMAT_ISO_SLASH}) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    /**
     * Check if date falls within register's start/end range (inclusive)
     */
    private boolean isDateInRange(LocalDate date, RegisterContext context) {
        return !date.isBefore(context.startDate) && !date.isAfter(context.endDate);
    }

    /**
     * Convert epoch millis to LocalDate using the configured server timezone.
     */
    private LocalDate epochMillisToLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(config.getServerZoneId()).toLocalDate();
    }

    /**
     * Extract registerId: use referenceId when referenceType is attendanceRegister,
     * otherwise fall back to additionalDetails.registerId for backward compatibility.
     */
    private String extractRegisterId(ProcessResource resource) {
        if (ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER.equals(resource.getReferenceType())) {
            String registerId = resource.getReferenceId();
            if (registerId != null && !registerId.isBlank()) {
                return registerId.trim();
            }
        }

        // Fallback to additionalDetails.registerId
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        if (additionalDetails != null && additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_REGISTER_ID) != null) {
            return String.valueOf(additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_REGISTER_ID)).trim();
        }
        return "";
    }

    /**
     * Fetch attendance register and extract dates, attendees, and staff for validation.
     */
    private RegisterContext fetchRegisterContext(String registerId, String tenantId,
                                                 RequestInfo requestInfo) {
        if (registerId.isEmpty()) {
            log.warn("No registerId provided, skipping date range and truth-table validation");
            return null;
        }

        try {
            StringBuilder url = new StringBuilder(config.getAttendanceRegisterSearchUrl());
            url.append("?tenantId=").append(tenantId)
               .append("&ids=").append(registerId);

            Map<String, Object> payload = new HashMap<>();
            payload.put(ProcessingConstants.REQUEST_INFO_KEY, requestInfo);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) serviceRequestRepository.fetchResult(url, payload);

            if (response == null || response.get(ProcessingConstants.ATTENDANCE_REGISTER_RESPONSE_KEY) == null) {
                log.warn("Attendance register not found: {}", registerId);
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> registers =
                    (List<Map<String, Object>>) response.get(ProcessingConstants.ATTENDANCE_REGISTER_RESPONSE_KEY);

            if (registers.isEmpty()) {
                log.warn("Attendance register not found: {}", registerId);
                return null;
            }

            Map<String, Object> register = registers.get(0);
            Object startDateObj = register.get(ProcessingConstants.ATTENDANCE_REGISTER_START_DATE_KEY);
            Object endDateObj = register.get(ProcessingConstants.ATTENDANCE_REGISTER_END_DATE_KEY);

            if (startDateObj == null || endDateObj == null) {
                log.warn("Register {} missing start/end dates, skipping date range validation", registerId);
                return null;
            }

            if (!(startDateObj instanceof Number) || !(endDateObj instanceof Number)) {
                log.warn("Register {} has non-numeric start/end dates, skipping date range validation", registerId);
                return null;
            }
            long startEpoch = ((Number) startDateObj).longValue();
            long endEpoch = ((Number) endDateObj).longValue();

            String serviceCode = register.get(ProcessingConstants.ATTENDANCE_REGISTER_SERVICE_CODE_KEY) != null
                    ? String.valueOf(register.get(ProcessingConstants.ATTENDANCE_REGISTER_SERVICE_CODE_KEY)).trim() : "";

            String campaignId = register.get(ProcessingConstants.ATTENDANCE_REGISTER_CAMPAIGN_ID_KEY) != null
                    ? String.valueOf(register.get(ProcessingConstants.ATTENDANCE_REGISTER_CAMPAIGN_ID_KEY)).trim() : "";

            // Build attendeesMap from register.attendees — O(n)
            Map<String, Map<String, Object>> attendeesMap = new HashMap<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attendees = (List<Map<String, Object>>) register.get(ProcessingConstants.ATTENDANCE_REGISTER_ATTENDEES_KEY);
            if (attendees != null) {
                for (Map<String, Object> attendee : attendees) {
                    String individualId = attendee.get(ProcessingConstants.ATTENDEE_INDIVIDUAL_ID_KEY) != null
                            ? String.valueOf(attendee.get(ProcessingConstants.ATTENDEE_INDIVIDUAL_ID_KEY)).trim() : "";
                    if (!individualId.isEmpty()) {
                        attendeesMap.put(individualId, attendee);
                    }
                }
            }

            // Build staffMap from register.staff — O(n), keyed by "userId_staffType"
            Map<String, Map<String, Object>> staffMap = new HashMap<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> staff = (List<Map<String, Object>>) register.get(ProcessingConstants.ATTENDANCE_REGISTER_STAFF_KEY);
            if (staff != null) {
                for (Map<String, Object> staffMember : staff) {
                    String userId = staffMember.get(ProcessingConstants.STAFF_USER_ID_KEY) != null
                            ? String.valueOf(staffMember.get(ProcessingConstants.STAFF_USER_ID_KEY)).trim() : "";
                    String type = staffMember.get(ProcessingConstants.STAFF_TYPE_KEY) != null
                            ? String.valueOf(staffMember.get(ProcessingConstants.STAFF_TYPE_KEY)).trim() : ProcessingConstants.STAFF_TYPE_OWNER;
                    if (!userId.isEmpty()) {
                        staffMap.put(userId + "_" + type, staffMember);
                    }
                }
            }

            RegisterContext context = new RegisterContext(
                    epochMillisToLocalDate(startEpoch),
                    epochMillisToLocalDate(endEpoch),
                    serviceCode,
                    campaignId,
                    attendeesMap,
                    staffMap);

            log.info("Register {} date range: {} to {}, serviceCode: {}, attendees: {}, staff: {}",
                    registerId, context.startDate, context.endDate, context.serviceCode,
                    attendeesMap.size(), staffMap.size());
            return context;

        } catch (Exception e) {
            log.error("Error fetching register context for {}: {}", registerId, e.getMessage());
            return null; // Continue validation without truth-table checks
        }
    }

    /**
     * Check if error columns exist and add them if needed
     */
    private ValidationColumnInfo checkAndAddErrorColumns(Sheet sheet, Map<String, String> localizationMap) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return validationService.addValidationColumns(sheet, localizationMap);
        }

        int errorColumnIndex = -1;
        int statusColumnIndex = -1;

        for (Cell cell : headerRow) {
            String headerValue = ExcelUtil.getCellValueAsString(cell);
            if (ValidationConstants.ERROR_DETAILS_COLUMN_NAME.equals(headerValue)) {
                errorColumnIndex = cell.getColumnIndex();
            } else if (ValidationConstants.STATUS_COLUMN_NAME.equals(headerValue)) {
                statusColumnIndex = cell.getColumnIndex();
            }
        }

        if (errorColumnIndex != -1 && statusColumnIndex != -1) {
            return new ValidationColumnInfo(statusColumnIndex, errorColumnIndex);
        }

        return validationService.addValidationColumns(sheet, localizationMap);
    }

    /**
     * Write validation errors to the sheet
     */
    private void processValidationErrors(Sheet sheet, List<ValidationError> errors,
                                          ValidationColumnInfo columnInfo) {
        int statusColumnIndex = columnInfo.getStatusColumnIndex();
        int errorColumnIndex = columnInfo.getErrorColumnIndex();

        for (ValidationError error : errors) {
            int excelRowNumber = error.getRowNumber();
            Row row = sheet.getRow(excelRowNumber - 1); // Convert to 0-based index

            if (row != null) {
                Cell statusCell = row.getCell(statusColumnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                statusCell.setCellValue(ValidationConstants.STATUS_INVALID);

                Cell errorCell = row.getCell(errorColumnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                errorCell.setCellValue(error.getErrorDetails());
            }
        }
    }

    private String getVal(Map<String, Object> row, String key) {
        return ExcelUtil.getValueAsString(row.get(key), config.getServerZoneId()).trim();
    }

    private String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMsg) {
        if (localizationMap != null) {
            return localizationMap.getOrDefault(key, defaultMsg);
        }
        return defaultMsg;
    }

    /**
     * Extract campaignId from the resource.
     * - referenceType "campaign": campaignId is the referenceId itself.
     * - referenceType "attendanceRegister": campaignId is in additionalDetails.
     */
    private String extractCampaignId(ProcessResource resource) {
        if (ProcessingConstants.REFERENCE_TYPE_CAMPAIGN.equals(resource.getReferenceType())) {
            String referenceId = resource.getReferenceId();
            return (referenceId != null && !referenceId.isBlank()) ? referenceId.trim() : "";
        }
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        if (additionalDetails != null && additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_CAMPAIGN_ID) != null) {
            return String.valueOf(additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_CAMPAIGN_ID)).trim();
        }
        return "";
    }

    /**
     * Build validation errors for all non-empty data rows when register belongs to a different campaign.
     */
    private List<ValidationError> buildCampaignMismatchErrors(List<Map<String, Object>> sheetData,
                                                               Map<String, String> localizationMap) {
        String errorMsg = getLocalizedMessage(localizationMap, LOC_REGISTER_WRONG_CAMPAIGN, DEFAULT_REGISTER_WRONG_CAMPAIGN);
        List<ValidationError> errors = new ArrayList<>();
        for (Map<String, Object> row : sheetData) {
            String registerId = getVal(row, COL_REGISTER_ID);
            String userName = getVal(row, COL_USERNAME);
            if (!userName.isEmpty() || !registerId.isEmpty()) {
                int rowNumber = ((Number) row.get(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY)).intValue();
                errors.add(ValidationError.builder()
                        .rowNumber(rowNumber)
                        .errorDetails(errorMsg)
                        .status(ValidationConstants.STATUS_INVALID)
                        .build());
            }
        }
        return errors;
    }

    /**
     * Holds register metadata, date range, and existing attendee/staff maps for validation.
     */
    private static class RegisterContext {
        final LocalDate startDate;
        final LocalDate endDate;
        final String serviceCode;
        final String campaignId;
        final Map<String, Map<String, Object>> attendeesMap;
        final Map<String, Map<String, Object>> staffMap;

        RegisterContext(LocalDate startDate, LocalDate endDate, String serviceCode, String campaignId,
                        Map<String, Map<String, Object>> attendeesMap, Map<String, Map<String, Object>> staffMap) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.serviceCode = serviceCode;
            this.campaignId = campaignId;
            this.attendeesMap = attendeesMap;
            this.staffMap = staffMap;
        }
    }
}
