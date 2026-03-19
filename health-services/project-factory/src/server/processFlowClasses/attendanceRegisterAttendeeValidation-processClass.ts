import { RequestInfo } from "../config/models/requestInfoSchema";
import { getLocalizedName } from "../utils/campaignUtils";
import { SheetMap } from "../models/SheetMap";
import { logger } from "../utils/logger";
import { sheetDataRowStatuses } from "../config/constants";
import { httpRequest } from "../utils/request";
import config from "../config";

const SHEET_NAMES = [
    "HCM_REGISTER_WORKER_SHEET",
    "HCM_REGISTER_MARKER_SHEET",
    "HCM_REGISTER_APPROVER_SHEET"
];

// Strict regex: dd-MM-yyyy (dash only) OR dd/MM/yyyy (slash only) — no mixed separators
const DASH_DATE_REGEX = /^(\d{2})-(\d{2})-(\d{4})$/;
const SLASH_DATE_REGEX = /^(\d{2})\/(\d{2})\/(\d{4})$/;

/**
 * Validation process class for Attendance Register Attendee Mapping.
 * Validates date formats, date ranges against register start/end, and register ID presence.
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
            const val = row["HCM_ATTENDANCE_REGISTER_ID"];
            if (val && String(val).trim()) {
                registerId = String(val).trim();
                break;
            }
        }

        // Fetch register to get start/end date for range validation
        let registerStartDate: number | null = null;
        let registerEndDate: number | null = null;
        if (registerId) {
            const register = await this.fetchRegister(registerId, tenantId, resourceDetails?.requestInfo);
            if (register) {
                registerStartDate = register.startDate ?? null;
                registerEndDate = register.endDate ?? null;
            } else {
                // Mark all rows invalid if register not found
                for (const { row } of allRows) {
                    this.addError(row, "HCM_ATTENDANCE_ATTENDEE_REGISTER_NOT_FOUND", localizationMap);
                }
                return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
            }
        }

        // Validate each row
        for (const { row } of allRows) {
            const rowRegisterId = row["HCM_ATTENDANCE_REGISTER_ID"];
            if (!rowRegisterId || !String(rowRegisterId).trim()) {
                this.addError(row, "HCM_ATTENDANCE_ATTENDEE_REGISTER_NOT_FOUND", localizationMap);
            }

            // Pass raw cell values directly to parseDate to preserve Date object type
            const enrollmentDateRaw = row["HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE"];
            const deEnrollmentDateRaw = row["HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE"];

            if (enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "") {
                const parsed = this.parseDate(enrollmentDateRaw);
                if (parsed === null) {
                    this.addError(row, "HCM_ATTENDANCE_ATTENDEE_INVALID_DATE_FORMAT", localizationMap);
                } else if (registerStartDate !== null && registerEndDate !== null) {
                    if (parsed < registerStartDate || parsed > registerEndDate) {
                        this.addError(row, "HCM_ATTENDANCE_ATTENDEE_DATE_OUT_OF_RANGE", localizationMap);
                    }
                }
            }

            if (deEnrollmentDateRaw !== null && deEnrollmentDateRaw !== undefined && deEnrollmentDateRaw !== "") {
                const parsedDeEnroll = this.parseDate(deEnrollmentDateRaw);
                if (parsedDeEnroll === null) {
                    this.addError(row, "HCM_ATTENDANCE_ATTENDEE_INVALID_DATE_FORMAT", localizationMap);
                } else {
                    if (registerStartDate !== null && registerEndDate !== null) {
                        if (parsedDeEnroll < registerStartDate || parsedDeEnroll > registerEndDate) {
                            this.addError(row, "HCM_ATTENDANCE_ATTENDEE_DATE_OUT_OF_RANGE", localizationMap);
                        }
                    }
                    // De-enrollment date must not be before enrollment date
                    if (enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "") {
                        const parsedEnroll = this.parseDate(enrollmentDateRaw);
                        if (parsedEnroll !== null && parsedDeEnroll < parsedEnroll) {
                            this.addError(row, "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_BEFORE_ENROLLMENT", localizationMap);
                        }
                    }
                }
            }
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

        return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
    }

    private static async fetchRegister(registerId: string, tenantId: string, requestInfo?: RequestInfo): Promise<any> {
        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
            const RequestInfo = requestInfo || {};
            // ids is an array param per API spec
            const response = await httpRequest(url, { RequestInfo }, { tenantId, ids: [registerId] });
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
