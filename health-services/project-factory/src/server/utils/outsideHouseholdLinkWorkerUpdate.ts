import { RequestInfo } from "../config/models/requestInfoSchema";
import config from "../config";
import { httpRequest } from "./request";
import { sameOutsideHouseholdLinkFields } from "./outsideHouseholdLinkFields";

/**
 * How a failed site link update is handled. temporary: a network failure, a 5xx, or health-project not reaching the
 * facility service; a later try may pass. permanent: another link has the same project and site, or the link's project
 * or site is not found; every try fails until the data is fixed. missing: the link no longer exists.
 */
export type LinkWorkerFailureKind = 'temporary' | 'permanent' | 'missing';
export type LinkWorkerUpdateFailure = { link: any; error: string; kind: LinkWorkerFailureKind };
export type LinkWorkerUpdateResult = { updated: any[]; failures: LinkWorkerUpdateFailure[]; notAttempted: any[] };
type SendOutcome = { ok: true } | { ok: false; kind: LinkWorkerFailureKind; error: string };

// Error codes of health-project's project-facility update (its validators, health-services-common ValidatorUtils). The
// single update answers every refusal with code VALIDATION_ERROR; the validator's own code is only inside the message
// ("...errorCode=DUPLICATE_ENTITY..."), next to a dump of the request (which carries the RequestInfo and its token).
const ROW_VERSION_MISMATCH = 'MISMATCHED_ROW_VERSION';
const LINK_MISSING_CODES = ['NON_EXISTENT_ENTITY', 'IS_DELETED_TRUE'];
const PERMANENT_CODES = ['DUPLICATE_ENTITY', 'NON_EXISTENT_RELATED_ENTITY', 'NULL_ID', 'TENANT_ID_INVALID'];
const REASONS: Record<string, string> = {
    DUPLICATE_ENTITY: 'another link has the same project and site; remove the extra link, then upload the file again',
    NON_EXISTENT_ENTITY: 'the link no longer exists',
    IS_DELETED_TRUE: 'the link no longer exists',
    NON_EXISTENT_RELATED_ENTITY: 'the project or the site of the link was not found',
    MISMATCHED_ROW_VERSION: 'the link kept changing while it was updated',
    NETWORK_ERROR: 'health-project could not reach the facility service',
};
const MAX_REASON_LENGTH = 300;

/** The links whose stored worker fields differ from the wanted ones (wanted keyed by the link's facility id). */
export function linksNeedingWorkerUpdate(links: any[], wantedByFacilityId: Map<string, Record<string, any>>): any[] {
    return links.filter((link: any) => {
        const wanted = wantedByFacilityId.get(link?.facilityId);
        return wanted !== undefined && !sameOutsideHouseholdLinkFields(link.additionalFields, wanted);
    });
}

/** Groups of links that have the same project and site; health-project refuses the update of all but one of them. */
export function duplicateLinkGroups(links: any[]): any[][] {
    const byProjectAndSite = new Map<string, any[]>();
    for (const link of links) {
        const key = `${link?.projectId}|${link?.facilityId}`;
        byProjectAndSite.set(key, [...(byProjectAndSite.get(key) || []), link]);
    }
    return Array.from(byProjectAndSite.values()).filter((group) => group.length > 1);
}

/** The links without repeats (a paged search can return a link twice when links change while it pages). */
export function distinctLinks(links: any[]): any[] {
    const seen = new Set<string>();
    return links.filter((link: any) => (link?.id && seen.has(link.id) ? false : (seen.add(link?.id), true)));
}

/**
 * The failure recorded for each link of a duplicate group: it is not sent, as only the data can be fixed. Each link's
 * reason names it as the one to keep, so the reason on the row that tracks a link says which other link to remove.
 */
export function duplicateLinkFailures(group: any[]): LinkWorkerUpdateFailure[] {
    return group.map((link: any) => {
        const others = group.filter((other: any) => other !== link).map((other: any) => other?.id).join(', ');
        return { link, kind: 'permanent' as const,
            error: `DUPLICATE_ENTITY: links ${link?.id} and ${others} have the same project and site; keep ${link?.id} (this row's link), remove ${others}, then upload the file again` };
    });
}

/** Every validator code inside a health-project message ("...errorCode=DUPLICATE_ENTITY..."). */
export function innerErrorCodes(text: string): string[] {
    const codes: string[] = [];
    const pattern = /errorCode=([A-Z_]+)/g;
    for (let match = pattern.exec(text); match; match = pattern.exec(text)) codes.push(match[1]);
    return codes;
}

/** health-project's error codes: the response code(s) and every validator code inside the message ("errorCode=..."). */
function errorCodes(error: any): string[] {
    const codes = String(error?.code ?? '').split(':').map((code) => code.trim()).filter(Boolean);
    codes.push(...innerErrorCodes(`${error?.message ?? ''} ${error?.description ?? ''}`));
    return Array.from(new Set(codes));
}

function failureKind(codes: string[]): LinkWorkerFailureKind {
    if (codes.some((code) => LINK_MISSING_CODES.includes(code))) return 'missing';
    if (codes.some((code) => PERMANENT_CODES.includes(code))) return 'permanent';
    return 'temporary';
}

/**
 * A short reason made only of codes and fixed texts: health-project's message is never kept, as it is a dump of the
 * request with its RequestInfo (and token). A code that is not a plain error code (e.g. a socket error text) is left out.
 */
function describeError(codes: string[]): string {
    const plainCodes = codes.filter((code) => /^[A-Z_]+$/.test(code));
    // VALIDATION_ERROR only wraps the validator's own code, so it is shown only when there is no other code
    const inner = plainCodes.filter((code) => code !== 'VALIDATION_ERROR');
    const shown = inner.length > 0 ? inner : plainCodes;
    const known = shown.find((code) => REASONS[code]);
    const reason = known ? REASONS[known]
        : shown.length > 0 ? 'health-project refused the link update' : 'health-project could not be reached or gave no usable answer';
    return `${shown.length > 0 ? `${shown.join(':')}: ` : ''}${reason}`.slice(0, MAX_REASON_LENGTH);
}

/** True for the answer of a successful update (it carries the updated link); any other answer is a failure. */
function isUpdated(response: any): boolean {
    return Boolean(response && typeof response === 'object' && !response.Errors && response.ProjectFacility);
}

/** The link as stored now (read by its id), or null when it no longer exists. */
async function readLink(link: any, requestInfo: RequestInfo | undefined): Promise<any | null> {
    const response = await httpRequest(`${config.host.projectHost}${config.paths.projectFacilitySearch}`,
        { RequestInfo: requestInfo, ProjectFacility: { id: [link.id] } },
        { tenantId: String(link?.tenantId ?? '').split('.')[0], limit: 1, offset: 0, includeDeleted: false });
    return (response?.ProjectFacilities || []).find((found: any) => found?.id === link.id && !found?.isDeleted) || null;
}

/**
 * Updates one link. It is sent once without httpRequest's retry. A stale row version reads the link again (the update is
 * done if the link already has the worker, e.g. an earlier call that timed out did reach health-project) and resends it.
 * A temporary failure is sent once more with httpRequest's retry when retry is set. A permanent or missing link is
 * returned at once, as another try cannot change it.
 */
async function sendLinkWorkerUpdate(
    updateUrl: string,
    link: any,
    wanted: Record<string, any>,
    requestInfo: RequestInfo | undefined,
    retry: boolean
): Promise<SendOutcome> {
    // A missing or unreadable setting (NaN) must not let the re-reads run forever
    const maxRereads = Math.max(0, config.mapping.outsideHouseholdLinkRereadAttempts || 0);
    const rereadWaitMs = Math.max(0, config.mapping.outsideHouseholdLinkRereadWaitMs || 0);
    let current = link;
    let rereads = 0;
    let retried = false;
    for (;;) {
        let response: any;
        try {
            response = await httpRequest(updateUrl, { RequestInfo: requestInfo, ProjectFacility: { ...current, additionalFields: wanted } },
                undefined, 'post', undefined, undefined, undefined, retried, true);
        } catch (error) {
            return { ok: false, kind: 'temporary', error: describeError(errorCodes({ message: error instanceof Error ? error.message : String(error) })) };
        }
        if (isUpdated(response)) return { ok: true };
        // A failure answer without Errors (e.g. a proxy's 502 page) is a temporary failure, never a success
        const error = response?.Errors?.[0] ?? {};
        const codes = errorCodes(error);
        if (codes.includes(ROW_VERSION_MISMATCH)) {
            if (rereads >= maxRereads) return { ok: false, kind: 'temporary', error: describeError(codes) };
            rereads++;
            await new Promise((resolve) => setTimeout(resolve, rereadWaitMs));
            let fresh: any;
            try {
                fresh = await readLink(current, requestInfo);
            } catch (readError) {
                return { ok: false, kind: 'temporary', error: `Could not read the link again: ${describeError(errorCodes({ message: readError instanceof Error ? readError.message : String(readError) }))}` };
            }
            if (!fresh) return { ok: false, kind: 'missing', error: describeError(['NON_EXISTENT_ENTITY']) };
            if (sameOutsideHouseholdLinkFields(fresh.additionalFields, wanted)) return { ok: true };
            current = fresh;
            continue;
        }
        const kind = failureKind(codes);
        if (kind === 'temporary' && retry && !retried) {
            retried = true;
            continue;
        }
        return { ok: false, kind, error: describeError(codes) };
    }
}

/**
 * Writes the wanted worker fields onto the links, a few at a time (OUTSIDE_HOUSEHOLD_LINK_UPDATE_CONCURRENCY). After a
 * group with a temporary failure the rest is not attempted, so a failing project service costs one group, not every
 * link; a permanent or missing link concerns that link alone, so the others go on. The failed and the not-attempted
 * links are returned for the caller to record. retry: one more send with httpRequest's own retry on a temporary failure.
 */
export async function updateLinkWorkers(
    links: any[],
    wantedByFacilityId: Map<string, Record<string, any>>,
    requestInfo: RequestInfo | undefined,
    retry: boolean
): Promise<LinkWorkerUpdateResult> {
    const updateUrl = `${config.host.projectHost}${config.paths.projectFacilityUpdate}`;
    const concurrency = Math.max(1, config.mapping.outsideHouseholdLinkUpdateConcurrency || 1);
    const result: LinkWorkerUpdateResult = { updated: [], failures: [], notAttempted: [] };
    for (let i = 0; i < links.length; i += concurrency) {
        const chunk = links.slice(i, i + concurrency);
        const outcomes = await Promise.all(chunk.map((link: any) =>
            sendLinkWorkerUpdate(updateUrl, link, wantedByFacilityId.get(link.facilityId) as Record<string, any>, requestInfo, retry)));
        outcomes.forEach((outcome, index) => {
            if (outcome.ok) {
                result.updated.push(chunk[index]);
            } else {
                result.failures.push({ link: chunk[index], error: outcome.error, kind: outcome.kind });
            }
        });
        if (outcomes.some((outcome) => !outcome.ok && outcome.kind === 'temporary')) {
            result.notAttempted = links.slice(i + concurrency);
            break;
        }
    }
    return result;
}
