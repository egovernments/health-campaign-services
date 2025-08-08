package org.egov.excelingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.web.models.GeneratedResource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class ExcelGenerationService {

    private final ServiceRequestClient serviceRequestClient;
    private final ExcelIngestionConfig config;
    private final ObjectMapper objectMapper;
    private final FileStoreService fileStoreService;

    @Autowired
    public ExcelGenerationService(ServiceRequestClient serviceRequestClient, ExcelIngestionConfig config, ObjectMapper objectMapper,
            FileStoreService fileStoreService) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.objectMapper = objectMapper;
        this.fileStoreService = fileStoreService;
    }

    public String generateExcelAndUpload(GeneratedResource generatedResource, RequestInfo requestInfo) throws IOException {
        log.info("Starting Excel generation process for hierarchyType: {}", generatedResource.getHierarchyType());

        Map<String, Object> hierarchyData = postApi(new StringBuilder(config.getHierarchySearchUrl()),
                createHierarchyPayload(requestInfo, generatedResource.getTenantId(), generatedResource.getHierarchyType()));
        log.debug("Hierarchy Data: {}", hierarchyData);
        StringBuilder url = new StringBuilder(config.getRelationshipSearchUrl());
        url.append("?includeChildren=true")
        .append("&tenantId=").append(URLEncoder.encode(generatedResource.getTenantId(), StandardCharsets.UTF_8))
        .append("&hierarchyType=").append(URLEncoder.encode(generatedResource.getHierarchyType(), StandardCharsets.UTF_8));


        Map<String, Object> relationshipData = postApi(url,
                createRelationshipPayload(requestInfo, generatedResource.getTenantId(), generatedResource.getHierarchyType()));
        log.debug("Relationship Data: {}", relationshipData);

        List<String> originalLevels = new ArrayList<>();
        List<String> validLevels = new ArrayList<>();

        if (hierarchyData != null) {
            Map<String, Object> boundaryHierarchyMap = (Map<String, Object>) ((List<Map<String, Object>>) hierarchyData.get("BoundaryHierarchy")).get(0);
            List<Map<String, Object>> hierarchyArray = (List<Map<String, Object>>) boundaryHierarchyMap.get("boundaryHierarchy");

            for (Map<String, Object> item : hierarchyArray) {
                String level = (String) item.get("boundaryType");
                originalLevels.add(level);
                validLevels.add(makeNameValid(level));
            }
        }

        Map<String, Set<String>> boundariesByLevel = new HashMap<>();
        validLevels.forEach(level -> boundariesByLevel.put(level, new HashSet<>()));
        Map<String, List<String>> childLookup = new HashMap<>();
        Map<String, String> nameMappings = new HashMap<>();

        if (relationshipData != null) {
            List<Map<String, Object>> tenantBoundaryList = (List<Map<String, Object>>) relationshipData.get("TenantBoundary");
            if (tenantBoundaryList != null && !tenantBoundaryList.isEmpty()) {
                Map<String, Object> tenantBoundaryMap = tenantBoundaryList.get(0);
                List<Map<String, Object>> boundaryList = (List<Map<String, Object>>) tenantBoundaryMap.get("boundary");
                processNodes(boundaryList, boundariesByLevel, childLookup, nameMappings);
            }
        }

        Workbook workbook = new XSSFWorkbook();

        Sheet levelSheet = workbook.createSheet("LevelData");
        Sheet childrenSheet = workbook.createSheet("BoundaryChildren");
        Sheet mappingSheet = workbook.createSheet("NameMappings");

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
        Row mainHeader = mainSheet.createRow(0);
        for (int i = 0; i < originalLevels.size(); i++) {
            mainHeader.createCell(i).setCellValue(originalLevels.get(i));
        }

        DataValidationHelper dvHelper = mainSheet.getDataValidationHelper();

        for (int i = 1; i <= 5000; i++) {
            DataValidationConstraint firstLevelConstraint = dvHelper.createFormulaListConstraint(validLevels.get(0));
            CellRangeAddressList firstLevelAddress = new CellRangeAddressList(i, i, 0, 0);
            DataValidation firstLevelValidation = dvHelper.createValidation(firstLevelConstraint, firstLevelAddress);
            mainSheet.addValidationData(firstLevelValidation);

            for (int j = 1; j < validLevels.size(); j++) {
                String prevCol = CellReference.convertNumToColString(j - 1);
                String currCol = CellReference.convertNumToColString(j);
                String formula = "INDIRECT(IF(" + prevCol + (i + 1) + "=\"\", \"" +
                        validLevels.get(j) + "\", VLOOKUP(" + prevCol + (i + 1) +
                        ", NameMappings!$A:$B, 2, FALSE)))";
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

            String formula = "AND(" + col + "2<>\"\", " +
                    "ISERROR(MATCH(" + col + "2, INDIRECT(IF(" + prevCol + "2=\"\", \"" +
                    levelName + "\", VLOOKUP(" + prevCol + "2, NameMappings!$A:$B, 2, FALSE))), 0)))";

            ConditionalFormattingRule rule = sheetCF.createConditionalFormattingRule(formula);
            PatternFormatting fill = rule.createPatternFormatting();
            fill.setFillBackgroundColor(IndexedColors.RED.getIndex());
            fill.setFillPattern(FillPatternType.SOLID_FOREGROUND.getCode());

            CellRangeAddress[] regions = { new CellRangeAddress(1, 5000, j, j) };
            sheetCF.addConditionalFormatting(regions, rule);
        }

        workbook.setSheetVisibility(workbook.getSheetIndex("LevelData"), SheetVisibility.VERY_HIDDEN);
        workbook.setSheetVisibility(workbook.getSheetIndex("BoundaryChildren"), SheetVisibility.VERY_HIDDEN);
        workbook.setSheetVisibility(workbook.getSheetIndex("NameMappings"), SheetVisibility.VERY_HIDDEN);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            workbook.write(bos);
        } finally {
            workbook.close();
        }

        log.info("Excel file generated successfully!");
        return fileStoreService.uploadFile(bos.toByteArray(), generatedResource.getTenantId(),
                generatedResource.getHierarchyType() + ".xlsx");
    }

    private Map<String, Object> postApi(StringBuilder url, Object request) {
        try {
            log.info("Calling API: {} with payload: {}", url, request);
            return (Map<String, Object>) serviceRequestClient.fetchResult(url, request, Map.class);
        } catch (Exception e) {
            log.error("Error calling API: {}", url, e);
            return null;
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

    private void processNodes(List<Map<String, Object>> nodes, Map<String, Set<String>> boundariesByLevel,
            Map<String, List<String>> childLookup, Map<String, String> nameMappings) {
        if (nodes == null) return;
        for (Map<String, Object> node : nodes) {
            String code = (String) node.get("code");
            String boundaryType = (String) node.get("boundaryType");

            String validCode = makeNameValid(code);
            String validType = makeNameValid(boundaryType);

            if (boundariesByLevel.containsKey(validType)) {
                boundariesByLevel.get(validType).add(code);
            }
            nameMappings.put(code, validCode);

            if (node.containsKey("children")) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
                List<String> childCodes = new ArrayList<>();
                for (Map<String, Object> child : children) {
                    childCodes.add((String) child.get("code"));
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
}
