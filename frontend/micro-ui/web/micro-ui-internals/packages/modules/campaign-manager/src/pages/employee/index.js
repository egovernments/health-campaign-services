import React, { useEffect } from "react";
import { Switch, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PrivateRoute, AppContainer, BreadCrumb } from "@egovernments/digit-ui-react-components";
// import CampaignHeader from "../../components/CampaignHeader";
import SetupCampaign from "./SetupCampaign";
import SelectingBoundaries from "../../components/SelectingBoundaries";
import ConfigureApp from "./ConfigureApp";

/**
 * The CampaignBreadCrumb function generates breadcrumb navigation for a campaign setup page in a React
 * application.
 * @returns The CampaignBreadCrumb component is returning a BreadCrumb component with the specified
 * crumbs array and spanStyle prop. The crumbs array contains two objects with path, content, and show
 * properties for each breadcrumb item. The spanStyle prop is set to { maxWidth: "min-content" }.
 */
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
    {
      path: pathVar === "my-campaign" ? "" : `/${window?.contextPath}/employee/campaign/my-campaign`,
      content: t("MY_CAMPAIGN"),
      show: pathVar === "my-campaign" ? true : false,
    },
    {
      path: pathVar === "setup-campaign" ? "" : `/${window?.contextPath}/employee/campaign/setup-campaign`,
      content: t("CREATE_NEW_CAMPAIGN"),
      show: pathVar === "setup-campaign" ? true : false,
    },
    {
      path: pathVar === "update-dates-boundary" ? "" : `/${window?.contextPath}/employee/campaign/my-campaign`,
      content: t("UPDATE_DATE_CHANGE"),
      show: pathVar === "update-dates-boundary" ? true: false,
    },
  ];

  return <BreadCrumb className="campaign-breadcrumb" crumbs={crumbs} spanStyle={{ maxWidth: "min-content" }} />;
};

/**
 * The `App` function in JavaScript defines a component that handles different routes and renders
 * corresponding components based on the path provided.
 * @returns The `App` component is returning a JSX structure that includes a `div` with a className of
 * "wbh-header-container" containing a `CampaignBreadCrumb` component and a `Switch` component. Inside
 * the `Switch` component, there are several `PrivateRoute` components with different paths and
 * corresponding components such as `UploadBoundaryData`, `CycleConfiguration`, `DeliveryRule`, `
 */
const App = ({ path, BOUNDARY_HIERARCHY_TYPE }) => {
  const location = useLocation();
  const UploadBoundaryData = Digit?.ComponentRegistryService?.getComponent("UploadBoundaryData");
  const CycleConfiguration = Digit?.ComponentRegistryService?.getComponent("CycleConfiguration");
  const DeliveryRule = Digit?.ComponentRegistryService?.getComponent("DeliveryRule");
  const MyCampaign = Digit?.ComponentRegistryService?.getComponent("MyCampaign");
  const CampaignSummary = Digit?.ComponentRegistryService?.getComponent("CampaignSummary");
  const Response = Digit?.ComponentRegistryService?.getComponent("Response");
  const AddProduct = Digit?.ComponentRegistryService?.getComponent("AddProduct");
  const UpdateDatesWithBoundaries = Digit?.ComponentRegistryService?.getComponent("UpdateDatesWithBoundaries");

  useEffect(() => {
    if (window.location.pathname !== "/workbench-ui/employee/campaign/setup-campaign") {
      window.Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
      window.Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_UPLOAD_ID");
    }
    if (window.location.pathname === "/workbench-ui/employee/campaign/response") {
      window.Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
      window.Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_UPLOAD_ID");
    }
    return () => {
      if (window.location.pathname !== "/workbench-ui/employee/campaign/setup-campaign") {
        window.Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_FORM_DATA");
        window.Digit.SessionStorage.del("HCM_CAMPAIGN_MANAGER_UPLOAD_ID");
      }
    };
  }, []);
  return (
    <React.Fragment>
      <div className="wbh-header-container">
        {window?.location?.pathname === "/workbench-ui/employee/campaign/add-product" ||
        window?.location?.pathname === "/workbench-ui/employee/campaign/response" ? null : (
          <CampaignBreadCrumb location={location} defaultPath={path} />
        )}
        {/* <CampaignHeader /> */}
      </div>
      <Switch>
        <AppContainer className="campaign">
          <PrivateRoute path={`${path}/create-campaign/upload-boundary-data`} component={() => <UploadBoundaryData />} />
          <PrivateRoute path={`${path}/create-campaign/cycle-configure`} component={() => <CycleConfiguration />} />
          <PrivateRoute path={`${path}/create-campaign/delivery-details`} component={() => <DeliveryRule />} />
          <PrivateRoute path={`${path}/setup-campaign`} component={() => <SetupCampaign hierarchyType={BOUNDARY_HIERARCHY_TYPE} />} />
          <PrivateRoute path={`${path}/my-campaign`} component={() => <MyCampaign />} />
          <PrivateRoute path={`${path}/preview`} component={() => <CampaignSummary />} />
          <PrivateRoute path={`${path}/response`} component={() => <Response />} />
          <PrivateRoute path={`${path}/selecting-boundary`} component={() => <SelectingBoundaries />} />
          <PrivateRoute path={`${path}/add-product`} component={() => <AddProduct />} />
          <PrivateRoute path={`${path}/configure-app`} component={() => <ConfigureApp />} />
          <PrivateRoute path={`${path}/update-dates-boundary`} component={() => <UpdateDatesWithBoundaries />} />
        </AppContainer>
      </Switch>
    </React.Fragment>
  );
};

export default App;
