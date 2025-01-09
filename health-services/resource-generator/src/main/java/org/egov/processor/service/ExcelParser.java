package org.egov.processor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.util.*;
import org.egov.processor.web.models.*;
import org.egov.processor.web.models.boundary.BoundarySearchResponse;
import org.egov.processor.web.models.boundary.EnrichedBoundary;
import org.egov.processor.web.models.campaignManager.Boundary;
import org.egov.processor.web.models.campaignManager.CampaignResources;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.egov.processor.web.models.planFacility.PlanFacility;
import org.egov.processor.web.models.planFacility.PlanFacilityResponse;
import org.egov.processor.web.models.planFacility.PlanFacilitySearchCriteria;
import org.egov.processor.web.models.planFacility.PlanFacilitySearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.processor.config.ServiceConstants.*;

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

	private CensusUtil censusUtil;

	private EnrichmentUtil enrichmentUtil;

	private PlanConfigurationUtil planConfigurationUtil;

	private OutputEstimationGenerationUtil outputEstimationGenerationUtil;

	private PlanFacilityUtil planFacilityUtil;

	public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil,
                       CalculationUtil calculationUtil, PlanUtil planUtil, CampaignIntegrationUtil campaignIntegrationUtil,
                       Configuration config, MdmsUtil mdmsUtil, BoundaryUtil boundaryUtil, LocaleUtil localeUtil, CensusUtil censusUtil, EnrichmentUtil enrichmentUtil, PlanConfigurationUtil planConfigurationUtil, OutputEstimationGenerationUtil outputEstimationGenerationUtil, PlanFacilityUtil planFacilityUtil) {
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
        this.censusUtil = censusUtil;
        this.enrichmentUtil = enrichmentUtil;
        this.planConfigurationUtil = planConfigurationUtil;
        this.outputEstimationGenerationUtil = outputEstimationGenerationUtil;
        this.planFacilityUtil = planFacilityUtil;
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
		processExcelFile(planConfigurationRequest, file, fileStoreId, campaignResponse);
		return null;
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
	 */
	private void processExcelFile(PlanConfigurationRequest planConfigurationRequest, File file, String fileStoreId,
			Object campaignResponse) {
		try (Workbook workbook = new XSSFWorkbook(file)) {
			List<Boundary> campaignBoundaryList = new ArrayList<>();
			List<CampaignResources> campaignResourcesList = new ArrayList<>();
			DataFormatter dataFormatter = new DataFormatter();
			processSheets(planConfigurationRequest, fileStoreId, campaignResponse, workbook,
					campaignBoundaryList, dataFormatter);
            uploadFileAndIntegrateCampaign(planConfigurationRequest, campaignResponse,
                    workbook, campaignBoundaryList, campaignResourcesList, fileStoreId);
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
	 * @param workbook The workbook containing data to be uploaded and integrated.
	 * @param campaignBoundaryList List of boundary objects related to the campaign.
	 * @param campaignResourcesList List of campaign resources to be integrated.
	 */
	private void uploadFileAndIntegrateCampaign(PlanConfigurationRequest planConfigurationRequest,
			Object campaignResponse, Workbook workbook,
			List<Boundary> campaignBoundaryList, List<CampaignResources> campaignResourcesList, String filestoreId) {
		File fileToUpload = null;
		try {
			PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
			fileToUpload = convertWorkbookToXls(workbook);
			if (planConfig.getStatus().equals(config.getPlanConfigTriggerPlanEstimatesStatus())) {
				String uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());
				planUtil.setFileStoreIdForPopulationTemplate(planConfigurationRequest, uploadedFileStoreId);
				planUtil.update(planConfigurationRequest);
			}
			if (planConfig.getStatus().equals(config.getPlanConfigUpdatePlanEstimatesIntoOutputFileStatus()) && config.isIntegrateWithAdminConsole()) {
				//Upload the processed output file into project factory
				String uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());
				campaignIntegrationUtil.updateResourcesInProjectFactory(planConfigurationRequest, uploadedFileStoreId);

				//process output file for localized header columns and addition of new columns
				outputEstimationGenerationUtil.processOutputFile(workbook, planConfigurationRequest, filestoreId);

				//upload the processed output file and update the same into plan configuration file object
				fileToUpload = convertWorkbookToXls(workbook);
				uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());
				planUtil.setFileStoreIdForPopulationTemplate(planConfigurationRequest, uploadedFileStoreId);
				planUtil.update(planConfigurationRequest);
			}
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
	 * @param request The request containing configuration details including tenant ID.
	 * @param fileStoreId The ID of the uploaded file in the file store.
	 * @param campaignResponse The response object containing campaign details.
	 * @param excelWorkbook The workbook containing sheets to be processed.
	 * @param campaignBoundaryList List of boundary objects related to the campaign.
	 * @param dataFormatter The data formatter for formatting cell values.
	 */
	private void processSheets(PlanConfigurationRequest request, String fileStoreId,
							   Object campaignResponse, Workbook excelWorkbook,
							   List<Boundary> campaignBoundaryList,
							   DataFormatter dataFormatter) {
		CampaignResponse campaign = campaignIntegrationUtil.parseCampaignResponse(campaignResponse);
		LocaleResponse localeResponse = localeUtil.searchLocale(request);
		Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(),
				request.getPlanConfiguration().getTenantId());
		planConfigurationUtil.orderPlanConfigurationOperations(request);
		enrichmentUtil.enrichResourceMapping(request, localeResponse, campaign.getCampaign().get(0).getProjectType(), fileStoreId);
		Map<String, Object> attributeNameVsDataTypeMap = prepareAttributeVsIndexMap(request,
				fileStoreId, campaign, request.getPlanConfiguration(), mdmsData);

		List<String> boundaryCodeList = getBoundaryCodeList(request, campaign);
		Map<String, String> mappedValues = request.getPlanConfiguration().getResourceMapping().stream()
				.filter(f -> f.getFilestoreId().equals(fileStoreId))
				.collect(Collectors.toMap(
						ResourceMapping::getMappedTo,
						ResourceMapping::getMappedFrom,
						(existing, replacement) -> existing,
						LinkedHashMap::new
				));
		excelWorkbook.forEach(excelWorkbookSheet -> {
			if (parsingUtil.isSheetAllowedToProcess(request, excelWorkbookSheet.getSheetName(), localeResponse)) {
				if (request.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerPlanEstimatesStatus())) {
					enrichmentUtil.enrichsheetWithApprovedCensusRecords(excelWorkbookSheet, request, fileStoreId, mappedValues);
					processRows(request, excelWorkbookSheet, dataFormatter, fileStoreId,
							campaignBoundaryList, attributeNameVsDataTypeMap, boundaryCodeList);
				} else if (request.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerCensusRecordsStatus())) {
					processRowsForCensusRecords(request, excelWorkbookSheet,
							fileStoreId, attributeNameVsDataTypeMap, boundaryCodeList, campaign.getCampaign().get(0).getHierarchyType());
				} else if (request.getPlanConfiguration().getStatus().equals(config.getPlanConfigUpdatePlanEstimatesIntoOutputFileStatus())) {

					// Create a Map of Boundary Code to Facility's fixed post detail.
					Map<String, String> boundaryCodeToFixedPostMap = fetchFixedPostDetails(request, excelWorkbook, fileStoreId);

					enrichmentUtil.enrichsheetWithApprovedPlanEstimates(excelWorkbookSheet, request, fileStoreId, mappedValues);
				}
			}
		});
	}

	/**
	 * This method makes plan facility search call and creates a map of boundary code to it's fixed post facility details.
	 *
	 * @param request       the plan configuration request.
	 * @param excelWorkbook the Excel workbook to be processed.
	 * @param fileStoreId   the fileStore id of the file.
	 * @return returns a map of boundary code to it's fixed post facility details.
	 */
	private Map<String, String> fetchFixedPostDetails(PlanConfigurationRequest request, Workbook excelWorkbook, String fileStoreId) {
		PlanConfiguration planConfiguration = request.getPlanConfiguration();

		// Create the map of boundary code to the facility assigned to that boundary.
		Map<String, String> boundaryCodeToFacilityNameMap = outputEstimationGenerationUtil.getBoundaryCodeToFacilityMap(excelWorkbook, request, fileStoreId);

		//Create plan facility search request
		PlanFacilitySearchRequest searchRequest = PlanFacilitySearchRequest.builder()
				.requestInfo(request.getRequestInfo())
				.planFacilitySearchCriteria(PlanFacilitySearchCriteria.builder()
						.tenantId(planConfiguration.getTenantId())
						.planConfigurationId(planConfiguration.getId())
						.build())
				.build();

		PlanFacilityResponse planFacilityResponse = planFacilityUtil.search(searchRequest);

		if (CollectionUtils.isEmpty(planFacilityResponse.getPlanFacility())) {
			throw new CustomException(NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_CODE, NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_MESSAGE);
		}

		// Create a Boundary Code to Facility's fixed post detail map.
		Map<String, String> boundaryCodeToFixedPostMap = new HashMap<>();

		for (PlanFacility planFacility : planFacilityResponse.getPlanFacility()) {
			// Find the boundary code corresponding to the facility name.
			String boundaryCode = findByValue(boundaryCodeToFacilityNameMap, planFacility.getFacilityName());

			// Extract the 'FIXED_POST' field from additional details.
			String fixedPostValue = (String) parsingUtil.extractFieldsFromJsonObject(planFacility.getAdditionalDetails(), FIXED_POST);

			// Populate the map.
			boundaryCodeToFixedPostMap.put(boundaryCode, fixedPostValue);
		}

		return boundaryCodeToFixedPostMap;
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
		performRowLevelCalculations(planConfigurationRequest, sheet, dataFormatter, fileStoreId, campaignBoundaryList, planConfig, attributeNameVsDataTypeMap, boundaryCodeList);
	}

	private void processRowsForCensusRecords(PlanConfigurationRequest planConfigurationRequest, Sheet sheet, String fileStoreId, Map<String, Object> attributeNameVsDataTypeMap, List<String> boundaryCodeList, String hierarchyType) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();

		Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
				.filter(f -> f.getFilestoreId().equals(fileStoreId))
				.collect(Collectors.toMap(
						ResourceMapping::getMappedTo,
						ResourceMapping::getMappedFrom,
						(existing, replacement) -> existing,
						LinkedHashMap::new
				));

		Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
		Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
				parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);
		Row firstRow = null;

		for (Row row : sheet) {
			if (parsingUtil.isRowEmpty(row))
				continue;

			if (row.getRowNum() == 0) {
				firstRow = row;
				continue;
			}

			validateRows(indexOfBoundaryCode, row, firstRow, attributeNameVsDataTypeMap, mappedValues, mapOfColumnNameAndIndex,
					planConfigurationRequest, boundaryCodeList, sheet);
			JsonNode currentRow = createFeatureNodeFromRow(row, mapOfColumnNameAndIndex);

			censusUtil.create(planConfigurationRequest, currentRow, mappedValues, hierarchyType);
		}
	}

	/**
	 * Retrieves a list of boundary codes based on the given plan configuration, campaign details, and request information.
	 *
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param campaign The campaign response object containing campaign details.
	 * @return A list of boundary codes corresponding to the specified hierarchy type and tenant ID.
	 */
	private List<String> getBoundaryCodeList(PlanConfigurationRequest planConfigurationRequest,
			CampaignResponse campaign) {
		BoundarySearchResponse boundarySearchResponse = boundaryUtil.search(planConfigurationRequest.getPlanConfiguration().getTenantId(),
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
			String fileStoreId, CampaignResponse campaign, PlanConfiguration planConfig, Object mdmsData) {
		org.egov.processor.web.models.File file = planConfig.getFiles().stream()
				.filter(f -> f.getFilestoreId().equalsIgnoreCase(fileStoreId)).findFirst().get();
        return mdmsUtil.filterMasterData(mdmsData.toString(), campaign.getCampaign().get(0).getProjectType());
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
	 */
	private void performRowLevelCalculations(PlanConfigurationRequest planConfigurationRequest, Sheet sheet,
			DataFormatter dataFormatter, String fileStoreId, List<Boundary> campaignBoundaryList,
			PlanConfiguration planConfig, Map<String, Object> attributeNameVsDataTypeMap, List<String> boundaryCodeList)  {
		Row firstRow = null;
		Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
				.filter(f -> f.getFilestoreId().equals(fileStoreId))
				.collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
		Map<String, BigDecimal> assumptionValueMap = calculationUtil
				.convertAssumptionsToMap(planConfig.getAssumptions());
		Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

		Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
				parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

		for (Row row : sheet) {
			if(parsingUtil.isRowEmpty(row))
				continue;

			if (row.getRowNum() == 0) {
				firstRow = row;
				continue;
			}

			Map<String, BigDecimal> resultMap = new HashMap<>();
			validateRows(indexOfBoundaryCode, row, firstRow, attributeNameVsDataTypeMap, mappedValues, mapOfColumnNameAndIndex,
					planConfigurationRequest, boundaryCodeList, sheet);
			JsonNode feature = createFeatureNodeFromRow(row, mapOfColumnNameAndIndex);
			performCalculationsOnOperations(sheet, planConfig, row, resultMap, mappedValues,
					assumptionValueMap, feature);
			if (config.isIntegrateWithAdminConsole())
				campaignIntegrationUtil.updateCampaignBoundary(planConfig, feature, assumptionValueMap, mappedValues,
						mapOfColumnNameAndIndex, campaignBoundaryList, resultMap);
			planUtil.create(planConfigurationRequest, feature, resultMap, mappedValues);

		}
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
			cell.getCellStyle().setLocked(false); // Ensure the new cell is editable

			if (row.getRowNum() == 1) {
				Cell headerCell = sheet.getRow(0).createCell(row.getLastCellNum() - 1);
				headerCell.setCellValue(output);
				headerCell.getCellStyle().setLocked(true);
			}
		}
		
	}

	/**
	 * Uploads the converted XLS file to the file store.
	 *
	 * @param convertedFile The converted XLS file to upload.
	 * @param tenantId      The tenant ID for the file upload.
	 * @return The file store  ID of the uploaded file, or null if an error occurred.
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
	 * @param columnIndexMap The mapping of column names to column indices.
	 * @return The JSON feature node representing the row.
	 */
	private JsonNode createFeatureNodeFromRow(Row row, Map<String, Integer> columnIndexMap) {
		ObjectNode featureNode = objectMapper.createObjectNode();
		ObjectNode propertiesNode = featureNode.putObject("properties");

		// Iterate over each entry in the columnIndexMap
		for (Map.Entry<String, Integer> entry : columnIndexMap.entrySet()) {
			String columnName = entry.getKey();
			Integer columnIndex = entry.getValue();

			// Get the cell value from the row based on the columnIndex
			Cell cell = row.getCell(columnIndex);
			if (cell == null) {
				// Handle null cells if needed
				propertiesNode.putNull(columnName);
				continue;
			}

			switch (cell.getCellType()) {
				case STRING:
					propertiesNode.put(columnName, cell.getStringCellValue());
					break;
				case NUMERIC:
					if (DateUtil.isCellDateFormatted(cell)) {
						// Handle date values
						propertiesNode.put(columnName, cell.getDateCellValue().toString());
					} else {
						propertiesNode.put(columnName, BigDecimal.valueOf(cell.getNumericCellValue()));
					}
					break;
				case BOOLEAN:
					propertiesNode.put(columnName, cell.getBooleanCellValue());
					break;
				case FORMULA:
					// Attempt to get the cached formula result value directly
					switch (cell.getCachedFormulaResultType()) {
						case NUMERIC:
							propertiesNode.put(columnName, BigDecimal.valueOf(cell.getNumericCellValue()));
							break;
						case STRING:
							propertiesNode.put(columnName, cell.getStringCellValue());
							break;
						case BOOLEAN:
							propertiesNode.put(columnName, cell.getBooleanCellValue());
							break;
						default:
							propertiesNode.putNull(columnName);
							break;
					}
					break;
                default:
					propertiesNode.putNull(columnName);
					break;
			}
		}

		return featureNode;
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
			planConfigurationRequest.getPlanConfiguration().setStatus("INVALID_DATA");
			planUtil.update(planConfigurationRequest);
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " at sheet - " + sheet);
		} catch (CustomException customException) {
			log.info(customException.toString()+ "at sheet - " + sheet.getSheetName());
			planConfigurationRequest.getPlanConfiguration().setStatus("INVALID_DATA");
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


}