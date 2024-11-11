package org.egov.processor.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.processor.config.Configuration;
import org.egov.processor.web.models.LocaleResponse;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.processor.web.models.census.Census;
import org.egov.processor.web.models.census.CensusResponse;
import org.egov.processor.web.models.census.CensusSearchCriteria;
import org.egov.processor.web.models.census.CensusSearchRequest;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class EnrichmentUtil {

    private MdmsV2Util mdmsV2Util;

    private LocaleUtil localeUtil;

    private ParsingUtil parsingUtil;

    private CensusUtil censusUtil;

    private Configuration config;
//    private MultiStateInstanceUtil centralInstanceUtil;

    public EnrichmentUtil(MdmsV2Util mdmsV2Util, LocaleUtil localeUtil, ParsingUtil parsingUtil, CensusUtil censusUtil, Configuration config) {
        this.mdmsV2Util = mdmsV2Util;
        this.localeUtil = localeUtil;
//        this.centralInstanceUtil = centralInstanceUtil;
        this.parsingUtil = parsingUtil;
        this.censusUtil = censusUtil;
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
//        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlanConfiguration().getTenantId());
        String rootTenantId = request.getPlanConfiguration().getTenantId().split("\\.")[0];
        String uniqueIndentifier = BOUNDARY + DOT_SEPARATOR  + MICROPLAN_PREFIX + campaignType;
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_ADMIN_CONSOLE_MODULE_NAME + DOT_SEPARATOR + MDMS_SCHEMA_ADMIN_SCHEMA, uniqueIndentifier);
        List<String> columnNameList = parsingUtil.extractPropertyNamesFromAdminSchema(mdmsV2Data.get(0).getData());

        List<ResourceMapping> resourceMappingList = new ArrayList<>();
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
            if (row.getRowNum() == 0) continue; // Skip header row

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
        log.info("Successfully update file with approved census data.");
        }
    }

    public List<String> getBoundaryCodesFromTheSheet(Sheet sheet, PlanConfigurationRequest planConfigurationRequest, String fileStoreId) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();

        Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
                .filter(f -> f.getFilestoreId().equals(fileStoreId))
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

    public List<Census> getCensusRecordsForEnrichment(PlanConfigurationRequest planConfigurationRequest, List<String> boundaryCodes) {
        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        CensusSearchCriteria censusSearchCriteria = CensusSearchCriteria.builder()
                .tenantId(planConfig.getTenantId())
                .areaCodes(boundaryCodes)
                .source(planConfig.getId()).build();

        CensusSearchRequest censusSearchRequest = CensusSearchRequest.builder()
                .censusSearchCriteria(censusSearchCriteria)
                .requestInfo(planConfigurationRequest.getRequestInfo()).build();

        CensusResponse censusResponse = censusUtil.fetchCensusRecords(censusSearchRequest);

        if(censusResponse.getCensus().isEmpty())
            throw new CustomException(NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_CODE, NO_CENSUS_FOUND_FOR_GIVEN_DETAILS_MESSAGE);

        return censusResponse.getCensus();

    }

    private BigDecimal getEditableValue(Census census, String key) {
        return census.getAdditionalFields().stream()
                .filter(field -> field.getEditable() && key.equals(field.getKey()))  // Filter by editability and matching key
                .map(field -> field.getValue())
                .findFirst()
                .orElse(null);
    }


}
