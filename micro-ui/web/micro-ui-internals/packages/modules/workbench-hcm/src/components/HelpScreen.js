import { Card, CardLabel, Header, Loader } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useTranslation } from "react-i18next";
import { useHistory, useParams } from "react-router-dom";

const DIGIT_UI_CONTEXTS = ["digit-ui", "works-ui", "workbench-ui", "health-ui", "sanitation-ui", "core-ui"];

const navigateToRespectiveURL = (history = {}, url = "") => {
  if (url?.indexOf(`/${window?.contextPath}`) === -1) {
    const hostUrl = window.location.origin;
    const updatedURL = DIGIT_UI_CONTEXTS?.every((e) => url?.indexOf(`/${e}`) === -1) ? hostUrl + "/employee/" + url : hostUrl + url;
    window.location.href = updatedURL;
  } else {
    history.push(url);
  }
};
const HelpScreen = () => {
  const { t } = useTranslation();
  const history = useHistory();
  const { screen } = useParams();

  const tenantId = Digit.UserService.getUser()?.info?.tenantId;
  const MdmsCriteria = {
    tenantId: tenantId,
    filters: {},
    schemaCode: "commonHCMUiConfig.HelpScreen",
    limit: 10,
    offset: 0,
    uniqueIdentifiers: [screen],
  };

  const { isLoading, data: Schemadata } = Digit.Hooks.useCustomAPIHook({
    url: `/${Digit.Hooks.workbench.getMDMSContextPath()}/v2/_search`,
    body: {
      MdmsCriteria,
    },
  });

  if (isLoading) {
    return <Loader />;
  }

  const RenderMessageCard = () => {
    const mdmsData = Schemadata?.mdms || [];

    return (
      <div className="help-screen-container">
        {mdmsData
          ?.filter((e) => e.isActive)
          ?.map((ele) => {
            const types = ele.data.type === "mail";
            return (
              <div className="help-screen-body">
                <Header> {t(Digit.Utils.locale.getTransformedLocale(`${screen}_${ele.data.screen}`))}</Header>
                <CardLabel>{t(ele.data.message)}</CardLabel>
                {types ? (
                  <a href={`mailto:${ele.data.link}?subject=${ele.data.screen}`}>{t(Digit.Utils.locale.getTransformedLocale(`${screen}_LINK`))}</a>
                ) : (
                  <a
                    onClick={() => {
                      navigateToRespectiveURL(history, `${ele.data.link}`);
                    }}
                  >
                    {t(Digit.Utils.locale.getTransformedLocale(`${screen}_LINK`))}
                  </a>
                )}
              </div>
            );
          })}
      </div>
    );
  };
  return (
    <div className="override-card">
      <Header className="works-header-view">{t("WORKBENCH_HELP_SCREEN")}</Header>
      <Card> {RenderMessageCard()}</Card>
    </div>
  );
};

export default HelpScreen;
