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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Validator for attendance register attendee sheets (Worker, Marker, Approver).
 * Same processor handles all 3 sheets — validations are identical.
 *
 * Validations (O(n) single pass):
 * 1. Register ID not empty on data rows
 * 2. Register ID matches expected registerId
 * 3. Enrollment date format (dd-MM-yyyy or dd/MM/yyyy)
 * 4. De-enrollment date format
 * 5. Enrollment date within register date range
 * 6. De-enrollment date within register date range
 * 7. De-enrollment requires enrollment
 * 8. De-enrollment >= enrollment
 */
@Component
@Slf4j
public class AttendanceRegisterAttendeeValidationProcessor implements IWorkbookProcessor {

    private static final DateTimeFormatter FORMAT_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMAT_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Column keys
    private static final String COL_REGISTER_ID = "HCM_ATTENDANCE_REGISTER_ID";
    private static final String COL_ENROLLMENT_DATE = "HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE";
    private static final String COL_DEENROLLMENT_DATE = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE";
    private static final String COL_USERNAME = "UserName";

    // Localization keys for error messages
    private static final String LOC_INVALID_DATE = "HCM_ATTENDANCE_ATTENDEE_INVALID_DATE_FORMAT";
    private static final String LOC_DATE_OUT_OF_RANGE = "HCM_ATTENDANCE_ATTENDEE_DATE_OUT_OF_RANGE";
    private static final String LOC_DEENROLL_WITHOUT_ENROLL = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_WITHOUT_ENROLLMENT";
    private static final String LOC_DEENROLL_BEFORE_ENROLL = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_BEFORE_ENROLLMENT";
    private static final String LOC_REGISTER_ID_EMPTY = "HCM_ATTENDANCE_ATTENDEE_REGISTER_ID_EMPTY";
    private static final String LOC_REGISTER_ID_MISMATCH = "HCM_ATTENDANCE_ATTENDEE_REGISTER_ID_MISMATCH";

    // Default error messages (used when localization not available)
    private static final String DEFAULT_INVALID_DATE = "Invalid date format. Use dd-MM-yyyy or dd/MM/yyyy";
    private static final String DEFAULT_DATE_OUT_OF_RANGE = "Date must be between register start and end dates";
    private static final String DEFAULT_DEENROLL_WITHOUT_ENROLL = "De-enrollment date requires enrollment date";
    private static final String DEFAULT_DEENROLL_BEFORE_ENROLL = "De-enrollment date cannot be before enrollment date";
    private static final String DEFAULT_REGISTER_ID_EMPTY = "Register ID is required";
    private static final String DEFAULT_REGISTER_ID_MISMATCH = "Register ID does not match expected register";

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

            // Fetch attendance register to get start/end dates
            RegisterDateRange dateRange = fetchRegisterDateRange(expectedRegisterId,
                    resource.getTenantId(), requestInfo);

            // Single-pass O(n) validation
            List<ValidationError> errors = new ArrayList<>();
            validateAttendeeData(sheetData, expectedRegisterId, dateRange, errors, localizationMap);

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
     * Validate attendee data - O(n) single pass
     */
    private void validateAttendeeData(List<Map<String, Object>> sheetData,
                                       String expectedRegisterId,
                                       RegisterDateRange dateRange,
                                       List<ValidationError> errors,
                                       Map<String, String> localizationMap) {

        log.info("Validating {} attendee rows", sheetData.size());

        int rowNumber = 2; // Excel row 1 is header, data starts at row 2
        for (Map<String, Object> row : sheetData) {
            String registerId = getVal(row, COL_REGISTER_ID);
            String enrollmentStr = getVal(row, COL_ENROLLMENT_DATE);
            String deEnrollmentStr = getVal(row, COL_DEENROLLMENT_DATE);
            String userName = getVal(row, COL_USERNAME);

            // Skip fully empty rows (no user data and no dates)
            if (userName.isEmpty() && registerId.isEmpty()
                    && enrollmentStr.isEmpty() && deEnrollmentStr.isEmpty()) {
                rowNumber++;
                continue;
            }

            List<String> rowErrors = new ArrayList<>();

            // 1. Register ID must not be empty
            if (registerId.isEmpty()) {
                rowErrors.add(getLocalizedMessage(localizationMap, LOC_REGISTER_ID_EMPTY,
                        DEFAULT_REGISTER_ID_EMPTY));
            } else {
                // 2. Register ID must match expected UUID or service code
                // Template writes serviceCode; UUID is the fallback for backward compatibility
                String expectedServiceCode = (dateRange != null && !dateRange.serviceCode.isEmpty())
                        ? dateRange.serviceCode : null;
                boolean matchesUuid = registerId.equals(expectedRegisterId);
                boolean matchesServiceCode = expectedServiceCode != null && registerId.equals(expectedServiceCode);
                if (!matchesUuid && !matchesServiceCode) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_REGISTER_ID_MISMATCH,
                            DEFAULT_REGISTER_ID_MISMATCH));
                }
            }

            // 3. Validate enrollment date
            LocalDate enrollmentDate = null;
            if (!enrollmentStr.isEmpty()) {
                enrollmentDate = parseDate(enrollmentStr);
                if (enrollmentDate == null) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_INVALID_DATE,
                            DEFAULT_INVALID_DATE));
                } else if (dateRange != null && !isDateInRange(enrollmentDate, dateRange)) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_DATE_OUT_OF_RANGE,
                            DEFAULT_DATE_OUT_OF_RANGE));
                }
            }

            // 4. Validate de-enrollment date
            LocalDate deEnrollmentDate = null;
            if (!deEnrollmentStr.isEmpty()) {
                deEnrollmentDate = parseDate(deEnrollmentStr);
                if (deEnrollmentDate == null) {
                    rowErrors.add(getLocalizedMessage(localizationMap, LOC_INVALID_DATE,
                            DEFAULT_INVALID_DATE));
                } else {
                    // Date range check
                    if (dateRange != null && !isDateInRange(deEnrollmentDate, dateRange)) {
                        rowErrors.add(getLocalizedMessage(localizationMap, LOC_DATE_OUT_OF_RANGE,
                                DEFAULT_DATE_OUT_OF_RANGE));
                    }

                    // 5. De-enrollment requires enrollment
                    if (enrollmentStr.isEmpty()) {
                        rowErrors.add(getLocalizedMessage(localizationMap, LOC_DEENROLL_WITHOUT_ENROLL,
                                DEFAULT_DEENROLL_WITHOUT_ENROLL));
                    }

                    // 6. De-enrollment >= enrollment
                    if (enrollmentDate != null && deEnrollmentDate.isBefore(enrollmentDate)) {
                        rowErrors.add(getLocalizedMessage(localizationMap, LOC_DEENROLL_BEFORE_ENROLL,
                                DEFAULT_DEENROLL_BEFORE_ENROLL));
                    }
                }
            }

            if (!rowErrors.isEmpty()) {
                errors.add(ValidationError.builder()
                        .rowNumber(rowNumber)
                        .errorDetails(String.join("; ", rowErrors))
                        .status(ValidationConstants.STATUS_INVALID)
                        .build());
            }

            rowNumber++;
        }

        log.info("Attendee validation completed. Total errors: {}", errors.size());
    }

    /**
     * Parse date string in dd-MM-yyyy or dd/MM/yyyy format
     */
    private LocalDate parseDate(String dateStr) {
        String trimmed = dateStr.trim();
        try {
            return LocalDate.parse(trimmed, FORMAT_DASH);
        } catch (DateTimeParseException ignored) {
            // Try next format
        }
        try {
            return LocalDate.parse(trimmed, FORMAT_SLASH);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Check if date falls within register's start/end range (inclusive)
     */
    private boolean isDateInRange(LocalDate date, RegisterDateRange range) {
        return !date.isBefore(range.startDate) && !date.isAfter(range.endDate);
    }

    /**
     * Convert epoch millis to LocalDate
     */
    private LocalDate epochMillisToLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("UTC")).toLocalDate();
    }

    /**
     * Extract registerId: use referenceId when referenceType is attendanceRegister,
     * otherwise fall back to additionalDetails.registerId for backward compatibility.
     */
    private String extractRegisterId(ProcessResource resource) {
        if ("attendanceRegister".equals(resource.getReferenceType())) {
            String registerId = resource.getReferenceId();
            if (registerId != null && !registerId.isBlank()) {
                return registerId.trim();
            }
        }

        // Fallback to additionalDetails.registerId
        Map<String, Object> additionalDetails = resource.getAdditionalDetails();
        if (additionalDetails != null && additionalDetails.get("registerId") != null) {
            return String.valueOf(additionalDetails.get("registerId")).trim();
        }
        return "";
    }

    /**
     * Fetch attendance register and extract start/end dates
     */
    private RegisterDateRange fetchRegisterDateRange(String registerId, String tenantId,
                                                      RequestInfo requestInfo) {
        if (registerId.isEmpty()) {
            log.warn("No registerId provided, skipping date range validation");
            return null;
        }

        try {
            StringBuilder url = new StringBuilder(config.getAttendanceRegisterSearchUrl());
            url.append("?tenantId=").append(tenantId)
               .append("&ids=").append(registerId);

            Map<String, Object> payload = new HashMap<>();
            payload.put("RequestInfo", requestInfo);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) serviceRequestRepository.fetchResult(url, payload);

            if (response == null || response.get("attendanceRegister") == null) {
                log.warn("Attendance register not found: {}", registerId);
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> registers =
                    (List<Map<String, Object>>) response.get("attendanceRegister");

            if (registers.isEmpty()) {
                log.warn("Attendance register not found: {}", registerId);
                return null;
            }

            Map<String, Object> register = registers.get(0);
            Object startDateObj = register.get("startDate");
            Object endDateObj = register.get("endDate");

            if (startDateObj == null || endDateObj == null) {
                log.warn("Register {} missing start/end dates, skipping date range validation", registerId);
                return null;
            }

            long startEpoch = ((Number) startDateObj).longValue();
            long endEpoch = ((Number) endDateObj).longValue();

            String serviceCode = register.get("serviceCode") != null
                    ? String.valueOf(register.get("serviceCode")).trim() : "";

            RegisterDateRange range = new RegisterDateRange(
                    epochMillisToLocalDate(startEpoch),
                    epochMillisToLocalDate(endEpoch),
                    serviceCode);

            log.info("Register {} date range: {} to {}, serviceCode: {}", registerId, range.startDate, range.endDate, range.serviceCode);
            return range;

        } catch (Exception e) {
            log.error("Error fetching register date range for {}: {}", registerId, e.getMessage());
            return null; // Continue validation without date range checks
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
            return new ValidationColumnInfo(errorColumnIndex, statusColumnIndex);
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
        return ExcelUtil.getValueAsString(row.get(key)).trim();
    }

    private String getLocalizedMessage(Map<String, String> localizationMap, String key, String defaultMsg) {
        if (localizationMap != null) {
            return localizationMap.getOrDefault(key, defaultMsg);
        }
        return defaultMsg;
    }

    /**
     * Simple record to hold register start/end dates and service code
     */
    private static class RegisterDateRange {
        final LocalDate startDate;
        final LocalDate endDate;
        final String serviceCode;

        RegisterDateRange(LocalDate startDate, LocalDate endDate, String serviceCode) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.serviceCode = serviceCode;
        }
    }
}
