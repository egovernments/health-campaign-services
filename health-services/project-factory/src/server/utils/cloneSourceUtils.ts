import { resourceTypes } from "../config/constants";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { logger } from "./logger";

/**
 * Shared lookup of a clone's source campaign for the three steps that must agree on it: the create-time
 * payload preparation, the launch-time resource validation and the launch-time sheet borrow. Using one
 * set of search criteria and one sheet selector removes the disagreement that let validation accept a
 * clone whose borrow then found nothing (the launch would fall through to the regular path with no
 * resources). It does not make the two searches one read: the source can still change between them.
 *
 * Prefers the id: a campaign number is shared by a parent and its ongoing-update child, and the search
 * has no ORDER BY, so a number lookup is ambiguous while both are active. Falls back to the number for
 * payloads from the older console tree, which writes cloneFrom only. Never throws.
 */
export async function resolveCloneSourceCampaign(tenantId: string, cloneFrom?: string | null, clonedCampaignId?: string | null): Promise<any | null> {
    if (!cloneFrom && !clonedCampaignId) {
        return null;
    }
    try {
        const searchResponse = await searchProjectTypeCampaignService(
            clonedCampaignId
                ? { tenantId, ids: [clonedCampaignId] }
                : { tenantId, campaignNumber: cloneFrom }
        );
        return searchResponse?.CampaignDetails?.[0] || null;
    } catch (error) {
        logger.warn(`Failed to resolve clone source ${clonedCampaignId || cloneFrom}: ${error}`);
        return null;
    }
}

/** The unified workbook resource on a resolved source campaign, if it has one. */
export function unifiedSheetOf(campaign: any): any | null {
    const resources = Array.isArray(campaign?.resources) ? campaign.resources : [];
    return resources.find((r: any) => r?.type === resourceTypes.unifiedConsoleResources && r?.filestoreId) || null;
}
