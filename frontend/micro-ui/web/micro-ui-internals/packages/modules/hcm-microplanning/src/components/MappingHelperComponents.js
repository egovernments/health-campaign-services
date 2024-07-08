// Importing necessary modules
import { Card, CardLabel, MultiSelectDropdown, Button, CheckBox, RadioButtons } from "@egovernments/digit-ui-components";
import "leaflet/dist/leaflet.css";
import React, { memo, useCallback, useEffect, useMemo, useRef, useState, Fragment } from "react";
import * as DigitSvgs from "@egovernments/digit-ui-svg-components";
import { CardSectionHeader, InfoIconOutline, LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import { fetchDropdownValues } from "../utils/processHierarchyAndData";
import { MapChoroplethGradientColors, PRIMARY_THEME_COLOR } from "../configs/constants";
import { ModalHeading } from "./CommonComponents";
import * as MicroplanIconCollection from "../icons/Svg";
import { generatePreviewUrl } from "../utils/mappingUtils";

const IconCollection = { ...MicroplanIconCollection, ...DigitSvgs };

export function checkTruthyKeys(obj) {
  for (let key in obj) {
    if (Object.hasOwn(obj, key)) {
      if (obj[key] && !(Array.isArray(obj[key]) && obj[key].length === 0)) {
        return true;
      }
    }
  }
  return false;
}

export const MapFilterIndex = ({ filterSelections, MapFilters, t }) => {
  return (
    <div className="filter-index">
      {filterSelections && filterSelections.length > 0 ? (
        <>
          {filterSelections.map((item, index) => (
            // <div className="filter-row">
            <FilterItemBuilder key={item?.id || index} item={item} MapFilters={MapFilters} t={t} />
            //   <p>{t(item)}</p>
            // </div>
          ))}
        </>
      ) : (
        ""
      )}
    </div>
  );
};

// Function to create the gradient from the colors array for choropleth index
export const MapChoroplethIndex = ({ t, choroplethProperty }) => {
  const createGradientString = (colors) => {
    return colors.map((color) => `${color.color} ${color.percent}%`).join(", ");
  };

  const gradientString = createGradientString(MapChoroplethGradientColors);
  const gradientStyle = {
    background: `linear-gradient(to right, ${gradientString})`,
  };

  return (
    <div className="choropleth-index">
      <div className="gradient-wrapper">
        <p>0%</p>
        <div className="gradient" style={gradientStyle} />
        <p>100%</p>
      </div>
      <p className="label">{t(choroplethProperty)}</p>
    </div>
  );
};

export const FilterItemBuilder = ({ item, MapFilters, t }) => {
  let temp = MapFilters?.find((e) => e?.name === item)?.icon?.index;
  let DynamicIcon = IconCollection?.[temp];
  // let icon;
  // if (typeof DynamicIcon === "function") icon = DynamicIcon({});
  return DynamicIcon && typeof DynamicIcon === "function" ? (
    <div className="filter-row">
      <DynamicIcon width={"1.5rem"} height={"1.5rem"} fill={"white"} />
      <p>{t(item)}</p>
    </div>
  ) : (
    // <div style={{width:"1.5rem"}}></div>
    ""
  );
};

export const ChoroplethSelection = memo(
  ({
    choroplethProperties,
    showChoroplethOptions,
    showChoroplethOptionRef,
    setShowChoroplethOptions,
    choroplethProperty,
    setChoroplethProperty,
    t,
  }) => {
    const handleChange = useCallback(
      (value) => {
        setChoroplethProperty(value?.code);
      },
      [choroplethProperties]
    );

    return (
      <div className="choropleth-section" ref={showChoroplethOptionRef}>
        <div
          className="icon-rest virtualization-icon"
          onClick={() => setShowChoroplethOptions((previous) => !previous)}
          onKeyUp={() => setShowChoroplethOptions((previous) => !previous)}
          tabIndex={0}
        >
          <p>{t("VISUALIZATIONS")}</p>
          <div className="icon">
            {DigitSvgs.FilterAlt && <DigitSvgs.FilterAlt width={"1.667rem"} height={"1.667rem"} fill={"rgba(255, 255, 255, 1)"} />}
          </div>
        </div>
        {showChoroplethOptions && (
          <div className="choropleth-section-option-wrapper">
            <div className="custom-box-wrapper">
              <RadioButtons
                additionalWrapperClass="custom-box"
                innerStyles={{ borderBottom: "0.063rem solid rgba(214, 213, 212, 1)" }}
                options={choroplethProperties.map((item) => ({ name: item, id: item, code: item }))}
                optionsKey="name"
                onSelect={handleChange}
                selectedOption={choroplethProperty}
              />
            </div>
            <Button
              variation="secondary"
              textStyles={{ width: "fit-content", fontSize: "0.875rem", fontWeight: "600", display: "flex", alignItems: "center" }}
              className="button-primary"
              style={{
                width: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "flex-start",
                border: 0,
                padding: "0 0.7rem 0 0.7rem",
                height: "2.5rem",
                maxHeight: "2.5rem",
                backgroundColor: "rgba(250, 250, 250, 1)",
              }}
              icon={"AutoRenew"}
              label={t("CLEAR_FILTER")}
              onClick={() => setChoroplethProperty()}
            />
          </div>
        )}
      </div>
    );
  }
);

export const FilterSection = memo(
  ({ filterProperties, showFilterOptionRef, showFilterOptions, setShowFilterOptions, filterSelections, setFilterSelections, t }) => {
    const handleChange = useCallback(
      (e, item) => {
        let tempFilterSelections = [...filterSelections]; // Clone the array to avoid mutating state directly
        if (filterSelections.includes(item)) {
          tempFilterSelections = tempFilterSelections.filter((element) => element !== item);
        } else {
          tempFilterSelections.push(item);
        }
        setFilterSelections(tempFilterSelections);
      },
      [filterSelections]
    );

    return (
      <div className="filter-section" ref={showFilterOptionRef}>
        <div
          className="icon-rest filter-icon"
          onClick={() => setShowFilterOptions((previous) => !previous)}
          onKeyUp={() => setShowFilterOptions((previous) => !previous)}
          tabIndex={0}
        >
          <p>{t("FILTERS")}</p>
          <div className="icon">
            {DigitSvgs.FilterAlt && <DigitSvgs.FilterAlt width={"1.667rem"} height={"1.667rem"} fill={"rgba(255, 255, 255, 1)"} />}
          </div>
        </div>
        {showFilterOptions && (
          <div className="filter-section-option-wrapper">
            <div className="custom-box-wrapper">
              {filterProperties.map((item) => (
                <div id={item} key={item} className="custom-box">
                  <CheckBox
                    onChange={(e) => handleChange(e, item)}
                    label={t(item)}
                    checked={!!filterSelections.includes(item)}
                    mainClassName="mainClassName"
                    labelClassName="labelClassName"
                    inputWrapperClassName="inputWrapperClassName"
                    inputClassName="inputClassName"
                    inputIconClassname="inputIconClassname"
                    iconFill={PRIMARY_THEME_COLOR}
                    onLabelClick={(e) => handleChange(e, item)}
                  />
                </div>
              ))}
            </div>
            <Button
              variation="secondary"
              textStyles={{ width: "fit-content", fontSize: "0.875rem", fontWeight: "600", display: "flex", alignItems: "center" }}
              className="button-primary"
              style={{
                width: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "flex-start",
                border: 0,
                padding: "0 0.7rem 0 0.7rem",
                height: "2.5rem",
                maxHeight: "2.5rem",
                backgroundColor: "rgba(250, 250, 250, 1)",
              }}
              icon={"AutoRenew"}
              label={t("CLEAR_ALL_FILTERS")}
              onClick={() => setFilterSelections([])}
            />
          </div>
        )}
      </div>
    );
  }
);

export const BoundarySelection = memo(
  ({
    boundarySelections,
    setBoundarySelections,
    boundaryData,
    hierarchy,
    filterBoundaryRef,
    isboundarySelectionSelected,
    setIsboundarySelectionSelected,
    t,
  }) => {
    const [processedHierarchy, setProcessedHierarchy] = useState([]);
    const [isLoading, setIsLoading] = useState(false);
    const [showConfirmationModal, setShowConformationModal] = useState(false);
    const itemRefs = useRef([]);
    const [expandedIndex, setExpandedIndex] = useState(null);
    const scrollContainerRef = useRef(null);
    const [changedBoundaryType, setChangedBoundaryType] = useState("");
    const [isScrollable, setIsScrollable] = useState(false);

    useEffect(() => {
      // Scroll to the expanded item's child element after the state has updated and the DOM has re-rendered
      if (expandedIndex !== null && itemRefs.current[expandedIndex]) {
        // Use a timeout to ensure the DOM has updated
        setTimeout(() => {
          const childElement = itemRefs.current[expandedIndex].children[0]; // Assuming child content is the second child
          // if (childElement) {
          //   childElement.scrollIntoView({ behavior: 'smooth' });
          // }
          if (childElement) {
            const scrollContainer = scrollContainerRef.current;
            const childElementBound = childElement.getBoundingClientRect();
            const containerRect = scrollContainer.getBoundingClientRect();

            // Calculate the offset from the top of the container
            const offset = childElementBound.top - containerRect.top;

            // Scroll the container
            scrollContainer.scrollTo({
              top: scrollContainer.scrollTop + offset - 10,
              behavior: "smooth",
            });
          }
        }, 0);
      }
    }, [expandedIndex]);

    const toggleExpand = (index) => {
      setExpandedIndex(index === expandedIndex ? null : index);
    };

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

    const handleClearAll = () => {
      setShowConformationModal(true);
    };

    const handleSubmitConfModal = () => {
      setBoundarySelections({});
      setShowConformationModal(false);
    };

    const handleCancelConfModal = () => {
      setShowConformationModal(false);
    };

    const checkScrollbar = () => {
      if (scrollContainerRef.current) {
        setIsScrollable(scrollContainerRef.current.scrollHeight > scrollContainerRef.current.clientHeight);
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
    }, [isboundarySelectionSelected]);

    useEffect(() => {
      const content = scrollContainerRef.current;
      content.addEventListener("scroll", checkScrollbar);

      return () => {
        content.removeEventListener("scroll", checkScrollbar);
      };
    }, [scrollContainerRef]);

    return (
      <div className={`filter-by-boundary  ${!isboundarySelectionSelected ? "height-control" : ""} `} ref={filterBoundaryRef}>
        {isLoading && <LoaderWithGap text={"LOADING"} />}
        <Button
          icon="FilterAlt"
          variation="secondary"
          className="button-primary"
          style={{ height: "2.5rem", maxHeight: "2.5rem" }}
          textStyles={{ width: "fit-content", display: "flex", alignItems: "center", fontWeight: "600" }}
          label={t("BUTTON_FILTER_BY_BOUNDARY")}
          onClick={() => setIsboundarySelectionSelected((previous) => !previous)}
        />
        <Card className={`boundary-selection ${!isboundarySelectionSelected ? "display-none" : ""}`}>
          <div className="header-section" title={t("SELECT_A_BOUNDARY_TOOLTIP")}>
            <CardSectionHeader>{t("SELECT_A_BOUNDARY")}</CardSectionHeader>
            <InfoIconOutline width="1.8rem" fill="rgba(11, 12, 12, 1)" />
          </div>
          <div
            className={`hierarchy-selection-container ${isScrollable ? "scrollable" : ""}`}
            style={checkTruthyKeys(boundarySelections) ? { maxHeight: "20rem" } : {}}
            ref={scrollContainerRef}
          >
            {processedHierarchy?.map((item, index) => (
              <div
                key={index}
                className="hierarchy-selection-element"
                ref={(el) => {
                  itemRefs.current[index] = el;
                }}
                onClick={() => toggleExpand(index)}
              >
                <CardLabel style={{ padding: 0, margin: 0 }}>{t(item?.boundaryType)}</CardLabel>
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
          {checkTruthyKeys(boundarySelections) && (
            <Button
              variation="secondary"
              className="button-primary"
              textStyles={{ width: "fit-content", display: "flex", alignItems: "center" }}
              style={{
                margin: "0.7rem 1rem 0rem 1rem",
                display: "flex",
                alignItems: "center",
                width: "14.5rem",
                height: "2rem",
                maxHeight: "2rem",
              }}
              icon={"AutoRenew"}
              label={t("CLEAR_ALL_FILTERS")}
              onClick={handleClearAll}
            />
          )}
          {showConfirmationModal && (
            <div className="popup-wrap-focus">
              <Modal
                popupStyles={{ borderRadius: "0.25rem", width: "31.188rem" }}
                popupModuleActionBarStyles={{
                  display: "flex",
                  flex: 1,
                  justifyContent: "space-between",
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
                headerBarMain={<ModalHeading style={{ fontSize: "1.5rem" }} label={t("CLEAR_ALL")} />}
                actionCancelLabel={t("YES")}
                actionCancelOnSubmit={handleSubmitConfModal}
                actionSaveLabel={t("NO")}
                actionSaveOnSubmit={handleCancelConfModal}
              >
                <div className="modal-body">
                  <p className="modal-main-body-p">{t("CLEAR_ALL_CONFIRMATION_MSG")}</p>
                </div>
              </Modal>
            </div>
          )}
        </Card>
      </div>
    );
  }
);

export const BaseMapSwitcher = ({
  baseMaps,
  showBaseMapSelector,
  setShowBaseMapSelector,
  handleBaseMapToggle,
  selectedBaseMapName,
  basemapRef,
  t,
}) => {
  if (!baseMaps) return null;
  return (
    <div className="base-map-selector">
      <div
        className="icon-first"
        onClick={() => setShowBaseMapSelector((previous) => !previous)}
        onKeyUp={() => setShowBaseMapSelector((previous) => !previous)}
        tabIndex={0}
      >
        <p>{t("LAYERS")}</p>
        <div className="icon">{DigitSvgs.Layers && <DigitSvgs.Layers width={"1.667rem"} height={"1.667rem"} fill={"rgba(255, 255, 255, 1)"} />}</div>
      </div>
      <div className="base-map-area-wrapper" ref={basemapRef}>
        {showBaseMapSelector && (
          <div className="base-map-area">
            {Object.entries(baseMaps).map(([name, baseMap], index) => {
              return (
                <div key={index} className={`base-map-entity ${name === selectedBaseMapName ? "selected" : ""}`}>
                  <img
                    className="base-map-img"
                    key={index}
                    src={generatePreviewUrl(baseMap?.metadata?.url, [0, 0], 0)}
                    alt={t("ERROR_LOADING_BASE_MAP")}
                    onClick={() => handleBaseMapToggle(name)}
                  />
                  <p>{t(name)}</p>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
