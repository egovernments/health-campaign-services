import config from "../config/index";
import { logger } from "./logger";
import { httpRequest } from "./request";



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
