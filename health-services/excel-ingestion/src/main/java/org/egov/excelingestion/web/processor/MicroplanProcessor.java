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
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.service.ApiPayloadBuilder;
import org.springframework.stereotype.Component;

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

    public MicroplanProcessor(ExcelIngestionConfig config,
            LocalizationService localizationService, BoundaryHierarchySheetCreator boundaryHierarchySheetCreator,
            BoundaryService boundaryService, RequestInfoConverter requestInfoConverter,
            ApiPayloadBuilder apiPayloadBuilder, ExcelSchemaSheetCreator excelSchemaSheetCreator,
            CampaignConfigSheetCreator campaignConfigSheetCreator, MDMSService mdmsService,
            ExcelStyleHelper excelStyleHelper, BoundaryUtil boundaryUtil, CustomExceptionHandler exceptionHandler) {
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
        // child codePath -> list of parent codePaths
        Map<String, List<String>> childToParentCodes = new LinkedHashMap<>();

        levels.forEach(l -> {
            boundariesByLevelLocalized.put(l, new TreeSet<>());
            boundariesByLevelCode.put(l, new LinkedHashSet<>());
        });

        // Populate the above maps by traversing relationship data
        if (relationshipData != null && relationshipData.getTenantBoundary() != null) {
            for (HierarchyRelation hr : relationshipData.getTenantBoundary()) {
                processNodesBuildCodePaths(hr.getBoundary(), boundaryTypeToLevel, null,
                        boundariesByLevelLocalized, boundariesByLevelCode, codeToLocalized, childToParentCodes,
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
        
        // === Create facility sheet using schema with correct localized name ===
        if (schemaJson != null && !schemaJson.isEmpty()) {
            workbook = (XSSFWorkbook) excelSchemaSheetCreator.addSchemaSheetFromJson(schemaJson, actualFacilitySheetName, workbook, mergedLocalizationMap);
        }

        // === Level Mapping sheet (hidden) for localization keys and values ===
        Sheet levelMappingSheet = workbook.createSheet("_h_LevelMapping_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_LevelMapping_h_"), true);
        
        // Header row
        Row mappingHeaderRow = levelMappingSheet.createRow(0);
        mappingHeaderRow.createCell(0).setCellValue("Level Key");
        mappingHeaderRow.createCell(1).setCellValue("Localized Level");
        mappingHeaderRow.createCell(2).setCellValue("Generic Level");
        
        // Data rows
        for (int i = 0; i < levels.size(); i++) {
            Row mappingRow = levelMappingSheet.createRow(i + 1);
            mappingRow.createCell(0).setCellValue(levelKeys.get(i));          // HCM_CAMP_CONF_LEVEL_1
            mappingRow.createCell(1).setCellValue(levels.get(i));             // Localized level name
            mappingRow.createCell(2).setCellValue("Level " + (i + 1));        // Generic "Level 1" for backward compatibility
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

        // === Parents sheet ===
        // For parents sheet, each column corresponds to a child codePath (unique).
        // Column header = codePath
        // Under each header we will put localized parent names (if any).
        Sheet parentSheet = workbook.createSheet("_h_Parents_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_Parents_h_"), true);
        int parentCol = 0;
        for (Map.Entry<String, List<String>> entry : childToParentCodes.entrySet()) {
            String childCode = entry.getKey(); // unique code path
            List<String> parentCodes = entry.getValue();

            Row headerRow = parentSheet.getRow(0) != null ? parentSheet.getRow(0) : parentSheet.createRow(0);
            // we store header as codePath so we can create named ranges by codePath
            headerRow.createCell(parentCol).setCellValue(childCode);

            // Sort parent names alphabetically
            List<String> sortedParentNames = new ArrayList<>();
            for (String parentCode : parentCodes) {
                String localizedParent = codeToLocalized.getOrDefault(parentCode, parentCode);
                sortedParentNames.add(localizedParent);
            }
            Collections.sort(sortedParentNames, String.CASE_INSENSITIVE_ORDER);

            int r = 1;
            for (String localizedParent : sortedParentNames) {
                Row row = parentSheet.getRow(r) != null ? parentSheet.getRow(r) : parentSheet.createRow(r);
                row.createCell(parentCol).setCellValue(localizedParent);
                r++;
            }

            // create named range named by sanitized codePath (unique)
            String sanitizedCodeName = makeNameValid(childCode);
            if (workbook.getName(sanitizedCodeName) == null && !sortedParentNames.isEmpty()) {
                Name namedRange = workbook.createName();
                namedRange.setNameName(sanitizedCodeName);
                String colLetter = CellReference.convertNumToColString(parentCol);
                namedRange.setRefersToFormula(
                        "_h_Parents_h_!$" + colLetter + "$2:$" + colLetter + "$" + (sortedParentNames.size() + 1));
            }
            parentCol++;
        }

        // === CodeMap sheet: localizedName -> codePath (used by VLOOKUP in parent
        // dropdown) ===
        // IMPORTANT: if a localized name maps to multiple codePaths, the VLOOKUP will
        // find the first - avoid duplicates in localized names if possible
        Sheet codeMapSheet = workbook.createSheet("_h_CodeMap_h_");
        workbook.setSheetHidden(workbook.getSheetIndex("_h_CodeMap_h_"), true);
        Row cmHeader = codeMapSheet.createRow(0);
        cmHeader.createCell(0).setCellValue("LocalizedName");
        cmHeader.createCell(1).setCellValue("CodePath");

        int cmRow = 1;
        for (Map.Entry<String, String> e : codeToLocalized.entrySet()) {
            String codePath = e.getKey();
            String localized = e.getValue();
            Row r = codeMapSheet.getRow(cmRow) != null ? codeMapSheet.getRow(cmRow) : codeMapSheet.createRow(cmRow);
            r.createCell(0).setCellValue(localized);
            r.createCell(1).setCellValue(codePath);
            cmRow++;
        }

        // create a named range for CodeMap if desired (not strictly necessary) - we use
        // full sheet reference in VLOOKUP

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
            workbook = (XSSFWorkbook) excelSchemaSheetCreator.addEnhancedSchemaSheetFromJson(userSchemaJson, actualUserSheetName, workbook, mergedLocalizationMap);
            
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
        
        workbook = (XSSFWorkbook) boundaryHierarchySheetCreator.createBoundaryHierarchySheet(
                workbook, hierarchyType, tenantId, requestInfo,
                mergedLocalizationMap, actualHierarchySheetName, generateResource.getBoundaries());


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
        
        // Protect facility sheet after all columns (schema + boundary) are configured
        mainSheet.protectSheet(config.getExcelSheetPassword());
        workbook.lockStructure();
        workbook.setWorkbookPassword(config.getExcelSheetPassword(), HashAlgorithm.sha512);

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
     * - childToParentCodes: childCodePath -> list of parent codePaths
     *
     * @param nodes                      current nodes
     * @param boundaryTypeToLevel        mapping boundaryType -> LevelName ("Level
     *                                   1" etc)
     * @param parentCodePath             parent code path (null if root)
     * @param boundariesByLevelLocalized
     * @param boundariesByLevelCode
     * @param codeToLocalized
     * @param childToParentCodes
     * @param localizationMap            code->localized string map from
     *                                   LocalizationService
     */
    private void processNodesBuildCodePaths(List<EnrichedBoundary> nodes,
            Map<String, String> boundaryTypeToLevel,
            String parentCodePath,
            Map<String, Set<String>> boundariesByLevelLocalized,
            Map<String, Set<String>> boundariesByLevelCode,
            Map<String, String> codeToLocalized,
            Map<String, List<String>> childToParentCodes,
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

            // if parent exists, add parent code mapping for child
            if (parentCodePath != null) {
                childToParentCodes.computeIfAbsent(codePath, k -> new ArrayList<>()).add(parentCodePath);
            } else {
                // ensure childToParentCodes has an entry (maybe empty list)
                childToParentCodes.putIfAbsent(codePath, new ArrayList<>());
            }

            // Recurse children: pass current codePath as parent
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                processNodesBuildCodePaths(node.getChildren(), boundaryTypeToLevel, codePath,
                        boundariesByLevelLocalized, boundariesByLevelCode, codeToLocalized, childToParentCodes,
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
        
        // Add boundary-related columns after schema columns
        // Add technical names to hidden row
        hiddenRow.createCell(lastSchemaCol).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(lastSchemaCol + 1).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(lastSchemaCol + 2).setCellValue("PARENT_BOUNDARY");
        
        // Create header style for boundary columns
        CellStyle boundaryHeaderStyle = excelStyleHelper.createHeaderStyle(workbook, config.getDefaultHeaderColor());
        
        // Add localized headers to visible row with styling
        Cell levelHeaderCell = visibleRow.createCell(lastSchemaCol);
        levelHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_LEVEL_COLUMN", "HCM_INGESTION_LEVEL_COLUMN"));
        levelHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        Cell boundaryHeaderCell = visibleRow.createCell(lastSchemaCol + 1);
        boundaryHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_BOUNDARY_COLUMN", "HCM_INGESTION_BOUNDARY_COLUMN"));
        boundaryHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        Cell parentHeaderCell = visibleRow.createCell(lastSchemaCol + 2);
        parentHeaderCell.setCellValue(localizationMap.getOrDefault("HCM_INGESTION_PARENT_COLUMN", "HCM_INGESTION_PARENT_COLUMN"));
        parentHeaderCell.setCellStyle(boundaryHeaderStyle);
        
        // Create boundary dropdown data based on filtered boundaries
        createBoundaryDropdownData(workbook, filteredBoundaries, levelTypes, localizationMap);
        
        // Add data validations for boundary columns
        addBoundaryDataValidations(workbook, sheet, lastSchemaCol, levelTypes, localizationMap);


        // Column widths & freeze
        for (int c = lastSchemaCol; c < lastSchemaCol + 3; c++) {
            sheet.setColumnWidth(c, 40 * 256);
        }
        sheet.createFreezePane(0, 2); // Freeze after row 2 since schema creator uses row 1 for technical names

        // Unlock cells for user input (for both sheets)
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        for (int r = 2; r <= config.getExcelRowLimit(); r++) { // Start from row 2 to skip hidden technical row
            Row row = sheet.getRow(r);
            if (row == null)
                row = sheet.createRow(r);
            for (int c = lastSchemaCol; c < lastSchemaCol + 3; c++) {
                Cell cell = row.getCell(c);
                if (cell == null)
                    cell = row.createCell(c);
                cell.setCellStyle(unlocked);
            }
        }
    }

    /**
     * Creates boundary dropdown data sheets based on filtered boundaries
     */
    private void createBoundaryDropdownData(XSSFWorkbook workbook, List<BoundaryUtil.BoundaryRowData> filteredBoundaries,
                                          List<String> levelTypes, Map<String, String> localizationMap) {
        
        // Create level sheet for dropdown options (or use existing one)
        Sheet levelSheet = workbook.getSheet("_h_Levels_h_");
        if (levelSheet == null) {
            levelSheet = workbook.createSheet("_h_Levels_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Levels_h_"), true);
        }
        
        // Clear existing content if sheet already existed
        if (levelSheet.getLastRowNum() >= 0) {
            for (int i = levelSheet.getLastRowNum(); i >= 0; i--) {
                Row row = levelSheet.getRow(i);
                if (row != null) {
                    levelSheet.removeRow(row);
                }
            }
        }
        
        Row levelRow = levelSheet.createRow(0);
        
        // Build ALL levels from hierarchy using localized level names (not just ones with data)
        Set<String> availableLevels = new LinkedHashSet<>();
        Map<String, String> levelIndexToLocalizedName = new HashMap<>();
        
        // Create all levels based on the full hierarchy
        for (int i = 0; i < levelTypes.size(); i++) {
            String levelKey = "HCM_CAMP_CONF_LEVEL_" + (i + 1);
            String localizedLevel = localizationMap.getOrDefault(levelKey, levelKey);
            availableLevels.add(localizedLevel);
            levelIndexToLocalizedName.put(String.valueOf(i), localizedLevel);
        }
        
        int colIndex = 0;
        for (String level : availableLevels) {
            levelRow.createCell(colIndex++).setCellValue(level);
        }
        
        // Create named range "Levels" (or update existing one) only if we have levels
        if (!availableLevels.isEmpty()) {
            Name levelsNamedRange = workbook.getName("Levels");
            if (levelsNamedRange == null) {
                levelsNamedRange = workbook.createName();
                levelsNamedRange.setNameName("Levels");
            }
            String lastColLetter = CellReference.convertNumToColString(availableLevels.size() - 1);
            levelsNamedRange.setRefersToFormula("_h_Levels_h_!$A$1:$" + lastColLetter + "$1");
        }
        
        // Create boundaries sheet with filtered boundaries (or use existing one)
        Sheet boundarySheet = workbook.getSheet("_h_Boundaries_h_");
        if (boundarySheet == null) {
            boundarySheet = workbook.createSheet("_h_Boundaries_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_Boundaries_h_"), true);
        }
        
        // Clear existing content if sheet already existed
        if (boundarySheet.getLastRowNum() >= 0) {
            for (int i = boundarySheet.getLastRowNum(); i >= 0; i--) {
                Row row = boundarySheet.getRow(i);
                if (row != null) {
                    boundarySheet.removeRow(row);
                }
            }
        }
        
        // Group boundaries by level
        Map<String, Set<String>> boundariesByLevel = new HashMap<>();
        Map<String, String> codeToLocalized = new HashMap<>();
        
        // Debug: Log the boundary data we're processing
        log.info("Processing {} filtered boundaries for dropdown creation", filteredBoundaries.size());
        for (int idx = 0; idx < Math.min(5, filteredBoundaries.size()); idx++) {
            BoundaryUtil.BoundaryRowData boundary = filteredBoundaries.get(idx);
            log.info("Boundary {}: path size={}, path={}", idx, boundary.getBoundaryPath().size(), boundary.getBoundaryPath());
        }
        
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 0; i < path.size(); i++) {  // Remove levelTypes.size() limit to see all levels
                if (path.get(i) != null) {
                    String levelKey = "HCM_CAMP_CONF_LEVEL_" + (i + 1);
                    String localizedLevel = localizationMap.getOrDefault(levelKey, levelKey);
                    boundariesByLevel.computeIfAbsent(localizedLevel, k -> new TreeSet<>()).add(path.get(i));
                    
                    // Store localized name for the boundary code
                    String localizedName = localizationMap.getOrDefault(path.get(i), path.get(i));
                    codeToLocalized.put(path.get(i), localizedName);
                }
            }
        }
        
        // Debug: Log what levels we found
        log.info("Found boundaries for levels: {}", boundariesByLevel.keySet());
        
        // Write boundary data by level
        int maxBoundaries = 0;
        colIndex = 0;
        for (String level : availableLevels) {
            Row header = boundarySheet.getRow(0) != null ? boundarySheet.getRow(0) : boundarySheet.createRow(0);
            header.createCell(colIndex).setCellValue(level);
            
            Set<String> boundaries = boundariesByLevel.getOrDefault(level, new TreeSet<>());
            List<String> sortedBoundaries = new ArrayList<>();
            for (String boundaryCode : boundaries) {
                sortedBoundaries.add(codeToLocalized.getOrDefault(boundaryCode, boundaryCode));
            }
            Collections.sort(sortedBoundaries, String.CASE_INSENSITIVE_ORDER);
            
            int rowNum = 1;
            for (String boundaryName : sortedBoundaries) {
                Row row = boundarySheet.getRow(rowNum) != null ? boundarySheet.getRow(rowNum) : boundarySheet.createRow(rowNum);
                row.createCell(colIndex).setCellValue(boundaryName);
                rowNum++;
            }
            maxBoundaries = Math.max(maxBoundaries, sortedBoundaries.size());
            
            // Create named range for this level (or update existing one)
            if (!sortedBoundaries.isEmpty()) {
                // Create sanitized name for Excel named range - find the corresponding level index
                String levelSanitized = null;
                for (Map.Entry<String, String> entry : levelIndexToLocalizedName.entrySet()) {
                    if (entry.getValue().equals(level)) {
                        levelSanitized = "Level_" + (Integer.parseInt(entry.getKey()) + 1);
                        break;
                    }
                }
                
                if (levelSanitized != null) {
                    Name namedRange = workbook.getName(levelSanitized);
                    if (namedRange == null) {
                        namedRange = workbook.createName();
                        namedRange.setNameName(levelSanitized);
                    }
                    String colLetter = CellReference.convertNumToColString(colIndex);
                    namedRange.setRefersToFormula("_h_Boundaries_h_!$" + colLetter + "$2:$" + colLetter + "$" + (sortedBoundaries.size() + 1));
                }
            }
            colIndex++;
        }
        
        // Create code mapping sheet for parent lookups (or use existing one)
        Sheet codeMapSheet = workbook.getSheet("_h_CodeMap_h_");
        if (codeMapSheet == null) {
            codeMapSheet = workbook.createSheet("_h_CodeMap_h_");
            workbook.setSheetHidden(workbook.getSheetIndex("_h_CodeMap_h_"), true);
        }
        
        // Clear existing content if sheet already existed
        if (codeMapSheet.getLastRowNum() >= 0) {
            for (int i = codeMapSheet.getLastRowNum(); i >= 0; i--) {
                Row row = codeMapSheet.getRow(i);
                if (row != null) {
                    codeMapSheet.removeRow(row);
                }
            }
        }
        
        Row codeMapHeader = codeMapSheet.createRow(0);
        codeMapHeader.createCell(0).setCellValue("Boundary Name");
        codeMapHeader.createCell(1).setCellValue("Boundary Code");
        codeMapHeader.createCell(2).setCellValue("Parent Name");
        codeMapHeader.createCell(3).setCellValue("Level Name");
        codeMapHeader.createCell(4).setCellValue("Named Range");
        
        int codeRowNum = 1;
        
        // Build parent-child mapping from boundary paths
        Map<String, String> childToParentName = new HashMap<>();
        for (BoundaryUtil.BoundaryRowData boundary : filteredBoundaries) {
            List<String> path = boundary.getBoundaryPath();
            for (int i = 1; i < path.size(); i++) { // Start from 1 to skip root
                if (path.get(i) != null && path.get(i-1) != null) {
                    String childCode = path.get(i);
                    String parentCode = path.get(i-1);
                    String childName = localizationMap.getOrDefault(childCode, childCode);
                    String parentName = localizationMap.getOrDefault(parentCode, parentCode);
                    childToParentName.put(childName, parentName);
                }
            }
        }
        
        // Add boundary mappings with parent information
        for (Map.Entry<String, String> entry : codeToLocalized.entrySet()) {
            Row codeRow = codeMapSheet.createRow(codeRowNum++);
            String boundaryName = entry.getValue(); // Localized boundary name
            String boundaryCode = entry.getKey();   // Boundary code
            String parentName = childToParentName.getOrDefault(boundaryName, ""); // Parent name
            
            codeRow.createCell(0).setCellValue(boundaryName);
            codeRow.createCell(1).setCellValue(boundaryCode);
            codeRow.createCell(2).setCellValue(parentName);
        }
        
        // Add level name to named range mappings
        for (Map.Entry<String, String> entry : levelIndexToLocalizedName.entrySet()) {
            Row levelMappingRow = codeMapSheet.createRow(codeRowNum++);
            levelMappingRow.createCell(3).setCellValue(entry.getValue()); // Localized level name
            levelMappingRow.createCell(4).setCellValue("Level_" + (Integer.parseInt(entry.getKey()) + 1)); // Named range name
        }
    }
    
    /**
     * Adds data validations for boundary columns
     */
    private void addBoundaryDataValidations(XSSFWorkbook workbook, Sheet sheet, int lastSchemaCol, List<String> levelTypes, 
                                          Map<String, String> localizationMap) {
        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        
        // Add data validation for rows (2..5000) - starting from row 2 since row 0 is hidden and row 1 is headers
        for (int rowIndex = 2; rowIndex <= config.getExcelRowLimit(); rowIndex++) {
            // Level dropdown uses named range "Levels"
            DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
            CellRangeAddressList levelAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol, lastSchemaCol);
            DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
            levelValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            levelValidation.setShowErrorBox(true);
            levelValidation.createErrorBox("Invalid Level", "Please select a valid level from the dropdown list.");
            levelValidation.setShowPromptBox(true);
            levelValidation.createPromptBox("Select Level", "Choose a level from the dropdown list.");
            sheet.addValidationData(levelValidation);
            
            // Boundary dropdown: depends on Level chosen - lookup the named range from the mapping  
            String levelCellRef = CellReference.convertNumToColString(lastSchemaCol) + (rowIndex + 1);
            String boundaryFormula = "IF(" + levelCellRef + "=\"\", \"\", INDIRECT(VLOOKUP(" + levelCellRef + ", _h_CodeMap_h_!$D:$E, 2, FALSE)))";
            DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint(boundaryFormula);
            CellRangeAddressList boundaryAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol + 1, lastSchemaCol + 1);
            DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
            boundaryValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            boundaryValidation.setShowErrorBox(true);
            boundaryValidation.createErrorBox("Invalid Boundary", "Please select a valid boundary from the dropdown list.");
            boundaryValidation.setShowPromptBox(true);
            boundaryValidation.createPromptBox("Select Boundary", "Choose a boundary based on the selected level.");
            sheet.addValidationData(boundaryValidation);
            
            // Parent column: automatically populate with formula (not a dropdown)
            // The parent will be auto-filled when user selects a boundary
            String boundaryCellRef = CellReference.convertNumToColString(lastSchemaCol + 1) + (rowIndex + 1);
            String parentFormula = "IF(" + boundaryCellRef + "=\"\", \"\", IFERROR(VLOOKUP(" + boundaryCellRef + ", _h_CodeMap_h_!$A:$C, 3, FALSE), \"\"))";
            
            // Set formula in parent column cells
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            Cell parentCell = row.createCell(lastSchemaCol + 2);
            parentCell.setCellFormula(parentFormula);
            
            // Make parent cell locked (user can't edit it directly)
            CellStyle lockedStyle = workbook.createCellStyle();
            lockedStyle.setLocked(true);
            parentCell.setCellStyle(lockedStyle);
        }
    }
    
}
