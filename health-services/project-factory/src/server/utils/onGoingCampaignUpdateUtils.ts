import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getLocalizedHeaders, throwError } from "./genericUtils";
import { httpRequest } from "./request";
import config from "../config/index";
import { getLocalizedName, populateBoundariesRecursively } from "./campaignUtils";
import { logger } from "./logger";
import { searchBoundaryRelationshipData } from "../api/coreApis";
import { cloneDeep } from "lodash";
import { CampaignResource } from "../config/models/resourceTypes";

async function getParentCampaignObject(request: any, parentId: any) {
  try {
      const CampaignDetails = {
        tenantId: request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId || request?.body?.CampaignDetails?.tenantId,
        ids: [parentId]
      }
    const parentSearchResponse = await searchProjectTypeCampaignService(CampaignDetails);
    return parentSearchResponse?.CampaignDetails?.[0];
  } catch (error) {
    logger.error("Error fetching parent campaign object:", error);
    throwError("CAMPAIGN", 400, "PARENT_CAMPAIGN_ERROR", "Parent Campaign fetching error ");
  }
}

function getCreatedResourceIds(resources: any, type: any) {
  const processedType = type === 'boundary'
    ? 'boundaryWithTarget'
    : (type.includes('With') ? type.split('With')[0] : type);
  return resources
    .filter((item: any) => item.type === processedType && item.createResourceId)
    .map((item: any) => item.createResourceId);
}

function buildSearchCriteria(request: any, createdResourceId: any, type: any) {
  const processedType = type === 'boundary'
    ? 'boundaryWithTarget'
    : (type === 'boundaryWithTarget' ? type : (type.includes('With') ? type.split('With')[0] : type));

  const requestInfo = request?.body
    ? request.body.RequestInfo
    : { RequestInfo: request?.RequestInfo };
  return {
    RequestInfo: requestInfo,
    SearchCriteria: {
      id: createdResourceId,
      tenantId: request?.query?.tenantId || request?.CampaignDetails?.tenantId,
      type: processedType
    }
  };
}



function modifyProcessedSheetData(type: any, sheetData: any, schema: any, localizationMap?: any) {
  if (!sheetData || sheetData.length === 0) return [];

  const originalHeaders = type === config?.facility?.facilityType ? [config?.facility?.facilityCodeColumn, ...schema?.columns] : schema?.columns;

  let localizedHeaders = getLocalizedHeaders(originalHeaders, localizationMap);

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

  const modifiedData = [localizedHeaders, ...dataRows];

  return modifiedData;
}

function freezeUnfreezeColumnsForProcessedFile(sheet: any, columnsToFreeze: number[], columnsToUnfreeze: number[]) {
  columnsToUnfreeze.forEach(colNumber => {
    for (let row = 1; row <= sheet.rowCount; row++) {
      const cell = sheet.getCell(row, colNumber);
      cell.protection = { locked: false };
    }
  });

  columnsToFreeze.forEach(colNumber => {
    for (let row = 1; row <= sheet.rowCount; row++) {
      const cell = sheet.getCell(row, colNumber);
      cell.protection = { locked: true };
    }
  });
}


// Returns the 1-based column index whose header-row cell matches headerName, defaulting to 1 when absent.
function getColumnIndexByHeader(sheet: any, headerName: string): number {
  const firstRow = sheet.getRow(1);

  for (let col = 1; col <= firstRow.cellCount; col++) {
    const cell = firstRow.getCell(col);
    if (cell.value === headerName) {
      return col;
    }
  }
  return 1;
}

async function checkAndGiveIfParentCampaignAvailable(request: any, campaignObject: any) {
  if (campaignObject?.parentId) {
    logger.info("enriching the parent campaign details for update flow");
    const parentCampaignObject = await getParentCampaignObject(request, campaignObject.parentId);

    if (parentCampaignObject?.status === "created" && !parentCampaignObject.isActive) {
      request.body.parentCampaignObject = parentCampaignObject;
    }
  }
}

function hideColumnsOfProcessedFile(sheet: any, columnsToHide: any[]) {
  columnsToHide.forEach((column) => {
    if (column > 0) {
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

function modifyNewSheetData(processedDistrictSheetData: any, newSheetData: any, headers: any, oldTargetColumnsToHide: any[], localizationMap?: any) {
  let modifiedData = [];
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  if (processedDistrictSheetData && processedDistrictSheetData.length > 0) {
    const dataRows = processedDistrictSheetData.map((row: any) => {
      return localizedHeaders.map((header: any) => row[header] || '');
    });
    modifiedData = [localizedHeaders, ...dataRows];
  } else {
    modifiedData = [newSheetData];
  }

  const newData = updateTargetValues(modifiedData, newSheetData, localizedHeaders, oldTargetColumnsToHide, localizationMap);
  return newData;
}


function updateTargetValues(originalData: any, newData: any, localizedHeaders: any, oldTargetColumnsToHide: any[], localizationMap?: any) {

  const boundaryCodeIndex = localizedHeaders.indexOf(getLocalizedName(config?.boundary?.boundaryCode, localizationMap));

  // Backfill blank target cells in newData from the matching originalData row (matched on the boundary-code prefix).
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
  // Rename the header row's old target columns with an "(OLD)" suffix (to be hidden) and append original target values to every row.
  newData = newData.map((newRow: any, rowIndex: number) => {
    const updatedValues: any[] = [];
    for (let i = boundaryCodeIndex + 1; i < localizedHeaders.length; i++) {
      updatedValues.push(newRow[i]);
      if (rowIndex === 0) {
        const modifiedValue = `${newRow[i]}(OLD)`;
        newRow[i] = modifiedValue;
        oldTargetColumnsToHide.push(modifiedValue);
      }
    }
    return [...newRow, ...updatedValues];
  });
  return newData;
}

/** Fails validation if the child campaign drops any parent-campaign boundary, or changes boundaries on create without supplying a new boundary file. */
export async function validateMissingBoundaryFromParent(requestBody : any) {
  const { CampaignDetails, parentCampaign } = requestBody;
  const allCurrentCampaignBoundaries : any = await getAllBoundariesFromCampaign(CampaignDetails);
  if(parentCampaign){
    const allParentBoundaries: any = await getAllBoundariesFromCampaign(parentCampaign);
    const setOfBoundaryCodesFromCurrentCampaign : any = new Set(allCurrentCampaignBoundaries.map((boundary: any) => boundary.code));
    const missingBoundaries = allParentBoundaries.filter((boundary: any) => !setOfBoundaryCodesFromCurrentCampaign.has(boundary.code));
    if (missingBoundaries.length > 0) {
      throw new Error(`Missing boundaries from parent campaign: ${missingBoundaries.map((boundary: any) => boundary.code).join(', ')}`);
    }
    if (CampaignDetails?.action == "create") {
      const parentBoundaryCodes = new Set(allParentBoundaries.map((b: any) => b.code));
      // If the number of boundaries is different, it means the child has extra boundaries,
      // as we've already confirmed it's not missing any from the parent.
      if (setOfBoundaryCodesFromCurrentCampaign.size !== parentBoundaryCodes.size) {
        const boundaryResource = CampaignDetails.resources?.find(
          (r: CampaignResource) => r.type === 'boundary' && r.filestoreId
        );
        const isUnifiedCampaign = CampaignDetails?.additionalDetails?.isUnifiedCampaign || false;
        if (!boundaryResource && !isUnifiedCampaign) {
          throwError("COMMON", 400, "VALIDATION_ERROR_MISSING_TARGET_FILE", "A new boundary file must be provided when changing boundaries from the parent campaign.");
        }
      }
    }
  }
  requestBody.boundariesCombined = allCurrentCampaignBoundaries;
}

const getAllBoundariesFromCampaign = async (campaignDetails: any) => {
  const relationship = await searchBoundaryRelationshipData(
    campaignDetails?.tenantId,
    campaignDetails?.hierarchyType,
    true,
    true,
    false
  );

  const rootBoundary = relationship?.TenantBoundary?.[0]?.boundary?.[0];
  const allBoundaries = cloneDeep(campaignDetails?.boundaries || []);

  const boundaryChildren = Object.fromEntries(
    allBoundaries.map(({ code, includeAllChildren }: any) => [code, includeAllChildren])
  );
  const boundaryCodes = new Set(allBoundaries.map(({ code }: any) => code));

  await populateBoundariesRecursively(
    rootBoundary,
    allBoundaries,
    boundaryChildren[rootBoundary?.code],
    boundaryCodes,
    boundaryChildren
  );

  return allBoundaries;
};

function getBoundariesArray(parentCampaignBoundaries: any, campaignBoundaries: any) {
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

async function fetchProjectsWithProjectId(
  request: any,
  projectIds: string | string[],
  tenantId: string,
  includeDescendants: boolean = true
) {
  const idsArray = Array.isArray(projectIds) ? projectIds : [projectIds];

  if (idsArray.length === 0) return [];

  const projectSearchBody = {
    RequestInfo: request?.body?.RequestInfo || request?.RequestInfo,
    Projects: idsArray.map(id => ({
      id,
      tenantId
    }))
  };

  const projectSearchParams = {
    tenantId,
    offset: 0,
    limit: idsArray.length,
    includeDescendants: includeDescendants
  };

  logger.info("Project search params: " + JSON.stringify(projectSearchParams));
  logger.info("Project search body: " + JSON.stringify(projectSearchBody));

  const projectSearchResponse = await httpRequest(
    config?.host?.projectHost + config?.paths?.projectSearch,
    projectSearchBody,
    projectSearchParams
  );

  if (
    projectSearchResponse?.Project &&
    Array.isArray(projectSearchResponse?.Project) &&
    projectSearchResponse.Project.length > 0
  ) {
    return projectSearchResponse.Project; // Always return array
  } else {
    throwError("PROJECT", 500, "PROJECT_SEARCH_ERROR");
    return [];
  }
}



async function fetchProjectsWithBoundaryCodeAndReferenceId(boundaryCode: any, tenantId: any, referenceId: any, RequestInfo?: any) {
  try {
    const projectSearchBody = {
      RequestInfo: RequestInfo,
      Projects: [
        {
          address: {
            boundary: boundaryCode,
          },
          tenantId: tenantId,
          referenceID: referenceId
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

  result.forEach((entry: any) => {
    const boundary = entry.boundary;
    boundarySet.add(boundary);
    if (!request?.body?.boundaryProjectMapping?.[boundary]) {
      request.body.boundaryProjectMapping[boundary] = { parent: null, projectId: null };
    }
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

/** Resolves a filestore id to its downloadable URL, throwing INVALID_FILE if the filestore returns no URL. */
export async function getFileUrl(fileStoreId: string, tenantId: string) {
  const fileResponse = await httpRequest(
    `${config.host.filestore}${config.paths.filestore}/url`,
    {},
    { tenantId, fileStoreIds: fileStoreId },
    "get"
  );

  if (!fileResponse || !fileResponse.fileStoreIds || !fileResponse.fileStoreIds[0] || !fileResponse.fileStoreIds[0].url) {
    throwError("FILE", 400, "INVALID_FILE");
  } else {
    return fileResponse.fileStoreIds[0].url;
  }
}




export {
    getParentCampaignObject,
    getCreatedResourceIds,
    buildSearchCriteria,
    modifyProcessedSheetData,
    freezeUnfreezeColumnsForProcessedFile,
    getColumnIndexByHeader,
    checkAndGiveIfParentCampaignAvailable,
    hideColumnsOfProcessedFile,
    unhideColumnsOfProcessedFile,
    modifyNewSheetData,
    getBoundariesFromCampaignSearchResponse,
    fetchProjectsWithProjectId,
    getBoundaryProjectMappingFromParentCampaign,
    fetchProjectFacilityWithProjectId,
    fetchProjectsWithBoundaryCodeAndReferenceId
};
