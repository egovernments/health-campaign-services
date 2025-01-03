import { CardLabel, Loader, MultiSelectDropdown, TextInput } from "@egovernments/digit-ui-components";
import React, { memo, useCallback, useEffect, useMemo, useState, Fragment, useRef } from "react";
import { fetchDropdownValues } from "../utils/processHierarchyAndData";
import { CloseButton, ModalHeading } from "./CommonComponents";
import { PRIMARY_THEME_COLOR, commonColumn } from "../configs/constants";
import { Button, LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import { useNumberFormatter } from "../hooks/useNumberFormatter";
import { calculateAggregateValue, filterObjects, useHypothesis } from "../utils/microplanPreviewUtils";

export const HypothesisValues = memo(({ boundarySelections, hypothesisAssumptionsList, setHypothesisAssumptionsList, setToast, setModal, t }) => {
  const [tempHypothesisList, setTempHypothesisList] = useState(hypothesisAssumptionsList || []);
  const { valueChangeHandler } = useHypothesis(tempHypothesisList, hypothesisAssumptionsList);
  const contentRef = useRef(null);
  const [isScrollable, setIsScrollable] = useState(false);

  const applyNewHypothesis = () => {
    if (tempHypothesisList.some((item) => item.active && (Number.isNaN(parseFloat(item.value)) || parseFloat(item.value) === 0))) {
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
            <div key={`hyopthesis_${item?.id ? item.id : index}`} className="hypothesis-list-entity">
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

export const BoundarySelection = memo(({ boundarySelections, setBoundarySelections, boundaryData, hierarchy, t }) => {
  const [processedHierarchy, setProcessedHierarchy] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [changedBoundaryType, setChangedBoundaryType] = useState("");

  // Filtering out dropdown values
  useEffect(() => {
    if (!boundaryData || !hierarchy) return;

    const processedHierarchyTemp = fetchDropdownValues(
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

export const DataPreview = memo(
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

export const AppplyChangedHypothesisConfirmation = ({ newhypothesisList, hypothesisList, t }) => {
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

export const EditResourceData = ({ previewData, selectedRow, resources, tempResourceChanges, setTempResourceChanges, data, t }) => {
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
                    name={"data_" + index}
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

export const Aggregates = memo(({ microplanPreviewAggregates, dataToShow, NumberFormatMappingForTranslation, t }) => {
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
