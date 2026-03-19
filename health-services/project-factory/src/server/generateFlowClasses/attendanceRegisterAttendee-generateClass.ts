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
        const hierarchyType: string = campaign?.hierarchyType || "";

        if (!localityCode) {
            throwError("CAMPAIGN", 400, "ATTENDANCE_REGISTER_LOCALITY_MISSING",
                `Attendance register ${registerId} has no localityCode set`);
        }
        if (!hierarchyType) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_HIERARCHY_MISSING",
                `Campaign ${campaignId} has no hierarchyType set`);
        }

        // Build boundary descendant set for worker filtering (boundary on or below register locality)
        const descendantBoundaryCodes = await this.getBoundaryDescendantCodes(tenantId, hierarchyType, localityCode);

        // Fetch all completed user credentials for this campaign
        const users = await getRelatedDataWithCampaign("user", campaign?.campaignNumber, tenantId, dataRowStatuses.completed);
        logger.info(`Fetched ${users.length} users for campaign ${campaignId}`);

        // Classify users and filter by boundary
        const workerRows: any[] = [];
        const markerRows: any[] = [];
        const approverRows: any[] = [];

        for (const userEntry of users) {
            const rawData = userEntry?.data || {};
            if (!rawData || !rawData["UserName"]) continue;

            // PRD: roles should be checked from all role columns 1-n (multiselect columns)
            // HCM_ADMIN_CONSOLE_USER_ROLE is a CONCATENATE formula — result may not be cached in DB
            // Fallback: collect from individual multiselect columns
            const roleCodes = this.getRoleCodes(rawData);

            const sheet = this.classifyUserToSheet(roleCodes);
            if (!sheet) continue;

            const boundaryCode = rawData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY"] || rawData["HCM_ADMIN_CONSOLE_BOUNDARY_CODE"] || "";

            // Filter by boundary
            if (sheet === WORKER_SHEET) {
                // Workers: boundary must be in or below register locality
                if (!descendantBoundaryCodes.has(boundaryCode)) continue;
            } else {
                // Markers and Approvers: boundary must exactly match register locality
                if (boundaryCode !== localityCode) continue;
            }

            const row = this.buildRowData(rawData, registerId, localizationMap, sheet === WORKER_SHEET);
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
     * Collect all boundary codes in the subtree rooted at localityCode (inclusive).
     * Returns a Set<string> for O(1) membership checks.
     */
    private static async getBoundaryDescendantCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string
    ): Promise<Set<string>> {
        const response = await searchBoundaryRelationshipData(tenantId, hierarchyType, true, false, false, localityCode);
        const root = response?.TenantBoundary?.[0]?.boundary?.[0];
        const codes = new Set<string>();
        this.collectBoundaryCodes(root, codes);
        return codes;
    }

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
    private static buildRowData(rawData: any, registerId: string, localizationMap: any, includeTeamCode: boolean): any {
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
            "HCM_ATTENDANCE_REGISTER_ID": registerId,
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
