import { callMdmsTypeSchema, createAndUploadFile } from "../api/genericApis";
import { getFormattedStringForDebug, logger } from "./logger";

import { updateProjectTypeCampaignService } from "../service/campaignManageService";
import {
  searchPlan,
  searchPlanCensus,
  searchPlanFacility,
} from "../api/microplanApis";
import {
  createAndPollForCompletion,
  getTheGeneratedResource,
} from "./pollUtils";
import { getExcelWorkbookFromFileURL } from "./excelUtils";
import {
  fetchFileFromFilestore,
  searchBoundaryRelationshipData,
  searchMDMSDataViaV1Api,
} from "../api/coreApis";
import { getLocalizedName } from "./campaignUtils";
import config from "../config";
import { throwError } from "./genericUtils";
import { MDMSModels } from "../models";

/**
 * Adds data rows to the provided worksheet.
 * @param worksheet The worksheet to which the data should be added.
 * @param data Array of data rows to add.
 */
export async function addDataToWorksheet(worksheet: any, data: string[][]) {
  data.forEach((row) => {
    worksheet.addRow(row);
  });

  // Optionally, you can apply styles or adjust column widths
  worksheet.columns.forEach((column: any) => {
    column.width = 20; // Adjust column width to fit content
  });
}

/**
 * Updates existing rows in a worksheet with the given data, starting from row 2.
 * @param worksheet The worksheet to update.
 * @param data Array of data rows to insert.
 */
function updateWorksheetRows(worksheet: any, data: string[][]) {
  data.forEach((rowData, index) => {
    const rowNumber = 2 + index; // Start updating from row 2
    const row = worksheet.getRow(rowNumber);

    // Set values for each column in the row
    rowData.forEach((cellValue, colIndex) => {
      row.getCell(colIndex + 1).value = cellValue; // Column index starts at 1
    });

    row.commit(); // Commit changes to the row
  });
}

const getPlanFacilityMapByFacilityId = (planFacilityArray: any = []) => {
  return planFacilityArray?.reduce((acc: any, curr: any) => {
    acc[curr?.facilityId] = curr;
    return acc;
  }, {});
};
const getRolesAndCount = (resources = [], userRoleMapping: any) => {
  const USER_ROLE_MAP: any = {};

  // Iterate through the userRoleMapping to determine rules for roles
  userRoleMapping?.user.forEach((mapping: any) => {
    const { to, from, filter } = mapping;

    resources?.forEach((resource: any) => {
      // Apply filter logic ensuring all criteria in `from` must match
      const match = from.every((criteria: string) =>
        filter === "includes"
          ? resource?.resourceType?.includes(criteria)
          : resource?.resourceType === criteria
      );

      if (match) {
        // Log the resource information
        logger.info(
          `filtered ${filter.toUpperCase()}: ${resource?.resourceType} :: ${resource?.estimatedNumber}`
        );

        // Map roles based on the "to" field
        USER_ROLE_MAP[to] = resource?.estimatedNumber;
      }
    });
  });

  logger.info("Completed user role & boundary map");
  logger.info(`Map USER_ROLE_MAP ${getFormattedStringForDebug(USER_ROLE_MAP)}`);

  return { USER_ROLE_MAP };
};


const getUserRoleMapWithBoundaryCode = (planFacilityArray: any = [], userRoleMapping: any) => {
  return planFacilityArray?.reduce((acc: any, curr: any) => {
    acc[curr?.locality] = {
      //   ...curr,
      ...getRolesAndCount(
        curr?.resources?.filter(
          (resource: any) => resource?.estimatedNumber > 0
        ),
        userRoleMapping
      ),
    };
    return acc;
  }, {});
};

function consolidateUserRoles(
  userBoundaryMap: any,
  boundaryiwthchildrednMap: any
) {
  const result: any = {};

  // Iterate through all parent boundaries
  for (const parentBoundary in boundaryiwthchildrednMap) {
    const children = boundaryiwthchildrednMap[parentBoundary];
    const consolidatedRoles: any = {};

    // Process each child boundary
    children.forEach((child: any) => {
      const childCode = child.code;
      const userRoles = userBoundaryMap[childCode]?.USER_ROLE_MAP || {};

      // Aggregate roles for the parent boundary
      for (const role in userRoles) {
        if (!consolidatedRoles[role]) {
          consolidatedRoles[role] = 0;
        }
        consolidatedRoles[role] += userRoles[role];
      }
    });

    // Attach consolidated roles to the parent boundary
    result[parentBoundary] = {
      parentBoundary,
      children,
      consolidatedRoles,
    };
  }

  return result;
}

//   // Example Usage
//   const consolidatedData = consolidateUserRoles(userBoundaryMap, boundaryiwthchildrednMap);
//   console.log(JSON.stringify(consolidatedData, null, 2));

const getPlanCensusMapByBoundaryCode = (censusArray: any = []) => {
  return censusArray?.reduce((acc: any, curr: any) => {
    acc[curr?.boundaryCode] = curr;
    return acc;
  }, {});
};

export const fetchFacilityData = async (request: any, localizationMap: any) => {
  const { tenantId, planConfigurationId, campaignId } =
    request.body.MicroplanDetails;

  const facilityAdminSchema = await callMdmsTypeSchema(request, tenantId, true, "facility");
  const localizedHeadersMap = getLocalizedHeadersMapForFacility(facilityAdminSchema.descriptionToFieldMap, localizationMap);
  const planFacilityResponse = await searchPlanFacility(
    planConfigurationId,
    tenantId
  );
  const facilityBoundaryMap =
    getPlanFacilityMapByFacilityId(planFacilityResponse);
  logger.debug(
    `created facilityBoundaryMap :${getFormattedStringForDebug(
      facilityBoundaryMap
    )}`
  );

  const generatedFacilityTemplateFileStoreId = await getTheGeneratedResource(
    campaignId,
    tenantId,
    "facilityWithBoundary",
    request.body.CampaignDetails?.hierarchyType
  );
  logger.debug(
    `downloadresponse fetchFacilityData ${getFormattedStringForDebug(
      generatedFacilityTemplateFileStoreId
    )}`
  );
  const fileUrl = await fetchFileFromFilestore(
    generatedFacilityTemplateFileStoreId,
    tenantId
  );
  logger.debug(
    `downloadresponse fileUrl ${getFormattedStringForDebug(fileUrl)}`
  );
  const workbook = await getExcelWorkbookFromFileURL(
    fileUrl,
    getLocalizedName(config?.facility?.facilityTab, localizationMap)
  );

  const updatedWorksheet = await findAndChangeFacilityData(
    workbook.getWorksheet(
      getLocalizedName(config?.facility?.facilityTab, localizationMap)
    ),
    facilityBoundaryMap,
    localizedHeadersMap
  );
  const responseData =
    updatedWorksheet && (await createAndUploadFile(workbook, request));
  logger.info(
    "facility File updated successfully:" + JSON.stringify(responseData)
  );
  if (responseData?.[0]?.fileStoreId) {
    logger.info(
      "facility File updated successfully:" +
        JSON.stringify(responseData?.[0]?.fileStoreId)
    );
  } else {
    throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
  }

  const polledResponseOfDataCreate = await validateSheet(
    request,
    tenantId,
    "facility",
    responseData?.[0]?.fileStoreId,
    campaignId,
    request.body.CampaignDetails.hierarchyType
  );
  if (
    Array.isArray(polledResponseOfDataCreate) &&
    polledResponseOfDataCreate.length > 0
  ) {
    await updateCampaignDetails(
      request,
      request?.body?.CampaignDetails?.resources,
      responseData?.[0]?.fileStoreId,
      polledResponseOfDataCreate[0]?.id,
      "facility"
    );
    logger.info(
      `updated the resources of facility resource id ${polledResponseOfDataCreate?.[0]?.id}`
    );
  }
  logger.info(`updated the resources of facility`);
};

function getLocalizedHeadersMapForFacility(descriptionToFieldMap: Record<string, string>, localizationMap: any){
  for(const [key,value] of Object.entries(descriptionToFieldMap)){
    descriptionToFieldMap[key] = getLocalizedName(value, localizationMap);
  }
  return descriptionToFieldMap;
}

export const fetchTargetData = async (request: any, localizationMap: any) => {
  const { tenantId, planConfigurationId, campaignId } =
    request.body.MicroplanDetails;
  const planCensusResponse = await searchPlanCensus(
    planConfigurationId,
    tenantId,
    getBoundariesFromCampaign(request.body.CampaignDetails)?.length
  );
  console.log(planCensusResponse, "planCensusResponse");
  const targetBoundaryMap = getPlanCensusMapByBoundaryCode(planCensusResponse);
  logger.debug(
    `created targetBoundaryMap :${getFormattedStringForDebug(
      targetBoundaryMap
    )}`
  );

  const generatedTargetTemplateFileStoreId = await getTheGeneratedResource(
    campaignId,
    tenantId,
    "boundary",
    request.body.CampaignDetails?.hierarchyType
  );
  logger.debug(
    `downloadresponse target ${getFormattedStringForDebug(
      generatedTargetTemplateFileStoreId
    )}`
  );
  const fileUrl = await fetchFileFromFilestore(
    generatedTargetTemplateFileStoreId,
    tenantId
  );
  logger.debug(
    `downloadresponse target ${getFormattedStringForDebug(fileUrl)}`
  );

  const workbook = await getExcelWorkbookFromFileURL(fileUrl);

  await workbook.worksheets.forEach(async (worksheet) => {
    console.log(`Processing worksheet: ${worksheet.name}`);

    if (worksheet.name !== getLocalizedName(config?.boundary?.boundaryTab, localizationMap) && worksheet.name !== getLocalizedName(config?.values?.readMeTab, localizationMap)) {
      // harcoded to be changed
      // Iterate over rows (skip the header row)
      await findAndChangeTargetData(worksheet, targetBoundaryMap);
    }
  });

  const responseData = await createAndUploadFile(workbook, request);

  logger.info(
    "Target File updated successfully:" + JSON.stringify(responseData)
  );
  if (responseData?.[0]?.fileStoreId) {
    logger.info(
      "Target File updated successfully:" +
        JSON.stringify(responseData?.[0]?.fileStoreId)
    );
  } else {
    throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
  }
  const polledResponseOfDataCreate = await validateSheet(
    request,
    tenantId,
    "boundaryWithTarget",
    responseData?.[0]?.fileStoreId,
    campaignId,
    request.body.CampaignDetails.hierarchyType
  );

  if (
    Array.isArray(polledResponseOfDataCreate) &&
    polledResponseOfDataCreate.length > 0
  ) {
    await updateCampaignDetails(
      request,
      request?.body?.CampaignDetails?.resources,
      responseData?.[0]?.fileStoreId,
      polledResponseOfDataCreate[0]?.id,
      "boundaryWithTarget"
    );
    logger.info(
      `updated the resources of facility resource id ${polledResponseOfDataCreate?.[0]?.id}`
    );
  }
  logger.info(`updated the resources of target`);
};

function findAndChangeUserData(worksheet: any, mappingData: any) {
  logger.info(
    `Received for facility mapping, enitity count : ${
      Object.keys(mappingData)?.length
    }`
  );
  console.log(mappingData, "mappingData user");
  // column no is // harcoded to be changed
  const mappedData: any = {};

  const dataRows: any = [];
  Object.keys(mappingData).map((key) => {
    const roles = Object.keys(mappingData[key].consolidatedRoles);
    roles.map((role) => {
      for (let i = 0; i < mappingData[key].consolidatedRoles?.[role]; i++) {
        dataRows.push(["", "", role, "Permanent", key, "Active"]);
      }
    });
  });
  logger.debug(
    `${getFormattedStringForDebug(dataRows)},"dataRows to be pushed`
  );
  updateWorksheetRows(worksheet, dataRows);

  logger.info(
    `Updated the boundary & active/inactive status information in facility received from the microplan`
  );
  logger.info(
    `mapping completed for facility enitity count : ${
      Object.keys(mappedData)?.length
    }`
  );
  logger.info(
    `mapping not found for facility entity count : ${
      Object.keys(mappingData)?.length - Object.keys(mappedData)?.length
    }`
  );
  return worksheet;
}

function findAndChangeFacilityData(
  worksheet: any,
  mappingData: Record<string, {
    additionalDetails: any; serviceBoundaries: string[] 
}>,
headersMap: Record<string, string>
) {
  let facilityCodeIndex: number = 1;
  let boundaryCodeIndex: number = 6;
  let facilityUsageIndex: number = 7;
  let headerValues: any = [];

  const mappedData: Record<string, boolean> = {};
  const missingFacilities: string[] = [];

  // Iterate through each row in the worksheet
  worksheet.eachRow((row: any, rowIndex: number) => {
    if (rowIndex === 1) {
      headerValues = row.values;
      facilityCodeIndex = headerValues.indexOf("Facility Code");
      boundaryCodeIndex = headerValues.indexOf(headersMap["Boundary Code"]);
      facilityUsageIndex = headerValues.indexOf(headersMap["Facility usage"]);
      return;
    }

    const facilityCode = row.getCell(facilityCodeIndex).value;
    if (facilityCode && mappingData[facilityCode]) {
      const facilityDetails = mappingData[facilityCode];
      row.getCell(boundaryCodeIndex).value = facilityDetails.serviceBoundaries.join(",") || "";
      row.getCell(facilityUsageIndex).value = "Active";
      mappedData[facilityCode] = true;
    } else {
      row.getCell(boundaryCodeIndex).value = "";
      row.getCell(facilityUsageIndex).value = "Inactive";
    }
  });

  // Handle missing facilities
  for (const [facilityCode, facilityDetails] of Object.entries(mappingData)) {
    if (!mappedData[facilityCode]) {
      missingFacilities.push(facilityCode);
      const newRow = worksheet.addRow([]);
      newRow.getCell(facilityCodeIndex).value = facilityCode;
      newRow.getCell(headerValues.indexOf(headersMap["Facility Name"])).value = facilityDetails?.additionalDetails?.facilityName;
      newRow.getCell(headerValues.indexOf(headersMap["Facility type"])).value = facilityDetails?.additionalDetails?.facilityType;
      newRow.getCell(headerValues.indexOf(headersMap["Facility status"])).value = facilityDetails?.additionalDetails?.facilityStatus;
      newRow.getCell(headerValues.indexOf(headersMap["Capacity"])).value = facilityDetails?.additionalDetails?.capacity;
      newRow.getCell(boundaryCodeIndex).value = facilityDetails.serviceBoundaries.join(",") || "";
      newRow.getCell(facilityUsageIndex).value = "Active";
    }
  }
  logger.info(
    `Updated the boundary & active/inactive status information in facility received from the microplan`);
  logger.info(
      `mapping completed for facility enitity count : ${
        Object.keys(mappedData)?.length
      }`
    );

  return worksheet;
}


function findAndChangeTargetData(worksheet: any, mappingData: any) {
  logger.info(
    `Received for Target mapping, enitity count : ${
      Object.keys(mappingData)?.length
    }`
  );
  const mappedData: any = {};
  // Iterate through rows in Sheet1 (starting from row 2 to skip the header)
  worksheet.eachRow((row: any, rowIndex: number) => {
    if (rowIndex === 1) return; // Skip the header row
    const column1Value = row.getCell(5).value; // Get the value from column 1
    if (mappingData?.[column1Value]) {
      // Update columns 5 and 6 if column 1 value matches
      row.getCell(6).value =
        mappingData?.[column1Value]?.["additionalDetails"]?.[
          "CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION"
        ]; // Set "BoundaryCode" in column 5
      // row.getCell(7).value = "Active"; // Set "Status" in column 6
      mappedData[column1Value] = rowIndex;
    } else {
      logger.info(`not doing anything if taregt cel not found`);
      // Default values for other rows
      // row.getCell(6).value = "";
      // row.getCell(7).value = "Inactive";
    }
  });
  logger.info(
    `Updated the boundary & active/inactive status information in Target received from the microplan`
  );
  logger.info(
    `mapping completed for Target enitity count : ${
      Object.keys(mappedData)?.length
    }`
  );
  logger.info(
    `mapping not found for Target entity count : ${
      Object.keys(mappingData)?.length - Object.keys(mappedData)?.length
    }`
  );
  return worksheet;
}

const getBoundariesFromCampaign = (CampaignDetails: any = {}) => {
  logger.info("fetching all boundaries in that CampaignDetails");
  const boundaries = CampaignDetails?.boundaries?.map((obj: any) => obj?.code);
  logger.debug(
    `boundaries in that CampaignDetails are :${getFormattedStringForDebug(
      boundaries
    )}`
  );
  return boundaries;
};

export const fetchUserData = async (request: any, localizationMap: any) => {
  const { tenantId, planConfigurationId, campaignId } =
    request.body.MicroplanDetails;

  const userRoleMapping = await fetchUserRoleMappingFromMDMS(request, tenantId);
  const planResponse = await searchPlan(
    planConfigurationId,
    tenantId,
    getBoundariesFromCampaign(request.body.CampaignDetails)?.length
  );
  const boundariesOfCampaign = await getBoundaryInformation(
    request.body.CampaignDetails,
    request.body.CampaignDetails?.hierarchyType,
    tenantId
  );
  const filteredBoundariesAtWhichUserGetsCreated =
    getFilteredBoundariesAtWhichUserGetsCreated(boundariesOfCampaign);
  logger.debug(
    `boundariesOfCampaign : ${getFormattedStringForDebug(boundariesOfCampaign)}`
  );

  const filteredBoundaryCodeMapWithChildrens =
    enrichBoundariesWithTheSelectedChildrens(
      boundariesOfCampaign,
      filteredBoundariesAtWhichUserGetsCreated
    );
  logger.debug(
    `filteredBoundaryCodeMapWithChildrens : ${getFormattedStringForDebug(
      filteredBoundaryCodeMapWithChildrens
    )}`
  );

  const boundaryWithRoleMap = getUserRoleMapWithBoundaryCode(planResponse, userRoleMapping);
  logger.debug(
    `created userBoundaryMap :${getFormattedStringForDebug(
      boundaryWithRoleMap
    )}`
  );

  const consolidatedUserRolesPerBoundary = consolidateUserRoles(
    boundaryWithRoleMap,
    filteredBoundaryCodeMapWithChildrens
  );

  logger.debug(
    `created final consolidatedUserRolesPerBoundary :${getFormattedStringForDebug(
      consolidatedUserRolesPerBoundary
    )}`
  );
  const generatedUserTemplateFileStoreId = await getTheGeneratedResource(
    campaignId,
    tenantId,
    "userWithBoundary",
    request.body.CampaignDetails?.hierarchyType
  );
  logger.debug(
    `downloadresponse userWithBoundary ${getFormattedStringForDebug(
      generatedUserTemplateFileStoreId
    )}`
  );
  const fileUrl = await fetchFileFromFilestore(
    generatedUserTemplateFileStoreId,
    tenantId
  );
  logger.debug(
    `downloadresponse userWithBoundary ${getFormattedStringForDebug(fileUrl)}`
  );

  const workbook = await getExcelWorkbookFromFileURL(
    fileUrl,
    getLocalizedName(config?.user.userTab, localizationMap)
  );

  const updatedWorksheet = await findAndChangeUserData(
    workbook.getWorksheet(
      getLocalizedName(config?.user.userTab, localizationMap)
    ),
    consolidatedUserRolesPerBoundary
  );
  const responseData =
    updatedWorksheet && (await createAndUploadFile(workbook, request));
  logger.info("user File updated successfully:" + JSON.stringify(responseData));
  if (responseData?.[0]?.fileStoreId) {
    logger.info(
      "user File updated successfully:" +
        JSON.stringify(responseData?.[0]?.fileStoreId)
    );
  } else {
    throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
  }

  await updateCampaignDetails(
    request,
    request?.body?.CampaignDetails?.resources,
    responseData?.[0]?.fileStoreId,
    "c8f3f544-df19-433c-af73-fe3269a170cd",
    "user"
  );

  logger.info(`updated the resources of user`);
};

export async function updateCampaignDetails(
  request: any,
  resources: any,
  fileStoreId: any,
  resourceDetailsId: any,
  type: string
) {
  // Validate input structure
  if (Array.isArray(resources) && fileStoreId) {
    let resourceFound = false; // Flag to track if resource is updated

    // Loop through resources to update or append as needed
    resources.forEach((resource: any) => {
      if (resource.type === type) {
        resource.filestoreId = fileStoreId;
        resource.resourceId = resourceDetailsId;
        console.log(
          `Updated resource of type ${type} with filestoreId: ${resource.filestoreId}`
        );
        resourceFound = true;
      }
    });

    // If no resource of the given type was found, append a new one
    if (!resourceFound) {
      resources.push({
        type: type,
        filename: `filled-${type}-data-from-microplan.xlsx`, // Dynamically naming based on type
        filestoreId: fileStoreId,
        resourceId: resourceDetailsId,
      });
      console.log(`Appended new resource of type ${type}`);
    }
  } else {
    console.error(
      "Invalid structure in CampaignDetails or fileDetails. Ensure both are non-empty arrays."
    );
  }

  // Call external service after updating the campaign details
  await updateProjectTypeCampaignService(request);
}

export async function validateSheet(
  request: any,
  tenantId: any,
  type: any,
  fileStoreId: any,
  campaignId: any,
  hierarchyType: any
) {
  let dataCreateBody = {
    ResourceDetails: {
      tenantId: tenantId,
      type: type,
      fileStoreId: fileStoreId,
      action: "validate",
      campaignId: campaignId,
      hierarchyType: hierarchyType,
      additionalDetails: {},
    },
  };

  // Now merging defaultRequestInfo *with* dataCreateBody, so both are preserved
  const newRequest: any = {
    body: { ...request.body, ...dataCreateBody }, // Spread both objects to keep both their properties
  };

  try {
    const resourceDetails = await createAndPollForCompletion(newRequest);
    logger.info("Final result:", resourceDetails);
    return resourceDetails || null;
  } catch (error) {
    logger.error(
      `Error during resource creation and polling:for type ${type}`,
      error
    );
    return null;
  }
}
// sample oundary
//{code: "MICROPLAN_MO", name: "MICROPLAN_MO", parent:"", type: "COUNTRY", isRoot: true, includeAllChildren: false}

const getFilteredBoundariesAtWhichUserGetsCreated = (boundaries = []) => {
  //add config at which level grouping will happen. hardcoded to loclaity
  const filteredBoundariesAtWhichUserGetsCreated = boundaries?.filter(
    (boundary: any) => boundary?.type == "LOCALITY"
  );
  logger.info(
    `filteredBoundariesAtWhichUserGetsCreated count is ${filteredBoundariesAtWhichUserGetsCreated?.length}`
  );
  logger.debug(
    `filteredBoundariesAtWhichUserGetsCreated are ${getFormattedStringForDebug(
      filteredBoundariesAtWhichUserGetsCreated
    )}`
  );
  return filteredBoundariesAtWhichUserGetsCreated;
};

const getBoundaryInformation = async (
  CampaignDetails: any,
  boundaryHierarchy: string,
  tenantId: string
) => {
  const boundaries = CampaignDetails?.boundaries;
  if (boundaries?.some((boundary: any) => boundary?.includeAllChildren)) {
    const boundaryResponse = await searchBoundaryRelationshipData(
      tenantId,
      boundaryHierarchy,
      true
    );
    logger.info("got the boundary hierarchy response");
    if (boundaryResponse?.TenantBoundary?.[0]?.boundary?.[0]) {
      logger.info("got the boundary hierarchy response");
    }

    return boundaries;
  }
};

const enrichBoundariesWithTheSelectedChildrens = (
  allSelectedBoundaries = [],
  filteredBoundaries = []
) => {
  const enrichedMap: any = {};
  filteredBoundaries?.map((boundary: any) => {
    enrichedMap[boundary?.code] = allSelectedBoundaries?.filter(
      (bound: any) => bound.parent == boundary?.code
    );
  });
  return enrichedMap;
};
async function fetchUserRoleMappingFromMDMS(request: any, tenantId: any) {
  const MdmsCriteria: MDMSModels.MDMSv1RequestCriteria = {
    MdmsCriteria: {
    tenantId: tenantId,
    moduleDetails: [
      {
        moduleName: "HCM-ADMIN-CONSOLE",
        masterDetails: [
          {
            name: "microplanIntegration"
          },
        ],
      },
    ],
  }
  };
  const data = await searchMDMSDataViaV1Api(MdmsCriteria);
  const result: Record<string, any[]> = {};
  if (
    data?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.microplanIntegration &&
    Array.isArray(data.MdmsRes["HCM-ADMIN-CONSOLE"].microplanIntegration)
  ) {
    const integrations = data.MdmsRes["HCM-ADMIN-CONSOLE"].microplanIntegration;

    integrations.forEach((integration: any) => {
      const type = integration.type;

      if (!result[type]) {
        result[type] = [];
      }

      result[type].push({
        to: integration.to,
        from: integration.from,
        type: integration.type,
        filter: integration.filter,
      });
    });
  }

  return result;
}
