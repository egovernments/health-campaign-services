import { Loader, FormComposerV2 } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useParams } from "react-router-dom";
import { Toast } from "@egovernments/digit-ui-components";
import _ from "lodash";
import { ConfigureAppConfig } from "../../configs/ConfigureAppConfig";

/**
 * The `SetupCampaign` function in JavaScript handles the setup and management of campaign details,
 * including form data handling, validation, and submission.
 * @returns The `SetupCampaign` component is being returned. It consists of a form setup for creating
 * or updating a campaign with multiple steps like campaign details, delivery details, boundary
 * details, targets, facility details, user details, and review details. The form data is validated at
 * each step, and the user can navigate between steps using a stepper component. The form submission
 * triggers API calls to create or update the campaign
 */

// Example usage:
// updateUrlParams({ id: 'sdjkhsdjkhdshfsdjkh', anotherParam: 'value' });
function updateUrlParams(params) {
  const url = new URL(window.location.href);
  Object.entries(params).forEach(([key, value]) => {
    url.searchParams.set(key, value);
  });
  window.history.replaceState({}, "", url);
}

const ConfigureApp = ({ hierarchyType }) => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { t } = useTranslation();
  const history = useHistory();
  const [totalFormData, setTotalFormData] = useState({});
  const [campaignConfig, setCampaignConfig] = useState(ConfigureAppConfig(totalFormData));
  const [showToast, setShowToast] = useState(null);
  const searchParams = new URLSearchParams(location.search);
  const [currentKey, setCurrentKey] = useState(() => {
    const keyParam = searchParams.get("key");
    return keyParam ? parseInt(keyParam) : 1;
  });

  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 10000);
    }
  }, [showToast]);

  const onSubmit = (formData, cc) => {

    if (!filteredConfig?.[0]?.form?.[0]?.isLast && !filteredConfig[0].form[0].body[0].mandatoryOnAPI) {
      setCurrentKey(currentKey + 1);
    }
    return;
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

  useEffect(() => {
    // setCurrentStep(Number(filteredConfig?.[0]?.form?.[0]?.stepCount - 1));
    setShowToast(null);
  }, [currentKey]);

  useEffect(() => {
      updateUrlParams({ key: currentKey });
  }, [currentKey]);

  const closeToast = () => {
    setShowToast(null);
  };

  return (
    <React.Fragment>
      <FormComposerV2
        config={config?.form.map((config) => {
          return {
            ...config,
            body: config?.body.filter((a) => !a.hideInEmployee),
          };
        })}
        onSubmit={onSubmit}
        showSecondaryLabel={currentKey > 1}
        secondaryLabel={t("HCM_BACK")}
        actionClassName={"actionBarClass"}
        className="setup-campaign"
        showFormInNav={true}
        // cardClassName="setup-campaign-card"
        // noCardStyle={currentKey === 4 || currentStep === 7 || currentStep === 0 ? false : true}
        onSecondayActionClick={onSecondayActionClick}
        label={filteredConfig?.[0]?.form?.[0]?.isLast === true ? t("HCM_SUBMIT") : t("HCM_NEXT")}
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

export default ConfigureApp;
