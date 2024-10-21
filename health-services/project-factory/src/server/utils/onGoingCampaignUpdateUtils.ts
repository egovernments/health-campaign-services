import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getLocalizedHeaders, replicateRequest, throwError } from "./genericUtils";
import { httpRequest } from "./request";
import config from "../config/index";
import { getLocalizedName } from "./campaignUtils";
import { logger } from "./logger";
import { callGenerate } from "./generateUtils";

async function getParentCampaignObject(request: any, parentId: any) {
  try {
    const searchBodyForParent = {
      RequestInfo: request.body.RequestInfo,
      CampaignDetails: {
        tenantId: request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId || request?.body?.CampaignDetails?.tenantId,
        ids: [parentId]
      }
    };
    const req: any = replicateRequest(request, searchBodyForParent);
    const parentSearchResponse = await searchProjectTypeCampaignService(req);
    return parentSearchResponse?.CampaignDetails?.[0];
  } catch (error) {
    console.error("Error fetching parent campaign object:", error);
    throwError("CAMPAIGN", 400, "PARENT_CAMPAIGN_ERROR", "Parent Campaign fetching error ");
  }
}

function getCreatedResourceIds(resources: any, type: any) {
  const processedType = type === 'boundary'
    ? 'boundaryWithTarget'
    : (type.includes('With') ? type.split('With')[0] : type); return resources
      .filter((item: any) => item.type === processedType)
      .map((item: any) => item.createResourceId);
}

function buildSearchCriteria(request: any, createdResourceId: any, type: any) {
  let processedType = type === 'boundary'
    ? 'boundaryWithTarget'
    : (type.includes('With') ? type.split('With')[0] : type);
  return {
    RequestInfo: request.body.RequestInfo,
    SearchCriteria: {
      id: createdResourceId,
      tenantId: request?.query?.tenantId,
      type: processedType
    }
  };
}

async function fetchFileUrls(request: any, processedFileStoreIdForUSerOrFacility: any) {
  try {
    const reqParamsForFetchingFile = {
      tenantId: request?.query?.tenantId,
      fileStoreIds: processedFileStoreIdForUSerOrFacility
    };
    return await httpRequest(
      `${config?.host?.filestore}${config?.paths?.filestorefetch}`,
      request?.body,
      reqParamsForFetchingFile,
      "get"
    );
  } catch (error) {
    console.error("Error fetching file URLs:", error);
    throw error;
  }
}



function modifyProcessedSheetData(request: any, sheetData: any, localizationMap?: any) {
  const type = request?.query?.type || request?.body?.ResourceDetails?.type;
  const typeWithoutWith = type.includes('With') ? type.split('With')[0] : type;
  if (!sheetData || sheetData.length === 0) return [];

  // Find the row with the maximum number of keys
  const maxLengthRow = sheetData.reduce((maxRow: any, row: any) => {
    return Object.keys(row).length > Object.keys(maxRow).length ? row : maxRow;
  }, {});

  // Extract headers from the keys of the row with the maximum number of keys
  const originalHeaders = Object.keys(maxLengthRow);
  if (typeWithoutWith == 'user') {
    const statusIndex = originalHeaders.indexOf('#status#');
    // Insert 'errordetails' after '#status#' if found
    if (statusIndex !== -1) {
      originalHeaders.splice(statusIndex + 1, 0, '#errorDetails#');
    }
  }

  let localizedHeaders = getLocalizedHeaders(originalHeaders, localizationMap);

  // Map each object in sheetData to an array of values corresponding to the header order
  let dataRows = sheetData.map((row: any) => {
    return localizedHeaders.map((header: any) => row[header] || '');
  });

  const updatedHeaders = localizedHeaders.map((header: any) => header === getLocalizedName(config?.boundary?.boundaryCodeMandatory, localizationMap) ?
    getLocalizedName(config?.boundary?.boundaryCodeOld, localizationMap) : header)

  const updatedWithAdditionalHeaders = [...updatedHeaders, config?.boundary?.boundaryCodeMandatory]
  localizedHeaders = getLocalizedHeaders(updatedWithAdditionalHeaders, localizationMap);

  dataRows = dataRows.map((row: any, index: number) => {
    const boundaryCodeValue = sheetData[index][getLocalizedName(config?.boundary?.boundaryCodeMandatory, localizationMap)] || '';
    return [...row, boundaryCodeValue];
  });

  // Combine headers and dataRows
  const modifiedData = [localizedHeaders, ...dataRows];

  return modifiedData;
}

function freezeUnfreezeColumnsForProcessedFile(sheet: any, columnsToFreeze: number[], columnsToUnfreeze: number[]) {
  // First, unfreeze specified columns
  columnsToUnfreeze.forEach(colNumber => {
    for (let row = 1; row <= sheet.rowCount; row++) {
      const cell = sheet.getCell(row, colNumber);
      cell.protection = { locked: false }; // Unfreeze the cell
    }
  });

  // Then, freeze specified columns
  columnsToFreeze.forEach(colNumber => {
    for (let row = 1; row <= sheet.rowCount; row++) {
      const cell = sheet.getCell(row, colNumber);
      cell.protection = { locked: true }; // Freeze the cell
    }
  });
}


function getColumnIndexByHeader(sheet: any, headerName: string): number {
  // Get the first row (assumed to be the header row)
  const firstRow = sheet.getRow(1);

  // Find the column index where the header matches the provided name
  for (let col = 1; col <= firstRow.cellCount; col++) {
    const cell = firstRow.getCell(col);
    if (cell.value === headerName) {
      return col; // Return the column index (1-based)
    }
  }
  return 1;
}

// function validateBoundaryCodes(activeRows: any, localizationMap?: any) {
//   const updatedBoundaryCodeKey = getLocalizedName('HCM_ADMIN_CONSOLE_UPDATED_BOUNDARY_CODE', localizationMap);
//   const updatedBoundaryCodeValue = activeRows[updatedBoundaryCodeKey];
//   const boundaryCodeMandatoryKey = getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY", localizationMap);
//   const boundaryCodeMandatoryValue = activeRows[boundaryCodeMandatoryKey];
//   if (!updatedBoundaryCodeValue && !boundaryCodeMandatoryValue) {
//     const errorDetails = {
//       errors: [
//         {
//           instancePath: '',
//           schemaPath: '#/required',
//           keyword: 'required',
//           params: {
//             missingProperty: `${updatedBoundaryCodeKey} and ${boundaryCodeMandatoryKey} both`
//           },
//           message: `must have required properties ${`${updatedBoundaryCodeKey}, ${boundaryCodeMandatoryKey}`}`
//         }
//       ]
//     };

//     throw new Error(JSON.stringify(errorDetails));
//   }
// }

async function checkAndGiveIfParentCampaignAvailable(request: any, campaignObject: any) {
  if (campaignObject?.parentId) {
    const parentCampaignObject = await getParentCampaignObject(request, campaignObject.parentId);

    if (parentCampaignObject?.status === "created" && !parentCampaignObject.isActive) {
      request.body.parentCampaignObject = parentCampaignObject;
    }
  }
}

function hideColumnsOfProcessedFile(sheet: any, columnsToHide: any[]) {
  columnsToHide.forEach((column) => {
    if (column) {
      sheet.getColumn(column).hidden = true;
    }
  });
}

function unhideColumnsOfProcessedFile(sheet: any, columnsToUnide: any) {
  columnsToUnide.forEach((column: any) => {
    if (column) {
      sheet.getColumn(column).hidden = false;
    }
  });
}

function modifyNewSheetData(processedDistrictSheetData: any, newSheetData: any, headers: any, localizationMap?: any) {
  let modifiedData = [];
  let localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  if (processedDistrictSheetData && processedDistrictSheetData.length > 0) {
    const dataRows = processedDistrictSheetData.map((row: any) => {
      return localizedHeaders.map((header: any) => row[header] || '');
    });
    modifiedData = [localizedHeaders, ...dataRows];
  } else {
    // If processedDistrictSheetData is not present, work only with newSheetData
    modifiedData = [newSheetData];
  }

  const newData = updateTargetValues(modifiedData, newSheetData, localizedHeaders, localizationMap);
  return newData;
}


function updateTargetValues(originalData: any, newData: any, localizedHeaders: any, localizationMap?: any) {

  const boundaryCodeIndex = localizedHeaders.indexOf(getLocalizedName(config?.boundary?.boundaryCode, localizationMap));

  // Update newData with matching values from originalData
  newData = newData.map((newRow: any) => {
    const matchingRow = originalData.find((originalRow: any) =>
      originalRow.slice(0, boundaryCodeIndex + 1).every((val: any, index: any) => val === newRow[index])
    );

    return newRow.map((value: any, index: any) =>
      index > boundaryCodeIndex && matchingRow && value === ''
        ? matchingRow[index]
        : value
    );
  });
  newData = newData.map((newRow: any, rowIndex: number) => {
    const updatedValues: any[] = [];
    for (let i = boundaryCodeIndex + 1; i < localizedHeaders.length; i++) {
      updatedValues.push(newRow[i]);  // Store original value
      if (rowIndex === 0) {  // Only modify the first row
        newRow[i] = newRow[i] + "(OLD)"; // Modify value with (OLD) for the first row
      }
    }
    // Concatenate original values at the end of every row
    return [...newRow, ...updatedValues];
  });
  return newData;
}

function validateBoundariesIfParentPresent(request: any) {
  const { parentCampaign, CampaignDetails } = request?.body || {};

  if (parentCampaign) {
    const errors: string[] = [];
    const newBoundaries: any[] = [];
    const parentCampaignBoundaryCodes = parentCampaign.boundaries.map((boundary: any) => boundary.code);

    CampaignDetails?.boundaries?.forEach((boundary: any) => {
      if (parentCampaignBoundaryCodes.includes(boundary.code)) {
        errors.push(boundary.code);
      } else {
        if (!boundary?.isRoot) {
          newBoundaries.push(boundary);
        } else {
          throwError(
            "COMMON",
            400,
            "VALIDATION_ERROR",
            `Boundary with code ${boundary.code} cannot be added as it is marked as root. Root boundary should come from the parent campaign.`
          );
        }
      }
    });

    if (errors.length > 0) {
      throwError("COMMON", 400, "VALIDATION_ERROR", `Boundary Codes found already in Parent Campaign: ${errors.join(', ')}`);
    }
    request.body.boundariesCombined = [...parentCampaign.boundaries, ...newBoundaries];
  }
  else {
    request.body.boundariesCombined = request?.body?.CampaignDetails?.boundaries
  }
}


async function callGenerateWhenChildCampaigngetsCreated(request: any) {
  try {
    const newRequestBody = {
      RequestInfo: request?.body?.RequestInfo,
      Filters: {
        boundaries: request?.body?.boundariesCombined
      }
    };

    const { query } = request;
    const params = {
      tenantId: request?.body?.CampaignDetails?.tenantId,
      forceUpdate: 'true',
      hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
      campaignId: request?.body?.CampaignDetails?.id
    };

    const newParamsBoundary = { ...query, ...params, type: "boundary" };
    const newRequestBoundary = replicateRequest(request, newRequestBody, newParamsBoundary);
    await callGenerate(newRequestBoundary, "boundary");

    const newParamsFacilityWithBoundary = { ...query, ...params, type: "facilityWithBoundary" };
    const newRequestFacilityWithBoundary = replicateRequest(request, newRequestBody, newParamsFacilityWithBoundary);
    await callGenerate(newRequestFacilityWithBoundary, "facilityWithBoundary");

    const newParamsUserWithBoundary = { ...query, ...params, type: "userWithBoundary" };
    const newRequestUserWithBoundary = replicateRequest(request, newRequestBody, newParamsUserWithBoundary);
    await callGenerate(newRequestUserWithBoundary, "userWithBoundary");
  }
  catch (error: any) {
    logger.error(error);
    throwError("COMMON", 400, "GENERATE_ERROR", `Error while generating user/facility/boundary: ${error.message}`);
  }
}


function getBoundariesArray(parentCampaignBoundaries: any, campaignBoundaries: any) {
  // Ensure both inputs are arrays or default to empty arrays
  const validParentBoundaries = Array.isArray(parentCampaignBoundaries) ? parentCampaignBoundaries : [];
  const validCampaignBoundaries = Array.isArray(campaignBoundaries) ? campaignBoundaries : [];

  return [...validParentBoundaries, ...validCampaignBoundaries];
}

async function getBoundariesFromCampaignSearchResponse(request: any, campaignDetails: any) {
  let parentCampaignBoundaries: any[] = [];
  if (campaignDetails?.parentId) {
    const parentCampaignObject = await getParentCampaignObject(request, campaignDetails?.parentId);
    parentCampaignBoundaries = parentCampaignObject?.boundaries;
  }
  return getBoundariesArray(parentCampaignBoundaries, campaignDetails?.boundaries)
}

async function fetchProjectsWithParentRootProjectId(request: any) {
  const { projectId, tenantId } = request?.body?.parentCampaign;
  const projectSearchBody = {
    RequestInfo: request?.body?.RequestInfo,
    Projects: [
      {
        id: projectId,
        tenantId: tenantId
      }
    ]
  }
  const projectSearchParams = {
    tenantId: tenantId,
    offset: 0,
    limit: 1,
    includeDescendants: true
  }
  logger.info("Project search params " + JSON.stringify(projectSearchParams))
  const projectSearchResponse = await httpRequest(config?.host?.projectHost + config?.paths?.projectSearch, projectSearchBody, projectSearchParams);
  if (projectSearchResponse?.Project && Array.isArray(projectSearchResponse?.Project) && projectSearchResponse?.Project?.length > 0) {
    return projectSearchResponse;
  }
  else {
    throwError("PROJECT", 500, "PROJECT_SEARCH_ERROR")
    return []
  }
}

async function fetchProjectsWithBoundaryCodeAndName(boundaryCode: any, tenantId: any, projectName: any, RequestInfo?: any) {
  try {
    const projectSearchBody = {
      RequestInfo: RequestInfo,
      Projects: [
        {
          address: {
            boundary: boundaryCode,
          },
          name: projectName,
          tenantId: tenantId
        }
      ]
    }
    const projectSearchParams = {
      tenantId: tenantId,
      offset: 0,
      limit: 1
    }
    logger.info("Project search params " + JSON.stringify(projectSearchParams))
    const projectSearchResponse = await httpRequest(config?.host?.projectHost + config?.paths?.projectSearch, projectSearchBody, projectSearchParams);
    if (projectSearchResponse?.Project && Array.isArray(projectSearchResponse?.Project) && projectSearchResponse?.Project?.length > 0) {
      return projectSearchResponse;
    }
    else {
      return null;
    }
  } catch (error: any) {
    throwError("PROJECT", 500, "PROJECT_SEARCH_ERROR")
  }
}

function getBoundaryProjectMappingFromParentCampaign(request: any, project: any) {

  const boundarySet = new Set<string>();

  // Initialize result array with the project itself (id and boundary)
  const result = [
    {
      id: project.id,
      boundary: project.address.boundary
    },
    ...project.descendants.map((descendant: any) => ({
      id: descendant.id,
      boundary: descendant.address.boundary
    }))
  ];

  // Iterate over the result array to find matching boundaries and populate projectId
  result.forEach((entry: any) => {
    const boundary = entry.boundary;
    boundarySet.add(boundary);
    // Initialize the boundaryProjectMapping for this boundary if not present
    if (!request?.body?.boundaryProjectMapping?.[boundary]) {
      request.body.boundaryProjectMapping[boundary] = { parent: null, projectId: null };
    }
    // Update the projectId in the request's boundaryProjectMapping
    request.body.boundaryProjectMapping[boundary].projectId = entry.id;
  });

  return boundarySet;
}


async function fetchProjectFacilityWithProjectId(request: any, projectId: any, facilityId: any) {
  try {
    const { tenantId } = request?.body?.parentCampaign || request?.parentCampaign;
    const projectSearchBody = {
      RequestInfo: request?.body?.RequestInfo || request?.RequestInfo,
      ProjectFacility: {
        projectId: [
          projectId
        ],
        facilityId: [
          facilityId
        ]
      }
    }
    const projectSearchParams = {
      tenantId: tenantId,
      offset: 0,
      limit: 1
    }
    logger.info("Project search params " + JSON.stringify(projectSearchParams))
    const projectFacilitySearchResponse = await httpRequest(config?.host?.projectHost + config?.paths?.projectFacilitySearch, projectSearchBody, projectSearchParams);

    if (projectFacilitySearchResponse?.ProjectFacilities && Array.isArray(projectFacilitySearchResponse?.ProjectFacilities) && projectFacilitySearchResponse?.ProjectFacilities?.length > 0) {
      return projectFacilitySearchResponse;
    }
    else {
      return null
    }
  } catch (error: any) {
    throwError("PROJECT", 500, "PROJECT_FACILTY_SEARCH_ERROR")
  }
}


async function fetchProjectStaffWithProjectId(request: any, projectId: any, staffId: any) {
  try {
    const { tenantId } = request?.body?.parentCampaign || request?.parentCampaign;
    const projectSearchBody = {
      RequestInfo: request?.body?.RequestInfo || request?.RequestInfo,
      ProjectStaff: {
        projectId: [
          projectId
        ],
        staffId: [
          staffId
        ]
      }
    }

    const projectSearchParams = {
      tenantId: tenantId,
      offset: 0,
      limit: 1
    }
    logger.info("Project search params " + JSON.stringify(projectSearchParams))
    const projectStaffSearchResponse = await httpRequest(config?.host?.projectHost + config?.paths?.projectStaffSearch, projectSearchBody, projectSearchParams);
    if (projectStaffSearchResponse?.ProjectStaff && Array.isArray(projectStaffSearchResponse?.ProjectStaff) && projectStaffSearchResponse?.ProjectStaff?.length > 0) {
      return projectStaffSearchResponse;
    }
    else {
      return null
    }
  } catch (error: any) {
    throwError("PROJECT", 500, "PROJECT_STAFF_SEARCH_ERROR")
  }

}

async function delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary(resource: any, messageObject: any, boundaryCode: any, uniqueIdentifier: any, isDelink: boolean) {
  const projectResponse = await fetchProjectsWithBoundaryCodeAndName(boundaryCode, messageObject?.parentCampaign?.tenantId, messageObject?.CampaignDetails?.campaignName, messageObject?.RequestInfo);
  let matchingProjectObject: any;
  if (projectResponse) {
    matchingProjectObject = projectResponse?.Project[0];
  }
  const matchingProjectId = matchingProjectObject?.id;
  if (!matchingProjectId) {
    return false;  // No matching project found
  }


  if (resource?.type === "facility") {
    const projectFacilityResponse = await fetchProjectFacilityWithProjectId(messageObject, matchingProjectId, uniqueIdentifier);
    if (projectFacilityResponse) {
      if (isDelink) {
        await deleteProjectFacilityMapping(messageObject, projectFacilityResponse)
      }
      return true;
    } else {
      return false;
    }
  }
  if (resource?.type === 'user') {
    const projectStaffResponse = await fetchProjectStaffWithProjectId(messageObject, matchingProjectId, uniqueIdentifier);
    if (projectStaffResponse) {
      if (isDelink) {
        await deleteProjectStaffMapping(messageObject, projectStaffResponse)
      }
      return true;
    } else {
      return false;
    }
  }
  else return false;
}


async function deleteProjectFacilityMapping(messageObject: any, projectFacilityResponse: any) {
  const projectFacilityDeleteBody = {
    RequestInfo: messageObject?.RequestInfo,
    ProjectFacilities: [
      projectFacilityResponse?.ProjectFacilities[0]
    ]
  }
  try {
    await httpRequest(config?.host?.projectHost + config?.paths?.projectFacilityDelete, projectFacilityDeleteBody);
  }
  catch (error: any) {
    throwError("PROJECT", 500, "PROJECT_FACILITY_DELETE_ERROR")
  }
}

async function deleteProjectStaffMapping(messageObject: any, projectStaffResponse: any) {
  const projectStaffDeleteBody = {
    RequestInfo: messageObject?.RequestInfo,
    ProjectStaff: [
      projectStaffResponse?.ProjectStaff[0]
    ]
  }
  try {
    await httpRequest(config?.host?.projectHost + config?.paths?.projectStaffDelete, projectStaffDeleteBody);
  }
  catch (error: any) {
    throwError("PROJECT", 500, "PROJECT_STAFF_DELETE_ERROR")
  }
}



export {
  getParentCampaignObject,
  getCreatedResourceIds,
  buildSearchCriteria,
  fetchFileUrls,
  modifyProcessedSheetData,
  freezeUnfreezeColumnsForProcessedFile,
  getColumnIndexByHeader,
  checkAndGiveIfParentCampaignAvailable,
  hideColumnsOfProcessedFile,
  unhideColumnsOfProcessedFile,
  modifyNewSheetData,
  validateBoundariesIfParentPresent,
  callGenerateWhenChildCampaigngetsCreated,
  getBoundariesFromCampaignSearchResponse,
  fetchProjectsWithParentRootProjectId,
  getBoundaryProjectMappingFromParentCampaign,
  fetchProjectFacilityWithProjectId,
  fetchProjectsWithBoundaryCodeAndName,
  delinkAndLinkResourcesWithProjectCorrespondingToGivenBoundary
}