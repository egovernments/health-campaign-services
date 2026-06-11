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
import { httpRequest, defaultheader } from "../utils/request";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import config from "../config";

const WORKER_SHEET = attendanceSheetNames.WORKER;
const MARKER_SHEET = attendanceSheetNames.MARKER;
const APPROVER_SHEET = attendanceSheetNames.APPROVER;

const SHEET_NAMES = [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET];

const MDMS_EXCEL_INGESTION_GENERATE_SCHEMA = "HCM-ADMIN-CONSOLE.excelIngestionGenerate";
const MDMS_ATTENDANCE_REGISTER_ATTENDEE_CONFIG_NAME = "attendanceRegisterAttendee";

// Strict regex: dd-MM-yyyy (dash only) OR dd/MM/yyyy (slash only) — no mixed separators
const DASH_DATE_REGEX = /^(\d{2})-(\d{2})-(\d{4})$/;
const SLASH_DATE_REGEX = /^(\d{2})\/(\d{2})\/(\d{4})$/;

// Excel serial date conversion constants
const EXCEL_EPOCH_MS = Date.UTC(1899, 11, 30); // Excel epoch: Dec 30, 1899
const MS_PER_DAY = 86_400_000;
const EXCEL_SERIAL_THRESHOLD = 100_000_000; // Below = Excel serial, above = epoch ms
const ISO_DATE_PREFIX_REGEX = /^\d{4}-\d{2}-\d{2}/; // Matches YYYY-MM-DD start

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
        const tenantId = resourceDetails?.tenantId;
        logger.info(`Validating attendance register attendee file — tenantId=${tenantId}, campaignId=${resourceDetails?.campaignId}`);

        // Fetch MDMS config once — role codes whose users may skip cross-register enrollment check
        const multiRegisterAllowedRoles = await this.fetchMultiRegisterAllowedRoles(tenantId, resourceDetails?.requestInfo);
        logger.info(`Multi-register allowed roles for tenant ${tenantId}: [${Array.from(multiRegisterAllowedRoles).join(", ")}]`);

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
        logger.info(`Attendee validation context — campaignNumber=${campaignNumber}, registerId=${registerId}, totalRows=${allRows.length}`);

        // Fetch register to get start/end date only (attendees/staff fetched separately)
        let registerStartDate: number | null = null;
        let registerEndDate: number | null = null;
        // UUID of the register — attendance API returns entry.registerId as UUID, not serviceCode
        let registerUuid: string | null = null;

        if (registerId) {
            const register = await this.fetchRegister(registerId, tenantId, resourceDetails?.requestInfo);
            if (register) {
                // Validate that register belongs to the current campaign
                if (campaignNumber && register.campaignNumber && register.campaignNumber !== campaignNumber) {
                    logger.warn(`Register ${registerId} belongs to campaign ${register.campaignNumber}, not current campaign ${campaignNumber} — marking all ${allRows.length} rows invalid`);
                    for (const { row } of allRows) {
                        this.addError(row, attendanceErrorKeys.REGISTER_BELONGS_TO_DIFFERENT_CAMPAIGN, localizationMap);
                    }
                    return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
                }
                registerStartDate = register.startDate ?? null;
                registerEndDate = register.endDate ?? null;
                registerUuid = register.id ?? null;
                logger.debug(`Register ${registerId} — uuid=${registerUuid}, startDate=${registerStartDate} (${registerStartDate ? new Date(registerStartDate).toISOString() : 'null'}), endDate=${registerEndDate} (${registerEndDate ? new Date(registerEndDate).toISOString() : 'null'})`);
            } else {
                // Mark all rows invalid if register not found
                logger.warn(`Register ${registerId} not found — marking all ${allRows.length} rows invalid`);
                for (const { row } of allRows) {
                    this.addError(row, attendanceErrorKeys.REGISTER_NOT_FOUND, localizationMap);
                }
                return this.buildSheetMap(SHEET_NAMES, wholeSheetData, localizationMap);
            }
        }

        // Resolve HRMS usernames → individualIds
        const usernames: string[] = [];
        for (const { row } of allRows) {
            const username = this.getCellAsString(row[attendanceColumnKeys.USERNAME]);
            if (username && !usernames.includes(username)) {
                usernames.push(username);
            }
        }
        const { usernameToIndividualId, usernameToRoles } = await this.resolveIndividualIds(usernames, tenantId, resourceDetails?.requestInfo);
        const allIndividualIds = [...new Set(usernameToIndividualId.values())];

        // Fetch all enrollment records across ALL registers for cross-register validation
        const [attendeeEnrollmentsMap, ownerStaffMap, approverStaffMap] = await Promise.all([
            allIndividualIds.length
                ? this.fetchAttendeeEnrollments(allIndividualIds, tenantId, resourceDetails?.requestInfo)
                : Promise.resolve(new Map<string, any[]>()),
            allIndividualIds.length
                ? this.fetchStaffEnrollments(allIndividualIds, [attendanceStaffTypes.OWNER], tenantId, resourceDetails?.requestInfo)
                : Promise.resolve(new Map<string, any[]>()),
            allIndividualIds.length
                ? this.fetchStaffEnrollments(allIndividualIds, [attendanceStaffTypes.APPROVER], tenantId, resourceDetails?.requestInfo)
                : Promise.resolve(new Map<string, any[]>()),
        ]);
        const staffEnrollmentsMap = new Map<string, any[]>([...ownerStaffMap, ...approverStaffMap]);

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
                    logger.debug(`Row ${row["!row#number!"]}: invalid enrollment date format — raw='${enrollmentDateRaw}', type=${typeof enrollmentDateRaw}`);
                    this.addError(row, attendanceErrorKeys.INVALID_DATE_FORMAT, localizationMap);
                } else if (registerStartDate !== null && registerEndDate !== null) {
                    // Compare calendar dates in configured timezone (not raw epochs)
                    const parsedParts = this.epochToDatePartsInTz(parsed);
                    const startParts = this.epochToDatePartsInTz(registerStartDate);
                    const endParts = this.epochToDatePartsInTz(registerEndDate);
                    if (this.compareDateParts(parsedParts, startParts) < 0 || this.compareDateParts(parsedParts, endParts) > 0) {
                        logger.debug(`Row ${row["!row#number!"]}: enrollment date out of range — parsed=${parsedParts.day}/${parsedParts.month}/${parsedParts.year}, registerStart=${startParts.day}/${startParts.month}/${startParts.year}, registerEnd=${endParts.day}/${endParts.month}/${endParts.year}, registerId=${registerId}`);
                        this.addError(row, attendanceErrorKeys.DATE_OUT_OF_RANGE, localizationMap);
                    }
                }
            }

            if (deEnrollmentDateRaw !== null && deEnrollmentDateRaw !== undefined && deEnrollmentDateRaw !== "") {
                const parsedDeEnroll = this.parseDate(deEnrollmentDateRaw);
                if (parsedDeEnroll === null) {
                    logger.debug(`Row ${row["!row#number!"]}: invalid de-enrollment date format — raw='${deEnrollmentDateRaw}', type=${typeof deEnrollmentDateRaw}`);
                    this.addError(row, attendanceErrorKeys.INVALID_DATE_FORMAT, localizationMap);
                } else {
                    if (registerStartDate !== null && registerEndDate !== null) {
                        // Compare calendar dates in configured timezone (not raw epochs)
                        const deEnrollParts = this.epochToDatePartsInTz(parsedDeEnroll);
                        const startParts = this.epochToDatePartsInTz(registerStartDate);
                        const endParts = this.epochToDatePartsInTz(registerEndDate);
                        if (this.compareDateParts(deEnrollParts, startParts) < 0 || this.compareDateParts(deEnrollParts, endParts) > 0) {
                            logger.debug(`Row ${row["!row#number!"]}: de-enrollment date out of range — parsed=${deEnrollParts.day}/${deEnrollParts.month}/${deEnrollParts.year}, registerStart=${startParts.day}/${startParts.month}/${startParts.year}, registerEnd=${endParts.day}/${endParts.month}/${endParts.year}, registerId=${registerId}`);
                            this.addError(row, attendanceErrorKeys.DATE_OUT_OF_RANGE, localizationMap);
                        }
                    }
                    // De-enrollment date must not be before enrollment date
                    if (enrollmentDateRaw !== null && enrollmentDateRaw !== undefined && enrollmentDateRaw !== "") {
                        const parsedEnroll = this.parseDate(enrollmentDateRaw);
                        if (parsedEnroll !== null && parsedDeEnroll < parsedEnroll) {
                            logger.debug(`Row ${row["!row#number!"]}: de-enrollment date (${parsedDeEnroll}) before enrollment date (${parsedEnroll}), registerId=${registerId}`);
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

            // Look up existing record in current register and check for active enrollment in other registers.
            // The sheet stores serviceCode in HCM_ATTENDANCE_REGISTER_ID, but attendance API returns
            // entry.registerId as the register UUID — use registerUuid for correct matching.
            let existing: any = null;
            let activeInOtherRegister: any = null;
            if (isWorkerSheet) {
                const allEntries: any[] = attendeeEnrollmentsMap.get(individualId) || [];
                existing = registerUuid ? allEntries.find((e: any) => e.registerId === registerUuid) || null : null;
                activeInOtherRegister = registerUuid ? allEntries.find((e: any) => e.registerId !== registerUuid && !e.denrollmentDate) || null : null;
            } else {
                const staffType = sheetName === MARKER_SHEET ? attendanceStaffTypes.OWNER : attendanceStaffTypes.APPROVER;
                const allStaffEntries: any[] = staffEnrollmentsMap.get(`${individualId}_${staffType}`) || [];
                existing = registerUuid ? allStaffEntries.find((e: any) => e.registerId === registerUuid) || null : null;
                activeInOtherRegister = registerUuid ? allStaffEntries.find((e: any) => e.registerId !== registerUuid && !e.denrollmentDate) || null : null;
            }

            // A1/D1: no fields provided for new record — skip, nothing to validate
            if (!existing && !enrollmentDateEpoch && !deEnrollmentDateEpoch && !teamCode) {
                continue;
            }

            // Block new enrollment if individual is already actively enrolled in another register,
            // unless this specific user has a role listed in MDMS multiRegisterAllowedRoles.
            const userRoles: string[] = username ? (usernameToRoles.get(username) || []) : [];
            const isExemptUser = multiRegisterAllowedRoles.size > 0 && userRoles.some((r) => multiRegisterAllowedRoles.has(r));
            if (activeInOtherRegister && !existing && !isExemptUser) {
                logger.debug(`Row ${row["!row#number!"]}: user ${username} already enrolled in register ${activeInOtherRegister.registerId}, cannot enroll in ${registerId}`);
                this.addError(row, attendanceErrorKeys.ALREADY_ENROLLED_IN_ANOTHER_REGISTER, localizationMap);
                continue;
            }

            this.validateTruthTableRules(existing, enrollmentDateEpoch, deEnrollmentDateEpoch, teamCode, row, localizationMap);
        }

        const invalidCount = allRows.filter(({ row }) => row["#status#"] === sheetDataRowStatuses.INVALID).length;
        logger.info(`Attendee validation complete — registerId=${registerId}, totalRows=${allRows.length}, invalidRows=${invalidCount}`);

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
            // Compare calendar dates in configured timezone (tolerates epoch differences from timezone migration)
            if (enrollmentDateEpoch !== null && existing.enrollmentDate != null
                && !this.sameDateInTz(enrollmentDateEpoch, existing.enrollmentDate)) {
                // C3/C7/C9/C13/F3/F7
                this.addError(row, attendanceErrorKeys.CANNOT_CHANGE_ENROLLMENT_DATE, localizationMap);
                return;
            }
            if (deEnrollmentDateEpoch !== null && !this.sameDateInTz(deEnrollmentDateEpoch, existing.denrollmentDate)) {
                // C5/C8/C14/F5/F8
                this.addError(row, attendanceErrorKeys.CANNOT_CHANGE_DEENROLLMENT_DATE, localizationMap);
            }
            return;
        }

        // ACTIVE record — Section B (attendee) or Section E (staff)
        // Compare calendar dates in configured timezone (tolerates epoch differences from timezone migration)
        if (enrollmentDateEpoch !== null && existing.enrollmentDate != null
            && !this.sameDateInTz(enrollmentDateEpoch, existing.enrollmentDate)) {
            // B3/B6/B10/B13/E3/E6
            this.addError(row, attendanceErrorKeys.CANNOT_CHANGE_ENROLLMENT_DATE, localizationMap);
        }
    }

    /**
     * Fetch the set of role codes from MDMS multiRegisterAllowedRoles.
     * Users whose roles intersect this set may be enrolled in multiple registers simultaneously.
     */
    private static async fetchMultiRegisterAllowedRoles(tenantId: string, requestInfo?: RequestInfo): Promise<Set<string>> {
        const allowedRoles = new Set<string>();
        try {
            const url = config.host.mdmsV2 + config.paths.mdms_v2_search;
            const header = {
                ...defaultheader,
                cachekey: `mdmsv2ExcelIngestionGenerate${tenantId}${MDMS_ATTENDANCE_REGISTER_ATTENDEE_CONFIG_NAME}`,
            };
            const requestBody = {
                RequestInfo: requestInfo || {},
                MdmsCriteria: {
                    tenantId,
                    schemaCode: MDMS_EXCEL_INGESTION_GENERATE_SCHEMA,
                    filters: { excelIngestionGenerateName: MDMS_ATTENDANCE_REGISTER_ATTENDEE_CONFIG_NAME },
                    limit: 1,
                    offset: 0,
                },
            };
            const response = await httpRequest(url, requestBody, undefined, undefined, undefined, header);
            const data = response?.mdms?.[0]?.data;
            if (!data) return allowedRoles;

            const roles: string[] = data.multiRegisterAllowedRoles || [];
            for (const role of roles) {
                allowedRoles.add(role);
            }
        } catch (err: any) {
            logger.warn(`Failed to fetch attendanceRegisterAttendee MDMS config for tenant ${tenantId}: ${err?.message}`);
        }
        return allowedRoles;
    }

    /**
     * Resolve usernames to individualIds and role codes via HRMS employee search.
     * Returns both usernameToIndividualId and usernameToRoles maps.
     */
    private static async resolveIndividualIds(
        usernames: string[],
        tenantId: string,
        requestInfo?: RequestInfo
    ): Promise<{ usernameToIndividualId: Map<string, string>; usernameToRoles: Map<string, string[]> }> {
        const usernameToIndividualId = new Map<string, string>();
        const usernameToRoles = new Map<string, string[]>();
        if (!usernames.length) return { usernameToIndividualId, usernameToRoles };

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
                        usernameToIndividualId.set(emp.code, emp.user.uuid);
                        // Capture role codes from user.roles[].code
                        const roles: string[] = Array.isArray(emp?.user?.roles)
                            ? emp.user.roles.map((r: any) => r?.code).filter(Boolean)
                            : [];
                        usernameToRoles.set(emp.code, roles);
                    }
                }
            }
        }

        logger.info(`Resolved ${usernameToIndividualId.size}/${usernames.length} usernames via HRMS`);
        return { usernameToIndividualId, usernameToRoles };
    }

    private static getCellAsString(value: any): string {
        if (value === null || value === undefined) return "";
        return String(value).trim();
    }

    private static async fetchRegister(registerId: string, tenantId: string, requestInfo?: RequestInfo): Promise<any> {
        try {
            const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
            const RequestInfo = requestInfo || {};
            const response = await httpRequest(url, { RequestInfo }, {
                tenantId,
                serviceCode: registerId,
                includeAttendee: false,
                includeStaff: false,
            });
            return response?.attendanceRegister?.[0] || null;
        } catch (err: any) {
            logger.warn(`Failed to fetch register ${registerId}: ${err?.message}`);
            return null;
        }
    }

    /**
     * Fetch all attendee enrollment records for the given individualIds across ALL registers.
     * Returns Map<individualId, IndividualEntry[]>.
     */
    private static async fetchAttendeeEnrollments(
        individualIds: string[],
        tenantId: string,
        requestInfo?: RequestInfo
    ): Promise<Map<string, any[]>> {
        const result = new Map<string, any[]>();
        const url = config.host.attendanceHost + config.paths.attendanceAttendeeSearch;
        const limit = config.attendanceRegister.attendeeSearchPageSize;
        let offset = 0;

        try {
            while (true) {
                const response = await httpRequest(
                    url,
                    { RequestInfo: requestInfo || {}, attendees: { individualIds, tenantId } },
                    { tenantId, limit, offset }
                );
                const entries: any[] = response?.attendees || [];
                for (const entry of entries) {
                    if (!entry.individualId) continue;
                    const existing = result.get(entry.individualId);
                    if (existing) existing.push(entry);
                    else result.set(entry.individualId, [entry]);
                }
                if (entries.length < limit) break;
                offset += limit;
            }
        } catch (err: any) {
            logger.warn(`Failed to fetch attendee enrollments for ${individualIds.length} individuals in tenant ${tenantId}: ${err?.message}`);
        }

        logger.info(`Fetched attendee enrollments for ${result.size} individuals across all registers`);
        return result;
    }

    /**
     * Fetch all staff records for the given individualIds + staffTypes across ALL registers.
     * Returns Map<`${userId}_${staffType}`, StaffPermission[]>.
     */
    private static async fetchStaffEnrollments(
        individualIds: string[],
        staffTypes: string[],
        tenantId: string,
        requestInfo?: RequestInfo
    ): Promise<Map<string, any[]>> {
        const result = new Map<string, any[]>();
        const url = config.host.attendanceHost + config.paths.attendanceStaffSearch;
        const limit = config.attendanceRegister.staffSearchPageSize;
        let offset = 0;

        try {
            while (true) {
                const response = await httpRequest(
                    url,
                    { RequestInfo: requestInfo || {}, staff: { individualIds, staffTypes, tenantId } },
                    { tenantId, limit, offset }
                );
                const entries: any[] = response?.staff || [];
                for (const entry of entries) {
                    if (!entry.userId || !entry.staffType) continue;
                    const key = `${entry.userId}_${entry.staffType}`;
                    const existing = result.get(key);
                    if (existing) existing.push(entry);
                    else result.set(key, [entry]);
                }
                if (entries.length < limit) break;
                offset += limit;
            }
        } catch (err: any) {
            logger.warn(`Failed to fetch staff enrollments for types [${staffTypes.join(",")}], ${individualIds.length} individuals in tenant ${tenantId}: ${err?.message}`);
        }

        logger.info(`Fetched staff enrollments for types [${staffTypes.join(",")}] across all registers`);
        return result;
    }

    // ── Timezone-aware date utilities ──────────────────────────────────────

    /** Cached Intl formatter for the configured server timezone */
    private static tzFormatter: Intl.DateTimeFormat | null = null;

    private static getTzFormatter(): Intl.DateTimeFormat {
        if (!this.tzFormatter) {
            this.tzFormatter = new Intl.DateTimeFormat('en-US', {
                timeZone: config.appTimezone,
                year: 'numeric', month: '2-digit', day: '2-digit',
                hour: '2-digit', minute: '2-digit', second: '2-digit',
                hour12: false
            });
        }
        return this.tzFormatter;
    }

    /**
     * Returns epoch ms for midnight (00:00:00.000) of the given date in the configured server timezone.
     */
    private static midnightEpochInTz(year: number, month: number, day: number): number {
        const utcGuess = Date.UTC(year, month, day);
        const parts = this.getTzFormatter().formatToParts(new Date(utcGuess));
        const get = (type: string) => parseInt(parts.find(p => p.type === type)!.value, 10);
        const localHour = get('hour') === 24 ? 0 : get('hour');
        const localMinute = get('minute');
        const localSecond = get('second');
        const localYear = get('year');
        const localMonth = get('month') - 1;
        const localDay = get('day');
        const localAsUtc = Date.UTC(localYear, localMonth, localDay, localHour, localMinute, localSecond);
        const offsetMs = localAsUtc - utcGuess;
        return Date.UTC(year, month, day) - offsetMs;
    }

    /**
     * Extracts calendar date parts {year, month, day} from an epoch ms value in the configured server timezone.
     */
    private static epochToDatePartsInTz(epochMs: number): { year: number; month: number; day: number } {
        const parts = this.getTzFormatter().formatToParts(new Date(epochMs));
        const get = (type: string) => parseInt(parts.find(p => p.type === type)!.value, 10);
        return { year: get('year'), month: get('month'), day: get('day') };
    }

    /** Compare two epoch values by calendar date in configured timezone. Returns true if same date. */
    private static sameDateInTz(aEpoch: number, bEpoch: number): boolean {
        const a = this.epochToDatePartsInTz(aEpoch);
        const b = this.epochToDatePartsInTz(bEpoch);
        return a.year === b.year && a.month === b.month && a.day === b.day;
    }

    /** Returns -1, 0, or 1 comparing two date-part objects chronologically. */
    private static compareDateParts(
        a: { year: number; month: number; day: number },
        b: { year: number; month: number; day: number }
    ): number {
        if (a.year !== b.year) return a.year < b.year ? -1 : 1;
        if (a.month !== b.month) return a.month < b.month ? -1 : 1;
        if (a.day !== b.day) return a.day < b.day ? -1 : 1;
        return 0;
    }

    // ── Date parsing ────────────────────────────────────────────────────────

    /**
     * Parse date value to epoch ms at start of day (midnight) in the configured server timezone.
     * Supports Date objects, existing epoch numbers, and dd-MM-yyyy / dd/MM/yyyy strings.
     */
    private static parseDate(value: any): number | null {
        if (value instanceof Date) {
            if (isNaN(value.getTime())) return null;
            return this.midnightEpochInTz(value.getFullYear(), value.getMonth(), value.getDate());
        }
        if (typeof value === "number") {
            if (value < EXCEL_SERIAL_THRESHOLD) {
                // Excel serial date: days since Dec 30, 1899
                const utcDate = new Date(EXCEL_EPOCH_MS + value * MS_PER_DAY);
                return this.midnightEpochInTz(utcDate.getUTCFullYear(), utcDate.getUTCMonth(), utcDate.getUTCDate());
            }
            return value;
        }
        const str = String(value).trim();
        // Handle ISO 8601 strings (e.g., "2026-04-05T00:00:00.000Z" from getRawCellValue)
        if (ISO_DATE_PREFIX_REGEX.test(str)) {
            const isoDate = new Date(str);
            if (!isNaN(isoDate.getTime())) {
                const parts = this.epochToDatePartsInTz(isoDate.getTime());
                return this.midnightEpochInTz(parts.year, parts.month - 1, parts.day);
            }
        }
        const match = DASH_DATE_REGEX.exec(str) || SLASH_DATE_REGEX.exec(str);
        if (!match) return null;
        const day = parseInt(match[1], 10);
        const month = parseInt(match[2], 10) - 1;
        const year = parseInt(match[3], 10);
        const check = new Date(Date.UTC(year, month, day));
        if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month || check.getUTCDate() !== day) {
            return null;
        }
        return this.midnightEpochInTz(year, month, day);
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
