import { Request } from "@egovernments/digit-ui-libraries";
import { parseXlsxToJsonMultipleSheetsForSessionUtil } from "../utils/exceltojson";
import JSZip from "jszip";
import * as XLSX from "xlsx";
import axios from "axios";
import shp from "shpjs";
import { EXCEL, GEOJSON, SHAPEFILE, ACCEPT_HEADERS, LOCALITY, commonColumn } from "../configs/constants";
import { addBoundaryData, fetchBoundaryData, filterBoundaries } from "./createTemplate";
import { handleExcelFile } from "./uploadUtils";

function handleExcelArrayBuffer(arrayBuffer, file) {
  return new Promise((resolve, reject) => {
    try {
      // Read the response as an array buffer
      // const arrayBuffer = response.arrayBuffer();

      // Convert the array buffer to binary string
      const data = new Uint8Array(arrayBuffer);
      const binaryString = String.fromCharCode.apply(null, data);

      // Parse the binary string into a workbook
      const workbook = XLSX.read(binaryString, { type: "binary" });

      // Assuming there's only one sheet in the workbook
      const sheetName = workbook.SheetNames[0];
      const sheet = workbook.Sheets[sheetName];

      // Convert the sheet to JSON object
      const jsonData = XLSX.utils.sheet_to_json(sheet);

      resolve(jsonData);
    } catch (error) {
      reject(error);
    }
  });
}

function shpToGeoJSON(shpBuffer, file) {
  return new Promise((resolve, reject) => {
    try {
      shp(shpBuffer)
        .then((geojson) => {
          resolve({ jsonData: geojson, file });
        })
        .catch((error) => reject(error));
    } catch (error) {
      reject(error);
    }
  });
}

function parseGeoJSONResponse(arrayBuffer, file) {
  return new Promise((resolve, reject) => {
    try {
      const decoder = new TextDecoder("utf-8");
      const jsonString = decoder.decode(arrayBuffer);
      const jsonData = JSON.parse(jsonString);
      resolve({ jsonData, file });
    } catch (error) {
      reject(error);
    }
  });
}

// Function to read blob data and parse it into JSON
function parseBlobToJSON(blob, file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();

    reader.onload = function (event) {
      const data = new Uint8Array(event.target.result);
      const workbook = XLSX.read(data, { type: "array" });
      const jsonData = {};

      workbook.SheetNames.forEach((sheetName) => {
        const sheetData = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName]);
        jsonData[sheetName] = sheetData;
      });

      resolve({ jsonData, file });
    };

    reader.onerror = function () {
      reject(new Error("Error reading the blob data"));
    };

    reader.readAsArrayBuffer(blob);
  });
}

export const updateSessionUtils = {
  computeSessionObject: async (row, state, additionalProps) => {
    const sessionObj = {};
    const setCurrentPage = () => {
      sessionObj.currentPage = {
        id: 0,
        name: "MICROPLAN_DETAILS",
        component: "MicroplanDetails",
        checkForCompleteness: true,
      };
    };

    //currently hardcoded
    const setMicroplanStatus = () => {
      sessionObj.status = {
        MICROPLAN_DETAILS: true,
        UPLOAD_DATA: true,
        HYPOTHESIS: true,
        FORMULA_CONFIGURATION: true,
      };
    };

    const setMicroplanDetails = () => {
      if (row.name) {
        sessionObj.microplanDetails = {
          name: row?.name,
        };
      }
    };

    const setMicroplanHypothesis = () => {
      if (row.assumptions.length > 0) {
        sessionObj.hypothesis = row.assumptions?.filter((item) => item?.active);
      }
    };

    const sortRules = (rules) => {
      // Step 1: Identify all unique rule outputs
      const allOutputs = [...new Set(rules.map((rule) => rule.output))];

      // Step 2: Build input-output relationships
      const inputOutputMap = new Map(); // Map to store input -> output relationship
      rules.forEach((rule) => {
        const { input, output } = rule;
        if (!inputOutputMap.has(input)) {
          inputOutputMap.set(input, []);
        }
        inputOutputMap.get(input).push(output);
      });

      // Step 3: Sort the output list based on dependencies
      const sortedOutputList = [];
      const visited = new Set();

      const dfs = (output) => {
        if (!visited.has(output)) {
          visited.add(output);
          if (inputOutputMap.has(output)) {
            inputOutputMap.get(output).forEach((input) => {
              dfs(input);
            });
          }
          sortedOutputList.push(output);
        }
      };

      // Sort outputs based on dependencies
      allOutputs.forEach((output) => {
        dfs(output);
      });

      // Reverse to get outputs in the correct order (outputs first)
      sortedOutputList.reverse();

      // Step 4: Arrange rules based on sorted output list
      const sortedRules = [];
      const ruleMap = new Map(rules.map((rule) => [rule.id, rule]));

      sortedOutputList.forEach((output) => {
        rules
          .filter((rule) => rule.output === output)
          .forEach((rule) => {
            sortedRules.push(rule);
          });
      });

      return sortedRules;
    };

    const setMicroplanRuleEngine = () => {
      const rulesList = state.UIConfiguration?.filter((item) => item.name === "ruleConfigure")?.[0]?.ruleConfigureOperators;
      let sortedRules = sortRules(row.operations);
      if (row.operations.length > 0) {
        sessionObj.ruleEngine = sortedRules?.map((item) => {
          return {
            ...item,
            operator: rulesList.filter((rule) => rule.code === item.operator)?.[0]?.name,
          };
        });
      }
    };

    const setDraftValues = () => {
      sessionObj.planConfigurationId = row?.id;
      sessionObj.auditDetails = row.auditDetails;
    };

    const fetchBoundaryDataWrapper = async (schemaData) => {
      let boundaryDataAgainstBoundaryCode = {};
      // if (!schemaData?.doHierarchyCheckInUploadedData) {
      try {
        const rootBoundary = additionalProps.campaignData?.boundaries?.filter((boundary) => boundary.isRoot); // Retrieve session storage data once and store it in a variable
        const sessionData = Digit.SessionStorage.get("microplanHelperData") || {};
        let boundaryData = sessionData.filteredBoundaries;
        let filteredBoundaries;
        if (!boundaryData) {
          // Only fetch boundary data if not present in session storage
          boundaryData = await fetchBoundaryData(
            await Digit.ULBService.getCurrentTenantId(),
            additionalProps.campaignData?.hierarchyType,
            rootBoundary?.[0]?.code
          );
          filteredBoundaries = await filterBoundaries(boundaryData, additionalProps.campaignData?.boundaries);

          // Update the session storage with the new filtered boundaries
          Digit.SessionStorage.set("microplanHelperData", {
            ...sessionData,
            filteredBoundaries: filteredBoundaries,
          });
        } else {
          filteredBoundaries = boundaryData;
        }
        const xlsxData = addBoundaryData([], filteredBoundaries, additionalProps.campaignData?.hierarchyType)?.[0]?.data;
        xlsxData.forEach((item, i) => {
          if (i === 0) return;
          let boundaryCodeIndex = xlsxData?.[0]?.indexOf(commonColumn);
          if (boundaryCodeIndex >= item.length) {
            // If boundaryCodeIndex is out of bounds, return the item as is
            boundaryDataAgainstBoundaryCode[item[boundaryCodeIndex]] = item.slice().map(additionalProps.t);
          } else {
            // Otherwise, remove the element at boundaryCodeIndex
            boundaryDataAgainstBoundaryCode[item[boundaryCodeIndex]] = item
              .slice(0, boundaryCodeIndex)
              .concat(item.slice(boundaryCodeIndex + 1))
              .map(additionalProps.t);
          }
        });
      } catch (error) {
        console.error(error?.message);
      }
      // }
      return boundaryDataAgainstBoundaryCode;
    };

    const handleGeoJson = async (file, result, upload, translatedData, active, processedData, shapefileOrigin = false) => {
      if (!file) {
        console.error(`${shapefileOrigin ? "Shapefile" : "Geojson"} file is undefined`);
        return upload;
      }

      const { inputFileType, templateIdentifier, filestoreId, id: fileId } = file || {};
      let uploadObject = createUploadObject(templateIdentifier, inputFileType, fileId, filestoreId, shapefileOrigin ? ".zip" : ".geojson", active);

      const schema = findSchema(inputFileType, templateIdentifier, additionalProps?.campaignType);
      if (!schema) {
        console.error("Schema got undefined while handling geojson at handleGeoJson");
        return [...upload, uploadObject];
      }

      await handleGeoJsonSpecific(schema, uploadObject, templateIdentifier, result, translatedData, filestoreId, processedData);
      upload.push(uploadObject);
      return upload;
    };

    const handleExcel = (file, result, upload, translatedData, active) => {
      if (!file) {
        console.error("Excel file is undefined");
        return upload;
      }

      const { inputFileType, templateIdentifier, filestoreId, id: fileId } = file || {};
      let uploadObject = createUploadObject(templateIdentifier, inputFileType, fileId, filestoreId, ".xlsx", active),
        schema = findSchema(inputFileType, templateIdentifier, additionalProps.campaignType);
      if (!schema) {
        console.error("Schema got undefined while handling excel at handleExcel");
        return [...upload, uploadObject];
      }

      uploadObject.data = result; //resultAfterMapping?.tempFileDataToStore;
      upload.push(uploadObject);
      return upload;
    };

    const createUploadObject = (templateIdentifier, inputFileType, fileId, filestoreId, extension, active) => ({
      id: fileId,
      templateIdentifier,
      section: templateIdentifier,
      fileName: `${templateIdentifier}${extension}`,
      fileType: inputFileType,
      file: null,
      fileId: fileId,
      filestoreId: filestoreId,
      error: null,
      resourceMapping: row?.resourceMapping?.filter((resourse) => resourse.filestoreId === filestoreId).map((item) => ({ ...item, filestoreId })),
      data: {},
      active,
    });

    const findSchema = (inputFileType, templateIdentifier, campaignType) => {
      return state?.Schemas?.find(
        (schema) =>
          schema.type === inputFileType && schema.section === templateIdentifier && (!schema.campaignType || schema.campaignType === campaignType)
      );
    };

    const handleGeoJsonSpecific = async (schema, upload, templateIdentifier, result, translatedData, filestoreId, processedData) => {
      let schemaKeys;
      if (schema?.schema?.["Properties"]) {
        schemaKeys = additionalProps.hierarchyData?.concat(Object.keys(schema.schema["Properties"]));
      }
      upload.data = result;
      if (processedData) return;
      const mappedToList = upload?.resourceMapping.map((item) => item.mappedTo);
      let sortedSecondList = Digit.Utils.microplan.sortSecondListBasedOnFirstListOrder(schemaKeys, upload?.resourceMapping);
      const newFeatures = result["features"].map((item) => {
        let newProperties = {};
        sortedSecondList
          ?.filter((resourse) => resourse.filestoreId === filestoreId)
          .forEach((e) => {
            newProperties[e["mappedTo"]] = item["properties"][e["mappedFrom"]];
          });
        item["properties"] = newProperties;
        return item;
      });
      upload.data.features = newFeatures;
      if (
        additionalProps.hierarchyData?.every(
          (item) =>
            !mappedToList.includes(`${additionalProps.campaignData?.hierarchyType}_${Digit.Utils.microplan.transformIntoLocalisationCode(item)}`)
        )
      ) {
        let boundaryDataAgainstBoundaryCode = await fetchBoundaryDataWrapper(schema);
        upload.data.features.forEach((feature) => {
          const boundaryCode = feature.properties.boundaryCode;
          let additionalDetails = {};
          for (let i = 0; i < additionalProps.hierarchyData?.length; i++) {
            if (boundaryDataAgainstBoundaryCode[boundaryCode]?.[i] || boundaryDataAgainstBoundaryCode[boundaryCode]?.[i] === "") {
              additionalDetails[additionalProps.hierarchyData?.[i]] = boundaryDataAgainstBoundaryCode[boundaryCode][i];
            } else {
              additionalDetails[additionalProps.hierarchyData?.[i]] = "";
            }
          }
          feature.properties = { ...additionalDetails, ...feature.properties };
        });
      }
    };

    const fetchFiles = async () => {
      const files = row?.files;
      if (!files || files.length === 0) {
        return [];
      }

      const promises = [];
      let storedData = [];
      for (const { filestoreId, inputFileType, templateIdentifier, id, active } of files) {
        if (!active) continue;
        const schemaData = findSchema(inputFileType, templateIdentifier, additionalProps?.campaignType);
        if (!schemaData) {
          console.error("Schema got undefined while handling geojson at handleGeoJson");
          return [...upload, uploadObject];
        }
        const boundaryDataAgainstBoundaryCode = {};
        let fileData = {
          filestoreId,
          inputFileType,
          templateIdentifier,
          id,
        };
        let dataInSsn = Digit.SessionStorage.get("microplanData")?.upload?.find((item) => item.active && item.id === id);
        if (dataInSsn && dataInSsn.filestoreId === filestoreId) {
          storedData.push({ file: fileData, jsonData: dataInSsn?.data, processedData: true, translatedData: false, active });
        } else {
          const promiseToAttach = axios
            .get("/filestore/v1/files/id", {
              responseType: "arraybuffer",
              headers: {
                "Content-Type": "application/json",
                Accept: ACCEPT_HEADERS[inputFileType],
                "auth-token": Digit.UserService.getUser()?.["access_token"],
              },
              params: {
                tenantId: Digit.ULBService.getCurrentTenantId(),
                fileStoreId: filestoreId,
              },
            })
            .then(async (res) => {
              if (inputFileType === EXCEL) {
                try {
                  const file = new Blob([res.data], { type: ACCEPT_HEADERS[inputFileType] });
                  const response = await handleExcelFile(
                    file,
                    schemaData,
                    additionalProps.hierarchyData.map(
                      (item) => `${additionalProps.campaignData?.hierarchyType}_${Digit.Utils.microplan.transformIntoLocalisationCode(item)}`
                    ),
                    { id: inputFileType },
                    boundaryDataAgainstBoundaryCode,
                    () => {},
                    additionalProps.t,
                    additionalProps.campaignData,
                    additionalProps.readMeSheetName
                  );
                  let fileData = {
                    filestoreId,
                    inputFileType,
                    templateIdentifier,
                    id,
                  };

                  return { jsonData: response.fileDataToStore, file: fileData, translatedData: true, active };
                } catch (error) {
                  console.error(error);
                }
              } else if (inputFileType === GEOJSON) {
                let response = await parseGeoJSONResponse(res.data, {
                  filestoreId,
                  inputFileType,
                  templateIdentifier,
                  id,
                });
                return { ...response, translatedData: true, active };
              } else if (inputFileType === SHAPEFILE) {
                const geoJson = await shpToGeoJSON(res.data, {
                  filestoreId,
                  inputFileType,
                  templateIdentifier,
                  id,
                });
                return { ...geoJson, translatedData: true, active };
              }
            });
          promises.push(promiseToAttach);
        }
      }

      const resolvedPromises = await Promise.all(promises);
      let result = storedData;
      if (resolvedPromises) result = [...storedData, ...resolvedPromises];
      return result;
    };
    const setMicroplanUpload = async (filesResponse) => {
      //here based on files response set data in session
      if (filesResponse.length === 0) {
        return {};
      }
      //populate this object based on the files and return
      let upload = [];

      await filesResponse.forEach(async ({ jsonData, file, translatedData, active, processedData }, idx) => {
        switch (file?.inputFileType) {
          case "Shapefile":
            upload = await handleGeoJson(file, jsonData, upload, translatedData, active, processedData, true);
            break;
          case "Excel":
            upload = handleExcel(file, jsonData, upload, translatedData, active);
            break;
          case "GeoJSON":
            upload = await handleGeoJson(file, jsonData, upload, translatedData, active, processedData);
            break;
          default:
            break;
        }
      });
      //here basically parse the files data from filestore parse it and populate upload object based on file type -> excel,shape,geojson
      return upload;
    };

    try {
      setCurrentPage();
      setMicroplanStatus();
      setMicroplanDetails();
      setMicroplanHypothesis();
      setMicroplanRuleEngine();
      setDraftValues();
      // calling fucntion to cache filtered boundary data
      await fetchBoundaryDataWrapper({});
      const filesResponse = await fetchFiles();
      const upload = await setMicroplanUpload(filesResponse);
      sessionObj.upload = upload;
      return sessionObj;
    } catch (error) {
      console.error(error.message);
    }
  },
};
