import config from "../config";
import { logger } from "./logger";
import { generatedResourceStatuses } from "../config/constants";
import { executeQuery, getTableName } from "./db";
import { getCampaignIdsByCampaignNumber } from "./genericUtils";
import { AttendanceRegisterId, TenantId } from "../config/models/brandedTypes";
import { attendeeIdentity } from "./attendanceIdentityUtils";

const ATTENDANCE_REGISTER_TYPE = "attendanceRegister";
const ATTENDANCE_REGISTER_ATTENDEE_TYPE = "attendanceRegisterAttendee";
const WORKER_SHEET_TYPE = "worker";
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
export async function handleAttendanceRegisterDelete(messageObject: any): Promise<void> {
    const registers: unknown = messageObject?.attendanceRegister;
    if (!Array.isArray(registers) || registers.length === 0) {
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
 * in logs — a failed group is NOT redelivered and that deletion/de-enrolment is lost.
 */
async function runPerGroup<T>(
    groups: T[],
    apply: (group: T) => Promise<void>,
    logPrefix: string
): Promise<void> {
    const failures: unknown[] = [];
    for (const group of groups) {
        try {
            await apply(group);
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
 * Record de-enrolments from the attendance service against the rows this service created, so
 * downloads can drop people once the date passes. Attendees are keyed by individualId, staff by
 * userId, and both by staff/sheet type. Entries without a de-enrolment date are ignored — the same
 * topic also carries tag edits, which must not remove anyone.
 */
export async function handleAttendanceAttendeeDeEnrolment(messageObject: any): Promise<void> {
    await applyDeEnrolments(messageObject?.attendees, "attendee",
        (entry) => typeof entry?.individualId === "string" ? entry.individualId.trim() : "",
        () => WORKER_SHEET_TYPE);
}

export async function handleAttendanceStaffDeEnrolment(messageObject: any): Promise<void> {
    await applyDeEnrolments(messageObject?.staff, "staff",
        (entry) => typeof entry?.userId === "string" ? entry.userId.trim() : "",
        (entry) => staffSheetType(entry?.staffType));
}

/** Attendance staff types map onto the marker/approver sheets the rows were stored under. */
function staffSheetType(staffType: unknown): string {
    return String(staffType ?? "").toUpperCase() === STAFF_TYPE_APPROVER ? "approver" : "marker";
}

async function applyDeEnrolments(
    entries: unknown,
    label: string,
    personIdOf: (entry: any) => string,
    sheetTypeOf: (entry: any) => string
): Promise<void> {
    if (!Array.isArray(entries) || entries.length === 0) {
        logger.warn(`ATTENDANCE DEENROL :: ${label} message carried no entries, skipping`);
        return;
    }

    // Group by (tenant, date) so one bulk de-enrolment is a single UPDATE
    const byKey = new Map<string, { tenantId: TenantId; denrollmentDate: number; identities: string[] }>();

    for (const entry of entries as any[]) {
        const denrollmentDate = Number(entry?.denrollmentDate);
        // No date means this is a tag edit on the shared topic, not a de-enrolment
        if (!Number.isFinite(denrollmentDate) || denrollmentDate <= 0) continue;

        const tenantId = typeof entry?.tenantId === "string" ? entry.tenantId.trim() : "";
        const registerId = typeof entry?.registerId === "string" ? entry.registerId.trim() : "";
        const personId = personIdOf(entry);
        if (!tenantId || !registerId || !personId) {
            logger.warn(`ATTENDANCE DEENROL :: skipping ${label} entry missing tenantId/registerId/personId`);
            continue;
        }

        const identity = attendeeIdentity(registerId, personId, sheetTypeOf(entry));
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
        const result = await executeQuery(query,
            [group.denrollmentDate, ATTENDANCE_REGISTER_ATTENDEE_TYPE, group.identities]);
        const matched = result?.rowCount ?? 0;
        await expireGeneratedTemplates(group.tenantId, campaignNumbersOf(result), ATTENDANCE_REGISTER_ATTENDEE_TYPE);
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
}

/** Group by tenant so each group is one parameterised UPDATE rather than one per register. O(n). */
function groupDeletedRegisters(registers: unknown[]): DeletedRegisterGroup[] {
    const byTenant = new Map<string, DeletedRegisterGroup>();

    for (const entry of registers) {
        const register = entry as Record<string, unknown>;
        const id = typeof register?.id === "string" ? register.id.trim() : "";
        const tenantId = typeof register?.tenantId === "string" ? register.tenantId.trim() : "";

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
    await expireGeneratedTemplates(group.tenantId, campaignNumbersOf(result), ATTENDANCE_REGISTER_TYPE);
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
}

/** Distinct campaignNumbers touched by an UPDATE ... RETURNING campaignNumber. */
function campaignNumbersOf(result: any): string[] {
    const rows: any[] = result?.rows || [];
    return Array.from(new Set(rows.map((r) => r?.campaignnumber).filter(Boolean)));
}

/**
 * Expire the generated template for the affected campaigns. Flagging the row is not enough on its
 * own: a download serves the last completed template, so without this the change only appears after
 * something else happens to trigger regeneration.
 */
async function expireGeneratedTemplates(
    tenantId: TenantId,
    campaignNumbers: string[],
    type: string
): Promise<void> {
    if (campaignNumbers.length === 0) return;

    const campaignIds: string[] = [];
    for (const campaignNumber of campaignNumbers) {
        campaignIds.push(...await getCampaignIdsByCampaignNumber(campaignNumber, tenantId));
    }
    if (campaignIds.length === 0) {
        logger.warn(`ATTENDANCE SYNC :: no campaignId resolved for ${campaignNumbers.join(", ")}, template not expired`);
        return;
    }

    const tableName = getTableName(config?.DB_CONFIG?.DB_GENERATED_RESOURCE_DETAILS_TABLE_NAME, tenantId);
    const query = `UPDATE ${tableName}
                   SET status = $1
                   WHERE type = $2 AND campaignId = ANY($3) AND status <> $1`;
    const result = await executeQuery(query,
        [generatedResourceStatuses.expired, type, Array.from(new Set(campaignIds))]);
    logger.info(
        `ATTENDANCE SYNC :: expired ${result?.rowCount ?? 0} generated '${type}' template(s) ` +
        `for campaign(s) ${campaignNumbers.join(", ")} so the next download rebuilds`
    );
}
