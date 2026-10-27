package org.egov.excelingestion.processor;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.OutsideHouseholdTypeMasterService;
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
 * OutsideHouseholdValidationProcessor, driven through processWorkbook on a real workbook: category must belong to the
 * row's type (site-types master), boundary resolved / in campaign / lowest-or-second-lowest level, error
 * cells, outsideHouseholdSheetStatus. Required/enum columns are checked by the MDMS-schema validation, not here.
 */
@ExtendWith(MockitoExtension.class)
class OutsideHouseholdValidationProcessorTest {

    private static final String SHEET = "LocalizedOHSheet";
    private static final String TENANT = "mz";
    private static final int STATUS_COL = 1;
    private static final int ERROR_COL = 20;
    private static final int STATUS_VALIDATION_COL = 21;

    @Mock private ValidationService validationService;
    @Mock private EnrichmentUtil enrichmentUtil;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private ExcelUtil excelUtil;
    @Mock private CustomExceptionHandler exceptionHandler;
    @Mock private OutsideHouseholdTypeMasterService outsideHouseholdTypeMasterService;

    private OutsideHouseholdValidationProcessor processor;
    private XSSFWorkbook workbook;
    private Sheet sheet;
    private ProcessResource resource;

    @BeforeEach
    void setUp() {
        processor = new OutsideHouseholdValidationProcessor(validationService, enrichmentUtil, boundaryUtil, excelUtil,
                exceptionHandler, outsideHouseholdTypeMasterService);
        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet(SHEET);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY);
        header.createCell(STATUS_COL).setCellValue(ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY);
        sheet.createRow(1).createCell(0).setCellValue("Site name");
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
        lenient().when(outsideHouseholdTypeMasterService.getCategoriesByOutsideHouseholdType(any(), any())).thenReturn(Map.of(
                "OUTSIDE_HOUSEHOLD_PLACES", List.of("SCHOOL", "MARKET", "HEALTH_POST", "COMMUNITY_CENTRE", "OTHER"),
                "TRANSIT_POST", List.of("BUS_STATION", "BORDER_CROSSING", "CHECKPOINT", "FERRY_TERMINAL", "OTHER")));
    }

    @Test
    void validRowsGetNoErrorsEvenWhenEveryOutsideHouseholdIsInactive() {
        List<Map<String, Object>> rows = List.of(
                validRow(3, "Inactive", "V1"),
                validRow(4, "Inactive", "V2"));

        List<ValidationError> errors = run(rows);

        assertTrue(errors.isEmpty(), "No 'at least one Active' rule for sites, got " + errors);
        verify(validationService, never()).addValidationColumns(any(), anyMap());
    }

    @Test
    void enumValuesAreLeftToTheSchemaValidation() {
        // A type unknown to the master, and status / usage values: left to the schema step, nothing here
        Map<String, Object> row = validRow(3, "Active", "V1");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, "MOBILE_OUTSIDE_HOUSEHOLD");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "MOSQUE");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY, "Seasonal");

        assertTrue(run(List.of(row)).isEmpty());
    }

    @Test
    void categoryOutsideTheTypesListIsARowError() {
        Map<String, Object> ohpBusStation = validRow(3, "Active", "V1"); // OUTSIDE_HOUSEHOLD_PLACES + BUS_STATION
        ohpBusStation.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "BUS_STATION");
        Map<String, Object> transitBusStation = validRow(4, "Active", "V1");
        transitBusStation.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, "TRANSIT_POST");
        transitBusStation.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "BUS_STATION");

        List<ValidationError> errors = run(List.of(ohpBusStation, transitBusStation));

        assertEquals(1, errors.size(), errors.toString());
        assertEquals(3, errors.get(0).getRowNumber());
        assertEquals(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, errors.get(0).getColumnName());
        assertEquals(ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_CATEGORY_NOT_FOR_TYPE, errors.get(0).getErrorDetails());
        assertEquals(ValidationConstants.STATUS_INVALID, statusCell(3));
    }

    @Test
    void categoryCheckIgnoresCaseAndUsesTheLocalizedMessage() {
        Map<String, String> localization = new HashMap<>();
        localization.put(ValidationConstants.HCM_OUTSIDE_HOUSEHOLD_CATEGORY_NOT_FOR_TYPE, "Categorie non autorisee");
        Map<String, Object> lowerCaseOk = validRow(3, "Active", "V1");
        lowerCaseOk.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "school");
        Map<String, Object> wrong = validRow(4, "Active", "V1");
        wrong.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "CHECKPOINT");

        List<ValidationError> errors = run(List.of(lowerCaseOk, wrong), localization);

        assertEquals(1, errors.size(), errors.toString());
        assertEquals(4, errors.get(0).getRowNumber());
        assertEquals("Categorie non autorisee", errors.get(0).getErrorDetails());
    }

    @Test
    void typeWithoutCategoriesListAcceptsAnyCategory() {
        when(outsideHouseholdTypeMasterService.getCategoriesByOutsideHouseholdType(any(), any()))
                .thenReturn(Map.of("OUTSIDE_HOUSEHOLD_PLACES", List.of()));
        Map<String, Object> row = validRow(3, "Active", "V1");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "CHECKPOINT");

        assertTrue(run(List.of(row)).isEmpty());
    }

    @Test
    void boundaryMustResolveBeInCampaignAndBeLowestOrSecondLowestLevel() {
        Map<String, Object> notResolved = validRow(3, "Active", null);
        notResolved.put("ADMIN_VILLAGE", "Pasted name"); // selection present, code blank
        Map<String, Object> noBoundary = validRow(4, "Active", null);
        Map<String, Object> outsideCampaign = validRow(5, "Active", "X9");
        Map<String, Object> tooHigh = validRow(6, "Active", "P1"); // root level - higher than second-lowest
        Map<String, Object> lowest = validRow(7, "Active", "V2");
        Map<String, Object> secondLowest = validRow(8, "Active", "D1");

        List<ValidationError> errors = run(List.of(notResolved, noBoundary, outsideCampaign, tooHigh, lowest, secondLowest));

        assertEquals(4, errors.size(), errors.toString());
        assertEquals(3, errors.get(0).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_BOUNDARY_INVALID_SELECTION, errors.get(0).getErrorDetails());
        assertEquals(4, errors.get(1).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_BOUNDARY_REQUIRED, errors.get(1).getErrorDetails());
        assertEquals(5, errors.get(2).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_BOUNDARY_CODE_NOT_IN_CAMPAIGN, errors.get(2).getErrorDetails());
        assertEquals(6, errors.get(3).getRowNumber());
        assertEquals(ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_BOUNDARY_INVALID_LEVEL, errors.get(3).getErrorDetails());
        assertNull(sheet.getRow(6).getCell(ERROR_COL), "Lowest-level row must not get an error cell");
        assertNull(sheet.getRow(7).getCell(ERROR_COL), "Second-lowest-level row must not get an error cell");
    }

    @Test
    void errorsAreAppendedToTheRowErrorCell() {
        Map<String, Object> row = validRow(3, "Active", "P1"); // in campaign, but above second-lowest level
        // The MDMS-schema step already wrote its own message into this row
        Row excelRow = sheet.createRow(2);
        excelRow.createCell(ERROR_COL).setCellValue("Required field 'HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY' is missing");
        excelRow.createCell(STATUS_VALIDATION_COL).setCellValue(ValidationConstants.STATUS_INVALID);
        Row header = sheet.getRow(0);
        header.createCell(ERROR_COL).setCellValue(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
        header.createCell(STATUS_VALIDATION_COL).setCellValue(ValidationConstants.STATUS_COLUMN_NAME);

        run(List.of(row));

        // addValidationColumns itself reuses the existing error/status columns (see setUp's stub), so the
        // schema step's message and this processor's message land in the same cell, appended.
        assertEquals("Required field 'HCM_ADMIN_CONSOLE_OUTSIDE_HOUSEHOLD_CATEGORY' is missing; "
                        + ValidationConstants.DEFAULT_OUTSIDE_HOUSEHOLD_BOUNDARY_INVALID_LEVEL,
                sheet.getRow(2).getCell(ERROR_COL).getStringCellValue());
    }

    @Test
    void boundaryLookupFailureMarksRowsAsSystemError() {
        when(boundaryUtil.getEnrichedBoundaryCodesFromCampaign(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boundary service down"));

        List<ValidationError> errors = run(List.of(validRow(3, "Active", "V1")));

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
                enumProp(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, List.of("OUTSIDE_HOUSEHOLD_PLACES", "TRANSIT_POST")),
                enumProp(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, List.of("SCHOOL", "MARKET", "MOSQUE")),
                enumProp(ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY, List.of("Active", "Inactive"))));
        Map<String, Object> newCategory = validRow(3, "Inactive", "V1");
        newCategory.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "MOSQUE");
        Map<String, Object> unknownCategory = validRow(4, "Active", "V1");
        unknownCategory.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "STADIUM");

        List<ValidationError> schemaErrors = schemaValidation.validateDataWithPreFetchedSchema(
                List.of(newCategory, unknownCategory), SHEET, schema, new HashMap<>());

        assertEquals(1, schemaErrors.size(), schemaErrors.toString());
        assertEquals(4, schemaErrors.get(0).getRowNumber());
        assertEquals(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, schemaErrors.get(0).getColumnName());

        // ...and the sites processor accepts it once the master lists it for the type
        when(outsideHouseholdTypeMasterService.getCategoriesByOutsideHouseholdType(any(), any()))
                .thenReturn(Map.of("OUTSIDE_HOUSEHOLD_PLACES", List.of("SCHOOL", "MARKET", "MOSQUE")));
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
                eq(ValidationConstants.SHEET_KIND_OUTSIDE_HOUSEHOLD));
        return new ArrayList<>(captor.getValue());
    }

    private String statusCell(int rowNumber) {
        return sheet.getRow(rowNumber - 1).getCell(STATUS_VALIDATION_COL).getStringCellValue();
    }

    private static Map<String, Object> validRow(int rowNumber, String usage, String boundaryCode) {
        Map<String, Object> row = new HashMap<>();
        row.put(ProcessingConstants.ACTUAL_ROW_NUMBER_KEY, rowNumber);
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_TYPE_COLUMN_KEY, "OUTSIDE_HOUSEHOLD_PLACES");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_CATEGORY_COLUMN_KEY, "SCHOOL");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_NAME_COLUMN_KEY, "Site " + rowNumber);
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_STATUS_COLUMN_KEY, "Permanent");
        row.put(ProcessingConstants.OUTSIDE_HOUSEHOLD_USAGE_COLUMN_KEY, usage);
        if (boundaryCode != null) {
            row.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, boundaryCode);
        }
        return row;
    }
}
