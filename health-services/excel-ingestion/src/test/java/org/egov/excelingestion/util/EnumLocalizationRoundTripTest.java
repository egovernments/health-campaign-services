package org.egov.excelingestion.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end check of the enum dropdown localization feature: a sheet generated in a non-English
 * locale shows localized dropdown values and pre-filled cells, and the upload-side normalizer turns
 * them back into the canonical MDMS values that the processors and project-factory match on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnumLocalizationRoundTripTest {

    private static final String SHEET_NAME = "Facilities";
    private static final String STATUS_COL = ProcessingConstants.FACILITY_STATUS_COLUMN_KEY;
    private static final String USAGE_COL = ProcessingConstants.FACILITY_USAGE_COLUMN_KEY;

    @Mock
    private ExcelIngestionConfig config;
    @Mock
    private ExcelStyleHelper excelStyleHelper;
    @Mock
    private CellProtectionManager cellProtectionManager;
    @Mock
    private ExcelUtil excelUtil;
    @Mock
    private org.egov.excelingestion.exception.CustomExceptionHandler exceptionHandler;

    private ExcelDataPopulator populator;
    private EnumValueNormalizer normalizer;
    private Map<String, String> localizationMap;

    @BeforeEach
    void setUp() {
        when(excelStyleHelper.createCustomHeaderStyle(any(), any(), anyBoolean()))
                .thenAnswer(inv -> ((Workbook) inv.getArgument(0)).createCellStyle());
        when(excelStyleHelper.createDataCellStyle(any(), anyBoolean()))
                .thenAnswer(inv -> ((Workbook) inv.getArgument(0)).createCellStyle());

        when(excelUtil.convertSheetToMapListCached(any(), any(), any())).thenReturn(new ArrayList<>());

        populator = new ExcelDataPopulator(config, excelStyleHelper, cellProtectionManager);
        normalizer = new EnumValueNormalizer(
                new SchemaColumnDefUtil(new ColumnDefMaker(), exceptionHandler),
                new ObjectMapper(), excelUtil);

        localizationMap = new HashMap<>();
        localizationMap.put(STATUS_COL, "Statut de l'etablissement");
        localizationMap.put(STATUS_COL + "_PERMANENT", "Permanent(e)");
        localizationMap.put(STATUS_COL + "_TEMPORARY", "Temporaire");
        localizationMap.put(USAGE_COL, "Actif / Inactif");
        localizationMap.put(USAGE_COL + "_ACTIVE", "Actif");
        localizationMap.put(USAGE_COL + "_INACTIVE", "Inactif");
    }

    private List<ColumnDef> facilityColumns() {
        List<ColumnDef> columns = new ArrayList<>();
        columns.add(ColumnDef.builder().name(STATUS_COL).type("enum").orderNumber(1).width(30)
                .enumValues(List.of(ProcessingConstants.STATUS_TEMPORARY, ProcessingConstants.STATUS_PERMANENT))
                .build());
        columns.add(ColumnDef.builder().name(USAGE_COL).type("enum").orderNumber(2).width(30)
                .enumValues(List.of("Active", "Inactive"))
                .build());
        return columns;
    }

    /** MDMS-shaped schema for the two enum columns, as the upload path receives it. */
    private Map<String, Object> facilitySchema() {
        List<Map<String, Object>> enumProperties = new ArrayList<>();
        enumProperties.add(new HashMap<>(Map.of(
                "name", STATUS_COL, "orderNumber", 1,
                "enum", List.of(ProcessingConstants.STATUS_TEMPORARY, ProcessingConstants.STATUS_PERMANENT))));
        enumProperties.add(new HashMap<>(Map.of(
                "name", USAGE_COL, "orderNumber", 2,
                "enum", List.of("Active", "Inactive"))));
        Map<String, Object> schema = new HashMap<>();
        schema.put("enumProperties", enumProperties);
        return schema;
    }

    @Test
    void generatedSheetShowsLocalizedDropdownAndPrefilledValues() {
        // Pre-filled rows carry canonical values, exactly as the generators produce them
        List<Map<String, Object>> data = List.of(
                new HashMap<>(Map.of(STATUS_COL, "Permanent", USAGE_COL, "Inactive")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, facilityColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        // The dropdown list itself is localized
        List<String> dropdownValues = new ArrayList<>();
        for (DataValidation dv : sheet.getDataValidations()) {
            String[] values = dv.getValidationConstraint().getExplicitListValues();
            if (values != null) {
                dropdownValues.addAll(List.of(values));
            }
        }
        assertTrue(dropdownValues.contains("Permanent(e)"), "status dropdown should be localized");
        assertTrue(dropdownValues.contains("Temporaire"), "status dropdown should be localized");
        assertTrue(dropdownValues.contains("Actif"), "usage dropdown should be localized");
        assertTrue(dropdownValues.contains("Inactif"), "usage dropdown should be localized");

        // and the pre-filled cell matches the localized list, so it is selectable in its own dropdown
        assertEquals("Permanent(e)", cellValue(sheet, 2, STATUS_COL));
        assertEquals("Inactif", cellValue(sheet, 2, USAGE_COL));
    }

    @Test
    void uploadNormalizesLocalizedValuesBackToCanonical() {
        List<Map<String, Object>> data = List.of(
                new HashMap<>(Map.of(STATUS_COL, "Permanent", USAGE_COL, "Inactive")),
                new HashMap<>(Map.of(STATUS_COL, "Temporary", USAGE_COL, "Active")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, facilityColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        // Sanity: the generated file really is localized before normalization
        assertEquals("Permanent(e)", cellValue(sheet, 2, STATUS_COL));

        normalizeUpload(workbook, sheet);

        // Canonical values restored - what the processors compare and project-factory maps on
        assertEquals("Permanent", cellValue(sheet, 2, STATUS_COL));
        assertEquals("Inactive", cellValue(sheet, 2, USAGE_COL));
        assertEquals("Temporary", cellValue(sheet, 3, STATUS_COL));
        assertEquals("Active", cellValue(sheet, 3, USAGE_COL));
    }

    @Test
    void normalizationAlsoUpdatesTheSharedCachedRowsValidationReads() {
        List<Map<String, Object>> data = List.of(
                new HashMap<>(Map.of(STATUS_COL, "Permanent", USAGE_COL, "Active")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, facilityColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        // The immutable-baseline join populates this @Cacheable list BEFORE normalization runs, and
        // validation/processors/persistence all read it instead of re-reading the cells. Rewriting only
        // the cells would leave every downstream consumer on localized values.
        List<Map<String, Object>> cachedRows = new ArrayList<>();
        cachedRows.add(new HashMap<>(Map.of(STATUS_COL, "Permanent(e)", USAGE_COL, "Actif")));
        when(excelUtil.convertSheetToMapListCached(any(), eq(SHEET_NAME), any())).thenReturn(cachedRows);

        normalizeUpload(workbook, sheet);

        assertEquals("Permanent", cachedRows.get(0).get(STATUS_COL),
                "cached row must be canonical, else validation sees the localized value");
        assertEquals("Active", cachedRows.get(0).get(USAGE_COL));
    }

    @Test
    void unrecognizedValueIsLeftForValidatorsToReject() {
        List<Map<String, Object>> data = List.of(new HashMap<>(Map.of(STATUS_COL, "Permanent")));
        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, facilityColumns(), data, localizationMap);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        // Simulate a pasted, out-of-list value (Excel list validation is bypassed on paste)
        setCellValue(sheet, 2, STATUS_COL, "Definitely Not A Status");

        normalizeUpload(workbook, sheet);

        assertEquals("Definitely Not A Status", cellValue(sheet, 2, STATUS_COL),
                "unknown values must survive normalization so the existing validators flag them");
    }

    @Test
    void withoutLocalizationEntriesGenerationAndUploadAreUnchanged() {
        // No enum localization seeded: today's behavior must be preserved end to end
        Map<String, String> headersOnly = Map.of(STATUS_COL, "Facility Status", USAGE_COL, "Active / Inactive");
        List<Map<String, Object>> data = List.of(
                new HashMap<>(Map.of(STATUS_COL, "Permanent", USAGE_COL, "Active")));

        Workbook workbook = populator.populateSheetWithData(SHEET_NAME, facilityColumns(), data, headersOnly);
        Sheet sheet = workbook.getSheet(SHEET_NAME);

        assertEquals("Permanent", cellValue(sheet, 2, STATUS_COL));
        assertEquals("Active", cellValue(sheet, 2, USAGE_COL));

        Map<String, Map<String, Object>> schemas = Map.of(SHEET_NAME, facilitySchema());
        normalizer.normalizeToCanonical(workbook, resource(), schemas, headersOnly);

        assertEquals("Permanent", cellValue(sheet, 2, STATUS_COL));
        assertEquals("Active", cellValue(sheet, 2, USAGE_COL));
    }

    // ---------- helpers ----------

    /**
     * Runs the upload-side normalization. Defaults the cached-rows stub to empty; tests that care about
     * the cache stub it themselves BEFORE calling this (Mockito keeps the most recent stubbing).
     */
    private void normalizeUpload(Workbook workbook, Sheet sheet) {
        normalizer.normalizeToCanonical(workbook, resource(), Map.of(SHEET_NAME, facilitySchema()), localizationMap);
    }

    private ProcessResource resource() {
        return ProcessResource.builder().tenantId("mz").fileStoreId("fs-1").type("unified-console").build();
    }

    private int columnIndex(Sheet sheet, String technicalName) {
        org.apache.poi.ss.usermodel.Row technicalRow = sheet.getRow(0);
        for (int i = 0; i < technicalRow.getLastCellNum(); i++) {
            Cell cell = technicalRow.getCell(i);
            if (cell != null && technicalName.equals(cell.getStringCellValue())) {
                return i;
            }
        }
        return -1;
    }

    private String cellValue(Sheet sheet, int rowIdx, String technicalName) {
        Cell cell = sheet.getRow(rowIdx).getCell(columnIndex(sheet, technicalName));
        return cell == null ? null : cell.getStringCellValue();
    }

    private void setCellValue(Sheet sheet, int rowIdx, String technicalName, String value) {
        sheet.getRow(rowIdx).getCell(columnIndex(sheet, technicalName)).setCellValue(value);
    }
}
