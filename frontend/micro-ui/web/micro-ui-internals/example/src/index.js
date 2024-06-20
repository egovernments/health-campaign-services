import React from "react";
import ReactDOM from "react-dom";

import { initLibraries } from "@egovernments/digit-ui-libraries";
// import { paymentConfigs, PaymentLinks, PaymentModule } from "@egovernments/digit-ui-module-common";
import { DigitUI } from "@egovernments/digit-ui-module-core";
import "@egovernments/digit-ui-css/example/index.css";

import { UICustomizations } from "./UICustomizations";
import { initCampaignComponents } from "@egovernments/digit-ui-module-campaign-manager"
import { initWorkbenchComponents } from "@egovernments/digit-ui-module-workbench";
import { initUtilitiesComponents } from "@egovernments/digit-ui-module-utilities";
import { initWorkbenchHCMComponents } from "@egovernments/digit-ui-module-hcmworkbench";
import { initMicroplanningComponents } from "@egovernments/digit-ui-module-hcmmicroplanning";

var Digit = window.Digit || {};

const enabledModules = [
  "DSS",
  "HRMS",
  "Workbench",
  "HCMWORKBENCH",
  "Campaign",
  //  "Engagement", "NDSS","QuickPayLinks", "Payment",
  "Utilities",
  "Microplanning"
  //added to check fsm
  // "FSM"
];

const initTokens = (stateCode) => {
  const userType = window.sessionStorage.getItem("userType") || process.env.REACT_APP_USER_TYPE || "CITIZEN";
  const token = window.localStorage.getItem("token") || process.env[`REACT_APP_${userType}_TOKEN`];

  const citizenInfo = window.localStorage.getItem("Citizen.user-info");

  const citizenTenantId = window.localStorage.getItem("Citizen.tenant-id") || stateCode;

  const employeeInfo = window.localStorage.getItem("Employee.user-info");
  const employeeTenantId = window.localStorage.getItem("Employee.tenant-id");

  const userTypeInfo = userType === "CITIZEN" || userType === "QACT" ? "citizen" : "employee";
  window.Digit.SessionStorage.set("user_type", userTypeInfo);
  window.Digit.SessionStorage.set("userType", userTypeInfo);

  if (userType !== "CITIZEN") {
    window.Digit.SessionStorage.set("User", { access_token: token, info: userType !== "CITIZEN" ? JSON.parse(employeeInfo) : citizenInfo });
  } else {
    // if (!window.Digit.SessionStorage.get("User")?.extraRoleInfo) window.Digit.SessionStorage.set("User", { access_token: token, info: citizenInfo });
  }

  window.Digit.SessionStorage.set("Citizen.tenantId", citizenTenantId);

  if (employeeTenantId && employeeTenantId.length) window.Digit.SessionStorage.set("Employee.tenantId", employeeTenantId);
};

const initDigitUI = () => {
  window.contextPath = window?.globalConfigs?.getConfig("CONTEXT_PATH") || "digit-ui";
  window.Digit.Customizations = {
    commonUiConfig: UICustomizations
  };
  window?.Digit.ComponentRegistryService.setupRegistry({
    // PaymentModule,
    // ...paymentConfigs,
    // PaymentLinks,
  });
  initUtilitiesComponents();
  initWorkbenchComponents();
  initWorkbenchHCMComponents();
  initCampaignComponents();
  initMicroplanningComponents();

  const moduleReducers = (initData) => initData;


  const stateCode = window?.globalConfigs?.getConfig("STATE_LEVEL_TENANT_ID") || "pb";
  initTokens(stateCode);

  ReactDOM.render(<DigitUI stateCode={stateCode} enabledModules={enabledModules}       defaultLanding="employee"  moduleReducers={moduleReducers} />, document.getElementById("root"));
};

initLibraries().then(() => {
  initDigitUI();
});
