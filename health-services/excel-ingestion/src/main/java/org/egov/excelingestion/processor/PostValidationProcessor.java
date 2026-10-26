package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the posts sheet (CLF / transit posts), reusing {@link FacilityValidationProcessor}'s error
 * columns and row-status handling. Required/enum columns are already checked by the MDMS-schema
 * validation; this only adds post-specific checks: Remove only on an existing post, and boundary at the
 * lowest or second lowest hierarchy level. Errors set {@code postSheetStatus = invalid} on the row.
 */
@Component
@Slf4j
public class PostValidationProcessor implements IWorkbookProcessor {

    private final ValidationService validationService;
    private final EnrichmentUtil enrichmentUtil;
    private final BoundaryUtil boundaryUtil;
    private final ExcelUtil excelUtil;
    private final CustomExceptionHandler exceptionHandler;

    public PostValidationProcessor(ValidationService validationService, EnrichmentUtil enrichmentUtil,
                                   BoundaryUtil boundaryUtil, ExcelUtil excelUtil,
                                   CustomExceptionHandler exceptionHandler) {
        this.validationService = validationService;
        this.enrichmentUtil = enrichmentUtil;
        this.boundaryUtil = boundaryUtil;
        this.excelUtil = excelUtil;
        this.exceptionHandler = exceptionHandler;
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

            log.info("Starting post validation for sheet: {}", sheetName);
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);

            List<ValidationError> errors = new ArrayList<>();
            validateRemoveOnlyOnExistingPosts(sheetData, errors, localizationMap);
            validateBoundaries(sheetData, resource, requestInfo, errors, localizationMap);
            log.info("Post validation completed with {} errors", errors.size());
            enrichmentUtil.logValidationErrors(resource.getReferenceId(), sheetName, errors);

            if (!errors.isEmpty()) {
                ValidationColumnInfo columnInfo = validationService.addValidationColumns(sheet, localizationMap);
                FacilityValidationProcessor.processValidationErrors(sheet, errors, columnInfo, localizationMap);
            }
            // Per-sheet status like the facility sheet: postSheetStatus = invalid when this sheet has row errors
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_POST);
            return workbook;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing post validation sheet: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.POST_VALIDATION_FAILED,
                    ErrorConstants.POST_VALIDATION_FAILED_MESSAGE + ": " + e.getMessage(), e);
            return workbook; // never reached
        }
    }

    /** Remove is a row error unless the row already carries an existing post's clientReferenceId (no facility lookup). */
    private void validateRemoveOnlyOnExistingPosts(List<Map<String, Object>> sheetData, List<ValidationError> errors,
                                                   Map<String, String> localizationMap) {
        for (Map<String, Object> rowData : sheetData) {
            String usage = trimToNull(ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.POST_USAGE_COLUMN_KEY)));
            if (!ProcessingConstants.POST_USAGE_REMOVE.equals(usage)) {
                continue;
            }
            String clientReferenceId = trimToNull(ExcelUtil.getValueAsString(
                    rowData.get(ProcessingConstants.POST_CLIENT_REFERENCE_ID_COLUMN_KEY)));
            if (clientReferenceId == null) {
                errors.add(error(rowData, ProcessingConstants.POST_USAGE_COLUMN_KEY, localizationMap,
                        ValidationConstants.HCM_POST_REMOVE_ONLY_EXISTING, ValidationConstants.DEFAULT_POST_REMOVE_ONLY_EXISTING));
            }
        }
    }

    /**
     * Every post needs a boundary: the selection must resolve to a code, the code must be part of the
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
            log.error("Error during post boundary validation: {}", e.getMessage(), e);
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
                        : error(rowData, null, localizationMap, ValidationConstants.HCM_POST_BOUNDARY_REQUIRED,
                                ValidationConstants.DEFAULT_POST_BOUNDARY_REQUIRED));
            } else if (campaignBoundaryCodes == null || !campaignBoundaryCodes.contains(boundaryCode)) {
                errors.add(error(rowData, null, localizationMap, ValidationConstants.HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN,
                        ValidationConstants.DEFAULT_BOUNDARY_CODE_NOT_IN_CAMPAIGN));
            } else if (lowestTwoLevelCodes == null || !lowestTwoLevelCodes.contains(boundaryCode)) {
                errors.add(error(rowData, null, localizationMap, ValidationConstants.HCM_POST_BOUNDARY_INVALID_LEVEL,
                        ValidationConstants.DEFAULT_POST_BOUNDARY_INVALID_LEVEL));
            }
        }
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
