import React, { useEffect } from "react";
import { Switch, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PrivateRoute, AppContainer, BreadCrumb } from "@egovernments/digit-ui-react-components";
import DataIngestionComponent from "../../components/IngestionComponents/DataIngestionComponent";

import ErrorViewPage from "./ErrorViewPage";

import IngestionInbox from "./IngestionInbox";


const WorkbenchBreadCrumb = ({ location ,defaultPath}) => {
  const { t } = useTranslation();

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
      path: `/${window?.contextPath}/employee/hcmworkbench/ou`,
      content: t("WORKBENCH_OU"),
      show: location.pathname.includes("/hcmworkbench/ou") ? true : false,

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
    
  ];
  return <BreadCrumb className="workbench-bredcrumb" crumbs={crumbs} spanStyle={{ maxWidth: "min-content" }} />;
};

const App = ({ path }) => {
  const location = useLocation();
  const MDMSCreateSession = Digit.Hooks.useSessionStorage("MDMS_add", {});
  const [sessionFormData, setSessionFormData, clearSessionFormData] = MDMSCreateSession;
  
  const MDMSViewSession = Digit.Hooks.useSessionStorage("MDMS_view", {});
  const [sessionFormDataView,setSessionFormDataView,clearSessionFormDataView] = MDMSViewSession

  useEffect(() => {
    if (!window.location.href.includes("mdms-add-v2") && sessionFormData && Object.keys(sessionFormData) != 0) {
      clearSessionFormData();
    }
    if (!window.location.href.includes("mdms-view") && sessionFormDataView ) {
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
          <PrivateRoute path={`${path}/facility`} component={() => <DataIngestionComponent ingestionType={"Facility"} />} />
          <PrivateRoute path={`${path}/user`} component={() => <DataIngestionComponent ingestionType={"User"} />} />
          <PrivateRoute path={`${path}/ou`} component={() => <DataIngestionComponent ingestionType={"OU"} />} />

          <PrivateRoute path={`${path}/view`} component={() => <ErrorViewPage  />} />

          <PrivateRoute path={`${path}/inbox`} component={() => <IngestionInbox />} />
          <PrivateRoute path={`${path}/boundary`} component={() => <DataIngestionComponent ingestionType={"Boundary"} />} />
          <PrivateRoute path={`${path}/project`} component={() => <DataIngestionComponent ingestionType={"Project"} />} />


        </AppContainer>
      </Switch>
    </React.Fragment>
  );
};

export default App;
