import config from "../config";
import { logger } from "../utils/logger";
import { defaultheader, httpRequest } from "../utils/request";
import { defaultRequestInfo } from "./coreApis";

export async function getBoundaryRelationshipData(params: any) {
    logger.info("Boundary relationship search initiated");
    const url = `${config.host.boundaryHost}${config.paths.boundaryRelationship}`;
    const header = {
        ...defaultheader,
        cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId}${params.codes || ''}${params?.includeChildren || ''}`,
    }
    const boundaryRelationshipResponse = await httpRequest(url, defaultRequestInfo, params, undefined, undefined, header);
    logger.info("Boundary relationship search response received");
    return boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
}
