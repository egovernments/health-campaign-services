package org.egov.processor.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.util.BoundaryUtil;
import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.CampaignIntegrationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.LocaleUtil;
import org.egov.processor.util.MdmsUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.util.PlanUtil;
import org.egov.processor.web.models.Locale;
import org.egov.processor.web.models.LocaleResponse;
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
	
	private LocaleUtil localeUtil;

	public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil,
			CalculationUtil calculationUtil, PlanUtil planUtil, CampaignIntegrationUtil campaignIntegrationUtil,
			Configuration config, MdmsUtil mdmsUtil, BoundaryUtil boundaryUtil,LocaleUtil localeUtil) {
		this.objectMapper = objectMapper;
		this.parsingUtil = parsingUtil;
		this.filestoreUtil = filestoreUtil;
		this.calculationUtil = calculationUtil;
		this.planUtil = planUtil;
		this.campaignIntegrationUtil = campaignIntegrationUtil;
		this.config = config;
		this.mdmsUtil = mdmsUtil;
		this.boundaryUtil = boundaryUtil;
		this.localeUtil = localeUtil;
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
			log.error("File not found: {} in tenant: {}", fileStoreId, planConfig.getTenantId());
			throw new CustomException("FileNotFound",
					"The file with ID " + fileStoreId + " was not found in the tenant " + planConfig.getTenantId());
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
			processSheets(planConfigurationRequest, fileStoreId, campaignResponse, planConfig, workbook,
					campaignBoundaryList, campaignResourcesList, dataFormatter);
			String uploadedFileStoreId = uploadFileAndIntegrateCampaign(planConfigurationRequest, campaignResponse,
					planConfig, workbook, campaignBoundaryList, campaignResourcesList);
			return uploadedFileStoreId;
		} catch (FileNotFoundException e) {
			log.error("File not found: {}", e.getMessage());
			throw new CustomException("FileNotFound", "The specified file was not found.");
		} catch (InvalidFormatException e) {
			log.error("Invalid format: {}", e.getMessage());
			throw new CustomException("InvalidFormat", "The file format is not supported.");
		} catch (IOException e) {
			log.error("Error processing Excel file: {}", e);
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					"Error processing Excel file");
		}
	}

	/**
	 * Uploads a converted file and integrates campaign details if configured to do so.
	 * 
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param campaignResponse The response object containing campaign details.
	 * @param planConfig The configuration details specific to the plan.
	 * @param workbook The workbook containing data to be uploaded and integrated.
	 * @param campaignBoundaryList List of boundary objects related to the campaign.
	 * @param campaignResourcesList List of campaign resources to be integrated.
	 * @return The ID of the uploaded file in the file store.
	 */
	private String uploadFileAndIntegrateCampaign(PlanConfigurationRequest planConfigurationRequest,
			Object campaignResponse, PlanConfiguration planConfig, Workbook workbook,
			List<Boundary> campaignBoundaryList, List<CampaignResources> campaignResourcesList) {
		File fileToUpload = null;
		try {
			fileToUpload = convertWorkbookToXls(workbook);
			String uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());

			if (config.isIntegrateWithAdminConsole()) {
				campaignIntegrationUtil.updateCampaignResources(uploadedFileStoreId, campaignResourcesList,
						fileToUpload.getName());

				campaignIntegrationUtil.updateCampaignDetails(planConfigurationRequest, campaignResponse,
						campaignBoundaryList, campaignResourcesList);
			}
			return uploadedFileStoreId;
		} finally {
			try {
			if (fileToUpload != null && !fileToUpload.delete()) {
				log.warn("Failed to delete temporary file: " + fileToUpload.getPath());
			}
			}catch(SecurityException e) {
				 log.error("Security exception when attempting to delete file: " + e.getMessage());
			}
		}
	}

	/**
	 * Processes each sheet in the workbook for plan configuration data.
	 * Validates column names, processes rows, and integrates campaign details.
	 * 
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param fileStoreId The ID of the uploaded file in the file store.
	 * @param campaignResponse The response object containing campaign details.
	 * @param planConfig The configuration details specific to the plan.
	 * @param excelWorkbook The workbook containing sheets to be processed.
	 * @param campaignBoundaryList List of boundary objects related to the campaign.
	 * @param campaignResourcesList List of campaign resources to be integrated.
	 * @param dataFormatter The data formatter for formatting cell values.
	 */
	private void processSheets(PlanConfigurationRequest planConfigurationRequest, String fileStoreId,
			Object campaignResponse, PlanConfiguration planConfig, Workbook excelWorkbook,
			List<Boundary> campaignBoundaryList, List<CampaignResources> campaignResourcesList,
			DataFormatter dataFormatter) {
		LocaleResponse localeResponse = localeUtil.searchLocale(planConfigurationRequest);
		CampaignResponse campaign = parseCampaignResponse(campaignResponse);
		Map<String, Object> attributeNameVsDataTypeMap = prepareAttributeVsIndexMap(planConfigurationRequest,
				fileStoreId, campaign, planConfig);
		List<String> boundaryCodeList = getBoundaryCodeList(planConfigurationRequest, campaign, planConfig);

		excelWorkbook.forEach(excelWorkbookSheet -> {
			if (isSheetAlloedToProcess(planConfigurationRequest, excelWorkbookSheet.getSheetName(),localeResponse)) {
				Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(excelWorkbookSheet);
				List<String> columnNamesList = mapOfColumnNameAndIndex.keySet().stream().toList();
				parsingUtil.validateColumnNames(columnNamesList, planConfig, fileStoreId);
				processRows(planConfigurationRequest, excelWorkbookSheet, dataFormatter, fileStoreId,
						campaignBoundaryList, attributeNameVsDataTypeMap, boundaryCodeList);
			}
		});
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
	 * @param campaignBoundaryList     The list of campaign boundaries to be
	 *                                 updated.
	 * @param attributeNameVsDataTypeMap Mapping of attribute names to their data types.
	 * @param boundaryCodeList List of boundary codes.
	 * @throws IOException If an I/O error occurs.
	 */
	private void processRows(PlanConfigurationRequest planConfigurationRequest, Sheet sheet, DataFormatter dataFormatter, String fileStoreId, List<Boundary> campaignBoundaryList, Map<String, Object> attributeNameVsDataTypeMap, List<String> boundaryCodeList) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		Row firstRow = null;
		performRowLevelCalculations(planConfigurationRequest, sheet, dataFormatter, fileStoreId, campaignBoundaryList, planConfig, attributeNameVsDataTypeMap, boundaryCodeList, firstRow);
	}

	/**
	 * Retrieves a list of boundary codes based on the given plan configuration, campaign details, and request information.
	 *
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param campaign The campaign response object containing campaign details.
	 * @param planConfig The configuration details specific to the plan.
	 * @return A list of boundary codes corresponding to the specified hierarchy type and tenant ID.
	 */
	private List<String> getBoundaryCodeList(PlanConfigurationRequest planConfigurationRequest,
			CampaignResponse campaign, PlanConfiguration planConfig) {
		BoundarySearchResponse boundarySearchResponse = boundaryUtil.search(planConfig.getTenantId(),
				campaign.getCampaign().get(0).getHierarchyType(), planConfigurationRequest);
		List<String> boundaryList = new ArrayList<>();
		List<String> boundaryCodeList = getAllBoundaryPresentforHierarchyType(
				boundarySearchResponse.getTenantBoundary().get(0).getBoundary(), boundaryList);
		return boundaryCodeList;
	}

	/**
	 * Prepares a mapping of attribute names to their corresponding indices or data types based on configuration and MDMS data.
	 *
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param fileStoreId The ID of the uploaded file in the file store.
	 * @param campaign The campaign response object containing campaign details.
	 * @param planConfig The configuration details specific to the plan.
	 * @return A map of attribute names to their corresponding indices or data types.
	 */
	private Map<String, Object> prepareAttributeVsIndexMap(PlanConfigurationRequest planConfigurationRequest,
			String fileStoreId, CampaignResponse campaign, PlanConfiguration planConfig) {
		Object mdmsData = mdmsUtil.fetchMdmsData(planConfigurationRequest.getRequestInfo(),
				planConfigurationRequest.getPlanConfiguration().getTenantId());
		org.egov.processor.web.models.File file = planConfig.getFiles().stream()
				.filter(f -> f.getFilestoreId().equalsIgnoreCase(fileStoreId)).findFirst().get();
		Map<String, Object> attributeNameVsDataTypeMap = mdmsUtil.filterMasterData(mdmsData.toString(), file.getInputFileType(),
				file.getTemplateIdentifier(), campaign.getCampaign().get(0).getProjectType());
		return attributeNameVsDataTypeMap;
	}

	
	/**
	 * Parses an object representing campaign response into a CampaignResponse object.
	 * 
	 * @param campaignResponse The object representing campaign response to be parsed.
	 * @return CampaignResponse object parsed from the campaignResponse.
	 */
	private CampaignResponse parseCampaignResponse(Object campaignResponse) {
		CampaignResponse campaign = null;
		campaign = objectMapper.convertValue(campaignResponse, CampaignResponse.class);
		return campaign;
	}

	/**
	 * Performs row-level calculations and processing on each row in the sheet.
	 * Validates rows, maps resource values, converts assumptions, creates feature nodes,
	 * calculates operations results, updates campaign boundaries, and creates plan entities.
	 *
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param sheet The sheet from which rows are processed.
	 * @param dataFormatter The data formatter for formatting cell values.
	 * @param fileStoreId The ID of the uploaded file in the file store.
	 * @param campaignBoundaryList List of boundary objects related to the campaign.
	 * @param planConfig The configuration details specific to the plan.
	 * @param attributeNameVsDataTypeMap Mapping of attribute names to their data types.
	 * @param boundaryCodeList List of boundary codes.
	 * @param firstRow The first row of the sheet.
	 */
	private void performRowLevelCalculations(PlanConfigurationRequest planConfigurationRequest, Sheet sheet,
			DataFormatter dataFormatter, String fileStoreId, List<Boundary> campaignBoundaryList,
			PlanConfiguration planConfig, Map<String, Object> attributeNameVsDataTypeMap, List<String> boundaryCodeList,
			Row firstRow)  {
		for (Row row : sheet) {
			if(isRowEmpty(row))
				continue;

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

			Integer indexOfBoundaryCode = campaignIntegrationUtil.getIndexOfBoundaryCode(0,
					campaignIntegrationUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);
			validateRows(indexOfBoundaryCode, row, firstRow, attributeNameVsDataTypeMap, mappedValues, mapOfColumnNameAndIndex,
					planConfigurationRequest, boundaryCodeList, sheet);
			JsonNode feature = createFeatureNodeFromRow(row, dataFormatter, mapOfColumnNameAndIndex);
			performCalculationsOnOperations(sheet, planConfig, row, resultMap, mappedValues,
					assumptionValueMap, feature);
			if (config.isIntegrateWithAdminConsole())
				campaignIntegrationUtil.updateCampaignBoundary(planConfig, feature, assumptionValueMap, mappedValues,
						mapOfColumnNameAndIndex, campaignBoundaryList, resultMap);
			planUtil.create(planConfigurationRequest, feature, resultMap, mappedValues);
			// TODO: remove after testing
			printRow(sheet, row);
		}
	}

	/**
	 * Checks if a given row is empty.
	 *
	 * A row is considered empty if it is null or if all of its cells are empty or of type BLANK.
	 *
	 * @param row the Row to check
	 * @return true if the row is empty, false otherwise
	 */
	public static boolean isRowEmpty(Row row) {
		if (row == null) {
			return true;
		}
		for (Cell cell : row) {
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Performs calculations on operations for a specific row in the sheet.
	 * Calculates results based on plan configuration operations, updates result map, and sets cell values.
	 *
	 * @param sheet The sheet where calculations are performed.
	 * @param planConfig The configuration details for the plan.
	 * @param row The row in the sheet where calculations are applied.
	 * @param resultMap The map to store calculation results.
	 * @param mappedValues Mapping of values needed for calculations.
	 * @param assumptionValueMap Map of assumption values used in calculations.
	 * @param feature JSON node containing additional features or data for calculations.
	 */
	private void performCalculationsOnOperations(Sheet sheet, PlanConfiguration planConfig, Row row,
			Map<String, BigDecimal> resultMap, Map<String, String> mappedValues,
			Map<String, BigDecimal> assumptionValueMap, JsonNode feature) {
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
				log.info("XLS file saved successfully.");
				return outputFile;
			} catch (IOException e) {
				log.info("Error saving XLS file: " + e);
				return null;
			}
		} catch (IOException e) {
			log.info("Error converting workbook to XLS: " + e);
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
	 * @param indexOfBoundaryCode      The index of the "BCode" column in the row.
	 * @param row                      The row containing the data to be validated.
	 * @param columnHeaderRow          The row containing the column headers.
	 * @param attributeNameVsDataTypeMap             Map containing data types from external
	 *                                 source (MDMS).
	 * @param mappedValues             Map containing mapped values.
	 * @param mapOfColumnNameAndIndex  Map containing column names and their
	 *                                 corresponding indices.
	 * @param planConfigurationRequest Object representing the plan configuration
	 *                                 request.
	 * @throws CustomException if the input data is not valid or if a custom
	 *                         exception occurs.
	 */
	public void validateRows(Integer indexOfBoundaryCode, Row row, Row columnHeaderRow, Map<String, Object> attributeNameVsDataTypeMap,
			Map<String, String> mappedValues, Map<String, Integer> mapOfColumnNameAndIndex,
			PlanConfigurationRequest planConfigurationRequest, List<String> boundaryCodeList, Sheet sheet) {

		try {
			validateTillBoundaryCode(indexOfBoundaryCode, row, columnHeaderRow);
			validateAttributes(attributeNameVsDataTypeMap, mappedValues, mapOfColumnNameAndIndex, row, columnHeaderRow, indexOfBoundaryCode,
					boundaryCodeList);
		} catch (JsonProcessingException e) {
			log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " at sheet - " + sheet);
			planConfigurationRequest.getPlanConfiguration().setStatus(StatusEnum.INVALID_DATA);
			planUtil.update(planConfigurationRequest);
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " at sheet - " + sheet);
		} catch (CustomException customException) {
			log.info(customException.toString()+ "at sheet - " + sheet.getSheetName());
			planConfigurationRequest.getPlanConfiguration().setStatus(StatusEnum.INVALID_DATA);
			planUtil.update(planConfigurationRequest);
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					customException.getMessage()+ "at sheet - " + sheet.getSheetName());
		}
	}

	/**
	 * Validates the data in columns from "BCode" column.
	 * 
	 * @param attributeNameVsDataTypeMap    Map containing data types from an external
	 *                                		source (MDMS).
	 * @param mappedValues            		Map containing mapped values.
	 * @param mapOfColumnNameAndIndex 		Map containing column names and their
	 *                                		corresponding indices.
	 * @param row                     		The row containing the data to be validated.
	 * @param columnHeaderRow         		The row containing the column headers.
	 * @param indexOfBoundaryCode     		The index of the "BCode" column in the row.
	 * @throws JsonMappingException    		if there's an issue mapping JSON.
	 * @throws JsonProcessingException 		if there's an issue processing JSON.
	 */
	private void validateAttributes(Map<String, Object> attributeNameVsDataTypeMap, Map<String, String> mappedValues,
			Map<String, Integer> mapOfColumnNameAndIndex, Row row, Row columnHeaderRow, Integer indexOfBoundaryCode,
			List<String> boundaryCodeList) throws JsonMappingException, JsonProcessingException {
		for (int j = indexOfBoundaryCode; j < mapOfColumnNameAndIndex.size(); j++) {
			Cell cell = row.getCell(j);
			Cell columnName = columnHeaderRow.getCell(j);
			String name = findByValue(mappedValues, columnName.getStringCellValue());
			if (attributeNameVsDataTypeMap.containsKey(name)) {
				Map<String, Object> mapOfAttributes = (Map<String, Object>) attributeNameVsDataTypeMap.get(name);
				boolean isRequired = (mapOfAttributes.containsKey(ServiceConstants.ATTRIBUTE_IS_REQUIRED)
						? (boolean) mapOfAttributes.get(ServiceConstants.ATTRIBUTE_IS_REQUIRED)
						: false);
				if (cell != null) {
					switch (cell.getCellType()) {
					case STRING:
						String cellValue = cell.getStringCellValue();
						if (j == indexOfBoundaryCode && !boundaryCodeList.contains(cellValue)) {
							log.info("Boundary Code " + cellValue + " is not present in boundary search. Code for row "
									+ (row.getRowNum() + 1) + " and cell/column " + columnName);
							throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
									"Boundary Code " + cellValue + " is not present in boundary search. Code for row "
											+ (row.getRowNum() + 1) + " and cell/column " + columnName);
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
					case BLANK:
						if (!isRequired) {
							continue;
						}else {
							throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
									ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell "
											+ columnName);
						}
					default:
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell "
										+ columnName);
					}
				}else {
					if(isRequired) {
						throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
								ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell "
										+ columnName);
					}
				}
			}
		}
	}

	/**
	 * Validates the data in columns up to the specified index of the "BCode"
	 * column.
	 * 
	 * @param indexOfBoundaryCode    The index of the "BCode" column in the row.
	 * @param row             The row containing the data to be validated.
	 * @param columnHeaderRow The row containing the column headers.
	 */
	private void validateTillBoundaryCode(Integer indexOfBoundaryCode, Row row, Row columnHeaderRow) {
		for (int j = 0; j <= indexOfBoundaryCode - 1; j++) {
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
	
	/**
	 * Checks if a sheet is allowed to be processed based on MDMS constants and locale-specific configuration.
	 * 
	 * @param planConfigurationRequest The request containing configuration details including request info and tenant ID.
	 * @param sheetName The name of the sheet to be processed.
	 * @return true if the sheet is allowed to be processed, false otherwise.
	 * @throws JsonMappingException If there's an issue mapping JSON response to Java objects.
	 * @throws JsonProcessingException If there's an issue processing JSON during conversion.
	 */
	private boolean isSheetAlloedToProcess(PlanConfigurationRequest planConfigurationRequest, String sheetName,LocaleResponse localeResponse) {
		Map<String, Object> mdmsDataConstants = mdmsUtil.fetchMdmsDataForCommonConstants(
				planConfigurationRequest.getRequestInfo(),
				planConfigurationRequest.getPlanConfiguration().getTenantId());
		String value = (String) mdmsDataConstants.get("readMeSheetName");
		for (Locale locale : localeResponse.getMessages()) {
			if ((locale.getCode().equalsIgnoreCase(value))) {
				if (sheetName.equals(locale.getMessage()))
					return false;
			}
		}
		return true;

	}
}