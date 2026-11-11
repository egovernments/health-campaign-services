// Local docker-compose variant of UAT globalConfigsPaymentsUAT.js
// Backend API calls are made relatively to the page origin, which is served
// by frontend-proxy and forwarded to Kong (http://kong:8000) inside the network.
var globalConfigs = (function () {
  var contextPath = "payments-ui";
  var projectContext = "health-project";
  var individualContext = "health-individual";
  var attendanceContext = "health-attendance";
  var musterRollContext = "health-muster-roll";
  var expenseContext = "health-expense";
  var expenseCalculatorContext = "health-expense-calculator";
  var stateTenantId = "mz";
  var gmaps_api_key = "";
  var configModuleName = "commonUiConfig";
  var centralInstanceEnabled = false;
  var localeRegion = "IN";
  var localeDefault = "en";
  var mdmsContext = "egov-mdms-service";
  var hrmsContext = "health-hrms";
  var hierarchyType = "MICROPLAN";
  var footerBWLogoURL = "/digit-ui-assets/digit-footer-bw.png";
  var footerLogoURL = "/digit-ui-assets/digit-footer.png";
  var digitHomeURL = "https://www.digit.org/";
  var assetS3Bucket = "egov-dev-assets";
  var calculationPageAssets = "/digit-ui-assets/calculation-page-assets/";
  var getConfig = function (key) {
    if (key === "STATE_LEVEL_TENANT_ID") return stateTenantId;
    if (key === "GMAPS_API_KEY") return gmaps_api_key;
    if (key === "ROLE_BASED_HOMECARD") return true;
    if (key === "ENABLE_SINGLEINSTANCE") return centralInstanceEnabled;
    if (key === "DIGIT_FOOTER_BW") return footerBWLogoURL;
    if (key === "DIGIT_FOOTER") return footerLogoURL;
    if (key === "DIGIT_HOME_URL") return digitHomeURL;
    if (key === "S3BUCKET") return assetS3Bucket;
    if (key === "CONTEXT_PATH") return contextPath;
    if (key === "UICONFIG_MODULENAME") return configModuleName;
    if (key === "LOCALE_REGION") return localeRegion;
    if (key === "LOCALE_DEFAULT") return localeDefault;
    if (key === "MDMS_CONTEXT_PATH") return mdmsContext;
    if (key === "MDMS_V2_CONTEXT_PATH") return mdmsContext;
    if (key === "MDMS_V1_CONTEXT_PATH") return mdmsContext;
    if (key === "HRMS_CONTEXT_PATH") return hrmsContext;
    if (key === "CALCULATION_PAGE_ASSETS") return calculationPageAssets;
    if (key === "PROJECT_CONTEXT_PATH") return projectContext;
    if (key === "HIERARCHY_TYPE") return hierarchyType;
    if (key === "INDIVIDUAL_CONTEXT_PATH") return individualContext;
    if (key === "ATTENDANCE_CONTEXT_PATH") return attendanceContext;
    if (key === "MUSTER_ROLL_CONTEXT_PATH") return musterRollContext;
    if (key === "EXPENSE_CONTEXT_PATH") return expenseContext;
    if (key === "EXPENSE_CALCULATOR_CONTEXT_PATH") return expenseCalculatorContext;
  };
  return { getConfig };
})();
