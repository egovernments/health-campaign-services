import { Loader, FormComposerV2, Header, MultiUploadWrapper, Button, Close, LogoutIcon } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useParams } from "react-router-dom";
import TimelineCampaign from "../../components/TimelineCampaign";
import { CampaignConfig } from "../../configs/CampaignConfig";
import { QueryClient, useQueryClient } from "react-query";
import { Stepper, Toast } from "@egovernments/digit-ui-components";

const SetupCampaign = () => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();
  const history = useHistory();
  const [currentStep, setCurrentStep] = useState(0);
  const [currentKey, setCurrentKey] = useState(1);
  const [totalFormData, setTotalFormData] = useState({});
  const [campaignConfig, setCampaignConfig] = useState(CampaignConfig(totalFormData));
  const [shouldUpdate, setShouldUpdate] = useState(false);
  const [params, setParams, clearParams] = Digit.Hooks.useSessionStorage("HCM_CAMPAIGN_MANAGER_FORM_DATA", {});
  const [showToast, setShowToast] = useState(null);
  const { mutate } = Digit.Hooks.campaign.useCreateCampaign(tenantId);
  const { mutate: updateCampaign } = Digit.Hooks.campaign.useUpdateCampaign(tenantId);
  const searchParams = new URLSearchParams(location.search);
  const id = searchParams.get("id");
  const [isDraftCreated, setIsDraftCreated] = useState(false);
  const client = useQueryClient();

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

  function updateUrlParams(params) {
    const url = new URL(window.location.href);
    Object.entries(params).forEach(([key, value]) => {
      url.searchParams.set(key, value);
    });
    window.history.replaceState({}, "", url);
  }
  // Example usage:
  // updateUrlParams({ id: 'sdjkhsdjkhdshfsdjkh', anotherParam: 'value' });
  useEffect(() => {
    setCampaignConfig(CampaignConfig(totalFormData));
  }, [totalFormData]);

  //to convert formData to payload
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
            restructuredRule.conditions.push({
              attribute: attribute.attribute ? attribute.attribute.code : null,
              operator: attribute.operator ? attribute.operator.code : null,
              value: parseInt(attribute.value),
            });
          });

          rule.products.forEach((prod) => {
            restructuredRule.products.push({
              value: prod?.value,
              count: prod?.count,
            });
          });

          restructuredData.push(restructuredRule);
        });
      });
    });

    return restructuredData;
  }

  useEffect(() => {
    if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
      const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
    }
    if (shouldUpdate === true) {
      if (filteredConfig?.[0]?.form?.[0]?.isLast) {
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
          payloadData.resources = [];
          payloadData.projectType = null;
          payloadData.additionalDetails = {};
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          }

          await mutate(payloadData, {
            onError: (error, variables) => {
            },
            onSuccess: async (data) => {
              draftRefetch();
              Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
              history.push(
                `/${window.contextPath}/employee/campaign/response?campaignId=${data?.CampaignDetails?.campaignNumber}&isSuccess=${true}`,
                {
                  message: "ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE",
                  text: "ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE_TEXT",
                }
              );
            },
          });
        };

        reqCreate();
      } else if (!isDraftCreated) {
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
          payloadData.resources = [];
          payloadData.projectType = null;
          payloadData.additionalDetails = {};
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          }

          await mutate(payloadData, {
            onError: (error, variables) => {
              console.log(error);
            },
            onSuccess: async (data) => {
              updateUrlParams({ id: data?.CampaignDetails?.id });
              setIsDraftCreated(true);
              draftRefetch();
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
          payloadData.resources = [];
          payloadData.projectType = null;
          payloadData.additionalDetails = {};
          if (totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule) {
            const temp = restructureData(totalFormData?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule);
            payloadData.deliveryRules = temp;
          }

          await updateCampaign(payloadData, {
            onError: (error, variables) => {
              console.log(error);
            },
            onSuccess: async (data) => {
              updateUrlParams({ id: data?.CampaignDetails?.id });
              draftRefetch();
            },
          });
        };

        reqCreate();
      }
      setShouldUpdate(false);
    }
  }, [shouldUpdate, totalFormData, currentKey]);

  function validateCycleData(data) {
    const { cycle, deliveries } = data.cycleConfigure.cycleConfgureDate;
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
            if (attribute.attribute !== "" || attribute.operator !== null || attribute.value !== "") {
              isValid = false;
            }
          });

          rule.products.forEach((product) => {
            // Check if count and value are empty
            if (product.count !== null || product.value !== null) {
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

  const handleValidate = (formData) => {
    const key = Object.keys(formData)?.[0];
    switch (key) {
      case "campaignName":
        if (typeof formData?.campaignName !== "string" || !formData?.campaignName.trim()) {
          setShowToast({ key: "error", label: "CAMPAIGN_NAME_MISSING_TYPE_ERROR" });
          return false;
        } else {
          return true;
        }
      case "campaignDates":
        const startDateObj = new Date(formData?.campaignDates?.startDate);
        const endDateObj = new Date(formData?.campaignDates?.endDate);
        if (formData?.campaignDates?.startDate && formData?.campaignDates?.endDate && endDateObj > startDateObj) {
          return true;
        } else {
          setShowToast({ key: "error", label: "CAMPAIGN_DATES_MISSING_ERROR" });
          return false;
        }
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

  const onSubmit = (formData) => {
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

    const dummyData = {
      projectType: "bednet",
      campaignName: "lln",
      boundaries: [
        {
          code: "string",
          type: "string",
        },
      ],
    };

    setShouldUpdate(true);

    console.log("filteredConfig",filteredConfig)
    if (!filteredConfig?.[0]?.form?.[0]?.isLast) {
      setCurrentKey(currentKey + 1);
    }
    // convertFormData(totalFormData);
    const payload = convertPayload(dummyData);
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
        noCardStyle={currentStep === 1 || currentStep === 6 || currentStep === 2 ? true : false}
        onSecondayActionClick={onSecondayActionClick}
        label={filteredConfig?.[0]?.form?.[0]?.isLast === true ? t("HCM_SUBMIT") : t("HCM_NEXT")}
      />
      {showToast && <Toast error={showToast.key === "error" ? true : false} label={t(showToast.label)} onClose={closeToast} />}
    </React.Fragment>
  );
};

export default SetupCampaign;
