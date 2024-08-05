import { Header, LoaderWithGap } from "@egovernments/digit-ui-react-components";
import React, { useRef, useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { Card, Modal, CardText } from "@egovernments/digit-ui-react-components";
import BulkUpload from "./BulkUpload";
import Ajv from "ajv";
import XLSX from "xlsx";
import { InfoCard, PopUp, Toast, Button, DownloadIcon } from "@egovernments/digit-ui-components";
import { schemaConfig } from "../configs/schemaConfig";
import { headerConfig } from "../configs/headerConfig";
import { PRIMARY_COLOR } from "../utils";
import { downloadExcelWithCustomName } from "../utils";

/**
 * The `UploadData` function in JavaScript handles the uploading, validation, and management of files
 * for different types of data in a web application.
 * @returns The `UploadData` component is returning a JSX structure that includes a div with class
 * names, a Header component, a Button component for downloading a template, an info-text div, a
 * BulkUpload component for handling file uploads, and an InfoCard component for displaying error
 * messages if any validation errors occur during file upload.
 */
const UploadData = ({ formData, onSelect, ...props }) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [uploadedFile, setUploadedFile] = useState([]);
  const params = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_UPLOAD_ID");
  const [showInfoCard, setShowInfoCard] = useState(false);
  const [errorsType, setErrorsType] = useState({});
  const [schema, setSchema] = useState(null);
  const [showToast, setShowToast] = useState(null);
  const type = props?.props?.type;
  const [executionCount, setExecutionCount] = useState(0);
  const [isError, setIsError] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [apiError, setApiError] = useState(null);
  const [isValidation, setIsValidation] = useState(false);
  const [fileName, setFileName] = useState(null);
  const [downloadError, setDownloadError] = useState(false);
  const [resourceId, setResourceId] = useState(null);
  const searchParams = new URLSearchParams(location.search);
  const id = searchParams.get("id");
  // const { isLoading, data: Schemas } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [
  //   { name: "facilitySchema" },
  //   { name: "userSchema" },
  //   { name: "Boundary" },
  // ]);

  const { data: Schemas, isLoading: isThisLoading } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "adminSchema" }],
    {},
    { schemaCode: "HCM-ADMIN-CONSOLE.adminSchema" }
  );

  const { data: readMe } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "ReadMeConfig" }]);
  const { data: baseTimeOut } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "baseTimeout" }]);
  const [sheetHeaders, setSheetHeaders] = useState({});
  const [translatedSchema, setTranslatedSchema] = useState({});
  const [readMeInfo, setReadMeInfo] = useState({});
  const [enabled, setEnabled] = useState(false);
  const [showPopUp, setShowPopUp] = useState(true);
  const currentKey = searchParams.get("key");
  const totalData = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA");
  const [convertedSchema, setConvertedSchema] = useState({});
  const [loader, setLoader] = useState(false);

  useEffect(() => {
    if (type === "facilityWithBoundary") {
      onSelect("uploadFacility", { uploadedFile, isError, isValidation, apiError, isSuccess });
    } else if (type === "boundary") {
      onSelect("uploadBoundary", { uploadedFile, isError, isValidation, apiError, isSuccess });
    } else {
      onSelect("uploadUser", { uploadedFile, isError, isValidation, apiError, isSuccess });
    }
  }, [uploadedFile, isError, isValidation, apiError, isSuccess]);

  useEffect(() => {
    if (resourceId) {
      setUploadedFile((prev) =>
        prev.map((i) => ({
          ...i,
          resourceId: resourceId,
        }))
      );
    }
  }, [resourceId]);
  var translateSchema = (schema) => {
    var newSchema = { ...schema };
    var newProp = {};

    // Object.keys(schema?.properties)
    //   .map((e) => ({ key: e, value: t(e) }))
    //   .map((e) => {
    //     newProp[e.value] = schema?.properties[e.key];
    //   });

    // Translate properties keys and their 'name' fields
    Object.keys(schema?.properties).forEach((key) => {
      const translatedKey = t(key);
      const translatedProperty = { ...schema.properties[key], name: t(schema.properties[key].name) };
      newProp[translatedKey] = translatedProperty;
    });
    const newRequired = schema?.required.map((e) => t(e));

    newSchema.properties = newProp;
    newSchema.required = newRequired;
    delete newSchema.unique;
    return { ...newSchema };
  };

  var translateReadMeInfo = (schema) => {
    const translatedSchema = schema.map((item) => {
      return {
        header: t(item.header),
        isHeaderBold: item.isHeaderBold,
        inSheet: item.inSheet,
        inUiInfo: item.inUiInfo,
        descriptions: item.descriptions.map((desc) => {
          return {
            text: t(desc.text),
            isStepRequired: desc.isStepRequired,
            isBold: desc.isBold,
          };
        }),
      };
    });
    return translatedSchema;
  };

  function enrichSchema(data, properties, required, columns) {
    // Sort columns based on orderNumber, using name as tie-breaker if orderNumbers are equal
    columns.sort((a, b) => {
      if (a?.orderNumber === b?.orderNumber) {
        return a.name.localeCompare(b.name);
      }
      return a.orderNumber - b.orderNumber;
    });

    // Extract sorted property names
    const sortedPropertyNames = columns.map((column) => column.name);

    // Update data with new properties and required fields
    data.properties = properties;
    data.required = required;
    // delete data.campaignType;
    // data.columns = sortedPropertyNames;
  }

  function convertIntoSchema(data) {
    var convertData = { ...data };
    var properties = {};
    var required = [];
    var columns = [];

    for (const propType of ["enumProperties", "numberProperties", "stringProperties"]) {
      if (convertData?.properties[propType] && Array.isArray(convertData?.properties[propType]) && convertData?.properties[propType]?.length > 0) {
        for (const property of convertData?.properties[propType]) {
          properties[property?.name] = {
            ...property,
            type: propType === "stringProperties" ? "string" : propType === "numberProperties" ? "number" : undefined,
          };

          if (property?.isRequired && required.indexOf(property?.name) === -1) {
            required.push(property?.name);
          }

          // If orderNumber is missing, default to a very high number
          columns.push({ name: property?.name, orderNumber: property?.orderNumber || 9999999999 });
        }
      }
    }
    enrichSchema(convertData, properties, required, columns);
    const newData = JSON.parse(JSON.stringify(convertData));
    delete newData.campaignType;
    return newData;
  }

  useEffect(() => {
    if (uploadedFile.length == 0) {
      setErrorsType({});
    }
  }, [uploadedFile]);

  useEffect(async () => {
    if (Schemas?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.adminSchema) {
      const facility = await convertIntoSchema(
        Schemas?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.adminSchema?.filter((item) => item.title === "facility" && item.campaignType === "all")?.[0]
      );
      const boundary = await convertIntoSchema(
        Schemas?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.adminSchema?.filter(
          (item) => item.title === "boundaryWithTarget" && item.campaignType === totalData?.HCM_CAMPAIGN_TYPE?.projectType?.code
        )?.[0]
      );
      const user = await convertIntoSchema(
        Schemas?.MdmsRes?.["HCM-ADMIN-CONSOLE"]?.adminSchema?.filter((item) => item.title === "user" && item.campaignType === "all")?.[0]
      );
      const schema = {
        boundary: boundary,
        facilityWithBoundary: facility,
        userWithBoundary: user,
      };

      setConvertedSchema(schema);
    }
  }, [Schemas, type]);

  useEffect(async () => {
    if (convertedSchema && Object.keys(convertedSchema).length > 0) {
      const newFacilitySchema = await translateSchema(convertedSchema?.facilityWithBoundary);
      const newBoundarySchema = await translateSchema(convertedSchema?.boundary);
      const newUserSchema = await translateSchema(convertedSchema?.userWithBoundary);
      const headers = {
        boundary: Object?.keys(newBoundarySchema?.properties),
        facilityWithBoundary: Object?.keys(newFacilitySchema?.properties),
        userWithBoundary: Object?.keys(newUserSchema?.properties),
      };

      const schema = {
        boundary: newBoundarySchema,
        facilityWithBoundary: newFacilitySchema,
        userWithBoundary: newUserSchema,
      };

      setSheetHeaders(headers);
      setTranslatedSchema(schema);
    }
  }, [convertedSchema]);

  useEffect(async () => {
    if (readMe?.["HCM-ADMIN-CONSOLE"]) {
      const newReadMeFacility = await translateReadMeInfo(
        readMe?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig?.filter((item) => item.type === type)?.[0]?.texts
      );
      const newReadMeUser = await translateReadMeInfo(readMe?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig?.filter((item) => item.type === type)?.[0]?.texts);
      const newReadMeboundary = await translateReadMeInfo(
        readMe?.["HCM-ADMIN-CONSOLE"]?.ReadMeConfig?.filter((item) => item.type === type)?.[0]?.texts
      );

      const readMeText = {
        boundary: newReadMeboundary,
        facilityWithBoundary: newReadMeFacility,
        userWithBoundary: newReadMeUser,
      };

      setReadMeInfo(readMeText);
    }
  }, [readMe?.["HCM-ADMIN-CONSOLE"], type]);

  useEffect(() => {
    if (executionCount < 5) {
      let uploadType = "uploadUser";
      if (type === "boundary") {
        uploadType = "uploadBoundary";
      } else if (type === "facilityWithBoundary") {
        uploadType = "uploadFacility";
      }
      onSelect(uploadType, { uploadedFile, isError, isValidation: false, apiError: false, isSuccess: uploadedFile?.length > 0 });
      setExecutionCount((prevCount) => prevCount + 1);
    }
  });

  useEffect(() => {
    switch (type) {
      case "boundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile || []);
        setApiError(null);
        setIsValidation(false);
        setDownloadError(false);
        setIsError(false);
        setIsSuccess(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.isSuccess || null);
        setShowPopUp(!props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile.length);
        break;
      case "facilityWithBoundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile || []);
        setApiError(null);
        setIsValidation(false);
        setDownloadError(false);
        setIsError(false);
        setIsSuccess(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.isSuccess || null);
        setShowPopUp(!props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile.length);
        break;
      default:
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile || []);
        setApiError(null);
        setIsValidation(false);
        setDownloadError(false);
        setIsError(false);
        setIsSuccess(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.isSuccess || null);
        setShowPopUp(!props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile.length);
        break;
    }
  }, [type, props?.props?.sessionData]);

  useEffect(() => {
    if (errorsType[type]) {
      setShowInfoCard(true);
    } else {
      setShowInfoCard(false);
    }
  }, [type, errorsType]);

  const validateData = (data) => {
    const ajv = new Ajv({ strict: false }); // Initialize Ajv
    let validate = ajv.compile(translatedSchema[type]);
    const errors = []; // Array to hold validation errors

    data.forEach((item, index) => {
      if (!validate(item)) {
        errors.push({ index: (item?.["!row#number!"] || item?.["__rowNum__"]) + 1, errors: validate.errors });
      }
    });

    if (errors.length > 0) {
      const errorMessage = errors
        .map(({ index, errors }) => {
          const formattedErrors = errors
            .map((error) => {
              let instancePath = error.instancePath || ""; // Assign an empty string if dataPath is not available
              if (error.instancePath ===  "/Phone Number (Mandatory)" ) {
                return `${t("HCM_DATA_AT_ROW")} ${index} ${t("HCM_IN_COLUMN")}  ${t("HCM_DATA_SHOULD_BE_10_DIGIT")}`;
              }
              if (instancePath.startsWith("/")) {
                instancePath = instancePath.slice(1);
              }
              if (error.keyword === "required") {
                const missingProperty = error.params?.missingProperty || "";
                return `${t("HCM_DATA_AT_ROW")} ${index} ${t("HCM_IN_COLUMN")} '${missingProperty}' ${t("HCM_DATA_SHOULD_NOT_BE_EMPTY")}`;
              }
              if (error.keyword === "type" && error.message === "must be string") {
                return `${t("HCM_DATA_AT_ROW")} ${index} ${t("HCM_IN_COLUMN")} ${instancePath} ${t("HCM_IS_INVALID")}`;
              }
              let formattedError = `${t("HCM_IN_COLUMN")} '${instancePath}' ${error.message}`;
              if (error.keyword === "enum" && error.params && error.params.allowedValues) {
                formattedError += `${t("HCM_DATA_ALLOWED_VALUES_ARE")} ${error.params.allowedValues.join("/ ")}`;
              }
              return `${t("HCM_DATA_AT_ROW")} ${index} ${formattedError}`;
            })
            .join(", ");
          return formattedErrors;
        })
        .join(", ");

      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: errorMessage,
      }));
      setIsError(true);
      return false;
    } else {
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: "", // Clear the error message
      }));
      setShowInfoCard(false);
      return true;
    }
  };

  const validateTarget = (jsonData, headersToValidate) => {
    const boundaryCodeIndex = headersToValidate.indexOf(t("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
    const headersBeforeBoundaryCode = headersToValidate.slice(0, boundaryCodeIndex);

    const filteredData = jsonData
      .filter((e) => {
        if (e[headersBeforeBoundaryCode[headersBeforeBoundaryCode.length - 1]]) {
          return true;
        }
      })
      .filter((e) => e[t("HCM_ADMIN_CONSOLE_TARGET_AT_THE_SELECTED_BOUNDARY_LEVEL")]);

    if (filteredData.length == 0) {
      const errorMessage = t("HCM_MISSING_TARGET");
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: errorMessage,
      }));
      setIsError(true);
      return false;
    }

    const targetValue = filteredData?.[0][t("HCM_ADMIN_CONSOLE_TARGET_AT_THE_SELECTED_BOUNDARY_LEVEL")];

    if (targetValue <= 0 || targetValue >= 100000000) {
      const errorMessage = t("HCM_TARGET_VALIDATION_ERROR");
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: errorMessage,
      }));
      setIsError(true);
      return false;
    }
    return true;
  };

  const validateTargetData = (data, sheetName, targetError) => {
    const ajv = new Ajv({ strict: false }); // Initialize Ajv
    let validate = ajv.compile(translatedSchema[type]);
    const errors = []; // Array to hold validation errors

    data.forEach((item, index) => {
      if (!validate(item)) {
        errors.push({ index: (item?.["!row#number!"] || item?.["__rowNum__"]) + 1, errors: validate.errors });
      }
    });
    if (errors.length > 0) {
      const errorMessage = errors
        .map(({ index, errors }) => {
          const formattedErrors = errors
            .map((error) => {
              let instancePath = error.instancePath || ""; // Assign an empty string if dataPath is not available
              if (instancePath.startsWith("/")) {
                instancePath = instancePath.slice(1);
              }
              if (error.keyword === "required") {
                const missingProperty = error.params?.missingProperty || "";
                return `${t("HCM_DATA_AT_ROW")} ${index} ${t("HCM_IN_COLUMN")} '${missingProperty}' ${t(
                  "HCM_DATA_SHOULD_NOT_BE_EMPTY"
                )} at ${sheetName}`;
              }
              if (error.keyword === "type" && error.message === "must be string") {
                return `${t("HCM_DATA_AT_ROW")} ${index} ${t("HCM_IN_COLUMN")} ${instancePath} ${t("HCM_IS_INVALID")} at ${sheetName}`;
              }
              if (error.keyword === "maximum") {
                return `${t("HCM_DATA_AT_ROW")} ${index} ${t("HCM_IN_COLUMN")} ${instancePath} ${t("HCM_IS_MAXIMUM_VALUE")} at ${sheetName}`;
              }
              let formattedError = `${t("HCM_IN_COLUMN")} '${instancePath}' ${error.message}`;
              if (error.keyword === "enum" && error.params && error.params.allowedValues) {
                formattedError += `${t("HCM_DATA_ALLOWED_VALUES_ARE")} ${error.params.allowedValues.join("/ ")}`;
              }
              return `${t("HCM_DATA_AT_ROW")} ${index} ${formattedError} at ${sheetName}`;
            })
            .join(", ");
          return formattedErrors;
        })
        .join(", ");

      setIsError(true);
      targetError.push(errorMessage);
      return false;
    } else {
      return true;
    }
  };
  // Function to compare arrays for equality
  const arraysEqual = (arr1, arr2) => {
    if (arr1.length !== arr2.length) return false;
    for (let i = 0; i < arr1.length; i++) {
      if (arr1[i] !== arr2[i]) return false;
    }
    return true;
  };

  const validateMultipleTargets = (workbook) => {
    let isValid = true;
    // const sheet = workbook.Sheets[workbook.SheetNames[2]];
    const mdmsHeaders = sheetHeaders[type];
    // const expectedHeaders = XLSX.utils.sheet_to_json(sheet, {
    //   header: 1,
    // })[0];
    const excludedSheetNames = [t("HCM_README_SHEETNAME"), t("HCM_ADMIN_CONSOLE_BOUNDARY_DATA")];
    let nextSheetName = null;
    let expectedHeaders = [];

    for (let i = 0; i < workbook.SheetNames.length; i++) {
      const sheetName = workbook.SheetNames[i];
      if (!excludedSheetNames.includes(sheetName)) {
        nextSheetName = workbook.SheetNames[i];
        break;
      }
    }

    if (nextSheetName) {
      const sheet = workbook.Sheets[nextSheetName];
      expectedHeaders = XLSX.utils.sheet_to_json(sheet, { header: 1 })[0];
    }


    // for (const header of mdmsHeaders) {
    //   if (!expectedHeaders.includes(t(header))) {
    //     const errorMessage = t("HCM_BOUNDARY_INVALID_SHEET");
    //     setErrorsType((prevErrors) => ({
    //       ...prevErrors,
    //       [type]: errorMessage,
    //     }));
    //     setIsError(true);
    //     isValid = false;
    //     break;
    //   }
    // }

    if (!isValid) return isValid;

    for (let i = 0; i < workbook.SheetNames.length; i++) {
      const sheetName = workbook?.SheetNames[i];

      if (sheetName === t("HCM_README_SHEETNAME") || sheetName === t("HCM_ADMIN_CONSOLE_BOUNDARY_DATA")) {
        continue;
      }

      const sheet = workbook?.Sheets[sheetName];

      // Convert the sheet to JSON to extract headers
      const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
        header: 1,
      })[0];

      // Check if headers match the expected headers
      // if (!arraysEqual(headersToValidate, expectedHeaders)) {
      //   const errorMessage = t("HCM_MISSING_HEADERS");
      //   setErrorsType((prevErrors) => ({
      //     ...prevErrors,
      //     [type]: errorMessage,
      //   }));
      //   setIsError(true);
      //   isValid = false;
      //   break;
      // }
    }

    if (!isValid) return isValid;
    const targetError = [];

    // Iterate over each sheet in the workbook, starting from the second sheet
    for (let i = 0; i < workbook.SheetNames.length; i++) {
      const sheetName = workbook?.SheetNames[i];

      if (sheetName === t("HCM_README_SHEETNAME") || sheetName === t("HCM_ADMIN_CONSOLE_BOUNDARY_DATA")) {
        continue;
      }

      const sheet = workbook?.Sheets[sheetName];

      // Convert the sheet to JSON to extract headers
      const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
        header: 1,
      })[0];

      const jsonData = XLSX.utils.sheet_to_json(sheet, { blankrows: true });
      
      if(jsonData.length == 0) continue;

      const boundaryCodeIndex = headersToValidate.indexOf(t("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));

      for (const row of jsonData) {
        for (let j = boundaryCodeIndex + 1; j < headersToValidate.length; j++) {
          const value = row[headersToValidate[j]];
          if (value === undefined || value === null) {
            targetError.push(
              `${t("HCM_DATA_AT_ROW")} ${jsonData.indexOf(row) + 2} ${t("HCM_IN_COLUMN")} "${headersToValidate[j]}" ${t(
                "HCM_DATA_SHOULD_NOT_BE_EMPTY"
              )} at ${sheetName}`
            );
          } else if (value >= 100000000) {
            targetError.push(
              `${t("HCM_DATA_AT_ROW")} ${jsonData.indexOf(row) + 2} ${t("HCM_IN_COLUMN")} "${headersToValidate[j]}" ${t(
                "HCM_DATA_SHOULD_BE_LESS_THAN_MAXIMUM"
              )} at ${sheetName}`
            );
          } else if (value < 0) {
            targetError.push(
              `${t("HCM_DATA_AT_ROW")} ${jsonData.indexOf(row) + 2} ${t("HCM_IN_COLUMN")} "${headersToValidate[j]}" ${t(
                "HCM_DATA_SHOULD_BE_GREATER_THAN_ZERO"
              )} at ${sheetName}`
            );
          } else if (typeof value !== "number") {
            targetError.push(
              `${t("HCM_DATA_AT_ROW")} ${jsonData.indexOf(row) + 2} ${t("HCM_IN_COLUMN")} "${headersToValidate[j]}" ${t(
                "HCM_DATA_SHOULD_BE_NUMBER"
              )} at ${sheetName}`
            );
          }
        }
      }

      // if (!validateTargetData(jsonData, sheetName, targetError)) {
      // }
    }
    if (targetError.length > 0) {
      const errorMessage = targetError.join(", ");
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: errorMessage,
      }));
      setShowInfoCard(true);
      isValid = false;
    } else {
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: "", // Clear the error message
      }));
      setShowInfoCard(false);
      isValid = true;
    }
    return isValid;
  };

  const sheetTypeMap = {
    facilityWithBoundary: t("HCM_ADMIN_CONSOLE_AVAILABLE_FACILITIES"),
    boundary: t("HCM_ADMIN_CONSOLE_BOUNDARY_DATA"),
    userWithBoundary: t("HCM_ADMIN_CONSOLE_USER_LIST"),
  };

  const validateExcel = (selectedFile) => {
    return new Promise((resolve, reject) => {
      // Check if a file is selected
      if (!selectedFile) {
        reject(t("HCM_FILE_UPLOAD_ERROR"));
        return;
      }

      // Read the Excel file
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: "array" });
          const sheet = workbook.Sheets[sheetTypeMap[type]];
          const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
            header: 1,
          })[0];

          const expectedHeaders = sheetHeaders[type];

          const SheetNames = sheetTypeMap[type];

          const sheetData = XLSX.utils.sheet_to_json(workbook.Sheets[SheetNames], { blankrows: true });
          var jsonData = sheetData.map((row, index) => {
            const rowData = {};
            if (Object.keys(row).length > 0) {
              Object.keys(row).forEach((key) => {
                rowData[key] = row[key] === undefined || row[key] === "" ? "" : row[key];
              });
              rowData["!row#number!"] = index + 1; // Adding row number
              return rowData;
            }
          });

          jsonData = jsonData.filter((element) => element !== undefined);
          // if (type === "boundary") {
          //   if (workbook?.SheetNames.filter(sheetName => sheetName !== t("HCM_ADMIN_CONSOLE_BOUNDARY_DATA")).length == 0) {
          //     const errorMessage = t("HCM_INVALID_BOUNDARY_SHEET");
          //     setErrorsType((prevErrors) => ({
          //       ...prevErrors,
          //       [type]: errorMessage,
          //     }));
          //     setIsError(true);
          //     return;
          //   }
          // } else
          if (type === "facilityWithBoundary") {
            if (workbook?.SheetNames.filter((sheetName) => sheetName == t("HCM_ADMIN_CONSOLE_AVAILABLE_FACILITIES")).length == 0) {
              const errorMessage = t("HCM_INVALID_FACILITY_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
            if (type === "facilityWithBoundary") {
              const activeColumnName = t("HCM_ADMIN_CONSOLE_FACILITY_USAGE");
              const uniqueIdentifierColumnName = t("HCM_ADMIN_CONSOLE_FACILITY_CODE");
              if (activeColumnName && uniqueIdentifierColumnName) {
                jsonData = jsonData.filter((item) => item[activeColumnName] !== "Inactive" || !item[uniqueIdentifierColumnName]);
              }
              if (jsonData.length == 0) {
                const errorMessage = t("HCM_FACILITY_USAGE_VALIDATION");
                setErrorsType((prevErrors) => ({
                  ...prevErrors,
                  [type]: errorMessage,
                }));
                setIsError(true);
                return;
              }
            }
          } else if (type === "userWithBoundary") {
            if (workbook?.SheetNames.filter((sheetName) => sheetName == t("HCM_ADMIN_CONSOLE_USER_LIST")).length == 0) {
              const errorMessage = t("HCM_INVALID_USER_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          }
          if (type === "boundary" && workbook?.SheetNames?.length >= 3) {
            if (!validateMultipleTargets(workbook)) {
              return;
            }
          } else if (type !== "boundary") {
            for (const header of expectedHeaders) {
              if (!headersToValidate.includes(header)) {
                const errorMessage = t("HCM_MISSING_HEADERS");
                setErrorsType((prevErrors) => ({
                  ...prevErrors,
                  [type]: errorMessage,
                }));
                setIsError(true);
                return;
              }
            }
          }

          if (type === "boundary" && workbook?.SheetNames.length == 1) {
            if (!validateTarget(jsonData, headersToValidate)) {
              return;
            }
          }

          if (jsonData.length == 0) {
            const errorMessage = t("HCM_EMPTY_SHEET");
            setErrorsType((prevErrors) => ({
              ...prevErrors,
              [type]: errorMessage,
            }));
            setIsError(true);
            return;
          }
          if (type !== "boundary") {
            if (validateData(jsonData, SheetNames)) {
              resolve(true);
            } else {
              setShowInfoCard(true);
            }
          }
        } catch (error) {
          console.log(error);
          reject("HCM_FILE_UNAVAILABLE");
        }
      };

      reader.readAsArrayBuffer(selectedFile);
    });
  };

  const onBulkUploadSubmit = async (file) => {
    if (file.length > 1) {
      setShowToast({ key: "error", label: t("HCM_ERROR_MORE_THAN_ONE_FILE") });
      return;
    }
    setFileName(file?.[0]?.name);
    const module = "HCM-ADMIN-CONSOLE-CLIENT";
    const { data: { files: fileStoreIds } = {} } = await Digit.UploadServices.MultipleFilesStorage(module, file, tenantId);
    const filesArray = [fileStoreIds?.[0]?.fileStoreId];
    const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
    const fileData = fileUrl
      .map((i) => {
        const urlParts = i?.url?.split("/");
        const fileName = file?.[0]?.name;
        const id = fileUrl?.[0]?.id;
        // const fileType = type === "facilityWithBoundary" ? "facility" : type === "userWithBoundary" ? "user" : type;
        const fileType =
          type === "facilityWithBoundary" ? "facility" : type === "userWithBoundary" ? "user" : type === "boundary" ? "boundaryWithTarget" : type;
        return {
          // ...i,
          filestoreId: id,
          resourceId: resourceId,
          filename: fileName,
          type: fileType,
        };
      })
      .map(({ id, ...rest }) => rest);
    setUploadedFile(fileData);
    const validate = await validateExcel(file[0]);
  };

  const onFileDelete = (file, index) => {
    setUploadedFile((prev) => prev.filter((i) => i.id !== file.id));
    setIsError(false);
    setIsSuccess(false);
    setIsValidation(false);
    setApiError(null);
    setErrorsType({});
    setShowToast(null);
  };

  const onFileDownload = (file) => {
    if (file && file?.url) {
      // Splitting filename before .xlsx or .xls
      const fileNameWithoutExtension = file?.filename.split(/\.(xlsx|xls)/)[0];
      downloadExcelWithCustomName({ fileStoreId: file?.filestoreId, customName: fileNameWithoutExtension });
    }
  };
  useEffect(() => {
    const fetchData = async () => {
      if (!errorsType[type] && uploadedFile?.length > 0 && !isSuccess) {
        // setShowToast({ key: "info", label: t("HCM_VALIDATION_IN_PROGRESS") });
        setIsValidation(true);
        setIsError(true);
        setLoader(true);

        try {
          const temp = await Digit.Hooks.campaign.useResourceData(
            uploadedFile,
            params?.hierarchyType,
            type,
            tenantId,
            id,
            baseTimeOut?.["HCM-ADMIN-CONSOLE"]
          );
          if (temp?.isError) {
            setLoader(false);
            setIsValidation(false);
            const errorMessage = temp?.error.replaceAll(":", "-");
            setShowToast({ key: "error", label: errorMessage, transitionTime: 5000000 });
            setIsError(true);
            setApiError(errorMessage);

            return;
          }
          if (temp?.status === "completed") {
            setLoader(false);
            setIsValidation(false);
            if (temp?.additionalDetails?.sheetErrors.length === 0) {
              setShowToast({ key: "success", label: t("HCM_VALIDATION_COMPLETED") });
              if (temp?.id) {
                setResourceId(temp?.id);
              }
              if (!errorsType[type]) {
                setIsError(false);
                setIsSuccess(true);
                return;
                // setIsValidation(false);
              }
              return;
            } else {
              const processedFileStore = temp?.processedFilestoreId;
              if (!processedFileStore) {
                setShowToast({ key: "error", label: t("HCM_VALIDATION_FAILED") });
                // setIsValidation(true);
                return;
              } else {
                const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch([processedFileStore], tenantId);
                const fileData = fileUrl
                  .map((i) => {
                    const urlParts = i?.url?.split("/");
                    const id = fileUrl?.[0]?.id;
                    // const fileName = fileName;
                    const fileType =
                      type === "facilityWithBoundary"
                        ? "facility"
                        : type === "userWithBoundary"
                        ? "user"
                        : type === "boundary"
                        ? "boundaryWithTarget"
                        : type;
                    return {
                      ...i,
                      filestoreId: id,
                      filename: fileName,
                      type: fileType,
                      resourceId: temp?.id,
                    };
                  })
                  .map(({ id, ...rest }) => rest);
                // onFileDelete(uploadedFile);
                setUploadedFile(fileData);
                setShowToast({ key: "warning", label: t("HCM_CHECK_FILE_AGAIN") });
                setIsError(true);
              }
            }
          } else {
            setLoader(false);
            setIsValidation(false);
            // setShowToast({ key: "error", label: t("HCM_VALIDATION_FAILED"), transitionTime: 5000000 });
            const processedFileStore = temp?.processedFilestoreId;
            if (!processedFileStore) {
              setShowToast({ key: "error", label: t("HCM_VALIDATION_FAILED"), transitionTime: 5000000 });
              return;
            } else {
              setIsError(true);
              const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch([processedFileStore], tenantId);
              const fileData = fileUrl
                .map((i) => {
                  const urlParts = i?.url?.split("/");
                  const id = fileUrl?.[0]?.id;
                  // const fileName = file?.[0]?.name;
                  const fileType =
                    type === "facilityWithBoundary"
                      ? "facility"
                      : type === "userWithBoundary"
                      ? "user"
                      : type === "boundary"
                      ? "boundaryWithTarget"
                      : type;
                  return {
                    ...i,
                    filestoreId: id,
                    filename: fileName,
                    type: fileType,
                  };
                })
                .map(({ id, ...rest }) => rest);
              // onFileDelete(uploadedFile);
              setUploadedFile(fileData);
              setShowToast({ key: "warning", label: t("HCM_CHECK_FILE_AGAIN"), transitionTime: 5000000 });
              setIsError(true);
            }
          }
        } catch (error) {}
      }
    };

    fetchData();
  }, [errorsType]);

  const Template = {
    url: "/project-factory/v1/data/_download",
    params: {
      tenantId: tenantId,
      type: type,
      hierarchyType: params?.hierarchyType,
      id: type === "boundary" ? params?.boundaryId : type === "facilityWithBoundary" ? params?.facilityId : params?.userId,
    },
  };
  const mutation = Digit.Hooks.useCustomAPIMutationHook(Template);

  const downloadTemplate = async () => {
    // if (type === "boundary" && params?.isBoundaryLoading) {
    //   setDownloadError(true);
    //   setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_TRY_IN_SOME_TIME") });
    //   return;
    // }
    // if (type === "facilityWithBoundary" && params?.isFacilityLoading) {
    //   setDownloadError(true);
    //   setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_TRY_IN_SOME_TIME") });
    //   return;
    // }
    // if (type === "userWithBoundary" && params?.isUserLoading) {
    //   setDownloadError(true);
    //   setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_TRY_IN_SOME_TIME") });
    //   return;
    // }
    // if (!params?.boundaryId || !params?.facilityId || !params?.userId) {
    //   setEnabled(true);

    //   setDownloadError(true);
    //   setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_TRY_IN_SOME_TIME") });
    //   return;
    // }
    await mutation.mutate(
      {
        params: {
          tenantId: tenantId,
          type: type,
          hierarchyType: params?.hierarchyType,
          campaignId: id,
        },
      },
      {
        onSuccess: async (result) => {
          if (result?.GeneratedResource?.[0]?.status === "failed") {
            setDownloadError(true);
            setShowToast({ key: "error", label: t("ERROR_WHILE_DOWNLOADING") });
            return;
          }
          if (result?.GeneratedResource?.[0]?.status === "inprogress") {
            setDownloadError(true);
            setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_TRY_IN_SOME_TIME") });
            return;
          }
          if (!result?.GeneratedResource?.[0]?.fileStoreid || result?.GeneratedResource?.length == 0) {
            setDownloadError(true);
            setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_TRY_IN_SOME_TIME") });
            return;
          }
          const filesArray = [result?.GeneratedResource?.[0]?.fileStoreid];
          const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
          const fileData = fileUrl?.map((i) => {
            const urlParts = i?.url?.split("/");
            // const fileName = urlParts[urlParts?.length - 1]?.split("?")?.[0];
            const fileName = type === "boundary" ? "Target Template" : type === "facilityWithBoundary" ? "Facility Template" : "User Template";
            return {
              ...i,
              filename: fileName,
            };
          });

          if (fileData && fileData?.[0]?.url) {
            setDownloadError(false);
            if (fileData?.[0]?.id) {
              downloadExcelWithCustomName({ fileStoreId: fileData?.[0]?.id, customName: fileData?.[0]?.filename });
            }
          } else {
            setDownloadError(true);
            setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT") });
          }
        },
        onError: (result) => {
          setDownloadError(true);
          setShowToast({ key: "error", label: t("ERROR_WHILE_DOWNLOADING") });
        },
      }
    );
  };

  // useEffect(() => {
  //   if (showToast) {
  //     setTimeout(closeToast, 5000);
  //   }
  // }, [showToast]);
  const closeToast = () => {
    setShowToast(null);
  };
  useEffect(() => {
    if (showToast) {
      const t = setTimeout(closeToast, 50000);
      return () => clearTimeout(t);
    }
  }, [showToast]);

  useEffect(() => {
    setShowToast(null);
  }, [currentKey]);

  return (
    <>
      {loader && <LoaderWithGap text={"CAMPAIGN_VALIDATION_INPROGRESS"} />}
      <Card>
        <div className="campaign-bulk-upload">
          <Header className="digit-form-composer-sub-header">
            {type === "boundary" ? t("WBH_UPLOAD_TARGET") : type === "facilityWithBoundary" ? t("WBH_UPLOAD_FACILITY") : t("WBH_UPLOAD_USER")}
          </Header>
          <Button
            label={t("WBH_DOWNLOAD_TEMPLATE")}
            variation="secondary"
            icon={"FileDownload"}
            type="button"
            className="campaign-download-template-btn"
            onClick={downloadTemplate}
          />
        </div>
        {uploadedFile.length === 0 && (
          <div className="info-text">
            {type === "boundary" ? t("HCM_BOUNDARY_MESSAGE") : type === "facilityWithBoundary" ? t("HCM_FACILITY_MESSAGE") : t("HCM_USER_MESSAGE")}
          </div>
        )}
        <BulkUpload onSubmit={onBulkUploadSubmit} fileData={uploadedFile} onFileDelete={onFileDelete} onFileDownload={onFileDownload} />
        {showInfoCard && (
          <InfoCard
            populators={{
              name: "infocard",
            }}
            variant="error"
            style={{ marginLeft: "0rem", maxWidth: "100%" }}
            label={t("HCM_ERROR")}
            additionalElements={[
              <React.Fragment key={type}>
                {errorsType[type] && (
                  <React.Fragment>
                    {errorsType[type]
                      .split(",")
                      .slice(0, 50)
                      .map((error, index) => (
                        <React.Fragment key={index}>
                          {index > 0 && <br />}
                          {error.trim()}
                        </React.Fragment>
                      ))}
                  </React.Fragment>
                )}
              </React.Fragment>,
            ]}
          />
        )}
      </Card>
      <InfoCard
        populators={{
          name: "infocard",
        }}
        variant="default"
        style={{ margin: "0rem", maxWidth: "100%" }}
        additionalElements={readMeInfo[type]?.map((info, index) => (
          <div key={index} style={{ display: "flex", flexDirection: "column" }}>
            <h2>{info?.header}</h2>
            <ul style={{ paddingLeft: 0 }}>
              {info?.descriptions.map((desc, i) => (
                <li key={i} className="info-points">
                  {desc.isBold ? <h2>{desc.text}</h2> : <p>{desc.text}</p>}
                </li>
              ))}
            </ul>
          </div>
        ))}
        label={"Info"}
      />
      {showPopUp && (
        <PopUp
          type={"default"}
          className={"popUpClass"}
          footerclassName={"popUpFooter"}
          heading={
            type === "boundary"
              ? t("ES_CAMPAIGN_UPLOAD_BOUNDARY_DATA_MODAL_HEADER")
              : type === "facilityWithBoundary"
              ? t("ES_CAMPAIGN_UPLOAD_FACILITY_DATA_MODAL_HEADER")
              : t("ES_CAMPAIGN_UPLOAD_USER_DATA_MODAL_HEADER")
          }
          children={[
            <div>
              {type === "boundary"
                ? t("ES_CAMPAIGN_UPLOAD_BOUNDARY_DATA_MODAL_TEXT")
                : type === "facilityWithBoundary"
                ? t("ES_CAMPAIGN_UPLOAD_FACILITY_DATA_MODAL_TEXT")
                : t("ES_CAMPAIGN_UPLOAD_USER_DATA_MODAL_TEXT ")}
            </div>,
          ]}
          onOverlayClick={() => {
            setShowPopUp(false);
          }}
          footerChildren={[
            <Button
              type={"button"}
              size={"large"}
              variation={"secondary"}
              label={t("HCM_CAMPAIGN_UPLOAD_CANCEL")}
              onClick={() => {
                setShowPopUp(false);
              }}
            />,
            <Button
              type={"button"}
              size={"large"}
              variation={"primary"}
              icon={"FileDownload"}
              label={t("HCM_CAMPAIGN_DOWNLOAD_TEMPLATE")}
              title={t("HCM_CAMPAIGN_DOWNLOAD_TEMPLATE")}
              onClick={() => {
                downloadTemplate(), setShowPopUp(false);
              }}
            />,
          ]}
          sortFooterChildren={true}
          onClose={() => {
            setShowPopUp(false);
          }}
        ></PopUp>
      )}
      {showToast && (uploadedFile?.length > 0 || downloadError) && (
        <Toast
          type={showToast?.key === "error" ? "error" : showToast?.key === "info" ? "info" : showToast?.key === "warning" ? "warning" : "success"}
          // error={showToast.key === "error" ? true : false}
          // warning={showToast.key === "warning" ? true : false}
          // info={showToast.key === "info" ? true : false}
          label={t(showToast.label)}
          transitionTime={showToast.transitionTime}
          onClose={closeToast}
        />
      )}
    </>
  );
};

export default UploadData;
