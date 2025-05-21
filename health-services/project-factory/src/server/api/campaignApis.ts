import config from "../config";
import { v4 as uuidv4 } from "uuid";
import { httpRequest } from "../utils/request";
import { getFormattedStringForDebug, logger } from "../utils/logger";
import createAndSearch from "../config/createAndSearch";
import {
  getDataFromSheet,
  throwError,
  translateSchema,
  replicateRequest,
  getLocalizedMessagesHandler,
} from "../utils/genericUtils";
import {
  immediateValidationForTargetSheet,
  validateEmptyActive,
  validateMultiSelect,
  validateSheetData,
  validateTargetSheetData,
  validateViaSchemaSheetWise,
} from "../validators/campaignValidators";
import { getCampaignNumber } from "./genericApis";
import {
  boundaryBulkUpload,
  convertToTypeData,
  generateHierarchy,
  generateProcessedFileAndPersist,
  getBoundaryOnWhichWeSplit,
  getLocalizedName,
  reorderBoundariesOfDataAndValidate,
  checkIfSourceIsMicroplan,
  createIdRequests,
  createUniqueUserNameViaIdGen,
  boundaryGeometryManagement,
  getBoundaryCodeAndBoundaryTypeMapping,
  getSchema,
  validateUsernamesFormat,
} from "../utils/campaignUtils";
const _ = require("lodash");
import { produceModifiedMessages } from "../kafka/Producer";
import { createDataService } from "../service/dataManageService";
import { searchProjectTypeCampaignService } from "../service/campaignManageService";
import { getExcelWorkbookFromFileURL } from "../utils/excelUtils";
import {
  processTrackStatuses,
  processTrackTypes,
  resourceDataStatuses,
} from "../config/constants";
import { persistTrack } from "../utils/processTrackUtils";
import { checkAndGiveIfParentCampaignAvailable } from "../utils/onGoingCampaignUpdateUtils";
import { validateMicroplanFacility } from "../validators/microplanValidators";
import {
  createPlanFacilityForMicroplan,
  isMicroplanRequest
} from "../utils/microplanUtils";
import { getTransformedLocale } from "../utils/localisationUtils";
import { BoundaryModels } from "../models";
import { defaultRequestInfo, searchBoundaryRelationshipData, searchBoundaryRelationshipDefinition } from "./coreApis";

/**
 * Enriches the campaign data with unique IDs and generates campaign numbers.
 * @param requestBody The request body containing the campaign data.
 */
async function enrichCampaign(requestBody: any) {
  // Enrich campaign data with unique IDs and generate campaign numbers
  if (requestBody?.Campaign) {
    requestBody.Campaign.id = uuidv4();
    logger.info(`ENRICHMENT:: generated id for the campaign ${requestBody.Campaign.id}`);
    requestBody.Campaign.campaignNo = await getCampaignNumber(
      requestBody,
      config.values.idgen.format,
      config.values.idgen.idName,
      requestBody?.Campaign?.tenantId
    );
    logger.info(`ENRICHMENT:: generated sequence no for the campaign ${requestBody.Campaign.campaignNo}`);
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
      campaignDetails.id = uuidv4();
    }
  }
}

async function getAllFacilitiesInLoop(
  searchedFacilities: any[],
  facilitySearchParams: any,
  facilitySearchBody: any
) {
  const response = await httpRequest(
    config.host.facilityHost + config.paths.facilitySearch,
    facilitySearchBody,
    facilitySearchParams
  );

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
async function getAllFacilities(tenantId: string) {
  // Retrieve all facilities for the given tenant ID
  const facilitySearchBody = {
    RequestInfo: defaultRequestInfo,
    Facility: { isPermanent: true },
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId?.split(".")?.[0],
  };

  const facilityMap: Map<string, any> = new Map();
  let searchAgain = true;

  while (searchAgain) {
    const batch: any[] = [];
    searchAgain = await getAllFacilitiesInLoop(
      batch,
      facilitySearchParams,
      facilitySearchBody
    );
    for (const facility of batch) {
      const name = facility?.name?.trim();
      if (!name) continue;
      // Overwrite previous if same name found
      facilityMap.set(name, facility);
    }
    facilitySearchParams.offset += 50;
  }

  return Array.from(facilityMap.values());
}

/**
 * Retrieves facilities by their IDs.
 * @param tenantId The ID of the tenant.
 * @param ids An array of facility IDs.
 * @param requestBody The request body containing additional parameters.
 * @returns An array of facilities.
 */
async function getFacilitiesViaIds(
  tenantId: string,
  ids: any[],
  requestBody: any
) {
  // Retrieve facilities by their IDs
  const facilitySearchBody: any = {
    RequestInfo: requestBody?.RequestInfo,
    Facility: {},
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId?.split(".")?.[0],
  };

  const searchedFacilities: any[] = [];

  // Split ids into chunks of 50
  for (let i = 0; i < ids.length; i += 50) {
    const chunkIds = ids.slice(i, i + 50);
    facilitySearchBody.Facility.id = chunkIds;
    await getAllFacilitiesInLoop(
      searchedFacilities,
      facilitySearchParams,
      facilitySearchBody
    );
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
      } else if (element?.getValueViaPath) {
        _.set(
          params,
          element?.keyPath,
          _.get(request.body, element?.getValueViaPath)
        );
      }
    }
  }
  return params;
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
      } else if (element?.getValueViaPath) {
        _.set(
          requestBody,
          element?.keyPath,
          _.get(requestBody, element?.getValueViaPath)
        );
      } else {
        _.set(requestBody, element?.keyPath, {});
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

function updateErrorsForUser(
  request: any,
  newCreatedData: any[],
  newSearchedData: any[],
  errors: any[],
  createAndSearchConfig: any,
  userNameAndPassword: any[]
) {
  const isSourceMicroplan =
    request?.body?.ResourceDetails?.additionalDetails?.source == "microplan";
  newCreatedData.forEach((createdElement: any) => {
    let foundMatch = false;
    for (const searchedElement of newSearchedData) {
      if (searchedElement?.code === createdElement?.code) {
        foundMatch = true;
        newSearchedData.splice(newSearchedData.indexOf(searchedElement), 1);
        errors.push({
          status: "CREATED",
          rowNumber: createdElement["!row#number!"],
          isUniqueIdentifier: isSourceMicroplan ? false : true,
          uniqueIdentifier: _.get(
            searchedElement,
            createAndSearchConfig.uniqueIdentifier,
            ""
          ),
          errorDetails: "",
        });
        userNameAndPassword.push({
          userName: searchedElement?.user?.userName,
          password: createdElement?.user?.password,
          rowNumber: createdElement["!row#number!"],
        });
        break;
      }
    }
    if (!foundMatch) {
      errors.push({
        status: "NOT_CREATED",
        rowNumber: createdElement["!row#number!"],
        errorDetails: `Can't confirm creation of this data`,
      });
      logger.info(
        "Can't confirm creation of this data of row number : " +
        createdElement["!row#number!"]
      );
    }
  });
}

function matchCreatedAndSearchedData(
  createdData: any[],
  searchedData: any[],
  request: any,
  createAndSearchConfig: any,
  activities: any
) {
  const newCreatedData = JSON.parse(JSON.stringify(createdData));
  const newSearchedData = JSON.parse(JSON.stringify(searchedData));
  const uid = createAndSearchConfig.uniqueIdentifier;
  newCreatedData.forEach((element: any) => {
    delete element[uid];
  });
  var errors: any[] = [];
  if (request?.body?.ResourceDetails?.type == "user") {
    var userNameAndPassword: any = [];
    updateErrorsForUser(
      request,
      newCreatedData,
      newSearchedData,
      errors,
      createAndSearchConfig,
      userNameAndPassword
    );
    request.body.userNameAndPassword = userNameAndPassword;
  }
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails
    ? [...request?.body?.sheetErrorDetails, ...errors]
    : errors;
  request.body.Activities = activities;
}

async function getUuidsError(
  request: any,
  response: any,
  mobileNumberRowNumberMapping: any
) {
  var errors: any[] = [];
  var count = 0;
  request.body.mobileNumberUuidsMapping = request.body.mobileNumberUuidsMapping
    ? request.body.mobileNumberUuidsMapping
    : {};
  for (const user of response.Individual) {
    if (!user?.userUuid) {
      logger.info(
        `User with mobileNumber ${user?.mobileNumber} doesn't have userUuid`
      );
      errors.push({
        status: "INVALID",
        rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber],
        errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have userUuid`,
      });
      count++;
    } else if (!user?.userDetails?.username) {
      logger.info(
        `User with mobileNumber ${user?.mobileNumber} doesn't have username`
      );
      errors.push({
        status: "INVALID",
        rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber],
        errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have username`,
      });
      count++;
    } else if (!user?.userUuid) {
      logger.info(
        `User with mobileNumber ${user?.mobileNumber} doesn't have userServiceUuid`
      );
      errors.push({
        status: "INVALID",
        rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber],
        errorDetails: `User with mobileNumber ${user?.mobileNumber} doesn't have userServiceUuid`,
      });
      count++;
    } else {
      request.body.mobileNumberUuidsMapping[user?.mobileNumber] = {
        userUuid: user?.id,
        code: user?.userDetails?.username,
        rowNumber: mobileNumberRowNumberMapping[user?.mobileNumber],
        password: user?.userDetails?.password,
        userServiceUuid: user?.userUuid,
      };
    }
  }
  if (count > 0) {
    request.body.sheetErrorDetails = request?.body?.sheetErrorDetails
      ? [...request?.body?.sheetErrorDetails, ...errors]
      : errors;
  }
}

const searchBatchRequest = async (
  request: any,
  batch: any[],
  mobileNumberRowNumberMapping: any
) => {
  const searchBody = {
    RequestInfo: request?.body?.RequestInfo,
    Individual: {
      mobileNumber: batch,
    },
  };
  const params = {
    limit: 55,
    offset: 0,
    tenantId: request?.body?.ResourceDetails?.tenantId,
    includeDeleted: true,
  };
  logger.info("Individual search to validate the mobile no initiated");
  const response = await httpRequest(
    config.host.healthIndividualHost + config.paths.healthIndividualSearch,
    searchBody,
    params,
    undefined,
    undefined,
    undefined,
    undefined,
    true
  );

  if (!response) {
    throwError(
      "COMMON",
      400,
      "INTERNAL_SERVER_ERROR",
      "Error occurred during user search while validating mobile number."
    );
  }
  if (config.values.notCreateUserIfAlreadyThere) {
    await getUuidsError(request, response, mobileNumberRowNumberMapping);
  }

  if (response?.Individual?.length > 0) {
    return response.Individual.map((item: any) => item?.mobileNumber);
  }
  return [];
};

async function getUserWithMobileNumbers(
  request: any,
  mobileNumbers: any[],
  mobileNumberRowNumberMapping: any
) {
  logger.debug(
    "mobileNumbers to search: " + getFormattedStringForDebug(mobileNumbers)
  );
  const BATCH_SIZE = 50;
  let allResults: any[] = [];

  // Create an array of batch promises
  const batchPromises = [];
  for (let i = 0; i < mobileNumbers.length; i += BATCH_SIZE) {
    const batch = mobileNumbers.slice(i, i + BATCH_SIZE);
    batchPromises.push(
      searchBatchRequest(request, batch, mobileNumberRowNumberMapping)
    );
  }

  // Wait for all batch requests to complete
  const batchResults = await Promise.all(batchPromises);

  // Aggregate all results
  for (const result of batchResults) {
    allResults = allResults.concat(result);
  }
  // Convert the results array to a Set to eliminate duplicates
  const resultSet = new Set(allResults);
  logger.info(`Already Existing mobile numbers : ${Array.from(resultSet).join(",")}`);
  return resultSet;
}

async function matchUserValidation(createdData: any[], request: any) {
  var mobileNumbercount = 0;
  var userNameCount = 0;
  const errors = [];
  const mobileNumbers = createdData
    .filter((item) => item?.user?.mobileNumber)
    .map((item) => item?.user?.mobileNumber);
  const mobileNumberRowNumberMapping = createdData.reduce((acc, curr) => {
    acc[curr.user.mobileNumber] = curr["!row#number!"];
    return acc;
  }, {});
  // const userNames = createdData
  // .filter((item) => item?.user?.code)
  // .map((item) => item?.user?.code);

  const userNameRowNumberMapping = createdData.reduce((acc, curr) => {
    if (curr?.user?.userName) {
      acc[curr.user.userName] = curr["!row#number!"];
    }
    return acc;
  }, {});
  const userNameResponses = await getEmployeesBasedOnUserName(createdData, request);

  for (const key in userNameRowNumberMapping) {
    if (
      userNameResponses.has(key)
    ) {
      if (Array.isArray(userNameRowNumberMapping[key])) {
        for (const row of userNameRowNumberMapping[key]) {
          errors.push({
            status: "INVALID",
            rowNumber: row.row,
            sheetName: row.sheetName,
            errorDetails: `User with user name ${key} already exists`,
          });
        }
      } else {
        errors.push({
          status: "INVALID",
          rowNumber: userNameRowNumberMapping[key],
          errorDetails: `User with user name ${key} already exists`,
        });
      }
      userNameCount++;
    }
  }

  logger.debug(
    "mobileNumberRowNumberMapping : " +
    getFormattedStringForDebug(mobileNumberRowNumberMapping)
  );
  const mobileNumberResponse = await getUserWithMobileNumbers(
    request,
    mobileNumbers,
    mobileNumberRowNumberMapping
  );
  for (const key in mobileNumberRowNumberMapping) {
    if (
      mobileNumberResponse.has(key) &&
      !config.values.notCreateUserIfAlreadyThere
    ) {
      if (Array.isArray(mobileNumberRowNumberMapping[key])) {
        for (const row of mobileNumberRowNumberMapping[key]) {
          errors.push({
            status: "INVALID",
            rowNumber: row.row,
            sheetName: row.sheetName,
            errorDetails: `User with contact number ${key} already exists`,
          });
        }
      } else {
        errors.push({
          status: "INVALID",
          rowNumber: mobileNumberRowNumberMapping[key],
          errorDetails: `User with contact number ${key} already exists`,
        });
      }
      mobileNumbercount++;
    }
  }
  if (mobileNumbercount || userNameCount) {
    request.body.ResourceDetails.status = "invalid";
  }
  logger.info("Invalid resources count : " + mobileNumbercount + userNameCount);
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails
    ? [...request?.body?.sheetErrorDetails, ...errors]
    : errors;
}
function matchViaUserIdAndCreationTime(
  createdData: any[],
  searchedData: any[],
  request: any,
  creationTime: any,
  createAndSearchConfig: any,
  activities: any
) {
  var matchingSearchData = [];
  const userUuid = request?.body?.RequestInfo?.userInfo?.uuid;
  var count = 0;
  if (request?.body?.ResourceDetails?.type != "user") {
    for (const data of searchedData) {
      if (
        data?.auditDetails?.createdBy == userUuid &&
        data?.auditDetails?.createdTime >= creationTime
      ) {
        matchingSearchData.push(data);
        count++;
      }
    }
  } else {
    count = searchedData.length;
    matchingSearchData = searchedData;
  }
  if (count < createdData.length) {
    request.body.ResourceDetails.status = "PERSISTER_ERROR";
  }
  matchCreatedAndSearchedData(
    createdData,
    matchingSearchData,
    request,
    createAndSearchConfig,
    activities
  );
  logger.info("New created resources count : " + count);
}

async function processSearch(
  createAndSearchConfig: any,
  request: any,
  params: any
) {
  setSearchLimits(createAndSearchConfig, request, params);
  const arraysToMatch = await performSearch(
    createAndSearchConfig,
    request,
    params
  );
  return arraysToMatch;
}

function setSearchLimits(
  createAndSearchConfig: any,
  request: any,
  params: any
) {
  setLimitOrOffset(
    createAndSearchConfig?.searchDetails?.searchLimit,
    params,
    request.body
  );
  setLimitOrOffset(
    createAndSearchConfig?.searchDetails?.searchOffset,
    params,
    request.body
  );
}

function setLimitOrOffset(
  limitOrOffsetConfig: any,
  params: any,
  requestBody: any
) {
  if (limitOrOffsetConfig) {
    if (limitOrOffsetConfig?.isInParams) {
      _.set(
        params,
        limitOrOffsetConfig?.keyPath,
        parseInt(limitOrOffsetConfig?.value)
      );
    }
    if (limitOrOffsetConfig?.isInBody) {
      _.set(
        requestBody,
        limitOrOffsetConfig?.keyPath,
        parseInt(limitOrOffsetConfig?.value)
      );
    }
  }
}

async function performSearch(
  createAndSearchConfig: any,
  request: any,
  params: any
) {
  const arraysToMatch: any[] = [];
  let searchAgain = true;
  while (searchAgain) {
    const searcRequestBody = {
      RequestInfo: request?.body?.RequestInfo,
    };
    changeBodyViaElements(
      createAndSearchConfig?.searchDetails?.searchElements,
      searcRequestBody
    );
    const response = await httpRequest(
      createAndSearchConfig?.searchDetails?.url,
      searcRequestBody,
      params
    );
    const resultArray = _.get(
      response,
      createAndSearchConfig?.searchDetails?.searchPath
    );
    if (resultArray && Array.isArray(resultArray)) {
      arraysToMatch.push(...resultArray);
      if (
        resultArray.length <
        parseInt(createAndSearchConfig?.searchDetails?.searchLimit?.value)
      ) {
        searchAgain = false;
      }
    } else {
      searchAgain = false;
    }
    updateOffset(createAndSearchConfig, params, request.body);
  }
  return arraysToMatch;
}

function updateOffset(
  createAndSearchConfig: any,
  params: any,
  requestBody: any
) {
  const offsetConfig = createAndSearchConfig?.searchDetails?.searchOffset;
  const limit = createAndSearchConfig?.searchDetails?.searchLimit?.value;
  if (offsetConfig) {
    if (offsetConfig?.isInParams) {
      _.set(
        params,
        offsetConfig?.keyPath,
        parseInt(_.get(params, offsetConfig?.keyPath) + parseInt(limit))
      );
    }
    if (offsetConfig?.isInBody) {
      _.set(
        requestBody,
        offsetConfig?.keyPath,
        parseInt(_.get(requestBody, offsetConfig?.keyPath) + parseInt(limit))
      );
    }
  }
}

async function processSearchAndValidation(request: any) {
  // if (request?.body?.dataToSearch?.length > 0) {
  //   const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
  //   changeBodyViaElements(createAndSearchConfig?.searchDetails?.searchElements, request)
  //   changeBodyViaSearchFromSheet(createAndSearchConfig?.requiresToSearchFromSheet, request, dataFromSheet)
  //   const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
  //   matchData(request, request.body.dataToSearch, arraysToMatch, createAndSearchConfig)
  // }
  if (request?.body?.ResourceDetails?.type == "user") {
    await enrichEmployees(request?.body?.dataToCreate, request);
    await matchUserValidation(request.body.dataToCreate, request);
  }
}

async function getEmployeesBasedOnUuids(dataToCreate: any[], request: any) {
  const searchBody = {
    RequestInfo: request?.body?.RequestInfo,
  };

  const tenantId = request?.body?.ResourceDetails?.tenantId;
  const searchUrl = config.host.hrmsHost + config.paths.hrmsEmployeeSearch;
  logger.info(`Waiting for 10 seconds`);
  await new Promise((resolve) => setTimeout(resolve, 10000));
  const chunkSize = 50;
  let employeesSearched: any[] = [];

  for (let i = 0; i < dataToCreate.length; i += chunkSize) {
    const chunk = dataToCreate.slice(i, i + chunkSize);
    const uuids = chunk.map((data: any) => data.uuid).join(",");

    const params = {
      tenantId: tenantId,
      uuids: uuids,
      limit: 51,
      offset: 0,
    };

    try {
      const response = await httpRequest(
        searchUrl,
        searchBody,
        params,
        undefined,
        undefined,
        undefined,
        undefined,
        true
      );
      if (response && response.Employees) {
        employeesSearched = employeesSearched.concat(response.Employees);
      } else {
        throw new Error("Unable to fetch employees based on UUIDs");
      }
    } catch (error: any) {
      console.log(error);
      throwError(
        "COMMON",
        500,
        "INTERNAL_SERVER_ERROR",
        error.message ||
        "Some internal error occurred while searching employees"
      );
    }
  }

  return employeesSearched;
}

async function getEmployeesBasedOnUserName(dataToCreate: any[], request: any) {
  const searchBody = {
    RequestInfo: request?.body?.RequestInfo,
  };

  // const tenantId = request?.body?.ResourceDetails?.tenantId;
  const searchUrl = config.host.hrmsHost + config.paths.hrmsEmployeeSearch;
  logger.info(`Waiting for 10 seconds`);
  // await new Promise((resolve) => setTimeout(resolve, 10000));
  const chunkSize = 50;
  let foundUsernames = new Set<string>(); // ✅ Initialize resultSet properly

  for (let i = 0; i < dataToCreate.length; i += chunkSize) {
    const chunk = dataToCreate.slice(i, i + chunkSize);
    const userNames = chunk.map((data: any) => data?.code).filter(Boolean); // ✅ Now an array, not a string


    const params = {
      tenantId: 'mz',
      limit: 51,
      offset: 0,
      codes: userNames.join(","), // ✅ Convert array to comma-separated string
    };

    try {
      const response = await httpRequest(
        searchUrl,
        searchBody,
        params,
        undefined,
        undefined,
        undefined,
        undefined,
        true
      );
      if (response?.Employees?.length) {
        response.Employees.forEach((emp: any) => {
          if (emp?.code) foundUsernames.add(emp.code); // ✅ Add only valid codes
        });
      }
    } catch (error: any) {
      console.log(error);
      throwError(
        "COMMON",
        500,
        "INTERNAL_SERVER_ERROR",
        error.message ||
        "Some internal error occurred while searching employees"
      );
    }
  }

  return foundUsernames;
}

// Confirms the creation of resources by matching created and searched data.
async function confirmCreation(
  createAndSearchConfig: any,
  request: any,
  dataToCreate: any[],
  creationTime: any,
  activities: any
) {
  // Confirm creation of resources by matching data  // wait for 5 seconds
  if (request?.body?.ResourceDetails?.type != "user") {
    const params: any = getParamsViaElements(
      createAndSearchConfig?.searchDetails?.searchElements,
      request
    );
    const arraysToMatch = await processSearch(
      createAndSearchConfig,
      request,
      params
    );
    matchViaUserIdAndCreationTime(
      dataToCreate,
      arraysToMatch,
      request,
      creationTime,
      createAndSearchConfig,
      activities
    );
  } else {
    const arraysToMatch = await getEmployeesBasedOnUuids(dataToCreate, request);
    matchViaUserIdAndCreationTime(
      dataToCreate,
      arraysToMatch,
      request,
      creationTime,
      createAndSearchConfig,
      activities
    );
  }
}

async function processValidateAfterSchema(
  dataFromSheet: any,
  request: any,
  createAndSearchConfig: any,
  properties: any,
  localizationMap?: { [key: string]: string }
) {
  try {
    validateEmptyActive(dataFromSheet, request?.body?.ResourceDetails?.type, localizationMap);
    const errorsRelatedToUserName :any = validateUsernamesFormat(dataFromSheet,localizationMap)
    const errorsRelatedToMultiSelect:any = validateMultiSelect(dataFromSheet, properties, localizationMap);
    const allErrors = [...errorsRelatedToUserName, ...errorsRelatedToMultiSelect];
    request.body.sheetErrorDetails = request?.body?.sheetErrorDetails
      ? [...request?.body?.sheetErrorDetails, ...allErrors]
      : allErrors;
    if(request?.body?.sheetErrorDetails?.length){
      request.body.ResourceDetails.status = resourceDataStatuses.invalid
    }
    if (
      request?.body?.ResourceDetails?.additionalDetails?.source ==
      "microplan" &&
      request?.body?.ResourceDetails?.type == "facility"
    ) {
      validateMicroplanFacility(request, dataFromSheet, localizationMap);
    }
    const typeData = await convertToTypeData(
      request,
      dataFromSheet,
      createAndSearchConfig,
      request.body,
      localizationMap
    );
    request.body.dataToSearch = typeData.searchData;
    request.body.dataToCreate = typeData.createData;
    await processSearchAndValidation(request);
    await reorderBoundariesOfDataAndValidate(request, localizationMap);
    await generateProcessedFileAndPersist(request, localizationMap);
  } catch (error) {
    console.log(error);
    await handleResouceDetailsError(request, error);
  }
}

export async function processValidateAfterSchemaSheetWise(
  request: any,
  createAndSearchConfig: any,
  localizationMap?: { [key: string]: string }
) {
  if (
    request?.body?.ResourceDetails?.additionalDetails?.source == "microplan" &&
    request.body.ResourceDetails.type == "user"
  ) {
    await generateProcessedFileAndPersist(request, localizationMap);
  }
}

async function processSheetWise(
  forCreate: any,
  dataFromSheet: any,
  request: any,
  createAndSearchConfig: any,
  translatedSchema: any,
  localizationMap?: { [key: string]: string }
) {
  try {
    const errorMap: any = await validateViaSchemaSheetWise(
      dataFromSheet,
      translatedSchema,
      request,
      localizationMap
    );
    enrichErrorIfSheetInvalid(request, errorMap);
    await processSearchAndValidation(request);
    if (request?.body?.sheetErrorDetails?.length > 0) {
      request.body.ResourceDetails.status = resourceDataStatuses.invalid;
      await generateProcessedFileAndPersist(request, localizationMap);
    } else {
      if (forCreate) {
        await processAfterValidation(
          dataFromSheet,
          createAndSearchConfig,
          request,
          localizationMap
        );
      } else {
        await processValidateAfterSchemaSheetWise(
          request,
          createAndSearchConfig,
          localizationMap
        );
      }
    }
  } catch (error) {
    console.log(error);
    await handleResouceDetailsError(request, error);
  }
}

function enrichErrorIfSheetInvalid(request: any, errorMap: any) {
  if (Object.keys(errorMap).length > 0) {
    var sheetErrorDetails = [];
    for (const sheetName of Object.keys(errorMap)) {
      const errorData = errorMap[sheetName];
      for (const row of Object.keys(errorData)) {
        if (errorData[row].length > 0) {
          const errorDetails = errorData[row].join(", ");
          sheetErrorDetails.push({
            status: "INVALID",
            sheetName: sheetName,
            rowNumber: row,
            errorDetails: errorDetails,
          });
        }
      }
    }
    request.body.sheetErrorDetails = request?.body?.sheetErrorDetails
      ? [...request?.body?.sheetErrorDetails, ...sheetErrorDetails]
      : sheetErrorDetails;
  }
}

async function processValidate(
  request: any,
  localizationMap?: { [key: string]: string }
) {
  const type: string = request.body.ResourceDetails.type;
  const createAndSearchConfig = createAndSearch[type];
  const dataFromSheet: any = await getDataFromSheet(
    request,
    request?.body?.ResourceDetails?.fileStoreId,
    request?.body?.ResourceDetails?.tenantId,
    createAndSearchConfig,
    null,
    localizationMap
  );
  if (type == "boundaryWithTarget") {
    const hierarchyType = request?.body?.ResourceDetails?.hierarchyType;
    const hierarchyModule = `${config.localisation.boundaryPrefix
      }-${getTransformedLocale(hierarchyType)}`?.toLowerCase();
    const localizationMapForHierarchy = await getLocalizedMessagesHandler(
      request,
      request?.body?.ResourceDetails?.tenantId,
      hierarchyModule
    );
    localizationMap = {
      ...localizationMap,
      ...localizationMapForHierarchy,
    };
    let differentTabsBasedOnLevel = await getBoundaryOnWhichWeSplit(request?.body?.ResourceDetails?.campaignId, request?.body?.ResourceDetails?.tenantId);
    differentTabsBasedOnLevel = getLocalizedName(
      `${request?.body?.ResourceDetails?.hierarchyType}_${differentTabsBasedOnLevel}`.toUpperCase(),
      localizationMap
    );
    logger.info("target sheet format validation started");
    await immediateValidationForTargetSheet(
      request,
      dataFromSheet,
      differentTabsBasedOnLevel,
      localizationMap
    );
    logger.info(
      "target sheet format validation completed and starts with data validation"
    );
    validateTargetSheetData(
      dataFromSheet,
      request,
      createAndSearchConfig?.boundaryValidation,
      differentTabsBasedOnLevel,
      localizationMap
    );
  } else {
    const type = request?.body?.ResourceDetails?.type;
    const tenantId = request?.body?.ResourceDetails?.tenantId;
    const isUpdate = request?.body?.parentCampaignObject ? true : false;
    const isSourceMicroplan = checkIfSourceIsMicroplan(request?.body?.ResourceDetails);
    const schema : any = await getSchema(tenantId, isUpdate, type, isSourceMicroplan);
    const translatedSchema = await translateSchema(schema, localizationMap);
    if (Array.isArray(dataFromSheet)) {
      if (
        request?.body?.ResourceDetails?.additionalDetails?.source != "microplan"
      ) {
        await validateSheetData(
          dataFromSheet,
          request,
          translatedSchema,
          createAndSearchConfig?.boundaryValidation,
          localizationMap
        );
      }
      processValidateAfterSchema(
        dataFromSheet,
        request,
        createAndSearchConfig,
        schema?.properties,
        localizationMap
      );
    } else {
      if (dataFromSheet && Object.keys(dataFromSheet).length > 0) {
        processSheetWise(
          false,
          dataFromSheet,
          request,
          createAndSearchConfig,
          translatedSchema,
          localizationMap
        );
      } else {
        throwError(
          "COMMON",
          400,
          "VALIDATION_ERROR",
          "No data filled in the sheet."
        );
      }
    }
  }
}

function convertUserRoles(employees: any[], request: any) {
  for (const employee of employees) {
    if (employee?.user?.roles) {
      var newRoles: any[] = [];
      if (!Array.isArray(employee.user.roles)) {
        const rolesArray = employee.user.roles
          .split(",")
          .map((role: any) => role.trim());
        for (const role of rolesArray) {
          const code = role.toUpperCase().split(" ").join("_");
          newRoles.push({
            name: role,
            code: code,
            tenantId: request?.body?.ResourceDetails?.tenantId,
          });
        }
        employee.user.roles = newRoles;
      }
    }
  }
}

export function generateUserPassword() {
  // Function to generate a random lowercase letter
  function getRandomLowercaseLetter() {
    const letters = "abcdefghijklmnopqrstuvwxyz";
    return letters.charAt(Math.floor(Math.random() * letters.length));
  }

  // Function to generate a random uppercase letter
  function getRandomUppercaseLetter() {
    const letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
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

async function enrichJurisdictions(employee: any, request: any, boundaryCodeAndBoundaryTypeMapping : any) {
  const jurisdictionsArray =
    typeof employee?.jurisdictions === "string"
      ? employee.jurisdictions.split(",").map((jurisdiction: any) => jurisdiction.trim())
      : undefined;

  if (Array.isArray(jurisdictionsArray) && jurisdictionsArray.length > 0) {
    const jurisdictions = jurisdictionsArray.map((jurisdiction: any) => {
      return {
        tenantId: request?.body?.ResourceDetails?.tenantId,
        boundaryType: boundaryCodeAndBoundaryTypeMapping[jurisdiction],
        boundary: jurisdiction,
        hierarchy: request?.body?.ResourceDetails?.hierarchyType,
        roles: employee?.user?.roles,
      };
    })
    employee.jurisdictions = jurisdictions
  }
  else {
    employee.jurisdictions = [
      {
        tenantId: request?.body?.ResourceDetails?.tenantId,
        boundaryType: config.values.userMainBoundaryType,
        boundary: config.values.userMainBoundary,
        hierarchy: request?.body?.ResourceDetails?.hierarchyType,
        roles: employee?.user?.roles,
      },
    ];
  }
}

async function enrichEmployees(employees: any[], request: any) {
  const boundaryRelationshipResponse = await searchBoundaryRelationshipData(request?.body?.ResourceDetails?.tenantId, request?.body?.ResourceDetails?.hierarchyType, true);
  if (!boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary) {
    throw new Error("Boundary relationship search failed");
  }
  const boundaryCodeAndBoundaryTypeMapping = getBoundaryCodeAndBoundaryTypeMapping(boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary);
  convertUserRoles(employees, request);
  const idRequests = createIdRequests(employees);
  request.body.idRequests = idRequests;
  let result = await createUniqueUserNameViaIdGen(idRequests);
  var i = 0;
  for (const employee of employees) {
    const { user } = employee;
    var generatedPassword = user?.password;
    if(!user.password || user.password === "undefined" || user.password.trim() === "")
    {
       generatedPassword =
      config?.user?.userPasswordAutoGenerate == "true"
        ? generateUserPassword()
        : config?.user?.userDefaultPassword;
    }
    if (!user.userName || user.userName === "undefined" || user.userName.trim() === "") {
      // Assign an ID only if the userName is missing
      user.userName = result?.idResponses?.[i]?.id;
      i++; // Only increment when an ID is used
    }
    // user.userName = employee.user?.userName || result?.idResponses?.[i]?.id;
    user.password = generatedPassword;
    employee.code = user.userName;
    await enrichJurisdictions(employee, request, boundaryCodeAndBoundaryTypeMapping);
    if (employee?.user) {
      employee.user.tenantId = request?.body?.ResourceDetails?.tenantId;
      employee.user.dob = 0;
    }
  }
}

function enrichDataToCreateForUser(
  dataToCreate: any[],
  responsePayload: any,
  request: any
) {
  const createdEmployees = responsePayload?.Employees;
  // create an object which have keys as employee.code and values as employee.uuid
  const employeeMap = createdEmployees.reduce((map: any, employee: any) => {
    map[employee.code] = {
      uuid: employee?.uuid,
      userServiceUuid: employee?.user?.userServiceUuid,
    };
    return map;
  }, {});
  for (const employee of dataToCreate) {
    const mappedEmployee = employeeMap[employee?.code];
    if (mappedEmployee) {
      if (!employee?.userServiceUuid) {
        employee.userServiceUuid = mappedEmployee.userServiceUuid;
      }
      if (!employee?.uuid) {
        employee.uuid = mappedEmployee.uuid;
      }
    }
  }
}

async function handeFacilityProcess(
  createAndSearchConfig: any,
  params: any,
  newRequestBody: any
) {
  modifyFacilityAddress(newRequestBody);
  const response = await httpRequest(
    createAndSearchConfig?.createDetails?.url,
    newRequestBody,
    params,
    "post",
    undefined,
    undefined,
    true
  );
  return response?.Facility?.id;
}

function modifyFacilityAddress(newRequestBody: any) {
  if (newRequestBody?.Facility?.address?.locality?.code) {
    newRequestBody.Facility.address.locality.code = newRequestBody.Facility.address.locality.code?.split(",")[0]?.trim();
    newRequestBody.Facility.address.tenantId = newRequestBody.Facility.tenantId;
  }
  else {
    newRequestBody.Facility.address = {}
  }
}

async function handleUserProcess(
  request: any,
  createAndSearchConfig: any,
  params: any,
  dataToCreate: any[],
  newRequestBody: any
) {
  if (config.values.notCreateUserIfAlreadyThere) {
    var Employees: any[] = [];
    if (request.body?.mobileNumberUuidsMapping) {
      for (const employee of newRequestBody.Employees) {
        if (
          request.body.mobileNumberUuidsMapping[employee?.user?.mobileNumber]
        ) {
          logger.info(
            `User with mobile number ${employee?.user?.mobileNumber} already exist`
          );
        } else {
          Employees.push(employee);
        }
      }
    }
    newRequestBody.Employees = Employees;
  }
  if (newRequestBody.Employees.length > 0) {
    var responsePayload = await httpRequest(
      createAndSearchConfig?.createBulkDetails?.url,
      newRequestBody,
      params,
      "post",
      undefined,
      undefined,
      true,
      false
    );
    if (responsePayload?.Employees && responsePayload?.Employees?.length > 0) {
      enrichDataToCreateForUser(dataToCreate, responsePayload, request);
    } else {
      throwError(
        "COMMON",
        500,
        "INTERNAL_SERVER_ERROR",
        "Some internal server error occured during user creation."
      );
    }
  }
}

async function enrichAlreadyExsistingUser(request: any) {
  if (
    request.body.ResourceDetails.type == "user" &&
    request?.body?.mobileNumberUuidsMapping
  ) {
    for (const employee of request.body.dataToCreate) {
      if (
        request?.body?.mobileNumberUuidsMapping[employee?.user?.mobileNumber]
      ) {
        employee.uuid =
          request?.body?.mobileNumberUuidsMapping[
            employee?.user?.mobileNumber
          ].userUuid;
        employee.code =
          request?.body?.mobileNumberUuidsMapping[
            employee?.user?.mobileNumber
          ].code;
        employee.user.userName =
          request?.body?.mobileNumberUuidsMapping[
            employee?.user?.mobileNumber
          ].code;
        employee.user.password =
          request?.body?.mobileNumberUuidsMapping[
            employee?.user?.mobileNumber
          ].password;
        employee.user.userServiceUuid =
          request?.body?.mobileNumberUuidsMapping[
            employee?.user?.mobileNumber
          ].userServiceUuid;
      }
    }
  }
}

async function performAndSaveResourceActivity(
  request: any,
  createAndSearchConfig: any,
  params: any,
  type: any,
  localizationMap?: { [key: string]: string }
) {
  logger.info(type + " create data  ");
  if (createAndSearchConfig?.createBulkDetails?.limit) {
    await createBulkData(
      request,
      createAndSearchConfig,
      params,
      type);
  }
  else if (createAndSearchConfig?.createDetails) {
    await createSingleData(
      request,
      createAndSearchConfig,
      params,
      type,
      localizationMap);
  }
  await generateProcessedFileAndPersist(request, localizationMap);
}


async function createBulkData(
  request: any,
  createAndSearchConfig: any,
  params: any,
  type: any,
) {
  const limit = createAndSearchConfig?.createBulkDetails?.limit;
  const dataToCreate = request?.body?.dataToCreate;
  const chunks = Math.ceil(dataToCreate.length / limit); // Calculate number of chunks
  let creationTime = Date.now();
  var activities: any[] = [];
  for (let i = 0; i < chunks; i++) {
    const start = i * limit;
    const end = (i + 1) * limit;
    const chunkData = dataToCreate.slice(start, end); // Get a chunk of data
    const newRequestBody: any = {
      RequestInfo: request?.body?.RequestInfo,
    };
    _.set(
      newRequestBody,
      createAndSearchConfig?.createBulkDetails?.createPath,
      chunkData
    );
    if (type == "user") {
      await handleUserProcess(
        request,
        createAndSearchConfig,
        params,
        chunkData,
        newRequestBody
      );
    }
    // wait for 5 seconds after each chunk
    logger.info(`Waiting for 5 seconds after each chunk`);
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
  await enrichAlreadyExsistingUser(request);
  logger.info(`Final waiting for 10 seconds`);
  await new Promise((resolve) => setTimeout(resolve, 10000));
  await confirmCreation(
    createAndSearchConfig,
    request,
    dataToCreate,
    creationTime,
    activities
  );
}

async function createSingleData(
  request: any,
  createAndSearchConfig: any,
  params: any,
  type: any,
  localizationMap?: { [key: string]: string }
) {
  const dataToCreate = request?.body?.dataToCreate;
  const facilityDataForMicroplan = request?.body?.facilityDataForMicroplan;
  const isMicroplanSource = await isMicroplanRequest(request);
  const errors: any[] = [];
  for (const data of dataToCreate) {
    const newRequestBody: any = {
      RequestInfo: request?.body?.RequestInfo,
    };
    _.set(newRequestBody, createAndSearchConfig?.createDetails?.createPath, data);
    if (type == "facility") {
      const id = await handeFacilityProcess(
        createAndSearchConfig,
        params,
        newRequestBody
      );
      if (id) {
        errors.push({
          status: "CREATED",
          rowNumber: data["!row#number!"],
          isUniqueIdentifier: true,
          uniqueIdentifier: id,
          errorDetails: "",
        });
        if (isMicroplanSource && facilityDataForMicroplan) {
          const rowNumber = data['!row#number!'];
          const microplanFacilityData = facilityDataForMicroplan.find((microplanFacility: any) => microplanFacility['!row#number!'] == rowNumber) || null;
          if (microplanFacilityData) {
            microplanFacilityData.facilityDetails.id = id
          }
        }
      }
    }
  }
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails
    ? [...request?.body?.sheetErrorDetails, ...errors]
    : errors;
  await createPlanFacilityForMicroplan(request, localizationMap);
}

/**
 * Processes generic requests such as create or validate.
 * @param request The HTTP request object.
 */
async function processGenericRequest(
  request: any,
  localizationMap?: { [key: string]: string }
) {
  // Process generic requests
  if (
    request?.body?.ResourceDetails?.type != "boundary" &&
    request?.body?.ResourceDetails?.type != "boundaryManagement"
  ) {
    const responseFromCampaignSearch = await getCampaignSearchResponse(request);
    const campaignObject = responseFromCampaignSearch?.CampaignDetails?.[0];
    if (
      campaignObject?.additionalDetails?.resourceDistributionStrategy ==
      "HOUSE_TO_HOUSE"
    ) {
      request.body.showFixedPost = false;
    } else {
      request.body.showFixedPost = true;
    }
    request.body.projectTypeCode = campaignObject?.projectType;
    await checkAndGiveIfParentCampaignAvailable(request, campaignObject);
  }

  if (request?.body?.ResourceDetails?.action == "create") {
    await processCreate(request, localizationMap);
  } else {
    await processValidate(request, localizationMap);
  }
}

async function handleResouceDetailsError(request: any, error: any) {
  var stringifiedError: any;
  if (error?.description || error?.message) {
    stringifiedError = JSON.stringify({
      status: error.status || "",
      code: error.code || "",
      description: error.description || "",
      message: error.message || "",
    });
  } else {
    if (typeof error == "object") stringifiedError = JSON.stringify(error);
    else {
      stringifiedError = error;
    }
  }

  logger.error("Error while processing after validation : " + error);
  if (request?.body?.ResourceDetails) {
    request.body.ResourceDetails.status = "failed";
    request.body.ResourceDetails.additionalDetails = {
      ...request?.body?.ResourceDetails?.additionalDetails,
      error: stringifiedError,
    };
    const persistMessage: any = {
      ResourceDetails: request.body.ResourceDetails,
    };
    if (request?.body?.ResourceDetails?.action == "create") {
      persistMessage.ResourceDetails.additionalDetails = {
        error: stringifiedError,
      };
    }
    await produceModifiedMessages(
      persistMessage,
      config?.kafka?.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC
    );
  }
  if (
    request?.body?.Activities &&
    Array.isArray(request?.body?.Activities) &&
    request?.body?.Activities.length > 0
  ) {
    logger.info("Waiting for 2 seconds");
    await new Promise((resolve) => setTimeout(resolve, 2000));

    const activities = request?.body?.Activities;
    const chunkPromises = [];
    for (let i = 0; i < activities.length; i += 10) {
      const chunk = activities.slice(i, Math.min(i + 10, activities.length));
      const activityObject: any = { Activities: chunk };
      chunkPromises.push(
        produceModifiedMessages(
          activityObject,
          config?.kafka?.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC
        )
      );
    }
    await Promise.all(chunkPromises);
  }
}

async function persistCreationProcess(request: any, status: any) {
  if (request?.body?.ResourceDetails?.type == "facility") {
    await persistTrack(
      request?.body?.ResourceDetails?.campaignId,
      processTrackTypes.facilityCreation,
      status
    );
  } else if (request?.body?.ResourceDetails?.type == "user") {
    await persistTrack(
      request?.body?.ResourceDetails?.campaignId,
      processTrackTypes.staffCreation,
      status
    );
  }
}

async function processAfterValidation(
  dataFromSheet: any,
  createAndSearchConfig: any,
  request: any,
  localizationMap?: { [key: string]: string }
) {
  await persistCreationProcess(request, processTrackStatuses.inprogress);
  try {
    validateEmptyActive(dataFromSheet, request?.body?.ResourceDetails?.type, localizationMap);
    if (
      request?.body?.ResourceDetails?.additionalDetails?.source ==
      "microplan" &&
      request.body.ResourceDetails.type == "user"
    ) {
      await processSearchAndValidation(request);
    } else {
      const typeData = await convertToTypeData(
        request,
        dataFromSheet,
        createAndSearchConfig,
        request.body,
        localizationMap
      );
      request.body.dataToCreate = typeData.createData;
      request.body.dataToSearch = typeData.searchData;
      await processSearchAndValidation(request);
      await reorderBoundariesOfDataAndValidate(request, localizationMap);
    }
    if (
      createAndSearchConfig?.createBulkDetails &&
      request.body.ResourceDetails.status != "invalid"
    ) {
      await performAndSaveResourceActivityByChangingBody(
        request,
        createAndSearchConfig,
        localizationMap
      )
    }
    else if (createAndSearchConfig?.createDetails &&
      request.body.ResourceDetails.status != "invalid") {
      await performAndSaveResourceActivity(
        request,
        createAndSearchConfig,
        {},
        request.body.ResourceDetails.type,
        localizationMap
      );
    }
    else if (request.body.ResourceDetails.status == "invalid") {
      await generateProcessedFileAndPersist(request, localizationMap);
    }
  } catch (error: any) {
    console.log(error);
    await persistCreationProcess(request, processTrackStatuses.failed);
    await handleResouceDetailsError(request, error);
  }
  await persistCreationProcess(request, processTrackStatuses.completed);
}

async function performAndSaveResourceActivityByChangingBody(
  request: any,
  createAndSearchConfig: any,
  localizationMap?: { [key: string]: string }
) {
  _.set(
    request.body,
    createAndSearchConfig?.createBulkDetails?.createPath,
    request?.body?.dataToCreate
  );
  const params: any = getParamsViaElements(
    createAndSearchConfig?.createBulkDetails?.createElements,
    request
  );
  changeBodyViaElements(
    createAndSearchConfig?.createBulkDetails?.createElements,
    request
  );
  await performAndSaveResourceActivity(
    request,
    createAndSearchConfig,
    params,
    request.body.ResourceDetails.type,
    localizationMap
  );
}

/**
 * Processes the creation of resources.
 * @param request The HTTP request object.
 */
async function processCreate(request: any, localizationMap?: any) {
  // Process creation of resources
  const type: string = request.body.ResourceDetails.type;
  if (type == "boundary" || type == "boundaryManagement") {
    boundaryBulkUpload(request, localizationMap);
  } else if (type == "boundaryGeometryManagement") {
    await boundaryGeometryManagement(request, localizationMap);
  } else {
    // console.log(`Source is MICROPLAN -->`, source);
    let createAndSearchConfig: any;
    createAndSearchConfig = createAndSearch[type];
    const responseFromCampaignSearch = await getCampaignSearchResponse(request);
    const campaignType =
      responseFromCampaignSearch?.CampaignDetails[0]?.projectType;
    if (checkIfSourceIsMicroplan(request?.body?.ResourceDetails)) {
      logger.info(`Data create Source is MICROPLAN`);
      if (createAndSearchConfig?.parseArrayConfig?.parseLogic) {
        createAndSearchConfig.parseArrayConfig.parseLogic =
          createAndSearchConfig.parseArrayConfig.parseLogic.map((item: any) => {
            if (item.sheetColumn === "E") {
              item.sheetColumnName += `_${campaignType}`;
            }
            return item;
          });
      }
    }

    const dataFromSheet = await getDataFromSheet(
      request,
      request?.body?.ResourceDetails?.fileStoreId,
      request?.body?.ResourceDetails?.tenantId,
      createAndSearchConfig,
      undefined,
      localizationMap
    );
    const isUpdate = request?.body?.parentCampaignObject ? true : false;
    const schema = await getSchema(request?.body?.ResourceDetails?.tenantId, isUpdate, type, checkIfSourceIsMicroplan(request?.body?.ResourceDetails));
    await processAfterGettingSchema(
      dataFromSheet,
      schema,
      request,
      createAndSearchConfig,
      localizationMap
    );
  }
}



async function processAfterGettingSchema(
  dataFromSheet: any,
  schema: any,
  request: any,
  createAndSearchConfig: any,
  localizationMap?: any
) {
  logger.info("translating schema");
  const translatedSchema = await translateSchema(schema, localizationMap);
  if (Array.isArray(dataFromSheet)) {
    await validateSheetData(
      dataFromSheet,
      request,
      translatedSchema,
      createAndSearchConfig?.boundaryValidation,
      localizationMap
    );
    logger.info("validation done sucessfully");
    processAfterValidation(
      dataFromSheet,
      createAndSearchConfig,
      request,
      localizationMap
    );
  } else {
    if (dataFromSheet && Object.keys(dataFromSheet).length > 0) {
      processSheetWise(
        true,
        dataFromSheet,
        request,
        createAndSearchConfig,
        translatedSchema,
        localizationMap
      );
    } else {
      throwError(
        "COMMON",
        400,
        "VALIDATION_ERROR",
        "No data filled in the sheet."
      );
    }
  }
}

/**
 * Creates resources for a project campaign.
 * @param request The HTTP request object.
 */
async function createProjectCampaignResourcData(request: any) {
  await persistTrack(
    request.body.CampaignDetails.id,
    processTrackTypes.triggerResourceCreation,
    processTrackStatuses.inprogress
  );
  try {
    // Create resources for a project campaign
    if (
      request?.body?.CampaignDetails?.action == "create" &&
      request?.body?.CampaignDetails?.resources
    ) {
      for (const resource of request?.body?.CampaignDetails?.resources) {
        const action =
          resource?.type === "boundaryWithTarget" ? "validate" : "create";
        // if (resource.type != "boundaryWithTarget") {
        const resourceDetails = {
          type: resource.type,
          fileStoreId: resource.filestoreId,
          tenantId: request?.body?.CampaignDetails?.tenantId,
          action: action,
          hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
          additionalDetails: {},
          campaignId: request?.body?.CampaignDetails?.id,
        };
        logger.info(`Creating the resources for type ${resource.type}`);
        logger.debug(
          "resourceDetails " + getFormattedStringForDebug(resourceDetails)
        );
        const createRequestBody = {
          RequestInfo: request.body.RequestInfo,
          ResourceDetails: resourceDetails,
        };
        const req = replicateRequest(request, createRequestBody);
        const res: any = await createDataService(req);
        if (res?.id) {
          resource.createResourceId = res?.id;
        }
      }
    }
  } catch (error: any) {
    console.log(error);
    await persistTrack(
      request?.body?.CampaignDetails?.id,
      processTrackTypes.triggerResourceCreation,
      processTrackStatuses.failed,
      {
        error: String(
          error?.message +
          (error?.description ? ` : ${error?.description}` : "") || error
        ),
      }
    );
    throw new Error(error);
  }
  await persistTrack(
    request.body.CampaignDetails.id,
    processTrackTypes.triggerResourceCreation,
    processTrackStatuses.completed
  );
}

async function confirmProjectParentCreation(request: any, projectId: any) {
  const searchBody = {
    RequestInfo: request.body.RequestInfo,
    Projects: [
      {
        id: projectId,
        tenantId: request.body.CampaignDetails.tenantId,
      },
    ],
  };
  const params = {
    tenantId: request.body.CampaignDetails.tenantId,
    offset: 0,
    limit: 5,
  };
  var projectFound = false;
  var retry = 6;
  while (!projectFound && retry >= 0) {
    const response = await httpRequest(
      config.host.projectHost + config.paths.projectSearch,
      searchBody,
      params
    );
    if (response?.Project?.[0]) {
      projectFound = true;
    } else {
      logger.info("Project not found. Waiting for 1 seconds");
      retry = retry - 1;
      logger.info(`Waiting for ${retry} for 1 more second`);
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
  if (!projectFound) {
    throwError(
      "PROJECT",
      500,
      "PROJECT_CONFIRMATION_FAILED",
      "Project confirmation failed, for the project with id " + projectId
    );
  }
}

async function projectCreate(projectCreateBody: any, request: any) {
  logger.info("Project creation API started");
  logger.debug(
    "Project creation body " + getFormattedStringForDebug(projectCreateBody)
  );
  if (!request.body.newlyCreatedBoundaryProjectMap) {
    request.body.newlyCreatedBoundaryProjectMap = {};
  }
  const projectCreateResponse = await httpRequest(
    config.host.projectHost + config.paths.projectCreate,
    projectCreateBody,
    undefined,
    undefined,
    undefined,
    undefined,
    undefined,
    true
  );
  logger.debug(
    "Project creation response" +
    getFormattedStringForDebug(projectCreateResponse)
  );
  if (projectCreateResponse?.Project[0]?.id) {
    logger.info(
      "Project created successfully with name " +
      JSON.stringify(projectCreateResponse?.Project[0]?.name)
    );
    logger.info(
      `for boundary type ${projectCreateResponse?.Project[0]?.address?.boundaryType} and code ${projectCreateResponse?.Project[0]?.address?.boundary}`
    );
    request.body.boundaryProjectMapping[
      projectCreateBody?.Projects?.[0]?.address?.boundary
    ].projectId = projectCreateResponse?.Project[0]?.id;
  } else {
    throwError(
      "PROJECT",
      500,
      "PROJECT_CREATION_FAILED",
      "Project creation failed, for the request: " +
      JSON.stringify(projectCreateBody)
    );
  }
}

async function projectUpdateForTargets(projectUpdateBody: any, request: any, boundaryCode: any) {
  logger.info("Project Update For Targets started");

  logger.debug("Project update request body: " + getFormattedStringForDebug(projectUpdateBody));
  logger.info(`Project update started for boundary code: ${boundaryCode} and project name: ${request?.body?.CampaignDetails?.campaignName}`);

  try {
    const projectUpdateResponse = await httpRequest(
      config.host.projectHost + config.paths.projectUpdate,
      projectUpdateBody,
      undefined, undefined, undefined, undefined, undefined,
      true
    );
    logger.debug("Project update response: " + getFormattedStringForDebug(projectUpdateResponse));
    logger.info(`Project update response for boundary code: ${boundaryCode} and project name: ${request?.body?.CampaignDetails?.campaignName}`);
  } catch (error: any) {
    logger.error("Project update failed", error);
    throwError(
      "PROJECT",
      500,
      "PROJECT_UPDATE_ERROR",
      `Project update failed for the request: ${getFormattedStringForDebug(projectUpdateBody)}. Error: ${getFormattedStringForDebug(error.message)}`
    );
  }
}

function generateHierarchyList(data: any[], parentChain: any = []) {
  let result: any[] = [];

  // Iterate over each boundary in the current level
  for (let boundary of data) {
    let currentChain = [...parentChain, boundary.code];

    // Add the current chain to the result
    result.push(currentChain.join(","));

    // If there are children, recursively call the function
    if (boundary.children && boundary.children.length > 0) {
      let childResults = generateHierarchyList(boundary.children, currentChain);
      result = result.concat(childResults);
    }
  }
  return result;
}

const getHierarchy = async (
  tenantId: string,
  hierarchyType: string
) => {
  const BoundaryTypeHierarchySearchCriteria: BoundaryModels.BoundaryHierarchyDefinitionSearchCriteria =
  {
    BoundaryTypeHierarchySearchCriteria: {
      tenantId,
      hierarchyType,
    },
  };
  const response: BoundaryModels.BoundaryHierarchyDefinitionResponse =
    await searchBoundaryRelationshipDefinition(
      BoundaryTypeHierarchySearchCriteria
    );
  const boundaryList = response?.BoundaryHierarchy?.[0].boundaryHierarchy;
  return generateHierarchy(boundaryList);
};

const getHeadersOfBoundarySheet = async (
  fileUrl: string,
  sheetName: string,
  getRow = false,
  localizationMap?: any
) => {
  const localizedBoundarySheetName = getLocalizedName(
    sheetName,
    localizationMap
  );
  const workbook: any = await getExcelWorkbookFromFileURL(
    fileUrl,
    localizedBoundarySheetName
  );

  const worksheet = workbook.getWorksheet(localizedBoundarySheetName);
  const columnsToValidate = worksheet
    .getRow(1)
    .values.map((header: any) =>
      header ? header.toString().trim() : undefined
    );

  // Filter out empty items and return the result
  return columnsToValidate.filter((header: any) => typeof header === "string");
};

async function getCampaignSearchResponse(request: any) {
  try {
    logger.info(`searching for campaign details`);
    const CampaignDetails = {
      tenantId: request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId,
      ids: [request?.query?.campaignId || request?.body?.ResourceDetails?.campaignId],
    };
    const projectTypeSearchResponse: any =
      await searchProjectTypeCampaignService(CampaignDetails);
    return projectTypeSearchResponse;
  } catch (error: any) {
    logger.error(
      `Error while searching for campaign details: ${error.message}`
    );
    throwError("COMMON", 400, "RESPONSE_NOT_FOUND_ERROR", error?.message);
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
  confirmProjectParentCreation,
  projectUpdateForTargets
};
