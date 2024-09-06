import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getLocalizedHeaders, replicateRequest } from "./genericUtils";
import { httpRequest } from "./request";
import config from "../config/index";
import { getLocalizedName } from "./campaignUtils";

async function getParentCampaignObject(request: any, parentId: any) {
  try {
    const searchBodyForParent = {
      RequestInfo: request.body.RequestInfo,
      CampaignDetails: {
        tenantId: request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId,
        ids: [parentId]
      }
    };
    const req: any = replicateRequest(request, searchBodyForParent);
    const parentSearchResponse = await searchProjectTypeCampaignService(req);
    return parentSearchResponse?.CampaignDetails?.[0];
  } catch (error) {
    console.error("Error fetching parent campaign object:", error);
    throw error;
  }
}

function getCreatedResourceIds(resources: any, type: any) {
  return resources
    .filter((item: any) => item.type === type.split('With')[0])
    .map((item: any) => item.createResourceId);
}

function buildSearchCriteria(request: any, createdResourceId: any, type: any) {
  return {
    RequestInfo: request.body.RequestInfo,
    SearchCriteria: {
      id: createdResourceId,
      tenantId: request?.query?.tenantId,
      type: type.split('With')[0]
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



function modifyProcessedSheetData(sheetData: any, localizationMap?: any, schema?: any) {
  if (!sheetData || sheetData.length === 0) return [];
  console.log(schema, "sssssssssssss")

  // Find the row with the maximum number of keys
  const maxLengthRow = sheetData.reduce((maxRow: any, row: any) => {
    return Object.keys(row).length > Object.keys(maxRow).length ? row : maxRow;
  }, {});

  // Extract headers from the keys of the row with the maximum number of keys
  const originalHeaders = schema ? schema.columns : Object.keys(maxLengthRow);
  console.log(originalHeaders, "oggggggg")
  // Define the new header to add
  const additionalHeader = 'HCM_ADMIN_CONSOLE_UPDATED_BOUNDARY_CODE';

  // Combine original headers with the additional header
  const headers = schema ? originalHeaders : [...originalHeaders, additionalHeader];
  const localizedHeaders = getLocalizedHeaders(headers, localizationMap);

  // Map each object in sheetData to an array of values corresponding to the header order
  const dataRows = sheetData.map((row: any) => {
    return localizedHeaders.map((header: any) => row[header] || '');
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

function validateBoundaryCodes(activeRows: any, localizationMap?: any) {
  const updatedBoundaryCodeKey = getLocalizedName('HCM_ADMIN_CONSOLE_UPDATED_BOUNDARY_CODE', localizationMap);
  const updatedBoundaryCodeValue = activeRows[updatedBoundaryCodeKey];
  const boundaryCodeMandatoryKey = getLocalizedName("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY", localizationMap);
  const boundaryCodeMandatoryValue = activeRows[boundaryCodeMandatoryKey];
  if (!updatedBoundaryCodeValue && !boundaryCodeMandatoryValue) {
    const errorDetails = {
      errors: [
        {
          instancePath: '',
          schemaPath: '#/required',
          keyword: 'required',
          params: {
            missingProperty: `${updatedBoundaryCodeKey} and ${boundaryCodeMandatoryKey} both`
          },
          message: `must have required properties ${`${updatedBoundaryCodeKey}, ${boundaryCodeMandatoryKey}`}`
        }
      ]
    };

    throw new Error(JSON.stringify(errorDetails));
  }
}

async function checkAndGiveIfParentCampaignAvailable(request: any, campaignObject: any): Promise<any | null> {
  if (campaignObject?.parentId) {
    const parentCampaignObject = await getParentCampaignObject(request, campaignObject.parentId);

    if (parentCampaignObject?.status === "created" && parentCampaignObject.isActive) { // for time being let it be active
      return parentCampaignObject;
    }
  }
  return null;
}




export {
  getParentCampaignObject,
  getCreatedResourceIds,
  buildSearchCriteria,
  fetchFileUrls,
  modifyProcessedSheetData,
  freezeUnfreezeColumnsForProcessedFile,
  getColumnIndexByHeader,
  validateBoundaryCodes,
  checkAndGiveIfParentCampaignAvailable
}