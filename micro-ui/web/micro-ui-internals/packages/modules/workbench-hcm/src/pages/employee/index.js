import React, { useEffect } from "react";
import { Switch, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PrivateRoute, AppContainer, BreadCrumb } from "@egovernments/digit-ui-react-components";
import Example from "../../components/IngestionComponents/Example";

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
  console.log(path," ppppppppppppp")
  
  return (
    <React.Fragment>
      <div className="wbh-header-container">
        <WorkbenchBreadCrumb location={location} defaultPath={path} />
      </div>
      <Switch>
        <AppContainer className="workbench">
          <PrivateRoute path={`${path}/sample`} component={() => <div>Sample Screen loaded</div>} />
          <PrivateRoute path={`${path}/facility`} component={() => <Example ingestionType={"Facility"} />} />
          <PrivateRoute path={`${path}/user`} component={() => <Example ingestionType={"User"} />} />
          <PrivateRoute path={`${path}/ou`} component={() => <Example ingestionType={"OU"} />} />

        </AppContainer>
      </Switch>
    </React.Fragment>
  );
};

export default App;
