import _ from "lodash";
import { findChildren, findParent } from "../utils/processHierarchyAndData";
import { EXCEL, LOCALITY, commonColumn } from "../configs/constants";

const formatDates = (value, type) => {
  if (type !== "EPOC" && (!value || Number.isNaN(Number(value)))) {
    value = new Date();
  }
  switch (type) {
    case "date":
      return new Date(value)?.toISOString?.()?.split?.("T")?.[0];
    case "datetime":
      return new Date(value).toISOString();
    case "EPOC":
      return String(new Date(value)?.getTime());
  }
};

// get schema for validation
const getSchema = (campaignType, type, section, schemas) => {
  if (!campaignType || !type || !section || !schemas) return {};
  return schemas.find((schema) => {
    if (!schema.campaignType) {
      return schema.type === type && schema.section === section;
    }
    return schema.campaignType === campaignType && schema.type === type && schema.section === section;
  });
};

// Sorting 2 lists, The first list is a list of string and second one is list of Objects
const sortSecondListBasedOnFirstListOrder = (firstList, secondList) => {
  if (!firstList) return [];
  // Create a map to store the indices of elements in the first list
  const indexMap = {};
  firstList.forEach((value, index) => {
    indexMap[value] = index;
  });

  // Sort the second list based on the order of elements in the first list
  secondList.sort((objecta, objectb) => {
    // Get the mappedTo values of each object
    const mappedToA = objecta.mappedTo;
    const mappedToB = objectb.mappedTo;

    // Get the indices of mappedTo values in the first list
    const indexA = indexMap[mappedToA];
    const indexB = indexMap[mappedToB];

    // Compare the indices
    return indexA - indexB;
  });

  return secondList;
};

const computeGeojsonWithMappedProperties = ({ campaignType, fileType, templateIdentifier, validationSchemas }) => {
  const schemaData = getSchema(campaignType, fileType, templateIdentifier, validationSchemas);
  let schemaKeys;
  if (schemaData?.schema?.["Properties"]) schemaKeys = hierarchy.concat(Object.keys(schemaData.schema["Properties"]));
  // Sorting the resourceMapping list inorder to maintain the column sequence
  const sortedSecondList = sortSecondListBasedOnFirstListOrder(schemaKeys, resourceMapping);
  // Creating a object with input data with MDMS keys
  const newFeatures = fileData.data["features"].map((item) => {
    let newProperties = {};

    sortedSecondList.forEach((e) => {
      newProperties[e["mappedTo"]] = item["properties"][e["mappedFrom"]];
    });
    item["properties"] = newProperties;
    return item;
  });
  let data = fileData.data;
  data["features"] = newFeatures;
  return data;
};

const destroySessionHelper = (currentPath, pathList, sessionName) => {
  if (!pathList.includes(currentPath)) {
    sessionStorage.removeItem(`Digit.${sessionName}`);
  }
};

const convertGeojsonToExcelSingleSheet = (InputData, fileName) => {
  if (!InputData || !Array.isArray(InputData) || InputData.length === 0) {
    return null;
  }

  // Extract keys from the first feature's properties
  const keys = Object.keys(InputData?.[0]?.properties);

  if (!keys || keys.length === 0) {
    return null;
  }

  // Extract corresponding values for each feature
  const values = InputData?.map((feature) => {
    return keys.map((key) => feature.properties[key]);
  });

  // Group keys and values into the desired format
  return { [fileName]: [keys, ...values] };
};

const areObjectsEqual = (obj1, obj2) => {
  return obj1.name === obj2.name && obj1.code === obj2.code;
};

const computeDifferences = (data1, data2) => {
  const removed = {};
  const added = {};

  for (const key in data1) {
    if (Object.hasOwn(data2, key)) {
      removed[key] = data1[key].filter((item1) => !data2[key].some((item2) => areObjectsEqual(item1, item2)));
      added[key] = data2[key].filter((item2) => !data1[key].some((item1) => areObjectsEqual(item1, item2)));
    } else {
      removed[key] = data1[key];
      added[key] = [];
    }
  }

  for (const key in data2) {
    if (!data1.hasOwnProperty(key)) {
      added[key] = data2[key];
      removed[key] = [];
    }
  }

  return { removed, added };
};

const extractNames = (data) => {
  let names = [];

  for (let key in data) {
    if (Array.isArray(data[key])) {
      data[key].forEach((item) => {
        if (item.name) {
          names.push(item.name);
        }
      });
    }
  }

  return names;
};
// function that handles dropdown selection. used in: mapping and microplan preview
const handleSelection = (e, boundaryType, boundarySelections, hierarchy, setBoundarySelections, boundaryData, setIsLoading) => {
  setIsLoading(true);
  if (!e || !boundaryType) return;
  let selections = e.map((item) => item?.[1]);
  let newComputedSelection = { ...boundarySelections, [boundaryType]: selections };
  const { removed, added } = computeDifferences(boundarySelections, newComputedSelection);
  // for(const item in removed){
  if (removed && Object.keys(removed).length !== 0 && Object.values(removed)?.flatMap((item) => item).length !== 0) {
    let filteredRemoved = extractNames(removed);
    let children = Object.values(findChildren(filteredRemoved, Object.values(boundaryData)?.[0]?.hierarchicalData))?.map((item) => item?.name);
    for (const key in newComputedSelection) {
      newComputedSelection[key] = newComputedSelection[key].filter((item) => !children.includes(item?.name));
    }
  }
  setBoundarySelections(newComputedSelection);
};

// Preventing default action when we scroll on input[number] is that it increments or decrements the number
const inputScrollPrevention = (e) => {
  e.target.addEventListener(
    "wheel",
    function (e) {
      e.preventDefault();
    },
    { passive: false }
  );
};

// Construct api request body
const mapDataForApi = (data, Operators, microplanName, campaignId, status, reqType = "update") => {
  let files = [],
    resourceMapping = [];
  if (data && data.upload) {
    Object.values(data?.upload).forEach((item) => {
      if (!item || item.error || !item.filestoreId) return;
      if (reqType === "create" && !item?.active) return;
      const data = {
        active: item?.active,
        filestoreId: item?.filestoreId,
        inputFileType: item?.fileType,
        templateIdentifier: item?.section,
        id: item?.fileId,
      };
      files.push(data);
    });
    Object.values(data?.upload).forEach((item) => {
      if (reqType === "create" && item.resourceMapping && item.resourceMapping.every((item) => item.active === false)) return;
      if (
        !item ||
        !item.resourceMapping ||
        item.error ||
        !Array.isArray(item.resourceMapping) ||
        !item.resourceMapping.every((item) => item.mappedFrom) ||
        !item.resourceMapping.every((item) => item.mappedTo)
      )
        return;
      resourceMapping.push(item?.resourceMapping);
    });
    resourceMapping = resourceMapping.flatMap((inner) => inner);
  }

  // return a Create API body
  return {
    PlanConfiguration: {
      status,
      tenantId: Digit.ULBService.getStateId(),
      name: microplanName,
      executionPlanId: campaignId,
      files,
      assumptions: data?.hypothesis?.reduce((acc, item) => {
        if (reqType === "create" && !item?.active) return acc;
        if (item.key && item.value) {
          acc.push(JSON.parse(JSON.stringify(item)));
        }
        return acc;
      }, []),
      operations: data?.ruleEngine?.reduce((acc, item) => {
        if (reqType === "create" && !item?.active) return acc;
        if (!item.active && !item.operator && !item.output && !item.input && !item.assumptionValue) return acc;
        const data = JSON.parse(JSON.stringify(item));
        const operator = Operators.find((e) => e.name === data.operator);
        if (operator && operator.code) data.operator = operator?.code;
        acc.push(data);
        // }
        return acc;
      }, []),
      resourceMapping,
    },
  };
};

const resourceMappingAndDataFilteringForExcelFiles = (schemaData, hierarchy, selectedFileType, fileDataToStore, t, translatedData = true) => {
  let resourceMappingData = [];
  let newFileData = {};
  let toAddInResourceMapping;

  if (selectedFileType.id === EXCEL && fileDataToStore) {
    // Extract all unique column names from the first row of each sheet in fileDataToStore for resource mapping
    const columnForMapping = new Set(Object.values(fileDataToStore).flatMap((value) => value?.[0] || []));

    if (schemaData?.schema?.["Properties"]) {
      let toChange;
      if (LOCALITY && hierarchy[hierarchy?.length - 1] !== LOCALITY) {
        toChange = hierarchy[hierarchy?.length - 1];
      }

      // Get schema keys and hierarchy to map columns
      const schemaKeys = Object.keys(schemaData.schema["Properties"]).concat(hierarchy);

      schemaKeys.forEach((item) => {
        // Check if the column is present in the file, either in translated form or original form based on translatedData flag
        if ((translatedData && columnForMapping.has(t(item))) || (!translatedData && columnForMapping.has(item))) {
          // Special case for LOCALITY
          if (LOCALITY && toChange === item) {
            toAddInResourceMapping = {
              mappedFrom: t(item),
              mappedTo: LOCALITY,
            };
          }
          // Add the mapping information
          resourceMappingData.push({
            mappedFrom: t(item),
            mappedTo: item,
          });
        }
      });
    }

    // Filter and map the columns of fileDataToStore based on resource mapping
    Object.entries(fileDataToStore).forEach(([key, value]) => {
      let data = [];
      let headers = [];
      let toRemove = [];

      if (value && value.length > 0) {
        // Process header row
        value[0].forEach((item, index) => {
          // Find the corresponding mapped column name
          const mappedTo = resourceMappingData.find((e) => (translatedData && e.mappedFrom === item) || (!translatedData && e.mappedFrom === t(item)))
            ?.mappedTo;
          if (!mappedTo) {
            toRemove.push(index); // Mark column for removal if not mapped
            return;
          }
          headers.push(mappedTo); // Add mapped column name to headers
        });

        // Process data rows
        for (let i = 1; i < value?.length; i++) {
          let temp = [];
          for (let j = 0; j < value[i].length; j++) {
            if (!toRemove.includes(j)) {
              temp.push(value[i][j]); // Keep only the columns that are mapped
            }
          }
          data.push(temp);
        }
      }

      // Combine headers and data for each sheet
      newFileData[key] = [headers, ...data];
    });
  }

  // Finalize the resource mapping data
  resourceMappingData.pop();
  resourceMappingData.push(toAddInResourceMapping);

  return { tempResourceMappingData: resourceMappingData, tempFileDataToStore: newFileData };
};

const addResourcesToFilteredDataToShow = (previewData, resources, hypothesisAssumptionsList, formulaConfiguration, userEditedResources, t) => {
  // Clone the preview data to avoid mutating the original data
  const data = _.cloneDeep(previewData);

  // Helper function to check for user-edited data
  const checkUserEditedData = (commonColumnData, resourceName) => {
    if (userEditedResources && userEditedResources[commonColumnData]) {
      return userEditedResources[commonColumnData][resourceName];
    }
  };

  // Ensure the previewData has at least one row and the first row is an array
  if (!Array.isArray(data) || !Array.isArray(data[0])) {
    return [];
  }

  // Identify the index of the common column
  const conmmonColumnIndex = data[0].indexOf(commonColumn);
  if (conmmonColumnIndex === -1) {
    return [];
  }

  // Ensure resources is a valid array
  if (!Array.isArray(resources)) {
    return data;
  }

  // Process each row of the data
  const combinedData = data.map((item, index) => {
    if (!Array.isArray(item)) {
      return item;
    }

    if (index === 0) {
      // Add resource names to the header row
      resources.forEach((e) => item.push(e));
      return item;
    }

    // Process each resource for the current row
    resources.forEach((resourceName, resourceIndex) => {
      let savedData = checkUserEditedData(item[conmmonColumnIndex], resourceName);
      if (savedData !== undefined) {
        item.push(savedData);
      } else {
        let calculations = calculateResource(resourceName, item, formulaConfiguration, previewData[0], hypothesisAssumptionsList, t);
        if (calculations !== null) calculations = Math.round(calculations);
        item.push(calculations !== null && calculations !== undefined ? calculations : undefined);
      }
    });

    return item;
  });

  return combinedData;
};

const calculateResource = (resourceName, rowData, formulaConfiguration, headers, hypothesisAssumptionsList, t) => {
  let formula = formulaConfiguration?.find((item) => item?.active && item?.output === resourceName);
  if (!formula) return null;

  // Finding Input
  // check for Uploaded Data
  let inputValue = findInputValue(formula, rowData, formulaConfiguration, headers, hypothesisAssumptionsList, t);
  if (inputValue == undefined || inputValue === null) return null;
  let assumptionValue = hypothesisAssumptionsList?.find((item) => item?.active && item?.key === formula?.assumptionValue)?.value;
  if (assumptionValue == undefined) return null;

  return findResult(inputValue, assumptionValue, formula?.operator);
};

// function to find input value, it calls calculateResource fucntion recurcively until it get a proper value
const findInputValue = (formula, rowData, formulaConfiguration, headers, hypothesisAssumptionsList, t) => {
  const inputIndex = headers?.indexOf(formula?.input);
  if (inputIndex === -1 || !rowData[inputIndex]) {
    // let tempFormula = formulaConfiguration.find((item) => item?.output === formula?.input);
    return calculateResource(formula?.input, rowData, formulaConfiguration, headers, hypothesisAssumptionsList, t);
  } else return rowData[inputIndex];
};

const findResult = (inputValue, assumptionValue, operator) => {
  switch (operator) {
    case "DEVIDED_BY":
      if (assumptionValue === 0) return;
      return inputValue / assumptionValue;
    case "MULTIPLIED_BY":
      return inputValue * assumptionValue;
    case "ADDITION":
      return inputValue + assumptionValue;
    case "SUBSTRACTION":
      return inputValue - assumptionValue;
    case "RAISE_TO":
      return inputValue ** assumptionValue;
    default:
      return;
  }
};

const fetchData = (state, campaignType) => {
  let hypothesis = [];
  let rulesOutputs = [];
  let uploadList = [];

  hypothesis = state?.HypothesisAssumptions?.find((item) => item.campaignType === campaignType)?.assumptions;
  rulesOutputs = state?.RuleConfigureOutput?.find((item) => item.campaignType === campaignType)?.data;
  uploadList = state?.UploadConfiguration?.reduce((acc, item) => {
    if (item.required) acc.push(item.id);
    return acc;
  }, []);
  return { hypothesisList: hypothesis, rulesOutputs, uploadList };
};
const hypothesisCheck = (hypothesis, validList) => {
  if (hypothesis && Array.isArray(hypothesis) && hypothesis.length !== 0 && validList && Array.isArray(validList) && validList.length !== 0) {
    return hypothesis.filter((item) => item.active).every((item) => validList.includes(item.key));
  }
  return false;
};
const ruleOutputCheck = (rules, ruleOuputList) => {
  if (
    rules &&
    Array.isArray(rules) &&
    rules.filter((item) => item.active).length !== 0 &&
    ruleOuputList &&
    Array.isArray(ruleOuputList) &&
    ruleOuputList.length !== 0
  ) {
    return rules.filter((item) => item.active).every((item) => ruleOuputList.includes(item.output));
  }
  return false;
};
const emptyRuleCheck = (rules) => {
  return !rules || rules.filter((item) => item.active && Object.values(item)?.filter((e) => e === "").length === 0);
};
const ruleHypothesisCheck = (rules, ruleHypothesis) => {
  if (rules && Array.isArray(rules) && rules.length !== 0 && ruleHypothesis && Array.isArray(ruleHypothesis) && ruleHypothesis.length !== 0) {
    return rules.filter((item) => item.active).every((item) => ruleHypothesis.includes(item.assumptionValue));
  }
  return false;
};
const uploadCheck = (uploads, uploadList) => {
  if (uploads && Array.isArray(uploads) && uploads.length !== 0 && uploadList && Array.isArray(uploadList) && uploadList.length !== 0) {
    return uploads.some((item) => uploadList.includes(item.templateIdentifier) && item.active);
  }
  return false;
};
const planConfigRequestBodyValidator = (data, state, campaignType) => {
  if (!data || !campaignType || !state) return false;

  const { hypothesisList, rulesOutputs, uploadList } = fetchData(state, campaignType);
  let checks =
    // microplan name check
    (!data || !data.name) &&
    hypothesisCheck(data?.PlanConfiguration?.assumptions, hypothesisList) &&
    emptyRuleCheck(data?.PlanConfiguration?.operations) &&
    ruleOutputCheck(data?.PlanConfiguration?.operations, rulesOutputs) &&
    ruleHypothesisCheck(
      data?.PlanConfiguration?.operations,
      data?.PlanConfiguration?.assumptions?.filter((item) => item.active)?.map((item) => item.key)
    ) &&
    uploadCheck(data?.PlanConfiguration?.files, uploadList);
  return checks;
  // if()
};

const processDropdownForNestedMultiSelect = (dropDownOptions) => {
  if (!dropDownOptions) return dropDownOptions;
  const result = dropDownOptions.reduce((acc, item) => {
    const { parent, ...rest } = item;

    // Find the group by parentBoundaryType
    let group = acc.find((g) => g.name === parent?.name);

    // If not found, create a new group
    if (!group) {
      group = { name: parent?.name, options: [] };
      acc.push(group);
    }

    // Add the item to the options of the found/created group
    group.options.push(rest);

    return acc;
  }, []);
  return result;
};

const transformIntoLocalisationCode = (code) => {
  return code?.toUpperCase();
};

export default {
  formatDates,
  computeGeojsonWithMappedProperties,
  destroySessionHelper,
  mapDataForApi,
  inputScrollPrevention,
  handleSelection,
  convertGeojsonToExcelSingleSheet,
  resourceMappingAndDataFilteringForExcelFiles,
  sortSecondListBasedOnFirstListOrder,
  addResourcesToFilteredDataToShow,
  calculateResource,
  planConfigRequestBodyValidator,
  getSchema,
  processDropdownForNestedMultiSelect,
  transformIntoLocalisationCode,
};
