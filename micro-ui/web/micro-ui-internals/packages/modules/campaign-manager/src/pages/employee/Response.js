import React, { useState } from "react";
import { Link, useHistory, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Banner, Card, LinkLabel, ArrowLeftWhite, ActionBar, SubmitBar } from "@egovernments/digit-ui-react-components";

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

  return (
    <Card>
      <Banner
        successful={isResponseSuccess}
        message={t(state?.message)}
        multipleResponseIDs={[campaignId]}
        info={t(state?.info)}
        whichSvg={`${isResponseSuccess ? "tick" : null}`}
      />
      <Card style={{ border: "none", boxShadow: "none", padding: "0", paddingBottom: "1rem" }}>
        <div style={{ display: "flex", marginBottom: "0.75rem" }}> {t(state?.text, { CAMPAIGN_ID: campaignId })} </div>
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
      </Card>
    </Card>
  );
};

export default Response;
