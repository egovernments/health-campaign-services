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
    } else if (error.request) {
      // Network error
      throw new Error("Network error while fetching facility data");
    }
    // Other errors
    throw new Error("Error while fetching facility data: " + error.message);
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
    if (index !== 0) {
      if (!item) {
        item = [];
      }
      const itemLength = item.length;
      while (item.length <= topIndex) {
        item.push("");
      }
      item.push(item[itemLength - 1]);
    } else {
      item.push(commonColumn);
    }

    return item;
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
  const columnSchema = schema.schema?.Properties || {};
  let newXlsxData = [];
  let columnList = [[], [], [], []]; // Initialize columnList with four empty arrays

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
  // If no hierarchyLevelName is provided, return the original data
  if (!hierarchyLevelName) return xlsxData;

  // Initialize an array to hold the result
  const result = [];

  // Array to store the row with empty hierarchy level value
  let emptyHierarchyRow = [];

  // Iterate over each sheet in the xlsxData
  for (const sheet of xlsxData) {
    const sheetData = sheet.data;

    // Find the index of the hierarchy level name in the header row
    const hierarchyLevelIndex = sheetData[0].indexOf(hierarchyLevelName);

    // If the hierarchy level name is not found, skip this sheet
    if (hierarchyLevelIndex === -1) {
      result.push(sheet);
      return result;
    }

    // Create a map to hold new sheets data based on hierarchy level values
    const sheetsMap = {};
    // Create a map to hold dangling data for each hierarchy value
    const danglingDataMap = {};
    // Flag to track if the last processed row had an empty hierarchy level value
    let lastWasEmpty = true;

    // Iterate through the sheet data starting from the second row (skipping header)
    for (let i = 1; i < sheetData.length; i++) {
      const row = sheetData[i];
      const hierarchyValue = row[hierarchyLevelIndex];

      // If the hierarchy value is not empty and there was previous empty data,
      if (emptyHierarchyRow.length && hierarchyValue !== "") {
        danglingDataMap[hierarchyValue] = emptyHierarchyRow;
      }

      // If hierarchy value is empty, store this row
      if (hierarchyValue === "" && lastWasEmpty) {
        emptyHierarchyRow.push(row);
      } else {
        // store the empty data in the danglingDataMap for the current hierarchy value
        if (emptyHierarchyRow.length && hierarchyValue === "") {
          emptyHierarchyRow = []; // Reset emptyHierarchyRow
        }
      }

      // If this hierarchy value hasn't been seen before, create a new sheet for it
      if (!sheetsMap[hierarchyValue] && hierarchyValue !== "") {
        // Include all rows with empty hierarchy level data or different hierarchy values
        sheetsMap[hierarchyValue] = {
          sheetName: hierarchyValue,
          data: [sheetData[0]], // Start with the header row
        };
      }

      // Include the current row if its hierarchy level data matches the sheet's hierarchy value
      if (hierarchyValue === row[hierarchyLevelIndex] && hierarchyValue !== "") {
        sheetsMap[hierarchyValue].data.push(row);
      }

      // Update the lastWasEmpty flag
      if (hierarchyValue === "" && !lastWasEmpty) {
        lastWasEmpty = true;
      } else if (hierarchyValue !== "") {
        lastWasEmpty = false;
      }
    }

    // Combine danglingDataMap with sheetsMap
    for (const key of Object.keys(danglingDataMap)) {
      if (sheetsMap[key]) {
        // Combine dangling data with existing sheet data
        sheetsMap[key].data = [sheetData[0], ...danglingDataMap[key], ...sheetsMap[key].data.slice(1)];
      } else {
        // Create a new sheet for dangling data
        sheetsMap[key] = {
          sheetName: key,
          data: [...danglingDataMap[key], sheetData[0]], // Include header row
        };
      }
    }

    // Convert the sheets map to an array of objects and add to the result
    result.push(...Object.values(sheetsMap));
  }

  return result || xlsxData;
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
  let additionalCols = [];
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
  xlsxData = [facilitySheet, ...xlsxData];
  return xlsxData;
};

/**
 *
 * @param {boolean} hierarchyLevelWiseSheets
 * @param {string} hierarchyLevelName , if district Wise is true, then this must be present,
 * @param {boolean} addFacilityData
 * @param {Object} schema
 *
 */
export const createTemplate = async ({
  hierarchyLevelWiseSheets = true,
  hierarchyLevelName,
  addFacilityData = false,
  schema,
  boundaries,
  tenantId,
  hierarchyType,
  t,
}) => {
  const rootBoundary = boundaries?.filter((boundary) => boundary.isRoot); // Retrieve session storage data once and store it in a variable
  const sessionData = Digit.SessionStorage.get("microplanHelperData") || {};
  let boundaryData = sessionData.filteredBoundaries;
  let filteredBoundaries;

  if (!boundaryData) {
    // Only fetch boundary data if not present in session storage
    boundaryData = await fetchBoundaryData(tenantId, hierarchyType, rootBoundary?.[0]?.code);
    filteredBoundaries = await filterBoundaries(boundaryData, boundaries);

    // Update the session storage with the new filtered boundaries
    Digit.SessionStorage.set("microplanHelperData", {
      ...sessionData,
      filteredBoundaries: filteredBoundaries,
    });
  } else {
    filteredBoundaries = boundaryData;
  }

  // const filteredBoundaryData = boundaryData;
  let xlsxData = [];
  // adding boundary data to xlsxData
  xlsxData = addBoundaryData(xlsxData, filteredBoundaries, hierarchyType);

  if (hierarchyLevelWiseSheets) {
    // district wise boundary Data sheets
    xlsxData = devideXlsxDataHierarchyLevelWise(xlsxData, hierarchyLevelName);
    if (addFacilityData) {
      // adding facility sheet
      const facilities = await getAllFacilities(tenantId);
      if (schema?.template?.facilitySchemaApiMapping)
        xlsxData = addFacilitySheet(xlsxData, schema?.template?.facilitySchemaApiMapping, facilities, schema, t);
      else xlsxData = addSchemaData(xlsxData, schema);
    } else {
      // not adding facility sheet
      // adding schema data to xlsxData
      xlsxData = addSchemaData(xlsxData, schema);
    }
  } else {
    // total boundary Data in one sheet
    if (addFacilityData) {
      // adding facility sheet
      const facilities = await getAllFacilities(tenantId);
      if (schema?.template?.facilitySchemaApiMapping)
        xlsxData = addFacilitySheet(xlsxData, schema?.template?.facilitySchemaApiMapping, facilities, schema, t);
      else {
        const facilitySheet = {
          sheetName: FACILITY_DATA_SHEET,
          data: [],
        };
        facilitySheet = addSchemaData([facilitySheet], schema, [commonColumn]);
        xlsxData = [...facilitySheet, ...xlsxData];
      }
    } else {
      // not adding facility sheet

      // adding schema data to xlsxData
      xlsxData = addSchemaData(xlsxData, schema);
    }
  }
  return xlsxData;
};
