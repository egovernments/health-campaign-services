import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import * as Icons from "@egovernments/digit-ui-svg-components";
import { FileUploader } from "react-drag-drop-files";
import { InfoButton, InfoCard } from "@egovernments/digit-ui-components";
import { PRIMARY_THEME_COLOR } from "../configs/constants";

// Component for rendering individual section option
export const UploadSection = ({ item, selected, setSelectedSection, uploadDone }) => {
  const { t } = useTranslation();
  // Handle click on section option
  const handleClick = () => {
    setSelectedSection(item);
  };

  return (
    <div className={` ${selected ? "upload-section-options-active" : "upload-section-options-inactive"}`} onClick={handleClick}>
      <div className="icon">
        <CustomIcon Icon={Icons[item?.iconName]} height="26" color={selected ? PRIMARY_THEME_COLOR : "rgba(214, 213, 212, 1)"} />
      </div>
      <p>{t(item.code)}</p>
      {uploadDone && (
        <div className="icon end">
          <CustomIcon Icon={Icons["TickMarkBackgroundFilled"]} width="26" color={PRIMARY_THEME_COLOR} />
        </div>
      )}
    </div>
  );
};

export const UploadInstructions = ({ setModal, t }) => {
  return (
    <InfoCard
      text={t("UPLOAD_GUIDELINE_INFO_CARD_INSTRUCTION")}
      className={"information-description"}
      style={{ margin: "1rem 0 0 0", width: "100%", maxWidth: "unset" }}
      additionalElements={[
        <div className="link-wrapper">
          {t("REFER")}
          <div className="link" onClick={setModal}>
            {t("INFORMATION_DESCRIPTION_LINK")}
          </div>
        </div>,
      ]}
    />
  );
};

// Component for rendering individual upload option
export const UploadComponents = ({ item, selected, uploadOptions, selectedFileType, selectFileTypeHandler }) => {
  const { t } = useTranslation();
  const title = item.code;

  // Component for rendering individual upload option container
  const UploadOptionContainer = ({ item, selectedFileType, selectFileTypeHandler }) => {
    const [isHovered, setIsHovered] = useState(false);

    const handleMouseEnter = () => {
      setIsHovered(true);
    };

    const handleMouseLeave = () => {
      setIsHovered(false);
    };

    return (
      <div
        key={item.id}
        className="upload-option"
        style={selectedFileType.id === item.id ? { border: `0.125rem solid ${PRIMARY_THEME_COLOR}`, color: PRIMARY_THEME_COLOR } : {}}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
      >
        <CustomIcon
          key={item.id}
          Icon={Icons[item.iconName]}
          width={"2.5rem"}
          height={"3rem"}
          color={selectedFileType.id === item.id || isHovered ? PRIMARY_THEME_COLOR : "rgba(80, 90, 95, 1)"}
        />
        <p style={{ color: selectedFileType.id === item.id || isHovered ? PRIMARY_THEME_COLOR : "rgba(80, 90, 95, 1)" }}>{t(item.code)}</p>
        <button
          className={selectedFileType && selectedFileType.id === item.id ? "selected-button" : "select-button"}
          type="button"
          id={item.id}
          name={item.id}
          onClick={selectFileTypeHandler}
        >
          {selectedFileType.id === item.id && (
            <CustomIcon Icon={Icons["TickMarkBackgroundFilled"]} height={"2.5rem"} color={"rgba(255, 255, 255, 1)"} />
          )}
          {selectedFileType.id === item.id ? t("SELECTED") : t("SELECT")}
        </button>
      </div>
    );
  };

  return (
    <div key={item.id} className={`${selected ? "upload-component-active" : "upload-component-inactive"}`}>
      <div>
        <div className="heading">
          <h2 className="h2-class">{t(`HEADING_UPLOAD_DATA_${title}`)}</h2>
        </div>

        <p>{t(`INSTRUCTIONS_DATA_UPLOAD_OPTIONS_${title}`)}</p>
      </div>
      <div className={selectedFileType.id === item.id ? " upload-option-container-selected" : "upload-option-container"}>
        {uploadOptions?.map((item) => (
          <UploadOptionContainer key={item.id} item={item} selectedFileType={selectedFileType} selectFileTypeHandler={selectFileTypeHandler} />
        ))}
      </div>
    </div>
  );
};

// Component for uploading file
export const FileUploadComponent = ({
  selectedSection,
  selectedFileType,
  UploadFileToFileStorage,
  section,
  onTypeError,
  downloadTemplateHandler,
  showDownloadTemplate,
}) => {
  if (!selectedSection || !selectedFileType) return <div></div>;
  const { t } = useTranslation();
  let types;
  section["UploadFileTypes"].forEach((item) => {
    if (item.id === selectedFileType.id) types = item.fileExtension;
  });
  return (
    <div key={selectedSection.id} className="upload-component-active">
      <div>
        <div className="heading">
          <h2 className="h2-class">{t(`HEADING_FILE_UPLOAD_${selectedSection.code}_${selectedFileType.code}`)}</h2>
          {showDownloadTemplate() && (
            <button type="button" className="download-template-button" onClick={downloadTemplateHandler} tabIndex="0">
              <div className="icon">
                <CustomIcon color={PRIMARY_THEME_COLOR} height={"24"} width={"24"} Icon={Icons.FileDownload} />
              </div>
              <p>{t("DOWNLOAD_TEMPLATE")}</p>
            </button>
          )}
        </div>
        <p>{t(`INSTRUCTIONS_FILE_UPLOAD_FROM_TEMPLATE_${selectedSection.code}`)}</p>
        <FileUploader handleChange={UploadFileToFileStorage} label={"idk"} onTypeError={onTypeError} multiple={false} name="file" types={types}>
          <div className="upload-file">
            <CustomIcon Icon={Icons.FileUpload} width={"2.5rem"} height={"3rem"} color={"rgba(177, 180, 182, 1)"} />
            <div className="browse-text-wrapper">
              {t(`INSTRUCTIONS_UPLOAD_${selectedFileType.code}`)}&nbsp;<div className="browse-text">{t("INSTRUCTIONS_UPLOAD_BROWSE_FILES")}</div>
            </div>
          </div>
        </FileUploader>
      </div>
    </div>
  );
};

// Component to display uploaded file
export const UploadedFile = ({
  selectedSection,
  selectedFileType,
  file,
  ReuplaodFile,
  DownloadFile,
  DeleteFile,
  error,
  openDataPreview,
  downloadTemplateHandler,
  showDownloadTemplate,
}) => {
  const { t } = useTranslation();
  const [errorList, setErrorList] = useState([]);
  useEffect(() => {
    let tempErrorList = [];
    if (file?.errorLocationObject) {
      for (const [sheetName, values] of Object.entries(file?.errorLocationObject)) {
        for (const [row, columns] of Object.entries(values)) {
          for (const [column, errors] of Object.entries(columns)) {
            for (const error of errors) {
              let convertedError;
              if (typeof error === "object") {
                let { error: actualError, ...otherProperties } = error;
                convertedError = t(actualError, otherProperties?.values);
              } else {
                convertedError = t(error);
              }
              tempErrorList.push(
                t("ERROR_UPLOAD_DATA_LOCATION_AND_MESSAGE", {
                  rowNumber: Number(row) + 1,
                  columnName: t(column),
                  error: convertedError,
                  sheetName: sheetName,
                })
              );
            }
          }
        }
      }
    }
    if (tempErrorList.length !== 0) {
      setErrorList(tempErrorList);
    }
  }, [file]);
  return (
    <div key={selectedSection.id} className="upload-component-active">
      <div>
        <div className="heading">
          <h2 className="h2-class">{t(`HEADING_FILE_UPLOAD_${selectedSection.code}_${selectedFileType.code}`)}</h2>
          {showDownloadTemplate() && (
            <button type="button" className="download-template-button" onClick={downloadTemplateHandler} tabIndex="0">
              <div className="icon">
                <CustomIcon color={PRIMARY_THEME_COLOR} height={"24"} width={"24"} Icon={Icons.FileDownload} />
              </div>
              <p>{t("DOWNLOAD_TEMPLATE")}</p>
            </button>
          )}
        </div>
        <p>{t(`INSTRUCTIONS_FILE_UPLOAD_FROM_TEMPLATE_${selectedSection.code}`)}</p>

        <div className="uploaded-file" onDoubleClick={openDataPreview}>
          <div className="uploaded-file-details">
            <div>
              <CustomIcon Icon={Icons.File} width={"48"} height={"48"} color="rgba(80, 90, 95, 1)" />
            </div>
            <p>{file.fileName}</p>
          </div>
          <div className="uploaded-file-operations">
            <button className="button" onClick={ReuplaodFile} tabIndex="0" type="button">
              <CustomIcon Icon={Icons.FileUpload} width={"1.5rem"} height={"1.5rem"} color={PRIMARY_THEME_COLOR} />
              <p>{t("Reupload")}</p>
            </button>
            <button className="button" onClick={DownloadFile} tabIndex="0" type="button">
              <CustomIcon Icon={Icons.FileDownload} width={"1.5rem"} height={"1.5rem"} color={PRIMARY_THEME_COLOR} />
              <p>{t("Download")}</p>
            </button>
            <button className="delete-button" onClick={DeleteFile} tabIndex="0" type="button">
              <CustomIcon Icon={Icons.Trash} width={"0.8rem"} height={"1rem"} color={PRIMARY_THEME_COLOR} />
              <p>{t("DELETE")}</p>
            </button>
          </div>
        </div>
      </div>
      {error && Array.isArray(error) && (
        <InfoCard
          variant="error"
          style={{ margin: "0.5rem 0" }}
          label={t("ERROR_UPLOADED_FILE")}
          additionalElements={[
            <InfoButton infobuttontype="error" label={t("ERROR_VIEW_DETAIL_ERRORS")} onClick={openDataPreview} />,
            <div className="file-upload-error-container">
              {error?.map((item) => {
                if (item !== "ERROR_REFER_UPLOAD_PREVIEW_TO_SEE_THE_ERRORS") {
                  return <p>{t(item)}</p>;
                }
                return null;
              })}
              {errorList.length !== 0 && errorList.map((item) => <p>{item}</p>)}
            </div>,
          ]}
        />
      )}
    </div>
  );
};

// Uplaod GuideLines
export const UploadGuideLines = ({ uploadGuideLines, t }) => {
  const formMsgFromObject = (item) => {
    if (!item?.hasLink) {
      return t(item?.name);
    }
    return (
      <>
        {t(item?.name)} <a href={item?.linkEndPoint}>{t(item?.linkName)}</a>{" "}
      </>
    );
  };
  return (
    <div className="guidelines">
      {uploadGuideLines?.map((item, index) => (
        <div className="instruction-list-container">
          <p key={index} className="instruction-list number">
            {t(index + 1)}.
          </p>
          <div key={index} className="instruction-list text">
            {formMsgFromObject(item)}
          </div>
        </div>
      ))}
    </div>
  );
};

export const CustomIcon = (props) => {
  if (!props.Icon) return null;
  return <props.Icon fill={props.color} {...props} />;
};
