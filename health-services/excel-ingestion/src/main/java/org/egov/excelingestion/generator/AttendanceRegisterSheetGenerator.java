package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for attendance register sheet - uses ExcelPopulator approach
 * Generates template with:
 * 1. README sheet with instructions
 * 2. Attendance Register List sheet with Register ID column
 * 3. Boundary hierarchy data for dropdown population
 */
@Component
@Slf4j
public class AttendanceRegisterSheetGenerator implements IExcelPopulatorSheetGenerator {

    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;

    public AttendanceRegisterSheetGenerator(BoundaryService boundaryService, BoundaryUtil boundaryUtil,
                                          MDMSService mdmsService, CampaignService campaignService,
                                          CustomExceptionHandler exceptionHandler,
                                          SchemaColumnDefUtil schemaColumnDefUtil) {
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
    }

    @Override
    public SheetGenerationResult generateSheetData(SheetGenerationConfig config,
                                                 GenerateResource generateResource,
                                                 RequestInfo requestInfo,
                                                 Map<String, String> localizationMap) {

        log.info("Generating attendance register sheet data for campaign: {}", generateResource.getReferenceId());

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

            // Fetch boundary hierarchy
            BoundaryHierarchyResponse hierarchyData = boundaryService.fetchBoundaryHierarchy(tenantId, hierarchyType, requestInfo);
            List<BoundaryHierarchyChild> hierarchyRelations = hierarchyData.getBoundaryHierarchy().get(0).getBoundaryHierarchy();

            // Get campaign boundaries
            List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries =
                campaignService.getBoundariesFromCampaign(campaignId, tenantId, requestInfo);

            if (campaignBoundaries == null || campaignBoundaries.isEmpty()) {
                log.info("No campaign boundaries found for campaign: {}", campaignId);
                return SheetGenerationResult.builder()
                        .columnDefs(new ArrayList<>())
                        .data(new ArrayList<>())
                        .build();
            }

            // Fetch schema columns for Register ID
            List<ColumnDef> schemaColumns = fetchAttendanceRegisterSchema(tenantId, requestInfo);

            // Create column definitions: boundary columns + Register ID
            List<ColumnDef> columnDefs = createAttendanceRegisterColumnDefs(hierarchyRelations, hierarchyType, schemaColumns);

            // Return empty data (users fill in the template)
            return SheetGenerationResult.builder()
                    .columnDefs(columnDefs)
                    .data(new ArrayList<>())
                    .build();

        } catch (Exception e) {
            log.error("Error generating attendance register sheet data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate attendance register sheet data", e);
        }
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

    /**
     * Fetch attendance register schema from MDMS
     * Returns columns for the register sheet
     */
    private List<ColumnDef> fetchAttendanceRegisterSchema(String tenantId, RequestInfo requestInfo) {
        List<ColumnDef> columns = new ArrayList<>();
        String schemaName = "attendance-register";

        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", schemaName);

            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);

            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");

                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    String schemaJson = mapper.writeValueAsString(properties);
                    columns = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
                    log.info("Successfully fetched {} schema columns for attendance-register", columns.size());
                }
            } else {
                log.warn("No schema found for: {}", schemaName);
            }
        } catch (Exception e) {
            log.error("Error fetching schema for attendance-register: {}", e.getMessage());
        }

        return columns;
    }

    /**
     * Create column definitions for attendance register:
     * 1. Boundary hierarchy columns (read-only, with dropdowns)
     * 2. Register ID column (user-fillable)
     */
    private List<ColumnDef> createAttendanceRegisterColumnDefs(
            List<BoundaryHierarchyChild> hierarchyRelations,
            String hierarchyType,
            List<ColumnDef> schemaColumns) {

        List<ColumnDef> columns = new ArrayList<>();

        // Create boundary hierarchy columns - frozen and with dropdown support
        for (int i = 0; i < hierarchyRelations.size(); i++) {
            String boundaryType = hierarchyRelations.get(i).getBoundaryType();
            String columnName = (hierarchyType + "_" + boundaryType).toUpperCase();

            columns.add(ColumnDef.builder()
                    .name(columnName)
                    .orderNumber(i + 1)
                    .width(50)
                    .colorHex("#93c47d")
                    .freezeColumn(true) // Boundary columns are frozen
                    .build());
        }

        // Add hidden boundary code column for internal processing
        columns.add(ColumnDef.builder()
                .name("HCM_ADMIN_CONSOLE_BOUNDARY_CODE")
                .orderNumber(hierarchyRelations.size() + 1)
                .width(80)
                .hideColumn(true)
                .freezeColumn(true)
                .adjustHeight(true)
                .build());

        // Add schema columns (Register ID, etc.) - user fillable
        int currentOrderNumber = hierarchyRelations.size() + 2;
        for (ColumnDef schemaCol : schemaColumns) {
            columns.add(ColumnDef.builder()
                    .name(schemaCol.getName())
                    .type(schemaCol.getType())
                    .description(schemaCol.getDescription())
                    .colorHex(schemaCol.getColorHex())
                    .orderNumber(currentOrderNumber++)
                    .freezeColumnIfFilled(schemaCol.isFreezeColumnIfFilled())
                    .hideColumn(schemaCol.isHideColumn())
                    .required(schemaCol.isRequired())
                    .pattern(schemaCol.getPattern())
                    .minimum(schemaCol.getMinimum())
                    .maximum(schemaCol.getMaximum())
                    .minLength(schemaCol.getMinLength())
                    .maxLength(schemaCol.getMaxLength())
                    .freezeColumn(schemaCol.isFreezeColumn())
                    .adjustHeight(schemaCol.isAdjustHeight())
                    .width(schemaCol.getWidth())
                    .unFreezeColumnTillData(schemaCol.isUnFreezeColumnTillData())
                    .freezeTillData(schemaCol.isFreezeTillData())
                    .enumValues(schemaCol.getEnumValues())
                    .multiSelectDetails(schemaCol.getMultiSelectDetails())
                    .build());
        }

        return columns;
    }
}
