import { Loader, TourProvider } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useRouteMatch } from "react-router-dom";
import EmployeeApp from "./pages/employee";
import { CustomisedHooks } from "./hooks";
import { UICustomizations } from "./configs/UICustomizations";
import CampaignCard from "./components/CampaignCard";
import CycleConfiguration from "./pages/employee/CycleConfiguration";
import DeliverySetup from "./pages/employee/deliveryRule";
import TimelineCampaign from "./components/TimelineCampaign";
import CampaignDates from "./components/CampaignDates";
import CampaignType from "./components/CampaignType";
import CampaignName from "./components/CampaignName";
import MyCampaign from "./pages/employee/MyCampaign";
import CampaignSummary from "./components/CampaignSummary";
import CycleDetaisPreview from "./components/CycleDetaisPreview";
import Response from "./pages/employee/Response";
import SelectingBoundaries from "./components/SelectingBoundaries";
import UploadData from "./components/UploadData";
import CampaignSelection from "./components/CampaignType";
import CampaignDocumentsPreview from "./components/CampaignDocumentsPreview";
import AddProduct from "./pages/employee/AddProduct";
import AddProductField from "./components/AddProductField";
import CycleDataPreview from "./components/CycleDataPreview";
import { ErrorBoundary } from "@egovernments/digit-ui-components";
import CampaignResourceDocuments from "./components/CampaignResourceDocuments";
import ConfigureApp from "./pages/employee/ConfigureApp";
import SideEffects from "./components/ConfigureApp/SideEffect";
import SideEffectType from "./components/ConfigureApp/SideEffectType";
import { DSSCard } from "./components/DSSCard";

/**
 * The CampaignModule function fetches store data based on state code, module code, and language, and
 * renders the EmployeeApp component within a TourProvider component if the data is not loading.
 * @returns The CampaignModule component returns either a Loader component if data is still loading, or
 * a TourProvider component wrapping an EmployeeApp component with specific props passed to it.
 */
const CampaignModule = ({ stateCode, userType, tenants }) => {
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { data: BOUNDARY_HIERARCHY_TYPE } = Digit.Hooks.useCustomMDMS(tenantId, "HCM-ADMIN-CONSOLE", [{ name: "hierarchyConfig" }], {
    select: (data) => {
      return data?.["HCM-ADMIN-CONSOLE"]?.hierarchyConfig?.find((item) => item.isActive)?.hierarchy;
    },
  });

  const moduleCode = ["campaignmanager", "workbench", "mdms", "schema", "hcm-admin-schemas", `boundary-${BOUNDARY_HIERARCHY_TYPE}`];
  const { path, url } = useRouteMatch();
  const language = Digit.StoreData.getCurrentLanguage();
  const { isLoading, data: store } = Digit.Services.useStore({
    stateCode,
    moduleCode,
    language,
  });

  if (isLoading) {
    return <Loader />;
  }

  return (
    <ErrorBoundary moduleName="CAMPAIGN">
      <TourProvider>
        <EmployeeApp BOUNDARY_HIERARCHY_TYPE={BOUNDARY_HIERARCHY_TYPE} path={path} stateCode={stateCode} url={url} userType={userType} />
      </TourProvider>
    </ErrorBoundary>
  );
};

const componentsToRegister = {
  CampaignModule: CampaignModule,
  CampaignCard: CampaignCard,
  UploadData,
  DeliveryRule: DeliverySetup,
  CycleConfiguration: CycleConfiguration,
  TimelineCampaign,
  CampaignDates,
  CampaignType,
  CampaignName,
  MyCampaign,
  CampaignSummary,
  CycleDetaisPreview,
  Response,
  SelectingBoundaries,
  CampaignSelection,
  CampaignDocumentsPreview: CampaignDocumentsPreview,
  AddProduct,
  AddProductField,
  CycleDataPreview,
  CampaignResourceDocuments,
  ConfigureApp,
  SideEffects,
  SideEffectType,
  DSSCard,
};

const overrideHooks = () => {
  Object.keys(CustomisedHooks).map((ele) => {
    if (ele === "Hooks") {
      Object.keys(CustomisedHooks[ele]).map((hook) => {
        Object.keys(CustomisedHooks[ele][hook]).map((method) => {
          setupHooks(hook, method, CustomisedHooks[ele][hook][method]);
        });
      });
    } else if (ele === "Utils") {
      Object.keys(CustomisedHooks[ele]).map((hook) => {
        Object.keys(CustomisedHooks[ele][hook]).map((method) => {
          setupHooks(hook, method, CustomisedHooks[ele][hook][method], false);
        });
      });
    } else {
      Object.keys(CustomisedHooks[ele]).map((method) => {
        setupLibraries(ele, method, CustomisedHooks[ele][method]);
      });
    }
  });
};

/* To Overide any existing hook we need to use similar method */
const setupHooks = (HookName, HookFunction, method, isHook = true) => {
  window.Digit = window.Digit || {};
  window.Digit[isHook ? "Hooks" : "Utils"] = window.Digit[isHook ? "Hooks" : "Utils"] || {};
  window.Digit[isHook ? "Hooks" : "Utils"][HookName] = window.Digit[isHook ? "Hooks" : "Utils"][HookName] || {};
  window.Digit[isHook ? "Hooks" : "Utils"][HookName][HookFunction] = method;
};
/* To Overide any existing libraries  we need to use similar method */
const setupLibraries = (Library, service, method) => {
  window.Digit = window.Digit || {};
  window.Digit[Library] = window.Digit[Library] || {};
  window.Digit[Library][service] = method;
};

/* To Overide any existing config/middlewares  we need to use similar method */
const updateCustomConfigs = () => {
  setupLibraries("Customizations", "commonUiConfig", { ...window?.Digit?.Customizations?.commonUiConfig, ...UICustomizations });
  // setupLibraries("Utils", "parsingUtils", { ...window?.Digit?.Utils?.parsingUtils, ...parsingUtils });
};

/**
 * The `initCampaignComponents` function initializes campaign components by overriding hooks, updating
 * custom configurations, and registering components.
 */
const initCampaignComponents = () => {
  overrideHooks();
  updateCustomConfigs();
  Object.entries(componentsToRegister).forEach(([key, value]) => {
    Digit.ComponentRegistryService.setComponent(key, value);
  });
};

export { initCampaignComponents };
