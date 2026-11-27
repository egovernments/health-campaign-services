package org.egov.excelingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.excelingestion.constants.GenerationConstants;
import org.springframework.stereotype.Component;

/**
 * Resolves the locale an uploaded workbook was GENERATED in.
 *
 * <p>Sheet names and column headers are written in the generating locale and are then matched by
 * exact string equality at upload time. So a file generated in one locale and validated under
 * another fails with REQUIRED_SHEET_MISSING even though the file is perfectly valid. The locale is
 * therefore structural identity for these files, not just a message language - it must come from
 * the file, not from the caller's current UI locale.
 *
 * <p>Generation stamps the locale into the hidden {@link GenerationConstants#META_SHEET_NAME} sheet.
 * Files generated before that existed carry no locale cell; for those we fall back to the caller's
 * locale, which is exactly the pre-existing behaviour.
 */
@Component
@Slf4j
public class WorkbookLocaleResolver {

    /**
     * @param workbook the uploaded workbook
     * @param requestLocale locale resolved from the request; used as-is for legacy files
     * @return the locale to use for sheet/header localization; never null unless requestLocale is
     */
    public String resolveLocale(Workbook workbook, String requestLocale) {
        String stampedLocale = readStampedLocale(workbook);
        if (stampedLocale == null || stampedLocale.isEmpty()) {
            log.info("No locale stamped in workbook metadata; using request locale {}", requestLocale);
            return requestLocale;
        }
        if (!stampedLocale.equals(requestLocale)) {
            log.info("Workbook was generated in locale {} but request locale is {}; using {} so sheet "
                    + "and column names match the file", stampedLocale, requestLocale, stampedLocale);
        }
        return stampedLocale;
    }

    /**
     * Reads the generation locale stamped in the hidden metadata sheet.
     *
     * @return the stamped locale, or null when absent (legacy file, or the sheet was stripped)
     */
    private String readStampedLocale(Workbook workbook) {
        Sheet meta = workbook.getSheet(GenerationConstants.META_SHEET_NAME);
        if (meta == null) {
            return null;
        }
        Row row = meta.getRow(GenerationConstants.META_ROW_INDEX);
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(GenerationConstants.META_LOCALE_CELL_INDEX);
        if (cell == null) {
            return null;
        }
        String value = ExcelUtil.getCellValueAsString(cell);
        return value == null ? null : value.trim();
    }
}
