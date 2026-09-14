import config from "../config";
import { dataRowStatuses } from "../config/constants";
import { RequestInfo } from "../config/models/requestInfoSchema";
import { CampaignDataRow } from "../config/models/campaignDataRow";
import { ColumnProperties, SheetMap } from "../models/SheetMap";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { formatEpochAsSheetDate } from "../utils/attendanceIdentityUtils";
import { getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";
import { logger } from "../utils/logger";
import { httpRequest } from "../utils/request";

const BULK_MAPPING_SHEET = "HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET";
const ATTENDEE_DATA_TYPE = "attendanceRegisterAttendee";
const ATTENDANCE_REGISTER_SEARCH_LIMIT = 200;
const INDIVIDUAL_SEARCH_BATCH_SIZE = 100;
const MAX_ROLE_COLUMNS = 5;
const STAFF_TYPE_APPROVER = "APPROVER";
const STAFF_TYPE_OWNER = "OWNER";
const ROLE_WORKER = "WORKER";
const ROLE_MARKER = "TEAM_SUPERVISOR";
const ROLE_APPROVER = "PROXIMITY_SUPERVISOR";

const REGISTER_CODE_COLUMN = "HCM_ATTENDANCE_REGISTER_CODE";
const REGISTER_NAME_COLUMN = "HCM_ATTENDANCE_REGISTER_NAME";
const REGISTER_UUID_COLUMN = "HCM_ATTENDANCE_REGISTER_UUID";
const USER_NAME_COLUMN = "HCM_ADMIN_CONSOLE_USER_NAME";
const WORKER_ID_COLUMN = "HCM_ADMIN_CONSOLE_USER_WORKER_ID";
const ROLE_COLUMN = "HCM_ADMIN_CONSOLE_USER_ROLE";
const BOUNDARY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_NAME";
const BOUNDARY_CODE_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
const BOUNDARY_CODE_MANDATORY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
const USERNAME_COLUMN = "UserName";
const ENROLLMENT_DATE_COLUMN = "HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE";
const DEENROLLMENT_DATE_COLUMN = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE";

interface RegisterData {
    id: string;
    serviceCode: string;
    name: string;
    localityCode: string;
    attendees: AttendanceAttendeeRow[];
    staff: AttendanceStaffRow[];
}

type BulkRow = Record<string, string>;

interface AttendanceAttendeeRow {
    individualId?: string;
    enrollmentDate?: number | string | null;
    denrollmentDate?: number | string | null;
}

interface AttendanceStaffRow {
    userId?: string;
    staffType?: string;
    enrollmentDate?: number | string | null;
    denrollmentDate?: number | string | null;
    additionalDetails?: Record<string, unknown>;
}

interface IndividualProfile {
    displayName: string;
    username: string;
}

/**
 * Generates a single-sheet register-user mapping template:
 * one row per register-user mapping, with register-only rows for unmapped registers.
 */
export class TemplateClass {
    static async generate(templateConfig: any, responseToSend: any, _localizationMap: any): Promise<SheetMap> {
        logger.info("Generating attendance register user bulk mapping template...");

        const tenantId = String(responseToSend?.tenantId || "").trim();
        const campaignId = String(responseToSend?.campaignId || "").trim();

        if (!tenantId || !campaignId) {
            throwError("CAMPAIGN", 400, "VALIDATION_ERROR", "tenantId and campaignId are required for bulk mapping generation");
        }

        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaign = campaignResp?.CampaignDetails?.[0];
        if (!campaign) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found");
        }

        const campaignNumber = String(campaign?.campaignNumber || "").trim();
        if (!campaignNumber) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NUMBER_MISSING", `Campaign ${campaignId} has no campaignNumber set`);
        }

        const campaignStartDate = this.formatEpochIfPresent(campaign?.startDate);
        const campaignEndDate = this.formatEpochIfPresent(campaign?.endDate);

        const registers = await this.fetchCampaignRegisters(campaignId, tenantId, responseToSend?.requestInfo);
        const registerByServiceCode = new Map<string, RegisterData>();
        const registerById = new Map<string, RegisterData>();
        for (const register of registers) {
            registerByServiceCode.set(register.serviceCode, register);
            registerById.set(register.id, register);
        }

        const attendeeRows = await getRelatedDataWithCampaign(
            ATTENDEE_DATA_TYPE,
            campaignNumber,
            tenantId,
            dataRowStatuses.completed
        ) as CampaignDataRow[];

        const rows = this.buildBulkRows(
            registers,
            Array.isArray(attendeeRows) ? attendeeRows : [],
            registerByServiceCode,
            registerById,
            campaignStartDate,
            campaignEndDate
        );

        const outputRows = this.containsMappedRows(rows)
            ? rows
            : await this.buildRowsFromAttendanceState(
                registers,
                tenantId,
                responseToSend?.requestInfo,
                campaignStartDate,
                campaignEndDate
            );

        logger.info(`Built ${outputRows.length} rows for bulk mapping template (registers=${registers.length})`);

        const sheetName = templateConfig?.sheets?.[0]?.sheetName || BULK_MAPPING_SHEET;
        return {
            [sheetName]: {
                data: outputRows,
                dynamicColumns: this.bulkSheetColumns()
            }
        };
    }

    static buildBulkRows(
        registers: RegisterData[],
        attendeeRows: CampaignDataRow[],
        registerByServiceCode: Map<string, RegisterData>,
        registerById: Map<string, RegisterData>,
        campaignStartDate: string,
        campaignEndDate: string
    ): BulkRow[] {
        const dedupedRows = new Map<string, BulkRow>();
        const mappedRegisters = new Set<string>();

        for (const attendeeRow of attendeeRows) {
            if (attendeeRow?.isDeleted) continue;

            const rawData = this.asRecord(attendeeRow?.data);
            if (!rawData) continue;

            const register = this.resolveRegister(rawData, attendeeRow?.uniqueIdAfterProcess, registerByServiceCode, registerById);
            if (!register) continue;

            const registerKey = this.registerIdentity(register);
            mappedRegisters.add(registerKey);

            const row = this.buildMappedRow(register, rawData, attendeeRow?.denrollmentDate, campaignStartDate, campaignEndDate);
            const dedupeKey = `${registerKey}::${this.personIdentity(rawData, attendeeRow?.uniqueIdentifier)}`;
            const existing = dedupedRows.get(dedupeKey);
            dedupedRows.set(dedupeKey, existing ? this.mergeRows(existing, row) : row);
        }

        for (const register of registers) {
            const registerKey = this.registerIdentity(register);
            if (mappedRegisters.has(registerKey)) continue;
            dedupedRows.set(
                `${registerKey}::__register_only__`,
                this.buildRegisterOnlyRow(register, campaignStartDate, campaignEndDate)
            );
        }

        return Array.from(dedupedRows.values()).sort((a, b) => this.sortRows(a, b));
    }

    private static bulkSheetColumns(): { [columnName: string]: ColumnProperties } {
        return {
            [REGISTER_CODE_COLUMN]: { orderNumber: 1, width: 22, freezeColumn: true },
            [REGISTER_NAME_COLUMN]: { orderNumber: 2, width: 36 },
            [REGISTER_UUID_COLUMN]: { orderNumber: 3, width: 42 },
            [USER_NAME_COLUMN]: { orderNumber: 4, width: 30 },
            [WORKER_ID_COLUMN]: { orderNumber: 5, width: 38 },
            [ROLE_COLUMN]: { orderNumber: 6, width: 30 },
            [BOUNDARY_COLUMN]: { orderNumber: 7, width: 28 },
            [ENROLLMENT_DATE_COLUMN]: { orderNumber: 8, width: 22 },
            [DEENROLLMENT_DATE_COLUMN]: { orderNumber: 9, width: 22 }
        };
    }

    private static async fetchCampaignRegisters(campaignId: string, tenantId: string, requestInfo?: RequestInfo): Promise<RegisterData[]> {
        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo || {};
        const seen = new Set<string>();
        const registers: RegisterData[] = [];

        for (let offset = 0; offset <= 10000; offset += ATTENDANCE_REGISTER_SEARCH_LIMIT) {
            const response = await httpRequest(
                url,
                { RequestInfo },
                { tenantId, referenceId: campaignId, limit: ATTENDANCE_REGISTER_SEARCH_LIMIT, offset }
            );
            const batch = Array.isArray(response?.attendanceRegister) ? response.attendanceRegister : [];
            if (batch.length === 0) break;

            for (const item of batch) {
                if (item?.isDeleted === true) continue;
                const id = String(item?.id || "").trim();
                const serviceCode = String(item?.serviceCode || "").trim();
                if (!id || !serviceCode) continue;
                const key = `${id}::${serviceCode}`;
                if (seen.has(key)) continue;
                seen.add(key);
                registers.push({
                    id,
                    serviceCode,
                    name: String(item?.name || serviceCode).trim(),
                    localityCode: String(item?.localityCode || "").trim(),
                    attendees: Array.isArray(item?.attendees) ? item.attendees : [],
                    staff: Array.isArray(item?.staff) ? item.staff : []
                });
            }

            if (batch.length < ATTENDANCE_REGISTER_SEARCH_LIMIT) break;
        }

        return registers;
    }

    private static containsMappedRows(rows: BulkRow[]): boolean {
        return rows.some((row) => Boolean(this.firstNonBlank(
            row[USER_NAME_COLUMN],
            row[WORKER_ID_COLUMN]
        )));
    }

    private static async buildRowsFromAttendanceState(
        registers: RegisterData[],
        tenantId: string,
        requestInfo: RequestInfo | undefined,
        campaignStartDate: string,
        campaignEndDate: string
    ): Promise<BulkRow[]> {
        if (registers.length === 0) return [];

        const personIds = new Set<string>();
        for (const register of registers) {
            for (const attendee of register.attendees || []) {
                const personId = this.asText(attendee?.individualId);
                if (personId) personIds.add(personId);
            }
            for (const staff of register.staff || []) {
                const personId = this.asText(staff?.userId);
                if (personId) personIds.add(personId);
            }
        }

        const profiles = await this.fetchIndividualProfiles(
            tenantId,
            Array.from(personIds),
            requestInfo
        );
        const rows = new Map<string, BulkRow>();
        const mappedRegisters = new Set<string>();

        for (const register of registers) {
            const registerKey = this.registerIdentity(register);

            for (const attendee of register.attendees || []) {
                const personId = this.asText(attendee?.individualId);
                if (!personId) continue;

                const profile = profiles.get(personId);
                const role = ROLE_WORKER;
                const dedupeKey = `${registerKey}::${personId}::${role}`;
                rows.set(dedupeKey, {
                    [REGISTER_CODE_COLUMN]: register.serviceCode,
                    [REGISTER_NAME_COLUMN]: register.name,
                    [REGISTER_UUID_COLUMN]: register.id,
                    [USER_NAME_COLUMN]: this.firstNonBlank(profile?.displayName, profile?.username, personId),
                    [WORKER_ID_COLUMN]: personId,
                    [ROLE_COLUMN]: role,
                    [BOUNDARY_COLUMN]: register.localityCode,
                    [ENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                        this.formatEpochIfPresent(attendee?.enrollmentDate),
                        campaignStartDate
                    ),
                    [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                        this.formatEpochIfPresent(attendee?.denrollmentDate),
                        campaignEndDate
                    )
                });
                mappedRegisters.add(registerKey);
            }

            for (const staff of register.staff || []) {
                const personId = this.asText(staff?.userId);
                if (!personId) continue;

                const role = this.normalizeRoleFromStaffType(staff?.staffType);
                if (!role) continue;

                const profile = profiles.get(personId);
                const staffName = this.displayNameFromStaff(staff);
                const dedupeKey = `${registerKey}::${personId}::${role}`;
                rows.set(dedupeKey, {
                    [REGISTER_CODE_COLUMN]: register.serviceCode,
                    [REGISTER_NAME_COLUMN]: register.name,
                    [REGISTER_UUID_COLUMN]: register.id,
                    [USER_NAME_COLUMN]: this.firstNonBlank(profile?.displayName, staffName, profile?.username, personId),
                    [WORKER_ID_COLUMN]: personId,
                    [ROLE_COLUMN]: role,
                    [BOUNDARY_COLUMN]: register.localityCode,
                    [ENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                        this.formatEpochIfPresent(staff?.enrollmentDate),
                        campaignStartDate
                    ),
                    [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                        this.formatEpochIfPresent(staff?.denrollmentDate),
                        campaignEndDate
                    )
                });
                mappedRegisters.add(registerKey);
            }
        }

        for (const register of registers) {
            const registerKey = this.registerIdentity(register);
            if (mappedRegisters.has(registerKey)) continue;
            rows.set(`${registerKey}::__register_only__`, this.buildRegisterOnlyRow(register, campaignStartDate, campaignEndDate));
        }

        return Array.from(rows.values()).sort((a, b) => this.sortRows(a, b));
    }

    private static async fetchIndividualProfiles(
        tenantId: string,
        personIds: string[],
        requestInfo?: RequestInfo
    ): Promise<Map<string, IndividualProfile>> {
        const profiles = new Map<string, IndividualProfile>();
        if (personIds.length === 0) return profiles;

        const ids = Array.from(new Set(personIds.map((id) => this.asText(id)).filter(Boolean)));
        const primaryUrl = config.host.healthIndividualHost + config.paths.healthIndividualSearch;
        const fallbackPath = "individual/v1/_search";
        const fallbackUrl = config.host.healthIndividualHost + fallbackPath;
        const useFallbackProbe = config.paths.healthIndividualSearch !== fallbackPath;
        const RequestInfo = requestInfo || {};

        for (let offset = 0; offset < ids.length; offset += INDIVIDUAL_SEARCH_BATCH_SIZE) {
            const batch = ids.slice(offset, offset + INDIVIDUAL_SEARCH_BATCH_SIZE);
            const payload = { RequestInfo, Individual: { id: batch } };
            const params = {
                tenantId,
                limit: batch.length + 5,
                offset: 0
            };

            const primary = await httpRequest(primaryUrl, payload, params, "post", "", undefined, false, false, true);
            let individuals = Array.isArray(primary?.Individual) ? primary.Individual : [];

            if (individuals.length === 0 && useFallbackProbe) {
                const legacy = await httpRequest(fallbackUrl, payload, params, "post", "", undefined, false, false, true);
                individuals = Array.isArray(legacy?.Individual) ? legacy.Individual : [];
            }

            for (const item of individuals) {
                const individualId = this.asText(item?.id);
                if (!individualId) continue;
                profiles.set(individualId, {
                    displayName: this.displayNameFromIndividual(item),
                    username: this.firstNonBlank(
                        this.asText(item?.userDetails?.username),
                        this.asText(item?.username)
                    )
                });
            }
        }

        return profiles;
    }

    private static displayNameFromIndividual(individual: Record<string, unknown>): string {
        const name = this.asRecord(individual?.name);
        return this.firstNonBlank(
            [this.asText(name?.givenName), this.asText(name?.otherNames), this.asText(name?.familyName)]
                .filter(Boolean)
                .join(" ")
                .trim(),
            this.asText(individual?.username)
        );
    }

    private static displayNameFromStaff(staff: AttendanceStaffRow): string {
        const additionalDetails = this.asRecord(staff?.additionalDetails);
        return this.firstNonBlank(
            this.asText(additionalDetails?.staffName),
            this.asText(additionalDetails?.ownerName)
        );
    }

    private static normalizeRoleFromStaffType(staffType: unknown): string {
        const normalized = this.asText(staffType).toUpperCase();
        if (!normalized) return "";
        if (normalized === STAFF_TYPE_APPROVER) return ROLE_APPROVER;
        if (normalized === STAFF_TYPE_OWNER) return ROLE_MARKER;
        return normalized;
    }

    private static resolveRegister(
        rawData: Record<string, unknown>,
        uniqueIdAfterProcess: string | null,
        registerByServiceCode: Map<string, RegisterData>,
        registerById: Map<string, RegisterData>
    ): RegisterData | null {
        const serviceCode = this.firstNonBlank(
            this.asText(rawData["_registerServiceCode"]),
            this.asText(rawData["HCM_ATTENDANCE_REGISTER_ID"]),
            this.asText(rawData[REGISTER_CODE_COLUMN])
        );
        if (serviceCode) {
            const byServiceCode = registerByServiceCode.get(serviceCode);
            if (byServiceCode) return byServiceCode;
        }

        const registerIdFromIdentity = this.registerIdFromIdentity(uniqueIdAfterProcess || "");
        if (registerIdFromIdentity) {
            const byId = registerById.get(registerIdFromIdentity);
            if (byId) return byId;
        }

        return null;
    }

    private static buildMappedRow(
        register: RegisterData,
        rawData: Record<string, unknown>,
        syncedDeenrollmentDate: number | null,
        campaignStartDate: string,
        campaignEndDate: string
    ): BulkRow {
        const deEnrollmentDate = this.firstNonBlank(
            this.formatEpochIfPresent(syncedDeenrollmentDate),
            this.asText(rawData[DEENROLLMENT_DATE_COLUMN]),
            campaignEndDate
        );

        return {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [USER_NAME_COLUMN]: this.asText(rawData[USER_NAME_COLUMN]),
            [WORKER_ID_COLUMN]: this.asText(rawData[WORKER_ID_COLUMN]),
            [ROLE_COLUMN]: this.extractRole(rawData),
            [BOUNDARY_COLUMN]: this.firstNonBlank(
                this.asText(rawData[BOUNDARY_COLUMN]),
                this.asText(rawData[BOUNDARY_CODE_MANDATORY_COLUMN]),
                this.asText(rawData[BOUNDARY_CODE_COLUMN])
            ),
            [ENROLLMENT_DATE_COLUMN]: this.firstNonBlank(this.asText(rawData[ENROLLMENT_DATE_COLUMN]), campaignStartDate),
            [DEENROLLMENT_DATE_COLUMN]: deEnrollmentDate
        };
    }

    private static buildRegisterOnlyRow(register: RegisterData, campaignStartDate: string, campaignEndDate: string): BulkRow {
        return {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [USER_NAME_COLUMN]: "",
            [WORKER_ID_COLUMN]: "",
            [ROLE_COLUMN]: "",
            [BOUNDARY_COLUMN]: "",
            [ENROLLMENT_DATE_COLUMN]: campaignStartDate,
            [DEENROLLMENT_DATE_COLUMN]: campaignEndDate
        };
    }

    private static mergeRows(existing: BulkRow, incoming: BulkRow): BulkRow {
        const merged: BulkRow = { ...existing };
        for (const [key, value] of Object.entries(incoming)) {
            if (key === ROLE_COLUMN) {
                merged[key] = this.mergeRoles(existing[ROLE_COLUMN], value);
                continue;
            }
            merged[key] = this.firstNonBlank(existing[key], value);
        }
        return merged;
    }

    private static mergeRoles(existing: string, incoming: string): string {
        const unique = new Set<string>();
        const roles: string[] = [];
        for (const role of [...this.splitRoles(existing), ...this.splitRoles(incoming)]) {
            const key = role.toUpperCase();
            if (unique.has(key)) continue;
            unique.add(key);
            roles.push(role);
        }
        return roles.join(", ");
    }

    private static splitRoles(roleValue: string): string[] {
        return roleValue
            .split(",")
            .map((role) => role.trim())
            .filter((role) => role.length > 0);
    }

    private static extractRole(rawData: Record<string, unknown>): string {
        const baseRole = this.asText(rawData[ROLE_COLUMN]);
        if (baseRole) {
            return this.mergeRoles("", baseRole);
        }

        const roles: string[] = [];
        for (let i = 1; i <= MAX_ROLE_COLUMNS; i++) {
            const role = this.asText(rawData[`HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_${i}`]);
            if (role) roles.push(role);
        }
        return this.mergeRoles("", roles.join(", "));
    }

    private static personIdentity(rawData: Record<string, unknown>, fallbackUniqueIdentifier: string): string {
        return this.firstNonBlank(
            this.asText(rawData[WORKER_ID_COLUMN]),
            this.asText(rawData[USERNAME_COLUMN]),
            this.asText(rawData[USER_NAME_COLUMN]),
            fallbackUniqueIdentifier,
            "__unknown__"
        );
    }

    private static registerIdentity(register: RegisterData): string {
        return this.firstNonBlank(register.id, register.serviceCode);
    }

    private static registerIdFromIdentity(identity: string): string {
        const idx = identity.indexOf("_");
        return idx > 0 ? identity.slice(0, idx).trim() : "";
    }

    private static sortRows(a: BulkRow, b: BulkRow): number {
        return this.asText(a[REGISTER_CODE_COLUMN]).localeCompare(this.asText(b[REGISTER_CODE_COLUMN]))
            || this.asText(a[USER_NAME_COLUMN]).localeCompare(this.asText(b[USER_NAME_COLUMN]))
            || this.asText(a[WORKER_ID_COLUMN]).localeCompare(this.asText(b[WORKER_ID_COLUMN]));
    }

    private static formatEpochIfPresent(value: unknown): string {
        if (value === null || value === undefined || value === "") return "";
        const asNumber = Number(value);
        if (!Number.isFinite(asNumber)) return "";
        return formatEpochAsSheetDate(asNumber);
    }

    private static firstNonBlank(...values: Array<string | null | undefined>): string {
        for (const value of values) {
            if (!value) continue;
            const trimmed = value.trim();
            if (trimmed) return trimmed;
        }
        return "";
    }

    private static asText(value: unknown): string {
        if (value === null || value === undefined) return "";
        return String(value).trim();
    }

    private static asRecord(value: unknown): Record<string, unknown> | null {
        if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
        return value as Record<string, unknown>;
    }
}
