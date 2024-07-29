import { FormComposerV2 } from "@egovernments/digit-ui-react-components";
import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useLocation } from "react-router-dom";
import { dateChangeBoundaryConfig, dateChangeConfig } from "../../configs/dateChangeBoundaryConfig";
import { Button, PopUp, Toast } from "@egovernments/digit-ui-components";

function UpdateDatesWithBoundaries() {
  const { t } = useTranslation();
  const history = useHistory();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const [showToast, setShowToast] = useState(null);
  const { state } = useLocation();
  const [showPopUp, setShowPopUp] = useState(null);
  const { isLoading: DateWithBoundaryLoading, data: DateWithBoundary } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "dateWithBoundary" }],
    {
      select: (data) => {
        return data?.["HCM-ADMIN-CONSOLE"]?.dateWithBoundary?.[0]?.dateWithBoundary;
      },
    }
  );
  const closeToast = () => {
    setShowToast(null);
  };

  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);

  const onSubmit = async (formData) => {
    if (!formData?.dateWithBoundary || formData?.dateWithBoundary?.length === 0) {
      setShowToast({ isError: true, label: "SELECT_BOUNDARY_LEVEL_ERROR" });
      return;
    }
    setShowPopUp(formData);
  };

  const onConfirm = async (formData) => {
    setShowPopUp(null);
    try {
      if (DateWithBoundary) {
        const temp = await Digit.Hooks.campaign.useProjectUpdateWithBoundary({ formData: formData?.dateWithBoundary });
        // setShowToast({ isError: false, label: "DATE_UPDATED_SUCCESSFULLY" });
        history.push(`/${window.contextPath}/employee/campaign/response?isSuccess=${true}`, {
          message: t("ES_CAMPAIGN_DATE_CHANGE_WITH_BOUNDARY_SUCCESS"),
          // text: t("ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE_TEXTKK"),
          // info: t("ES_CAMPAIGN_SUCCESS_INFO_TEXTKK"),
          actionLabel: t("HCM_DATE_CHANGE_SUCCESS_RESPONSE_ACTION"),
          actionLink: `/${window.contextPath}/employee/campaign/my-campaign`,
        });
      } else {
        const res = await Digit.CustomService.getResponse({
          url: "/health-project/v1/_update",
          body: {
            Projects: [formData?.dateAndCycle],
          },
        });
        // setShowToast({ isError: false, label: "DATE_UPDATED_SUCCESSFULLY" });
        history.push(`/${window.contextPath}/employee/campaign/response?isSuccess=${true}`, {
          message: t("ES_CAMPAIGN_DATE_CHANGE_SUCCESS"),
          // text: t("ES_CAMPAIGN_CREATE_SUCCESS_RESPONSE_TEXTKK"),
          // info: t("ES_CAMPAIGN_SUCCESS_INFO_TEXTKK"),
          actionLabel: t("HCM_DATE_CHANGE_SUCCESS_RESPONSE_ACTION"),
          actionLink: `/${window.contextPath}/employee/campaign/my-campaign`,
        });
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
      {showPopUp && (
        <PopUp
          className={"boundaries-pop-module"}
          type={"default"}
          heading={t("ES_CAMPAIGN_CHANGE_DATE_CONFIRM")}
          children={[]}
          onOverlayClick={() => {
            setShowPopUp(false);
          }}
          footerChildren={[
            <Button
              type={"button"}
              size={"large"}
              variation={"secondary"}
              label={t("NO")}
              onClick={() => {
                setShowPopUp(null);
              }}
            />,
            <Button
              type={"button"}
              size={"large"}
              variation={"primary"}
              label={t("YES")}
              onClick={() => {
                onConfirm(showPopUp);
              }}
            />,
          ]}
          sortFooterChildren={true}
        ></PopUp>
      )}
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
