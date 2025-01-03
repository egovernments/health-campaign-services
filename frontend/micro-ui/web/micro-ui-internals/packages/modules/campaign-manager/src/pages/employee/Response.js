import React, { useState, Fragment } from "react";
import { Link, useHistory, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ActionBar, SubmitBar } from "@egovernments/digit-ui-react-components";
import { PanelCard } from "@egovernments/digit-ui-components";

const Response = () => {
  const { t } = useTranslation();
  const history = useHistory();
  const queryStrings = Digit.Hooks.useQueryParams();
  const [campaignId, setCampaignId] = useState(queryStrings?.campaignId);
  const [isResponseSuccess, setIsResponseSuccess] = useState(
    queryStrings?.isSuccess === "true" ? true : queryStrings?.isSuccess === "false" ? false : true
  );
  const { state } = useLocation();
  const isMobile = window.Digit.Utils.browser.isMobile();

  const navigate = (page) => {
    switch (page) {
      case "contracts-inbox": {
        history.push(`/${window.contextPath}/employee/tqm/summary`);
      }
    }
  };

  const children = [
    <div style={{ display: "flex" }} key="response-text">
      {state?.boldText ? (
        <p style={{ margin: "0rem" }}>
          {t(state?.preText)}
          <b> {t(state?.boldText)} </b>
          {t(state?.postText)}
        </p>
      ) : (
        t(state?.text, { CAMPAIGN_ID: campaignId })
      )}
    </div>,
  ];

  return (
    <>
      <PanelCard
        type={isResponseSuccess ? "success" : "error"}
        message={t(state?.message)}
        response={campaignId}
        info={t(state?.info)}
        footerChildren={[]}
        children={children}
      />
      {isMobile ? (
        <Link to={state?.actionLink ? state?.actionLink : `/${window.contextPath}/employee/`}>
          <SubmitBar label={state?.actionLabel ? t(state?.actionLabel) : t("ES_CAMPAIGN_RESPONSE_ACTION")} />
        </Link>
      ) : (
        <ActionBar>
          <Link to={state?.actionLink ? state?.actionLink : `/${window.contextPath}/employee/`}>
            <SubmitBar label={state?.actionLabel ? t(state?.actionLabel) : t("ES_CAMPAIGN_RESPONSE_ACTION")} />
          </Link>
        </ActionBar>
      )}
    </>
  );
};

export default Response;
