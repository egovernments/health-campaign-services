import { Loader, FormComposerV2, Header, MultiUploadWrapper, Button, Close, LogoutIcon } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useParams } from "react-router-dom";
import { CampaignConfig } from "../../configs/CampaignConfig";
import { QueryClient, useQueryClient } from "react-query";
import { Stepper, Toast } from "@egovernments/digit-ui-components";
import _ from "lodash";

/**
 * The `SetupCampaign` function in JavaScript handles the setup and management of campaign details,
 * including form data handling, validation, and submission.
 * @returns The `SetupCampaign` component is being returned. It consists of a form setup for creating
 * or updating a campaign with multiple steps like campaign details, delivery details, boundary
 * details, targets, facility details, user details, and review details. The form data is validated at
 * each step, and the user can navigate between steps using a stepper component. The form submission
 * triggers API calls to create or update the campaign
 */

function loopAndReturn(dataa) {
  let newArray = [];
  const data = dataa?.map((i) => ({ ...i, operator: { code: i?.operator }, attribute: { code: i?.attribute } }));

  data.forEach((item) => {
    // Check if an object with the same attribute already exists in the newArray
    const existingIndex = newArray.findIndex((element) => element.attribute.code === item.attribute.code);
    if (existingIndex !== -1) {
      // If an existing item is found, replace it with the new object
      const existingItem = newArray[existingIndex];
      newArray[existingIndex] = {
        attribute: existingItem.attribute,
        operator: { code: "IN_BETWEEN" },
        toValue: existingItem.value && item.value ? Math.min(existingItem.value, item.value) : null,
        fromValue: existingItem.value && item.value ? Math.max(existingItem.value, item.value) : null,
      };
    }
    // else if (item?.operator?.code === "EQUAL_TO" && item?.attribute?.code === "Gender") {
    // newArray.push({
    // ...item,
    // value: item?.value
    // ? {
    // code: item?.value,
    // }
    // : null,
    // });
    // }
    else {
      // If no existing item with the same attribute is found, push the current item
      // if (item?.operator?.code === "EQUAL_TO" && item?.attribute?.code === "Gender") {
      //   newArray.push({
      //     ...item,
      //     value: {
      //       code: item?.value,
      //     },
      //   });
      // } else {
      newArray.push(item);
      // }
    }
  });

  const withKey = newArray.map((i, c) => ({ key: c + 1, ...i }));
  return withKey;
}

function cycleDataRemap(data) {
  if (!data) return null;
  const uniqueCycleObjects = Object.values(
    data?.reduce((acc, obj) => {
      acc[obj?.cycleNumber] = acc[obj?.cycleNumber] || obj;
      return acc;
    }, {})
  );
  return uniqueCycleObjects.map((i, n) => {
    return {
      key: i.cycleNumber,
      fromDate: i?.startDate ? new Date(i?.startDate)?.toISOString()?.split("T")?.[0] : null,
      toDate: i?.endDate ? new Date(i?.endDate)?.toISOString()?.split("T")?.[0] : null,
    };
  });
}

// function reverseDeliveryRemap(data) {
//   if (!data) return null;
//   const reversedData = [];
//   let currentCycleIndex = null;
//   let currentDeliveryIndex = null;
//   let currentCycle = null;
//   let currentDelivery = null;

//   data.forEach((item, index) => {
//     if (currentCycleIndex !== item.cycleNumber) {
//       currentCycleIndex = item.cycleNumber;
//       currentCycle = {
//         cycleIndex: currentCycleIndex.toString(),
//         active: index === 0, // Set active to true only for the first index
//         deliveries: [],
//       };
//       reversedData.push(currentCycle);
//     }

//     if (currentDeliveryIndex !== item.deliveryNumber) {
//       currentDeliveryIndex = item.deliveryNumber;
//       currentDelivery = {
//         deliveryIndex: currentDeliveryIndex.toString(),
//         active: item?.deliveryNumber === 1, // Set active to true only for the first index
//         deliveryRules: [],
//       };
//       currentCycle.deliveries.push(currentDelivery);
//     }

//     currentDelivery.deliveryRules.push({
//       ruleKey: currentDelivery.deliveryRules.length + 1,
//       delivery: {},
//       attributes: loopAndReturn(item.conditions),
//       products: [...item.products],
//     });
//   });

//   return reversedData;
// }

function reverseDeliveryRemap(data) {
  if (!data) return null;
  const reversedData = [];
  let currentCycleIndex = null;
  let currentCycle = null;

  data.forEach((item, index) => {
    if (currentCycleIndex !== item.cycleNumber) {
      currentCycleIndex = item.cycleNumber;
      currentCycle = {
        cycleIndex: currentCycleIndex.toString(),
        active: index === 0, // Initialize active to false
        deliveries: [],
      };
      reversedData.push(currentCycle);
    }

    const deliveryIndex = item.deliveryNumber.toString();

    let delivery = currentCycle.deliveries.find((delivery) => delivery.deliveryIndex === deliveryIndex);

    if (!delivery) {
      delivery = {
        deliveryIndex: deliveryIndex,
        active: item.deliveryNumber === 1, // Set active to true only for the first delivery
        deliveryRules: [],
      };
      currentCycle.deliveries.push(delivery);
    }

    delivery.deliveryRules.push({
      ruleKey: item.deliveryRuleNumber,
      delivery: {},
      deliveryType: item?.deliveryType,
      attributes: loopAndReturn(item.conditions),
      products: [...item.products],
    });
  });

  return reversedData;
}

function groupByType(data) {
  if (!data) return null;
  const result = {};

  data.forEach((item) => {
    if (result[item.type]) {
      result[item.type].push(item);
    } else {
      result[item.type] = [item];
    }
  });

  return {
    TenantBoundary: {
      boundary: result,
    },
  };
}

function groupByTypeRemap(data) {
  if (!data) return null;

  const result = {};

  data.forEach((item) => {
    const type = item?.type;
    const boundaryType = item?.type;
    const parentCode = item?.parent;
    const obj = {
      parentCode,
      boundaryTypeData: {
        TenantBoundary: [
          {
            boundary: [{ ...item, boundaryType }],
          },
        ],
      },
    };

    if (result[type]) {
      result[type][0].boundaryTypeData.TenantBoundary[0].boundary.push(item);
    } else {
      result[type] = [obj];
    }
  });

  return result;
}

// Example usage:
// updateUrlParams({ id: 'sdjkhsdjkhdshfsdjkh', anotherParam: 'value' });
function updateUrlParams(params) {
  const url = new URL(window.location.href);
  Object.entries(params).forEach(([key, value]) => {
    url.searchParams.set(key, value);
  });
  window.history.replaceState({}, "", url);
}

const SetupCampaign = ({ hierarchyType }) => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();
  const history = useHistory();
  const [currentStep, setCurrentStep] = useState(0);
  const [totalFormData, setTotalFormData] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [campaignConfig, setCampaignConfig] = useState(CampaignConfig(totalFormData, null, isSubmitting));
  const [shouldUpdate, setShouldUpdate] = useState(false);
  const [params, setParams, clearParams] = Digit.Hooks.useSessionStorage("HCM_CAMPAIGN_MANAGER_FORM_DATA", {});
  const [dataParams, setDataParams] = Digit.Hooks.useSessionStorage("HCM_CAMPAIGN_MANAGER_UPLOAD_ID", {});
  const [showToast, setShowToast] = useState(null);
  const [summaryErrors, setSummaryErrors] = useState({});
  const { mutate } = Digit.Hooks.campaign.useCreateCampaign(tenantId);
  const { mutate: updateCampaign } = Digit.Hooks.campaign.useUpdateCampaign(tenantId);
  const searchParams = new URLSearchParams(location.search);
  const id = searchParams.get("id");
  const isPreview = searchParams.get("preview");
  const isSummary = searchParams.get("summary");
  const noAction = searchParams.get("action");
  const isDraft = searchParams.get("draft");
  const isSkip = searchParams.get("skip");
  const keyParam = searchParams.get("key");
  const [isDraftCreated, setIsDraftCreated] = useState(false);
  const filteredBoundaryData = params?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
  const client = useQueryClient();
  // const hierarchyType2 = params?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.hierarchy?.hierarchyType
  const [currentKey, setCurrentKey] = useState(() => {
    const keyParam = searchParams.get("key");
    return keyParam ? parseInt(keyParam) : 1;
  });

  // const [lowest, setLowest] = useState(null);
  const [fetchBoundary, setFetchBoundary] = useState(() => Boolean(searchParams.get("fetchBoundary")));
  const [fetchUpload, setFetchUpload] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [targetEnabled, setTargetEnabled] = useState(false);
  const [facilityEnabled, setFacilityEnabled] = useState(false);
  const [userEnabled, setUserEnabled] = useState(false);
  const [active, setActive] = useState(0);
  const { data: hierarchyConfig } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "hierarchyConfig" }]);
  const [refetchGenerate, setRefetchGenerate] = useState(null);
  // const hierarchyType = hierarchyConfig?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.[0]?.hierarchy;

  // const lowestHierarchy = hierarchyConfig?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.[0]?.lowestHierarchy;
  const lowestHierarchy = useMemo(() => hierarchyConfig?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.[0]?.lowestHierarchy, [hierarchyConfig]);

  const reqCriteria = {
    url: `/boundary-service/boundary-hierarchy-definition/_search`,
    changeQueryName: `${hierarchyType}`,
    body: {
      BoundaryTypeHierarchySearchCriteria: {
        tenantId: tenantId,
        limit: 2,
        offset: 0,
        hierarchyType: hierarchyType,
      },
    },
  };

  const { data: hierarchyDefinition } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  // useEffect(() => {
  //   if (hierarchyDefinition) {
  //     setLowest(
  //       hierarchyDefinition?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.filter(
  //         (e) => !hierarchyDefinition?.BoundaryHierarchy?.[0]?.boundaryHierarchy?.find((e1) => e1?.parentBoundaryType == e?.boundaryType)
  //       )
  //     );
  //   }
  // }, [hierarchyDefinition]);
  const { isLoading: draftLoading, data: draftData, error: draftError, refetch: draftRefetch } = Digit.Hooks.campaign.useSearchCampaign({
    tenantId: tenantId,
    filter: {
      ids: [id],
    },
    config: {
      enabled: id ? true : false,
      select: (data) => {
        return data?.[0];
      },
    },
  });

  const { isLoading, data: projectType } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-PROJECT-TYPES", [{ name: "projectTypes" }]);

  useEffect(() => {
    if (fetchUpload) {
      setFetchUpload(false);
    }
    if (fetchBoundary && currentKey > 5) {
      setFetchBoundary(false);
    }
  }, [fetchUpload, fetchBoundary]);

  useEffect(() => {
    if (isPreview === "true") {
      setIsDraftCreated(true);
      setCurrentKey(10);
      return;
    }
    if (isDraft === "true") {
      setIsDraftCreated(true);
      if (isSkip === "false") {
        currentKey !== 1 ? null : setCurrentKey(1);
      } else {
        setCurrentKey(draftData?.additionalDetails?.key);
      }
      return;
    }
  }, [isPreview, isDraft, draftData]);

  useEffect(() => {
    setTotalFormData(params);
  }, [params]);

  //DATA STRUCTURE
  useEffect(() => {
    if (isLoading) return;
    if (Object.keys(params).length !== 0) return;
    if (!draftData) return;
    const delivery = Array.isArray(draftData?.deliveryRules) ? draftData?.deliveryRules : [];
    const filteredProjectType = projectType?.["HCM-PROJECT-TYPES"]?.projectTypes?.filter((i) => i?.code === draftData?.projectType);
    const restructureFormData = {
      HCM_CAMPAIGN_TYPE: { projectType: filteredProjectType?.[0] },
      HCM_CAMPAIGN_NAME: {
        campaignName: draftData?.campaignName,
      },
      HCM_CAMPAIGN_DATE: {
        campaignDates: {
          startDate: draftData?.startDate ? new Date(draftData?.startDate)?.toISOString()?.split("T")?.[0] : "",
          endDate: draftData?.endDate ? new Date(draftData?.endDate)?.toISOString()?.split("T")?.[0] : "",
        },
      },
      HCM_CAMPAIGN_CYCLE_CONFIGURE: {
        cycleConfigure: {
          cycleConfgureDate: draftData?.additionalDetails?.cycleData?.cycleConfgureDate
            ? draftData?.additionalDetails?.cycleData?.cycleConfgureDate
            : {
                cycle: delivery?.map((obj) => obj?.cycleNumber)?.length > 0 ? Math.max(...delivery?.map((obj) => obj?.cycleNumber)) : 1,
                deliveries: delivery?.map((obj) => obj?.deliveryNumber)?.length > 0 ? Math.max(...delivery?.map((obj) => obj?.deliveryNumber)) : 1,
                refetch: true,
              },
          cycleData: draftData?.additionalDetails?.cycleData?.cycleData
            ? draftData?.additionalDetails?.cycleData?.cycleData
            : cycleDataRemap(delivery),
        },
      },
      HCM_CAMPAIGN_DELIVERY_DATA: {
        deliveryRule: reverseDeliveryRemap(delivery),
      },
      HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA: {
        boundaryType: {
          boundaryData: groupByTypeRemap(draftData?.boundaries),
          selectedData: draftData?.boundaries,
        },
      },
      HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA: {
        uploadBoundary: { uploadedFile: draftData?.resources?.filter((i) => i?.type === "boundaryWithTarget"), 
          isSuccess : draftData?.resources?.filter((i) => i?.type === "boundaryWithTarget").length>0
        },
      },
      HCM_CAMPAIGN_UPLOAD_FACILITY_DATA: {
        uploadFacility: { uploadedFile: draftData?.resources?.filter((i) => i?.type === "facility") ,
        isSuccess : draftData?.resources?.filter((i) => i?.type === "facility").length>0
        }
      },
      HCM_CAMPAIGN_UPLOAD_USER_DATA: {
        uploadUser: { uploadedFile: draftData?.resources?.filter((i) => i?.type === "user") , 
          isSuccess : draftData?.resources?.filter((i) => i?.type === "user").length>0
         },
      },
    };
    setParams({ ...restructureFormData });
  }, [params, draftData, isLoading, projectType]);

  useEffect(() => {
    setTimeout(() => {
      setEnabled(fetchUpload || (fetchBoundary && currentKey > 3));
      setFacilityEnabled(refetchGenerate || (!dataParams?.boundaryId && (fetchUpload || (fetchBoundary && currentKey > 3))));
      setTargetEnabled(refetchGenerate || (!dataParams?.facilityId && (fetchUpload || (fetchBoundary && currentKey > 3))));
      setUserEnabled(refetchGenerate || (!dataParams?.userId && (fetchUpload || (fetchBoundary && currentKey > 3))));
    }, 3000);
    if (refetchGenerate === true) {
      setRefetchGenerate(false);
    }
  }, [fetchUpload, fetchBoundary, currentKey, dataParams, refetchGenerate]);

  const { data: facilityId, isLoading: isFacilityLoading, refetch: refetchFacility } = Digit.Hooks.campaign.useGenerateIdCampaign({
    type: "facilityWithBoundary",
    hierarchyType: hierarchyType,
    campaignId: id,
    // config: {
    //   enabled: setTimeout(fetchUpload || (fetchBoundary && currentKey > 6)),
    // },
    config: {
      enabled: facilityEnabled,
    },
  });

  const { data: boundaryId, isLoading: isBoundaryLoading, refetch: refetchBoundary } = Digit.Hooks.campaign.useGenerateIdCampaign({
    type: "boundary",
    hierarchyType: hierarchyType,
    campaignId: id,
    // config: {
    //   enabled: fetchUpload || (fetchBoundary && currentKey > 6),
    // },
    config: {
      enabled: targetEnabled,
    },
  });

  const { data: userId, isLoading: isUserLoading, refetch: refetchUser } = Digit.Hooks.campaign.useGenerateIdCampaign({
    type: "userWithBoundary",
    hierarchyType: hierarchyType,
    campaignId: id,
    // config: {
    //   enabled: fetchUpload || (fetchBoundary && currentKey > 6),
    // },
    config: {
      enabled: userEnabled,
    },
  });

  useEffect(() => {
    if (draftData?.additionalDetails?.facilityId && draftData?.additionalDetails?.targetId && draftData?.additionalDetails?.userId) {
      setDataParams({
        ...dataParams,
        boundaryId: draftData?.additionalDetails?.targetId,
        facilityId: draftData?.additionalDetails?.facilityId,
        userId: draftData?.additionalDetails?.userId,
        hierarchyType: hierarchyType,
        hierarchy: hierarchyDefinition?.BoundaryHierarchy?.[0],
      });
    }
  }, [isBoundaryLoading, isFacilityLoading, isUserLoading, facilityId, boundaryId, userId, hierarchyDefinition?.BoundaryHierarchy?.[0], draftData]); // Only run if dataParams changes


  useEffect(() => {
     if (hierarchyDefinition?.BoundaryHierarchy?.[0]) {
      setDataParams({
        ...dataParams,
        facilityId: facilityId,
        boundaryId: boundaryId,
        userId: userId,
        hierarchyType: hierarchyType,
        hierarchy: hierarchyDefinition?.BoundaryHierarchy?.[0],
        isBoundaryLoading,
        isFacilityLoading,
        isUserLoading,
      });
    }
  }, [isBoundaryLoading, isFacilityLoading, isUserLoading, facilityId, boundaryId, userId, hierarchyDefinition?.BoundaryHierarchy?.[0], draftData]);
  useEffect(() => {
    setCampaignConfig(CampaignConfig(totalFormData, dataParams, isSubmitting, summaryErrors));
  }, [totalFormData, dataParams, isSubmitting, summaryErrors]);

  useEffect(() => {
    setIsSubmitting(false);
    if (currentKey === 10 && isSummary !== "true") {
      updateUrlParams({ key: currentKey, summary: true });
    } else {
      updateUrlParams({ key: currentKey, summary: false });
      setSummaryErrors(null);
    }
  }, [currentKey]);

  function restructureData(data) {
    const dateData = totalFormData?.HCM_CAMPAIGN_CYCLE_CONFIGURE?.cycleConfigure?.cycleData;
    const restructuredData = [];

    data.forEach((cycle) => {
      cycle.deliveries.forEach((delivery, index) => {
        delivery.deliveryRules.forEach((rule) => {
          const restructuredRule = {
            startDate: Digit.Utils.date.convertDateToEpoch(dateData?.find((i) => i.key == cycle.cycleIndex)?.fromDate), // Hardcoded for now
            endDate: Digit.Utils.date.convertDateToEpoch(dateData?.find((i) => i?.key == cycle?.cycleIndex)?.toDate), // Hardcoded for now
            cycleNumber: parseInt(cycle.cycleIndex),
            deliveryNumber: parseInt(delivery.deliveryIndex),
            deliveryType: rule?.deliveryType,
            deliveryRuleNumber: parseInt(rule.ruleKey), // New key added
            products: [],
            conditions: [],
          };

          rule.attributes.forEach((attribute) => {
            if (attribute?.operator?.code === "IN_BETWEEN") {
              restructuredRule.conditions.push({
                attribute: attribute?.attribute?.code
                  ? attribute?.attribute?.code
                  : typeof attribute?.attribute === "string"
                  ? attribute?.attribute
                  : null,
                operator: "LESS_THAN_EQUAL_TO",
                value: attribute.fromValue ? Number(attribute.fromValue) : null,
              });

              restructuredRule.conditions.push({
                attribute: attribute?.attribute?.code
                  ? attribute?.attribute?.code
                  : typeof attribute?.attribute === "string"
                  ? attribute?.attribute
                  : null,
                operator: "GREATER_THAN_EQUAL_TO",
                value: attribute.toValue ? Number(attribute.toValue) : null,
              });
            } else {
              restructuredRule.conditions.push({
                attribute: attribute?.attribute?.code
                  ? attribute?.attribute?.code
                  : typeof attribute?.attribute === "string"
                  ? attribute?.attribute
                  : null,
                operator: attribute.operator ? attribute.operator.code : null,
                value:
                  attribute?.attribute?.code === "Gender" && attribute?.value?.length > 0
                    ? attribute?.value
                    : attribute?.value
                    ? Number(attribute?.value)
                    : null,
              });
            }
          });

          rule.products.forEach((prod) => {
            restructuredRule.products.push({
              value: prod?.value,
              name: prod?.name,
              count: prod?.count,
            });
          });

          restructuredData.push(restructuredRule);
        });
      });
    });

    return restructuredData;
  }

  function resourceData(facilityData, boundaryData, userData) {
    const resources = [facilityData, boundaryData, userData].filter((data) => data !== null && data !== undefined);
    return resources;
  }

  useEffect(async () => {
    if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
      const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
    }
  }, [shouldUpdate]);

  const compareIdentical = (draftData, payload) => {
    return _.isEqual(draftData, payload);
  };
  //API CALL
  useEffect(async () => {
    if (shouldUpdate === true) {
      if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.skipAPICall && !id) {
        return;
      } else if (filteredConfig?.[0]?.form?.[0]?.isLast) {
        const reqCreate = async () => {
          let payloadData = { ...draftData };
          payloadData.hierarchyType = hierarchyType;
          payloadData.startDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate)
            : null;
          payloadData.endDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate)
            : null;
          payloadData.tenantId = tenantId;
          payloadData.action = "create";
          payloadData.campaignName = totalFormData?.HCM_CAMPAIGN_NAME?.campaignName;
          if (totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData) {
            payloadData.boundaries = totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
          }
          const temp = resourceData(
            totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile?.[0],
            totalFormData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile?.[0],
            totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile?.[0]
          );
          payloadData.resources = temp;
          payloadData.projectType = totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.code;
          payloadData.additionalDetails = {
            beneficiaryType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType,
            key: currentKey,
            targetId: dataParams?.boundaryId,
            facilityId: dataParams?.facilityId,
            userId: dataParams?.userId,
          };
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          }

          // await mutate(payloadData, {
          //   onError: (error, variables) => {},
          //   onSuccess: async (data) => {
          //     draftRefetch();
          //     history.push(
          //       `/${window.contextPath}/employee/campaign/response?campaignId=${data?.CampaignDetails?.campaignNumber}&isSuccess=${true}`,
          //       {
          //         message: "ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE",
          //         text: "ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE_TEXT",
          //       }
          //     );
          //     Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
          //   },
          // });
          if (compareIdentical(draftData, payloadData) === false) {
            await updateCampaign(payloadData, {
              onError: (error, variables) => {
                console.log(error);
                setShowToast({ key: "error", label: error?.message ? error?.message : error });
              },
              onSuccess: async (data) => {
                draftRefetch();
                history.push(
                  `/${window.contextPath}/employee/campaign/response?campaignId=${data?.CampaignDetails?.campaignNumber}&isSuccess=${true}`,
                  {
                    message: t("ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE"),
                    text: t("ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE_TEXT"),
                    info: t("ES_CAMPAIGN_SUCCESS_INFO_TEXT"),
                    actionLabel: t("HCM_CAMPAIGN_SUCCESS_RESPONSE_ACTION"),
                    actionLink: `/${window.contextPath}/employee/campaign/my-campaign`,
                  }
                );
                Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
              },
            });
          }
        };

        reqCreate();
      } else if (!isDraftCreated && !id) {
        const reqCreate = async () => {
          let payloadData = {};
          payloadData.hierarchyType = hierarchyType;
          if (totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate) {
            payloadData.startDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
              ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate)
              : null;
          }
          if (totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate) {
            payloadData.endDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate
              ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate)
              : null;
          }
          payloadData.tenantId = tenantId;
          payloadData.action = "draft";
          payloadData.campaignName = totalFormData?.HCM_CAMPAIGN_NAME?.campaignName;
          if (totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData) {
            payloadData.boundaries = totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
          }
          const temp = resourceData(
            totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile?.[0],
            totalFormData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile?.[0],
            totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile?.[0]
          );
          payloadData.resources = temp;
          payloadData.projectType = totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.code;
          payloadData.additionalDetails = {
            beneficiaryType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType,
            key: currentKey,
            targetId: dataParams?.boundaryId,
            facilityId: dataParams?.facilityId,
            userId: dataParams?.userId,
          };
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          }

          await mutate(payloadData, {
            onError: (error, variables) => {
              if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.mandatoryOnAPI) {
                setShowToast({ key: "error", label: error?.message ? error?.message : error });
              }
            },
            onSuccess: async (data) => {
              updateUrlParams({ id: data?.CampaignDetails?.id });
              setIsDraftCreated(true);
              draftRefetch();
              if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.mandatoryOnAPI) {
                setCurrentKey(currentKey + 1);
              }
            },
          });
        };

        reqCreate();
      } else {
        const reqCreate = async () => {
          let payloadData = { ...draftData };
          payloadData.hierarchyType = hierarchyType;
          if (totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate) {
            payloadData.startDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
              ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate)
              : null;
          }
          if (totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate) {
            payloadData.endDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate
              ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate)
              : null;
          }
          payloadData.tenantId = tenantId;
          payloadData.action = "draft";
          payloadData.campaignName = totalFormData?.HCM_CAMPAIGN_NAME?.campaignName;
          if (totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData) {
            payloadData.boundaries = totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
          }
          const temp = resourceData(
            totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile?.[0],
            totalFormData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile?.[0],
            totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile?.[0]
          );
          payloadData.resources = temp;
          payloadData.projectType = totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.code;
          payloadData.additionalDetails = {
            beneficiaryType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType,
            key: currentKey,
            targetId: dataParams?.boundaryId,
            facilityId: dataParams?.facilityId,
            userId: dataParams?.userId,
          };
          if (totalFormData?.HCM_CAMPAIGN_CYCLE_CONFIGURE?.cycleConfigure) {
            payloadData.additionalDetails.cycleData = totalFormData?.HCM_CAMPAIGN_CYCLE_CONFIGURE?.cycleConfigure;
          } else {
            payloadData.additionalDetails.cycleData = {};
          }
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          } else {
            payloadData.deliveryRules = [];
          }
          if (!payloadData?.startDate && !payloadData?.endDate) {
            delete payloadData?.startDate;
            delete payloadData?.endDate;
          }
          if (compareIdentical(draftData, payloadData) === false) {
            await updateCampaign(payloadData, {
              onError: (error, variables) => {
                console.log(error);
                if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.mandatoryOnAPI) {
                  setShowToast({ key: "error", label: error?.message ? error?.message : error });
                }
              },
              onSuccess: async (data) => {
                updateUrlParams({ id: data?.CampaignDetails?.id });
                draftRefetch();
                if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.mandatoryOnAPI) {
                  setCurrentKey(currentKey + 1);
                }
              },
            });
          } else {
            setCurrentKey(currentKey + 1);
          }
        };

        reqCreate();
      }
      setShouldUpdate(false);
    }
  }, [shouldUpdate]);

  function validateCycleData(data) {
    const { cycle, deliveries } = data?.cycleConfigure?.cycleConfgureDate;
    const cycleData = data.cycleConfigure.cycleData;
    let dateError = [];
    // Validate cycle and deliveries
    // if (cycle <= 0 || deliveries <= 0) {
    //   return { error: true, message: "DELIVERY_CYCLE_EMPTY_ERROR" };
    // }

    [...Array(cycle)].forEach((item, index) => {
      const check = cycleData?.find((i) => i?.key === index + 1);
      if (!check?.fromDate || !check?.toDate) {
        dateError.push({
          name: `CYCLE_${index + 1}`,
          cycle: index + 1,
          dateError: true,
          // error: `Dates are missing in Cycle {CYCLE_NO}${index + 1}`,
          error: t(`CAMPAIGN_SUMMARY_DATE_MISSING_ERROR`, { CYCLE_NO: index + 1 }),
          button: t(`CAMPAIGN_SUMMARY_ADD_DATE_ACTION`),
        });
      }
    });
    // setSummaryErrors((prev) => {
    //   return {
    //     ...prev,
    //     deliveryErrors: prev?.deliveryErrors ? [...prev.deliveryErrors, ...dateError] : [...dateError],
    //   };
    // });
    // Validate cycleData length
    // if (cycleData.length !== cycle) {
    // return { error: true, message: "DELIVERY_CYCLE_MISMATCH_LENGTH_ERROR" };
    // }

    // Validate fromDate and startDate in cycleData
    // for (const item of cycleData) {
    // if (!item.fromDate || !item.toDate) {
    // return { error: true, message: "DELIVERY_CYCLE_DATE_ERROR" };
    // }
    // }

    return dateError;
  }

  function validateDeliveryRules(data, projectType, cycleConfigureData) {
    let isValid = true;
    let deliveryRulesError = [];
    let dateError = validateCycleData(cycleConfigureData);

    // Iterate over deliveryRule array
    data.deliveryRule.forEach((cycle) => {
      cycle.deliveries.forEach((delivery) => {
        delivery.deliveryRules.forEach((rule) => {
          // Validate attributes and products length
          if (projectType !== "LLIN-MZ" && !rule?.deliveryType) {
            isValid = false;
            deliveryRulesError?.push({
              name: `CYCLE_${cycle?.cycleIndex}`,
              cycle: cycle?.cycleIndex,
              // error: `Delivery Type missing in delivery condition ${rule?.ruleKey} delivery ${delivery?.deliveryIndex}`,
              error: t(`CAMPAIGN_SUMMARY_DELIVERY_TYPE_MISSING_ERROR`, {
                CONDITION_NO: rule?.ruleKey,
                DELIVERY_NO: delivery?.deliveryIndex,
                CYCLE_NO: cycle?.cycleIndex,
              }),
              button: t(`CAMPAIGN_SUMMARY_ADD_DELIVERY_TYPE_ACTION`),
            });
            // return;
          }
          if (rule.attributes.length === 0) {
            isValid = false;
            deliveryRulesError?.push({
              name: `CYCLE_${cycle?.cycleIndex}`,
              cycle: cycle?.cycleIndex,
              // error: `Values missing in delivery condition ${rule?.ruleKey} delivery ${delivery?.deliveryIndex}`,
              error: t(`CAMPAIGN_SUMMARY_VALUES_MISSING_ERROR`, {
                CONDITION_NO: rule?.ruleKey,
                DELIVERY_NO: delivery?.deliveryIndex,
                CYCLE_NO: cycle?.cycleIndex,
              }),
              button: t(`CAMPAIGN_SUMMARY_ADD_VALUES_ACTION`),
            });
            // return;
          }
          if (rule.products.length === 0) {
            isValid = false;
            deliveryRulesError?.push({
              name: `CYCLE_${cycle?.cycleIndex}`,
              cycle: cycle?.cycleIndex,
              // error: `Product missing in delivery condition ${rule?.ruleKey} delivery ${delivery?.deliveryIndex}`,
              error: t(`CAMPAIGN_SUMMARY_PRODUCT_MISSING_ERROR`, {
                CONDITION_NO: rule?.ruleKey,
                DELIVERY_NO: delivery?.deliveryIndex,
                CYCLE_NO: cycle?.cycleIndex,
              }),
              button: t(`CAMPAIGN_SUMMARY_ADD_PRODUCT_ACTION`),
            });
            // return;
          }

          rule.attributes.forEach((attribute) => {
            // Check if attribute, operator, and value are empty
            if (attribute.attribute === "" || attribute.operator === null || attribute.value === "") {
              if (attribute?.operator?.code === "IN_BETWEEN" && attribute?.toValue !== "" && attribute?.fromValue !== "") {
                isValid = true;
              } else {
                deliveryRulesError?.push({
                  name: `CYCLE_${cycle?.cycleIndex}`,
                  cycle: cycle?.cycleIndex,
                  error: t(`CAMPAIGN_SUMMARY_ATTRIBUTES_MISSING_ERROR`, {
                    CONDITION_NO: rule?.ruleKey,
                    DELIVERY_NO: delivery?.deliveryIndex,
                    CYCLE_NO: cycle?.cycleIndex,
                  }),
                  // error: `Attributes missing in delivery condition ${rule?.ruleKey} delivery ${delivery?.deliveryIndex}`,
                  button: t(`CAMPAIGN_SUMMARY_ADD_ATTRIBUTES_ACTION`),
                });
                isValid = false;
              }
            }
          });

          rule.products.forEach((product) => {
            // Check if count and value are empty
            if (product.count === null || product.value === null) {
              isValid = false;
            }
          });
        });
      });
    });

    setSummaryErrors((prev) => {
      return {
        ...prev,
        deliveryErrors: [...deliveryRulesError, ...dateError],
      };
    });
    return isValid;
    // ? "Delivery rules are valid"
    // : "Attributes, operators, values, count, or value are not empty in delivery rules or attributes/products length is 0";
  }

  function checkAttributeValidity(data) {
    for (const rule of data?.deliveryRule) {
      for (const delivery of rule?.deliveries) {
        for (const rule of delivery?.deliveryRules) {
          for (const attribute of rule?.attributes) {
            if (
              attribute?.operator &&
              attribute?.operator?.code === "IN_BETWEEN" &&
              attribute?.fromValue &&
              attribute?.toValue &&
              attribute?.fromValue !== "" &&
              attribute?.toValue !== "" &&
              Number(attribute?.toValue) >= Number(attribute?.fromValue)
            ) {
              // return `Error: Attribute "${attribute?.attribute?.code ? attribute?.attribute?.code : attribute?.attribute}" has invalid range (${
              //   attribute.toValue
              // } to ${attribute.fromValue})`;
              return "CAMPAIGN_IN_BETWEEN_ERROR";
            } else if (attribute?.value === 0 || attribute?.value === "0") {
              return "CAMPAIGN_VALUE_ZERO_ERROR";
            }
          }
        }
      }
    }
    return false;
  }

  // function validateBoundaryLevel(data) {
  //   // Extracting boundary types from hierarchy response
  //   const boundaryTypes = new Set(hierarchyDefinition?.BoundaryHierarchy?.[0]?.boundaryHierarchy.map((item) => item?.boundaryType));

  //   // Extracting unique boundary types from data
  //   const uniqueDataBoundaryTypes = new Set(data?.map((item) => item.type));

  //   // Checking if all unique boundary types from hierarchy response are present in data
  //   const allBoundaryTypesPresent = [...boundaryTypes].every((type) => uniqueDataBoundaryTypes.has(type));

  //   return allBoundaryTypesPresent;
  // }

  function validateBoundaryLevel(data) {
    // Extracting boundary hierarchy from hierarchy definition
    const boundaryHierarchy = hierarchyDefinition?.BoundaryHierarchy?.[0]?.boundaryHierarchy || [];

    // Find the index of the lowest hierarchy
    const lowestIndex = boundaryHierarchy.findIndex((item) => item?.boundaryType === lowestHierarchy);

    // Create a set of boundary types including only up to the lowest hierarchy
    const boundaryTypes = new Set(boundaryHierarchy.filter((_, index) => index <= lowestIndex).map((item) => item?.boundaryType));

    // Extracting unique boundary types from data
    const uniqueDataBoundaryTypes = new Set(data?.map((item) => item.type));

    // Checking if all boundary types from the filtered hierarchy are present in data
    const allBoundaryTypesPresent = [...boundaryTypes].every((type) => uniqueDataBoundaryTypes.has(type));

    return allBoundaryTypesPresent;
  }

  // function recursiveParentFind(filteredData) {
  //   const parentChildrenMap = {};

  //   // Build the parent-children map
  //   filteredData?.forEach((item) => {
  //     if (item?.parent) {
  //       if (!parentChildrenMap[item?.parent]) {
  //         parentChildrenMap[item?.parent] = [];
  //       }
  //       parentChildrenMap[item?.parent].push(item.code);
  //     }
  //   });

  //   // Check for missing children
  //   const missingParents = filteredData?.filter((item) => item?.parent && !parentChildrenMap[item.code]);
  //   const extraParent = missingParents?.filter((i) => i?.type !== lowest?.[0]?.boundaryType);

  //   return extraParent;
  // }

  function recursiveParentFind(filteredData) {
    const parentChildrenMap = {};

    // Build the parent-children map
    filteredData?.forEach((item) => {
      if (item?.parent) {
        if (!parentChildrenMap[item?.parent]) {
          parentChildrenMap[item?.parent] = [];
        }
        parentChildrenMap[item?.parent].push(item.code);
      }
    });

    // Check for missing children
    const missingParents = filteredData?.filter((item) => item?.parent && !parentChildrenMap[item.code]);
    const extraParent = missingParents?.filter((i) => i?.type !== lowestHierarchy);

    return extraParent;
  }

  // validating the screen data on clicking next button
  const handleValidate = (formData) => {
    const key = Object.keys(formData)?.[0];
    switch (key) {
      case "campaignName":
        if (typeof formData?.campaignName !== "string" || !formData?.campaignName.trim()) {
          setShowToast({ key: "error", label: "CAMPAIGN_NAME_MISSING_TYPE_ERROR" });
          return false;
        } else if (formData.campaignName.length > 250) {
          setShowToast({ key: "error", label: "CAMPAIGN_NAME_TOO_LONG_ERROR" });
          return false;
        } else {
          setShowToast(null);
          return true;
        }
      case "projectType":
        if (!formData?.projectType) {
          setShowToast({ key: "error", label: "PROJECT_TYPE_UNDEFINED_ERROR" });
          return false;
        } else {
          setShowToast(null);
          return true;
        }
      case "campaignDates":
        const startDateObj = new Date(formData?.campaignDates?.startDate);
        const endDateObj = new Date(formData?.campaignDates?.endDate);
        if (!formData?.campaignDates?.startDate || !formData?.campaignDates?.endDate) {
          setShowToast({ key: "error", label: `${t("HCM_CAMPAIGN_DATE_MISSING")}` });
          return false;
        } else if (endDateObj.getTime() === startDateObj.getTime()) {
          setShowToast({ key: "error", label: `${t("HCM_CAMPAIGN_END_DATE_EQUAL_START_DATE")}` });
          return false;
        } else if (endDateObj.getTime() < startDateObj) {
          setShowToast({ key: "error", label: `${t("HCM_CAMPAIGN_END_DATE_BEFORE_START_DATE")}` });
          return false;
        } else {
          setShowToast(null);
          return true;
        }
      case "boundaryType":
        if (formData?.boundaryType?.selectedData) {
          const validateBoundary = validateBoundaryLevel(formData?.boundaryType?.selectedData);
          const missedType = recursiveParentFind(formData?.boundaryType?.selectedData);
          if (!validateBoundary) {
            setShowToast({ key: "error", label: t("HCM_CAMPAIGN_ALL_THE_LEVELS_ARE_MANDATORY") });
            return false;
          } else if (recursiveParentFind(formData?.boundaryType?.selectedData).length > 0) {
            setShowToast({
              key: "error",
              label: `${t(`HCM_CAMPAIGN_FOR`)} ${t(`${hierarchyType}_${missedType?.[0]?.type}`?.toUpperCase())} ${t(missedType?.[0]?.code)} ${t(
                `HCM_CAMPAIGN_CHILD_NOT_PRESENT`
              )}`,
            });
            return false;
          }
          setShowToast(null);
          const checkEqual = _.isEqual(
            formData?.boundaryType?.selectedData,
            totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData
          );
          console.log("HSHSHS", checkEqual);
          setFetchUpload(true);
          setRefetchGenerate(checkEqual === false ? true : false);
          return true;
        } else {
          setShowToast({ key: "error", label: `${t("HCM_SELECT_BOUNDARY")}` });
          return false;
        }

      case "uploadBoundary":
        if (formData?.uploadBoundary?.isValidation) {
          setShowToast({ key: "info", label: `${t("HCM_FILE_VALIDATION_PROGRESS")}`, transitionTime: 6000000000 });
          return false;
        } else if (formData?.uploadBoundary?.isError) {
          if (formData?.uploadBoundary?.apiError) {
            setShowToast({ key: "error", label: formData?.uploadBoundary?.apiError, transitionTime: 6000000000 });
          } else setShowToast({ key: "error", label: `${t("HCM_FILE_VALIDATION")}` });
          return false;
        } else {
          setShowToast(null);
          return true;
        }

      case "uploadFacility":
        if (formData?.uploadFacility?.isValidation) {
          setShowToast({ key: "info", label: `${t("HCM_FILE_VALIDATION_PROGRESS")}`, transitionTime: 6000000000 });
          return false;
        } else if (formData?.uploadFacility?.isError) {
          if (formData?.uploadFacility?.apiError) {
            setShowToast({ key: "error", label: formData?.uploadFacility?.apiError, transitionTime: 6000000000 });
          } else setShowToast({ key: "error", label: `${t("HCM_FILE_VALIDATION")}` });
          return false;
        } else {
          setShowToast(null);
          return true;
        }
      case "uploadUser":
        if (formData?.uploadUser?.isValidation) {
          setShowToast({ key: "info", label: `${t("HCM_FILE_VALIDATION_PROGRESS")}`, transitionTime: 6000000000 });
          return false;
        } else if (formData?.uploadUser?.isError) {
          if (formData?.uploadUser?.apiError) {
            setShowToast({ key: "error", label: formData?.uploadUser?.apiError, transitionTime: 6000000000 });
          } else setShowToast({ key: "error", label: `${t("HCM_FILE_VALIDATION")}` });
          return false;
        } else {
          setShowToast(null);
          return true;
        }

      case "cycleConfigure":
        const cycleNumber = formData?.cycleConfigure?.cycleConfgureDate?.cycle;
        const deliveryNumber = formData?.cycleConfigure?.cycleConfgureDate?.deliveries;
        if (cycleNumber === "" || cycleNumber === 0 || deliveryNumber === "" || deliveryNumber === 0) {
          setShowToast({ key: "error", label: "DELIVERY_CYCLE_ERROR" });
          return false;
        } else {
          setShowToast(null);
          return true;
        }
      case "deliveryRule":
        const isAttributeValid = checkAttributeValidity(formData);
        if (isAttributeValid) {
          setShowToast({ key: "error", label: isAttributeValid });
          return false;
        }
        setShowToast(null);
        return;
      case "summary":
        const cycleConfigureData = totalFormData?.HCM_CAMPAIGN_CYCLE_CONFIGURE;
        const isCycleError = validateCycleData(cycleConfigureData);
        const deliveryCycleData = totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA;
        const isDeliveryError = validateDeliveryRules(
          deliveryCycleData,
          totalFormData?.["HCM_CAMPAIGN_TYPE"]?.projectType?.code?.toUpperCase(),
          cycleConfigureData
        );
        const isTargetError = totalFormData?.HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA?.uploadBoundary?.uploadedFile?.[0]?.filestoreId
          ? false
          : (setSummaryErrors((prev) => {
              return {
                ...prev,
                target: [
                  {
                    name: `target`,
                    error: t(`TARGET_FILE_MISSING`),
                  },
                ],
              };
            }),
            true);
        const isFacilityError = totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.uploadedFile?.[0]?.filestoreId
          ? false
          : (setSummaryErrors((prev) => {
              return {
                ...prev,
                facility: [
                  {
                    name: `facility`,
                    error: t(`FACILITY_FILE_MISSING`),
                  },
                ],
              };
            }),
            true);
        const isUserError = totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA?.uploadUser?.uploadedFile?.[0]?.filestoreId
          ? false
          : (setSummaryErrors((prev) => {
              return {
                ...prev,
                user: [
                  {
                    name: `user`,
                    error: t(`USER_FILE_MISSING`),
                  },
                ],
              };
            }),
            true);
        if (isCycleError?.length > 0) {
          setShowToast({ key: "error", label: "DELIVERY_CYCLE_MISMATCH_LENGTH_ERROR" });
          return false;
        }
        if (isDeliveryError === false) {
          setShowToast({ key: "error", label: "DELIVERY_RULES_ERROR" });
          return false;
        }
        if (isTargetError) {
          setShowToast({ key: "error", label: "TARGET_DETAILS_ERROR" });
          return false;
        }
        if (isFacilityError) {
          setShowToast({ key: "error", label: "FACILITY_DETAILS_ERROR" });
          return false;
        }
        if (isUserError) {
          setShowToast({ key: "error", label: "USER_DETAILS_ERROR" });
          return false;
        }
        setShowToast(null);
        return true;
      default:
        break;
    }
  };

  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 10000);
    }
  }, [showToast]);

  const onSubmit = (formData, cc) => {
    setIsSubmitting(true);
    const checkValid = handleValidate(formData);
    if (checkValid === false) {
      return;
    }

    const name = filteredConfig?.[0]?.form?.[0]?.name;

    if (name === "HCM_CAMPAIGN_TYPE" && totalFormData?.["HCM_CAMPAIGN_TYPE"]?.projectType?.code !== formData?.projectType?.code) {
      setTotalFormData((prevData) => ({
        [name]: formData,
      }));
      //to set the data in the local storage
      setParams({
        [name]: { ...formData },
      });
    } else if (name === "HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA" && formData?.boundaryType?.updateBoundary === true) {
      setTotalFormData((prevData) => ({
        ...prevData,
        [name]: formData,
        ["HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA"]: {},
        ["HCM_CAMPAIGN_UPLOAD_FACILITY_DATA"]: {},
        ["HCM_CAMPAIGN_UPLOAD_USER_DATA"]: {},
      }));
      //to set the data in the local storage
      setParams({
        ...params,
        [name]: { ...formData },
        ["HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA"]: {},
        ["HCM_CAMPAIGN_UPLOAD_FACILITY_DATA"]: {},
        ["HCM_CAMPAIGN_UPLOAD_USER_DATA"]: {},
      });
    } else {
      setTotalFormData((prevData) => ({
        ...prevData,
        [name]: formData,
      }));
      //to set the data in the local storage
      setParams({
        ...params,
        [name]: { ...formData },
      });
    }

    if (
      filteredConfig?.[0]?.form?.[0]?.isLast ||
      !filteredConfig[0].form[0].body[0].skipAPICall ||
      (filteredConfig[0].form[0].body[0].skipAPICall && id)
    ) {
      setShouldUpdate(true);
    }

    if (!filteredConfig?.[0]?.form?.[0]?.isLast && !filteredConfig[0].form[0].body[0].mandatoryOnAPI) {
      setCurrentKey(currentKey + 1);
    }
    if (isDraft === "true" && isSkip !== "false") {
      updateUrlParams({ skip: "false" });
    }
    return;
  };

  const onStepClick = (step) => {
    if ((currentKey === 5 || currentKey === 6) && step > 1) {
      return;
    }
    const filteredSteps = campaignConfig[0].form.filter((item) => item.stepCount === String(step + 1));

    const key = parseInt(filteredSteps[0].key);
    const name = filteredSteps[0].name;

    if (step === 6 && Object.keys(totalFormData).includes("HCM_CAMPAIGN_UPLOAD_USER_DATA")) {
      setCurrentKey(10);
      setCurrentStep(7);
    } else if (step === 1 && totalFormData["HCM_CAMPAIGN_NAME"] && totalFormData["HCM_CAMPAIGN_NAME"]) {
      setCurrentKey(3);
      setCurrentStep(1);
    } else if (!totalFormData["HCM_CAMPAIGN_NAME"] || !totalFormData["HCM_CAMPAIGN_NAME"]) {
      // Do not set stepper and key
    } else if (Object.keys(totalFormData).includes(name)) {
      setCurrentKey(key);
      setCurrentStep(step);
      // Do not set stepper and key
    }
  };

  const filterNonEmptyValues = (obj) => {
    const keys = [];
    for (const key in obj) {
      if (typeof obj[key] === "object" && obj[key] !== null) {
        // Check if any nested value is non-null and non-empty
        if (hasNonEmptyValue(obj[key])) {
          keys.push(key);
        }
      } else if (obj[key] !== null && obj[key] !== "") {
        keys.push(key);
      }
    }
    return keys;
  };

  const hasNonEmptyValue = (obj) => {
    for (const key in obj) {
      if (obj[key] !== null && obj[key] !== "") {
        if (typeof obj[key] === "object") {
          if (hasNonEmptyValue(obj[key])) {
            return true;
          }
        } else {
          return true;
        }
      }
    }
    return false;
  };

  const findHighestStepCount = () => {
    const totalFormDataKeys = Object.keys(totalFormData);

    const nonNullFormDataKeys = filterNonEmptyValues(totalFormData);

    const relatedSteps = campaignConfig?.[0]?.form.filter((step) => nonNullFormDataKeys.includes(step.name));

    const highestStep = relatedSteps.reduce((max, step) => Math.max(max, parseInt(step.stepCount)), 0);
    if(isDraft == "true"){
      const filteredSteps = campaignConfig?.[0]?.form.find((item) => item.key === keyParam)?.stepCount;
      setActive(filteredSteps);
    }
    else{
    setActive(highestStep);
    }

    // setActive(highestStep);
  };

  useEffect(() => {
    findHighestStepCount();
  }, [totalFormData, campaignConfig]);

  const onSecondayActionClick = () => {
    if (currentKey > 1) {
      setShouldUpdate(false);
      setCurrentKey(currentKey - 1);
    }
  };

  // filtering the config on the basis of the screen or key
  // const filteredConfig = campaignConfig
  //   .map((config) => {
  //     return {
  //       ...config,
  //       form: config?.form.filter((step) => parseInt(step.key) == currentKey),
  //     };
  //   })
  //   .filter((config) => config.form.length > 0);

  const filterCampaignConfig = (campaignConfig, currentKey) => {
    return campaignConfig
      .map((config) => {
        return {
          ...config,
          form: config?.form.filter((step) => parseInt(step.key) === currentKey),
        };
      })
      .filter((config) => config.form.length > 0);
  };

  const [filteredConfig, setFilteredConfig] = useState(filterCampaignConfig(campaignConfig, currentKey));

  useEffect(() => {
    setFilteredConfig(filterCampaignConfig(campaignConfig, currentKey));
  }, [campaignConfig, currentKey]);

  const config = filteredConfig?.[0];

  // setting the current step when the key is changed on the basis of the config
  useEffect(() => {
    setCurrentStep(Number(filteredConfig?.[0]?.form?.[0]?.stepCount - 1));
    // setShowToast(null);
  }, [currentKey, filteredConfig]);

  useEffect(() => {
    // setCurrentStep(Number(filteredConfig?.[0]?.form?.[0]?.stepCount - 1));
    setShowToast(null);
  }, [currentKey]);

  const closeToast = () => {
    setShowToast(null);
  };

  if (isPreview === "true" && !draftData) {
    return <Loader />;
  }

  if (isDraft === "true" && !draftData) {
    return <Loader />;
  }

  return (
    <React.Fragment>
      {noAction !== "false" && (
        <Stepper
          customSteps={[
            "HCM_CAMPAIGN_SETUP_DETAILS",
            "HCM_BOUNDARY_DETAILS",
            "HCM_DELIVERY_DETAILS",
            "HCM_TARGETS",
            "HCM_FACILITY_DETAILS",
            "HCM_USER_DETAILS",
            "HCM_REVIEW_DETAILS",
          ]}
          currentStep={currentStep + 1}
          onStepClick={onStepClick}
          activeSteps={active}
        />
      )}
      <FormComposerV2
        config={config?.form.map((config) => {
          return {
            ...config,
            body: config?.body.filter((a) => !a.hideInEmployee),
          };
        })}
        onSubmit={onSubmit}
        showSecondaryLabel={currentKey > 1 ? true : false}
        secondaryLabel={noAction === "false" ? null : t("HCM_BACK")}
        actionClassName={"actionBarClass"}
        className="setup-campaign"
        cardClassName="setup-campaign-card"
        noCardStyle={currentKey === 4 || currentStep === 7 || currentStep === 0 ? false : true}
        onSecondayActionClick={onSecondayActionClick}
        label={noAction === "false" ? null : filteredConfig?.[0]?.form?.[0]?.isLast === true ? t("HCM_SUBMIT") : t("HCM_NEXT")}
      />
      {showToast && (
        <Toast
          type={showToast?.key === "error" ? "error" : showToast?.key === "info" ? "info" : showToast?.key === "warning" ? "warning" : "success"}
          // info={showToast?.key === "info" ? true : false}
          // error={showToast?.key === "error" ? true : false}
          label={t(showToast?.label)}
          transitionTime={showToast.transitionTime}
          onClose={closeToast}
        />
      )}
    </React.Fragment>
  );
};

export default SetupCampaign;
