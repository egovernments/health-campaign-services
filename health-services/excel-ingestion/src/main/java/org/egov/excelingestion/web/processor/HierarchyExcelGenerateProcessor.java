package org.egov.excelingestion.web.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.tracer.model.CustomException;
import org.egov.excelingestion.service.FileStoreService;
import org.egov.excelingestion.service.LocalizationService;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.util.RequestInfoConverter;
import org.egov.excelingestion.service.ApiPayloadBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component("hierarchyExcelGenerateProcessor")
@Slf4j
public class HierarchyExcelGenerateProcessor implements IGenerateProcessor {

    private final ServiceRequestClient serviceRequestClient;
    private final ExcelIngestionConfig config;
    private final FileStoreService fileStoreService;
    private final ObjectMapper objectMapper;
    private final LocalizationService localizationService;
    private final RequestInfoConverter requestInfoConverter;
    private final ApiPayloadBuilder apiPayloadBuilder;

    @Autowired
    public HierarchyExcelGenerateProcessor(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config,
            FileStoreService fileStoreService, ObjectMapper objectMapper, LocalizationService localizationService,
            RequestInfoConverter requestInfoConverter, ApiPayloadBuilder apiPayloadBuilder) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
        this.localizationService = localizationService;
        this.requestInfoConverter = requestInfoConverter;
        this.apiPayloadBuilder = apiPayloadBuilder;
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

        // Fetch localized messages
        String localizationModule = "hcm-boundary-" + hierarchyType.toLowerCase().replace(" ", "_");
        Map<String, String> localizationMap = localizationService.getLocalizedMessages(
                tenantId, localizationModule, locale, requestInfoConverter.convertToExcelIngestionRequestInfo(requestInfo));

        BoundaryHierarchyResponse hierarchyData = postApi(new StringBuilder(config.getHierarchySearchUrl()),
                apiPayloadBuilder.createHierarchyPayload(requestInfo, tenantId, hierarchyType),
                BoundaryHierarchyResponse.class);

        if (hierarchyData == null || hierarchyData.getBoundaryHierarchy() == null
                || hierarchyData.getBoundaryHierarchy().isEmpty()) {
            throw new CustomException(ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND,
                    ErrorConstants.BOUNDARY_HIERARCHY_NOT_FOUND_MESSAGE.replace("{0}", hierarchyType));
        }

        List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0)
                .getBoundaryHierarchy();

        StringBuilder url = new StringBuilder(config.getRelationshipSearchUrl());
        url.append("?includeChildren=true")
                .append("&tenantId=").append(URLEncoder.encode(tenantId, StandardCharsets.UTF_8))
                .append("&hierarchyType=").append(URLEncoder.encode(hierarchyType, StandardCharsets.UTF_8));

        BoundarySearchResponse relationshipData = postApi(url,
                apiPayloadBuilder.createRelationshipPayload(requestInfo),
                BoundarySearchResponse.class);

        List<String> originalLevels = new ArrayList<>();
        List<String> validLevels = new ArrayList<>();
        if (hierarchyRelations != null && !hierarchyRelations.isEmpty()) {
            for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
                String level = hierarchyRelation.getBoundaryType();
                originalLevels.add(level);
                validLevels.add(makeNameValid(level, localizationMap));
            }
        }

        Map<String, Set<String>> boundariesByLevel = new HashMap<>();
        validLevels.forEach(level -> boundariesByLevel.put(level, new HashSet<>()));
        Map<String, List<String>> childLookup = new HashMap<>();
        Map<String, String> nameMappings = new HashMap<>();

        // NEW map for localizedName -> validCombinedKey (named range)
        Map<String, String> localizedNameToCombinedKey = new HashMap<>();

        if (relationshipData != null && relationshipData.getTenantBoundary() != null) {
            for (HierarchyRelation hierarchyRelation : relationshipData.getTenantBoundary()) {
                processNodes(hierarchyRelation.getBoundary(), boundariesByLevel, childLookup, nameMappings,
                        localizationMap, localizedNameToCombinedKey);
            }
        }

        XSSFWorkbook workbook = new XSSFWorkbook();

        Sheet levelSheet = workbook.createSheet("LevelData");
        workbook.setSheetVisibility(workbook.getSheetIndex("LevelData"), SheetVisibility.VERY_HIDDEN);

        Sheet childrenSheet = workbook.createSheet("BoundaryChildren");
        workbook.setSheetVisibility(workbook.getSheetIndex("BoundaryChildren"), SheetVisibility.VERY_HIDDEN);

        Sheet mappingSheet = workbook.createSheet("NameMappings");
        workbook.setSheetVisibility(workbook.getSheetIndex("NameMappings"), SheetVisibility.VERY_HIDDEN);

        // Populate LevelData sheet and named ranges for levels
        for (int i = 0; i < validLevels.size(); i++) {
            String levelName = validLevels.get(i);
            Row headerRow = levelSheet.getRow(0) != null ? levelSheet.getRow(0) : levelSheet.createRow(0);
            headerRow.createCell(i).setCellValue(levelName);

            List<String> boundaries = new ArrayList<>(boundariesByLevel.get(levelName));
            for (int j = 0; j < boundaries.size(); j++) {
                Row row = levelSheet.getRow(j + 1) != null ? levelSheet.getRow(j + 1) : levelSheet.createRow(j + 1);
                row.createCell(i).setCellValue(boundaries.get(j));
            }
            if (!boundaries.isEmpty()) {
                Name namedRange = workbook.createName();
                namedRange.setNameName(levelName);
                String colLetter = CellReference.convertNumToColString(i);
                namedRange.setRefersToFormula(
                        "LevelData!$" + colLetter + "$2:$" + colLetter + "$" + (boundaries.size() + 1));
            }
        }

        // Populate BoundaryChildren sheet and create named ranges for each parent
        // localized name
        int childColumnIndex = 0;
        for (Map.Entry<String, List<String>> entry : childLookup.entrySet()) {
            String parentLocalizedName = entry.getKey(); // localized name (displayed in main sheet)
            List<String> childrenLocalizedNames = entry.getValue();

            Row headerRow = childrenSheet.getRow(0) != null ? childrenSheet.getRow(0) : childrenSheet.createRow(0);
            headerRow.createCell(childColumnIndex).setCellValue(parentLocalizedName);

            for (int i = 0; i < childrenLocalizedNames.size(); i++) {
                Row row = childrenSheet.getRow(i + 1) != null ? childrenSheet.getRow(i + 1)
                        : childrenSheet.createRow(i + 1);
                row.createCell(childColumnIndex).setCellValue(childrenLocalizedNames.get(i));
            }
            if (!childrenLocalizedNames.isEmpty()) {
                String namedRangeName = localizedNameToCombinedKey.get(parentLocalizedName);
                if (namedRangeName != null) {
                    Name namedRange = workbook.createName();
                    namedRange.setNameName(namedRangeName);
                    String colLetter = CellReference.convertNumToColString(childColumnIndex);
                    namedRange.setRefersToFormula(
                            "BoundaryChildren!$" + colLetter + "$2:$" + colLetter + "$"
                                    + (childrenLocalizedNames.size() + 1));
                }
            }
            childColumnIndex++;
        }

        // Populate NameMappings sheet with localizedName → namedRangeName mapping for
        // VLOOKUP
        Row mappingHeader = mappingSheet.createRow(0);
        mappingHeader.createCell(0).setCellValue("Localized Name");
        mappingHeader.createCell(1).setCellValue("Named Range");

        int mappingRowIndex = 1;
        for (Map.Entry<String, String> entry : localizedNameToCombinedKey.entrySet()) {
            Row row = mappingSheet.createRow(mappingRowIndex++);
            row.createCell(0).setCellValue(entry.getKey()); // Localized Name (dropdown display)
            row.createCell(1).setCellValue(entry.getValue()); // Named Range (Excel valid named range)
        }

        Sheet mainSheet = workbook.createSheet("Boundaries");

        Row keyRow = mainSheet.createRow(0);
        Row headerRow = mainSheet.createRow(1);

        for (int i = 0; i < originalLevels.size(); i++) {
            String unlocalizedCode = hierarchyType.toUpperCase() + "_" + originalLevels.get(i).toUpperCase();
            keyRow.createCell(i).setCellValue(unlocalizedCode);
            headerRow.createCell(i)
                    .setCellValue(localizationMap.getOrDefault(unlocalizedCode, unlocalizedCode));
        }
        keyRow.setZeroHeight(true);

        for (int col = 0; col < originalLevels.size(); col++) {
            mainSheet.setColumnWidth(col, 40 * 256);
        }
        mainSheet.createFreezePane(0, 2);

        DataValidationHelper dvHelper = mainSheet.getDataValidationHelper();

        for (int i = 2; i <= config.getExcelRowLimit() + 1; i++) { // Rows 3 to row limit + 2 (1-based indexing)
            // First level dropdown uses named range directly
            DataValidationConstraint firstLevelConstraint = dvHelper.createFormulaListConstraint(validLevels.get(0));
            CellRangeAddressList firstLevelAddress = new CellRangeAddressList(i, i, 0, 0);
            DataValidation firstLevelValidation = dvHelper.createValidation(firstLevelConstraint, firstLevelAddress);
            mainSheet.addValidationData(firstLevelValidation);

            // For subsequent columns, use INDIRECT(VLOOKUP()) to get child dropdown named
            // ranges
            for (int j = 1; j < validLevels.size(); j++) {
                String prevCol = CellReference.convertNumToColString(j - 1);
                String formula = "INDIRECT(IF(" +
                        prevCol + (i + 1) + "=\"\", \"" + validLevels.get(j) + "\", " +
                        "VLOOKUP(" + prevCol + (i + 1) + ", NameMappings!$A:$B, 2, FALSE)))";

                DataValidationConstraint dvConstraint = dvHelper.createFormulaListConstraint(formula);
                CellRangeAddressList addr = new CellRangeAddressList(i, i, j, j);
                DataValidation validation = dvHelper.createValidation(dvConstraint, addr);

                // To avoid Excel warnings on newer Excel versions, set explicit properties
                if (validation instanceof org.apache.poi.ss.usermodel.DataValidation) {
                    validation.setSuppressDropDownArrow(true);
                    validation.setShowErrorBox(true);
                }

                mainSheet.addValidationData(validation);
            }
        }

        // Conditional Formatting to highlight invalid dropdown selections
        SheetConditionalFormatting sheetCF = mainSheet.getSheetConditionalFormatting();
        for (int j = 1; j < validLevels.size(); j++) {
            String col = CellReference.convertNumToColString(j);
            String prevCol = CellReference.convertNumToColString(j - 1);
            String levelName = validLevels.get(j);
            String formula = "AND(" + col + "3<>\"\", " +
                    "ISERROR(MATCH(" + col + "3, INDIRECT(IF(" + prevCol + "3=\"\", \"" + levelName + "\", " +
                    "VLOOKUP(" + prevCol + "3, NameMappings!$A:$B, 2, FALSE))), 0)))";

            ConditionalFormattingRule rule = sheetCF.createConditionalFormattingRule(formula);
            PatternFormatting fill = rule.createPatternFormatting();
            fill.setFillBackgroundColor(IndexedColors.RED.getIndex());
            fill.setFillPattern(FillPatternType.SOLID_FOREGROUND.getCode());

            CellRangeAddress[] regions = { new CellRangeAddress(2, config.getExcelRowLimit() + 1, j, j) };
            sheetCF.addConditionalFormatting(regions, rule);
        }

        // Unlock data entry cells
        CellStyle unlockedCellStyle = workbook.createCellStyle();
        unlockedCellStyle.setLocked(false);

        for (int i = 2; i <= config.getExcelRowLimit() + 1; i++) {
            Row row = mainSheet.getRow(i);
            if (row == null) {
                row = mainSheet.createRow(i);
            }
            for (int j = 0; j < originalLevels.size(); j++) {
                Cell cell = row.getCell(j);
                if (cell == null) {
                    cell = row.createCell(j);
                }
                cell.setCellStyle(unlockedCellStyle);
            }
        }

        mainSheet.protectSheet(config.getExcelSheetPassword());

        // Set sheet selection and workbook protection
        for (Sheet s : workbook) {
            s.setSelected(false);
        }
        mainSheet.setSelected(true);
        workbook.setActiveSheet(workbook.getSheetIndex("Boundaries"));
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

    private <T> T postApi(StringBuilder url, Object request, Class<T> type) {
        try {
            log.info("Calling API: {} with payload: {}", url, request);
            return serviceRequestClient.fetchResult(url, request, type);
        } catch (Exception e) {
            log.error("Error calling API: {}", url, e);
            throw new CustomException(ErrorConstants.NETWORK_ERROR,
                    ErrorConstants.NETWORK_ERROR_MESSAGE.replace("{0}", url.toString()));
        }
    }


    private void processNodes(List<EnrichedBoundary> nodes, Map<String, Set<String>> boundariesByLevel,
            Map<String, List<String>> childLookup,
            Map<String, String> nameMappings,
            Map<String, String> localizationMap,
            Map<String, String> localizedNameToCombinedKey) {
        if (nodes == null)
            return;
        for (EnrichedBoundary node : nodes) {
            String code = node.getCode();
            String boundaryType = node.getBoundaryType();

            String localizedCode = localizationMap.getOrDefault(code, code);
            String localizedBoundaryType = localizationMap.getOrDefault(boundaryType, boundaryType);

            String combinedKey = code + "_" + localizedCode;
            String validCombinedKey = makeNameValid(combinedKey, localizationMap);
            String validLocalizedBoundaryType = makeNameValid(localizedBoundaryType, localizationMap);

            if (boundariesByLevel.containsKey(validLocalizedBoundaryType)) {
                boundariesByLevel.get(validLocalizedBoundaryType).add(localizedCode);
            }

            nameMappings.put(combinedKey, validCombinedKey);
            localizedNameToCombinedKey.put(localizedCode, validCombinedKey);

            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                List<EnrichedBoundary> children = node.getChildren();
                List<String> childLocalizedNames = new ArrayList<>();
                for (EnrichedBoundary child : children) {
                    childLocalizedNames.add(localizationMap.getOrDefault(child.getCode(), child.getCode()));
                }
                childLookup.put(localizedCode, childLocalizedNames);
                processNodes(children, boundariesByLevel, childLookup, nameMappings, localizationMap,
                        localizedNameToCombinedKey);
            }
        }
    }

    private String makeNameValid(String name, Map<String, String> localizationMap) {
        String localizedName = localizationMap.getOrDefault(name, name);
        String valid = localizedName.replaceAll("[^a-zA-Z0-9_.]", "_");
        if (Character.isDigit(valid.charAt(0)))
            valid = "_" + valid;
        return valid;
    }


    @Override
    public String getType() {
        return "hierarchyExcel";
    }
}
