import { Button, Header } from "@egovernments/digit-ui-react-components";
import React, { useRef, useState, useEffect, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { DownloadIcon, Card } from "@egovernments/digit-ui-react-components";
import BulkUpload from "./BulkUpload";
import Ajv from "ajv";
import XLSX from "xlsx";
import { InfoCard, Toast } from "@egovernments/digit-ui-components";
import { schemaConfig } from "../configs/schemaConfig";
import { headerConfig } from "../configs/headerConfig";
import { PRIMARY_COLOR } from "../utils";

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
  const [apiError, setApiError] = useState(null);
  const [isValidation, setIsValidation] = useState(false);
  const [fileName, setFileName] = useState(null);
  const [downloadError, setDownloadError] = useState(false);
  const { isLoading, data: Schemas } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [
    { name: "facilitySchema" },
    { name: "userSchema" },
    { name: "Boundary" },
  ]);

  const { data: readMe } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "ReadMeConfig" }]);
  const [sheetHeaders, setSheetHeaders] = useState({});
  const [translatedSchema, setTranslatedSchema] = useState({});
  const [readMeInfo, setReadMeInfo] = useState({});

  useEffect(() => {
    if (type === "facilityWithBoundary") {
      onSelect("uploadFacility", { uploadedFile, isError, isValidation, apiError });
    } else if (type === "boundary") {
      onSelect("uploadBoundary", { uploadedFile, isError, isValidation, apiError });
    } else {
      onSelect("uploadUser", { uploadedFile, isError, isValidation, apiError });
    }
  }, [uploadedFile, isError, isValidation, apiError]);

  var translateSchema = (schema) => {
    var newSchema = { ...schema };
    var newProp = {};

    Object.keys(schema?.properties)
      .map((e) => ({ key: e, value: t(e) }))
      .map((e) => {
        newProp[e.value] = schema?.properties[e.key];
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
          };
        }),
      };
    });
    return translatedSchema;
  };

  useEffect(async () => {
    if (Schemas?.["HCM-ADMIN-CONSOLE"]) {
      const newFacilitySchema = await translateSchema(Schemas?.["HCM-ADMIN-CONSOLE"]?.facilitySchema?.[0]);
      const newBoundarySchema = await translateSchema(Schemas?.["HCM-ADMIN-CONSOLE"]?.Boundary?.[0]);
      const newUserSchema = await translateSchema(Schemas?.["HCM-ADMIN-CONSOLE"]?.userSchema?.[0]);
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
  }, [Schemas?.["HCM-ADMIN-CONSOLE"] , type]);

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
  }, [readMe?.["HCM-ADMIN-CONSOLE"] , type]);


  useEffect(() => {
    if (executionCount < 5) {
      let uploadType = "uploadUser";
      if (type === "boundary") {
        uploadType = "uploadBoundary";
      } else if (type === "facilityWithBoundary") {
        uploadType = "uploadFacility";
      }
      onSelect(uploadType, { uploadedFile });
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
        break;
      case "facilityWithBoundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile || []);
        setApiError(null);
        setIsValidation(false);
        setDownloadError(false);
        setIsError(false);
        break;
      default:
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile || []);
        setApiError(null);
        setIsValidation(false);
        setDownloadError(false);
        setIsError(false);
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
    const ajv = new Ajv(); // Initialize Ajv
    let validate = ajv.compile(translatedSchema[type]);
    const errors = []; // Array to hold validation errors

    data.forEach((item, index) => {
      if (!validate(item)) {
        errors.push({ index: item?.["!row#number!"] + 1, errors: validate.errors });
      }
    });

    if (errors.length > 0) {
      const errorMessage = errors
        .map(({ index, errors }) => {
          const formattedErrors = errors
            .map((error) => {
              let formattedError = `${error.instancePath}: ${error.message}`;
              if (error.keyword === "enum" && error.params && error.params.allowedValues) {
                formattedError += `. Allowed values are: ${error.params.allowedValues.join("/ ")}`;
              }
              return formattedError;
            })
            .join(", ");
          return `Data at row ${index}: ${formattedErrors}`;
        })
        .join(" , ");

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
    const sheet = workbook.Sheets[workbook.SheetNames[2]];
    const mdmsHeaders = sheetHeaders[type];
    const expectedHeaders = XLSX.utils.sheet_to_json(sheet, {
      header: 1,
    })[0];

    for (const header of mdmsHeaders) {
      if (!expectedHeaders.includes(header)) {
        const errorMessage = t("HCM_MISSING_HEADERS");
        setErrorsType((prevErrors) => ({
          ...prevErrors,
          [type]: errorMessage,
        }));
        setIsError(true);
        isValid = false;
        break;
      }
    }

    if (!isValid) return isValid;

    // Iterate over each sheet in the workbook, starting from the second sheet
    for (let i = 2; i < workbook.SheetNames.length; i++) {
      const sheetName = workbook?.SheetNames[i];

      const sheet = workbook?.Sheets[sheetName];

      // Convert the sheet to JSON to extract headers
      const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
        header: 1,
      })[0];

      // Check if headers match the expected headers
      if (!arraysEqual(headersToValidate, expectedHeaders)) {
        const errorMessage = t("HCM_MISSING_HEADERS");
        setErrorsType((prevErrors) => ({
          ...prevErrors,
          [type]: errorMessage,
        }));
        setIsError(true);
        isValid = false;
        break;
      }

      const jsonData = XLSX.utils.sheet_to_json(sheet, { blankrows: true });

      const boundaryCodeIndex = headersToValidate.indexOf(t("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
      const headersBeforeBoundaryCode = headersToValidate.slice(0, boundaryCodeIndex);

      const columnBeforeBoundaryCode = jsonData.map((row) => row[headersBeforeBoundaryCode[headersBeforeBoundaryCode.length - 1]]);

      // Getting the length of data in the column before the boundary code
      const lengthOfColumnBeforeBoundaryCode = columnBeforeBoundaryCode.filter((value) => value !== undefined && value !== "").length;

      const filteredData = jsonData
        .filter((e) => e[headersBeforeBoundaryCode[headersBeforeBoundaryCode?.length - 1]])
        .filter((e) => e[t("HCM_ADMIN_CONSOLE_TARGET_AT_THE_SELECTED_BOUNDARY_LEVEL")]);
      if (filteredData?.length == 0 || filteredData?.length != lengthOfColumnBeforeBoundaryCode) {
        const errorMessage = t("HCM_MISSING_TARGET");
        setErrorsType((prevErrors) => ({
          ...prevErrors,
          [type]: errorMessage,
        }));
        setIsError(true);
        isValid = false;
        break;
      }

      const targetValue = filteredData?.[0][t("HCM_ADMIN_CONSOLE_TARGET_AT_THE_SELECTED_BOUNDARY_LEVEL")];

      if (targetValue <= 0 || targetValue >= 100000000) {
        const errorMessage = t("HCM_TARGET_VALIDATION_ERROR");
        setErrorsType((prevErrors) => ({
          ...prevErrors,
          [type]: errorMessage,
        }));
        setIsError(true);
        isValid = false;
        break;
      }
    }

    return isValid;
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
          const sheet = workbook.Sheets[workbook.SheetNames[1]];
          const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
            header: 1,
          })[0];

          const SheetNames = workbook.SheetNames[1];
          const expectedHeaders = sheetHeaders[type];
          if (type === "boundary") {
            if (SheetNames !== t("HCM_ADMIN_CONSOLE_BOUNDARY_DATA")) {
              const errorMessage = t("HCM_INVALID_BOUNDARY_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          } else if (type === "facilityWithBoundary") {
            if (SheetNames !== t("HCM_ADMIN_CONSOLE_AVAILABLE_FACILITIES")) {
              const errorMessage = t("HCM_INVALID_FACILITY_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          } else {
            if (SheetNames !== t("HCM_ADMIN_CONSOLE_USER_LIST")) {
              const errorMessage = t("HCM_INVALID_USER_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          }
          if (type === "boundary" && workbook?.SheetNames?.length > 1) {
            if (!validateMultipleTargets(workbook)) {
              return;
            }
          } else {
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

          const sheetData = XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[1]], { blankrows: true });
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

          if (validateData(jsonData, SheetNames)) {
            resolve(true);
          } else {
            setShowInfoCard(true);
          }
        } catch (error) {
          console.log("error", error);
          reject("HCM_FILE_UNAVAILABLE");
        }
      };

      reader.readAsArrayBuffer(selectedFile);
    });
  };

  const closeToast = () => {
    setShowToast(null);
  };
  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000000);
    }
  }, [showToast]);

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
    setIsValidation(false);
    setApiError(null);
  };

  const onFileDownload = (file) => {
    if (file && file?.url) {
      window.location.href = file?.url;
      // Splitting filename before .xlsx or .xls
      // const fileNameWithoutExtension = file?.filename.split(/\.(xlsx|xls)/)[0];
      // downloadExcel(new Blob([file], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }), fileNameWithoutExtension);
    }
  };
  useEffect(() => {
    const fetchData = async () => {
      if (!errorsType[type] && uploadedFile.length > 0) {
        setShowToast({ key: "info", label: t("HCM_VALIDATION_IN_PROGRESS") });
        setIsValidation(true);
        setIsError(true);

        try {
          const temp = await Digit.Hooks.campaign.useResourceData(uploadedFile, params?.hierarchyType, type, tenantId);
          if (temp?.isError) {
            const errorMessage = temp?.error.replaceAll(":", "-");
            setShowToast({ key: "error", label: errorMessage, transitionTime: 5000000 });
            setIsError(true);
            setApiError(errorMessage);
            setIsValidation(false);
            return;
          }
          if (temp?.status === "completed") {
            setIsValidation(false);
            if (temp?.additionalDetails?.sheetErrors.length === 0) {
              setShowToast({ key: "success", label: t("HCM_VALIDATION_COMPLETED") });
              if (!errorsType[type]) {
                setIsError(false);
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
                setShowToast({ key: "warning", label: t("HCM_CHECK_FILE_AGAIN") });
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
                onFileDelete(uploadedFile);
                setUploadedFile(fileData);
                setIsError(true);
              }
            }
          } else {
            setIsValidation(false);
            setShowToast({ key: "error", label: t("HCM_VALIDATION_FAILED") });
            const processedFileStore = temp?.processedFilestoreId;
            if (!processedFileStore) {
              setShowToast({ key: "error", label: t("HCM_VALIDATION_FAILED") });
              return;
            } else {
              setShowToast({ key: "warning", label: t("HCM_CHECK_FILE_AGAIN") });
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
              onFileDelete(uploadedFile);
              setUploadedFile(fileData);
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
    await mutation.mutate(
      {
        params: {
          tenantId: tenantId,
          type: type,
          hierarchyType: params?.hierarchyType,
          id: type === "boundary" ? params?.boundaryId : type === "facilityWithBoundary" ? params?.facilityId : params?.userId,
        },
      },
      {
        onSuccess: async (result) => {
          if (result?.GeneratedResource?.[0]?.status === "failed") {
            setDownloadError(true);
            setShowToast({ key: "error", label: t("ERROR_WHILE_DOWNLOADING") });
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
            const fileName = type === "boundary" ? "Boundary Template" : type === "facilityWithBoundary" ? "Facility Template" : "User Template";
            return {
              ...i,
              filename: fileName,
            };
          });

          if (fileData && fileData?.[0]?.url) {
            setDownloadError(false);
            // downloadExcel(fileData[0].blob, fileData[0].fileName);
            window.location.href = fileData?.[0]?.url;
            // handleFileDownload(fileData?.[0]);
            // downloadExcel(
            // new Blob([fileData], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }),
            // fileData?.[0]?.filename
            // );
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

  // const downloadExcel = (blob, fileName) => {
  //   console.log("fileName", fileName);
  //     const link = document.createElement("a");
  //     link.href = URL.createObjectURL(blob);
  //     link.download = fileName + ".xlsx";
  //     document.body.append(link);
  //     link.click();
  //     link.remove();
  //     // document.body.removeChild(link);
  //     setTimeout(() => URL.revokeObjectURL(link.href), 7000);
  // };

  const downloadExcel = (blob, fileName) => {
    if (window.mSewaApp && window.mSewaApp.isMsewaApp() && window.mSewaApp.downloadBase64File) {
      var reader = new FileReader();
      reader.readAsDataURL(blob);
      reader.onloadend = function () {
        var base64data = reader.result;
        // Adjust MIME type and file extension if necessary
        window.mSewaApp.downloadBase64File(base64data, fileName + ".xlsx");
      };
    } else {
      const link = document.createElement("a");
      // Adjust MIME type to Excel format
      link.href = URL.createObjectURL(blob);
      link.download = fileName + ".xlsx"; // Adjust file extension
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(link.href), 7000);
    }
  };

  return (
    <>
      <Card>
        <div className="campaign-bulk-upload">
          <Header className="digit-form-composer-sub-header">
            {type === "boundary" ? t("WBH_UPLOAD_TARGET") : type === "facilityWithBoundary" ? t("WBH_UPLOAD_FACILITY") : t("WBH_UPLOAD_USER")}
          </Header>
          <Button
            label={t("WBH_DOWNLOAD_TEMPLATE")}
            variation="secondary"
            icon={<DownloadIcon styles={{ height: "1.25rem", width: "1.25rem" }} fill={PRIMARY_COLOR} />}
            type="button"
            className="campaign-download-template-btn"
            onButtonClick={downloadTemplate}
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
                    {errorsType[type].split(",").map((error, index) => (
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
                  <p>{i + 1}. </p>
                  <p>{desc.text}</p>
                </li>
              ))}
            </ul>
          </div>
        ))}
        label={"Info"}
      />
      {showToast && (uploadedFile?.length > 0 || downloadError) && (
        <Toast
          error={showToast.key === "error" ? true : false}
          warning={showToast.key === "warning" ? true : false}
          info={showToast.key === "info" ? true : false}
          label={t(showToast.label)}
          transitionTime={showToast.transitionTime}
          onClose={closeToast}
        />
      )}
    </>
  );
};

export default UploadData;
