import React, { useEffect, useState, Fragment, useMemo } from "react";
import { CardText, LabelFieldPair, Card, Header, CardLabel, LoaderWithGap } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { InfoCard, MultiSelectDropdown, PopUp, Button, Toast } from "@egovernments/digit-ui-components";
import { mailConfig } from "../configs/mailConfig";
/**
 * The function `SelectingBoundaries` in JavaScript handles the selection of boundaries based on
 * hierarchy data and allows users to choose specific boundaries within the hierarchy.
 * @returns The `SelectingBoundaries` component is being returned. It consists of JSX elements
 * including Cards, Headers, Dropdowns, MultiSelectDropdowns, and InfoCard. The component allows users
 * to select hierarchy types and boundaries based on the data fetched from API calls. It also handles
 * the selection of boundaries and updates the state accordingly. The component is designed to be
 * interactive and user-friendly for selecting boundaries within
 */
function SelectingBoundaries({ onSelect, formData, ...props }) {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [params, setParams] = useState(props?.props?.dataParams);
  const [hierarchy, setHierarchy] = useState(params?.hierarchyType);
  const [boundaryType, setBoundaryType] = useState(
    props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData ? undefined : null
  );
  const [targetedData, setTargetedData] = useState();
  const [boundaryData, setBoundaryData] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData || {});
  // const [parentArray, setParentArray] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData.filter(item => item.includeAllChildren).map(item => item.code) || null);
  const [parentArray, setParentArray] = useState(null);
  const [boundaryTypeDataresult, setBoundaryTypeDataresult] = useState(null);
  const [selectedData, setSelectedData] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData || []);
  const [parentBoundaryTypeRoot, setParentBoundaryTypeRoot] = useState(
    (props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData?.find((item) => item?.isRoot === true) || {})
      ?.boundaryType || null
  );
  const [showToast, setShowToast] = useState(null);
  const [updatedHierarchy, setUpdatedHierarchy] = useState({});
  const [hierarchyTypeDataresult, setHierarchyTypeDataresult] = useState(params?.hierarchy);
  const [executionCount, setExecutionCount] = useState(0);
  // State variable to store the lowest hierarchy level
  // const [lowestHierarchy, setLowestHierarchy] = useState(null);
  const [showPopUp, setShowPopUp] = useState(null);
  const [restrictSelection, setRestrictSelection] = useState(null);
  const [updateBoundary, setUpdateBoundary] = useState(null);
  const [loaderEnabled, setLoaderEnabled] = useState(false);
  const { isLoading, data: hierarchyConfig } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "hierarchyConfig" }]);

  // const lowestHierarchy = hierarchyConfig?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.[0]?.lowestHierarchy;
  // const lowestHierarchy = useMemo(() => hierarchyConfig?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.[0]?.lowestHierarchy, [hierarchyConfig]);
  const lowestHierarchy = useMemo(() => {
    return hierarchyConfig?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.find(item => item.isActive)?.lowestHierarchy;
  }, [hierarchyConfig]);
  const lowestChild = hierarchyTypeDataresult?.boundaryHierarchy.filter((item) => item.parentBoundaryType === lowestHierarchy)?.[0]?.boundaryType;
  const searchParams = new URLSearchParams(location.search);
  const isDraft = searchParams.get("draft");
  const draftBoundary = searchParams.get("draftBoundary");

  function updateUrlParams(params) {
    const url = new URL(window.location.href);
    Object.entries(params).forEach(([key, value]) => {
      url.searchParams.set(key, value);
    });
    window.history.replaceState({}, "", url);
  }


  const fetchOptions = async ()=>{
    setLoaderEnabled(true);
    const draftSelected = props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
    for (const item of draftSelected) {
      const code = item?.code;
      const parent = item?.parent;
      const boundary = item?.type;

      const childBoundary = props?.props?.dataParams?.hierarchy?.boundaryHierarchy.filter((item) => item.parentBoundaryType === boundary)?.[0]?.boundaryType;
      const reqCriteriaBoundaryTypeSearch = await Digit.CustomService.getResponse({
        url: "/boundary-service/boundary-relationships/_search",
        params: {
          tenantId: tenantId,
          hierarchyType: props?.props?.dataParams?.hierarchyType,
          boundaryType: childBoundary,
          parent: code,
        },
        body: {},
      });
      const boundaryTypeData = reqCriteriaBoundaryTypeSearch;

      setBoundaryData((prevBoundaryData) => {
        const existingData = prevBoundaryData[childBoundary] || [];

        // Check if the entry already exists
        const updatedData = {
          ...prevBoundaryData,
          [childBoundary]: [...existingData.filter((entry) => entry.parentCode !== code), { parentCode: code, boundaryTypeData }],
        };
        return updatedData;
      });
    }
    updateUrlParams({ draftBoundary: false });
    setLoaderEnabled(false);
  }

  useEffect(()=>{
    if(isDraft == "true" && props?.props?.dataParams?.hierarchy &&
      props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData?.length > 0 &&
      draftBoundary === "true"
    ){
      fetchOptions();
    }
  },[isDraft,draftBoundary,props?.props?.dataParams?.hierarchy , props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData])

  useEffect(() => {
    if (!updateBoundary) {
      if (
        props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile?.length > 0 ||
        props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile?.length > 0 ||
        props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile?.length > 0
      ) {
        setRestrictSelection(true);
      }
    }
  }, [props?.props?.sessionData, updateBoundary]);

  useEffect(() => {
    if (props?.props?.dataParams) {
      setParams(props?.props?.dataParams);
    }
  }, [props?.props?.dataParams]);

  useEffect(() => {
    onSelect("boundaryType", { boundaryData: boundaryData, selectedData: selectedData, updateBoundary: updateBoundary });
  }, [boundaryData, selectedData]);

  useEffect(() => {
    setHierarchy(params?.hierarchyType);
  }, [params?.hierarchyType]);

  useEffect(() => {
    if (params?.hierarchy) {
      const sortHierarchy = (hierarchy) => {
        const boundaryMap = new Map();
        hierarchy.forEach(item => {
          boundaryMap.set(item.boundaryType, item);
        });

        const sortedHierarchy = [];
        let currentType = null;

        while (sortedHierarchy.length < hierarchy.length) {
          for (let i = 0; i < hierarchy.length; i++) {
            if (hierarchy[i].parentBoundaryType === currentType) {
              sortedHierarchy.push(hierarchy[i]);
              currentType = hierarchy[i].boundaryType;
              break;
            }
          }
        }

        return sortedHierarchy;
      };

      const sortedHierarchy = sortHierarchy(params.hierarchy.boundaryHierarchy);
      setHierarchyTypeDataresult({
        ...params.hierarchy,
        boundaryHierarchy: sortedHierarchy
      });
    }
  }, [params?.hierarchy]);

  useEffect(() => {
    if (executionCount < 5) {
      onSelect("boundaryType", { boundaryData: boundaryData, selectedData: selectedData, updateBoundary: updateBoundary });
      setExecutionCount((prevCount) => prevCount + 1);
    }
  });

  useEffect(() => {
    setBoundaryData(
      props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData
        ? props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData
        : {}
    );
    setSelectedData(
      props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData
        ? props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData
        : []
    );
  }, [props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType]);

  const closeToast = () => {
    setShowToast(null);
  };

  useEffect(() => {
    if (hierarchyTypeDataresult) {
      const boundaryDataObj = {};
      hierarchyTypeDataresult?.boundaryHierarchy?.forEach((boundary) => {
        boundaryDataObj[boundary?.boundaryType] = [];
      });
      if (!props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData || Object.keys(boundaryData).length === 0) {
        setBoundaryData(boundaryDataObj);
      }
      const boundaryWithTypeNullParent = hierarchyTypeDataresult?.boundaryHierarchy?.find((boundary) => boundary?.parentBoundaryType === null);
      // Set the boundary type with null parentBoundaryType
      if (boundaryWithTypeNullParent) {
        if (!props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData || Object.keys(boundaryData).length === 0) {
          setBoundaryType(boundaryWithTypeNullParent?.boundaryType);
        }
        setParentBoundaryTypeRoot(boundaryWithTypeNullParent?.boundaryType);
      }
      createHierarchyStructure(hierarchyTypeDataresult);
    }
  }, [hierarchyTypeDataresult]);

  function createHierarchyStructure(hierarchyTypeDataresult) {
    const hierarchyStructure = {};

    // Recursive function to gather all descendants for a given boundary type
    function gatherDescendants(boundaryType) {
      const descendants = [];
      hierarchyTypeDataresult;

      // Find all children for the current boundary type
      const children = hierarchyTypeDataresult?.boundaryHierarchy?.filter((item) => item?.parentBoundaryType === boundaryType);

      // Recursively gather descendants for each child
      children.forEach((child) => {
        const childBoundaryType = child?.boundaryType;
        const childDescendants = gatherDescendants(childBoundaryType);
        descendants.push(childBoundaryType, ...childDescendants);
      });

      return descendants;
    }

    // Iterate through the boundaryHierarchy array to populate hierarchyStructure
    hierarchyTypeDataresult?.boundaryHierarchy?.forEach((item) => {
      const boundaryType = item?.boundaryType;
      const descendants = gatherDescendants(boundaryType);

      hierarchyStructure[boundaryType] = descendants;
    });

    setUpdatedHierarchy(hierarchyStructure);
  }

  const newData = [];
  const fetchBoundaryTypeData = async () => {
    if (boundaryType === undefined || boundaryType === lowestChild) {
      // Do nothing if boundaryType is undefined
      return;
    }
    if (parentArray === null) {
      const reqCriteriaBoundaryTypeSearch = Digit.CustomService.getResponse({
        url: "/boundary-service/boundary-relationships/_search",
        params: {
          tenantId: tenantId,
          hierarchyType: hierarchy,
          boundaryType: boundaryType,
          parent: null,
        },
        body: {},
      });
      // setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_LOADING_BOUNDARY") });
      const boundaryTypeData = await reqCriteriaBoundaryTypeSearch;
      setBoundaryTypeDataresult([{ parentCode: null, boundaryTypeData: boundaryTypeData }]);
      // closeToast();
    } else {
      // for (const parentCode of parentArray) {
      //   const reqCriteriaBoundaryTypeSearch = Digit.CustomService.getResponse({
      //     url: "/boundary-service/boundary-relationships/_search",
      //     params: {
      //       tenantId: tenantId,
      //       hierarchyType: hierarchy,
      //       boundaryType: boundaryType,
      //       parent: parentCode,
      //     },
      //     body: {},
      //   });
      //   // setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_LOADING_BOUNDARY") });
      //   setLoaderEnabled(true);
      //   const boundaryTypeData = await reqCriteriaBoundaryTypeSearch;
      //   newData.push({ parentCode, boundaryTypeData });
      // }
      setLoaderEnabled(true);
      const temp = await Digit.Hooks.campaign.useParallelSearch({
        parentArray: parentArray,
        tenantId: tenantId,
        boundaryType: boundaryType,
        hierarchy: hierarchy,
        targetedData: targetedData,
      });
      const newDataArray = [...newData, ...temp];
      setBoundaryTypeDataresult(newDataArray);
      setTimeout(() => {
        setLoaderEnabled(false);
      }, 100);
      // closeToast();
    }
  };

  useEffect(() => {
    fetchBoundaryTypeData();
  }, [boundaryType, parentArray, selectedData]);

  useEffect(() => {
    if (boundaryTypeDataresult) {
      if (boundaryType !== undefined) {
        const updatedBoundaryData = {
          ...boundaryData,
          [boundaryType]: boundaryTypeDataresult,
        };
        setBoundaryData(updatedBoundaryData);
      } else {
        const updatedBoundaryData = {
          ...boundaryData,
          [boundaryTypeDataresult?.[0]?.boundaryTypeData?.TenantBoundary?.[0]?.boundary?.[0]?.boundaryType]: boundaryTypeDataresult,
        };
        setBoundaryData(updatedBoundaryData);
      }
    }
  }, [boundaryTypeDataresult]);

  const checkDataPresent = ({ action }) => {
    if (action === false) {
      setShowPopUp(false);
      setUpdateBoundary(true);
      setRestrictSelection(false);
      return;
    }
    if (action === true) {
      setShowPopUp(false);
      setUpdateBoundary(false);
      return;
    }
  };

  const handleBoundaryChange = (data, boundary) => {
    setTargetedData(boundary?.boundaryType);
    if (
      !updateBoundary &&
      restrictSelection &&
      (props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile?.length > 0 ||
        props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile?.length > 0 ||
        props?.props?.sessionData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile?.length > 0)
    ) {
      setShowPopUp(true);
      return;
    }
    if (!data || data.length === 0) {
      const check = updatedHierarchy[boundary?.boundaryType];

      if (check) {
        const typesToRemove = [boundary?.boundaryType, ...check];
        const updatedSelectedData = selectedData?.filter((item) => !typesToRemove?.includes(item?.type));
        const updatedBoundaryData = { ...boundaryData };

        typesToRemove.forEach((type) => {
          if (type !== boundary?.boundaryType && updatedBoundaryData?.hasOwnProperty(type)) {
            updatedBoundaryData[type] = [];
          }
        });
        if (!_.isEqual(selectedData, updatedSelectedData)) {
          setSelectedData(updatedSelectedData);
        }
        setBoundaryData(updatedBoundaryData);
      }
      return;
    }

    let res = [];
    data &&
      data?.map((ob) => {
        res.push(ob?.[1]);
      });
    let transformedRes = [];
    if (!isDraft) {
      transformedRes = res?.map((item) => ({
        code: item.code,
        type: item.type || item.boundaryType,
        isRoot: item.boundaryType === parentBoundaryTypeRoot,
        includeAllChildren: item.type === lowestHierarchy || item.boundaryType === lowestHierarchy,
        parent: item?.parent,
      }));
    } else {
      // transformedRes = selectedData.filter((item) => item?.type === boundary?.boundaryType)
      const filteredData = selectedData.filter((item) => item?.type === boundary?.boundaryType);
      if (filteredData.length === 0 || filteredData.length !== res.length) {
        // If no selected data for the particular boundary type, run the transformation logic
        transformedRes = res?.map((item) => ({
          code: item.code,
          type: item.type || item.boundaryType,
          isRoot: item.boundaryType === parentBoundaryTypeRoot,
          includeAllChildren: item.type === lowestHierarchy || item.boundaryType === lowestHierarchy,
          parent: item?.parent,
        }));
      } else {
        transformedRes = filteredData;
      }

    }

    const newBoundaryType = transformedRes?.[0]?.type;
    const existingBoundaryType = selectedData?.length > 0 ? selectedData?.[0]?.type : null;
    if (existingBoundaryType === newBoundaryType) {
      // Update only the data for the specific boundaryType
      const flattenedRes = transformedRes.flat();
      const updatedSelectedData = selectedData
        ?.map((item) => {
          if (item.type === newBoundaryType) {
            return transformedRes?.flat();
          } else {
            return item;
          }
        })
        .flat();
      if (!_.isEqual(selectedData, updatedSelectedData)) {
        setSelectedData(updatedSelectedData);
      }
    } else {
      // Update only the data for the new boundaryType
      const mergedData = [...selectedData?.filter((item) => item?.type !== newBoundaryType), ...transformedRes];

      // Filter out items with undefined type
      const filteredData = mergedData?.filter(
        (item, index, self) => item?.type !== undefined && index === self?.findIndex((t) => t?.code === item?.code)
      );

      // Filter out items whose parent is not present in the array

      const updatedSelectedData = [];
      const addChildren = (item) => {
        updatedSelectedData.push(item);
        const children = filteredData.filter((child) => child.parent === item.code);
        children.forEach((child) => addChildren(child));
      };
      filteredData.filter((item) => item.isRoot).forEach((rootItem) => addChildren(rootItem));
      if (!_.isEqual(selectedData, updatedSelectedData)) {
        setSelectedData(updatedSelectedData);
      }
    }
    const parentBoundaryEntry = hierarchyTypeDataresult
      ? hierarchyTypeDataresult?.boundaryHierarchy?.find(
          (e) => e?.parentBoundaryType === res?.[0]?.boundaryType || e?.parentBoundaryType === res?.[0]?.type
        )
      : null;
    setBoundaryType(parentBoundaryEntry?.boundaryType);
    const codes = res?.map((item) => item?.code);
    if (JSON.stringify(codes) !== JSON.stringify(parentArray)) {
      setParentArray(codes);
    }
  };

  return (
    <>
      {loaderEnabled && <LoaderWithGap text={"CAMPAIGN_BOUNDARY_PLEASE_WAIT"}></LoaderWithGap>}
      <Card>
        <div className="selecting-boundary-div">
          <Header>{t(`CAMPAIGN_SELECT_BOUNDARY`)}</Header>
          <CardText>{t(`CAMPAIGN_SELECT_BOUNDARIES_DESCRIPTION`)}</CardText>
          {hierarchyTypeDataresult?.boundaryHierarchy
            .filter((boundary, index, array) => {
              // Find the index of the lowest hierarchy
              const lowestIndex = array.findIndex((b) => b.boundaryType === lowestHierarchy);
              // Include only those boundaries that are above or equal to the lowest hierarchy
              return index <= lowestIndex;
            })
            .map((boundary, index) =>
              boundary?.parentBoundaryType == null ? (
                <LabelFieldPair key={index} style={{ alignItems: 'flex-start' }}>
                  <CardLabel>
                    {/* {t(`${hierarchy}_${boundary?.boundaryType}`?.toUpperCase())} */}
                    {t((hierarchy + "_" + boundary?.boundaryType).toUpperCase())}

                    <span className="mandatory-span">*</span>
                  </CardLabel>
                  <div className="digit-field">
                    <MultiSelectDropdown
                      props={{ className: "selecting-boundaries-dropdown" }}
                      t={t}
                      restrictSelection={restrictSelection}
                      options={
                        boundaryData[boundary?.boundaryType]?.map((item) => item?.boundaryTypeData?.TenantBoundary?.[0]?.boundary)?.flat() || []
                      }
                      optionsKey={"code"}
                      selected={selectedData?.filter((item) => item?.type === boundary?.boundaryType) || []}
                      onSelect={(value) => {
                        handleBoundaryChange(value, boundary);
                      }}
                      config={{
                        isDropdownWithChip: true, 
                      }}
                    />
                  </div>
                </LabelFieldPair>
              ) : (
                <LabelFieldPair key={index} style={{ alignItems: 'flex-start' }}>
                  <CardLabel>
                    {t((hierarchy + "_" + boundary?.boundaryType).toUpperCase())}
                    <span className="mandatory-span">*</span>
                  </CardLabel>
                  <div className="digit-field">
                    <MultiSelectDropdown
                      t={t}
                      restrictSelection={restrictSelection}
                      props={{ className: "selecting-boundaries-dropdown" }}
                      options={
                        boundaryData[boundary?.boundaryType]?.map((item) => ({
                          code: item?.parentCode,
                          options:
                            item?.boundaryTypeData?.TenantBoundary?.[0]?.boundary?.map((child) => ({
                              code: child?.code,
                              type: child?.boundaryType,
                              parent: item?.parentCode,
                            })) || [],
                        })) || []
                      }
                      optionsKey={"code"}
                      onSelect={(value) => {
                        handleBoundaryChange(value, boundary);
                      }}
                      config={{
                        isDropdownWithChip: true, 
                      }}
                      selected={selectedData?.filter((item) => item?.type === boundary?.boundaryType) || []}
                      addCategorySelectAllCheck={true}
                      addSelectAllCheck={true}
                      variant="nestedmultiselect"
                    />
                  </div>
                </LabelFieldPair>
              )
            )}
        </div>
      </Card>
      <InfoCard
        populators={{
          name: "infocard",
        }}
        variant="default"
        style={{ margin: "0rem", maxWidth: "100%" }}
        additionalElements={[
          <span style={{ color: "#505A5F" }}>
            {t("HCM_BOUNDARY_INFO ")}
            <a href={`mailto:${mailConfig.mailId}`} style={{ color: "black" }}>
              {mailConfig?.mailId}
            </a>
          </span>,
        ]}
        label={"Info"}
      />
      {showPopUp && (
        <PopUp
          className={"boundaries-pop-module"}
          type={"default"}
          heading={t("ES_CAMPAIGN_UPDATE_BOUNDARY_MODAL_HEADER")}
          children={[
            <div>
              <CardText style={{ margin: 0 }}>{t("ES_CAMPAIGN_UPDATE_BOUNDARY_MODAL_TEXT") + " "}</CardText>
            </div>,
          ]}
          onOverlayClick={() => {
            setShowPopUp(false);
          }}
          footerChildren={[
            <Button
              type={"button"}
              size={"large"}
              variation={"secondary"}
              label={t("ES_CAMPAIGN_BOUNDARY_MODAL_BACK")}
              onClick={() => {
                checkDataPresent({ action: false });
              }}
            />,
            <Button
              type={"button"}
              size={"large"}
              variation={"primary"}
              label={t("ES_CAMPAIGN_BOUNDARY_MODAL_SUBMIT")}
              onClick={() => {
                checkDataPresent({ action: true });
              }}
            />,
          ]}
          sortFooterChildren={true}
        ></PopUp>
      )}
      {showToast && (
        <Toast
          type={showToast?.key === "error" ? "error" : showToast?.key === "info" ? "info" : showToast?.key === "warning" ? "warning" : "success"}
          // error={showToast.key === "error" ? true : false}
          // warning={showToast.key === "warning" ? true : false}
          // info={showToast.key === "info" ? true : false}
          label={t(showToast.label)}
          onClose={closeToast}
        />
      )}
    </>
  );
}
export default SelectingBoundaries;
