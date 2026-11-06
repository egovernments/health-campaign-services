package org.egov.excelingestion.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.constants.GenerationConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locale resolution for uploaded workbooks: the file's generation locale wins over the request
 * locale, because sheet/column names are matched against names written in that locale.
 */
class WorkbookLocaleResolverTest {

    private final WorkbookLocaleResolver resolver = new WorkbookLocaleResolver();

    private Workbook workbookWithMeta(String generationId, String locale) {
        Workbook wb = new XSSFWorkbook();
        Sheet meta = wb.createSheet(GenerationConstants.META_SHEET_NAME);
        Row row = meta.createRow(GenerationConstants.META_ROW_INDEX);
        if (generationId != null) {
            row.createCell(GenerationConstants.META_GENERATION_ID_CELL_INDEX).setCellValue(generationId);
        }
        if (locale != null) {
            row.createCell(GenerationConstants.META_LOCALE_CELL_INDEX).setCellValue(locale);
        }
        return wb;
    }

    /** The bug being fixed: generated in en_IN, validated from a fr_FR session. */
    @Test
    void stampedLocaleWinsOverDifferentRequestLocale() {
        Workbook wb = workbookWithMeta("gen-1", "en_IN");
        assertEquals("en_IN", resolver.resolveLocale(wb, "fr_FR"));
    }

    @Test
    void stampedLocaleUsedWhenItMatchesRequestLocale() {
        Workbook wb = workbookWithMeta("gen-1", "en_IN");
        assertEquals("en_IN", resolver.resolveLocale(wb, "en_IN"));
    }

    /** Locale is stamped for every generated file, including non-join-mode ones with no generationId. */
    @Test
    void localeResolvedWhenNoGenerationIdPresent() {
        Workbook wb = workbookWithMeta(null, "pt_MZ");
        assertEquals("pt_MZ", resolver.resolveLocale(wb, "en_IN"));
    }

    /** Legacy join-mode file: generationId only, no locale cell. Must not disturb existing behaviour. */
    @Test
    void fallsBackToRequestLocaleForLegacyFileWithOnlyGenerationId() {
        Workbook wb = workbookWithMeta("gen-1", null);
        assertEquals("fr_FR", resolver.resolveLocale(wb, "fr_FR"));
    }

    /** Legacy non-join-mode file: no metadata sheet at all. */
    @Test
    void fallsBackToRequestLocaleWhenNoMetaSheet() {
        Workbook wb = new XSSFWorkbook();
        wb.createSheet("Users");
        assertEquals("fr_FR", resolver.resolveLocale(wb, "fr_FR"));
    }

    @Test
    void fallsBackToRequestLocaleWhenLocaleCellIsBlank() {
        Workbook wb = workbookWithMeta("gen-1", "   ");
        assertEquals("en_IN", resolver.resolveLocale(wb, "en_IN"));
    }
}
