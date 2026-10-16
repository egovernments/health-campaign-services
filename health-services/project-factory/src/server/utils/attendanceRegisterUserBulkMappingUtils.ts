import config from "../config";
import { attendanceColumnKeys, attendanceSheetNames, sheetDataRowStatuses } from "../config/constants";
import { RequestInfo } from "../config/models/requestInfoSchema";
import { SheetMap } from "../models/SheetMap";
import { getLocalizedName } from "./campaignUtils";
import { logger } from "./logger";
import { httpRequest } from "./request";

const INDIVIDUAL_SEARCH_BATCH_SIZE = 100;
const FALLBACK_INDIVIDUAL_SEARCH_PATH = "individual/v1/_search";

const ALL_ATTENDANCE_SHEETS = [
    attendanceSheetNames.WORKER,
    attendanceSheetNames.MARKER,
    attendanceSheetNames.APPROVER,
];

export const bulkRegisterColumnKeys = {
    registerCode: "HCM_ATTENDANCE_REGISTER_CODE",
    registerName: "HCM_ATTENDANCE_REGISTER_NAME",
    registerUuid: "HCM_ATTENDANCE_REGISTER_UUID",
};

function bulkRegisterDynamicColumns() {
    return {
        [bulkRegisterColumnKeys.registerCode]: { orderNumber: 0.01, width: 22, freezeColumn: true },
        [bulkRegisterColumnKeys.registerName]: { orderNumber: 0.02, width: 36 },
        [bulkRegisterColumnKeys.registerUuid]: { orderNumber: 0.03, width: 42 },
        [attendanceColumnKeys.REGISTER_ID]: { hideColumn: true },
    };
}

export const bulkAttendanceColumnKeys = {
    registerCode: bulkRegisterColumnKeys.registerCode,
    registerName: bulkRegisterColumnKeys.registerName,
    registerUuid: bulkRegisterColumnKeys.registerUuid,
    userName: "HCM_ADMIN_CONSOLE_USER_NAME",
    workerId: "HCM_ADMIN_CONSOLE_USER_WORKER_ID",
    role: "HCM_ADMIN_CONSOLE_USER_ROLE",
    teamCode: attendanceColumnKeys.TEAM_CODE,
    enrollmentDate: attendanceColumnKeys.ENROLLMENT_DATE,
    deenrollmentDate: attendanceColumnKeys.DEENROLLMENT_DATE,
    username: attendanceColumnKeys.USERNAME,
    registerId: attendanceColumnKeys.REGISTER_ID,
};

export interface IndividualProfile {
    username: string;
    displayName: string;
}

export type RowsBySheetName = Map<string, Record<string, any>[]>;

function createEmptyRowsBySheetName(): RowsBySheetName {
    return new Map<string, Record<string, any>[]>(ALL_ATTENDANCE_SHEETS.map((sheetName) => [sheetName, []]));
}

export function cellAsString(value: unknown): string {
    if (value === null || value === undefined) return "";
    return String(value).trim();
}

function hasCellValue(value: unknown): boolean {
    return cellAsString(value).length > 0;
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

export function getBulkRowsBySheetName(
    wholeSheetData: Record<string, any>,
    localizationMap: Record<string, string>
): RowsBySheetName {
    const rowsBySheetName = createEmptyRowsBySheetName();
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        const localizedSheetName = getLocalizedName(sheetName, localizationMap);
        const rows = wholeSheetData?.[localizedSheetName];
        rowsBySheetName.set(sheetName, Array.isArray(rows) ? rows : []);
    }
    return rowsBySheetName;
}

export function collectBulkWorkerIds(rowsBySheetName: RowsBySheetName): string[] {
    const ids: string[] = [];
    const seen = new Set<string>();
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        for (const row of rowsBySheetName.get(sheetName) || []) {
            const workerId = cellAsString(row?.[bulkAttendanceColumnKeys.workerId]);
            if (!workerId || seen.has(workerId)) continue;
            seen.add(workerId);
            ids.push(workerId);
        }
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

export function normalizeBulkRowsForAttendeeFlow(
    allRowsBySheetName: RowsBySheetName,
    profiles: Map<string, IndividualProfile>,
    localizationMap?: Record<string, string>
): {
    actionableRowsBySheetName: RowsBySheetName;
    resolvedIndividualIds: Record<string, string>;
} {
    const actionableRowsBySheetName = createEmptyRowsBySheetName();
    const resolvedIndividualIds: Record<string, string> = {};

    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        const rows = allRowsBySheetName.get(sheetName) || [];
        for (const row of rows) {
            delete row["#status#"];
            delete row["#errorDetails#"];

            const registerServiceCode = firstNonBlank(
                row?.[bulkRegisterColumnKeys.registerCode],
                row?.[attendanceColumnKeys.REGISTER_ID]
            );

            if (registerServiceCode) {
                row[bulkRegisterColumnKeys.registerCode] = registerServiceCode;
                row[attendanceColumnKeys.REGISTER_ID] = registerServiceCode;
            }

            const workerId = cellAsString(row?.[bulkAttendanceColumnKeys.workerId]);
            let username = cellAsString(row?.[attendanceColumnKeys.USERNAME]);
            let displayName = cellAsString(row?.[bulkAttendanceColumnKeys.userName]);
            const teamCode = sheetName === attendanceSheetNames.WORKER
                ? cellAsString(row?.[bulkAttendanceColumnKeys.teamCode])
                : "";

            if (workerId) {
                const profile = profiles.get(workerId);
                username = firstNonBlank(profile?.username, username, workerId);
                displayName = firstNonBlank(displayName, profile?.displayName, username);
                row[bulkAttendanceColumnKeys.workerId] = workerId;
                row[attendanceColumnKeys.USERNAME] = username;
                row[bulkAttendanceColumnKeys.userName] = displayName;
                if (username) {
                    resolvedIndividualIds[username] = workerId;
                }
            }

            const hasUserLookupInput = Boolean(workerId || username);
            const hasDisplayNameInput = Boolean(displayName);
            const hasDateInput = hasCellValue(row?.[bulkAttendanceColumnKeys.enrollmentDate])
                || hasCellValue(row?.[bulkAttendanceColumnKeys.deenrollmentDate]);
            const hasActionInput = hasUserLookupInput || hasDisplayNameInput || hasDateInput || Boolean(teamCode);

            if (!hasActionInput) {
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

            if (!hasUserLookupInput) {
                row["#status#"] = sheetDataRowStatuses.INVALID;
                row["#errorDetails#"] = localizedError(
                    "HCM_ATTENDANCE_ATTENDEE_USER_NOT_FOUND",
                    localizationMap,
                    "Worker ID or UserName is required"
                );
                continue;
            }

            actionableRowsBySheetName.get(sheetName)?.push(row);
        }
    }

    return { actionableRowsBySheetName, resolvedIndividualIds };
}

export function groupRowsByRegister(rowsBySheetName: RowsBySheetName): Map<string, RowsBySheetName> {
    const groupedRowsByRegister = new Map<string, RowsBySheetName>();

    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        for (const row of rowsBySheetName.get(sheetName) || []) {
            if (row?.["#status#"] === sheetDataRowStatuses.INVALID) continue;
            const registerServiceCode = cellAsString(row?.[attendanceColumnKeys.REGISTER_ID]);
            if (!registerServiceCode) continue;

            let groupedRows = groupedRowsByRegister.get(registerServiceCode);
            if (!groupedRows) {
                groupedRows = createEmptyRowsBySheetName();
                groupedRowsByRegister.set(registerServiceCode, groupedRows);
            }
            groupedRows.get(sheetName)?.push(row);
        }
    }

    return groupedRowsByRegister;
}

export function getLocalizedAttendanceSheetData(
    rowsBySheetName: RowsBySheetName,
    localizationMap: Record<string, string>
): Record<string, Record<string, any>[]> {
    const localizedSheetData: Record<string, Record<string, any>[]> = {};
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        const localizedName = getLocalizedName(sheetName, localizationMap);
        localizedSheetData[localizedName] = rowsBySheetName.get(sheetName) || [];
    }
    return localizedSheetData;
}

export function ensureRowsHaveStatus(rowsBySheetName: RowsBySheetName): void {
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        for (const row of rowsBySheetName.get(sheetName) || []) {
            if (!row["#status#"]) {
                row["#status#"] = sheetDataRowStatuses.SKIPPED;
            }
        }
    }
}

export function collectBulkSheetErrors(rowsBySheetName: RowsBySheetName): { sheetName: string; errorDetails: string }[] {
    const errors: { sheetName: string; errorDetails: string }[] = [];
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        for (const row of rowsBySheetName.get(sheetName) || []) {
            if (row["#status#"] !== sheetDataRowStatuses.INVALID) continue;
            const errorDetails = cellAsString(row["#errorDetails#"]);
            if (!errorDetails) continue;
            errors.push({ sheetName, errorDetails });
        }
    }
    return errors;
}

export function toBulkSheetMap(rowsBySheetName: RowsBySheetName): SheetMap {
    const sheetMap: SheetMap = {};
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        sheetMap[sheetName] = {
            data: rowsBySheetName.get(sheetName) || [],
            dynamicColumns: bulkRegisterDynamicColumns(),
        };
    }
    return sheetMap;
}

export function logBulkProjectionSummary(
    allRowsBySheetName: RowsBySheetName,
    actionableRowsBySheetName: RowsBySheetName
): void {
    const totalWorkerRows = allRowsBySheetName.get(attendanceSheetNames.WORKER)?.length || 0;
    const totalMarkerRows = allRowsBySheetName.get(attendanceSheetNames.MARKER)?.length || 0;
    const totalApproverRows = allRowsBySheetName.get(attendanceSheetNames.APPROVER)?.length || 0;
    const actionableWorkerRows = actionableRowsBySheetName.get(attendanceSheetNames.WORKER)?.length || 0;
    const actionableMarkerRows = actionableRowsBySheetName.get(attendanceSheetNames.MARKER)?.length || 0;
    const actionableApproverRows = actionableRowsBySheetName.get(attendanceSheetNames.APPROVER)?.length || 0;

    logger.info(
        `Bulk row normalization complete — workerRows=${totalWorkerRows} (actionable=${actionableWorkerRows}), `
        + `markerRows=${totalMarkerRows} (actionable=${actionableMarkerRows}), `
        + `approverRows=${totalApproverRows} (actionable=${actionableApproverRows})`
    );
}

export function hasActionableRows(rowsBySheetName: RowsBySheetName): boolean {
    for (const sheetName of ALL_ATTENDANCE_SHEETS) {
        if ((rowsBySheetName.get(sheetName) || []).length > 0) return true;
    }
    return false;
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
