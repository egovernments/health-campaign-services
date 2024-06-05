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
import org.egov.processor.util.PlanUtil;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.DateUtil;


@Slf4j
@Service
public class ExcelParser implements FileParser {

    private ObjectMapper objectMapper;

    private ParsingUtil parsingUtil;

    private FilestoreUtil filestoreUtil;

    private CalculationUtil calculationUtil;
    
    private PlanUtil planUtil;

    private MdmsUtil mdmsUtil;

    public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil, CalculationUtil calculationUtil,PlanUtil planUtil) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
        this.calculationUtil = calculationUtil;
        this.planUtil = planUtil;
    }

    /**
     * Parses the file data based on the provided plan configuration and file store ID.
     *
     * @param planConfig   The plan configuration containing mapping and operation details.
     * @param fileStoreId  The file store ID of the Excel file to be parsed.
     * @return The file store ID of the uploaded updated file, or null if an error occurred.
     */
    @Override
    public Object parseFileData(PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        File file = parsingUtil.convertByteArrayToFile(byteArray, "excel");

        if (file == null || !file.exists()) {
            log.info("FILE NOT FOUND");
            return null;
        }

        return processExcelFile(planConfigurationRequest, file, fileStoreId);
    }

    /**
     * Processes the Excel file, updating it with the calculated results and uploading the updated file.
     *
     * @param planConfigurationRequest The plan configuration request containing mapping and operation details.
     * @param convertedFile       The Excel file to be processed.
     * @return The file store ID of the uploaded updated file, or null if an error occurred.
     */
    private String processExcelFile(PlanConfigurationRequest planConfigurationRequest, File convertedFile, org.egov.processor.web.models.File file) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        try (Workbook workbook = new XSSFWorkbook(convertedFile)) {
            DataFormatter dataFormatter = new DataFormatter();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
                List<String> columnNamesList = mapOfColumnNameAndIndex.keySet().stream().toList();

                parsingUtil.validateColumnNames(columnNamesList, planConfig, file.getFilestoreId());

                // Assuming processRows handles processing for each sheet
                processRows(planConfigurationRequest, sheet, dataFormatter, file);
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
    private void processRows(PlanConfigurationRequest planConfigurationRequest, Sheet sheet, DataFormatter dataFormatter, org.egov.processor.web.models.File file) throws IOException {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        Object mdmsData = mdmsUtil.fetchMdmsData(planConfigurationRequest.getRequestInfo(), planConfigurationRequest.getPlanConfiguration().getTenantId());
        List<Map<String, Object>> list = mdmsUtil.filterMasterData(mdmsData.toString(), file.getInputFileType(), file.getTemplateIdentifier());
        log.info(list.toString());

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
            
            planUtil.create(planConfigurationRequest,feature,resultMap,mappedValues, assumptionValueMap);
            //TODO: remove after testing
            printRow(sheet, row);
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
//        System.out.println("Feature Node ---- > " + featureNode);
        return featureNode;
    }

    public void printRow(Sheet sheet, Row row) {
        System.out.print("Row -> ");
        for (Cell cell : row) {
            int columnIndex = cell.getColumnIndex();
            String columnName = sheet.getRow(0).getCell(columnIndex).getStringCellValue();
            System.out.print("Column " + columnName + " - ");
            switch (cell.getCellType()) {
                case STRING:
                    System.out.print(cell.getStringCellValue() + "\t");
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        System.out.print(cell.getDateCellValue() + "\t");
                    } else {
                        System.out.print(cell.getNumericCellValue() + "\t");
                    }
                    break;
                case BOOLEAN:
                    System.out.print(cell.getBooleanCellValue() + "\t");
                    break;
                case FORMULA:
                    System.out.print(cell.getCellFormula() + "\t");
                    break;
                case BLANK:
                    System.out.print("<blank>\t");
                    break;
                default:
                    System.out.print("<unknown>\t");
                    break;
            }
        }
        System.out.println(); // Move to the next line after printing the row
    }

    public void validateRows(Row row, Sheet sheet, Map<String, Integer> columnIndexMap, Map<String, String> mappedValues)
    {

        for (Cell cell : row) {
            // Get the column name from the sheet data
            String sheetColumnName = sheet.getRow(0).getCell(cell.getColumnIndex()).getStringCellValue();

            // Check if the column name exists in the mapping
            if (mappedValues.containsKey(sheetColumnName)) {
                // Get the corresponding master data column name
                String masterDataColumnName = mappedValues.get(sheetColumnName);

                // Validate the data type of the cell against the master data
                validateCellType(cell, masterDataColumnName, masterData);
            }
        }
    }

    private static void validateCellType(Cell cell, String masterDataColumnName, Map<String, Map<String, Object>> masterData) {
        Map<String, Object> columnProperties = masterData.get(masterDataColumnName);
        String expectedType = (String) columnProperties.get("type");

        // Check the data type of the cell against the expected type
        switch (expectedType) {
            case "string":
                if (cell.getCellType() != Cell.CELL_TYPE_STRING) {
                    throw new IllegalArgumentException(
                            String.format("Invalid data type for column '%s'. Expected 'string' but got '%s'.",
                                    cell.getAddress(), cell.getCellType()));
                }
                break;
            case "number":
                if (cell.getCellType() != Cell.CELL_TYPE_NUMERIC) {
                    throw new IllegalArgumentException(
                            String.format("Invalid data type for column '%s'. Expected 'number' but got '%s'.",
                                    cell.getAddress(), cell.getCellType()));
                }
                break;
            // Add more cases for other data types as needed
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported data type '%s' in master data.", expectedType));
        }
    }
}