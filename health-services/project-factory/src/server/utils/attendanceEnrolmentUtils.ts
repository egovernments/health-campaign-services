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
    isDeleted?: boolean;
    attendees?: AttendeeRecord[];
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
        return Array.isArray(registers)
            ? (registers as AttendanceRegister[]).filter((r) => !r?.isDeleted)
            : [];
    } catch (error) {
        logger.warn(`Attendance register search failed for campaign ${campaignNumber}: ${error instanceof Error ? error.message : String(error)}`);
        return [];
    }
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
    const liveByIndividual = new Map<string, { register: AttendanceRegister; attendee: AttendeeRecord }[]>();
    for (const register of registers) {
        if (register?.localityCode && !registerByLocality.has(register.localityCode)) {
            registerByLocality.set(register.localityCode, register);
        }
        for (const attendee of register?.attendees ?? []) {
            if (!attendee?.individualId || attendee?.denrollmentDate != null) continue;
            const bucket = liveByIndividual.get(attendee.individualId) ?? [];
            bucket.push({ register, attendee });
            liveByIndividual.set(attendee.individualId, bucket);
        }
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
