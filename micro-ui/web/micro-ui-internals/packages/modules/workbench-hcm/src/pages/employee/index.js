import React, { useEffect } from "react";
import { Switch, useLocation, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PrivateRoute, AppContainer, BreadCrumb } from "@egovernments/digit-ui-react-components";
import DataIngestionComponent from "../../components/IngestionComponents/DataIngestionComponent";

import ErrorViewPage from "./ErrorViewPage";

import IngestionInbox from "./IngestionInbox";
import ViewProject from "./ViewProject";
import CreateCampaign from "./CreateCampaign";

import MasterComponent from "../../components/MasterComponent";
import HelpScreen from "../../components/HelpScreen";


const WorkbenchBreadCrumb = ({ location, defaultPath }) => {
  const { t } = useTranslation();
  const { screen } = useParams();

  const crumbs = [
    {
      path: `/${window?.contextPath}/employee`,
      content: t("WORKBENCH_HOME"),
      show: true,
    },
    {
      path: `/${window?.contextPath}/employee/hcmworkbench/facility`,
      content: t("WORKBENCH_FACILITY"),
      show: location.pathname.includes("/hcmworkbench/facility") ? true : false,
    },
    {
      path: `/${window?.contextPath}/employee/hcmworkbench/user`,
      content: t("WORKBENCH_USER"),
      show: location.pathname.includes("/hcmworkbench/user") ? true : false,
    },
    {
      path: `/${window?.contextPath}/employee/hcmworkbench/view`,
      content: t("WORKBENCH_VIEW"),
      show: location.pathname.includes("/hcmworkbench/view") ? true : false,

      path: `/${window?.contextPath}/employee/hcmworkbench/inbox`,
      content: t("WORKBENCH_INBOX"),
      show: location.pathname.includes("/hcmworkbench/inbox") ? true : false,
    },
    {
      path: `/${window?.contextPath}/employee/hcmworkbench/boundary`,
      content: t("WORKBENCH_BOUNDARY"),
      show: location.pathname.includes("/hcmworkbench/boundary") ? true : false,
    },
    {
      path: `/${window?.contextPath}/employee/hcmworkbench/project`,
      content: t("WORKBENCH_PROJECT"),
      show: location.pathname.includes("/hcmworkbench/project") ? true : false,
    },
    {
      path: `/${window?.contextPath}/employee/hcmworkbench/microplan`,
      content: t("WORKBENCH_MICROPLAN"),
      show: location.pathname.includes("/hcmworkbench/microplan") ? true : false,
    },
    {

      path: `/${window?.contextPath}/employee/hcmworkbench/campaign`,
      content: t("WORKBENCH_CREATE_CAMPAIGN"),
      show: location.pathname.includes("/hcmworkbench/campaign") ? true : false,
    },
      {
      path: `/${window?.contextPath}/employee/hcmworkbench/master`,
      content: location.pathname.includes("master-landing-screen") ? t("WORKBENCH_MASTER") : t("WORKBENCH_USER"),
      query: `landingscreen=${screen}`,
      show: location.pathname.includes("/hcmworkbench/master") ? true : false,
    },
    

  ];
  return <BreadCrumb className="workbench-bredcrumb" crumbs={crumbs} spanStyle={{ maxWidth: "min-content" }} />;
};

const App = ({ path }) => {
  const location = useLocation();
  const MDMSCreateSession = Digit.Hooks.useSessionStorage("MDMS_add", {});
  const [sessionFormData, setSessionFormData, clearSessionFormData] = MDMSCreateSession;

  const MDMSViewSession = Digit.Hooks.useSessionStorage("MDMS_view", {});
  const [sessionFormDataView, setSessionFormDataView, clearSessionFormDataView] = MDMSViewSession;

  useEffect(() => {
    if (!window.location.href.includes("mdms-add-v2") && sessionFormData && Object.keys(sessionFormData) != 0) {
      clearSessionFormData();
    }
    if (!window.location.href.includes("mdms-view") && sessionFormDataView) {
      clearSessionFormDataView();
    }
  }, [location]);

  return (
    <React.Fragment>
      <div className="wbh-header-container">
        <WorkbenchBreadCrumb location={location} defaultPath={path} />
      </div>
      <Switch>
        <AppContainer className="workbench">
          <PrivateRoute path={`${path}/sample`} component={() => <div>Sample Screen loaded</div>} />
          <PrivateRoute path={`${path}/facility`} component={() => <DataIngestionComponent ingestionType={"facility"} />} />
          <PrivateRoute path={`${path}/user`} component={() => <DataIngestionComponent ingestionType={"user"} />} />
          <PrivateRoute path={`${path}/view`} component={() => <ErrorViewPage />} />
          <PrivateRoute path={`${path}/inbox`} component={() => <IngestionInbox />} />
          <PrivateRoute path={`${path}/boundary`} component={() => <DataIngestionComponent ingestionType={"boundary"} />} />
          <PrivateRoute path={`${path}/project`} component={() => <DataIngestionComponent ingestionType={"project"} />} />
          <PrivateRoute path={`${path}/campaign-view`} component={() => <ViewProject />} />
          <PrivateRoute path={`${path}/microplan`} component={() => <DataIngestionComponent ingestionType={"microplan"} />} />
          <PrivateRoute path={`${path}/campaign`} component={() => <CreateCampaign />} />
          <PrivateRoute path={`${path}/master/:screen`} component={() => <MasterComponent />} />
          <PrivateRoute path={`${path}/help-screen/:screen`} component={() => <HelpScreen />} />
        </AppContainer>
      </Switch>
    </React.Fragment>
  );
};

export default App;
