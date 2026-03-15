import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { sheetDataRowStatuses } from "../config/constants";
import { httpRequest } from "../utils/request";
import { defaultRequestInfo } from "../api/coreApis";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import config from "../config";
import { getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";

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
        await validateResourceDetailsBeforeProcess("attendanceRegisterValidation", resourceDetails, localizationMap);
        logger.info("Processing Attendance Register file...");
        logger.info(`ResourceDetails: ${JSON.stringify(resourceDetails)}`);

        const campaign = await this.getCampaignDetails(resourceDetails);
        const campaignNumber = campaign?.campaignNumber;
        const campaignName = campaign?.campaignName;
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
        const boundaryProjectMap = await this.buildBoundaryProjectMap(campaignNumber, tenantId, campaign);
        logger.info("Built boundary to project mapping with {} entries", boundaryProjectMap.size);

        // Validate that projects exist for all boundaries in the sheet
        this.validateProjectsExist(sheetData, boundaryProjectMap);

        // Get hierarchy levels for boundary column detection
        const hierarchyLevels = campaign?.hierarchyType || "admin";

        // Transform rows to register payloads with per-row status - O(n)
        const transformResults = this.transformRowsToRegisterPayloads(
            sheetData,
            boundaryProjectMap,
            campaignName,
            tenantId,
            hierarchyLevels,
            localizationMap,
            resourceDetails
        );

        // Extract valid payloads for batch creation
        const validPayloads = transformResults
            .filter(r => r.payload !== null)
            .map(r => r.payload);
        logger.info("Transformed {} valid payloads out of {} rows", validPayloads.length, sheetData.length);

        const requestInfo = resourceDetails?.requestInfo;

        // Idempotent batch creation - check for existing, create new only
        await this.idempotentBatchCreate(validPayloads, tenantId, requestInfo);

        // Return processed data with per-row status and error details
        const processedData = sheetData.map((row: any, index: number) => ({
            ...row,
            "#status#": transformResults[index]?.status || sheetDataRowStatuses.CREATED,
            "#errorDetails#": transformResults[index]?.error || ""
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
     * Validate that projects exist for all boundary codes in the sheet data.
     * Fails early with descriptive errors if any boundaries lack project mappings.
     */
    private static validateProjectsExist(
        sheetData: any[],
        boundaryProjectMap: Map<string, any>
    ): void {
        const missingBoundaries: string[] = [];

        for (const row of sheetData) {
            const boundaryCode = row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
            if (boundaryCode && !boundaryProjectMap.has(boundaryCode)) {
                missingBoundaries.push(boundaryCode);
            }
        }

        if (missingBoundaries.length > 0) {
            const uniqueMissing = Array.from(new Set(missingBoundaries));
            const errorMsg = `No projects found for ${uniqueMissing.length} boundary code(s): ${uniqueMissing.slice(0, 10).join(", ")}${uniqueMissing.length > 10 ? "..." : ""}. Ensure project creation (Phase 1) is completed before creating attendance registers.`;
            logger.error(errorMsg);
            throwError("CAMPAIGN", 400, "PROJECT_CREATION_ERROR", errorMsg);
        }
    }

    /**
     * Build boundary -> project mapping from campaign data.
     * Uses the campaign_data table where boundary rows store the created projectId
     * in `uniqueIdAfterProcess` after project creation completes.
     * Returns Map<boundaryCode, { projectId, startDate, endDate }>
     * Time Complexity: O(n) where n = number of boundary data rows
     */
    private static async buildBoundaryProjectMap(
        campaignNumber: string,
        tenantId: string,
        campaign: any
    ): Promise<Map<string, any>> {
        try {
            logger.info("Fetching boundary to project mappings for campaign: {}", campaignNumber);

            // Fetch boundary data rows from campaign_data table
            // Each row has boundaryCode in data["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"]
            // and projectId in uniqueIdAfterProcess (set by boundary-processClass after project creation)
            const boundaryDataRows = await getRelatedDataWithCampaign("boundary", campaignNumber, tenantId);

            const boundaryProjectMap = new Map<string, any>();

            for (const row of boundaryDataRows) {
                const boundaryCode = row?.data?.["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                const projectId = row?.uniqueIdAfterProcess;

                if (boundaryCode && projectId) {
                    boundaryProjectMap.set(boundaryCode, {
                        projectId: projectId,
                        startDate: campaign?.startDate,
                        endDate: campaign?.endDate
                    });
                }
            }

            logger.info("Loaded {} boundary to project mappings", boundaryProjectMap.size);
            return boundaryProjectMap;
        } catch (error: any) {
            logger.error("Error building boundary project map: {}", error?.message);
            throw error;
        }
    }

    /**
     * Transform Excel rows to Attendance Register payloads.
     * Returns per-row results with status and error info.
     * Time Complexity: O(n) where n = number of rows
     */
    private static transformRowsToRegisterPayloads(
        sheetData: any[],
        boundaryProjectMap: Map<string, any>,
        campaignName: string,
        tenantId: string,
        hierarchyType: string,
        localizationMap: any,
        resourceDetails: any
    ): { payload: any | null; status: string; error: string }[] {
        const results: { payload: any | null; status: string; error: string }[] = [];

        for (const row of sheetData) {
            try {
                // Get the boundary code from the hidden column
                const boundaryCode = row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                if (!boundaryCode) {
                    results.push({ payload: null, status: sheetDataRowStatuses.INVALID, error: "Boundary code is missing" });
                    continue;
                }

                // Get project info from mapping - O(1) lookup
                const projectInfo = boundaryProjectMap.get(boundaryCode);
                if (!projectInfo) {
                    results.push({ payload: null, status: sheetDataRowStatuses.INVALID, error: `No project found for boundary: ${boundaryCode}. Ensure project creation is completed.` });
                    continue;
                }

                // Get Register ID from the sheet
                const registerId = row["HCM_ATTENDANCE_REGISTER_ID"];
                if (!registerId || registerId.trim() === "") {
                    results.push({ payload: null, status: sheetDataRowStatuses.INVALID, error: "Register ID is required" });
                    continue;
                }

                // Get boundary name from localized map
                const boundaryName = localizationMap[boundaryCode] || boundaryCode;

                // Read optional fields from Excel
                const eventType = row["HCM_ATTENDANCE_REGISTER_EVENT_TYPE"];
                const sessions = row["HCM_ATTENDANCE_REGISTER_SESSIONS"];

                // Get defaults from config
                const defaultEventType = config.attendanceRegister.defaultEventType;
                const defaultSessions = config.attendanceRegister.defaultSessions;

                // Build additionalDetails with conditional fields
                const additionalDetails: any = {
                    campaignNumber: resourceDetails?.campaignNumber,
                    campaignName: campaignName
                };

                // Add eventType: Excel value > Config default > Skip if both empty
                if (eventType !== undefined && eventType !== null && String(eventType).trim() !== "") {
                    additionalDetails.eventType = String(eventType).trim();
                } else if (defaultEventType && defaultEventType !== "") {
                    additionalDetails.eventType = defaultEventType;
                    logger.debug("Using default eventType from config: {}", defaultEventType);
                }

                // Add sessions: Excel value > Config default (with validation)
                if (sessions !== undefined && sessions !== null && sessions !== "") {
                    const parsedSessions = typeof sessions === 'number' ? sessions : parseInt(sessions, 10);
                    if (!isNaN(parsedSessions) && parsedSessions >= 0) {
                        additionalDetails.sessions = parsedSessions;
                    } else {
                        logger.warn("Invalid sessions value: {}, using default: {}", sessions, defaultSessions);
                        additionalDetails.sessions = defaultSessions;
                    }
                } else {
                    additionalDetails.sessions = defaultSessions;
                    logger.debug("Using default sessions from config: {}", defaultSessions);
                }

                // Create register payload
                const payload = {
                    tenantId: tenantId,
                    name: `${campaignName} ${boundaryName}`,
                    referenceId: projectInfo.projectId,
                    campaignId: resourceDetails?.campaignId,
                    serviceCode: registerId,
                    startDate: projectInfo.startDate,
                    endDate: projectInfo.endDate,
                    localityCode: boundaryCode,
                    additionalDetails: additionalDetails
                };

                results.push({ payload, status: sheetDataRowStatuses.CREATED, error: "" });
            } catch (error: any) {
                logger.error("Error transforming row: {}", error?.message);
                results.push({ payload: null, status: sheetDataRowStatuses.INVALID, error: error?.message || "Unknown error during transformation" });
            }
        }

        const validCount = results.filter(r => r.payload !== null).length;
        logger.info("Transformed {} valid payloads out of {} rows", validCount, sheetData.length);
        return results;
    }

    /**
     * Idempotent batch creation - check for existing registers, create only new ones
     * Time Complexity: O(n) where n = number of registers
     */
    private static async idempotentBatchCreate(payloads: any[], tenantId: string, requestInfo?: any): Promise<void> {
        if (payloads.length === 0) {
            logger.info("No registers to create");
            return;
        }

        try {
            // Step 1: Fetch existing registers by serviceCode - O(n) lookup after fetch
            const serviceCodes = payloads.map(p => p.serviceCode);
            logger.info("Checking for existing registers with {} serviceCode(s)", serviceCodes.length);

            const existingRegisters = await this.searchExistingRegisters(serviceCodes, tenantId, requestInfo);
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

                await this.createAttendanceRegisters(batch, tenantId, requestInfo);
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
    private static async searchExistingRegisters(serviceCodes: string[], tenantId: string, requestInfo?: any): Promise<any[]> {
        if (serviceCodes.length === 0) {
            return [];
        }

        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
            const RequestInfo = requestInfo || defaultRequestInfo?.RequestInfo || {};
            const requestBody = {
                RequestInfo
            };

            logger.debug("Searching for existing registers with serviceCode(s): {}", serviceCodes.join(", "));
            const response = await httpRequest(url, requestBody, { tenantId, serviceCode: serviceCodes });
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
    private static async createAttendanceRegisters(registers: any[], tenantId: string, requestInfo?: any): Promise<void> {
        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterCreate;
            const RequestInfo = requestInfo || defaultRequestInfo?.RequestInfo || {};
            const requestBody = {
                RequestInfo,
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
