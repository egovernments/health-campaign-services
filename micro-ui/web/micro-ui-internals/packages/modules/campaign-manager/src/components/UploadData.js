import { Button, Header } from "@egovernments/digit-ui-react-components";
import React, { useRef, useState , useEffect } from "react";
import { useTranslation } from "react-i18next";
import { DownloadIcon } from "@egovernments/digit-ui-react-components";
import BulkUpload from "./BulkUpload";
import Ajv from "ajv";
import XLSX from "xlsx";
import { InfoCard } from "@egovernments/digit-ui-components";
import { schemaConfig } from "../configs/schemaConfig";

/**
 * The `UploadData` function in JavaScript handles the uploading, validation, and management of files
 * for different types of data in a web application.
 * @returns The `UploadData` component is returning a JSX structure that includes a div with class
 * names, a Header component, a Button component for downloading a template, an info-text div, a
 * BulkUpload component for handling file uploads, and an InfoCard component for displaying error
 * messages if any validation errors occur during file upload.
 */
const UploadData = ({formData , onSelect , ...props}) => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [uploadedFile, setUploadedFile] = useState([]);
  const params = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_UPLOAD_ID")
  const [showInfoCard, setShowInfoCard] = useState(false);
  const [errorsType , setErrorsType] = useState({});
  const [schema , setSchema] = useState(null);
  const type = props?.props?.type;
  useEffect(() => {
    if(type==="facilityWithBoundary"){
    onSelect("uploadFacility", uploadedFile);
    }
    else if(type === "boundary"){
      onSelect("uploadBoundary", uploadedFile);
    }
  }, [uploadedFile]);

  useEffect(() => {
    switch (type) {
      case "boundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary || []);
        break;
      case "facilityWithBoundary":
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility || []);
        break;
      default:
        setUploadedFile(props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser || []);
        break;
    }  
   
  }, [type]);

  useEffect(() =>{
    if (errorsType[type]) {
      setShowInfoCard(true);
    } else {
      setShowInfoCard(false);
    }
  },[type,errorsType]);


  const validateData = (data) => {
    const ajv = new Ajv(); // Initialize Ajv
    // const validate = ajv.compile(schema); // Compile schema
    let validate;
    if (type === 'facilityWithBoundary') {
      validate = ajv.compile(schemaConfig?.facilityWithBoundary);
    } else if (type === 'boundary') {
      validate = ajv.compile(schemaConfig?.Boundary);
    }
    else{
      validate =  ajv.compile(schemaConfig?.User);
    }
    const errors = []; // Array to hold validation errors

    data.forEach((item, index) => {
      if (!validate(item)) {
        errors.push({ index, errors: validate.errors });
      }
    });

    if (errors.length > 0) {
      const errorMessage = errors
        .map(({ index, errors }) => {
          const formattedErrors = errors.map((error) => `${error.instancePath}: ${error.message}`).join(", ");
          return `Data at index ${index}: ${formattedErrors}`;
        })
        .join(" , ");

        setErrorsType((prevErrors) => ({
          ...prevErrors,
          [type]: errorMessage
        }));
      return false;
    } else {
      setErrorsType((prevErrors) => ({
        ...prevErrors,
        [type]: '' // Clear the error message
      }));
      setShowInfoCard(false);
      return true;
    }

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

          // Assuming your columns are in the first sheet
          const sheetData = XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]]);

          const jsonData = sheetData.map((row, index) => {
            const rowData = {};
            Object.keys(row).forEach((key) => {
              rowData[key] = row[key] === undefined || row[key] === "" ? null : row[key];
            });
            return rowData;
          });

          if (validateData(jsonData)) {
            resolve(true);
          } else {
            setShowInfoCard(true);
          }
        } catch (error) {
          reject("HCM_FILE_UNAVAILABLE");
        }
      };

      reader.readAsArrayBuffer(selectedFile);
    });
  };

  const onBulkUploadSubmit = async (file) => {
    const module = "HCM";
    const { data: { files: fileStoreIds } = {} } = await Digit.UploadServices.MultipleFilesStorage(module, file, tenantId);
    const filesArray = [fileStoreIds?.[0]?.fileStoreId];
    const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
    const fileData = fileUrl.map((i) => {
      const urlParts = i?.url?.split("/");
      const fileName = urlParts[urlParts?.length - 1]?.split("?")?.[0];
      const fileType = (type === "facilityWithBoundary") ? "facility" : type;
      return {
        ...i,
        fileName: fileName,
        type: fileType
      };
    });
    setUploadedFile(fileData);
    const validate = await validateExcel(file[0]);
  };

  const onFileDelete = (file, index) => {
    setUploadedFile((prev) => prev.filter((i) => i.id !== file.id));
  };

  const onFileDownload = (file) => {
    // window.open(file?.url, "_blank", `name=${file?.fileName}`);
    if (file && file?.url) {
      window.location.href = file?.url;
    }
  };

  const Template = {
    url: "/project-factory/v1/data/_download",
    params: {
      tenantId: tenantId,
      type: type,
      forceUpdate: false,
      hierarchyType: params.hierarchyType,
      id: (type === 'boundary' ? params?.boundaryId : (type === 'facilityWithBoundary' ? params?.facilityId : params?.userId))
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
          id: (type === 'boundary' ? params?.boundaryId : (type === 'facilityWithBoundary' ? params?.facilityId : params?.userId))
        },
      },
      {
        onSuccess: async (result) => {
          const filesArray = [result?.GeneratedResource?.[0]?.fileStoreid]
          const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch(filesArray, tenantId);
          const fileData = fileUrl?.map((i) => {
            const urlParts = i?.url?.split("/");
            const fileName = urlParts[urlParts?.length - 1]?.split("?")?.[0];
            return {
              ...i,
              fileName: fileName,
            };
          });
          if (fileData && fileData?.[0]?.url) {
            window.location.href = fileData?.[0]?.url;
          }
        },
      }
    );
  };

  return (
    <React.Fragment>
      <div className="campaign-bulk-upload">
        <Header className="digit-form-composer-sub-header">{type==="boundary" ? t("WBH_UPLOAD_TARGET") : (type === "facilityWithBoundary" ? t("WBH_UPLOAD_FACILITY") : t("WBH_UPLOAD_USER") )}</Header>
        <Button
          label={t("WBH_DOWNLOAD_TEMPLATE")}
          variation="secondary"
          icon={<DownloadIcon styles={{ height: "1.25rem", width: "1.25rem" }} fill="#F47738" />}
          type="button"
          className="campaign-download-template-btn"
          onButtonClick={downloadTemplate}
        />
      </div>
      <div className="info-text">{type==="boundary" ? t("HCM_BOUNDARY_MESSAGE") : (type === "facilityWithBoundary" ? t("HCM_FACILITY_MESSAGE") : t("HCM_USER_MESSAGE") )}</div>
      <BulkUpload onSubmit={onBulkUploadSubmit} fileData={uploadedFile} onFileDelete={onFileDelete} onFileDownload={onFileDownload} />
      {showInfoCard && (
        <InfoCard
          populators={{
            name: "infocard",
          }}
          variant="error"
          style={{ marginLeft: "0rem", maxWidth: "100%" }}
          label={t("HCM_ERROR")}
          additionalElements={[Object.entries(errorsType).map(([type, errorMessage]) => (
            <React.Fragment key={type}>
              {errorMessage.split(',').map((error, index) => (
                <React.Fragment key={index}>
                  {index > 0 && <br />} 
                  {error.trim()}
                </React.Fragment>
              ))}
            </React.Fragment>
          ))]}
        />
      )}
    </React.Fragment>
  );
};

export default UploadData;
