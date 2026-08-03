package org.egov.excelingestion.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.mdms.ReadMeConfigData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the README (instructions) sheet for a generated workbook.
 *
 * <p>The workbook produced by excel-ingestion combines several resource sheets (user, facility,
 * boundary/target) that project-factory used to generate as separate workbooks, each with its own
 * README. This generator merges them: it reads the MDMS {@code ReadMeConfig} entry for each type
 * listed in {@code readMeTypes} and renders their instruction blocks one after another into a single
 * sheet, so the operator has one place to read how to fill the workbook.
 *
 * <p>Rendering matches the old project-factory sheet: one wide wrapped column, bold block headers,
 * a blank spacer line between blocks, and the whole sheet locked.
 */
@Component
@Slf4j
public class ReadMeSheetGenerator implements ISheetGenerator {

    /** Width (in characters) of the single instruction column - mirrors the old ExcelJS sheet. */
    private static final int INSTRUCTION_COLUMN_WIDTH_CHARS = 130;

    /** Approximate characters that fit on one rendered line, used to size wrapped row heights. */
    private static final int MAX_CHARACTERS_PER_LINE = 100;

    /** Height of a single text line, in POI twips (15pt). */
    private static final short LINE_HEIGHT_TWIPS = 300;

    /** Separator for the ReadMeConfig type list carried in {@code schemaName}. */
    private static final String TYPE_SEPARATOR = ",";

    private final MDMSConfigService mdmsConfigService;
    private final ExcelIngestionConfig config;

    public ReadMeSheetGenerator(MDMSConfigService mdmsConfigService, ExcelIngestionConfig config) {
        this.mdmsConfigService = mdmsConfigService;
        this.config = config;
    }

    @Override
    public XSSFWorkbook generateSheet(XSSFWorkbook workbook,
                                      String sheetName,
                                      SheetGenerationConfig sheetConfig,
                                      GenerateResource generateResource,
                                      RequestInfo requestInfo,
                                      Map<String, String> localizationMap) {

        List<String> readMeTypes = resolveReadMeTypes(sheetConfig, generateResource);
        log.info("Generating README sheet '{}' for types: {}", sheetName, readMeTypes);

        List<Line> lines = buildLines(readMeTypes, generateResource, requestInfo, localizationMap);

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            sheet = workbook.createSheet(sheetName);
        }

        writeLines(workbook, sheet, lines);

        sheet.setColumnWidth(0, INSTRUCTION_COLUMN_WIDTH_CHARS * 256);

        protectSheet(sheet);

        log.info("README sheet '{}' generated with {} lines", sheetName, lines.size());
        return workbook;
    }

    /**
     * Locks the README, matching the Boundary List rule: it is read-only reference content, never a
     * user-entry sheet. Applied here rather than centrally because this generator uses the direct
     * workbook path, which bypasses {@code ExcelDataPopulator.applyProtection}; and in join mode
     * (unified-console) {@code ConfigBasedGenerationService} skips the workbook-wide protection pass
     * entirely, so an unprotected README would otherwise ship fully editable. Every cell already
     * carries a locked style, so protectSheet makes the whole sheet read-only.
     */
    private void protectSheet(Sheet sheet) {
        String password = config.getExcelSheetPassword();
        if (password == null || password.isEmpty()) {
            log.info("README sheet protection SKIPPED for '{}' - no password configured", sheet.getSheetName());
            return;
        }
        sheet.protectSheet(password);
        log.info("README sheet protection applied to '{}'", sheet.getSheetName());
    }

    /**
     * ReadMeConfig types whose instruction blocks go into this README, in render order.
     *
     * <p>Resolved from, in order of precedence:
     * <ol>
     *   <li>{@code readMeTypes} — programmatic override, not settable from MDMS today;</li>
     *   <li>{@code schemaName} — the MDMS-authored list, comma-separated
     *       ("unified-sheet", or "user,facility,boundary" to merge per-resource configs). This field is
     *       reused because the excelIngestionGenerate schema sets {@code "additionalProperties": false}
     *       on each sheet, so a dedicated key cannot be saved; it is unused for generation whenever a
     *       generationClass is set;</li>
     *   <li>the generation type — keeps single-resource templates working with no config change.</li>
     * </ol>
     *
     * <p>The generation type fallback only fits templates whose type matches an authored ReadMeConfig
     * type. The combined workbook's type is "unified-console" while its ReadMeConfig type is
     * "unified-sheet", so that template must name the type explicitly via schemaName.
     */
    private List<String> resolveReadMeTypes(SheetGenerationConfig sheetConfig, GenerateResource generateResource) {
        List<String> configured = sheetConfig.getReadMeTypes();
        if (configured == null || configured.isEmpty()) {
            configured = parseTypes(sheetConfig.getSchemaName());
        }
        if (configured.isEmpty()) {
            configured = List.of(generateResource.getType());
        }
        // De-duplicate while preserving authored order so a type listed twice renders once.
        return new ArrayList<>(new LinkedHashSet<>(configured));
    }

    /** Splits the comma-separated MDMS type list, dropping blanks and surrounding whitespace. */
    private List<String> parseTypes(String commaSeparatedTypes) {
        if (commaSeparatedTypes == null || commaSeparatedTypes.isBlank()) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        for (String type : commaSeparatedTypes.split(TYPE_SEPARATOR)) {
            String trimmed = type.trim();
            if (!trimmed.isEmpty()) {
                types.add(trimmed);
            }
        }
        return types;
    }

    /**
     * Flattens every type's ReadMeConfig into the ordered list of rendered lines. A type with no
     * config authored in MDMS is skipped with a warning rather than failing the generation, so
     * adding a new sheet to the workbook never breaks the download before its copy exists.
     */
    private List<Line> buildLines(List<String> readMeTypes,
                                  GenerateResource generateResource,
                                  RequestInfo requestInfo,
                                  Map<String, String> localizationMap) {
        List<Line> lines = new ArrayList<>();
        Set<String> renderedHeaders = new LinkedHashSet<>();

        for (String type : readMeTypes) {
            ReadMeConfigData readMeConfig =
                    mdmsConfigService.getReadMeConfig(requestInfo, generateResource.getTenantId(), type);

            if (readMeConfig == null) {
                log.warn("No ReadMe config found for type '{}', skipping its README block", type);
                continue;
            }

            for (ReadMeConfigData.ReadMeText text : readMeConfig.getTexts()) {
                if (!text.isInSheet()) {
                    continue;
                }

                String header = localize(localizationMap, text.getHeader());
                // Guard against the same block appearing under two types (shared instructions).
                if (!renderedHeaders.add(header)) {
                    continue;
                }

                // Blank spacer before each block, matching the old sheet's layout.
                if (!lines.isEmpty()) {
                    lines.add(new Line("", false));
                }
                lines.add(new Line(header, text.isBoldHeader()));

                for (ReadMeConfigData.ReadMeDescription description : text.getDescriptions()) {
                    lines.add(new Line(localize(localizationMap, description.getText()), false));
                }
            }
        }

        return lines;
    }

    /** Writes each line into column A, applying the bold style to block headers. */
    private void writeLines(XSSFWorkbook workbook, Sheet sheet, List<Line> lines) {
        CellStyle bodyStyle = createInstructionStyle(workbook, false);
        CellStyle headerStyle = createInstructionStyle(workbook, true);

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);

            Row row = sheet.createRow(i);
            Cell cell = row.createCell(0);
            cell.setCellValue(line.text);
            cell.setCellStyle(line.bold ? headerStyle : bodyStyle);

            int wrappedLines = Math.max(1, (int) Math.ceil((double) line.text.length() / MAX_CHARACTERS_PER_LINE));
            row.setHeight((short) (wrappedLines * LINE_HEIGHT_TWIPS));
        }
    }

    /**
     * Instruction cell style: left aligned, wrapped and locked. Locking every cell is what makes the
     * workbook-level protection pass (applied later in ConfigBasedGenerationService) freeze the sheet.
     */
    private CellStyle createInstructionStyle(XSSFWorkbook workbook, boolean bold) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setLocked(true);

        Font font = workbook.createFont();
        font.setBold(bold);
        style.setFont(font);

        return style;
    }

    private String localize(Map<String, String> localizationMap, String key) {
        // Fall back to the raw key when localization is missing, matching how sheet headers behave.
        return LocalizationUtil.getLocalizedMessage(localizationMap, key, key);
    }

    /** One rendered README row. */
    private static final class Line {
        private final String text;
        private final boolean bold;

        private Line(String text, boolean bold) {
            this.text = text != null ? text : "";
            this.bold = bold;
        }
    }
}
