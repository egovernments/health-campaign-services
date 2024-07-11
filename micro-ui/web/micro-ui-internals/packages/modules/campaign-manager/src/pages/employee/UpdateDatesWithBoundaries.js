import { Loader, FormComposerV2, Header } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { dateChangeBoundaryConfig, dateChangeConfig } from "../../configs/dateChangeBoundaryConfig";
import { Toast } from "@egovernments/digit-ui-components";
import { isError } from "lodash";

function UpdateDatesWithBoundaries() {
  const { t } = useTranslation();
  const history = useHistory();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [showToast, setShowToast] = useState(null);
  const { state } = useLocation();
  const DateWithBoundary = true;

  const closeToast = () => {
    setShowToast(null);
  };

  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);

  const onSubmit = async (formData) => {
    try {
      if (DateWithBoundary) {
        const temp = await Digit.Hooks.campaign.useProjectUpdateWithBoundary({ formData: formData?.dateWithBoundary });
        setShowToast({ isError: false, label: "DATE_UPDATED_SUCCESSFULLY" });
      } else {
        const res = await Digit.CustomService.getResponse({
          url: "/health-project/v1/_update",
          body: {
            Projects: [formData?.dateAndCycle],
          },
        });
        setShowToast({ isError: false, label: "DATE_UPDATED_SUCCESSFULLY" });
      }
    } catch (error) {
      setShowToast({ isError: true, label: error?.response?.data?.Errors?.[0]?.message ? error?.response?.data?.Errors?.[0]?.message : error });
    }
  };
  const onFormValueChange = (setValue, formData, formState, reset, setError, clearErrors, trigger, getValues) => {
    return;
  };

  return (
    <div>
      <FormComposerV2
        label={t("CAMPAIGN_UPDATE_DATE_SUBMIT")}
        config={
          DateWithBoundary
            ? dateChangeBoundaryConfig?.map((config) => {
                return {
                  ...config,
                };
              })
            : dateChangeConfig?.map((config) => {
                return {
                  ...config,
                };
              })
        }
        onSubmit={onSubmit}
        fieldStyle={{ marginRight: 0 }}
        noBreakLine={true}
        className="date-update"
        cardClassName={"date-update-card"}
        onFormValueChange={onFormValueChange}
        actionClassName={"dateUpdateAction"}
        noCardStyle={true}
      />

      {showToast && (
        <Toast
          type={showToast?.isError ? "error" : "success"}
          // error={showToast?.isError}
          label={t(showToast?.label)}
          isDleteBtn={"true"}
          onClose={() => setShowToast(false)}
        />
      )}
    </div>
  );
}

export default UpdateDatesWithBoundaries;
