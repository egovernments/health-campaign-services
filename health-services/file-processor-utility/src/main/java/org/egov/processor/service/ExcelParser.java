package org.egov.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import static org.egov.processor.web.models.Operation.OperatorEnum.MINUS;
import static org.egov.processor.web.models.Operation.OperatorEnum.PERCENT;
import static org.egov.processor.web.models.Operation.OperatorEnum.PLUS;
import static org.egov.processor.web.models.Operation.OperatorEnum.SLASH;
import static org.egov.processor.web.models.Operation.OperatorEnum.STAR;
import static org.egov.processor.web.models.Operation.OperatorEnum._U;

@Slf4j
@Service
public class ExcelParser implements FileParser {

    private ObjectMapper objectMapper;

    private ParsingUtil parsingUtil;

    private FilestoreUtil filestoreUtil;

    public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
    }

    @Override
    public Object parseFileData(PlanConfiguration planConfig, String fileStoreId, String attributeToFetch) {

        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        File file = parsingUtil.convertByteArrayToFile(byteArray, "excel");

        // Check if the GeoJSON file exists
        if (file == null || !file.exists()) log.info("FILE NOT FOUND - ");
        else log.info("File exists at - " + file.getAbsolutePath());

        try
        {
            Workbook workbook = new XSSFWorkbook(file);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            Map<String, Integer> mapOfColumnNameandIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
            List<String> columnNames = new ArrayList<>(mapOfColumnNameandIndex.keySet());
            List<ResourceMapping> resourceMappingList = planConfig.getResourceMapping();

            // Validate the attribute mapping
            boolean isValid = parsingUtil.validateAttributeMapping(columnNames, resourceMappingList, fileStoreId);
            if (isValid) {
                log.info("Attribute mapping is valid.");
            } else {
                log.info("Attribute mapping is invalid.");
            }

            //TODO: Figure out where you will get the column to calculate population sum
            int populationColumnIndex = mapOfColumnNameandIndex.get("tp1");
//            double populationSum = sumColumnValues(sheet, dataFormatter, populationColumnIndex);

        }
        catch (IOException | InvalidFormatException e)
        {
            log.error(e.getMessage());
        }
        return null;
    }

    private double sumColumnValues(Sheet sheet, DataFormatter dataFormatter, int columnIndex) {
        double sum = 0.0;
        //TODO figure out how to skip the heirarchy related rows
        for (int n = 4; n < sheet.getPhysicalNumberOfRows(); n++) {
            Row row = sheet.getRow(n);
            if (row != null) {
                Cell cell = row.getCell(columnIndex);
                if (cell != null) {
                    String cellValue = dataFormatter.formatCellValue(cell);
                    try {
                        Double value = Double.parseDouble(cellValue);
                        sum += value;
                    } catch (NumberFormatException e) {
                        // Ignore if the cell value is not a valid number
                        log.error("NumberFormatException");
                    }
                }
            }
        }
        return sum;
    }

    @Override
    public BigDecimal fetchPopulationData(PlanConfiguration planConfiguration, String fileStoreId) {
        return null;
    }
}