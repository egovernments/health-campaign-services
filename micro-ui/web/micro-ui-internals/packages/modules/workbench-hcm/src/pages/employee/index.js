import React, { useEffect } from "react";
import { Switch, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PrivateRoute, AppContainer, BreadCrumb } from "@egovernments/digit-ui-react-components";

const WorkbenchBreadCrumb = ({ location ,defaultPath}) => {
  const { t } = useTranslation();

  const crumbs = [
    {
      path: `/${window?.contextPath}/employee`,
      content: t("WORKBENCH_HOME"),
      show: true,
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
        </AppContainer>
      </Switch>
    </React.Fragment>
  );
};

export default App;
