package org.egov.processor.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.processor.config.Configuration;
import org.egov.processor.web.PlanResponse;
import org.egov.processor.web.PlanSearchCriteria;
import org.egov.processor.web.PlanSearchRequest;
import org.egov.processor.web.models.*;
import org.egov.processor.web.models.census.Census;
import org.egov.processor.web.models.census.CensusResponse;
import org.egov.processor.web.models.census.CensusSearchCriteria;
import org.egov.processor.web.models.census.CensusSearchRequest;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.processor.config.ServiceConstants.*;
import static org.egov.processor.config.ErrorConstants.*;

@Component
@Slf4j
public class EnrichmentUtil {

    private MdmsV2Util mdmsV2Util;

    private LocaleUtil localeUtil;

    private ParsingUtil parsingUtil;

    private CensusUtil censusUtil;

    private PlanUtil planUtil;

    private Configuration config;

    public EnrichmentUtil(MdmsV2Util mdmsV2Util, LocaleUtil localeUtil, ParsingUtil parsingUtil, CensusUtil censusUtil, PlanUtil planUtil, Configuration config) {
        this.mdmsV2Util = mdmsV2Util;
        this.localeUtil = localeUtil;
        this.parsingUtil = parsingUtil;
        this.censusUtil = censusUtil;
        this.planUtil = planUtil;
        this.config = config;
    }

    /**
     * Enriches the `PlanConfiguration` with resource mappings based on MDMS data and locale messages.
     *
     * @param request The request containing the configuration to enrich.
     * @param localeResponse The response containing locale messages.
     * @param campaignType The campaign type identifier.
     * @param fileStoreId The associated file store ID.
     */
    public void enrichResourceMapping(PlanConfigurationRequest request, LocaleResponse localeResponse, String campaignType, String fileStoreId)
    {
        String rootTenantId = request.getPlanConfiguration().getTenantId().split("\\.")[0];
        String uniqueIndentifier = BOUNDARY + DOT_SEPARATOR  + MICROPLAN_PREFIX + campaignType;
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_ADMIN_CONSOLE_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_ADMIN_SCHEMA, uniqueIndentifier);
        List<String> columnNameList = parsingUtil.extractPropertyNamesFromAdminSchema(mdmsV2Data.get(0).getData());

        List<ResourceMapping> resourceMappingList = !CollectionUtils.isEmpty(request.getPlanConfiguration().getResourceMapping()) ?
                request.getPlanConfiguration().getResourceMapping() : new ArrayList<>();

        resourceMappingList.forEach(resourceMapping -> resourceMapping.setActive(Boolean.FALSE));

        for(String columnName : columnNameList) {
            ResourceMapping resourceMapping = ResourceMapping
                    .builder()
                    .filestoreId(fileStoreId)
                    .mappedTo(columnName)
                    .active(Boolean.TRUE)
                    .mappedFrom(localeUtil.localeSearch(localeResponse.getMessages(), columnName))
                    .build();
            UUIDEnrichmentUtil.enrichRandomUuid(resourceMapping, "id");
            resourceMappingList.add(resourceMapping);
        }

        //enrich plan configuration with enriched resource mapping list
        request.getPlanConfiguration().setResourceMapping(resourceMappingList);

    }

    /**
     * Enriches the given Excel sheet with approved census records.
     *
     * @param sheet                               The Excel sheet to be enriched.
     * @param planConfigurationRequest            The request containing plan configuration details.
     * @param fileStoreId                         The identifier of the uploaded file.
     * @param mappedValues                        A mapping between census fields and sheet column names.
     * @throws CustomException                    If no census records are found for the given boundary codes.
     */
    public void enrichsheetWithApprovedCensusRecords(Sheet sheet, PlanConfigurationRequest planConfigurationRequest, String fileStoreId, Map<String, String> mappedValues) {
        List<String> boundaryCodes = getBoundaryCodesFromTheSheet(sheet, planConfigurationRequest, fileStoreId);

        Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
        Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
                parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

        //Getting census records for the list of boundaryCodes
        List<Census> censusList = getCensusRecordsForEnrichment(planConfigurationRequest, boundaryCodes);

        // Create a map from boundaryCode to Census for quick lookups
        Map<String, Census> censusMap = censusList.stream()
                .collect(Collectors.toMap(Census::getBoundaryCode, census -> census));


        for(Row row: sheet) {
            parsingUtil.printRow(sheet, row);
            // Skip the header row and empty rows
            if (row.getRowNum() == 0 || parsingUtil.isRowEmpty(row)) {
                continue;
            }

            // Get the boundaryCode in the current row
            Cell boundaryCodeCell = row.getCell(indexOfBoundaryCode);
            String boundaryCode = boundaryCodeCell.getStringCellValue();

            Census census = censusMap.get(boundaryCode);

            if (census != null) {
                // For each field in the sheetToCensusMap, update the cell if the field is editable
                for (Map.Entry<String, String> entry : mappedValues.entrySet()) {
                    String censusKey = entry.getKey();
                    String sheetColumn = entry.getValue();

                    if(config.getCensusAdditionalFieldOverrideKeys().contains(censusKey))
                        continue;
                    censusKey = config.getCensusAdditionalPrefixAppendKeys().contains(censusKey) ? CONFIRMED_KEY + censusKey : censusKey;

                    // Get the column index from the mapOfColumnNameAndIndex
                    Integer columnIndex = mapOfColumnNameAndIndex.get(sheetColumn);
                    if (columnIndex != null) {
                        // Get the value for this field in the census, if editable
                        BigDecimal editableValue = getEditableValue(census, censusKey);

                        if(ObjectUtils.isEmpty(editableValue)) continue;

                        Cell cell = row.getCell(columnIndex);
                        if (cell == null) {
                            cell = row.createCell(columnIndex);
                        }
                        cell.setCellValue(editableValue.doubleValue());

                    }
                }
            }
        }
        log.info("Successfully updated file with approved census data.");
    }

    /**
     * Extracts boundary codes from the given Excel sheet based on the plan configuration request.
     *
     * @param sheet                    The Excel sheet to extract boundary codes from.
     * @param planConfigurationRequest  The request containing plan configuration details.
     * @param fileStoreId               The identifier of the uploaded file.
     * @return                           A list of boundary codes extracted from the sheet.
     */
    public List<String> getBoundaryCodesFromTheSheet(Sheet sheet, PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();

        Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
                .filter(rm -> rm.getFilestoreId().equals(fileStoreId))
                .filter(rm -> rm.getActive().equals(Boolean.TRUE))
                .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));

        Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);

        Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
                parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

        List<String> boundaryCodes = new ArrayList<>();

        for (Row row : sheet) {
            // Skip the header row and empty rows
            if (row.getRowNum() == 0 || parsingUtil.isRowEmpty(row)) {
                continue;
            }

            // Get the boundary code cell
            Cell boundaryCodeCell = row.getCell(indexOfBoundaryCode);

            // Check if the cell is non-empty and collect its value
            if (boundaryCodeCell != null && boundaryCodeCell.getCellType() == CellType.STRING) {
                String boundaryCode = boundaryCodeCell.getStringCellValue().trim();
                if (!boundaryCode.isEmpty()) {
                    boundaryCodes.add(boundaryCode);
                }
            }
        }

        return boundaryCodes;
    }

    /**
     * Retrieves census records for a given list of boundary codes.
     *
     * @param planConfigurationRequest  The request containing plan configuration details.
     * @param boundaryCodes             A list of boundary codes for which census records are required.
     * @return                          A list of census records matching the given boundary codes.
     * @throws CustomException          If no census records are found for the given boundary codes.
     */
    public List<Census> getCensusRecordsForEnrichment(PlanConfigurationRequest planConfigurationRequest, List<String> boundaryCodes) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        CensusSearchCriteria censusSearchCriteria = CensusSearchCriteria.builder()
                .tenantId(planConfig.getTenantId())
                .areaCodes(boundaryCodes)
                .limit(boundaryCodes.size())
                .source(planConfig.getId()).build();

        CensusSearchRequest censusSearchRequest = CensusSearchRequest.builder()
                .censusSearchCriteria(censusSearchCriteria)
                .requestInfo(planConfigurationRequest.getRequestInfo()).build();

        CensusResponse censusResponse = censusUtil.fetchCensusRecords(censusSearchRequest);

        if(censusResponse.getCensus().isEmpty())
            throw new CustomException(NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_CODE, NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_MESSAGE);

        return censusResponse.getCensus();

    }

    /**
     * Retrieves the editable value of a given field from a census record.
     *
     * @param census  The census record to retrieve the value from.
     * @param key     The key representing the field in the census record.
     * @return BigDecimal The editable value if present, otherwise null.
     */
    private BigDecimal getEditableValue(Census census, String key) {
        return census.getAdditionalFields().stream()
                .filter(field -> field.getEditable() && key.equals(field.getKey()))  // Filter by editability and matching key
                .map(field -> field.getValue())
                .findFirst()
                .orElse(null);
    }

    /**
     * Enriches the given Excel sheet with approved plan estimates.
     *
     * @param sheet                    The Excel sheet to be enriched.
     * @param planConfigurationRequest  The request containing plan configuration details.
     * @param fileStoreId               The identifier of the uploaded file.
     * @param mappedValues              A mapping between plan fields and sheet column names.
     * @throws CustomException          If no plan records are found for the given boundary codes.
     */
    public void enrichsheetWithApprovedPlanEstimates(Sheet sheet, PlanConfigurationRequest planConfigurationRequest, String fileStoreId, Map<String, String> mappedValues) {
        List<String> boundaryCodes = getBoundaryCodesFromTheSheet(sheet, planConfigurationRequest, fileStoreId);

        //Getting plan records for the list of boundaryCodes
        List<Plan> planList = getPlanRecordsForEnrichment(planConfigurationRequest, boundaryCodes);

        // Create a map from boundaryCode to Plan for quick lookups
        Map<String, Plan> planMap = planList.stream()
                .collect(Collectors.toMap(Plan::getLocality, plan -> plan));

        List<String> outputColumnList = planList.get(0).getResources().stream()
                .map(Resource::getResourceType)
                .toList();

        // Setting column headers for the calculated plan estimate resources.
        outputColumnList.forEach(output -> {
            Cell outputColHeader = sheet.getRow(0).createCell(sheet.getRow(0).getLastCellNum(), CellType.STRING);
            outputColHeader.setCellValue(output);
        });

        Map<String, Integer> mapOfColumnNameAndIndex = parsingUtil.getAttributeNameIndexFromExcel(sheet);
        Integer indexOfBoundaryCode = parsingUtil.getIndexOfBoundaryCode(0,
                parsingUtil.sortColumnByIndex(mapOfColumnNameAndIndex), mappedValues);

        for(Row row: sheet) {
            // Skip the header row and empty rows
            if (row.getRowNum() == 0 || parsingUtil.isRowEmpty(row)) {
                continue;
            }
            // Get the boundaryCode in the current row
            Cell boundaryCodeCell = row.getCell(indexOfBoundaryCode);
            String boundaryCode = boundaryCodeCell.getStringCellValue();

            Plan planEstimate = planMap.get(boundaryCode);

            if (planEstimate != null) {
                Map<String, BigDecimal> resourceTypeToEstimatedNumberMap = new HashMap<>();

                // If resources are not empty, iterate over each resource and map resourceType with it's estimatedValue.
                if(!CollectionUtils.isEmpty(planEstimate.getResources()))
                    planEstimate.getResources().forEach(resource ->
                            resourceTypeToEstimatedNumberMap.put(resource.getResourceType(), resource.getEstimatedNumber()));


                // Iterate over each output column to update the row cells with resource values
                for (String resourceType : outputColumnList) {
                    BigDecimal estimatedValue = resourceTypeToEstimatedNumberMap.get(resourceType);

                    if (estimatedValue != null) {
                        // Get the index of the column to update
                        Integer columnIndex = mapOfColumnNameAndIndex.get(resourceType);
                        if (columnIndex != null) {
                            // Update the cell with the resource value
                            Cell cell = row.getCell(columnIndex);
                            if (cell == null) {
                                cell = row.createCell(columnIndex);
                            }
                            cell.setCellValue(estimatedValue.doubleValue());
                            cell.getCellStyle().setLocked(config.isEnableLockOnPlanEstimationSheet());
                        }
                    } else {
                        // If estimatedValue is null, set the cell to empty
                        Integer columnIndex = mapOfColumnNameAndIndex.get(resourceType);
                        if (columnIndex != null) {
                            // Ensure the cell is empty
                            Cell cell = row.getCell(columnIndex);
                            if (cell == null) {
                                cell = row.createCell(columnIndex);
                            }
                            cell.setCellValue(NOT_APPLICABLE); // Set as not applicable
                            cell.getCellStyle().setLocked(config.isEnableLockOnPlanEstimationSheet());
                        }
                    }
                }
            }

            log.info("Successfully update file with approved census data.");
        }
    }


    /**
     * Retrieves plan records for a given list of boundary codes.
     *
     * @param planConfigurationRequest  The request containing plan configuration details.
     * @param boundaryCodes             A list of boundary codes for which plan records are required.
     * @return List<Plan>               A list of plan records matching the given boundary codes.
     * @throws CustomException          If no plan records are found for the given boundary codes.
     */
    public List<Plan> getPlanRecordsForEnrichment(PlanConfigurationRequest planConfigurationRequest, List<String> boundaryCodes) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        PlanSearchCriteria planSearchCriteria = PlanSearchCriteria.builder()
                .tenantId(planConfig.getTenantId())
                .locality(boundaryCodes)
                .limit(boundaryCodes.size())
                .planConfigurationId(planConfig.getId()).build();

        PlanSearchRequest planSearchRequest = PlanSearchRequest.builder()
                .planSearchCriteria(planSearchCriteria)
                .requestInfo(planConfigurationRequest.getRequestInfo()).build();

        PlanResponse planResponse = planUtil.search(planSearchRequest);

        if(planResponse.getPlan().isEmpty())
            throw new CustomException(NO_PLAN_FOUND_FOR_GIVEN_DETAILS_CODE, NO_PLAN_FOUND_FOR_GIVEN_DETAILS_MESSAGE);

        return planResponse.getPlan();
    }

    /**
     * Retrieves census records in batches based on the given plan configuration request.
     *
     * @param planConfigurationRequest The request containing the plan configuration and request info.
     * @param batchSize                The number of records to fetch in a single batch.
     * @param offset                   The starting position of records for pagination.
     * @return A list of Census records retrieved based on the search criteria.
     */
    public List<Census> getCensusRecordsInBatches(PlanConfigurationRequest planConfigurationRequest, int batchSize, int offset) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        CensusSearchCriteria censusSearchCriteria = CensusSearchCriteria.builder()
                .tenantId(planConfig.getTenantId())
                .limit(batchSize)
                .source(planConfig.getId())
                .offset(offset)
                .build();

        CensusSearchRequest censusSearchRequest = CensusSearchRequest.builder()
                .censusSearchCriteria(censusSearchCriteria)
                .requestInfo(planConfigurationRequest.getRequestInfo()).build();

        CensusResponse censusResponse = censusUtil.fetchCensusRecords(censusSearchRequest);

        return censusResponse.getCensus();
    }
}
