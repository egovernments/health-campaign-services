import { RequestInfo } from "../config/models/requestInfoSchema";
import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { sheetDataRowStatuses } from "../config/constants";
import { validateResourceDetailsBeforeProcess } from "../utils/sheetManageUtils";
import { httpRequest } from "../utils/request";
import config from "../config";

const WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
const MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
const APPROVER_SHEET = "HCM_REGISTER_APPROVER_SHEET";

const SHEET_NAMES = [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET];

// Strict regex: dd-MM-yyyy (dash only) OR dd/MM/yyyy (slash only) — no mixed separators
const DASH_DATE_REGEX = /^(\d{2})-(\d{2})-(\d{4})$/;
const SLASH_DATE_REGEX = /^(\d{2})\/(\d{2})\/(\d{4})$/;


/**
 * Process class for Attendance Register Attendee Mapping.
 * Resolves individualIds via HRMS, then creates/updates/deletes attendees and staff
 * with idempotency checks.
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        await validateResourceDetailsBeforeProcess("attendanceRegisterAttendeeValidation", resourceDetails, localizationMap);
        logger.info("Processing attendance register attendee file...");

        const tenantId = resourceDetails?.tenantId;
        const requestInfo = resourceDetails?.requestInfo || {};

        // Collect all rows indexed by sheet
        const sheetRows: Map<string, any[]> = new Map();
        for (const name of SHEET_NAMES) {
            const localizedKey = getLocalizedName(name, localizationMap);
            sheetRows.set(name, wholeSheetData[localizedKey] || []);
        }

        // Collect all unique usernames across all sheets
        const usernameToRows: Map<string, Array<{ row: any; sheetName: string }>> = new Map();
        for (const sheetName of SHEET_NAMES) {
            const rows = sheetRows.get(sheetName) || [];
            for (const row of rows) {
                const username = this.getCellAsString(row["UserName"]);
                if (!username) {
                    row["#status#"] = sheetDataRowStatuses.INVALID;
                    row["#errorDetails#"] = getLocalizedName("HCM_ATTENDANCE_ATTENDEE_USER_NOT_FOUND", localizationMap) || "User not found in HRMS";
                    continue;
                }
                if (!usernameToRows.has(username)) usernameToRows.set(username, []);
                usernameToRows.get(username)!.push({ row, sheetName });
            }
        }

        // Resolve individualIds via HRMS batch search
        const usernames = Array.from(usernameToRows.keys());
        const rootTenantId = tenantId.split(".")[0];
        const usernameToIndividualId = await this.resolveIndividualIds(usernames, rootTenantId, requestInfo);

        // Mark rows invalid where HRMS lookup failed
        Array.from(usernameToRows.entries()).forEach(([username, rowEntries]) => {
            if (!usernameToIndividualId.has(username)) {
                for (const { row } of rowEntries) {
                    row["#status#"] = sheetDataRowStatuses.INVALID;
                    row["#errorDetails#"] = getLocalizedName("HCM_ATTENDANCE_ATTENDEE_USER_NOT_FOUND", localizationMap) || "User not found in HRMS";
                }
            }
        });

        // Collect unique register service codes
        const registerServiceCodes = new Set<string>();
        for (const name of SHEET_NAMES) {
            for (const row of sheetRows.get(name) || []) {
                if (row["#status#"] === sheetDataRowStatuses.INVALID) continue;
                const rid = this.getCellAsString(row["HCM_ATTENDANCE_REGISTER_ID"]);
                if (rid) registerServiceCodes.add(rid);
            }
        }

        // Fetch registers with existing attendees/staff
        const registerDataMap = await this.fetchRegistersWithEnrollments(Array.from(registerServiceCodes), tenantId, requestInfo);

        // Per-row idempotency decision — collect operations with their row references
        // Row statuses are set AFTER API calls succeed to keep them accurate
        const attendeesToCreate: Array<{ payload: any; row: any }> = [];
        const attendeesToUpdate: Array<{ payload: any; row: any }> = [];
        const staffToCreate: Array<{ payload: any; row: any }> = [];
        const staffToUpdate: Array<{ payload: any; row: any }> = [];

        for (const sheetName of SHEET_NAMES) {
            const rows = sheetRows.get(sheetName) || [];
            const isWorkerSheet = sheetName === WORKER_SHEET;
            const isMarkerSheet = sheetName === MARKER_SHEET;

            for (const row of rows) {
                if (row["#status#"] === sheetDataRowStatuses.INVALID) continue;

                const username = this.getCellAsString(row["UserName"]);
                const registerId = this.getCellAsString(row["HCM_ATTENDANCE_REGISTER_ID"]);
                const individualId = usernameToIndividualId.get(username) || "";
                // Pass raw cell values directly to parseDate to preserve Date object type
                const enrollmentDateRaw = row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"];
                const deEnrollmentDateRaw = row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"];
                const teamCode = isWorkerSheet ? this.getCellAsString(row["HCM_ATTENDANCE_ATTENDEE_TEAM_CODE"]) : undefined;

                if (!individualId || !registerId) {
                    row["#status#"] = sheetDataRowStatuses.SKIPPED;
                    continue;
                }

                const hasEnrollmentDate = enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "";
                const hasDeEnrollmentDate = deEnrollmentDateRaw !== null && deEnrollmentDateRaw !== undefined && deEnrollmentDateRaw !== "";
                const enrollmentDateEpoch = hasEnrollmentDate ? this.parseDate(enrollmentDateRaw) : null;
                const deEnrollmentDateEpoch = hasDeEnrollmentDate ? this.parseDate(deEnrollmentDateRaw) : null;

                const registerData = registerDataMap.get(registerId);

                if (isWorkerSheet) {
                    const attendeesMap: Map<string, any> = registerData?.attendeesMap || new Map();
                    const existing = attendeesMap.get(individualId);
                    this.collectAttendeeOperation(
                        existing, enrollmentDateEpoch, deEnrollmentDateEpoch, teamCode || "",
                        tenantId, registerId, individualId, row,
                        attendeesToCreate, attendeesToUpdate
                    );
                } else {
                    const staffType = isMarkerSheet ? "OWNER" : "APPROVER";
                    const staffMap: Map<string, any> = registerData?.staffMap || new Map();
                    const staffKey = `${individualId}_${staffType}`;
                    const existing = staffMap.get(staffKey);
                    this.collectStaffOperation(
                        existing, enrollmentDateEpoch, deEnrollmentDateEpoch,
                        tenantId, registerId, individualId, staffType, row,
                        staffToCreate, staffToUpdate
                    );
                }
            }
        }

        // Execute batched API calls in sequence: creates → updates
        // batchApiCall sets INVALID on failed rows; success status set only on rows without status
        if (attendeesToCreate.length > 0) {
            await this.batchApiCall(attendeesToCreate, config.paths.attendanceAttendeeCreate, "attendees", requestInfo);
            attendeesToCreate.filter(e => !e.row["#status#"]).forEach(e => { e.row["#status#"] = sheetDataRowStatuses.CREATED; });
            logger.info(`Created ${attendeesToCreate.length} attendees`);
        }
        if (staffToCreate.length > 0) {
            await this.batchApiCall(staffToCreate, config.paths.attendanceStaffCreate, "staff", requestInfo);
            staffToCreate.filter(e => !e.row["#status#"]).forEach(e => { e.row["#status#"] = sheetDataRowStatuses.CREATED; });
            logger.info(`Created ${staffToCreate.length} staff`);
        }
        if (attendeesToUpdate.length > 0) {
            await this.batchApiCall(attendeesToUpdate, config.paths.attendanceAttendeeUpdate, "attendees", requestInfo);
            attendeesToUpdate.filter(e => !e.row["#status#"]).forEach(e => { e.row["#status#"] = sheetDataRowStatuses.UPDATED; });
            logger.info(`Updated ${attendeesToUpdate.length} attendees`);
        }
        if (staffToUpdate.length > 0) {
            await this.batchApiCall(staffToUpdate, config.paths.attendanceStaffUpdate, "staff", requestInfo);
            staffToUpdate.filter(e => !e.row["#status#"]).forEach(e => { e.row["#status#"] = sheetDataRowStatuses.UPDATED; });
            logger.info(`Updated ${staffToUpdate.length} staff`);
        }

        // Build SheetMap with all processed rows
        const sheetMap: SheetMap = {};
        for (const name of SHEET_NAMES) {
            sheetMap[name] = { data: sheetRows.get(name) || [], dynamicColumns: null };
        }
        return sheetMap;
    }

    /**
     * Determine attendee operation and collect into the appropriate list.
     * All changed fields for an existing active attendee are merged into one _update payload.
     * Row status is NOT set here — caller sets it after API calls succeed.
     */
    private static collectAttendeeOperation(
        existing: any,
        enrollmentDateEpoch: number | null,
        deEnrollmentDateEpoch: number | null,
        teamCode: string,
        tenantId: string,
        registerId: string,
        individualId: string,
        row: any,
        attendeesToCreate: Array<{ payload: any; row: any }>,
        attendeesToUpdate: Array<{ payload: any; row: any }>
    ): void {
        if (!existing) {
            if (!enrollmentDateEpoch && !deEnrollmentDateEpoch) {
                row["#status#"] = sheetDataRowStatuses.SKIPPED;
                return;
            }
            const payload: any = {
                registerId,
                individualId,
                enrollmentDate: enrollmentDateEpoch || deEnrollmentDateEpoch!,
                tenantId
            };
            if (teamCode) payload.tag = teamCode;
            if (deEnrollmentDateEpoch) payload.denrollmentDate = deEnrollmentDateEpoch;
            attendeesToCreate.push({ payload, row });
            return;
        }

        // Existing attendee — already de-enrolled, no further updates
        if (existing.denrollmentDate) {
            row["#status#"] = sheetDataRowStatuses.EXISTING;
            return;
        }

        // Active attendee — collect ALL changed fields into one update payload
        const updatePayload: any = { id: existing.id, registerId, individualId, tenantId };
        let hasChanges = false;

        if (enrollmentDateEpoch !== null && enrollmentDateEpoch !== existing.enrollmentDate) {
            updatePayload.enrollmentDate = enrollmentDateEpoch;
            hasChanges = true;
        }
        if (deEnrollmentDateEpoch !== null) {
            updatePayload.denrollmentDate = deEnrollmentDateEpoch;
            hasChanges = true;
        }
        if (teamCode && teamCode !== (existing.tag || "")) {
            updatePayload.tag = teamCode;
            hasChanges = true;
        }

        if (hasChanges) {
            attendeesToUpdate.push({ payload: updatePayload, row });
        } else {
            row["#status#"] = sheetDataRowStatuses.EXISTING;
        }
    }

    /**
     * Determine staff operation and collect into the appropriate list.
     * All changed fields for an existing active staff are merged into one _update payload.
     * Row status is NOT set here — caller sets it after API calls succeed.
     */
    private static collectStaffOperation(
        existing: any,
        enrollmentDateEpoch: number | null,
        deEnrollmentDateEpoch: number | null,
        tenantId: string,
        registerId: string,
        individualId: string,
        staffType: string,
        row: any,
        staffToCreate: Array<{ payload: any; row: any }>,
        staffToUpdate: Array<{ payload: any; row: any }>
    ): void {
        if (!existing) {
            if (!enrollmentDateEpoch && !deEnrollmentDateEpoch) {
                row["#status#"] = sheetDataRowStatuses.SKIPPED;
                return;
            }
            const payload: any = {
                registerId,
                userId: individualId,
                enrollmentDate: enrollmentDateEpoch || deEnrollmentDateEpoch!,
                tenantId,
                staffType
            };
            if (deEnrollmentDateEpoch) payload.denrollmentDate = deEnrollmentDateEpoch;
            staffToCreate.push({ payload, row });
            return;
        }

        // Existing staff — already de-enrolled, no further updates
        if (existing.denrollmentDate) {
            row["#status#"] = sheetDataRowStatuses.EXISTING;
            return;
        }

        // Active staff — collect ALL changed fields into one update payload
        const updatePayload: any = { id: existing.id, registerId, userId: individualId, tenantId, staffType: existing.staffType || staffType };
        let hasChanges = false;

        if (enrollmentDateEpoch !== null && enrollmentDateEpoch !== existing.enrollmentDate) {
            updatePayload.enrollmentDate = enrollmentDateEpoch;
            hasChanges = true;
        }
        if (deEnrollmentDateEpoch !== null) {
            updatePayload.denrollmentDate = deEnrollmentDateEpoch;
            hasChanges = true;
        }
        if (staffType !== (existing.staffType || "")) {
            updatePayload.staffType = staffType;
            hasChanges = true;
        }

        if (hasChanges) {
            staffToUpdate.push({ payload: updatePayload, row });
        } else {
            row["#status#"] = sheetDataRowStatuses.EXISTING;
        }
    }

    /**
     * Resolve individualIds from usernames via HRMS employee search.
     * Fires one request per username in parallel (up to hrmsParallelSearchLimit concurrency).
     */
    private static async resolveIndividualIds(
        usernames: string[],
        rootTenantId: string,
        requestInfo: RequestInfo
    ): Promise<Map<string, string>> {
        const result = new Map<string, string>();
        if (!usernames.length) return result;

        const searchUrl = config.host.hrmsHost + config.paths.hrmsEmployeeSearch;
        const searchBody = { RequestInfo: requestInfo };
        const parallelLimit = Math.min(config.hrms.hrmsParallelSearchLimit, usernames.length);

        for (let i = 0; i < usernames.length; i += parallelLimit) {
            const window = usernames.slice(i, i + parallelLimit);
            const responses = await Promise.all(
                window.map(async (username) => {
                    const params = { tenantId: rootTenantId, limit: 2, offset: 0, codes: username };
                    try {
                        const response = await httpRequest(searchUrl, searchBody, params);
                        return response?.Employees || [];
                    } catch (err: any) {
                        logger.error(`HRMS search failed for user ${username}: ${err?.message}`);
                        return [];
                    }
                })
            );
            for (const employees of responses) {
                for (const emp of employees) {
                    if (emp?.code && emp?.user?.uuid) {
                        result.set(emp.code, emp.user.uuid);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Fetch attendance registers and build attendeesMap and staffMap for each.
     */
    private static async fetchRegistersWithEnrollments(
        registerServiceCodes: string[],
        tenantId: string,
        requestInfo: RequestInfo
    ): Promise<Map<string, { register: any; attendeesMap: Map<string, any>; staffMap: Map<string, any> }>> {
        const result = new Map<string, { register: any; attendeesMap: Map<string, any>; staffMap: Map<string, any> }>();
        if (!registerServiceCodes.length) return result;

        // serviceCode param accepts one value at a time — search in parallel batches
        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo;
        const parallelLimit = config.attendanceRegister.serviceCodeParallelSearchLimit;

        for (let i = 0; i < registerServiceCodes.length; i += parallelLimit) {
            const window = registerServiceCodes.slice(i, i + parallelLimit);
            const responses = await Promise.all(
                window.map(code =>
                    httpRequest(url, { RequestInfo }, { tenantId, serviceCode: code }).catch(err => {
                        logger.warn(`Error fetching register for serviceCode ${code}: ${err?.message}`);
                        return null;
                    })
                )
            );
            for (const response of responses) {
                for (const register of response?.attendanceRegister || []) {
                    const attendeesMap = new Map<string, any>();
                    for (const attendee of register.attendees || []) {
                        if (attendee.individualId) attendeesMap.set(attendee.individualId, attendee);
                    }
                    const staffMap = new Map<string, any>();
                    for (const staff of register.staff || []) {
                        const type = staff.staffType || "OWNER";
                        if (staff.userId) staffMap.set(`${staff.userId}_${type}`, staff);
                    }
                    result.set(register.serviceCode, { register, attendeesMap, staffMap });
                }
            }
        }
        return result;
    }

    /**
     * Execute items in batches of config.attendanceRegister.batchSize against an attendance API endpoint.
     * On batch failure, sets INVALID + errorDetails on each row in the failed batch — does NOT throw.
     * All attendance endpoints use 'RequestInfo' (capital R) per API contract.
     */
    private static async batchApiCall(
        items: Array<{ payload: any; row: any }>,
        urlPath: string,
        bodyKey: string,
        requestInfo: RequestInfo
    ): Promise<void> {
        const url = config.host.attendanceHost + urlPath;
        for (let i = 0; i < items.length; i += config.attendanceRegister.batchSize) {
            const batchItems = items.slice(i, i + config.attendanceRegister.batchSize);
            const body: any = { [bodyKey]: batchItems.map(e => e.payload), RequestInfo: requestInfo };
            try {
                await httpRequest(url, body);
            } catch (err: any) {
                logger.error(`Attendance API call failed for ${urlPath} batch ${i}: ${err?.message}`);
                for (const item of batchItems) {
                    item.row["#status#"] = sheetDataRowStatuses.INVALID;
                    item.row["#errorDetails#"] = err?.message || "API call failed";
                }
            }
        }
    }

    /**
     * Parse date string in dd-MM-yyyy or dd/MM/yyyy format (strict — no mixed separators).
     * Returns UTC epoch ms or null if invalid format or impossible date.
     */
    private static parseDate(value: any): number | null {
        // Handle Date objects (ExcelJS may parse date cells as Date)
        if (value instanceof Date) {
            if (isNaN(value.getTime())) return null;
            return Date.UTC(value.getFullYear(), value.getMonth(), value.getDate());
        }
        // Handle epoch numbers passed directly
        if (typeof value === "number") return value;
        const str = String(value).trim();
        const match = DASH_DATE_REGEX.exec(str) || SLASH_DATE_REGEX.exec(str);
        if (!match) return null;
        const day = parseInt(match[1], 10);
        const month = parseInt(match[2], 10) - 1;
        const year = parseInt(match[3], 10);
        // Use UTC to avoid server-timezone-dependent boundary comparisons
        const ts = Date.UTC(year, month, day);
        const check = new Date(ts);
        // Validate the date is real (catches impossible dates like 31-02-2024)
        if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month || check.getUTCDate() !== day) {
            return null;
        }
        return ts;
    }

    private static getCellAsString(value: any): string {
        if (value === null || value === undefined || value === "") return "";
        if (value instanceof Date) return value.toISOString();
        return String(value).trim();
    }
}
