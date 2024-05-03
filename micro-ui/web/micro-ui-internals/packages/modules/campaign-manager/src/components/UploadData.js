import { Button, Header } from "@egovernments/digit-ui-react-components";
import React, { useRef, useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { DownloadIcon } from "@egovernments/digit-ui-react-components";
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
  const [isError, setIsError] = useState(true);

  useEffect(() => {
    if (type === "facilityWithBoundary") {
      onSelect("uploadFacility", { uploadedFile, isError });
    } else if (type === "boundary") {
      onSelect("uploadBoundary", { uploadedFile, isError });
    } else {
      onSelect("uploadUser", { uploadedFile, isError });
    }
  }, [uploadedFile, isError]);

  // useEffect(() => {
  //   if(type === "boundary"){
  //     if (executionCount < 5) {
  //       onSelect("uploadBoundary", uploadedFile);
  //       setExecutionCount(prevCount => prevCount + 1);
  //     }
  //   }
  //   else if(type === "facilityWithBoundary"){
  //     if (executionCount < 5) {
  //       onSelect("uploadFacility", uploadedFile);
  //       setExecutionCount(prevCount => prevCount + 1);
  //     }
  //   }
  //   else{
  //     if (executionCount < 5) {
  //       onSelect("uploadUser", uploadedFile);
  //       setExecutionCount(prevCount => prevCount + 1);
  //     }
  //   }
  // });

  useEffect(() => {
    if (executionCount < 5) {
      let uploadType = "uploadUser";
      if (type === "boundary") {
        uploadType = "uploadBoundary";
      } else if (type === "facilityWithBoundary") {
        uploadType = "uploadFacility";
      }
      onSelect(uploadType, uploadedFile);
      setExecutionCount((prevCount) => prevCount + 1);
    }
  }, [type, executionCount, onSelect, uploadedFile]);

  useEffect(() => {
    switch (type) {
      case "boundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile || []);
        break;
      case "facilityWithBoundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile || []);
        break;
      default:
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile || []);
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
    let validate;
    if (type === "facilityWithBoundary") {
      validate = ajv.compile(schemaConfig?.facilityWithBoundary);
    } else if (type === "boundary") {
      validate = ajv.compile(schemaConfig?.Boundary);
    } else {
      validate = ajv.compile(schemaConfig?.User);
    }
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
    const boundaryCodeIndex = headersToValidate.indexOf("Boundary Code");
    const headersBeforeBoundaryCode = headersToValidate.slice(0, boundaryCodeIndex);

    const filteredData = jsonData
      .filter((e) => {
        if (e[headersBeforeBoundaryCode[headersBeforeBoundaryCode.length - 1]]) {
          return true;
        }
      })
      .filter((e) => e["Target at the Selected Boundary level"]);

    if (filteredData.length == 0) {
      const errorMessage = t("HCM_MISSING_TARGET");
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: errorMessage,
      }));
      setIsError(true);
      return false;
    }

    const targetValue = filteredData?.[0]["Target at the Selected Boundary level"];

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

          const sheet = workbook.Sheets[workbook.SheetNames[0]];
          const headersToValidate = XLSX.utils.sheet_to_json(sheet, {
            header: 1,
          })[0];

          const SheetNames = workbook.SheetNames[0];
          if (type === "boundary") {
            if (SheetNames !== "Boundary Data") {
              const errorMessage = t("HCM_INVALID_BOUNDARY_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          } else if (type === "facilityWithBoundary") {
            if (SheetNames !== "List of Available Facilities") {
              const errorMessage = t("HCM_INVALID_FACILITY_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          } else {
            if (SheetNames !== "List of Users") {
              const errorMessage = t("HCM_INVALID_USER_SHEET");
              setErrorsType((prevErrors) => ({
                ...prevErrors,
                [type]: errorMessage,
              }));
              setIsError(true);
              return;
            }
          }

          const expectedHeaders = headerConfig[type];
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

          const sheetData = XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]], { blankrows: true });
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

          if (type === "boundary") {
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
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);

  const onBulkUploadSubmit = async (file) => {
    if (file.length > 1) {
      setShowToast({ key: "error", label: t("HCM_ERROR_MORE_THAN_ONE_FILE") });
      return;
    }
    const module = "HCM";
    const { data: { files: fileStoreIds } = {} } = await Digit.UploadServices.MultipleFilesStorage(module, file, tenantId);
    const filesArray = [fileStoreIds?.[0]?.fileStoreId];
    const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
    const fileData = fileUrl.map((i) => {
      const urlParts = i?.url?.split("/");
      const fileName = file?.[0]?.name;
      const fileType = type === "facilityWithBoundary" ? "facility" : type === "userWithBoundary" ? "user" : type;
      return {
        ...i,
        fileName: fileName,
        type: fileType,
      };
    });
    setUploadedFile(fileData);
    const validate = await validateExcel(file[0]);
  };

  const onFileDelete = (file, index) => {
    setUploadedFile((prev) => prev.filter((i) => i.id !== file.id));
  };

  const onFileDownload = (file) => {
    if (file && file?.url) {
      window.location.href = file?.url;
      // Splitting filename before .xlsx or .xls
      // const fileNameWithoutExtension = file?.fileName.split(/\.(xlsx|xls)/)[0];
      // downloadExcel(new Blob([file], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }), fileNameWithoutExtension);
    }
  };

  useEffect(() => {
    const fetchData = async () => {
      if (!errorsType[type] && uploadedFile.length > 0) {
        // Set loading state to true
        // setLoading(true);
        setShowToast({ key: "warning", label: t("HCM_VALIDATION_IN_PROGRESS") });
        setIsError(true);

        try {
          const temp = await Digit.Hooks.campaign.useResourceData(uploadedFile, params?.hierarchyType, type);
          if (temp?.status === "completed") {
            if (Object.keys(temp?.additionalDetails).length === 0) {
              setShowToast({ key: "warning", label: t("HCM_VALIDATION_COMPLETED") });
              if (!errorsType[type]) {
                setIsError(false);
              }
            } else {
              setShowToast({ key: "warning", label: t("HCM_VALIDATION_FAILED") });
              const processedFileStore = temp?.processedFileStore;
              if (!processedFileStore) {
                setShowToast({ key: "error", label: t("HCM_CHECK_FILE_AGAIN") });
                return;
              } else {
                const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch([processedFileStore], tenantId);
                const fileData = fileUrl.map((i) => {
                  const urlParts = i?.url?.split("/");
                  const fileName = file?.[0]?.name;
                  const fileType = type === "facilityWithBoundary" ? "facility" : type === "userWithBoundary" ? "user" : type;
                  return {
                    ...i,
                    fileName: fileName,
                    type: fileType,
                  };
                });
                setUploadedFile(fileData);
              }
            }
          } else {
            setShowToast({ key: "error", label: t("HCM_VALIDATION_FAILED") });
            const processedFileStore = temp?.processedFileStore;
            if (!processedFileStore) {
              setShowToast({ key: "error", label: t("HCM_CHECK_FILE_AGAIN") });
              return;
            } else {
              const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch([processedFileStore], tenantId);
              const fileData = fileUrl.map((i) => {
                const urlParts = i?.url?.split("/");
                const fileName = file?.[0]?.name;
                const fileType = type === "facilityWithBoundary" ? "facility" : type === "userWithBoundary" ? "user" : type;
                return {
                  ...i,
                  fileName: fileName,
                  type: fileType,
                };
              });
              setUploadedFile(fileData);
            }
          }
        } catch (error) {
        }
      }
    };

    fetchData();
  }, [errorsType, uploadedFile]);

  const Template = {
    url: "/project-factory/v1/data/_download",
    params: {
      tenantId: tenantId,
      type: type,
      forceUpdate: false,
      hierarchyType: params.hierarchyType,
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
          forceUpdate: false,
          hierarchyType: params.hierarchyType,
          id: type === "boundary" ? params?.boundaryId : type === "facilityWithBoundary" ? params?.facilityId : params?.userId,
        },
      },
      {
        onSuccess: async (result) => {
          const filesArray = [result?.GeneratedResource?.[0]?.fileStoreid];
          const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
          const fileData = fileUrl?.map((i) => {
            const urlParts = i?.url?.split("/");
            // const fileName = urlParts[urlParts?.length - 1]?.split("?")?.[0];
            const fileName = type === "boundary" ? "Boundary Template" : type === "facilityWithBoundary" ? "Facility Template" : "User Template";
            return {
              ...i,
              fileName: fileName,
            };
          });

          if (fileData && fileData?.[0]?.url) {
            // downloadExcel(fileData[0].blob, fileData[0].fileName);
            window.location.href = fileData?.[0]?.url;
            // handleFileDownload(fileData?.[0]);
            // downloadExcel(new Blob([fileData], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }),fileData?.[0]?.fileName );
          } else {
            setShowToast({ key: "error", label: t("HCM_PLEASE_WAIT") });
          }
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
    <React.Fragment>
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
      {showToast && <Toast error={showToast.key === "error" ? true : false} label={t(showToast.label)} onClose={closeToast} />}
    </React.Fragment>
  );
};

export default UploadData;
