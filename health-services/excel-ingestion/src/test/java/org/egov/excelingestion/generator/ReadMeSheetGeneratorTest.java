package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.mdms.ReadMeConfigData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for the merged README (instructions) sheet.
 */
@ExtendWith(MockitoExtension.class)
class ReadMeSheetGeneratorTest {

    private static final String TENANT_ID = "mz";
    private static final String SHEET_NAME = "README";

    private static final String PASSWORD = "sheet-password";

    @Mock
    private MDMSConfigService mdmsConfigService;

    @Mock
    private ExcelIngestionConfig config;

    private ReadMeSheetGenerator generator;
    private XSSFWorkbook workbook;

    @BeforeEach
    void setUp() {
        generator = new ReadMeSheetGenerator(mdmsConfigService, config);
        workbook = new XSSFWorkbook();
        lenient().when(config.getExcelSheetPassword()).thenReturn(PASSWORD);
    }

    @Test
    void mergesInstructionBlocksFromEveryConfiguredTypeInOrder() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1", "USER_LINE_2"));
        stubConfig("facility", block("FACILITY_HEADER", true, "FACILITY_LINE_1"));

        Map<String, String> localization = new HashMap<>();
        localization.put("USER_HEADER", "User instructions");
        localization.put("USER_LINE_1", "Fill the name");
        localization.put("USER_LINE_2", "Phone must be 10 digits");
        localization.put("FACILITY_HEADER", "Facility instructions");
        localization.put("FACILITY_LINE_1", "Facility code is auto-filled");

        generate(sheetConfig(List.of("user", "facility")), localization);

        List<String> lines = readLines();
        assertEquals(
                List.of("User instructions",
                        "Fill the name",
                        "Phone must be 10 digits",
                        "",
                        "Facility instructions",
                        "Facility code is auto-filled"),
                lines);
    }

    @Test
    void rendersBlockHeaderBoldAndDescriptionsPlain() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"));

        generate(sheetConfig(List.of("user")), Map.of());

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        assertTrue(isBold(sheet, 0), "block header must be bold");
        assertFalse(isBold(sheet, 1), "description must not be bold");
    }

    @Test
    void skipsBlocksNotMarkedInSheet() {
        ReadMeConfigData.ReadMeText hidden = block("HIDDEN_HEADER", true, "HIDDEN_LINE");
        hidden.setInSheet(false);

        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"), hidden);

        generate(sheetConfig(List.of("user")), Map.of());

        List<String> lines = readLines();
        assertFalse(lines.contains("HIDDEN_HEADER"));
        assertFalse(lines.contains("HIDDEN_LINE"));
        assertEquals(List.of("USER_HEADER", "USER_LINE_1"), lines);
    }

    @Test
    void skipsTypeWithNoConfigInsteadOfFailing() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"));
        when(mdmsConfigService.getReadMeConfig(any(), eq(TENANT_ID), eq("facility"))).thenReturn(null);

        generate(sheetConfig(List.of("user", "facility")), Map.of());

        assertEquals(List.of("USER_HEADER", "USER_LINE_1"), readLines());
    }

    @Test
    void fallsBackToRawKeyWhenLocalizationMissing() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"));

        generate(sheetConfig(List.of("user")), Map.of());

        assertEquals(List.of("USER_HEADER", "USER_LINE_1"), readLines());
    }

    @Test
    void fallsBackToGenerationTypeWhenReadMeTypesAbsent() {
        stubConfig("boundary", block("TARGET_HEADER", true, "TARGET_LINE"));

        SheetGenerationConfig config = SheetGenerationConfig.builder().sheetName(SHEET_NAME).build();
        GenerateResource resource = new GenerateResource();
        resource.setTenantId(TENANT_ID);
        resource.setType("boundary");

        generator.generateSheet(workbook, SHEET_NAME, config, resource, new RequestInfo(), Map.of());

        assertEquals(List.of("TARGET_HEADER", "TARGET_LINE"), readLines());
    }

    @Test
    void rendersSharedBlockOnlyOnceAcrossTypes() {
        stubConfig("user", block("COMMON_HEADER", true, "COMMON_LINE"));
        stubConfig("facility", block("COMMON_HEADER", true, "COMMON_LINE"));

        generate(sheetConfig(List.of("user", "facility")), Map.of());

        List<String> lines = readLines();
        assertEquals(1, lines.stream().filter("COMMON_HEADER"::equals).count(),
                "a block shared by two types must render once");
    }

    @Test
    void producesEmptySheetWhenNoTypeHasConfig() {
        when(mdmsConfigService.getReadMeConfig(any(), eq(TENANT_ID), eq("user"))).thenReturn(null);

        generate(sheetConfig(List.of("user")), Map.of());

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        assertNotNull(sheet, "sheet is still created so the workbook keeps a stable tab layout");
        assertEquals(0, sheet.getPhysicalNumberOfRows());
    }

    /**
     * Pins the real MDMS payload shape: unknown fields ({@code inUiInfo}, {@code isStepRequired}) must
     * deserialize without error, and the {@code inSheet: false} block must not reach the sheet.
     */
    @Test
    void rendersRealMdmsPayloadIgnoringUnknownFieldsAndUiOnlyBlocks() throws Exception {
        String json = """
                {
                  "type": "facility",
                  "texts": [
                    {
                      "header": "FACILITYWITHBOUNDARY_README_HEADER_1",
                      "inSheet": false,
                      "inUiInfo": true,
                      "descriptions": [
                        { "text": "FACILITYWITHBOUNDARY_README_HEADER_1_DESC_1", "isStepRequired": true }
                      ],
                      "isHeaderBold": true
                    },
                    {
                      "header": "FACILITYWITHBOUNDARY_README_HEADER_2",
                      "inSheet": true,
                      "inUiInfo": true,
                      "descriptions": [
                        { "text": "FACILITYWITHBOUNDARY_README_HEADER_2_DESC_1", "isStepRequired": true },
                        { "text": "FACILITYWITHBOUNDARY_README_HEADER_2_DESC_2", "isStepRequired": false }
                      ],
                      "isHeaderBold": true
                    }
                  ]
                }
                """;

        ReadMeConfigData data = new ObjectMapper().readValue(json, ReadMeConfigData.class);
        when(mdmsConfigService.getReadMeConfig(any(), eq(TENANT_ID), eq("facility"))).thenReturn(data);

        generate(sheetConfig(List.of("facility")), Map.of());

        assertEquals(
                List.of("FACILITYWITHBOUNDARY_README_HEADER_2",
                        "FACILITYWITHBOUNDARY_README_HEADER_2_DESC_1",
                        "FACILITYWITHBOUNDARY_README_HEADER_2_DESC_2"),
                readLines(),
                "only inSheet:true blocks render; unknown MDMS fields are ignored");
    }

    /** A single authored config (the "unified-sheet" entry) renders without any merge. */
    @Test
    void rendersSingleUnifiedSheetConfig() {
        stubConfig("unified-sheet", block("UNIFIED_HEADER", true, "UNIFIED_LINE_1", "UNIFIED_LINE_2"));

        generate(sheetConfig(List.of("unified-sheet")), Map.of());

        assertEquals(List.of("UNIFIED_HEADER", "UNIFIED_LINE_1", "UNIFIED_LINE_2"), readLines());
    }

    /**
     * The README is read-only reference content, so it must ship locked like the Boundary List. The
     * direct generator path bypasses ExcelDataPopulator, and join mode skips the workbook-wide
     * protection pass, so the generator has to protect the sheet itself.
     */
    @Test
    void locksSheetAndEveryCell() {
        stubConfig("unified-sheet", block("UNIFIED_HEADER", true, "UNIFIED_LINE_1"));

        generate(sheetConfig(List.of("unified-sheet")), Map.of());

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        assertTrue(sheet.getProtect(), "README sheet must be protected");
        for (Row row : sheet) {
            assertTrue(row.getCell(0).getCellStyle().getLocked(),
                    "every README cell must carry a locked style");
        }
    }

    @Test
    void skipsProtectionWhenNoPasswordConfigured() {
        when(config.getExcelSheetPassword()).thenReturn("");
        stubConfig("unified-sheet", block("UNIFIED_HEADER", true, "UNIFIED_LINE_1"));

        generate(sheetConfig(List.of("unified-sheet")), Map.of());

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        assertFalse(sheet.getProtect(), "no password configured means no sheet protection");
        assertEquals(List.of("UNIFIED_HEADER", "UNIFIED_LINE_1"), readLines(),
                "content still renders when protection is skipped");
    }

    /**
     * The MDMS excelIngestionGenerate schema sets "additionalProperties": false on each sheet, so
     * readMeTypes cannot be saved there. Authors put the type in schemaName instead, which is unused
     * for generation once a generationClass is set.
     */
    @Test
    void readsSingleTypeFromSchemaNameWhenReadMeTypesAbsent() {
        stubConfig("unified-sheet", block("UNIFIED_HEADER", true, "UNIFIED_LINE_1"));

        generate(schemaNameConfig("unified-sheet"), Map.of());

        assertEquals(List.of("UNIFIED_HEADER", "UNIFIED_LINE_1"), readLines());
    }

    @Test
    void mergesCommaSeparatedTypesFromSchemaNameInOrder() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"));
        stubConfig("facility", block("FACILITY_HEADER", true, "FACILITY_LINE_1"));

        generate(schemaNameConfig("user,facility"), Map.of());

        assertEquals(
                List.of("USER_HEADER", "USER_LINE_1", "", "FACILITY_HEADER", "FACILITY_LINE_1"),
                readLines());
    }

    /** Authors hand-edit the MDMS JSON, so spacing and stray separators must not break the lookup. */
    @Test
    void ignoresWhitespaceAndEmptyEntriesInSchemaNameList() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"));
        stubConfig("facility", block("FACILITY_HEADER", true, "FACILITY_LINE_1"));

        generate(schemaNameConfig(" user , , facility ,"), Map.of());

        assertEquals(
                List.of("USER_HEADER", "USER_LINE_1", "", "FACILITY_HEADER", "FACILITY_LINE_1"),
                readLines());
    }

    /** readMeTypes wins when both are set, so a programmatic caller can override the MDMS value. */
    @Test
    void prefersReadMeTypesOverSchemaName() {
        stubConfig("user", block("USER_HEADER", true, "USER_LINE_1"));

        SheetGenerationConfig config = SheetGenerationConfig.builder()
                .sheetName(SHEET_NAME)
                .readMeTypes(List.of("user"))
                .schemaName("facility")
                .build();

        generate(config, Map.of());

        assertEquals(List.of("USER_HEADER", "USER_LINE_1"), readLines());
    }

    @Test
    void fallsBackToGenerationTypeWhenSchemaNameBlank() {
        stubConfig("unified-console", block("CONSOLE_HEADER", true, "CONSOLE_LINE"));

        generate(schemaNameConfig("   "), Map.of());

        assertEquals(List.of("CONSOLE_HEADER", "CONSOLE_LINE"), readLines());
    }

    // --- helpers ---

    private void generate(SheetGenerationConfig config, Map<String, String> localizationMap) {
        GenerateResource resource = new GenerateResource();
        resource.setTenantId(TENANT_ID);
        resource.setType("unified-console");
        generator.generateSheet(workbook, SHEET_NAME, config, resource, new RequestInfo(), localizationMap);
    }

    private SheetGenerationConfig sheetConfig(List<String> readMeTypes) {
        return SheetGenerationConfig.builder()
                .sheetName(SHEET_NAME)
                .readMeTypes(readMeTypes)
                .build();
    }

    /** Mirrors the real MDMS entry, which carries the type list in schemaName. */
    private SheetGenerationConfig schemaNameConfig(String schemaName) {
        return SheetGenerationConfig.builder()
                .sheetName(SHEET_NAME)
                .schemaName(schemaName)
                .build();
    }

    private void stubConfig(String type, ReadMeConfigData.ReadMeText... texts) {
        ReadMeConfigData data = ReadMeConfigData.builder()
                .type(type)
                .texts(List.of(texts))
                .build();
        when(mdmsConfigService.getReadMeConfig(any(), eq(TENANT_ID), eq(type))).thenReturn(data);
    }

    private ReadMeConfigData.ReadMeText block(String header, boolean bold, String... descriptions) {
        List<ReadMeConfigData.ReadMeDescription> descriptionList = new ArrayList<>();
        for (String description : descriptions) {
            descriptionList.add(ReadMeConfigData.ReadMeDescription.builder().text(description).build());
        }
        return ReadMeConfigData.ReadMeText.builder()
                .header(header)
                .isHeaderBold(bold)
                .inSheet(true)
                .descriptions(descriptionList)
                .build();
    }

    private boolean isBold(Sheet sheet, int rowIndex) {
        XSSFCellStyle style = (XSSFCellStyle) sheet.getRow(rowIndex).getCell(0).getCellStyle();
        return style.getFont().getBold();
    }

    private List<String> readLines() {
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        List<String> lines = new ArrayList<>();
        if (sheet == null) {
            return lines;
        }
        for (Row row : sheet) {
            lines.add(row.getCell(0).getStringCellValue());
        }
        return lines;
    }
}
