import React, { useState, useEffect, useMemo, Fragment, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import * as Icons from "@egovernments/digit-ui-svg-components";
import { FileUploader } from "react-drag-drop-files";
import { convertJsonToXlsx } from "../utils/jsonToExcelBlob";
import { parseXlsxToJsonMultipleSheets } from "../utils/exceltojson";
import { ModalWrapper } from "./Modal";
import { checkForErrorInUploadedFileExcel } from "../utils/excelValidations";
import { geojsonPropetiesValidation, geojsonValidations } from "../utils/geojsonValidations";
import JSZip from "jszip";
import { SpatialDataPropertyMapping } from "./resourceMapping";
import shp from "shpjs";
import { JsonPreviewInExcelForm } from "./JsonPreviewInExcelForm";
import { ButtonType1, ButtonType2, CloseButton, ModalHeading } from "./CommonComponents";
import { InfoButton, InfoCard, Loader, Toast } from "@egovernments/digit-ui-components";
import {
  ACCEPT_HEADERS,
  BOUNDARY_DATA_SHEET,
  EXCEL,
  FILE_STORE,
  GEOJSON,
  LOCALITY,
  PRIMARY_THEME_COLOR,
  SHAPEFILE,
  commonColumn,
} from "../configs/constants";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import { v4 as uuidv4 } from "uuid";
import { addBoundaryData, createTemplate, fetchBoundaryData, filterBoundaries } from "../utils/createTemplate";
import XLSX from "xlsx";
import ExcelJS from "exceljs";
const page = "upload";

const Upload = ({
  MicroplanName = "default",
  campaignType = Digit.SessionStorage.get("microplanHelperData")?.campaignData?.projectType,
  microplanData,
  setMicroplanData,
  checkDataCompletion,
  setCheckDataCompletion,
  currentPage,
  pages,
  navigationEvent,
  setToast,
}) => {
  const { t } = useTranslation();

  // States
  const [editable, setEditable] = useState(true);
  const [sections, setSections] = useState([]);
  const [selectedSection, setSelectedSection] = useState(null);
  const [modal, setModalState] = useState("none");
  const [selectedFileType, setSelectedFileType] = useState(null);
  const [dataPresent, setDataPresent] = useState(false);
  const [dataUpload, setDataUpload] = useState(false);
  const [loader, setLoader] = useState(false);
  const [fileData, setFileData] = useState();
  // const [toast, setToast] = useState();
  const [uploadedFileError, setUploadedFileError] = useState();
  const [fileDataList, setFileDataList] = useState([]);
  const [validationSchemas, setValidationSchemas] = useState([]);
  const [template, setTemplate] = useState([]);
  const [resourceMapping, setResourceMapping] = useState([]);
  const [previewUploadedData, setPreviewUploadedData] = useState();
  const [uploadGuideLines, setUploadGuideLines] = useState();
  const { state, dispatch } = useMyContext();

  //fetch campaign data
  const { id = "" } = Digit.Hooks.useQueryParams();
  const { isLoading: isCampaignLoading, data: campaignData } = Digit.Hooks.microplan.useSearchCampaign(
    {
      CampaignDetails: {
        tenantId: Digit.ULBService.getCurrentTenantId(),
        ids: [id],
      },
    },
    {
      enabled: !!id,
    }
  );

  // request body for boundary hierarchy api
  const reqCriteria = {
    url: `/boundary-service/boundary-hierarchy-definition/_search`,
    params: {},
    body: {
      BoundaryTypeHierarchySearchCriteria: {
        tenantId: Digit.ULBService.getCurrentTenantId(),
        hierarchyType: campaignData?.hierarchyType,
        // hierarchyType:  "Microplan",
      },
    },
    config: {
      enabled: !!campaignData?.hierarchyType,
      select: (data) => {
        return data?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.map((item) => item?.boundaryType) || {};
      },
    },
  };
  const { isLoading: ishierarchyLoading, data: hierarchy } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  // Set TourSteps
  useEffect(() => {
    const tourData = tourSteps(t)?.[page] || {};
    if (state?.tourStateData?.name === page) return;
    dispatch({
      type: "SETINITDATA",
      state: { tourStateData: tourData },
    });
  }, [t]);

  const setModal = (modalString) => {
    const elements = document.querySelectorAll(".popup-wrap-rest-unfocus");
    elements.forEach((element) => {
      element.classList.toggle("popup-wrap-rest-unfocus-active");
    });
    setModalState(modalString);
  };

  // UseEffect for checking completeness of data before moveing to next section
  useEffect(() => {
    if (!fileDataList || checkDataCompletion !== "true" || !setCheckDataCompletion) return;
    // uncomment to activate data change save check
    // if (!microplanData?.upload || !_.isEqual(fileDataList, microplanData.upload)) setModal("data-change-check");
    // else
    updateData(true);
  }, [checkDataCompletion]);

  // UseEffect to store current data
  useEffect(() => {
    if (!fileDataList || !setMicroplanData) return;
    setMicroplanData((previous) => ({ ...previous, upload: fileDataList }));
  }, [fileDataList]);

  // check if data has changed or not
  const updateData = useCallback(
    (check) => {
      if (!fileDataList || !setMicroplanData) return;

      // if user has selected a file type and wants to go back to file type selection he/she can click back buttom
      const currentSectionIndex = sections.findIndex((item) => item.id === selectedSection.id);
      if (!dataPresent) {
        if (navigationEvent?.name !== "step") {
          if (navigationEvent?.name === "next") {
            if (currentSectionIndex < sections.length - 1) {
              setSelectedSection(sections[currentSectionIndex + 1]);
              setCheckDataCompletion("false");
              return;
            }
          } else if (navigationEvent?.name === "previousStep") {
            if (dataUpload) {
              setDataUpload(false);
              setSelectedFileType(null);
              setCheckDataCompletion("false");
              return;
            }
            if (currentSectionIndex > 0) {
              setSelectedSection(sections[currentSectionIndex - 1]);
              setCheckDataCompletion("false");
              return;
            }
          }
        }
      } else {
        if (navigationEvent?.name === "next") {
          if (currentSectionIndex < sections.length - 1) {
            setSelectedSection(sections[currentSectionIndex + 1]);
            setCheckDataCompletion("false");
            return;
          }
        } else if (navigationEvent?.name === "previousStep") {
          if (currentSectionIndex > 0) {
            setSelectedSection(sections[currentSectionIndex - 1]);
            setCheckDataCompletion("false");
            return;
          }
        }
      }

      if (check) {
        setMicroplanData((previous) => ({ ...previous, upload: fileDataList }));
        const valueList = fileDataList ? fileDataList : [];
        const sectionCheckList = sections?.filter((item) => item.required);

        if (
          valueList.length !== 0 &&
          sectionCheckList.every((item) => {
            let filteredList = fileDataList?.filter((e) => e.active && e.templateIdentifier === item.id);
            if (filteredList?.length === 0) return false;
            return filteredList?.every((element) => element?.error === null) && fileDataList && !fileDataList.some((e) => e?.active && e?.error);
          })
        )
          setCheckDataCompletion("valid");
        else setCheckDataCompletion("invalid");
      } else {
        const valueList = microplanData?.Upload ? Object.values(microplanData?.Upload) : [];
        if (
          valueList.length !== 0 &&
          sectionCheckList.every((item) =>
            fileDataList?.filter((e) => e.templateIdentifier === item.id)?.every((element) => element.active && element?.error === null)
          )
        )
          setCheckDataCompletion("valid");
        else setCheckDataCompletion("invalid");
      }
    },
    [fileDataList, setMicroplanData, microplanData, setCheckDataCompletion, dataPresent, dataUpload, navigationEvent]
  );

  // const cancelUpdateData = useCallback(() => {
  //   setCheckDataCompletion(false);
  //   setModal("none");
  // }, [setCheckDataCompletion, setModal]);

  // UseEffect to extract data on first render
  useEffect(() => {
    if (microplanData && microplanData.upload) {
      setFileDataList(microplanData.upload);
    }

    if (pages) {
      const previouspage = pages[currentPage?.id - 1];
      if (previouspage?.checkForCompleteness && !microplanData?.status?.[previouspage?.name]) setEditable(false);
      else setEditable(true);
    }
  }, []);

  // UseEffect to add a event listener for keyboard
  useEffect(() => {
    window.addEventListener("keydown", handleKeyPress);

    return () => window.removeEventListener("keydown", handleKeyPress);
  }, [modal, previewUploadedData]);

  const handleKeyPress = (event) => {
    // if (modal !== "upload-guidelines") return;
    if (["x", "Escape"].includes(event.key)) {
      // Perform the desired action when "x" or "esc" is pressed
      if (modal === "upload-guidelines") {
        setModal("none");
      }
      if (previewUploadedData) setPreviewUploadedData(undefined);
    }
  };

  // Effect to update sections and selected section when data changes
  useEffect(() => {
    if (state) {
      let uploadSections = state?.UploadConfiguration;
      let schemas = state?.Schemas;
      let UIConfiguration = state?.UIConfiguration;
      if (UIConfiguration) {
        const uploadGuideLinesList = UIConfiguration.find((item) => item.name === "uploadGuideLines").UploadGuideLineInstructions;
        setUploadGuideLines(uploadGuideLinesList);
      }
      if (schemas) setValidationSchemas(schemas);
      if (uploadSections) {
        setSelectedSection(uploadSections.length > 0 ? uploadSections[0] : null);
        setSections(uploadSections);
      }
    }
  }, []);

  // Memoized section options to prevent unnecessary re-renders
  const sectionOptions = useMemo(() => {
    if (!sections) return [];
    return sections.map((item) => (
      <UploadSection
        key={item.id}
        item={item}
        selected={selectedSection.id === item.id}
        setSelectedSection={setSelectedSection}
        uploadDone={fileDataList?.filter((e) => e.active && e.templateIdentifier === item.id && !e.error)?.length !== 0}
      />
    ));
  }, [sections, selectedSection, fileDataList]);

  // Handler for when a file type is selected for uplaod
  const selectFileTypeHandler = (e) => {
    if (selectedSection && selectedSection.UploadFileTypes) {
      setSelectedFileType(selectedSection.UploadFileTypes.find((item) => item.id === e.target.name));
      setModal("upload-modal");
      return;
    }
    setToast({
      state: "error",
      message: t("ERROR_UNKNOWN"),
    });
    setLoader(false);
    return;
  };

  // Memoized section components to prevent unnecessary re-renders
  const sectionComponents = useMemo(() => {
    if (!sections) return;
    return sections.map((item) => (
      <UploadComponents
        key={item.id}
        item={item}
        selected={selectedSection.id === item.id}
        uploadOptions={item.UploadFileTypes}
        selectedFileType={selectedFileType ? selectedFileType : {}}
        selectFileTypeHandler={selectFileTypeHandler}
      />
    ));
  }, [sections, selectedSection, selectedFileType]);

  // Close model click handler
  const closeModal = () => {
    setResourceMapping([]);
    setModal("none");
  };

  // handler for show file upload screen
  const UploadFileClickHandler = (download = false) => {
    if (download) {
      downloadTemplateHandler();
    }
    setModal("none");
    setDataUpload(true);
  };

  const downloadTemplateHandler = () => {
    const downloadParams = {
      campaignType,
      type: selectedFileType.id,
      section: selectedSection.id,
      setToast,
      campaignData,
      hierarchyType: campaignData?.hierarchyType,
      Schemas: validationSchemas,
      HierarchyConfigurations: state?.HierarchyConfigurations,
      setLoader,
      t,
    };
    downloadTemplate(downloadParams);
  };
  // Effect for updating current session data in case of section change
  useEffect(() => {
    if (selectedSection) {
      let file = fileDataList?.find((item) => item.active && item.templateIdentifier === selectedSection.id);
      if (file?.resourceMapping) {
        setSelectedFileType(selectedSection.UploadFileTypes.find((item) => item?.id === file?.fileType));
        setUploadedFileError(file?.error);
        setFileData(file);
        setDataPresent(true);
      } else {
        resetSectionState();
      }
    } else {
      resetSectionState();
    }
  }, [selectedSection]);

  const resetSectionState = () => {
    setUploadedFileError(null);
    setSelectedFileType(null);
    setDataPresent(false);
    setResourceMapping([]);
    setDataUpload(false);
  };

  // Function for handling upload file event
  const UploadFileToFileStorage = async (file) => {
    if (!file) return;
    try {
      // setting loader
      setLoader("FILE_UPLOADING");
      let check;
      let fileDataToStore;
      let errorMsg;
      let errorLocationObject; // object containing the location and type of error
      let response;
      let callMapping = false;
      // Checking if the file follows name convention rules
      if (!validateNamingConvention(file, selectedFileType["namingConvention"], setToast, t)) {
        setLoader(false);
        return;
      }

      let schemaData;
      if (selectedFileType.id !== SHAPEFILE) {
        // Check if validation schema is present or not
        schemaData = getSchema(campaignType, selectedFileType.id, selectedSection.id, validationSchemas);
        if (!schemaData) {
          setToast({
            state: "error",
            message: t("ERROR_VALIDATION_SCHEMA_ABSENT"),
          });
          setLoader(false);
          return;
        }
      }
      let resourceMappingData = [];

      // Handling different filetypes
      switch (selectedFileType.id) {
        case EXCEL:
          // let response = handleExcelFile(file,schemaData);
          try {
            response = await handleExcelFile(file, schemaData, hierarchy, selectedFileType, {}, setUploadedFileError, t, campaignData);
            check = response.check;
            errorMsg = response.errorMsg;
            errorLocationObject = response.errors;
            fileDataToStore = response.fileDataToStore;
            resourceMappingData = response?.tempResourceMappingData;
            if (check === true) {
              if (response?.toast) setToast(response.toast);
              else setToast({ state: "success", message: t("FILE_UPLOADED_SUCCESSFULLY") });
            } else if (response.toast) {
              setToast(response.toast);
            } else {
              setToast({ state: "error", message: t("ERROR_UPLOADED_FILE") });
            }
            if (response.interruptUpload) {
              setLoader(false);
              return;
            }
          } catch (error) {
            console.error("Excel parsing error", error.message);
            setToast({ state: "error", message: t("ERROR_UPLOADED_FILE") });
            handleValidationErrorResponse(t("ERROR_UPLOADED_FILE"));
            return;
          }
          break;
        case GEOJSON:
          try {
            response = await handleGeojsonFile(file, schemaData, setUploadedFileError, t);
            file = new File([file], file.name, { type: "application/geo+json" });
            if (response.check === false && response.stopUpload) {
              setLoader(false);
              setToast(response.toast);
              return;
            }
            check = response.check;
            errorMsg = response.error;
            fileDataToStore = response.fileDataToStore;
            callMapping = true;
          } catch (error) {
            // console.error("Geojson parsing error", error.message);
            setToast({ state: "error", message: t("ERROR_UPLOADED_FILE") });
            handleValidationErrorResponse(t("ERROR_UPLOADED_FILE"));
            return;
          }
          break;
        case SHAPEFILE:
          try {
            response = await handleShapefiles(file, schemaData, setUploadedFileError, selectedFileType, setToast, t);
            file = new File([file], file.name, { type: "application/octet-stream" });
            check = response.check;
            errorMsg = response.error;
            fileDataToStore = response.fileDataToStore;
            callMapping = true;
          } catch (error) {
            console.error("Shapefile parsing error", error.message);
            setToast({ state: "error", message: t("ERROR_UPLOADED_FILE") });
            handleValidationErrorResponse(t("ERROR_UPLOADED_FILE"));
            return;
          }
          break;
        default:
          setToast({
            state: "error",
            message: t("ERROR_UNKNOWN_FILETYPE"),
          });
          setLoader(false);
          return;
      }
      let filestoreId;
      if (!errorMsg && !callMapping) {
        try {
          const filestoreResponse = await Digit.UploadServices.Filestorage(FILE_STORE, file, Digit.ULBService.getCurrentTenantId());
          if (filestoreResponse?.data?.files?.length > 0) {
            filestoreId = filestoreResponse?.data?.files[0]?.fileStoreId;
          } else {
            errorMsg = t("ERROR_UPLOADING_FILE");
            setToast({ state: "error", message: t("ERROR_UPLOADING_FILE") });
            setFileData((previous) => ({ ...previous, error: errorMsg }));
            setUploadedFileError(errorMsg);
          }
        } catch (errorData) {
          console.log(errorData.message);
          errorMsg = t("ERROR_UPLOADING_FILE");
          setToast({ state: "error", message: t("ERROR_UPLOADING_FILE") });
          setUploadedFileError(errorMsg);
          handleValidationErrorResponse(t("ERROR_UPLOADING_FILE"));
        }
      }

      if (selectedFileType.id === EXCEL) {
        resourceMappingData = resourceMappingData.map((item) => ({ ...item, filestoreId }));
      }
      let uuid = uuidv4();
      // creating a fileObject to save all the data collectively
      let fileObject = {
        id: uuid,
        templateIdentifier: `${selectedSection.id}`,
        fileName: file.name,
        section: selectedSection.id,
        fileType: selectedFileType.id,
        data: fileDataToStore,
        file,
        error: errorMsg ? errorMsg : null,
        filestoreId,
        resourceMapping: resourceMappingData,
        active: true,
        errorLocationObject, // contains location and type of error
      };
      setFileDataList((prevFileDataList) => {
        let temp = _.cloneDeep(prevFileDataList);
        if (!temp) return temp;
        let index = prevFileDataList?.findIndex((item) => item.active && item.templateIdentifier === selectedSection.id);
        if (index !== -1)
          temp[index] = { ...temp[index], resourceMapping: temp[index]?.resourceMapping.map((e) => ({ active: false, ...e })), active: false };
        temp.push(fileObject);
        return temp;
      });
      setFileData(fileObject);
      if (errorMsg === undefined && callMapping) {
        setModal("spatial-data-property-mapping");
      }
      setDataPresent(true);
      setLoader(false);
    } catch (error) {
      console.log(error.message);
      console.error("File Upload error", error?.message);
      setUploadedFileError("ERROR_UPLOADING_FILE");
      setLoader(false);
    }
  };

  // Reupload the selected file
  const reuplaodFile = () => {
    setResourceMapping([]);
    setFileData(undefined);
    setDataPresent(false);
    setUploadedFileError(null);
    setDataUpload(false);
    setSelectedFileType(null);
    closeModal();
  };

  // Function for creating blob out of data
  const dataToBlob = async () => {
    try {
      let blob;
      switch (fileData.fileType) {
        case EXCEL:
          if (fileData?.errorLocationObject?.length !== 0)
            blob = await prepareExcelFileBlobWithErrors(fileData.data, fileData.errorLocationObject, t);
          else blob = fileData.file;
          break;
        case SHAPEFILE:
        case GEOJSON:
          if (fileData && fileData.data) {
            const result = Digit.Utils.microplan.convertGeojsonToExcelSingleSheet(fileData?.data?.features, fileData?.section);
            if (fileData?.errorLocationObject?.length !== 0) blob = await prepareExcelFileBlobWithErrors(result, fileData.errorLocationObject, t);
          }
          break;
      }
      return blob;
    } catch (error) {
      console.error("Error generating blob:", error);
      return;
    }
  };

  // Download the selected file
  const downloadFile = async () => {
    try {
      let blob = await dataToBlob();
      if (blob) {
        // Crating a url object for the blob
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;

        // Forming a name for downloaded file
        let fileNameParts = fileData.fileName.split(".");
        fileNameParts.pop();
        fileNameParts.push("xlsx");
        fileNameParts.join(".");

        //Downloading the file
        link.download = fileNameParts.join(".");
        link.click();
        URL.revokeObjectURL(url);
      } else {
        let downloadUrl = await Digit.UploadServices.Filefetch([fileData.filestoreId], Digit.ULBService.getCurrentTenantId());
        const link = document.createElement("a");
        link.href = downloadUrl;
        // Forming a name for downloaded file
        let fileNameParts = fileData.fileName.split(".");
        fileNameParts.pop();
        fileNameParts.push("xlsx");
        fileNameParts.join(".");
        link.download = fileNameParts; // Replace with the desired file name and extension
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    } catch (error) {
      console.error(error.message);
      setToast({
        state: "error",
        message: t("ERROR_UNKNOWN_ERROR"),
      });
    }
  };

  // delete the selected file
  const deleteFile = () => {
    setResourceMapping([]);
    setFileDataList((previous) => {
      let temp = _.cloneDeep(previous);
      if (!temp) return temp;
      let index = temp?.findIndex((item) => {
        return item.id === fileData.id;
      });
      if (index !== -1)
        temp[index] = { ...temp[index], resourceMapping: temp[index]?.resourceMapping.map((e) => ({ active: false, ...e })), active: false };
      return temp;
    });
    setFileData(undefined);
    setDataPresent(false);
    setUploadedFileError(null);
    setDataUpload(false);
    setSelectedFileType(null);
    closeModal();
  };

  // Function for handling the validations for geojson and shapefiles after mapping of properties
  const validationForMappingAndDataSaving = async () => {
    try {
      setLoader("LOADING");
      const schemaData = getSchema(campaignType, selectedFileType.id, selectedSection.id, validationSchemas);
      let error;
      if (!checkForSchemaData(schemaData)) return;
      const { data, valid, errors } = computeMappedDataAndItsValidations(schemaData);
      error = errors;
      if (!valid) return;
      let filestoreId;
      if (!error) {
        filestoreId = await saveFileToFileStore();
      }
      let resourceMappingData;
      if (filestoreId) {
        resourceMappingData = resourceMapping.map((item) => {
          return { ...item, filestoreId };
        });
      }
      setResourceMapping([]);

      let boundaryDataAgainstBoundaryCode = (await boundaryDataGeneration(schemaData, campaignData, t)) || {};
      const mappedToList = resourceMappingData.map((item) => item.mappedTo);
      if (hierarchy.every((item) => !mappedToList.includes(item))) {
        data.features.forEach((feature) => {
          const boundaryCode = feature.properties.boundaryCode;
          let additionalDetails = {};
          for (let i = 0; i < hierarchy.length; i++) {
            if (boundaryDataAgainstBoundaryCode[boundaryCode]?.[i] || boundaryDataAgainstBoundaryCode[boundaryCode]?.[i] === "") {
              additionalDetails[hierarchy[i]] = boundaryDataAgainstBoundaryCode[boundaryCode][i];
            } else {
              additionalDetails[hierarchy[i]] = "";
            }
          }
          feature.properties = { ...additionalDetails, ...feature.properties };
        });
      }

      let fileObject = _.cloneDeep(fileData);
      fileObject = { ...fileData, data, resourceMapping: resourceMappingData, error: error ? error : null, filestoreId };
      setFileData(fileObject);
      setFileDataList((prevFileDataList) => {
        let temp = _.cloneDeep(prevFileDataList);
        if (!temp) return temp;
        let index = prevFileDataList?.findIndex((item) => item.id === fileData.id);
        if (index !== -1) temp[index] = fileObject;
        // temp.push(fileObject);
        return temp;
      });

      setToast({ state: "success", message: t("FILE_UPLOADED_SUCCESSFULLY") });
      setLoader(false);
    } catch (error) {
      console.log(error.message);
      setUploadedFileError(t("ERROR_UPLOADING_FILE"));
      setToast({ state: "error", message: t("ERROR_UPLOADING_FILE") });
      setLoader(false);
      handleValidationErrorResponse("ERROR_UPLOADING_FILE");
    }
  };
  const saveFileToFileStore = async () => {
    try {
      const filestoreResponse = await Digit.UploadServices.Filestorage(FILE_STORE, fileData.file, Digit.ULBService.getCurrentTenantId());
      if (filestoreResponse?.data?.files?.length > 0) {
        return filestoreResponse?.data?.files[0]?.fileStoreId;
      } else {
        error = t("ERROR_UPLOADING_FILE");
        setToast({ state: "error", message: t("ERROR_UPLOADING_FILE") });
        setResourceMapping([]);
        setUploadedFileError(error);
      }
    } catch (error) {
      console.log(error.message);
      error = t("ERROR_UPLOADING_FILE");
      handleValidationErrorResponse(error);
      setResourceMapping([]);
      return;
    }
  };
  const computeMappedDataAndItsValidations = (schemaData) => {
    const data = computeGeojsonWithMappedProperties();
    const response = geojsonPropetiesValidation(data, schemaData.schema, fileData?.section, t);
    if (!response.valid) {
      handleValidationErrorResponse(response.message, response.errors);
      return { data: data, errors: response.errors, valid: response.valid };
    }
    return { data: data, valid: response.valid };
  };

  const handleValidationErrorResponse = (error, errorLocationObject = {}) => {
    const fileObject = fileData;
    if (fileObject) {
      fileObject.error = [error];
      if (errorLocationObject) fileObject.errorLocationObject = errorLocationObject;
      setFileData((previous) => ({ ...previous, error, errorLocationObject }));
      setFileDataList((prevFileDataList) => {
        let temp = _.cloneDeep(prevFileDataList);
        if (!temp) return temp;
        let index = prevFileDataList?.findIndex((item) => item.id === fileData.id);
        temp[index] = fileObject;
        return temp;
      });
      setToast({ state: "error", message: t("ERROR_UPLOADED_FILE") });
      if (error) setUploadedFileError(error);
    }
    setLoader(false);
  };

  const checkForSchemaData = (schemaData) => {
    if (resourceMapping?.length === 0) {
      setToast({ state: "warning", message: t("WARNING_INCOMPLETE_MAPPING") });
      setLoader(false);
      return false;
    }

    if (!schemaData || !schemaData.schema || !schemaData.schema["Properties"]) {
      setToast({ state: "error", message: t("ERROR_VALIDATION_SCHEMA_ABSENT") });
      setLoader(false);
      return;
    }

    let columns = [];
    if (schemaData?.doHierarchyCheckInUploadedData) {
      columns.push(...hierarchy);
    }
    columns.push(
      ...Object.entries(schemaData?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.isRequired) {
          acc.push(key);
        }
        return acc;
      }, [])
    );

    const resourceMappingLength = resourceMapping.filter((e) => !!e?.mappedFrom && columns.includes(e?.mappedTo)).length;
    if (resourceMappingLength !== columns?.length) {
      setToast({ state: "warning", message: t("WARNING_INCOMPLETE_MAPPING") });
      setLoader(false);
      return false;
    }
    setModal("none");
    return true;
  };

  const computeGeojsonWithMappedProperties = () => {
    const schemaData = getSchema(campaignType, selectedFileType.id, selectedSection.id, validationSchemas);
    let schemaKeys;
    if (schemaData?.schema?.["Properties"]) schemaKeys = hierarchy.concat(Object.keys(schemaData.schema["Properties"]));
    // Sorting the resourceMapping list inorder to maintain the column sequence
    const sortedSecondList = Digit.Utils.microplan.sortSecondListBasedOnFirstListOrder(schemaKeys, resourceMapping);
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

  // Handler for checing file extension and showing errors in case it is wrong
  const onTypeErrorWhileFileUpload = () => {
    // if (selectedFileType.id === EXCEL) setToast({ state: "error", message: t("ERROR_EXCEL_EXTENSION") });
    // if (selectedFileType.id === GEOJSON) setToast({ state: "error", message: t("ERROR_GEOJSON_EXTENSION") });
    // if (selectedFileType.id === SHAPEFILE) setToast({ state: "error", message: t("ERROR_SHAPE_FILE_EXTENSION") });

    switch (selectedFileType.id) {
      case EXCEL:
        setToast({ state: "error", message: t("ERROR_EXCEL_EXTENSION") });
        break;
      case GEOJSON:
        setToast({ state: "error", message: t("ERROR_GEOJSON_EXTENSION") });
        break;
      case SHAPEFILE:
        setToast({ state: "error", message: t("ERROR_SHAPE_FILE_EXTENSION") });
        break;
    }
  };

  // Cancle mapping and uplaod in case of geojson and shapefiles
  const cancelUpload = () => {
    setFileDataList((previous) => {
      let temp = previous?.filter((item) => item.id !== fileData?.id);
      return temp;
    });
    setFileData(undefined);
    setDataPresent(false);
    setUploadedFileError(null);
    setDataUpload(false);
    setSelectedFileType(null);
    closeModal();
  };

  const openDataPreview = () => {
    let data;
    switch (fileData.fileType) {
      case EXCEL:
        data = fileData.data;
        break;
      case SHAPEFILE:
      case GEOJSON:
        if (!fileData || !fileData.data) {
          setToast({
            state: "error",
            message: t("ERROR_DATA_NOT_PRESENT"),
          });
          return;
        }
        data = Digit.Utils.microplan.convertGeojsonToExcelSingleSheet(fileData?.data?.features, fileData?.section);
        break;
    }
    if (!data || Object.keys(data).length === 0) {
      setToast({
        state: "error",
        message: t("ERROR_DATA_NOT_PRESENT"),
      });
      return;
    }
    setPreviewUploadedData(data);
  };

  if (isCampaignLoading || ishierarchyLoading) {
    return (
      <div className="api-data-loader">
        <Loader />
      </div>
    );
  }

  return (
    <>
      <div className={`jk-header-btn-wrapper upload-section${!editable ? " non-editable-component" : ""} `}>
        <div className={`upload popup-wrap-rest-unfocus`}>
          <div className="upload-component-wrapper">
            {!dataPresent ? (
              dataUpload ? (
                <div className="upload-component">
                  <FileUploadComponent
                    section={sections.filter((e) => e.id === selectedSection.id)[0]}
                    selectedSection={selectedSection}
                    selectedFileType={selectedFileType}
                    UploadFileToFileStorage={UploadFileToFileStorage}
                    onTypeError={onTypeErrorWhileFileUpload}
                    downloadTemplateHandler={downloadTemplateHandler}
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
                    ReuplaodFile={() => {
                      setModal("reupload-conformation");
                    }}
                    DownloadFile={downloadFile}
                    DeleteFile={() => {
                      setModal("delete-conformation");
                    }}
                    error={uploadedFileError}
                    openDataPreview={openDataPreview}
                    downloadTemplateHandler={downloadTemplateHandler}
                  />
                )}
              </div>
            )}
            {!dataPresent && dataUpload && (
              <UploadInstructions
                setModal={() => {
                  setModal("upload-guidelines");
                }}
                t={t}
              />
            )}
          </div>

          <div className="upload-section-option">{sectionOptions}</div>
        </div>

        <div className="popup-wrap-focus">
          {modal === "upload-modal" && (
            <ModalWrapper
              closeButton={true}
              selectedSection={selectedSection}
              selectedFileType={selectedFileType}
              closeModal={() => {
                closeModal();
                setSelectedFileType(null);
              }}
              LeftButtonHandler={() => UploadFileClickHandler(false)}
              RightButtonHandler={() => UploadFileClickHandler(true)}
              sections={sections}
              popupModuleActionBarStyles={{
                flex: 1,
                justifyContent: "space-between",
                padding: "1rem",
                gap: "1rem",
              }}
              footerLeftButtonBody={<ButtonType1 text={t("ALREADY_HAVE_IT")} />}
              footerRightButtonBody={<ButtonType2 text={t("DOWNLOAD_TEMPLATE")} showDownloadIcon={true} />}
              header={
                <ModalHeading
                  style={{ fontSize: "1.5rem" }}
                  label={t("HEADING_DOWNLOAD_TEMPLATE_FOR_" + selectedSection.code + "_" + selectedFileType.code)}
                />
              }
              bodyText={t("INSTRUCTIONS_DOWNLOAD_TEMPLATE_FOR_" + selectedSection.code + "_" + selectedFileType.code)}
            />
          )}
          {modal === "delete-conformation" && (
            <Modal
              popupStyles={{ borderRadius: "0.25rem", width: "31.188rem" }}
              popupModuleActionBarStyles={{
                display: "flex",
                flex: 1,
                justifyContent: "flex-start",
                width: "100%",
                padding: "1rem",
              }}
              popupModuleMianStyles={{ padding: 0, margin: 0 }}
              style={{
                flex: 1,
                height: "2.5rem",
                border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
              }}
              headerBarMainStyle={{ padding: 0, paddingRight: "1rem", margin: 0 }}
              headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_DELETE_FILE_CONFIRMATION")} />}
              actionCancelLabel={t("YES")}
              actionCancelOnSubmit={deleteFile}
              actionSaveLabel={t("NO")}
              actionSaveOnSubmit={closeModal}
            >
              <div className="modal-body">
                <p className="modal-main-body-p">{t("INSTRUCTIONS_DELETE_FILE_CONFIRMATION")}</p>
              </div>
            </Modal>
          )}
          {modal === "reupload-conformation" && (
            <Modal
              popupStyles={{ borderRadius: "0.25rem", width: "31.188rem" }}
              popupModuleActionBarStyles={{
                display: "flex",
                flex: 1,
                justifyContent: "flex-start",
                width: "100%",
                padding: "1rem",
              }}
              popupModuleMianStyles={{ padding: 0, margin: 0 }}
              style={{
                flex: 1,
                height: "2.5rem",
                border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
              }}
              headerBarMainStyle={{ padding: 0, margin: 0 }}
              headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_REUPLOAD_FILE_CONFIRMATION")} />}
              actionCancelLabel={t("YES")}
              actionCancelOnSubmit={reuplaodFile}
              actionSaveLabel={t("NO")}
              actionSaveOnSubmit={closeModal}
            >
              <div className="modal-body">
                <p className="modal-main-body-p">{t("INSTRUCTIONS_REUPLOAD_FILE_CONFIRMATION")}</p>
              </div>
            </Modal>
          )}
          {modal === "spatial-data-property-mapping" && (
            <Modal
              popupStyles={{ width: "48.438rem", borderRadius: "0.25rem", height: "fit-content" }}
              popupModuleActionBarStyles={{
                display: "flex",
                flex: 1,
                justifyContent: "flex-end",
                width: "100%",
                padding: "1rem",
              }}
              popupModuleMianStyles={{ padding: 0, margin: 0 }}
              style={{
                backgroundColor: "white",
                height: "2.5rem",
                border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
              }}
              headerBarMainStyle={{ padding: 0, margin: 0 }}
              headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_SPATIAL_DATA_PROPERTY_MAPPING")} />}
              actionSaveOnSubmit={validationForMappingAndDataSaving}
              actionSaveLabel={t("COMPLETE_MAPPING")}
              headerBarEnd={<CloseButton clickHandler={cancelUpload} style={{ margin: "0.4rem 0.8rem 0 0" }} />}
            >
              <div className="modal-body">
                <p className="modal-main-body-p">{t("INSTRUCTION_SPATIAL_DATA_PROPERTY_MAPPING")}</p>
              </div>
              <SpatialDataPropertyMapping
                uploadedData={fileData.data}
                resourceMapping={resourceMapping}
                setResourceMapping={setResourceMapping}
                schema={getSchema(campaignType, selectedFileType.id, selectedSection.id, validationSchemas)}
                setToast={setToast}
                hierarchy={hierarchy}
                close={cancelUpload}
                t={t}
              />
            </Modal>
          )}
          {modal === "upload-guidelines" && (
            <Modal
              popupStyles={{ borderRadius: "0.25rem", width: "90%" }}
              popupModuleActionBarStyles={{
                display: "flex",
                flex: 1,
                justifyContent: "flex-end",
                width: "100%",
                padding: "1rem",
              }}
              hideSubmit={true}
              popupModuleMianStyles={{ padding: 0, margin: 0 }}
              headerBarMainStyle={{ padding: 0, margin: 0 }}
              headerBarMain={
                <ModalHeading
                  style={{ fontSize: "2.5rem", marginLeft: "1rem", marginTop: "1.5rem" }}
                  className="guide-line-heading"
                  label={t("HEADING_DATA_UPLOAD_GUIDELINES")}
                />
              }
              headerBarEnd={<CloseButton clickHandler={closeModal} style={{ margin: "0.8rem 0.8rem 0 0" }} />}
            >
              <UploadGuideLines uploadGuideLines={uploadGuideLines} t={t} />
            </Modal>
          )}
          {loader && <LoaderWithGap text={t(loader)} />}
          {/* {toast && toast.state === "success" && <Toast style={{ zIndex: "9999999" }} label={toast.message} onClose={() => setToast(null)} />}
          {toast && toast.state === "error" && (
            <Toast style={{ zIndex: "9999999" }} label={toast.message} isDleteBtn onClose={() => setToast(null)} type="error" />
          )}
          {toast && toast.state === "warning" && (
            <Toast style={{ zIndex: "9999999" }} label={toast.message} isDleteBtn onClose={() => setToast(null)} type="warning" />
          )} */}
          {previewUploadedData && (
            <div className="popup-wrap">
              <JsonPreviewInExcelForm
                sheetsData={previewUploadedData}
                errorLocationObject={fileData?.errorLocationObject}
                onBack={() => setPreviewUploadedData(undefined)}
                onDownload={downloadFile}
              />
            </div>
          )}
        </div>
        {/* // uncomment to activate data change save check
      {modal === "data-change-check" && (
        <Modal
          popupStyles={{ borderRadius: "0.25rem", width: "31.188rem" }}
          popupModuleActionBarStyles={{
            display: "flex",
            flex: 1,
            justifyContent: "space-between",
            padding: 0,
            width: "100%",
            padding: "0 1rem 1rem 1.3rem",
          }}
          popupModuleMianStyles={{ padding: 0, margin: 0 }}
          style={{
            flex: 1,
            backgroundColor: "white",
            border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
          }}
          headerBarMainStyle={{ padding: 0, margin: 0 }}
          headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_DATA_WAS_UPDATED_WANT_TO_SAVE")} />}
          actionCancelLabel={t("YES")}
          actionCancelOnSubmit={updateData.bind(null, true)}
          actionSaveLabel={t("NO")}
          headerBarEnd={<CloseButton clickHandler={cancelUpdateData} style={{ padding: "0.4rem 0.8rem 0 0" }} />}
          actionSaveOnSubmit={() => updateData(false)}
        >
          <div className="modal-body">
            <p className="modal-main-body-p">{t("INSTRUCTION_DATA_WAS_UPDATED_WANT_TO_SAVE")}</p>
          </div>
        </Modal>
      )} */}
      </div>
    </>
  );
};

// Component for rendering individual section option
const UploadSection = ({ item, selected, setSelectedSection, uploadDone }) => {
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

const UploadInstructions = ({ setModal, t }) => {
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
const UploadComponents = ({ item, selected, uploadOptions, selectedFileType, selectFileTypeHandler }) => {
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
        {uploadOptions &&
          uploadOptions.map((item) => (
            <UploadOptionContainer key={item.id} item={item} selectedFileType={selectedFileType} selectFileTypeHandler={selectFileTypeHandler} />
          ))}
      </div>
    </div>
  );
};

// Component for uploading file
const FileUploadComponent = ({ selectedSection, selectedFileType, UploadFileToFileStorage, section, onTypeError, downloadTemplateHandler }) => {
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
          <button className="download-template-button" onClick={downloadTemplateHandler} tabIndex="0">
            <div className="icon">
              <CustomIcon color={PRIMARY_THEME_COLOR} height={"24"} width={"24"} Icon={Icons.FileDownload} />
            </div>
            <p>{t("DOWNLOAD_TEMPLATE")}</p>
          </button>
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
const UploadedFile = ({
  selectedSection,
  selectedFileType,
  file,
  ReuplaodFile,
  DownloadFile,
  DeleteFile,
  error,
  openDataPreview,
  downloadTemplateHandler,
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
                  rowNumber: row,
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
          <button className="download-template-button" onClick={downloadTemplateHandler} tabIndex="0">
            <div className="icon">
              <CustomIcon color={PRIMARY_THEME_COLOR} height={"24"} width={"24"} Icon={Icons.FileDownload} />
            </div>
            <p>{t("DOWNLOAD_TEMPLATE")}</p>
          </button>
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
            <button className="button" onClick={ReuplaodFile} tabIndex="0">
              <CustomIcon Icon={Icons.FileUpload} width={"1.5rem"} height={"1.5rem"} color={PRIMARY_THEME_COLOR} />
              <p>{t("Reupload")}</p>
            </button>
            <button className="button" onClick={DownloadFile} tabIndex="0">
              <CustomIcon Icon={Icons.FileDownload} width={"1.5rem"} height={"1.5rem"} color={PRIMARY_THEME_COLOR} />
              <p>{t("Download")}</p>
            </button>
            <button className="delete-button" onClick={DeleteFile} tabIndex="0">
              <CustomIcon Icon={Icons.Trash} width={"0.8rem"} height={"1rem"} color={PRIMARY_THEME_COLOR} />
              <p>{t("DELETE")}</p>
            </button>
          </div>
        </div>
      </div>
      {error && Array.isArray(error) && (
        <InfoCard
          variant="error"
          style={{ margin: "0" }}
          label={t("ERROR_UPLOADED_FILE")}
          additionalElements={[
            <InfoButton infobuttontype="error" label={"ERROR_VIEW_DETAIL_ERRORS"} onClick={openDataPreview} />,
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

// Function for checking the uploaded file for nameing conventions
const validateNamingConvention = (file, namingConvention, setToast, t) => {
  try {
    let processedConvention = namingConvention.replace("$", ".[^.]*$");
    const regx = new RegExp(processedConvention);

    if (regx && !regx.test(file.name)) {
      setToast({
        state: "error",
        message: t("ERROR_NAMING_CONVENSION"),
      });
      return false;
    }
    return true;
  } catch (error) {
    console.error(error.message);
    setToast({
      state: "error",
      message: t("ERROR_UNKNOWN"),
    });
  }
};

// Function for reading ancd checking geojson data
const readGeojson = async (file, t) => {
  return new Promise((resolve, reject) => {
    if (!file) return resolve({ valid: false, toast: { state: "error", message: t("ERROR_PARSING_FILE") } });

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const geoJSONData = JSON.parse(e.target.result);
        const trimmedGeoJSONData = trimJSON(geoJSONData);
        resolve({ valid: true, geojsonData: trimmedGeoJSONData });
      } catch (error) {
        resolve({ valid: false, toast: { state: "error", message: t("ERROR_INCORRECT_FORMAT") } });
      }
    };
    reader.onerror = function (error) {
      resolve({ valid: false, toast: { state: "error", message: t("ERROR_CORRUPTED_FILE") } });
    };

    reader.readAsText(file);
  });
};

// Function to recursively trim leading and trailing spaces from string values in a JSON object
const trimJSON = (jsonObject) => {
  if (typeof jsonObject !== "object") {
    return jsonObject; // If not an object, return as is
  }

  if (Array.isArray(jsonObject)) {
    return jsonObject.map((item) => trimJSON(item)); // If it's an array, recursively trim each item
  }

  const trimmedObject = {};
  for (const key in jsonObject) {
    if (jsonObject.hasOwnProperty(key)) {
      const value = jsonObject[key];
      // Trim string values, recursively trim objects
      trimmedObject[key.trim()] = typeof value === "string" ? value.trim() : typeof value === "object" ? trimJSON(value) : value;
    }
  }
  return trimmedObject;
};
// Function for reading and validating shape file data
const readAndValidateShapeFiles = async (file, t, namingConvention) => {
  return new Promise(async (resolve, reject) => {
    if (!file) {
      resolve({ valid: false, toast: { state: "error", message: t("ERROR_PARSING_FILE") } });
    }
    const fileRegex = new RegExp(namingConvention.replace("$", ".*$"));
    // File Size Check
    const fileSizeInBytes = file.size;
    const maxSizeInBytes = 2 * 1024 * 1024 * 1024; // 2 GB

    // Check if file size is within limit
    if (fileSizeInBytes > maxSizeInBytes)
      resolve({ valid: false, message: t("ERROR_FILE_SIZE"), toast: { state: "error", message: t("ERROR_FILE_SIZE") } });

    try {
      const zip = await JSZip.loadAsync(file);
      const isEPSG4326 = await checkProjection(zip);
      if (!isEPSG4326) {
        resolve({ valid: false, message: t("ERROR_WRONG_PRJ"), toast: { state: "error", message: t("ERROR_WRONG_PRJ") } });
      }
      const files = Object.keys(zip.files);
      const allFilesMatchRegex = files.every((fl) => {
        return fileRegex.test(fl);
      });
      let regx = new RegExp(namingConvention.replace("$", "\\.shp$"));
      const shpFile = zip.file(regx)[0];
      regx = new RegExp(namingConvention.replace("$", "\\.shx$"));
      const shxFile = zip.file(regx)[0];
      regx = new RegExp(namingConvention.replace("$", "\\.dbf$"));
      const dbfFile = zip.file(regx)[0];

      let geojson;
      if (shpFile && dbfFile) {
        const shpArrayBuffer = await shpFile.async("arraybuffer");
        const dbfArrayBuffer = await dbfFile.async("arraybuffer");

        geojson = shp.combine([shp.parseShp(shpArrayBuffer), shp.parseDbf(dbfArrayBuffer)]);
      }
      if (shpFile && dbfFile && shxFile && allFilesMatchRegex) resolve({ valid: true, data: geojson });
      else if (!allFilesMatchRegex)
        resolve({
          valid: false,
          message: [t("ERROR_CONTENT_NAMING_CONVENSION")],
          toast: { state: "error", data: geojson, message: t("ERROR_CONTENT_NAMING_CONVENSION") },
        });
      else if (!shpFile)
        resolve({ valid: false, message: [t("ERROR_SHP_MISSING")], toast: { state: "error", data: geojson, message: t("ERROR_SHP_MISSING") } });
      else if (!dbfFile)
        resolve({ valid: false, message: [t("ERROR_DBF_MISSING")], toast: { state: "error", data: geojson, message: t("ERROR_DBF_MISSING") } });
      else if (!shxFile)
        resolve({ valid: false, message: [t("ERROR_SHX_MISSING")], toast: { state: "error", data: geojson, message: t("ERROR_SHX_MISSING") } });
    } catch (error) {
      resolve({ valid: false, toast: { state: "error", message: t("ERROR_PARSING_FILE") } });
    }
  });
};

// Function for projections check in case of shapefile data
const checkProjection = async (zip) => {
  const prjFile = zip.file(/.prj$/i)[0];
  if (!prjFile) {
    return "absent";
  }

  const prjText = await prjFile.async("text");

  if (prjText.includes("GEOGCS") && prjText.includes("WGS_1984") && prjText.includes("DATUM") && prjText.includes("D_WGS_1984")) {
    return "EPSG:4326";
  } else {
    return false;
  }
};

// Function to handle the template download
const downloadTemplate = async ({
  campaignType,
  type,
  section,
  setToast,
  campaignData,
  hierarchyType,
  Schemas,
  HierarchyConfigurations,
  setLoader,
  t,
}) => {
  try {
    setLoader("LOADING");
    // Find the template based on the provided parameters
    const schema = getSchema(campaignType, type, section, Schemas);
    const hierarchyLevelName = HierarchyConfigurations?.find((item) => item.name === "devideBoundaryDataBy")?.value;
    let template = await createTemplate({
      hierarchyLevelWiseSheets: schema?.template?.hierarchyLevelWiseSheets,
      hierarchyLevelName,
      addFacilityData: schema?.template?.includeFacilityData,
      schema,
      boundaries: campaignData?.boundaries,
      tenantId: Digit.ULBService.getCurrentTenantId(),
      hierarchyType,
      t,
    });
    const translatedTemplate = translateTemplate(template, t);

    // const workbook = XLSX.utils.book_new();

    // translatedTemplate.forEach(({ sheetName, data }) => {
    //   const worksheet = XLSX.utils.json_to_sheet(data, { skipHeader: true });
    //   const columnCount = data?.[0]?.length || 0;
    //   const wscols = Array(columnCount).fill({ width: 30 });
    //   worksheet["!cols"] = wscols;
    //   XLSX.utils.book_append_sheet(workbook, worksheet, sheetName);
    // });

    // const excelBuffer = XLSX.write(workbook, { bookType: "xlsx", type: "array", compression: true });
    // Create a new workbook
    const workbook = new ExcelJS.Workbook();

    translatedTemplate.forEach(({ sheetName, data }) => {
      // Create a new worksheet with properties
      const worksheet = workbook.addWorksheet(sheetName, {
        properties: {
          outlineLevelCol: 1,
          defaultRowHeight: 15,
        },
      });

      // Add data to worksheet
      for (const row of data) {
        worksheet.addRow(row);
      }

      // Set column widths
      const columnCount = data?.[0]?.length || 0;
      const wscols = Array(columnCount).fill({ width: 30 });
      wscols.forEach((col, colIndex) => {
        worksheet.getColumn(colIndex + 1).width = col.width;
      });

      // Make the first row bold
      if (worksheet.getRow(1)) {
        worksheet.getRow(1).font = { bold: true };
      }

      // Adjust properties afterwards if needed (not supported by worksheet-writer)
      worksheet.properties.outlineLevelCol = 2;
      worksheet.properties.defaultRowHeight = 20; // Adjusted row height
    });

    // Write the workbook to a buffer
    workbook.xlsx.writeBuffer({ compression: true }).then((buffer) => {
      // Create a Blob from the buffer
      const blob = new Blob([buffer], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
      // Create a URL for the Blob
      const url = URL.createObjectURL(blob);
      // Create a link element and simulate click to trigger download
      const link = document.createElement("a");
      link.href = url;
      link.download = t(section) + ".xlsx";
      link.click();
      // Revoke the URL to release the Blob
      URL.revokeObjectURL(url);
      setLoader(false);
    });
  } catch (error) {
    setLoader(false);
    console.error(error?.message);
    setToast({ state: "error", message: t("ERROR_DOWNLOADING_TEMPLATE") });
  }
};

const translateTemplate = (template, t) => {
  // Initialize an array to hold the transformed result
  const transformedResult = [];

  // Iterate over each sheet in the divided data
  for (const sheet of template) {
    const sheetData = sheet.data;

    // Find the index of the boundaryCode column in the header row
    const boundaryCodeIndex = sheetData[0].indexOf(commonColumn);

    const sheetName = t(sheet.sheetName);
    const transformedSheet = {
      sheetName: sheetName.length > 31 ? sheetName.slice(0, 31) : sheetName,
      data: [],
    };

    // Iterate over each row in the sheet data
    for (const [rowIndex, row] of sheetData.entries()) {
      // Transform each entity in the row using the transformFunction
      const transformedRow = row.map((entity, index) => {
        // Skip transformation for the boundaryCode column
        if ((index === boundaryCodeIndex && rowIndex !== 0) || typeof entity === "number") {
          return entity;
        } else {
          return t(entity);
        }
      });
      transformedSheet.data.push(transformedRow);
    }

    // Add the transformed sheet to the transformed result
    transformedResult.push(transformedSheet);
  }

  return transformedResult;
};

// get schema for validation
const getSchema = (campaignType, type, section, schemas) => {
  return schemas.find((schema) => {
    if (!schema.campaignType) {
      return schema.type === type && schema.section === section;
    }
    return schema.campaignType === campaignType && schema.type === type && schema.section === section;
  });
};

// Uplaod GuideLines
const UploadGuideLines = ({ uploadGuideLines, t }) => {
  return (
    <div className="guidelines">
      {/* <p className="sub-heading">{t("PREREQUISITES")}</p>
      <div className="instruction-list">
        {t("INSTRUCTION_PREREQUISITES_1")}&nbsp;
        <a className="link" href="https://help.sap.com/docs/SAP_BW4HANA/107a6e8a38b74ede94c833ca3b7b6f51/ff09575df3614f3da5738ea14e72b703.html">
          {t("INSTRUCTION_PREREQUISITES_LINK")}
        </a>
      </div>
      <p className="instruction-list ">{t("INSTRUCTION_PREREQUISITES_2")}</p>
      <p className="sub-heading padtop">{t("PROCEDURE")}</p> */}
      <p className="sub-heading">{t("PROCEDURE")}</p>
      {uploadGuideLines.map((item, index) => (
        <div className="instruction-list-container">
          <p key={index} className="instruction-list number">
            {t(index + 1)}.
          </p>
          <p key={index} className="instruction-list text">
            {t(item)}
          </p>
        </div>
      ))}
    </div>
  );
};

const CustomIcon = (props) => {
  if (!props.Icon) return null;
  return <props.Icon fill={props.color} {...props} />;
};

// Performs resource mapping and data filtering for Excel files based on provided schema data, hierarchy, and file data.
const resourceMappingAndDataFilteringForExcelFiles = (schemaData, hierarchy, selectedFileType, fileDataToStore, t) => {
  let resourceMappingData = [];
  let newFileData = {};
  let toAddInResourceMapping;
  if (selectedFileType.id === EXCEL && fileDataToStore) {
    // Extract all unique column names from fileDataToStore and then doing thir resource mapping
    const columnForMapping = new Set(Object.values(fileDataToStore).flatMap((value) => value?.[0] || []));
    if (schemaData?.schema?.["Properties"]) {
      const schemaKeys = Object.keys(schemaData.schema["Properties"]).concat(hierarchy);
      schemaKeys.forEach((item) => {
        if (columnForMapping.has(t(item))) {
          resourceMappingData.push({
            mappedFrom: t(item),
            mappedTo: item,
          });
        }
      });
    }

    // Filtering the columns with respect to the resource mapping and removing the columns that are not needed
    Object.entries(fileDataToStore).forEach(([key, value]) => {
      let data = [];
      let headers = [];
      let toRemove = [];
      if (value && value.length > 0) {
        value[0].forEach((item, index) => {
          const mappedTo = resourceMappingData.find((e) => e.mappedFrom === item)?.mappedTo;
          if (!mappedTo) {
            toRemove.push(index);
            return;
          }
          headers.push(mappedTo);
          return;
        });
        for (let i = 1; i < value?.length; i++) {
          let temp = [];
          for (let j = 0; j < value[i].length; j++) {
            if (!toRemove.includes(j)) {
              temp.push(value[i][j]);
            }
          }
          data.push(temp);
        }
      }
      newFileData[key] = [headers, ...data];
    });
  }
  return { tempResourceMappingData: resourceMappingData, tempFileDataToStore: newFileData };
};

const prepareExcelFileBlobWithErrors = async (data, errors, t) => {
  let tempData = { ...data };
  // Process each dataset within the data object
  const processedData = {};
  for (const key in tempData) {
    if (tempData.hasOwnProperty(key)) {
      const dataset = [...tempData[key]];

      // Add the 'error' column to the header
      dataset[0] = dataset[0].map((item) => t(item));
      // Process each data row
      if (errors) {
        dataset[0].push(t("MICROPLAN_ERROR_COLUMN"));
        for (let i = 1; i < dataset.length; i++) {
          const row = dataset[i];

          // Check if there are errors for the given commonColumnData
          const errorInfo = errors?.[key]?.[i - 1];
          if (errorInfo) {
            let rowDataAddOn = Object.entries(errorInfo)
              .map(([key, value]) => {
                return t(key) + ": " + value.map((item) => t(item)).join(", ");
              })
              .join("\n");
            row.push(rowDataAddOn);
          } else {
            row.push("");
          }
        }
      }
      processedData[key] = dataset;
    }
  }
  const errorColumn = "MICROPLAN_ERROR_COLUMN";
  const style = {
    font: { color: { argb: "B91900" } },
    border: {
      top: { style: "thin", color: { argb: "B91900" } },
      left: { style: "thin", color: { argb: "B91900" } },
      bottom: { style: "thin", color: { argb: "B91900" } },
      right: { style: "thin", color: { argb: "B91900" } },
    },
  };
  let xlsxBlob = await convertJsonToXlsx(processedData, { errorColumn, style });
  return xlsxBlob;
};

const boundaryDataGeneration = async (schemaData, campaignData, t) => {
  let boundaryDataAgainstBoundaryCode = {};
  if (schemaData && !schemaData.doHierarchyCheckInUploadedData) {
    try {
      const rootBoundary = campaignData?.boundaries?.filter((boundary) => boundary.isRoot); // Retrieve session storage data once and store it in a variable
      const sessionData = Digit.SessionStorage.get("microplanHelperData") || {};
      let boundaryData = sessionData.filteredBoundaries;
      let filteredBoundaries;
      if (!boundaryData) {
        // Only fetch boundary data if not present in session storage
        boundaryData = await fetchBoundaryData(Digit.ULBService.getCurrentTenantId(), campaignData?.hierarchyType, rootBoundary?.[0]?.code);
        filteredBoundaries = filterBoundaries(boundaryData, campaignData?.boundaries);

        // Update the session storage with the new filtered boundaries
        Digit.SessionStorage.set("microplanHelperData", {
          ...sessionData,
          filteredBoundaries: filteredBoundaries,
        });
      } else {
        filteredBoundaries = boundaryData;
      }
      const xlsxData = addBoundaryData([], filteredBoundaries)?.[0]?.data;
      xlsxData.forEach((item, i) => {
        if (i === 0) return;
        let boundaryCodeIndex = xlsxData?.[0]?.indexOf(commonColumn);
        if (boundaryCodeIndex >= item.length) {
          // If boundaryCodeIndex is out of bounds, return the item as is
          boundaryDataAgainstBoundaryCode[item[boundaryCodeIndex]] = item.slice().map(t);
        } else {
          // Otherwise, remove the element at boundaryCodeIndex
          boundaryDataAgainstBoundaryCode[item[boundaryCodeIndex]] = item
            .slice(0, boundaryCodeIndex)
            .concat(item.slice(boundaryCodeIndex + 1))
            .map(t);
        }
      });
      return boundaryDataAgainstBoundaryCode;
    } catch (error) {
      console.error(error?.message);
    }
  }
};

export const handleExcelFile = async (
  file,
  schemaData,
  hierarchy,
  selectedFileType,
  boundaryDataAgainstBoundaryCode,
  setUploadedFileError,
  t,
  campaignData
) => {
  try {
    // Converting the file to preserve the sequence of columns so that it can be stored
    let fileDataToStore = await parseXlsxToJsonMultipleSheets(file, { header: 0 });
    if (fileDataToStore[t(BOUNDARY_DATA_SHEET)]) delete fileDataToStore[t(BOUNDARY_DATA_SHEET)];
    let { tempResourceMappingData, tempFileDataToStore } = resourceMappingAndDataFilteringForExcelFiles(
      schemaData,
      hierarchy,
      selectedFileType,
      fileDataToStore,
      t
    );
    fileDataToStore = await convertJsonToXlsx(tempFileDataToStore);
    // Converting the input file to json format
    let result = await parseXlsxToJsonMultipleSheets(fileDataToStore, { header: 1 });
    if (result && result.error) {
      return {
        check: false,
        interruptUpload: true,
        error: result.error,
        fileDataToStore: {},
        toast: { state: "error", message: t("ERROR_CORRUPTED_FILE") },
      };
    }
    let extraColumns = [commonColumn];
    // checking if the hierarchy and common column is present the  uploaded data
    extraColumns = [...hierarchy, commonColumn];
    let data = Object.values(tempFileDataToStore);
    let errorMsg;
    let errors; // object containing the location and type of error
    let toast;
    let hierarchyDataPresent = true;
    let latLngColumns =
      Object.entries(schemaData?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.isLocationDataColumns) {
          acc.push(key);
        }
        return acc;
      }, []) || [];
    data.forEach((item) => {
      const keys = item[0];
      if (keys?.length !== 0) {
        if (!extraColumns?.every((e) => keys.includes(e))) {
          if (schemaData && !schemaData.doHierarchyCheckInUploadedData) {
            hierarchyDataPresent = false;
          } else {
            errorMsg = {
              check: false,
              interruptUpload: true,
              error: t("ERROR_BOUNDARY_DATA_COLUMNS_ABSENT"),
              fileDataToStore: {},
              toast: { state: "error", message: t("ERROR_BOUNDARY_DATA_COLUMNS_ABSENT") },
            };
          }
        }
        if (!latLngColumns?.every((e) => keys.includes(e))) {
          toast = { state: "warning", message: t("ERROR_UPLOAD_EXCEL_LOCATION_DATA_MISSING") };
        }
      }
    });
    if (errorMsg && !errorMsg?.check) return errorMsg;
    // Running Validations for uploaded file
    let response = await checkForErrorInUploadedFileExcel(result, schemaData.schema, t);
    if (!response.valid) setUploadedFileError(response.message);
    errorMsg = response.message;
    errors = response.errors;
    const missingProperties = response.missingProperties;
    let check = response.valid;
    try {
      if (schemaData && !schemaData.doHierarchyCheckInUploadedData && !hierarchyDataPresent && boundaryDataAgainstBoundaryCode) {
        let tempBoundaryDataAgainstBoundaryCode = (await boundaryDataGeneration(schemaData, campaignData, t)) || {};
        for (const sheet in tempFileDataToStore) {
          const commonColumnIndex = tempFileDataToStore[sheet]?.[0]?.indexOf(commonColumn);
          if (commonColumnIndex !== -1)
            tempFileDataToStore[sheet] = tempFileDataToStore[sheet].map((item, index) => [
              ...(tempBoundaryDataAgainstBoundaryCode[item[commonColumnIndex]]
                ? tempBoundaryDataAgainstBoundaryCode[item[commonColumnIndex]]
                : index !== 0
                ? new Array(hierarchy.length).fill("")
                : []),
              ...item,
            ]);

          tempFileDataToStore[sheet][0] = [...hierarchy, ...tempFileDataToStore[sheet][0]];
        }
      }
    } catch (error) {
      console.error("Error in boundary adding operaiton: ", error);
    }
    tempFileDataToStore = addMissingPropertiesToFileData(tempFileDataToStore, missingProperties);
    return { check, errors, errorMsg, fileDataToStore: tempFileDataToStore, tempResourceMappingData, toast };
  } catch (error) {
    console.error("Error in handling Excel file:", error.message);
  }
};
const addMissingPropertiesToFileData = (data, missingProperties) => {
  if (!data || !missingProperties) return data;
  let tempData = {};
  Object.entries(data).forEach(([key, value], index) => {
    const filteredMissingProperties = [...missingProperties]?.reduce((acc, item) => {
      if (!value?.[0]?.includes(item)) {
        acc.push(item);
      }
      return acc;
    }, []);
    const newTempHeaders = value?.[0].length !== 0 ? [...value[0], ...filteredMissingProperties] : [...filteredMissingProperties];
    console.log(newTempHeaders);
    tempData[key] = [newTempHeaders, ...value.slice(1)];
  });
  return tempData;
};

const handleGeojsonFile = async (file, schemaData, setUploadedFileError, t) => {
  // Reading and checking geojson data
  const data = await readGeojson(file, t);
  if (!data.valid) {
    return { check: false, stopUpload: true, toast: data.toast };
  }

  // Running geojson validaiton on uploaded file
  let response = geojsonValidations(data.geojsonData, schemaData.schema, t);
  if (!response.valid) setUploadedFileError(response.message);
  let check = response.valid;
  let error = response.message;
  let fileDataToStore = data.geojsonData;
  return { check, error, fileDataToStore };
};

const handleShapefiles = async (file, schemaData, setUploadedFileError, selectedFileType, setToast, t) => {
  // Reading and validating the uploaded geojson file
  let response = await readAndValidateShapeFiles(file, t, selectedFileType["namingConvention"]);
  if (!response.valid) {
    setUploadedFileError(response.message);
    setToast(response.toast);
  }
  let check = response.valid;
  let error = response.message;
  let fileDataToStore = response.data;
  return { check, error, fileDataToStore };
};

export default Upload;
