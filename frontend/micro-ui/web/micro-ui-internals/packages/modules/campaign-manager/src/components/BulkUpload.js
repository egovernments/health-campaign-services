import React, { useState, useMemo, useEffect } from "react";
import { UploadIcon, FileIcon, DeleteIconv2, Button, DownloadIcon, PopUp, SVG } from "@egovernments/digit-ui-react-components";
import { FileUploader } from "react-drag-drop-files";
import { useTranslation } from "react-i18next";
import XLSX from "xlsx";
import XlsPreview from "./XlsPreview";
import { PRIMARY_COLOR } from "../utils";
import { Toast } from "@egovernments/digit-ui-components";

/**
 * The BulkUpload component in JavaScript allows users to upload, validate, preview, download, and
 * delete files in bulk with support for Excel file validation.
 * @returns The `BulkUpload` component is returning a JSX structure that includes conditional rendering
 * based on the presence of `fileData`. If `fileData` is empty or null, it renders a `FileUploader`
 * component with drag and drop functionality. If `fileData` contains files, it renders file cards for
 * each file with options to download and delete. Additionally, it includes a preview component for
 * Excel files
 */
const BulkUpload = ({ multiple = true, onSubmit, fileData, onFileDelete, onFileDownload }) => {
  const { t } = useTranslation();
  const [files, setFiles] = useState([]);
  const fileTypes = ["XLS", "XLSX", "csv", "CSV"];
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [showPreview, setShowPreview] = useState(false);
  const [fileUrl, setFileUrl] = useState(fileData?.[0]);
  const [fileName, setFileName] = useState(null);
  const [showToast, setShowToast] = useState(false);

  useEffect(() => {
    const fetch = async () => {
      const { data: { fileStoreIds: fileUrl } = {} } = await Digit.UploadServices.Filefetch([fileData?.[0]?.filestoreId], tenantId);
      const temp = fileData?.map((i) => ({
        ...i,
        url: fileUrl?.[0]?.url,
      }));
      setFileUrl(temp?.[0]);
    };
    fetch();
  }, [fileData]);

  const closeToast = () => {
    setTimeout(() => {
      setShowToast(null);
    }, 5000);
  };

  const dragDropJSX = (
    <div className="upload-drag-drop-container">
      <UploadIcon />
      <p className="drag-drop-text">
        <text className="drag-drop"> {t("WBH_DRAG_DROP")}</text> <text className="browse-text">{t("WBH_BULK_BROWSE_FILES")}</text>
      </p>
    </div>
  );

  const handleFileDelete = (file, index) => {
    onFileDelete(file, index);
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
          const sheet = workbook.Sheets[workbook.SheetNames[0]];

          const columnsToValidate = XLSX.utils.sheet_to_json(sheet, {
            header: 1,
          })[0];

          // Check if all columns have non-empty values in every row
          const isValid = XLSX.utils
            .sheet_to_json(sheet)
            .every((row) => columnsToValidate.every((column) => row[column] !== undefined && row[column] !== null && row[column] !== ""));

          if (isValid) {
            // All columns in all rows have non-empty values, it is valid
            resolve(true);
          } else {
            // const label = "HCM_FILE_VALIDATION_ERROR";
            // setShowToast({ isError: true, label });
            reject(t("HCM_FILE_VALIDATION_ERROR"));
          }
        } catch (error) {
          reject("HCM_FILE_UNAVAILABLE");
        }
      };

      reader.readAsArrayBuffer(selectedFile);
    });
  };

  const handleFileDownload = async (e, file) => {
    if (e?.stopPropagation) {
      e.stopPropagation();
    }
    onFileDownload(file);
  };

  const handleChange = async (newFiles) => {
    try {
      // await validateExcel(newFiles[0]);
      onSubmit([...newFiles]);
    } catch (error) {
      // Handle the validation error, you can display a message or take appropriate actions.
      setShowToast({ key: "error", label: error });
      closeToast();
    }
  };

  const fileTypeError = (err) => {
    setShowToast({ key: "error", label: t("HCM_ERROR_INVALID_FILE_TYPE") });
  };

  const renderFileCards = useMemo(() => {
    return fileData?.map((file, index) => (
      <div
        className="uploaded-file-container"
        key={index}
        onClick={(e) => {
          e.stopPropagation();
          setShowPreview(true);
        }}
      >
        <div
          className="uploaded-file-container-sub"
          style={{ cursor: "pointer" }}
          // onClick={() => {
          //   setShowPreview(true);
          // }}
        >
          <FileIcon className="icon" />
          <div style={{ marginLeft: "0.5rem", color: "#505A5F", fontWeight: "700" }}>{file.filename}</div>
        </div>
        <div className="delete-and-download-button">
          <Button
            label={t("WBH_DOWNLOAD")}
            variation="secondary"
            icon={<DownloadIcon styles={{ height: "1.25rem", width: "1.25rem" }} fill={PRIMARY_COLOR} />}
            type="button"
            className="workbench-download-template-btn hover"
            onButtonClick={(e) => {
              e.stopPropagation();
              handleFileDownload(e, fileUrl);
            }}
          />
          <Button
            label={t("WBH_DELETE")}
            variation="secondary"
            icon={<DeleteIconv2 styles={{ height: "1.25rem", width: "2.5rem" }} fill={PRIMARY_COLOR} />}
            type="button"
            className="workbench-download-template-btn hover"
            onButtonClick={(e) => {
              e.stopPropagation();
              handleFileDelete(file, index);
              setShowPreview(false);
            }}
          />
        </div>
      </div>
    ));
  }, [fileData, fileUrl]);

  return (
    <React.Fragment>
      {(!fileData || fileData?.length === 0) && (
        <FileUploader
          multiple={multiple}
          handleChange={handleChange}
          name="file"
          types={fileTypes}
          children={dragDropJSX}
          onTypeError={fileTypeError}
        />
      )}
      {fileData?.length > 0 && renderFileCards}
      {showPreview && <XlsPreview file={fileUrl} onDownload={() => handleFileDownload(null, fileUrl)} onBack={() => setShowPreview(false)} />}
      {showToast && (
        <Toast
          label={showToast?.label}
          type={showToast?.key === "error" ? "error" : showToast?.key === "info" ? "info" : "success"}
          // error={showToast?.key === "error" ? true : false}
          isDleteBtn={true}
          onClose={() => setShowToast(null)}
        ></Toast>
      )}
    </React.Fragment>
  );
};

export default BulkUpload;
