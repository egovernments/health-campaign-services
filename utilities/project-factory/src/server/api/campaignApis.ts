import config from "../config";
import { v4 as uuidv4 } from 'uuid';
import { httpRequest } from "../utils/request";
import { logger } from "../utils/logger";
import createAndSearch from '../config/createAndSearch';
import { getDataFromSheet, matchData, generateActivityMessage, throwError } from "../utils/genericUtils";
import { validateSheetData } from '../utils/validators/campaignValidators';
import { getCampaignNumber } from "./genericApis";
import { autoGenerateBoundaryCodes, convertToTypeData, generateHierarchy } from "../utils/campaignUtils";
import axios from "axios";
const _ = require('lodash');



async function enrichCampaign(requestBody: any) {
  if (requestBody?.Campaign) {
    requestBody.Campaign.id = uuidv4();
    requestBody.Campaign.campaignNo = await getCampaignNumber(requestBody, config.values.idgen.format, config.values.idgen.idName, requestBody?.Campaign?.tenantId);
    for (const campaignDetails of requestBody?.Campaign?.CampaignDetails) {
      campaignDetails.id = uuidv4();
    }
  }
}

async function getAllFacilitiesInLoop(searchedFacilities: any[], facilitySearchParams: any, facilitySearchBody: any) {
  await new Promise(resolve => setTimeout(resolve, 3000)); // Wait for 3 seconds
  logger.info("facilitySearchParams : " + JSON.stringify(facilitySearchParams));
  const response = await httpRequest(config.host.facilityHost + config.paths.facilitySearch, facilitySearchBody, facilitySearchParams);

  if (Array.isArray(response?.Facilities)) {
    searchedFacilities.push(...response?.Facilities);
    return response.Facilities.length >= 50; // Return true if there are more facilities to fetch, false otherwise
  } else {
    throwError("Search failed for Facility. Check Logs", 500, "FACILITY_SEARCH_FAILED");
    return false;
  }
}

async function getAllFacilities(tenantId: string, requestBody: any) {
  const facilitySearchBody = {
    RequestInfo: requestBody?.RequestInfo,
    Facility: { isPermanent: true }
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId?.split('.')?.[0]
  };

  logger.info("Facility search url : " + config.host.facilityHost + config.paths.facilitySearch);
  logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
  const searchedFacilities: any[] = [];
  let searchAgain = true;

  while (searchAgain) {
    searchAgain = await getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody);
    facilitySearchParams.offset += 50;
  }

  return searchedFacilities;
}

async function getFacilitiesViaIds(tenantId: string, ids: any[], requestBody: any) {
  const facilitySearchBody: any = {
    RequestInfo: requestBody?.RequestInfo,
    Facility: {}
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId?.split('.')?.[0]
  };

  logger.info("Facility search url : " + config.host.facilityHost + config.paths.facilitySearch);
  const searchedFacilities: any[] = [];

  // Split ids into chunks of 50
  for (let i = 0; i < ids.length; i += 50) {
    const chunkIds = ids.slice(i, i + 50);
    facilitySearchBody.Facility.id = chunkIds;
    logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
    await getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody);
  }

  return searchedFacilities;
}

function getParamsViaElements(elements: any, request: any) {
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

function changeBodyViaElements(elements: any, request: any) {
  if (!elements) {
    return;
  }
  for (const element of elements) {
    if (element?.isInBody) {
      if (element?.value) {
        _.set(request.body, element?.keyPath, element?.value);
      }
      else if (element?.getValueViaPath) {
        _.set(request.body, element?.keyPath, _.get(request.body, element?.getValueViaPath))
      }
      else {
        _.set(request.body, element?.keyPath, {})
      }
    }
  }
}

function changeBodyViaSearchFromSheet(elements: any, request: any, dataFromSheet: any) {
  if (!elements) {
    return;
  }
  for (const element of elements) {
    const arrayToSearch = []
    for (const data of dataFromSheet) {
      if (data[element.sheetColumnName]) {
        arrayToSearch.push(data[element.sheetColumnName]);
      }
    }
    _.set(request.body, element?.searchPath, arrayToSearch);
  }
}

function updateErrors(newCreatedData: any[], newSearchedData: any[], errors: any[], createAndSearchConfig: any, activity: any) {
  newCreatedData.forEach((createdElement: any) => {
    let foundMatch = false;
    for (const searchedElement of newSearchedData) {
      let match = true;
      for (const key in createdElement) {
        if (createdElement.hasOwnProperty(key) && !searchedElement.hasOwnProperty(key) && key != '!row#number!') {
          match = false;
          break;
        }
        if (createdElement[key] !== searchedElement[key] && key != '!row#number!') {
          match = false;
          break;
        }
      }
      if (match) {
        foundMatch = true;
        newSearchedData.splice(newSearchedData.indexOf(searchedElement), 1);
        errors.push({ status: "PERSISTED", rowNumber: createdElement["!row#number!"], isUniqueIdentifier: true, uniqueIdentifier: searchedElement[createAndSearchConfig.uniqueIdentifier], errorDetails: "" })
        break;
      }
    }
    if (!foundMatch) {
      errors.push({ status: "NOT_PERSISTED", rowNumber: createdElement["!row#number!"], errorDetails: `Can't confirm persistence of this data` })
      activity.status = 2001 // means not persisted
      logger.info("Can't confirm persistence of this data of row number : " + createdElement["!row#number!"]);
    }
  });
}

function matchCreatedAndSearchedData(createdData: any[], searchedData: any[], request: any, createAndSearchConfig: any, activity: any) {
  const newCreatedData = JSON.parse(JSON.stringify(createdData));
  const newSearchedData = JSON.parse(JSON.stringify(searchedData));
  const uid = createAndSearchConfig.uniqueIdentifier;
  newCreatedData.forEach((element: any) => {
    delete element[uid];
  })
  var errors: any[] = []
  updateErrors(newCreatedData, newSearchedData, errors, createAndSearchConfig, activity);
  request.body.sheetErrorDetails = request?.body?.sheetErrorDetails ? [...request?.body?.sheetErrorDetails, ...errors] : errors;
  request.body.Activities = [...(request?.body?.Activities ? request?.body?.Activities : []), activity]
}
function matchViaUserIdAndCreationTime(createdData: any[], searchedData: any[], request: any, creationTime: any, createAndSearchConfig: any, activity: any) {
  var matchingSearchData = [];
  const userUuid = request?.body?.RequestInfo?.userInfo?.uuid
  var count = 0;
  for (const data of searchedData) {
    if (data?.auditDetails?.createdBy == userUuid && data?.auditDetails?.createdTime >= creationTime) {
      matchingSearchData.push(data);
      count++;
    }
  }
  if (count < createdData.length) {
    request.body.ResourceDetails.status = "PERSISTER_ERROR"
  }
  matchCreatedAndSearchedData(createdData, matchingSearchData, request, createAndSearchConfig, activity);
  logger.info("New created resources count : " + count);
}

async function processSearch(createAndSearchConfig: any, request: any, params: any) {
  setSearchLimits(createAndSearchConfig, request, params);
  logger.info("Search url : " + createAndSearchConfig?.searchDetails?.url);

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
    logger.info("Search url : " + createAndSearchConfig?.searchDetails?.url);
    logger.info("Search params : " + JSON.stringify(params));
    logger.info("Search body : " + JSON.stringify(request.body));

    const response = await httpRequest(createAndSearchConfig?.searchDetails?.url, request.body, params);
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
    await new Promise(resolve => setTimeout(resolve, 5000));
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
  const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
  changeBodyViaElements(createAndSearchConfig?.searchDetails?.searchElements, request)
  changeBodyViaSearchFromSheet(createAndSearchConfig?.requiresToSearchFromSheet, request, dataFromSheet)
  const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
  matchData(request, request.body.dataToSearch, arraysToMatch, createAndSearchConfig)
}


async function confirmCreation(createAndSearchConfig: any, request: any, facilityCreateData: any[], creationTime: any, activity: any) {
  // wait for 5 seconds
  await new Promise(resolve => setTimeout(resolve, 5000));
  const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
  changeBodyViaElements(createAndSearchConfig?.searchDetails?.searchElements, request)
  const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
  matchViaUserIdAndCreationTime(facilityCreateData, arraysToMatch, request, creationTime, createAndSearchConfig, activity)
}

async function processValidate(request: any) {
  const type: string = request.body.ResourceDetails.type;
  const createAndSearchConfig = createAndSearch[type]
  const dataFromSheet = await getDataFromSheet(request?.body?.ResourceDetails?.fileStoreId, request?.body?.ResourceDetails?.tenantId, createAndSearchConfig)
  await validateSheetData(dataFromSheet, request, createAndSearchConfig?.sheetSchema, createAndSearchConfig?.boundaryValidation)
  const typeData = convertToTypeData(dataFromSheet, createAndSearchConfig, request.body)
  request.body.dataToSearch = typeData.searchData;
  await processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)
}

async function performAndSaveResourceActivity(request: any, createAndSearchConfig: any, params: any, type: any) {
  logger.info(type + " create data : " + JSON.stringify(request?.body?.dataToCreate));
  logger.info(type + " bulk create url : " + createAndSearchConfig?.createBulkDetails?.url, params);
  if (createAndSearchConfig?.createBulkDetails?.limit) {
    const limit = createAndSearchConfig?.createBulkDetails?.limit;
    const dataToCreate = request?.body?.dataToCreate;
    const chunks = Math.ceil(dataToCreate.length / limit); // Calculate number of chunks
    for (let i = 0; i < chunks; i++) {
      const start = i * limit;
      const end = (i + 1) * limit;
      const chunkData = dataToCreate.slice(start, end); // Get a chunk of data
      const creationTime = Date.now();
      const newRequestBody = JSON.parse(JSON.stringify(request.body));
      _.set(newRequestBody, createAndSearchConfig?.createBulkDetails?.createPath, chunkData);
      const responsePayload = await httpRequest(createAndSearchConfig?.createBulkDetails?.url, newRequestBody, params, "post", undefined, undefined, true);
      var activity = await generateActivityMessage(request?.body?.ResourceDetails?.tenantId, request.body, newRequestBody, responsePayload, type, createAndSearchConfig?.createBulkDetails?.url, responsePayload?.statusCode)
      await confirmCreation(createAndSearchConfig, request, chunkData, creationTime, activity);
      logger.info("Activity : " + JSON.stringify(activity));
    }
  }
}

async function processGenericRequest(request: any) {
  if (request?.body?.ResourceDetails?.action == "create") {
    await processCreate(request)
  }
  else {
    await processValidate(request)
  }
}


async function processCreate(request: any) {
  const type: string = request.body.ResourceDetails.type;
  if (type == "boundary") {
    await autoGenerateBoundaryCodes(request);
  }
  else {
    const createAndSearchConfig = createAndSearch[type]
    const dataFromSheet = await getDataFromSheet(request?.body?.ResourceDetails?.fileStoreId, request?.body?.ResourceDetails?.tenantId, createAndSearchConfig)
    await validateSheetData(dataFromSheet, request, createAndSearchConfig?.sheetSchema, createAndSearchConfig?.boundaryValidation)
    const typeData = convertToTypeData(dataFromSheet, createAndSearchConfig, request.body)
    request.body.dataToCreate = typeData.createData;
    request.body.dataToSearch = typeData.searchData;
    await processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)
    if (createAndSearchConfig?.createBulkDetails) {
      _.set(request.body, createAndSearchConfig?.createBulkDetails?.createPath, request?.body?.dataToCreate);
      const params: any = getParamsViaElements(createAndSearchConfig?.createBulkDetails?.createElements, request);
      changeBodyViaElements(createAndSearchConfig?.createBulkDetails?.createElements, request)
      await performAndSaveResourceActivity(request, createAndSearchConfig, params, type);
    }
  }
}

async function createProjectCampaignResourcData(request: any) {
  if (request?.body?.CampaignDetails?.action == "create" && request?.body?.CampaignDetails?.resources) {
    for (const resource of request?.body?.CampaignDetails?.resources) {
      const resourceDetails = {
        type: resource.type,
        fileStoreId: resource.filestoreId,
        tenantId: request?.body?.CampaignDetails?.tenantId,
        action: "create",
        hierarchyType: request?.body?.CampaignDetails?.hierarchyType,
        additionalDetails: {}
      };
      try {
        await axios.post(`${config.host.projectFactoryBff}project-factory/v1/data/_create`, {
          RequestInfo: request.body.RequestInfo,
          ResourceDetails: resourceDetails
        });
      } catch (error: any) {
        // Handle error for individual resource creation
        logger.error(`Error creating resource: ${error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error}`);
        throwError(error?.response?.data?.Errors?.[0]?.message || String(error), 500, "RESOURCE_CREATION_ERROR");
      }
    }
  }
}

async function projectCreate(projectCreateBody: any, request: any) {
  logger.info("Project creation url " + config.host.projectHost + config.paths.projectCreate)
  logger.info("Project creation body " + JSON.stringify(projectCreateBody))
  const projectCreateResponse = await httpRequest(config.host.projectHost + config.paths.projectCreate, projectCreateBody);
  logger.info("Project creation response" + JSON.stringify(projectCreateResponse))
  if (projectCreateResponse?.Project[0]?.id) {
    logger.info("Project created successfully with id " + JSON.stringify(projectCreateResponse?.Project[0]?.id))
    request.body.boundaryProjectMapping[projectCreateBody?.Projects?.[0]?.address?.boundary].projectId = projectCreateResponse?.Project[0]?.id
  }
  else {
    throwError("Project creation failed, for the request: " + JSON.stringify(projectCreateBody), 500, "PROJECT_CREATION_FAILED");
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

  try {
    const response = await httpRequest(url, requestBody);
    const boundaryList = response?.BoundaryHierarchy?.[0].boundaryHierarchy;
    return generateHierarchy(boundaryList);
  } catch (error: any) {
    logger.error(`Error fetching hierarchy data: ${error.message}`, error);
    throw error;
  }
};

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
  getHierarchy
};