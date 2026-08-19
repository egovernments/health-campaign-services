import * as ExcelJS from "exceljs";
import config from "../config";
import { logger } from "./logger";
import { executeQuery, getTableName } from "./db";
import { getCampaignIdsByCampaignNumber, getLocalizedMessagesHandlerViaLocale, getRelatedDataWithCampaign } from "./genericUtils";
import { formatEpochAsSheetDate } from "./attendanceIdentityUtils";
import { searchResourceDetailsFromDB, getResourceDetailById, ResourceDetailRow } from "./resourceDetailsUtils";
import { createAndUploadFileWithOutRequest } from "../api/genericApis";
import { fetchFileFromFilestore } from "../api/coreApis";
import { getExcelWorkbookFromFileURL } from "./excelUtils";
import { TenantId } from "../config/models/brandedTypes";
import { attendanceSheetRefresh } from "../config/constants";
import { sleep } from "./timeUtils";

const REGISTER_RESOURCE_TYPE = "attendanceRegister";
const ATTENDEE_RESOURCE_TYPE = "attendanceRegisterAttendee";
const REGISTER_ID_KEY = "HCM_ATTENDANCE_REGISTER_ID";
const USERNAME_KEY = "UserName";
const DEENROLMENT_DATE_KEY = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE";

// Row 1 carries the unlocalized column keys, row 2 the localized header the user sees.
const KEY_ROW = 1;
const FIRST_DATA_ROW = 3;
const IDENTITY_SEPARATOR = "_";

/** additionalDetails key holding the refresh state, so the UI can poll it like any other progress. */
const REFRESH_STATE_KEY = attendanceSheetRefresh.additionalDetailsKey;
const REFRESH_PENDING = attendanceSheetRefresh.statePending;
const REFRESH_IN_PROGRESS = attendanceSheetRefresh.stateInProgress;

/** What is written under that key: the state plus when it was set, which the lease rule compares against. */
interface RefreshMarker {
    state: string;
    at: number;
}

/** Why a read did or did not hand over a file — logged per row so a stuck lease is greppable. */
type RefreshOutcome =
    | "refreshed"
    | "refreshedElsewhere"
    | "alreadyCorrect"
    | "nothingToRefresh"
    | "stillRunning"
    | "waitedOut"
    | "failed";

interface RefreshResult {
    fileStoreId: string | null;
    outcome: RefreshOutcome;
}

/**
 * Flags the current-register file as out of date. Recorded before the rewrite is attempted so the
 * work stays discoverable if this pod dies: a later read finds the flag and finishes the job.
 */
export async function markRegisterSheetRefreshPending(tenantId: TenantId, campaignNumber: string): Promise<void> {
    const resources = await findRegisterResources(tenantId, campaignNumber);
    for (const resource of resources) {
        await setRefreshState(tenantId, resource.id, { state: REFRESH_PENDING, at: Date.now() });
    }
}

/**
 * Read-path completion: whatever the console is handed must already be correct. Rows whose refresh
 * is still owed are finished here, so the id returned points at a file without deleted registers.
 * A row already being refreshed elsewhere is left alone and reported as in-progress for the UI to poll.
 */
export async function completeOwedRegisterSheetRefresh(
    tenantId: TenantId,
    rows: ResourceDetailRow[]
): Promise<Map<string, string | null>> {
    // id -> new fileStoreId, or null for "known stale, do not serve". Absent means untouched.
    const refreshed = new Map<string, string | null>();
    // One budget for the whole response, so several owed rows cannot add up to a very slow search.
    const waitDeadline = Date.now() + config.attendanceRegister.sheetRefreshWaitMs;

    for (const row of rows) {
        if (!isAttendanceSheetType(row?.type) || !hasRefreshMarker(row)) continue;
        const startedAt = Date.now();
        try {
            const result = await claimAndRefresh(tenantId, row, waitDeadline);
            // Withhold the file unless this call knows it is correct: handing over a sheet that still
            // lists a deleted register is worse than a download that does nothing and can be retried.
            refreshed.set(row.id, result.fileStoreId);
            logRefreshOutcome(row.id, result.outcome, startedAt);
        } catch (error) {
            // A search must still answer; the flag stays set so the next read retries.
            logger.error(`ATTENDANCE SHEET :: read-path refresh failed for resource ${row.id}: ${String(error)}`);
            logRefreshOutcome(row.id, "failed", startedAt);
            refreshed.set(row.id, null);
        }
    }
    return refreshed;
}

/**
 * One line per owed file, since this work now sits inside a search: the outcome shows how often a
 * download is withheld and the duration shows what the caller paid for it, without a metrics stack.
 */
function logRefreshOutcome(resourceId: string, outcome: RefreshOutcome, startedAt: number): void {
    logger.info(
        `ATTENDANCE SHEET :: refresh outcome resource=${resourceId} outcome=${outcome} ` +
        `durationMs=${Date.now() - startedAt}`
    );
}

function isAttendanceSheetType(type: string | undefined): boolean {
    return type === REGISTER_RESOURCE_TYPE || type === ATTENDEE_RESOURCE_TYPE;
}

/**
 * Any marker means this file is not known to be correct: either the refresh is still owed, or it is
 * running right now. Which of the two it is decides claim-and-rewrite versus wait, and that is
 * settled by the claim statement rather than read here — one place owns the lease rule.
 */
function hasRefreshMarker(row: ResourceDetailRow): boolean {
    return Boolean(readRefreshMarker(row));
}

/** additionalDetails is free-form JSON, so the marker is read through one place instead of inline casts. */
function readRefreshMarker(row: ResourceDetailRow | null): unknown {
    return row?.additionaldetails?.[REFRESH_STATE_KEY];
}

function refreshStateOf(marker: unknown): string {
    if (typeof marker !== "object" || marker === null || !("state" in marker)) return "";
    return String(marker.state);
}

/**
 * Claims the refresh by moving pending -> inProgress in one conditional update, so concurrent
 * deletes and reads cannot rewrite the same file twice. fileStoreId is the file the caller may
 * serve, or null when this call cannot vouch for one.
 */
async function claimAndRefresh(
    tenantId: TenantId,
    resource: ResourceDetailRow,
    waitDeadline: number
): Promise<RefreshResult> {
    const processedFileStoreId = resource?.processedfilestoreid;
    if (!processedFileStoreId) {
        // Nothing has been served for this campaign yet (no completed upload), so there is nothing to
        // correct. Cleared unconditionally: no claim was taken here, so the state still reads pending
        // and finishRefresh — which only drops an inProgress marker — would leave it set for good.
        await clearRefreshMarker(tenantId, resource.id);
        return { fileStoreId: null, outcome: "nothingToRefresh" };
    }

    // Two passes at most: the wait can end with the work handed back as pending, which this claims.
    for (let attempt = 0; attempt < 2; attempt++) {
        if (await claimRefresh(tenantId, resource.id)) {
            return rewriteUnderClaim(tenantId, resource, processedFileStoreId);
        }
        const landed = await waitForRefreshToLand(tenantId, resource.id, waitDeadline);
        if (landed.settled) return { fileStoreId: landed.fileStoreId, outcome: "refreshedElsewhere" };
        if (!landed.owedAgain) return { fileStoreId: null, outcome: "stillRunning" };
    }
    return { fileStoreId: null, outcome: "waitedOut" };
}

async function rewriteUnderClaim(
    tenantId: TenantId,
    resource: ResourceDetailRow,
    processedFileStoreId: string
): Promise<RefreshResult> {
    try {
        const newFileStoreId = resource.type === ATTENDEE_RESOURCE_TYPE
            ? await rewriteWithSyncedDeEnrolments(tenantId, resource, processedFileStoreId)
            : await rewriteWithoutDeletedRegisters(tenantId, resource, processedFileStoreId);
        await finishRefresh(tenantId, resource.id, newFileStoreId);
        // No rewrite needed means the existing file was already correct, so it stays servable.
        return newFileStoreId
            ? { fileStoreId: newFileStoreId, outcome: "refreshed" }
            : { fileStoreId: processedFileStoreId, outcome: "alreadyCorrect" };
    } catch (error) {
        // Hand the claim back so the next read retries instead of the lease having to expire.
        await setRefreshState(tenantId, resource.id, { state: REFRESH_PENDING, at: Date.now() });
        throw error;
    }
}

/**
 * Waits for the refresh running elsewhere, so a download started during the rewrite still gets the
 * corrected file rather than the stale pointer. Gives up at the deadline and lets the caller report
 * the refresh as in-progress.
 */
async function waitForRefreshToLand(
    tenantId: TenantId,
    resourceId: string,
    deadline: number
): Promise<{ settled: boolean; owedAgain: boolean; fileStoreId: string | null }> {
    while (Date.now() < deadline) {
        await sleep(config.attendanceRegister.sheetRefreshPollMs);
        const row = await getResourceDetailById(resourceId, tenantId);
        const marker = readRefreshMarker(row);

        if (!marker) {
            return { settled: true, owedAgain: false, fileStoreId: row?.processedfilestoreid || null };
        }
        if (refreshStateOf(marker) === REFRESH_PENDING) {
            return { settled: false, owedAgain: true, fileStoreId: null };
        }
    }

    logger.warn(`ATTENDANCE SHEET :: resource ${resourceId} — refresh still running at the wait deadline, reported in progress`);
    return { settled: false, owedAgain: false, fileStoreId: null };
}

/**
 * Flags the current-attendees file of the registers whose enrolments changed. One attendee resource
 * exists per register (parentResourceId is the register), so the marker lands exactly where needed.
 */
export async function markAttendeeSheetRefreshPending(tenantId: TenantId, registerIds: string[]): Promise<void> {
    for (const registerId of new Set(registerIds)) {
        for (const resource of await findAttendeeResources(tenantId, registerId)) {
            await setRefreshState(tenantId, resource.id, { state: REFRESH_PENDING, at: Date.now() });
        }
    }
}

/** Attendee resources are keyed by their register rather than by campaign, which the event carries. */
async function findAttendeeResources(tenantId: TenantId, registerId: string): Promise<ResourceDetailRow[]> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    const query = `SELECT * FROM ${tableName}
                   WHERE tenantId = $1 AND type = $2 AND parentResourceId = $3 AND isActive = true`;
    const result = await executeQuery(query, [tenantId, ATTENDEE_RESOURCE_TYPE, registerId]);
    return result?.rows || [];
}

/**
 * Stamps the synced de-enrolment date onto the current-attendees file. De-enrolled people stay
 * listed — the date is what the console has to show — so rows are annotated, never removed.
 */
async function rewriteWithSyncedDeEnrolments(
    tenantId: TenantId,
    resource: ResourceDetailRow,
    processedFileStoreId: string
): Promise<string | null> {
    const registerId = resource.parentresourceid;
    if (!registerId) return null; // nothing identifies which register's people these are

    const fileUrl = await fetchFileFromFilestore(processedFileStoreId, tenantId);
    const workbook = await getExcelWorkbookFromFileURL(fileUrl);

    // Sheet names in the file are localized, the stored rows name their sheet by key, so the map has to
    // be built in the locale the file was written in — which the file itself carries.
    const localizationMap = await sheetNameLocalization(workbook, tenantId);
    const deEnrolmentDates = await syncedDeEnrolmentDates(tenantId, resource.campaignid, registerId, localizationMap);
    if (deEnrolmentDates.size === 0) return null;

    const stamped = stampDeEnrolmentDates(workbook, deEnrolmentDates);
    if (stamped === 0) {
        logger.info(`ATTENDANCE SHEET :: resource ${resource.id} — current-attendees file already up to date`);
        return null;
    }

    const newFileStoreId = await uploadRefreshedWorkbook(workbook, tenantId, resource.id);

    logger.info(
        `ATTENDANCE SHEET :: resource ${resource.id} — stamped ${stamped} de-enrolment date(s) into the ` +
        `current-attendees file, new fileStoreId ${newFileStoreId}`
    );
    return newFileStoreId;
}

/** Same fallback as the sheet writer's getLocalizedName, without pulling its module's redis/HTTP setup in. */
function localizedSheetName(sheetKey: string, localizationMap: Record<string, string>): string {
    const localized = localizationMap?.[sheetKey];
    return localized && localized.trim() !== "" ? localized : sheetKey;
}

/**
 * The locale a generated file was written in, taken from its own `locale#campaignId` keywords so the
 * sheet names resolve exactly as they did at write time. Falls back to the configured default.
 */
async function sheetNameLocalization(
    workbook: ExcelJS.Workbook,
    tenantId: TenantId
): Promise<Record<string, string>> {
    const keywords = typeof workbook?.keywords === "string" ? workbook.keywords : "";
    const locale = keywords.includes("#") ? keywords.split("#")[0] : config.localisation.defaultLocale;
    try {
        return await getLocalizedMessagesHandlerViaLocale(locale, tenantId);
    } catch (error) {
        // An unreachable localization service must not block the refresh: the raw keys still match a
        // file written before those keys were localized.
        logger.warn(`ATTENDANCE SHEET :: could not resolve localization for locale ${locale}: ${String(error)}`);
        return {};
    }
}

/** Keyed by sheet and username, since the same person can sit on different sheets of one register. */
async function syncedDeEnrolmentDates(
    tenantId: TenantId,
    campaignId: string,
    registerId: string,
    localizationMap: Record<string, string>
): Promise<Map<string, string>> {
    const { campaignNumber } = await getCampaignNumberOf(campaignId, tenantId);
    const dates = new Map<string, string>();
    if (!campaignNumber) return dates;

    const rows = await getRelatedDataWithCampaign(ATTENDEE_RESOURCE_TYPE, campaignNumber, tenantId);
    for (const row of rows) {
        if (row?.denrollmentDate == null) continue;
        // Rows of other registers share the campaign, so the stamped identity decides ownership
        const identity = row?.uniqueIdAfterProcess ? String(row.uniqueIdAfterProcess) : "";
        if (!identity.startsWith(`${registerId}${IDENTITY_SEPARATOR}`)) continue;

        const data = row?.data || {};
        const userName = data[USERNAME_KEY] ? String(data[USERNAME_KEY]).trim() : "";
        const sheetKey = data._sheetName ? String(data._sheetName).trim() : "";
        if (!userName || !sheetKey) continue;

        const date = formatEpochAsSheetDate(Number(row.denrollmentDate));
        // Both names are registered: the localized one matches a current file, the key matches one
        // written before that key had a translation.
        dates.set(`${localizedSheetName(sheetKey, localizationMap)}${IDENTITY_SEPARATOR}${userName}`, date);
        dates.set(`${sheetKey}${IDENTITY_SEPARATOR}${userName}`, date);
    }
    return dates;
}

/** Returns how many cells changed, so an already-correct file is not re-uploaded for nothing. */
export function stampDeEnrolmentDates(workbook: ExcelJS.Workbook, deEnrolmentDates: Map<string, string>): number {
    let stamped = 0;
    for (const worksheet of workbook?.worksheets || []) {
        const keyRow = worksheet.getRow(KEY_ROW);
        const columnCount = keyRow?.cellCount || 0;
        const userNameColumn = columnIndexOfKey(keyRow, columnCount, USERNAME_KEY);
        const deEnrolmentColumn = columnIndexOfKey(keyRow, columnCount, DEENROLMENT_DATE_KEY);
        if (!userNameColumn || !deEnrolmentColumn) continue;

        for (let rowNumber = FIRST_DATA_ROW; rowNumber <= worksheet.rowCount; rowNumber++) {
            const row = worksheet.getRow(rowNumber);
            const userName = cellText(row.getCell(userNameColumn));
            if (!userName) continue;

            const date = deEnrolmentDates.get(`${worksheet.name}${IDENTITY_SEPARATOR}${userName}`);
            if (!date || cellText(row.getCell(deEnrolmentColumn)) === date) continue;
            row.getCell(deEnrolmentColumn).value = date;
            stamped++;
        }
    }
    return stamped;
}

async function rewriteWithoutDeletedRegisters(
    tenantId: TenantId,
    resource: ResourceDetailRow,
    processedFileStoreId: string
): Promise<string | null> {
    const deletedServiceCodes = await deletedRegisterServiceCodes(tenantId, resource.campaignid);
    if (deletedServiceCodes.size === 0) return null;

    const fileUrl = await fetchFileFromFilestore(processedFileStoreId, tenantId);
    const workbook = await getExcelWorkbookFromFileURL(fileUrl);

    const removed = removeDeletedRegisterRows(workbook, deletedServiceCodes);
    if (removed === 0) {
        logger.info(`ATTENDANCE SHEET :: resource ${resource.id} — file already free of deleted registers`);
        return null;
    }

    const newFileStoreId = await uploadRefreshedWorkbook(workbook, tenantId, resource.id);

    logger.info(
        `ATTENDANCE SHEET :: resource ${resource.id} — removed ${removed} deleted register row(s), ` +
        `new fileStoreId ${newFileStoreId}`
    );
    return newFileStoreId;
}

/** The refresh is only useful if it yields an id to repoint at, so a missing one is an error, not a null. */
async function uploadRefreshedWorkbook(
    workbook: ExcelJS.Workbook,
    tenantId: TenantId,
    resourceId: string
): Promise<string> {
    const uploaded = await createAndUploadFileWithOutRequest(workbook, tenantId);
    const fileStoreId: unknown = uploaded?.[0]?.fileStoreId;
    if (typeof fileStoreId !== "string" || !fileStoreId) {
        throw new Error(`ATTENDANCE SHEET :: resource ${resourceId} — refreshed file upload returned no fileStoreId`);
    }
    return fileStoreId;
}

/** Deleted registers are read from the current DB state, never from the event, so no delete is lost. */
async function deletedRegisterServiceCodes(tenantId: TenantId, campaignId: string): Promise<Set<string>> {
    const { campaignNumber } = await getCampaignNumberOf(campaignId, tenantId);
    if (!campaignNumber) return new Set();
    const rows = await getRelatedDataWithCampaign(REGISTER_RESOURCE_TYPE, campaignNumber, tenantId);
    return new Set<string>(
        rows.filter((row) => row?.isDeleted && row?.uniqueIdentifier).map((row) => String(row.uniqueIdentifier))
    );
}

/** Scoped by tenant as well as id: outside a central instance every tenant shares this table. */
async function getCampaignNumberOf(campaignId: string, tenantId: TenantId): Promise<{ campaignNumber: string | null }> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DETAILS_TABLE_NAME, tenantId);
    const result = await executeQuery(
        `SELECT campaignNumber FROM ${tableName} WHERE id = $1 AND tenantId = $2 LIMIT 1`,
        [campaignId, tenantId]
    );
    return { campaignNumber: result?.rows?.[0]?.campaignnumber || null };
}

async function findRegisterResources(tenantId: TenantId, campaignNumber: string): Promise<ResourceDetailRow[]> {
    const campaignIds = await getCampaignIdsByCampaignNumber(campaignNumber, tenantId);
    if (campaignIds.length === 0) {
        logger.warn(`ATTENDANCE SHEET :: no campaignId resolved for ${campaignNumber}, current-register file not refreshed`);
        return [];
    }
    return searchResourceDetailsFromDB({
        tenantId,
        campaignIds,
        type: [REGISTER_RESOURCE_TYPE],
        isActive: true
    });
}

/** Only one caller wins: pending (or an expired claim) becomes inProgress in a single statement. */
async function claimRefresh(tenantId: TenantId, resourceId: string): Promise<boolean> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    const claim = JSON.stringify({ state: REFRESH_IN_PROGRESS, at: Date.now() });
    const staleBefore = Date.now() - config.attendanceRegister.sheetRefreshLeaseMs;
    const query = `UPDATE ${tableName}
                   SET additionalDetails = jsonb_set(COALESCE(additionalDetails, '{}'::jsonb), $1, $2::jsonb, true)
                   WHERE id = $3
                     AND (additionalDetails -> $4 ->> 'state' = $5
                          OR (additionalDetails -> $4 ->> 'state' = $6
                              AND COALESCE((additionalDetails -> $4 ->> 'at')::bigint, 0) < $7))
                   RETURNING id`;
    const result = await executeQuery(query, [
        `{${REFRESH_STATE_KEY}}`, claim, resourceId, REFRESH_STATE_KEY,
        REFRESH_PENDING, REFRESH_IN_PROGRESS, staleBefore
    ]);
    return (result?.rowCount ?? 0) > 0;
}

/**
 * Clears the flag and repoints the resource in one statement. Written directly rather than through
 * the persister: the read that triggered this has to see the new file id in the same request.
 */
async function finishRefresh(tenantId: TenantId, resourceId: string, newFileStoreId: string | null): Promise<void> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    const values: (string | number)[] = [`{${REFRESH_STATE_KEY}}`, resourceId, Date.now(), REFRESH_STATE_KEY, REFRESH_IN_PROGRESS];
    let setFileStoreId = "";
    if (newFileStoreId) {
        setFileStoreId = `, processedFileStoreId = $6`;
        values.push(newFileStoreId);
    }
    // The marker is dropped only while it still reads inProgress. A delete that arrived during the
    // rewrite set it back to pending, and clearing that would lose the deletion; the file pointer is
    // still moved, because the file this produced is newer than the one it replaced either way.
    const query = `UPDATE ${tableName}
                   SET additionalDetails = CASE
                           WHEN additionalDetails -> $4 ->> 'state' = $5
                               THEN COALESCE(additionalDetails, '{}'::jsonb) #- $1
                           ELSE additionalDetails
                       END,
                       lastModifiedTime = $3${setFileStoreId}
                   WHERE id = $2`;
    await executeQuery(query, values);
}

/** Drops the marker whatever state it is in, for the paths that never claimed it. */
async function clearRefreshMarker(tenantId: TenantId, resourceId: string): Promise<void> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    const query = `UPDATE ${tableName}
                   SET additionalDetails = COALESCE(additionalDetails, '{}'::jsonb) #- $1,
                       lastModifiedTime = $3
                   WHERE id = $2`;
    await executeQuery(query, [`{${REFRESH_STATE_KEY}}`, resourceId, Date.now()]);
}

async function setRefreshState(tenantId: TenantId, resourceId: string, state: RefreshMarker): Promise<void> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    const query = `UPDATE ${tableName}
                   SET additionalDetails = jsonb_set(COALESCE(additionalDetails, '{}'::jsonb), $1, $2::jsonb, true)
                   WHERE id = $3`;
    await executeQuery(query, [`{${REFRESH_STATE_KEY}}`, JSON.stringify(state), resourceId]);
}

/**
 * Rewrites the surviving rows from the top of the data block and blanks what is left over. Values
 * are moved rather than rows spliced, so each row keeps the validation already sitting on it — the
 * cascades are row-relative, and this path does not reload the schema to re-apply them.
 */
export function removeDeletedRegisterRows(workbook: ExcelJS.Workbook, deletedServiceCodes: Set<string>): number {
    const worksheet = findRegisterListSheet(workbook);
    if (!worksheet) return 0;

    const keyRow = worksheet.getRow(KEY_ROW);
    const columnCount = keyRow?.cellCount || 0;
    const registerIdColumn = columnIndexOfKey(keyRow, columnCount, REGISTER_ID_KEY);
    if (!registerIdColumn) return 0;

    const dataRows: { registerId: string; values: ExcelJS.CellValue[] }[] = [];
    let lastDataRow = FIRST_DATA_ROW - 1;
    for (let rowNumber = FIRST_DATA_ROW; rowNumber <= worksheet.rowCount; rowNumber++) {
        const row = worksheet.getRow(rowNumber);
        const registerId = cellText(row.getCell(registerIdColumn));
        if (!registerId) continue; // padding row the template pre-formats
        lastDataRow = rowNumber;
        dataRows.push({ registerId, values: readRowValues(row, columnCount) });
    }

    const survivors = dataRows.filter((row) => !deletedServiceCodes.has(row.registerId));
    if (survivors.length === dataRows.length) return 0;

    let targetRow = FIRST_DATA_ROW;
    for (const survivor of survivors) {
        writeRowValues(worksheet.getRow(targetRow++), survivor.values, columnCount);
    }
    for (let rowNumber = targetRow; rowNumber <= lastDataRow; rowNumber++) {
        writeRowValues(worksheet.getRow(rowNumber), [], columnCount);
    }

    return dataRows.length - survivors.length;
}

/** Located by its key row rather than its name, which is localized per environment. */
function findRegisterListSheet(workbook: ExcelJS.Workbook): ExcelJS.Worksheet | null {
    for (const worksheet of workbook?.worksheets || []) {
        const keyRow = worksheet.getRow(KEY_ROW);
        if (columnIndexOfKey(keyRow, keyRow?.cellCount || 0, REGISTER_ID_KEY)) return worksheet;
    }
    return null;
}

function columnIndexOfKey(keyRow: ExcelJS.Row, columnCount: number, key: string): number {
    for (let column = 1; column <= columnCount; column++) {
        if (cellText(keyRow.getCell(column)) === key) return column;
    }
    return 0;
}

/** A formula is read as its last computed value: copied to another row its references would be wrong. */
function readRowValues(row: ExcelJS.Row, columnCount: number): ExcelJS.CellValue[] {
    const values: ExcelJS.CellValue[] = [];
    for (let column = 1; column <= columnCount; column++) {
        const value = row.getCell(column).value;
        values.push(value && typeof value === "object" && "formula" in value ? value.result ?? null : value);
    }
    return values;
}

function writeRowValues(row: ExcelJS.Row, values: ExcelJS.CellValue[], columnCount: number): void {
    for (let column = 1; column <= columnCount; column++) {
        row.getCell(column).value = values[column - 1] ?? null;
    }
}

function cellText(cell: ExcelJS.Cell): string {
    const value = cell?.value;
    if (value === null || value === undefined) return "";
    if (typeof value === "object") {
        if ("result" in value && value.result != null) return String(value.result).trim();
        if ("text" in value && value.text != null) return String(value.text).trim();
        return "";
    }
    return String(value).trim();
}
