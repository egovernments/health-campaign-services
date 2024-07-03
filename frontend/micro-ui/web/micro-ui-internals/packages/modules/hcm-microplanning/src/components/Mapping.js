// Importing necessary modules
import { Card, Header } from "@egovernments/digit-ui-components";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import React, { useCallback, useEffect, useRef, useState, Fragment } from "react";
import { useTranslation } from "react-i18next";
import ZoomControl from "./ZoomControl";
import CustomScaleControl from "./CustomScaleControl";
import * as DigitSvgs from "@egovernments/digit-ui-svg-components";
import { LoaderWithGap } from "@egovernments/digit-ui-react-components";
import { tourSteps } from "../configs/tourSteps";
import { useMyContext } from "../utils/context";
import {
  MapFilterIndex,
  MapChoroplethIndex,
  ChoroplethSelection,
  FilterSection,
  BoundarySelection,
  BaseMapSwitcher,
} from "./MappingHelperComponents";
import {
  enableMapInteractions,
  disableMapInteractions,
  removeAllLayers,
  filterBoundarySelection,
  findBounds,
  addGeojsonToMap,
  addFilterProperties,
  addChoroplethProperties,
  prepareGeojson,
  extractGeoData,
} from "../utils/mappingUtils";

const page = "mapping";

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
    const UIConfiguration = state?.UIConfiguration;
    if (UIConfiguration) {
      const filterDataOriginList = UIConfiguration.find((item) => item.name === "mapping");
      setFilterDataOrigin(filterDataOriginList);
    }
    const BaseMapLayers = state?.BaseMapLayers;
    const schemas = state?.Schemas;
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

// Exporting Mapping component
export default Mapping;
