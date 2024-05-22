import React, { useEffect, useState, Fragment } from "react";
import { CardText, LabelFieldPair, Card, Header, CardLabel } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { Dropdown, InfoCard, MultiSelectDropdown, Toast } from "@egovernments/digit-ui-components";
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
  // const [hierarchy, setHierarchy] = useState(props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.hierarchy || {});
  // const [showcomponent, setShowComponent] = useState(
  //   props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.hierarchy || false
  // );
  // const [boundaryType, setBoundaryType] = useState(null);
  const [boundaryType, setBoundaryType] = useState(
    props?.props?.sessionData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.boundaryData ? undefined : null
  );
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
  const [lowestHierarchy, setLowestHierarchy] = useState(null);

  useEffect(() => {
    if (props?.props?.dataParams) {
      setParams(props?.props?.dataParams);
    }
  }, [props?.props?.dataParams]);

  useEffect(() => {
    onSelect("boundaryType", { boundaryData: boundaryData, selectedData: selectedData });
  }, [boundaryData, selectedData]);

  useEffect(() => {
    setHierarchy(params?.hierarchyType);
  }, [params?.hierarchyType]);

  useEffect(() => {
    setHierarchyTypeDataresult(params?.hierarchy);
  }, [params?.hierarchy]);

  useEffect(() => {
    if (executionCount < 5) {
      onSelect("boundaryType", { boundaryData: boundaryData, selectedData: selectedData });
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

      setLowestHierarchy(
        hierarchyTypeDataresult?.boundaryHierarchy?.filter(
          (e) => !hierarchyTypeDataresult?.boundaryHierarchy?.find((e1) => e1?.parentBoundaryType == e?.boundaryType)
        )
      );
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
    if (boundaryType === undefined) {
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
      for (const parentCode of parentArray) {
        const reqCriteriaBoundaryTypeSearch = Digit.CustomService.getResponse({
          url: "/boundary-service/boundary-relationships/_search",
          params: {
            tenantId: tenantId,
            hierarchyType: hierarchy,
            boundaryType: boundaryType,
            parent: parentCode,
          },
          body: {},
        });
        setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_LOADING_BOUNDARY") });
        const boundaryTypeData = await reqCriteriaBoundaryTypeSearch;
        newData.push({ parentCode, boundaryTypeData });
      }
      setBoundaryTypeDataresult(newData);
      closeToast();
    }
  };

  useEffect(() => {
    fetchBoundaryTypeData();
  }, [boundaryType, parentArray]);

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

  const handleBoundaryChange = (data, boundary) => {
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
        setSelectedData(updatedSelectedData);
        setBoundaryData(updatedBoundaryData);
      }
      return;
    }

    let res = [];
    data &&
      data?.map((ob) => {
        res.push(ob?.[1]);
      });

    const transformedRes = res?.map((item) => ({
      code: item.code,
      type: item.type || item.boundaryType,
      isRoot: item.boundaryType === parentBoundaryTypeRoot,
      includeAllChildren: item.type === lowestHierarchy?.[0]?.boundaryType,
      parent: item?.parent,
    }));

    // res.forEach((boundary) => {
    //   const index = transformedRes?.findIndex((item) => item?.code === boundary?.code);
    //   if (index !== -1) {
    //     transformedRes[index].includeAllChildren = true;
    //   }
    //   // Find the parent boundary type using the hierarchy data
    //   const parentBoundaryType = hierarchyTypeDataresult?.boundaryHierarchy?.find((e) => e?.boundaryType === boundary?.boundaryType)
    //     ?.parentBoundaryType;

    //   // If the selected boundary has a parent, set includeAllChildren to false for the parent
    //   if (parentBoundaryType) {
    //     const parentIndexes = selectedData?.reduce((acc, item, index) => {
    //       if (item?.type === parentBoundaryType) {
    //         acc.push(index);
    //       }
    //       return acc;
    //     }, []);

    //     parentIndexes?.forEach((parentIndex) => {
    //       selectedData[parentIndex].includeAllChildren = true;
    //     });
    //   }
    // });

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

      setSelectedData(updatedSelectedData);
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

      setSelectedData(updatedSelectedData);
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
      <Card>
        <div className="selecting-boundary-div">
          <Header>{t(`CAMPAIGN_SELECT_BOUNDARY`)}</Header>
          <CardText>{t(`CAMPAIGN_SELECT_BOUNDARIES_DESCRIPTION`)}</CardText>
          {hierarchyTypeDataresult?.boundaryHierarchy.map((boundary, index) =>
            boundary?.parentBoundaryType == null ? (
              <LabelFieldPair key={index}>
                <CardLabel>
                  {t(`${hierarchy}_${boundary?.boundaryType}`?.toUpperCase())}
                  <span className="mandatory-span">*</span>
                </CardLabel>
                <div className="digit-field">
                  <MultiSelectDropdown
                    props={{ className: "selecting-boundaries-dropdown" }}
                    t={t}
                    options={boundaryData[boundary?.boundaryType]?.map((item) => item?.boundaryTypeData?.TenantBoundary?.[0]?.boundary)?.flat() || []}
                    optionsKey={"code"}
                    selected={selectedData?.filter((item) => item?.type === boundary?.boundaryType) || []}
                    onSelect={(value) => {
                      handleBoundaryChange(value, boundary);
                    }}
                  />
                </div>
              </LabelFieldPair>
            ) : (
              <LabelFieldPair key={index}>
                <CardLabel>
                  {t(`${hierarchy}_${boundary?.boundaryType}`?.toUpperCase())}
                  <span className="mandatory-span">*</span>
                </CardLabel>
                <div className="digit-field">
                  <MultiSelectDropdown
                    t={t}
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
