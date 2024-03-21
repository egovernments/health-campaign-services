import { Loader, FormComposerV2, Header, Toast, MultiUploadWrapper, Button, Close, LogoutIcon } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory } from "react-router-dom";
import TimelineCampaign from "../../components/TimelineCampaign";
import { CampaignConfig } from "../../configs/CampaignConfig";

const SetupCampaign = () => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();
  const history = useHistory();
  const [currentStep, setCurrentStep] = useState(0);
  const [currentKey, setCurrentKey] = useState(1);
  const [totalFormData, setTotalFormData] = useState({});
  const [campaignConfig, setCampaignConfig] = useState(CampaignConfig(totalFormData));
  const [params, setParams, clearParams] = Digit.Hooks.useSessionStorage("HCM_CAMPAIGN_MANAGER_FORM_DATA", {});

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

  const onSubmit = (formData) => {
    setCurrentKey(currentKey + 1);
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

    // convertFormData(totalFormData);
    const payload = convertPayload(dummyData);
  };

  const onStepClick = (step) => {
    const filteredSteps = campaignConfig[0].form.filter((item) => item.stepCount === String(step + 1));

    const key = parseInt(filteredSteps[0].key);
    // setCurrentKey(key);
    // setCurrentStep(step);

    if (Object.keys(totalFormData).includes(key.toString())) {
      setCurrentKey(key);
      setCurrentStep(step);
    }
  };

  const onSecondayActionClick = () => {
    if (currentKey > 1) {
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
    return campaignConfig.map(config => {
      return {
        ...config,
        form: config?.form.filter(step => parseInt(step.key) === currentKey),
      };
    }).filter(config => config.form.length > 0);

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

  return (
    <React.Fragment>
      <TimelineCampaign currentStep={currentStep + 1} onStepClick={onStepClick} />
      <FormComposerV2
        config={config?.form.map((config) => {
          return {
            ...config,
            body: config?.body.filter((a) => !a.hideInEmployee),
          };
        })}
        onSubmit={onSubmit}
        showSecondaryLabel={currentKey > 1 ? true : false}
        secondaryLabel={"PREVIOUS"}
        noCardStyle={currentStep == 1 ? true : false}
        onSecondayActionClick={onSecondayActionClick}
        label={currentKey < 10 ? "NEXT" : "SUBMIT"}
      />
    </React.Fragment>
  );
};

export default SetupCampaign;
