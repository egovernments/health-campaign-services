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
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.egov.processor.web.models.census.Census;
import org.egov.processor.web.models.mdmsV2.MixedStrategyOperationLogic;
import org.egov.processor.web.models.planFacility.PlanFacility;
import org.egov.processor.web.models.planFacility.PlanFacilityResponse;
import org.egov.processor.web.models.planFacility.PlanFacilitySearchCriteria;
import org.egov.processor.web.models.planFacility.PlanFacilitySearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.processor.config.ErrorConstants.*;
import static org.egov.processor.config.ServiceConstants.*;
import static org.egov.processor.web.models.File.InputFileTypeEnum.EXCEL;

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

	private MixedStrategyUtil mixedStrategyUtil;

	private PlanFacilityUtil planFacilityUtil;

	public ExcelParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil,
					   CalculationUtil calculationUtil, PlanUtil planUtil, CampaignIntegrationUtil campaignIntegrationUtil,
					   Configuration config, MdmsUtil mdmsUtil, BoundaryUtil boundaryUtil, LocaleUtil localeUtil, CensusUtil censusUtil, EnrichmentUtil enrichmentUtil, PlanConfigurationUtil planConfigurationUtil, OutputEstimationGenerationUtil outputEstimationGenerationUtil, MixedStrategyUtil mixedStrategyUtil, PlanFacilityUtil planFacilityUtil) {
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
        this.mixedStrategyUtil = mixedStrategyUtil;
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
								CampaignResponse campaignResponse) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		File file = parsingUtil.convertByteArrayToFile(filestoreUtil.getFileByteArray(planConfig.getTenantId(), fileStoreId), ServiceConstants.FILE_EXTENSION);
		if (file == null || !file.exists()) {
			log.error(FILE_NOT_FOUND_LOG, fileStoreId, planConfig.getTenantId());
			throw new CustomException(FILE_NOT_FOUND_CODE, String.format(FILE_NOT_FOUND_MESSAGE, fileStoreId, planConfig.getTenantId()));
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
			CampaignResponse campaignResponse) {
		try (Workbook workbook = new XSSFWorkbook(file)) {
			processSheets(planConfigurationRequest, fileStoreId, campaignResponse, workbook);
            uploadFileAndIntegrateCampaign(planConfigurationRequest, workbook, fileStoreId);
		} catch (FileNotFoundException e) {
			log.error(EXCEL_FILE_NOT_FOUND_MESSAGE + LOG_PLACEHOLDER, e.getMessage());
			throw new CustomException(EXCEL_FILE_NOT_FOUND_CODE, EXCEL_FILE_NOT_FOUND_MESSAGE);
		} catch (InvalidFormatException e) {
			log.error(INVALID_FILE_FORMAT_MESSAGE + LOG_PLACEHOLDER, e.getMessage());
			throw new CustomException(INVALID_FILE_FORMAT_CODE, INVALID_FILE_FORMAT_MESSAGE);
		} catch (IOException e) {
			log.error(ERROR_PROCESSING_EXCEL_FILE + LOG_PLACEHOLDER, e.getMessage());
			throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
					ERROR_PROCESSING_EXCEL_FILE);
		}
	}

	/**
	 * Uploads a converted file and integrates campaign details if configured to do so.
	 *
	 * @param planConfigurationRequest The request containing configuration details including tenant ID.
	 * @param workbook                 The workbook containing data to be uploaded and integrated.
	 * @param filestoreId              The ID of the file in the file store.
	 */
	private void uploadFileAndIntegrateCampaign(PlanConfigurationRequest planConfigurationRequest, Workbook workbook, String filestoreId) {
		File fileToUpload = null;
		try {
			PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
			fileToUpload = parsingUtil.convertWorkbookToXls(workbook);
			// RESOURCE_ESTIMATION_IN_PROGRESS
			if (planConfig.getStatus().equals(config.getPlanConfigTriggerPlanEstimatesStatus())) {
				String uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());
				planUtil.setFileStoreIdForEstimationsInProgress(planConfigurationRequest, uploadedFileStoreId);
				planUtil.update(planConfigurationRequest);
			}
			// RESOURCE_ESTIMATION_IN_PROGRESS && Integrate with admin console
			if (planConfig.getStatus().equals(config.getPlanConfigUpdatePlanEstimatesIntoOutputFileStatus()) && config.isIntegrateWithAdminConsole()) {
				//Upload the processed output file into project factory
				String uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());
				campaignIntegrationUtil.updateResourcesInProjectFactory(planConfigurationRequest, uploadedFileStoreId);

				//process output file for localized header columns and addition of new columns
				outputEstimationGenerationUtil.processOutputFile(workbook, planConfigurationRequest, filestoreId);

				//upload the processed output file and update the same into plan configuration file object
				fileToUpload = parsingUtil.convertWorkbookToXls(workbook);
				uploadedFileStoreId = uploadConvertedFile(fileToUpload, planConfig.getTenantId());
				planUtil.addFileForFinalEstimations(planConfigurationRequest, uploadedFileStoreId, EXCEL);
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
	 */
	private void 	processSheets(PlanConfigurationRequest request, String fileStoreId,
							   CampaignResponse campaignResponse, Workbook excelWorkbook) {
		// IMPORTANT: Localisation takes place, Schema against validation for mdms,
		LocaleResponse localeResponse = localeUtil.searchLocale(request);
		Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(),
				request.getPlanConfiguration().getTenantId());
		// IMPORTANT: Sorting based on executionMapping
		planConfigurationUtil.orderPlanConfigurationOperations(request);
		// Resource mapping: from_code-> to_code
		enrichmentUtil.enrichResourceMapping(request, localeResponse, campaignResponse.getCampaign().get(0).getProjectType(), fileStoreId);
		Map<String, Object> attributeNameVsDataTypeMap = prepareAttributeVsIndexMap(fileStoreId, campaignResponse, request.getPlanConfiguration(), mdmsData);

		List<String> boundaryCodeList = getBoundaryCodeList(request, campaignResponse);
		// Code vs name, non-localised vs localised values
		Map<String, String> mappedValues = request.getPlanConfiguration().getResourceMapping().stream()
				.filter(rm -> rm.getFilestoreId().equals(fileStoreId))
				.filter(rm -> rm.getActive().equals(Boolean.TRUE))
				.collect(Collectors.toMap(
						ResourceMapping::getMappedTo,
						ResourceMapping::getMappedFrom,
						(existing, replacement) -> existing,
						LinkedHashMap::new
				));

		// Process Census records in batches to create plan estimates on PlanConfigTriggerPlanEstimatesStatus status.
		// RESOURCE_ESTIMATION_IN_PROGRESS
		if (request.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerPlanEstimatesStatus())) {
			Map<String, Object> boundaryCodeToCensusAdditionalDetails = new HashMap<>();
			batchProcessCensusRecords(request, boundaryCodeToCensusAdditionalDetails);
		}

		// Fetch mdms data for common constants
		Map<String, Object> mdmsDataForCommonConstants = mdmsUtil.fetchMdmsDataForCommonConstants(
				request.getRequestInfo(),
				request.getPlanConfiguration().getTenantId());

		excelWorkbook.forEach(excelWorkbookSheet -> {
			//IMPORTANT: If readme file here skipped
			if (outputEstimationGenerationUtil.isSheetAllowedToProcess(excelWorkbookSheet.getSheetName(), localeResponse, mdmsDataForCommonConstants)) {
                // STATUS: EXECUTION_TO_BE_DONE
				if (request.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerCensusRecordsStatus())) {
					// Check inputs
					processRowsForCensusRecords(request, excelWorkbookSheet,
							fileStoreId, attributeNameVsDataTypeMap, boundaryCodeList, campaignResponse.getCampaign().get(0).getHierarchyType());

				}
                // STATUS: RESOURCE_ESTIMATION_APPROVED
                else if (request.getPlanConfiguration().getStatus().equals(config.getPlanConfigUpdatePlanEstimatesIntoOutputFileStatus())) {
					enrichmentUtil.enrichsheetWithApprovedCensusRecords(excelWorkbookSheet, request, fileStoreId, mappedValues);
					enrichmentUtil.enrichsheetWithApprovedPlanEstimates(excelWorkbookSheet, request, fileStoreId, mappedValues);
				}
			}
		});
	}

	/**
	 * Processes census records in batches based on the given plan configuration request.
	 * The method retrieves census records in chunks of a configured batch size and processes them iteratively.
	 *
	 * @param planConfigurationRequest              The request containing plan configuration details.
	 * @param boundaryCodeToCensusAdditionalDetails A map to store additional census details indexed by boundary code.
	 */
	public void batchProcessCensusRecords(PlanConfigurationRequest planConfigurationRequest, Map<String, Object> boundaryCodeToCensusAdditionalDetails) {
		int offset = 0;
		List<Census> censusRecords;

		do {
			// Fetch census records in batches
			censusRecords = enrichmentUtil.getCensusRecordsInBatches(planConfigurationRequest, config.getBatchSize(), offset);

			// Break the loop if no census records found
			if(CollectionUtils.isEmpty(censusRecords)) {
				break;
			}

			// Process the retrieved batch of census records
			processCensusRecordsForPlan(censusRecords, boundaryCodeToCensusAdditionalDetails, planConfigurationRequest);
			log.info("Processed {} census records", censusRecords.size());

			// Increment offset for next batch
			offset += config.getBatchSize();
		} while (!CollectionUtils.isEmpty(censusRecords));
	}

	/**
	 * Executes processing of census records by performing calculations, data enrichment, and mixed strategy evaluations.
	 *
	 * @param censusRecords                         The list of census records to be processed.
	 * @param boundaryCodeToCensusAdditionalDetails A map to store census additional details, indexed by boundary code.
	 * @param planConfigurationRequest              The request object containing the plan configuration.
	 */
	private void processCensusRecordsForPlan(List<Census> censusRecords, Map<String, Object> boundaryCodeToCensusAdditionalDetails, PlanConfigurationRequest planConfigurationRequest) {

		PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

		// Fetch mixed strategy logic from the MDMS.
		// IMPORTANT: for some columns the value will be null in columns depending on fixed or not
		List<MixedStrategyOperationLogic> mixedStrategyOperationLogicList = mixedStrategyUtil
				.fetchMixedStrategyOperationLogicFromMDMS(planConfigurationRequest);

		// Create a mapping of boundary codes to fixed post details.
		// Village Mapped to fixed post or not
		Map<String, Boolean> boundaryCodeToFixedPostMap = fetchFixedPostDetails(planConfigurationRequest);

		for (Census census : censusRecords) {
			String boundaryCode = census.getBoundaryCode();

			// Convert census data into a structured JSON feature node.
			// For making it easy to edit
			JsonNode featureNode = createFeatureNodeFromCensus(census);
			Map<String, BigDecimal> resultMap = new HashMap<>();

			// Perform calculations for each operation defined in the plan configuration.
			// Calculations for each of the operations and enriches the resultMap
			performCalculationsForOperations(planConfiguration, featureNode, resultMap);

			// Store census additional details mapped to their respective boundary codes.
			boundaryCodeToCensusAdditionalDetails.put(boundaryCode, census.getAdditionalDetails());

			// Process result map using mixed strategy logic
			//Important: Clean up unneccesary fields by putting it to null in resultMap for categories not allowed
			mixedStrategyUtil.processResultMap(resultMap, planConfiguration.getOperations(), mixedStrategyUtil.getCategoriesNotAllowed(
							boundaryCodeToFixedPostMap.get(boundaryCode), planConfiguration, mixedStrategyOperationLogicList));

			// Trigger plan estimate create based on the estimates calculated.
            //IMPORTANT
			planUtil.create(planConfigurationRequest, featureNode, resultMap, boundaryCodeToCensusAdditionalDetails);
			log.info("Successfully created plan for {} boundary", boundaryCode);
		}
	}

	public void performCalculationsForOperations(PlanConfiguration planConfiguration, JsonNode featureNode, Map<String, BigDecimal> resultMap) {

		// Convert assumptions into a map for efficient retrieval.
		//making key value map for using assumptions
		Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfiguration.getAssumptions());

		// Perform calculations for each operation defined in the plan configuration.
		for (Operation operation : planConfiguration.getOperations()) {
			// Important: calculates the result
			BigDecimal result = calculationUtil.calculateResult(operation, featureNode, assumptionValueMap, resultMap);
			String output = operation.getOutput();
			// IMPORTANT: Storing the output as key so that the next time it can be used as input in operations
			resultMap.put(output, result);
		}
	}

	/**
	 * This method makes plan facility search call and creates a map of boundary code to it's fixed post facility details.
	 *
	 * @param request     the plan configuration request.
	 * @return returns a map of boundary code to it's fixed post facility details.
	 */
	private Map<String, Boolean> fetchFixedPostDetails(PlanConfigurationRequest request) {
		PlanConfiguration planConfiguration = request.getPlanConfiguration();

		//Create plan facility search request
		// IMPORTANT: Service boundaries are the facilities that are serving villages
		PlanFacilitySearchRequest searchRequest = PlanFacilitySearchRequest.builder()
				.requestInfo(request.getRequestInfo())
				.planFacilitySearchCriteria(PlanFacilitySearchCriteria.builder()
						.tenantId(planConfiguration.getTenantId())
						.planConfigurationId(planConfiguration.getId())
						.build())
				.build();

		PlanFacilityResponse planFacilityResponse = planFacilityUtil.search(searchRequest);

		if (ObjectUtils.isEmpty(planFacilityResponse) || CollectionUtils.isEmpty(planFacilityResponse.getPlanFacility())) {
			throw new CustomException(NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_CODE, NO_PLAN_FACILITY_FOUND_FOR_GIVEN_DETAILS_MESSAGE);
		}

		// Create a Boundary Code to Facility's fixed post detail map.
		Map<String, Boolean> boundaryCodeToFixedPostMap = new HashMap<>();

		for (PlanFacility planFacility : planFacilityResponse.getPlanFacility()) {
			// Ensure serviceBoundaries are not empty
			if (!CollectionUtils.isEmpty(planFacility.getServiceBoundaries())) {

				// Extract the 'FIXED_POST' field from additional details.
				String fixedPostValue = parsingUtil.extractFieldsFromJsonObject(planFacility.getAdditionalDetails(), FIXED_POST, String.class);

				// Normalize the value and determine boolean equivalent.
				Boolean isFixedPost = !ObjectUtils.isEmpty(fixedPostValue) && fixedPostValue.trim().equalsIgnoreCase(FIXED_POST_YES);

				// Populate the map with boundary code and isFixedPost.
				planFacility.getServiceBoundaries().forEach((String boundary) ->
						boundaryCodeToFixedPostMap.put(boundary, isFixedPost)
				);
			}
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
	 * @param fileStoreId              The ID of the file in the file store.
	 * @param attributeNameVsDataTypeMap Mapping of attribute names to their data types.
	 * @param boundaryCodeList List of boundary codes.
	 */
	protected void processRows(PlanConfigurationRequest planConfigurationRequest, Sheet sheet,
							 String fileStoreId, Map<String, Object> attributeNameVsDataTypeMap,
							 List<String> boundaryCodeList, Map<String, Object> boundaryCodeToCensusAdditionalDetails,
							   boolean skipFixedPost) {

		// Create a Map of Boundary Code to Facility's fixed post detail.
		// Only fetch fixed post details if the flag is false
		Map<String, Boolean> boundaryCodeToFixedPostMap = skipFixedPost
				? Collections.emptyMap() // Provide an empty map if skipping
				: fetchFixedPostDetails(planConfigurationRequest);
		performRowLevelCalculations(planConfigurationRequest, sheet, fileStoreId, attributeNameVsDataTypeMap, boundaryCodeList, boundaryCodeToFixedPostMap, boundaryCodeToCensusAdditionalDetails, skipFixedPost);
	}

	private void processRowsForCensusRecords(PlanConfigurationRequest planConfigurationRequest, Sheet sheet, String fileStoreId, Map<String, Object> attributeNameVsDataTypeMap, List<String> boundaryCodeList, String hierarchyType) {
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		// Same again
		Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
				.filter(rm -> rm.getFilestoreId().equals(fileStoreId))
				.filter(rm -> rm.getActive().equals(Boolean.TRUE))
				.collect(Collectors.toMap(
						ResourceMapping::getMappedTo,
						ResourceMapping::getMappedFrom,
						(existing, replacement) -> existing,
						LinkedHashMap::new
				));

		// map of column name and index
		Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

		//All columns before boundary are strings so separate checking for both
		Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
				parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

		//First row values header row, save it
		Row firstRow = null;

		for (Row row : sheet) {
			if (parsingUtil.isRowEmpty(row))
				continue;

			if (row.getRowNum() == 0) {
				//save
				firstRow = row;
				continue;
			}
			// validation
			validateRows(indexOfBoundaryCode, row, firstRow, attributeNameVsDataTypeMap, mappedValues, mapOfColumnNameAndIndex,
					planConfigurationRequest, boundaryCodeList, sheet);
			// converting into json object , easy to edit then convert back into row
			JsonNode currentRow = createFeatureNodeFromRow(row, mapOfColumnNameAndIndex);
			// creating
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
    List<String> getBoundaryCodeList(PlanConfigurationRequest planConfigurationRequest,
                                     CampaignResponse campaign) {
		BoundarySearchResponse boundarySearchResponse = boundaryUtil.search(planConfigurationRequest.getPlanConfiguration().getTenantId(),
				campaign.getCampaign().get(0).getHierarchyType(), planConfigurationRequest);
		List<String> boundaryList = new ArrayList<>();

		if (ObjectUtils.isEmpty(boundarySearchResponse.getTenantBoundary())) {
			throw new CustomException(BOUNDARY_CODE_NOT_FOUND_CODE, BOUNDARY_CODE_NOT_FOUND_MESSAGE);
		}

		return getAllBoundaryPresentforHierarchyType(
				boundarySearchResponse.getTenantBoundary().get(0).getBoundary(), boundaryList);
	}

	/**
	 * Prepares a mapping of attribute names to their corresponding indices or data types based on configuration and MDMS data.
	 *
	 * @param fileStoreId The ID of the uploaded file in the file store.
	 * @param campaign The campaign response object containing campaign details.
	 * @param planConfig The configuration details specific to the plan.
	 * @return A map of attribute names to their corresponding indices or data types.
	 */

	public Map<String, Object> prepareAttributeVsIndexMap(String fileStoreId, CampaignResponse campaign, PlanConfiguration planConfig, Object mdmsData) {
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
	 * @param fileStoreId The ID of the uploaded file in the file store.
	 * @param attributeNameVsDataTypeMap Mapping of attribute names to their data types.
	 * @param boundaryCodeList List of boundary codes.
	 */
	private void performRowLevelCalculations(PlanConfigurationRequest planConfigurationRequest, Sheet sheet,
			String fileStoreId,	Map<String, Object> attributeNameVsDataTypeMap, List<String> boundaryCodeList,
											 Map<String, Boolean> boundaryCodeToFixedPostMap, Map<String, Object> boundaryCodeToCensusAdditionalDetails, boolean skipFixedPost)  {
		Row firstRow = null;
		PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
		Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
				.filter(rm -> rm.getFilestoreId().equals(fileStoreId))
				.filter(rm -> rm.getActive().equals(Boolean.TRUE))
				.collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
		Map<String, BigDecimal> assumptionValueMap = calculationUtil
				.convertAssumptionsToMap(planConfig.getAssumptions());
		Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

		Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
				parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

		List<MixedStrategyOperationLogic> mixedStrategyOperationLogicList = mixedStrategyUtil
				.fetchMixedStrategyOperationLogicFromMDMS(planConfigurationRequest);

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

			// Get Boundary Code for the current row.
			String boundaryCode = row.getCell(indexOfBoundaryCode).getStringCellValue();

			// Skip processing the result map for mixed strategies if the flag is true
			if (!skipFixedPost) {
				mixedStrategyUtil.processResultMap(resultMap, planConfig.getOperations(),
						mixedStrategyUtil.getCategoriesNotAllowed(boundaryCodeToFixedPostMap.get(boundaryCode),
								planConfig, mixedStrategyOperationLogicList));
			}

			if(!planConfigurationRequest.getPlanConfiguration().getStatus().equalsIgnoreCase(DRAFT_STATUS))
				planUtil.create(planConfigurationRequest, feature, resultMap, boundaryCodeToCensusAdditionalDetails);

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
    String uploadConvertedFile(File convertedFile, String tenantId) {
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
	 * Creates a JSON feature node from a given {@code Census} object.
	 *
	 * @param census The census object containing boundary code and additional fields.
	 * @return A jsonNode representing the feature node with relevant properties.
	 */
	private JsonNode createFeatureNodeFromCensus(Census census) {
		ObjectNode featureNode = objectMapper.createObjectNode();
		ObjectNode propertiesNode = featureNode.putObject("properties");

		// Set boundaryCode
		propertiesNode.put(BOUNDARY_CODE, census.getBoundaryCode());

		// Extract additionalField with "CONFIRMED_" in the key
		census.getAdditionalFields().stream()
				.filter(field -> field.getKey().startsWith(CONFIRMED_KEY)) // Filter relevant fields
				.forEach(field -> propertiesNode.put(
						field.getKey().substring(CONFIRMED_KEY.length()), // Remove prefix
						field.getValue()));

		return featureNode;
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
			//before boundary index
			validateTillBoundaryCode(indexOfBoundaryCode, row, columnHeaderRow);
			//at boundary and after boundary index
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
					log.debug("CELL TYPE - " + String.valueOf(cell.getCellType()));
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
						} else if (isRequired){
							log.info(ServiceConstants.INPUT_IS_NOT_VALID + (row.getRowNum() + 1) + " and cell/column "
									+ columnName);
							throw new CustomException(Integer.toString(HttpStatus.INTERNAL_SERVER_ERROR.value()),
									ServiceConstants.INPUT_IS_NOT_VALID + row.getRowNum() + " and cell " + columnName);
						}
						continue;
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
	 * Retrieves a Workbook from the given filestore ID and tenant ID.
	 *
	 * @param filestoreId the filestore identifier
	 * @param tenantId the tenant identifier
	 * @return the Workbook extracted from the filestore
	 * @throws CustomException if the file is not found or cannot be read
	 */
	public Workbook getWorkbookFromFilestoreId(String filestoreId, String tenantId) {
		File file = parsingUtil.convertByteArrayToFile(
				filestoreUtil.getFileByteArray(tenantId, filestoreId),
				ServiceConstants.FILE_EXTENSION
		);

		if (file == null || !file.exists()) {
			log.error(FILE_NOT_FOUND_LOG, filestoreId, tenantId);
			throw new CustomException(FILE_NOT_FOUND_CODE,
					String.format(FILE_NOT_FOUND_MESSAGE, filestoreId, tenantId));
		}

		try {
			return new XSSFWorkbook(file);
		} catch (IOException | InvalidFormatException e) {
			log.error("Error while reading the workbook from file: {}", file.getAbsolutePath(), e);
			throw new CustomException(WORKBOOK_READ_ERROR_CODE, WORKBOOK_READ_ERROR_MESSAGE);
		}
	}

}