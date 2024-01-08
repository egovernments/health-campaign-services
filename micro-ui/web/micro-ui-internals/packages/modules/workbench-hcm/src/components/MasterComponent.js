import { Card, Header, Loader, SVG } from "@egovernments/digit-ui-react-components";
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
const MasterComponent = () => {
  const { t } = useTranslation();
  const { screen } = useParams();
  const history = useHistory();

  const tenantId = Digit.UserService.getUser()?.info?.tenantId;
  const MdmsCriteria = {
    tenantId: tenantId,
    filters: {},
    schemaCode: "commonHCMUiConfig.MasterLandingScreen",
    uniqueIdentifiers: [screen],
    limit: 10,
    offset: 0,
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

  const RenderCard = () => {
    const mdmsData = Schemadata?.mdms || [];

    return (
      <div className="master-container">
        {mdmsData.length > 0 &&
          mdmsData
            ?.filter((e) => e.isActive)?.[0]
            .data?.links?.map((ele, index) => {
              const icon = ele.icon;

              let IconComp = require("@egovernments/digit-ui-svg-components")?.[icon];
              IconComp = IconComp ? <IconComp /> : <SVG.Work />;
              return (
                <div className="master-card" key={index}>
                  <Card className="custom-master">
                    <a
                      onClick={() => {
                        navigateToRespectiveURL(history, `${ele.url}`);
                      }}
                    >
                      <div className="master-icon">{IconComp}</div>
                      {t(Digit.Utils.locale.getTransformedLocale(`${screen}_${ele.code}`))}
                    </a>
                  </Card>
                </div>
              );
            })}
      </div>
    );
  };

  return (
    <div className="override-card">
      <Header className="works-header-view">{screen === "master-landing-screen" ? t("WORKBENCH_MASTER") : t("WORKBENCH_USER")}</Header>
      {RenderCard()}
    </div>
  );
};

export default MasterComponent;
