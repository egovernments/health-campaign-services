import { Loader, FormComposerV2, Header, MultiUploadWrapper, Button, Close, LogoutIcon } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useParams } from "react-router-dom";
import { CampaignConfig } from "../../configs/CampaignConfig";
import { QueryClient, useQueryClient } from "react-query";
import { Stepper, Toast } from "@egovernments/digit-ui-components";

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
        toValue: Math.min(existingItem.value, item.value),
        fromValue: Math.max(existingItem.value, item.value),
      };
    } else if (item?.operator?.code === "EQUAL_TO") {
      newArray.push({
        ...item,
        value: {
          code: item?.value,
        },
      });
    } else {
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
      attributes: loopAndReturn(item.conditions),
      // .map((condition) => ({
      // value: condition?.value ? condition?.value : "",
      // operator: condition?.operator
      // ? {
      // code: condition.operator,
      // }
      // : null,
      // attribute: condition?.attribute
      // ? {
      // code: condition.attribute,
      // }
      // : null,
      // })),
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
    const type = item.type;
    const obj = {
      TenantBoundary: [
        {
          boundary: [item],
        },
      ],
    };

    if (result[type]) {
      result[type][0].TenantBoundary[0].boundary.push(item);
    } else {
      result[type] = [obj];
    }
  });

  return result;
}

const SetupCampaign = () => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();
  const history = useHistory();
  const [currentStep, setCurrentStep] = useState(0);
  const [totalFormData, setTotalFormData] = useState({});
  const [campaignConfig, setCampaignConfig] = useState(CampaignConfig(totalFormData));
  const [shouldUpdate, setShouldUpdate] = useState(false);
  const [params, setParams, clearParams] = Digit.Hooks.useSessionStorage("HCM_CAMPAIGN_MANAGER_FORM_DATA", {});
  const [dataParams, setDataParams] = Digit.Hooks.useSessionStorage("HCM_CAMPAIGN_MANAGER_UPLOAD_ID", {});
  const [showToast, setShowToast] = useState(null);
  const { mutate } = Digit.Hooks.campaign.useCreateCampaign(tenantId);
  const { mutate: updateCampaign } = Digit.Hooks.campaign.useUpdateCampaign(tenantId);
  const searchParams = new URLSearchParams(location.search);
  const id = searchParams.get("id");
  const isPreview = searchParams.get("preview");
  const isDraft = searchParams.get("draft");
  const isSkip = searchParams.get("skip");
  const [isDraftCreated, setIsDraftCreated] = useState(false);
  const filteredBoundaryData = params?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
  const client = useQueryClient();
  const hierarchyType = "ADMIN";
  const [currentKey, setCurrentKey] = useState(() => {
    const keyParam = searchParams.get("key");
    return keyParam ? parseInt(keyParam) : 1;
  });

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

  const { isLoading, data: projectType } = Digit.Hooks.useCustomMDMS("mz", "HCM-PROJECT-TYPES", [{ name: "projectTypes" }]);

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

  useEffect(() => {
    if (Object.keys(params).length !== 0) return;
    if (!draftData) return;
    const delivery = draftData?.campaignDetails?.deliveryRules;
    const filteredProjectType = projectType?.["HCM-PROJECT-TYPES"]?.projectTypes?.filter((i) => i.code === draftData?.projectType);
    const restructureFormData = {
      HCM_CAMPAIGN_TYPE: { projectType: filteredProjectType?.[0] },
      HCM_CAMPAIGN_NAME: {
        campaignName: draftData?.campaignName,
      },
      HCM_CAMPAIGN_DATE: {
        campaignDates: {
          startDate: draftData?.startDate ? new Date(draftData?.startDate)?.toISOString()?.split("T")?.[0] : null,
          endDate: draftData?.endDate ? new Date(draftData?.endDate)?.toISOString()?.split("T")?.[0] : null,
        },
      },
      HCM_CAMPAIGN_CYCLE_CONFIGURE: {
        cycleConfigure: {
          cycleConfgureDate: {
            cycle: delivery?.map((obj) => obj?.cycleNumber) ? Math.max(...delivery?.map((obj) => obj?.cycleNumber)) : 1,
            deliveries: delivery?.map((obj) => obj?.deliveryNumber) ? Math.max(...delivery?.map((obj) => obj?.deliveryNumber)) : 1,
          },
          cycleData: cycleDataRemap(delivery),
        },
      },
      HCM_CAMPAIGN_DELIVERY_DATA: {
        deliveryRule: reverseDeliveryRemap(delivery),
      },
      HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA: {
        boundaryType: {
          boundaryData: groupByTypeRemap(draftData?.boundaries),
        },
        selectedData: draftData?.boundaries,
      },
      HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA: {
        uploadBoundary: draftData?.campaignDetails?.resources?.filter((i) => i?.type === "boundary"),
      },
      HCM_CAMPAIGN_UPLOAD_FACILITY_DATA: {
        uploadFacility: draftData?.campaignDetails?.resources?.filter((i) => i?.type === "facility"),
      },
      HCM_CAMPAIGN_UPLOAD_USER_DATA: {
        uploadUser: draftData?.campaignDetails?.resources?.filter((i) => i?.type === "user"),
      },
    };
    setParams({ ...restructureFormData });
  }, [params, draftData]);

  const facilityId = Digit.Hooks.campaign.useGenerateIdCampaign("facilityWithBoundary", hierarchyType);
  const boundaryId = Digit.Hooks.campaign.useGenerateIdCampaign("boundary", hierarchyType, filteredBoundaryData);
  const userId = Digit.Hooks.campaign.useGenerateIdCampaign("facilityWithBoundary", hierarchyType); // to be integrated later

  useEffect(() => {
    setDataParams({
      ...dataParams,
      facilityId: facilityId,
      boundaryId: boundaryId,
      hierarchyType: hierarchyType,
    });
  }, [facilityId, boundaryId]); // Only run if dataParams changes

  // Example usage:
  // updateUrlParams({ id: 'sdjkhsdjkhdshfsdjkh', anotherParam: 'value' });
  function updateUrlParams(params) {
    const url = new URL(window.location.href);
    Object.entries(params).forEach(([key, value]) => {
      url.searchParams.set(key, value);
    });
    window.history.replaceState({}, "", url);
  }

  useEffect(() => {
    setCampaignConfig(CampaignConfig(totalFormData));
  }, [totalFormData]);

  useEffect(() => {
    const convertFormData = (totalFormData) => {
      const modifiedData = [
        {
          startDate: totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate,
          endDate: totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate,
          projectType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType.code,
          campaignName: totalFormData?.HCM_CAMPAIGN_NAME?.campaignName,
        },
      ];
    };
    convertFormData(totalFormData);
  }, [totalFormData]);

  // function to convert payload to formData
  const convertPayload = (dummyData) => {
    return {
      1: {},
      2: {
        projectType: dummyData?.projectType,
      },
      3: {
        campaignName: dummyData?.campaignName,
      },
      4: {
        boundaries: dummyData?.boundaries,
      },
    };
  };

  useEffect(() => {
    updateUrlParams({ key: currentKey });
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
            deliveryRuleNumber: parseInt(rule.ruleKey), // New key added
            products: [],
            conditions: [],
          };

          rule.attributes.forEach((attribute) => {
            if (attribute?.operator?.code === "IN_BETWEEN") {
              restructuredRule.conditions.push({
                attribute: attribute.attribute.code,
                operator: "LESS_THAN",
                value: Number(attribute.fromValue),
              });
              attribute;
              restructuredRule.conditions.push({
                attribute: attribute.attribute.code,
                operator: "GREATER_THAN",
                value: Number(attribute.toValue),
              });
            } else {
              restructuredRule.conditions.push({
                attribute: attribute.attribute ? attribute.attribute.code : null,
                operator: attribute.operator ? attribute.operator.code : null,
                value: attribute?.attribute?.code === "Gender" ? attribute?.value : Number(attribute?.value),
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

  useEffect(async () => {
    if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
      const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
    }
    if (totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA) {
      const FacilityTemp = await Digit.Hooks.campaign.useResourceData(totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA, hierarchyType, "facility");
      setDataParams({
        ...dataParams,
        ValidateFacilityId: FacilityTemp?.ResourceDetails?.id,
      });
    }
    if (totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA) {
      const UserTemp = await Digit.Hooks.campaign.useResourceData(totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA, hierarchyType, "user");
      setDataParams({
        ...dataParams,
        ValidateUserId: UserTemp?.ResourceDetails?.id,
      });
    }
  }, [shouldUpdate]);
  
  useEffect(async () => {
    // if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
    //   const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
    // }
    // if (totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA) {
    //   const FacilityTemp = await Digit.Hooks.campaign.useResourceData(totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA, hierarchyType, "facility");
    //   setDataParams({
    //     ...dataParams,
    //     ValidateFacilityId: FacilityTemp?.ResourceDetails?.id,
    //   });
    // }
    // if (totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA) {
    //   const UserTemp = await Digit.Hooks.campaign.useResourceData(totalFormData?.HCM_CAMPAIGN_UPLOAD_USER_DATA, hierarchyType, "user");
    //   setDataParams({
    //     ...dataParams,
    //     ValidateUserId: UserTemp?.ResourceDetails?.id,
    //   });
    // }
    if (shouldUpdate === true) {
      if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.skipAPICall) {
        return;
      } else if (filteredConfig?.[0]?.form?.[0]?.isLast) {
        const reqCreate = async () => {
          let payloadData = {};
          payloadData.startDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate)
            : null;
          payloadData.endDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate)
            : null;
          payloadData.tenantId = tenantId;
          payloadData.action = "create";
          payloadData.campaignName = totalFormData?.HCM_CAMPAIGN_NAME?.campaignName;
          payloadData.boundaries = totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
          payloadData.resources = [totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.[0]];
          payloadData.projectType = totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.code;
          payloadData.additionalDetails = {
            beneficiaryType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType,
            key: currentKey,
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
          await updateCampaign(payloadData, {
            onError: (error, variables) => {
              console.log(error);
              setShowToast({ key: "error", label: error });
            },
            onSuccess: async (data) => {
              draftRefetch();
              history.push(
                `/${window.contextPath}/employee/campaign/response?campaignId=${data?.CampaignDetails?.campaignNumber}&isSuccess=${true}`,
                {
                  message: "ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE",
                  text: "ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE_TEXT",
                }
              );
              Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
            },
          });
        };

        reqCreate();
      } else if (!isDraftCreated && !id) {
        const reqCreate = async () => {
          let payloadData = {};
          payloadData.startDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate)
            : null;
          payloadData.endDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate)
            : null;
          payloadData.tenantId = tenantId;
          payloadData.action = "draft";
          payloadData.campaignName = totalFormData?.HCM_CAMPAIGN_NAME?.campaignName;
          payloadData.boundaries = totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
          payloadData.resources = [totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.[0]];
          payloadData.projectType = totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.code;
          payloadData.additionalDetails = {
            beneficiaryType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType,
            key: currentKey,
          };
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);

            payloadData.deliveryRules = temp;
          }

          await mutate(payloadData, {
            onError: (error, variables) => {
              if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.mandatoryOnAPI) {
                setShowToast({ key: "error", label: error });
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
          let payloadData = draftData;
          payloadData.startDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate)
            : null;
          payloadData.endDate = totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate
            ? Digit.Utils.date.convertDateToEpoch(totalFormData?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate)
            : null;
          payloadData.tenantId = tenantId;
          payloadData.action = "draft";
          payloadData.campaignName = totalFormData?.HCM_CAMPAIGN_NAME?.campaignName;
          payloadData.boundaries = totalFormData?.HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA?.boundaryType?.selectedData;
          payloadData.resources = [totalFormData?.HCM_CAMPAIGN_UPLOAD_FACILITY_DATA?.uploadFacility?.[0]];
          payloadData.projectType = totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.code;
          payloadData.additionalDetails = {
            beneficiaryType: totalFormData?.HCM_CAMPAIGN_TYPE?.projectType?.beneficiaryType,
            key: currentKey,
          };
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          }

          await updateCampaign(payloadData, {
            onError: (error, variables) => {
              console.log(error);
              if (filteredConfig?.[0]?.form?.[0]?.body?.[0]?.mandatoryOnAPI) {
                setShowToast({ key: "error", label: error });
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
        };

        reqCreate();
      }
      setShouldUpdate(false);
    }
  }, [shouldUpdate]);

  function validateCycleData(data) {
    const { cycle, deliveries } = data?.cycleConfigure?.cycleConfgureDate;
    const cycleData = data.cycleConfigure.cycleData;

    // Validate cycle and deliveries
    if (cycle <= 0 || deliveries <= 0) {
      return { error: true, message: "Cycle and deliveries should be greater than 0" };
    }

    // Validate cycleData length
    if (cycleData.length !== cycle) {
      return { error: true, message: "Cycle data length should be equal to cycle" };
    }

    // Validate fromDate and startDate in cycleData
    for (const item of cycleData) {
      if (!item.fromDate || !item.toDate) {
        return { error: true, message: "From date and start date should not be empty in cycle data" };
      }
    }

    return false;
  }

  function validateDeliveryRules(data) {
    let isValid = true;

    // Iterate over deliveryRule array
    data.deliveryRule.forEach((cycle) => {
      cycle.deliveries.forEach((delivery) => {
        delivery.deliveryRules.forEach((rule) => {
          // Validate attributes and products length
          if (rule.attributes.length === 0 || rule.products.length === 0) {
            isValid = false;
            return;
          }

          rule.attributes.forEach((attribute) => {
            // Check if attribute, operator, and value are empty
            if (attribute.attribute === "" || attribute.operator === null || attribute.value === "") {
              if (attribute?.operator?.code === "IN_BETWEEN" && attribute?.toValue !== "" && attribute?.fromValue !== "") {
                isValid = true;
              } else {
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
          return true;
        }
      case "projectType":
        if (!formData?.projectType) {
          setShowToast({ key: "error", label: "PROJECT_TYPE_UNDEFINED_ERROR" });
          return false;
        } else {
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
          return true;
        }
      case "cycleConfigure":
        const cycleNumber = formData?.cycleConfigure?.cycleConfgureDate?.cycle;
        const deliveryNumber = formData?.cycleConfigure?.cycleConfgureDate?.deliveries;
        if (cycleNumber === "" || cycleNumber === 0 || deliveryNumber === "" || deliveryNumber === 0) {
          setShowToast({ key: "error", label: "DELIVERY_CYCLE_ERROR" });
          return false;
        } else {
          return true;
        }
      case "deliveryRule":
        const isAttributeValid = checkAttributeValidity(formData);
        if (isAttributeValid) {
          setShowToast({ key: "error", label: isAttributeValid });
          return false;
        }
        return;
      case "summary":
        const cycleConfigureData = totalFormData?.HCM_CAMPAIGN_CYCLE_CONFIGURE;
        const isCycleError = validateCycleData(cycleConfigureData);
        if (isCycleError?.error === true) {
          setShowToast({ key: "error", label: isCycleError?.message });
          return false;
        }
        const deliveryCycleData = totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA;
        const isDeliveryError = validateDeliveryRules(deliveryCycleData);
        if (isDeliveryError === false) {
          setShowToast({ key: "error", label: "DELIVERY_RULES_ERROR" });
          return false;
        }
        return true;
      default:
        break;
    }
  };

  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);

  const onSubmit = (formData, cc) => {
    const checkValid = handleValidate(formData);
    if (checkValid === false) {
      return;
    }

    const name = filteredConfig?.[0]?.form?.[0]?.name;

    setTotalFormData((prevData) => ({
      ...prevData,
      [name]: formData,
    }));
    //to set the data in the local storage
    setParams({
      ...params,
      [name]: { ...formData },
    });

    if (filteredConfig?.[0]?.form?.[0]?.isLast || !filteredConfig[0].form[0].body[0].skipAPICall) {
      setShouldUpdate(true);
    }

    if (!filteredConfig?.[0]?.form?.[0]?.isLast && !filteredConfig[0].form[0].body[0].mandatoryOnAPI) {
      setCurrentKey(currentKey + 1);
    }
    if (isDraft === "true" && isSkip !== "false") {
      updateUrlParams({ skip: "false" });
    }
  };

  const onStepClick = (step) => {
    const filteredSteps = campaignConfig[0].form.filter((item) => item.stepCount === String(step + 1));

    const key = parseInt(filteredSteps[0].key);
    const name = filteredSteps[0].name;

    if (Object.keys(totalFormData).includes(name)) {
      setCurrentKey(key);
      setCurrentStep(step);
    }
  };

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
  }, [currentKey, filteredConfig]);

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
      <Stepper
        customSteps={[
          "HCM_CAMPAIGN_SETUP_DETAILS",
          "HCM_DELIVERY_DETAILS",
          "HCM_BOUNDARY_DETAILS",
          "HCM_TARGETS",
          "HCM_FACILITY_DETAILS",
          "HCM_USER_DETAILS",
          "HCM_REVIEW_DETAILS",
        ]}
        currentStep={currentStep + 1}
        onStepClick={onStepClick}
      />
      <FormComposerV2
        config={config?.form.map((config) => {
          return {
            ...config,
            body: config?.body.filter((a) => !a.hideInEmployee),
          };
        })}
        onSubmit={onSubmit}
        showSecondaryLabel={currentKey > 1 ? true : false}
        secondaryLabel={t("HCM_BACK")}
        actionClassName={"actionBarClass"}
        className="setup-campaign"
        noCardStyle={currentStep === 1 || currentStep === 6 || currentStep === 2 ? true : false}
        onSecondayActionClick={onSecondayActionClick}
        label={filteredConfig?.[0]?.form?.[0]?.isLast === true ? t("HCM_SUBMIT") : t("HCM_NEXT")}
      />
      {showToast && <Toast error={showToast.key === "error" ? true : false} label={t(showToast.label)} onClose={closeToast} />}
    </React.Fragment>
  );
};

export default SetupCampaign;
