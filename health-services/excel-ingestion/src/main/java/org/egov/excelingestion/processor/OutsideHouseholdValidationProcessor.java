package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.OutsideHouseholdTypeMasterService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates the Outside Household sheet (Outside Household Places / Transit Posts), reusing {@link FacilityValidationProcessor}'s error
 * columns and row-status handling. Required/enum columns are already checked by the MDMS-schema
 * validation; this only adds site-specific checks: the category must belong to the row's site type
 * (site-types master), and boundary at the lowest or second lowest hierarchy level. Errors set {@code outsideHouseholdSheetStatus = invalid} on the row.
 */
@Component
@Slf4j
public class OutsideHouseholdValidationProcessor implements IWorkbookProcessor {

    private final ValidationService validationService;
    private final EnrichmentUtil enrichmentUtil;
    private final BoundaryUtil boundaryUtil;
    private final ExcelUtil excelUtil;
    private final CustomExceptionHandler exceptionHandler;
    private final OutsideHouseholdTypeMasterService outsideHouseholdTypeMasterService;

    public OutsideHouseholdValidationProcessor(ValidationService validationService, EnrichmentUtil enrichmentUtil,
                                   BoundaryUtil boundaryUtil, ExcelUtil excelUtil,
                                   CustomExceptionHandler exceptionHandler,
                                   OutsideHouseholdTypeMasterService outsideHouseholdTypeMasterService) {
        this.validationService = validationService;
        this.enrichmentUtil = enrichmentUtil;
        this.boundaryUtil = boundaryUtil;
        this.excelUtil = excelUtil;
        this.exceptionHandler = exceptionHandler;
        this.outsideHouseholdTypeMasterService = outsideHouseholdTypeMasterService;
    }

    @Override
    public Workbook processWorkbook(Workbook workbook, String sheetName, ProcessResource resource,
                                    RequestInfo requestInfo, Map<String, String> localizationMap) {
        try {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                log.warn("Sheet {} not found in workbook", sheetName);
                return workbook;
            }

            log.info("Starting site validation for sheet: {}", sheetName);
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);

            List<ValidationError> errors = new ArrayList<>();
            validateCategoryMatchesType(sheetData, outsideHouseholdTypeMasterService.getCategoriesByOutsideHouseholdType(
                    requestInfo, resource.getTenantId()), errors, localizationMap);
            validateBoundaries(sheetData, resource, requestInfo, errors, localizationMap);
            validateNoDuplicateSites(sheetData, errors, localizationMap);
            log.info("Site validation completed with {} errors", errors.size());
            enrichmentUtil.logValidationErrors(resource.getReferenceId(), sheetName, errors);

            if (!errors.isEmpty()) {
                ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, localizationMap);
                FacilityValidationProcessor.processValidationErrors(sheet, errors, columnInfo, localizationMap);
            }
            // Per-sheet status like the facility sheet: outsideHouseholdSheetStatus = invalid when this sheet has row errors
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_OUTSIDE_HOUSEHOLD);
            return workbook;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing site validation sheet: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.OUTSIDE_HOUSEHOLD_VALIDATION_FAILED,
                    ErrorConstants.OUTSIDE_HOUSEHOLD_VALIDATION_FAILED_MESSAGE + ": " + e.getMessage(), e);
            return workbook; // never reached
        }
    }

    /**
     * The category must be one of the categories the site-types master lists for the row's type. Rows with
     * a blank type/category, or a type with no categories list, are left to the schema checks.
     */
    private void validateCategoryMatchesType(List<Map<String, Object>> sheetData,
                                             Map<String, List<String>> categoriesByOutsideHouseholdType,
                                             List<ValidationError> errors, Map<String, String> localizationMap) {
        for (Map<String, Object> rowData : sheetData) {
            String outsideHouseholdType = OutsideHouseholdTypeMasterService.normalize(
                    ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY)));
            String category = OutsideHouseholdTypeMasterService.normalize(
                    ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY)));
            List<String> allowed = outsideHouseholdType == null ? null : categoriesByOutsideHouseholdType.get(outsideHouseholdType);
            if (category != null && allowed != null && !allowed.isEmpty() && !allowed.contains(category)) {
                errors.add(error(rowData, ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, localizationMap,
                        ValidationConstants.HCM_OUTSIDE_HOUSEHOLD_CATEGORY_NOT_FOR_TYPE, ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_CATEGORY_NOT_FOR_TYPE));
            }
        }
    }

    /**
     * Every site needs a boundary: the selection must resolve to a code, the code must be part of the
     * campaign, and it must be of the lowest or second lowest hierarchy level.
     */
    private void validateBoundaries(List<Map<String, Object>> sheetData, ProcessResource resource,
                                    RequestInfo requestInfo, List<ValidationError> errors,
                                    Map<String, String> localizationMap) {
        String hierarchyPrefix = resource.getHierarchyType() == null || resource.getHierarchyType().isEmpty()
                ? null : resource.getHierarchyType().toUpperCase() + "_";

        Set<String> campaignBoundaryCodes;
        Set<String> lowestTwoLevelCodes;
        try {
            campaignBoundaryCodes = boundaryUtil.getEnrichedBoundaryCodesFromCampaign(resource.getId(),
                    resource.getReferenceId(), resource.getTenantId(), resource.getHierarchyType(), requestInfo);
            lowestTwoLevelCodes = boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(resource.getId(),
                    resource.getReferenceId(), resource.getTenantId(), resource.getHierarchyType(), 2, requestInfo);
        } catch (Exception e) {
            log.error("Error during site boundary validation: {}", e.getMessage(), e);
            for (Map<String, Object> rowData : sheetData) {
                ValidationError error = error(rowData, null, localizationMap,
                        ValidationConstants.HCM_BOUNDARY_VALIDATION_FAILED, ValidationConstants.DEFAULT_BOUNDARY_VALIDATION_FAILED);
                error.setStatus(ValidationConstants.STATUS_ERROR);
                errors.add(error);
            }
            return;
        }

        for (Map<String, Object> rowData : sheetData) {
            String boundaryCode = trimToNull(ExcelUtil.getValueAsString(
                    rowData.get(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY)));
            if (boundaryCode == null) {
                boolean selected = hierarchyPrefix != null
                        && FacilityValidationProcessor.hasBoundarySelection(rowData, hierarchyPrefix);
                errors.add(selected
                        ? error(rowData, null, localizationMap, ValidationConstants.HCM_BOUNDARY_INVALID_SELECTION,
                                ValidationConstants.DEFAULT_BOUNDARY_INVALID_SELECTION)
                        : error(rowData, null, localizationMap, ValidationConstants.HCM_OUTSIDE_HOUSEHOLD_BOUNDARY_REQUIRED,
                                ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_BOUNDARY_REQUIRED));
            } else if (campaignBoundaryCodes == null || !campaignBoundaryCodes.contains(boundaryCode)) {
                errors.add(error(rowData, null, localizationMap, ValidationConstants.HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN,
                        ValidationConstants.DEFAULT_BOUNDARY_CODE_NOT_IN_CAMPAIGN));
            } else if (lowestTwoLevelCodes == null || !lowestTwoLevelCodes.contains(boundaryCode)) {
                errors.add(error(rowData, null, localizationMap, ValidationConstants.HCM_OUTSIDE_HOUSEHOLD_BOUNDARY_INVALID_LEVEL,
                        ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_BOUNDARY_INVALID_LEVEL));
            }
        }
    }

    /**
     * A site is its type + category + boundary + name (case and extra spaces ignored, as project-factory matches sites).
     * A row the planner adds (no site id yet) that repeats another row is an error, like a repeated name in the
     * Facilities List. Pre-filled rows are sites that already exist, so they are never errors themselves, even if
     * two of them share a name. Rows without a type, boundary or name are left to the other checks; a blank category
     * counts as its own value.
     */
    private void validateNoDuplicateSites(List<Map<String, Object>> sheetData, List<ValidationError> errors,
                                          Map<String, String> localizationMap) {
        Set<String> existingSites = new HashSet<>();
        for (Map<String, Object> rowData : sheetData) {
            String key = siteKey(rowData);
            if (key != null && isExistingSite(rowData)) {
                existingSites.add(key);
            }
        }
        Set<String> newSites = new HashSet<>();
        for (Map<String, Object> rowData : sheetData) {
            String key = siteKey(rowData);
            if (key == null || isExistingSite(rowData)) {
                continue;
            }
            if (existingSites.contains(key) || !newSites.add(key)) {
                errors.add(error(rowData, ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY, localizationMap,
                        ValidationConstants.HCM_OUTSIDE_HOUSEHOLD_DUPLICATE_SITE, ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_DUPLICATE_SITE));
            }
        }
    }

    private static boolean isExistingSite(Map<String, Object> rowData) {
        return trimToNull(ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_CLIENT_REFERENCE_ID_COLUMN_KEY))) != null;
    }

    /**
     * type|category|boundary|name with the name lower-cased and spaces collapsed; null when type, boundary or name is
     * missing. A blank category is kept as an empty part, so it only matches another blank category.
     */
    private static String siteKey(Map<String, Object> rowData) {
        String type = OutsideHouseholdTypeMasterService.normalize(
                ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY)));
        String category = OutsideHouseholdTypeMasterService.normalize(
                ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY)));
        String boundaryCode = trimToNull(ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY)));
        String name = trimToNull(ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY)));
        if (type == null || boundaryCode == null || name == null) {
            return null;
        }
        return type + "|" + (category == null ? "" : category) + "|" + boundaryCode + "|"
                + name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private ValidationError error(Map<String, Object> rowData, String column, Map<String, String> localizationMap,
                                  String key, String defaultMessage) {
        ValidationError error = new ValidationError();
        error.setRowNumber((Integer) rowData.get(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY));
        error.setColumnName(column);
        error.setStatus(ValidationConstants.STATUS_INVALID);
        error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, key, defaultMessage));
        return error;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
