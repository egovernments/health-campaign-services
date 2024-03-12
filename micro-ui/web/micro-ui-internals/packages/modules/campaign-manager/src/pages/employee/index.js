import React, { useEffect } from "react";
import { Switch, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PrivateRoute, AppContainer, BreadCrumb } from "@egovernments/digit-ui-react-components";
// import CampaignHeader from "../../components/CampaignHeader";

const CampaignBreadCrumb = ({ location, defaultPath }) => {
  const { t } = useTranslation();
  const search = useLocation().search;
  const pathVar = location.pathname.replace(defaultPath + "/", "").split("?")?.[0];

  const crumbs = [
    {
      path: `/${window?.contextPath}/employee`,
      content: t("CAMPAIGN_HOME"),
      show: true,
    },
  ];

  return <BreadCrumb className="campaign-bredcrumb" crumbs={crumbs} spanStyle={{ maxWidth: "min-content" }} />;
};

const App = ({ path }) => {
  const location = useLocation();
  const UploadBoundaryData = Digit?.ComponentRegistryService?.getComponent("UploadBoundaryData");

  return (
    <React.Fragment>
      <div className="wbh-header-container">
        <CampaignBreadCrumb location={location} defaultPath={path} />
        {/* <CampaignHeader /> */}
      </div>
      <Switch>
        <AppContainer className="campaign">
          <PrivateRoute path={`${path}/create-campaign/upload-boundary-data`} component={() => <UploadBoundaryData />} />
          <PrivateRoute path={`${path}/sample`} component={() => <div>Home Campaign Loaded</div>} />
        </AppContainer>
      </Switch>
    </React.Fragment>
  );
};

export default App;
