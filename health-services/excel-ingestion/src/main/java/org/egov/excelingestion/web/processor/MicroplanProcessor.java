package org.egov.excelingestion.web.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.awt.Color;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.ExcelSchemaSheetCreator;
import org.egov.excelingestion.util.BoundaryHierarchySheetCreator;
import org.egov.excelingestion.util.CampaignConfigSheetCreator;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.service.ApiPayloadBuilder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    
    @Autowired
    private CustomExceptionHandler exceptionHandler;

    public MicroplanProcessor(ExcelIngestionConfig config,
            LocalizationService localizationService, BoundaryHierarchySheetCreator boundaryHierarchySheetCreator,
            BoundaryService boundaryService, RequestInfoConverter requestInfoConverter,
            ApiPayloadBuilder apiPayloadBuilder, ExcelSchemaSheetCreator excelSchemaSheetCreator,
            CampaignConfigSheetCreator campaignConfigSheetCreator, MDMSService mdmsService) {
        this.config = config;
        this.localizationService = localizationService;
        this.boundaryHierarchySheetCreator = boundaryHierarchySheetCreator;
        this.boundaryService = boundaryService;
        this.requestInfoConverter = requestInfoConverter;
        this.apiPayloadBuilder = apiPayloadBuilder;
        this.excelSchemaSheetCreator = excelSchemaSheetCreator;
        this.campaignConfigSheetCreator = campaignConfigSheetCreator;
        this.mdmsService = mdmsService;
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
        String schemaJson = fetchSchemaFromMDMS(tenantId, requestInfo, "facility-microplan-ingestion");
        
        // Fetch schema from MDMS for the user sheet
        String userSchemaJson = fetchSchemaFromMDMS(tenantId, requestInfo, "user-microplan-ingestion");

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
        String campaignConfigData = fetchCampaignConfigFromMDMS(tenantId, requestInfo, "HCM_CAMP_CONF_SHEETNAME");
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
            Set<String> localizedSet = boundariesByLevelLocalized.getOrDefault(level, Collections.emptySet());

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
        
        // Add boundary columns to facility sheet
        addBoundaryColumnsToSheet(workbook, actualFacilitySheetName, mergedLocalizationMap);

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
            
            // Add boundary columns to user sheet as well
            addBoundaryColumnsToSheet(workbook, actualUserSheetName, mergedLocalizationMap);
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

    private String fetchSchemaFromMDMS(String tenantId, RequestInfo requestInfo, String title) {
        try {
            // Create filters for schema search
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", title);
            
            // Call MDMS service
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, "HCM-ADMIN-CONSOLE.schemas", filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                // Extract the properties part which contains stringProperties, numberProperties, and enumProperties
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                if (properties != null) {
                    // Convert properties to JSON string
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully fetched MDMS schema for: {}", title);
                    return mapper.writeValueAsString(properties);
                }
            }
            log.warn("No MDMS data found for schema: {}", title);
        } catch (Exception e) {
            log.error("Error fetching MDMS schema {}: {}", title, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR, 
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        // No MDMS data found - throw error instead of returning default
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND, 
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", title),
                new RuntimeException("Schema '" + title + "' not found in MDMS configuration"));
        return null; // This will never be reached due to exception throwing above
    }


    private String fetchCampaignConfigFromMDMS(String tenantId, RequestInfo requestInfo, String sheetName) {
        try {
            // Create filters for campaign config search
            Map<String, Object> filters = new HashMap<>();
            filters.put("sheetName", sheetName);
            
            // Call MDMS service
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, "HCM-ADMIN-CONSOLE.configsheet", filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                
                if (data != null) {
                    // Convert data to JSON string
                    ObjectMapper mapper = new ObjectMapper();
                    log.info("Successfully fetched MDMS campaign config for: {}", sheetName);
                    return mapper.writeValueAsString(data);
                }
            }
            log.warn("No MDMS data found for campaign config: {}", sheetName);
        } catch (Exception e) {
            log.error("Error fetching MDMS campaign config {}: {}", sheetName, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_SERVICE_ERROR, 
                    ErrorConstants.MDMS_SERVICE_ERROR_MESSAGE, e);
        }
        
        // No MDMS data found - throw error instead of returning null
        exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND, 
                ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", sheetName),
                new RuntimeException("Campaign config sheet '" + sheetName + "' not found in MDMS configuration"));
        return null; // This will never be reached due to exception throwing above
    }


    private void addBoundaryColumnsToSheet(XSSFWorkbook workbook, String sheetName, Map<String, String> localizationMap) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            log.warn("Sheet '{}' not found, cannot add boundary columns", sheetName);
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
        CellStyle boundaryHeaderStyle = createBoundaryHeaderStyle(workbook, config.getDefaultHeaderColor());
        
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

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();

        // Add data validation for rows (2..5000) - starting from row 2 since row 0 is hidden and row 1 is headers
        for (int rowIndex = 2; rowIndex <= config.getExcelRowLimit(); rowIndex++) {
            // Level dropdown uses named range "Levels" which contains display names "Level 1", "Level 2"...
            DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
            CellRangeAddressList levelAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol, lastSchemaCol);
            DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
            levelValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            levelValidation.setShowErrorBox(true);
            levelValidation.createErrorBox("Invalid Level", "Please select a valid level from the dropdown list.");
            levelValidation.setShowPromptBox(true);
            levelValidation.createPromptBox("Select Level", "Choose a level from the dropdown list.");
            sheet.addValidationData(levelValidation);

            // Boundary dropdown: depends on Level chosen. Level cell contains "Level 1" etc.
            // We created named ranges for Level as sanitized like "Level_1" that refer to localized names in Boundaries sheet.
            String levelCellRef = CellReference.convertNumToColString(lastSchemaCol) + (rowIndex + 1);
            String boundaryFormula = "INDIRECT(SUBSTITUTE(" + levelCellRef + ",\" \",\"_\"))";
            DataValidationConstraint boundaryConstraint = dvHelper.createFormulaListConstraint(boundaryFormula);
            CellRangeAddressList boundaryAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol + 1, lastSchemaCol + 1);
            DataValidation boundaryValidation = dvHelper.createValidation(boundaryConstraint, boundaryAddr);
            boundaryValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            boundaryValidation.setShowErrorBox(true);
            boundaryValidation.createErrorBox("Invalid Boundary", "Please select a valid boundary from the dropdown list.");
            boundaryValidation.setShowPromptBox(true);
            boundaryValidation.createPromptBox("Select Boundary", "Choose a boundary based on the selected level.");
            sheet.addValidationData(boundaryValidation);

            // Parent dropdown: depends on selected boundary's localized name.
            // Steps: VLOOKUP(localizedBoundary, CodeMap!$A:$B, 2, FALSE) -> returns codePath
            // INDIRECT(that codePath) -> named range we created for that child codePath in Parents sheet
            String boundaryCellRef = CellReference.convertNumToColString(lastSchemaCol + 1) + (rowIndex + 1);
            String parentFormula = "IF(" + boundaryCellRef + "=\"\", \"\", INDIRECT(VLOOKUP(" + boundaryCellRef
                    + ", _h_CodeMap_h_!$A:$B, 2, FALSE)))";
            DataValidationConstraint parentConstraint = dvHelper.createFormulaListConstraint(parentFormula);
            CellRangeAddressList parentAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol + 2, lastSchemaCol + 2);
            DataValidation parentValidation = dvHelper.createValidation(parentConstraint, parentAddr);
            parentValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            parentValidation.setShowErrorBox(true);
            parentValidation.createErrorBox("Invalid Parent Boundary", "Please select a valid parent boundary from the dropdown list.");
            parentValidation.setShowPromptBox(true);
            parentValidation.createPromptBox("Select Parent Boundary", "Choose a parent boundary based on the selected boundary.");
            sheet.addValidationData(parentValidation);
        }

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
    
    private CellStyle createBoundaryHeaderStyle(Workbook workbook, String colorHex) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        
        // Set background color
        Color color = Color.decode(colorHex);
        XSSFColor xssfColor = new XSSFColor(color, null);
        style.setFillForegroundColor(xssfColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // Set borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // Center align text
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // Bold font
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        // Lock the cell
        style.setLocked(true);
        
        return style;
    }
}
