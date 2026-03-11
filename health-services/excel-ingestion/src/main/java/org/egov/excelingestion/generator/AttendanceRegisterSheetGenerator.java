package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.HierarchicalBoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.util.ExcelDataPopulator;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for attendance register sheet with boundary hierarchy dropdowns
 * Generates template with:
 * 1. README sheet with instructions
 * 2. Attendance Register List sheet with boundary columns (with dropdowns) + Register ID
 * 3. Boundary hierarchy data for dropdown population
 *
 * Follows same pattern as UserSheetGenerator and FacilitySheetGenerator
 */
@Component
@Slf4j
public class AttendanceRegisterSheetGenerator implements ISheetGenerator {

    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ExcelDataPopulator excelDataPopulator;
    private final HierarchicalBoundaryUtil hierarchicalBoundaryUtil;

    public AttendanceRegisterSheetGenerator(BoundaryService boundaryService, BoundaryUtil boundaryUtil,
                                          MDMSService mdmsService, CampaignService campaignService,
                                          CustomExceptionHandler exceptionHandler,
                                          SchemaColumnDefUtil schemaColumnDefUtil,
                                          ExcelDataPopulator excelDataPopulator,
                                          HierarchicalBoundaryUtil hierarchicalBoundaryUtil) {
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
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

        log.info("Generating attendance register sheet: {} for campaign: {}", sheetName, generateResource.getReferenceId());

        try {
            String tenantId = generateResource.getTenantId();
            String campaignId = generateResource.getReferenceId();

            // Precondition: Check if project creation is complete
            checkProjectCreationComplete(campaignId, tenantId, requestInfo);

            // Fetch campaign details
            CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(campaignId, tenantId, requestInfo);
            if (campaign == null) {
                throw new Exception("Campaign not found");
            }

            String hierarchyType = campaign.getHierarchyType();

            // Fetch schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", config.getSchemaName());

            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);

            String schemaJson = extractSchemaFromMDMSResponse(mdmsList, config.getSchemaName());

            if (schemaJson != null && !schemaJson.isEmpty()) {
                List<ColumnDef> columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);

                // Create empty sheet first
                if (workbook.getSheetIndex(sheetName) >= 0) {
                    workbook.removeSheetAt(workbook.getSheetIndex(sheetName));
                }
                workbook.createSheet(sheetName);

                // Add boundary dropdowns first using HierarchicalBoundaryUtil (matching User/Facility pattern)
                if (shouldAddBoundaryDropdowns(generateResource)) {
                    // Get enriched boundaries from campaign service
                    List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries =
                            campaignService.getBoundariesFromCampaign(generateResource.getReferenceId(),
                                    generateResource.getTenantId(), requestInfo);

                    if (campaignBoundaries != null && !campaignBoundaries.isEmpty()) {
                        // Get enriched boundaries using cached function
                        List<Boundary> enrichedBoundaries = boundaryUtil.getEnrichedBoundariesFromCampaign(
                                generateResource.getId(), generateResource.getReferenceId(),
                                generateResource.getTenantId(), generateResource.getHierarchyType(), requestInfo);

                        // Add boundary columns with dropdown support to the sheet
                        hierarchicalBoundaryUtil.addHierarchicalBoundaryColumnWithData(
                                workbook, sheetName, localizationMap, enrichedBoundaries,
                                generateResource.getHierarchyType(), generateResource.getTenantId(), requestInfo, null);
                    }
                }

                // Filter out boundary columns from the list passed to ExcelDataPopulator
                List<ColumnDef> schemaOnlyColumns = columns.stream()
                        .filter(c -> !c.getName().startsWith(hierarchyType.toUpperCase() + "_"))
                        .collect(java.util.stream.Collectors.toList());

                // Then add schema columns (Register ID) using ExcelDataPopulator
                workbook = (XSSFWorkbook) excelDataPopulator.populateSheetWithData(workbook, sheetName, schemaOnlyColumns, null, localizationMap);
            }

        } catch (Exception e) {
            log.error("Error generating attendance register sheet {}: {}", sheetName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate attendance register sheet: " + sheetName, e);
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

    /**
     * Precondition: Verify project creation is complete
     * Note: This is a placeholder - actual precondition check happens in Project Factory (TypeScript)
     * Excel Ingestion service generates template; Project Factory enforces business rules
     */
    private void checkProjectCreationComplete(String campaignId, String tenantId, RequestInfo requestInfo) throws Exception {
        try {
            CampaignSearchResponse.CampaignDetail campaign = campaignService.searchCampaignById(campaignId, tenantId, requestInfo);
            if (campaign == null) {
                throw new Exception("Campaign not found");
            }

            log.info("Campaign verified for attendance register template generation: {}", campaignId);
        } catch (Exception e) {
            log.error("Error verifying campaign: {}", e.getMessage());
            throw e;
        }
    }
}
