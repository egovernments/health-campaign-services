import { BOUNDARY_DATA_SHEET, FACILITY_DATA_SHEET, SCHEMA_PROPERTIES_PREFIX, commonColumn } from "../configs/constants";

export const fetchBoundaryData = async (tenantId, hierarchyType, codes) => {
  // request for boundary relation api
  const reqCriteria = {
    url: "/boundary-service/boundary-relationships/_search",
    params: { tenantId, hierarchyType, codes, includeChildren: true },
    body: {},
  };
  let response;
  try {
    response = (await Digit.CustomService.getResponse(reqCriteria))?.TenantBoundary?.[0]?.boundary || {};
  } catch (error) {
    console.error("Error in fetching boundary Data: ", error.message);
  }
  return response;
};

export const getFacilities = async (params, body) => {
  // request for boundary relation api
  const reqCriteria = {
    url: "/facility/v1/_search",
    params: params,
    body: body,
  };
  let response;
  try {
    response = (await Digit.CustomService.getResponse(reqCriteria))?.Facilities || {};
  } catch (error) {
    if (error.response) {
      throw new Error(`Failed to fetch facility data: ${error.response.data.message}`);
    }
    if (error.request) {
      // Network error
      throw new Error("Network error while fetching facility data");
    }
    // Other errors
    throw new Error(`Error while fetching facility data: ${error.message}`);
  }
  return response;
};

// export const fetchColumnsFromMdms = (schema)=>{
//   return
// }

/**
 *
 * @param {*} xlsxData
 * @param {*} boundaryData
 * @returns xlsxData with boundary data added
 */
export const addBoundaryData = (xlsxData, boundaryData, hierarchyType) => {
  // Return the original data if there is no boundary data to add
  if (!boundaryData) return xlsxData;

  // Initialize the array to hold new data
  let newXlsxData = [];

  // Recursive function to convert boundary data into sheet format
  const convertBoundaryDataToSheets = (boundaryData, currentBoundaryPredecessor = [], hierarchyAccumulator = [], dataAccumulator = []) => {
    // Return if boundary data is not valid or not an array
    if (!boundaryData || !Array.isArray(boundaryData)) return;

    // Clone the current boundary predecessor to avoid modifying the original data
    const rowData = [...currentBoundaryPredecessor];
    // Clone the data accumulator to preserve the accumulated data
    let tempDataAccumulator = [...dataAccumulator];
    // Use a set to accumulate unique hierarchy levels
    let tempHierarchyAccumulator = new Set(hierarchyAccumulator);

    // Iterate over each item in the boundary data array
    for (const item of boundaryData) {
      if (item?.code) {
        // Create a new row with the current item's code
        const tempRow = [...rowData, item?.code];
        let response;
        // Add the current item's boundary type to the hierarchy
        tempHierarchyAccumulator.add(item.boundaryType);

        // If the current item has children, recursively process them
        if (item.children)
          response = convertBoundaryDataToSheets(item.children, tempRow, tempHierarchyAccumulator, [...tempDataAccumulator, tempRow]);

        // Update the accumulators with the response from the recursive call
        if (response) {
          tempDataAccumulator = response.tempDataAccumulator;
          tempHierarchyAccumulator = response.tempHierarchyAccumulator;
        }
      }
    }

    // Return the accumulated data and hierarchy
    return { tempDataAccumulator, tempHierarchyAccumulator };
  };

  // Convert the boundary data into sheet format and extract the sorted data and hierarchy
  let { tempDataAccumulator: sortedBoundaryDataForXlsxSheet, tempHierarchyAccumulator: hierarchy } = convertBoundaryDataToSheets(boundaryData);

  // Add the hierarchy as the first row of the sheet
  hierarchy = [...hierarchy].map((item) => `${hierarchyType}_${Digit.Utils.microplan.transformIntoLocalisationCode(item)}`);
  sortedBoundaryDataForXlsxSheet = [[...hierarchy], ...sortedBoundaryDataForXlsxSheet];

  // Determine the maximum row length to ensure all rows have the same length
  const topIndex = Math.max(...sortedBoundaryDataForXlsxSheet.map((row) => row.length)) - 1;

  // Ensure all rows are of the same length by filling them with empty strings
  sortedBoundaryDataForXlsxSheet = sortedBoundaryDataForXlsxSheet.map((item, index) => {
    let newItem = item;
    if (index !== 0) {
      if (!newItem) {
        newItem = [];
      }
      const itemLength = newItem.length;
      while (newItem.length <= topIndex) {
        newItem.push("");
      }
      newItem.push(newItem[itemLength - 1]);
    } else {
      newItem.push(commonColumn);
    }

    return newItem;
  });

  // Add the new sheet data to the original data
  newXlsxData = [...xlsxData, ...newXlsxData, { sheetName: BOUNDARY_DATA_SHEET, data: sortedBoundaryDataForXlsxSheet }];

  // Return the updated data
  return newXlsxData;
};

const fillDataWithBlanks = (data, tillRow) => {
  while (data.length < tillRow) {
    data.push([]);
  }

  const maxLength = Math.max(...data.map((row) => row.length));
  return data.map((row) => [...row, ...new Array(maxLength - row.length).fill("")]);
};
const generateLocalisationKeyForSchemaProperties = (code) => {
  if (!code) return code;
  return `${SCHEMA_PROPERTIES_PREFIX}_${code}`;
};
/**
 *
 * @param {array} xlsxData , xlsx data
 * @param {object} schema , schema to refer to
 * @returns {Array of Object} , xlsxData with schema data added
 *
 * adds schema data to sheets
 */
const addSchemaData = (xlsxData, schema, extraColumnsToAdd) => {
  if (!schema) return xlsxData;
  let columnSchema = schema.schema?.Properties || {};
  const newXlsxData = [];
  const columnList = [[], [], [], []]; // Initialize columnList with four empty arrays

  for (const [key, value] of Object.entries(columnSchema)) {
    if (key === commonColumn) continue;

    columnList[0].push(generateLocalisationKeyForSchemaProperties(key)); // Add key to the first array

    //   columnList[1].push(value.type || ""); // Add type to the second array

    //   columnList[2].push(value.isRequired ? "MANDATORY" : "OPTIONAL"); // Add requirement status to the third array

    //   columnList[3].push(value.pattern || ""); // Add pattern to the fourth array
  }

  if (extraColumnsToAdd) columnList[0].push(...extraColumnsToAdd);

  for (let { sheetName, data } of xlsxData) {
    data = fillDataWithBlanks(data, 4);
    columnList.forEach((item, index) => {
      // Append the new items to the row
      if (data[index]) {
        data[index] = [...data[index], ...item];
      } else {
        data[index] = [...item];
      }
    });

    newXlsxData.push({ sheetName, data });
  }

  return newXlsxData;
};

/**
 *
 * @param {Array of Object} xlsxData
 * @param {string} hierarchyLevelName
 */
const devideXlsxDataHierarchyLevelWise = (xlsxData, hierarchyLevelName) => {
  if (!hierarchyLevelName) return xlsxData; // Return original data if no hierarchy level name

  const result = []; // Initialize result array

  // Iterate over each sheet in the xlsxData
  for (const sheet of xlsxData) {
    const sheetData = sheet.data;
    const hierarchyLevelIndex = sheetData[0].indexOf(hierarchyLevelName);

    // If hierarchy level name not found, skip this sheet
    if (hierarchyLevelIndex === -1) {
      result.push(sheet);
      continue;
    }

    const { sheetsMap, danglingDataMap } = processSheetData(sheetData, hierarchyLevelIndex);

    // Combine danglingDataMap with sheetsMap
    for (const key of Object.keys(danglingDataMap)) {
      if (sheetsMap[key]) {
        sheetsMap[key].data = [sheetData[0], ...danglingDataMap[key], ...sheetsMap[key].data.slice(1)];
      } else {
        sheetsMap[key] = {
          sheetName: key,
          data: [...danglingDataMap[key], sheetData[0]],
        };
      }
    }

    // Add sheetsMap values to result
    result.push(...Object.values(sheetsMap));
  }

  return result.length > 0 ? result : xlsxData; // Return result or original data if result is empty
};

// Function to process sheet data and return sheetsMap and danglingDataMap
const processSheetData = (sheetData, hierarchyLevelIndex) => {
  const sheetsMap = {};
  const danglingDataMap = {};
  let emptyHierarchyRow = [];
  let lastWasEmpty = true;

  // Iterate through sheet data starting from the second row (skipping header)
  for (let i = 1; i < sheetData.length; i++) {
    const row = sheetData[i];
    const hierarchyValue = row[hierarchyLevelIndex];

    if (emptyHierarchyRow.length && hierarchyValue !== "") {
      danglingDataMap[hierarchyValue] = emptyHierarchyRow;
    }

    if (hierarchyValue === "" && lastWasEmpty) {
      emptyHierarchyRow.push(row);
    } else {
      emptyHierarchyRow = [];
    }

    if (!sheetsMap[hierarchyValue] && hierarchyValue !== "") {
      sheetsMap[hierarchyValue] = {
        sheetName: hierarchyValue,
        data: [sheetData[0]],
      };
    }

    if (hierarchyValue === row[hierarchyLevelIndex] && hierarchyValue !== "") {
      sheetsMap[hierarchyValue].data.push(row);
    }

    lastWasEmpty = hierarchyValue === "";
  }

  return { sheetsMap, danglingDataMap };
};

export const filterBoundaries = (boundaryData, boundaryFilters) => {
  if (!boundaryFilters) return boundaryData;
  // Define a helper function to recursively filter boundaries
  function filterRecursive(boundary) {
    // Find the filter that matches the current boundary
    const filter = boundaryFilters?.find((f) => f.code === boundary.code && f.type === boundary.boundaryType);

    // If no filter is found, return the boundary with its children filtered recursively
    if (!filter) {
      return {
        ...boundary,
        children: boundary.children.map(filterRecursive),
      };
    }

    // If the boundary has no children, handle the case where includeAllChildren is false
    if (!boundary.children.length) {
      // Return the boundary with an empty children array
      return {
        ...boundary,
        children: [],
      };
    }

    // If includeAllChildren is true, return the boundary with all children
    if (filter.includeAllChildren) {
      return {
        ...boundary,
        children: boundary.children.map(filterRecursive),
      };
    }

    // Filter children based on the filters
    const filteredChildren = boundary.children
      .filter((child) => boundaryFilters.some((f) => f.code === child.code && f.type === child.boundaryType))
      .map(filterRecursive);

    // Return the boundary with filtered children
    return {
      ...boundary,
      children: filteredChildren,
    };
  }

  // Map through the boundary data and apply the recursive filter function to each boundary
  const filteredData = boundaryData.map(filterRecursive);
  return filteredData;
};

/**
 * Retrieves all facilities for a given tenant ID.
 * @param tenantId The ID of the tenant.
 * @returns An array of facilities.
 */
async function getAllFacilities(tenantId) {
  // Retrieve all facilities for the given tenant ID
  const facilitySearchBody = {
    Facility: { isPermanent: true },
  };

  const facilitySearchParams = {
    limit: 50,
    offset: 0,
    tenantId: tenantId,
  };

  const searchedFacilities = [];
  let searchAgain = true;

  while (searchAgain) {
    const response = await getFacilities(facilitySearchParams, facilitySearchBody);
    if (response) {
      searchAgain = response.length >= 50;
      searchedFacilities.push(...response);
      facilitySearchParams.offset += 50;
    } else searchAgain = false;
  }

  return searchedFacilities;
}

const addFacilitySheet = (xlsxData, mapping, facilities, schema, t) => {
  if (!mapping) return xlsxData;
  // Create header row
  const headers = Object.keys(mapping);

  // Create data rows
  const dataRow = [];
  for (const facility of facilities) {
    facility.isPermanent = facility.isPermanent ? t("PERMAENENT") : t("TEMPORARY");
    dataRow.push(headers.map((header) => facility[mapping[header]]));
  }
  headers.push(commonColumn);
  const additionalCols = [];
  if (schema?.schema?.Properties) {
    const properties = Object.keys(schema.schema.Properties);
    for (const col of properties) {
      if (!headers.includes(col)) {
        additionalCols.push(col);
      }
    }
  }
  headers.push(...additionalCols);
  // Combine headers and data rows
  const arrayOfArrays = [headers.map((item) => generateLocalisationKeyForSchemaProperties(item)), ...dataRow];

  const facilitySheet = {
    sheetName: FACILITY_DATA_SHEET,
    data: arrayOfArrays,
  };
  const updatedXlsxData = [facilitySheet, ...xlsxData];
  return updatedXlsxData;
};

const addReadMeSheet = (xlsxData, readMeData, readMeSheetName) => {
  if (!readMeSheetName) return xlsxData;
  const data = readMeData.reduce((acc, item) => {
    if (item?.header) {
      acc.push([item.header], ...(item.points || []).map((item) => [item]), [], [], [], []);
    }
    return acc;
  }, []);

  const readMeSheet = {
    sheetName: readMeSheetName,
    data: [["MICROPLAN_TEMPLATE_README_MAIN_HEADER"], [], [], [], ...data],
  };
  xlsxData.unshift(readMeSheet);
  return xlsxData;
};

/**
 * @param {Object} options
 * @param {boolean} options.hierarchyLevelWiseSheets
 * @param {string} options.hierarchyLevelName
 * @param {boolean} options.addFacilityData
 * @param {Object} options.schema
 * @param {Object[]} options.boundaries
 * @param {string} options.tenantId
 * @param {string} options.hierarchyType
 * @param {Object} options.readMeData
 * @param {string} options.readMeSheetName
 * @param {string} options.t // Assuming t is some context or translation object
 */
export const createTemplate = async ({
  hierarchyLevelWiseSheets = true,
  hierarchyLevelName,
  addFacilityData = false,
  schema,
  boundaries,
  tenantId,
  hierarchyType,
  readMeData,
  readMeSheetName,
  t,
}) => {
  // Fetch or retrieve boundary data
  const filteredBoundaries = await fetchFilteredBoundaries(boundaries, tenantId, hierarchyType);

  // Initialize xlsxData array
  let xlsxData = [];

  // Add boundary data to xlsxData
  xlsxData = addBoundaryData(xlsxData, filteredBoundaries, hierarchyType);

  // Handle hierarchy level sheets
  if (hierarchyLevelWiseSheets) {
    xlsxData = devideXlsxDataHierarchyLevelWise(xlsxData, hierarchyLevelName);
  }

  // Handle facility data addition
  if (addFacilityData) {
    xlsxData = await addFacilityDataToSheets(xlsxData, schema, tenantId, t);
  } else {
    // If no facility data, add schema data directly
    xlsxData = addSchemaData(xlsxData, schema);
  }

  // Add readme sheet data if provided
  xlsxData = addReadMeSheet(xlsxData, readMeData, readMeSheetName);

  return xlsxData;
};

// Function to fetch filtered boundaries
const fetchFilteredBoundaries = async (boundaries, tenantId, hierarchyType) => {
  const rootBoundary = boundaries?.find((boundary) => boundary.isRoot);
  const sessionData = Digit.SessionStorage.get("microplanHelperData") || {};
  let boundaryData = sessionData.filteredBoundaries;

  if (!boundaryData) {
    boundaryData = await fetchBoundaryData(tenantId, hierarchyType, rootBoundary?.code);
    const filteredBoundaries = await filterBoundaries(boundaryData, boundaries);
    Digit.SessionStorage.set("microplanHelperData", {
      ...sessionData,
      filteredBoundaries: filteredBoundaries,
    });
    return filteredBoundaries;
  }
  return boundaryData;
};

// Function to add facility data to sheets
const addFacilityDataToSheets = async (xlsxData, schema, tenantId, t) => {
  const facilities = await getAllFacilities(tenantId);
  if (schema?.template?.facilitySchemaApiMapping) {
    return addFacilitySheet(xlsxData, schema.template.facilitySchemaApiMapping, facilities, schema, t);
  }
  // If no specific facility schema mapping, add default facility data
  const facilitySheet = {
    sheetName: FACILITY_DATA_SHEET,
    data: [],
  };
  return addSchemaData([facilitySheet], schema);
};
