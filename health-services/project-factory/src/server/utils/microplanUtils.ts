import { resourceDataStatuses, rolesForMicroplan } from "../config/constants";
import { v4 as uuidv4 } from 'uuid';
import config from "./../config";
import { throwError } from "./genericUtils";
import { httpRequest } from "./request";
import { getSheetData } from "./../api/genericApis";
import { getLocalizedName } from "./campaignUtils";
import createAndSearch from "../config/createAndSearch";
import { produceModifiedMessages } from "../kafka/Producer";


export const filterData = (data: any) => {
  return data.filter((item: any) => {
    // Create a shallow copy of the object without `#status#` and `#errorDetails#`
    const { '#status#': status, '#errorDetails#': errorDetails, ...rest } = item;

    // Check if only `!row#number!` remains after removing status and errorDetails
    const remainingKeys = Object.keys(rest).filter(key => key !== '!row#number!');

    // Include the item if any other properties exist besides `!row#number!`
    return remainingKeys.length > 0;
  });
};





export async function getUserDataFromMicroplanSheet(request: any, fileStoreId: any, tenantId: any, createAndSearchConfig: any, localizationMap?: { [key: string]: string }) {
  const fileResponse = await httpRequest(config.host.filestore + config.paths.filestore + "/url", {}, { tenantId: tenantId, fileStoreIds: fileStoreId }, "get");
  if (!fileResponse?.fileStoreIds?.[0]?.url) {
    throwError("FILE", 500, "DOWNLOAD_URL_NOT_FOUND");
  }
  var userMapping: any = {};
  for (const sheetName of rolesForMicroplan) {
    const dataOfSheet = filterData(await getSheetData(fileResponse?.fileStoreIds?.[0]?.url, sheetName, true, undefined, localizationMap));
    for (const user of dataOfSheet) {
      user.role = sheetName;
      user["!sheet#name!"] = sheetName;
      const emailKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_EMAIL_MICROPLAN", localizationMap)
      user[emailKey] = user[emailKey]?.text || user[emailKey];
      const phoneNumberKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER_MICROPLAN", localizationMap)
      if (!userMapping[user[phoneNumberKey]]) {
        userMapping[user[phoneNumberKey]] = [user]
      }
      else {
        userMapping[user[phoneNumberKey]].push(user)
      }
    }
  }
  const allUserData = getAllUserData(request, userMapping, localizationMap);
  return allUserData;
}

export function getAllUserData(request: any, userMapping: any, localizationMap: any) {
  const emailKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_EMAIL_MICROPLAN", localizationMap);
  const nameKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_NAME_MICROPLAN", localizationMap);
  const phoneNumberKey = getLocalizedName("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER_MICROPLAN", localizationMap);
  validateInConsistency(request, userMapping, emailKey, nameKey);
  validateNationalDuplicacy(request, userMapping, phoneNumberKey);
  var dataToCreate: any = [];
  for (const phoneNumber of Object.keys(userMapping)) {
    const roles = userMapping[phoneNumber].map((user: any) => user.role).join(',');
    const email = userMapping[phoneNumber]?.[0]?.[emailKey] || null;
    const name = userMapping[phoneNumber]?.[0]?.[nameKey];
    const rowNumbers = userMapping[phoneNumber].map((user: any) => user["!row#number!"]);
    const sheetNames = userMapping[phoneNumber].map((user: any) => user["!sheet#name!"]);
    const tenantId = request?.body?.ResourceDetails?.tenantId;
    const rowXSheet = rowNumbers.map((row: any, index: number) => ({
      row: row,
      sheetName: sheetNames[index]
    }));
    dataToCreate.push({ ["!row#number!"]: rowXSheet, tenantId: tenantId, employeeType: "TEMPORARY", user: { emailId: email, name: name, mobileNumber: phoneNumber, roles: roles } });
  }
  request.body.dataToCreate = dataToCreate;
  return convertDataSheetWise(userMapping);
}

function validateInConsistency(request: any, userMapping: any, emailKey: any, nameKey: any) {
  let overallInconsistencies: string[] = []; // Collect all inconsistencies here

  enrichInconsistencies(overallInconsistencies, userMapping, nameKey, emailKey);
  if (overallInconsistencies.length > 0) {
    request.body.ResourceDetails.status = resourceDataStatuses.invalid
  }

  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...overallInconsistencies] : overallInconsistencies;
}

function validateNationalDuplicacy(request: any, userMapping: any, phoneNumberKey: any) {
  const duplicates: any[] = [];

  for (const phoneNumber in userMapping) {
    const roleMap: any = {};
    const users = userMapping[phoneNumber];

    for (const user of users) {
      if (user.role && user.role.startsWith("Root ")) {
        // Trim the role
        const trimmedRole = user.role.replace("Root ", "").trim().toLowerCase();
        const trimmedRoleWithCapital = trimmedRole.charAt(0).toUpperCase() + trimmedRole.slice(1);

        // Check for duplicates in the roleMap
        if (roleMap[trimmedRole] && roleMap[trimmedRole]["!sheet#name!"] != user["!sheet#name!"]) {
          const errorMessage: any = `An user with ${trimmedRoleWithCapital} role can’t be assigned to ${user.role} role`;
          duplicates.push({ rowNumber: user["!row#number!"], sheetName: user["!sheet#name!"], status: "INVALID", errorDetails: errorMessage });
        } else {
          roleMap[trimmedRole] = user;
        }
      }
      else {
        const trimmedRole = user.role.toLowerCase();
        const errorMessage: any = `An user with ${"Root " + trimmedRole} role can’t be assigned to ${user.role} role`;
        if (roleMap[trimmedRole] && roleMap[trimmedRole]["!sheet#name!"] != user["!sheet#name!"]) {
          duplicates.push({ rowNumber: user["!row#number!"], sheetName: user["!sheet#name!"], status: "INVALID", errorDetails: errorMessage });
        } else {
          roleMap[trimmedRole] = user;
        }
      }
    }
  }
  if (duplicates.length > 0) {
    request.body.ResourceDetails.status = resourceDataStatuses.invalid
  }
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...duplicates] : duplicates;
}

function convertDataSheetWise(userMapping: any) {
  var sheetMapping: any = {}
  for (const phoneNumber in userMapping) {
    const users = userMapping[phoneNumber];
    for (const user of users) {
      const sheetName = user["!sheet#name!"];
      if (!sheetMapping[sheetName]) {
        sheetMapping[sheetName] = [];
      }
      sheetMapping[sheetName].push(user);
    }
  }
  return sheetMapping;
}

function getInconsistencyErrorMessage(phoneNumber: any, userRecords: any) {
  // Create the error message mentioning all the records for this phone number
  var errors: any = []
  let errorMessage = `User details for the same contact number isn’t matching. Please check the user’s name or email ID`;
  for (const record of userRecords) {
    errors.push({ rowNumber: record.row, sheetName: record.sheet, status: "INVALID", errorDetails: errorMessage });
  }

  return errors
}

function enrichInconsistencies(overallInconsistencies: any, userMapping: any, nameKey: string, emailKey: string) {
  for (const phoneNumber in userMapping) {
    if (phoneNumber && phoneNumber != 'undefined') {
      const users = userMapping[phoneNumber];
      let userRecords: any[] = [];

      // Collect all user data for this phone number
      for (const user of users) {
        userRecords.push({
          row: user["!row#number!"],
          sheet: user["!sheet#name!"],
          name: user[nameKey],
          email: user[emailKey]
        });
      }

      const errorMessage = getInconsistencyErrorMessage(phoneNumber, userRecords);

      // Check for any inconsistencies by comparing all records with each other
      const firstRecord = userRecords[0]; // Take the first record as baseline
      const inconsistentRecords = userRecords.filter(record =>
        record.name !== firstRecord.name || record.email !== firstRecord.email
      );
      if (inconsistentRecords.length > 0) {
        overallInconsistencies.push(...errorMessage);  // Collect all inconsistencies
      }
    }
  }
}

function lockTillStatus(workbook: any) {
  workbook.worksheets.forEach((sheet: any) => {
    const statusCell = findStatusColumn(sheet); // Find the status column

    if (!statusCell) {
      // Lock the entire sheet if no "#status#" found
      sheet.protect('passwordhere', {
        selectLockedCells: true,
        selectUnlockedCells: false
      });
    } else {
      // Lock the entire sheet but allow selecting unlocked cells
      sheet.protect('passwordhere', {
        selectLockedCells: true,
        selectUnlockedCells: true // Allow selecting unlocked cells
      });

      // Lock the first row
      sheet.getRow(1).eachCell({ includeEmpty: true }, (cell: any) => {
        cell.protection = { locked: true };
      });

      // Lock every column starting from the "#status#" column
      const statusColIndex = statusCell.col;
      for (let col = statusColIndex; col <= sheet.columnCount; col++) {
        sheet.getColumn(col).eachCell({ includeEmpty: true }, (cell: any) => {
          cell.protection = { locked: true };
        });
      }

      // Unlock the rest of the sheet (if needed)
      sheet.eachRow((row: any, rowIndex: any) => {
        if (rowIndex > 1) {  // Skip first row
          row.eachCell({ includeEmpty: true }, (cell: any) => {
            if (cell.col < statusColIndex) {  // Unlock cells before the status column
              cell.protection = { locked: false };
            }
          });
        }
      });
    }
  });
}

export function lockWithConfig(sheet: any) {
  for (let row = 1; row <= parseInt(config.values.unfrozeTillRow); row++) {
    for (let col = 1; col <= parseInt(config.values.unfrozeTillColumn); col++) {
      const cell = sheet.getCell(row, col);
      if (!cell.value && cell.value !== 0) {
        cell.protection = { locked: false };
      }
    }
  }
  sheet.protect('passwordhere', { selectLockedCells: true, selectUnlockedCells: true });
}

function lockAll(workbook: any) {
  workbook.worksheets.forEach((sheet: any) => {
    lockWithConfig(sheet);
  })
}


export function lockSheet(request: any, workbook: any) {
  if (request?.body?.ResourceDetails?.type == 'create') {
    lockAll(workbook);
  }
  else {
    lockTillStatus(workbook);
  }
}

// Helper function to find the column containing "#status#"
function findStatusColumn(sheet: any) {
  let statusCell: any = null;

  // Loop through each row
  sheet.eachRow((row: any, rowIndex: any): any => {
    // Loop through each cell in the row
    row.eachCell((cell: any, colIndex: any) => {
      // If "#status#" is found, capture the row and column
      if (cell.value === "#status#") {
        statusCell = { row: rowIndex, col: colIndex };
      }
    });

    // If the status cell is found, stop further iteration
    if (statusCell) {
      return false; // This will only break the row loop, not the function
    }
  });

  // Return the found statusCell, or null if not found
  return statusCell;
}

export function changeCreateDataForMicroplan(request: any, element: any, rowData: any, localizationMap?: any) {
  const type = request?.body?.ResourceDetails?.type;
  const activeColumnName = createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName ? getLocalizedName(createAndSearch?.[request?.body?.ResourceDetails?.type]?.activeColumnName, localizationMap) : null;
  if (type == 'facility') {
    const projectType = request?.body?.projectTypeCode;
    const facilityCapacityColumn = getLocalizedName(`HCM_ADMIN_CONSOLE_FACILITY_CAPACITY_MICROPLAN_${projectType}`, localizationMap);
    if (rowData[facilityCapacityColumn] >= 0) {
      element.storageCapacity = rowData[facilityCapacityColumn]
    }
    if (activeColumnName && rowData[activeColumnName] == "Active") {
      if (Array(request?.body?.facilityDataForMicroplan) && request?.body?.facilityDataForMicroplan?.length > 0) {
        request.body.facilityDataForMicroplan.push({ ...rowData, facilityDetails: element })
      }
      else {
        request.body.facilityDataForMicroplan = [{ ...rowData, facilityDetails: element }]
      }
    }
  }
}

export function updateFacilityDetailsForMicroplan(request: any, createdData: any) {
  const facilityDataForMicroplan = request?.body?.facilityDataForMicroplan;
  if (Array.isArray(facilityDataForMicroplan) && facilityDataForMicroplan.length > 0) {
    for (const element of facilityDataForMicroplan) {
      const rowNumber = element['!row#number!'];
      const createdDataWithMatchingRowNumber = createdData.find((data: any) => data['!row#number!'] == rowNumber) || null;
      if (createdDataWithMatchingRowNumber) {
        element.facilityDetails.id = createdDataWithMatchingRowNumber.id
      }
    }
  }
}


export async function createPlanFacilityForMicroplan(request: any, localizationMap?: any) {
  if (request?.body?.ResourceDetails?.type == 'facility' && request?.body?.ResourceDetails?.additionalDetails?.source == 'microplan') {
    const allFacilityDatas = request?.body?.facilityDataForMicroplan;
    const planConfigurationId = request?.body?.ResourceDetails?.additionalDetails?.microplanId;
    for (const element of allFacilityDatas) {
      const residingBoundariesColumn = getLocalizedName(`HCM_ADMIN_CONSOLE_RESIDING_BOUNDARY_CODE_MICROPLAN`, localizationMap);
      const singularResidingBoundary = element?.[residingBoundariesColumn]?.split(",")?.[0];
      const facilityStatus = element?.facilityDetails?.isPermanent ? "Permanent" : "Temporary";
      const facilityType = element?.facilityDetails?.usage;
      const currTime = new Date().getTime();
      const produceObject: any = {
        PlanFacility: {
          id: uuidv4(),
          tenantId: element?.facilityDetails?.tenantId,
          planConfigurationId: planConfigurationId,
          facilityId: element?.facilityDetails?.id,
          residingBoundary: singularResidingBoundary,
          facilityName: element?.facilityDetails?.name,
          serviceBoundaries: null,
          additionalDetails: {
            capacity: element?.facilityDetails?.storageCapacity,
            facilityName: element?.facilityDetails?.name,
            facilityType: facilityType,
            facilityStatus: facilityStatus,
            assignedVillages: [],
            servingPopulation: 0
          },
          active: true,
          auditDetails: {
            createdBy: request?.body?.RequestInfo?.userInfo?.uuid,
            lastModifiedBy: request?.body?.RequestInfo?.userInfo?.uuid,
            createdTime: currTime,
            lastModifiedTime: currTime
          }
        }
      }
      const fixedPostColumn = getLocalizedName(`HCM_ADMIN_CONSOLE_FACILITY_FIXED_POST_MICROPLAN`, localizationMap);
      if (element?.[fixedPostColumn]) {
        produceObject.PlanFacility.additionalDetails.fixedPost = element?.[fixedPostColumn]
      }
      await produceModifiedMessages(produceObject, config?.kafka?.KAFKA_SAVE_PLAN_FACILITY_TOPIC);
    }
  }
}


export async function planFacilitySearch(request:any) {
  const {tenantId, planConfigurationId} = request.body.MicroplanDetails;
  const searchBody = {
        RequestInfo: request.body.RequestInfo,
        PlanFacilitySearchCriteria: {
            tenantId: tenantId,
            planConfigurationId: planConfigurationId
        }
    }

    const searchResponse = await httpRequest(config.host.planServiceHost + config.paths.planFacilitySearch, searchBody);
    return searchResponse; 
}

export function planConfigSearch(request: any) {
  const {tenantId, planConfigurationId} = request.body.MicroplanDetails;
  const searchBody = {
        RequestInfo: request.body.RequestInfo,
        PlanConfigurationSearchCriteria: {
            tenantId: tenantId,
            id: planConfigurationId
        }
    }

    const searchResponse = httpRequest(config.host.planServiceHost + config.paths.planFacilityConfigSearch, searchBody);
  return searchResponse;
}

export function modifyBoundaryIfSourceMicroplan(boundaryData: any[], request: any) {
  const hierarchy = request?.body?.hierarchyType?.boundaryHierarchy;
  if (request?.body?.isSourceMicroplan && request?.query?.type === 'facilityWithBoundary') {
    boundaryData = boundaryData.filter((boundary: any) => boundary[hierarchy.length - 1]);
  }
  return boundaryData;
}

