import React, { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
// import { Config } from "../../configs/UploadConfig";
// import { SVG as Icons } from "@egovernments/digit-ui-react-components";
import { Modal } from "@egovernments/digit-ui-react-components";
import * as Icons from "@egovernments/digit-ui-svg-components";
import { FileUploader } from "react-drag-drop-files";

import Config from "../../configs/UploadConfiguration.json";
console.log(Icons);
const Upload = ({ MicroplanName = "default" }) => {
  const { t } = useTranslation();

  // Fetching data using custom MDMS hook
  const { isLoading, data } = Digit.Hooks.useCustomMDMS("mz", "hcm-microplanning", [{ name: "UploadConfiguration" }]);

  // State to store sections and selected section
  const [sections, setSections] = useState([]);
  const [selectedSection, setSelectedSection] = useState(null);
  const [modal, setModal] = useState("none");
  const [selectedFileType, setSelectedFileType] = useState("none");
  const [dataPresent, setDataPresent] = useState(false);
  const [dataUpload, setDataUpload] = useState(false);
  const [loaderActivation, setLoderActivation] = useState(false);
  const [fileData, setFileData] = useState();
  const [toast, setToast] = useState();

  // Effect to update sections and selected section when data changes
  useEffect(() => {
    if (data) {
      // let uploadSections = data["hcm-microplanning"]["UploadConfiguration"];
      let uploadSections = Config["UploadConfiguration"];
      setSelectedSection(uploadSections.length > 0 ? uploadSections[0].id : null);
      setSections(uploadSections);
    }
  }, [data]);

  // To close the Toast after 10 seconds
  useEffect(() => {
    if (toast == undefined) return;
    const timer = setTimeout(() => {
      setToast(undefined);
    }, 10000);
    return () => clearTimeout(timer);
  }, [toast]);

  // Memoized section options to prevent unnecessary re-renders
  const sectionOptions = useMemo(() => {
    if (!sections) return [];
    return sections.map((item) => (
      <UploadSection key={item.id} item={item} selected={selectedSection === item.id} setSelectedSection={setSelectedSection} />
    ));
  }, [sections, selectedSection]);

  // Handler for when a file type is selected for uplaod
  const selectFileTypeHandler = (e) => {
    console.log(e);
    console.log(e.target.name);
    setSelectedFileType(e.target.name);
    setModal("upload-modal");
  };

  // Memoized section components to prevent unnecessary re-renders
  const sectionComponents = useMemo(() => {
    if (!sections) return;
    return sections.map((item) => (
      <UploadComponents
        key={item.id}
        item={item}
        selected={selectedSection === item.id}
        uploadOptions={item.UploadFileTypes}
        selectedFileType={selectedFileType}
        selectFileTypeHandler={selectFileTypeHandler}
      />
    ));
  }, [sections, selectedSection, selectedFileType]);

  const closeModal = () => {
    setModal("none");
    setSelectedFileType("none");
  };

  const UploadFileClickHandler = () => {
    setModal("none");
    setDataUpload(true);
  };

  useEffect(() => {
    // setDataPresent(false);
    if (selectedSection && selectedFileType) {
      const file = Digit.SessionStorage.get(`Microplanning_${selectedSection}`);
      console.log(file);
      setFileData(file);
      if (file) setDataPresent(true);
      else setDataPresent(false);
    } else {
      setSelectedFileType("none");
      setDataPresent(false);
    }
  }, [selectedSection]);
  // const mobileView = Digit.Utils.browser.isMobile() ? true : false;

  const UploadFileToFileStorage = async (file) => {
    // const response =  await Digit.UploadServices.Filestorage("engagement", file, Digit.ULBService.getStateId());
    setLoderActivation(true);
    console.log(file);
    let fileObject = {
      id: `Microplanning_${selectedSection}`,
      fileName: file.name,
      section: selectedSection,
      fileType: selectedFileType,
      file,
    };
    Digit.SessionStorage.set(fileObject.id, fileObject);
    setFileData(fileObject);
    setToast({ state: "success", message: "File uploaded Successfully!" });
    setLoderActivation(false);
    setDataPresent(true);
  };

  // Reupload the selected file
  const reuplaodFile = () => {
    setFileData(undefined);
    setDataPresent(false);
    setDataUpload(true);
    closeModal();
  };

  // Download the selected file
  const downloaddFile = () => {
    console.log(fileData);
    const blob = new Blob([fileData.file], { type: "application/octet-stream" });

    const url = URL.createObjectURL(blob);

    const link = document.createElement("a");
    link.href = url;
    link.download = fileData.fileName;

    link.click();

    // Clean up by revoking the URL
    URL.revokeObjectURL(url);
  };

  // delete the selected file
  const deleteDelete = () => {
    Digit.SessionStorage.del(fileData.id);
    setFileData(undefined);
    setDataPresent(false);
    closeModal();
  };

  return (
    <div className="jk-header-btn-wrapper microplanning">
      <div className="upload">
        {!dataPresent ? (
          dataUpload ? (
            <div className="upload-component">
              <FileUploadComponent
                section={sections.filter((e) => e.id === selectedSection)[0]}
                selectedSection={selectedSection}
                selectedFileType={selectedFileType}
                UploadFileToFileStorage={UploadFileToFileStorage}
              />
            </div>
          ) : (
            <div className="upload-component">{sectionComponents}</div>
          )
        ) : (
          <div className="upload-component">
            {selectedSection != null && fileData !== null && (
              <UploadedFile
                selectedSection={selectedSection}
                selectedFileType={selectedFileType}
                file={fileData}
                ReuplaodFile={() => setModal("reupload-conformation")}
                DownloaddFile={downloaddFile}
                DeleteDelete={() => setModal("delete-conformation")}
              />
            )}
          </div>
        )}
        <div className="upload-section-option">{sectionOptions}</div>
      </div>

      {modal === "upload-modal" && (
        <ModalWrapper
          selectedSection={selectedSection}
          selectedFileType={selectedFileType}
          closeModal={closeModal}
          LeftButtonHandler={UploadFileClickHandler}
          RightButtonHandler={UploadFileClickHandler}
          sections={sections}
          footerLeftButtonBody={<AlternateButton text={t("ALREDY_HAVE_IT")} />}
          footerRightButtonBody={<DownloadButton text={t("DOWNLOAD_TEMPLATE")} />}
          header={<ModalHeading label={t("HEADING_DOWNLOAD_TEMPLATE_FOR_" + selectedSection.toUpperCase() + "_" + selectedFileType.toUpperCase())} />}
          bodyText={t("INSTRUCTIONS_DOWNLOAD_TEMPLATE_FOR_" + selectedSection.toUpperCase() + "_" + selectedFileType.toUpperCase())}
        />
      )}
      {modal === "delete-conformation" && (
        <ModalWrapper
          selectedSection={selectedSection}
          selectedFileType={selectedFileType}
          closeModal={closeModal}
          LeftButtonHandler={deleteDelete}
          RightButtonHandler={closeModal}
          sections={sections}
          footerLeftButtonBody={<AlternateButton text={t("YES")} />}
          footerRightButtonBody={<AlternateButton text={t("NO")} />}
          header={<ModalHeading label={t("HEADING_DELETE_FILE_CONFORMATION")} />}
          bodyText={t("INSTRUCTIONS_DELETE_FILE_CONFORMATION")}
        />
      )}
      {modal === "reupload-conformation" && (
        <ModalWrapper
          selectedSection={selectedSection}
          selectedFileType={selectedFileType}
          closeModal={closeModal}
          LeftButtonHandler={reuplaodFile}
          RightButtonHandler={closeModal}
          sections={sections}
          footerLeftButtonBody={<AlternateButton text={t("YES")} />}
          footerRightButtonBody={<AlternateButton text={t("NO")} />}
          header={<ModalHeading label={t("HEADING_REUPLOAD_FILE_CONFORMATION")} />}
          bodyText={t("INSTRUCTIONS_REUPLOAD_FILE_CONFORMATION")}
        />
      )}
      {loaderActivation && <Loader />}
      {toast !== undefined && <Toast toast={toast} handleClose={() => setToast(undefined)} />}
    </div>
  );
};

// Component for rendering individual section option
const UploadSection = ({ item, selected, setSelectedSection }) => {
  const { t } = useTranslation();
  // Handle click on section option
  const handleClick = () => {
    setSelectedSection(item.id);
  };

  return (
    <div className={`upload-section-options ${selected ? "upload-section-options-active" : "upload-section-options-inactive"}`} onClick={handleClick}>
      <div style={{ padding: "0 10px" }}>
        <CustomIcon Icon={Icons[item.iconName]} color={selected ? "rgba(244, 119, 56, 1)" : "rgba(214, 213, 212, 1)"} />
      </div>
      <p>{t(item.title)}</p>
      <div style={{ marginLeft: "auto", marginRight: 0 }}>
        <CustomIcon Icon={Icons["TickMarkBackgroundFilled"]} color={"rgba(255, 255, 255, 0)"} />
      </div>
    </div>
  );
};

// Component for rendering individual upload option
const UploadComponents = ({ item, selected, uploadOptions, selectedFileType, selectFileTypeHandler }) => {
  const { t } = useTranslation();
  const title = item.title.toUpperCase();

  // Component for rendering individual upload option container
  const UploadOptionContainer = ({ item, selectedFileType, selectFileTypeHandler }) => {
    return (
      <div
        key={item.id}
        className="upload-option"
        style={selectedFileType === item.id ? { border: "2px rgba(244, 119, 56, 1) solid", color: "rgba(244, 119, 56, 1)" } : {}}
      >
        <CustomIcon key={item.id} Icon={Icons[item.iconName]} color={"rgba(244, 119, 56, 1)"} />
        <p>{t(item.code)}</p>
        {/* <ButtonSelector textStyles={{margin:"0px"}} theme="border" label={selectedFileType === item.id?t("Selected"):t("Select ")} onSubmit={selectFileTypeHandler} style={{}}/> */}
        <button
          className={selectedFileType === item.id ? "selected-button" : "select-button"}
          type="button"
          id={item.id}
          name={item.id}
          onClick={selectFileTypeHandler}
        >
          {selectedFileType === item.id && <CustomIcon Icon={Icons["TickMarkBackgroundFilled"]} color={"rgba(255, 255, 255, 0)"} />}
          {selectedFileType === item.id ? t("Selected") : t("Select ")}
        </button>
      </div>
    );
  };

  return (
    <div key={item.id} className={`${selected ? "upload-component-active" : "upload-component-inactive"}`}>
      <div>
        <h2>{t(`HEADING_UPLOAD_DATA_${title}`)}</h2>
        <p>{t(`INSTRUCTIONS_DATA_UPLOAD_OPTIONS_${title}`)}</p>
      </div>
      <div className={selectedFileType === item.id ? " upload-option-container-selected" : "upload-option-container"}>
        {/* <div className={selectedFileType === item.id ? " upload-option-container-selected" : ""}> */}
        {uploadOptions &&
          uploadOptions.map((item) => (
            <UploadOptionContainer key={item.id} item={item} selectedFileType={selectedFileType} selectFileTypeHandler={selectFileTypeHandler} />
          ))}
      </div>
    </div>
  );
};

// Component for uploading file/files
const FileUploadComponent = ({ selectedSection, selectedFileType, UploadFileToFileStorage, section }) => {
  if (!selectedSection || !selectedFileType) return <div></div>;
  const { t } = useTranslation();
  let types;
  section["UploadFileTypes"].forEach((item) => {
    if (item.id === selectedFileType) types = item.fileExtension;
  });
  return (
    <div key={selectedSection} className="upload-component-active">
      <div>
        <h2>{t(`HEADING_FILE_UPLOAD_${selectedSection.toUpperCase()}_${selectedFileType.toUpperCase()}`)}</h2>
        <p>{t(`INSTRUCTIONS_FILE_UPLOAD_FROM_TEMPLATE_${selectedSection.toUpperCase()}`)}</p>
        <FileUploader handleChange={UploadFileToFileStorage} label={"idk"} multiple={false} name="file" types={types}>
          <div className="upload-file">
            <CustomIcon Icon={Icons.FileUpload} width={"2.5rem"} height={"3rem"} color={"rgba(177, 180, 182, 1)"} />
            <p>{t(`UNLOAD_INSTRUCTIONS_${selectedFileType.toUpperCase()}`)}</p>
          </div>
        </FileUploader>
        {/* children={dragDropJSX} onTypeError={fileValidator} /> */}
      </div>
    </div>
  );
};

// Component to display uploaded file
const UploadedFile = ({ selectedSection, selectedFileType, file, ReuplaodFile, DownloaddFile, DeleteDelete }) => {
  console.log(file);
  const { t } = useTranslation();
  return (
    <div key={selectedSection} className="upload-component-active">
      <div>
        <h2>{t(`HEADING_FILE_UPLOAD_${selectedSection.toUpperCase()}_${selectedFileType.toUpperCase()}`)}</h2>
        <p>{t(`INSTRUCTIONS_FILE_UPLOAD_FROM_TEMPLATE_${selectedSection.toUpperCase()}`)}</p>

        <div className="uploaded-file">
          <div className="uploaded-file-details">
            <div>
              <CustomIcon Icon={Icons.file} color="rgba(80, 90, 95, 1)" />
            </div>
            <p>{file.fileName}</p>
          </div>
          <div className="uploaded-file-operations">
            <div className="button" onClick={ReuplaodFile}>
              <CustomIcon Icon={Icons.FileUpload} width={"2.5rem"} height={"2.5rem"} color={"rgba(177, 180, 182, 1)"} />
              <p>{t("Reupload")}</p>
            </div>
            <div className="button" onClick={DownloaddFile}>
              <CustomIcon Icon={Icons.FileDownload} width={"2.5rem"} height={"3rem"} color={"rgba(177, 180, 182, 1)"} />
              <p>{t("Download")}</p>
            </div>
            <div className="button deletebutton" onClick={DeleteDelete}>
              <CustomIcon Icon={Icons.Trash} width={"2.5rem"} height={"3rem"} color={"rgba(177, 180, 182, 1)"} />
              <p>{t("Delete")}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

const ModalWrapper = ({
  selectedSection,
  selectedFileType,
  closeModal,
  LeftButtonHandler,
  RightButtonHandler,
  sections,
  footerLeftButtonBody,
  footerRightButtonBody,
  header,
  bodyText,
}) => {
  console.log(selectedSection);
  const { t } = useTranslation();
  return (
    <Modal
      headerBarMain={header}
      headerBarEnd={<CloseBtn onClick={closeModal} />}
      actionCancelOnSubmit={LeftButtonHandler}
      actionSaveOnSubmit={RightButtonHandler}
      formId="microplanning"
      popupStyles={{ width: "34rem" }}
      headerBarMainStyle={{ margin: 0, width: "34rem", overflow: "hidden" }}
      popupModuleMianStyles={{ margin: 0, padding: 0 }}
      popupModuleActionBarStyles={{ justifyContent: "space-between", padding: "1rem" }}
      style={{}}
      hideSubmit={false}
      footerLeftButtonstyle={{
        padding: 0,
        alignSelf: "flex-start",
        height: "fit-content",
        textStyles: { fontWeight: "600" },
        backgroundColor: "rgba(255, 255, 255, 1)",
        color: "rgba(244, 119, 56, 1)",
        width: "14rem",
        border: "1px solid rgba(244, 119, 56, 1)",
      }}
      footerRightButtonstyle={{
        padding: 0,
        alignSelf: "flex-end",
        height: "fit-content",
        textStyles: { fontWeight: "500" },
        backgroundColor: "rgba(244, 119, 56, 1)",
        color: "rgba(255, 255, 255, 1)",
        width: "14rem",
        boxShadow: "0px -2px 0px 0px rgba(11, 12, 12, 1) inset",
      }}
      footerLeftButtonBody={footerLeftButtonBody}
      footerRightButtonBody={footerRightButtonBody}
    >
      <div className="modal-body">
        <p className="modal-main-body-p">{bodyText}</p>
      </div>
    </Modal>
  );
};

// Custom icon component
const CustomIcon = (props) => {
  if (!props.Icon) return null;
  return <props.Icon fill={props.color} style={{ outerWidth: "62px", outerHeight: "62px" }} {...props} />;
};

const Close = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="#FFFFFF" xmlns="http://www.w3.org/2000/svg">
    <path d="M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19L12 13.41L17.59 19L19 17.59L13.41 12L19 6.41Z" fill="#0B0C0C" />
  </svg>
);

const CloseBtn = (props) => {
  return (
    <div className="icon-bg-secondary" onClick={props.onClick} style={{ backgroundColor: "#FFFFFF" }}>
      <Close />
    </div>
  );
};

const AlternateButton = (props) => {
  return (
    <div className="altrady-have-template-button">
      <p>{props.text}</p>
    </div>
  );
};

const DownloadButton = (props) => {
  console.log(<CustomIcon color={"white"} Icon={Icons.FileDownload} />);
  return (
    <div className="download-template-button">
      <div className="icon">
        <CustomIcon color={"white"} height={"24"} width={"24"} Icon={Icons.FileDownload} />
      </div>
      <p>{props.text}</p>
    </div>
  );
};

const ModalHeading = (props) => {
  return <p className="modal-header">{props.label}</p>;
};

const Loader = () => {
  return (
    <div className="loader-container">
      <div className="loader">
        <div className="loader-inner" />
      </div>
      <div className="loader-text">File Uploading....</div>
    </div>
  );
};

const Toast = ({ toast, handleClose }) => {
  return (
    <div className={`toast-container ${toast.state}`}>
      <div className="toast-content">
        <CustomIcon Icon={Icons["TickMarkBackgroundFilled"]} color={"rgba(255, 255, 255, 0)"} />
        <span className="message">{toast.message}</span>
        <button className="close-button" onClick={handleClose}>
          &#10005;
        </button>
      </div>
    </div>
  );
};

export default Upload;
