import config from "../config";
import { logger } from "./logger";
import { executeQuery, getTableName } from "./db";
import { markAttendeeSheetRefreshPending, markRegisterSheetRefreshPending } from "./attendanceSheetUtils";
import { AttendanceRegisterId, IndividualId, TenantId } from "../config/models/brandedTypes";
import { attendeeIdentity, attendeeSheetTypes, AttendeeSheetType } from "./attendanceIdentityUtils";

const ATTENDANCE_REGISTER_TYPE = "attendanceRegister";
const ATTENDANCE_REGISTER_ATTENDEE_TYPE = "attendanceRegisterAttendee";
const STAFF_TYPE_APPROVER = "APPROVER";


/**
 * One tenant's worth of register ids to soft-delete. The delete event does not carry
 * campaignNumber, and the register id is globally unique, so tenant is the only scope needed.
 */
interface DeletedRegisterGroup {
    tenantId: TenantId;
    registerIds: AttendanceRegisterId[];
}

/**
 * Mark campaign_data rows for registers deleted in the attendance service, so generated
 * templates stop offering them. Registers deleted before this consumer shipped are not
 * retroactively corrected — only deletions observed from here on.
 */
export async function handleAttendanceRegisterDelete(messageObject: unknown): Promise<void> {
    const registers = arrayField(messageObject, "attendanceRegister");
    if (registers.length === 0) {
        logger.warn("ATTENDANCE DELETE :: message carried no attendanceRegister entries, skipping");
        return;
    }

    const groups = groupDeletedRegisters(registers);
    if (groups.length === 0) {
        logger.warn(`ATTENDANCE DELETE :: none of the ${registers.length} entries were usable, skipping`);
        return;
    }

    await runPerGroup(groups, markRegistersDeleted, "ATTENDANCE DELETE");
}

/**
 * Run every group even if one fails, so a transient error on one tenant does not skip the rest.
 * The listener catches and commits the offset regardless, so the rethrow only surfaces the failure
 * in logs — a group that fails every attempt is NOT redelivered and that change is lost.
 */
async function runPerGroup<T>(
    groups: T[],
    apply: (group: T) => Promise<void>,
    logPrefix: string
): Promise<void> {
    const failures: unknown[] = [];
    for (const group of groups) {
        try {
            await applyWithRetries(group, apply, logPrefix);
        } catch (error) {
            failures.push(error);
            logger.error(`${logPrefix} :: group failed, continuing with the rest: ${String(error)}`);
        }
    }
    if (failures.length > 0) {
        throw new Error(`${logPrefix} :: ${failures.length} of ${groups.length} group(s) failed: ${String(failures[0])}`);
    }
}

/**
 * Retried because this is the only chance the event gets: the offset commits even on a throw, so a
 * deadlock or a dropped connection would otherwise lose the deletion outright. Both updates are
 * idempotent (a flag and a date, keyed by identity), so a partly-applied attempt is safe to repeat.
 */
async function applyWithRetries<T>(
    group: T,
    apply: (group: T) => Promise<void>,
    logPrefix: string
): Promise<void> {
    const attempts = Math.max(1, config.attendanceRegister.syncGroupAttempts);
    for (let attempt = 1; attempt <= attempts; attempt++) {
        try {
            await apply(group);
            return;
        } catch (error) {
            if (attempt === attempts) throw error;
            logger.warn(`${logPrefix} :: attempt ${attempt} of ${attempts} failed, retrying: ${String(error)}`);
            await sleep(config.attendanceRegister.syncGroupRetryDelayMs * attempt);
        }
    }
}

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Record de-enrolments from the attendance service against the rows this service created, so
 * downloads can drop people once the date passes. Attendees are keyed by individualId, staff by
 * userId, and both by staff/sheet type. Entries without a de-enrolment date are ignored — the same
 * topic also carries tag edits, which must not remove anyone.
 */
export async function handleAttendanceAttendeeDeEnrolment(messageObject: unknown): Promise<void> {
    await applyDeEnrolments(arrayField(messageObject, "attendees"), "attendee",
        (entry) => trimmedString(entry.individualId),
        () => attendeeSheetTypes.worker);
}

export async function handleAttendanceStaffDeEnrolment(messageObject: unknown): Promise<void> {
    await applyDeEnrolments(arrayField(messageObject, "staff"), "staff",
        (entry) => trimmedString(entry.userId),
        (entry) => staffSheetType(entry.staffType));
}

/** Kafka payloads arrive untyped, so every field these consumers read is narrowed through one of these. */
function arrayField(message: unknown, key: string): unknown[] {
    const value = asRecord(message)?.[key];
    return Array.isArray(value) ? value : [];
}

function asRecord(value: unknown): Record<string, unknown> | null {
    return typeof value === "object" && value !== null && !Array.isArray(value)
        ? (value as Record<string, unknown>)
        : null;
}

function trimmedString(value: unknown): string {
    return typeof value === "string" ? value.trim() : "";
}

/** Attendance staff types map onto the marker/approver sheets the rows were stored under. */
function staffSheetType(staffType: unknown): AttendeeSheetType {
    return String(staffType ?? "").toUpperCase() === STAFF_TYPE_APPROVER
        ? attendeeSheetTypes.approver
        : attendeeSheetTypes.marker;
}

async function applyDeEnrolments(
    entries: unknown[],
    label: string,
    personIdOf: (entry: Record<string, unknown>) => string,
    sheetTypeOf: (entry: Record<string, unknown>) => AttendeeSheetType
): Promise<void> {
    if (entries.length === 0) {
        logger.warn(`ATTENDANCE DEENROL :: ${label} message carried no entries, skipping`);
        return;
    }

    // Group by (tenant, date) so one bulk de-enrolment is a single UPDATE
    const byKey = new Map<string, { tenantId: TenantId; denrollmentDate: number; identities: string[] }>();
    // Registers whose current-attendees file now shows a stale enrolment state
    const registersByTenant = new Map<string, Set<string>>();

    for (const rawEntry of entries) {
        const entry = asRecord(rawEntry);
        if (!entry) continue;
        const denrollmentDate = Number(entry.denrollmentDate);
        // No date means this is a tag edit on the shared topic, not a de-enrolment
        if (!Number.isFinite(denrollmentDate) || denrollmentDate <= 0) continue;

        const tenantId = trimmedString(entry.tenantId);
        const registerId = trimmedString(entry.registerId);
        const personId = personIdOf(entry);
        if (!tenantId || !registerId || !personId) {
            logger.warn(`ATTENDANCE DEENROL :: skipping ${label} entry missing tenantId/registerId/personId`);
            continue;
        }

        // Branded at the Kafka boundary: both values were just checked to be non-empty strings
        const identity = attendeeIdentity(
            registerId as AttendanceRegisterId,
            personId as IndividualId,
            sheetTypeOf(entry)
        );
        if (!registersByTenant.has(tenantId)) registersByTenant.set(tenantId, new Set());
        registersByTenant.get(tenantId)!.add(registerId);
        const key = `${tenantId}::${denrollmentDate}`;
        const existing = byKey.get(key);
        if (existing) existing.identities.push(identity);
        else byKey.set(key, { tenantId: tenantId as TenantId, denrollmentDate, identities: [identity] });
    }

    if (byKey.size === 0) {
        logger.info(`ATTENDANCE DEENROL :: no ${label} entries carried a de-enrolment date, nothing to do`);
        return;
    }

    await runPerGroup(Array.from(byKey.values()), async (group) => {
        const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, group.tenantId);
        const query = `UPDATE ${tableName}
                       SET denrollmentDate = $1
                       WHERE type = $2 AND uniqueIdAfterProcess = ANY($3)
                       RETURNING campaignNumber`;

        // Chunked: a bulk de-enrolment can carry thousands of identities, and one statement holding
        // them all locks campaign_data for as long as it runs.
        const batchSize = config.attendanceRegister.deEnrolmentUpdateBatchSize;
        let matched = 0;
        for (let i = 0; i < group.identities.length; i += batchSize) {
            const batch = group.identities.slice(i, i + batchSize);
            const result = await executeQuery(query,
                [group.denrollmentDate, ATTENDANCE_REGISTER_ATTENDEE_TYPE, batch]);
            matched += result?.rowCount ?? 0;
        }
        logger.info(
            `ATTENDANCE DEENROL :: tenant ${group.tenantId} — recorded de-enrolment on ${matched} of ` +
            `${group.identities.length} ${label} row(s)`
        );
        if (matched < group.identities.length) {
            logger.warn(
                `ATTENDANCE DEENROL :: tenant ${group.tenantId} — ${group.identities.length - matched} ${label} ` +
                `row(s) not found (created before this consumer shipped, or not yet persisted)`
            );
        }
    }, "ATTENDANCE DEENROL");

    // The console serves the current-attendees file from the last upload, so the recorded date has to
    // reach that file too. Only the debt is recorded here; the download that needs it does the work.
    for (const [tenantId, registerIds] of registersByTenant) {
        await markAttendeeSheetRefreshPending(tenantId as TenantId, Array.from(registerIds));
    }
}

/** Group by tenant so each group is one parameterised UPDATE rather than one per register. O(n). */
function groupDeletedRegisters(registers: unknown[]): DeletedRegisterGroup[] {
    const byTenant = new Map<string, DeletedRegisterGroup>();

    for (const entry of registers) {
        const register = asRecord(entry);
        const id = trimmedString(register?.id);
        const tenantId = trimmedString(register?.tenantId);

        if (!id || !tenantId) {
            logger.warn(`ATTENDANCE DELETE :: skipping entry missing id/tenantId (id=${id || "n/a"})`);
            continue;
        }

        const existing = byTenant.get(tenantId);
        if (existing) {
            existing.registerIds.push(id as AttendanceRegisterId);
        } else {
            byTenant.set(tenantId, {
                tenantId: tenantId as TenantId,
                registerIds: [id as AttendanceRegisterId],
            });
        }
    }

    return Array.from(byTenant.values());
}

/** Idempotent by construction — re-delivering the same event just rewrites isDeleted=true. */
async function markRegistersDeleted(group: DeletedRegisterGroup): Promise<void> {
    const tableName = getTableName(config?.DB_CONFIG?.DB_CAMPAIGN_DATA_TABLE_NAME, group.tenantId);
    const query = `UPDATE ${tableName}
                   SET isDeleted = true
                   WHERE type = $1 AND uniqueIdAfterProcess = ANY($2)
                   RETURNING campaignNumber`;

    const result = await executeQuery(query, [ATTENDANCE_REGISTER_TYPE, group.registerIds]);
    const matched = result?.rowCount ?? 0;
    logger.info(
        `ATTENDANCE DELETE :: tenant ${group.tenantId} — marked ${matched} of ` +
        `${group.registerIds.length} register row(s) deleted`
    );

    // A zero match means our row was not persisted yet when the delete arrived (campaign_data is
    // written asynchronously), so the deletion is lost. Logged loudly rather than retried.
    if (matched < group.registerIds.length) {
        logger.warn(
            `ATTENDANCE DELETE :: tenant ${group.tenantId} — ${group.registerIds.length - matched} register(s) ` +
            `had no campaign_data row to mark: ${group.registerIds.join(", ")}`
        );
    }

    // The console serves a snapshot file written at upload time, so the flag above is not enough on
    // its own — the deleted rows have to come out of that file too. Only the debt is recorded here;
    // the rewrite runs on the download that needs it, keeping file I/O off the listener thread.
    for (const campaignNumber of campaignNumbersOf(result)) {
        await markRegisterSheetRefreshPending(group.tenantId, campaignNumber);
    }
}

/** Distinct campaignNumbers touched by an UPDATE ... RETURNING campaignNumber. */
function campaignNumbersOf(result: unknown): string[] {
    const rows = arrayField(result, "rows");
    const campaignNumbers = rows
        .map((row) => trimmedString(asRecord(row)?.campaignnumber))
        .filter(Boolean);
    return Array.from(new Set(campaignNumbers));
}
