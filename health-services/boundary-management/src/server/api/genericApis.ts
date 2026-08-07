import{getLocalizedName,extractCodesFromBoundaryRelationshipResponse,getHierarchy} from "../utils/boundaryUtils"
import {getExcelWorkbookFromFileURL,enrichTemplateMetaData} from "../utils/excelUtils";
import { logger,getFormattedStringForDebug } from  "../utils/logger";
import {findMapValue,getLocalizedHeaders,throwError,getDataSheetReady} from "../utils/genericUtils";
import config from "../config/index";
import { httpRequest,defaultheader } from "../utils/request";
import {getLocaleFromRequestInfo} from "../utils/localisationUtils";
import {searchBoundaryRelationshipData,defaultRequestInfo} from "./coreApis";
import FormData from "form-data"; 
const _ = require('lodash'); // Import lodash library


// Function to retrieve data from a specific sheet in an Excel file
const getSheetData = async (
  fileUrl: string,
  sheetName: string,
  getRow = false,
  createAndSearchConfig?: any,
  localizationMap?: { [key: string]: string }
) => {
  // Retrieve workbook using the getExcelWorkbookFromFileURL function
  const localizedSheetName = getLocalizedName(sheetName, localizationMap);
  const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedSheetName);

  const worksheet: any = workbook.getWorksheet(localizedSheetName);

  // If parsing array configuration is provided, validate first row of each column
  // validateFirstRowColumn(createAndSearchConfig, worksheet, localizationMap);

  // Collect sheet data by iterating through rows and cells
  const sheetData = getSheetDataFromWorksheet(worksheet);
  const jsonData = getJsonData(sheetData, getRow);
  return jsonData;
};

/**
 * Asynchronously creates boundary relationships based on the provided request, boundary type map, and modified child-parent map.
 * @param request The HTTP request object.
 * @param boundaryTypeMap Map of boundary codes to types.
 * @param modifiedChildParentMap Modified child-parent map.
 */
async function createBoundaryRelationship(request: any, boundaryMap: Map<{ key: string, value: string }, string>, modifiedChildParentMap: Map<string, string | null>) {
  try {

    const updatedBoundaryMap: Array<{ key: string, value: string }> = Array.from(boundaryMap).map(([key, value]) => ({ key: value, value: key.key }));

    let activityMessage: any[] = [];
    const requestBody = { "RequestInfo": request.body.RequestInfo } as { RequestInfo: any; BoundaryRelationship?: any };
    const url = `${config.host.boundaryHost}${config.paths.boundaryRelationship}`;
    const params = {
      "type": "boundaryManagement",
      "tenantId": request?.body?.ResourceDetails?.tenantId,
      "boundaryType": null,
      "codes": null,
      "includeChildren": true,
      "hierarchyType": request?.body?.ResourceDetails?.hierarchyType
    };
    const header = {
      ...defaultheader,
      // cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId}${params.codes || ''}${params?.includeChildren || ''}`,
    }

    const boundaryRelationshipResponse = await httpRequest(url, request.body, params, undefined, undefined, header);
    const boundaryData = boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
    const allCodes = extractCodesFromBoundaryRelationshipResponse(boundaryData);

    let flag = 1;

    for (const { key: boundaryCode, value: boundaryType } of updatedBoundaryMap) {
      if (!allCodes.has(boundaryCode)) {
        const boundary = {
          tenantId: request?.body?.ResourceDetails?.tenantId,
          boundaryType: boundaryType,
          code: boundaryCode,
          hierarchyType: request?.body?.ResourceDetails?.hierarchyType,
          parent: modifiedChildParentMap.get(boundaryCode) || null
        };

        flag = 0;
        requestBody.BoundaryRelationship = boundary;
        await confirmBoundaryParentCreation(request, modifiedChildParentMap.get(boundaryCode) || null);
        try {
          const response = await httpRequest(`${config.host.boundaryHost}${config.paths.boundaryRelationshipCreate}`, requestBody, {}, 'POST', undefined, undefined, true);

          if (!response.TenantBoundary || !Array.isArray(response.TenantBoundary) || response.TenantBoundary.length === 0) {
            throwError("BOUNDARY", 500, "BOUNDARY_RELATIONSHIP_CREATE_ERROR");
          }
          logger.info(`Boundary relationship created for boundaryType :: ${boundaryType} & boundaryCode :: ${boundaryCode} `);
        } catch (error) {
          // Log the error and rethrow to be caught by the outer try...catch block
          logger.error(`Error creating boundary relationship for boundaryType :: ${boundaryType} & boundaryCode :: ${boundaryCode} :: `, error);
          throw error;
        }
      }
    };

    if (flag === 1) {
      throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary already present in the system");
    }

    request.body = {
      ...request.body,
      Activities: activityMessage
    };
  } catch (error: any) {
    const errorCode = error.code || "INTERNAL_SERVER_ERROR";
    const errorMessage = error.description || "Error while boundary relationship create";
    logger.error(`Error in createBoundaryRelationship: ${errorMessage}`, error);
    throwError("COMMON", 500, errorCode, errorMessage);
  }
}

async function confirmBoundaryParentCreation(request: any, code: any) {
  if (code) {
    const searchBody = {
      RequestInfo: request.body.RequestInfo,
    }
    const params: any = {
      hierarchyType: request?.body?.ResourceDetails?.hierarchyType,
      tenantId: request?.body?.ResourceDetails?.tenantId,
      codes: code
    }
    var retry = 6;
    var boundaryFound = false;
    const header = {
      ...defaultheader,
      // cachekey: `boundaryRelationShipSearch${params?.hierarchyType}${params?.tenantId}${params.codes.replace(/’/g, '') || ''}${params?.includeChildren || ''}`,
    }
    while (!boundaryFound && retry >= 0) {
      const response = await httpRequest(config.host.boundaryHost + config.paths.boundaryRelationship, searchBody, params, undefined, undefined, header);
      if (response?.TenantBoundary?.[0].boundary?.[0]) {
        boundaryFound = true;
      }
      else {
        logger.info("Boundary not found. Waiting for 1 seconds");
        retry = retry - 1
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }
    if (!boundaryFound) {
      throwError("BOUNDARY", 500, "INTERNAL_SERVER_ERROR", "Boundary creation failed, for the boundary with code " + code);
    }
  }
}

// Function to create an Excel sheet
async function createExcelSheet(data: any, headers: any) {
  var rows = [headers, ...data];
  return rows;
}

function getSheetDataFromWorksheet(worksheet: any) {
  var sheetData: any[][] = [];

  worksheet?.eachRow({ includeEmpty: true }, (row: any, rowNumber: any) => {
    const rowData: any[] = [];

    row.eachCell({ includeEmpty: true }, (cell: any, colNumber: any) => {
      const cellValue = getRawCellValue(cell);
      rowData[colNumber - 1] = cellValue; // Store cell value (0-based index)
    });

    // Push non-empty row only
    if (rowData.some(value => value !== null && value !== undefined)) {
      sheetData[rowNumber - 1] = rowData; // Store row data (0-based index)
    }
  });
  return sheetData;
}

// Helper function to extract raw cell value
function getRawCellValue(cell: any) {
  if (cell.value && typeof cell.value === 'object') {
    if ('richText' in cell.value) {
      // Handle rich text
      return cell.value.richText.map((rt: any) => rt.text).join('');
    }
    else if ('hyperlink' in cell.value) {
      if (cell?.value?.text?.richText?.length > 0) {
        return cell.value.text.richText.map((t: any) => t.text).join('');
      }
      else {
        return cell.value.text;
      }
    }
    else if ('formula' in cell.value) {
      // Get the result of the formula
      return cell.value.result;
    }
    else if ('sharedFormula' in cell.value) {
      // Get the result of the shared formula
      return cell.value.result;
    }
    else if ('error' in cell.value) {
      // Get the error value
      return cell.value.error;
    } else if (cell.value instanceof Date) {
      // Handle date values
      return cell.value.toISOString();
    }
    else {
      // Return as-is for other object types
      return cell.value;
    }
  }
  return cell.value; // Return raw value for plain strings, numbers, etc.
}

/**
 * Retrieves boundary sheet data based on the request parameters.
 * @param {any} request The HTTP request object.
 * @param {{[key: string]: string}} localizationMap The localization map.
 * @param {boolean} useCache Whether to use cache or not.
 * @returns {Promise<any>} The boundary sheet data.
 */
async function getBoundarySheetData(
  request: any,
  localizationMap?: { [key: string]: string },
  useCache?:boolean
) {
  // Retrieve boundary data based on the request parameters
  // const params = {
  //   ...request?.query,
  //   includeChildren: true,
  // };
  const hierarchyType = request?.query?.hierarchyType;
  const tenantId = request?.query?.tenantId;
  logger.info(
    `processing boundary data generation for hierarchyType : ${hierarchyType}`
  );
  // const boundaryData = await getBoundaryRelationshipData(request, params);
  const boundaryRelationshipResponse: any = await searchBoundaryRelationshipData(tenantId, hierarchyType, true, true,useCache);
  const boundaryData = boundaryRelationshipResponse?.TenantBoundary?.[0]?.boundary;
  if (!boundaryData || boundaryData.length === 0) {
    logger.info(`boundary data not found for hierarchyType : ${hierarchyType}`);
    const hierarchy = await getHierarchy(
      request?.query?.tenantId,
      hierarchyType
    );
    const modifiedHierarchy = hierarchy.map((ele) =>
      `${hierarchyType}_${ele}`.toUpperCase()
    );
    const localizedHeadersUptoHierarchy = getLocalizedHeaders(
      modifiedHierarchy,
      localizationMap
    );
    var headerColumnsAfterHierarchy;

    headerColumnsAfterHierarchy = await getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement(request, localizationMap);
    const headers = [...localizedHeadersUptoHierarchy, ...headerColumnsAfterHierarchy];
    // create empty sheet if no boundary present in system
    // const localizedBoundaryTab = getLocalizedName(
    //   getBoundaryTabName(),
    //   localizationMap
    // );
    logger.info(`generated a empty template for boundary`);
    return await createExcelSheet(
      boundaryData,
      headers
    );
  } 
  else {
    return await getDataSheetReady(boundaryData, request, localizationMap);
  }
}

async function getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement(request: any, localizationMap?: { [key: string]: string }) {
  try {
    const mdmsResponse = await callMdmsTypeSchema(
      request?.query?.tenantId || request?.body?.ResourceDetails?.tenantId,
      false,
      "boundaryManagement",
      "all"
    );
    if (!mdmsResponse || mdmsResponse?.columns.length === 0) {
      logger.error(
        `Campaign Type all has not any columns configured in schema`
      );
      throwError(
        "COMMON",
        400,
        "SCHEMA_ERROR",
        `Campaign Type all has not any columns configured in schema`
      );
    }
    // Extract columns from the response
    const columnsForGivenCampaignId = mdmsResponse?.columns;

    // Get localized headers based on the column names
    const headerColumnsAfterHierarchy = getLocalizedHeaders(
      columnsForGivenCampaignId,
      localizationMap
    );
    if (
      !headerColumnsAfterHierarchy.includes(
        getLocalizedName(config.boundary.boundaryCode, localizationMap)
      )
    ) {
      logger.error(
        `Column Headers of generated Boundary Template does not have ${getLocalizedName(
          config.boundary.boundaryCode,
          localizationMap
        )} column`
      );
      throwError(
        "COMMON",
        400,
        "VALIDATION_ERROR",
        `Column Headers of generated Boundary Template does not have ${getLocalizedName(
          config.boundary.boundaryCode,
          localizationMap
        )} column`
      );
    }
    return headerColumnsAfterHierarchy;
  } catch (error: any) {
    console.log(error);
    throwError(
      "FILE",
      400,
      "FETCHING_COLUMN_ERROR",
      "Error fetching column Headers From Schema (either boundary code column not found or given  Campaign Type not found in schema) Check logs"
    );
  }
}
async function callMdmsTypeSchema(
  tenantId: string,
  isUpdate: boolean,
  type: any,
  campaignType = "all"
) {
  const RequestInfo = defaultRequestInfo?.RequestInfo;
  const requestBody = {
    RequestInfo,
    MdmsCriteria: {
      tenantId: tenantId,
      uniqueIdentifiers: [
        `${type}.${campaignType}`
      ],
      schemaCode: "HCM-ADMIN-CONSOLE.adminSchema"
    }
  };
  const url = config.host.mdmsV2 + config.paths.mdms_v2_search;
  const header = {
    ...defaultheader,
    cachekey: `mdmsv2Seacrh${requestBody?.MdmsCriteria?.tenantId}${campaignType}${type}.${campaignType}${requestBody?.MdmsCriteria?.schemaCode}`
  }
  const response = await httpRequest(url, requestBody, undefined, undefined, undefined, header);
  if (!response?.mdms?.[0]?.data) {
    throwError("COMMON", 500, "INTERNAL_SERVER_ERROR", "Error occured during schema search");
  }
  return convertIntoSchema(response?.mdms?.[0]?.data, isUpdate);
}


function convertIntoSchema(data: any, isUpdate: boolean) {
  const properties: any = {};
  const errorMessage: any = {};
  const required: any[] = [];
  let columns: any[] = [];
  const unique: any[] = [];
  const columnsNotToBeFreezed: any[] = [];
  const columnsToBeFreezed: any[] = [];
  const columnsToHide: any[] = [];

  for (const propType of ['enumProperties', 'numberProperties', 'stringProperties']) {
    if (data.properties[propType] && Array.isArray(data.properties[propType]) && data.properties[propType]?.length > 0) {
      for (const property of data.properties[propType]) {
        properties[property?.name] = {
          ...property,
          type: propType === 'stringProperties' ? 'string' : propType === 'numberProperties' ? 'number' : undefined
        };
        if (property?.errorMessage)
          errorMessage[property?.name] = property?.errorMessage;

        if (property?.isRequired && required.indexOf(property?.name) === -1) {
          required.push({ name: property?.name, orderNumber: property?.orderNumber });
        }
        if (property?.isUnique && unique.indexOf(property?.name) === -1) {
          unique.push(property?.name);
        }
        if (!property?.freezeColumn || property?.freezeColumn == false) {
          columnsNotToBeFreezed.push(property?.name);
        }
        if (property?.freezeColumn) {
          columnsToBeFreezed.push(property?.name);
        }
        if (property?.hideColumn) {
          columnsToHide.push(property?.name);
        }

        // If orderNumber is missing, default to a very high number
        if (isUpdate) {
          columns.push({ name: property?.name, orderNumber: property?.orderNumber || 9999999999 });
        }
        else {
          if (!property?.isUpdate) {
            columns.push({ name: property?.name, orderNumber: property?.orderNumber || 9999999999 });
          }
        }
      }
    }
  }

  const descriptionToFieldMap: Record<string, string> = {};

  for (const [key, field] of Object.entries(properties)) {
    // Cast field to `any` since it is of type `unknown`
    const typedField = field as any;
  
    if (typedField.isRequired) {
      descriptionToFieldMap[typedField.description] = key;
    }
  }
  data.descriptionToFieldMap = descriptionToFieldMap;
  
  
  enrichSchema(data, properties, required, columns, unique, columnsNotToBeFreezed, columnsToBeFreezed, columnsToHide, errorMessage);
  return data;
}

function enrichSchema(data: any, properties: any, required: any, columns: any, unique: any, columnsNotToBeFreezed: any, columnsToBeFreezed: any, columnsToHide: any, errorMessage: any) {

  // Sort columns based on orderNumber, using name as tie-breaker if orderNumbers are equal
  columns.sort((a: any, b: any) => {
    if (a.orderNumber === b.orderNumber) {
      return a.name.localeCompare(b.name);
    }
    return a.orderNumber - b.orderNumber;
  });

  required.sort((a: any, b: any) => {
    if (a.orderNumber === b.orderNumber) {
      return a.name.localeCompare(b.name);
    }
    return a.orderNumber - b.orderNumber;
  });

  const sortedRequiredColumns = required.map((column: any) => column.name);

  // Extract sorted property names
  const sortedPropertyNames = columns.map((column: any) => column.name);

  // Update data with new properties and required fields
  data.properties = properties;
  data.required = sortedRequiredColumns;
  data.columns = sortedPropertyNames;
  data.unique = unique;
  data.errorMessage = errorMessage;
  data.columnsNotToBeFreezed = columnsNotToBeFreezed;
  data.columnsToBeFreezed = columnsToBeFreezed;
  data.columnsToHide = columnsToHide;
}


// Function to handle getting boundary codes
async function getAutoGeneratedBoundaryCodesHandler(boundaryList: any, childParentMap: Map<{ key: string; value: string; }, { key: string; value: string; } | null>, elementCodesMap: any, countMap: any, request: any,hierarchy?:any) {
  try {
    // Get updated element codes map
    logger.info("Auto Generation of Boundary code begins for the user uploaded sheet")
    const updatedelementCodesMap = await getAutoGeneratedBoundaryCodes(boundaryList, childParentMap, elementCodesMap, countMap, request,hierarchy);
    return updatedelementCodesMap; // Return the updated element codes map
  } catch (error) {
    // Log and propagate the error
    console.error("Error in getBoundaryCodesHandler:", error);
    throw error;
  }
}
/**
 * Function to generate auto-generated boundary codes based on boundary list, child-parent mapping,
 * element codes map, count map, and request information.
 * @param boundaryList List of boundary data
 * @param childParentMap Map of child-parent relationships
 * @param elementCodesMap Map of element codes
 * @param countMap Map of counts for each element
 * @param request HTTP request object
 * @returns Updated element codes map
 */
async function getAutoGeneratedBoundaryCodes(boundaryList: any, childParentMap: any, elementCodesMap: any, countMap: any, request: any,hierarchy?:any) {
  // Initialize an array to store column data
  const columnsData: { key: string, value: string }[][] = [];
  // Extract unique elements from each column
  for (const row of boundaryList) {
    const rowMap = new Map<string, any>();
  row.forEach((obj:any) => rowMap.set(obj.key, obj.value));

  // Step 2: Find last present hierarchy index
  let lastPresentHierarchyIndex = -1;
  for (let i = 0; i < hierarchy.length; i++) {
    const level = hierarchy[i];
    if (rowMap.get(level)) {
      lastPresentHierarchyIndex = i;
    }
  }
  for (let i = 0; i <= lastPresentHierarchyIndex; i++) {
    const level = hierarchy[i];
    const element = row.find((obj:any) => obj.key === level);

    if (!columnsData[i]) {
      columnsData[i] = [];
    }
    const existingElement = columnsData[i].find((existing: any) => _.isEqual(existing, element));
    if (!existingElement) {
      columnsData[i].push(element);
    }
    };
  }

  // Iterate over columns to generate boundary codes
  for (let i = 0; i < columnsData.length; i++) {
    const column = columnsData[i] || [];
    for (const element of column) {
      if (!findMapValue(elementCodesMap, element) && element.value !== '') {
        const parentElement = findMapValue(childParentMap, element);
        if (parentElement !== undefined && parentElement !== null) {
          const parentBoundaryCode = findMapValue(elementCodesMap, parentElement);
          const currentCount = (findMapValue(countMap, parentElement) || 0) + 1;
          countMap.set(parentElement, currentCount);

          const code = generateElementCode(
            currentCount,
            parentElement,
            parentBoundaryCode,
            element.value,
            config.excludeBoundaryNameAtLastFromBoundaryCodes,
            childParentMap,
            elementCodesMap
          );

          elementCodesMap.set(element, code); // Store the code of the element in the map
        } else {
          // Generate default code if parent code is not found
          const prefix = config?.excludeHierarchyTypeFromBoundaryCodes
            ? element.value.toString().substring(0, 2).toUpperCase()
            : `${(request?.body?.ResourceDetails?.hierarchyType + "_").toUpperCase()}${element.value.toString().substring(0, 2).toUpperCase()}`;

          elementCodesMap.set(element, prefix);
        }
      }
    }
  }
  modifyElementCodesMap(elementCodesMap); // Modify the element codes map
  return elementCodesMap; // Return the updated element codes map
}

/**
 * Asynchronously creates boundary entities based on the provided request and boundary map.
 * @param request The HTTP request object.
 * @param boundaryMap Map of boundary names to codes.
 */
async function createBoundaryEntities(request: any, boundaryMap: Map<any, any>) {
  try {
    const updatedBoundaryMap: Array<{ key: string, value: string }> = Array.from(boundaryMap).map(([key, value]) => ({ key: key.value, value: value }));
    // Create boundary entities
    const requestBody = { "RequestInfo": request.body.RequestInfo } as { RequestInfo: any; Boundary?: any };
    const boundaries: any[] = [];
    const codesFromResponse: any = [];
    const boundaryCodes: any[] = [];
    Array.from(boundaryMap.entries()).forEach(([, boundaryCode]) => {
      boundaryCodes.push(boundaryCode);
    });
    const boundaryEntitiesCreated: any[] = [];
    const boundaryEntityCreateChunkSize = 200;
    const chunkSize = 20;
    const boundaryCodeChunks = [];
    for (let i = 0; i < boundaryCodes.length; i += chunkSize) {
      boundaryCodeChunks.push(boundaryCodes.slice(i, i + chunkSize));
    }

    for (const chunk of boundaryCodeChunks) {
      const string = chunk.join(', ');
      const boundaryEntityResponse = await httpRequest(config.host.boundaryHost + config.paths.boundaryServiceSearch, request.body, { tenantId: request?.body?.ResourceDetails?.tenantId, codes: string });
      const boundaryCodesFromResponse = boundaryEntityResponse.Boundary.flatMap((boundary: any) => boundary.code.toString());
      codesFromResponse.push(...boundaryCodesFromResponse);
    }

    const codeSet = new Set(codesFromResponse);// Creating a set and filling it with the codes from the response
    for (const { key: boundaryName, value: boundaryCode } of updatedBoundaryMap) {
      if (!codeSet.has(boundaryCode.toString())) {
        const boundary = {
          tenantId: request?.body?.ResourceDetails?.tenantId,
          code: boundaryCode,
          geometry: null,
          additionalDetails: {
            name: boundaryName
          }
        };
        boundaries.push(boundary);
      }
    };
    if (!(boundaries.length === 0)) {
      for (let i = 0; i < boundaries.length; i += boundaryEntityCreateChunkSize) {
        requestBody.Boundary = boundaries.slice(i, i + boundaryEntityCreateChunkSize);
        const response = await httpRequest(`${config.host.boundaryHost}boundary-service/boundary/_create`, requestBody, {}, 'POST',);
        boundaryEntitiesCreated.push(response)
      }
      logger.info('Boundary entities created');
      logger.debug('Boundary entities response: ' + getFormattedStringForDebug(boundaryEntitiesCreated));
    }
    else {
      // throwError("COMMON", 400, "VALIDATION_ERROR", "Boundary entity already present in the system");
      logger.info("Boundary Entities are already in the system")
    }
  } catch (error) {
    throwError("COMMMON", 500, "INTERNAL_SERVER_ERROR", "Error while Boundary Entity Creation")
  }
}

function modifyElementCodesMap(elementCodesMap: any) {
  const set = new Set<string>();
  const specialCharsRegex = /[^\w]/g; // Replace any non-word character
  const maxLength = 59; // Max length allowed for unique values

  elementCodesMap.forEach((value: any, key: any) => {
    let modifiedValue = value.replace(specialCharsRegex, '_').trim(); // Replace special characters and trim
    if (modifiedValue.length > maxLength) {
      logger.info(`Length of ${modifiedValue} is greater than max length ${maxLength}, so it will be truncated to ${maxLength} characters.`);
    }
    modifiedValue = modifiedValue.substring(0, maxLength); // Ensure length is at most 59
    let modifiedTempValue = modifiedValue; // Store the base modified value
    let count = 1;

    // Ensure uniqueness and valid length
    while (set.has(modifiedValue)) {
      let suffix = `_${count}`;
      let allowedLength = maxLength - suffix.length;
      modifiedValue = modifiedTempValue.substring(0, allowedLength) + suffix;
      count++;
    }

    set.add(modifiedValue); // Store the unique value
    elementCodesMap.set(key, modifiedValue); // Update the map
  });
}

/**
 * Function to generate an element code based on sequence, parent code, and element.
 * @param sequence Sequence number
 * @param parentElement Parent element
 * @param parentBoundaryCode Parent boundary code
 * @param element Element
 * @param excludeBoundaryNameAtLastFromBoundaryCodes Whether to exclude boundary name at last
 * @param childParentMap Map of child to parent elements
 * @param elementCodesMap Map of elements to their codes
 * @returns Generated element code
 */
function generateElementCode(sequence: any, parentElement: any, parentBoundaryCode: any, element: any, excludeBoundaryNameAtLastFromBoundaryCodes?: any, childParentMap?: any, elementCodesMap?: any) {
  // Pad single-digit numbers with leading zero
  const paddedSequence = sequence.toString().padStart(2, "0");
  let code;

  if (excludeBoundaryNameAtLastFromBoundaryCodes) {
    code = `${parentBoundaryCode.toUpperCase()}_${paddedSequence}`;
  } else {
    const grandParentElement = findMapValue(childParentMap, parentElement);
    if (grandParentElement != null && grandParentElement != undefined) {
      const lastUnderscoreIndex = parentBoundaryCode ? parentBoundaryCode.lastIndexOf('_') : -1;
      const parentBoundaryCodeTrimmed = lastUnderscoreIndex !== -1 ? parentBoundaryCode.substring(0, lastUnderscoreIndex) : parentBoundaryCode;
      code = `${parentBoundaryCodeTrimmed.toUpperCase()}_${paddedSequence}_${element.toString().toUpperCase()}`;
    } else {
      code = `${parentBoundaryCode.toUpperCase()}_${paddedSequence}_${element.toString().toUpperCase()}`;
    }
  }

  return code.trim();
}

export function getJsonData(sheetData: any, getRow = false, getSheetName = false, sheetName = "sheet1") {
  const jsonData: any[] = [];
  const headers = sheetData[0]; // Extract the headers from the first row

  for (let i = 1; i < sheetData.length; i++) {
    const rowData: any = {};
    const row = sheetData[i];
    if (row) {
      for (let j = 0; j < headers.length; j++) {
        const key = headers[j];
        const value = row[j] === undefined || row[j] === "" ? "" : row[j];
        if (value || value === 0) {
          rowData[key] = value;
        }
      }
      if (Object.keys(rowData).length > 0) {
        if (getRow) rowData["!row#number!"] = i + 1;
        if (getSheetName) rowData["!sheet#name!"] = sheetName;
        jsonData.push(rowData);
      }
    }
  };
  return jsonData;
}

// Function to create Excel sheet and upload it
async function createAndUploadFile(
  updatedWorkbook: any,
  request: any,
  tenantId?: any
) {
  let retries: any = 3;
  // Enrich metadatas
  if (request?.body?.RequestInfo && request?.query?.campaignId) {
    enrichTemplateMetaData(updatedWorkbook, getLocaleFromRequestInfo(request?.body?.RequestInfo), request?.query?.campaignId);
  }
  while (retries--) {
    try {
      // Write the updated workbook to a buffer
      const buffer = await updatedWorkbook.xlsx.writeBuffer();

      // Create form data for file upload
      const formData = new FormData();
      formData.append("file", buffer, "filename.xlsx");
      formData.append(
        "tenantId",
        tenantId ? tenantId : request?.body?.RequestInfo?.userInfo?.tenantId
      );
      formData.append("module", "HCM-ADMIN-CONSOLE-SERVER");

      // Make HTTP request to upload file
      var fileCreationResult = await httpRequest(
        config.host.filestore + config.paths.filestore,
        formData,
        undefined,
        undefined,
        undefined,
        {
          "Content-Type": "multipart/form-data",
          "auth-token": request?.body?.RequestInfo?.authToken || request?.RequestInfo?.authToken,
        }
      );

      // Extract response data
      const responseData = fileCreationResult?.files;
      if (responseData) {
        return responseData;
      }
    }
    catch (error: any) {
      console.error(`Attempt failed:`, error.message);

      // Add a delay before the next retry (2 seconds)
      await new Promise((resolve) => setTimeout(resolve, 5000));
    }
  }
  throw new Error("Error while uploading excel file: INTERNAL_SERVER_ERROR");
}


export{getSheetData,getAutoGeneratedBoundaryCodesHandler,createBoundaryEntities,createBoundaryRelationship
    ,createExcelSheet , createAndUploadFile ,getBoundarySheetData ,getConfigurableColumnHeadersBasedOnCampaignTypeForBoundaryManagement
};