package org.egov.excelingestion.generator;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ColumnDefMaker;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.ExcelStyleHelper;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the non-ASCII / non-English level-name fix: the generated boundary code cell
 * must never be empty. Exercises the real BoundaryHierarchySheetGenerator (data cell fix)
 * and the real ExcelDataPopulator.createHeaderRows (visible header fix) across every script.
 */
@ExtendWith(MockitoExtension.class)
class NonAsciiBoundaryCodeTest {

    private static final String HIER = "MYHIER";

    // Exhaustive battery: Latin accents, spaced, Arabic, Devanagari, CJK, Cyrillic, Greek, Thai.
    private static final List<String> LEVEL_NAMES = Arrays.asList(
            "Café", "Región Sur", "Ñuñoa", "Zürich",
            "منطقة", "क्षेत्र", "地区", "Район", "Περιφέρεια", "เขต");

    @Mock private BoundaryService boundaryService;
    @Mock private BoundaryUtil boundaryUtil;
    @Mock private MDMSService mdmsService;
    @Mock private CampaignService campaignService;
    @Mock private CustomExceptionHandler exceptionHandler;

    private BoundaryHierarchySheetGenerator generator;
    private ExcelDataPopulator populator;
    private AutoCloseable closeable;

    private static String expectedCode(String levelName) {
        return (HIER + "_" + levelName).toUpperCase();
    }

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ColumnDefMaker columnDefMaker = new ColumnDefMaker();
        SchemaColumnDefUtil schemaColumnDefUtil = new SchemaColumnDefUtil(columnDefMaker, exceptionHandler);
        generator = new BoundaryHierarchySheetGenerator(
                boundaryService, boundaryUtil, mdmsService, campaignService, exceptionHandler, schemaColumnDefUtil);
        // createHeaderRows uses only the style helper; config/protection are unused on that path.
        populator = new ExcelDataPopulator(mock(ExcelIngestionConfig.class), new ExcelStyleHelper(),
                mock(CellProtectionManager.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) closeable.close();
    }

    // ---- shared generator wiring -------------------------------------------------

    private GenerateResource resource() {
        return GenerateResource.builder()
                .tenantId("tenant1")
                .hierarchyType(HIER)
                .additionalDetails(new HashMap<>())
                .build();
    }

    private List<String> boundaryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < LEVEL_NAMES.size(); i++) codes.add("BCODE_" + i);
        return codes;
    }

    private void wireGenerator(Map<String, String> localizationMap) {
        List<BoundaryHierarchyChild> children = new ArrayList<>();
        for (String name : LEVEL_NAMES) children.add(BoundaryHierarchyChild.builder().boundaryType(name).build());
        BoundaryHierarchy hierarchy = BoundaryHierarchy.builder().boundaryHierarchy(children).build();

        when(boundaryService.fetchBoundaryHierarchy(any(), any(), any()))
                .thenReturn(BoundaryHierarchyResponse.builder().boundaryHierarchy(Arrays.asList(hierarchy)).build());
        lenient().when(boundaryService.fetchBoundaryRelationship(any(), any(), any()))
                .thenReturn(BoundarySearchResponse.builder().tenantBoundary(new ArrayList<>()).build());
        lenient().when(boundaryUtil.buildCodeToBoundaryMap(any())).thenReturn(new HashMap<>());
        lenient().when(boundaryUtil.getEnrichedBoundariesFromCampaign(any(), any(), any(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(boundaryUtil.processBoundariesWithEnrichment(any(), any(), any()))
                .thenReturn(Collections.singletonList(
                        new BoundaryUtil.BoundaryRowData(boundaryCodes(), "BCODE_" + (LEVEL_NAMES.size() - 1))));
        when(campaignService.getBoundariesFromCampaign(any(), any(), any()))
                .thenReturn(Collections.singletonList(
                        CampaignSearchResponse.BoundaryDetail.builder().code("BCODE_0").isRoot(true).build()));
        when(campaignService.getProjectTypeFromCampaign(any(), any(), any())).thenReturn(null);
    }

    // ---- 1. Column technical codes preserve every script (never empty) -----------

    @Test
    void columnCodes_preserveAllNonEnglishScripts_neverEmpty() {
        wireGenerator(new HashMap<>());
        SheetGenerationResult result = generator.generateSheetData(
                sheetConfig(), resource(), new RequestInfo(), new HashMap<>());

        List<ColumnDef> columns = result.getColumnDefs();
        for (int i = 0; i < LEVEL_NAMES.size(); i++) {
            String name = columns.get(i).getName();
            assertNotNull(name, "column code null for " + LEVEL_NAMES.get(i));
            assertFalse(name.trim().isEmpty(), "column code EMPTY for " + LEVEL_NAMES.get(i));
            assertEquals(expectedCode(LEVEL_NAMES.get(i)), name,
                    "column code should preserve the script for " + LEVEL_NAMES.get(i));
        }
    }

    // ---- 2. Data cell falls back to the code when localization is BLANK ----------

    @Test
    void dataCell_fallsBackToBoundaryCode_whenLocalizationBlank() {
        Map<String, String> blank = new HashMap<>();
        for (String c : boundaryCodes()) blank.put(c, "");            // the bug trigger
        wireGenerator(blank);

        SheetGenerationResult result = generator.generateSheetData(sheetConfig(), resource(), new RequestInfo(), blank);
        assertEquals(1, result.getData().size());
        Map<String, Object> row = result.getData().get(0);

        List<String> codes = boundaryCodes();
        for (int i = 0; i < LEVEL_NAMES.size(); i++) {
            Object cell = row.get(expectedCode(LEVEL_NAMES.get(i)));
            assertNotNull(cell, "cell null at level " + LEVEL_NAMES.get(i));
            assertFalse(cell.toString().trim().isEmpty(),
                    "cell EMPTY at level " + LEVEL_NAMES.get(i) + " (regression)");
            assertEquals(codes.get(i), cell, "blank localization must fall back to boundary code");
        }
    }

    // ---- 3. Data cell uses the localized non-English name when present -----------

    @Test
    void dataCell_usesLocalizedNonEnglishName_whenPresent() {
        Map<String, String> loc = new HashMap<>();
        List<String> codes = boundaryCodes();
        for (int i = 0; i < codes.size(); i++) loc.put(codes.get(i), LEVEL_NAMES.get(i)); // non-English values
        wireGenerator(loc);

        SheetGenerationResult result = generator.generateSheetData(sheetConfig(), resource(), new RequestInfo(), loc);
        Map<String, Object> row = result.getData().get(0);
        for (int i = 0; i < LEVEL_NAMES.size(); i++) {
            assertEquals(LEVEL_NAMES.get(i), row.get(expectedCode(LEVEL_NAMES.get(i))),
                    "localized non-English value should be preserved verbatim");
        }
    }

    // ---- 4. Visible header falls back to code when blank, and round-trips bytes --

    @Test
    void header_fallsBackToCode_whenBlank_andSurvivesXlsxRoundTrip() throws Exception {
        List<ColumnDef> columns = new ArrayList<>();
        Map<String, String> blank = new HashMap<>();
        for (String name : LEVEL_NAMES) {
            String code = expectedCode(name);
            columns.add(ColumnDef.builder().name(code).colorHex("#93c47d").width(50).orderNumber(columns.size() + 1).build());
            blank.put(code, "");   // blank localized value -> must fall back to the code
        }

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Boundary");
        invokeCreateHeaderRows(wb, sheet, columns, blank);

        // Row 0 = technical code, Row 1 = visible header. Both must equal the code, non-empty.
        assertHeaders(sheet, columns);

        // Prove the bytes round-trip (UTF-8) through an actual .xlsx write+read.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        try (Workbook reread = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertHeaders(reread.getSheet("Boundary"), columns);
        }
    }

    @Test
    void header_usesLocalizedNonEnglishName_whenPresent() throws Exception {
        List<ColumnDef> columns = new ArrayList<>();
        Map<String, String> loc = new HashMap<>();
        for (String name : LEVEL_NAMES) {
            String code = expectedCode(name);
            columns.add(ColumnDef.builder().name(code).colorHex("#93c47d").width(50).orderNumber(columns.size() + 1).build());
            loc.put(code, "Localized-" + name);   // non-English friendly value
        }
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Boundary");
        invokeCreateHeaderRows(wb, sheet, columns, loc);

        Row visible = sheet.getRow(1);
        for (int i = 0; i < columns.size(); i++) {
            assertEquals("Localized-" + LEVEL_NAMES.get(i), visible.getCell(i).getStringCellValue());
        }
        wb.close();
    }

    // ---- helpers -----------------------------------------------------------------

    private void assertHeaders(Sheet sheet, List<ColumnDef> columns) {
        Row technical = sheet.getRow(0);
        Row visible = sheet.getRow(1);
        for (int i = 0; i < columns.size(); i++) {
            String code = columns.get(i).getName();
            String tech = technical.getCell(i).getStringCellValue();
            String head = visible.getCell(i).getStringCellValue();
            assertEquals(code, tech, "technical code row must hold the code");
            assertFalse(tech.trim().isEmpty(), "technical code EMPTY: " + code);
            assertEquals(code, head, "visible header must fall back to the code when localization blank: " + code);
            assertFalse(head.trim().isEmpty(), "visible header EMPTY (regression) for: " + code);
        }
    }

    private void invokeCreateHeaderRows(Workbook wb, Sheet sheet, List<ColumnDef> columns, Map<String, String> loc)
            throws Exception {
        Method m = ExcelDataPopulator.class.getDeclaredMethod(
                "createHeaderRows", Workbook.class, Sheet.class, List.class, Map.class);
        m.setAccessible(true);
        m.invoke(populator, wb, sheet, columns, loc);
    }

    private SheetGenerationConfig sheetConfig() {
        return mock(SheetGenerationConfig.class);
    }
}
