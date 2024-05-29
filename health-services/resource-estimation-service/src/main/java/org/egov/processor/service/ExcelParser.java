package org.egov.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.CampaignIntegrationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.util.PlanUtil;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.processor.web.models.campaignManager.Boundary;
import org.egov.processor.web.models.campaignManager.CampaignResources;
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
    
    private CampaignIntegrationUtil campaignIntegrationUtil;
    
    private Configuration config;

    public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil, CalculationUtil calculationUtil,PlanUtil planUtil, CampaignIntegrationUtil campaignIntegrationUtil, Configuration config) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
        this.calculationUtil = calculationUtil;
        this.planUtil = planUtil;
        this.campaignIntegrationUtil = campaignIntegrationUtil;
        this.config = config;
    }

    /**
     * Parses file data, extracts information from the file, and processes it.
     *
     * @param planConfigurationRequest The plan configuration request containing necessary information for parsing the file.
     * @param fileStoreId The ID of the file in the file store.
     * @param campaignResponse The response object to be updated with parsed data.
     * @return The parsed and processed data.
     */
    @Override
    public Object parseFileData(PlanConfigurationRequest planConfigurationRequest, String fileStoreId, Object campaignResponse) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), fileStoreId);
        File file = parsingUtil.convertByteArrayToFile(byteArray, "excel");

        if (file == null || !file.exists()) {
            log.info("FILE NOT FOUND");
            return null;
        }

        return processExcelFile(planConfigurationRequest, file, fileStoreId,campaignResponse);
    }

    /**
     * Processes an Excel file, extracts data, and updates campaign details and resources.
     *
     * @param planConfigurationRequest The plan configuration request containing necessary information for processing the file.
     * @param file The Excel file to be processed.
     * @param fileStoreId The ID of the file in the file store.
     * @param campaignResponse The response object to be updated with processed data.
     * @return The ID of the uploaded file.
     */
    private String processExcelFile(PlanConfigurationRequest planConfigurationRequest, File file, String fileStoreId,Object campaignResponse) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        try (Workbook workbook = new XSSFWorkbook(file)) {
        	List<Boundary> campaignBoundaryList = new ArrayList<>();
			List<CampaignResources> campaignResourcesList = new ArrayList<>();
            DataFormatter dataFormatter = new DataFormatter();

            for (int sheetNumber = 0; sheetNumber < workbook.getNumberOfSheets(); sheetNumber++) {
                Sheet sheet = workbook.getSheetAt(sheetNumber);
                Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
                List<String> columnNamesList = mapOfColumnNameAndIndex.keySet().stream().toList();

                parsingUtil.validateColumnNames(columnNamesList, planConfig, fileStoreId);

                // Assuming processRows handles processing for each sheet
                processRows(planConfigurationRequest, sheet, dataFormatter,fileStoreId, campaignResponse,
						campaignBoundaryList, campaignResourcesList);
            }

            String uploadedFileId = uploadConvertedFile(
					new File(convertWorkbookToXls(workbook).getParent(), ServiceConstants.FILE_NAME),
					planConfig.getTenantId());

            if(config.isIntegrateWithAdminConsole()) {
			campaignIntegrationUtil.updateCampaignResources(uploadedFileId, campaignResourcesList);
			campaignIntegrationUtil.updateCampaignDetails(planConfigurationRequest, campaignResponse,
					campaignBoundaryList, campaignResourcesList);
            }
			return uploadedFileId;
        } catch (IOException | InvalidFormatException e) {
            log.error("Error processing Excel file: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Processes rows of data in an Excel sheet, performs calculations, updates campaign boundaries, and creates plans.
     *
     * @param planConfigurationRequest The plan configuration request containing necessary information for processing the rows.
     * @param sheet The Excel sheet containing the data to be processed.
     * @param dataFormatter The data formatter for formatting cell values.
     * @param fileStoreId The ID of the file in the file store.
     * @param campaignResponse The response object to be updated with processed data.
     * @param campaignBoundaryList The list of campaign boundaries to be updated.
     * @param campaignResourcesList The list of campaign resources to be updated.
     * @throws IOException If an I/O error occurs.
     */
    private void processRows(PlanConfigurationRequest planConfigurationRequest, Sheet sheet, DataFormatter dataFormatter, String fileStoreId,
			Object campaignResponse, List<Boundary> campaignBoundaryList,List<CampaignResources> campaignResourcesList) throws IOException {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
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
            if(config.isIntegrateWithAdminConsole()) 
            campaignIntegrationUtil.updateCampaignBoundary(planConfig,feature,assumptionValueMap,mappedValues,mapOfColumnNameAndIndex,campaignBoundaryList,resultMap);
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
}