package org.egov.processor.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.util.BoundaryUtil;
import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.CampaignIntegrationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.MdmsUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.util.PlanUtil;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfiguration.StatusEnum;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.processor.web.models.boundary.BoundarySearchResponse;
import org.egov.processor.web.models.boundary.EnrichedBoundary;
import org.egov.processor.web.models.campaignManager.Boundary;
import org.egov.processor.web.models.campaignManager.CampaignResources;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

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

	private MdmsUtil mdmsUtil;

	private BoundaryUtil boundaryUtil;

	public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil,
			CalculationUtil calculationUtil, PlanUtil planUtil, CampaignIntegrationUtil campaignIntegrationUtil,
			Configuration config, MdmsUtil mdmsUtil, BoundaryUtil boundaryUtil) {
		this.objectMapper = objectMapper;
		this.parsingUtil = parsingUtil;
		this.filestoreUtil = filestoreUtil;
		this.calculationUtil = calculationUtil;
		this.planUtil = planUtil;
		this.campaignIntegrationUtil = campaignIntegrationUtil;
		this.config = config;
		this.mdmsUtil = mdmsUtil;
		this.boundaryUtil = boundaryUtil;
	}

	/**
	 * Parses file data, extracts information from the file, and processes it.
	 *
	 * @param planConfigurationRequest The plan configuration request containing
	 *                                 necessary information for parsing the file.
	 * @param fileStoreId              The ID of the file in the file store.
	 * @param campaignResponse         The response object to be updated with parsed
	 *                                 data.
	 * @return The parsed and processed data.
	 */
	@Override
	public Object parseFileData(PlanConfigurationRequest planConfigurationRequest, String fileStoreId,
			Object campaignResponse) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), fileStoreId);
		File file = parsingUtil.convertByteArrayToFile(byteArray, ServiceConstants.FILE_EXTENSION);

		if (file == null || !file.exists()) {
			log.info("FILE NOT FOUND");
			return null;
		}

		return processExcelFile(planConfigurationRequest, file, fileStoreId, campaignResponse);
	}

	/**
	 * Processes an Excel file, extracts data, and updates campaign details and
	 * resources.
	 *
	 * @param planConfigurationRequest The plan configuration request containing
	 *                                 necessary information for processing the
	 *                                 file.
	 * @param file                     The Excel file to be processed.
	 * @param fileStoreId              The ID of the file in the file store.
	 * @param campaignResponse         The response object to be updated with
	 *                                 processed data.
	 * @return The ID of the uploaded file.
	 */
	private String processExcelFile(PlanConfigurationRequest planConfigurationRequest, File file, String fileStoreId,
			Object campaignResponse) {
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
				processRows(planConfigurationRequest, sheet, dataFormatter, fileStoreId, campaignResponse,
						campaignBoundaryList, campaignResourcesList);
			}

			File fileToUpload = convertWorkbookToXls(workbook);
			String uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());

			if (config.isIntegrateWithAdminConsole()) {
				campaignIntegrationUtil.updateCampaignResources(uploadedFileStoreId, campaignResourcesList,
						fileToUpload.getName());
				campaignIntegrationUtil.updateCampaignDetails(planConfigurationRequest, campaignResponse,
						campaignBoundaryList, campaignResourcesList);
			}
			return uploadedFileStoreId;
		} catch (IOException | InvalidFormatException e) {
			log.error("Error processing Excel file: {}", e.getMessage());
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					"Error processing Excel file");
		}
	}

	/**
	 * Processes rows of data in an Excel sheet, performs calculations, updates
	 * campaign boundaries, and creates plans.
	 *
	 * @param planConfigurationRequest The plan configuration request containing
	 *                                 necessary information for processing the
	 *                                 rows.
	 * @param sheet                    The Excel sheet containing the data to be
	 *                                 processed.
	 * @param dataFormatter            The data formatter for formatting cell
	 *                                 values.
	 * @param fileStoreId              The ID of the file in the file store.
	 * @param campaignResponse         The response object to be updated with
	 *                                 processed data.
	 * @param campaignBoundaryList     The list of campaign boundaries to be
	 *                                 updated.
	 * @param campaignResourcesList    The list of campaign resources to be updated.
	 * @throws IOException If an I/O error occurs.
	 */
	private void processRows(PlanConfigurationRequest planConfigurationRequest, Sheet sheet,
			DataFormatter dataFormatter, String fileStoreId, Object campaignResponse,
			List<Boundary> campaignBoundaryList, List<CampaignResources> campaignResourcesList) throws IOException {
		CampaignResponse campaign = null;
		campaign = objectMapper.convertValue(campaignResponse, CampaignResponse.class);
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		Object mdmsData = mdmsUtil.fetchMdmsData(planConfigurationRequest.getRequestInfo(),
				planConfigurationRequest.getPlanConfiguration().getTenantId());
		org.egov.processor.web.models.File file = planConfig.getFiles().stream()
				.filter(f -> f.getFilestoreId().equalsIgnoreCase(fileStoreId)).findFirst().get();
		Map<String, Object> attributenamevsdatatypemap = mdmsUtil.filterMasterData(mdmsData.toString(), file.getInputFileType(),
				file.getTemplateIdentifier(), campaign.getCampaign().get(0).getProjectType());
		BoundarySearchResponse boundarySearchResponse = boundaryUtil.search(planConfig.getTenantId(),
				campaign.getCampaign().get(0).getHierarchyType(), planConfigurationRequest);
		List<String> boundaryList = new ArrayList<>();
		List<String> boundaryCodeList = getAllBoundaryPresentforHierarchyType(
				boundarySearchResponse.getTenantBoundary().get(0).getBoundary(), boundaryList);
		// log.info(list.toString());
		Row firstRow = null;
		for (Row row : sheet) {
			if (row.getRowNum() == 0) {
				firstRow = row;
				continue;
			}

			Map<String, BigDecimal> resultMap = new HashMap<>();
			Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
					.filter(f -> f.getFilestoreId().equals(fileStoreId))
					.collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
			Map<String, BigDecimal> assumptionValueMap = calculationUtil
					.convertAssumptionsToMap(planConfig.getAssumptions());
			Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

			Integer indexOfBCode = campaignIntegrationUtil.getIndexOfBoundaryCode(0,
					campaignIntegrationUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);
			validateRows(indexOfBCode, row, firstRow, attributenamevsdatatypemap, mappedValues, mapOfColumnNameAndIndex,
					planConfigurationRequest, boundaryCodeList);
			JsonNode feature = createFeatureNodeFromRow(row, dataFormatter, mapOfColumnNameAndIndex);
			int columnIndex = row.getLastCellNum(); // Get the index of the last cell in the row

			for (Operation operation : planConfig.getOperations()) {
				BigDecimal result = calculationUtil.calculateResult(operation, feature, mappedValues,
						assumptionValueMap, resultMap);
				String output = operation.getOutput();
				resultMap.put(output, result);

				Cell cell = row.createCell(columnIndex++);
				cell.setCellValue(result.doubleValue());

				if (row.getRowNum() == 1) {
					Cell headerCell = sheet.getRow(0).createCell(row.getLastCellNum() - 1);
					headerCell.setCellValue(output);
				}
			}
			if (config.isIntegrateWithAdminConsole())
				campaignIntegrationUtil.updateCampaignBoundary(planConfig, feature, assumptionValueMap, mappedValues,
						mapOfColumnNameAndIndex, campaignBoundaryList, resultMap);
			planUtil.create(planConfigurationRequest, feature, resultMap, mappedValues);
			// TODO: remove after testing
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
	 * @param row            The row in the Excel sheet.
	 * @param dataFormatter  The data formatter for formatting cell values.
	 * @param columnIndexMap The mapping of column names to column indices.
	 * @return The JSON feature node representing the row.
	 */
	private JsonNode createFeatureNodeFromRow(Row row, DataFormatter dataFormatter,
			Map<String, Integer> columnIndexMap) {
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
			// String columnName = sheet.getRow(0).getCell(columnIndex).toString();
			// System.out.print("Column " + columnName + " - ");
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

	/**
	 * Validates the data in a row.
	 * 
	 * @param indexOfBCode             The index of the "BCode" column in the row.
	 * @param row                      The row containing the data to be validated.
	 * @param columnHeaderRow          The row containing the column headers.
	 * @param attributenamevsdatatypemap             Map containing data types from external
	 *                                 source (MDMS).
	 * @param mappedValues             Map containing mapped values.
	 * @param mapOfColumnNameAndIndex  Map containing column names and their
	 *                                 corresponding indices.
	 * @param planConfigurationRequest Object representing the plan configuration
	 *                                 request.
	 * @throws CustomException if the input data is not valid or if a custom
	 *                         exception occurs.
	 */
	public void validateRows(Integer indexOfBCode, Row row, Row columnHeaderRow, Map<String, Object> attributenamevsdatatypemap,
			Map<String, String> mappedValues, Map<String, Integer> mapOfColumnNameAndIndex,
			PlanConfigurationRequest planConfigurationRequest, List<String> boundaryCodeList) {

		try {
			validateTillBoundaryCode(indexOfBCode, row, columnHeaderRow);
			validateAttributes(attributenamevsdatatypemap, mappedValues, mapOfColumnNameAndIndex, row, columnHeaderRow, indexOfBCode,
					boundaryCodeList);
		} catch (JsonProcessingException e) {
			log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1));
			planConfigurationRequest.getPlanConfiguration().setStatus(StatusEnum.INVALID_DATA);
			planUtil.update(planConfigurationRequest);
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum());
		} catch (CustomException customException) {
			log.info(customException.toString());
			planConfigurationRequest.getPlanConfiguration().setStatus(StatusEnum.INVALID_DATA);
			planUtil.update(planConfigurationRequest);
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					customException.getMessage());
		}
	}

	/**
	 * Validates the data in columns from "BCode" column.
	 * 
	 * @param attributenamevsdatatypemap            Map containing data types from an external
	 *                                source (MDMS).
	 * @param mappedValues            Map containing mapped values.
	 * @param mapOfColumnNameAndIndex Map containing column names and their
	 *                                corresponding indices.
	 * @param row                     The row containing the data to be validated.
	 * @param columnHeaderRow         The row containing the column headers.
	 * @param indexOfBCode            The index of the "BCode" column in the row.
	 * @throws JsonMappingException    if there's an issue mapping JSON.
	 * @throws JsonProcessingException if there's an issue processing JSON.
	 */
	private void validateAttributes(Map<String, Object> attributenamevsdatatypemap, Map<String, String> mappedValues,
			Map<String, Integer> mapOfColumnNameAndIndex, Row row, Row columnHeaderRow, Integer indexOfBCode,
			List<String> boundaryCodeList) throws JsonMappingException, JsonProcessingException {
		for (int j = indexOfBCode; j < mapOfColumnNameAndIndex.size(); j++) {
			Cell cell = row.getCell(j);
			Cell columnName = columnHeaderRow.getCell(j);
			String name = findByValue(mappedValues, columnName.getStringCellValue());
			String value;
			if (attributenamevsdatatypemap.containsKey(name)) {
				value = attributenamevsdatatypemap.get(name).toString();
				switch (cell.getCellType()) {
				case STRING:
					String cellValue = cell.getStringCellValue();
					if (j == indexOfBCode && !boundaryCodeList.contains(cellValue)) {
						log.info("Boundary Code is not present in search for row " + (row.getRowNum() + 1)
								+ " and cell/column " + columnName);
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								"Boundary Code is not present in search for row " + (row.getRowNum() + 1)
										+ " and cell/column " + columnName);
					}
					// "^[a-zA-Z0-9 .,()-]+$"
					if (cellValue != null && !cellValue.isEmpty()
							&& cellValue.matches(ServiceConstants.VALIDATE_STRING_REGX)) {
						continue;
					} else {
						log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell/column "
								+ columnName);
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " and cell " + columnName);
					}
				case NUMERIC:
					String numricValue = Double.toString(cell.getNumericCellValue());
					// "^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$"
					if (numricValue != null && !numricValue.isEmpty()
							&& numricValue.matches(ServiceConstants.VALIDATE_NUMBER_REGX)) {
						continue;
					} else {
						log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell/column "
								+ columnName);
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " and cell " + columnName);
					}
				case BOOLEAN:
					Boolean booleanvalue = cell.getBooleanCellValue();
					// "^(?i)(true|false)$"
					if (booleanvalue != null && !booleanvalue.toString().isEmpty()
							&& booleanvalue.toString().matches(ServiceConstants.VALIDATE_BOOLEAN_REGX)) {
						continue;
					} else {
						log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell/column "
								+ columnName);
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " and cell " + columnName);
					}
				default:
					throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
							ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell " + columnName);
				}
			}
		}
	}

	/**
	 * Validates the data in columns up to the specified index of the "BCode"
	 * column.
	 * 
	 * @param indexOfBCode    The index of the "BCode" column in the row.
	 * @param row             The row containing the data to be validated.
	 * @param columnHeaderRow The row containing the column headers.
	 */
	private void validateTillBoundaryCode(Integer indexOfBCode, Row row, Row columnHeaderRow) {
		for (int j = 0; j <= indexOfBCode - 1; j++) {
			Cell cell = row.getCell(j);
			if (cell != null && !cell.getCellType().name().equals("BLANK")) {
				String cellValue = cell.getStringCellValue();
				if (!cellValue.isBlank()) {
					if (cellValue != null && !cellValue.isEmpty()
							&& cellValue.matches(ServiceConstants.VALIDATE_STRING_REGX)) {
						continue;
					} else {
						log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1)
								+ " and cell/column number " + (j + 1));
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " and cell "
										+ columnHeaderRow.getCell(j));
					}
				}
			}
		}
	}

	/**
	 * Finds the key associated with a given value in a map.
	 * 
	 * @param map   The map to search.
	 * @param value The value to search for.
	 * @return The key associated with the specified value, or {@code null} if not
	 *         found.
	 */
	public String findByValue(Map<String, String> map, String value) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (entry.getValue().equals(value)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public List<String> getAllBoundaryPresentforHierarchyType(List<EnrichedBoundary> boundaries,
			List<String> boundaryList) {
		for (EnrichedBoundary boundary : boundaries) {
			boundaryList.add(boundary.getCode());
			// Recursively check children if they exist
			if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
				getAllBoundaryPresentforHierarchyType(boundary.getChildren(), boundaryList);
			}
		}
		return boundaryList;
	}
}