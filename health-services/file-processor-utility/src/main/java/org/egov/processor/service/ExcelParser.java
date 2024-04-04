package org.egov.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExcelParser implements FileParser {

    private ObjectMapper objectMapper;

    private ParsingUtil parsingUtil;

    public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
    }


    public void parseFileData(PlanConfiguration planConfig) {
        File file = new File("Microplan/valid/Population/PopulationValid.xlsx");

        // Check if the GeoJSON file exists
        if (file.exists()) log.info("File exists at - " + file.getAbsolutePath());
        else log.info("FILE NOT FOUND - " + file.getAbsolutePath());

        try
        {
            Workbook workbook = new XSSFWorkbook(file);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            List<String> columnNames = parsingUtil.getAttributeNameFromExcel(sheet);
            List<ResourceMapping> resourceMappingList = planConfig.getResourceMapping();

            // Validate the attribute mapping
            boolean isValid = parsingUtil.validateAttributeMapping(columnNames, resourceMappingList);
            if (isValid) {
                log.info("Attribute mapping is valid.");
            } else {
                log.info("Attribute mapping is invalid.");
            }

            for (int n = 1; n < sheet.getPhysicalNumberOfRows(); n++) {
                Row row = sheet.getRow(n);
                if (row != null) {
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i);
                        if (cell != null) {
                            String cellValue = dataFormatter.formatCellValue(cell);
//                          System.out.println("Row: " + n + ", Column: " + i + ", Value: " + cellValue);
                        }
                    }
                }

            }
        }
        catch (IOException | InvalidFormatException e)
        {
            log.error(e.getMessage());
        }
    }

}