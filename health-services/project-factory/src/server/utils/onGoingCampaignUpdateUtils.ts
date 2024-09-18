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

  if (!processedDistrictSheetData || processedDistrictSheetData.length === 0) return [];

  let localizedHeaders = getLocalizedHeaders(headers, localizationMap);
  const dataRows = processedDistrictSheetData.map((row: any) => {
    return localizedHeaders.map((header: any) => row[header] || '');
  });
  const modifiedData = [localizedHeaders, ...dataRows];
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
  modifyNewSheetData
}