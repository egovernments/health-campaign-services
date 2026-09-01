import config from "../config";
import { logger } from "./logger";
import { httpRequest } from "./request";
import {
    AttendanceRegisterId,
    BoundaryCode,
    CampaignNumber,
    IndividualId,
    TenantId,
} from "../config/models/brandedTypes";

/** An attendee row as the attendance store persists it. `denrollmentDate` spelling is the store's, not a typo. */
interface AttendeeRecord {
    id?: string;
    tenantId?: TenantId;
    individualId?: IndividualId;
    registerId?: AttendanceRegisterId;
    enrollmentDate?: number;
    denrollmentDate?: number;
    tag?: string;
    additionalDetails?: Record<string, unknown>;
    auditDetails?: Record<string, unknown>;
}

interface AttendanceRegister {
    id?: AttendanceRegisterId;
    tenantId?: TenantId;
    localityCode?: BoundaryCode;
    registerNumber?: string;
    campaignNumber?: CampaignNumber;
    isDeleted?: boolean;
}

/** One worker's move, resolved by the caller that already knows the new area. */
export interface EnrolmentMove {
    individualId: IndividualId;
    targetLocalityCode: BoundaryCode;
}

function url(path: unknown): string {
    const host = (config as { host?: Record<string, unknown> })?.host?.attendanceHost;
    if (typeof host !== "string" || typeof path !== "string" || !host || !path) return "";
    return `${host.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
}

function paths(): Record<string, unknown> {
    return (config as { paths?: Record<string, unknown> })?.paths ?? {};
}

/**
 * Registers for one campaign, fetched ONCE per batch — a per-worker search would be one HTTP call
 * per row on a 50k-row upload. Returns [] on failure, which callers treat as "cannot reconcile"
 * rather than "this worker has no enrolment".
 */
export async function fetchCampaignRegisters(
    campaignNumber: CampaignNumber,
    tenantId: TenantId,
    requestInfo: unknown
): Promise<AttendanceRegister[]> {
    const endpoint = url(paths().attendanceRegisterSearch);
    if (!endpoint) {
        logger.warn("Attendance reconciliation skipped: attendance host or register-search path not configured");
        return [];
    }
    try {
        const response = await httpRequest(
            endpoint,
            { RequestInfo: requestInfo, attendanceRegisterSearchCriteria: { tenantId, campaignNumber } },
            { tenantId, limit: config.attendanceRegister.batchSize, offset: 0 }
        );
        const registers = (response as { attendanceRegister?: unknown })?.attendanceRegister;
        if (!Array.isArray(registers)) return [];
        // The attendance search IGNORES campaignNumber - verified on unified-dev, where asking for one
        // campaign returned 100 registers spanning four unrelated hierarchies. Filtering client-side is
        // mandatory: without it a destination register could belong to a DIFFERENT campaign, which would
        // enrol the worker onto someone else's register and move their pay there.
        const all = registers as AttendanceRegister[];
        const live = all.filter((r) => !r?.isDeleted);
        const ours = live.filter((r) => r?.campaignNumber === campaignNumber);
        if (ours.length !== live.length) {
            logger.info(`Attendance: server returned ${live.length} registers for campaign ${campaignNumber}; ${ours.length} actually belong to it (criteria ignored server-side, filtered locally)`);
        }
        return ours;
    } catch (error) {
        logger.warn(`Attendance register search failed for campaign ${campaignNumber}: ${error instanceof Error ? error.message : String(error)}`);
        return [];
    }
}


/**
 * Live (not end-dated) enrolments for the given individuals, gathered by a BOUNDED paged scan.
 *
 * Does not read register.attendees: that field is not reliably populated by the register search
 * (verified on unified-dev - a register probed by its own id returned attendees:null), so trusting it
 * makes every worker look unenrolled and the realignment silently do nothing. The attendee search
 * ignores its criteria too, hence the client-side filter over pages.
 *
 * Returns null when the scan cap is hit, meaning "enrolment state undetermined" - callers must then
 * make no change rather than act on a partial view.
 */
async function fetchLiveEnrolments(
    individualIds: Set<string>,
    tenantId: TenantId,
    requestInfo: unknown
): Promise<Map<string, AttendeeRecord[]> | null> {
    const endpoint = url(paths().attendanceAttendeeSearch);
    if (!endpoint) {
        logger.warn("Attendance reconciliation skipped: attendee-search path not configured");
        return null;
    }
    const pageSize = config.attendanceRegister.attendeeSearchPageSize;
    const maxPages = config.attendanceRegister.enrolmentScanMaxPages;
    const found = new Map<string, AttendeeRecord[]>();
    for (let page = 0; page < maxPages; page++) {
        let batch: AttendeeRecord[];
        try {
            const response = await httpRequest(
                endpoint,
                { RequestInfo: requestInfo, attendeeSearchCriteria: { tenantId } },
                { tenantId, limit: pageSize, offset: page * pageSize }
            );
            const raw = (response as { attendees?: unknown })?.attendees;
            batch = Array.isArray(raw) ? (raw as AttendeeRecord[]) : [];
        } catch (error) {
            logger.warn(`Attendee scan failed on page ${page}: ${error instanceof Error ? error.message : String(error)}`);
            return null;
        }
        for (const attendee of batch) {
            const id = attendee?.individualId;
            if (!id || !individualIds.has(id) || attendee?.denrollmentDate != null) continue;
            const bucket = found.get(id) ?? [];
            bucket.push(attendee);
            found.set(id, bucket);
        }
        if (batch.length < pageSize) return found; // last page reached cleanly
    }
    logger.warn(`Attendance realignment ABORTED: attendee scan hit the ${maxPages}-page cap, so enrolment state is undetermined. No enrolment was changed; ${individualIds.size} worker(s) may still be paid against the area they left.`);
    return null;
}

/**
 * Realign attendance enrolments with the areas workers are now assigned to, so pay follows the
 * worker: a bill's area comes from the register it was generated from, never from the assignment,
 * so an area change with a stale enrolment keeps paying the area the worker left.
 *
 * Reconciles current state rather than pairing add/remove events, so it is idempotent under Kafka
 * redelivery. Enrols on the destination BEFORE end-dating the old row, so a partial failure leaves
 * the worker enrolled twice (visible, repairable) rather than not at all. Never throws — the
 * assignment change is already durable and must not be failed by a missing register.
 */
export async function reconcileAttendanceEnrolments(
    moves: EnrolmentMove[],
    campaignNumber: CampaignNumber,
    tenantId: TenantId,
    requestInfo: unknown
): Promise<void> {
    if (moves.length === 0) return;

    const registers = await fetchCampaignRegisters(campaignNumber, tenantId, requestInfo);
    if (registers.length === 0) return;

    const registerByLocality = new Map<string, AttendanceRegister>();
    const registerById = new Map<string, AttendanceRegister>();
    for (const register of registers) {
        if (register?.id) registerById.set(register.id, register);
        if (register?.localityCode && !registerByLocality.has(register.localityCode)) {
            registerByLocality.set(register.localityCode, register);
        }
    }

    // Enrolments come from the attendee search, NOT from register.attendees - see fetchLiveEnrolments.
    const enrolments = await fetchLiveEnrolments(
        new Set(moves.map((m) => String(m.individualId))), tenantId, requestInfo);
    if (enrolments === null) return; // undetermined - make no change

    const liveByIndividual = new Map<string, { register: AttendanceRegister; attendee: AttendeeRecord }[]>();
    for (const [individualId, attendees] of enrolments) {
        const pairs = attendees
            .map((attendee) => ({ register: registerById.get(String(attendee.registerId)), attendee }))
            .filter((x): x is { register: AttendanceRegister; attendee: AttendeeRecord } => !!x.register);
        if (pairs.length) liveByIndividual.set(individualId, pairs);
    }

    const createEndpoint = url(paths().attendanceAttendeeCreate);
    const deleteEndpoint = url(paths().attendanceAttendeeDelete);
    if (!createEndpoint || !deleteEndpoint) {
        logger.warn("Attendance reconciliation skipped: attendee create/delete path not configured");
        return;
    }

    const batchSize = config.attendanceRegister.batchSize;
    for (let i = 0; i < moves.length; i += batchSize) {
        await Promise.all(
            moves.slice(i, i + batchSize).map((move) =>
                moveOneEnrolment(move, liveByIndividual, registerByLocality, createEndpoint, deleteEndpoint, campaignNumber, tenantId, requestInfo)
            )
        );
    }
}

async function moveOneEnrolment(
    move: EnrolmentMove,
    liveByIndividual: Map<string, { register: AttendanceRegister; attendee: AttendeeRecord }[]>,
    registerByLocality: Map<string, AttendanceRegister>,
    createEndpoint: string,
    deleteEndpoint: string,
    campaignNumber: CampaignNumber,
    tenantId: TenantId,
    requestInfo: unknown
): Promise<void> {
    const live = liveByIndividual.get(move.individualId) ?? [];
    // No enrolment is not an error: enrolment is a separate explicit upload, and enrolling here
    // would invent attendance intent nobody expressed.
    if (live.length === 0) return;
    if (live.some(({ register }) => register?.localityCode === move.targetLocalityCode)) return;

    const destination = registerByLocality.get(move.targetLocalityCode);
    if (!destination?.id) {
        logger.warn(
            `Attendance mismatch NOT corrected for individual ${move.individualId}: no register at ${move.targetLocalityCode} in campaign ${campaignNumber}. ` +
            `Existing enrolment left in place so the worker stays attendable and payable, but pay will follow the OLD area until a register exists here.`
        );
        return;
    }

    const now = Date.now();
    try {
        await httpRequest(
            createEndpoint,
            { RequestInfo: requestInfo, attendees: [{ tenantId, individualId: move.individualId, registerId: destination.id, enrollmentDate: now }] },
            { tenantId }
        );
    } catch (error) {
        logger.warn(`Attendance enrolment at ${move.targetLocalityCode} failed for individual ${move.individualId}; old enrolment left untouched: ${error instanceof Error ? error.message : String(error)}`);
        return;
    }

    for (const { register, attendee } of live) {
        try {
            await httpRequest(
                deleteEndpoint,
                { RequestInfo: requestInfo, attendees: [{ ...attendee, denrollmentDate: now }] },
                { tenantId }
            );
        } catch (error) {
            logger.warn(`Could not end-date individual ${move.individualId} on register ${register?.id}; they now hold TWO enrolments and need review: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
}
