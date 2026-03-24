import { RequestInfo } from "../config/models/requestInfoSchema";
import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { sheetDataRowStatuses, dataRowStatuses } from "../config/constants";
import { httpRequest } from "../utils/request";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import config from "../config";
import { getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";
import { produceModifiedMessages } from "../kafka/Producer";

/**
 * Process class for Attendance Register creation
 * Flow:
 * 1. Fetch sheet data from "HCM_ATTENDANCE_REGISTER_LIST"
 * 2. Build boundary -> project mapping from campaign data
 * 3. Transform rows to register payloads with serviceCode, dates, etc.
 * 4. Check for existing registers via idempotency (by serviceCode)
 * 5. Batch create new registers (100 per batch)
 * 6. Persist all rows to campaign_data table
 * 7. Re-fetch all rows from campaign_data and return as SheetMap
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
        const campaignId = campaign?.id;
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
            campaignNumber,
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

        // Fetch existing campaign_data rows BEFORE creation for idempotent upsert decision
        const existingDataMap = await this.buildExistingCampaignDataMap(campaignNumber, tenantId);
        logger.info("Found {} existing campaign_data rows for attendanceRegister", existingDataMap.size);

        // Idempotent batch creation - check for existing, create new only; classifies by campaign ownership
        const { existingServiceCodes, conflictingServiceCodes, serviceCodeToUuidMap } =
            await this.idempotentBatchCreate(validPayloads, campaignId, tenantId, requestInfo);

        // Build processed data with per-row status and error details (same as before, used for persistence)
        const processedData = sheetData.map((row: any, index: number) => {
            const result = transformResults[index];
            let status = result?.status || sheetDataRowStatuses.CREATED;
            let error = result?.error || "";

            if (status === sheetDataRowStatuses.CREATED && result?.serviceCode) {
                if (conflictingServiceCodes.has(result.serviceCode)) {
                    status = sheetDataRowStatuses.INVALID;
                    error = "Service code not available";
                } else if (existingServiceCodes.has(result.serviceCode)) {
                    status = sheetDataRowStatuses.UPDATED;
                }
            }

            return {
                ...row,
                "#status#": status,
                "#errorDetails#": error
            };
        });

        // Persist all rows to campaign_data table
        await this.persistRegistersToCampaignData(
            sheetData,
            transformResults,
            processedData,
            existingDataMap,
            conflictingServiceCodes,
            serviceCodeToUuidMap,
            campaignNumber,
            tenantId
        );

        // Wait for Kafka persistence (same pattern as user-processClass)
        const waitTime = Math.max(3000, sheetData.length * 5);
        logger.info(`Waiting ${waitTime}ms for campaign_data persistence...`);
        await new Promise(res => setTimeout(res, waitTime));

        // Re-fetch ALL rows from campaign_data (includes previous uploads)
        const allRows = await getRelatedDataWithCampaign("attendanceRegister", campaignNumber, tenantId);
        logger.info("Re-fetched {} rows from campaign_data for attendanceRegister", allRows.length);

        // Rows without a serviceCode (transform failures: missing boundary code / register ID)
        // are not persisted to DB — append them from in-memory to preserve them in the output
        const unpersistableRows = sheetData
            .map((_: any, i: number) => ({ result: transformResults[i], processed: processedData[i] }))
            .filter(({ result }: any) => !result.serviceCode)
            .map(({ processed }: any) => processed);

        const outputData = [...allRows.map((r: any) => r.data), ...unpersistableRows];
        if (unpersistableRows.length > 0) {
            logger.info("Appended {} unpersistable INVALID rows to output", unpersistableRows.length);
        }

        const sheetMap: SheetMap = {};
        sheetMap["HCM_ATTENDANCE_REGISTER_LIST"] = {
            data: outputData,
            dynamicColumns: null
        };

        logger.info(`SheetMap generated for attendance register processing`);
        return sheetMap;
    }

    /**
     * Build a map of existing campaign_data rows keyed by serviceCode (uniqueIdentifier).
     * Used to decide SAVE (new) vs UPDATE (existing) Kafka topic.
     */
    private static async buildExistingCampaignDataMap(campaignNumber: string, tenantId: string): Promise<Map<string, any>> {
        const rows = await getRelatedDataWithCampaign("attendanceRegister", campaignNumber, tenantId);
        const map = new Map<string, any>();
        for (const row of rows) {
            map.set(row.uniqueIdentifier, row);
        }
        return map;
    }

    /**
     * Persist all processed register rows to campaign_data via Kafka.
     * - NEW rows (not in existingDataMap) → KAFKA_SAVE_SHEET_DATA_TOPIC
     * - EXISTING rows (already in campaign_data) → KAFKA_UPDATE_SHEET_DATA_TOPIC
     */
    private static async persistRegistersToCampaignData(
        sheetData: any[],
        transformResults: Array<{ payload: any | null; serviceCode: string | null; status: string; error: string }>,
        processedData: any[],
        existingDataMap: Map<string, any>,
        conflictingServiceCodes: Set<string>,
        serviceCodeToUuidMap: Map<string, string>,
        campaignNumber: string,
        tenantId: string
    ): Promise<void> {
        const toSave: any[] = [];
        const toUpdate: any[] = [];

        for (let i = 0; i < sheetData.length; i++) {
            const result = transformResults[i];
            const processedRow = processedData[i];
            const serviceCode = result?.serviceCode;

            // Rows with no serviceCode (transform failures with no Register ID / boundary code)
            // have no stable identity — skip them from persistence. They are genuinely invalid
            // and cannot be re-identified on a subsequent upload.
            if (!serviceCode) continue;

            const isConflicting = conflictingServiceCodes.has(serviceCode);
            const dbStatus = isConflicting ? dataRowStatuses.failed : dataRowStatuses.completed;
            // Only store the UUID for registers that belong to this campaign (not foreign-campaign conflicts)
            const uniqueIdAfterProcess = isConflicting ? null : (serviceCodeToUuidMap.get(serviceCode) || null);

            const payload = {
                campaignNumber,
                type: "attendanceRegister",
                uniqueIdentifier: serviceCode,
                data: processedRow,
                status: dbStatus,
                uniqueIdAfterProcess
            };

            if (existingDataMap.has(serviceCode)) {
                toUpdate.push(payload);
            } else {
                toSave.push(payload);
            }
        }

        const BATCH_SIZE = 100;

        for (let i = 0; i < toSave.length; i += BATCH_SIZE) {
            const batch = toSave.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_SAVE_SHEET_DATA_TOPIC, tenantId);
        }
        for (let i = 0; i < toUpdate.length; i += BATCH_SIZE) {
            const batch = toUpdate.slice(i, i + BATCH_SIZE);
            await produceModifiedMessages({ datas: batch }, config.kafka.KAFKA_UPDATE_SHEET_DATA_TOPIC, tenantId);
        }

        logger.info("Persisted {} new and {} updated attendance register rows to campaign_data", toSave.length, toUpdate.length);
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
        campaignNumber: string,
        tenantId: string,
        hierarchyType: string,
        localizationMap: any,
        resourceDetails: any
    ): { payload: any | null; serviceCode: string | null; status: string; error: string }[] {
        const results: { payload: any | null; serviceCode: string | null; status: string; error: string }[] = [];

        for (const row of sheetData) {
            try {
                // Get the boundary code from the hidden column
                const boundaryCode = row["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"];
                if (!boundaryCode) {
                    results.push({ payload: null, serviceCode: null, status: sheetDataRowStatuses.INVALID, error: "Boundary code is missing" });
                    continue;
                }

                // Get project info from mapping - O(1) lookup
                const projectInfo = boundaryProjectMap.get(boundaryCode);
                if (!projectInfo) {
                    results.push({ payload: null, serviceCode: null, status: sheetDataRowStatuses.INVALID, error: `No project found for boundary: ${boundaryCode}. Ensure project creation is completed.` });
                    continue;
                }

                // Get Register ID from the sheet
                const registerId = row["HCM_ATTENDANCE_REGISTER_ID"];
                if (!registerId || registerId.trim() === "") {
                    results.push({ payload: null, serviceCode: null, status: sheetDataRowStatuses.INVALID, error: "Register ID is required" });
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
                    campaignNumber: campaignNumber,
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

                results.push({ payload, serviceCode: registerId, status: sheetDataRowStatuses.CREATED, error: "" });
            } catch (error: any) {
                logger.error("Error transforming row: {}", error?.message);
                results.push({ payload: null, serviceCode: null, status: sheetDataRowStatuses.INVALID, error: error?.message || "Unknown error during transformation" });
            }
        }

        const validCount = results.filter(r => r.payload !== null).length;
        logger.info("Transformed {} valid payloads out of {} rows", validCount, sheetData.length);
        return results;
    }

    /**
     * Idempotent batch create/update - check for existing registers, create new ones and update existing same-campaign ones
     * Returns existingServiceCodes, conflictingServiceCodes, and serviceCodeToUuidMap (serviceCode -> UUID)
     */
    private static async idempotentBatchCreate(
        payloads: any[],
        campaignId: string,
        tenantId: string,
        requestInfo?: RequestInfo
    ): Promise<{
        existingServiceCodes: Set<string>;
        conflictingServiceCodes: Set<string>;
        serviceCodeToUuidMap: Map<string, string>;
    }> {
        const serviceCodeToUuidMap = new Map<string, string>();

        if (payloads.length === 0) {
            logger.info("No registers to create");
            return {
                existingServiceCodes: new Set<string>(),
                conflictingServiceCodes: new Set<string>(),
                serviceCodeToUuidMap
            };
        }

        try {
            // Step 1: Fetch existing registers by serviceCode across all campaigns - O(n) lookup after fetch
            const serviceCodes = payloads.map(p => p.serviceCode);
            logger.info("Checking for existing registers with {} serviceCode(s)", serviceCodes.length);

            const existingRegisters = await this.searchExistingRegisters(serviceCodes, tenantId, requestInfo);

            // Build a map from serviceCode -> existing register for O(1) lookup
            const existingByServiceCode = new Map<string, any>();
            for (const r of existingRegisters) {
                existingByServiceCode.set(r.serviceCode, r);
                // Populate UUID map for existing registers
                if (r.serviceCode && r.id) {
                    serviceCodeToUuidMap.set(r.serviceCode, r.id);
                }
            }

            // Step 2: Classify registers by campaign ownership
            const existingServiceCodes = new Set<string>();    // same campaign — update
            const conflictingServiceCodes = new Set<string>(); // different campaign — mark INVALID

            for (const r of existingRegisters) {
                if (r.campaignId === campaignId) {
                    existingServiceCodes.add(r.serviceCode);
                } else {
                    conflictingServiceCodes.add(r.serviceCode);
                }
            }

            logger.info("Found {} same-campaign registers to update, {} cross-campaign conflicts",
                existingServiceCodes.size, conflictingServiceCodes.size);

            // Step 3: Separate new vs existing same-campaign registers - O(n)
            const newRegisters = payloads.filter(p =>
                !existingServiceCodes.has(p.serviceCode) && !conflictingServiceCodes.has(p.serviceCode)
            );
            const registersToUpdate = payloads
                .filter(p => existingServiceCodes.has(p.serviceCode))
                .map(p => this.mergeWithExistingRegister(p, existingByServiceCode.get(p.serviceCode)));

            logger.info("{} new registers to create, {} existing to update", newRegisters.length, registersToUpdate.length);

            const BATCH_SIZE = 100;

            // Step 4: Create new registers in batches of 100
            if (newRegisters.length > 0) {
                for (let i = 0; i < newRegisters.length; i += BATCH_SIZE) {
                    const batch = newRegisters.slice(i, i + BATCH_SIZE);
                    logger.info("Creating batch of {} registers (batch {})", batch.length, Math.floor(i / BATCH_SIZE) + 1);
                    const createdRegisters = await this.createAttendanceRegisters(batch, tenantId, requestInfo);
                    // Capture UUIDs from created registers
                    for (const reg of createdRegisters) {
                        if (reg.serviceCode && reg.id) {
                            serviceCodeToUuidMap.set(reg.serviceCode, reg.id);
                        }
                    }
                }
                logger.info("Successfully created {} new attendance registers", newRegisters.length);
            }

            // Step 5: Update existing same-campaign registers in batches of 100
            if (registersToUpdate.length > 0) {
                for (let i = 0; i < registersToUpdate.length; i += BATCH_SIZE) {
                    const batch = registersToUpdate.slice(i, i + BATCH_SIZE);
                    logger.info("Updating batch of {} registers (batch {})", batch.length, Math.floor(i / BATCH_SIZE) + 1);
                    await this.updateAttendanceRegisters(batch, requestInfo);
                }
                logger.info("Successfully updated {} existing attendance registers", registersToUpdate.length);
            }

            return { existingServiceCodes, conflictingServiceCodes, serviceCodeToUuidMap };
        } catch (error: any) {
            logger.error("Error during idempotent batch create/update: {}", error?.message);
            throw error;
        }
    }

    /**
     * Merge incoming payload with existing register for update.
     */
    private static mergeWithExistingRegister(payload: any, existingRegister: any): any {
        return {
            ...existingRegister,
            name: payload.name,
            startDate: payload.startDate,
            endDate: payload.endDate,
            referenceId: payload.referenceId,
            status: payload.status !== undefined ? payload.status : existingRegister.status,
            additionalDetails: payload.additionalDetails,
            localityCode: payload.localityCode,
            reviewStatus: payload.reviewStatus !== undefined ? payload.reviewStatus : existingRegister.reviewStatus,
            periodStatuses: payload.periodStatuses !== undefined ? payload.periodStatuses : existingRegister.periodStatuses,
            campaignId: payload.campaignId,
        };
    }

    /**
     * Search for existing attendance registers by serviceCode
     */
    private static async searchExistingRegisters(serviceCodes: string[], tenantId: string, requestInfo?: RequestInfo): Promise<any[]> {
        if (serviceCodes.length === 0) {
            return [];
        }

        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo || {};
        const requestBody = { RequestInfo };

        // serviceCode is type: string in swagger — send one per call, batch in parallel
        const parallelLimit = Math.min(config.attendanceRegister.serviceCodeParallelSearchLimit, serviceCodes.length);
        const allRegisters: any[] = [];

        for (let i = 0; i < serviceCodes.length; i += parallelLimit) {
            const window = serviceCodes.slice(i, i + parallelLimit);
            const results = await Promise.all(
                window.map(code =>
                    httpRequest(url, requestBody, { tenantId, serviceCode: code })
                        .then((r: any) => r?.attendanceRegister || [])
                        .catch((err: any) => {
                            logger.warn("Error searching for existing registers for serviceCode {}: {}", code, err?.message);
                            return [];
                        })
                )
            );
            for (const registers of results) {
                allRegisters.push(...registers);
            }
        }

        logger.info("Found {} existing attendance registers across {} serviceCode(s)", allRegisters.length, serviceCodes.length);
        return allRegisters;
    }

    /**
     * Create attendance registers via Attendance Service API.
     * Returns the created register objects (with UUIDs) from the response.
     */
    private static async createAttendanceRegisters(registers: any[], tenantId: string, requestInfo?: RequestInfo): Promise<any[]> {
        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterCreate;
            const RequestInfo = requestInfo || {};
            const requestBody = {
                RequestInfo,
                attendanceRegister: registers
            };

            logger.debug("Creating {} registers via Attendance Service", registers.length);
            const response = await httpRequest(url, requestBody);

            if (response?.ResponseInfo?.status?.toUpperCase() === "SUCCESSFUL") {
                logger.info("Successfully created {} attendance registers", registers.length);
                return response?.attendanceRegister || [];
            } else {
                logger.error("Unexpected response from Attendance Service: {}", JSON.stringify(response));
                throw new Error("Failed to create attendance registers");
            }
        } catch (error: any) {
            logger.error("Error creating attendance registers: {}", error?.message);
            throw error;
        }
    }

    /**
     * Update existing attendance registers via Attendance Service API.
     */
    private static async updateAttendanceRegisters(registers: any[], requestInfo?: RequestInfo): Promise<void> {
        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterUpdate;
            const RequestInfo = requestInfo || {};
            const requestBody = {
                RequestInfo,
                attendanceRegister: registers
            };

            logger.debug("Updating {} registers via Attendance Service", registers.length);
            const response = await httpRequest(url, requestBody);

            if (response?.ResponseInfo?.status?.toUpperCase() === "SUCCESSFUL") {
                logger.info("Successfully updated {} attendance registers", registers.length);
            } else {
                logger.error("Unexpected response from Attendance Service update: {}", JSON.stringify(response));
                throw new Error("Failed to update attendance registers");
            }
        } catch (error: any) {
            logger.error("Error updating attendance registers: {}", error?.message);
            throw error;
        }
    }
}
