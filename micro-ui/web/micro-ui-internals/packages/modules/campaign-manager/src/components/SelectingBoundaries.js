import React, { useEffect, useState, Fragment } from "react";
import { CardText, LabelFieldPair, Card, Header, CardLabel } from "@egovernments/digit-ui-react-components";
import { useTranslation } from "react-i18next";
import { Dropdown, InfoCard, MultiSelectDropdown } from "@egovernments/digit-ui-components";
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
  const [hierarchy, setHierarchy] = useState({});
  const [showcomponent, setShowComponent] = useState(false);
  const [boundaryType, setBoundaryType] = useState(null);
  const [boundaryData, setBoundaryData] = useState({});
  const [parentArray, setParentArray] = useState(null);
  const [boundaryTypeDataresult, setBoundaryTypeDataresult] = useState(null);
  const [selectedData,setSelectedData] = useState([]);
  const [parentBoundaryType , setParentBoundaryType] = useState(null);
  useEffect(() => {
    onSelect("boundaryType", {boundaryData:boundaryData , selectedData:selectedData});
  }, [boundaryData , selectedData]);
  const reqCriteriaBoundaryHierarchySearch = {
    url: "/boundary-service/boundary-hierarchy-definition/_search",
    params: {},
    body: {
      BoundaryTypeHierarchySearchCriteria: {
        tenantId: tenantId,
      },
    },
    config: {
      enabled: true,
    },
  };
  const { data: hierarchyTypeDataresult } = Digit.Hooks.useCustomAPIHook(reqCriteriaBoundaryHierarchySearch);
  const handleChange = (data) => {
    setHierarchy(data);
    setShowComponent(true);
    // to make the boundary data object
    const boundaryDataObj = {};
    data.boundaryHierarchy.forEach((boundary) => {
      boundaryDataObj[boundary.boundaryType] = [];
    });
    setBoundaryData(boundaryDataObj);
    const boundaryWithTypeNullParent = data.boundaryHierarchy.find((boundary) => boundary.parentBoundaryType === null);
    // Set the boundary type with null parentBoundaryType
    if (boundaryWithTypeNullParent) {
      setBoundaryType(boundaryWithTypeNullParent.boundaryType);
      setParentBoundaryType(boundaryWithTypeNullParent.boundaryType)
    }
  };

  const newData = [];
  const fetchBoundaryTypeData = async () => {
    if (parentArray === null) {
      const reqCriteriaBoundaryTypeSearch = Digit.CustomService.getResponse({
        url: "/boundary-service/boundary-relationships/_search",
        params: {
          tenantId: tenantId,
          hierarchyType: hierarchy?.hierarchyType,
          boundaryType: boundaryType,
          parent: null,
        },
        body: {},
      });
      const boundaryTypeData = await reqCriteriaBoundaryTypeSearch;
      setBoundaryTypeDataresult([boundaryTypeData]);
    } else {
      for (const parentCode of parentArray) {
        const reqCriteriaBoundaryTypeSearch = Digit.CustomService.getResponse({
          url: "/boundary-service/boundary-relationships/_search",
          params: {
            tenantId: tenantId,
            hierarchyType: hierarchy?.hierarchyType,
            boundaryType: boundaryType,
            parent: parentCode,
          },
          body: {},
        });
        const boundaryTypeData = await reqCriteriaBoundaryTypeSearch;
        newData.push(boundaryTypeData);
      }
      setBoundaryTypeDataresult(newData);
    }
  };

  useEffect(() => {
    fetchBoundaryTypeData();
  }, [boundaryType, parentArray, hierarchy]);

  useEffect(() => {
    if (boundaryTypeDataresult && boundaryTypeDataresult[0]?.TenantBoundary) {
      if (boundaryType !== undefined) {
        const updatedBoundaryData = {
          ...boundaryData,
          [boundaryType]: boundaryTypeDataresult,
        };
        setBoundaryData(updatedBoundaryData);
      } else {
        const updatedBoundaryData = {
          ...boundaryData,
          [boundaryTypeDataresult?.[0]?.TenantBoundary?.[0]?.boundary?.[0]?.boundaryType]: boundaryTypeDataresult,
        };
        setBoundaryData(updatedBoundaryData);
      }
    }
  }, [boundaryTypeDataresult , hierarchy]);

  const handleBoundaryChange = (data, boundary) => {
    let res = [];
    data &&
      data?.map((ob) => {
        res.push(ob?.[1]);
      });

    const transformedRes = res?.map((item) => ({
      code: item.code,
      type: item.boundaryType,
      isRoot: item.boundaryType === parentBoundaryType,
      includeAllChildren: true
    }));

    res.forEach((boundary) => {
      const index = transformedRes.findIndex((item) => item.code === boundary?.code);
      if (index !== -1) {
        transformedRes[index].includeAllChildren = true; // Set includeAllChildren to true for the selected boundary
      }
      // Find the parent boundary type using the hierarchy data
      const parentBoundaryType = hierarchy?.boundaryHierarchy.find((e) => e.boundaryType === boundary.boundaryType)?.parentBoundaryType;

      // If the selected boundary has a parent, set includeAllChildren to false for the parent
      if (parentBoundaryType) {
        const parentIndexes = selectedData.reduce((acc, item, index) => {
          if (item.type === parentBoundaryType) {
            acc.push(index);
          }
          return acc;
        }, []);
      
        parentIndexes.forEach((parentIndex) => {
          selectedData[parentIndex].includeAllChildren = false;
        });
      }
      
    });
  
    const newBoundaryType = transformedRes?.[0]?.type;
    const existingBoundaryType = selectedData.length > 0 ? selectedData?.[0]?.type : null;
  
    if (existingBoundaryType === newBoundaryType) {
      // Update only the data for the specific boundaryType
      const updatedSelectedData = selectedData?.map(item => {
        if (item.type === newBoundaryType) {
          return transformedRes;
        } else {
          return item;
        }
      });
      setSelectedData(updatedSelectedData);
    } else {
      // Update only the data for the new boundaryType
      setSelectedData([...selectedData.filter(item => item.type !== newBoundaryType), ...transformedRes]);
    }
    // setSelectedData(res);
    const parentBoundaryEntry = hierarchy ? hierarchy?.boundaryHierarchy.find((e) => e.parentBoundaryType === res?.[0]?.boundaryType) : null;
    setBoundaryType(parentBoundaryEntry?.boundaryType);
    const codes = res.map((item) => item.code);
    if (JSON.stringify(codes) !== JSON.stringify(parentArray)) {
      setParentArray(codes);
    }
  };

  return (
    <>
      <Card>
        <Header>{t(`CAMPAIGN_SELECT_HIERARCHY`)} </Header>
        <LabelFieldPair>
          <CardLabel>
            {`${t("HCM_HIERARCHY_TYPE")}`}
            <span className="mandatory-span">*</span>
          </CardLabel>
          <div className="digit-field">          <Dropdown
            t={t}
            option={hierarchyTypeDataresult?.BoundaryHierarchy}
            optionKey={"hierarchyType"}
            selected={hierarchy}
            select={(value) => {
              handleChange(value);
            }}
          /></div>
        </LabelFieldPair>
      </Card>
      {showcomponent && (
        <Card>
          <div className="selecting-boundary-div">
            <Header>{t(`CAMPAIGN_SELECT_BOUNDARY`)}</Header>
            <CardText>{t(`CAMPAIGN_SELECT_BOUNDARIES_DESCRIPTION`)}</CardText>
            {hierarchy?.boundaryHierarchy.map((boundary, index) => (
              <LabelFieldPair key={index}>
                <CardLabel>
                  {boundary.boundaryType}
                  <span className="mandatory-span">*</span>
                </CardLabel>
                <div className="digit-field">
                <MultiSelectDropdown
                  t={t}
                  // option={boundaryTypeDataresult?.TenantBoundary?.[0]?.boundary}
                  options={boundaryData[boundary.boundaryType]?.map((item) => item?.TenantBoundary?.[0]?.boundary).flat() || []}
                  optionsKey={"code"}
                  // selected={boundaryData}
                  onSelect={(value) => {
                    handleBoundaryChange(value, boundary);
                  }}
                />
                </div>
              </LabelFieldPair>
            ))}
          </div>
        </Card>
      )}
      {showcomponent && (
        <InfoCard
          populators={{
            name: "infocard",
          }}
          variant="default"
          style= {{margin: "0rem", maxWidth: "100%"}}
          additionalElements={[
            <span style={{ color:"#505a5f"}}>
              {t("HCM_BOUNDARY_INFO ")}
              <a href={`mailto:${mailConfig.mailId}`} style={{ color:"black"}}>{t("L1team@email.com")}</a>
            </span>
          ]}
          label={"Info"}
        />
      )}
    </>
  );
}
export default SelectingBoundaries;
