package org.egov.processor.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.egov.processor.web.models.LocaleResponse;
import org.egov.processor.web.models.PlanConfigurationRequest;

public class OutputEstimationGenerationUtil {

    private LocaleUtil localeUtil;

    private ParsingUtil parsingUtil;

    public OutputEstimationGenerationUtil(LocaleUtil localeUtil, ParsingUtil parsingUtil) {
        this.localeUtil = localeUtil;
        this.parsingUtil = parsingUtil;
    }

    public void processOutputFile(Workbook workbook, PlanConfigurationRequest request) {
        LocaleResponse localeResponse = localeUtil.searchLocale(request);
        //removing readme sheet
        for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
            Sheet sheet = workbook.getSheetAt(i);
            if (!parsingUtil.isSheetAllowedToProcess(request, sheet.getSheetName(), localeResponse)) {
                workbook.removeSheetAt(i);
            }
        }
    }

    public void processSheetForHeaderLocalization(Sheet sheet, LocaleResponse localeResponse) {
        for (Row row : sheet) {
            if (parsingUtil.isRowEmpty(row)) continue;

            if (row.getRowNum() == 0) {

            }
        }

    }
}
