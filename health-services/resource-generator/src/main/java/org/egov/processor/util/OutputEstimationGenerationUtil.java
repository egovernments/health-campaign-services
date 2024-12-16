package org.egov.processor.util;

import org.apache.poi.ss.usermodel.*;
import org.egov.processor.web.models.Locale;
import org.egov.processor.web.models.LocaleResponse;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.tracer.model.CustomException;

import java.util.Map;
import java.util.stream.Collectors;

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

        //
        Map<String, String> localizationCodeAndMessageMap = localeResponse.getMessages().stream().collect(Collectors.toMap(Locale::getCode, Locale::getMessage));

        for(Sheet sheet: workbook) {
            processSheetForHeaderLocalization(sheet, localizationCodeAndMessageMap);
        }
    }

    public void processSheetForHeaderLocalization(Sheet sheet, Map<String, String> localizationCodeAndMessageMap) {
        // Fetch the header row from sheet
        Row row = sheet.getRow(0);
        if (parsingUtil.isRowEmpty(row)) throw new CustomException();


        //Iterate from the end, for every cell localize the header value
        for (int i = row.getLastCellNum() - 1; i >= 0; i--) {
            Cell headerColumn = row.getCell(i);

            if (headerColumn == null || headerColumn.getCellType() != CellType.STRING) {
                continue;
            }
            String headerColumnValue = headerColumn.getStringCellValue();

            // Exit the loop if the header column value is not in the localization map
            if (!localizationCodeAndMessageMap.containsKey(headerColumnValue)) {
                break;
            }

            // Update the cell value with the localized message
            headerColumn.setCellValue(localizationCodeAndMessageMap.get(headerColumnValue));
        }

    }
}
