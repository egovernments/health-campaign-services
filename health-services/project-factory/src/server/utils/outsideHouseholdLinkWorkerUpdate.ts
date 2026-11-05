import { RequestInfo } from "../config/models/requestInfoSchema";
import config from "../config";
import { httpRequest } from "./request";
import { sameOutsideHouseholdLinkFields } from "./outsideHouseholdLinkFields";

export type LinkWorkerUpdateFailure = { link: any; error: string };
export type LinkWorkerUpdateResult = { updated: any[]; failures: LinkWorkerUpdateFailure[]; notAttempted: any[] };

/** The links whose stored worker fields differ from the wanted ones (wanted keyed by the link's facility id). */
export function linksNeedingWorkerUpdate(links: any[], wantedByFacilityId: Map<string, Record<string, any>>): any[] {
    return links.filter((link: any) => {
        const wanted = wantedByFacilityId.get(link?.facilityId);
        return wanted !== undefined && !sameOutsideHouseholdLinkFields(link.additionalFields, wanted);
    });
}

/**
 * Writes the wanted worker fields onto the links, a few at a time (OUTSIDE_HOUSEHOLD_LINK_UPDATE_CONCURRENCY). After a
 * group with a failed call the rest is not attempted, so a failing project service costs one group, not every link; the
 * failed and the not-attempted links are returned for the caller to record. retry: httpRequest's own retry per call.
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
        const settled = await Promise.allSettled(chunk.map((link: any) =>
            httpRequest(updateUrl, { RequestInfo: requestInfo, ProjectFacility: { ...link, additionalFields: wantedByFacilityId.get(link.facilityId) } },
                undefined, 'post', undefined, undefined, undefined, retry)));
        settled.forEach((outcome, index) => {
            if (outcome.status === "fulfilled") {
                result.updated.push(chunk[index]);
            } else {
                const reason: any = outcome.reason;
                result.failures.push({ link: chunk[index], error: reason instanceof Error ? reason.message : String(reason?.message ?? reason) });
            }
        });
        if (result.failures.length > 0) {
            result.notAttempted = links.slice(i + concurrency);
            break;
        }
    }
    return result;
}
