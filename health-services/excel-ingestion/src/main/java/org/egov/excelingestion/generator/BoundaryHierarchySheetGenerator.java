package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for boundary hierarchy sheet - uses ExcelPopulator approach
 */
@Component
@Slf4j
public class BoundaryHierarchySheetGenerator implements IExcelPopulatorSheetGenerator {

    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;

    public BoundaryHierarchySheetGenerator(BoundaryService boundaryService, BoundaryUtil boundaryUtil,
                                          MDMSService mdmsService, CampaignService campaignService,
                                          CustomExceptionHandler exceptionHandler,
                                          SchemaColumnDefUtil schemaColumnDefUtil) {
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
    }

    @Override
    public SheetGenerationResult generateSheetData(SheetGenerationConfig config,
                                                 GenerateResource generateResource,
                                                 RequestInfo requestInfo,
                                                 Map<String, String> localizationMap) {
        
        log.info("Generating boundary hierarchy sheet data for hierarchy: {}", generateResource.getHierarchyType());
        
        try {
            String tenantId = generateResource.getTenantId();
            String hierarchyType = generateResource.getHierarchyType();
            
            // Fetch boundary hierarchy data
            BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
            List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
            
            // Fetch boundary relationship data
            BoundarySearchResponse boundaryRelationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
            Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(boundaryRelationshipData);
            
            // Get level types
            List<String> levelTypes = new ArrayList<>();
            for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
                levelTypes.add(hierarchyRelation.getBoundaryType());
            }
            
            // Check if boundaries are configured in additionalDetails
            if (generateResource.getBoundaries() == null || generateResource.getBoundaries().isEmpty()) {
                log.info("No boundaries configured in additionalDetails for boundary hierarchy sheet, returning empty result");
                return SheetGenerationResult.builder()
                        .columnDefs(new ArrayList<>())
                        .data(new ArrayList<>())
                        .build();
            }
            
            // Filter boundaries based on additionalDetails configuration
            List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                    generateResource.getBoundaries(), codeToEnrichedBoundary, levelTypes);
            
            // Extract projectType from additionalDetails if present
            String projectType = null;
            if (generateResource.getAdditionalDetails() != null 
                && generateResource.getAdditionalDetails().containsKey("projectType")) {
                projectType = (String) generateResource.getAdditionalDetails().get("projectType");
            }
            
            // Fetch schema columns if projectType exists
            List<ColumnDef> schemaColumns = new ArrayList<>();
            if (projectType != null && !projectType.isEmpty()) {
                schemaColumns = fetchSchemaColumns(projectType, generateResource.getTenantId(), requestInfo);
            }
            
            // Create column definitions
            List<ColumnDef> boundaryColumns = createBoundaryHierarchyColumnDefs(hierarchyRelations, hierarchyType, schemaColumns);
            
            // Create data
            List<Map<String, Object>> boundaryData = getBoundaryHierarchyDataFromFiltered(
                    filteredBoundaries, hierarchyRelations, hierarchyType, localizationMap, schemaColumns);
            
            // Fetch and merge existing campaign data for targets if reference ID is provided
            String referenceId = generateResource.getReferenceId();
            if (referenceId != null && !referenceId.isEmpty()) {
                String campaignNumber = getCampaignNumberFromReferenceId(referenceId, generateResource.getTenantId(), requestInfo);
                if (campaignNumber != null && !campaignNumber.isEmpty()) {
                    boundaryData = mergeExistingCampaignData(boundaryData, campaignNumber, 
                            generateResource.getTenantId(), requestInfo, schemaColumns);
                }
            }
            
            return SheetGenerationResult.builder()
                    .columnDefs(boundaryColumns)
                    .data(boundaryData)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating boundary hierarchy sheet data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate boundary hierarchy sheet data", e);
        }
    }
    
    private List<ColumnDef> fetchSchemaColumns(String projectType, String tenantId, RequestInfo requestInfo) {
        List<ColumnDef> columns = new ArrayList<>();
        String schemaName = "target-" + projectType;
        
        try {
            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);
            
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    String schemaJson = mapper.writeValueAsString(properties);
                    columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
                    log.info("Successfully fetched {} schema columns for projectType: {}", columns.size(), projectType);
                }
            } else {
                log.warn("No schema found for: {}", schemaName);
            }
        } catch (Exception e) {
            log.error("Error fetching schema for projectType {}: {}", projectType, e.getMessage());
            // Don't throw exception, just return empty columns
        }
        
        return columns;
    }
    
    
    
    private List<ColumnDef> createBoundaryHierarchyColumnDefs(List<BoundaryHierarchyChild> hierarchyRelations, 
                                                             String hierarchyType, 
                                                             List<ColumnDef> schemaColumns) {
        List<ColumnDef> columns = new ArrayList<>();
        
        // Create columns for each hierarchy level using exact project-factory pattern
        // All boundary columns are fully locked (freezeColumn: true)
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String boundaryType = hierarchyRelations.get(i).getBoundaryType();
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
            
            columns.add(ColumnDef.builder()
                    .name(columnName)
                    .orderNumber(i + 1)
                    .width(50)
                    .colorHex("#93c47d")
                    .freezeColumn(true) // Lock boundary columns completely
                    .build());
        }
        
        // Add hidden boundary code column
        columns.add(ColumnDef.builder()
                .name("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")
                .orderNumber(hierarchyRelations.size() + 1)
                .width(80)
                .hideColumn(true)
                .freezeColumn(true)
                .adjustHeight(true)
                .build());
        
        // Add schema columns after boundary columns
        int currentOrderNumber = hierarchyRelations.size() + 2;
        for (ColumnDef schemaCol : schemaColumns) {
            // Create a new column with adjusted order number
            columns.add(ColumnDef.builder()
                    .name(schemaCol.getName())
                    .type(schemaCol.getType())
                    .description(schemaCol.getDescription())
                    .colorHex(schemaCol.getColorHex())
                    .orderNumber(currentOrderNumber++)
                    .freezeColumnIfFilled(schemaCol.isFreezeColumnIfFilled())
                    .hideColumn(schemaCol.isHideColumn())
                    .required(schemaCol.isRequired())
                    .pattern(schemaCol.getPattern())
                    .minimum(schemaCol.getMinimum())
                    .maximum(schemaCol.getMaximum())
                    .minLength(schemaCol.getMinLength())
                    .maxLength(schemaCol.getMaxLength())
                    .freezeColumn(schemaCol.isFreezeColumn())
                    .adjustHeight(schemaCol.isAdjustHeight())
                    .width(schemaCol.getWidth())
                    .unFreezeColumnTillData(schemaCol.isUnFreezeColumnTillData())
                    .freezeTillData(schemaCol.isFreezeTillData())
                    .enumValues(schemaCol.getEnumValues())
                    .multiSelectDetails(schemaCol.getMultiSelectDetails())
                    .build());
        }
        
        
        return columns;
    }
    
    private List<Map<String, Object>> getBoundaryHierarchyDataFromFiltered(List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                                          List<BoundaryHierarchyChild> hierarchyRelations,
                                                                          String hierarchyType,
                                                                          Map<String, String> localizationMap,
                                                                          List<ColumnDef> schemaColumns) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        int lastLevelIndex = hierarchyRelations.size() - 1;
        
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> boundaryPath = boundary.getBoundaryPath();
            
            // Only include boundaries that have data at the last level (leaf boundaries)
            if (boundaryPath.size() > lastLevelIndex &&
                boundaryPath.get(lastLevelIndex) != null &&
                !boundaryPath.get(lastLevelIndex).isEmpty()) {
                
                Map<String, Object> row = new HashMap<>();
                
                // Fill columns based on hierarchy levels
                for (int i = 0; i < hierarchyRelations.size(); i++) {
                    String boundaryType = hierarchyRelations.get(i).getBoundaryType();
                    String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
                    String boundaryCode = i < boundaryPath.size() ? boundaryPath.get(i) : "";
                    String boundaryName = "";
                    
                    if (boundaryCode != null && !boundaryCode.isEmpty()) {
                        boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    }
                    
                    row.put(columnName, boundaryName);
                }
                
                // Add the hidden boundary code column
                String lastLevelBoundaryCode = boundaryPath.get(lastLevelIndex);
                row.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", lastLevelBoundaryCode);
                
                // Add schema columns with null/default values (to be filled by users)
                for (ColumnDef schemaCol : schemaColumns) {
                    row.put(schemaCol.getName(), null);
                }
                
                data.add(row);
            }
        }
        
        return data;
    }
    
    private String getCampaignNumberFromReferenceId(String referenceId, String tenantId, RequestInfo requestInfo) {
        try {
            log.info("Searching campaign by reference ID: {}", referenceId);
            CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(referenceId, tenantId, requestInfo);
            
            if (campaign != null) {
                String campaignNumber = campaign.getCampaignNumber();
                log.info("Found campaign number: {} for reference ID: {}", campaignNumber, referenceId);
                return campaignNumber;
            } else {
                log.warn("No campaign found for reference ID: {}", referenceId);
                return null;
            }
        } catch (Exception e) {
            log.error("Error fetching campaign for reference ID {}: {}", referenceId, e.getMessage());
            return null;
        }
    }
    
    private List<Map<String, Object>> mergeExistingCampaignData(List<Map<String, Object>> boundaryData, 
                                                               String campaignNumber, String tenantId, 
                                                               RequestInfo requestInfo, List<ColumnDef> schemaColumns) {
        try {
            log.info("Fetching existing campaign data for boundary/target type, campaign: {}", campaignNumber);
            
            // Search for existing boundary/target campaign data
            // Get all boundary codes to search for
            List<String> boundaryCodes = new ArrayList<>(); 
            for (Map<String, Object> row : boundaryData) {
                String boundaryCode = (String) row.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
                if (boundaryCode != null && !boundaryCode.isEmpty()) {
                    boundaryCodes.add(boundaryCode);
                }
            }
            
            List<Map<String, Object>> campaignDataResponse = campaignService.searchCampaignDataByUniqueIdentifiers(
                    boundaryCodes, "boundary", null, campaignNumber, tenantId, requestInfo);
            
            if (campaignDataResponse == null || campaignDataResponse.isEmpty()) {
                log.info("No existing boundary/target data found for campaign: {}", campaignNumber);
                return boundaryData;
            }
            
            // Extract the actual data object from campaign data records
            List<Map<String, Object>> existingData = new ArrayList<>();
            for (Map<String, Object> record : campaignDataResponse) {
                if (record.get("data") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataObject = (Map<String, Object>) record.get("data");
                    if (dataObject != null) {
                        existingData.add(dataObject);
                    }
                }
            }
            
            if (existingData.isEmpty()) {
                log.info("No existing boundary/target data found in campaign data records");
                return boundaryData;
            }
            
            // Create a map of boundary code to existing data for fast lookup
            Map<String, Map<String, Object>> existingDataMap = new HashMap<>();
            for (Map<String, Object> data : existingData) {
                String boundaryCode = (String) data.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
                if (boundaryCode != null && !boundaryCode.isEmpty()) {
                    existingDataMap.put(boundaryCode, data);
                }
            }
            
            // Merge existing data with boundary data
            for (Map<String, Object> row : boundaryData) {
                String boundaryCode = (String) row.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
                if (boundaryCode != null && existingDataMap.containsKey(boundaryCode)) {
                    Map<String, Object> existingRow = existingDataMap.get(boundaryCode);
                    
                    // Merge schema column values from existing data
                    for (ColumnDef schemaCol : schemaColumns) {
                        String columnName = schemaCol.getName();
                        if (existingRow.containsKey(columnName)) {
                            row.put(columnName, existingRow.get(columnName));
                        }
                    }
                }
            }
            
            log.info("Successfully merged existing campaign data for {} boundary rows", boundaryData.size());
            return boundaryData;
            
        } catch (Exception e) {
            log.error("Error merging existing campaign data for campaign {}: {}", campaignNumber, e.getMessage());
            // Return original data if merge fails
            return boundaryData;
        }
    }
}