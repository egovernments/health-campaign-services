package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.FacilityService;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generator for facility sheet with existing campaign data support
 */
@Component
@Slf4j
public class FacilitySheetGenerator implements ISheetGenerator {

    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final FacilityService facilityService;
    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final CellProtectionManager cellProtectionManager;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ExcelDataPopulator excelDataPopulator;
    private final HierarchicalBoundaryUtil hierarchicalBoundaryUtil;

    public FacilitySheetGenerator(MDMSService mdmsService, CampaignService campaignService,
                                 FacilityService facilityService, BoundaryService boundaryService, 
                                 BoundaryUtil boundaryUtil, CellProtectionManager cellProtectionManager,
                                 CustomExceptionHandler exceptionHandler,
                                 SchemaColumnDefUtil schemaColumnDefUtil,
                                 ExcelDataPopulator excelDataPopulator,
                                 HierarchicalBoundaryUtil hierarchicalBoundaryUtil) {
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.facilityService = facilityService;
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.cellProtectionManager = cellProtectionManager;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.excelDataPopulator = excelDataPopulator;
        this.hierarchicalBoundaryUtil = hierarchicalBoundaryUtil;
    }

    @Override
    public XSSFWorkbook generateSheet(XSSFWorkbook workbook, 
                                     String sheetName, 
                                     SheetGenerationConfig config,
                                     GenerateResource generateResource, 
                                     RequestInfo requestInfo,
                                     Map<String, String> localizationMap) {
        
        log.info("Generating facility sheet: {} for schema: {}", sheetName, config.getSchemaName());
        
        try {
            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", config.getSchemaName());
            
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, generateResource.getTenantId(), ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            
            String schemaJson = extractSchemaFromMDMSResponse(mdmsList, config.getSchemaName());
            
            if (schemaJson != null && !schemaJson.isEmpty()) {
                List<ColumnDef> columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
                
                // Generate data with existing campaign data if reference ID is provided
                List<Map<String, Object>> data = fetchExistingFacilityData(generateResource, requestInfo);
                
                // Enrich facilities with boundary paths to populate boundary columns
                if (data != null && !data.isEmpty()) {
                    enrichFacilitiesWithBoundaryPaths(data, generateResource, requestInfo, localizationMap);
                }
                
                // Create empty sheet first
                if (workbook.getSheetIndex(sheetName) >= 0) {
                    workbook.removeSheetAt(workbook.getSheetIndex(sheetName));
                }
                workbook.createSheet(sheetName);
                
                // Add boundary dropdowns first using HierarchicalBoundaryUtil
                if (shouldAddBoundaryDropdowns(generateResource)) {
                    // Get enriched boundaries from campaign service using cached function
                    List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = 
                        campaignService.getBoundariesFromCampaign(generateResource.getReferenceId(), 
                            generateResource.getTenantId(), requestInfo);
                    
                    if (campaignBoundaries != null && !campaignBoundaries.isEmpty()) {
                        
                        // Get enriched boundaries using cached function
                        List<Boundary> enrichedBoundaries = boundaryUtil.getEnrichedBoundariesFromCampaign(
                            generateResource.getId(), generateResource.getReferenceId(), 
                            generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);
                        
                        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(
                                workbook, sheetName, localizationMap, enrichedBoundaries,
                                generateResource.getHierarchyType(), generateResource.getTenantId(), requestInfo, data);
                    }
                }
                
                // Then add schema columns and data using ExcelDataPopulator
                workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, sheetName, columns, data, localizationMap);
                
                // Re-apply cell protection for freezeColumnIfFilled after all data is populated
                // This ensures that facility columns with existing data are properly frozen
                cellProtectionManager.applyCellProtection(workbook, workbook.getSheet(sheetName), columns);
            }
            
        } catch (Exception e) {
            log.error("Error generating facility sheet {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate facility sheet: " + sheetName, e);
        }
        
        return workbook;
    }
    
    private boolean shouldAddBoundaryDropdowns(GenerateResource generateResource) {
        return generateResource.getReferenceId() != null && !generateResource.getReferenceId().isEmpty()
               && generateResource.getHierarchyType() != null && !generateResource.getHierarchyType().isEmpty();
    }
    
    private String extractSchemaFromMDMSResponse(List<Map<String, Object>> mdmsList, String title) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully extracted MDMS schema for: {}", title);
                    return mapper.writeValueAsString(properties);
                }
            }
            log.warn("No MDMS data found for schema: {}", title);
        } catch (Exception e) {
            log.error("Error extracting MDMS schema {}: {}", title, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR,
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND,
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", title),
                new RuntimeException("Schema '" + title + "' not found in MDMS configuration"));
        return null;
    }
    
    private List<Map<String, Object>> fetchExistingFacilityData(GenerateResource generateResource, 
                                                                RequestInfo requestInfo) {
        String referenceId = generateResource.getReferenceId();
        if (referenceId == null || referenceId.isEmpty()) {
            log.info("No reference ID provided for facility sheet");
            return null; // Headers-only sheet
        }
        
        try {
            // Get campaign number from reference ID
            String campaignNumber = getCampaignNumberFromReferenceId(referenceId, 
                    generateResource.getTenantId(), requestInfo);
            
            if (campaignNumber == null || campaignNumber.isEmpty()) {
                log.info("No campaign found for reference ID: {}", referenceId);
                return null; // Headers-only sheet
            }
            
            // Step 1: Fetch all permanent facilities from facility API
            log.info("Fetching permanent facilities for tenant: {}", generateResource.getTenantId());
            List<Map<String, Object>> allPermanentFacilities = facilityService.fetchAllPermanentFacilities(
                    generateResource.getTenantId(), requestInfo);
            
            // Step 2: Get enriched boundary codes including children using existing boundary util
                        // Use the method that includes all boundary codes for facility filtering
                        Set<String> enrichedBoundaryCodes = boundaryUtil.getEnrichedBoundaryCodesFromCampaign(generateResource.getId(), referenceId,
                                generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);
                        log.info("Enriched boundary codes for filtering (including all boundaries): {}", enrichedBoundaryCodes);            
            // Step 3: Transform permanent facilities to sheet format and filter by enriched boundaries
            List<Map<String, Object>> transformedPermanentFacilities = transformAndFilterPermanentFacilities(
                    allPermanentFacilities, enrichedBoundaryCodes);
            
            // Step 4: Fetch existing campaign data for this campaign
            List<Map<String, Object>> existingCampaignData = fetchExistingCampaignData(
                    campaignNumber, generateResource.getTenantId(), requestInfo);
            
            // Step 5: Merge permanent facilities with existing campaign data (remove duplicates by name, keep first)
            List<Map<String, Object>> mergedData = mergeAndDeduplicateFacilities(
                    transformedPermanentFacilities, existingCampaignData);
            
            log.info("Final facility data count: {} (permanent: {}, campaign: {})", 
                    mergedData.size(), transformedPermanentFacilities.size(), existingCampaignData.size());
            
            return mergedData;
            
        } catch (Exception e) {
            log.error("Error fetching facility data: {}", e.getMessage(), e);
            return null; // Headers-only sheet on error
        }
    }
    
    private List<Map<String, Object>> transformAndFilterPermanentFacilities(
            List<Map<String, Object>> permanentFacilities, Set<String> enrichedBoundaryCodes) {
        
        List<Map<String, Object>> transformedFacilities = new ArrayList<>();
        
        for (Map<String, Object> facility : permanentFacilities) {
            try {
                // Transform permanent facility to sheet format using same pattern as project-factory
                Map<String, Object> transformedFacility = transformPermanentFacilityToSheetFormat(facility);
                
                // Filter by enriched boundary codes - only include facilities within campaign boundaries
                String facilityBoundaryCode = getFacilityBoundaryCode(facility);
                if (facilityBoundaryCode != null && enrichedBoundaryCodes.contains(facilityBoundaryCode)) {
                    transformedFacilities.add(transformedFacility);
                    log.debug("Included facility {} with boundary code {}", 
                            transformedFacility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME"), facilityBoundaryCode);
                } else {
                    log.debug("Filtered out facility {} with boundary code {} (not in enriched campaign boundaries)", 
                            facility.get("name"), facilityBoundaryCode);
                }
                
            } catch (Exception e) {
                log.warn("Error transforming facility {}: {}", facility.get("id"), e.getMessage());
            }
        }
        
        return transformedFacilities;
    }
    
    private String getFacilityBoundaryCode(Map<String, Object> facility) {
        // Extract boundary code from facility address following project-factory pattern
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) facility.get("address");
            if (address != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> locality = (Map<String, Object>) address.get("locality");
                if (locality != null) {
                    return (String) locality.get("code");
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting boundary code from facility {}: {}", facility.get("id"), e.getMessage());
        }
        return null;
    }
    
    private Map<String, Object> transformPermanentFacilityToSheetFormat(Map<String, Object> facility) {
        // Transform facility using the same pattern as project-factory transform configs
        Map<String, Object> transformed = new HashMap<>();
        
        // Following the Facility transform config pattern from project-factory
        transformed.put("HCM_ADMIN_CONSOLE_FACILITY_CODE", facility.get("id"));
        transformed.put("HCM_ADMIN_CONSOLE_FACILITY_NAME", facility.get("name"));
        
        // Transform isPermanent to status
        Boolean isPermanent = (Boolean) facility.get("isPermanent");
        transformed.put("HCM_ADMIN_CONSOLE_FACILITY_STATUS", 
                (isPermanent != null && isPermanent) ? "Permanent" : "Temporary");
        
        // Map facility usage and type
        transformed.put("HCM_ADMIN_CONSOLE_FACILITY_TYPE", facility.get("usage"));
        transformed.put("HCM_ADMIN_CONSOLE_FACILITY_CAPACITY", facility.get("storageCapacity"));
        
        // Set default usage as "Inactive" if not specified (following project-factory pattern)
        transformed.put("HCM_ADMIN_CONSOLE_FACILITY_USAGE", "Inactive");
        
        // Extract and map boundary code
        String boundaryCode = getFacilityBoundaryCode(facility);
        if (boundaryCode != null) {
            transformed.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", boundaryCode);
        }
        
        return transformed;
    }
    
    private void enrichFacilitiesWithBoundaryPaths(List<Map<String, Object>> facilities, 
                                                   GenerateResource generateResource, RequestInfo requestInfo,
                                                   Map<String, String> localizationMap) {
        try {
            // Get boundary hierarchy for column mapping
            BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(
                    generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);
            List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
            
            // Get boundary relationship data for path building
            BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(
                    generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);
            Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(relationshipData);
            
            // For each facility, find its boundary path and populate boundary columns
            for (Map<String, Object> facility : facilities) {
                String localityCode = (String) facility.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
                if (localityCode != null && !localityCode.isEmpty()) {
                    List<String> boundaryPath = findBoundaryPathFromLocalityCode(localityCode, codeToEnrichedBoundary);
                    populateBoundaryColumnsFromPath(facility, boundaryPath, hierarchyRelations, 
                            generateResource.getHierarchyType(), localizationMap);
                }
            }
            
        } catch (Exception e) {
            log.error("Error enriching facilities with boundary paths: {}", e.getMessage(), e);
        }
    }
    
    private List<String> findBoundaryPathFromLocalityCode(String localityCode, 
                                                          Map<String, EnrichedBoundary> codeToEnrichedBoundary) {
        List<String> path = new ArrayList<>();
        
        // Find the boundary with the given locality code
        EnrichedBoundary currentBoundary = codeToEnrichedBoundary.get(localityCode);
        if (currentBoundary == null) {
            log.warn("No boundary found for locality code: {}", localityCode);
            return path;
        }
        
        // Build path from locality up to root
        List<String> reversePath = new ArrayList<>();
        while (currentBoundary != null) {
            reversePath.add(currentBoundary.getCode());
            // Find parent boundary
            currentBoundary = findParentBoundary(currentBoundary, codeToEnrichedBoundary);
        }
        
        // Reverse the path to go from root to locality
        Collections.reverse(reversePath);
        return reversePath;
    }
    
    private EnrichedBoundary findParentBoundary(EnrichedBoundary boundary, 
                                               Map<String, EnrichedBoundary> codeToEnrichedBoundary) {
        // Find parent by searching through all boundaries
        for (EnrichedBoundary candidate : codeToEnrichedBoundary.values()) {
            if (candidate.getChildren() != null) {
                for (EnrichedBoundary child : candidate.getChildren()) {
                    if (child.getCode().equals(boundary.getCode())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
    
    private void populateBoundaryColumnsFromPath(Map<String, Object> facility, 
                                               List<String> boundaryPath,
                                               List<BoundaryHierarchyChild> hierarchyRelations,
                                               String hierarchyType,
                                               Map<String, String> localizationMap) {
        // Fill columns based on hierarchy levels
        for (int i = 0; i < hierarchyRelations.size() && i < boundaryPath.size(); i++) {
            BoundaryHierarchyChild hierarchyRelation = hierarchyRelations.get(i);
            String boundaryType = hierarchyRelation.getBoundaryType();
            String columnKey = hierarchyType.toUpperCase() + "_" + boundaryType.toUpperCase();
            
            String boundaryCode = boundaryPath.get(i);
            if (boundaryCode != null) {
                // Get localized value for the boundary code
                String localizedValue = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                facility.put(columnKey, localizedValue);
            }
        }
    }
    
    private List<Map<String, Object>> fetchExistingCampaignData(String campaignNumber, 
                                                               String tenantId, RequestInfo requestInfo) {
        try {
            // Search for all facility data for this campaign
            List<Map<String, Object>> campaignDataResponse = campaignService.searchCampaignDataByUniqueIdentifiers(
                    new ArrayList<>(), "facility", null, campaignNumber, tenantId, requestInfo);
            
            if (campaignDataResponse != null && !campaignDataResponse.isEmpty()) {
                // Extract the actual data object from each campaign data record
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
                
                log.info("Found {} existing campaign facility records", existingData.size());
                return existingData;
            }
            
            log.info("No existing campaign facility data found");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Error fetching existing campaign data: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private List<Map<String, Object>> mergeAndDeduplicateFacilities(
            List<Map<String, Object>> permanentFacilities, 
            List<Map<String, Object>> campaignFacilities) {
        
        // Use LinkedHashMap to preserve insertion order following project-factory pattern
        Map<String, Map<String, Object>> facilityMap = new LinkedHashMap<>();
        
        // Step 1: Add permanent facilities first (by facility ID)
        for (Map<String, Object> facility : permanentFacilities) {
            String facilityId = (String) facility.get("HCM_ADMIN_CONSOLE_FACILITY_CODE");
            if (facilityId != null && !facilityId.trim().isEmpty()) {
                facilityMap.put(facilityId, facility);
                log.debug("Added permanent facility with ID: {}", facilityId);
            }
        }
        
        // Step 2: Add campaign facilities - CAMPAIGN TAKES PRIORITY in ID collisions
        for (Map<String, Object> facility : campaignFacilities) {
            String facilityId = (String) facility.get("HCM_ADMIN_CONSOLE_FACILITY_CODE");
            
            if (facilityId != null && !facilityId.trim().isEmpty()) {
                // Facility has ID - ALWAYS add campaign facility (replaces permanent if exists)
                if (facilityMap.containsKey(facilityId)) {
                    log.debug("Replaced permanent facility with campaign facility for ID: {} (campaign takes precedence)", facilityId);
                } else {
                    log.debug("Added existing campaign facility with ID: {}", facilityId);
                }
                facilityMap.put(facilityId, facility); // Campaign facility replaces or adds
            } else {
                // New facility without ID - use name as key for deduplication
                String facilityName = (String) facility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME");
                if (facilityName != null && !facilityName.trim().isEmpty()) {
                    String nameKey = "NEW_" + facilityName; // Prefix to avoid collision with IDs
                    if (!facilityMap.containsKey(nameKey)) {
                        facilityMap.put(nameKey, facility);
                        log.debug("Added new facility without ID: {}", facilityName);
                    }
                }
            }
        }
        
        // Step 3: Final name-based deduplication - campaign facilities take priority over API permanent facilities
        Map<String, Map<String, Object>> finalMap = new LinkedHashMap<>();
        Map<String, String> nameToKeyMap = new HashMap<>(); // Track name -> key mapping
        
        for (Map.Entry<String, Map<String, Object>> entry : facilityMap.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> facility = entry.getValue();
            String facilityName = (String) facility.get("HCM_ADMIN_CONSOLE_FACILITY_NAME");
            
            if (facilityName != null && !facilityName.trim().isEmpty()) {
                String existingKey = nameToKeyMap.get(facilityName);
                
                if (existingKey == null) {
                    // First occurrence of this name
                    finalMap.put(key, facility);
                    nameToKeyMap.put(facilityName, key);
                } else {
                    // Duplicate name found - check which one is campaign-related
                    boolean isCurrentFromCampaign = key.startsWith("NEW_"); // Campaign data (new facilities)
                    boolean isExistingFromCampaign = existingKey.startsWith("NEW_"); // Existing campaign data
                    
                    if (isCurrentFromCampaign && !isExistingFromCampaign) {
                        // Current is from campaign, existing is from API - replace API with campaign
                        finalMap.remove(existingKey);
                        finalMap.put(key, facility);
                        nameToKeyMap.put(facilityName, key);
                        log.debug("Replaced API permanent facility with campaign facility for name: {}", facilityName);
                    } else if (!isCurrentFromCampaign && isExistingFromCampaign) {
                        // Current is from API, existing is from campaign - keep campaign, skip API
                        log.debug("Skipped API permanent facility, keeping campaign facility for name: {}", facilityName);
                    } else {
                        // Both are same type (both API or both campaign) - keep first occurrence
                        log.debug("Skipped duplicate facility with name: {} (keeping first occurrence)", facilityName);
                    }
                }
            }
        }
        
        List<Map<String, Object>> result = new ArrayList<>(finalMap.values());
        log.info("Final merged facilities: {} unique facilities (after name-based deduplication)", result.size());
        
        return result;
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
}