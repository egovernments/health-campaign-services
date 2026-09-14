import config from "../config";
import { attendanceColumnKeys, attendanceSheetNames, sheetDataRowStatuses } from "../config/constants";
import { RequestInfo } from "../config/models/requestInfoSchema";
import { getLocalizedName } from "./campaignUtils";
import { logger } from "./logger";
import { httpRequest } from "./request";

const INDIVIDUAL_SEARCH_BATCH_SIZE = 100;
const FALLBACK_INDIVIDUAL_SEARCH_PATH = "individual/v1/_search";

const ROLE_DEFAULTS: Record<string, string> = {
    [attendanceSheetNames.WORKER]: "WORKER",
    [attendanceSheetNames.MARKER]: "TEAM_SUPERVISOR",
    [attendanceSheetNames.APPROVER]: "PROXIMITY_SUPERVISOR",
};

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

export const bulkAttendanceSheetName = "HCM_ATTENDANCE_REGISTER_USER_BULK_MAPPING_SHEET";

export const bulkAttendanceColumnKeys = {
    registerCode: "HCM_ATTENDANCE_REGISTER_CODE",
    userName: "HCM_ADMIN_CONSOLE_USER_NAME",
    workerId: "HCM_ADMIN_CONSOLE_USER_WORKER_ID",
    role: "HCM_ADMIN_CONSOLE_USER_ROLE",
    enrollmentDate: attendanceColumnKeys.ENROLLMENT_DATE,
    deenrollmentDate: attendanceColumnKeys.DEENROLLMENT_DATE,
};

interface IndividualProfile {
    username: string;
    displayName: string;
}

export interface BulkRowProjection {
    sourceRow: Record<string, any>;
    projectedRow: Record<string, any>;
    registerServiceCode: string;
    sheetName: string;
}

export function cellAsString(value: unknown): string {
    if (value === null || value === undefined) return "";
    return String(value).trim();
}

export function collectBulkWorkerIds(rows: Record<string, any>[]): string[] {
    const ids: string[] = [];
    const seen = new Set<string>();
    for (const row of rows) {
        const id = cellAsString(row?.[bulkAttendanceColumnKeys.workerId]);
        if (!id || seen.has(id)) continue;
        seen.add(id);
        ids.push(id);
    }
    return ids;
}

export async function fetchIndividualProfilesById(
    tenantId: string,
    individualIds: string[],
    requestInfo?: RequestInfo
): Promise<Map<string, IndividualProfile>> {
    const profiles = new Map<string, IndividualProfile>();
    if (individualIds.length === 0) return profiles;

    const uniqueIds = Array.from(new Set(individualIds.map((id) => cellAsString(id)).filter(Boolean)));
    if (!uniqueIds.length) return profiles;

    const primaryUrl = config.host.healthIndividualHost + config.paths.healthIndividualSearch;
    const fallbackUrl = config.host.healthIndividualHost + FALLBACK_INDIVIDUAL_SEARCH_PATH;
    const useFallbackProbe = config.paths.healthIndividualSearch !== FALLBACK_INDIVIDUAL_SEARCH_PATH;
    const RequestInfo = requestInfo || {};

    for (let offset = 0; offset < uniqueIds.length; offset += INDIVIDUAL_SEARCH_BATCH_SIZE) {
        const batch = uniqueIds.slice(offset, offset + INDIVIDUAL_SEARCH_BATCH_SIZE);
        const payload = { RequestInfo, Individual: { id: batch } };
        const params = { tenantId, limit: batch.length + 5, offset: 0 };

        const primary = await httpRequest(primaryUrl, payload, params, "post", "", undefined, false, false, true);
        let individuals = Array.isArray(primary?.Individual) ? primary.Individual : [];

        if (individuals.length === 0 && useFallbackProbe) {
            const fallback = await httpRequest(fallbackUrl, payload, params, "post", "", undefined, false, false, true);
            individuals = Array.isArray(fallback?.Individual) ? fallback.Individual : [];
        }

        for (const individual of individuals) {
            const individualId = cellAsString(individual?.id);
            if (!individualId) continue;

            const username = firstNonBlank(
                cellAsString(individual?.userDetails?.username),
                cellAsString(individual?.username)
            );
            if (!username) continue;

            profiles.set(individualId, {
                username,
                displayName: firstNonBlank(displayNameFromIndividual(individual), username)
            });
        }
    }

    return profiles;
}

export function projectBulkRowsToAttendanceSheets(
    rows: Record<string, any>[],
    profiles: Map<string, IndividualProfile>,
    localizationMap?: Record<string, string>
): {
    rowsBySheetName: Map<string, Record<string, any>[]>;
    projections: BulkRowProjection[];
    resolvedIndividualIds: Record<string, string>;
} {
    const rowsBySheetName = new Map<string, Record<string, any>[]>([
        [attendanceSheetNames.WORKER, []],
        [attendanceSheetNames.MARKER, []],
        [attendanceSheetNames.APPROVER, []],
    ]);
    const projections: BulkRowProjection[] = [];
    const resolvedIndividualIds: Record<string, string> = {};

    for (const row of rows) {
        delete row["#status#"];
        delete row["#errorDetails#"];

        const registerServiceCode = firstNonBlank(
            cellAsString(row?.[bulkAttendanceColumnKeys.registerCode]),
            cellAsString(row?.[attendanceColumnKeys.REGISTER_ID])
        );
        const workerId = cellAsString(row?.[bulkAttendanceColumnKeys.workerId]);
        const userName = cellAsString(row?.[bulkAttendanceColumnKeys.userName]);
        const roleValue = cellAsString(row?.[bulkAttendanceColumnKeys.role]);
        const enrollmentDate = row?.[bulkAttendanceColumnKeys.enrollmentDate];
        const deenrollmentDate = row?.[bulkAttendanceColumnKeys.deenrollmentDate];
        const hasMappingData = Boolean(workerId || userName || roleValue || enrollmentDate || deenrollmentDate);

        if (!hasMappingData) {
            row["#status#"] = sheetDataRowStatuses.SKIPPED;
            continue;
        }

        if (!registerServiceCode) {
            row["#status#"] = sheetDataRowStatuses.INVALID;
            row["#errorDetails#"] = localizedError(
                "HCM_ATTENDANCE_ATTENDEE_REGISTER_NOT_FOUND",
                localizationMap,
                "Register code is required"
            );
            continue;
        }

        if (!workerId) {
            row["#status#"] = sheetDataRowStatuses.INVALID;
            row["#errorDetails#"] = localizedError(
                "HCM_ATTENDANCE_ATTENDEE_USER_NOT_FOUND",
                localizationMap,
                "Worker ID is required"
            );
            continue;
        }

        const profile = profiles.get(workerId);
        const username = firstNonBlank(profile?.username, workerId);
        const displayName = firstNonBlank(userName, profile?.displayName, username);
        const sheetName = sheetNameFromRoleValue(roleValue);
        const normalizedRole = normalizeRoleValue(roleValue, sheetName);

        row[bulkAttendanceColumnKeys.registerCode] = registerServiceCode;
        row[bulkAttendanceColumnKeys.userName] = displayName;
        row[bulkAttendanceColumnKeys.workerId] = workerId;
        row[bulkAttendanceColumnKeys.role] = normalizedRole;

        const projectedRow: Record<string, any> = {
            ...row,
            [attendanceColumnKeys.REGISTER_ID]: registerServiceCode,
            [attendanceColumnKeys.USERNAME]: username,
            [bulkAttendanceColumnKeys.userName]: displayName,
            [bulkAttendanceColumnKeys.workerId]: workerId,
            [bulkAttendanceColumnKeys.role]: normalizedRole,
        };

        rowsBySheetName.get(sheetName)?.push(projectedRow);
        projections.push({ sourceRow: row, projectedRow, registerServiceCode, sheetName });
        resolvedIndividualIds[username] = workerId;
    }

    return { rowsBySheetName, projections, resolvedIndividualIds };
}

export function getLocalizedAttendanceSheetData(
    rowsBySheetName: Map<string, Record<string, any>[]>,
    localizationMap: Record<string, string>
): Record<string, Record<string, any>[]> {
    const localizedSheetData: Record<string, Record<string, any>[]> = {};
    for (const sheetName of [attendanceSheetNames.WORKER, attendanceSheetNames.MARKER, attendanceSheetNames.APPROVER]) {
        const localizedName = getLocalizedName(sheetName, localizationMap);
        localizedSheetData[localizedName] = rowsBySheetName.get(sheetName) || [];
    }
    return localizedSheetData;
}

export function applyProjectedStatusesToBulkRows(projections: BulkRowProjection[]): void {
    for (const { sourceRow, projectedRow } of projections) {
        if (projectedRow["#status#"]) sourceRow["#status#"] = projectedRow["#status#"];
        if (projectedRow["#errorDetails#"]) sourceRow["#errorDetails#"] = projectedRow["#errorDetails#"];

        sourceRow[bulkAttendanceColumnKeys.registerCode] = projectedRow[attendanceColumnKeys.REGISTER_ID];
        sourceRow[bulkAttendanceColumnKeys.userName] = projectedRow[bulkAttendanceColumnKeys.userName];
        sourceRow[bulkAttendanceColumnKeys.workerId] = projectedRow[bulkAttendanceColumnKeys.workerId];
        sourceRow[bulkAttendanceColumnKeys.role] = projectedRow[bulkAttendanceColumnKeys.role];
        sourceRow[bulkAttendanceColumnKeys.enrollmentDate] = projectedRow[bulkAttendanceColumnKeys.enrollmentDate];
        sourceRow[bulkAttendanceColumnKeys.deenrollmentDate] = projectedRow[bulkAttendanceColumnKeys.deenrollmentDate];
    }
}

function splitRoleCodes(value: string): string[] {
    if (!value) return [];
    return value
        .split(",")
        .map((part) => cellAsString(part).toUpperCase())
        .filter(Boolean);
}

function sheetNameFromRoleValue(roleValue: string): string {
    const roleCodes = splitRoleCodes(roleValue);
    if (roleCodes.some((role) => APPROVER_ROLE_CODES.has(role))) return attendanceSheetNames.APPROVER;
    if (roleCodes.some((role) => MARKER_ROLE_CODES.has(role))) return attendanceSheetNames.MARKER;
    return attendanceSheetNames.WORKER;
}

function normalizeRoleValue(roleValue: string, sheetName: string): string {
    const roleCodes = splitRoleCodes(roleValue);
    if (!roleCodes.length) return ROLE_DEFAULTS[sheetName] || ROLE_DEFAULTS[attendanceSheetNames.WORKER];
    return roleCodes.join(", ");
}

function displayNameFromIndividual(individual: Record<string, any>): string {
    const name = individual?.name || {};
    return firstNonBlank(
        [cellAsString(name?.givenName), cellAsString(name?.otherNames), cellAsString(name?.familyName)]
            .filter(Boolean)
            .join(" ")
            .trim(),
        cellAsString(individual?.username)
    );
}

function firstNonBlank(...values: unknown[]): string {
    for (const value of values) {
        const normalized = cellAsString(value);
        if (normalized) return normalized;
    }
    return "";
}

function localizedError(
    key: string,
    localizationMap: Record<string, string> | undefined,
    fallback: string
): string {
    if (!localizationMap) return fallback;
    const localized = cellAsString(getLocalizedName(key, localizationMap));
    if (!localized || localized === key) return fallback;
    return localized;
}

export function mergeResolvedIndividualIdObjects(
    existing: Record<string, any> | undefined,
    incoming: Record<string, string>
): Record<string, string> {
    const merged: Record<string, string> = {};
    if (existing && typeof existing === "object") {
        for (const [username, individualId] of Object.entries(existing)) {
            const normalizedUsername = cellAsString(username);
            const normalizedIndividualId = cellAsString(individualId);
            if (!normalizedUsername || !normalizedIndividualId) continue;
            merged[normalizedUsername] = normalizedIndividualId;
        }
    }
    for (const [username, individualId] of Object.entries(incoming)) {
        const normalizedUsername = cellAsString(username);
        const normalizedIndividualId = cellAsString(individualId);
        if (!normalizedUsername || !normalizedIndividualId) continue;
        merged[normalizedUsername] = normalizedIndividualId;
    }
    return merged;
}

export function collectBulkSheetErrors(rows: Record<string, any>[]): { sheetName: string; errorDetails: string }[] {
    const errors: { sheetName: string; errorDetails: string }[] = [];
    for (const row of rows) {
        if (row["#status#"] !== sheetDataRowStatuses.INVALID) continue;
        const errorDetails = cellAsString(row["#errorDetails#"]);
        if (!errorDetails) continue;
        errors.push({
            sheetName: bulkAttendanceSheetName,
            errorDetails,
        });
    }
    return errors;
}

export function logBulkProjectionSummary(rowsBySheetName: Map<string, Record<string, any>[]>): void {
    logger.info(
        `Bulk row projection complete — workerRows=${rowsBySheetName.get(attendanceSheetNames.WORKER)?.length || 0}, `
        + `markerRows=${rowsBySheetName.get(attendanceSheetNames.MARKER)?.length || 0}, `
        + `approverRows=${rowsBySheetName.get(attendanceSheetNames.APPROVER)?.length || 0}`
    );
}
