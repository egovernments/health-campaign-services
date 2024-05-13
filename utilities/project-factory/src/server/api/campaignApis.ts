import config from "../config";
import { v4 as uuidv4 } from 'uuid';
import { httpRequest } from "../utils/request";
import { logger } from "../utils/logger";
import createAndSearch from '../config/createAndSearch';
import { getDataFromSheet, matchData, generateActivityMessage, throwError, translateSchema } from "../utils/genericUtils";
import { fetchBoundariesInChunks, immediateValidationForTargetSheet, validateSheetData, validateTargetSheetData } from '../utils/validators/campaignValidators';
import { callMdmsData, getCampaignNumber, getWorkbook } from "./genericApis";
import { boundaryBulkUpload, convertToTypeData, generateHierarchy, generateProcessedFileAndPersist, getLocalizedName } from "../utils/campaignUtils";
const _ = require('lodash');
import * as XLSX from 'xlsx';
import { produceModifiedMessages } from "../Kafka/Listener";
import { userRoles } from "../config/constants";



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
  await new Promise(resolve => setTimeout(resolve, 3000)); // Wait for 3 seconds
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
    logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
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
          password: "eGov@123",
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
        // console.log(key, createdElement[key], searchedElement[key], " ssssssssssssssssssssssssssssssssss");
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

function matchUserValidation(createdData: any[], searchedData: any[], request: any, createAndSearchConfig: any) {
  var count = 0;
  const errors = []
  const codeSet = new Set(searchedData.map(item => item?.code));
  const numberSet = new Set(searchedData.map(item => item?.user?.mobileNumber));
  for (const data of createdData) {
    if (codeSet.has(data.code) && numberSet.has(data?.user?.mobileNumber)) {
      errors.push({ status: "INVALID", rowNumber: data["!row#number!"], errorDetails: `User with mobileNumber ${data?.user?.mobileNumber} and userName ${data.code} already exists` })
      count++;
    }
    else if (codeSet.has(data.code)) {
      errors.push({ status: "INVALID", rowNumber: data["!row#number!"], errorDetails: `User with userName ${data.code} already exists` })
      count++;
    }
    else if (numberSet.has(data?.user?.mobileNumber)) {
      errors.push({ status: "INVALID", rowNumber: data["!row#number!"], errorDetails: `User with mobileNumber ${data?.user?.mobileNumber} already exists` })
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
    const codeSet = new Set(createdData.map(item => item.code));
    for (const data of searchedData) {
      if (codeSet.has(data.code)) {
        matchingSearchData.push(data);
        count++;
      }
    }
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
  if (request?.body?.dataToSearch?.length > 0) {
    const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
    changeBodyViaElements(createAndSearchConfig?.searchDetails?.searchElements, request)
    changeBodyViaSearchFromSheet(createAndSearchConfig?.requiresToSearchFromSheet, request, dataFromSheet)
    const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
    matchData(request, request.body.dataToSearch, arraysToMatch, createAndSearchConfig)
  }
  if (request?.body?.ResourceDetails?.type == "user") {
    await enrichEmployees(request?.body?.dataToCreate, request)
    const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
    const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
    matchUserValidation(request.body.dataToCreate, arraysToMatch, request, createAndSearchConfig)
  }
}



// Confirms the creation of resources by matching created and searched data.
async function confirmCreation(createAndSearchConfig: any, request: any, dataToCreate: any[], creationTime: any, activities: any) {
  // Confirm creation of resources by matching data  // wait for 5 seconds
  const params: any = getParamsViaElements(createAndSearchConfig?.searchDetails?.searchElements, request);
  const arraysToMatch = await processSearch(createAndSearchConfig, request, params)
  matchViaUserIdAndCreationTime(dataToCreate, arraysToMatch, request, creationTime, createAndSearchConfig, activities)
}

async function processValidateAfterSchema(dataFromSheet: any, request: any, createAndSearchConfig: any, localizationMap?: { [key: string]: string }) {
  try {
    const typeData = await convertToTypeData(request, dataFromSheet, createAndSearchConfig, request.body, localizationMap)
    request.body.dataToSearch = typeData.searchData;
    request.body.dataToCreate = typeData.createData;
    await processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)
    await generateProcessedFileAndPersist(request, localizationMap);
  } catch (error) {
    await handleResouceDetailsError(request, error);
  }
}

async function processValidate(request: any, localizationMap?: { [key: string]: string }) {
  const type: string = request.body.ResourceDetails.type;
  const tenantId = request.body.ResourceDetails.tenantId;
  const createAndSearchConfig = createAndSearch[type]
  const dataFromSheet = await getDataFromSheet(request, request?.body?.ResourceDetails?.fileStoreId, request?.body?.ResourceDetails?.tenantId, createAndSearchConfig, null, localizationMap)
  if (type == 'boundaryWithTarget') {
    immediateValidationForTargetSheet(dataFromSheet, localizationMap);
    validateTargetSheetData(dataFromSheet, request, createAndSearchConfig?.boundaryValidation, localizationMap);
  }

  else {
    const schemaMasterName: string = type === 'facility' ? config.facilitySchemaMasterName : type === 'user' ? config.userSchemaMasterName : "";
    const mdmsResponse = await callMdmsData(request, config.moduleName, schemaMasterName, tenantId);
    let schema: any;
    if (type === 'facility') {
      schema = mdmsResponse.MdmsRes[config.moduleName].facilitySchema[0];
    } if (type === 'user') {
      schema = mdmsResponse.MdmsRes[config.moduleName].userSchema[0];
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
        newRoles.push({ name: role, code: userRoles[role], tenantId: request?.body?.ResourceDetails?.tenantId })
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
  return hash.toString().padStart(5, '0');
}

function enrichUserNameAndPassword(employees: any[]) {
  const epochTime = Date.now();
  employees.forEach((employee) => {
    const { user, "!row#number!": rowNumber } = employee;
    const nameInitials = user.name.split(' ').map((name: any) => name.charAt(0)).join('');
    const generatedCode = `${nameInitials}${generateHash(`${epochTime}`)}${rowNumber}`;
    user.userName = generatedCode;
    user.password = "eGov@123";
    employee.code = generatedCode
  });
}

async function enrichJurisdictions(employee: any, request: any) {
  const boundaryCodeArray = employee.jurisdictions.split(',').map((code: any) => code.trim());
  const boundaryCodeSet = new Set(boundaryCodeArray);
  const boundaryCodeArrayUnique: any[] = Array.from(boundaryCodeSet);
  const responseBoundaries = await fetchBoundariesInChunks(request);
  var boundaryObject: any = {};
  responseBoundaries.forEach((element: any) => {
    element.boundary = element.code
    element.roles = employee.user.roles
    element.hierarchy = element.hierarchyType
    delete element.hierarchyType
    delete element.code
    delete element.parentId
    delete element.id
    boundaryObject[element.boundary] = element;
  });
  var newJurisdictions: any[] = [];
  for (const boundaryCode of boundaryCodeArrayUnique) {
    newJurisdictions.push(boundaryObject[boundaryCode])
  }
  employee.jurisdictions = newJurisdictions
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

async function performAndSaveResourceActivity(request: any, createAndSearchConfig: any, params: any, type: any, localizationMap?: { [key: string]: string }) {
  logger.info(type + " create data : " + JSON.stringify(request?.body?.dataToCreate));
  logger.info(type + " bulk create url : " + createAndSearchConfig?.createBulkDetails?.url, params);
  if (createAndSearchConfig?.createBulkDetails?.limit) {
    const limit = createAndSearchConfig?.createBulkDetails?.limit;
    const dataToCreate = request?.body?.dataToCreate;
    const chunks = Math.ceil(dataToCreate.length / limit); // Calculate number of chunks
    const creationTime = Date.now();
    var activities = [];
    for (let i = 0; i < chunks; i++) {
      const start = i * limit;
      const end = (i + 1) * limit;
      const chunkData = dataToCreate.slice(start, end); // Get a chunk of data
      const newRequestBody: any = {
        RequestInfo: request?.body?.RequestInfo,
      }
      _.set(newRequestBody, createAndSearchConfig?.createBulkDetails?.createPath, chunkData);
      if (type == "facility") {
        for (const facility of newRequestBody.Facilities) {
          facility.address = {}
        }
        logger.info("Facility create data : " + JSON.stringify(newRequestBody));
        var responsePayload = await httpRequest(createAndSearchConfig?.createBulkDetails?.url, newRequestBody, params, "post", undefined, undefined, true);
      }
      else if (type == "user") {
        var responsePayload = await httpRequest(createAndSearchConfig?.createBulkDetails?.url, newRequestBody, params, "post", undefined, undefined, true);
      }
      var activity = await generateActivityMessage(request?.body?.ResourceDetails?.tenantId, request.body, newRequestBody, responsePayload, type, createAndSearchConfig?.createBulkDetails?.url, responsePayload?.statusCode)
      logger.info("Activity : " + JSON.stringify(activity));
      activities.push(activity);
      await new Promise(resolve => setTimeout(resolve, 10000));
    }
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
  logger.error("Error while processing after validation : " + error)
  if (request?.body?.ResourceDetails) {
    request.body.ResourceDetails.status = "failed";
    request.body.ResourceDetails.additionalDetails = {
      ...request?.body?.ResourceDetails?.additionalDetails,
      error: JSON.stringify({
        status: error.status || '',
        code: error.code || '',
        description: error.description || '',
        message: error.message || ''
      })
    };
    produceModifiedMessages(request?.body, config.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC);
  }
  if (request?.body?.Activities && Array.isArray(request?.body?.Activities && request?.body?.Activities.length > 0)) {
    await new Promise(resolve => setTimeout(resolve, 2000));
    produceModifiedMessages(request?.body, config.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC);
  }
}

async function processAfterValidation(dataFromSheet: any, createAndSearchConfig: any, request: any, localizationMap?: { [key: string]: string }) {
  try {
    const typeData = await convertToTypeData(request, dataFromSheet, createAndSearchConfig, request.body, localizationMap)
    request.body.dataToCreate = typeData.createData;
    request.body.dataToSearch = typeData.searchData;
    await processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)
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
    await handleResouceDetailsError(request, error)
  }
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
    const createAndSearchConfig = createAndSearch[type]
    const dataFromSheet = await getDataFromSheet(request, request?.body?.ResourceDetails?.fileStoreId, request?.body?.ResourceDetails?.tenantId, createAndSearchConfig, undefined, localizationMap)
    const schemaMasterName: string = type === 'facility' ? config.facilitySchemaMasterName : type === 'user' ? config.userSchemaMasterName : "";
    const mdmsResponse = await callMdmsData(request, config.moduleName, schemaMasterName, tenantId);
    let schema: any;
    if (type === 'facility') {
      schema = mdmsResponse.MdmsRes[config.moduleName].facilitySchema[0];
    } if (type === 'user') {
      schema = mdmsResponse.MdmsRes[config.moduleName].userSchema[0];
    }
    const translatedSchema = await translateSchema(schema, localizationMap);
    await validateSheetData(dataFromSheet, request, translatedSchema, createAndSearchConfig?.boundaryValidation, localizationMap)
    processAfterValidation(dataFromSheet, createAndSearchConfig, request, localizationMap)
  }
}

/**
 * Creates resources for a project campaign.
 * @param request The HTTP request object.
 */
async function createProjectCampaignResourcData(request: any) {
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
          additionalDetails: {}
        };
        logger.info("resourceDetails " + JSON.stringify(resourceDetails))
        const response = await httpRequest(`${config.host.projectFactoryBff}project-factory/v1/data/_create`, {
          RequestInfo: request.body.RequestInfo,
          ResourceDetails: resourceDetails
        });
        if (response?.ResourceDetails?.id) {
          resource.createResourceId = response?.ResourceDetails?.id
        }
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
  const localizedBoundarySheetName = getLocalizedName(sheetName, localizationMap)
  const workbook: any = await getWorkbook(fileUrl, localizedBoundarySheetName);
  const columnsToValidate = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], {
    header: 1,
  })[0] as (any)[];

  // Filter out empty items and return the result
  for (let i = 0; i < columnsToValidate.length; i++) {
    if (typeof columnsToValidate[i] !== 'string') {
      columnsToValidate[i] = undefined;
    }
  }
  return columnsToValidate;
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
  handleResouceDetailsError
};
