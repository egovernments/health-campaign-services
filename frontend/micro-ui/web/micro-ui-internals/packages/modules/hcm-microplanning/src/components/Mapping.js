// Importing necessary modules
import {
  Card,
  CardLabel,
  CustomDropdown,
  Dropdown,
  Header,
  MultiSelectDropdown,
  Toast,
  TreeSelect,
  Button,
  CheckBox,
  RadioButtons,
} from "@egovernments/digit-ui-components";
import L, { map } from "leaflet";
import "leaflet/dist/leaflet.css";
import React, { memo, useCallback, useEffect, useMemo, useRef, useState, Fragment } from "react";
import { useTranslation } from "react-i18next";
import ZoomControl from "./ZoomControl";
import CustomScaleControl from "./CustomScaleControl";
import * as DigitSvgs from "@egovernments/digit-ui-svg-components";
import { CardSectionHeader, InfoIconOutline, LoaderWithGap, Modal } from "@egovernments/digit-ui-react-components";
import { processHierarchyAndData, findParent, fetchDropdownValues, findChildren, calculateAggregateForTree } from "../utils/processHierarchyAndData";
import { EXCEL, GEOJSON, SHAPEFILE, MapChoroplethGradientColors, PRIMARY_THEME_COLOR } from "../configs/constants";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import { ClearAllIcon, CloseButton, ModalHeading } from "./CommonComponents";
import { PopulationSvg } from "../icons/Svg";
import chroma from "chroma-js";
import * as MicroplanIconCollection from "../icons/Svg";

const IconCollection = { ...MicroplanIconCollection, ...DigitSvgs };

const page = "mapping";

function checkTruthyKeys(obj) {
  for (let key in obj) {
    if (Object.hasOwn(obj, key)) {
      if (obj[key] && !(Array.isArray(obj[key]) && obj[key].length === 0)) {
        return true;
      }
    }
  }
  return false;
}

// Mapping component definition
const Mapping = ({
  campaignType = Digit.SessionStorage.get("microplanHelperData")?.campaignData?.projectType,
  microplanData,
  setMicroplanData,
  checkDataCompletion,
  setCheckDataCompletion,
  currentPage,
  pages,
  setToast,
  ...props
}) => {
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
  var reqCriteria = {
    url: `/boundary-service/boundary-hierarchy-definition/_search`,
    params: {},
    body: {
      BoundaryTypeHierarchySearchCriteria: {
        tenantId: Digit.ULBService.getStateId(),
        // hierarchyType:  "Microplan",
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
  const { isLoading: ishierarchyLoading, data: hierarchy } = Digit.Hooks.useCustomAPIHook(reqCriteria);
  // request body for boundary hierarchy api
  var reqCriteria = {
    url: `/boundary-service/boundary/_search`,
    params: { codes: Digit.ULBService.getCurrentTenantId(), tenantId: Digit.ULBService.getCurrentTenantId() },
    body: {},
    config: {
      select: (data) => {
        return data?.Boundary || {};
      },
    },
  };
  const { isLoading: isBoundaryLoading, data: Boundary } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  // Setting up state variables
  const [editable, setEditable] = useState(true);
  const { t } = useTranslation();
  var [map, setMap] = useState(null);
  var [_mapNode, set__mapNode] = useState("map");
  const [layers, setLayer] = useState([]);
  const [validationSchemas, setValidationSchemas] = useState([]);
  const [filterDataOrigin, setFilterDataOrigin] = useState({});
  const [dataAvailability, setDataAvailability] = useState("true");
  // const [toast, setToast] = useState();
  const [baseMaps, setBaseMaps] = useState({});
  const [selectedBaseMap, setSelectedBaseMap] = useState({});
  const [selectedBaseMapName, setSelectedBaseMapName] = useState("");
  const [showBaseMapSelector, setShowBaseMapSelector] = useState(false);
  const [boundaryData, setBoundaryData] = useState({}); // State for boundary data
  const [filterData, setFilterData] = useState({}); // State for facility data
  const [boundarySelections, setBoundarySelections] = useState({});
  const [isboundarySelectionSelected, setIsboundarySelectionSelected] = useState(false);
  const { state, dispatch } = useMyContext();
  const [filterPropertyNames, setFilterPropertyNames] = useState();
  const [filterProperties, setFilterProperties] = useState();
  const [showFilterOptions, setShowFilterOptions] = useState(false);
  const [filterSelections, setFilterSelections] = useState([]);
  const [choroplethProperties, setChoroplethProperties] = useState([]);
  const [showChoroplethOptions, setShowChoroplethOptions] = useState(false);
  const [choroplethProperty, setChoroplethProperty] = useState();
  const [dataCompleteness, setDataCompleteness] = useState();
  const basemapRef = useRef();
  const filterBoundaryRef = useRef();
  const showChoroplethOptionRef = useRef();
  const showFilterOptionRef = useRef();
  const [loader, setLoader] = useState(false);

  // Set TourSteps
  useEffect(() => {
    const tourData = tourSteps(t)?.[page] || {};
    if (state?.tourStateData?.name === page) return;
    dispatch({
      type: "SETINITDATA",
      state: { tourStateData: tourData },
    });
  }, []);

  // Effect to initialize map when data is fetched
  useEffect(() => {
    if (!state || !Boundary) return;
    let UIConfiguration = state?.UIConfiguration;
    if (UIConfiguration) {
      const filterDataOriginList = UIConfiguration.find((item) => item.name === "mapping");
      setFilterDataOrigin(filterDataOriginList);
    }
    const BaseMapLayers = state?.BaseMapLayers;
    let schemas = state?.Schemas;
    if (schemas) setValidationSchemas(schemas);
    if (!BaseMapLayers || (BaseMapLayers && BaseMapLayers.length === 0)) return;
    let baseMaps = {};
    let defaultBaseMap = undefined;
    BaseMapLayers.forEach((item) => {
      if (item.url) {
        const layer = L.tileLayer(item.url, {
          minZoom: item?.minZoom,
          maxZoom: item?.maxZoom,
          attribution: item?.attribution,
        });
        baseMaps[item?.name] = {
          metadata: item,
          layer,
        };
        if (!defaultBaseMap)
          defaultBaseMap = {
            name: item?.name,
            layer,
          };
      }
    });
    setSelectedBaseMapName(defaultBaseMap?.name);
    setBaseMaps(baseMaps);
    if (!map) {
      init(_mapNode, defaultBaseMap, Boundary);
    }
  }, [Boundary]);

  useEffect(() => {
    if (map && filterDataOrigin && Object.keys(filterDataOrigin).length !== 0) {
      setLoader("LOADING");
      // Check if all the data is present or not, if it is then extract it in a format that can be used for mapping and other mapping related operations
      extractGeoData(
        campaignType,
        microplanData,
        filterDataOrigin,
        validationSchemas,
        setToast,
        setDataAvailability,
        hierarchy,
        setBoundaryData,
        setFilterData,
        setFilterProperties,
        setFilterSelections,
        setFilterPropertyNames,
        state,
        setChoroplethProperties,
        setDataCompleteness,
        t
      );
      setLoader(false);
    }
  }, [filterDataOrigin, hierarchy]);

  // Function to initialize map
  const init = (id, defaultBaseMap, Boundary) => {
    if (map !== null) return;

    // let bounds = findBounds(Boundary);

    let mapConfig = {
      center: [0, 0],
      zoomControl: false,
      zoom: 3,
      scrollwheel: true,
      minZoom: 3,
    };

    let map_i = L.map(id, mapConfig);
    var verticalBounds = L.latLngBounds(L.latLng(-90, -170), L.latLng(85, 190));
    map_i.on("drag", () => {
      map_i.panInsideBounds(verticalBounds, { animate: true });
    });
    map_i.on("zoom", () => {
      map_i.panInsideBounds(verticalBounds, { animate: true });
    });
    const defaultBaseLayer = defaultBaseMap?.layer.addTo(map_i);
    // if (bounds) map_i.fitBounds(bounds);
    setSelectedBaseMap(defaultBaseLayer);
    setMap(map_i);
  };

  const handleBaseMapToggle = (newBaseMap) => {
    if (map) {
      const currentBaseLayer = selectedBaseMap;
      if (currentBaseLayer) {
        currentBaseLayer.remove();
      }
      const newBaseLayer = baseMaps[newBaseMap].layer.addTo(map);
      // Add the new base layer to the bottom of the layer stack
      newBaseLayer.addTo(map);

      // Update the baseLayer state
      setSelectedBaseMap(newBaseLayer);
      setSelectedBaseMapName(newBaseMap);
    }
  };

  // showing selected boundary data
  useEffect(() => {
    if (!boundarySelections && !choroplethProperty && !filterSelections) return;
    setLoader("LOADING");
    try {
      removeAllLayers(map, layers);
      const { filteredSelection, childrenList } = filterBoundarySelection(boundaryData, boundarySelections);
      let newLayer = [];
      let addOn = {
        fillColor: "rgba(255, 107, 43, 0)",
        weight: 3.5,
        opacity: 1,
        color: "rgba(176, 176, 176, 1)",
        fillOpacity: 0,
        fill: "rgb(4,136,219,1)",
        child: !childrenList || childrenList.length === 0, // so that this layer also has mounse in and mouse out events
      };
      let geojsonsBase = prepareGeojson(boundaryData, "ALL", addOn);
      if (geojsonsBase) {
        let baseLayer = addGeojsonToMap(map, geojsonsBase, t);
        if (baseLayer) newLayer.push(baseLayer);
        let bounds = findBounds(geojsonsBase);
        if (bounds) map.fitBounds(bounds);
      }

      addOn = {
        fillColor: "rgba(255, 107, 43, 1)",
        weight: 2.5,
        opacity: 1,
        color: "rgba(255, 255, 255, 1)",
        fillOpacity: 0.22,
        fill: "rgb(4,136,219)",
      };

      let geojsonLayer;
      if (choroplethProperty) {
        if (dataCompleteness === "partial" || dataCompleteness === "false" || dataCompleteness === undefined) {
          setToast({
            state: "warning",
            message: t("DISPLAYING_DATA_ONLY_FOR_UPLOADED_BOUNDARIES"),
          });
        }

        let choroplethGeojson = prepareGeojson(boundaryData, "ALL", { ...addOn, child: true, fillColor: "rgb(0,0,0,0)" }) || [];
        if (choroplethGeojson && choroplethGeojson.length !== 0)
          choroplethGeojson = addChoroplethProperties(choroplethGeojson, choroplethProperty, filteredSelection);
        geojsonLayer = addGeojsonToMap(map, choroplethGeojson, t);
        if (geojsonLayer) {
          newLayer.push(geojsonLayer);
        }
      }
      geojsonLayer = null;
      const geojsons = prepareGeojson(boundaryData, filteredSelection, addOn);
      if (geojsons && geojsons.length > 0) {
        geojsonLayer = addGeojsonToMap(map, geojsons, t);
        newLayer.push(geojsonLayer);
        let bounds = findBounds(geojsons);
        if (bounds) map.fitBounds(bounds);
      }

      const childrenGeojson = prepareGeojson(boundaryData, childrenList, { ...addOn, opacity: 0, fillOpacity: 0, child: true });
      let childrenGeojsonLayer = addGeojsonToMap(map, childrenGeojson, t);
      if (childrenGeojsonLayer) newLayer.push(childrenGeojsonLayer);

      //filters
      const filterGeojsons = prepareGeojson(filterData, filteredSelection && filteredSelection.length !== 0 ? filteredSelection : "ALL", addOn);
      const filterGeojsonWithProperties = addFilterProperties(filterGeojsons, filterSelections, filterPropertyNames, state?.MapFilters);
      let filterGeojsonLayer = addGeojsonToMap(map, filterGeojsonWithProperties, t);
      if (filterGeojsonLayer) newLayer.push(filterGeojsonLayer);

      setLayer(newLayer);
    } catch (error) {
      console.error("Error while adding geojson to map: ", error.message);
    }
    setLoader(false);
  }, [boundarySelections, choroplethProperty, filterSelections]);

  const handleOutsideClickAndSubmitSimultaneously = useCallback(() => {
    if (isboundarySelectionSelected) setIsboundarySelectionSelected(false);
    if (showBaseMapSelector) setShowBaseMapSelector(false);
    if (showFilterOptions) setShowFilterOptions(false);
    if (showChoroplethOptions) setShowChoroplethOptions(false);
  }, [
    isboundarySelectionSelected,
    showBaseMapSelector,
    showFilterOptions,
    showChoroplethOptions,
    setIsboundarySelectionSelected,
    setShowBaseMapSelector,
    setShowFilterOptions,
    setShowChoroplethOptions,
  ]);
  Digit?.Hooks.useClickOutside(filterBoundaryRef, handleOutsideClickAndSubmitSimultaneously, isboundarySelectionSelected, { capture: true });
  Digit?.Hooks.useClickOutside(basemapRef, handleOutsideClickAndSubmitSimultaneously, showBaseMapSelector, { capture: true });
  Digit?.Hooks.useClickOutside(showFilterOptionRef, handleOutsideClickAndSubmitSimultaneously, showFilterOptions, { capture: true });
  Digit?.Hooks.useClickOutside(showChoroplethOptionRef, handleOutsideClickAndSubmitSimultaneously, showChoroplethOptions, { capture: true });

  // function to stop mouse event propogation from custom comopents to leaflet map
  const handleMouseDownAndScroll = (event) => {
    event?.stopPropagation();
    disableMapInteractions(map);
  };

  const handleMouseUpAndScroll = (event) => {
    enableMapInteractions(map);
  };
  useEffect(() => {
    if (isboundarySelectionSelected || showBaseMapSelector || showFilterOptions || showChoroplethOptions) handleMouseDownAndScroll();
    else handleMouseUpAndScroll();
  }, [isboundarySelectionSelected, showBaseMapSelector, showFilterOptions, showChoroplethOptions, choroplethProperty, filterPropertyNames]);

  // Rendering component
  return (
    <div className={`jk-header-btn-wrapper mapping-section ${editable ? "" : "non-editable-component"}`}>
      <Header className="heading">{t("MAPPING")}</Header>
      <Card className="mapping-body-container" style={{ margin: "0", padding: "0" }}>
        <Card className="map-container">
          {/* Container for map */}
          <BoundarySelection
            boundarySelections={boundarySelections}
            setBoundarySelections={setBoundarySelections}
            boundaryData={boundaryData}
            hierarchy={hierarchy}
            filterBoundaryRef={filterBoundaryRef}
            isboundarySelectionSelected={isboundarySelectionSelected}
            setIsboundarySelectionSelected={setIsboundarySelectionSelected}
            t={t}
          />
          <div ref={set__mapNode} className="map" id="map">
            <div
              className="top-right-map-subcomponents"
              onScroll={handleMouseDownAndScroll}
              onMouseDown={handleMouseDownAndScroll}
              onMouseUp={handleMouseUpAndScroll}
            >
              <div className="icon-first">
                <BaseMapSwitcher
                  baseMaps={baseMaps}
                  showBaseMapSelector={showBaseMapSelector}
                  setShowBaseMapSelector={setShowBaseMapSelector}
                  handleBaseMapToggle={handleBaseMapToggle}
                  selectedBaseMapName={selectedBaseMapName}
                  basemapRef={basemapRef}
                  t={t}
                />
              </div>
              {filterProperties && Object.keys(filterProperties).length !== 0 && (
                <FilterSection
                  filterProperties={filterProperties}
                  showFilterOptionRef={showFilterOptionRef}
                  showFilterOptions={showFilterOptions}
                  setShowFilterOptions={setShowFilterOptions}
                  filterSelections={filterSelections}
                  setFilterSelections={setFilterSelections}
                  t={t}
                />
              )}
              <ChoroplethSelection
                choroplethProperties={choroplethProperties}
                showChoroplethOptions={showChoroplethOptions}
                setShowChoroplethOptions={setShowChoroplethOptions}
                showChoroplethOptionRef={showChoroplethOptionRef}
                choroplethProperty={choroplethProperty}
                setChoroplethProperty={setChoroplethProperty}
                t={t}
              />
            </div>

            <div className="bottom-left-map-subcomponents" onMouseDown={handleMouseDownAndScroll} onMouseUp={handleMouseUpAndScroll}>
              <ZoomControl map={map} t={t} />
              <div className="north-arrow">
                {DigitSvgs.NorthArrow && <DigitSvgs.NorthArrow width={"1.667rem"} height={"1.667rem"} fill={"rgba(255, 255, 255, 1)"} />}
              </div>
              <CustomScaleControl map={map} t={t} />
            </div>

            <div className="bottom-right-map-subcomponents" onMouseDown={handleMouseDownAndScroll} onMouseUp={handleMouseUpAndScroll}>
              {filterSelections && filterSelections.length > 0 && (
                <MapFilterIndex filterSelections={filterSelections} MapFilters={state?.MapFilters} t={t} />
              )}
              {choroplethProperty && <MapChoroplethIndex t={t} choroplethProperty={choroplethProperty} />}
            </div>
          </div>
        </Card>
      </Card>
      {loader && <LoaderWithGap text={t(loader)} />}
    </div>
  );
};

const MapFilterIndex = ({ filterSelections, MapFilters, t }) => {
  return (
    <div className="filter-index">
      {filterSelections && filterSelections.length > 0 ? (
        <>
          {filterSelections.map((item) => (
            // <div className="filter-row">
            <FilterItemBuilder item={item} MapFilters={MapFilters} t={t} />
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
const MapChoroplethIndex = ({ t, choroplethProperty }) => {
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

const FilterItemBuilder = ({ item, MapFilters, t }) => {
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

const ChoroplethSelection = memo(
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

const FilterSection = memo(
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
                <div id={item} className="custom-box">
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
                height: "2rem",
                maxHeight: "2rem",
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

const BoundarySelection = memo(
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
      let processedHierarchyTemp = fetchDropdownValues(
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
    }, []);

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

const BaseMapSwitcher = ({ baseMaps, showBaseMapSelector, setShowBaseMapSelector, handleBaseMapToggle, selectedBaseMapName, basemapRef, t }) => {
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
                    alt={name}
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

const generatePreviewUrl = (baseMapUrl, center = [0, 0], zoom = 5) => {
  const lon = Math.floor(((center[1] + 180) / 360) * Math.pow(0, zoom));
  const lat = Math.floor(
    ((1 - Math.log(Math.tan((center[0] * Math.PI) / 180) + 1 / Math.cos((center[0] * Math.PI) / 180)) / Math.PI) / 2) * Math.pow(2, zoom)
  );
  if (baseMapUrl) {
    return baseMapUrl.replace("{z}", zoom).replace("{x}", lat).replace("{y}", lon);
  }
  // Return a default preview URL or handle this case as needed
  return "default-preview-url.jpg"; // todo
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

const calculateAggregateForTreeMicroplanWrapper = (entity) => {
  if (!entity || typeof entity !== "object") return {};
  let newObject = {};
  for (let [key, value] of Object.entries(entity)) {
    if (!value?.["hierarchicalData"]) continue;
    let aggregatedTree = calculateAggregateForTree(value?.["hierarchicalData"]);
    newObject[key] = { ...value, hierarchicalData: aggregatedTree };
  }
  return newObject;
};

const extractGeoData = (
  campaignType,
  microplanData,
  filterDataOrigin,
  validationSchemas,
  setToast,
  setDataAvailability,
  hierarchy,
  setBoundaryData,
  setFilterData,
  setFilterProperties,
  setFilterSelections,
  setFilterPropertyNames,
  state,
  setChoroplethProperties,
  setDataCompleteness,
  t
) => {
  if (!hierarchy) return;

  let setBoundary = {};
  let setFilter = {};
  let virtualizationPropertiesCollector = new Set();
  let filterPropertiesCollector = new Set();
  let filterPropertieNameCollector = new Set();
  let resources = state?.Resources?.find((item) => item.campaignType === campaignType)?.data;
  let hypothesisAssumptionsList = microplanData?.hypothesis;
  let formulaConfiguration = microplanData?.ruleEngine;
  // Check if microplanData and its upload property exist
  let dataAvailabilityCheck; // Initialize data availability check
  if (microplanData?.upload) {
    let files = _.cloneDeep(microplanData?.upload);
    dataAvailabilityCheck = "initialStage"; // Initialize data availability check
    // Loop through each file in the microplan upload
    for (let fileData of files) {
      if (!fileData.active) continue; // if file is inactive skip it

      // Check if the file is not part of boundary or layer data origins
      if (!filterDataOrigin?.boundriesDataOrigin?.includes(fileData?.section) && !filterDataOrigin?.layerDataOrigin?.includes(fileData?.section)) {
        dataAvailabilityCheck = "false"; // Set data availability to false if file not found in data origins
      }

      // If data availability is not false, proceed with further checks
      if (dataAvailabilityCheck !== false) {
        if (fileData?.error) {
          dataAvailabilityCheck =
            dataAvailabilityCheck === "partial"
              ? "partial"
              : dataAvailabilityCheck === "false" || dataAvailabilityCheck === "initialStage"
              ? "false"
              : "partial";
          continue;
        }
        if (!fileData?.fileType || !fileData?.section) continue; // Skip files with errors or missing properties

        // Get validation schema for the file
        let schema = getSchema(campaignType, fileData?.fileType, fileData?.section, validationSchemas);
        const properties = Object.entries(schema?.schema?.Properties || {});
        const latLngColumns = [];
        let filterProperty = [];

        for (const [key, value] of properties) {
          if (value?.isLocationDataColumns) {
            latLngColumns.push(t(key));
          }
          if (filterDataOrigin?.layerDataOrigin?.includes(fileData?.section) && value?.isFilterPropertyOfMapSection) {
            filterProperty.push(key);
          }
          if (value?.isVisualizationPropertyOfMapSection && filterDataOrigin?.boundriesDataOrigin?.includes(fileData?.section)) {
            virtualizationPropertiesCollector.add(key);
          }
        }

        filterProperty.forEach((property) => filterPropertieNameCollector.add(property));

        // Check if file contains latitude and longitude columns
        if (fileData?.data && Object.keys(fileData?.data).length > 0) {
          if (dataAvailabilityCheck === "initialStage") dataAvailabilityCheck = "true";
          // Check file type and update data availability accordingly
          switch (fileData?.fileType) {
            case EXCEL: {
              let columnList = Object.values(fileData?.data)?.[0]?.[0];
              let check = true;
              if (latLngColumns) {
                for (let colName of latLngColumns) {
                  check = check && columnList.includes(t(colName)); // Check if columns exist in the file
                }
              }
              dataAvailabilityCheck = check
                ? dataAvailabilityCheck === "partial"
                  ? "partial"
                  : dataAvailabilityCheck === "false"
                  ? "partial"
                  : "true"
                : dataAvailabilityCheck === "partial"
                ? "partial"
                : dataAvailabilityCheck === "false"
                ? "false"
                : "partial"; // Update data availability based on column check
              let dataWithResources = Object.values(fileData?.data);
              if (resources && formulaConfiguration && hypothesisAssumptionsList && schema?.showResourcesInMappingSection) {
                dataWithResources = dataWithResources?.map((item) => {
                  return Digit.Utils.microplan.addResourcesToFilteredDataToShow(
                    item,
                    resources,
                    hypothesisAssumptionsList,
                    formulaConfiguration,
                    microplanData?.microplanPreview?.userEditedResources ? microplanData?.microplanPreview?.userEditedResources : [],
                    t
                  );
                });
              }

              let hasLocationData = false;
              // has lat lon a points
              const convertedData = dataWithResources?.map((item) =>
                item?.map((row, rowIndex) => {
                  if (rowIndex === 0) {
                    if (row.indexOf("features") === -1) {
                      row.push("feature");
                    }
                    return row;
                  }
                  const latIndex = item?.[0].findIndex((cell) => cell === "lat");
                  const lonIndex = item?.[0].findIndex((cell) => cell === "long");
                  let properties = {};
                  row.map((e, index) => {
                    properties[item?.[0]?.[index]] = e;
                  });
                  if (latIndex !== -1 && lonIndex !== -1) {
                    if (!hasLocationData) hasLocationData = true;
                    const lat = row[latIndex];
                    const lon = row[lonIndex];
                    const feature = {
                      type: "Feature",
                      properties: properties,
                      geometry: {
                        type: "Point",
                        coordinates: [lon, lat],
                      },
                    };
                    row.push(feature);
                  } else {
                    row.push(null);
                  }
                  return row;
                })
              );

              if (hasLocationData) {
                if (Object.values(fileData?.data).length > 0 && filterProperty) {
                  filterProperty?.forEach((item) => {
                    Object.values(fileData?.data).forEach((data) => {
                      let filterPropertyIndex = data?.[0].indexOf(item);
                      if (filterPropertyIndex && filterPropertyIndex !== -1)
                        data.slice(1).forEach((e) => {
                          return filterPropertiesCollector.add(e[filterPropertyIndex]);
                        });
                    });
                  });
                }
              }
              // extract dada
              var { hierarchyLists, hierarchicalData } = processHierarchyAndData(hierarchy, convertedData);
              if (filterDataOrigin?.boundriesDataOrigin?.includes(fileData?.section))
                setBoundary = { ...setBoundary, [fileData.section]: { hierarchyLists, hierarchicalData } };
              else if (filterDataOrigin?.layerDataOrigin?.includes(fileData?.section))
                setFilter = { ...setFilter, [fileData.section]: { hierarchyLists, hierarchicalData } };
              break;
            }
            case GEOJSON:
            case SHAPEFILE: {
              dataAvailabilityCheck = dataAvailabilityCheck === "partial" ? "partial" : dataAvailabilityCheck === "false" ? "partial" : "true"; // Update data availability for GeoJSON or Shapefile
              // Extract keys from the first feature's properties
              let keys = Object.keys(fileData?.data.features[0].properties);
              keys.push("feature");

              // Extract corresponding values for each feature
              const values = fileData?.data?.features.map((feature) => {
                // list with features added to it
                const temp = keys.map((key) => {
                  if (feature.properties[key] === "") {
                    return null;
                  }
                  if (key === "feature") return feature;
                  return feature.properties[key];
                });
                return temp;
              });

              if (fileData?.data?.features && filterProperty) {
                filterProperty?.forEach((item) => {
                  if (Object.values(fileData?.data).length > 0) {
                    fileData?.data?.features.forEach((e) => {
                      if (e?.properties?.[item]) filterPropertiesCollector.add(e?.properties?.[item]);
                    });
                  }
                });
              }

              // Group keys and values into the desired format
              // Adding resource data
              let dataWithResources = [keys, ...values];
              if (resources && formulaConfiguration && hypothesisAssumptionsList) {
                dataWithResources = Digit.Utils.microplan.addResourcesToFilteredDataToShow(
                  dataWithResources,
                  resources,
                  hypothesisAssumptionsList,
                  formulaConfiguration,
                  microplanData?.microplanPreview?.userEditedResources ? microplanData?.microplanPreview?.userEditedResources : [],
                  t
                );
                let indexOfFeatureInDataWithResources = dataWithResources?.[0]?.indexOf("feature");
                keys.push(...resources);
                dataWithResources = dataWithResources.map((item, index) => {
                  if (index === 0) return item;
                  let newProperties = {};
                  for (const e of keys) {
                    if (e === "feature") continue;
                    let index = dataWithResources?.[0]?.indexOf(e);
                    newProperties[e] = item[index];
                  }
                  let newRow = _.cloneDeep(item);
                  newRow[indexOfFeatureInDataWithResources] = { ...item[indexOfFeatureInDataWithResources], properties: newProperties };
                  return newRow;
                });
              }

              // extract dada
              var { hierarchyLists, hierarchicalData } = processHierarchyAndData(hierarchy, [dataWithResources]);
              if (filterDataOrigin?.boundriesDataOrigin?.includes(fileData?.section))
                setBoundary = { ...setBoundary, [fileData.section]: { hierarchyLists, hierarchicalData } };
              else if (filterDataOrigin?.layerDataOrigin?.includes(fileData?.section))
                setFilter = { ...setFilter, [fileData.section]: { hierarchyLists, hierarchicalData } };
            }
          }
        }
      }
    }

    // Set overall data availability
    setDataAvailability(dataAvailabilityCheck);

    // Combine boundary and layer data origins
    const combineList = [...(filterDataOrigin?.boundriesDataOrigin || []), ...(filterDataOrigin?.layerDataOrigin || [])];

    // Section wise check
    if (dataAvailabilityCheck === "true") {
      let sectionWiseCheck = true;
      combineList.forEach((item) => {
        sectionWiseCheck = Object.keys(files).includes(item) && sectionWiseCheck;
      });
      if (!sectionWiseCheck) dataAvailabilityCheck = "partial"; // Update data availability if section-wise check fails
    }

    // Update data availability based on conditions
    if (dataAvailabilityCheck === "initialStage" && (combineList.length === 0 || Object.keys(files).length === 0)) dataAvailabilityCheck = "false";
    switch (dataAvailabilityCheck) {
      case "false":
      case undefined:
        // Set warning toast message for no data to show
        setToast({
          state: "warning",
          message: t("MAPPING_NO_DATA_TO_SHOW"),
        });
        break;
      case "partial":
        // Set warning toast message for partial data to show
        setToast({
          state: "warning",
          message: t("MAPPING_PARTIAL_DATA_TO_SHOW"),
        });
        break;
    }
  } else {
    setToast({
      state: "error",
      message: t("MAPPING_NO_DATA_TO_SHOW"),
    });
  }
  setDataCompleteness(dataAvailabilityCheck);
  setBoundary = calculateAggregateForTreeMicroplanWrapper(setBoundary);
  setFilter = calculateAggregateForTreeMicroplanWrapper(setFilter);
  setBoundaryData((previous) => ({ ...previous, ...setBoundary }));
  setFilterData((previous) => ({ ...previous, ...setFilter }));
  setFilterProperties([...filterPropertiesCollector]);
  setFilterSelections([...filterPropertiesCollector]);
  setFilterPropertyNames([...filterPropertieNameCollector]);
  let tempVirtualizationPropertiesCollectorArray = [...virtualizationPropertiesCollector];
  if (tempVirtualizationPropertiesCollectorArray.length !== 0)
    setChoroplethProperties([...tempVirtualizationPropertiesCollectorArray, ...(resources ? resources : [])]);
};

//prepare geojson to show on the map
const prepareGeojson = (boundaryData, selection, style = {}) => {
  if (!boundaryData || Object.keys(boundaryData).length === 0) return [];
  let geojsonRawFeatures = [];
  if (selection == "ALL") {
    for (let data of Object.values(boundaryData)) {
      const templist = fetchFeatures(data?.hierarchicalData, selection, [], style);
      if (templist?.length !== 0) geojsonRawFeatures = [...geojsonRawFeatures, ...templist];
    }
  } else if (Array.isArray(selection)) {
    for (let data of Object.values(boundaryData)) {
      const templist = fetchFeatures(data?.hierarchicalData, selection, [], style);
      if (templist?.length !== 0) geojsonRawFeatures = [...geojsonRawFeatures, ...templist];
    }
  }

  return geojsonRawFeatures.filter(Boolean);
};
const fetchFeatures = (data, parameter = "ALL", outputList = [], addOn = {}) => {
  let tempStorage = [];
  if (parameter === "ALL") {
    // outputList(Object.values(data).flatMap(item=>item?.data?.feature))
    for (let [entityKey, entityValue] of Object.entries(data)) {
      if (entityValue?.data?.feature) {
        let feature = entityValue.data.feature;
        feature.properties["name"] = entityKey;
        feature.properties["addOn"] = addOn;
        if (entityValue?.children) tempStorage = [...tempStorage, feature, ...fetchFeatures(entityValue?.children, parameter, outputList, addOn)];
        else tempStorage = [...tempStorage, feature];
      } else {
        tempStorage = [...tempStorage, ...fetchFeatures(entityValue?.children, parameter, outputList, addOn)];
      }
    }
    return tempStorage;
  }
  if (Array.isArray(parameter)) {
    for (let [entityKey, entityValue] of Object.entries(data)) {
      if (parameter.includes(entityKey) && entityValue && entityValue.data && entityValue.data.feature) {
        let feature = entityValue.data.feature;
        feature.properties["name"] = entityKey;
        feature.properties["addOn"] = addOn;
        if (entityValue?.children) tempStorage = [...tempStorage, feature, ...fetchFeatures(entityValue?.children, parameter, outputList, addOn)];
        else tempStorage = [...tempStorage, feature];
      }
      if (entityValue?.children) tempStorage = [...tempStorage, ...fetchFeatures(entityValue?.children, parameter, outputList, addOn)];
    }
    return tempStorage;
  }
};

const addChoroplethProperties = (geojson, choroplethProperty, filteredSelection) => {
  // Calculate min and max values of the property
  const values = geojson.map((feature) => feature.properties[choroplethProperty]).filter((item) => !!item || item === 0) || [];
  if (!values || values.length === 0) return [];
  const convertedValues = values.map((item) => (!isNaN(item) ? item : 0));
  const minValue = Math.min(...convertedValues);
  const maxValue = Math.max(...convertedValues);

  // Create a new geojson object
  const newGeojson = geojson.map((feature) => {
    const newFeature = { ...feature, properties: { ...feature.properties, addOn: { ...feature.properties.addOn } } };
    let color;

    if (choroplethProperty) {
      color = interpolateColor(newFeature.properties[choroplethProperty], minValue, maxValue, MapChoroplethGradientColors);
    }

    newFeature.properties.addOn.fillColor = color;
    newFeature.properties.addOn.color = "rgba(0, 0, 0, 1)";
    if (!filteredSelection || filteredSelection.length === 0 || filteredSelection.includes(newFeature.properties.name)) {
      newFeature.properties.addOn.fillOpacity = 1;
    } else {
      newFeature.properties.addOn.fillOpacity = 0.4;
      newFeature.properties.addOn.opacity = 0.7;
    }

    return newFeature;
  });
  return newGeojson;
};

/**
 * filterGeojsons : json
 * filterSelection : array
 * MapFilters :
 */
const addFilterProperties = (filterGeojsons, filterSelections, filterPropertyNames, iconMapping) => {
  try {
    if (!filterGeojsons || !iconMapping || !filterSelections) return [];
    let newFilterGeojson = [];
    filterGeojsons.forEach((item) => {
      if (filterPropertyNames && filterPropertyNames.length !== 0 && item.properties) {
        let icon;
        filterPropertyNames.forEach((name) => {
          if (item.properties[name]) {
            let temp = item.properties[name];
            if (!filterSelections.includes(temp)) return;
            temp = iconMapping?.find((e) => e?.name == temp)?.icon?.marker;
            let DynamicIcon = IconCollection?.[temp];
            if (typeof DynamicIcon === "function") {
              icon = L.divIcon({
                className: "custom-svg-icon",
                html: DynamicIcon({}),
                iconAnchor: [25, 50],
              });
              newFilterGeojson.push({ ...item, properties: { ...item?.properties, addOn: { ...item?.properties?.addOn, icon: icon } } });
            } else {
              icon = DefaultMapMarker({});
              newFilterGeojson.push({ ...item, properties: { ...item?.properties, addOn: { ...item?.properties?.addOn, icon: icon } } });
            }
          }
        });
      }
      return item;
    });
    return newFilterGeojson;
  } catch (error) {
    console.error(error.message);
  }
};

/**
 * map: map
 * geojson: geojson
 * t: translator
 */

const addGeojsonToMap = (map, geojson, t) => {
  try {
    if (!map || !geojson) return false;
    const geojsonLayer = L.geoJSON(geojson, {
      style: function (feature) {
        if (Object.keys(feature.properties.addOn).length !== 0) {
          return feature.properties.addOn;
        } else {
          return {
            weight: 2,
            opacity: 1,
            color: "rgba(176, 176, 176, 1)",
            fillColor: "rgb(0,0,0,0)",
            // fillColor: choroplethProperty ? color : "rgb(0,0,0,0)",
            fillOpacity: 0,
            // fillOpacity: choroplethProperty ? (feature?.properties?.style?.fillOpacity ? feature.properties.style.fillOpacity : 0.7) : 0,
          };
        }
      },
      pointToLayer: function (feature, latlng) {
        if (feature.properties.addOn.icon) {
          let icon = feature.properties.addOn.icon;
          if (icon) {
            return L.marker(latlng, {
              icon: icon,
            });
          }
        }
        return L.marker(latlng, {
          icon: MapMarker(feature.properties.addOn),
        });
      },
      onEachFeature: function (feature, layer) {
        let popupContent;
        popupContent = "<div class='map-pop-up'>";
        popupContent += "<table style='border-collapse: collapse;'>";
        popupContent +=
          "<div style='font-family: Roboto;font-size: 1.3rem;font-weight: 700;text-align: left; color:rgba(11, 12, 12, 1);'>" +
          feature.properties["name"] +
          "</div>";
        for (let prop in feature.properties) {
          if (prop !== "name" && prop !== "addOn" && prop !== "feature") {
            let data = !!feature.properties[prop] ? feature.properties[prop] : t("NO_DATA");
            popupContent +=
              "<tr style='padding-top:0.5rem;'><td style='padding-top:0.5rem; font-family: Roboto;font-size: 0.8rem;font-weight: 700;text-align: left; color:rgba(80, 90, 95, 1);padding-right:1rem'>" +
              t(prop) +
              "</td><td>" +
              data +
              "</td></tr>";
          }
        }
        popupContent += "</table></div>";
        layer.bindPopup(popupContent, {
          minWidth: "28rem",
          padding: "0",
        });
        // Adjust map here when pop up closes
        layer.on("popupclose", function () {
          map.fitBounds(geojsonLayer.getBounds());
        });
        layer.on({
          mouseover: function (e) {
            const layer = e.target;
            if (layer.feature.properties.addOn && !layer.feature.properties.addOn.child) {
              return;
            }
            if (layer.setStyle)
              layer.setStyle({
                weight: 2.7,
                opacity: 1,
                color: "rgba(255, 255, 255, 1)",
              });
            // layer.openPopup();
          },
          mouseout: function (e) {
            const layer = e.target;
            if (layer.feature.properties.addOn && !layer.feature.properties.addOn.child) {
              return;
            }
            if (layer.setStyle) {
              if (layer.feature.properties.addOn && Object.keys(layer.feature.properties.addOn).length !== 0)
                layer.setStyle({
                  ...layer.feature.properties.addOn,
                });
              else
                layer.setStyle({
                  weight: 2,
                  color: "rgba(176, 176, 176, 1)",
                });
            }
            // layer.closePopup();
          },
        });
      },
    });
    geojsonLayer.addTo(map);
    return geojsonLayer;
  } catch (error) {
    console.error(error.message);
  }
};

function interpolateColor(value, minValue, maxValue, colors) {
  // Handle case where min and max values are the same
  if (minValue === maxValue) {
    // Return a default color or handle the case as needed
    return colors[0].color;
  }

  // Normalize the value to a percentage between 0 and 100
  const percent = !isNaN(value) ? ((value - minValue) / (maxValue - minValue)) * 100 : 0;
  // Find the two colors to interpolate between
  let lowerColor, upperColor;
  for (let i = 0; i < colors.length - 1; i++) {
    if (!isNaN(percent) && percent >= colors[i].percent && percent <= colors[i + 1].percent) {
      lowerColor = colors[i];
      upperColor = colors[i + 1];
      break;
    }
  }
  // Interpolate between the two colors
  const t = (percent - lowerColor.percent) / (upperColor.percent - lowerColor.percent);
  return chroma.mix(lowerColor.color, upperColor.color, t, "lab").hex();
}

// Find bounds for multiple geojson together
const findBounds = (data, buffer = 0.1) => {
  if (!Array.isArray(data) || data.length === 0) {
    return null;
  }

  // Initialize variables to store bounds
  var minLat = Number.MAX_VALUE;
  var maxLat = -Number.MAX_VALUE;
  var minLng = Number.MAX_VALUE;
  var maxLng = -Number.MAX_VALUE;

  // Iterate through the data to find bounds
  data.forEach(function (feature) {
    if (!feature || !feature.geometry || !feature.geometry.type || !feature.geometry.coordinates) {
      return null;
    }

    var coords = feature.geometry.coordinates;
    var geometryType = feature.geometry.type;

    switch (geometryType) {
      case "Point":
        var coord = coords;
        var lat = coord[1];
        var lng = coord[0];
        minLat = Math.min(minLat, lat);
        maxLat = Math.max(maxLat, lat);
        minLng = Math.min(minLng, lng);
        maxLng = Math.max(maxLng, lng);
        break;
      case "MultiPoint":
        coords.forEach(function (coord) {
          var lat = coord[1];
          var lng = coord[0];
          minLat = Math.min(minLat, lat);
          maxLat = Math.max(maxLat, lat);
          minLng = Math.min(minLng, lng);
          maxLng = Math.max(maxLng, lng);
        });
        break;
      case "LineString":
      case "MultiLineString":
      case "Polygon":
      case "MultiPolygon":
        coords.forEach(function (polygons) {
          if ((geometryType === "Polygon" || geometryType === "MultiPolygon") && Array.isArray(polygons[0][0])) {
            polygons.forEach(function (coordinates) {
              coordinates.forEach(function (coord) {
                if (!Array.isArray(coord) || coord.length !== 2 || typeof coord[0] !== "number" || typeof coord[1] !== "number") {
                  return null;
                }

                var lat = coord[1];
                var lng = coord[0];
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
                minLng = Math.min(minLng, lng);
                maxLng = Math.max(maxLng, lng);
              });
            });
          } else {
            polygons.forEach(function (coord) {
              if (!Array.isArray(coord) || coord.length !== 2 || typeof coord[0] !== "number" || typeof coord[1] !== "number") {
                return null;
              }

              var lat = coord[1];
              var lng = coord[0];
              minLat = Math.min(minLat, lat);
              maxLat = Math.max(maxLat, lat);
              minLng = Math.min(minLng, lng);
              maxLng = Math.max(maxLng, lng);
            });
          }
        });
        break;
      default:
        return null;
    }
  });

  // Check if valid bounds found
  if (minLat === Number.MAX_VALUE || maxLat === -Number.MAX_VALUE || minLng === Number.MAX_VALUE || maxLng === -Number.MAX_VALUE) {
    return null;
  }
  // Apply buffer to bounds
  minLat -= buffer;
  maxLat += buffer;
  minLng -= buffer;
  maxLng += buffer;

  // Set bounds for the Leaflet map
  var bounds = [
    [minLat, minLng],
    [maxLat, maxLng],
  ];

  return bounds;
};

const filterBoundarySelection = (boundaryData, boundarySelections) => {
  if (Object.keys(boundaryData).length === 0 || Object.keys(boundarySelections).length === 0) return [];
  let selectionList = [];
  Object.values(boundarySelections).forEach((item) => (selectionList = [...selectionList, ...item.map((e) => e.name)]));
  let childrenList = [];
  const set1 = new Set(selectionList);
  selectionList = selectionList.filter((item) => {
    const children = findChildren([item], Object.values(boundaryData)?.[0]?.hierarchicalData);
    if (children) {
      let childrenKeyList = getAllKeys(children);
      childrenList = [...childrenList, ...childrenKeyList];
      const nonePresent = childrenKeyList.every((item) => !set1.has(item));
      const allPresent = childrenKeyList.every((item) => set1.has(item));
      return nonePresent ? true : allPresent ? true : false;
    } else {
      return true;
    }
  });
  return { filteredSelection: selectionList, childrenList };
};

// Recursive function to extract all keys
const getAllKeys = (obj, keys = []) => {
  for (let [key, value] of Object.entries(obj)) {
    keys.push(key);
    if (value.children) {
      getAllKeys(value.children, keys);
    }
  }
  return keys;
};

// Remove all layers from the map
const removeAllLayers = (map, layer) => {
  if (!map) return;
  layer.forEach((layer) => {
    map.removeLayer(layer);
  });
};
// Map-Marker
const MapMarker = (style = {}) => {
  return L.divIcon({
    className: "custom-svg-icon",
    html: PopulationSvg(style),
    iconAnchor: [25, 50],
  });
};
const DefaultMapMarker = (style = {}) => {
  return L.divIcon({
    className: "custom-svg-icon",
    html: IconCollection.DefaultMapMarkerSvg(style),
    iconAnchor: [25, 50],
  });
};

const disableMapInteractions = (map) => {
  if (!map) return;
  map.dragging.disable();
  map.scrollWheelZoom.disable();
  map.touchZoom.disable();
  map.doubleClickZoom.disable();
  map.boxZoom.disable();
  map.keyboard.disable();
};

const enableMapInteractions = (map) => {
  if (!map) return;
  map.dragging.enable();
  map.scrollWheelZoom.enable();
  map.touchZoom.enable();
  map.doubleClickZoom.enable();
  map.boxZoom.enable();
  map.keyboard.enable();
};

// Exporting Mapping component
export default Mapping;
