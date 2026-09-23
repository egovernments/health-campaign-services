import config from "../config";
import { attendanceColumnKeys, attendanceSheetNames, attendanceStaffTypes, dataRowStatuses } from "../config/constants";
import { RequestInfo } from "../config/models/requestInfoSchema";
import { CampaignDataRow } from "../config/models/campaignDataRow";
import { ColumnProperties, SheetMap } from "../models/SheetMap";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { formatEpochAsSheetDate } from "../utils/attendanceIdentityUtils";
import { getRelatedDataWithCampaign, throwError } from "../utils/genericUtils";
import { logger } from "../utils/logger";
import { httpRequest } from "../utils/request";
import { decrypt } from "../utils/cryptUtils";

const INDIVIDUAL_SEARCH_BATCH_SIZE = 100;
const MAX_ROLE_COLUMNS = 5;
const ATTENDEE_DATA_TYPE = "attendanceRegisterAttendee";
const USER_DATA_TYPE = "user";

const WORKER_SHEET = attendanceSheetNames.WORKER;
const MARKER_SHEET = attendanceSheetNames.MARKER;
const APPROVER_SHEET = attendanceSheetNames.APPROVER;

const SHEET_NAMES = [WORKER_SHEET, MARKER_SHEET, APPROVER_SHEET];
const NON_WORKER_SHEETS = [MARKER_SHEET, APPROVER_SHEET];

const MARKER_ROLE_CODES = new Set([
    "WAREHOUSE_MANAGER",
    "TEAM_SUPERVISOR",
]);

const APPROVER_ROLE_CODES = new Set([
    "PROXIMITY_SUPERVISOR",
    "CAMPAIGN_SUPERVISOR",
]);

const WORKER_ROLE_CODES = new Set([
    "DISTRIBUTOR",
    "REGISTRAR",
    "FIELD_SUPPORT",
    "HEALTH_FACILITY_WORKER",
]);
const DEFAULT_MARKER_STAFF_ROLE_CODE = "TEAM_SUPERVISOR";
const DEFAULT_APPROVER_STAFF_ROLE_CODE = "PROXIMITY_SUPERVISOR";

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
const ONE_DAY_MS = 24 * 60 * 60 * 1000;

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
interface AttendanceStateBuildMeta {
    skippedRequesterOwnerNoRolesRegisterKeys: Set<string>;
}

/**
 * Generates a bulk mapping workbook with the same 3 attendee tabs and columns,
 * while prepending register metadata columns on each sheet.
 *
 * Rows are loaded from persisted attendee mappings when present; otherwise they
 * are reconstructed from live attendance state.
 */
export class TemplateClass {
    static async generate(
        templateConfig: any,
        responseToSend: any,
        localizationMap: Record<string, string> = {}
    ): Promise<SheetMap> {
        logger.info("Generating attendance register user bulk mapping template...");

        const tenantId = String(responseToSend?.tenantId || "").trim();
        const campaignId = String(responseToSend?.campaignId || "").trim();

        if (!tenantId || !campaignId) {
            throwError("CAMPAIGN", 400, "VALIDATION_ERROR", "tenantId and campaignId are required for bulk mapping generation");
        }

        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaign = campaignResp?.CampaignDetails?.[0];
        logger.info("ENROLL-DEBUG " + JSON.stringify({
            topStart: campaign?.startDate,
            addlStart: campaign?.additionalDetails?.startDate,
            created: campaign?.auditDetails?.createdTime ?? campaign?.createdTime,
            cycleStarts: (campaign?.deliveryRules || []).flatMap((r: any) => (r?.cycles || []).map((c: any) => c?.startDate)),
        }));
        if (!campaign) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NOT_FOUND", "Campaign not found");
        }

        const campaignNumber = String(campaign?.campaignNumber || "").trim();
        const hierarchyType = this.firstNonBlank(
            this.asText(campaign?.hierarchyType),
            this.asText(responseToSend?.hierarchyType)
        );
        if (!campaignNumber) {
            throwError("CAMPAIGN", 400, "CAMPAIGN_NUMBER_MISSING", `Campaign ${campaignId} has no campaignNumber set`);
        }

        const campaignStartDate = this.resolveCampaignStartDate(campaign);
        logger.info(`ENROLL-DEBUG resolved campaignStartDate=${campaignStartDate}`);
        const campaignEndDate = this.resolveCampaignEndDate(campaign);

        const localityCodes = this.resolveRegisterSearchLocalityCodes(responseToSend?.additionalDetails);
        const registers = await this.fetchCampaignRegisters(
            tenantId,
            String(responseToSend?.hierarchyType || "").trim(),
            campaignNumber,
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
        const campaignUserRows = await getRelatedDataWithCampaign(
            USER_DATA_TYPE,
            campaignNumber,
            tenantId,
            dataRowStatuses.completed
        ) as CampaignDataRow[];
        this.logBulkTrace("generation-input-summary", {
            tenantId,
            campaignId,
            campaignNumber,
            hierarchyType,
            localityCodesRequested: localityCodes,
            registerCount: registers.length,
            registerSamples: this.summarizeRegisters(registers),
            attendeeRowsCount: Array.isArray(attendeeRows) ? attendeeRows.length : 0,
            campaignUserRowsCount: Array.isArray(campaignUserRows) ? campaignUserRows.length : 0,
        });
        const rowsFromStoredData = this.buildRowsFromStoredMappings(
            registers,
            Array.isArray(attendeeRows) ? attendeeRows : [],
            registerByServiceCode,
            registerById,
            campaignStartDate,
            campaignEndDate,
            localizationMap
        );

        const outputRowsBySheetName = await this.buildOutputRowsBySheetName(
            rowsFromStoredData,
            Array.isArray(campaignUserRows) ? campaignUserRows : [],
            registers,
            tenantId,
            hierarchyType,
            templateConfig,
            campaignNumber,
            responseToSend?.requestInfo,
            campaignStartDate,
            campaignEndDate,
            localizationMap
        );

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
        campaignEndDate: string,
        localizationMap: Record<string, string>
    ): RowsBySheetName {
        const dedupedRowsBySheetName = this.createEmptyDedupedRowsBySheetName();
        const hasStampedRowsByServiceCode = this.collectHasStampedRowsByServiceCode(attendeeRows);
        let skippedMissingRawData = 0;
        let skippedRegisterResolution = 0;
        let skippedRegisterInstance = 0;
        let skippedSheetResolution = 0;
        let acceptedRows = 0;
        const acceptedNonWorkerSamples: Record<string, string>[] = [];

        for (const attendeeRow of attendeeRows) {
            if (attendeeRow?.isDeleted) continue;

            const rawData = this.asRecord(attendeeRow?.data);
            if (!rawData) {
                skippedMissingRawData++;
                continue;
            }

            const register = this.resolveRegister(rawData, attendeeRow?.uniqueIdAfterProcess, registerByServiceCode, registerById);
            if (!register) {
                skippedRegisterResolution++;
                continue;
            }

            const rowServiceCode = this.storedRowServiceCode(rawData, register.serviceCode);
            if (!this.belongsToRegisterInstance(
                register,
                attendeeRow?.uniqueIdAfterProcess,
                hasStampedRowsByServiceCode.get(rowServiceCode) === true
            )) {
                skippedRegisterInstance++;
                continue;
            }

            const sheetName = this.resolveSheetName(rawData);
            if (!sheetName) {
                skippedSheetResolution++;
                continue;
            }

            const row = this.buildMappedRow(
                register,
                sheetName,
                rawData,
                attendeeRow?.denrollmentDate,
                campaignStartDate,
                campaignEndDate,
                localizationMap
            );
            const dedupeKey = `${this.registerIdentity(register)}::${sheetName}::${this.personIdentity(rawData, attendeeRow?.uniqueIdentifier)}`;

            const sheetRows = dedupedRowsBySheetName.get(sheetName);
            if (!sheetRows) continue;
            const existing = sheetRows.get(dedupeKey);
            sheetRows.set(dedupeKey, existing ? this.mergeRows(existing, row) : row);
            acceptedRows++;
            if (sheetName !== WORKER_SHEET && acceptedNonWorkerSamples.length < 30) {
                acceptedNonWorkerSamples.push({
                    registerCode: this.asText(row[REGISTER_CODE_COLUMN]),
                    registerUuid: this.asText(row[REGISTER_UUID_COLUMN]),
                    sheetName,
                    workerId: this.asText(row[WORKER_ID_COLUMN]),
                    userName: this.asText(row[USER_NAME_COLUMN]),
                    username: this.asText(row[USERNAME_COLUMN]),
                });
            }
        }

        const rowsBySheetName = this.flattenRowsBySheetName(dedupedRowsBySheetName);
        this.logBulkTrace("stored-rows-build-summary", {
            totalInputRows: Array.isArray(attendeeRows) ? attendeeRows.length : 0,
            totalRegisters: registers.length,
            acceptedRows,
            skippedMissingRawData,
            skippedRegisterResolution,
            skippedRegisterInstance,
            skippedSheetResolution,
            rowSummary: this.summarizeRowsBySheetName(rowsBySheetName),
            acceptedNonWorkerSamples,
        });
        return rowsBySheetName;
    }

    private static restrictCampaignFallbackToWorkerAndApproverRows(rowsBySheetName: RowsBySheetName): RowsBySheetName {
        return new Map<string, BulkRow[]>([
            [WORKER_SHEET, rowsBySheetName.get(WORKER_SHEET) || []],
            [MARKER_SHEET, []],
            [APPROVER_SHEET, rowsBySheetName.get(APPROVER_SHEET) || []],
        ]);
    }

    private static async buildRowsFromAttendanceState(
        registers: RegisterData[],
        tenantId: string,
        campaignNumber: string,
        requestInfo: RequestInfo | undefined,
        requestingUsername: string,
        campaignStartDate: string,
        campaignEndDate: string,
        localizationMap: Record<string, string>,
        buildMeta?: AttendanceStateBuildMeta
    ): Promise<RowsBySheetName> {
        const dedupedRowsBySheetName = this.createEmptyDedupedRowsBySheetName();
        if (!registers.length) return this.flattenRowsBySheetName(dedupedRowsBySheetName);
        const normalizedRequestingUsername = this.asText(requestingUsername).toUpperCase();
        const staffResolutionSamples: Record<string, string>[] = [];
        let staffRowsSeen = 0;
        let skippedStaffWithoutPersonId = 0;
        let skippedDuplicateStaffIdentity = 0;
        let skippedStaffWithoutSheetResolution = 0;
        let skippedRequesterOwnerWithoutRoles = 0;
        let markerRowsBuilt = 0;
        let approverRowsBuilt = 0;
        const perRegisterBuildStats = new Map<string, {
            registerCode: string;
            registerUuid: string;
            attendeeRowsBuilt: number;
            markerRowsBuilt: number;
            approverRowsBuilt: number;
            staffRowsSeen: number;
            skippedStaffWithoutPersonId: number;
            skippedDuplicateStaffIdentity: number;
            skippedStaffWithoutSheetResolution: number;
            skippedRequesterOwnerWithoutRoles: number;
        }>();

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
        const roleCodesByIndividualId = await this.fetchRoleCodesByIndividualId(
            campaignNumber,
            tenantId,
            Array.from(personIds),
            requestInfo
        );

        for (const register of registers) {
            const registerKey = this.registerIdentity(register);
            const perRegister = perRegisterBuildStats.get(registerKey) || {
                registerCode: register.serviceCode,
                registerUuid: register.id,
                attendeeRowsBuilt: 0,
                markerRowsBuilt: 0,
                approverRowsBuilt: 0,
                staffRowsSeen: 0,
                skippedStaffWithoutPersonId: 0,
                skippedDuplicateStaffIdentity: 0,
                skippedStaffWithoutSheetResolution: 0,
                skippedRequesterOwnerWithoutRoles: 0,
            };
            perRegisterBuildStats.set(registerKey, perRegister);
            const attendeeByPersonId = new Map<string, AttendanceAttendeeRow>();
            for (const attendee of register.attendees || []) {
                const personId = this.asText(attendee?.individualId);
                if (!personId || attendeeByPersonId.has(personId)) continue;
                attendeeByPersonId.set(personId, attendee);
            }
            const staffByPersonId = new Map<string, AttendanceStaffRow>();
            for (const staff of register.staff || []) {
                const personId = this.asText(staff?.userId);
                if (!personId || staffByPersonId.has(personId)) continue;
                staffByPersonId.set(personId, staff);
            }

            for (const [personId, attendee] of attendeeByPersonId.entries()) {
                const profile = profiles.get(personId);
                const roleCodes = this.resolveAttendanceWorkerRoleCodes(
                    roleCodesByIndividualId.get(personId) || []
                );
                const dedupeKey = `${registerKey}::${WORKER_SHEET}::${personId}`;
                const row = this.buildAttendanceStateWorkerRow(
                    register,
                    attendee,
                    staffByPersonId.get(personId),
                    profile,
                    personId,
                    roleCodes,
                    campaignStartDate,
                    campaignEndDate,
                    localizationMap
                );
                dedupedRowsBySheetName.get(WORKER_SHEET)?.set(dedupeKey, row);
                perRegister.attendeeRowsBuilt++;
            }

            const seenStaffIdentities = new Set<string>();
            for (const staff of register.staff || []) {
                staffRowsSeen++;
                perRegister.staffRowsSeen++;
                const personId = this.asText(staff?.userId);
                if (!personId) {
                    skippedStaffWithoutPersonId++;
                    perRegister.skippedStaffWithoutPersonId++;
                    continue;
                }
                const staffType = this.asText(staff?.staffType).toUpperCase();
                const staffIdentity = `${personId}::${staffType || "__UNKNOWN__"}`;
                if (seenStaffIdentities.has(staffIdentity)) {
                    skippedDuplicateStaffIdentity++;
                    perRegister.skippedDuplicateStaffIdentity++;
                    continue;
                }
                seenStaffIdentities.add(staffIdentity);

                const profile = profiles.get(personId);
                const roleCodes = roleCodesByIndividualId.get(personId) || [];
                const profileUsername = this.asText(profile?.username).toUpperCase();
                const skipRequesterOwnerWithoutRoles = Boolean(normalizedRequestingUsername)
                    && staffType === attendanceStaffTypes.OWNER
                    && roleCodes.length === 0
                    && profileUsername === normalizedRequestingUsername;
                if (skipRequesterOwnerWithoutRoles) {
                    skippedRequesterOwnerWithoutRoles++;
                    perRegister.skippedRequesterOwnerWithoutRoles++;
                    buildMeta?.skippedRequesterOwnerNoRolesRegisterKeys.add(registerKey);
                    if (staffResolutionSamples.length < 50) {
                        staffResolutionSamples.push({
                            registerCode: register.serviceCode,
                            registerUuid: register.id,
                            personId,
                            username: this.firstNonBlank(profile?.username, personId),
                            staffType,
                            resolvedSheet: "SKIPPED_REQUESTING_OWNER_NO_ROLES",
                            roleCodes: "",
                            resolvedRoleCodes: "",
                        });
                    }
                    continue;
                }

                const staffSheet = this.resolveAttendanceStaffSheet(
                    staffType,
                    roleCodes
                );
                if (staffResolutionSamples.length < 50) {
                    staffResolutionSamples.push({
                        registerCode: register.serviceCode,
                        registerUuid: register.id,
                        personId,
                        username: this.firstNonBlank(profile?.username, personId),
                        staffType,
                        resolvedSheet: staffSheet?.sheetName || "SKIPPED",
                        roleCodes: roleCodes.join(","),
                        resolvedRoleCodes: (staffSheet?.roleCodes || []).join(","),
                    });
                }
                if (!staffSheet) {
                    skippedStaffWithoutSheetResolution++;
                    perRegister.skippedStaffWithoutSheetResolution++;
                    continue;
                }

                const dedupeKey = `${registerKey}::${staffSheet.sheetName}::${personId}`;
                const row = this.buildAttendanceStateStaffRow(
                    register,
                    staff,
                    attendeeByPersonId.get(personId),
                    profile,
                    personId,
                    staffSheet.roleCodes,
                    campaignStartDate,
                    campaignEndDate,
                    localizationMap
                );
                dedupedRowsBySheetName.get(staffSheet.sheetName)?.set(dedupeKey, row);
                if (staffSheet.sheetName === MARKER_SHEET) {
                    markerRowsBuilt++;
                    perRegister.markerRowsBuilt++;
                }
                if (staffSheet.sheetName === APPROVER_SHEET) {
                    approverRowsBuilt++;
                    perRegister.approverRowsBuilt++;
                }
            }
        }

        const rowsBySheetName = this.flattenRowsBySheetName(dedupedRowsBySheetName);
        this.logBulkTrace("attendance-state-build-summary", {
            campaignNumber,
            registerCount: registers.length,
            staffRowsSeen,
            skippedStaffWithoutPersonId,
            skippedDuplicateStaffIdentity,
            skippedStaffWithoutSheetResolution,
            skippedRequesterOwnerWithoutRoles,
            markerRowsBuilt,
            approverRowsBuilt,
            rowSummary: this.summarizeRowsBySheetName(rowsBySheetName),
            perRegisterBuildStats: Array.from(perRegisterBuildStats.values()).slice(0, 200),
            staffResolutionSamples,
        });
        return rowsBySheetName;
    }

    private static async buildOutputRowsBySheetName(
        rowsFromStoredData: RowsBySheetName,
        campaignUserRows: CampaignDataRow[],
        registers: RegisterData[],
        tenantId: string,
        hierarchyType: string,
        templateConfig: any,
        campaignNumber: string,
        requestInfo: RequestInfo | undefined,
        campaignStartDate: string,
        campaignEndDate: string,
        localizationMap: Record<string, string>
    ): Promise<RowsBySheetName> {
        if (!registers.length) {
            return rowsFromStoredData;
        }

        const attendanceStateBuildMeta: AttendanceStateBuildMeta = {
            skippedRequesterOwnerNoRolesRegisterKeys: new Set<string>()
        };

        const rowsFromCampaignUsers = await this.buildRowsFromCampaignUsers(
            registers,
            campaignUserRows,
            campaignStartDate,
            campaignEndDate,
            tenantId,
            hierarchyType,
            templateConfig,
            requestInfo,
            localizationMap
        );

        const rowsFromAttendanceState = await this.buildRowsFromAttendanceState(
            registers,
            tenantId,
            campaignNumber,
            requestInfo,
            this.asText((requestInfo as any)?.userInfo?.userName),
            campaignStartDate,
            campaignEndDate,
            localizationMap,
            attendanceStateBuildMeta
        );
        this.logBulkTrace("rows-from-attendance-state", {
            rowSummary: this.summarizeRowsBySheetName(rowsFromAttendanceState)
        });

        const workerRowsFromAttendanceState = this.sliceRowsBySheets(rowsFromAttendanceState, [WORKER_SHEET]);
        const nonWorkerRowsFromAttendanceState = this.sliceRowsBySheets(rowsFromAttendanceState, NON_WORKER_SHEETS);
        const workerRowsFromStoredData = this.sliceRowsBySheets(rowsFromStoredData, [WORKER_SHEET]);
        const nonWorkerRowsFromStoredData = this.dropRolelessNonWorkerRows(
            this.sliceRowsBySheets(rowsFromStoredData, NON_WORKER_SHEETS)
        );
        this.logBulkTrace("rows-from-stored-data", {
            rowSummary: this.summarizeRowsBySheetName(rowsFromStoredData),
            nonWorkerRowSummaryAfterRoleFilter: this.summarizeRowsBySheetName(nonWorkerRowsFromStoredData)
        });

        const workerRowsFromRegisterMappings = this.mergeRowsBySheetName(
            workerRowsFromAttendanceState,
            this.filterRowsForMissingRegisterSheets(
                workerRowsFromStoredData,
                workerRowsFromAttendanceState
            )
        );
        const nonWorkerRowsFromRegisterMappings = this.mergeRowsBySheetName(
            nonWorkerRowsFromStoredData,
            this.filterRowsForMissingRegisterSheets(
                nonWorkerRowsFromAttendanceState,
                nonWorkerRowsFromStoredData
            )
        );
        this.logBulkTrace("rows-after-register-mapping-merge", {
            workerRowSummary: this.summarizeRowsBySheetName(workerRowsFromRegisterMappings),
            nonWorkerRowSummary: this.summarizeRowsBySheetName(nonWorkerRowsFromRegisterMappings)
        });

        const rowsFromRegisterMappings = this.mergeRowsBySheetName(
            workerRowsFromRegisterMappings,
            nonWorkerRowsFromRegisterMappings
        );
        const campaignRowsForMissingRegisterSheets = this.filterRowsForMissingRegisterSheets(
            rowsFromCampaignUsers,
            rowsFromRegisterMappings
        );
        const campaignWorkerRowsForUnmappedRegisterSheets = this.restrictCampaignFallbackToWorkerAndApproverRows(
            campaignRowsForMissingRegisterSheets
        );
        const campaignNonWorkerRowsForSkippedRequesterOwnerRegisters = this.pickCampaignNonWorkerFallbackRows(
            campaignRowsForMissingRegisterSheets,
            attendanceStateBuildMeta.skippedRequesterOwnerNoRolesRegisterKeys,
            this.asText((requestInfo as any)?.userInfo?.userName)
        );
        const campaignRowsForUnmappedRegisterSheets = this.mergeRowsBySheetName(
            campaignWorkerRowsForUnmappedRegisterSheets,
            campaignNonWorkerRowsForSkippedRequesterOwnerRegisters
        );
        this.logBulkTrace("rows-from-campaign-user-fallback", {
            rowSummary: this.summarizeRowsBySheetName(campaignRowsForUnmappedRegisterSheets),
            skippedRequesterOwnerNoRolesRegisterKeys: Array.from(
                attendanceStateBuildMeta.skippedRequesterOwnerNoRolesRegisterKeys
            )
        });

        if (this.containsMappedRows(rowsFromRegisterMappings)) {
            const mergedRows = this.mergeRowsBySheetName(rowsFromRegisterMappings, campaignRowsForUnmappedRegisterSheets);
            this.logBulkTrace("final-rows-from-register-mapping", {
                rowSummary: this.summarizeRowsBySheetName(mergedRows)
            });
            return mergedRows;
        }

        if (this.containsMappedRows(campaignRowsForUnmappedRegisterSheets)) {
            this.logBulkTrace("final-rows-from-campaign-fallback", {
                rowSummary: this.summarizeRowsBySheetName(campaignRowsForUnmappedRegisterSheets)
            });
            return campaignRowsForUnmappedRegisterSheets;
        }

        this.logBulkTrace("final-rows-from-attendance-state", {
            rowSummary: this.summarizeRowsBySheetName(rowsFromAttendanceState)
        });
        return rowsFromAttendanceState;
    }

    private static async buildRowsFromCampaignUsers(
        registers: RegisterData[],
        campaignUserRows: CampaignDataRow[],
        campaignStartDate: string,
        campaignEndDate: string,
        tenantId: string,
        hierarchyType: string,
        templateConfig: any,
        requestInfo: RequestInfo | undefined,
        localizationMap: Record<string, string>
    ): Promise<RowsBySheetName> {
        const dedupedRowsBySheetName = this.createEmptyDedupedRowsBySheetName();
        if (!registers.length || !campaignUserRows.length) {
            return this.flattenRowsBySheetName(dedupedRowsBySheetName);
        }

        let campaignUserRowsSeen = 0;
        let skippedDeletedRows = 0;
        let skippedMissingRawData = 0;
        let skippedUnknownPerson = 0;
        let skippedUnclassifiedRole = 0;
        let skippedMissingBoundaryCode = 0;
        let skippedBoundaryMismatch = 0;
        let acceptedRows = 0;
        const acceptedRowsBySheet: Record<string, number> = {
            [WORKER_SHEET]: 0,
            [MARKER_SHEET]: 0,
            [APPROVER_SHEET]: 0,
        };

        const normalizedCampaignUsers = campaignUserRows.filter((entry) =>
            this.asText(entry?.type).toLowerCase() === USER_DATA_TYPE
        );
        if (!normalizedCampaignUsers.length) {
            return this.flattenRowsBySheetName(dedupedRowsBySheetName);
        }

        const getBoundaryFilter = (sheetName: string) =>
            templateConfig?.sheets?.find((sheet: any) => sheet.sheetName === sheetName)?.boundaryFilter;
        const allowedCodesCache = new Map<string, Set<string>>();
        const getAllowedCodes = async (sheetName: string, localityCode: string): Promise<Set<string>> => {
            const cacheKey = `${sheetName}::${localityCode}`;
            const cached = allowedCodesCache.get(cacheKey);
            if (cached) return cached;
            const allowedCodes = await this.resolveAllowedBoundaryCodes(
                tenantId,
                hierarchyType,
                localityCode,
                getBoundaryFilter(sheetName),
                requestInfo
            );
            allowedCodesCache.set(cacheKey, allowedCodes);
            return allowedCodes;
        };

        for (const register of registers) {
            const registerKey = this.registerIdentity(register);
            for (const userEntry of normalizedCampaignUsers) {
                campaignUserRowsSeen++;
                if (userEntry?.isDeleted) {
                    skippedDeletedRows++;
                    continue;
                }
                const rawData = this.asRecord(userEntry?.data);
                if (!rawData) {
                    skippedMissingRawData++;
                    continue;
                }

                const personId = this.personIdentity(rawData, "__unknown__");
                if (personId === "__unknown__") {
                    skippedUnknownPerson++;
                    continue;
                }

                const sheetName = this.classifyCampaignUserToSheet(rawData);
                if (!sheetName) {
                    skippedUnclassifiedRole++;
                    continue;
                }
                const boundaryCode = this.firstNonBlank(
                    this.asText(rawData[BOUNDARY_CODE_MANDATORY_COLUMN]),
                    this.asText(rawData[BOUNDARY_CODE_COLUMN])
                );
                if (!boundaryCode) {
                    skippedMissingBoundaryCode++;
                    continue;
                }
                const allowedCodes = await getAllowedCodes(sheetName, register.localityCode);
                if (!allowedCodes.has(boundaryCode)) {
                    skippedBoundaryMismatch++;
                    continue;
                }

                const dedupeKey = `${registerKey}::${sheetName}::${personId}`;
                const row = this.buildCampaignUserRow(
                    register,
                    sheetName,
                    rawData,
                    campaignStartDate,
                    campaignEndDate,
                    localizationMap
                );
                dedupedRowsBySheetName.get(sheetName)?.set(dedupeKey, row);
                acceptedRows++;
                acceptedRowsBySheet[sheetName] = (acceptedRowsBySheet[sheetName] || 0) + 1;
            }
        }

        const rowsBySheetName = this.flattenRowsBySheetName(dedupedRowsBySheetName);
        this.logBulkTrace("campaign-user-build-summary", {
            registerCount: registers.length,
            campaignUserRowsSeen,
            acceptedRows,
            acceptedRowsBySheet,
            skippedDeletedRows,
            skippedMissingRawData,
            skippedUnknownPerson,
            skippedUnclassifiedRole,
            skippedMissingBoundaryCode,
            skippedBoundaryMismatch,
            rowSummary: this.summarizeRowsBySheetName(rowsBySheetName),
        });
        return rowsBySheetName;
    }

    private static collectMappedRegisterSheetKeys(rowsBySheetName: RowsBySheetName): Set<string> {
        const registerSheetKeys = new Set<string>();
        for (const sheetName of SHEET_NAMES) {
            for (const row of rowsBySheetName.get(sheetName) || []) {
                const hasMappedPerson = Boolean(this.firstNonBlank(
                    row[WORKER_ID_COLUMN],
                    row[USERNAME_COLUMN],
                    row[USER_NAME_COLUMN]
                ));
                if (!hasMappedPerson) continue;
                const registerKey = this.rowRegisterKey(row);
                if (!registerKey) continue;
                registerSheetKeys.add(`${sheetName}::${registerKey}`);
            }
        }
        return registerSheetKeys;
    }

    private static filterRowsForMissingRegisterSheets(
        rowsBySheetName: RowsBySheetName,
        preferredRowsBySheetName: RowsBySheetName
    ): RowsBySheetName {
        const mappedRegisterSheetKeys = this.collectMappedRegisterSheetKeys(preferredRowsBySheetName);
        const filteredRowsBySheetName = new Map<string, BulkRow[]>();
        for (const sheetName of SHEET_NAMES) {
            const filteredRows = (rowsBySheetName.get(sheetName) || []).filter((row) => {
                const registerKey = this.rowRegisterKey(row);
                if (!registerKey) return false;
                return !mappedRegisterSheetKeys.has(`${sheetName}::${registerKey}`);
            });
            filteredRowsBySheetName.set(sheetName, filteredRows);
        }
        return filteredRowsBySheetName;
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
        const registerKey = this.rowRegisterKey(row);
        if (!registerKey) return null;
        const rowRecord = this.asRecord(row);
        const personIdentity = rowRecord
            ? this.personIdentity(rowRecord, "__seed__")
            : "__seed__";
        return `${registerKey}::${sheetName}::${personIdentity}`;
    }

    private static rowRegisterKey(row: BulkRow): string {
        return this.firstNonBlank(
            row[REGISTER_UUID_COLUMN],
            row[REGISTER_ID_COLUMN],
            row[REGISTER_CODE_COLUMN]
        );
    }

    private static async fetchCampaignRegisters(
        tenantId: string,
        hierarchyType: string,
        campaignNumber: string,
        localityCodes: string[],
        requestInfo?: RequestInfo
    ): Promise<RegisterData[]> {
        this.logBulkTrace("register-fetch-start", {
            tenantId,
            campaignNumber,
            hierarchyType,
            localityCodesRequested: localityCodes,
        });
        // Bulk download must always include the full campaign register set.
        // Locality-derived discovery is treated as a supplemental source only.
        const campaignWideRegisters = await this.searchRegistersByCampaignNumber(
            tenantId,
            campaignNumber,
            requestInfo
        );

        const effectiveLocalityCodes = Array.from(
            new Set(localityCodes.map((code) => String(code || "").trim()).filter(Boolean))
        );

        if (!effectiveLocalityCodes.length) {
            this.logBulkTrace("register-fetch-complete", {
                mode: "campaignWideOnly",
                campaignWideRegisterCount: campaignWideRegisters.length,
                finalRegisterCount: campaignWideRegisters.length,
                registerSamples: this.summarizeRegisters(campaignWideRegisters),
            });
            return campaignWideRegisters;
        }

        const subtreeCodes = await this.resolveLocalitySubtreeCodes(
            tenantId,
            hierarchyType,
            effectiveLocalityCodes,
            requestInfo
        );
        const referenceIds = await this.resolveProjectIdsForBoundaries(campaignNumber, tenantId, subtreeCodes);

        if (!referenceIds.length) {
            logger.info(
                `No created projects found under locality code(s) ${effectiveLocalityCodes.join(", ")} `
                + `for campaign ${campaignNumber}; using campaign-wide register discovery`
            );
            this.logBulkTrace("register-fetch-complete", {
                mode: "campaignWideFallback_noReferenceIds",
                campaignWideRegisterCount: campaignWideRegisters.length,
                effectiveLocalityCodes,
                subtreeCodeCount: subtreeCodes.length,
                finalRegisterCount: campaignWideRegisters.length,
                registerSamples: this.summarizeRegisters(campaignWideRegisters),
            });
            return campaignWideRegisters;
        }

        const localityScopedRegisters = await this.searchRegistersByReferenceIds(tenantId, referenceIds, requestInfo);
        const merged = this.mergeRegisterSets(campaignWideRegisters, localityScopedRegisters);
        this.logBulkTrace("register-fetch-complete", {
            mode: "campaignWidePlusLocality",
            campaignWideRegisterCount: campaignWideRegisters.length,
            localityScopedRegisterCount: localityScopedRegisters.length,
            effectiveLocalityCodes,
            subtreeCodeCount: subtreeCodes.length,
            referenceIdCount: referenceIds.length,
            finalRegisterCount: merged.length,
            registerSamples: this.summarizeRegisters(merged),
        });
        return merged;
    }

    private static mergeRegisterSets(
        primary: RegisterData[],
        secondary: RegisterData[]
    ): RegisterData[] {
        const merged: RegisterData[] = [];
        const seen = new Set<string>();
        const addRegister = (register: RegisterData) => {
            const id = this.asText(register?.id);
            const serviceCode = this.asText(register?.serviceCode);
            if (!id || !serviceCode) return;
            const key = `${id}::${serviceCode}`;
            if (seen.has(key)) return;
            seen.add(key);
            merged.push(register);
        };

        for (const register of primary || []) addRegister(register);
        for (const register of secondary || []) addRegister(register);

        return merged;
    }

    /** Expands each requested locality to itself plus every descendant boundary code, so registers created below it are still found. */
    private static async resolveLocalitySubtreeCodes(
        tenantId: string,
        hierarchyType: string,
        localityCodes: string[],
        requestInfo?: RequestInfo
    ): Promise<string[]> {
        const codes = new Set<string>(localityCodes);

        for (const localityCode of localityCodes) {
            const response: any = await searchBoundaryRelationshipData(
                tenantId,
                hierarchyType,
                true,
                false,
                true,
                localityCode,
                requestInfo
            );
            this.collectBoundaryCodes(response?.TenantBoundary?.[0]?.boundary, codes);
        }

        logger.info(`Resolved ${codes.size} boundary code(s) under ${localityCodes.length} requested locality code(s)`);
        return Array.from(codes);
    }

    private static collectBoundaryCodes(nodes: any, codes: Set<string>): void {
        if (!Array.isArray(nodes)) return;
        for (const node of nodes) {
            const code = this.asText(node?.code);
            if (code) codes.add(code);
            this.collectBoundaryCodes(node?.children, codes);
        }
    }

    private static async resolveAllowedBoundaryCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string,
        filter: any,
        requestInfo?: RequestInfo
    ): Promise<Set<string>> {
        if (!filter || !filter.mode) return new Set([localityCode]);
        if (filter.mode === "ANCESTOR_AND_SELF") {
            return this.getBoundaryAncestorAndSelfCodes(tenantId, hierarchyType, localityCode, requestInfo);
        }
        if (filter.mode === "LEVEL_RANGE") {
            return this.getBoundaryLevelRangeCodes(
                tenantId,
                hierarchyType,
                localityCode,
                filter.levelConfig || {},
                requestInfo
            );
        }
        logger.warn(`Unknown boundaryFilter mode '${filter.mode}', defaulting to self only`);
        return new Set([localityCode]);
    }

    private static async getBoundaryAncestorAndSelfCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string,
        requestInfo?: RequestInfo
    ): Promise<Set<string>> {
        const response = await searchBoundaryRelationshipData(
            tenantId,
            hierarchyType,
            false,
            true,
            false,
            localityCode,
            requestInfo
        );
        const root = response?.TenantBoundary?.[0]?.boundary?.[0];
        const codes = new Set<string>();
        this.collectBoundaryCodesFromNode(root, codes);
        return codes.size > 0 ? codes : new Set([localityCode]);
    }

    private static async getBoundaryLevelRangeCodes(
        tenantId: string,
        hierarchyType: string,
        localityCode: string,
        levelConfig: Record<string, string>,
        requestInfo?: RequestInfo
    ): Promise<Set<string>> {
        const response = await searchBoundaryRelationshipData(
            tenantId,
            hierarchyType,
            true,
            false,
            false,
            localityCode,
            requestInfo
        );
        const root = response?.TenantBoundary?.[0]?.boundary?.[0];
        if (!root) return new Set([localityCode]);

        const registerBoundaryType = this.asText(root?.boundaryType);
        const deepestType = this.asText(levelConfig?.[registerBoundaryType]);
        if (!deepestType) {
            logger.info(`No levelConfig entry for boundary type '${registerBoundaryType}', using self only for locality ${localityCode}`);
            return new Set([localityCode]);
        }

        const codes = new Set<string>();
        this.collectDescendantsUntilLevel(root, deepestType, codes);
        return codes.size > 0 ? codes : new Set([localityCode]);
    }

    private static collectDescendantsUntilLevel(node: any, deepestType: string, codes: Set<string>): void {
        if (!node) return;
        const code = this.asText(node?.code);
        if (code) codes.add(code);
        if (this.asText(node?.boundaryType) === deepestType) return;
        for (const child of node.children || []) {
            this.collectDescendantsUntilLevel(child, deepestType, codes);
        }
    }

    private static collectBoundaryCodesFromNode(node: any, codes: Set<string>): void {
        if (!node) return;
        const code = this.asText(node?.code);
        if (code) codes.add(code);
        for (const child of node.children || []) {
            this.collectBoundaryCodesFromNode(child, codes);
        }
    }

    /** Maps boundary codes to the project ids campaign_data recorded when each boundary's project was created. */
    private static async resolveProjectIdsForBoundaries(
        campaignNumber: string,
        tenantId: string,
        boundaryCodes: string[]
    ): Promise<string[]> {
        const wanted = new Set(boundaryCodes);
        const boundaryRows = await getRelatedDataWithCampaign(
            "boundary",
            campaignNumber,
            tenantId,
            dataRowStatuses.completed
        ) as CampaignDataRow[];

        const projectIds = new Set<string>();
        for (const row of Array.isArray(boundaryRows) ? boundaryRows : []) {
            const boundaryCode = this.asText(row?.uniqueIdentifier);
            const projectId = this.asText(row?.uniqueIdAfterProcess);
            if (boundaryCode && projectId && wanted.has(boundaryCode)) {
                projectIds.add(projectId);
            }
        }
        return Array.from(projectIds);
    }

    private static async searchRegistersByReferenceIds(
        tenantId: string,
        referenceIds: string[],
        requestInfo?: RequestInfo
    ): Promise<RegisterData[]> {
        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo || {};
        const pageLimit = config.attendanceRegister.registerSearchPageLimit;
        const chunkSize = config.attendanceRegister.registerSearchReferenceIdChunkSize;
        const seen = new Set<string>();
        const registers: RegisterData[] = [];
        let searchCalls = 0;

        for (let index = 0; index < referenceIds.length; index += chunkSize) {
            // Criteria binds via @ModelAttribute from query params, so the list travels comma-separated
            const chunk = referenceIds.slice(index, index + chunkSize).join(",");
            for (let offset = 0; ; offset += pageLimit) {
                const response = await httpRequest(
                    url,
                    { RequestInfo },
                    {
                        tenantId,
                        referenceIds: chunk,
                        includeAttendee: true,
                        includeStaff: true,
                        limit: pageLimit,
                        offset
                    }
                );
                searchCalls++;
                const batch = Array.isArray(response?.attendanceRegister) ? response.attendanceRegister : [];
                if (batch.length === 0) break;
                this.collectRegisters(batch, seen, registers);
                if (batch.length < pageLimit) break;
            }
        }

        logger.info(
            `Fetched ${registers.length} register(s) via ${searchCalls} referenceIds search call(s) `
            + `across ${referenceIds.length} project id(s)`
        );
        return registers;
    }

    private static async searchRegistersByCampaignNumber(
        tenantId: string,
        campaignNumber: string,
        requestInfo?: RequestInfo
    ): Promise<RegisterData[]> {
        const url = config.host.attendanceHost + config.paths.attendanceRegisterSearch;
        const RequestInfo = requestInfo || {};
        const pageLimit = config.attendanceRegister.registerSearchPageLimit;
        const seen = new Set<string>();
        const registers: RegisterData[] = [];
        let searchCalls = 0;

        let offset = 0;
        while (true) {
            const response = await httpRequest(
                url,
                { RequestInfo },
                {
                    tenantId,
                    campaignNumber,
                    includeAttendee: true,
                    includeStaff: true,
                    limit: pageLimit,
                    offset
                }
            );
            searchCalls++;
            const batch = Array.isArray(response?.attendanceRegister) ? response.attendanceRegister : [];
            if (batch.length === 0) break;
            const before = registers.length;
            this.collectRegisters(batch, seen, registers);
            if (registers.length === before) break;
            offset += batch.length;
        }

        logger.info(
            `Fetched ${registers.length} register(s) via ${searchCalls} campaignNumber-scoped search call(s)`
        );
        return registers;
    }

    private static collectRegisters(
        batch: any[],
        seen: Set<string>,
        registers: RegisterData[]
    ): void {
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
    }

    private static resolveRegisterSearchLocalityCodes(
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

        const requestedLocalityCodes = Array.from(codes);
        if (requestedLocalityCodes.length) {
            logger.info(
                "Ignoring localityCode for attendanceRegisterUserBulkMapping generation; "
                + "register discovery is campaign-wide"
            );
        }

        return [];
    }

    private static addLocalityCode(codes: Set<string>, rawCode: unknown): void {
        const code = this.asText(rawCode);
        if (code) {
            codes.add(code);
        }
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

    private static collectHasStampedRowsByServiceCode(rows: CampaignDataRow[]): Map<string, boolean> {
        const hasStampedRowsByServiceCode = new Map<string, boolean>();
        for (const row of rows || []) {
            if (row?.isDeleted) continue;
            const rawData = this.asRecord(row?.data);
            if (!rawData) continue;
            const serviceCode = this.storedRowServiceCode(rawData);
            if (!serviceCode) continue;

            const existing = hasStampedRowsByServiceCode.get(serviceCode) === true;
            if (existing) continue;
            hasStampedRowsByServiceCode.set(
                serviceCode,
                Boolean(this.registerIdentityFromProcessStamp(row?.uniqueIdAfterProcess))
            );
        }
        return hasStampedRowsByServiceCode;
    }

    private static storedRowServiceCode(rawData: Record<string, unknown>, fallback = ""): string {
        return this.firstNonBlank(
            this.asText(rawData["_registerServiceCode"]),
            this.asText(rawData[REGISTER_ID_COLUMN]),
            this.asText(rawData[REGISTER_CODE_COLUMN]),
            fallback
        );
    }

    private static belongsToRegisterInstance(
        register: RegisterData,
        uniqueIdAfterProcess: string | null | undefined,
        hasStampedRowsForServiceCode: boolean
    ): boolean {
        const stamp = this.registerIdentityFromProcessStamp(uniqueIdAfterProcess);
        if (stamp) {
            return stamp === register.id || stamp === register.serviceCode;
        }
        return !hasStampedRowsForServiceCode;
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
        campaignEndDate: string,
        localizationMap: Record<string, string>
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
        const rowBoundaryCode = this.firstNonBlank(
            row[BOUNDARY_CODE_MANDATORY_COLUMN],
            row[BOUNDARY_CODE_COLUMN],
            register.localityCode
        );
        row[BOUNDARY_COLUMN] = this.resolveBoundaryDisplayName(
            row[BOUNDARY_COLUMN],
            rowBoundaryCode,
            localizationMap
        );
        row[BOUNDARY_CODE_MANDATORY_COLUMN] = rowBoundaryCode;
        row[ENROLLMENT_DATE_COLUMN] = campaignStartDate;
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
        attendee: AttendanceAttendeeRow | undefined,
        staff: AttendanceStaffRow | undefined,
        profile: IndividualProfile | undefined,
        personId: string,
        roleCodes: string[],
        campaignStartDate: string,
        campaignEndDate: string,
        localizationMap: Record<string, string>
    ): BulkRow {
        const username = this.firstNonBlank(profile?.username, personId);
        const row: BulkRow = {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [REGISTER_ID_COLUMN]: register.serviceCode,
            [USER_NAME_COLUMN]: this.firstNonBlank(profile?.displayName, profile?.username, personId),
            [WORKER_ID_COLUMN]: personId,
            [USERNAME_COLUMN]: username,
            [PASSWORD_COLUMN]: "",
            [BOUNDARY_COLUMN]: this.resolveBoundaryDisplayName("", register.localityCode, localizationMap),
            [BOUNDARY_CODE_MANDATORY_COLUMN]: register.localityCode,
            [ENROLLMENT_DATE_COLUMN]: campaignStartDate,
            [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                this.formatEpochIfPresent(attendee?.denrollmentDate),
                this.formatEpochIfPresent(staff?.denrollmentDate),
                campaignEndDate
            ),
            [TEAM_CODE_COLUMN]: this.asText(attendee?.tag),
        };
        this.assignRoleColumns(row, roleCodes);
        return row;
    }

    private static buildAttendanceStateStaffRow(
        register: RegisterData,
        staff: AttendanceStaffRow | undefined,
        attendee: AttendanceAttendeeRow | undefined,
        profile: IndividualProfile | undefined,
        personId: string,
        roleCodes: string[],
        campaignStartDate: string,
        campaignEndDate: string,
        localizationMap: Record<string, string>
    ): BulkRow {
        const username = this.firstNonBlank(profile?.username, personId);
        const row: BulkRow = {
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
            [BOUNDARY_COLUMN]: this.resolveBoundaryDisplayName("", register.localityCode, localizationMap),
            [BOUNDARY_CODE_MANDATORY_COLUMN]: register.localityCode,
            [ENROLLMENT_DATE_COLUMN]: campaignStartDate,
            [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                this.formatEpochIfPresent(staff?.denrollmentDate),
                this.formatEpochIfPresent(attendee?.denrollmentDate),
                campaignEndDate
            ),
        };
        this.assignRoleColumns(row, roleCodes);
        return row;
    }

    private static buildCampaignUserRow(
        register: RegisterData,
        sheetName: string,
        rawData: Record<string, unknown>,
        campaignStartDate: string,
        campaignEndDate: string,
        localizationMap: Record<string, string>
    ): BulkRow {
        const roleCodes = this.extractRoleCodes(rawData);
        const encryptedUsername = this.asText(rawData[USERNAME_COLUMN]);
        const encryptedPassword = this.asText(rawData[PASSWORD_COLUMN]);
        const boundaryCode = this.firstNonBlank(
            this.asText(rawData[BOUNDARY_CODE_MANDATORY_COLUMN]),
            this.asText(rawData[BOUNDARY_CODE_COLUMN]),
            register.localityCode
        );

        const row: BulkRow = {
            [REGISTER_CODE_COLUMN]: register.serviceCode,
            [REGISTER_NAME_COLUMN]: register.name,
            [REGISTER_UUID_COLUMN]: register.id,
            [REGISTER_ID_COLUMN]: register.serviceCode,
            [WORKER_ID_COLUMN]: this.asText(rawData[WORKER_ID_COLUMN]),
            [USER_NAME_COLUMN]: this.asText(rawData[USER_NAME_COLUMN]),
            [USERNAME_COLUMN]: encryptedUsername ? decrypt(encryptedUsername) : "",
            [PASSWORD_COLUMN]: encryptedPassword ? decrypt(encryptedPassword) : "",
            [BOUNDARY_COLUMN]: this.resolveBoundaryDisplayName(
                this.asText(rawData[BOUNDARY_COLUMN]),
                boundaryCode,
                localizationMap
            ),
            [BOUNDARY_CODE_MANDATORY_COLUMN]: boundaryCode,
            [ENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                campaignStartDate,
                this.normalizeSheetDateIfPresent(this.asText(rawData[ENROLLMENT_DATE_COLUMN]))
            ),
            [DEENROLLMENT_DATE_COLUMN]: this.firstNonBlank(
                this.normalizeSheetDateIfPresent(this.asText(rawData[DEENROLLMENT_DATE_COLUMN])),
                campaignEndDate
            ),
        };
        this.assignRoleColumns(row, roleCodes);

        if (sheetName === WORKER_SHEET) {
            row[TEAM_CODE_COLUMN] = this.asText(rawData[TEAM_CODE_COLUMN]);
        } else {
            delete row[TEAM_CODE_COLUMN];
        }

        return row;
    }

    private static resolveBoundaryDisplayName(
        boundaryName: string,
        boundaryCode: string,
        localizationMap: Record<string, string>
    ): string {
        const explicitBoundaryName = this.asText(boundaryName);
        if (explicitBoundaryName) return explicitBoundaryName;
        const code = this.asText(boundaryCode);
        if (!code) return "";
        if (!localizationMap || !(code in localizationMap)) return code;
        const localizedBoundaryName = this.asText(localizationMap[code]);
        return localizedBoundaryName || code;
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
            const rows = rowsBySheetName.get(sheetName) || [];
            sheetMap[sheetName] = {
                data: sheetName === MARKER_SHEET
                    ? rows.filter((row) => this.extractRoleCodes(row).length > 0)
                    : rows,
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

    private static sheetNameFromRoleCodes(roleCodes: string[]): string | null {
        if (roleCodes.some((role) => APPROVER_ROLE_CODES.has(role))) return APPROVER_SHEET;
        if (roleCodes.some((role) => MARKER_ROLE_CODES.has(role))) return MARKER_SHEET;
        if (roleCodes.some((role) => WORKER_ROLE_CODES.has(role))) return WORKER_SHEET;
        return null;
    }

    private static resolveAttendanceWorkerRoleCodes(roleCodes: string[]): string[] {
        const workerRoleCodes = roleCodes.filter((role) => WORKER_ROLE_CODES.has(role));
        return workerRoleCodes.length ? workerRoleCodes : roleCodes;
    }

    private static resolveAttendanceStaffSheet(
        staffType: string,
        roleCodes: string[]
    ): { sheetName: string; roleCodes: string[] } | null {
        if (staffType === attendanceStaffTypes.APPROVER) {
            const approverRoleCodes = roleCodes.filter((role) => APPROVER_ROLE_CODES.has(role));
            return {
                sheetName: APPROVER_SHEET,
                roleCodes: approverRoleCodes.length ? approverRoleCodes : [DEFAULT_APPROVER_STAFF_ROLE_CODE],
            };
        }
        if (staffType === attendanceStaffTypes.OWNER) {
            const markerRoleCodes = roleCodes.filter((role) => MARKER_ROLE_CODES.has(role));
            return {
                sheetName: MARKER_SHEET,
                roleCodes: markerRoleCodes.length ? markerRoleCodes : [DEFAULT_MARKER_STAFF_ROLE_CODE],
            };
        }

        const roleBasedSheetName = this.sheetNameFromRoleCodes(roleCodes);
        if (!roleBasedSheetName || roleBasedSheetName === WORKER_SHEET) return null;
        return { sheetName: roleBasedSheetName, roleCodes };
    }

    private static classifyCampaignUserToSheet(rawData: Record<string, unknown>): string | null {
        return this.sheetNameFromRoleCodes(this.extractRoleCodes(rawData));
    }

    private static displayNameFromStaff(staff: AttendanceStaffRow | undefined): string {
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

    private static assignRoleColumns(row: BulkRow, roleCodes: string[]): void {
        const normalizedRoleCodes = Array.from(new Set(
            roleCodes
                .map((role) => this.asText(role).toUpperCase())
                .filter(Boolean)
        ));
        row[ROLE_COLUMN] = normalizedRoleCodes.join(",");
        for (let i = 1; i <= MAX_ROLE_COLUMNS; i++) {
            row[`HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_${i}`] = normalizedRoleCodes[i - 1] || "";
        }
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

    private static registerIdentityFromProcessStamp(identity: string | null | undefined): string {
        const normalizedIdentity = this.asText(identity);
        const idx = normalizedIdentity.indexOf("_");
        return idx > 0 ? normalizedIdentity.slice(0, idx).trim() : "";
    }

    private static registerIdFromIdentity(identity: string): string {
        return this.registerIdentityFromProcessStamp(identity);
    }

    private static sliceRowsBySheets(rowsBySheetName: RowsBySheetName, sheetNames: string[]): RowsBySheetName {
        const wanted = new Set(sheetNames);
        return new Map<string, BulkRow[]>([
            [WORKER_SHEET, wanted.has(WORKER_SHEET) ? [...(rowsBySheetName.get(WORKER_SHEET) || [])] : []],
            [MARKER_SHEET, wanted.has(MARKER_SHEET) ? [...(rowsBySheetName.get(MARKER_SHEET) || [])] : []],
            [APPROVER_SHEET, wanted.has(APPROVER_SHEET) ? [...(rowsBySheetName.get(APPROVER_SHEET) || [])] : []],
        ]);
    }

    private static dropRolelessNonWorkerRows(rowsBySheetName: RowsBySheetName): RowsBySheetName {
        const withRoles = (rows: BulkRow[]) => rows.filter((row) => this.extractRoleCodes(row).length > 0);
        return new Map<string, BulkRow[]>([
            [WORKER_SHEET, []],
            [MARKER_SHEET, withRoles(rowsBySheetName.get(MARKER_SHEET) || [])],
            [APPROVER_SHEET, withRoles(rowsBySheetName.get(APPROVER_SHEET) || [])],
        ]);
    }

    private static pickCampaignNonWorkerFallbackRows(
        rowsBySheetName: RowsBySheetName,
        eligibleRegisterKeys: Set<string>,
        requestingUsername: string
    ): RowsBySheetName {
        if (!eligibleRegisterKeys.size) {
            return new Map<string, BulkRow[]>([
                [WORKER_SHEET, []],
                [MARKER_SHEET, []],
                [APPROVER_SHEET, []],
            ]);
        }

        const normalizedRequestingUsername = this.asText(requestingUsername).toUpperCase();
        const includeRow = (row: BulkRow): boolean => {
            const registerKey = this.rowRegisterKey(row);
            if (!registerKey || !eligibleRegisterKeys.has(registerKey)) return false;
            if (!normalizedRequestingUsername) return true;
            return this.asText(row[USERNAME_COLUMN]).toUpperCase() !== normalizedRequestingUsername;
        };

        return new Map<string, BulkRow[]>([
            [WORKER_SHEET, []],
            [MARKER_SHEET, (rowsBySheetName.get(MARKER_SHEET) || []).filter(includeRow)],
            [APPROVER_SHEET, (rowsBySheetName.get(APPROVER_SHEET) || []).filter(includeRow)],
        ]);
    }

    private static summarizeRowsBySheetName(rowsBySheetName: RowsBySheetName): Record<string, unknown> {
        const summary: Record<string, unknown> = {};
        for (const sheetName of SHEET_NAMES) {
            const rows = rowsBySheetName.get(sheetName) || [];
            const uniqueRegisters = Array.from(new Set(
                rows.map((row) => this.firstNonBlank(
                    this.asText(row[REGISTER_CODE_COLUMN]),
                    this.asText(row[REGISTER_UUID_COLUMN]),
                    this.asText(row[REGISTER_ID_COLUMN])
                )).filter(Boolean)
            ));
            const sampleRows = rows.slice(0, 20).map((row) => ({
                registerCode: this.asText(row[REGISTER_CODE_COLUMN]),
                registerUuid: this.asText(row[REGISTER_UUID_COLUMN]),
                workerId: this.asText(row[WORKER_ID_COLUMN]),
                userName: this.asText(row[USER_NAME_COLUMN]),
                username: this.asText(row[USERNAME_COLUMN]),
                roles: this.extractRoleCodes(row).join(","),
            }));
            summary[sheetName] = {
                rowCount: rows.length,
                uniqueRegisterCount: uniqueRegisters.length,
                registers: uniqueRegisters.slice(0, 20),
                sampleRows,
            };
        }
        return summary;
    }

    private static summarizeRegisters(registers: RegisterData[]): Array<Record<string, unknown>> {
        return registers.slice(0, 100).map((register) => ({
            registerCode: register.serviceCode,
            registerUuid: register.id,
            registerName: register.name,
            localityCode: register.localityCode,
            attendeeCount: Array.isArray(register.attendees) ? register.attendees.length : 0,
            staffCount: Array.isArray(register.staff) ? register.staff.length : 0,
        }));
    }

    private static logBulkTrace(event: string, payload: Record<string, unknown>): void {
        logger.info(`BULK-MAP-TRACE ${event} ${JSON.stringify(payload)}`);
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

    private static async fetchRoleCodesByIndividualId(
        campaignNumber: string,
        tenantId: string,
        neededIndividualIds: string[],
        requestInfo?: RequestInfo
    ): Promise<Map<string, string[]>> {
        const neededIds = new Set(
            neededIndividualIds.map((id) => this.asText(id)).filter(Boolean)
        );
        const roleCodesByIndividualId = new Map<string, Set<string>>();
        const usernames = new Set<string>();
        const userRows = await getRelatedDataWithCampaign(
            USER_DATA_TYPE,
            campaignNumber,
            tenantId,
            dataRowStatuses.completed
        ) as CampaignDataRow[];

        for (const userRow of Array.isArray(userRows) ? userRows : []) {
            if (userRow?.isDeleted) continue;
            const rawData = this.asRecord(userRow?.data);
            if (!rawData) continue;

            const roleCodes = this.extractRoleCodes(rawData);
            if (!roleCodes.length) continue;

            const possibleIds = [
                this.asText(rawData[WORKER_ID_COLUMN]),
                this.asText(userRow?.uniqueIdAfterProcess),
                this.asText(userRow?.uniqueIdentifier)
            ];
            for (const possibleId of possibleIds) {
                if (!possibleId || !neededIds.has(possibleId)) continue;
                const existing = roleCodesByIndividualId.get(possibleId) || new Set<string>();
                for (const roleCode of roleCodes) existing.add(roleCode);
                roleCodesByIndividualId.set(possibleId, existing);
            }

            const username = this.asText(decrypt(this.asText(rawData[USERNAME_COLUMN])));
            if (username) usernames.add(username);
        }

        const unresolvedIndividualIds = Array.from(neededIds).filter((id) => !roleCodesByIndividualId.has(id));
        if (unresolvedIndividualIds.length && usernames.size) {
            const rolesFromHrms = await this.fetchRoleCodesByIndividualIdViaHrms(
                tenantId,
                Array.from(usernames),
                requestInfo
            );
            for (const unresolvedIndividualId of unresolvedIndividualIds) {
                const resolvedRoleCodes = rolesFromHrms.get(unresolvedIndividualId);
                if (!resolvedRoleCodes?.length) continue;
                const existing = roleCodesByIndividualId.get(unresolvedIndividualId) || new Set<string>();
                for (const roleCode of resolvedRoleCodes) existing.add(roleCode);
                roleCodesByIndividualId.set(unresolvedIndividualId, existing);
            }
        }

        return new Map<string, string[]>(
            Array.from(roleCodesByIndividualId.entries()).map(([individualId, codes]) => [individualId, Array.from(codes)])
        );
    }

    private static async fetchRoleCodesByIndividualIdViaHrms(
        tenantId: string,
        usernames: string[],
        requestInfo?: RequestInfo
    ): Promise<Map<string, string[]>> {
        const roleCodesByIndividualId = new Map<string, Set<string>>();
        if (!usernames.length) return new Map<string, string[]>();

        const rootTenantId = tenantId.split(".")[0];
        const searchUrl = config.host.hrmsHost + config.paths.hrmsEmployeeSearch;
        const parallelLimit = Math.min(config.hrms.hrmsParallelSearchLimit, usernames.length);

        for (let index = 0; index < usernames.length; index += parallelLimit) {
            const window = usernames.slice(index, index + parallelLimit);
            const employeesByUsername = await Promise.all(
                window.map(async (username) => {
                    const params = { tenantId: rootTenantId, limit: 2, offset: 0, codes: username };
                    try {
                        const response = await httpRequest(searchUrl, { RequestInfo: requestInfo || {} }, params);
                        return Array.isArray(response?.Employees) ? response.Employees : [];
                    } catch (error: any) {
                        logger.warn(`HRMS role lookup failed for user ${username}: ${error?.message}`);
                        return [];
                    }
                })
            );

            for (const employees of employeesByUsername) {
                for (const employee of employees) {
                    const individualId = this.asText(employee?.user?.uuid);
                    if (!individualId) continue;
                    const roleCodes = Array.isArray(employee?.user?.roles)
                        ? employee.user.roles
                            .map((role: any) => this.asText(role?.code).toUpperCase())
                            .filter(Boolean)
                        : [];
                    if (!roleCodes.length) continue;
                    const existing = roleCodesByIndividualId.get(individualId) || new Set<string>();
                    for (const roleCode of roleCodes) existing.add(roleCode);
                    roleCodesByIndividualId.set(individualId, existing);
                }
            }
        }

        return new Map<string, string[]>(
            Array.from(roleCodesByIndividualId.entries()).map(([individualId, roleCodes]) => [individualId, Array.from(roleCodes)])
        );
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
        const epoch = this.parseDateValueToEpoch(value);
        if (epoch === null) return "";
        return formatEpochAsSheetDate(epoch);
    }

    private static parseDateValueToEpoch(value: unknown): number | null {
        if (value === null || value === undefined || value === "") return null;

        const asNumber = Number(value);
        if (Number.isFinite(asNumber)) {
            return asNumber;
        }

        const raw = this.asText(value);
        if (!raw) return null;

        const dashDate = DASH_DATE_REGEX.exec(raw);
        if (dashDate) {
            const day = Number(dashDate[1]);
            const month = Number(dashDate[2]);
            const year = Number(dashDate[3]);
            return Date.UTC(year, month - 1, day);
        }

        const slashDate = SLASH_DATE_REGEX.exec(raw);
        if (slashDate) {
            const day = Number(slashDate[1]);
            const month = Number(slashDate[2]);
            const year = Number(slashDate[3]);
            return Date.UTC(year, month - 1, day);
        }

        const parsedEpoch = Date.parse(raw);
        if (Number.isFinite(parsedEpoch)) {
            return parsedEpoch;
        }
        return null;
    }

    private static resolveCampaignStartDate(campaign: any): string {
        const createdEpoch = this.parseDateValueToEpoch(
            this.firstNonBlank(
                this.asText(campaign?.auditDetails?.createdTime),
                this.asText(campaign?.createdTime)
            )
        );
        const createdDate = createdEpoch !== null ? formatEpochAsSheetDate(createdEpoch) : "";

        const cycleStartEpoch = this.resolveCampaignDateEpochFromCycles(campaign, "startDate", "min");
        if (cycleStartEpoch !== null) {
            const adjustedCycleStartEpoch = this.adjustCampaignStartEpochForDateBoundary(cycleStartEpoch, createdEpoch);
            return formatEpochAsSheetDate(adjustedCycleStartEpoch);
        }

        const fromAdditionalDetailsEpoch = this.parseDateValueToEpoch(this.asRecord(campaign?.additionalDetails)?.startDate);
        const fromAdditionalDetails = fromAdditionalDetailsEpoch !== null
            ? formatEpochAsSheetDate(fromAdditionalDetailsEpoch)
            : "";
        const fromTopLevelEpoch = this.parseDateValueToEpoch(campaign?.startDate);
        const fromTopLevel = fromTopLevelEpoch !== null ? formatEpochAsSheetDate(fromTopLevelEpoch) : "";

        if (fromTopLevel && createdDate && fromTopLevel === createdDate) {
            if (fromAdditionalDetails && fromAdditionalDetails !== createdDate) {
                return fromAdditionalDetails;
            }
            if (fromTopLevelEpoch !== null) {
                const adjustedTopLevelEpoch = this.adjustCampaignStartEpochForDateBoundary(
                    fromTopLevelEpoch,
                    createdEpoch
                );
                const adjustedTopLevelDate = formatEpochAsSheetDate(adjustedTopLevelEpoch);
                if (adjustedTopLevelDate && adjustedTopLevelDate !== createdDate) {
                    return adjustedTopLevelDate;
                }
            }
            logger.warn(
                `Campaign startDate matches createdTime (${createdDate}); ignoring as enrollment prefill source for bulk template`
            );
            return "";
        }

        const selectedEpoch = fromTopLevelEpoch ?? fromAdditionalDetailsEpoch;
        if (selectedEpoch === null) return "";

        const adjustedSelectedEpoch = this.adjustCampaignStartEpochForDateBoundary(selectedEpoch, createdEpoch);
        return formatEpochAsSheetDate(adjustedSelectedEpoch);
    }

    private static resolveCampaignEndDate(campaign: any): string {
        const fromCycles = this.resolveCampaignDateFromCycles(campaign, "endDate", "max");
        if (fromCycles) return fromCycles;
        return this.firstNonBlank(
            this.formatEpochIfPresent(campaign?.endDate),
            this.formatEpochIfPresent(this.asRecord(campaign?.additionalDetails)?.endDate)
        );
    }

    private static resolveCampaignDateFromCycles(
        campaign: any,
        key: "startDate" | "endDate",
        selection: "min" | "max"
    ): string {
        const selectedEpoch = this.resolveCampaignDateEpochFromCycles(campaign, key, selection);
        if (selectedEpoch === null) return "";
        return formatEpochAsSheetDate(selectedEpoch);
    }

    private static resolveCampaignDateEpochFromCycles(
        campaign: any,
        key: "startDate" | "endDate",
        selection: "min" | "max"
    ): number | null {
        const deliveryRules = Array.isArray(campaign?.deliveryRules) ? campaign.deliveryRules : [];
        const cycleEpochs: number[] = [];
        for (const rule of deliveryRules) {
            const cycles = Array.isArray(rule?.cycles) ? rule.cycles : [];
            for (const cycle of cycles) {
                const epoch = this.parseDateValueToEpoch(cycle?.[key]);
                if (epoch !== null) cycleEpochs.push(epoch);
            }
        }
        if (!cycleEpochs.length) return null;
        return selection === "min"
            ? Math.min(...cycleEpochs)
            : Math.max(...cycleEpochs);
    }

    private static adjustCampaignStartEpochForDateBoundary(startEpoch: number, createdEpoch: number | null): number {
        if (createdEpoch === null) return startEpoch;

        const startDate = formatEpochAsSheetDate(startEpoch);
        const createdDate = formatEpochAsSheetDate(createdEpoch);
        if (!startDate || !createdDate || startDate !== createdDate) return startEpoch;
        if (startEpoch <= createdEpoch) return startEpoch;
        if ((startEpoch - createdEpoch) > ONE_DAY_MS) return startEpoch;

        const adjustedStartEpoch = startEpoch + ONE_DAY_MS;
        logger.info(
            "ENROLL-DEBUG adjusted campaign start epoch by +1 day to preserve date-only boundary "
            + `(startEpoch=${startEpoch}, createdEpoch=${createdEpoch})`
        );
        return adjustedStartEpoch;
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
