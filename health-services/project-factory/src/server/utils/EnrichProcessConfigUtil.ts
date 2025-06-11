import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { ResourceDetails } from "../config/models/resourceDetailsSchema";
import { logger } from "./logger";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { getBoundaryOnWhichWeSplit, populateBoundariesRecursively } from "./campaignUtils";

export class EnrichProcessConfigUtil {
    // Example void method: modifies templateConfig in-place
    async enrichTargetProcessConfig(resourceDetails: ResourceDetails, templateConfig: any) {
        logger.info("Enriching Boundary Process Config");
        const { campaignId, tenantId } = resourceDetails;
        const campaignResp = await searchProjectTypeCampaignService({ tenantId, ids: [campaignId] });
        const campaignDetails = campaignResp?.CampaignDetails?.[0];
        if (!campaignDetails) throw new Error("Campaign not found");
        const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(tenantId, campaignDetails?.hierarchyType, true, true, false);
        const boundaries = campaignDetails?.boundaries || [];

        const boundaryChildren: Record<string, boolean> = boundaries.reduce((acc: any, boundary: any) => {
            acc[boundary.code] = boundary.includeAllChildren;
            return acc;
        }, {});

        const boundaryCodes: any = new Set(boundaries.map((boundary: any) => boundary.code));

        await populateBoundariesRecursively(
            boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0],
            boundaries,
            boundaryChildren[boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code],
            boundaryCodes,
            boundaryChildren
        );

        const splitOn = await getBoundaryOnWhichWeSplit(campaignId, tenantId);
        const sheetsNamesBasedOnSplit = boundaries.filter((b: any) => b.type === splitOn).map((b: any) => b.code);
        for (const sheetName of sheetsNamesBasedOnSplit) {
            templateConfig.sheets.push({
                sheetName,
                lockWholeSheetInProcessedFile: true
            })
        }
        logger.info("Boundary Process Config Enriched");
    }

    // Dynamic function executor for void methods
    async execute(functionName: string, resourceDetails: ResourceDetails, templateConfig: any) {
        const func = (this as any)[functionName];
        if (typeof func === 'function') {
            await func.call(this, resourceDetails, templateConfig);
        } else {
            throw new Error(`Function "${functionName}" is not defined.`);
        }
    }
}
