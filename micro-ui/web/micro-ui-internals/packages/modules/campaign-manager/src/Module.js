import { Loader, TourProvider } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useRouteMatch } from "react-router-dom";
import EmployeeApp from "./pages/employee";
import { CustomisedHooks } from "./hooks";
import { UICustomizations } from "./configs/UICustomizations";
import CampaignCard from "./components/CampaignCard";
import UploadBoundaryData from "./pages/employee/UploadBoundaryData";
import CycleConfiguration from "./pages/employee/CycleConfiguration";
import DeliverySetup from "./pages/employee/deliveryRule";
import TimelineCampaign from "./components/TimelineCampaign";
import CampaignDates from "./components/CampaignDates";
import CampaignType from "./components/CampaignType";
import CampaignName from "./components/CampaignName";
import MyCampaign from "./pages/employee/MyCampaign";
import CampaignSummary from "./components/CampaignSummary";
import CycleDetaisPreview from "./components/CycleDetaisPreview";

const CampaignModule = ({ stateCode, userType, tenants }) => {
  const moduleCode = ["campaignmanager", "workbench", "mdms", "schema"];
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
    <TourProvider>
      <EmployeeApp path={path} stateCode={stateCode} url={url} userType={userType} />
    </TourProvider>
  );
};

const componentsToRegister = {
  CampaignModule: CampaignModule,
  // campaignModule: CampaignModule,
  // campaignCard: CampaignCard,
  CampaignCard: CampaignCard,
  UploadBoundaryData,
  DeliveryRule: DeliverySetup,
  CycleConfiguration: CycleConfiguration,
  TimelineCampaign,
  CampaignDates,
  CampaignType,
  CampaignName,
  MyCampaign,
  CampaignSummary,
  CycleDetaisPreview,
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

const initCampaignComponents = () => {
  overrideHooks();
  updateCustomConfigs();
  Object.entries(componentsToRegister).forEach(([key, value]) => {
    Digit.ComponentRegistryService.setComponent(key, value);
  });
};

export { initCampaignComponents };
