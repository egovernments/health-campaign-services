package org.egov.excelingestion.web.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.service.FileStoreService;
import org.egov.excelingestion.web.models.BoundarySearchResponse;
import org.egov.excelingestion.web.models.BoundaryHierarchyChild;
import org.egov.excelingestion.web.models.BoundaryHierarchyResponse;
import org.egov.excelingestion.web.models.EnrichedBoundary;
import org.egov.excelingestion.web.models.GeneratedResource;
import org.egov.excelingestion.web.models.GeneratedResourceRequest;
import org.egov.excelingestion.web.models.HierarchyRelation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("hierarchyExcelGenerateProcessor")
@Slf4j
public class HierarchyExcelGenerateProcessor implements IGenerateProcessor {

    private final ServiceRequestClient serviceRequestClient;
    private final ExcelIngestionConfig config;
    private final FileStoreService fileStoreService;
    private final ObjectMapper objectMapper;

    @Autowired
    public HierarchyExcelGenerateProcessor(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config, FileStoreService fileStoreService, ObjectMapper objectMapper) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.fileStoreService = fileStoreService;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedResource process(GeneratedResourceRequest request) {
        log.info("Processing hierarchy excel generation for type: {}", request.getGeneratedResource().getType());
        try {
            generateExcel(request.getGeneratedResource(), request.getRequestInfo());
        } catch (IOException e) {
            log.error("Error generating Excel file: {}", e.getMessage());
            throw new RuntimeException("Error generating Excel file", e);
        }
        return request.getGeneratedResource();
    }

    public byte[] generateExcel(GeneratedResource generatedResource, RequestInfo requestInfo) throws IOException {
        log.info("Starting Excel generation process for hierarchyType: {}", generatedResource.getHierarchyType());

        BoundaryHierarchyResponse hierarchyData = (BoundaryHierarchyResponse) postApi(new StringBuilder(config.getHierarchySearchUrl()),
                createHierarchyPayload(requestInfo, generatedResource.getTenantId(), generatedResource.getHierarchyType()), 
                BoundaryHierarchyResponse.class);
        log.debug("Hierarchy Data: {}", hierarchyData);
        StringBuilder url = new StringBuilder(config.getRelationshipSearchUrl());
        url.append("?includeChildren=true")
        .append("&tenantId=").append(URLEncoder.encode(generatedResource.getTenantId(), StandardCharsets.UTF_8))
        .append("&hierarchyType=").append(URLEncoder.encode(generatedResource.getHierarchyType(), StandardCharsets.UTF_8));


        BoundarySearchResponse relationshipData = (BoundarySearchResponse) postApi(url, 
                createRelationshipPayload(requestInfo, generatedResource.getTenantId(), generatedResource.getHierarchyType()),
                BoundarySearchResponse.class);
        log.debug("Relationship Data: {}", relationshipData);

        List<String> originalLevels = new ArrayList<>();
        List<String> validLevels = new ArrayList<>();

        List<BoundaryHierarchyChild> hierarchyRelations = null;
        if (hierarchyData != null && hierarchyData.getBoundaryHierarchy() != null && !hierarchyData.getBoundaryHierarchy().isEmpty()) {
            hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();
        } else {
            throw new RuntimeException("Boundary Hierarchy Search API returned no data.");
        }

        if (hierarchyRelations != null && !hierarchyRelations.isEmpty()) {
            for (BoundaryHierarchyChild hierarchyRelation : hierarchyRelations) {
                String level = hierarchyRelation.getBoundaryType();
                originalLevels.add(level);
                validLevels.add(makeNameValid(level));
            }
        }

        Map<String, Set<String>> boundariesByLevel = new HashMap<>();
        validLevels.forEach(level -> boundariesByLevel.put(level, new HashSet<>()));
        Map<String, List<String>> childLookup = new HashMap<>();
        Map<String, String> nameMappings = new HashMap<>();

        if (relationshipData != null && relationshipData.getTenantBoundary() != null) {
            for (HierarchyRelation hierarchyRelation : relationshipData.getTenantBoundary()) {
                processNodes(hierarchyRelation.getBoundary(), boundariesByLevel, childLookup, nameMappings);
            }
        }

        XSSFWorkbook workbook = new XSSFWorkbook();

        Sheet levelSheet = workbook.createSheet("LevelData");
        workbook.setSheetVisibility(workbook.getSheetIndex("LevelData"), SheetVisibility.VERY_HIDDEN);

        Sheet childrenSheet = workbook.createSheet("BoundaryChildren");
        workbook.setSheetVisibility(workbook.getSheetIndex("BoundaryChildren"), SheetVisibility.VERY_HIDDEN);

        Sheet mappingSheet = workbook.createSheet("NameMappings");
        workbook.setSheetVisibility(workbook.getSheetIndex("NameMappings"), SheetVisibility.VERY_HIDDEN);

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

        int childColumnIndex = 0;
        for (Map.Entry<String, List<String>> entry : childLookup.entrySet()) {
            String parentCode = entry.getKey();
            List<String> children = entry.getValue();

            Row headerRow = childrenSheet.getRow(0) != null ? childrenSheet.getRow(0) : childrenSheet.createRow(0);
            headerRow.createCell(childColumnIndex).setCellValue(parentCode);

            for (int i = 0; i < children.size(); i++) {
                Row row = childrenSheet.getRow(i + 1) != null ? childrenSheet.getRow(i + 1)
                        : childrenSheet.createRow(i + 1);
                row.createCell(childColumnIndex).setCellValue(children.get(i));
            }
            if (!children.isEmpty()) {
                Name namedRange = workbook.createName();
                namedRange.setNameName(parentCode);
                String colLetter = CellReference.convertNumToColString(childColumnIndex);
                namedRange.setRefersToFormula(
                        "BoundaryChildren!$" + colLetter + "$2:$" + colLetter + "$" + (children.size() + 1));
            }
            childColumnIndex++;
        }

        Row mappingHeader = mappingSheet.createRow(0);
        mappingHeader.createCell(0).setCellValue("OriginalName");
        mappingHeader.createCell(1).setCellValue("ValidName");

        int mappingRowIndex = 1;
        for (Map.Entry<String, String> entry : nameMappings.entrySet()) {
            Row row = mappingSheet.createRow(mappingRowIndex++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }

        Sheet mainSheet = workbook.createSheet("Boundaries");

        Row keyRow = mainSheet.createRow(0);
        Row headerRow = mainSheet.createRow(1);
        String hierarchyType = generatedResource.getHierarchyType();

        for (int i = 0; i < originalLevels.size(); i++) {
            String unlocalizedCode = hierarchyType.toUpperCase() + "_" + originalLevels.get(i).toUpperCase();
            keyRow.createCell(i).setCellValue(unlocalizedCode);
            headerRow.createCell(i).setCellValue(originalLevels.get(i));
        }
        keyRow.setZeroHeight(true);
        for (int col = 0; col < originalLevels.size(); col++) {
            mainSheet.setColumnWidth(col, 40 * 256); // 256 is unit size in POI
        }

        mainSheet.createFreezePane(0, 2);


        DataValidationHelper dvHelper = mainSheet.getDataValidationHelper();

        for (int i = 2; i <= 5001; i++) { // Start from row 3 (index 2)
            DataValidationConstraint firstLevelConstraint = dvHelper.createFormulaListConstraint(validLevels.get(0));
            CellRangeAddressList firstLevelAddress = new CellRangeAddressList(i, i, 0, 0);
            DataValidation firstLevelValidation = dvHelper.createValidation(firstLevelConstraint, firstLevelAddress);
            mainSheet.addValidationData(firstLevelValidation);

            for (int j = 1; j < validLevels.size(); j++) {
                String prevCol = CellReference.convertNumToColString(j - 1);
                String formula = "INDIRECT(IF(" 
                    + prevCol + (i + 1) + "=\"\", \"" 
                    + validLevels.get(j) 
                    + "\", VLOOKUP(" + prevCol + (i + 1) 
                    + ", NameMappings!$A:$B, 2, FALSE)))";


                DataValidationConstraint dvConstraint = dvHelper.createFormulaListConstraint(formula);
                CellRangeAddressList addr = new CellRangeAddressList(i, i, j, j);
                DataValidation validation = dvHelper.createValidation(dvConstraint, addr);
                mainSheet.addValidationData(validation);
            }
        }

        SheetConditionalFormatting sheetCF = mainSheet.getSheetConditionalFormatting();
        for (int j = 1; j < validLevels.size(); j++) {
            String col = CellReference.convertNumToColString(j);
            String prevCol = CellReference.convertNumToColString(j - 1);
            String levelName = validLevels.get(j);
            String formula =
                "AND(" + col + "3<>\"\", " + // Check from row 3
                "ISERROR(MATCH(" + col + "3, INDIRECT(IF(" + prevCol + "3=\"\", \"" + levelName +
                "\", VLOOKUP(" + prevCol + "3, NameMappings!$A:$B, 2, FALSE))), 0)))";



            ConditionalFormattingRule rule = sheetCF.createConditionalFormattingRule(formula);
            PatternFormatting fill = rule.createPatternFormatting();
            fill.setFillBackgroundColor(IndexedColors.RED.getIndex());
            fill.setFillPattern(FillPatternType.SOLID_FOREGROUND.getCode());

            CellRangeAddress[] regions = { new CellRangeAddress(2, 5001, j, j) }; // Apply from row 3
            sheetCF.addConditionalFormatting(regions, rule);
        }
        // Unlock data entry cells
        CellStyle unlockedCellStyle = workbook.createCellStyle();
        unlockedCellStyle.setLocked(false);

        for (int i = 2; i <= 5001; i++) {
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

        mainSheet.protectSheet("passwordhere");

        for (Sheet s : workbook) {
            s.setSelected(false);
        }
        mainSheet.setSelected(true);
        workbook.setActiveSheet(workbook.getSheetIndex("Boundaries"));
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

    private <T> T postApi(StringBuilder url, Object request, Class<T> type) {
        try {
            log.info("Calling API: {} with payload: {}", url, request);
            return serviceRequestClient.fetchResult(url, request, type);
        } catch (Exception e) {
            log.error("Error calling API: {}", url, e);
            throw new RuntimeException("Error calling API: " + url, e);
        }
    }

    private Map<String, Object> createHierarchyPayload(RequestInfo requestInfo, String tenantId, String hierarchyType) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> requestInfoMap = new HashMap<>();
        requestInfoMap.put("apiId", requestInfo.getApiId());
        requestInfoMap.put("msgId", requestInfo.getMsgId());
        requestInfoMap.put("authToken", requestInfo.getAuthToken());
        requestInfoMap.put("userInfo", requestInfo.getUserInfo());
        payload.put("RequestInfo", requestInfoMap);

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("tenantId", tenantId);
        criteria.put("limit", 5);
        criteria.put("offset", 0);
        criteria.put("hierarchyType", hierarchyType);
        payload.put("BoundaryTypeHierarchySearchCriteria", criteria);
        return payload;
    }

    private Map<String, Object> createRelationshipPayload(RequestInfo requestInfo, String tenantId, String hierarchyType) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> requestInfoMap = new HashMap<>();
        requestInfoMap.put("apiId", requestInfo.getApiId());
        requestInfoMap.put("msgId", requestInfo.getMsgId());
        requestInfoMap.put("authToken", requestInfo.getAuthToken());
        requestInfoMap.put("userInfo", requestInfo.getUserInfo());
        payload.put("RequestInfo", requestInfoMap);
        return payload;
    }

    private void processNodes(List<EnrichedBoundary> nodes, Map<String, Set<String>> boundariesByLevel,
            Map<String, List<String>> childLookup, Map<String, String> nameMappings) {
        if (nodes == null) return;
        for (EnrichedBoundary node : nodes) {
            String code = node.getCode();
            String boundaryType = node.getBoundaryType();

            String validCode = makeNameValid(code);
            String validType = makeNameValid(boundaryType);

            if (boundariesByLevel.containsKey(validType)) {
                boundariesByLevel.get(validType).add(code);
            }
            nameMappings.put(code, validCode);

            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                List<EnrichedBoundary> children = node.getChildren();
                List<String> childCodes = new ArrayList<>();
                for (EnrichedBoundary child : children) {
                    childCodes.add(child.getCode());
                }
                childLookup.put(validCode, childCodes);
                processNodes(children, boundariesByLevel, childLookup, nameMappings);
            }
        }
    }

    private String makeNameValid(String name) {
        String valid = name.replaceAll("[^a-zA-Z0-9_.]", "_");
        if (Character.isDigit(valid.charAt(0)))
            valid = "_" + valid;
        return valid;
    }

    @Override
    public String getType() {
        return "hierarchyExcel";
    }
}