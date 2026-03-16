package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for attendance register sheet
 * Validations:
 * 1. At least one boundary column filled per row
 * 2. All filled boundary codes exist in system
 * 3. Register ID not empty
 * 4. No duplicate Register IDs
 */
@Component
@Slf4j
public class AttendanceRegisterValidationProcessor implements IWorkbookProcessor {

    private final ValidationService validationService;
    private final BoundaryService boundaryService;
    private final CampaignService campaignService;
    private final EnrichmentUtil enrichmentUtil;
    private final ExcelUtil excelUtil;
    private final CustomExceptionHandler exceptionHandler;
    private final BoundaryUtil boundaryUtil;

    public AttendanceRegisterValidationProcessor(ValidationService validationService,
                                               BoundaryService boundaryService,
                                               CampaignService campaignService,
                                               EnrichmentUtil enrichmentUtil,
                                               ExcelUtil excelUtil,
                                               CustomExceptionHandler exceptionHandler,
                                               BoundaryUtil boundaryUtil) {
        this.validationService = validationService;
        this.boundaryService = boundaryService;
        this.campaignService = campaignService;
        this.enrichmentUtil = enrichmentUtil;
        this.excelUtil = excelUtil;
        this.exceptionHandler = exceptionHandler;
        this.boundaryUtil = boundaryUtil;
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

            log.info("Starting attendance register validation for sheet: {}", sheetName);

            // Convert sheet data to map list - CACHED VERSION
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);

            List<ValidationError> errors = new ArrayList<>();

            // Validate sheet data - O(n) single pass
            validateAttendanceRegisterData(sheetData, resource, requestInfo, errors, localizationMap);

            log.info("Attendance register validation completed with {} errors", errors.size());

            // Only add error columns if there are validation errors
            if (!errors.isEmpty()) {
                log.info("Found {} validation errors, adding error columns", errors.size());

                // Check if error columns already exist
                ValidationColumnInfo columnInfo = checkAndAddErrorColumns(sheet, localizationMap);

                // Process validation errors
                processValidationErrors(sheet, errors, columnInfo, localizationMap);
            } else {
                log.info("No validation errors found");
            }

            // Enrich resource additional details with error information
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);

            return workbook;

        } catch (Exception e) {
            log.error("Error processing attendance register validation sheet: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.USER_VALIDATION_FAILED,
                ErrorConstants.USER_VALIDATION_FAILED_MESSAGE + ": " + e.getMessage(), e);
            return workbook;
        }
    }

    /**
     * Validate attendance register data - O(n) time complexity
     * Validations:
     * 1. At least one boundary filled
     * 2. All boundary codes valid
     * 3. Register ID not empty
     * 4. No duplicate Register IDs
     */
    private void validateAttendanceRegisterData(List<Map<String, Object>> sheetData,
                                               ProcessResource resource,
                                               RequestInfo requestInfo,
                                               List<ValidationError> errors,
                                               Map<String, String> localizationMap) {

        log.info("Validating {} attendance register rows", sheetData.size());

        // Fetch valid boundary codes once - O(1) lookup
        Set<String> validBoundaryCodes = boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
                resource.getId(), resource.getReferenceId(), resource.getTenantId(),
                resource.getHierarchyType(), requestInfo);
        log.info("Loaded {} valid boundary codes for validation", validBoundaryCodes.size());

        // Get boundary columns from sheet headers
        Set<String> boundaryColumnNames = extractBoundaryColumnNames(sheetData);
        log.info("Found {} boundary columns in sheet", boundaryColumnNames.size());

        // Track Register IDs for duplicate detection - O(1) lookup
        Set<String> seenRegisterIds = new HashSet<>();

        // Single pass validation - O(n)
        int rowNumber = 2; // Excel row numbers start at 1, header is row 1
        for (Map<String, Object> row : sheetData) {
            // Skip fully empty rows (no boundary, no register ID)
            if (!hasAnyDataInRow(row, boundaryColumnNames)) {
                rowNumber++;
                continue;
            }

            List<String> rowErrors = new ArrayList<>();

            boolean hasBoundary = hasBoundaryFilled(row, boundaryColumnNames);
            String registerId = ExcelUtil.getValueAsString(row.get("HCM_ATTENDANCE_REGISTER_ID")).trim();
            boolean hasRegisterId = !registerId.isEmpty();
            String boundaryCode = ExcelUtil.getValueAsString(row.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")).trim();

            // Boundary filled but no register ID
            if (hasBoundary && !hasRegisterId) {
                rowErrors.add(localizationMap.getOrDefault(
                        "HCM_ATTENDANCE_REGISTER_VALIDATION_EMPTY_ID",
                        "Register ID is required when boundary is selected"));
            }

            // Register ID filled but no boundary
            if (!hasBoundary && hasRegisterId) {
                rowErrors.add(localizationMap.getOrDefault(
                        "HCM_ATTENDANCE_REGISTER_VALIDATION_NO_BOUNDARY",
                        "At least one boundary must be selected for the register"));
            }

            // Validate boundary code against campaign boundaries (same as User/Facility pattern)
            if (!boundaryCode.isEmpty() && !validBoundaryCodes.contains(boundaryCode)) {
                rowErrors.add(localizationMap.getOrDefault(
                        "HCM_ATTENDANCE_REGISTER_VALIDATION_INVALID_BOUNDARY",
                        "Invalid boundary code: " + boundaryCode));
            }

            // Duplicate check (only if register ID exists)
            if (hasRegisterId && !seenRegisterIds.add(registerId)) {
                rowErrors.add(localizationMap.getOrDefault(
                        "HCM_ATTENDANCE_REGISTER_VALIDATION_DUPLICATE_ID",
                        "Duplicate Register ID found"));
            }


            // Add errors for this row
            if (!rowErrors.isEmpty()) {
                String errorDetails = String.join("; ", rowErrors);
                errors.add(ValidationError.builder()
                        .rowNumber(rowNumber)
                        .errorDetails(errorDetails)
                        .status(ValidationConstants.STATUS_INVALID)
                        .build());
            }

            rowNumber++;
        }

        log.info("Validation completed. Total errors: {}", errors.size());
    }

    /**
     * Extract boundary column names from sheet data headers
     * Boundary columns follow pattern: {HIERARCHY_TYPE}_{BOUNDARY_TYPE}
     */
    private Set<String> extractBoundaryColumnNames(List<Map<String, Object>> sheetData) {
        Set<String> boundaryColumns = new HashSet<>();
        if (sheetData.isEmpty()) {
            return boundaryColumns;
        }

        Map<String, Object> firstRow = sheetData.get(0);
        for (String columnName : firstRow.keySet()) {
            // Boundary columns have underscore pattern, exclude special columns
            if (columnName != null && !columnName.startsWith("HCM_") &&
                    !columnName.startsWith("#") && !columnName.startsWith("__")
                    && !columnName.equals("uniqueKey") && !columnName.endsWith("_HELPER")) {
                boundaryColumns.add(columnName);
            }
        }
        return boundaryColumns;
    }

    /**
     * Check if at least one boundary column is filled in the row
     */
    private boolean hasBoundaryFilled(Map<String, Object> row, Set<String> boundaryColumnNames) {
        for (String columnName : boundaryColumnNames) {
            Object value = row.get(columnName);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyDataInRow(Map<String, Object> row, Set<String> boundaryColumnNames) {
        // Check boundary columns
        for (String col : boundaryColumnNames) {
            Object val = row.get(col);
            if (val != null && !String.valueOf(val).trim().isEmpty()) return true;
        }
        // Check Register ID
        Object registerId = row.get("HCM_ATTENDANCE_REGISTER_ID");
        if (registerId != null && !String.valueOf(registerId).trim().isEmpty()) return true;
        return false;
    }

    /**
     * Check if error columns exist and add them if needed
     */
    private ValidationColumnInfo checkAndAddErrorColumns(Sheet sheet, Map<String, String> localizationMap) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return validationService.addValidationColumns(sheet, localizationMap);
        }

        boolean hasErrorColumn = false;
        boolean hasStatusColumn = false;
        int errorColumnIndex = -1;
        int statusColumnIndex = -1;

        for (Cell cell : headerRow) {
            String headerValue = ExcelUtil.getCellValueAsString(cell);
            if (ValidationConstants.ERROR_DETAILS_COLUMN_NAME.equals(headerValue)) {
                hasErrorColumn = true;
                errorColumnIndex = cell.getColumnIndex();
            } else if (ValidationConstants.STATUS_COLUMN_NAME.equals(headerValue)) {
                hasStatusColumn = true;
                statusColumnIndex = cell.getColumnIndex();
            }
        }

        if (hasErrorColumn && hasStatusColumn) {
            return new ValidationColumnInfo(errorColumnIndex, statusColumnIndex);
        }

        return validationService.addValidationColumns(sheet, localizationMap);
    }

    /**
     * Process validation errors and add them to the sheet
     */
    private void processValidationErrors(Sheet sheet, List<ValidationError> errors,
                                        ValidationColumnInfo columnInfo,
                                        Map<String, String> localizationMap) {
        int statusColumnIndex = columnInfo.getStatusColumnIndex();
        int errorColumnIndex = columnInfo.getErrorColumnIndex();

        for (ValidationError error : errors) {
            int excelRowNumber = error.getRowNumber();
            Row row = sheet.getRow(excelRowNumber - 1); // Convert to 0-based index

            if (row != null) {
                // Set status to invalid
                Cell statusCell = row.getCell(statusColumnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                statusCell.setCellValue(ValidationConstants.STATUS_INVALID);

                // Set error details
                Cell errorCell = row.getCell(errorColumnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                errorCell.setCellValue(error.getErrorDetails());

                log.debug("Added validation errors to row {}: {}", excelRowNumber, error.getErrorDetails());
            }
        }
    }
}
