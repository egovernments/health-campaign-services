package org.egov.excelingestion.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.CellUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.FacilityService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.PostTypeMasterService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.CellProtectionManager;
import org.egov.excelingestion.util.EnumLocalizationUtil;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generator for the posts sheet (CLF / transit posts). Built like the facility sheet: schema columns from
 * MDMS, cascading boundary columns, then pre-filled rows via {@link ExcelDataPopulator}. Pre-filled rows are
 * this campaign's own stored posts ({@link FacilitySheetGenerator#fetchExistingCampaignData}), followed by
 * permanent posts carried over from earlier campaigns on this campaign's lowest/second-lowest boundaries
 * (as Exclude). Every row past the pre-filled ones gets an inline Post Status default formula.
 */
@Component
@Slf4j
public class PostSheetGenerator implements ISheetGenerator {

    // Carried-over posts may sit on the lowest OR second-lowest boundary level (matches validation).
    private static final int CARRY_OVER_LEVEL_COUNT = 2;

    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final FacilityService facilityService;
    private final BoundaryUtil boundaryUtil;
    private final CellProtectionManager cellProtectionManager;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ExcelDataPopulator excelDataPopulator;
    private final HierarchicalBoundaryUtil hierarchicalBoundaryUtil;
    private final PostTypeMasterService postTypeMasterService;
    private final ExcelIngestionConfig config;

    public PostSheetGenerator(MDMSService mdmsService, CampaignService campaignService,
                              FacilityService facilityService, BoundaryUtil boundaryUtil,
                              CellProtectionManager cellProtectionManager, CustomExceptionHandler exceptionHandler,
                              SchemaColumnDefUtil schemaColumnDefUtil, ExcelDataPopulator excelDataPopulator,
                              HierarchicalBoundaryUtil hierarchicalBoundaryUtil,
                              PostTypeMasterService postTypeMasterService, ExcelIngestionConfig config) {
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.facilityService = facilityService;
        this.boundaryUtil = boundaryUtil;
        this.cellProtectionManager = cellProtectionManager;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.excelDataPopulator = excelDataPopulator;
        this.hierarchicalBoundaryUtil = hierarchicalBoundaryUtil;
        this.postTypeMasterService = postTypeMasterService;
        this.config = config;
    }

    @Override
    public XSSFWorkbook generateSheet(XSSFWorkbook workbook, String sheetName, SheetGenerationConfig config,
                                      GenerateResource generateResource, RequestInfo requestInfo,
                                      Map<String, String> localizationMap) {
        log.info("Generating post sheet: {} for schema: {}", sheetName, config.getSchemaName());
        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", config.getSchemaName());
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, generateResource.getTenantId(), ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            String schemaJson = FacilitySheetGenerator.extractSchemaFromMDMSResponse(
                    mdmsList, config.getSchemaName(), exceptionHandler);
            if (schemaJson == null || schemaJson.isEmpty()) {
                return workbook;
            }

            List<ColumnDef> columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
            // Delivery-method gate (ExcelGenerationService) already keeps this sheet from generating at
            // all when the master has no active codes.
            Map<String, Boolean> postTypes = postTypeMasterService.getPostTypes(requestInfo, generateResource.getTenantId());
            List<Map<String, Object>> data = fetchExistingPostData(generateResource, requestInfo, postTypes);

            if (workbook.getSheetIndex(sheetName) >= 0) {
                workbook.removeSheetAt(workbook.getSheetIndex(sheetName));
            }
            workbook.createSheet(sheetName);

            if (FacilitySheetGenerator.shouldAddBoundaryDropdowns(generateResource)) {
                List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = campaignService.getBoundariesFromCampaign(
                        generateResource.getReferenceId(), generateResource.getTenantId(), requestInfo);
                if (campaignBoundaries != null && !campaignBoundaries.isEmpty()) {
                    List<Boundary> enrichedBoundaries = boundaryUtil.getEnrichedBoundariesFromCampaign(
                            generateResource.getId(), generateResource.getReferenceId(),
                            generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);
                    hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(
                            workbook, sheetName, localizationMap, enrichedBoundaries,
                            generateResource.getHierarchyType(), generateResource.getTenantId(), requestInfo, data);
                }
            }

            // Posts List is a free-entry console sheet, same as the Facilities List: unprotected in join
            // mode, pre-filled rows stamped with hidden row-ids for the server-side immutable join.
            workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, sheetName, columns, data,
                    localizationMap, generateResource.isUnprotectedJoinMode(),
                    ProcessingConstants.isUserEntrySheet(ProcessingConstants.POST_SHEET_GENERATOR_CLASS));
            cellProtectionManager.applyCellProtection(workbook, workbook.getSheet(sheetName), columns);

            // Default Post Status (per post type, from the master) on every row the PM will add
            applyPostStatusDefaultFormula(workbook.getSheet(sheetName), columns, postTypes,
                    localizationMap, data != null ? data.size() : 0);
            return workbook;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating post sheet {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate post sheet: " + sheetName, e);
        }
    }

    /** Pre-filled rows: this campaign's own posts, then permanent posts carried over from earlier campaigns. */
    private List<Map<String, Object>> fetchExistingPostData(GenerateResource generateResource,
                                                            RequestInfo requestInfo, Map<String, Boolean> postTypes) {
        String referenceId = generateResource.getReferenceId();
        if (isBlank(referenceId)) {
            log.info("No reference ID provided for post sheet");
            return null; // Headers-only sheet
        }
        try {
            CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(
                    referenceId, generateResource.getTenantId(), requestInfo);
            String campaignNumber = campaign != null ? campaign.getCampaignNumber() : null;
            if (isBlank(campaignNumber)) {
                log.info("No campaign found for reference ID: {}", referenceId);
                return null; // Headers-only sheet
            }

            // Stored rows already carry every post column, the hidden clientReferenceId join key and the
            // refreshed Post Usage (project-factory spreads the sheet row back).
            List<Map<String, Object>> campaignPosts = FacilitySheetGenerator.fetchExistingCampaignData(
                    campaignService, campaignNumber, generateResource.getTenantId(), requestInfo, true);

            List<Map<String, Object>> carryOverPosts = new ArrayList<>();
            if (!isBlank(generateResource.getHierarchyType())) {
                Set<String> carryOverCodes = boundaryUtil.getLowestLevelBoundaryCodesFromCampaign(
                        generateResource.getId(), referenceId, generateResource.getTenantId(),
                        generateResource.getHierarchyType(), CARRY_OVER_LEVEL_COUNT, requestInfo);
                carryOverPosts = fetchCarryOverPosts(generateResource.getTenantId(), carryOverCodes,
                        campaignPosts, postTypes, requestInfo);
            }

            List<Map<String, Object>> rows = new ArrayList<>(campaignPosts.size() + carryOverPosts.size());
            rows.addAll(campaignPosts);
            rows.addAll(carryOverPosts);
            log.info("Final post data count: {} (campaign: {}, carried over: {})",
                    rows.size(), campaignPosts.size(), carryOverPosts.size());
            return rows;

        } catch (Exception e) {
            log.error("Error fetching post data: {}", e.getMessage(), e);
            return null; // Headers-only sheet on error, as on the facility sheet
        }
    }

    /** Permanent posts of earlier campaigns on carryOverCodes, not already listed (by clientReferenceId), as Exclude. */
    private List<Map<String, Object>> fetchCarryOverPosts(String tenantId, Set<String> carryOverCodes,
                                                          List<Map<String, Object>> campaignPosts,
                                                          Map<String, Boolean> postTypes, RequestInfo requestInfo) {
        List<Map<String, Object>> posts = new ArrayList<>();
        if (carryOverCodes == null || carryOverCodes.isEmpty()) {
            return posts;
        }

        Set<String> listedIds = campaignPosts.stream()
                .map(post -> asTrimmedString(post.get(ProcessingConstants.POST_CLIENT_REFERENCE_ID_COLUMN_KEY)))
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        for (String postType : postTypes.keySet()) {
            for (Map<String, Object> facility : facilityService.fetchAllPermanentFacilities(tenantId, postType, requestInfo)) {
                String id = asTrimmedString(facility.get("clientReferenceId"));
                String locality = FacilitySheetGenerator.getFacilityBoundaryCode(facility);
                if (id == null || locality == null || !carryOverCodes.contains(locality) || listedIds.contains(id)) {
                    continue;
                }
                listedIds.add(id);
                posts.add(toPostRowFromFacility(facility, locality));
            }
        }
        log.info("Found {} posts carried over from earlier campaigns", posts.size());
        return posts;
    }

    /**
     * Writes an inline default formula into the Post Status cell of every row past the pre-filled ones:
     * {@code IF(t="","",IF(OR(t="<label>",t="<CODE>",...),"<Permanent>","<Temporary>"))}. The OR lists both
     * the localized label AND the code of every active type with isPermanentByDefault=true, because
     * EnumValueNormalizer may rewrite the Post Type cell to its code before or after this formula is read
     * (HashMap column order) - a label-only (or code-only) formula would go blank on whichever order runs.
     */
    private void applyPostStatusDefaultFormula(Sheet sheet, List<ColumnDef> columns, Map<String, Boolean> postTypes,
                                               Map<String, String> localizationMap, int dataRowCount) {
        if (postTypes == null || postTypes.isEmpty() || sheet == null) {
            return;
        }
        int postTypeColIdx = technicalColumnIndex(sheet, ProcessingConstants.POST_TYPE_COLUMN_KEY);
        int postStatusColIdx = technicalColumnIndex(sheet, ProcessingConstants.POST_STATUS_COLUMN_KEY);
        if (postTypeColIdx < 0 || postStatusColIdx < 0) {
            log.warn("Post Type/Post Status column not found on sheet '{}'; skipping default formula", sheet.getSheetName());
            return;
        }

        String formulaTemplate = buildPostStatusDefaultFormula(columns, postTypes, localizationMap);
        String postTypeColLetter = CellReference.convertNumToColString(postTypeColIdx);
        int firstEmptyRow = 2 + dataRowCount; // rows 0/1 are the header rows; pre-filled rows come first
        int lastRow = Math.max(ExcelUtil.findActualLastRowWithData(sheet), 2 + config.getExcelRowLimit() - 1);
        for (int rowIdx = firstEmptyRow; rowIdx <= lastRow; rowIdx++) {
            String typeCellRef = "$" + postTypeColLetter + "$" + (rowIdx + 1);
            CellUtil.getCell(CellUtil.getRow(rowIdx, sheet), postStatusColIdx)
                    .setCellFormula(formulaTemplate.replace("{t}", typeCellRef));
        }
        log.info("Applied Post Status default formula to sheet '{}' rows {}-{}",
                sheet.getSheetName(), firstEmptyRow + 1, lastRow + 1);
    }

    /** Formula template (token {@code {t}} stands in for the row's Post Type cell reference). */
    private static String buildPostStatusDefaultFormula(List<ColumnDef> columns, Map<String, Boolean> postTypes,
                                                        Map<String, String> localizationMap) {
        List<String> permanentCodes = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : postTypes.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                permanentCodes.add(entry.getKey());
            }
        }

        List<String> statusLabels = EnumLocalizationUtil.toLocalizedValues(ProcessingConstants.POST_STATUS_COLUMN_KEY,
                List.of(ProcessingConstants.STATUS_PERMANENT, ProcessingConstants.STATUS_TEMPORARY),
                localizationMap, columnPrefix(columns, ProcessingConstants.POST_STATUS_COLUMN_KEY));
        String permanentLabel = escapeFormulaLiteral(statusLabels.get(0));
        String temporaryLabel = escapeFormulaLiteral(statusLabels.get(1));
        if (permanentCodes.isEmpty()) {
            return "IF({t}=\"\",\"\",\"" + temporaryLabel + "\")";
        }

        List<String> permanentLabels = EnumLocalizationUtil.toLocalizedValues(ProcessingConstants.POST_TYPE_COLUMN_KEY,
                permanentCodes, localizationMap, columnPrefix(columns, ProcessingConstants.POST_TYPE_COLUMN_KEY));
        StringBuilder or = new StringBuilder();
        for (int i = 0; i < permanentCodes.size(); i++) {
            if (or.length() > 0) {
                or.append(",");
            }
            or.append("{t}=\"").append(escapeFormulaLiteral(permanentLabels.get(i))).append("\",")
              .append("{t}=\"").append(escapeFormulaLiteral(permanentCodes.get(i))).append("\"");
        }
        return "IF({t}=\"\",\"\",IF(OR(" + or + "),\"" + permanentLabel + "\",\"" + temporaryLabel + "\"))";
    }

    /** Doubles an embedded double quote, the Excel-formula-literal escape. */
    private static String escapeFormulaLiteral(String value) {
        return value == null ? "" : value.replace("\"", "\"\"");
    }

    /** The MDMS schema's declared localization key prefix for a technical column, or null if not found. */
    private static String columnPrefix(List<ColumnDef> columns, String technicalName) {
        if (columns == null) {
            return null;
        }
        for (ColumnDef column : columns) {
            if (technicalName.equals(column.getName())) {
                return column.getPrefix();
            }
        }
        return null;
    }

    /** Column index of a technical (row 0) header name on this sheet; -1 if not found. */
    private static int technicalColumnIndex(Sheet sheet, String technicalName) {
        Row technicalRow = sheet.getRow(0);
        if (technicalRow == null) {
            return -1;
        }
        for (Cell cell : technicalRow) {
            if (technicalName.equals(ExcelUtil.getCellValueAsString(cell))) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    /** Maps a permanent facility of a post type to a post row, listed as Exclude. */
    static Map<String, Object> toPostRowFromFacility(Map<String, Object> facility, String localityCode) {
        Map<String, Object> row = new HashMap<>();
        putIfPresent(row, ProcessingConstants.POST_CLIENT_REFERENCE_ID_COLUMN_KEY,
                asTrimmedString(facility.get("clientReferenceId")));
        putIfPresent(row, ProcessingConstants.POST_TYPE_COLUMN_KEY, asTrimmedString(facility.get("usage")));
        putIfPresent(row, ProcessingConstants.POST_CATEGORY_COLUMN_KEY,
                additionalFieldValue(facility, ProcessingConstants.POST_CATEGORY_ADDITIONAL_FIELD_KEY));
        putIfPresent(row, ProcessingConstants.POST_NAME_COLUMN_KEY, facility.get("name"));
        // fetchAllPermanentFacilities already filters isPermanent=true, so this is always Permanent.
        row.put(ProcessingConstants.POST_STATUS_COLUMN_KEY, ProcessingConstants.STATUS_PERMANENT);
        row.put(ProcessingConstants.POST_USAGE_COLUMN_KEY, ProcessingConstants.POST_USAGE_EXCLUDE);
        row.put(ProcessingConstants.BOUNDARY_CODE_COLUMN_KEY, localityCode);
        return row;
    }

    /** Value of one entry of a facility's additionalFields.fields list ({key, value}); null if absent. */
    @SuppressWarnings("unchecked")
    private static String additionalFieldValue(Map<String, Object> facility, String key) {
        Object additionalFields = facility.get("additionalFields");
        if (!(additionalFields instanceof Map)) {
            return null;
        }
        Object fields = ((Map<String, Object>) additionalFields).get("fields");
        if (!(fields instanceof List)) {
            return null;
        }
        for (Object field : (List<Object>) fields) {
            if (field instanceof Map && key.equals(((Map<String, Object>) field).get("key"))) {
                return asTrimmedString(((Map<String, Object>) field).get("value"));
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> row, String key, Object value) {
        if (value != null && !value.toString().trim().isEmpty()) {
            row.put(key, value);
        }
    }

    private static String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
