import { EXCEL, GEOJSON, SHAPEFILE, commonColumn } from "../configs/constants";

export const calculateAggregateValue = (aggregateName, dataToShow) => {
  if (!aggregateName || !dataToShow || dataToShow.length === 0) return;
  let aggregateNameList = aggregateName;
  if (typeof aggregateName !== "object") aggregateNameList = { name: aggregateName, entities: [aggregateName] };
  let aggregateData = 0;
  if (aggregateNameList)
    for (const item of aggregateNameList.entities) {
      const columnIndex = dataToShow?.[0].indexOf(item);
      dataToShow.slice(1).forEach((e) => {
        if (e?.[columnIndex]) aggregateData = aggregateData + Number(e[columnIndex]);
      });
    }
  return aggregateData;
};

export const fetchMicroplanData = (microplanData, campaignType, validationSchemas) => {
  if (!microplanData) return [];

  let combinesDataList = [];
  // Check if microplanData and its upload property exist
  if (microplanData?.upload) {
    let files = microplanData?.upload;
    // Loop through each file in the microplan upload
    for (let fileData of files) {
      const schema = getSchema(campaignType, fileData.fileType, fileData.templateIdentifier, validationSchemas);

      // Check if the file is not part of boundary or layer data origins
      if (!fileData.active || !fileData.fileType || !fileData?.section) continue; // Skip files with errors or missing properties

      // Check if file contains latitude and longitude columns
      if (fileData?.data) {
        // Check file type and update data availability accordingly
        switch (fileData?.fileType) {
          case EXCEL: {
            // extract dada
            const mergedData = schema?.template?.hierarchyLevelWiseSheets ? Object.values(fileData?.data).flat() : Object.values(fileData?.data)?.[0];

            let commonColumnIndex = mergedData?.[0]?.indexOf(commonColumn);

            let uniqueEntries;
            if (commonColumnIndex !== undefined)
              uniqueEntries = schema?.template?.hierarchyLevelWiseSheets
                ? Array.from(new Map(mergedData.map((entry) => [entry[commonColumnIndex], entry])).values())
                : mergedData;
            if (uniqueEntries) combinesDataList.push(uniqueEntries);
            break;
          }
          case GEOJSON:
          case SHAPEFILE: {
            // Extract keys from the first feature's properties
            let keys = Object.keys(fileData?.data.features[0].properties);

            // Extract corresponding values for each feature
            const values = fileData?.data?.features.map((feature) => {
              // list with features added to it
              const temp = keys.map((key) => {
                // if (feature.properties[key] === "") {
                //   return null;
                // }
                return feature.properties[key];
              });
              return temp;
            });

            let data = [keys, ...values];
            combinesDataList.push(data);
          }
        }
      }
    }
  }
  return combinesDataList;
};

// get schema for validation
export const getSchema = (campaignType, type, section, schemas) => {
  return schemas.find((schema) =>
    schema.campaignType
      ? schema.campaignType === campaignType && schema.type === type && schema.section === section
      : schema.type === type && schema.section === section
  );
};

export const fetchMicroplanPreviewData = (campaignType, microplanData, validationSchemas, hierarchy) => {
  try {
    const filteredSchemaColumns = getFilteredSchemaColumnsList(campaignType, microplanData, validationSchemas, hierarchy);
    const fetchedData = fetchMicroplanData(microplanData, campaignType, validationSchemas);
    const dataAfterJoins = performDataJoins(fetchedData, filteredSchemaColumns);
    return dataAfterJoins;
  } catch (error) {
    console.error("Error in fetch microplan data: ", error.message);
  }
};

const getFilteredSchemaColumnsList = (campaignType, microplanData, validationSchemas, hierarchy) => {
  let filteredSchemaColumns = getRequiredColumnsFromSchema(campaignType, microplanData, validationSchemas) || [];
  if (hierarchy) {
    filteredSchemaColumns = [...hierarchy, commonColumn, ...filteredSchemaColumns.filter((e) => e !== commonColumn)];
  }
  return filteredSchemaColumns;
};

const performDataJoins = (fetchedData, filteredSchemaColumns) => {
  return fetchedData.reduce((accumulator, currentData, index) => {
    if (index === 0) {
      return innerJoinLists(currentData, null, commonColumn, filteredSchemaColumns);
    }
    return innerJoinLists(accumulator, currentData, commonColumn, filteredSchemaColumns);
  }, null);
};

export const filterObjects = (arr1, arr2) => {
  if (!arr1 || !arr2) return [];
  // Create a new array to store the filtered objects
  let filteredArray = [];

  // Iterate through the first array
  arr1.forEach((obj1) => {
    // Find the corresponding object in the second array
    let obj2 = _.cloneDeep(arr2.find((item) => item.key === obj1.key));

    // If the object with the same key is found in the second array and their values are the same
    if (obj2 && obj1.value !== obj2.value) {
      // Push the object to the filtered array
      obj1.oldValue = obj2.value;
      filteredArray.push(obj1);
    }
  });

  return filteredArray;
};

export const useHypothesis = (tempHypothesisList, hypothesisAssumptionsList) => {
  // Handles the change in hypothesis value
  const valueChangeHandler = (e, setTempHypothesisList, boundarySelections, setToast, t) => {
    // Checks it the boundary filters at at root level ( given constraints )
    if (Object.keys(boundarySelections).length !== 0 && Object.values(boundarySelections)?.every((item) => item?.length !== 0))
      return setToast({ state: "error", message: t("HYPOTHESIS_CAN_BE_ONLY_APPLIED_ON_ADMIN_LEVEL_ZORO") });

    // validating user input
    if (e?.newValue.includes("+") || e?.newValue.includes("e")) return;
    if ((e?.newValue < 0 || e.newValue > 10000000000) && e?.newValue !== "") return;
    let value;
    const decimalIndex = e.newValue.indexOf(".");
    if (decimalIndex !== -1) {
      const numDecimals = e.newValue.length - decimalIndex - 1;
      if (numDecimals <= 2) {
        value = e.newValue;
      } else if (numDecimals > 2) {
        value = e.newValue.substring(0, decimalIndex + 3);
      }
    } else value = parseFloat(e.newValue);
    value = !isNaN(value) ? value : "";

    // update the state with user input
    let newhypothesisEntityIndex = hypothesisAssumptionsList.findIndex((item) => item?.id === e?.item?.id);
    let unprocessedHypothesisList = _.cloneDeep(tempHypothesisList);
    if (newhypothesisEntityIndex !== -1) unprocessedHypothesisList[newhypothesisEntityIndex].value = value;
    setTempHypothesisList(unprocessedHypothesisList);
  };

  return {
    valueChangeHandler,
  };
};

const validateRequestBody = (body, state, campaignType, setLoaderActivation, setToast, setCheckDataCompletion, navigationEvent, t) => {
  if (!Digit.Utils.microplan.planConfigRequestBodyValidator(body, state, campaignType)) {
    setLoaderActivation(false);
    if (navigationEvent.name === "next") {
      setToast({
        message: t("ERROR_DATA_NOT_SAVED"),
        state: "error",
      });
      setCheckDataCompletion("false");
    } else {
      setCheckDataCompletion("perform-action");
    }
    return false;
  }
  return true;
};

const handleApiSuccess = (data, updateData, setLoaderActivation, setMicroplanData, status) => {
  updateData();
  setLoaderActivation(false);
  setMicroplanData((previous) => ({ ...previous, microplanStatus: status }));
};

const handleApiError = (error, variables, setLoaderActivation, setToast, status, cancleNavigation, updateData, t) => {
  setLoaderActivation(false);
  setToast({
    message: t("ERROR_DATA_NOT_SAVED"),
    state: "error",
  });
  if (status === "GENERATED") {
    cancleNavigation();
  } else {
    updateData();
  }
};

const constructRequestBody = (microplanData, operatorsObject, MicroplanName, campaignId, status) => {
  const body = Digit.Utils.microplan.mapDataForApi(microplanData, operatorsObject, MicroplanName, campaignId, status);
  body.PlanConfiguration["id"] = microplanData?.planConfigurationId;
  body.PlanConfiguration["auditDetails"] = microplanData?.auditDetails;
  return body;
};

export const updateHyothesisAPICall = async (
  microplanData,
  setMicroplanData,
  operatorsObject,
  MicroplanName,
  campaignId,
  UpdateMutate,
  setToast,
  updateData,
  setLoaderActivation,
  status,
  cancleNavigation,
  state,
  campaignType,
  navigationEvent,
  setCheckDataCompletion,
  t
) => {
  try {
    const body = constructRequestBody(microplanData, operatorsObject, MicroplanName, campaignId, status);
    const isValid = validateRequestBody(body, state, campaignType, setLoaderActivation, setToast, setCheckDataCompletion, navigationEvent, t);
    if (!isValid) return;

    await UpdateMutate(body, {
      onSuccess: (data) => handleApiSuccess(data, updateData, setLoaderActivation, setMicroplanData, status),
      onError: (error, variables) => handleApiError(error, variables, setLoaderActivation, setToast, status, cancleNavigation, updateData, t),
    });
  } catch (error) {
    setLoaderActivation(false);
    setToast({
      message: t("ERROR_DATA_NOT_SAVED"),
      state: "error",
    });
  }
};

// get schema for validation
export const getRequiredColumnsFromSchema = (campaignType, microplanData, schemas) => {
  if (!schemas || !microplanData || !microplanData?.upload || !campaignType) return [];
  const sortData = [];
  if (microplanData?.upload) {
    for (const value of microplanData.upload) {
      if (value.active && value?.error === null) {
        sortData.push({ section: value.section, fileType: value?.fileType });
      }
    }
  }
  const filteredSchemas =
    schemas?.filter((schema) => {
      if (schema.campaignType) {
        return schema.campaignType === campaignType && sortData.some((entry) => entry.section === schema.section && entry.fileType === schema.type);
      }
      return sortData.some((entry) => entry.section === schema.section && entry.fileType === schema.type);
    }) || [];

  let finalData = [];
  let tempdata;

  tempdata = filteredSchemas
    ?.flatMap((item) =>
      Object.entries(item?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.isRuleConfigureInputs && value?.toShowInMicroplanPreview) {
          acc.push(key);
        }
        return acc;
      }, [])
    )
    .filter((item) => !!item);
  finalData = [...finalData, ...tempdata];

  tempdata = filteredSchemas
    ?.flatMap((item) =>
      Object.entries(item?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.toShowInMicroplanPreview) acc.push(key);
        return acc;
      }, [])
    )
    .filter((item) => !!item);
  finalData = [...finalData, ...tempdata];
  return [...new Set(finalData)];
};

/**
 * Combines two datasets based on a common column, duplicating rows from data1 for each matching row in data2.
 * The final dataset's columns and their order are determined by listOfColumnsNeededInFinalData.
 * If data2 is not provided, rows from data1 are included with null values for missing columns.
 */
export const innerJoinLists = (data1, data2, commonColumnName, listOfColumnsNeededInFinalData) => {
  // Error handling: Check if data1 array is provided
  if (!Array.isArray(data1)) {
    throw new Error("The first data input must be an array.");
  }

  // Error handling: Check if common column name is provided
  if (typeof commonColumnName !== "string") {
    throw new Error("Common column name must be a string.");
  }

  // Error handling: Check if listOfColumnsNeededInFinalData is provided and is an array
  if (!Array.isArray(listOfColumnsNeededInFinalData)) {
    throw new Error("listOfColumnsNeededInFinalData must be an array.");
  }

  // Find the index of the common column in the first dataset
  const commonColumnIndex1 = data1[0].indexOf(commonColumnName);

  // Error handling: Check if common column exists in the first dataset
  if (commonColumnIndex1 === -1) {
    throw new Error(`Common column "${commonColumnName}" not found in the first dataset.`);
  }

  let commonColumnIndex2 = -1;
  const data2Map = new Map();
  if (data2) {
    // Find the index of the common column in the second dataset
    commonColumnIndex2 = data2[0].indexOf(commonColumnName);

    // Error handling: Check if common column exists in the second dataset
    if (commonColumnIndex2 === -1) {
      throw new Error(`Common column "${commonColumnName}" not found in the second dataset.`);
    }

    // Create a map for the second dataset for quick lookup by the common column value
    for (let i = 1; i < data2.length; i++) {
      const row = data2[i];
      const commonValue = row[commonColumnIndex2];
      if (!data2Map.has(commonValue)) {
        data2Map.set(commonValue, []);
      }
      data2Map.get(commonValue).push(row);
    }
  }

  // Determine the headers for the final combined dataset based on listOfColumnsNeededInFinalData
  const combinedHeaders = listOfColumnsNeededInFinalData.filter((header) => data1[0].includes(header) || data2?.[0].includes(header));

  // Combine rows
  const combinedData = [combinedHeaders];
  const addedCommonValues = new Set();
  for (let i = 1; i < data1.length; i++) {
    const row1 = data1[i];
    const commonValue = row1[commonColumnIndex1];
    const rows2 = data2 ? data2Map.get(commonValue) || [[null]] : [[null]]; // Handle missing common values with a placeholder array of null

    // Check if rows2 is the placeholder array
    const isPlaceholderArray = rows2.length === 1 && rows2[0].every((value) => value === null);

    // Create combined rows for each row in data2
    if (isPlaceholderArray) {
      // If no corresponding row found in data2, use row from data1 with null values for missing columns
      const combinedRow = combinedHeaders.map((header) => {
        const index1 = data1[0].indexOf(header);
        return index1 !== -1 ? row1[index1] : null;
      });
      combinedData.push(combinedRow);
    } else {
      // If corresponding rows found in data2, combine each row from data2 with row from data1
      rows2.forEach((row2) => {
        const combinedRow = combinedHeaders.map((header) => {
          const index1 = data1[0].indexOf(header);
          const index2 = data2 ? data2[0].indexOf(header) : -1;
          return index1 !== -1 ? row1[index1] : index2 !== -1 ? row2[index2] : null;
        });
        combinedData.push(combinedRow);
      });
    }
    addedCommonValues.add(commonValue);
  }
  // Add rows from data2 that do not have a matching row in data1
  if (data2) {
    for (let i = 1; i < data2.length; i++) {
      const row2 = data2[i];
      const commonValue = row2[commonColumnIndex2];
      if (!addedCommonValues.has(commonValue)) {
        const combinedRow = combinedHeaders.map((header) => {
          // const index1 = data1[0].indexOf(header);
          const index2 = data2[0].indexOf(header);
          return index2 !== -1 ? row2[index2] : null;
        });
        combinedData.push(combinedRow);
      }
    }
  }

  return combinedData;
};

// function to filter the microplan data with respect to the hierarchy selected by the user
export const filterMicroplanDataToShowWithHierarchySelection = (data, selections, hierarchy, hierarchyIndex = 0) => {
  if (!selections || selections?.length === 0) return data;
  if (hierarchyIndex >= hierarchy?.length) return data;
  const filteredHirarchyLevelList = selections?.[hierarchy?.[hierarchyIndex]]?.map((item) => item?.name);
  if (!filteredHirarchyLevelList || filteredHirarchyLevelList?.length === 0) return data;
  const columnDataIndexForHierarchyLevel = data?.[0]?.indexOf(hierarchy?.[hierarchyIndex]);
  if (columnDataIndexForHierarchyLevel === -1) return data;
  const levelFilteredData = data.filter((item, index) => {
    if (index === 0) return true;
    if (item?.[columnDataIndexForHierarchyLevel] && filteredHirarchyLevelList.includes(item?.[columnDataIndexForHierarchyLevel])) return true;
    return false;
  });
  return filterMicroplanDataToShowWithHierarchySelection(levelFilteredData, selections, hierarchy, hierarchyIndex + 1);
};
