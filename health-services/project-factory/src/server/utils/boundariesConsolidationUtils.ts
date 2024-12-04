import config from "../config/index";
import { httpRequest } from "./request";
import { processBoundary } from "./campaignUtils";

async function consolidateBoundaries(messageObject: any, hierarchyType: any, tenantId: any, code: any, boundaries: any) {
  const params = {
    tenantId: tenantId,
    codes: code,
    hierarchyType: hierarchyType,
    includeChildren: true,
  };
  const header = {
    cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId
      }${params.codes || ""}${params?.includeChildren || ""}`,
  };
  const boundaryResponse = await httpRequest(
    config.host.boundaryHost + config.paths.boundaryRelationship,
    messageObject?.RequestInfo,
    params,
    undefined,
    undefined,
    header
  );
  if (boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]) {
    const boundaryChildren = boundaries.reduce((acc: any, boundary: any) => {
      acc[boundary.code] = boundary?.includeAllChildren;
      return acc;
    }, {});
    const boundaryCodes = new Set(
      boundaries.map((boundary: any) => boundary.code)
    );
    await processBoundary(
      boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0],
      boundaries,
      boundaryChildren[
      boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]?.code
      ],
      boundaryCodes,
      boundaryChildren
    );
    return boundaries;
  }
}
export { consolidateBoundaries, }