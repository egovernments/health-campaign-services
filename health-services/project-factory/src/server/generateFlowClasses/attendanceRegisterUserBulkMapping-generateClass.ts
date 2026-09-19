import config from "../config";
import { attendanceColumnKeys, attendanceSheetNames, dataRowStatuses } from "../config/constants";
import { RequestInfo } from "../config/models/requestInfoSchema";
import { CampaignDataRow } from "../config/models/campaignDataRow";
import { ColumnProperties, SheetMap } from "../models/SheetMap";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { formatEpochAsSheetDate } from "../utils/attendanceIdentityUtils";
import { getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";
import { logger } from "../utils/logger";
import { httpRequest } from "../utils/request";

const ATTENDEE_DATA_TYPE = "attendanceRegisterAttendee";
const ATTENDANCE_REGISTER_SEARCH_LIMIT = 200;
const INDIVIDUAL_SEARCH_BATCH_SIZE = 100;
const MAX_ROLE_COLUMNS = 5;
const STAFF_TYPE_APPROVER = "APPROVER";
const STAFF_TYPE_OWNER = "OWNER";

const WORKER_SHEET = attendanceSheetNames.WORKER;
const MARKER_SHEET = attendanceSheetNames.MARKER;
const APPROVER_SHEET = attendanceSheetNames.APPROVER;

const SHEET_NAMES = [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET];

const MARKER_ROLE_CODES = new Set([
    "MARKER",
    "OWNER",
    "TEAM_SUPERVISOR",
    "WAREHOUSE_MANAGER",
    "CAMPAIGN_SUPERVISOR",
]);

const APPROVER_ROLE_CODES = new Set([
    "APPROVER",
    "PROXIMITY_SUPERVISOR",
]);

const REGISTER_CODE_COLUMN = "HCM_ATTENDANCE_REGISTER_CODE";
const REGISTER_NAME_COLUMN = "HCM_ATTENDANCE_REGISTER_NAME";
const REGISTER_UUID_COLUMN = "HCM_ATTENDANCE_REGISTER_UUID";
const USER_NAME_COLUMN = "HCM_ADMIN_CONSOLE_USER_NAME";
const WORKER_ID_COLUMN = "HCM_ADMIN_CONSOLE_USER_WORKER_ID";
const ROLE_COLUMN = "HCM_ADMIN_CONSOLE_USER_ROLE";
const BOUNDARY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_NAME";
const BOUNDARY_CODE_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
const BOUNDARY_CODE_MANDATORY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
const USERNAME_COLUMN = attendanceColumnKeys.USERNAME;
const PASSWORD_COLUMN = "Password";
const REGISTER_ID_COLUMN = attendanceColumnKeys.REGISTER_ID;
const ENROLLMENT_DATE_COLUMN = attendanceColumnKeys.ENROLLMENT_DATE;
const DEENROLLMENT_DATE_COLUMN = attendanceColumnKeys.DEENROLLMENT_DATE;
const TEAM_CODE_COLUMN = attendanceColumnKeys.TEAM_CODE;

const DASH_DATE_REGEX = /^(\d{2})-(\d{2})-(\d{4})$/;
const SLASH_DATE_REGEX = /^(\d{2})\/(\d{2})\/(\d{4})$/;
const ISO_DATE_PREFIX_REGEX = /^\d{4}-\d{2}-\d{2}/;

interface RegisterData {
    id: string;
    serviceCode: string;
    name: string;
    localityCode: string;
    attendees: AttendanceAttendeeRow[];
    staff: AttendanceStaffRow[];
}

interface AttendanceAttendeeRow {
    individualId?: string;
    tag?: string;
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

type BulkRow = Record<string, string>;
type RowsBySheetName = Map<string, BulkRow[]>;
type DedupedRowsBySheetName = Map<string, Map<string, BulkRow>>;

/**
 * Generates a bulk mapping workbook with the same 3 attendee tabs and columns,
 * while prepending register metadata columns on each sheet.
 *
 * Rows are loaded from persisted attendee mappings when present; otherwise they
 * are reconstructed from live attendance state.
 */
export class TemplateClass {
    static async generate(_templateConfig: any, responseToSend: any, _localizationMap: any): Promise<SheetMap> {
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

        const registerSearchReferenceId = this.resolveRegisterSearchReferenceId(
            campaignId,
            campaign,
            responseToSend?.additionalDetails
        );
        const localityCodes = this.resolveRegisterSearchLocalityCodes(campaign, responseToSend?.additionalDetails);
        const registers = await this.fetchCampaignRegisters(
            registerSearchReferenceId,
            campaignId,
            tenantId,
            localityCodes,
            responseToSend?.requestInfo
        );
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

        const rowsFromStoredData = this.buildRowsFromStoredMappings(
            registers,
            Array.isArray(attendeeRows) ? attendeeRows : [],
            registerByServiceCode,
            registerById,
            campaignStartDate,
            campaignEndDate
        );

        const outputRowsBySheetName = await this.buildOutputRowsBySheetName(
            rowsFromStoredData,
            registers,
            tenantId,
            responseToSend?.requestInfo,
            campaignStartDate,
            campaignEndDate
        );

        this.ensureSeedRowsPerRegisterPerSheet(outputRowsBySheetName, registers);

        const totalRows = Array.from(outputRowsBySheetName.values()).reduce((sum, rows) => sum + rows.length, 0);
        logger.info(`Built ${totalRows} rows for bulk mapping template (registers=${registers.length})`);

        return this.toSheetMap(outputRowsBySheetName);
    }

    private static buildRowsFromStoredMappings(
        registers: RegisterData[],
        attendeeRows: CampaignDataRow[],
        registerByServiceCode: Map<string, RegisterData>,
        registerById: Map<string, RegisterData>,
        campaignStartDate: string,
        campaignEndDate: string
    ): RowsBySheetName {
        const dedupedRowsBySheetName = this.createEmptyDedupedRowsBySheetName();

        for (const attendeeRow of attendeeRows) {
            if (attendeeRow?.isDeleted) continue;

            const rawData = this.asRecord(attendeeRow?.data);
            if (!rawData) continue;

            const register = this.resolveRegister(rawData, attendeeRow?.uniqueIdAfterProcess, registerByServiceCode, registerById);
            if (!register) continue;

            const sheetName = this.resolveSheetName(rawData);
            if (!sheetName) continue;

            const row = this.buildMappedRow(register, sheetName, rawData, attendeeRow?.denrollmentDate, campaignStartDate, campaignEndDate);
            const dedupeKey = `${this.registerIdentity(register)}::${sheetName}::${this.personIdentity(rawData, attendeeRow?.uniqueIdentifier)}`;

            const sheetRows = dedupedRowsBySheetName.get(sheetName);
            if (!sheetRows) continue;
            const existing = sheetRows.get(dedupeKey);
            sheetRows.set(dedupeKey, existing ? this.mergeRows(existing, row) : row);
        }

        // If there are no mapped rows in campaign_data yet, include register seeds.
        if (!this.containsMappedRows(this.flattenRowsBySheetName(dedupedRowsBySheetName))) {
            for (const register of registers) {
                for (const sheetName of SHEET_NAMES) {
                    const seedKey = `${this.registerIdentity(register)}::${sheetName}::__seed__`;
                    dedupedRowsBySheetName.get(sheetName)?.set(seedKey, this.buildSeedRow(register, sheetName));
                }
            }
        }

        return this.flattenRowsBySheetName(dedupedRowsBySheetName);
    }

    private static async buildRowsFromAttendanceState(
        registers: RegisterData[],
        tenantId: string,
        requestInfo: RequestInfo | undefined,
        campaignStartDate: string,
        campaignEndDate: string
    ): Promise<RowsBySheetName> {
        const dedupedRowsBySheetName = this.createEmptyDedupedRowsBySheetName();
        if (!registers.length) return this.flattenRowsBySheetName(dedupedRowsBySheetName);

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

        const profiles = await this.fetchIndividualProfiles(tenantId, Array.from(personIds), requestInfo);

        for (const register of registers) {
            const registerKey = this.registerIdentity(register);

            for (const attendee of register.attendees || []) {
                const personId = this.asText(attendee?.individualId);
                if (!personId) continue;

                const profile = profiles.get(personId);
                const dedupeKey = `${registerKey}::${WORKER_SHEET}::${personId}`;
                const row = this.buildAttendanceStateWorkerRow(
                    register,
                    attendee,
                    profile,
                    personId,
                    campaignStartDate,
                    campaignEndDate
                );
                dedupedRowsBySheetName.get(WORKER_SHEET)?.set(dedupeKey, row);
            }

            for (const staff of register.staff || []) {
                const personId = this.asText(staff?.userId);
                if (!personId) continue;
                const sheetName = this.sheetNameFromStaffType(staff?.staffType);
                if (!sheetName) continue;

                const profile = profiles.get(personId);
                const dedupeKey = `${registerKey}::${sheetName}::${personId}`;
                const row = this.buildAttendanceStateStaffRow(
                    register,
                    staff,
                    profile,
                    personId,
                    campaignStartDate,
                    campaignEndDate
                );
                dedupedRowsBySheetName.get(sheetName)?.set(dedupeKey, row);
            }
        }

        return this.flattenRowsBySheetName(dedupedRowsBySheetName);
    }

    private static async buildOutputRowsBySheetName(
        rowsFromStoredData: RowsBySheetName,
        registers: RegisterData[],
        tenantId: string,
        requestInfo: RequestInfo | undefined,
        campaignStartDate: string,
        campaignEndDate: string
    ): Promise<RowsBySheetName> {
        if (!registers.length) {
            return rowsFromStoredData;
        }

        const mappedRegisterCodes = this.collectMappedRegisterServiceCodes(rowsFromStoredData);
        if (!mappedRegisterCodes.size) {
            return this.buildRowsFromAttendanceState(
                registers,
                tenantId,
                requestInfo,
                campaignStartDate,
                campaignEndDate
            );
        }

        const hasRegistersMissingFromStoredMappings = registers.some(
            (register) => !mappedRegisterCodes.has(register.serviceCode)
        );
        if (!hasRegistersMissingFromStoredMappings) {
            return rowsFromStoredData;
        }

        logger.info(
            `Stored attendee mappings cover ${mappedRegisterCodes.size}/${registers.length} registers; `
            + "supplementing remaining registers from attendance state"
        );
        const rowsFromAttendanceState = await this.buildRowsFromAttendanceState(
            registers,
            tenantId,
            requestInfo,
            campaignStartDate,
            campaignEndDate
        );

        return this.mergeRowsBySheetName(rowsFromStoredData, rowsFromAttendanceState);
    }

    private static collectMappedRegisterServiceCodes(rowsBySheetName: RowsBySheetName): Set<string> {
        const registerServiceCodes = new Set<string>();
        for (const sheetName of SHEET_NAMES) {
            for (const row of rowsBySheetName.get(sheetName) || []) {
                const hasMappedPerson = Boolean(this.firstNonBlank(
                    row[WORKER_ID_COLUMN],
                    row[USERNAME_COLUMN],
                    row[USER_NAME_COLUMN]
                ));
                if (!hasMappedPerson) continue;

                const registerServiceCode = this.firstNonBlank(
                    row[REGISTER_ID_COLUMN],
                    row[REGISTER_CODE_COLUMN]
                );
                if (!registerServiceCode) continue;
                registerServiceCodes.add(registerServiceCode);
            }
        }
        return registerServiceCodes;
    }

    private static mergeRowsBySheetName(
        primaryRowsBySheetName: RowsBySheetName,
        fallbackRowsBySheetName: RowsBySheetName
    ): RowsBySheetName {
        const dedupedRowsBySheetName = this.createEmptyDedupedRowsBySheetName();
        const mergeSourceRows = (rowsBySheetName: RowsBySheetName, preferIncomingValues: boolean) => {
            for (const sheetName of SHEET_NAMES) {
                const dedupedRows = dedupedRowsBySheetName.get(sheetName);
                if (!dedupedRows) continue;
                for (const row of rowsBySheetName.get(sheetName) || []) {
                    const dedupeKey = this.dedupeKeyForGeneratedRow(sheetName, row);
                    if (!dedupeKey) continue;
                    const existing = dedupedRows.get(dedupeKey);
                    if (!existing) {
                        dedupedRows.set(dedupeKey, { ...row });
                        continue;
                    }
                    const mergedRow = preferIncomingValues
                        ? this.mergeRows(row, existing)
                        : this.mergeRows(existing, row);
                    dedupedRows.set(dedupeKey, mergedRow);
                }
            }
        };

        mergeSourceRows(fallbackRowsBySheetName, false);
        mergeSourceRows(primaryRowsBySheetName, true);

        return this.flattenRowsBySheetName(dedupedRowsBySheetName);
    }

    private static dedupeKeyForGeneratedRow(sheetName: string, row: BulkRow): string | null {
        const registerServiceCode = this.firstNonBlank(
            row[REGISTER_ID_COLUMN],
            row[REGISTER_CODE_COLUMN]
        );
        if (!registerServiceCode) return null;
        const rowRecord = this.asRecord(row);
        const personIdentity = rowRecord
            ? this.personIdentity(rowRecord, "__seed__")
            : "__seed__";
        return `${registerServiceCode}::${sheetName}::${personIdentity}`;
    }

    private static async fetchCampaignRegisters(
        registerSearchReferenceId: string,
        campaignId: string,
        tenantId: string,
        localityCodes: string[],
        requestInfo?: RequestInfo
    ): Promise<RegisterData[]> {
        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo || {};
        const seen = new Set<string>();
        const registers: RegisterData[] = [];
        const effectiveLocalityCodes = Array.from(
            new Set(localityCodes.map((code) => String(code || "").trim()).filter(Boolean))
        );

        if (!effectiveLocalityCodes.length) {
            throwError(
                "CAMPAIGN",
                400,
                "LOCALITY_CODE_REQUIRED",
                `localityCode is required to search attendance registers for campaign ${campaignId}`
            );
        }

        for (const localityCode of effectiveLocalityCodes) {
            for (let offset = 0; offset <= 10000; offset += ATTENDANCE_REGISTER_SEARCH_LIMIT) {
                const response = await httpRequest(
                    url,
                    { RequestInfo },
                    {
                        tenantId,
                        referenceId: registerSearchReferenceId,
                        localityCode,
                        includeAttendee: true,
                        includeStaff: true,
                        limit: ATTENDANCE_REGISTER_SEARCH_LIMIT,
                        offset
                    }
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
        }

        return registers;
    }

    private static resolveRegisterSearchReferenceId(
        campaignId: string,
        campaign: any,
        additionalDetails?: Record<string, unknown>
    ): string {
        const campaignProjectId = this.asText(campaign?.projectId);
        if (campaignProjectId) {
            return campaignProjectId;
        }

        const requestProjectId = this.asText(additionalDetails?.projectId);
        if (requestProjectId) {
            return requestProjectId;
        }

        logger.warn(
            `Campaign ${campaignId} has no projectId; falling back to campaignId for attendance register search`
        );
        return campaignId;
    }

    private static resolveRegisterSearchLocalityCodes(
        campaign: any,
        additionalDetails?: Record<string, unknown>
    ): string[] {
        const codes = new Set<string>();

        this.addLocalityCode(codes, additionalDetails?.localityCode);
        const localityCodes = additionalDetails?.localityCodes;
        if (Array.isArray(localityCodes)) {
            for (const code of localityCodes) {
                this.addLocalityCode(codes, code);
            }
        }

        const campaignBoundaries = Array.isArray(campaign?.boundaries) ? campaign.boundaries : [];
        for (const boundary of campaignBoundaries) {
            this.addLocalityCode(codes, boundary?.code);
        }
        this.addLocalityCode(codes, campaign?.boundaryCode);

        return Array.from(codes);
    }

    private static addLocalityCode(codes: Set<string>, rawCode: unknown): void {
        const code = this.asText(rawCode);
        if (code) {
            codes.add(code);
        }
    }

    private static containsMappedRows(rowsBySheetName: RowsBySheetName): boolean {
        for (const sheetName of SHEET_NAMES) {
            const rows = rowsBySheetName.get(sheetName) || [];
            for (const row of rows) {
                if (this.firstNonBlank(
                    row[WORKER_ID_COLUMN],
                    row[USERNAME_COLUMN],
                    row[USER_NAME_COLUMN]
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private static resolveRegister(
        rawData: Record<string, unknown>,
        uniqueIdAfterProcess: string | null,
        registerByServiceCode: Map<string, RegisterData>,
        registerById: Map<string, RegisterData>
    ): RegisterData | null {
        const serviceCode = this.firstNonBlank(
            this.asText(rawData["_registerServiceCode"]),
            this.asText(rawData[REGISTER_ID_COLUMN]),
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
            const byServiceCode = registerByServiceCode.get(registerIdFromIdentity);
            if (byServiceCode) return byServiceCode;
        }

        return null;
    }

    private static resolveSheetName(rawData: Record<string, unknown>): string | null {
        const storedSheetName = this.asText(rawData["_sheetName"]);
        if (SHEET_NAMES.includes(storedSheetName)) return storedSheetName;

        const roleCodes = this.extractRoleCodes(rawData);
        if (roleCodes.some((role) => APPROVER_ROLE_CODES.has(role))) return APPROVER_SHEET;
        if (roleCodes.some((role) => MARKER_ROLE_CODES.has(role))) return MARKER_SHEET;
        return WORKER_SHEET;
    }

    private static buildMappedRow(
        register: RegisterData,
        sheetName: string,
        rawData: Record<string, unknown>,
        syncedDeenrollmentDate: number | null,
        campaignStartDate: string,
        campaignEndDate: string
    ): BulkRow {
        const row: BulkRow = {};
        for (const [key, value] of Object.entries(rawData)) {
            row[key] = this.asText(value);
        }

        delete row["_registerServiceCode"];
        delete row["_sheetName"];
        delete row["_denrollmentDate"];

        row[REGISTER_CODE_COLUMN] = register.serviceCode;
        row[REGISTER_NAME_COLUMN] = register.name;
        row[REGISTER_UUID_COLUMN] = register.id;
        row[REGISTER_ID_COLUMN] = this.firstNonBlank(row[REGISTER_ID_COLUMN], register.serviceCode);
        row[USER_NAME_COLUMN] = this.asText(row[USER_NAME_COLUMN]);
        row[WORKER_ID_COLUMN] = this.asText(row[WORKER_ID_COLUMN]);
        row[USERNAME_COLUMN] = this.asText(row[USERNAME_COLUMN]);
        row[PASSWORD_COLUMN] = this.asText(row[PASSWORD_COLUMN]);
        row[BOUNDARY_COLUMN] = this.firstNonBlank(
            row[BOUNDARY_COLUMN],
            row[BOUNDARY_CODE_MANDATORY_COLUMN],
            row[BOUNDARY_CODE_COLUMN],
            register.localityCode
        );
        row[ENROLLMENT_DATE_COLUMN] = this.firstNonBlank(
            campaignStartDate,
            this.normalizeSheetDateIfPresent(row[ENROLLMENT_DATE_COLUMN])
        );
        row[DEENROLLMENT_DATE_COLUMN] = this.firstNonBlank(
            this.normalizeSheetDateIfPresent(syncedDeenrollmentDate),
            this.normalizeSheetDateIfPresent(row[DEENROLLMENT_DATE_COLUMN]),
            campaignEndDate
        );

        if (sheetName === WORKER_SHEET) {
            row[TEAM_CODE_COLUMN] = this.asText(row[TEAM_CODE_COLUMN]);
        } else {
            delete row[TEAM_CODE_COLUMN];
        }

        return row;
    }

    private static buildAttendanceStateWorkerRow(
        register: RegisterData,
        attendee: AttendanceAttendeeRow,
        profile: IndividualProfile | undefined,
        personId: string,
        campaignStartDate: string,
        campaignEndDate: string
    ): BulkRow {
        const username = this.firstNonBlank(profile?.username, personId);
        return {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [REGISTER_ID_COLUMN]: register.serviceCode,
            [USER_NAME_COLUMN]: this.firstNonBlank(profile?.displayName, profile?.username, personId),
            [WORKER_ID_COLUMN]: personId,
            [USERNAME_COLUMN]: username,
            [PASSWORD_COLUMN]: "",
            [BOUNDARY_COLUMN]: register.localityCode,
            [BOUNDARY_CODE_MANDATORY_COLUMN]: register.localityCode,
            [ENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                campaignStartDate,
                this.formatEpochIfPresent(attendee?.enrollmentDate)
            ),
            [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                this.formatEpochIfPresent(attendee?.denrollmentDate),
                campaignEndDate
            ),
            [TEAM_CODE_COLUMN]: this.asText(attendee?.tag),
        };
    }

    private static buildAttendanceStateStaffRow(
        register: RegisterData,
        staff: AttendanceStaffRow,
        profile: IndividualProfile | undefined,
        personId: string,
        campaignStartDate: string,
        campaignEndDate: string
    ): BulkRow {
        const username = this.firstNonBlank(profile?.username, personId);
        return {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [REGISTER_ID_COLUMN]: register.serviceCode,
            [USER_NAME_COLUMN]: this.firstNonBlank(
                profile?.displayName,
                this.displayNameFromStaff(staff),
                profile?.username,
                personId
            ),
            [WORKER_ID_COLUMN]: personId,
            [USERNAME_COLUMN]: username,
            [PASSWORD_COLUMN]: "",
            [BOUNDARY_COLUMN]: register.localityCode,
            [BOUNDARY_CODE_MANDATORY_COLUMN]: register.localityCode,
            [ENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                campaignStartDate,
                this.formatEpochIfPresent(staff?.enrollmentDate)
            ),
            [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                this.formatEpochIfPresent(staff?.denrollmentDate),
                campaignEndDate
            ),
        };
    }

    private static buildSeedRow(register: RegisterData, sheetName: string): BulkRow {
        const seedRow: BulkRow = {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [REGISTER_ID_COLUMN]: register.serviceCode,
            [USER_NAME_COLUMN]: "",
            [WORKER_ID_COLUMN]: "",
            [USERNAME_COLUMN]: "",
            [PASSWORD_COLUMN]: "",
            [BOUNDARY_COLUMN]: register.localityCode,
            [BOUNDARY_CODE_MANDATORY_COLUMN]: register.localityCode,
            [ENROLLMENT_DATE_COLUMN]: "",
            [DEENROLLMENT_DATE_COLUMN]: "",
        };
        if (sheetName === WORKER_SHEET) {
            seedRow[TEAM_CODE_COLUMN] = "";
        }
        return seedRow;
    }

    private static ensureSeedRowsPerRegisterPerSheet(rowsBySheetName: RowsBySheetName, registers: RegisterData[]): void {
        for (const sheetName of SHEET_NAMES) {
            const rows = rowsBySheetName.get(sheetName) || [];
            const registersInSheet = new Set(rows.map((row) => this.firstNonBlank(
                row[REGISTER_CODE_COLUMN],
                row[REGISTER_ID_COLUMN]
            )));

            for (const register of registers) {
                if (registersInSheet.has(register.serviceCode)) continue;
                rows.push(this.buildSeedRow(register, sheetName));
            }

            rows.sort((left, right) => this.sortRows(left, right));
            rowsBySheetName.set(sheetName, rows);
        }
    }

    private static createEmptyDedupedRowsBySheetName(): DedupedRowsBySheetName {
        return new Map<string, Map<string, BulkRow>>(SHEET_NAMES.map((sheetName) => [sheetName, new Map<string, BulkRow>()]));
    }

    private static flattenRowsBySheetName(dedupedRowsBySheetName: DedupedRowsBySheetName): RowsBySheetName {
        const rowsBySheetName = new Map<string, BulkRow[]>();
        for (const sheetName of SHEET_NAMES) {
            const rows = Array.from(dedupedRowsBySheetName.get(sheetName)?.values() || []);
            rows.sort((left, right) => this.sortRows(left, right));
            rowsBySheetName.set(sheetName, rows);
        }
        return rowsBySheetName;
    }

    private static toSheetMap(rowsBySheetName: RowsBySheetName): SheetMap {
        const sheetMap: SheetMap = {};
        for (const sheetName of SHEET_NAMES) {
            sheetMap[sheetName] = {
                data: rowsBySheetName.get(sheetName) || [],
                dynamicColumns: this.registerColumns()
            };
        }
        return sheetMap;
    }

    private static registerColumns(): { [columnName: string]: ColumnProperties } {
        return {
            [REGISTER_CODE_COLUMN]: { orderNumber: 0.01, width: 22, freezeColumn: true, color: "#93c47d" },
            [REGISTER_NAME_COLUMN]: { orderNumber: 0.02, width: 36, color: "#93c47d" },
            [REGISTER_UUID_COLUMN]: { orderNumber: 0.03, width: 42, color: "#93c47d" },
            // Hidden helper column used by attendee ingest flow; keep out of visible bulk template to avoid duplicate "Register ID".
            [REGISTER_ID_COLUMN]: { hideColumn: true },
        };
    }

    private static sheetNameFromStaffType(staffType: unknown): string | null {
        const normalized = this.asText(staffType).toUpperCase();
        if (normalized === STAFF_TYPE_OWNER) return MARKER_SHEET;
        if (normalized === STAFF_TYPE_APPROVER) return APPROVER_SHEET;
        return null;
    }

    private static displayNameFromStaff(staff: AttendanceStaffRow): string {
        const additionalDetails = this.asRecord(staff?.additionalDetails);
        return this.firstNonBlank(
            this.asText(additionalDetails?.staffName),
            this.asText(additionalDetails?.ownerName)
        );
    }

    private static mergeRows(existing: BulkRow, incoming: BulkRow): BulkRow {
        const merged: BulkRow = { ...existing };
        for (const [key, value] of Object.entries(incoming)) {
            merged[key] = this.firstNonBlank(existing[key], value);
        }
        return merged;
    }

    private static extractRoleCodes(rawData: Record<string, unknown>): string[] {
        const roles = new Set<string>();
        const baseRole = this.asText(rawData[ROLE_COLUMN]);
        if (baseRole) {
            for (const role of baseRole.split(",")) {
                const normalizedRole = this.asText(role).toUpperCase();
                if (normalizedRole) roles.add(normalizedRole);
            }
        }
        for (let i = 1; i <= MAX_ROLE_COLUMNS; i++) {
            const role = this.asText(rawData[`HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_${i}`]).toUpperCase();
            if (role) roles.add(role);
        }
        return Array.from(roles);
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

    private static formatEpochIfPresent(value: unknown): string {
        if (value === null || value === undefined || value === "") return "";
        const asNumber = Number(value);
        if (!Number.isFinite(asNumber)) return "";
        return formatEpochAsSheetDate(asNumber);
    }

    private static normalizeSheetDateIfPresent(value: unknown): string {
        const formattedFromEpoch = this.formatEpochIfPresent(value);
        if (formattedFromEpoch) return formattedFromEpoch;

        const raw = this.asText(value);
        if (!raw) return "";

        if (DASH_DATE_REGEX.test(raw)) return raw;

        const slashDate = SLASH_DATE_REGEX.exec(raw);
        if (slashDate) return `${slashDate[1]}-${slashDate[2]}-${slashDate[3]}`;

        if (ISO_DATE_PREFIX_REGEX.test(raw)) {
            const parsedEpoch = Date.parse(raw);
            if (Number.isFinite(parsedEpoch)) {
                return formatEpochAsSheetDate(parsedEpoch);
            }
        }

        return raw;
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

    private static asRecord(value: unknown): Record<string, any> | null {
        if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
        return value as Record<string, any>;
    }
}
