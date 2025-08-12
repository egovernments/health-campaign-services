package org.egov.excelingestion.web.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.util.ExcelSchemaSheetCreator;
import org.egov.excelingestion.web.models.*;
import org.springframework.stereotype.Component;
import org.egov.common.http.client.ServiceRequestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component("microplanProcessor")
@Slf4j
public class MicroplanProcessor implements IGenerateProcessor {

    private final ServiceRequestClient serviceRequestClient;
    private final ExcelIngestionConfig config;
    private final LocalizationService localizationService;

    public MicroplanProcessor(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config,
            LocalizationService localizationService) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.localizationService = localizationService;
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
        String locale = requestInfo.getMsgId() != null && requestInfo.getMsgId().split("\\|").length > 1
                ? requestInfo.getMsgId().split("\\|")[1]
                : "en_MZ";

        String localizationModule = "hcm-boundary-" + hierarchyType.toLowerCase(); // localization module name
        Map<String, String> localizationMap = localizationService.getLocalizedMessages(
                tenantId, localizationModule, locale, convertToExcelIngestionRequestInfo(requestInfo));
        
        // Also fetch localization for schema columns
        String schemaLocalizationModule = "hcm-admin-schemas";
        Map<String, String> schemaLocalizationMap = localizationService.getLocalizedMessages(
                tenantId, schemaLocalizationModule, locale, convertToExcelIngestionRequestInfo(requestInfo));
        
        // Merge both localization maps
        Map<String, String> mergedLocalizationMap = new HashMap<>();
        mergedLocalizationMap.putAll(localizationMap);
        mergedLocalizationMap.putAll(schemaLocalizationMap);

        // Fetch schema from MDMS for the mapping sheet
        String schemaJson = fetchSchemaFromMDMS(tenantId, requestInfo, "facility-microplan-ingestion");
        
        // Fetch schema from MDMS for the user sheet
        String userSchemaJson = fetchSchemaFromMDMS(tenantId, requestInfo, "user-microplan-ingestion");

        // Fetch boundary hierarchy data
        String hierarchyUrl = config.getHierarchySearchUrl();
        Map<String, Object> hierarchyPayload = createHierarchyPayload(requestInfo, tenantId, hierarchyType);
        
        log.info("Calling Boundary Hierarchy API: {} with tenantId: {}, hierarchyType: {}", hierarchyUrl, tenantId, hierarchyType);
        
        BoundaryHierarchyResponse hierarchyData;
        try {
            StringBuilder uri = new StringBuilder(hierarchyUrl);
            hierarchyData = serviceRequestClient.fetchResult(uri, hierarchyPayload, BoundaryHierarchyResponse.class);
            log.info("Successfully fetched boundary hierarchy data");
        } catch (Exception e) {
            log.error("Error calling Boundary Hierarchy API: {}", e.getMessage(), e);
            throw new RuntimeException("Error calling Boundary Hierarchy API: " + hierarchyUrl, e);
        }

        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null
                || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            throw new RuntimeException("Boundary Hierarchy Search API returned no data.");
        }

        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0)
                .getBoundaryHierarchy();

        StringBuilder url = new StringBuilder(config.getRelationshipSearchUrl());
        url.append("?includeChildren=true")
                .append("&tenantId=").append(URLEncoder.encode(tenantId, StandardCharsets.UTF_8))
                .append("&hierarchyType=").append(URLEncoder.encode(hierarchyType, StandardCharsets.UTF_8));

        // Fetch boundary relationship data
        Map<String, Object> relationshipPayload = createRelationshipPayload(requestInfo, tenantId, hierarchyType);
        
        log.info("Calling Boundary Relationship API: {} with tenantId: {}, hierarchyType: {}", url.toString(), tenantId, hierarchyType);
        
        BoundarySearchResponse relationshipData;
        try {
            relationshipData = serviceRequestClient.fetchResult(url, relationshipPayload, BoundarySearchResponse.class);
            log.info("Successfully fetched boundary relationship data");
        } catch (Exception e) {
            log.error("Error calling Boundary Relationship API: {}", e.getMessage(), e);
            throw new RuntimeException("Error calling Boundary Relationship API: " + url.toString(), e);
        }

        // 1) Build level list "Level 1", "Level 2", ...
        List<String> levels = new ArrayList<>();
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            levels.add("Level " + (i + 1));
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
            workbook = (XSSFWorkbook) ExcelSchemaSheetCreator.addSchemaSheetFromJson(schemaJson, actualFacilitySheetName, workbook, mergedLocalizationMap);
        }

        // === Levels sheet (display names visible) ===
        Sheet levelSheet = workbook.createSheet("_h_Levels_h_");
        Row levelRow = levelSheet.createRow(0);
        for (int i = 0; i < levels.size(); i++) {
            levelRow.createCell(i).setCellValue(levels.get(i)); // "Level 1", "Level 2", ...
        }
        // Named range "Levels"
        Name levelsNamedRange = workbook.createName();
        levelsNamedRange.setNameName("Levels");
        String lastColLetter = CellReference.convertNumToColString(levels.size() - 1);
        levelsNamedRange.setRefersToFormula("_h_Levels_h_!$A$1:$" + lastColLetter + "$1");

        // === Boundaries sheet (localized names) - one column per level ===
        Sheet boundarySheet = workbook.createSheet("_h_Boundaries_h_");
        int maxBoundaries = 0;
        for (int i = 0; i < levels.size(); i++) {
            String level = levels.get(i);
            Set<String> codePaths = boundariesByLevelCode.getOrDefault(level, Collections.emptySet());
            Set<String> localizedSet = boundariesByLevelLocalized.getOrDefault(level, Collections.emptySet());

            Row header = boundarySheet.getRow(0) != null ? boundarySheet.getRow(0) : boundarySheet.createRow(0);
            header.createCell(i).setCellValue(level); // header shows level display name

            // We will write localized names in this column (aligned with codePaths
            // iteration order)
            int rowNum = 1;
            // To keep mapping stable, iterate codePaths (which is LinkedHashSet) and write
            // localized for each codePath
            for (String codePath : codePaths) {
                String localized = codeToLocalized.getOrDefault(codePath, codePath);
                Row row = boundarySheet.getRow(rowNum) != null ? boundarySheet.getRow(rowNum)
                        : boundarySheet.createRow(rowNum);
                row.createCell(i).setCellValue(localized);
                rowNum++;
            }
            maxBoundaries = Math.max(maxBoundaries, codePaths.size());

            // Create named range for this level using sanitized name like Level_1
            if (!codePaths.isEmpty()) {
                String levelSanitized = makeNameValid(level);
                if (workbook.getName(levelSanitized) == null) {
                    Name namedRange = workbook.createName();
                    namedRange.setNameName(levelSanitized);
                    String colLetter = CellReference.convertNumToColString(i);
                    namedRange.setRefersToFormula(
                            "_h_Boundaries_h_!$" + colLetter + "$2:$" + colLetter + "$" + (codePaths.size() + 1));
                }
            }
        }

        // === Parents sheet ===
        // For parents sheet, each column corresponds to a child codePath (unique).
        // Column header = codePath
        // Under each header we will put localized parent names (if any).
        Sheet parentSheet = workbook.createSheet("_h_Parents_h_");
        int parentCol = 0;
        for (Map.Entry<String, List<String>> entry : childToParentCodes.entrySet()) {
            String childCode = entry.getKey(); // unique code path
            List<String> parentCodes = entry.getValue();

            Row headerRow = parentSheet.getRow(0) != null ? parentSheet.getRow(0) : parentSheet.createRow(0);
            // we store header as codePath so we can create named ranges by codePath
            headerRow.createCell(parentCol).setCellValue(childCode);

            int r = 1;
            for (String parentCode : parentCodes) {
                String localizedParent = codeToLocalized.getOrDefault(parentCode, parentCode);
                Row row = parentSheet.getRow(r) != null ? parentSheet.getRow(r) : parentSheet.createRow(r);
                row.createCell(parentCol).setCellValue(localizedParent);
                r++;
            }

            // create named range named by sanitized codePath (unique)
            String sanitizedCodeName = makeNameValid(childCode);
            if (workbook.getName(sanitizedCodeName) == null && !parentCodes.isEmpty()) {
                Name namedRange = workbook.createName();
                namedRange.setNameName(sanitizedCodeName);
                String colLetter = CellReference.convertNumToColString(parentCol);
                namedRange.setRefersToFormula(
                        "_h_Parents_h_!$" + colLetter + "$2:$" + colLetter + "$" + (parentCodes.size() + 1));
            }
            parentCol++;
        }

        // === CodeMap sheet: localizedName -> codePath (used by VLOOKUP in parent
        // dropdown) ===
        // IMPORTANT: if a localized name maps to multiple codePaths, the VLOOKUP will
        // find the first - avoid duplicates in localized names if possible
        Sheet codeMapSheet = workbook.createSheet("_h_CodeMap_h_");
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
        
        // Add boundary columns after schema columns
        // Row 0 contains technical names (hidden), Row 1 contains visible headers
        Row hiddenRow = mainSheet.getRow(0);
        Row visibleRow = mainSheet.getRow(1);
        
        if (hiddenRow == null) {
            hiddenRow = mainSheet.createRow(0);
        }
        if (visibleRow == null) {
            visibleRow = mainSheet.createRow(1);
        }
        
        // Find the last used column from schema
        int lastSchemaCol = visibleRow.getLastCellNum();
        if (lastSchemaCol < 0) lastSchemaCol = 0;
        
        // Add boundary-related columns after schema columns
        // Add technical names to hidden row
        hiddenRow.createCell(lastSchemaCol).setCellValue("BOUNDARY_LEVEL");
        hiddenRow.createCell(lastSchemaCol + 1).setCellValue("BOUNDARY_NAME");
        hiddenRow.createCell(lastSchemaCol + 2).setCellValue("PARENT_BOUNDARY");
        
        // Add localized headers to visible row
        visibleRow.createCell(lastSchemaCol).setCellValue(mergedLocalizationMap.getOrDefault("HCM_INGESTION_LEVEL_COLUMN", "Level"));
        visibleRow.createCell(lastSchemaCol + 1)
                .setCellValue(mergedLocalizationMap.getOrDefault("HCM_INGESTION_BOUNDARY_COLUMN", "Boundary Name"));
        visibleRow.createCell(lastSchemaCol + 2)
                .setCellValue(mergedLocalizationMap.getOrDefault("HCM_INGESTION_PARENT_COLUMN", "Parent Boundary"));

        DataValidationHelper dvHelper = mainSheet.getDataValidationHelper();

        // Add data validation for rows (2..5000) - starting from row 2 since row 0 is hidden and row 1 is headers
        for (int rowIndex = 2; rowIndex <= 5000; rowIndex++) {
            // Level dropdown uses named range "Levels" which contains display names "Level
            // 1", "Level 2"...
            DataValidationConstraint levelConstraint = dvHelper.createFormulaListConstraint("Levels");
            CellRangeAddressList levelAddr = new CellRangeAddressList(rowIndex, rowIndex, lastSchemaCol, lastSchemaCol);
            DataValidation levelValidation = dvHelper.createValidation(levelConstraint, levelAddr);
            levelValidation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            levelValidation.setShowErrorBox(true);
            levelValidation.createErrorBox("Invalid Level", "Please select a valid level from the dropdown list.");
            levelValidation.setShowPromptBox(true);
            levelValidation.createPromptBox("Select Level", "Choose a level from the dropdown list.");
            mainSheet.addValidationData(levelValidation);

            // Boundary dropdown: depends on Level chosen. Level cell contains "Level 1"
            // etc.
            // We created named ranges for Level as sanitized like "Level_1" that refer to
            // localized names in Boundaries sheet.
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
            mainSheet.addValidationData(boundaryValidation);

            // Parent dropdown: depends on selected boundary's localized name.
            // Steps: VLOOKUP(localizedBoundary, CodeMap!$A:$B, 2, FALSE) -> returns
            // codePath
            // INDIRECT(that codePath) -> named range we created for that child codePath in
            // Parents sheet
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
            mainSheet.addValidationData(parentValidation);
        }

        // Column widths & freeze
        for (int c = lastSchemaCol; c < lastSchemaCol + 3; c++) {
            mainSheet.setColumnWidth(c, 40 * 256);
        }
        mainSheet.createFreezePane(0, 2); // Freeze after row 2 since schema creator uses row 1 for technical names

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
            workbook = (XSSFWorkbook) ExcelSchemaSheetCreator.addEnhancedSchemaSheetFromJson(userSchemaJson, actualUserSheetName, workbook, mergedLocalizationMap);
        }

        // Unlock cells for user input
        CellStyle unlocked = workbook.createCellStyle();
        unlocked.setLocked(false);
        for (int r = 2; r <= 5000; r++) { // Start from row 2 to skip hidden technical row
            Row row = mainSheet.getRow(r);
            if (row == null)
                row = mainSheet.createRow(r);
            for (int c = lastSchemaCol; c < lastSchemaCol + 3; c++) {
                Cell cell = row.getCell(c);
                if (cell == null)
                    cell = row.createCell(c);
                cell.setCellStyle(unlocked);
            }
        }

        // Set zoom to 60% BEFORE protection and hiding (Excel compatibility)
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            sheet.setZoom(70);
        }
        
        // Hide all sheets except the main sheet and user sheet
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != mainSheet && !sheet.getSheetName().equals(actualUserSheetName)) {
                workbook.setSheetHidden(i, true);
            }
        }
        
        // Set the active sheet before protection
        workbook.setActiveSheet(workbook.getSheetIndex(mainSheet));
        
        // Protect sheet after all columns (schema + boundary) are configured
        mainSheet.protectSheet("passwordhere");
        workbook.lockStructure();
        workbook.setWorkbookPassword("passwordhere", HashAlgorithm.sha512);

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
            String level = boundaryTypeToLevel.getOrDefault(boundaryType, "Level 1");

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

    private Map<String, Object> createHierarchyPayload(RequestInfo requestInfo, String tenantId, String hierarchyType) {
        Map<String, Object> payload = new HashMap<>();
        // Directly use the RequestInfo object
        payload.put("RequestInfo", requestInfo);

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("tenantId", tenantId);
        criteria.put("limit", 5);
        criteria.put("offset", 0);
        criteria.put("hierarchyType", hierarchyType);
        payload.put("BoundaryTypeHierarchySearchCriteria", criteria);
        return payload;
    }

    private Map<String, Object> createRelationshipPayload(RequestInfo requestInfo, String tenantId,
            String hierarchyType) {
        Map<String, Object> payload = new HashMap<>();
        // Directly use the RequestInfo object
        payload.put("RequestInfo", requestInfo);
        return payload;
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

    private org.egov.excelingestion.web.models.RequestInfo convertToExcelIngestionRequestInfo(
            RequestInfo commonRequestInfo) {
        return RequestInfo.builder()
                .apiId(commonRequestInfo.getApiId())
                .ver(commonRequestInfo.getVer())
                .ts(commonRequestInfo.getTs())
                .action(commonRequestInfo.getAction())
                .did(commonRequestInfo.getDid())
                .key(commonRequestInfo.getKey())
                .msgId(commonRequestInfo.getMsgId())
                .requesterId(commonRequestInfo.getRequesterId())
                .authToken(commonRequestInfo.getAuthToken())
                .userInfo(UserInfo.builder()
                        .id(commonRequestInfo.getUserInfo().getId())
                        .uuid(commonRequestInfo.getUserInfo().getUuid())
                        .userName(commonRequestInfo.getUserInfo().getUserName())
                        .name(commonRequestInfo.getUserInfo().getName())
                        .mobileNumber(commonRequestInfo.getUserInfo().getMobileNumber())
                        .emailId(commonRequestInfo.getUserInfo().getEmailId())
                        .locale(commonRequestInfo.getUserInfo().getLocale())
                        .type(commonRequestInfo.getUserInfo().getType())
                        .tenantId(commonRequestInfo.getUserInfo().getTenantId())
                        .build())
                .correlationId(commonRequestInfo.getCorrelationId())
                .build();
    }

    @Override
    public String getType() {
        return "microplan-ingestion";
    }

    private String fetchSchemaFromMDMS(String tenantId, RequestInfo requestInfo, String title) {
        String url = config.getMdmsSearchUrl();
        
        try {
            // Create MDMS request payload
            Map<String, Object> mdmsRequest = new HashMap<>();
            
            // Directly use the RequestInfo object
            mdmsRequest.put("RequestInfo", requestInfo);

            Map<String, Object> mdmsCriteria = new HashMap<>();
            mdmsCriteria.put("tenantId", tenantId);
            
            // Use filters with title instead of uniqueIdentifier
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", title);
            mdmsCriteria.put("filters", filters);
            
            mdmsCriteria.put("schemaCode", "HCM-ADMIN-CONSOLE.schemas");
            mdmsCriteria.put("limit", 1);
            mdmsCriteria.put("offset", 0);
            mdmsCriteria.put("sortBy", "uniqueIdentifier");
            mdmsCriteria.put("order", "ASC");
            mdmsRequest.put("MdmsCriteria", mdmsCriteria);

            // Call MDMS service
            log.info("Calling MDMS API: {} for schema: {}, tenantId: {}", url, title, tenantId);
            
            StringBuilder uri = new StringBuilder(url);
            Map<String, Object> responseBody = serviceRequestClient.fetchResult(uri, mdmsRequest, Map.class);
            
            if (responseBody != null && responseBody.get("mdms") != null) {
                List<Map<String, Object>> mdmsList = (List<Map<String, Object>>) responseBody.get("mdms");
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
            }
            log.warn("No MDMS data found for schema: {}", title);
        } catch (Exception e) {
            log.error("Error calling MDMS API for schema {}: {}", title, e.getMessage(), e);
        }
        
        // Return a default schema if MDMS fetch fails
        return getDefaultSchema();
    }

    private String getDefaultSchema() {
        // Default schema with basic columns if MDMS fetch fails
        return "{\"stringProperties\":[],\"numberProperties\":[],\"enumProperties\":[]}";
    }
}
