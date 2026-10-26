package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.SchemaValidationService;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ValidationColumnInfo;
import org.egov.excelingestion.web.models.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostValidationProcessor, driven through processWorkbook on a real workbook: Remove is a row error only
 * when the hidden clientReferenceId is blank (no facility lookup at upload), boundary resolved / in
 * campaign / lowest-or-second-lowest level, error cells, postSheetStatus. Required/enum columns are
 * checked by the MDMS-schema validation, not here - this processor never reads that master.
 */
@ExtendWith(MockitoExtension.class)
class PostValidationProcessorTest {

    private static final String SHEET = "LocalizedPostsSheet";
    private static final String TENANT = "mz";
    private static final int STATUS_COL = 1;
    private static final int ERROR_COL = 20;
    private static final int STATUS_VALIDATION_COL = 21;

    @Mock private ValidationService validationService;
    @Mock private EnrichmentUtil enrichmentUtil;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private ExcelUtil excelUtil;
    @Mock private CustomExceptionHandler exceptionHandler;

    private PostValidationProcessor processor;
    private XSSFWorkbook workbook;
    private Sheet sheet;
    private ProcessResource resource;

    @BeforeEach
    void setUp() {
        processor = new PostValidationProcessor(validationService, enrichmentUtil, boundaryUtil, excelUtil,
                exceptionHandler);
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet(SHEET);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(ProcessingConstants.POST_NAME_COLUMN_KEY);
        header.createCell(STATUS_COL).setCellValue(ProcessingConstants.POST_STATUS_COLUMN_KEY);
        sheet.createRow(1).createCell(0).setCellValue("Post name");
        resource = ProcessResource.builder().id("proc-1").tenantId(TENANT).referenceId("campaign-1")
                .hierarchyType("ADMIN").fileStoreId("fs-1").type("unified-console-validation").build();

        ValidationColumnInfo columnInfo = new ValidationColumnInfo();
        columnInfo.setErrorColumnIndex(ERROR_COL);
        columnInfo.setStatusColumnIndex(STATUS_VALIDATION_COL);
        lenient().when(validationService.addValidationColumns(any(), any())).thenReturn(columnInfo);
        // In-campaign codes at any level, and the lowest-or-second-lowest subset (Village + District).
        lenient().when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
                .thenReturn(Set.of("P1", "D1", "V1", "V2"));
        lenient().when(boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(any(), any(), any(), any(), eq(2), any()))
                .thenReturn(Set.of("V1", "V2", "D1"));
    }

    @Test
    void validRowsGetNoErrorsEvenWhenEveryPostIsExcluded() {
        List<Map<String, Object>> rows = List.of(
                validRow(3, "Exclude", "V1"),
                validRow(4, "Exclude", "V2"));

        List<ValidationError> errors = run(rows);

        assertTrue(errors.isEmpty(), "No 'at least one Include' rule for posts, got " + errors);
        verify(validationService, never()).addValidationColumns(any(), anyMap());
    }

    @Test
    void enumValuesAreLeftToTheSchemaValidation() {
        // No list of types, categories, statuses or usages in the processor: values it does not know pass here
        Map<String, Object> row = validRow(3, "Include", "V1");
        row.put(ProcessingConstants.POST_TYPE_COLUMN_KEY, "MOBILE_POST");
        row.put(ProcessingConstants.POST_CATEGORY_COLUMN_KEY, "MOSQUE");
        row.put(ProcessingConstants.POST_STATUS_COLUMN_KEY, "Seasonal");

        assertTrue(run(List.of(row)).isEmpty());
    }

    @Test
    void removeWithBlankClientReferenceIdIsARowError() {
        Map<String, Object> row = validRow(3, "Remove", "V1"); // no clientReferenceId: a row the PM typed/pasted

        List<ValidationError> errors = run(List.of(row));

        assertEquals(1, errors.size(), errors.toString());
        assertEquals(3, errors.get(0).getRowNumber());
        assertEquals(ProcessingConstants.POST_USAGE_COLUMN_KEY, errors.get(0).getColumnName());
        assertEquals(ValidationConstants.DEFAULT_POST_REMOVE_ONLY_EXISTING, errors.get(0).getErrorDetails());
        assertEquals(ValidationConstants.STATUS_INVALID, statusCell(3));
    }

    @Test
    void removeWithAPreFilledClientReferenceIdHasNoRemoveError_noFacilityLookup() {
        Map<String, Object> row = validRow(3, "Remove", "V1");
        row.put(ProcessingConstants.POST_CLIENT_REFERENCE_ID_COLUMN_KEY, "fac-clf-1");

        assertTrue(run(List.of(row)).isEmpty(), "a pre-filled clientReferenceId needs no facility lookup");
    }

    @Test
    void removeOnANewRowUsesTheLocalizedMessage() {
        Map<String, String> localization = new HashMap<>();
        localization.put(ValidationConstants.HCM_POST_REMOVE_ONLY_EXISTING, "Retirer : poste existant uniquement");

        List<ValidationError> errors = run(List.of(validRow(3, "Remove", "V1")), localization);

        assertEquals("Retirer : poste existant uniquement", errors.get(0).getErrorDetails());
    }

    @Test
    void boundaryMustResolveBeInCampaignAndBeLowestOrSecondLowestLevel() {
        Map<String, Object> notResolved = validRow(3, "Include", null);
        notResolved.put("ADMIN_VILLAGE", "Pasted name"); // selection present, code blank
        Map<String, Object> noBoundary = validRow(4, "Include", null);
        Map<String, Object> outsideCampaign = validRow(5, "Include", "X9");
        Map<String, Object> tooHigh = validRow(6, "Include", "P1"); // root level - higher than second-lowest
        Map<String, Object> lowest = validRow(7, "Include", "V2");
        Map<String, Object> secondLowest = validRow(8, "Include", "D1");

        List<ValidationError> errors = run(List.of(notResolved, noBoundary, outsideCampaign, tooHigh, lowest, secondLowest));

        assertEquals(4, errors.size(), errors.toString());
        assertEquals(3, errors.get(0).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_BOUNDARY_INVALID_SELECTION, errors.get(0).getErrorDetails());
        assertEquals(4, errors.get(1).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_POST_BOUNDARY_REQUIRED, errors.get(1).getErrorDetails());
        assertEquals(5, errors.get(2).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_BOUNDARY_CODE_NOT_IN_CAMPAIGN, errors.get(2).getErrorDetails());
        assertEquals(6, errors.get(3).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_POST_BOUNDARY_INVALID_LEVEL, errors.get(3).getErrorDetails());
        assertNull(sheet.getRow(6).getCell(ERROR_COL), "Lowest-level row must not get an error cell");
        assertNull(sheet.getRow(7).getCell(ERROR_COL), "Second-lowest-level row must not get an error cell");
    }

    @Test
    void errorsAreAppendedToTheRowErrorCell() {
        Map<String, Object> row = validRow(3, "Include", "P1"); // in campaign, but above second-lowest level
        // The MDMS-schema step already wrote its own message into this row
        Row excelRow = sheet.createRow(2);
        excelRow.createCell(ERROR_COL).setCellValue("Required field 'HCM_ADMIN_CONSOLE_POST_CATEGORY' is missing");
        excelRow.createCell(STATUS_VALIDATION_COL).setCellValue(ValidationConstants.STATUS_INVALID);
        Row header = sheet.getRow(0);
        header.createCell(ERROR_COL).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
        header.createCell(STATUS_VALIDATION_COL).setCellValue(ValidationConstants.STATUS_COLUMN_NAME);

        run(List.of(row));

        // addValidationColumns itself reuses the existing error/status columns (see setUp's stub), so the
        // schema step's message and this processor's message land in the same cell, appended.
        assertEquals("Required field 'HCM_ADMIN_CONSOLE_POST_CATEGORY' is missing; "
                        + ValidationConstants.DEFAULT_POST_BOUNDARY_INVALID_LEVEL,
                sheet.getRow(2).getCell(ERROR_COL).getStringCellValue());
    }

    @Test
    void boundaryLookupFailureMarksRowsAsSystemError() {
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boundary service down"));

        List<ValidationError> errors = run(List.of(validRow(3, "Include", "V1")));

        assertEquals(1, errors.size());
        assertEquals(ValidationConstants.STATUS_ERROR, errors.get(0).getStatus());
        assertEquals(ValidationConstants.STATUS_ERROR, statusCell(3));
    }

    @Test
    void aCategoryAddedToTheSchemaIsAcceptedWithoutCodeChange() {
        // The schema step validates enum values against the MDMS schema: a new category is accepted as soon
        // as the schema lists it, and a value outside the schema is still rejected.
        SchemaValidationService schemaValidation = new SchemaValidationService(org.mockito.Mockito.mock(MDMSService.class));
        Map<String, Object> schema = Map.of("enumProperties", List.of(
                enumProp(ProcessingConstants.POST_TYPE_COLUMN_KEY, List.of("CLF", "TRANSIT_POST")),
                enumProp(ProcessingConstants.POST_CATEGORY_COLUMN_KEY, List.of("SCHOOL", "MARKET", "MOSQUE")),
                enumProp(ProcessingConstants.POST_USAGE_COLUMN_KEY, List.of("Include", "Exclude", "Remove"))));
        Map<String, Object> newCategory = validRow(3, "Remove", "V1");
        newCategory.put(ProcessingConstants.POST_CATEGORY_COLUMN_KEY, "MOSQUE");
        Map<String, Object> unknownCategory = validRow(4, "Include", "V1");
        unknownCategory.put(ProcessingConstants.POST_CATEGORY_COLUMN_KEY, "STADIUM");

        List<ValidationError> schemaErrors = schemaValidation.validateDataWithPreFetchedSchema(
                List.of(newCategory, unknownCategory), SHEET, schema, new HashMap<>());

        assertEquals(1, schemaErrors.size(), schemaErrors.toString());
        assertEquals(4, schemaErrors.get(0).getRowNumber());
        assertEquals(ProcessingConstants.POST_CATEGORY_COLUMN_KEY, schemaErrors.get(0).getColumnName());

        // ...and the posts processor adds nothing for the new category either (pre-filled id, no lookup)
        newCategory.put(ProcessingConstants.POST_CLIENT_REFERENCE_ID_COLUMN_KEY, "fac-1");
        assertTrue(run(List.of(newCategory)).isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> enumProp(String name, List<String> values) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("name", name);
        prop.put("enum", values);
        prop.put("isRequired", true);
        return prop;
    }

    private List<ValidationError> run(List<Map<String, Object>> rows) {
        return run(rows, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private List<ValidationError> run(List<Map<String, Object>> rows, Map<String, String> localization) {
        for (Map<String, Object> row : rows) {
            int rowNumber = (Integer) row.get(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY);
            if (sheet.getRow(rowNumber - 1) == null) {
                sheet.createRow(rowNumber - 1).createCell(0).setCellValue("x");
            }
        }
        when(excelUtil.convertSheetToMapListCached(eq("fs-1"), eq(SHEET), eq(sheet))).thenReturn(rows);

        processor.processWorkbook(workbook, SHEET, resource, new RequestInfo(), localization);

        ArgumentCaptor<List<ValidationError>> captor = ArgumentCaptor.forClass(List.class);
        verify(enrichmentUtil).enrichErrorAndStatusInAdditionalDetails(eq(resource), captor.capture(),
                eq(ValidationConstants.SHEET_KIND_POST));
        return new ArrayList<>(captor.getValue());
    }

    private String statusCell(int rowNumber) {
        return sheet.getRow(rowNumber - 1).getCell(STATUS_VALIDATION_COL).getStringCellValue();
    }

    private static Map<String, Object> validRow(int rowNumber, String usage, String boundaryCode) {
        Map<String, Object> row = new HashMap<>();
        row.put(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY, rowNumber);
        row.put(ProcessingConstants.POST_TYPE_COLUMN_KEY, "CLF");
        row.put(ProcessingConstants.POST_CATEGORY_COLUMN_KEY, "SCHOOL");
        row.put(ProcessingConstants.POST_NAME_COLUMN_KEY, "Post " + rowNumber);
        row.put(ProcessingConstants.POST_STATUS_COLUMN_KEY, "Permanent");
        row.put(ProcessingConstants.POST_USAGE_COLUMN_KEY, usage);
        if (boundaryCode != null) {
            row.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, boundaryCode);
        }
        return row;
    }
}
