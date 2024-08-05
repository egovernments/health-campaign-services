import config from "../config";
import { v4 as uuidv4 } from 'uuid';
import { httpRequest } from "../utils/request";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import createAndSearch from '../config/createAndSearch';
import { getDataFromSheet, generateActivityMessage, throwError, translateSchema, replicateRequest, appendProjectTypeToCapacity } from "../utils/genericUtils";
import { immediateValidationForTargetSheet, validateSheetData, validateTargetSheetData } from '../validators/campaignValidators';
import { callMdmsTypeSchema, getCampaignNumber } from "./genericApis";
import { boundaryBulkUpload, convertToTypeData, generateHierarchy, generateProcessedFileAndPersist, getBoundaryOnWhichWeSplit, getLocalizedName, reorderBoundariesOfDataAndValidate, checkIfSourceIsMicroplan } from "../utils/campaignUtils";
const _ = require('lodash');
import { produceModifiedMessages } from "../kafka/Producer";
import { createDataService } from "../service/dataManageService";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getExcelWorkbookFromFileURL } from "../utils/excelUtils";
import { processTrackStatuses, processTrackTypes } from "../config/constants";
import { persistTrack } from "../utils/processTrackUtils";



/**
 * Enriches the campaign data with unique IDs and generates campaign numbers.
 * @param requestBody The request body containing the campaign data.
 */
async function enrichCampaign(requestBody: any) {
  // Enrich campaign data with unique IDs and generate campaign numbers
  if (requestBody?.Campaign) {
    requestBody.Campaign.id = uuidv4();
    requestBody.Campaign.campaignNo = await getCampaignNumber(requestBody, config.values.idgen.format, config.values.idgen.idName, requestBody?.Campaign?.tenantId);
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
      campaignDetails.id = uuidv4();
    }
  }
}

async function getAllFacilitiesInLoop(searchedFacilities: any[], facilitySearchParams: any, facilitySearchBody: any) {
  const response = await httpRequest(config.host.facilityHost + config.paths.facilitySearch, facilitySearchBody, facilitySearchParams);

  if (Array.isArray(response?.Facilities)) {
    searchedFacilities.push(...response?.Facilities);
    return response.Facilities.length >= 50; // Return true if there are more facilities to fetch, false otherwise
  } else {
    throwError("FACILITY", 500, "FACILITY_SEARCH_FAILED");
    return false;
  }
}

/**
 * Retrieves all facilities for a given tenant ID.
 * @param tenantId The ID of the tenant.
 * @param requestBody The request body containing additional parameters.
 * @returns An array of facilities.
 */
async function getAllFacilities(tenantId: string, requestBody: any) {
  // Retrieve all facilities for the given tenant ID
  const facilitySearchBody = {
    RequestInfo: requestBody?.RequestInfo,
    Facility: { isPermanent: true }
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId?.split('.')?.[0]
  };

  const searchedFacilities: any[] = [];
  let searchAgain = true;

  while (searchAgain) {
    searchAgain = await getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody);
    facilitySearchParams.offset += 50;
  }

  return searchedFacilities;
}

/**
 * Retrieves facilities by their IDs.
 * @param tenantId The ID of the tenant.
 * @param ids An array of facility IDs.
 * @param requestBody The request body containing additional parameters.
 * @returns An array of facilities.
 */
async function getFacilitiesViaIds(tenantId: string, ids: any[], requestBody: any) {
  // Retrieve facilities by their IDs
  const facilitySearchBody: any = {
    RequestInfo: requestBody?.RequestInfo,
    Facility: {}
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId?.split('.')?.[0]
  };

  const searchedFacilities: any[] = [];

  // Split ids into chunks of 50
  for (let i = 0; i < ids.length; i += 50) {
    const chunkIds = ids.slice(i, i + 50);
    facilitySearchBody.Facility.id = chunkIds;
    await getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody);
  }

  return searchedFacilities;
}

/**
 * Retrieves parameters based on elements.
 * @param elements An array of elements.
 * @param request The HTTP request object.
 * @returns Parameters extracted from elements.
 */
function getParamsViaElements(elements: any, request: any) {
  // Extract parameters based on elements
  var params: any = {};
  if (!elements) {
    return params;
  }
  for (const element of elements) {
    if (element?.isInParams) {
      if (element?.value) {
        _.set(params, element?.keyPath, element?.value);
      }
      else if (element?.getValueViaPath) {
        _.set(params, element?.keyPath, _.get(request.body, element?.getValueViaPath))
      }
    }
  }
  return params
}

/**
 * Changes request body based on elements.
 * @param elements An array of elements.
 * @param requestBody The request body to be modified.
 */
function changeBodyViaElements(elements: any, requestBody: any) {
  // Modify request body based on elements
  if (!elements) {
    return;
  }
  for (const element of elements) {
    if (element?.isInBody) {
      if (element?.value) {
        _.set(requestBody, element?.keyPath, element?.value);
      }
      else if (element?.getValueViaPath) {
        _.set(requestBody, element?.keyPath, _.get(requestBody, element?.getValueViaPath))
      }
      else {
        _.set(requestBody, element?.keyPath, {})
      }
    }
  }
}

// function changeBodyViaSearchFromSheet(elements: any, request: any, dataFromSheet: any) {
//   if (!elements) {
//     return;
//   }
//   for (const element of elements) {
//     const arrayToSearch = []
//     for (const data of dataFromSheet) {
//       if (data[element.sheetColumnName]) {
//         arrayToSearch.push(data[element.sheetColumnName]);
//       }
//     }
//     _.set(request.body, element?.searchPath, arrayToSearch);
//   }
// }

function updateErrorsForUser(newCreatedData: any[], newSearchedData: any[], errors: any[], createAndSearchConfig: any, userNameAndPassword: any[]) {
  newCreatedData.forEach((createdElement: any) => {
    let foundMatch = false;
    for (const searchedElement of newSearchedData) {
      if (searchedElement?.code === createdElement?.code) {
        foundMatch = true;
        newSearchedData.splice(newSearchedData.indexOf(searchedElement), 1);
        errors.push({ status: "CREATED", rowNumber: createdElement["!row#number!"], isUniqueIdentifier: true, uniqueIdentifier: _.get(searchedElement, createAndSearchConfig.uniqueIdentifier, ""), errorDetails: "" })
        userNameAndPassword.push({
          userName: searchedElement?.user?.userName,
          password: createdElement?.user?.password,
          rowNumber: createdElement["!row#number!"]
        })
        break;
      }
    }
    if (!foundMatch) {
      errors.push({ status: "NOT_CREATED", rowNumber: createdElement["!row#number!"], errorDetails: `Can't confirm creation of this data` })
      logger.info("Can't confirm creation of this data of row number : " + createdElement["!row#number!"]);
    }
  });
}

function updateErrors(newCreatedData: any[], newSearchedData: any[], errors: any[], createAndSearchConfig: any) {
  newCreatedData.forEach((createdElement: any) => {
    let foundMatch = false;
    for (const searchedElement of newSearchedData) {
      let match = true;
      for (const key in createdElement) {
        if (createdElement[key] !== searchedElement[key] && key != '!row#number!') {
          match = false;
          break;
        }
      }
      if (match) {
        foundMatch = true;
        newSearchedData.splice(newSearchedData.indexOf(searchedElement), 1);
        errors.push({ status: "CREATED", rowNumber: createdElement["!row#number!"], isUniqueIdentifier: true, uniqueIdentifier: _.get(searchedElement, createAndSearchConfig.uniqueIdentifier, ""), errorDetails: "" })
        break;
      }
    }
    if (!foundMatch) {
      errors.push({ status: "NOT_CREATED", rowNumber: createdElement["!row#number!"], errorDetails: `Can't confirm creation of this data` })
      logger.info("Can't confirm creation of this data of row number : " + createdElement["!row#number!"]);
    }
  });
}


function matchCreatedAndSearchedData(createdData: any[], searchedData: any[], request: any, createAndSearchConfig: any, activities: any) {
  const newCreatedData = JSON.parse(JSON.stringify(createdData));
  const newSearchedData = JSON.parse(JSON.stringify(searchedData));
  const uid = createAndSearchConfig.uniqueIdentifier;
  newCreatedData.forEach((element: any) => {
    delete element[uid];
  })
  var errors: any[] = []
  if (request?.body?.ResourceDetails?.type != "user") {
    if (request?.body?.ResourceDetails?.type == "facility") {
      newCreatedData?.forEach((element: any) => {
        delete element.address
      })
    }
    updateErrors(newCreatedData, newSearchedData, errors, createAndSearchConfig);
  }
  else {
    var userNameAndPassword: any = []
    updateErrorsForUser(newCreatedData, newSearchedData, errors, createAndSearchConfig, userNameAndPassword);
    request.body.userNameAndPassword = userNameAndPassword
  }
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
  request.body.Activities = activities
}

async function getUuidsError(request: any, response: any, mobileNumberRowNumberMapping: any) {
  var errors: any[] = []
  var count = 0;
  request.body.mobileNumberUuidsMapping = request.body.mobileNumberUuidsMapping ? request.body.mobileNumberUuidsMapping : {};
  for (const user of response.Individual) {
    if (!user?.userUuid) {
      logger.info(`User with mobileNumber ${user?.mobileNumber} doesn't have userUuid`)
      errors.push({ status: "INVALID", rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber], errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have userUuid` })
      count++;
    }
    else if (!user?.userDetails?.username) {
      logger.info(`User with mobileNumber ${user?.mobileNumber} doesn't have username`)
      errors.push({ status: "INVALID", rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber], errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have username` })
      count++;
    }
    else if (!user?.userDetails?.password) {
      logger.info(`User with mobileNumber ${user?.mobileNumber} doesn't have password`)
      errors.push({ status: "INVALID", rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber], errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have password` })
      count++;
    }
    else if (!user?.userUuid) {
      logger.info(`User with mobileNumber ${user?.mobileNumber} doesn't have userServiceUuid`)
      errors.push({ status: "INVALID", rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber], errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have userServiceUuid` })
      count++;
    }
    else {
      request.body.mobileNumberUuidsMapping[user?.mobileNumber] = { userUuid: user?.id, code: user?.userDetails?.username, rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber], password: user?.userDetails?.password, userServiceUuid: user?.userUuid }
    }
  }
  if (count > 0) {
    request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
  }
}

const createBatchRequest = async (request: any, batch: any[], mobileNumberRowNumberMapping: any) => {
  const searchBody = {
    RequestInfo: request?.body?.RequestInfo,
    Individual: {
      mobileNumber: batch
    }
  };
  const params = {
    limit: 55,
    offset: 0,
    tenantId: request?.body?.ResourceDetails?.tenantId,
    includeDeleted: true
  };
  logger.info("Individual search to validate the mobile no initiated");
  const response = await httpRequest(config.host.healthIndividualHost + config.paths.healthIndividualSearch, searchBody, params, undefined, undefined, undefined, undefined, true);

  if (!response) {
    throwError("COMMON", 400, "INTERNAL_SERVER_ERROR", "Error occurred during user search while validating mobile number.");
  }
  if (config.values.notCreateUserIfAlreadyThere) {
    await getUuidsError(request, response, mobileNumberRowNumberMapping);
  }

  if (response?.Individual?.length > 0) {
    return response.Individual.map((item: any) => item?.mobileNumber);
  }
  return [];
};

async function getUserWithMobileNumbers(request: any, mobileNumbers: any[], mobileNumberRowNumberMapping: any) {
  logger.info("mobileNumbers to search: " + JSON.stringify(mobileNumbers));
  const BATCH_SIZE = 50;
  let allResults: any[] = [];

  // Create an array of batch promises
  const batchPromises = [];
  for (let i = 0; i < mobileNumbers.length; i += BATCH_SIZE) {
    const batch = mobileNumbers.slice(i, i + BATCH_SIZE);
    batchPromises.push(createBatchRequest(request, batch, mobileNumberRowNumberMapping));
  }

  // Wait for all batch requests to complete
  const batchResults = await Promise.all(batchPromises);

  // Aggregate all results
  for (const result of batchResults) {
    allResults = allResults.concat(result);
  }
  // Convert the results array to a Set to eliminate duplicates
  const resultSet = new Set(allResults);
  logger.info(`Already Existing mobile numbers : ${JSON.stringify(resultSet)}`);
  return resultSet;
}


async function matchUserValidation(createdData: any[], request: any) {
  var count = 0;
  const errors = []
  const mobileNumbers = createdData.filter(item => item?.user?.mobileNumber).map(item => (item?.user?.mobileNumber));
  const mobileNumberRowNumberMapping = createdData.reduce((acc, curr) => {
    acc[curr.user.mobileNumber] = curr["!row#number!"];
    return acc;
  }, {});
  logger.info("mobileNumberRowNumberMapping : " + JSON.stringify(mobileNumberRowNumberMapping));
  const mobileNumberResponse = await getUserWithMobileNumbers(request, mobileNumbers, mobileNumberRowNumberMapping);
  for (const key in mobileNumberRowNumberMapping) {
    if (mobileNumberResponse.has(key) && !config.values.notCreateUserIfAlreadyThere) {
      errors.push({ status: "INVALID", rowNumber: mobileNumberRowNumberMapping[key], errorDetails: `User with mobileNumber ${key} already exists` })
      count++;
    }
  }
  if (count) {
    request.body.ResourceDetails.status = "invalid"
  }
  logger.info("Invalid resources count : " + count);
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
}
function matchViaUserIdAndCreationTime(createdData: any[], searchedData: any[], request: any, creationTime: any, createAndSearchConfig: any, activities: any) {
  var matchingSearchData = [];
  const userUuid = request?.body?.RequestInfo?.userInfo?.uuid
  var count = 0;
  if (request?.body?.ResourceDetails?.type != "user") {
    for (const data of searchedData) {
      if (data?.auditDetails?.createdBy == userUuid && data?.auditDetails?.createdTime >= creationTime) {
        matchingSearchData.push(data);
        count++;
      }
    }
  }
  else {
    count = searchedData.length;
    matchingSearchData = searchedData;
  }
  if (count < createdData.length) {
    request.body.ResourceDetails.status = "PERSISTER_ERROR"
  }
  matchCreatedAndSearchedData(createdData, matchingSearchData, request, createAndSearchConfig, activities);
  logger.info("New created resources count : " + count);
}

async function processSearch(createAndSearchConfig: any, request: any, params: any) {
  setSearchLimits(createAndSearchConfig, request, params);
  const arraysToMatch = await performSearch(createAndSearchConfig, request, params);
  return arraysToMatch;
}

function setSearchLimits(createAndSearchConfig: any, request: any, params: any) {
  setLimitOrOffset(createAndSearchConfig?.searchDetails?.searchLimit, params, request.body);
  setLimitOrOffset(createAndSearchConfig?.searchDetails?.searchOffset, params, request.body);
}

function setLimitOrOffset(limitOrOffsetConfig: any, params: any, requestBody: any) {
  if (limitOrOffsetConfig) {
    if (limitOrOffsetConfig?.isInParams) {
      _.set(params, limitOrOffsetConfig?.keyPath, parseInt(limitOrOffsetConfig?.value));
    }
    if (limitOrOffsetConfig?.isInBody) {
      _.set(requestBody, limitOrOffsetConfig?.keyPath, parseInt(limitOrOffsetConfig?.value));
    }
  }
}

async function performSearch(createAndSearchConfig: any, request: any, params: any) {
  const arraysToMatch: any[] = [];
  let searchAgain = true;
  while (searchAgain) {
    const searcRequestBody = {
      RequestInfo: request?.body?.RequestInfo
    }
    changeBodyViaElements(createAndSearchConfig?.searchDetails?.searchElements, searcRequestBody)
    const response = await httpRequest(createAndSearchConfig?.searchDetails?.url, searcRequestBody, params);
    const resultArray = _.get(response, createAndSearchConfig?.searchDetails?.searchPath);
    if (resultArray && Array.isArray(resultArray)) {
      arraysToMatch.push(...resultArray);
      if (resultArray.length < parseInt(createAndSearchConfig?.searchDetails?.searchLimit?.value)) {
        searchAgain = false;
      }
    } else {
      searchAgain = false;
    }
    updateOffset(createAndSearchConfig, params, request.body);
  }
  return arraysToMatch;
}

function updateOffset(createAndSearchConfig: any, params: any, requestBody: any) {
  const offsetConfig = createAndSearchConfig?.searchDetails?.searchOffset
  const limit = createAndSearchConfig?.searchDetails?.searchLimit?.value
  if (offsetConfig) {
    if (offsetConfig?.isInParams) {
      _.set(params, offsetConfig?.keyPath, parseInt(_.get(params, offsetConfig?.keyPath) + parseInt(limit)));
    }
    if (offsetConfig?.isInBody) {
      _.set(requestBody, offsetConfig?.keyPath, parseInt(_.get(requestBody, offsetConfig?.keyPath) + parseInt(limit)));
    }
  }
}


async function processSearchAndValidation(request: any, createAndSearchConfig: any, dataFromSheet: any[]) {
  // if (request?.body?.dataToSearch?.length > 0) {
  //   const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
  //   changeBodyViaElements(createAndSearchConfig?.searchDetails?.searchElements, request)
  //   changeBodyViaSearchFromSheet(createAndSearchConfig?.requiresToSearchFromSheet, request, dataFromSheet)
  //   const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
  //   matchData(request, request.body.dataToSearch, arraysToMatch, createAndSearchConfig)
  // }
  if (request?.body?.ResourceDetails?.type == "user") {
    await enrichEmployees(request?.body?.dataToCreate, request)
    await matchUserValidation(request.body.dataToCreate, request)
  }
}

async function getEmployeesBasedOnUuids(dataToCreate: any[], request: any) {
  const searchBody = {
    RequestInfo: request?.body?.RequestInfo
  };

  const tenantId = request?.body?.ResourceDetails?.tenantId;
  const searchUrl = config.host.hrmsHost + config.paths.hrmsEmployeeSearch;
  logger.info(`Waiting for 10 seconds`);
  await new Promise(resolve => setTimeout(resolve, 10000));
  const chunkSize = 50;
  let employeesSearched: any[] = [];

  for (let i = 0; i < dataToCreate.length; i += chunkSize) {
    const chunk = dataToCreate.slice(i, i + chunkSize);
    const uuids = chunk.map((data: any) => data.uuid).join(',');

    const params = {
      tenantId: tenantId,
      uuids: uuids,
      limit: 51,
      offset: 0
    };

    try {
      const response = await httpRequest(searchUrl, searchBody, params, undefined, undefined, undefined, undefined, true);
      if (response && response.Employees) {
        employeesSearched = employeesSearched.concat(response.Employees);
      } else {
        throw new Error("Unable to fetch employees based on UUIDs");
      }
    } catch (error: any) {
      console.log(error);
      throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", error.message || "Some internal error occurred while searching employees");
    }
  }

  return employeesSearched;
}






// Confirms the creation of resources by matching created and searched data.
async function confirmCreation(createAndSearchConfig: any, request: any, dataToCreate: any[], creationTime: any, activities: any) {
  // Confirm creation of resources by matching data  // wait for 5 seconds
  if (request?.body?.ResourceDetails?.type != "user") {
    const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
    const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
    matchViaUserIdAndCreationTime(dataToCreate, arraysToMatch, request, creationTime, createAndSearchConfig, activities)
  }
  else {
    const arraysToMatch = await getEmployeesBasedOnUuids(dataToCreate, request)
    matchViaUserIdAndCreationTime(dataToCreate, arraysToMatch, request, creationTime, createAndSearchConfig, activities)
  }
}

async function processValidateAfterSchema(dataFromSheet: any, request: any, createAndSearchConfig: any, localizationMap?: { [key: string]: string }) {
  try {
    const typeData = await convertToTypeData(request, dataFromSheet, createAndSearchConfig, request.body, localizationMap)
    request.body.dataToSearch = typeData.searchData;
    request.body.dataToCreate = typeData.createData;
    await processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)
    await reorderBoundariesOfDataAndValidate(request, localizationMap)
    await generateProcessedFileAndPersist(request, localizationMap);
  } catch (error) {
    console.log(error)
    await handleResouceDetailsError(request, error);
  }
}

async function processValidate(request: any, localizationMap?: { [key: string]: string }) {
  const type: string = request.body.ResourceDetails.type;
  const tenantId = request.body.ResourceDetails.tenantId;
  const createAndSearchConfig = createAndSearch[type]
  const dataFromSheet = await getDataFromSheet(request, request?.body?.ResourceDetails?.fileStoreId, request?.body?.ResourceDetails?.tenantId, createAndSearchConfig, null, localizationMap)
  if (type == 'boundaryWithTarget') {
    let differentTabsBasedOnLevel = await getBoundaryOnWhichWeSplit(request);
    differentTabsBasedOnLevel = getLocalizedName(`${request?.body?.ResourceDetails?.hierarchyType}_${differentTabsBasedOnLevel}`.toUpperCase(), localizationMap);
    logger.info("target sheet format validation started");
    await immediateValidationForTargetSheet(request, dataFromSheet, differentTabsBasedOnLevel, localizationMap);
    logger.info("target sheet format validation completed and starts with data validation");
    validateTargetSheetData(dataFromSheet, request, createAndSearchConfig?.boundaryValidation, differentTabsBasedOnLevel, localizationMap);
  }

  else {
    let schema: any;
    if (type == "facility" || type == "user") {
      const mdmsResponse = await callMdmsTypeSchema(request, tenantId, type);
      schema = mdmsResponse
    }
    const translatedSchema = await translateSchema(schema, localizationMap);
    await validateSheetData(dataFromSheet, request, translatedSchema, createAndSearchConfig?.boundaryValidation, localizationMap)
    processValidateAfterSchema(dataFromSheet, request, createAndSearchConfig, localizationMap)
  }
}

function convertUserRoles(employees: any[], request: any) {
  for (const employee of employees) {
    if (employee?.user?.roles) {
      var newRoles: any[] = []
      const rolesArray = employee.user.roles.split(',').map((role: any) => role.trim());
      for (const role of rolesArray) {
        const code = role.toUpperCase().split(' ').join('_')
        newRoles.push({ name: role, code: code, tenantId: request?.body?.ResourceDetails?.tenantId })
      }
      employee.user.roles = newRoles
    }
  }
}

function generateHash(input: string): string {
  const prime = 31; // Prime number
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = (hash * prime + input.charCodeAt(i)) % 100000; // Limit hash to 5 digits
  }
  return hash.toString().padStart(6, '0');
}

function generateUserPassword() {
  // Function to generate a random lowercase letter
  function getRandomLowercaseLetter() {
    const letters = 'abcdefghijklmnopqrstuvwxyz';
    return letters.charAt(Math.floor(Math.random() * letters.length));
  }

  // Function to generate a random uppercase letter
  function getRandomUppercaseLetter() {
    const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    return letters.charAt(Math.floor(Math.random() * letters.length));
  }

  // Generate a random 4-letter sequence where the second letter is uppercase
  function generate4LetterSequence() {
    const firstLetter = getRandomLowercaseLetter();
    const secondLetter = getRandomUppercaseLetter();
    const thirdLetter = getRandomLowercaseLetter();
    const fourthLetter = getRandomLowercaseLetter();
    return firstLetter + secondLetter + thirdLetter + fourthLetter;
  }

  // Generate a random 3-digit number
  function getRandom3DigitNumber() {
    return Math.floor(100 + Math.random() * 900); // Ensures the number is 3 digits
  }

  // Combine parts to form the password
  const firstSequence = generate4LetterSequence();
  const randomNumber = getRandom3DigitNumber();

  return `${firstSequence}@${randomNumber}`;
}


function enrichUserNameAndPassword(employees: any[]) {
  const epochTime = Date.now();
  employees.forEach((employee) => {
    const { user, "!row#number!": rowNumber } = employee;
    const nameInitials = user.name.split(' ').map((name: any) => name.charAt(0)).join('');
    const generatedCode = `${nameInitials}${generateHash(`${epochTime}`)}${rowNumber}`;
    const generatedPassword = config?.user?.userPasswordAutoGenerate == "true" ? generateUserPassword() : config?.user?.userDefaultPassword
    user.userName = generatedCode;
    user.password = generatedPassword;
    employee.code = generatedCode
  });
}

async function enrichJurisdictions(employee: any, request: any) {
  employee.jurisdictions = [
    {
      tenantId: request?.body?.ResourceDetails?.tenantId,
      boundaryType: config.values.userMainBoundaryType,
      boundary: config.values.userMainBoundary,
      hierarchy: request?.body?.ResourceDetails?.hierarchyType,
      roles: employee?.user?.roles
    }
  ]
}

async function enrichEmployees(employees: any[], request: any) {
  convertUserRoles(employees, request)
  for (const employee of employees) {
    enrichUserNameAndPassword(employees)
    await enrichJurisdictions(employee, request)
    if (employee?.user) {
      employee.user.tenantId = request?.body?.ResourceDetails?.tenantId
      employee.user.dob = 0
    }
  }
}

function enrichDataToCreateForUser(dataToCreate: any[], responsePayload: any, request: any) {
  const createdEmployees = responsePayload?.Employees;
  // create an object which have keys as employee.code and values as employee.uuid  
  const employeeMap = createdEmployees.reduce((map: any, employee: any) => {
    map[employee.code] = employee.uuid;
    return map;
  }, {});
  for (const employee of dataToCreate) {
    if (!employee?.uuid && employeeMap[employee?.code]) {
      employee.uuid = employeeMap[employee?.code];
    }
  }
}

async function handeFacilityProcess(request: any, createAndSearchConfig: any, params: any, activities: any[], newRequestBody: any) {
  for (const facility of newRequestBody.Facilities) {
    facility.address = {}
  }
  var responsePayload = await httpRequest(createAndSearchConfig?.createBulkDetails?.url, newRequestBody, params, "post", undefined, undefined, true);
  var activity = await generateActivityMessage(request?.body?.ResourceDetails?.tenantId, request.body, newRequestBody, responsePayload, "facility", createAndSearchConfig?.createBulkDetails?.url, responsePayload?.statusCode)
  logger.info(`Activity : ${createAndSearchConfig?.createBulkDetails?.url} status:  ${responsePayload?.statusCode}`);
  activities.push(activity);
}


async function handleUserProcess(request: any, createAndSearchConfig: any, params: any, dataToCreate: any[], activities: any[], newRequestBody: any) {
  if (config.values.notCreateUserIfAlreadyThere) {
    var Employees: any[] = []
    if (request.body?.mobileNumberUuidsMapping) {
      for (const employee of newRequestBody.Employees) {
        if (request.body.mobileNumberUuidsMapping[employee?.user?.mobileNumber]) {
          logger.info(`User with mobile number ${employee?.user?.mobileNumber} already exist`);
        }
        else {
          Employees.push(employee)
        }
      }
    }
    newRequestBody.Employees = Employees
  }
  if (newRequestBody.Employees.length > 0) {
    var responsePayload = await httpRequest(createAndSearchConfig?.createBulkDetails?.url, newRequestBody, params, "post", undefined, undefined, true, true);
    if (responsePayload?.Employees && responsePayload?.Employees?.length > 0) {
      enrichDataToCreateForUser(dataToCreate, responsePayload, request);
    }
    else {
      throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Some internal server error occured during user creation.");
    }
    var activity = await generateActivityMessage(request?.body?.ResourceDetails?.tenantId, request.body, newRequestBody, responsePayload, "user", createAndSearchConfig?.createBulkDetails?.url, responsePayload?.statusCode)
    logger.info(`Activity : ${createAndSearchConfig?.createBulkDetails?.url} status:  ${responsePayload?.statusCode}`);
    activities.push(activity);
  }
}

async function enrichAlreadyExsistingUser(request: any) {
  if (request.body.ResourceDetails.type == "user" && request?.body?.mobileNumberUuidsMapping) {
    for (const employee of request.body.dataToCreate) {
      if (request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber]) {
        employee.uuid = request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber].userUuid;
        employee.code = request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber].code;
        employee.user.userName = request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber].code;
        employee.user.password = request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber].password;
        employee.user.userServiceUuid = request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber].userServiceUuid;
      }
    }
  }
}

async function performAndSaveResourceActivity(request: any, createAndSearchConfig: any, params: any, type: any, localizationMap?: { [key: string]: string }) {
  logger.info(type + " create data  ");
  if (createAndSearchConfig?.createBulkDetails?.limit) {
    const limit = createAndSearchConfig?.createBulkDetails?.limit;
    const dataToCreate = request?.body?.dataToCreate;
    const chunks = Math.ceil(dataToCreate.length / limit); // Calculate number of chunks
    var creationTime = Date.now();
    var activities: any[] = [];
    for (let i = 0; i < chunks; i++) {
      const start = i * limit;
      const end = (i + 1) * limit;
      const chunkData = dataToCreate.slice(start, end); // Get a chunk of data
      const newRequestBody: any = {
        RequestInfo: request?.body?.RequestInfo,
      }
      _.set(newRequestBody, createAndSearchConfig?.createBulkDetails?.createPath, chunkData);
      creationTime = Date.now();
      if (type == "facility" || type == "facilityMicroplan") {
        await handeFacilityProcess(request, createAndSearchConfig, params, activities, newRequestBody);
      }
      else if (type == "user") {
        await handleUserProcess(request, createAndSearchConfig, params, chunkData, activities, newRequestBody);
      }
    }
    await enrichAlreadyExsistingUser(request);
    logger.info(`Waiting for 10 seconds`);
    await new Promise(resolve => setTimeout(resolve, 10000));
    await confirmCreation(createAndSearchConfig, request, dataToCreate, creationTime, activities);
  }
  await generateProcessedFileAndPersist(request, localizationMap);
}

/**
 * Processes generic requests such as create or validate.
 * @param request The HTTP request object.
 */
async function processGenericRequest(request: any, localizationMap?: { [key: string]: string }) {
  // Process generic requests
  if (request?.body?.ResourceDetails?.action == "create") {
    await processCreate(request, localizationMap)
  }
  else {
    await processValidate(request, localizationMap)
  }
}

async function handleResouceDetailsError(request: any, error: any) {
  var stringifiedError: any;
  if (error?.description || error?.message) {
    stringifiedError = JSON.stringify({
      status: error.status || '',
      code: error.code || '',
      description: error.description || '',
      message: error.message || ''
    });
  }
  else {
    if (typeof error == "object")
      stringifiedError = JSON.stringify(error);
    else {
      stringifiedError = error
    }
  }

  logger.error("Error while processing after validation : " + error)
  if (request?.body?.ResourceDetails) {
    request.body.ResourceDetails.status = "failed";
    request.body.ResourceDetails.additionalDetails = {
      ...request?.body?.ResourceDetails?.additionalDetails,
      error: stringifiedError
    };
    const persistMessage: any = { ResourceDetails: request.body.ResourceDetails }
    if (request?.body?.ResourceDetails?.action == "create") {
      persistMessage.ResourceDetails.additionalDetails = { error: stringifiedError }
    }
    await produceModifiedMessages(persistMessage, config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC);
  }
  if (request?.body?.Activities && Array.isArray(request?.body?.Activities) && request?.body?.Activities.length > 0) {
    logger.info("Waiting for 2 seconds");
    await new Promise(resolve => setTimeout(resolve, 2000));
    await produceModifiedMessages(request?.body, config?.kafka?.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC);
  }
}

async function persistCreationProcess(request: any, status: any) {
  if (request?.body?.ResourceDetails?.type == "facility") {
    await persistTrack(request?.body?.ResourceDetails?.campaignId, processTrackTypes.facilityCreation, status);
  }
  else if (request?.body?.ResourceDetails?.type == "user") {
    await persistTrack(request?.body?.ResourceDetails?.campaignId, processTrackTypes.staffCreation, status);
  }
}

async function processAfterValidation(dataFromSheet: any, createAndSearchConfig: any, request: any, localizationMap?: { [key: string]: string }) {
  await persistCreationProcess(request, processTrackStatuses.inprogress)
  try {
    const typeData = await convertToTypeData(request, dataFromSheet, createAndSearchConfig, request.body, localizationMap)
    request.body.dataToCreate = typeData.createData;
    request.body.dataToSearch = typeData.searchData;
    await processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)
    await reorderBoundariesOfDataAndValidate(request, localizationMap)
    if (createAndSearchConfig?.createBulkDetails && request.body.ResourceDetails.status != "invalid") {
      _.set(request.body, createAndSearchConfig?.createBulkDetails?.createPath, request?.body?.dataToCreate);
      const params: any = getParamsViaElements(createAndSearchConfig?.createBulkDetails?.createElements, request);
      changeBodyViaElements(createAndSearchConfig?.createBulkDetails?.createElements, request)
      await performAndSaveResourceActivity(request, createAndSearchConfig, params, request.body.ResourceDetails.type, localizationMap);
    }
    else if (request.body.ResourceDetails.status == "invalid") {
      await generateProcessedFileAndPersist(request, localizationMap);
    }
  } catch (error: any) {
    console.log(error)
    await persistCreationProcess(request, processTrackStatuses.failed)
    await handleResouceDetailsError(request, error)
  }
  await persistCreationProcess(request, processTrackStatuses.completed)
}

/**
 * Processes the creation of resources.
 * @param request The HTTP request object.
 */
async function processCreate(request: any, localizationMap?: any) {
  // Process creation of resources
  const type: string = request.body.ResourceDetails.type;
  const tenantId = request?.body?.ResourceDetails?.tenantId;
  if (type == "boundary") {
    boundaryBulkUpload(request, localizationMap);
  }
  else {
    // console.log(`Source is MICROPLAN -->`, source);
    let createAndSearchConfig: any;
    createAndSearchConfig = createAndSearch[type];
    const responseFromCampaignSearch = await getCampaignSearchResponse(request);
    const campaignType = responseFromCampaignSearch?.CampaignDetails[0]?.projectType;
    if (checkIfSourceIsMicroplan(request?.body?.ResourceDetails)) {
      logger.info(`Data create Source is MICROPLAN`);
      if (createAndSearchConfig?.parseArrayConfig?.parseLogic) {
        createAndSearchConfig.parseArrayConfig.parseLogic = createAndSearchConfig.parseArrayConfig.parseLogic.map(
          (item: any) => {
            if (item.sheetColumn === "E") {
              item.sheetColumnName += `_${campaignType}`;
            }
            return item;
          }
        );
      }
    }

    const dataFromSheet = await getDataFromSheet(request, request?.body?.ResourceDetails?.fileStoreId, request?.body?.ResourceDetails?.tenantId, createAndSearchConfig, undefined, localizationMap)
    let schema: any;

    if (type == "facility") {
      logger.info("Fetching schema to validate the created data for type: " + type);
      const mdmsResponse = await callMdmsTypeSchema(request, tenantId, type);
      schema = mdmsResponse
    }
    else if (type == "facilityMicroplan") {
      const mdmsResponse = await callMdmsTypeSchema(request, tenantId, "facility", "microplan");
      schema = mdmsResponse
      logger.info("Appending project type to capacity for microplan " + campaignType);
      schema = await appendProjectTypeToCapacity(schema, campaignType);
    }
    else if (type == "user") {
      logger.info("Fetching schema to validate the created data for type: " + type);
      const mdmsResponse = await callMdmsTypeSchema(request, tenantId, type);
      schema = mdmsResponse
    }
    logger.info("translating schema")
    const translatedSchema = await translateSchema(schema, localizationMap);
    await validateSheetData(dataFromSheet, request, translatedSchema, createAndSearchConfig?.boundaryValidation, localizationMap);
    logger.info("validation done sucessfully")
    processAfterValidation(dataFromSheet, createAndSearchConfig, request, localizationMap)
  }
}

/**
 * Creates resources for a project campaign.
 * @param request The HTTP request object.
 */
async function createProjectCampaignResourcData(request: any) {
  await persistTrack(request.body.CampaignDetails.id, processTrackTypes.triggerResourceCreation, processTrackStatuses.inprogress);
  try {
    // Create resources for a project campaign
    if (request?.body?.CampaignDetails?.action == "create" && request?.body?.CampaignDetails?.resources) {
      for (const resource of request?.body?.CampaignDetails?.resources) {
        if (resource.type != "boundaryWithTarget") {
          const resourceDetails = {
            type: resource.type,
            fileStoreId: resource.filestoreId,
            tenantId: request?.body?.CampaignDetails?.tenantId,
            action: "create",
            hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
            additionalDetails: {},
            campaignId: request?.body?.CampaignDetails?.id
          };
          logger.info(`Creating the resources for type ${resource.type}`)
          logger.debug("resourceDetails " + getFormattedStringForDebug(resourceDetails))
          const createRequestBody = {
            RequestInfo: request.body.RequestInfo,
            ResourceDetails: resourceDetails
          }
          const req = replicateRequest(request, createRequestBody)
          const res: any = await createDataService(req)
          if (res?.id) {
            resource.createResourceId = res?.id
          }
        }
      }
    }
  } catch (error: any) {
    console.log(error)
    await persistTrack(request?.body?.CampaignDetails?.id, processTrackTypes.triggerResourceCreation, processTrackStatuses.failed, { error: String((error?.message + (error?.description ? ` : ${error?.description}` : '')) || error) });
    throw new Error(error)
  }
  await persistTrack(request.body.CampaignDetails.id, processTrackTypes.triggerResourceCreation, processTrackStatuses.completed);
}

async function confirmProjectParentCreation(request: any, projectId: any) {
  const searchBody = {
    RequestInfo: request.body.RequestInfo,
    Projects: [
      {
        id: projectId,
        tenantId: request.body.CampaignDetails.tenantId
      }
    ]
  }
  const params = {
    tenantId: request.body.CampaignDetails.tenantId,
    offset: 0,
    limit: 5
  }
  var projectFound = false;
  var retry = 6;
  while (!projectFound && retry >= 0) {
    const response = await httpRequest(config.host.projectHost + config.paths.projectSearch, searchBody, params);
    if (response?.Project?.[0]) {
      projectFound = true;
    }
    else {
      logger.info("Project not found. Waiting for 1 seconds");
      retry = retry - 1
      logger.info(`Waiting for ${retry} for 1 more second`);
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  if (!projectFound) {
    throwError("PROJECT", 500, "PROJECT_CONFIRMATION_FAILED", "Project confirmation failed, for the project with id " + projectId);
  }
}

async function projectCreate(projectCreateBody: any, request: any) {
  logger.info("Project creation API started")
  logger.debug("Project creation body " + getFormattedStringForDebug(projectCreateBody))
  const projectCreateResponse = await httpRequest(config.host.projectHost + config.paths.projectCreate, projectCreateBody, undefined, undefined, undefined, undefined, undefined, true);
  logger.debug("Project creation response" + getFormattedStringForDebug(projectCreateResponse))
  if (projectCreateResponse?.Project[0]?.id) {
    logger.info("Project created successfully with name " + JSON.stringify(projectCreateResponse?.Project[0]?.name))
    logger.info(`for boundary type ${projectCreateResponse?.Project[0]?.address?.boundaryType} and code ${projectCreateResponse?.Project[0]?.address?.boundary}`)
    request.body.boundaryProjectMapping[projectCreateBody?.Projects?.[0]?.address?.boundary].projectId = projectCreateResponse?.Project[0]?.id
  }
  else {
    throwError("PROJECT", 500, "PROJECT_CREATION_FAILED", "Project creation failed, for the request: " + JSON.stringify(projectCreateBody));
  }
}

function generateHierarchyList(data: any[], parentChain: any = []) {
  let result: any[] = [];

  // Iterate over each boundary in the current level
  for (let boundary of data) {
    let currentChain = [...parentChain, boundary.code];

    // Add the current chain to the result
    result.push(currentChain.join(','));

    // If there are children, recursively call the function
    if (boundary.children && boundary.children.length > 0) {
      let childResults = generateHierarchyList(boundary.children, currentChain);
      result = result.concat(childResults);
    }
  }
  return result;

}

const getHierarchy = async (request: any, tenantId: string, hierarchyType: string) => {
  const url = `${config.host.boundaryHost}${config.paths.boundaryHierarchy}`;

  // Create request body
  const requestBody = {
    "RequestInfo": request?.body?.RequestInfo,
    "BoundaryTypeHierarchySearchCriteria": {
      "tenantId": tenantId,
      "limit": 5,
      "offset": 0,
      "hierarchyType": hierarchyType
    }
  };

  const response = await httpRequest(url, requestBody);
  const boundaryList = response?.BoundaryHierarchy?.[0].boundaryHierarchy;
  return generateHierarchy(boundaryList);
};

const getHeadersOfBoundarySheet = async (fileUrl: string, sheetName: string, getRow = false, localizationMap?: any) => {
  const localizedBoundarySheetName = getLocalizedName(sheetName, localizationMap);
  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedBoundarySheetName);

  const worksheet = workbook.getWorksheet(localizedBoundarySheetName);
  const columnsToValidate = worksheet.getRow(1).values.map((header: any) => header ? header.toString().trim() : undefined);

  // Filter out empty items and return the result
  return columnsToValidate.filter((header: any) => typeof header === 'string');
}


async function getCampaignSearchResponse(request: any) {
  try {
    logger.info(`searching for campaign details`);
    const requestInfo = { "RequestInfo": request?.body?.RequestInfo };
    const campaignDetails = { "CampaignDetails": { tenantId: request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId, "ids": [request?.query?.campaignId || request?.body?.ResourceDetails?.campaignId] } }
    const requestBody = { ...requestInfo, ...campaignDetails };
    const req: any = replicateRequest(request, requestBody)
    const projectTypeSearchResponse: any = await searchProjectTypeCampaignService(req);
    return projectTypeSearchResponse;
  } catch (error: any) {
    logger.error(`Error while searching for campaign details: ${error.message}`);
    throwError("COMMON", 400, "RESPONSE_NOT_FOUND_ERROR", error?.message)
  }
}

export {
  enrichCampaign,
  getAllFacilities,
  getFacilitiesViaIds,
  confirmCreation,
  getParamsViaElements,
  changeBodyViaElements,
  processGenericRequest,
  createProjectCampaignResourcData,
  processCreate,
  projectCreate,
  generateHierarchyList,
  getHierarchy,
  getHeadersOfBoundarySheet,
  handleResouceDetailsError,
  getCampaignSearchResponse,
  confirmProjectParentCreation
};
