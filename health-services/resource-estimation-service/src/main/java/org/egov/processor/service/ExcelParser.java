package org.egov.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;


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

    /**
     * Parses the file data based on the provided plan configuration and file store ID.
     *
     * @param planConfig   The plan configuration containing mapping and operation details.
     * @param fileStoreId  The file store ID of the Excel file to be parsed.
     * @return The file store ID of the uploaded updated file, or null if an error occurred.
     */
    @Override
    public Object parseFileData(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        File file = parsingUtil.convertByteArrayToFile(byteArray, "excel");

        if (file == null || !file.exists()) {
            log.info("FILE NOT FOUND");
            return null;
        }

        return processExcelFile(planConfig, file, fileStoreId);
    }

    /**
     * Processes the Excel file, updating it with the calculated results and uploading the updated file.
     *
     * @param planConfig The plan configuration containing mapping and operation details.
     * @param file       The Excel file to be processed.
     * @return The file store ID of the uploaded updated file, or null if an error occurred.
     */
    private String processExcelFile(PlanConfiguration planConfig, File file, String fileStoreId) {
        try (Workbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();
            Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
            List<String> columnNamesList = mapOfColumnNameAndIndex.keySet().stream().toList();

            parsingUtil.validateColumnNames(columnNamesList, planConfig, fileStoreId);

            File tempFile = createTempFile("updatedExcel", ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                processRows(planConfig, sheet, dataFormatter, fos);
            }

            return uploadConvertedFile(convertWorkbookToXls(workbook), planConfig.getTenantId());
        } catch (IOException | InvalidFormatException e) {
            log.error("Error processing Excel file: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Processes each row in the Excel sheet, updating it with the calculated results.
     *
     * @param planConfig     The plan configuration containing mapping and operation details.
     * @param sheet          The Excel sheet to process.
     * @param dataFormatter  The data formatter for formatting cell values.
     * @param fos            The file output stream to write the updated Excel data.
     * @throws IOException If an IO error occurs during processing.
     */
    private void processRows(PlanConfiguration planConfig, Sheet sheet, DataFormatter dataFormatter, FileOutputStream fos) throws IOException {
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }

            Map<String, BigDecimal> resultMap = new HashMap<>();
            Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
                    .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
            Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());
            Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

            JsonNode feature = createFeatureNodeFromRow(row, dataFormatter, mapOfColumnNameAndIndex);
            int columnIndex = row.getLastCellNum(); // Get the index of the last cell in the row

            for (Operation operation : planConfig.getOperations()) {
                BigDecimal result = calculationUtil.calculateResult(operation, feature, mappedValues, assumptionValueMap, resultMap);
                String output = operation.getOutput();
                resultMap.put(output, result);

                Cell cell = row.createCell(columnIndex++);
                cell.setCellValue(result.doubleValue());

                if (row.getRowNum() == 1) {
                    Cell headerCell = sheet.getRow(0).createCell(row.getLastCellNum() - 1);
                    headerCell.setCellValue(output);
                }
            }
        }
    }

    /**
     * Uploads the converted XLS file to the file store.
     *
     * @param convertedFile The converted XLS file to upload.
     * @param tenantId      The tenant ID for the file upload.
     * @return The file store ID of the uploaded file, or null if an error occurred.
     */
    private String uploadConvertedFile(File convertedFile, String tenantId) {
        if (convertedFile != null) {
            return filestoreUtil.uploadFile(convertedFile, tenantId);
        }
        return null;
    }

    /**
     * Creates a temporary file with the specified prefix and suffix.
     *
     * @param prefix The prefix for the temporary file.
     * @param suffix The suffix for the temporary file.
     * @return The created temporary file.
     * @throws IOException If an IO error occurs while creating the file.
     */
    private File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix);
    }

    /**
     * Converts the provided workbook to XLS format.
     *
     * @param workbook The workbook to convert.
     * @return The converted XLS file, or null if an error occurred.
     */
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

    /**
     * Creates a JSON feature node from a row in the Excel sheet.
     *
     * @param row           The row in the Excel sheet.
     * @param dataFormatter The data formatter for formatting cell values.
     * @param columnIndexMap The mapping of column names to column indices.
     * @return The JSON feature node representing the row.
     */
    private JsonNode createFeatureNodeFromRow(Row row, DataFormatter dataFormatter, Map<String, Integer> columnIndexMap) {
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

}