import { RequestInfo } from "../config/models/requestInfoSchema";
import { getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";
import { SheetMap } from "../models/SheetMap";
import { getLocalizedName } from "../utils/campaignUtils";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { logger } from "../utils/logger";
import { processStatuses, allProcesses, dataRowStatuses } from "../config/constants";
import { decrypt } from "../utils/cryptUtils";
import { httpRequest } from "../utils/request";
import config from "../config";

// Role codes for classifying users into sheets (priority order — first match wins)
const APPROVER_ROLES = new Set(["PROXIMITY_SUPERVISOR"]);
const MARKER_ROLES = new Set(["WAREHOUSE_MANAGER", "TEAM_SUPERVISOR", "CAMPAIGN_SUPERVISOR"]);
const WORKER_ROLES = new Set(["DISTRIBUTOR", "REGISTRAR", "FIELD_SUPPORT", "HEALTH_FACILITY_WORKER"]);

const WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
const MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
const APPROVER_SHEET = "HCM_REGISTER_APPROVER_SHEET";

/**
 * Generate class for Attendance Register Attendee Mapping.
 * Generates 3-sheet Excel prefilled with users filtered by role and boundary.
 */
export class TemplateClass {
    static async generate(templateConfig: any, responseToSend: any, localizationMap: any): Promise<SheetMap> {
        logger.info("Generating attendance register attendee template...");

        const { tenantId, campaignId } = responseToSend;
        const registerId = responseToSend?.additionalDetails?.registerId;

        if (!registerId) {
            throwError("CAMPAIGN", 400, "REGISTER_ID_REQUIRED", "registerId is required for attendanceRegisterAttendee generation");
        }

        // Fetch campaign details and verify attendance register creation is complete
        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaign = campaignResp?.CampaignDetails?.[0];
        if (!campaign) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found");
        }

        const processes = campaign?.processes || [];
        const registerProcess = processes.find(
            (p: any) => p.processName === allProcesses.attendanceRegisterCreation
        );
        if (!registerProcess || registerProcess.status !== processStatuses.completed) {
            throwError("CAMPAIGN", 400, "ATTENDANCE_REGISTER_NOT_COMPLETE",
                "Attendance registers must be created before mapping attendees");
        }

        // Fetch attendance register to get localityCode, startDate, endDate
        const register = await this.fetchRegister(registerId, tenantId, responseToSend?.requestInfo);
        if (!register) {
            throwError("CAMPAIGN", 400, "ATTENDANCE_REGISTER_NOT_FOUND", `Attendance register not found: ${registerId}`);
        }

        const localityCode: string = register?.localityCode || "";
        const registerServiceCode: string = register?.serviceCode || "";
        const hierarchyType: string = campaign?.hierarchyType || "";

        if (!localityCode) {
            throwError("CAMPAIGN", 400, "ATTENDANCE_REGISTER_LOCALITY_MISSING",
                `Attendance register ${registerId} has no localityCode set`);
        }
        if (!hierarchyType) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_HIERARCHY_MISSING",
                `Campaign ${campaignId} has no hierarchyType set`);
        }

        // ── DB-first population ────────────────────────────────────────────────
        // If attendees have been processed and stored in campaign_data, return them
        // directly rather than re-generating from HRMS (shows real processed state).
        const storedAttendeeRows = await getRelatedDataWithCampaign(
            "attendanceRegisterAttendee", campaign?.campaignNumber, tenantId, dataRowStatuses.completed
        );
        const filteredStoredRows = storedAttendeeRows.filter(
            (r: any) => r.data?._registerServiceCode === registerServiceCode
        );

        if (filteredStoredRows.length > 0) {
            logger.info(`Found ${filteredStoredRows.length} stored attendee rows for register ${registerServiceCode} — using DB data`);
            const sheetMap: SheetMap = {};
            for (const sheetName of [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET]) {
                const rowsForSheet = filteredStoredRows
                    .filter((r: any) => r.data?._sheetName === sheetName)
                    .map((r: any) => {
                        // Strip internal persistence fields before returning in output
                        // eslint-disable-next-line @typescript-eslint/no-unused-vars
                        const { _registerServiceCode, _sheetName: _sn, ...outputRow } = r.data;
                        return outputRow;
                    });
                sheetMap[sheetName] = { data: rowsForSheet, dynamicColumns: null };
            }
            return sheetMap;
        }
        logger.info(`No stored attendee rows found for register ${registerServiceCode} — falling back to HRMS generation`);

        // Resolve allowed boundary codes per sheet from MDMS boundaryFilter config (run in parallel)
        const getBoundaryFilter = (sheetName: string) =>
            templateConfig?.sheets?.find((s: any) => s.sheetName === sheetName)?.boundaryFilter;

        const [workerCodes, markerCodes, approverCodes] = await Promise.all([
            this.resolveAllowedBoundaryCodes(tenantId, hierarchyType, localityCode, getBoundaryFilter(WORKER_SHEET)),
            this.resolveAllowedBoundaryCodes(tenantId, hierarchyType, localityCode, getBoundaryFilter(MARKER_SHEET)),
            this.resolveAllowedBoundaryCodes(tenantId, hierarchyType, localityCode, getBoundaryFilter(APPROVER_SHEET))
        ]);

        // Fetch all completed user credentials for this campaign
        const users = await getRelatedDataWithCampaign("user", campaign?.campaignNumber, tenantId, dataRowStatuses.completed);
        logger.info(`Fetched ${users.length} users for campaign ${campaignId}`);

        // Classify users and filter by allowed boundary codes.
        // A user appears in at most one sheet — role priority (APPROVER > MARKER > WORKER) determines which.
        const workerRows: any[] = [];
        const markerRows: any[] = [];
        const approverRows: any[] = [];

        for (const userEntry of users) {
            const rawData = userEntry?.data || {};
            if (!rawData || !rawData["UserName"]) continue;

            // HCM_ADMIN_CONSOLE_USER_ROLE is a CONCATENATE formula — result may not be cached in DB
            // Fallback: collect from individual multiselect columns
            const roleCodes = this.getRoleCodes(rawData);

            const sheet = this.classifyUserToSheet(roleCodes);
            if (!sheet) continue;

            const boundaryCode = rawData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] || rawData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] || "";

            // Filter by boundary using per-sheet allowed codes resolved from MDMS config
            const allowedCodes = sheet === WORKER_SHEET ? workerCodes
                : sheet === MARKER_SHEET ? markerCodes : approverCodes;
            if (!allowedCodes.has(boundaryCode)) continue;

            const row = this.buildRowData(rawData, registerServiceCode, localizationMap, sheet === WORKER_SHEET);
            if (sheet === APPROVER_SHEET) approverRows.push(row);
            else if (sheet === MARKER_SHEET) markerRows.push(row);
            else workerRows.push(row);
        }

        logger.info(`Classification result — Workers: ${workerRows.length}, Markers: ${markerRows.length}, Approvers: ${approverRows.length}`);

        const sheetMap: SheetMap = {
            [WORKER_SHEET]: { data: workerRows, dynamicColumns: null },
            [MARKER_SHEET]: { data: markerRows, dynamicColumns: null },
            [APPROVER_SHEET]: { data: approverRows, dynamicColumns: null }
        };
        return sheetMap;
    }

    /**
     * Fetch attendance register by ID.
     */
    private static async fetchRegister(registerId: string, tenantId: string, requestInfo?: RequestInfo): Promise<any> {
        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo || {};
        // ids is an array param per API spec
        const response = await httpRequest(url, { RequestInfo }, { tenantId, ids: [registerId] });
        return response?.attendanceRegister?.[0] || null;
    }

    /**
     * Resolve allowed boundary codes for a sheet based on its BoundaryFilterConfig.
     *
     * Modes:
     * - ANCESTOR_AND_SELF: register locality + all ancestors (path from root to locality)
     * - LEVEL_RANGE: register locality down to configured deepest boundary type
     * - null/unknown: self only (exact locality match)
     */
    private static async resolveAllowedBoundaryCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string,
        filter: any
    ): Promise<Set<string>> {
        if (!filter || !filter.mode) return new Set([localityCode]);
        if (filter.mode === "ANCESTOR_AND_SELF") {
            return this.getBoundaryAncestorAndSelfCodes(tenantId, hierarchyType, localityCode);
        }
        if (filter.mode === "LEVEL_RANGE") {
            return this.getBoundaryLevelRangeCodes(tenantId, hierarchyType, localityCode, filter.levelConfig);
        }
        logger.warn(`Unknown boundaryFilter mode '${filter.mode}', defaulting to self only`);
        return new Set([localityCode]);
    }

    /**
     * Collect register locality + all ancestor boundary codes.
     * Calls boundary API with includeParents=true to get path from root to localityCode.
     */
    private static async getBoundaryAncestorAndSelfCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string
    ): Promise<Set<string>> {
        const response = await searchBoundaryRelationshipData(tenantId, hierarchyType, false, true, false, localityCode);
        const root = response?.TenantBoundary?.[0]?.boundary?.[0];
        const codes = new Set<string>();
        this.collectBoundaryCodes(root, codes);
        return codes.size > 0 ? codes : new Set([localityCode]);
    }

    /**
     * Collect boundary codes from register locality down to the configured deepest boundary type.
     * If no levelConfig entry for the register's boundary type, returns self only.
     */
    private static async getBoundaryLevelRangeCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string,
        levelConfig: Record<string, string>
    ): Promise<Set<string>> {
        const response = await searchBoundaryRelationshipData(tenantId, hierarchyType, true, false, false, localityCode);
        const root = response?.TenantBoundary?.[0]?.boundary?.[0];
        if (!root) return new Set([localityCode]);

        const registerBoundaryType: string = root.boundaryType || "";
        const deepestType = levelConfig?.[registerBoundaryType];
        if (!deepestType) {
            logger.info(`No levelConfig entry for boundary type '${registerBoundaryType}', using self only for locality ${localityCode}`);
            return new Set([localityCode]);
        }

        const codes = new Set<string>();
        this.collectDescendantsUntilLevel(root, deepestType, codes);
        return codes.size > 0 ? codes : new Set([localityCode]);
    }

    /**
     * DFS: collect all boundary codes from node downward, stopping at nodes of deepestType.
     * Nodes at deepestType are included; their children are not.
     */
    private static collectDescendantsUntilLevel(node: any, deepestType: string, codes: Set<string>): void {
        if (!node) return;
        if (node.code) codes.add(node.code);
        if (node.boundaryType === deepestType) return; // stop, don't recurse deeper
        for (const child of node.children || []) {
            this.collectDescendantsUntilLevel(child, deepestType, codes);
        }
    }

    /**
     * Collect all boundary codes from a node and its descendants (no depth limit).
     */
    private static collectBoundaryCodes(node: any, codes: Set<string>): void {
        if (!node) return;
        if (node.code) codes.add(node.code);
        for (const child of node.children || []) {
            this.collectBoundaryCodes(child, codes);
        }
    }

    /**
     * Classify user to a sheet based on role codes (priority: APPROVER > MARKER > WORKER).
     * Returns the sheet constant or null if no matching roles.
     */
    private static classifyUserToSheet(roleCodes: string[]): string | null {
        for (const role of roleCodes) {
            if (APPROVER_ROLES.has(role)) return APPROVER_SHEET;
        }
        for (const role of roleCodes) {
            if (MARKER_ROLES.has(role)) return MARKER_SHEET;
        }
        for (const role of roleCodes) {
            if (WORKER_ROLES.has(role)) return WORKER_SHEET;
        }
        return null;
    }

    /**
     * Build row data object for a user, with prefilled and empty editable columns.
     */
    private static buildRowData(rawData: any, registerServiceCode: string, localizationMap: any, includeTeamCode: boolean): any {
        const boundaryCode = rawData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] || rawData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] || "";
        // Combine roles into a single comma-separated string for display
        const roleDisplayStr = this.getRoleCodes(rawData).join(", ");
        const row: any = {
            "HCM_ADMIN_CONSOLE_USER_WORKER_ID": rawData["HCM_ADMIN_CONSOLE_USER_WORKER_ID"] || "",
            "HCM_ADMIN_CONSOLE_USER_NAME": rawData["HCM_ADMIN_CONSOLE_USER_NAME"] || "",
            "UserName": rawData["UserName"] ? decrypt(rawData["UserName"]) : "",
            "Password": rawData["Password"] ? decrypt(rawData["Password"]) : "",
            "HCM_ADMIN_CONSOLE_USER_ROLE": roleDisplayStr,
            "HCM_ADMIN_CONSOLE_BOUNDARY_NAME": rawData["HCM_ADMIN_CONSOLE_BOUNDARY_NAME"] || getLocalizedName(boundaryCode, localizationMap),
            "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY": boundaryCode,
            "HCM_ATTENDANCE_REGISTER_ID": registerServiceCode,
            "HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE": "",
            "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE": ""
        };
        if (includeTeamCode) {
            row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"] = "";
        }
        return row;
    }

    /**
     * Read role codes from user data.
     * Tries the cached CONCATENATE column first; falls back to individual multiselect columns (1-n).
     * PRD: "roles should be checked from all roles columns in the user sheet 1-n"
     */
    private static getRoleCodes(rawData: any): string[] {
        const baseRole: string = rawData["HCM_ADMIN_CONSOLE_USER_ROLE"] || "";
        if (baseRole.trim()) {
            return baseRole.split(",").map((r: string) => r.trim().toUpperCase()).filter(Boolean);
        }
        // Fallback: read individual multiselect columns
        const codes: string[] = [];
        for (let i = 1; i <= 5; i++) {
            const val = rawData[`HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_${i}`];
            if (val && typeof val === "string" && val.trim()) {
                codes.push(val.trim().toUpperCase());
            }
        }
        return codes;
    }
}
