import React, { useState, useEffect, useMemo, Fragment, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import { ModalWrapper } from "./Modal";
import { geojsonPropertiesValidation } from "../utils/geojsonValidations";
import { SpatialDataPropertyMapping } from "./resourceMapping";
import { JsonPreviewInExcelForm } from "./JsonPreviewInExcelForm";
import { ButtonType1, ButtonType2, CloseButton, ModalHeading } from "./CommonComponents";
import { Loader } from "@egovernments/digit-ui-components";
import { EXCEL, FILE_STORE, GEOJSON, PRIMARY_THEME_COLOR, SHAPEFILE } from "../configs/constants";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import { v4 as uuidv4 } from "uuid";
import {
  handleExcelFile,
  validateNamingConvention,
  findReadMe,
  downloadTemplate,
  getSchema,
  prepareExcelFileBlobWithErrors,
  boundaryDataGeneration,
  handleGeojsonFile,
  handleShapefiles,
  convertToSheetArray,
  findGuideLine,
  delay,
} from "../utils/uploadUtils";
import { UploadGuideLines, UploadedFile, FileUploadComponent, UploadComponents, UploadInstructions, UploadSection } from "./UploadHelperComponents";

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
  const [uploadedFileError, setUploadedFileError] = useState();
  const [fileDataList, setFileDataList] = useState([]);
  const [validationSchemas, setValidationSchemas] = useState([]);
  const [template, setTemplate] = useState([]);
  const [resourceMapping, setResourceMapping] = useState([]);
  const [previewUploadedData, setPreviewUploadedData] = useState();
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
        return (
          data?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.map(
            (item) => `${campaignData?.hierarchyType}_${Digit.Utils.microplan.transformIntoLocalisationCode(item?.boundaryType)}`
          ) || {}
        );
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
            const filteredList = fileDataList?.filter((e) => e.active && e.templateIdentifier === item.id);
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

  // UseEffect to extract data on first render
  useEffect(() => {
    if (microplanData?.upload) {
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
      const uploadSections = state?.UploadConfiguration;
      const schemas = state?.Schemas;
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

  const showDownloadTemplate = () => {
    if (selectedSection?.UploadFileTypes) {
      const schema = getSchema(campaignType, selectedFileType?.id, selectedSection.id, validationSchemas);
      if (schema?.template?.showTemplateDownload) return true;
    }
    return false;
  };

  // Handler for when a file type is selected for uplaod
  const selectFileTypeHandler = (e) => {
    if (selectedSection?.UploadFileTypes) {
      const schema = getSchema(campaignType, e.target.name, selectedSection.id, validationSchemas);
      setSelectedFileType(selectedSection.UploadFileTypes.find((item) => item.id === e.target.name));
      if (schema?.template?.showTemplateDownload) setModal("upload-modal");
      else UploadFileClickHandler(false);
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
  const readMeConstant = state?.CommonConstants?.find((item) => item?.name === "readMeSheetName");
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
      hierarchy,
      readMeData: state?.ReadMeData,
      readMeSheetName: readMeConstant ? readMeConstant.value : undefined,
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
      let additionalSheets = [];
      // Handling different filetypes
      switch (selectedFileType.id) {
        case EXCEL:
          // let response = handleExcelFile(file,schemaData);
          try {
            response = await handleExcelFile(
              file,
              schemaData,
              hierarchy,
              selectedFileType,
              {},
              setUploadedFileError,
              t,
              campaignData,
              state?.CommonConstants?.find((item) => item?.name === "readMeSheetName")?.value
            );
            check = response.check;
            errorMsg = response.errorMsg;
            errorLocationObject = response.errors;
            fileDataToStore = response.fileDataToStore;
            resourceMappingData = response?.tempResourceMappingData || [];
            additionalSheets = response?.additionalSheets;
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
          console.error(errorData.message);
          errorMsg = t("ERROR_UPLOADING_FILE");
          setToast({ state: "error", message: t("ERROR_UPLOADING_FILE") });
          setUploadedFileError(errorMsg);
          handleValidationErrorResponse(t("ERROR_UPLOADING_FILE"));
          return;
        }
      }

      if (selectedFileType.id === EXCEL) {
        resourceMappingData = resourceMappingData.map((item) => ({ ...item, filestoreId }));
      }
      const uuid = uuidv4();
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
        additionalSheets,
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
      console.error(error.message);
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

  const convertAndCombineFileData = () => {
    let combinedData = fileData?.data ? Object.entries(fileData.data)?.map(([key, value]) => ({ sheetName: key, data: value })) : [];
    if (fileData?.additionalSheets) {
      for (const sheet of fileData.additionalSheets) {
        if (sheet?.data && sheet.sheetName) {
          const index = sheet?.position < combinedData.length && sheet.position !== -1 ? sheet.position : combinedData.length;
          combinedData.splice(index, 0, sheet);
        }
      }
    }
    return combinedData;
  };

  // Function for creating blob out of data
  const dataToBlob = async () => {
    try {
      let blob;
      const schema = getSchema(campaignType, selectedFileType.id, selectedSection.id, validationSchemas);
      const filteredReadMeData = findReadMe(state?.ReadMeData, campaignType, selectedFileType.id, selectedSection.id);
      let combinedData = convertAndCombineFileData();
      const readMeSheetName = state?.CommonConstants?.find((item) => item?.name === "readMeSheetName")?.value;
      switch (fileData.fileType) {
        case EXCEL:
          if (fileData?.errorLocationObject?.length !== 0)
            blob = await prepareExcelFileBlobWithErrors(
              combinedData,
              fileData.errorLocationObject,
              schema,
              hierarchy,
              filteredReadMeData,
              readMeSheetName,
              t
            );
          else blob = fileData.file;
          break;
        case SHAPEFILE:
        case GEOJSON:
          if (fileData?.data) {
            const result = convertToSheetArray(Digit.Utils.microplan.convertGeojsonToExcelSingleSheet(fileData?.data?.features, fileData?.section));

            if (fileData?.errorLocationObject?.length !== 0)
              blob = await prepareExcelFileBlobWithErrors(
                result,
                fileData.errorLocationObject,
                schema,
                hierarchy,
                filteredReadMeData,
                readMeSheetName,
                t
              );
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
    setLoader("LOADING");
    try {
      await delay(100);
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
    setLoader(false);
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
      if (hierarchy.every((item) => !mappedToList.includes(t(item)))) {
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
      console.error(error.message);
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
      }
      error = t("ERROR_UPLOADING_FILE");
      setToast({ state: "error", message: t("ERROR_UPLOADING_FILE") });
      setResourceMapping([]);
      setUploadedFileError(error);
    } catch (errorData) {
      console.error("Error while uploading file to filestore: ", errorData?.message);
      let error = t("ERROR_UPLOADING_FILE");
      handleValidationErrorResponse(error);
      setResourceMapping([]);
      return;
    }
  };
  const computeMappedDataAndItsValidations = (schemaData) => {
    const data = computeGeojsonWithMappedProperties();
    const response = geojsonPropertiesValidation(data, schemaData.schema, fileData?.section, t);
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
                    showDownloadTemplate={showDownloadTemplate}
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
                    showDownloadTemplate={showDownloadTemplate}
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
                  label={t(`HEADING_DOWNLOAD_TEMPLATE_FOR_${selectedSection.code}_${selectedFileType.code}`)}
                />
              }
              bodyText={t(`INSTRUCTIONS_DOWNLOAD_TEMPLATE_FOR_${selectedSection.code}_${selectedFileType.code}`)}
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
              <UploadGuideLines
                uploadGuideLines={findGuideLine(campaignType, selectedFileType.id, selectedSection.id, state?.UploadGuidelines)}
                t={t}
              />
            </Modal>
          )}
          {loader && <LoaderWithGap text={t(loader)} />}

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
      </div>
    </>
  );
};

export default Upload;
