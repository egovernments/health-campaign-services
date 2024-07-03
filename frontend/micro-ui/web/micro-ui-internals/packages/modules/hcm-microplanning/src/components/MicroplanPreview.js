import { Header, Loader } from "@egovernments/digit-ui-components";
import React, { useCallback, useEffect, useMemo, useState, Fragment } from "react";
import { useTranslation } from "react-i18next";
import { processHierarchyAndData } from "../utils/processHierarchyAndData";
import { ModalHeading } from "./CommonComponents";
import { PRIMARY_THEME_COLOR } from "../configs/constants";
import { LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import {
  fetchMicroplanPreviewData,
  filterObjects,
  updateHyothesisAPICall,
  filterMicroplanDataToShowWithHierarchySelection,
} from "../utils/microplanPreviewUtils";
import {
  HypothesisValues,
  BoundarySelection,
  DataPreview,
  AppplyChangedHypothesisConfirmation,
  Aggregates,
} from "./MicroplanPreviewHelperCompoenents";

const page = "microplanPreview";

const MicroplanPreview = ({
  campaignType = Digit.SessionStorage.get("microplanHelperData")?.campaignData?.projectType,
  microplanData,
  setMicroplanData,
  checkDataCompletion,
  setCheckDataCompletion,
  currentPage,
  pages,
  navigationEvent,
  setToast,
  ...props
}) => {
  const { mutate: UpdateMutate } = Digit.Hooks.microplan.useUpdatePlanConfig();
  const userInfo = Digit.SessionStorage.get("User")?.info;
  const { id: campaignId = "" } = Digit.Hooks.useQueryParams();
  const { t } = useTranslation();
  const [hypothesisAssumptionsList, setHypothesisAssumptionsList] = useState([]);
  const [data, setData] = useState([]);
  const [dataToShow, setDataToShow] = useState([]);
  const [joinByColumns, setJoinByColumns] = useState([]);
  const [validationSchemas, setValidationSchemas] = useState([]);
  const [resources, setResources] = useState([]);
  const [formulaConfiguration, setFormulaConfiguration] = useState([]);
  const [boundarySelections, setBoundarySelections] = useState({}); // state for hierarchy from the data available from uploaded data
  const [boundaryData, setBoundaryData] = useState({}); // State for boundary data
  // const [toast, setToast] = useState();
  const [modal, setModal] = useState("none");
  const [operatorsObject, setOperatorsObject] = useState([]);

  const [loaderActivation, setLoaderActivation] = useState(false);

  const [userEditedResources, setUserEditedResources] = useState({}); // state to maintain a record of the resources that the user has edited ( boundaryCode : {resource : value})
  const [microplanPreviewAggregates, setMicroplaPreviewAggregates] = useState();
  const { state, dispatch } = useMyContext();
  const [updateHypothesis, setUpdateHypothesis] = useState(false);
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
        tenantId: Digit.ULBService.getStateId(),
        hierarchyType: campaignData?.hierarchyType,
      },
    },
    config: {
      enabled: !!campaignData?.hierarchyType,
      select: (data) => {
        return (
          data?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.map((item) => ({
            ...item,
            parentBoundaryType: item?.parentBoundaryType
              ? `${campaignData?.hierarchyType}_${Digit.Utils.microplan.transformIntoLocalisationCode(item?.parentBoundaryType)}`
              : null,
            boundaryType: `${campaignData?.hierarchyType}_${Digit.Utils.microplan.transformIntoLocalisationCode(item?.boundaryType)}`,
          })) || {}
        );
      },
    },
  };
  const { isLoading: ishierarchyLoading, data: hierarchyRawData } = Digit.Hooks.useCustomAPIHook(reqCriteria);
  const hierarchy = useMemo(() => {
    return hierarchyRawData?.map((item) => item?.boundaryType);
  }, [hierarchyRawData]);
  // Set TourSteps
  useEffect(() => {
    const tourData = tourSteps(t)?.[page] || {};
    if (state?.tourStateData?.name === page) return;
    dispatch({
      type: "SETINITDATA",
      state: { tourStateData: tourData },
    });
  }, []);

  // UseEffect to extract data on first render
  useEffect(() => {
    if (microplanData && (microplanData?.ruleEngine || microplanData?.hypothesis)) {
      const hypothesisAssumptions = microplanData?.hypothesis || [];
      const formulaConfiguration = microplanData?.ruleEngine?.filter((item) => Object.values(item).every((key) => key !== "")) || [];
      if (hypothesisAssumptions.length !== 0 && hypothesisAssumptionsList.length === 0) {
        setHypothesisAssumptionsList(hypothesisAssumptions);
      }
      if (formulaConfiguration.length !== 0) {
        setFormulaConfiguration(formulaConfiguration);
      }
    }
    if (microplanData?.microplanPreview?.userEditedResources) {
      setUserEditedResources(microplanData?.microplanPreview?.userEditedResources);
    }
  }, []);

  // Fetch and assign MDMS data
  useEffect(() => {
    if (!state) return;
    const UIConfiguration = state?.UIConfiguration;
    const schemas = state?.Schemas;
    let resourcelist = state?.Resources;
    let microplanPreviewAggregatesList = state?.MicroplanPreviewAggregates;
    microplanPreviewAggregatesList = microplanPreviewAggregatesList.find((item) => item.campaignType === campaignType)?.data;
    if (schemas) setValidationSchemas(schemas);
    resourcelist = resourcelist.find((item) => item.campaignType === campaignType)?.data;
    if (resourcelist) setResources(resourcelist);
    if (UIConfiguration) {
      const joinWithColumns = UIConfiguration.find((item) => item.name === "microplanPreview")?.joinWithColumns;
      setJoinByColumns(joinWithColumns);
    }
    let temp;
    if (UIConfiguration) temp = UIConfiguration.find((item) => item.name === "ruleConfigure");
    if (temp?.ruleConfigureOperators) {
      setOperatorsObject(temp.ruleConfigureOperators);
    }
    if (microplanPreviewAggregatesList) setMicroplaPreviewAggregates(microplanPreviewAggregatesList);
  }, []);

  // UseEffect for checking completeness of data before moveing to next section
  useEffect(() => {
    if (!dataToShow || checkDataCompletion !== "true" || !setCheckDataCompletion) return;
    const check = filterObjects(hypothesisAssumptionsList, microplanData?.hypothesis);
    if (check.length === 0) {
      if (navigationEvent?.name === "next") return setModal("confirm-microplan-generation");
      return createMicroplan(false, false);
    }
    setModal("confirm-apply-changed-hypothesis");
  }, [checkDataCompletion]);

  // check if data has changed or not
  const updateData = useCallback(
    (doPerform) => {
      // Update the microplan data with selected hierarchy and resources
      // This function also handles setting the completion check based on the action to be performed
      if (!setMicroplanData) return;
      try {
        let tempData = filterMicroplanDataToShowWithHierarchySelection(data, {}, hierarchy);
        // Adding resources to the data we need to show
        tempData = Digit.Utils.microplan.addResourcesToFilteredDataToShow(
          tempData,
          resources,
          hypothesisAssumptionsList,
          formulaConfiguration,
          userEditedResources,
          t
        );
        setMicroplanData((previous) => ({
          ...previous,
          microplanPreview: {
            previewData: tempData,
            userEditedResources,
          },
        }));
        if (doPerform) {
          return setCheckDataCompletion("perform-action");
        }
        setCheckDataCompletion("false");
      } catch (error) {
        console.error("Failed to update data:", error);
      }
    },
    [
      resources,
      boundarySelections,
      hierarchy,
      hypothesisAssumptionsList,
      formulaConfiguration,
      userEditedResources,
      setMicroplanData,
      setCheckDataCompletion,
    ]
  );

  const cancelUpdateData = useCallback(() => {
    setUpdateHypothesis(false);
    if (navigationEvent?.name === "next") setModal("confirm-microplan-generation");
    else createMicroplan(false, false);
  }, [setCheckDataCompletion, setModal]);

  useEffect(() => {
    if (boundarySelections && Object.values(boundarySelections).every((item) => item.length === 0) && hierarchy) {
      const tempBoundarySelection = {};
      for (const item of hierarchy) {
        tempBoundarySelection[item] = [];
      }
      setBoundarySelections(tempBoundarySelection);
    }
  }, [hierarchy]);

  // UseEffect to add a event listener for keyboard
  useEffect(() => {
    window.addEventListener("keydown", handleKeyPress);

    return () => window.removeEventListener("keydown", handleKeyPress);
  }, [modal]);

  const handleKeyPress = (event) => {
    // if (modal !== "upload-guidelines") return;
    if (["x", "Escape"].includes(event.key)) {
      // Perform the desired action when "x" or "esc" is pressed
      setCheckDataCompletion("false");
      setModal("none");
    }
  };

  const cancleNavigation = () => {
    if (navigationEvent?.name !== "next") setCheckDataCompletion("false");
    setModal("none");
  };

  const createMicroplan = useCallback(
    (doCreation, updateHypothesis) => {
      if (!hypothesisAssumptionsList || !setMicroplanData) return;
      const updateDataWrapper = () => {
        if (doCreation || navigationEvent?.name !== "next") {
          return updateData(true);
        }
        updateData(false);
      };
      const setCheckDataCompletionWrapper = (value) => {
        if (!doCreation) {
          return setCheckDataCompletion("false");
        }
        setCheckDataCompletion(value);
      };
      const microData = updateHypothesis ? updateMicroplanData(hypothesisAssumptionsList) : microplanData;
      setLoaderActivation(true);
      updateHyothesisAPICall(
        microData,
        setMicroplanData,
        operatorsObject,
        microData?.microplanDetails?.name,
        campaignId,
        UpdateMutate,
        setToast,
        updateDataWrapper,
        setLoaderActivation,
        doCreation && navigationEvent?.name === "next" ? "GENERATED" : "DRAFT",
        cancleNavigation,
        state,
        campaignType,
        navigationEvent,
        setCheckDataCompletionWrapper,
        t
      );

      setUpdateHypothesis(false);
      setModal("none");
    },
    [
      hypothesisAssumptionsList,
      setMicroplanData,
      operatorsObject,
      campaignId,
      UpdateMutate,
      setToast,
      updateData,
      setLoaderActivation,
      navigationEvent,
      t,
    ]
  );

  const updateMicroplanData = useCallback(
    (hypothesisAssumptionsList) => {
      let microData = {};
      setMicroplanData((previous) => {
        microData = { ...previous, hypothesis: hypothesisAssumptionsList };
        return microData;
      });
      return microData;
    },
    [setMicroplanData]
  );

  // Set microplan preview data
  useEffect(() => {
    if (data?.length !== 0 || !hierarchyRawData || !hierarchy || validationSchemas?.length === 0) return;

    const combinedData = fetchMicroplanPreviewData(campaignType, microplanData, validationSchemas, hierarchy);
    // process and form hierarchy
    if (combinedData && hierarchy) {
      const { hierarchyLists, hierarchicalData } = processHierarchyAndData(hierarchyRawData, [combinedData]);
      setBoundaryData({ Microplan: { hierarchyLists, hierarchicalData } });
    }
    if (combinedData) {
      setData(combinedData);
      setDataToShow(combinedData);
    }
  }, [hierarchy, hierarchyRawData, microplanData]);

  useEffect(() => {
    if (!boundarySelections && !resources) return;
    let tempData = filterMicroplanDataToShowWithHierarchySelection(data, boundarySelections, hierarchy);
    // Adding resources to the data we need to show
    tempData = Digit.Utils.microplan.addResourcesToFilteredDataToShow(
      tempData,
      resources,
      hypothesisAssumptionsList,
      formulaConfiguration,
      userEditedResources,
      t
    );
    setDataToShow(tempData);
    setMicroplanData((previous) => ({ ...previous, microplanPreview: { ...previous.microplanPreview, previewData: tempData, userEditedResources } }));
  }, [boundarySelections, resources, hypothesisAssumptionsList, userEditedResources]);

  if (isCampaignLoading || ishierarchyLoading) {
    return (
      <div className="api-data-loader">
        <Loader />
      </div>
    );
  }

  return (
    <>
      <div className={`jk-header-btn-wrapper microplan-preview-section`}>
        <div className="top-section">
          <p className="campaign-name">{t(campaignData?.campaignName)}</p>
          <Header className="heading">{t(microplanData?.microplanDetails?.name)}</Header>
          <p className="user-name">{t("MICROPLAN_PREVIEW_CREATE_BY", { username: userInfo?.name })}</p>
        </div>
        <div className="hierarchy-selection-container">
          <div className="hierarchy-selection">
            <BoundarySelection
              boundarySelections={boundarySelections}
              setBoundarySelections={setBoundarySelections}
              boundaryData={boundaryData}
              hierarchy={hierarchyRawData}
              t={t}
            />
          </div>
        </div>
        <Aggregates
          microplanPreviewAggregates={microplanPreviewAggregates}
          dataToShow={dataToShow}
          NumberFormatMappingForTranslation={state?.NumberFormatMappingForTranslation}
          t={t}
        />
        <div className="microplan-preview-body">
          <div className="hypothesis-container">
            <p className="hypothesis-heading">{t("MICROPLAN_PREVIEW_HYPOTHESIS_HEADING")}</p>
            <p className="instructions">{t("MICROPLAN_PREVIEW_HYPOTHESIS_INSTRUCTIONS")}</p>
            <HypothesisValues
              boundarySelections={boundarySelections}
              hypothesisAssumptionsList={hypothesisAssumptionsList}
              setHypothesisAssumptionsList={setHypothesisAssumptionsList}
              setToast={setToast}
              modal={modal}
              setModal={setModal}
              setMicroplanData={setMicroplanData}
              operatorsObject={operatorsObject}
              t={t}
            />
          </div>
          <div className="preview-container">
            {dataToShow?.length != 0 ? (
              <DataPreview
                previewData={dataToShow}
                isCampaignLoading={isCampaignLoading}
                userEditedResources={userEditedResources}
                setUserEditedResources={setUserEditedResources}
                resources={resources}
                modal={modal}
                setModal={setModal}
                data={data}
                t={t}
              />
            ) : (
              <div className="no-data-available-container">{t("NO_DATA_AVAILABLE")}</div>
            )}
          </div>
        </div>
        {modal === "confirm-apply-changed-hypothesis" && (
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
              backgroundColor: "white",
              border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
            }}
            headerBarMainStyle={{ padding: 0, margin: 0 }}
            headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_PROCEED_WITH_NEW_HYPOTHESIS")} />}
            actionCancelLabel={t("YES")}
            actionCancelOnSubmit={() => {
              setUpdateHypothesis(true);
              if (navigationEvent?.name === "next") setModal("confirm-microplan-generation");
              else createMicroplan(false, true);
            }}
            actionSaveLabel={t("NO")}
            actionSaveOnSubmit={cancelUpdateData}
            formId="modal-action"
          >
            <AppplyChangedHypothesisConfirmation newhypothesisList={hypothesisAssumptionsList} hypothesisList={microplanData?.hypothesis} t={t} />
          </Modal>
        )}
        {modal === "confirm-microplan-generation" && (
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
              backgroundColor: "white",
              border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
            }}
            headerBarMainStyle={{ padding: 0, margin: 0 }}
            headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("HEADING_MICROPLAN_GENERATION_CONFIRMATION")} />}
            actionCancelLabel={t("YES")}
            actionCancelOnSubmit={() => createMicroplan(true, updateHypothesis)}
            actionSaveLabel={t("NO")}
            actionSaveOnSubmit={() => createMicroplan(false, updateHypothesis)}
            formId="modal-action"
          >
            <div className="modal-body">
              <p className="modal-main-body-p">{t("INSTRUCTIONS_MICROPLAN_GENERATION_CONFIRMATION")}</p>
            </div>
          </Modal>
        )}
      </div>
      {loaderActivation && <LoaderWithGap text={"LOADING"} />}
    </>
  );
};

export default MicroplanPreview;
