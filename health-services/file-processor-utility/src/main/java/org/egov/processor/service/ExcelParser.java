package org.egov.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.mbeans.SparseUserDatabaseMBean;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import org.apache.tika.Tika;


@Slf4j
@Service
public class ExcelParser implements FileParser {

    private ObjectMapper objectMapper;

    private ParsingUtil parsingUtil;

    private FilestoreUtil filestoreUtil;

    private CalculationUtil calculationUtil;

    public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil, CalculationUtil calculationUtil) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
        this.calculationUtil = calculationUtil;
    }

    @Override
    public Object parseFileData(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        File file = parsingUtil.convertByteArrayToFile(byteArray, "excel");

        // Check if the Excel file exists
        if (file == null || !file.exists()) {
            log.info("FILE NOT FOUND - ");
            return null;
        } else {
            log.info("File exists at - " + file.getAbsolutePath());
        }

        String updatedFileStoreId = null;
        try (Workbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();

            // Create a temporary file to store the updated Excel data
            File tempFile = File.createTempFile("updatedExcel", ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                // Iterate over each row in the Excel sheet
                for (Row row : sheet) {
                    // Skip the header row
                    if (row.getRowNum() == 0) {
                        continue;
                    }

                    // Assuming these methods are implemented to populate the mappedValues and assumptionValueMap
                    Map<String, BigDecimal> resultMap = new HashMap<>();
                    Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
                            .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
                    Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());
                    Map<String, Integer> mapOfColumnNameandIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

                    // Process each row
                    JsonNode feature = processRow(row, dataFormatter, mapOfColumnNameandIndex);
                    int columnIndex = row.getLastCellNum(); // Get the index of the last cell in the row

                    for (Operation operation : planConfig.getOperations()) {
                        String input = operation.getInput();
                        String inputFromMapping = mappedValues.get(input);
                        BigDecimal inputValue = getInputValue(resultMap, feature, input, inputFromMapping);

                        BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());

                        BigDecimal result = calculationUtil.calculateResult(inputValue, operation.getOperator(), assumptionValue);

                        String output = operation.getOutput();
                        resultMap.put(output, result);

                        Cell cell = row.createCell(columnIndex++); // Append cell to the end of the row
                        cell.setCellValue(result.doubleValue()); // Set the cell value to the result

                        // Set the column name for the new cell
                        if (row.getRowNum() == 1) { // Assuming row 1 is the header row
                            Cell headerCell = sheet.getRow(0).createCell(row.getLastCellNum() - 1);
                            headerCell.setCellValue(output); // Set the header cell value to the output column name
                        }

                    }
                }
            }

            // Convert the workbook to XLS format and upload
            File convertedFile = convertWorkbookToXls(workbook);
            updatedFileStoreId = null;
            if (convertedFile != null) {
                updatedFileStoreId = filestoreUtil.uploadFile(convertedFile, planConfig.getTenantId());
            }
        } catch (IOException | InvalidFormatException e) {
            log.error("Error processing Excel file: {}", e.getMessage());
        }

        return updatedFileStoreId;
    }


    private File convertWorkbookToXls(Workbook workbook) {
        try {
            // Create a temporary file for the output XLS file
            File outputFile = File.createTempFile("output", ".xls");

            // Write the XLS file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
                System.out.println("XLS file saved successfully.");
                return outputFile;
            } catch (IOException e) {
                System.err.println("Error saving XLS file: " + e.getMessage());
                return null;
            }
        } catch (IOException e) {
            System.err.println("Error converting workbook to XLS: " + e.getMessage());
            return null;
        }
    }

    private BigDecimal getInputValue(Map<String, BigDecimal> resultMap, JsonNode feature, String input, String inputFromMapping) {
        if (resultMap.containsKey(input)) {
            return resultMap.get(input);
        } else {
            String columnName = inputFromMapping; // Assuming input is the column name
            String cellValue = feature.get("properties").get(columnName).asText();
            return new BigDecimal(cellValue);
        }
    }

    private JsonNode processRow(Row row, DataFormatter dataFormatter, Map<String, Integer> columnIndexMap) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode featureNode = objectMapper.createObjectNode();
        ObjectNode propertiesNode = featureNode.putObject("properties");

        // Iterate over each entry in the columnIndexMap
        for (Map.Entry<String, Integer> entry : columnIndexMap.entrySet()) {
            String columnName = entry.getKey();
            Integer columnIndex = entry.getValue();

            // Get the cell value from the row based on the columnIndex
            Cell cell = row.getCell(columnIndex);
            String cellValue = dataFormatter.formatCellValue(cell);

            // Add the columnName and cellValue to the propertiesNode
            propertiesNode.put(columnName, cellValue);
        }
        System.out.println("Feature Node ---- > " + featureNode);
        return featureNode;
    }

    //            Map<String, Integer> mapOfColumnNameandIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
//            List<String> columnNames = new ArrayList<>(mapOfColumnNameandIndex.keySet());
//            List<ResourceMapping> resourceMappingList = planConfig.getResourceMapping();

//            // Validate the attribute mapping
//            boolean isValid = parsingUtil.validateAttributeMapping(columnNames, resourceMappingList, fileStoreId);
//            if (isValid) {
//                log.info("Attribute mapping is valid.");
//            } else {
//                log.info("Attribute mapping is invalid.");
//            }



}