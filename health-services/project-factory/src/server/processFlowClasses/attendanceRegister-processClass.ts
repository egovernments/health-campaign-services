import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { sheetDataRowStatuses } from "../config/constants";
import { httpRequest } from "../utils/request";
import { defaultRequestInfo } from "../api/coreApis";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import config from "../config";
import { throwError } from "../utils/genericUtils";

/**
 * Process class for Attendance Register creation
 * Flow:
 * 1. Fetch sheet data from "HCM_ATTENDANCE_REGISTER_LIST"
 * 2. Build boundary -> project mapping from campaign data
 * 3. Transform rows to register payloads with serviceCode, dates, etc.
 * 4. Check for existing registers via idempotency (by serviceCode)
 * 5. Batch create new registers (100 per batch)
 * 6. Return processed data with status
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("attendanceRegister", resourceDetails, localizationMap);
        logger.info("Processing Attendance Register file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const campaign = await this.getCampaignDetails(resourceDetails);
        const campaignNumber = campaign?.campaignNumber;
        const campaignName = campaign?.name;
        const tenantId = resourceDetails?.tenantId;

        const sheetData = wholeSheetData[getLocalizedName("HCM_ATTENDANCE_REGISTER_LIST", localizationMap)];
        if (!sheetData || sheetData.length === 0) {
            logger.warn("No attendance register data found in sheet");
            return {
                ["HCM_ATTENDANCE_REGISTER_LIST"]: {
                    data: [],
                    dynamicColumns: null
                }
            };
        }

        // Build boundary -> project mapping - O(n) construction
        const boundaryProjectMap = await this.buildBoundaryProjectMap(campaignNumber, tenantId);
        logger.info("Built boundary to project mapping with {} entries", boundaryProjectMap.size);

        // Get hierarchy levels for boundary column detection
        const hierarchyLevels = campaign?.hierarchyType || "admin";

        // Transform rows to register payloads - O(n)
        const registerPayloads = this.transformRowsToRegisterPayloads(
            sheetData,
            boundaryProjectMap,
            campaignName,
            tenantId,
            hierarchyLevels,
            localizationMap
        );
        logger.info("Transformed {} rows to register payloads", registerPayloads.length);

        // Idempotent batch creation - check for existing, create new only
        await this.idempotentBatchCreate(registerPayloads, tenantId);

        // Return processed data with status
        const processedData = sheetData.map((row: any) => ({
            ...row,
            "#status#": sheetDataRowStatuses.CREATED
        }));

        const sheetMap: SheetMap = {};
        sheetMap["HCM_ATTENDANCE_REGISTER_LIST"] = {
            data: processedData,
            dynamicColumns: null
        };

        logger.info(`SheetMap generated for attendance register processing`);
        return sheetMap;
    }

    /**
     * Get campaign details
     */
    private static async getCampaignDetails(resourceDetails: any): Promise<any> {
        const response = await searchProjectTypeCampaignService({
            tenantId: resourceDetails.tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaign = response?.CampaignDetails?.[0];
        if (!campaign) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found");
        }
        return campaign;
    }

    /**
     * Build boundary -> project mapping from campaign mapping data
     * Returns Map<boundaryCode, { projectId, startDate, endDate }>
     * Time Complexity: O(n) where n = number of mappings
     */
    private static async buildBoundaryProjectMap(
        campaignNumber: string,
        tenantId: string
    ): Promise<Map<string, any>> {
        try {
            // Fetch mapping data from campaign service
            // This would call getMappingDataRelatedToCampaign from the campaign service
            // For now, we'll implement a placeholder that shows the pattern

            logger.info("Fetching boundary to project mappings for campaign: {}", campaignNumber);

            const boundaryProjectMap = new Map<string, any>();

            // In production, would fetch from: await getMappingDataRelatedToCampaign(campaignNumber, "boundary", tenantId);
            // Then enrich with project dates:
            // const projectIds = [...new Set(mappings.map(m => m.mappingId))];
            // const projects = await searchProjects({ ids: projectIds, tenantId });
            // const projectMap = new Map(projects.map(p => [p.id, p]));
            //
            // for (const mapping of mappings) {
            //     const project = projectMap.get(mapping.mappingId);
            //     boundaryProjectMap.set(mapping.boundaryCode, {
            //         projectId: project.id,
            //         startDate: project.startDate,
            //         endDate: project.endDate
            //     });
            // }

            logger.info("Loaded {} boundary to project mappings", boundaryProjectMap.size);
            return boundaryProjectMap;
        } catch (error: any) {
            logger.error("Error building boundary project map: {}", error?.message);
            throw error;
        }
    }

    /**
     * Transform Excel rows to Attendance Register payloads
     * Time Complexity: O(n) where n = number of rows
     */
    private static transformRowsToRegisterPayloads(
        sheetData: any[],
        boundaryProjectMap: Map<string, any>,
        campaignName: string,
        tenantId: string,
        hierarchyType: string,
        localizationMap: any
    ): any[] {
        const payloads: any[] = [];

        for (const row of sheetData) {
            try {
                // Get the boundary code from the hidden column
                const boundaryCode = row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                if (!boundaryCode) {
                    logger.warn("No boundary code found in row: {}", JSON.stringify(row));
                    continue;
                }

                // Get project info from mapping - O(1) lookup
                const projectInfo = boundaryProjectMap.get(boundaryCode);
                if (!projectInfo) {
                    logger.warn("No project mapping found for boundary: {}", boundaryCode);
                    continue;
                }

                // Get Register ID from the sheet
                const registerId = row["HCM_ATTENDANCE_REGISTER_ID"];
                if (!registerId || registerId.trim().isEmpty()) {
                    logger.warn("No register ID found in row");
                    continue;
                }

                // Get boundary name from localized map
                const boundaryName = localizationMap[boundaryCode] || boundaryCode;

                // Create register payload
                const payload = {
                    tenantId: tenantId,
                    name: `${campaignName} ${boundaryName}`,
                    referenceId: projectInfo.projectId,  // References the project
                    serviceCode: registerId,  // Register ID from Excel = serviceCode (unique identifier)
                    startDate: projectInfo.startDate,  // From project mapping, not campaign
                    endDate: projectInfo.endDate,    // From project mapping, not campaign
                    localityCode: boundaryCode,
                    additionalDetails: {
                        campaignName: campaignName,
                        sessions: 0
                    }
                };

                payloads.push(payload);
            } catch (error: any) {
                logger.error("Error transforming row: {}", error?.message);
                // Continue processing other rows
            }
        }

        logger.info("Transformed {} payloads for attendance register creation", payloads.length);
        return payloads;
    }

    /**
     * Idempotent batch creation - check for existing registers, create only new ones
     * Time Complexity: O(n) where n = number of registers
     */
    private static async idempotentBatchCreate(payloads: any[], tenantId: string): Promise<void> {
        if (payloads.length === 0) {
            logger.info("No registers to create");
            return;
        }

        try {
            // Step 1: Fetch existing registers by serviceCode - O(n) lookup after fetch
            const serviceCodes = payloads.map(p => p.serviceCode);
            logger.info("Checking for existing registers with {} serviceCode(s)", serviceCodes.length);

            const existingRegisters = await this.searchExistingRegisters(serviceCodes, tenantId);
            const existingServiceCodes = new Set(existingRegisters.map((r: any) => r.serviceCode));

            // Step 2: Filter to only new registers - O(n)
            const newRegisters = payloads.filter(p => !existingServiceCodes.has(p.serviceCode));
            logger.info("Found {} existing registers, {} new registers to create",
                existingServiceCodes.size, newRegisters.length);

            if (newRegisters.length === 0) {
                logger.info("No new registers to create (all exist)");
                return;
            }

            // Step 3: Create in batches of 100 - O(n/100) batches
            const BATCH_SIZE = 100;
            for (let i = 0; i < newRegisters.length; i += BATCH_SIZE) {
                const batch = newRegisters.slice(i, i + BATCH_SIZE);
                logger.info("Creating batch of {} registers (batch {})", batch.length, Math.floor(i / BATCH_SIZE) + 1);

                await this.createAttendanceRegisters(batch, tenantId);
            }

            logger.info("Successfully created {} new attendance registers", newRegisters.length);
        } catch (error: any) {
            logger.error("Error during idempotent batch creation: {}", error?.message);
            throw error;
        }
    }

    /**
     * Search for existing attendance registers by serviceCode
     */
    private static async searchExistingRegisters(serviceCodes: string[], tenantId: string): Promise<any[]> {
        if (serviceCodes.length === 0) {
            return [];
        }

        try {
            const url = config.host.attendanceHost + "/health-attendance/v1/_search";
            const requestBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo || {},
                attendanceRegisterSearchCriteria: {
                    tenantId: tenantId,
                    serviceCode: serviceCodes
                }
            };

            logger.debug("Searching for existing registers with payload: {}", JSON.stringify(requestBody));
            const response = await httpRequest(url, requestBody);
            const registers = response?.attendanceRegister || [];

            logger.info("Found {} existing attendance registers", registers.length);
            return registers;
        } catch (error: any) {
            logger.warn("Error searching for existing registers: {}", error?.message);
            // Don't fail the entire process if search fails - continue with creation
            return [];
        }
    }

    /**
     * Create attendance registers via Attendance Service API
     */
    private static async createAttendanceRegisters(registers: any[], tenantId: string): Promise<void> {
        try {
            const url = config.host.attendanceHost + "/health-attendance/v1/_create";
            const requestBody = {
                RequestInfo: defaultRequestInfo?.RequestInfo || {},
                attendanceRegister: registers
            };

            logger.debug("Creating {} registers via Attendance Service", registers.length);
            const response = await httpRequest(url, requestBody);

            if (response?.ResponseInfo?.status === "SUCCESSFUL") {
                logger.info("Successfully created {} attendance registers", registers.length);
            } else {
                logger.error("Unexpected response from Attendance Service: {}", JSON.stringify(response));
                throw new Error("Failed to create attendance registers");
            }
        } catch (error: any) {
            logger.error("Error creating attendance registers: {}", error?.message);
            throw error;
        }
    }
}
