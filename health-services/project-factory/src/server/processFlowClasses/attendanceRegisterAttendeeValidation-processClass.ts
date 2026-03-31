import { RequestInfo } from "../config/models/requestInfoSchema";
import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import {
    sheetDataRowStatuses,
    attendanceSheetNames,
    attendanceColumnKeys,
    attendanceStaffTypes,
    attendanceErrorKeys,
    attendanceCacheKeys,
} from "../config/constants";
import { httpRequest } from "../utils/request";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import config from "../config";

const WORKER_SHEET = attendanceSheetNames.WORKER;
const MARKER_SHEET = attendanceSheetNames.MARKER;
const APPROVER_SHEET = attendanceSheetNames.APPROVER;

const SHEET_NAMES = [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET];

// Strict regex: dd-MM-yyyy (dash only) OR dd/MM/yyyy (slash only) — no mixed separators
const DASH_DATE_REGEX = /^(\d{2})-(\d{2})-(\d{4})$/;
const SLASH_DATE_REGEX = /^(\d{2})\/(\d{2})\/(\d{4})$/;

/**
 * Validation process class for Attendance Register Attendee Mapping.
 * Validates date formats, date ranges, register ID presence, and truth-table business rules
 * (date immutability, enrollment required for new records).
 */
export class TemplateClass {
    static async process(
        resourceDetails: any,
        wholeSheetData: any,
        localizationMap: Record<string, string>,
        templateConfig: any
    ): Promise<SheetMap> {
        logger.info("Validating attendance register attendee file...");

        const tenantId = resourceDetails?.tenantId;

        // Collect all rows from all sheets
        const allRows: { row: any; sheetName: string }[] = [];
        for (const name of SHEET_NAMES) {
            const localizedKey = getLocalizedName(name, localizationMap);
            const rows = wholeSheetData[localizedKey] || [];
            for (const row of rows) {
                allRows.push({ row, sheetName: name });
            }
        }

        // Find registerId from first non-empty row
        let registerId: string | null = null;
        for (const { row } of allRows) {
            const val = row[attendanceColumnKeys.REGISTER_ID];
            if (val && String(val).trim()) {
                registerId = String(val).trim();
                break;
            }
        }

        // Fetch campaign details to get campaignNumber for register ownership validation
        const campaignResponse = await searchProjectTypeCampaignService({
            tenantId,
            ids: [resourceDetails?.campaignId],
        });
        const campaignNumber = campaignResponse?.CampaignDetails?.[0]?.campaignNumber;

        // Fetch register to get start/end date and existing attendees/staff
        let registerStartDate: number | null = null;
        let registerEndDate: number | null = null;
        let attendeesMap: Map<string, any> = new Map();
        let staffMap: Map<string, any> = new Map();

        if (registerId) {
            const register = await this.fetchRegister(registerId, tenantId, resourceDetails?.requestInfo);
            if (register) {
                // Validate that register belongs to the current campaign
                if (campaignNumber && register.campaignNumber && register.campaignNumber !== campaignNumber) {
                    for (const { row } of allRows) {
                        this.addError(row, attendanceErrorKeys.REGISTER_BELONGS_TO_DIFFERENT_CAMPAIGN, localizationMap);
                    }
                    return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
                }
                registerStartDate = register.startDate ?? null;
                registerEndDate = register.endDate ?? null;

                // Build attendeesMap and staffMap from register response for truth-table validation
                for (const attendee of register.attendees || []) {
                    if (attendee.individualId) attendeesMap.set(attendee.individualId, attendee);
                }
                for (const staff of register.staff || []) {
                    const type = staff.staffType || attendanceStaffTypes.OWNER;
                    if (staff.userId) staffMap.set(`${staff.userId}_${type}`, staff);
                }
            } else {
                // Mark all rows invalid if register not found
                for (const { row } of allRows) {
                    this.addError(row, attendanceErrorKeys.REGISTER_NOT_FOUND, localizationMap);
                }
                return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
            }
        }

        // Resolve HRMS usernames → individualIds for truth-table validation
        const usernames: string[] = [];
        for (const { row } of allRows) {
            const username = this.getCellAsString(row[attendanceColumnKeys.USERNAME]);
            if (username && !usernames.includes(username)) {
                usernames.push(username);
            }
        }
        const usernameToIndividualId = await this.resolveIndividualIds(usernames, tenantId, resourceDetails?.requestInfo);

        // Validate each row — structural + truth-table business rules
        for (const { row, sheetName } of allRows) {
            const rowRegisterId = row[attendanceColumnKeys.REGISTER_ID];
            if (!rowRegisterId || !String(rowRegisterId).trim()) {
                this.addError(row, attendanceErrorKeys.REGISTER_NOT_FOUND, localizationMap);
            }

            // Pass raw cell values directly to parseDate to preserve Date object type
            const enrollmentDateRaw = row[attendanceColumnKeys.ENROLLMENT_DATE];
            const deEnrollmentDateRaw = row[attendanceColumnKeys.DEENROLLMENT_DATE];

            if (enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "") {
                const parsed = this.parseDate(enrollmentDateRaw);
                if (parsed === null) {
                    this.addError(row, attendanceErrorKeys.INVALID_DATE_FORMAT, localizationMap);
                } else if (registerStartDate !== null && registerEndDate !== null) {
                    if (parsed < registerStartDate || parsed > registerEndDate) {
                        this.addError(row, attendanceErrorKeys.DATE_OUT_OF_RANGE, localizationMap);
                    }
                }
            }

            if (deEnrollmentDateRaw !== null && deEnrollmentDateRaw !== undefined && deEnrollmentDateRaw !== "") {
                const parsedDeEnroll = this.parseDate(deEnrollmentDateRaw);
                if (parsedDeEnroll === null) {
                    this.addError(row, attendanceErrorKeys.INVALID_DATE_FORMAT, localizationMap);
                } else {
                    if (registerStartDate !== null && registerEndDate !== null) {
                        if (parsedDeEnroll < registerStartDate || parsedDeEnroll > registerEndDate) {
                            this.addError(row, attendanceErrorKeys.DATE_OUT_OF_RANGE, localizationMap);
                        }
                    }
                    // De-enrollment date must not be before enrollment date
                    if (enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "") {
                        const parsedEnroll = this.parseDate(enrollmentDateRaw);
                        if (parsedEnroll !== null && parsedDeEnroll < parsedEnroll) {
                            this.addError(row, attendanceErrorKeys.DEENROLLMENT_BEFORE_ENROLLMENT, localizationMap);
                        }
                    }
                }
            }

            // Truth-table business validation — skip if row already has structural errors
            if (row["#status#"] === sheetDataRowStatuses.INVALID) continue;

            const username = this.getCellAsString(row[attendanceColumnKeys.USERNAME]);
            const individualId = username ? usernameToIndividualId.get(username) : null;
            // Skip truth-table validation if we can't resolve the user (HRMS error is non-blocking here;
            // the process class will catch unresolved users during processing)
            if (!individualId) continue;

            const isWorkerSheet = sheetName === WORKER_SHEET;
            const enrollmentDateEpoch = (enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "")
                ? this.parseDate(enrollmentDateRaw) : null;
            const deEnrollmentDateEpoch = (deEnrollmentDateRaw !== null && deEnrollmentDateRaw !== undefined && deEnrollmentDateRaw !== "")
                ? this.parseDate(deEnrollmentDateRaw) : null;
            const teamCode = isWorkerSheet ? this.getCellAsString(row[attendanceColumnKeys.TEAM_CODE]) : "";

            // Look up existing record
            let existing: any = null;
            if (isWorkerSheet) {
                existing = attendeesMap.get(individualId);
            } else {
                const staffType = sheetName === MARKER_SHEET ? attendanceStaffTypes.OWNER : attendanceStaffTypes.APPROVER;
                existing = staffMap.get(`${individualId}_${staffType}`);
            }

            this.validateTruthTableRules(existing, enrollmentDateEpoch, deEnrollmentDateEpoch, teamCode, row, localizationMap);
        }

        // Populate sheetErrors in resourceDetails for downstream process class
        const invalidRows = allRows.filter(({ row }) => row["#status#"] === sheetDataRowStatuses.INVALID);
        if (invalidRows.length > 0) {
            if (!resourceDetails.additionalDetails) resourceDetails.additionalDetails = {};
            resourceDetails.additionalDetails.sheetErrors = invalidRows.map(({ row, sheetName }) => ({
                sheetName,
                errorDetails: row["#errorDetails#"]
            }));
        }

        // Cache resolved data for process class reuse
        if (!resourceDetails.additionalDetails) resourceDetails.additionalDetails = {};
        resourceDetails.additionalDetails[attendanceCacheKeys.RESOLVED_INDIVIDUAL_IDS] = Object.fromEntries(usernameToIndividualId);

        return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
    }

    /**
     * Apply truth-table INVALID rules based on existing record state.
     * Covers: A2/A4/A7, B3/B6/B10/B13, C3/C5/C7-C9/C13/C14, D3, E3/E6, F3/F5/F7/F8.
     */
    private static validateTruthTableRules(
        existing: any,
        enrollmentDateEpoch: number | null,
        deEnrollmentDateEpoch: number | null,
        teamCode: string,
        row: any,
        localizationMap: Record<string, string>
    ): void {
        if (!existing) {
            // NEW record — Section A (attendee) or Section D (staff)
            if (!enrollmentDateEpoch && !deEnrollmentDateEpoch && !teamCode) {
                // A1/D1: skip — no validation error
                return;
            }
            if (!enrollmentDateEpoch) {
                // A2/A4/A7/D3: INVALID — enrollment date required
                this.addError(row, attendanceErrorKeys.ENROLLMENT_DATE_REQUIRED, localizationMap);
            }
            return;
        }

        if (existing.denrollmentDate) {
            // DE-ENROLLED record — Section C (attendee) or Section F (staff)
            // Enrollment date checked FIRST (per truth table C9)
            if (enrollmentDateEpoch !== null && existing.enrollmentDate != null
                && enrollmentDateEpoch !== existing.enrollmentDate) {
                // C3/C7/C9/C13/F3/F7
                this.addError(row, attendanceErrorKeys.CANNOT_CHANGE_ENROLLMENT_DATE, localizationMap);
                return;
            }
            if (deEnrollmentDateEpoch !== null && deEnrollmentDateEpoch !== existing.denrollmentDate) {
                // C5/C8/C14/F5/F8
                this.addError(row, attendanceErrorKeys.CANNOT_CHANGE_DEENROLLMENT_DATE, localizationMap);
            }
            return;
        }

        // ACTIVE record — Section B (attendee) or Section E (staff)
        if (enrollmentDateEpoch !== null && existing.enrollmentDate != null
            && enrollmentDateEpoch !== existing.enrollmentDate) {
            // B3/B6/B10/B13/E3/E6
            this.addError(row, attendanceErrorKeys.CANNOT_CHANGE_ENROLLMENT_DATE, localizationMap);
        }
    }

    /**
     * Resolve usernames to individualIds via HRMS employee search.
     */
    private static async resolveIndividualIds(
        usernames: string[],
        tenantId: string,
        requestInfo?: RequestInfo
    ): Promise<Map<string, string>> {
        const result = new Map<string, string>();
        if (!usernames.length) return result;

        const searchUrl = config.host.hrmsHost + config.paths.hrmsEmployeeSearch;
        const rootTenantId = tenantId.split(".")[0];
        const parallelLimit = Math.min(config.hrms.hrmsParallelSearchLimit, usernames.length);

        for (let i = 0; i < usernames.length; i += parallelLimit) {
            const window = usernames.slice(i, i + parallelLimit);
            const responses = await Promise.all(
                window.map(async (username) => {
                    const params = { tenantId: rootTenantId, limit: 2, offset: 0, codes: username };
                    try {
                        const response = await httpRequest(searchUrl, { RequestInfo: requestInfo || {} }, params);
                        return response?.Employees || [];
                    } catch (err: any) {
                        logger.warn(`HRMS search failed for user ${username}: ${err?.message}`);
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

        logger.info(`Resolved ${result.size}/${usernames.length} usernames via HRMS`);
        return result;
    }

    private static getCellAsString(value: any): string {
        if (value === null || value === undefined) return "";
        return String(value).trim();
    }

    private static async fetchRegister(registerId: string, tenantId: string, requestInfo?: RequestInfo): Promise<any> {
        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
            const RequestInfo = requestInfo || {};
            // ids is an array param per API spec
            const response = await httpRequest(url, { RequestInfo }, { tenantId, serviceCode: registerId });
            return response?.attendanceRegister?.[0] || null;
        } catch (err: any) {
            logger.warn(`Failed to fetch register ${registerId}: ${err?.message}`);
            return null;
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
        if (typeof value === "number") {
            return value;
        }
        const str = String(value).trim();
        const match = DASH_DATE_REGEX.exec(str) || SLASH_DATE_REGEX.exec(str);
        if (!match) return null;
        const day = parseInt(match[1], 10);
        const month = parseInt(match[2], 10) - 1; // JS months are 0-indexed
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

    private static addError(row: any, errorKey: string, localizationMap: Record<string, string>): void {
        const msg = getLocalizedName(errorKey, localizationMap) || errorKey;
        row["#status#"] = sheetDataRowStatuses.INVALID;
        if (row["#errorDetails#"]) {
            row["#errorDetails#"] = `${row["#errorDetails#"]}. ${msg}`;
        } else {
            row["#errorDetails#"] = msg;
        }
    }

    private static buildSheetMap(sheetNames: string[], wholeSheetData: any, localizationMap: Record<string, string>): SheetMap {
        const sheetMap: SheetMap = {};
        for (const name of sheetNames) {
            const localizedKey = getLocalizedName(name, localizationMap);
            sheetMap[name] = { data: wholeSheetData[localizedKey] || [], dynamicColumns: null };
        }
        return sheetMap;
    }
}
