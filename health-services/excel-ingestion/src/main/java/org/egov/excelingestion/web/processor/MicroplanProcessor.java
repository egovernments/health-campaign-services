package org.egov.excelingestion.web.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.ExcelSchemaSheetCreator;
import org.egov.excelingestion.util.BoundaryHierarchySheetCreator;
import org.egov.excelingestion.util.CampaignConfigSheetCreator;
import org.egov.excelingestion.util.ExcelStyleHelper;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.web.models.excel.MultiSelectDetails;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.service.ApiPayloadBuilder;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Component("microplanProcessor")
@Slf4j
public class MicroplanProcessor implements IGenerateProcessor {

    private final ExcelIngestionConfig config;
    private final LocalizationService localizationService;
    private final BoundaryHierarchySheetCreator boundaryHierarchySheetCreator;
    private final BoundaryService boundaryService;
    private final RequestInfoConverter requestInfoConverter;
    private final ApiPayloadBuilder apiPayloadBuilder;
    private final ExcelSchemaSheetCreator excelSchemaSheetCreator;
    private final CampaignConfigSheetCreator campaignConfigSheetCreator;
    private final MDMSService mdmsService;
    private final ExcelStyleHelper excelStyleHelper;
    private final BoundaryUtil boundaryUtil;
    private final CustomExceptionHandler exceptionHandler;
    private final CellProtectionManager cellProtectionManager;
    private final ExcelDataPopulator excelDataPopulator;

    public MicroplanProcessor(ExcelIngestionConfig config,
            LocalizationService localizationService, BoundaryHierarchySheetCreator boundaryHierarchySheetCreator,
            BoundaryService boundaryService, RequestInfoConverter requestInfoConverter,
            ApiPayloadBuilder apiPayloadBuilder, ExcelSchemaSheetCreator excelSchemaSheetCreator,
            CampaignConfigSheetCreator campaignConfigSheetCreator, MDMSService mdmsService,
            ExcelStyleHelper excelStyleHelper, BoundaryUtil boundaryUtil, CustomExceptionHandler exceptionHandler,
            CellProtectionManager cellProtectionManager, ExcelDataPopulator excelDataPopulator) {
        this.config = config;
        this.localizationService = localizationService;
        this.boundaryHierarchySheetCreator = boundaryHierarchySheetCreator;
        this.boundaryService = boundaryService;
        this.requestInfoConverter = requestInfoConverter;
        this.apiPayloadBuilder = apiPayloadBuilder;
        this.excelSchemaSheetCreator = excelSchemaSheetCreator;
        this.campaignConfigSheetCreator = campaignConfigSheetCreator;
        this.mdmsService = mdmsService;
        this.excelStyleHelper = excelStyleHelper;
        this.boundaryUtil = boundaryUtil;
        this.exceptionHandler = exceptionHandler;
        this.cellProtectionManager = cellProtectionManager;
        this.excelDataPopulator = excelDataPopulator;
    }

    @Override
    public GenerateResource process(GenerateResourceRequest request) {
        log.info("Processing hierarchy excel generation for type: {}", request.getGenerateResource().getType());
        return request.getGenerateResource();
    }

    public byte[] generateExcel(GenerateResource generateResource, RequestInfo requestInfo) throws IOException {
        log.info("Starting Excel generation process for hierarchyType: {}", generateResource.getHierarchyType());

        String tenantId = generateResource.getTenantId();
        String hierarchyType = generateResource.getHierarchyType();
        String locale = requestInfoConverter.extractLocale(requestInfo);

        String localizationModule = "hcm-boundary-" + hierarchyType.toLowerCase(); // localization module name
        Map<String, String> localizationMap = localizationService.getLocalizedMessages(
                tenantId, localizationModule, locale,requestInfo);
        
        // Also fetch localization for schema columns
        String schemaLocalizationModule = "hcm-admin-schemas";
        Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                tenantId, schemaLocalizationModule, locale, requestInfo);
        
        // Merge both localization maps
        Map<String, String> mergedLocalizationMap = new HashMap<>();
        mergedLocalizationMap.putAll(localizationMap);
        mergedLocalizationMap.putAll(schemaLocalizationMap);

        // Fetch schema from MDMS for the mapping sheet
        Map<String, Object> facilityFilters = new HashMap<>();
        facilityFilters.put("title", "facility-microplan-ingestion");
        List<Map<String, Object>> facilityMdmsList = mdmsService.searchMDMS(
                requestInfo, tenantId, "HCM-ADMIN-CONSOLE.schemas", facilityFilters, 1, 0);
        String schemaJson = extractSchemaFromMDMSResponse(facilityMdmsList, "facility-microplan-ingestion");
        
        // Fetch schema from MDMS for the user sheet
        Map<String, Object> userFilters = new HashMap<>();
        userFilters.put("title", "user-microplan-ingestion");
        List<Map<String, Object>> userMdmsList = mdmsService.searchMDMS(
                requestInfo, tenantId, "HCM-ADMIN-CONSOLE.schemas", userFilters, 1, 0);
        String userSchemaJson = extractSchemaFromMDMSResponse(userMdmsList, "user-microplan-ingestion");

        // Fetch boundary hierarchy data
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);

        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null
                || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND,
                    ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND_MESSAGE.replace("{0}", hierarchyType),
                    new RuntimeException("Boundary hierarchy data is null or empty for type: " + hierarchyType));
        }

        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0)
                .getBoundaryHierarchy();

        // Fetch boundary relationship data
        BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);

        // 1) Build level list using localized level names from HCM_CAMP_CONF_LEVEL_* keys
        List<String> levels = new ArrayList<>();
        List<String> levelKeys = new ArrayList<>();
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String levelKey = "HCM_CAMP_CONF_LEVEL_" + (i + 1);
            String localizedLevel = mergedLocalizationMap.getOrDefault(levelKey, levelKey);
            levels.add(localizedLevel);
            levelKeys.add(levelKey);
        }

        // map boundaryType -> levelName
        Map<String, String> boundaryTypeToLevel = new HashMap<>();
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String bt = hierarchyRelations.get(i).getBoundaryType();
            boundaryTypeToLevel.put(bt, levels.get(i));
        }

        // DATA STRUCTURES:
        // boundariesByLevelLocalized: Level -> localized display names (used in
        // dropdowns)
        Map<String, Set<String>> boundariesByLevelLocalized = new HashMap<>();
        // boundariesByLevelCode: Level -> codePaths (unique keys)
        Map<String, Set<String>> boundariesByLevelCode = new HashMap<>();
        // mapping codePath -> localized name
        Map<String, String> codeToLocalized = new LinkedHashMap<>();

        levels.forEach(l -> {
            boundariesByLevelLocalized.put(l, new TreeSet<>());
            boundariesByLevelCode.put(l, new LinkedHashSet<>());
        });

        // Populate the above maps by traversing relationship data
        if (relationshipData != null && relationshipData.getTenantBoundary() != null) {
            for (HierarchyRelation hr : relationshipData.getTenantBoundary()) {
                processNodesBuildCodePaths(hr.getBoundary(), boundaryTypeToLevel, null,
                        boundariesByLevelLocalized, boundariesByLevelCode, codeToLocalized,
                        localizationMap);
            }
        }

        // Create workbook and sheets
        XSSFWorkbook workbook = new XSSFWorkbook();

        // === Create Campaign Configuration sheet as the first sheet ===
        Map<String, Object> configFilters = new HashMap<>();
        configFilters.put("sheetName", "HCM_CAMP_CONF_SHEETNAME");
        List<Map<String, Object>> configMdmsList = mdmsService.searchMDMS(
                requestInfo, tenantId, "HCM-ADMIN-CONSOLE.configsheet", configFilters, 1, 0);
        String campaignConfigData = extractCampaignConfigFromMDMSResponse(configMdmsList, "HCM_CAMP_CONF_SHEETNAME");
        if (campaignConfigData != null && !campaignConfigData.isEmpty()) {
            String localizedConfigSheetName = mergedLocalizationMap.getOrDefault("HCM_CAMP_CONF_SHEETNAME", "HCM_CAMP_CONF_SHEETNAME");
            
            // Handle Excel's 31 character limit for sheet names
            String actualConfigSheetName = localizedConfigSheetName;
            if (localizedConfigSheetName.length() > 31) {
                actualConfigSheetName = localizedConfigSheetName.substring(0, 31);
                log.warn("Sheet name '{}' exceeds 31 character limit, trimming to '{}'", localizedConfigSheetName, actualConfigSheetName);
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> configData = mapper.readValue(campaignConfigData, Map.class);
                workbook = (XSSFWorkbook) campaignConfigSheetCreator.createCampaignConfigSheet(
                        workbook, actualConfigSheetName, configData, mergedLocalizationMap,
                        tenantId, hierarchyType, requestInfo);
                log.info("Campaign configuration sheet created successfully");
            } catch (Exception e) {
                log.error("Error creating campaign configuration sheet: {}", e.getMessage(), e);
                exceptionHandler.throwCustomException(ErrorConstants.CAMPAIGN_CONFIG_CREATION_ERROR, 
                        ErrorConstants.CAMPAIGN_CONFIG_CREATION_ERROR_MESSAGE, e);
            }
        }

        // Get the localized sheet name first
        String localizedFacilitySheetName = mergedLocalizationMap.getOrDefault("HCM_ADMIN_CONSOLE_FACILITIES_LIST", "HCM_ADMIN_CONSOLE_FACILITIES_LIST");
        
        // Excel has a 31 character limit for sheet names, so we need to handle this
        String actualFacilitySheetName = localizedFacilitySheetName;
        if (localizedFacilitySheetName.length() > 31) {
            actualFacilitySheetName = localizedFacilitySheetName.substring(0, 31);
            log.warn("Sheet name '{}' exceeds 31 character limit, trimming to '{}'", localizedFacilitySheetName, actualFacilitySheetName);
        }
        
        // === Create facility sheet using ExcelDataPopulator (headers-only) ===
        if (schemaJson != null && !schemaJson.isEmpty()) {
            List<ColumnDef> facilityColumns = convertSchemaToColumnDefs(schemaJson);
            workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, actualFacilitySheetName, facilityColumns, null, mergedLocalizationMap);
        }

        // === Levels sheet (localized display names visible) ===
        Sheet levelSheet = workbook.createSheet("_h_Levels_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Levels_h_"), true);
        Row levelRow = levelSheet.createRow(0);
        for (int i = 0; i < levels.size(); i++) {
            levelRow.createCell(i).setCellValue(levels.get(i)); // Localized level names
        }
        // Named range "Levels"
        Name levelsNamedRange = workbook.createName();
        levelsNamedRange.setNameName("Levels");
        String lastColLetter = CellReference.convertNumToColString(levels.size() - 1);
        levelsNamedRange.setRefersToFormula("_h_Levels_h_!$A$1:$" + lastColLetter + "$1");

        // === Boundaries sheet (localized names) - one column per level ===
        Sheet boundarySheet = workbook.createSheet("_h_Boundaries_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        int maxBoundaries = 0;
        for (int i = 0; i < levels.size(); i++) {
            String level = levels.get(i);
            Set<String> codePaths = boundariesByLevelCode.getOrDefault(level, Collections.emptySet());

            Row header = boundarySheet.getRow(0) != null ? boundarySheet.getRow(0) : boundarySheet.createRow(0);
            header.createCell(i).setCellValue(level); // header shows level display name

            // Sort localized names alphabetically
            List<String> sortedLocalizedNames = new ArrayList<>();
            for (String codePath : codePaths) {
                String localized = codeToLocalized.getOrDefault(codePath, codePath);
                sortedLocalizedNames.add(localized);
            }
            Collections.sort(sortedLocalizedNames, String.CASE_INSENSITIVE_ORDER);

            // Write sorted localized names
            int rowNum = 1;
            for (String localizedName : sortedLocalizedNames) {
                Row row = boundarySheet.getRow(rowNum) != null ? boundarySheet.getRow(rowNum)
                        : boundarySheet.createRow(rowNum);
                row.createCell(i).setCellValue(localizedName);
                rowNum++;
            }
            maxBoundaries = Math.max(maxBoundaries, sortedLocalizedNames.size());

            // Create named range for this level using sanitized name like Level_1
            if (!sortedLocalizedNames.isEmpty()) {
                String levelSanitized = makeNameValid(level);
                if (workbook.getName(levelSanitized) == null) {
                    Name namedRange = workbook.createName();
                    namedRange.setNameName(levelSanitized);
                    String colLetter = CellReference.convertNumToColString(i);
                    namedRange.setRefersToFormula(
                            "_h_Boundaries_h_!$" + colLetter + "$2:$" + colLetter + "$" + (sortedLocalizedNames.size() + 1));
                }
            }
        }

        // === Main facility sheet - get existing sheet created by schema ===
        Sheet mainSheet = workbook.getSheet(actualFacilitySheetName);
        if (mainSheet == null) {
            // Fallback if schema creation failed
            mainSheet = workbook.createSheet(actualFacilitySheetName);
        }
        
        // Add boundary columns to facility sheet based on configured boundaries
        addBoundaryColumnsToSheet(workbook, actualFacilitySheetName, mergedLocalizationMap, 
                                generateResource.getBoundaries(), hierarchyType, tenantId, requestInfo);

        // === Create User sheet using user schema ===
        String localizedUserSheetName = mergedLocalizationMap.getOrDefault("HCM_ADMIN_CONSOLE_USERS_LIST",
                "HCM_ADMIN_CONSOLE_USERS_LIST");
        
        // Handle Excel's 31 character limit for sheet names
        String actualUserSheetName = localizedUserSheetName;
        if (localizedUserSheetName.length() > 31) {
            actualUserSheetName = localizedUserSheetName.substring(0, 31);
            log.warn("Sheet name '{}' exceeds 31 character limit, trimming to '{}'", localizedUserSheetName, actualUserSheetName);
        }
        
        if (userSchemaJson != null && !userSchemaJson.isEmpty()) {
            List<ColumnDef> userColumns = convertSchemaToColumnDefs(userSchemaJson);
            workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, actualUserSheetName, userColumns, null, mergedLocalizationMap);
            
            // Add boundary columns to user sheet as well based on configured boundaries  
            addBoundaryColumnsToSheet(workbook, actualUserSheetName, mergedLocalizationMap,
                                    generateResource.getBoundaries(), hierarchyType, tenantId, requestInfo);
        }

        // === Create Boundary Hierarchy sheet ===
        String localizedHierarchySheetName = mergedLocalizationMap.getOrDefault("HCM_CONSOLE_BOUNDARY_HIERARCHY",
                "HCM_CONSOLE_BOUNDARY_HIERARCHY");
        
        // Handle Excel's 31 character limit for sheet names
        String actualHierarchySheetName = localizedHierarchySheetName;
        if (localizedHierarchySheetName.length() > 31) {
            actualHierarchySheetName = localizedHierarchySheetName.substring(0, 31);
            log.warn("Sheet name '{}' exceeds 31 character limit, trimming to '{}'", localizedHierarchySheetName, actualHierarchySheetName);
        }
        
        // Create boundary hierarchy sheet with data using ExcelDataPopulator
        // Use existing boundary filtering logic to get only configured boundaries
        BoundarySearchResponse boundaryRelationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(boundaryRelationshipData);
        
        // Get level types
        List<String> levelTypes = new ArrayList<>();
        for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
            levelTypes.add(hierarchyRelation.getBoundaryType());
        }
        
        // Filter boundaries based on additionalDetails configuration (reuse existing logic)
        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                generateResource.getBoundaries(), codeToEnrichedBoundary, levelTypes);
        
        List<ColumnDef> boundaryColumns = createBoundaryHierarchyColumnDefs(hierarchyRelations, hierarchyType);
        List<Map<String, Object>> boundaryData = getBoundaryHierarchyDataFromFiltered(filteredBoundaries, hierarchyRelations, hierarchyType, localizationMap);
        workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, actualHierarchySheetName, boundaryColumns, boundaryData, mergedLocalizationMap);


        // Set zoom to 60% BEFORE protection and hiding (Excel compatibility)
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            sheet.setZoom(config.getExcelSheetZoom());
        }
        
        // Get the campaign config sheet if it exists
        String actualConfigSheetName = null;
        if (campaignConfigData != null && !campaignConfigData.isEmpty()) {
            String localizedConfigSheetName = mergedLocalizationMap.getOrDefault("HCM_CAMP_CONF_SHEETNAME", "HCM_CAMP_CONF_SHEETNAME");
            actualConfigSheetName = localizedConfigSheetName.length() > 31 ? localizedConfigSheetName.substring(0, 31) : localizedConfigSheetName;
        }
        
        // Hide all sheets except the visible sheets (campaign config, facility, user, hierarchy)
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            boolean isVisible = sheet == mainSheet || 
                               sheet.getSheetName().equals(actualUserSheetName) ||
                               sheet.getSheetName().equals(actualHierarchySheetName) ||
                               (actualConfigSheetName != null && sheet.getSheetName().equals(actualConfigSheetName));
            
            if (!isVisible) {
                workbook.setSheetHidden(i, true);
            }
        }
        
        // Set the active sheet before protection - prioritize campaign config sheet as first sheet
        if (actualConfigSheetName != null && workbook.getSheet(actualConfigSheetName) != null) {
            workbook.setActiveSheet(workbook.getSheetIndex(actualConfigSheetName));
        } else {
            workbook.setActiveSheet(workbook.getSheetIndex(mainSheet));
        }
        
        // Apply comprehensive protection to all visible sheets using the CellProtectionManager
        log.info("Applying comprehensive sheet protection...");
        
        // Protect main facility sheet
        cellProtectionManager.applySheetProtection(workbook, mainSheet, config.getExcelSheetPassword());
        
        // Protect user sheet if it exists
        Sheet userSheet = workbook.getSheet(actualUserSheetName);
        if (userSheet != null) {
            cellProtectionManager.applySheetProtection(workbook, userSheet, config.getExcelSheetPassword());
        }
        
        // Protect hierarchy sheet if it exists
        Sheet hierarchySheet = workbook.getSheet(actualHierarchySheetName);
        if (hierarchySheet != null) {
            cellProtectionManager.applySheetProtection(workbook, hierarchySheet, config.getExcelSheetPassword());
        }
        
        // Protect campaign config sheet if it exists
        if (actualConfigSheetName != null) {
            Sheet configSheet = workbook.getSheet(actualConfigSheetName);
            if (configSheet != null) {
                cellProtectionManager.applySheetProtection(workbook, configSheet, config.getExcelSheetPassword());
            }
        }
        
        // Apply workbook-level structure protection
        cellProtectionManager.applyWorkbookProtection(workbook, config.getExcelSheetPassword());
        workbook.setWorkbookPassword(config.getExcelSheetPassword(), HashAlgorithm.sha512);
        
        log.info("Comprehensive protection applied successfully to all sheets");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            workbook.write(bos);
        } finally {
            workbook.close();
        }
        log.info("Excel file generated successfully!");
        return bos.toByteArray();
    }

    /**
     * Recursive traversal: builds:
     * - boundariesByLevelLocalized: level -> localized names (display)
     * - boundariesByLevelCode: level -> codePath strings (unique keys)
     * - codeToLocalized: codePath -> localized name
     *
     * @param nodes                      current nodes
     * @param boundaryTypeToLevel        mapping boundaryType -> LevelName ("Level
     *                                   1" etc)
     * @param parentCodePath             parent code path (null if root)
     * @param boundariesByLevelLocalized
     * @param boundariesByLevelCode
     * @param codeToLocalized
     * @param localizationMap            code->localized string map from
     *                                   LocalizationService
     */
    private void processNodesBuildCodePaths(List<EnrichedBoundary> nodes,
            Map<String, String> boundaryTypeToLevel,
            String parentCodePath,
            Map<String, Set<String>> boundariesByLevelLocalized,
            Map<String, Set<String>> boundariesByLevelCode,
            Map<String, String> codeToLocalized,
            Map<String, String> localizationMap) {
        if (nodes == null)
            return;

        for (EnrichedBoundary node : nodes) {
            String code = node.getCode(); // e.g. "CHICOA"
            String boundaryType = node.getBoundaryType();

            // Build codePath = parentCodePath + "_" + code OR just code if root
            final String codePath = (parentCodePath == null || parentCodePath.isEmpty()) ? code
                    : parentCodePath + "_" + code;

            // localized name for display (from localizationMap)
            String localized = localizationMap.getOrDefault(code, code);

            // level name (display)
            String level = boundaryTypeToLevel.get(boundaryType);
            if (level == null) {
                log.warn("Boundary type {} not found in hierarchy, skipping", boundaryType);
                continue;
            }

            // track codePath -> localized
            codeToLocalized.putIfAbsent(codePath, localized);

            // add to level sets
            boundariesByLevelCode.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(codePath);
            boundariesByLevelLocalized.computeIfAbsent(level, k -> new TreeSet<>()).add(localized);

            // Recurse children: pass current codePath as parent
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                processNodesBuildCodePaths(node.getChildren(), boundaryTypeToLevel, codePath,
                        boundariesByLevelLocalized, boundariesByLevelCode, codeToLocalized,
                        localizationMap);
            }
        }
    }


    /**
     * Make an Excel-friendly name (valid for named ranges) from any input string.
     * This will replace non-alphanumeric/underscore/dot with underscore and prefix
     * with "_" if it starts with digit.
     */
    private String makeNameValid(String name) {
        if (name == null)
            return "_null";
        String valid = name.replaceAll("[^A-Za-z0-9_.]", "_");
        if (Character.isDigit(valid.charAt(0)))
            valid = "_" + valid;
        return valid;
    }


    @Override
    public String getType() {
        return "microplan-ingestion";
    }

    private String extractSchemaFromMDMSResponse(List<Map<String, Object>> mdmsList, String title) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                // Extract the properties part which contains stringProperties, numberProperties, and enumProperties
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                if (properties != null) {
                    // Convert properties to JSON string
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
        
        // No MDMS data found - throw error instead of returning default
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND, 
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", title),
                new RuntimeException("Schema '" + title + "' not found in MDMS configuration"));
        return null; // This will never be reached due to exception throwing above
    }

    private String extractCampaignConfigFromMDMSResponse(List<Map<String, Object>> mdmsList, String sheetName) {
        try {
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                if (data != null) {
                    // Convert data to JSON string
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully extracted MDMS campaign config for: {}", sheetName);
                    return mapper.writeValueAsString(data);
                }
            }
            log.warn("No MDMS data found for campaign config: {}", sheetName);
        } catch (Exception e) {
            log.error("Error extracting MDMS campaign config {}: {}", sheetName, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR, 
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        // No MDMS data found - throw error instead of returning null
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND, 
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", sheetName),
                new RuntimeException("Campaign config sheet '" + sheetName + "' not found in MDMS configuration"));
        return null; // This will never be reached due to exception throwing above
    }


    private void addBoundaryColumnsToSheet(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap,
                                         List<Boundary> configuredBoundaries, String hierarchyType, 
                                         String tenantId, RequestInfo requestInfo) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add boundary columns", sheetName);
            return;
        }
        
        // Check if boundaries are configured in additionalDetails
        if (configuredBoundaries == null || configuredBoundaries.isEmpty()) {
            log.info("No boundaries configured in additionalDetails for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }
        
        // Fetch boundary relationship data to get enriched boundary information
        BoundarySearchResponse relationshipData = boundaryService.fetchBoundaryRelationship(tenantId, hierarchyType, requestInfo);
        Map<String, EnrichedBoundary> codeToEnrichedBoundary = boundaryUtil.buildCodeToBoundaryMap(relationshipData);
        
        // Fetch boundary hierarchy data to get level types
        BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            log.error("Boundary hierarchy data is null or empty for type: {}", hierarchyType);
            return;
        }
        
        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        List<String> levelTypes = new ArrayList<>();
        for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
            levelTypes.add(hierarchyRelation.getBoundaryType());
        }
        
        // Process boundaries based on configuration (this already handles includeAllChildren enrichment)
        List<BoundaryUtil.BoundaryRowData> filteredBoundaries = boundaryUtil.processBoundariesWithEnrichment(
                configuredBoundaries, codeToEnrichedBoundary, levelTypes);
        
        // If after filtering we have no boundaries, don't add boundary columns
        if (filteredBoundaries.isEmpty()) {
            log.info("No boundaries available after filtering for sheet '{}', skipping boundary column creation", sheetName);
            return;
        }

        // Add boundary columns after schema columns
        // Row 0 contains technical names (hidden), Row 1 contains visible headers
        Row hiddenRow = sheet.getRow(0);
        Row visibleRow = sheet.getRow(1);
        
        if (hiddenRow == null) {
            hiddenRow = sheet.createRow(0);
        }
        if (visibleRow == null) {
            visibleRow = sheet.createRow(1);
        }
        
        // Find the last used column from schema
        int lastSchemaCol = visibleRow.getLastCellNum();
        if (lastSchemaCol < 0) lastSchemaCol = 0;
        
        // Add level, boundary name, and boundary code columns after schema columns
        // Add technical names to hidden row
        hiddenRow.createCell(lastSchemaCol).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(lastSchemaCol + 1).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(lastSchemaCol + 2).setCellValue("HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
        
        // Create header style for boundary columns (left-aligned to match schema columns)
        CellStyle boundaryHeaderStyle = excelStyleHelper.createLeftAlignedHeaderStyle(workbook, config.getDefaultHeaderColor());
        
        // Add localized headers to visible row with styling
        Cell levelHeaderCell = visibleRow.createCell(lastSchemaCol);
        levelHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_LEVEL_COLUMN", "HCM_INGESTION_LEVEL_COLUMN"));
        levelHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        Cell boundaryHeaderCell = visibleRow.createCell(lastSchemaCol + 1);
        boundaryHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_BOUNDARY_COLUMN", "HCM_INGESTION_BOUNDARY_COLUMN"));
        boundaryHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        Cell boundaryCodeHeaderCell = visibleRow.createCell(lastSchemaCol + 2);
        boundaryCodeHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", "HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
        boundaryCodeHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Create level and boundary dropdowns with "Boundary (Parent)" format to avoid duplicates
        createLevelAndBoundaryDropdowns(workbook, filteredBoundaries, levelTypes, localizationMap);
        
        // Add boundary code mapping BEFORE data validations to ensure named range exists
        addBoundaryCodeMapping(workbook, filteredBoundaries, localizationMap);
        
        // Add data validation for level and boundary columns
        addLevelAndBoundaryDataValidations(workbook, sheet, lastSchemaCol, levelTypes, localizationMap);


        // Column widths & freeze - level, boundary name, and boundary code columns
        sheet.setColumnWidth(lastSchemaCol, 50 * 256); // Level column
        sheet.setColumnWidth(lastSchemaCol + 1, 50 * 256); // Boundary column (wider for "Boundary (Parent)" format)
        sheet.setColumnWidth(lastSchemaCol + 2, 25 * 256); // Boundary code column (narrower)
        sheet.createFreezePane(0, 2); // Freeze after row 2 since schema creator uses row 1 for technical names

        // Unlock cells for user input - level and boundary columns only (boundary code is auto-populated)
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        for (int r = 2; r <= config.getExcelRowLimit(); r++) { // Start from row 2 to skip hidden technical row
            Row row = sheet.getRow(r);
            if (row == null)
                row = sheet.createRow(r);
            // Only unlock level and boundary name columns (not boundary code)
            for (int c = lastSchemaCol; c < lastSchemaCol + 2; c++) {
                Cell cell = row.getCell(c);
                if (cell == null)
                    cell = row.createCell(c);
                cell.setCellStyle(unlocked);
            }
        }
        
        // Hide the boundary code column
        sheet.setColumnHidden(lastSchemaCol + 2, true);
    }

    /**
     * Creates level dropdown and level-specific boundary dropdowns with "boundary (parent)" format to avoid duplicates
     */
    private void createLevelAndBoundaryDropdowns(XSSFWorkbook workbook, List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                               List<String> levelTypes, Map<String, String> localizationMap) {
        
        // Build levels and boundary options by level
        Set<String> availableLevels = new LinkedHashSet<>();
        Map<String, Set<String>> boundariesByLevel = new HashMap<>();
        
        // Create all levels based on the full hierarchy
        for (int i = 0; i < levelTypes.size(); i++) {
            String levelKey = "HCM_CAMP_CONF_LEVEL_" + (i + 1);
            String localizedLevel = localizationMap.getOrDefault(levelKey, levelKey);
            availableLevels.add(localizedLevel);
            boundariesByLevel.put(localizedLevel, new TreeSet<>());
        }
        
        // Debug: Log the boundary data we're processing
        log.info("Processing {} filtered boundaries for dropdown creation", filteredBoundaries.size());
        
        // Process filtered boundaries and group by level with "Boundary (Parent)" format
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) != null) {
                    String boundaryCode = path.get(i);
                    String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    String levelKey = "HCM_CAMP_CONF_LEVEL_" + (i + 1);
                    String localizedLevel = localizationMap.getOrDefault(levelKey, levelKey);
                    
                    // Get parent name if exists  
                    String parentName = "";
                    if (i > 0 && path.get(i-1) != null) {
                        String parentCode = path.get(i-1);
                        parentName = localizationMap.getOrDefault(parentCode, parentCode);
                    }
                    
                    // Create display format: "Boundary (Parent)" or just "Boundary" if no parent
                    String displayText = parentName.isEmpty() ? boundaryName : boundaryName + " (" + parentName + ")";
                    
                    boundariesByLevel.get(localizedLevel).add(displayText);
                }
            }
        }
        
        // Create levels sheet  
        Sheet levelSheet = workbook.getSheet("_h_Levels_h_");
        if (levelSheet == null) {
            levelSheet = workbook.createSheet("_h_Levels_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Levels_h_"), true);
        } else {
            // Clear existing content
            if (levelSheet.getLastRowNum() >= 0) {
                for (int i = levelSheet.getLastRowNum(); i >= 0; i--) {
                    Row row = levelSheet.getRow(i);
                    if (row != null) {
                        levelSheet.removeRow(row);
                    }
                }
            }
        }
        
        Row levelRow = levelSheet.createRow(0);
        int colIndex = 0;
        for (String level : availableLevels) {
            levelRow.createCell(colIndex++).setCellValue(level);
        }
        
        // Create named range "Levels" for level dropdown
        if (!availableLevels.isEmpty()) {
            Name levelsNamedRange = workbook.getName("Levels");
            if (levelsNamedRange == null) {
                levelsNamedRange = workbook.createName();
                levelsNamedRange.setNameName("Levels");
            }
            String lastColLetter = CellReference.convertNumToColString(availableLevels.size() - 1);
            levelsNamedRange.setRefersToFormula("_h_Levels_h_!$A$1:$" + lastColLetter + "$1");
        }
        
        // Create boundaries sheet with level-specific columns
        Sheet boundarySheet = workbook.getSheet("_h_Boundaries_h_");
        if (boundarySheet == null) {
            boundarySheet = workbook.createSheet("_h_Boundaries_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        } else {
            // Clear existing content
            if (boundarySheet.getLastRowNum() >= 0) {
                for (int i = boundarySheet.getLastRowNum(); i >= 0; i--) {
                    Row row = boundarySheet.getRow(i);
                    if (row != null) {
                        boundarySheet.removeRow(row);
                    }
                }
            }
        }
        
        // Create header row and populate boundary data by level
        int maxBoundaries = 0;
        colIndex = 0;
        for (String level : availableLevels) {
            Row header = boundarySheet.getRow(0) != null ? boundarySheet.getRow(0) : boundarySheet.createRow(0);
            header.createCell(colIndex).setCellValue(level);
            
            Set<String> boundaries = boundariesByLevel.get(level);
            List<String> sortedBoundaries = new ArrayList<>(boundaries);
            Collections.sort(sortedBoundaries, String.CASE_INSENSITIVE_ORDER);
            
            int rowNum = 1;
            for (String boundaryOption : sortedBoundaries) {
                Row row = boundarySheet.getRow(rowNum) != null ? boundarySheet.getRow(rowNum) : boundarySheet.createRow(rowNum);
                row.createCell(colIndex).setCellValue(boundaryOption);
                rowNum++;
            }
            maxBoundaries = Math.max(maxBoundaries, sortedBoundaries.size());
            
            // Create named range for this level
            if (!sortedBoundaries.isEmpty()) {
                String levelSanitized = "Level_" + (colIndex + 1);
                Name namedRange = workbook.getName(levelSanitized);
                if (namedRange == null) {
                    namedRange = workbook.createName();
                    namedRange.setNameName(levelSanitized);
                }
                String colLetter = CellReference.convertNumToColString(colIndex);
                namedRange.setRefersToFormula("_h_Boundaries_h_!$" + colLetter + "$2:$" + colLetter + "$" + (sortedBoundaries.size() + 1));
            }
            colIndex++;
        }
        
        log.info("Created level and boundary dropdowns for {} levels", availableLevels.size());
    }
    
    /**
     * Creates boundary code mapping sheet for automatic code population
     */
    private void addBoundaryCodeMapping(XSSFWorkbook workbook, List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                      Map<String, String> localizationMap) {
        // Check if boundary code mapping sheet already exists (since this method may be called for multiple sheets)
        Sheet boundaryCodeMapSheet = workbook.getSheet("_h_BoundaryCodeMap_h_");
        if (boundaryCodeMapSheet != null) {
            // Sheet already exists, no need to recreate
            return;
        }
        
        // Create hidden sheet for boundary display name -> code mapping
        boundaryCodeMapSheet = workbook.createSheet("_h_BoundaryCodeMap_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_BoundaryCodeMap_h_"), true);
        
        // Header row
        Row headerRow = boundaryCodeMapSheet.createRow(0);
        headerRow.createCell(0).setCellValue("BoundaryDisplay");
        headerRow.createCell(1).setCellValue("BoundaryCode");
        
        // Build mapping from display name to boundary code
        Map<String, String> displayToCodeMap = new HashMap<>();
        
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) != null) {
                    String boundaryCode = path.get(i);
                    String boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    
                    // Get parent name if exists for display format
                    String parentName = "";
                    if (i > 0 && path.get(i-1) != null) {
                        String parentCode = path.get(i-1);
                        parentName = localizationMap.getOrDefault(parentCode, parentCode);
                    }
                    
                    // Create display format: "Boundary (Parent)" or just "Boundary" if no parent
                    String displayText = parentName.isEmpty() ? boundaryName : boundaryName + " (" + parentName + ")";
                    
                    // Store mapping
                    displayToCodeMap.put(displayText, boundaryCode);
                }
            }
        }
        
        // Populate mapping sheet
        int rowNum = 1;
        for (Map.Entry<String, String> entry : displayToCodeMap.entrySet()) {
            Row row = boundaryCodeMapSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(entry.getKey());   // Display name
            row.createCell(1).setCellValue(entry.getValue()); // Code
        }
        
        // Create named range for the mapping
        if (!displayToCodeMap.isEmpty()) {
            Name boundaryCodeMapRange = workbook.createName();
            boundaryCodeMapRange.setNameName("BoundaryCodeMap");
            boundaryCodeMapRange.setRefersToFormula("_h_BoundaryCodeMap_h_!$A$1:$B$" + (displayToCodeMap.size() + 1));
        }
    }

    /**
     * Adds data validations for level and boundary columns
     */
    private void addLevelAndBoundaryDataValidations(XSSFWorkbook workbook, Sheet sheet, int lastSchemaCol, 
                                                  List<String> levelTypes, Map<String, String> localizationMap) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Add data validation for rows starting from row 2
        for (int rowIndex = 2; rowIndex <= config.getExcelRowLimit(); rowIndex++) {
            // Level dropdown uses "Levels" named range
            DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
            CellRangeAddressList levelAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol, lastSchemaCol);
            DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
            levelValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            levelValidation.setShowErrorBox(true);
            levelValidation.createErrorBox("Invalid Level", "Please select a valid level from the dropdown list.");
            levelValidation.setShowPromptBox(true);
            levelValidation.createPromptBox("Select Level", "Choose a level from the dropdown list.");
            sheet.addValidationData(levelValidation);
            
            // Boundary dropdown: depends on level selected, shows "Boundary (Parent)" format 
            String levelCellRef = CellReference.convertNumToColString(lastSchemaCol) + (rowIndex + 1);
            String boundaryFormula = "IF(" + levelCellRef + "=\"\", \"\", INDIRECT(\"Level_\"&MATCH(" + levelCellRef + ", Levels, 0)))";
            DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint(boundaryFormula);
            CellRangeAddressList boundaryAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol + 1, lastSchemaCol + 1);
            DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
            boundaryValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            boundaryValidation.setShowErrorBox(true);
            boundaryValidation.createErrorBox("Invalid Boundary", "Please select a valid boundary from the dropdown list.");
            boundaryValidation.setShowPromptBox(true);
            boundaryValidation.createPromptBox("Select Boundary", "Choose a boundary from the dropdown list. Format: 'Boundary (Parent)' to avoid duplicates.");
            sheet.addValidationData(boundaryValidation);
            
            // Add VLOOKUP formula to boundary code column for automatic population
            String boundaryNameCellRef = CellReference.convertNumToColString(lastSchemaCol + 1) + (rowIndex + 1);
            String vlookupFormula = "IF(" + boundaryNameCellRef + "=\"\", \"\", VLOOKUP(" + boundaryNameCellRef + ", BoundaryCodeMap, 2, FALSE))";
            
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            Cell boundaryCodeCell = row.createCell(lastSchemaCol + 2);
            boundaryCodeCell.setCellFormula(vlookupFormula);
        }
    }

    /**
     * Convert MDMS schema JSON to List<ColumnDef> for ExcelDataPopulator
     */
    private List<ColumnDef> convertSchemaToColumnDefs(String schemaJson) {
        List<ColumnDef> columns = new ArrayList<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(schemaJson);
            
            // Process stringProperties
            if (root.has("stringProperties")) {
                for (JsonNode node : root.path("stringProperties")) {
                    columns.add(parseJsonToColumnDef(node, "string"));
                }
            }
            
            // Process numberProperties
            if (root.has("numberProperties")) {
                for (JsonNode node : root.path("numberProperties")) {
                    columns.add(parseJsonToColumnDef(node, "number"));
                }
            }
            
            // Process enumProperties
            if (root.has("enumProperties")) {
                for (JsonNode node : root.path("enumProperties")) {
                    columns.add(parseJsonToColumnDef(node, "enum"));
                }
            }
            
            // Sort by orderNumber
            columns.sort(Comparator.comparingInt(ColumnDef::getOrderNumber));
            
        } catch (Exception e) {
            log.error("Error converting schema JSON to ColumnDefs: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_CONVERSION_ERROR, 
                    "Error converting schema to column definitions", e);
        }
        
        return columns;
    }

    /**
     * Parse JsonNode to ColumnDef
     */
    private ColumnDef parseJsonToColumnDef(JsonNode node, String type) {
        ColumnDef.ColumnDefBuilder builder = ColumnDef.builder()
                .name(node.path("name").asText())
                .type(type)
                .description(node.path("description").asText())
                .colorHex(node.path("color").asText())
                .orderNumber(node.path("orderNumber").asInt(9999))
                .freezeColumnIfFilled(node.path("freezeColumnIfFilled").asBoolean(false))
                .hideColumn(node.path("hideColumn").asBoolean(false))
                .required(node.path("isRequired").asBoolean(false))
                .width(node.has("width") ? node.path("width").asInt() : null)
                .wrapText(node.path("wrapText").asBoolean(false))
                .prefix(node.path("prefix").asText(null))
                .adjustHeight(node.path("adjustHeight").asBoolean(false))
                .showInProcessed(node.path("showInProcessed").asBoolean(true))
                .freezeColumn(node.path("freezeColumn").asBoolean(false))
                .freezeTillData(node.path("freezeTillData").asBoolean(false))
                .unFreezeColumnTillData(node.path("unFreezeColumnTillData").asBoolean(false));
        
        // Handle enum properties
        if ("enum".equals(type) && node.has("enum")) {
            List<String> enumValues = new ArrayList<>();
            node.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            builder.enumValues(enumValues);
        }
        
        // Handle multiSelectDetails for string properties
        if (node.has("multiSelectDetails")) {
            JsonNode multiSelectNode = node.path("multiSelectDetails");
            List<String> enumValues = new ArrayList<>();
            multiSelectNode.path("enum").forEach(enumNode -> enumValues.add(enumNode.asText()));
            
            MultiSelectDetails details = MultiSelectDetails.builder()
                    .maxSelections(multiSelectNode.path("maxSelections").asInt(1))
                    .minSelections(multiSelectNode.path("minSelections").asInt(0))
                    .enumValues(enumValues)
                    .build();
            
            builder.multiSelectDetails(details);
        }
        
        return builder.build();
    }


    /**
     * Create column definitions for boundary hierarchy sheet - one column per hierarchy level
     * Uses project-factory pattern: HIERARCHYTYPE_BOUNDARYTYPE
     */
    private List<ColumnDef> createBoundaryHierarchyColumnDefs(List<BoundaryHierarchyChild> hierarchyRelations, String hierarchyType) {
        List<ColumnDef> columns = new ArrayList<>();
        
        // Create columns for each hierarchy level using exact project-factory pattern
        // Pattern: ${hierarchyType}_${boundaryType} (e.g., MICROPLAN_COUNTRY, MICROPLAN_PROVINCE)
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String boundaryType = hierarchyRelations.get(i).getBoundaryType();
            
            // Create localization key in exact project-factory format: HIERARCHYTYPE_BOUNDARYTYPE
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
            
            columns.add(ColumnDef.builder()
                    .name(columnName)
                    .orderNumber(i + 1)
                    .width(50) // Smaller default width
                    .colorHex("#93c47d") // Correct boundary color
                    .build());
        }
        
        // Add hidden boundary code column (following project-factory pattern)
        columns.add(ColumnDef.builder()
                .name("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")
                .orderNumber(hierarchyRelations.size() + 1)
                .width(80) // Same as project-factory
                .hideColumn(true) // Hidden column
                .freezeColumn(true) // Same as project-factory
                .adjustHeight(true) // Same as project-factory
                .build());
        
        return columns;
    }

    /**
     * Get boundary hierarchy data from filtered boundaries - create one row per boundary with hierarchy path
     * Only includes last level (leaf) boundaries and adds hidden boundary code column
     * Uses project-factory pattern for column names: HIERARCHYTYPE_BOUNDARYTYPE
     */
    private List<Map<String, Object>> getBoundaryHierarchyDataFromFiltered(List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                                                          List<BoundaryHierarchyChild> hierarchyRelations,
                                                                          String hierarchyType,
                                                                          Map<String, String> localizationMap) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        // Find the index of the last level (leaf level)
        int lastLevelIndex = hierarchyRelations.size() - 1;
        
        // Process each filtered boundary to create hierarchy rows
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> boundaryPath = boundary.getBoundaryPath();
            
            // Only include boundaries that have data at the last level (leaf boundaries)
            if (boundaryPath.size() > lastLevelIndex && 
                boundaryPath.get(lastLevelIndex) != null && 
                !boundaryPath.get(lastLevelIndex).isEmpty()) {
                
                Map<String, Object> row = new HashMap<>();
                
                // Fill columns based on hierarchy levels using exact project-factory column naming
                for (int i = 0; i < hierarchyRelations.size(); i++) {
                    String boundaryType = hierarchyRelations.get(i).getBoundaryType();
                    // Use same column naming pattern as in createBoundaryHierarchyColumnDefs
                    String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();
                    String boundaryCode = i < boundaryPath.size() ? boundaryPath.get(i) : "";
                    String boundaryName = "";
                    
                    if (boundaryCode != null && !boundaryCode.isEmpty()) {
                        boundaryName = localizationMap.getOrDefault(boundaryCode, boundaryCode);
                    }
                    
                    row.put(columnName, boundaryName);
                }
                
                // Add the hidden boundary code column with the last level boundary code
                String lastLevelBoundaryCode = boundaryPath.get(lastLevelIndex);
                row.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE", lastLevelBoundaryCode);
                
                data.add(row);
            }
        }
        
        return data;
    }

    /**
     * Recursively collect boundary paths - each row represents complete hierarchy path to a boundary
     */
    private void collectBoundaryPaths(List<EnrichedBoundary> boundaries, List<String> currentPath,
                                    List<Map<String, Object>> data, List<BoundaryHierarchyChild> hierarchyRelations,
                                    Map<String, Integer> boundaryTypeToLevel, Map<String, String> localizationMap) {
        if (boundaries == null) return;
        
        for (EnrichedBoundary boundary : boundaries) {
            // Create new path with current boundary
            List<String> newPath = new ArrayList<>(currentPath);
            
            // Find the level for this boundary type
            Integer level = boundaryTypeToLevel.get(boundary.getBoundaryType());
            if (level != null) {
                // Extend path to accommodate this level
                while (newPath.size() <= level) {
                    newPath.add("");
                }
                // Set the boundary name at the correct level
                String boundaryName = localizationMap.getOrDefault(boundary.getCode(), boundary.getCode());
                newPath.set(level, boundaryName);
            }
            
            // If this boundary has children, recurse
            if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
                collectBoundaryPaths(boundary.getChildren(), newPath, data, hierarchyRelations, 
                                   boundaryTypeToLevel, localizationMap);
            } else {
                // This is a leaf boundary - create a row
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < hierarchyRelations.size(); i++) {
                    String columnName = hierarchyRelations.get(i).getBoundaryType();
                    String value = i < newPath.size() ? newPath.get(i) : "";
                    row.put(columnName, value);
                }
                data.add(row);
            }
        }
    }
}
