import config from "../config/index";
import { logger } from "./logger";
import { httpRequest } from "./request";
import { getBoundaryRelationshipData } from "../api/boundaryApis";
import { getRootBoundaryCode } from "./campaignUtils";



export const getBoundaryColumnName = () => {
  // Construct Boundary column name from the config
  return config?.boundary?.boundaryCode;
};

// Function to generate localisation module name based on hierarchy type
export const getBoundaryTabName = () => {
  // Construct Boundary tab name from the config
  return config?.boundary?.boundaryTab;
};


export async function getLatLongMapForBoundaryCodes(request:any, boundaryCodeList: any[]) {
  const chunkSize = 20;
  const boundaryCodeChunks = [];
  let boundaryCodeLatLongMap: { [key: string]: number[] } = {}; // Map to hold lat/long data for boundary codes
  
  // Split the boundaryCodeList into chunks of 20
  for (let i = 0; i < boundaryCodeList.length; i += chunkSize) {
    boundaryCodeChunks.push(boundaryCodeList.slice(i, i + chunkSize));
  }
  
  // Process each chunk
  for (const chunk of boundaryCodeChunks) {
    const boundaryCodeString = chunk.join(", ");
  
    // Only proceed if there are valid boundary codes
    if (boundaryCodeList.length > 0 && boundaryCodeString) {
      logger.info(`Creating boundary entities for codes: ${boundaryCodeString}`);
  
      try {
        // Make the API request to fetch boundary entities
        const boundaryEntityResponse = await httpRequest(
          config.host.boundaryHost + config.paths.boundaryEntity,
          request.body,  // Passing the request body
          { tenantId: request?.query?.tenantId, codes: boundaryCodeString }  // Query params
        );
  
        // Process the boundary entities and extract lat/long for 'Point' geometries
        if (boundaryEntityResponse?.Boundary) {
          for (const boundary of boundaryEntityResponse.Boundary) {
            if (boundary?.geometry && boundary.geometry.type === "Point") {
              boundaryCodeLatLongMap[boundary.code] = boundary.geometry.coordinates;
            }
          }
        }
  
      } catch (error: any) {
        // Log error but do not stop the process for other chunks
        console.log(error);
        logger.error(`Failed to fetch boundary entities for codes: ${boundaryCodeString}, Error: ${error.message}`);
      }
  
    } else {
      // Log when the chunk is empty or invalid
      logger.debug(`Skipping empty or invalid chunk: ${boundaryCodeString}`);
    }
  }
  boundaryCodeLatLongMap['ADMIN_MO'] = [200,100];
  // Return or process the map further if needed
  return boundaryCodeLatLongMap;  
}


export async function getAllBoundariesForCampaign(campaignDetails: any) {
  logger.info("GETTING ALL BOUNDARIES FOR CAMPAIGN");
  const { boundaries } = campaignDetails;
  const boundaryDataFromRootOnwards = await getBoundaryRelationshipDataFromCampaignDetails(campaignDetails);
  let allBoundaries = [];
  var boundaryChildren = boundaries?.reduce((acc: any, boundary: any) => {
    acc[boundary.code] = boundary?.includeAllChildren;
    return acc;
  }, {});
  if (boundaryDataFromRootOnwards?.[0]) {
        allBoundaries = await getAllBoundariesWithChildren(
          boundaries,
          boundaryDataFromRootOnwards?.[0],
          boundaryChildren
        );
  }
  else{
    throw(new Error("Root boundary not found"));
  }
  logger.info("ALL BOUNDARIES FOR CAMPAIGN FETCHED");
  return allBoundaries;
}

async function getAllBoundariesWithChildren(
  boundaries: any[],
  currentBoundary: any,
  boundaryChildren: any
) {
  const boundaryCodes = new Set(boundaries.map((boundary: any) => boundary.code))
  // Start the recursion from the current boundary
  addChildrenRecursively(currentBoundary, boundaryChildren, boundaryCodes, boundaries);

  return boundaries;
}



function addChildrenRecursively(
  boundary: any,
  boundaryChildren: Record<string, boolean>,
  boundaryCodes: Set<string>,
  boundaries: any[]
) {
  // If no children, exit the recursion
  if (!boundary?.children) return;

  boundary.children.forEach((child: any) => {
    // If the child is already included, continue deeper
    if (boundaryCodes.has(child.code)) {
      addChildrenRecursively(child, boundaryChildren, boundaryCodes, boundaries);
    }
    // If the current boundary has `includeChildren` set to true, add child and go deeper
    else if (boundaryChildren[boundary.code]) {
      boundaries.push({
        code: child.code,
        type: child.boundaryType,
        parent : boundary.code
      });
      boundaryCodes.add(child.code);
      // Set the child's includeChildren to true to ensure the entire branch is added
      boundaryChildren[child.code] = true;
      addChildrenRecursively(child, boundaryChildren, boundaryCodes, boundaries);
    }
  });
}

export async function getBoundaryRelationshipDataFromCampaignDetails(campaignDetails: any) {
  const { tenantId, hierarchyType, boundaries } = campaignDetails;
  const rootBoundaryCode = getRootBoundaryCode(boundaries);
  const params = {
    includeChildren: true,
    codes: rootBoundaryCode,
    tenantId,
    hierarchyType
  };
  const boundaryDataFromRootOnwards = await getBoundaryRelationshipData(
    params
  );
  return boundaryDataFromRootOnwards;
}




