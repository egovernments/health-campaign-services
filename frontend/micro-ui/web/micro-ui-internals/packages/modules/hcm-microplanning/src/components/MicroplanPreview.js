import { CardLabel, Header, Loader, MultiSelectDropdown, TextInput, Toast } from "@egovernments/digit-ui-components";
import React, { memo, useCallback, useEffect, useMemo, useState, Fragment, useRef } from "react";
import { useTranslation } from "react-i18next";
import { processHierarchyAndData, findParent, fetchDropdownValues } from "../utils/processHierarchyAndData";
import { CloseButton, ModalHeading } from "./CommonComponents";
import { EXCEL, GEOJSON, PRIMARY_THEME_COLOR, SHAPEFILE, commonColumn } from "../configs/constants";
import { Button, LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import { timeLineOptions } from "../configs/timeLineOptions.json";
import { useNumberFormatter } from "../hooks/useNumberFormatter";

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
    let UIConfiguration = state?.UIConfiguration;
    let schemas = state?.Schemas;
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
    let check = filterObjects(hypothesisAssumptionsList, microplanData?.hypothesis);
    if (check.length === 0) {
      if (navigationEvent?.name === "next") return setModal("confirm-microplan-generation");
      return createMicroplan(false);
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
    setModal("confirm-microplan-generation");
  }, [setCheckDataCompletion, setModal]);

  useEffect(() => {
    if (boundarySelections && Object.values(boundarySelections).every((item) => item.length === 0) && hierarchy) {
      let tempBoundarySelection = {};
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
    (doCreation) => {
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

    let combinedData = fetchMicroplanPreviewData(campaignType, microplanData, validationSchemas, hierarchy);
    // process and form hierarchy
    if (combinedData && hierarchy) {
      var { hierarchyLists, hierarchicalData } = processHierarchyAndData(hierarchyRawData, [combinedData]);
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
            {dataToShow?.length !== 0 ? (
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
              setModal("confirm-microplan-generation");
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
            actionCancelOnSubmit={() => createMicroplan(true)}
            actionSaveLabel={t("NO")}
            actionSaveOnSubmit={() => createMicroplan(false)}
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

const HypothesisValues = memo(({ boundarySelections, hypothesisAssumptionsList, setHypothesisAssumptionsList, setToast, setModal, t }) => {
  const [tempHypothesisList, setTempHypothesisList] = useState(hypothesisAssumptionsList || []);
  const { valueChangeHandler } = useHypothesis(tempHypothesisList, hypothesisAssumptionsList);
  const contentRef = useRef(null);
  const [isScrollable, setIsScrollable] = useState(false);

  const applyNewHypothesis = () => {
    if (tempHypothesisList.some((item) => item.active && parseFloat(item.value) === 0)) {
      setToast({ state: "error", message: t("ERROR_HYPOTHESIS_VALUE_SHOULD_NOT_BE_ZERO") });
      return;
    }
    if (Object.keys(boundarySelections).length !== 0 && Object.values(boundarySelections)?.every((item) => item?.length !== 0))
      return setToast({ state: "error", message: t("HYPOTHESIS_CAN_BE_ONLY_APPLIED_ON_ADMIN_LEVEL_ZORO") });
    setHypothesisAssumptionsList(tempHypothesisList);
  };
  const checkScrollbar = () => {
    if (contentRef.current) {
      setIsScrollable(contentRef.current.scrollHeight > contentRef.current.clientHeight);
    }
  };

  useEffect(() => {
    // Initial check
    checkScrollbar();

    // Check on resize
    window.addEventListener("resize", checkScrollbar);

    // Cleanup event listeners on component unmount
    return () => {
      window.removeEventListener("resize", checkScrollbar);
    };
  }, []);

  useEffect(() => {
    const content = contentRef.current;
    content.addEventListener("scroll", checkScrollbar);

    return () => {
      content.removeEventListener("scroll", checkScrollbar);
    };
  }, [contentRef]);

  return (
    <div className="hypothesis-list-wrapper">
      <div className={`hypothesis-list ${isScrollable ? "scrollable" : ""}`} ref={contentRef}>
        {tempHypothesisList
          .filter((item) => item?.active)
          ?.filter((item) => item.key !== "")
          .map((item, index) => (
            <div key={`hyopthesis_${index}`} className="hypothesis-list-entity">
              <p>{t(item?.key)}</p>
              <div className="input">
                {/* Dropdown for boundaries */}
                <TextInput
                  name={`hyopthesis_${index}`}
                  type={"text"}
                  className="text-input"
                  value={item?.value}
                  t={t}
                  config={{}}
                  onChange={(value) =>
                    valueChangeHandler({ item, newValue: value?.target?.value }, setTempHypothesisList, boundarySelections, setToast, t)
                  }
                  disable={false}
                />
              </div>
            </div>
          ))}
      </div>
      <div className="hypothesis-controllers">
        <Button className={"button-primary"} style={{ width: "100%" }} onButtonClick={applyNewHypothesis} label={t("MICROPLAN_PREVIEW_APPLY")} />
      </div>
    </div>
  );
});

const BoundarySelection = memo(({ boundarySelections, setBoundarySelections, boundaryData, hierarchy, t }) => {
  const [processedHierarchy, setProcessedHierarchy] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [changedBoundaryType, setChangedBoundaryType] = useState("");

  // Filtering out dropdown values
  useEffect(() => {
    if (!boundaryData || !hierarchy) return;

    let processedHierarchyTemp = fetchDropdownValues(
      boundaryData,
      processedHierarchy.length !== 0 ? processedHierarchy : hierarchy,
      boundarySelections,
      changedBoundaryType
    );
    setProcessedHierarchy(processedHierarchyTemp);
    setIsLoading(false);
  }, [boundaryData, hierarchy, boundarySelections]);

  return (
    <div className="boundary-selection">
      {isLoading && <LoaderWithGap text={"LOADING"} />}
      {processedHierarchy?.map((item, index) => (
        <div key={index} className="hierarchy-selection-element">
          <CardLabel className="header">{t(item?.boundaryType)}</CardLabel>
          {item?.parentBoundaryType === null ? (
            <MultiSelectDropdown
              defaultLabel={t("SELECT_HIERARCHY", { heirarchy: t(item?.boundaryType) })}
              selected={boundarySelections?.[item?.boundaryType]}
              style={{ maxWidth: "23.75rem", margin: 0 }}
              ServerStyle={(item?.dropDownOptions || []).length > 5 ? { height: "13.75rem" } : {}}
              type={"multiselectdropdown"}
              t={t}
              options={item?.dropDownOptions || []}
              optionsKey="name"
              addSelectAllCheck={true}
              onSelect={(e) => {
                setChangedBoundaryType(item?.boundaryType);
                Digit.Utils.microplan.handleSelection(
                  e,
                  item?.boundaryType,
                  boundarySelections,
                  hierarchy,
                  setBoundarySelections,
                  boundaryData,
                  setIsLoading
                );
              }}
            />
          ) : (
            <MultiSelectDropdown
              defaultLabel={t("SELECT_HIERARCHY", { heirarchy: t(item?.boundaryType) })}
              selected={boundarySelections?.[item?.boundaryType]}
              style={{ maxWidth: "23.75rem", margin: 0 }}
              ServerStyle={(item?.dropDownOptions || []).length > 5 ? { height: "13.75rem" } : {}}
              type={"multiselectdropdown"}
              t={t}
              options={Digit.Utils.microplan.processDropdownForNestedMultiSelect(item?.dropDownOptions) || []}
              optionsKey="name"
              addSelectAllCheck={true}
              onSelect={(e) => {
                setChangedBoundaryType(item?.boundaryType);
                Digit.Utils.microplan.handleSelection(
                  e,
                  item?.boundaryType,
                  boundarySelections,
                  hierarchy,
                  setBoundarySelections,
                  boundaryData,
                  setIsLoading
                );
              }}
              variant="nestedmultiselect"
            />
          )}
        </div>
      ))}
    </div>
  );
});

const DataPreview = memo(
  ({ previewData, isCampaignLoading, ishierarchyLoading, resources, userEditedResources, setUserEditedResources, modal, setModal, data, t }) => {
    if (!previewData) return;
    const [tempResourceChanges, setTempResourceChanges] = useState(userEditedResources);
    const [selectedRow, setSelectedRow] = useState();
    const conmmonColumnIndex = useMemo(() => {
      return previewData?.[0]?.indexOf(commonColumn);
    }, [previewData]);
    if (isCampaignLoading || ishierarchyLoading) {
      return (
        <div className="api-data-loader">
          <Loader />
        </div>
      );
    }

    const rowClick = useCallback((rowIndex) => {
      setSelectedRow(rowIndex);
      setModal("change-preview-data");
    }, []);

    const finaliseRowDataChange = () => {
      setUserEditedResources(tempResourceChanges);
      setModal("none");
      setSelectedRow(undefined);
    };

    const modalCloseHandler = () => {
      setModal("none");
      setSelectedRow(undefined);
    };

    return (
      <div className="excel-wrapper">
        <div className="sheet-wrapper">
          <table className="excel-table">
            <thead>
              <tr>
                {previewData[0].map((header, columnIndex) => (
                  <th key={columnIndex} className="no-hover-row">
                    {t(header)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {previewData.slice(1).map((rowData, rowIndex) => {
                const rowDataList = Object.values(previewData[0]).map((header, cellIndex) => (
                  <td
                    className={`${selectedRow && selectedRow - 1 === rowIndex ? "selected-row" : ""}`}
                    key={cellIndex}
                    style={{
                      ...(rowData[cellIndex] || rowData[cellIndex] === 0 ? (!isNaN(rowData[cellIndex]) ? { textAlign: "end" } : {}) : {}),
                      ...(userEditedResources?.[rowData?.[conmmonColumnIndex]]?.[header] ||
                      userEditedResources?.[rowData?.[conmmonColumnIndex]]?.[header] === 0
                        ? { backgroundColor: "rgba(244, 119, 56, 0.12)" }
                        : {}),
                    }}
                  >
                    {cellIndex === 0 &&
                      userEditedResources?.[rowData?.[conmmonColumnIndex]] &&
                      Object.keys(userEditedResources?.[rowData?.[conmmonColumnIndex]]).length !== 0 && <div className="edited-row-marker" />}

                    {rowData[cellIndex] || rowData[cellIndex] === 0 ? rowData[cellIndex] : t("NO_DATA")}
                  </td>
                ));
                return (
                  <tr
                    key={rowIndex}
                    onDoubleClick={() => {
                      rowClick(rowIndex + 1);
                    }}
                    // style={{...(userEditedResources?.[rowData?.[conmmonColumnIndex]] && Object.keys(userEditedResources?.[rowData?.[conmmonColumnIndex]]).length !==0
                    //     ? { borderL: "1px solid rgba(244, 119, 56, 0.12)" }
                    //     : {}),}}
                  >
                    {rowDataList}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {modal === "change-preview-data" && (
          <div className="popup-wrap-focus">
            <Modal
              popupStyles={{ width: "80%", maxHeight: "37.938rem", borderRadius: "0.25rem" }}
              popupModuleActionBarStyles={{
                display: "flex",
                flex: 1,
                justifyContent: "flex-end",
                width: "100%",
                padding: "1rem",
              }}
              style={{
                backgroundColor: "white",
                border: `0.063rem solid ${PRIMARY_THEME_COLOR}`,
                marginTop: "0.5rem",
                marginBottom: "0.5rem",
                marginRight: "1.4rem",
                height: "2.5rem",
                width: "12.5rem",
              }}
              headerBarMainStyle={{ padding: "0 0 0 0.5rem" }}
              headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("EDIT_ROW", { rowNumber: selectedRow })} />}
              headerBarEnd={<CloseButton clickHandler={modalCloseHandler} style={{ padding: "0.4rem 0.8rem 0 0" }} />}
              actionCancelLabel={t("CANCLE")}
              actionCancelOnSubmit={modalCloseHandler}
              actionSaveLabel={t("SAVE_CHANGES")}
              actionSaveOnSubmit={finaliseRowDataChange}
              formId="modal-action"
            >
              <EditResourceData
                selectedRow={selectedRow}
                previewData={previewData}
                resources={resources}
                tempResourceChanges={tempResourceChanges}
                setTempResourceChanges={setTempResourceChanges}
                data={data}
                t={t}
              />
            </Modal>
          </div>
        )}
      </div>
    );
  }
);

// get schema for validation
const getRequiredColumnsFromSchema = (campaignType, microplanData, schemas) => {
  if (!schemas || !microplanData || !microplanData?.upload || !campaignType) return [];
  let sortData = [];
  if (microplanData?.upload) {
    for (const value of microplanData.upload) {
      if (value.active && value?.error === null) {
        sortData.push({ section: value.section, fileType: value?.fileType });
      }
    }
  }
  const filteredSchemas =
    schemas?.filter((schema) => {
      if (schema.campaignType) {
        return schema.campaignType === campaignType && sortData.some((entry) => entry.section === schema.section && entry.fileType === schema.type);
      }
      return sortData.some((entry) => entry.section === schema.section && entry.fileType === schema.type);
    }) || [];

  let finalData = [];
  let tempdata;
  // tempdata = filteredSchemas
  //   ?.map((item) =>
  //     Object.entries(item?.schema?.Properties || {}).reduce((acc, [key, value]) => {
  //       if (value?.isLocationDataColumns) {
  //         acc.push(key);
  //       }
  //       return acc;
  //     }, [])
  //   )
  //   .flatMap((item) => item)
  //   .filter((item) => !!item);
  // finalData = [...finalData, ...tempdata];
  tempdata = filteredSchemas
    ?.flatMap((item) =>
      Object.entries(item?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.isRuleConfigureInputs && value?.toShowInMicroplanPreview) {
          acc.push(key);
        }
        return acc;
      }, [])
    )
    .flat()
    .filter((item) => !!item);
  finalData = [...finalData, ...tempdata];

  tempdata = filteredSchemas
    ?.flatMap((item) =>
      Object.entries(item?.schema?.Properties || {}).reduce((acc, [key, value]) => {
        if (value?.toShowInMicroplanPreview) acc.push(key);
        return acc;
      }, [])
    )
    .flat()
    .filter((item) => !!item);
  finalData = [...finalData, ...tempdata];
  return [...new Set(finalData)];
};

/**
 * Combines two datasets based on a common column, duplicating rows from data1 for each matching row in data2.
 * The final dataset's columns and their order are determined by listOfColumnsNeededInFinalData.
 * If data2 is not provided, rows from data1 are included with null values for missing columns.
 */
const innerJoinLists = (data1, data2, commonColumnName, listOfColumnsNeededInFinalData) => {
  // Error handling: Check if data1 array is provided
  if (!Array.isArray(data1)) {
    throw new Error("The first data input must be an array.");
  }

  // Error handling: Check if common column name is provided
  if (typeof commonColumnName !== "string") {
    throw new Error("Common column name must be a string.");
  }

  // Error handling: Check if listOfColumnsNeededInFinalData is provided and is an array
  if (!Array.isArray(listOfColumnsNeededInFinalData)) {
    throw new Error("listOfColumnsNeededInFinalData must be an array.");
  }

  // Find the index of the common column in the first dataset
  const commonColumnIndex1 = data1[0].indexOf(commonColumnName);

  // Error handling: Check if common column exists in the first dataset
  if (commonColumnIndex1 === -1) {
    throw new Error(`Common column "${commonColumnName}" not found in the first dataset.`);
  }

  let commonColumnIndex2 = -1;
  const data2Map = new Map();
  if (data2) {
    // Find the index of the common column in the second dataset
    commonColumnIndex2 = data2[0].indexOf(commonColumnName);

    // Error handling: Check if common column exists in the second dataset
    if (commonColumnIndex2 === -1) {
      throw new Error(`Common column "${commonColumnName}" not found in the second dataset.`);
    }

    // Create a map for the second dataset for quick lookup by the common column value
    for (let i = 1; i < data2.length; i++) {
      const row = data2[i];
      const commonValue = row[commonColumnIndex2];
      if (!data2Map.has(commonValue)) {
        data2Map.set(commonValue, []);
      }
      data2Map.get(commonValue).push(row);
    }
  }

  // Determine the headers for the final combined dataset based on listOfColumnsNeededInFinalData
  const combinedHeaders = listOfColumnsNeededInFinalData.filter((header) => data1[0].includes(header) || data2?.[0].includes(header));

  // Combine rows
  const combinedData = [combinedHeaders];
  const addedCommonValues = new Set();
  for (let i = 1; i < data1.length; i++) {
    const row1 = data1[i];
    const commonValue = row1[commonColumnIndex1];
    const rows2 = data2 ? data2Map.get(commonValue) || [[null]] : [[null]]; // Handle missing common values with a placeholder array of null

    // Check if rows2 is the placeholder array
    const isPlaceholderArray = rows2.length === 1 && rows2[0].every((value) => value === null);

    // Create combined rows for each row in data2
    if (isPlaceholderArray) {
      // If no corresponding row found in data2, use row from data1 with null values for missing columns
      const combinedRow = combinedHeaders.map((header) => {
        const index1 = data1[0].indexOf(header);
        return index1 !== -1 ? row1[index1] : null;
      });
      combinedData.push(combinedRow);
    } else {
      // If corresponding rows found in data2, combine each row from data2 with row from data1
      rows2.forEach((row2) => {
        const combinedRow = combinedHeaders.map((header) => {
          const index1 = data1[0].indexOf(header);
          const index2 = data2 ? data2[0].indexOf(header) : -1;
          return index1 !== -1 ? row1[index1] : index2 !== -1 ? row2[index2] : null;
        });
        combinedData.push(combinedRow);
      });
    }
    addedCommonValues.add(commonValue);
  }
  // Add rows from data2 that do not have a matching row in data1
  if (data2) {
    for (let i = 1; i < data2.length; i++) {
      const row2 = data2[i];
      const commonValue = row2[commonColumnIndex2];
      if (!addedCommonValues.has(commonValue)) {
        const combinedRow = combinedHeaders.map((header) => {
          // const index1 = data1[0].indexOf(header);
          const index2 = data2[0].indexOf(header);
          return index2 !== -1 ? row2[index2] : null;
        });
        combinedData.push(combinedRow);
      }
    }
  }

  return combinedData;
};

// function to filter the microplan data with respect to the hierarchy selected by the user
const filterMicroplanDataToShowWithHierarchySelection = (data, selections, hierarchy, hierarchyIndex = 0) => {
  if (!selections || selections?.length === 0) return data;
  if (hierarchyIndex >= hierarchy?.length) return data;
  const filteredHirarchyLevelList = selections?.[hierarchy?.[hierarchyIndex]]?.map((item) => item?.name);
  if (!filteredHirarchyLevelList || filteredHirarchyLevelList?.length === 0) return data;
  const columnDataIndexForHierarchyLevel = data?.[0]?.indexOf(hierarchy?.[hierarchyIndex]);
  if (columnDataIndexForHierarchyLevel === -1) return data;
  const levelFilteredData = data.filter((item, index) => {
    if (index === 0) return true;
    if (item?.[columnDataIndexForHierarchyLevel] && filteredHirarchyLevelList.includes(item?.[columnDataIndexForHierarchyLevel])) return true;
    return false;
  });
  return filterMicroplanDataToShowWithHierarchySelection(levelFilteredData, selections, hierarchy, hierarchyIndex + 1);
};

const AppplyChangedHypothesisConfirmation = ({ newhypothesisList, hypothesisList, t }) => {
  const data = filterObjects(newhypothesisList, hypothesisList);
  return (
    <div className="apply-changes-hypothesis">
      <div className="instructions">
        <p>{t("INSTRUCTION_PROCEED_WITH_NEW_HYPOTHESIS")}</p>
      </div>
      <CardLabel className="table-header" style={{ padding: 0 }}>
        {t("MICROPLAN_PREVIEW_HYPOTHESIS")}
      </CardLabel>
      <div className="table-container">
        <table className="custom-table">
          <thead>
            <tr>
              <th>{t("KEYS")}</th>
              <th>{t("OLD_VALUE")}</th>
              <th>{t("NEW_VALUE")}</th>
            </tr>
          </thead>
          <tbody>
            {data?.map((row, index) => (
              <tr key={row.id} className={index % 2 === 0 ? "even-row" : "odd-row"}>
                <td>{t(row?.key)}</td>
                <td>{t(row?.oldValue)}</td>
                <td>{t(row?.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

function filterObjects(arr1, arr2) {
  if (!arr1 || !arr2) return [];
  // Create a new array to store the filtered objects
  let filteredArray = [];

  // Iterate through the first array
  arr1.forEach((obj1) => {
    // Find the corresponding object in the second array
    let obj2 = _.cloneDeep(arr2.find((item) => item.key === obj1.key));

    // If the object with the same key is found in the second array and their values are the same
    if (obj2 && obj1.value !== obj2.value) {
      // Push the object to the filtered array
      obj1.oldValue = obj2.value;
      filteredArray.push(obj1);
    }
  });

  return filteredArray;
}

const useHypothesis = (tempHypothesisList, hypothesisAssumptionsList) => {
  // Handles the change in hypothesis value
  const valueChangeHandler = (e, setTempHypothesisList, boundarySelections, setToast, t) => {
    // Checks it the boundary filters at at root level ( given constraints )
    if (Object.keys(boundarySelections).length !== 0 && Object.values(boundarySelections)?.every((item) => item?.length !== 0))
      return setToast({ state: "error", message: t("HYPOTHESIS_CAN_BE_ONLY_APPLIED_ON_ADMIN_LEVEL_ZORO") });

    // validating user input
    if (e?.newValue.includes("+") || e?.newValue.includes("e")) return;
    if ((e?.newValue < 0 || e.newValue > 10000000000) && e?.newValue !== "") return;
    let value;
    const decimalIndex = e.newValue.indexOf(".");
    if (decimalIndex !== -1) {
      const numDecimals = e.newValue.length - decimalIndex - 1;
      if (numDecimals <= 2) {
        value = e.newValue;
      } else if (numDecimals > 2) {
        value = e.newValue.substring(0, decimalIndex + 3);
      }
    } else value = parseFloat(e.newValue);
    value = !isNaN(value) ? value : "";

    // update the state with user input
    let newhypothesisEntityIndex = hypothesisAssumptionsList.findIndex((item) => item?.id === e?.item?.id);
    let unprocessedHypothesisList = _.cloneDeep(tempHypothesisList);
    if (newhypothesisEntityIndex !== -1) unprocessedHypothesisList[newhypothesisEntityIndex].value = value;
    setTempHypothesisList(unprocessedHypothesisList);
  };

  return {
    valueChangeHandler,
  };
};

const updateHyothesisAPICall = async (
  microplanData,
  setMicroplanData,
  operatorsObject,
  MicroplanName,
  campaignId,
  UpdateMutate,
  setToast,
  updateData,
  setLoaderActivation,
  status,
  cancleNavigation,
  state,
  campaignType,
  navigationEvent,
  setCheckDataCompletion,
  t
) => {
  try {
    let body = Digit.Utils.microplan.mapDataForApi(microplanData, operatorsObject, MicroplanName, campaignId, status);
    body.PlanConfiguration["id"] = microplanData?.planConfigurationId;
    body.PlanConfiguration["auditDetails"] = microplanData?.auditDetails;
    if (!Digit.Utils.microplan.planConfigRequestBodyValidator(body, state, campaignType)) {
      setLoaderActivation(false);
      if (navigationEvent.name === "next") {
        setToast({
          message: t("ERROR_DATA_NOT_SAVED"),
          state: "error",
        });
        setCheckDataCompletion("false");
      } else setCheckDataCompletion("perform-action");
      return;
    }
    await UpdateMutate(body, {
      onSuccess: async (data) => {
        updateData();
        setLoaderActivation(false);
        setMicroplanData((previous) => ({ ...previous, microplanStatus: status }));
      },
      onError: (error, variables) => {
        setLoaderActivation(false);
        setToast({
          message: t("ERROR_DATA_NOT_SAVED"),
          state: "error",
        });
        if (status === "GENERATED") cancleNavigation();
        else updateData();
      },
    });
  } catch (error) {
    setLoaderActivation(false);
    setToast({
      message: t("ERROR_DATA_NOT_SAVED"),
      state: "error",
    });
  }
};

// get schema for validation
const getSchema = (campaignType, type, section, schemas) => {
  return schemas.find((schema) =>
    schema.campaignType
      ? schema.campaignType === campaignType && schema.type === type && schema.section === section
      : schema.type === type && schema.section === section
  );
};

const fetchMicroplanPreviewData = (campaignType, microplanData, validationSchemas, hierarchy) => {
  try {
    //Decide columns to take and their sequence
    const getfilteredSchemaColumnsList = () => {
      let filteredSchemaColumns = getRequiredColumnsFromSchema(campaignType, microplanData, validationSchemas) || [];
      if (hierarchy) filteredSchemaColumns = [...hierarchy, commonColumn, ...filteredSchemaColumns.filter((e) => e !== commonColumn)];
      return filteredSchemaColumns;
    };
    let filteredSchemaColumns = getfilteredSchemaColumnsList();
    const fetchedData = fetchMicroplanData(microplanData, campaignType, validationSchemas);
    // Perform inner joins using reduce
    const dataAfterJoins = fetchedData.reduce((accumulator, currentData, index) => {
      if (index === 0) {
        return innerJoinLists(currentData, null, commonColumn, filteredSchemaColumns);
      }
      return innerJoinLists(accumulator, currentData, commonColumn, filteredSchemaColumns);
    }, null);
    return dataAfterJoins;
  } catch (error) {
    console.error("Error in fetch microplan data: ", error.message);
  }
};

const fetchMicroplanData = (microplanData, campaignType, validationSchemas) => {
  if (!microplanData) return [];

  let combinesDataList = [];
  // Check if microplanData and its upload property exist
  if (microplanData?.upload) {
    let files = microplanData?.upload;
    // Loop through each file in the microplan upload
    for (let fileData of files) {
      const schema = getSchema(campaignType, fileData.fileType, fileData.templateIdentifier, validationSchemas);

      // Check if the file is not part of boundary or layer data origins
      if (!fileData.active || !fileData.fileType || !fileData?.section) continue; // Skip files with errors or missing properties

      // Check if file contains latitude and longitude columns
      if (fileData?.data) {
        // Check file type and update data availability accordingly
        switch (fileData?.fileType) {
          case EXCEL: {
            // extract dada
            const mergedData = schema?.template?.hierarchyLevelWiseSheets ? Object.values(fileData?.data).flat() : Object.values(fileData?.data)?.[0];

            let commonColumnIndex = mergedData?.[0]?.indexOf(commonColumn);

            let uniqueEntries;
            if (commonColumnIndex !== undefined)
              uniqueEntries = schema?.template?.hierarchyLevelWiseSheets
                ? Array.from(new Map(mergedData.map((entry) => [entry[commonColumnIndex], entry])).values())
                : mergedData;
            if (uniqueEntries) combinesDataList.push(uniqueEntries);
            break;
          }
          case GEOJSON:
          case SHAPEFILE: {
            // Extract keys from the first feature's properties
            let keys = Object.keys(fileData?.data.features[0].properties);

            // Extract corresponding values for each feature
            const values = fileData?.data?.features.map((feature) => {
              // list with features added to it
              const temp = keys.map((key) => {
                // if (feature.properties[key] === "") {
                //   return null;
                // }
                return feature.properties[key];
              });
              return temp;
            });

            let data = [keys, ...values];
            combinesDataList.push(data);
          }
        }
      }
    }
  }
  return combinesDataList;
};

const EditResourceData = ({ previewData, selectedRow, resources, tempResourceChanges, setTempResourceChanges, data, t }) => {
  const conmmonColumnData = useMemo(() => {
    const index = previewData?.[0]?.indexOf(commonColumn);
    if (index === -1) return;
    return previewData?.[selectedRow]?.[index];
  }, [previewData]);

  const valueChangeHandler = (item, value) => {
    if (!conmmonColumnData) return;
    if (isNaN(value) || (!isFinite(value) && value !== "")) return;
    let changedDataAgainstBoundaryCode = tempResourceChanges?.[conmmonColumnData] || {};
    changedDataAgainstBoundaryCode[item] = value === "" ? undefined : parseFloat(value);
    setTempResourceChanges((previous) => ({ ...previous, [conmmonColumnData]: changedDataAgainstBoundaryCode }));
  };

  return (
    <div className="edit-resource-data">
      <table className="edit-resource-data-table">
        <thead>
          <tr>
            <th>{t("COLUMNS")}</th>
            <th>{t("OLD_VALUE")}</th>
            <th>{t("NEW_VALUE")}</th>
          </tr>
        </thead>
        <tbody>
          {data[0].map((item) => {
            let index = data?.[0]?.indexOf(item);
            if (index === -1) return;
            const currentData = data?.[selectedRow]?.[index];
            return (
              <tr key={item}>
                <td className="column-names">
                  <p>{t(item)}</p>
                </td>
                <td className="old-value">
                  <p>{currentData || t("NO_DATA")}</p>
                </td>
                <td className="new-value no-left-padding">
                  <TextInput
                    name={`data_${index}`}
                    value={currentData || t("NO_DATA")}
                    style={{ margin: 0, backgroundColor: "rgba(238, 238, 238, 1)" }}
                    t={t}
                    disabled={true}
                  />
                </td>
              </tr>
            );
          })}
          {resources.map((item) => {
            let index = previewData?.[0]?.indexOf(item);
            if (index === -1) return;
            const currentData = previewData?.[selectedRow]?.[index];

            return (
              <tr key={item}>
                <td className="column-names">
                  <p>{t(item)}</p>
                </td>
                <td className="old-value">
                  <p>{currentData || t("NO_DATA")}</p>
                </td>
                <td className="new-value no-left-padding">
                  <TextInput
                    name={`hyopthesis_${index}`}
                    value={
                      tempResourceChanges?.[conmmonColumnData]?.[item] || tempResourceChanges?.[conmmonColumnData]?.[item] === 0
                        ? tempResourceChanges[conmmonColumnData][item]
                        : ""
                    }
                    type="text"
                    style={{ margin: 0 }}
                    t={t}
                    onChange={(value) => valueChangeHandler(item, value.target.value)}
                  />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

const Aggregates = memo(({ microplanPreviewAggregates, dataToShow, NumberFormatMappingForTranslation, t }) => {
  const { formatNumber } = useNumberFormatter(NumberFormatMappingForTranslation?.reduce((acc, obj) => Object.assign(acc, obj), {}));

  if (!microplanPreviewAggregates) return null;
  return (
    <div className="aggregates">
      {microplanPreviewAggregates.map((item, index) => {
        const aggregate = calculateAggregateValue(item, dataToShow);
        return (
          <div key={index}>
            <p className="aggregate-value">{isNaN(parseInt(aggregate)) ? 0 : formatNumber(parseInt(aggregate))}</p>
            <p className="aggregate-label">{typeof item === "object" && item.name ? t(item.name) : t(item)}</p>
          </div>
        );
      })}
    </div>
  );
});

const calculateAggregateValue = (aggregateName, dataToShow) => {
  if (!aggregateName || !dataToShow || dataToShow.length === 0) return;
  let aggregateNameList = aggregateName;
  if (typeof aggregateName !== "object") aggregateNameList = { name: aggregateName, entities: [aggregateName] };
  let aggregateData = 0;
  if (aggregateNameList)
    for (const item of aggregateNameList.entities) {
      const columnIndex = dataToShow?.[0].indexOf(item);
      dataToShow.slice(1).forEach((e) => {
        if (e?.[columnIndex]) aggregateData = aggregateData + Number(e[columnIndex]);
      });
    }
  return aggregateData;
};

export default MicroplanPreview;
