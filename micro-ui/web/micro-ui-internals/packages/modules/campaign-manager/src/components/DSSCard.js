import { EmployeeModuleCard, ModuleCardFullWidth, SVG } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useTranslation } from "react-i18next";

const nationalScreenURLs = {
  overview: { key: "national-overview", stateKey: "overview", label: "NURT_OVERVIEW", active: true, nActive: true },
  propertytax: { key: "national-propertytax", stateKey: "propertytax", label: "NURT_PROPERTY_TAX", active: true, nActive: true },
  tradelicense: { key: "national-tradelicense", stateKey: "tradelicense", label: "NURT_TRADE_LICENCE", active: true, nActive: true },
  pgr: { key: "national-pgr", stateKey: "pgr", label: "NURT_COMPLAINS", active: true, nActive: true },
  fsm: { key: "fsm", stateKey: "fsm", label: "CS_HOME_FSM_SERVICES", active: true, nActive: false },
  mCollect: { key: "national-mcollect", stateKey: "mCollect", label: "NURT_MCOLLECT", active: true, nActive: true },
  ws: { key: "national-ws", stateKey: "ws", label: "NURT_WATER_SEWERAGE", active: true, nActive: true },
  obps: { key: "nss-obps", stateKey: "obps", label: "DSS_BUILDING_PERMISSION", active: true, nActive: true },
  noc: { key: "national-firenoc", stateKey: "noc", label: "NURT_FIRENOC", active: true, nActive: true },
  bnd: { key: "nss-birth-death", stateKey: "birth-death", label: "BIRTH_AND_DEATH", active: true, nActive: true },
  faqs: { key: "national-faqs", stateKey: "national-faqs", label: "DSS_FAQS", active: false, nActive: true, others: true },
  finance: { key: "national-finance", stateKey: "finance", label: "DSS_FINANCE", active: true, nActive: false },
  about: { key: "national-about", stateKey: "national-about", label: "DSS_ABOUT_DASHBOARD", active: false, nActive: true, others: true },
};

const healthDSSURLs = {
  about: { key: "about", stateKey: "about", label: "DSS_ABOUT", active: true },
  faqs: { key: "faqs", stateKey: "faqs", label: "DSS_FAQS", active: true },
  calculations: { key: "calculations", stateKey: "calculations", label: "DSS_CALCULATIONS", active: true }
};

export const checkCurrentScreen = () => {
  const moduleName = Digit.Utils.dss.getCurrentModuleName();
  const nationalURLS = Object.keys(nationalScreenURLs).map((key) => nationalScreenURLs[key].key);
  return nationalURLS.filter(ele=>ele!=="fsm").some((e) => moduleName?.includes(e));
};

const NDSSCard = () => {
  const NATADMIN = Digit.UserService.hasAccess("NATADMIN");
  const { t } = useTranslation();

  if (!NATADMIN) {
    return null;
  }

  let links = Object.values(nationalScreenURLs)
    .filter((ele) => ele["nActive"] === true)
    .map((obj) => ({
      label: t(obj?.label),
      link: `/digit-ui/employee/dss/dashboard/${obj?.key}`,
      link: obj?.others?`/digit-ui/employee/dss/${obj?.key}`:`/digit-ui/employee/dss/dashboard/${obj?.key}`,
    }));

  const propsForModuleCard = {
    headerStyle: { border: "none", height: "48px" },
    moduleName: t("ACTION_TEST_NATDASHBOARD"),
    subHeader: t("ACTION_TEST_NATDASHBOARD"),
    subHeaderLink: `/digit-ui/employee/dss/landing/NURT_DASHBOARD`,
    className: "employeeCard customEmployeeCard card-home full-width-card full-employee-card-height",
    links: [...links],
  };
  return <ModuleCardFullWidth {...propsForModuleCard} />;
};

const DSSCard = () => {
  const STADMIN = Digit.UserService.hasAccess("STADMIN");
  const { t } = useTranslation();

  const { data: tenantData } = Digit.Hooks.dss.useMDMS(Digit.ULBService.getStateId(), "tenant", ["tenants"], {
    select: (data) => {
      const tenantData = data?.["tenant"]?.["tenants"]?.[0];
      return tenantData;
    },
    enabled: true,
  });

  const shouldInvokeProjectService = tenantData?.integrateProjectService || false;
  if (shouldInvokeProjectService) {
    return <DynamicDSSCard />;
  }

  if (!STADMIN) {
    return null;
  }

  let links = Object.values(nationalScreenURLs)
    .filter((ele) => ele["active"] === true)
    .map((obj) => ({
      label: t(obj?.label),
      link: obj.active ? `/digit-ui/employee/dss/dashboard/${obj?.stateKey}` : `/employee/integration/dss/${obj?.stateKey}`,
    }));

  const propsForModuleCard = {
    headerStyle: { border: "none", height: "48px" },
    moduleName: t("ES_TITLE_DSS"),
    subHeader: t("ACTION_TEST_SURE_DASHBOARD"),
    subHeaderLink: `/digit-ui/employee/dss/landing/home`,
    className: "employeeCard card-home customEmployeeCard full-width-card full-employee-card-height",
    links: [...links],
  };
  return <ModuleCardFullWidth {...propsForModuleCard} styles={{ width: "100%" }} />;
};

const DynamicDSSCard = () => {
  const { t } = useTranslation();

  const isNationalSupervisor = Digit.UserService.hasAccess(["NATIONAL_SUPERVISOR"]);
  const isProvincialSupervisor = Digit.UserService.hasAccess(["PROVINCIAL_SUPERVISOR"]);
  const isDistrictSupervisor = Digit.UserService.hasAccess(["DISTRICT_SUPERVISOR"]);
  const isHealthFacilitySupervisor = Digit.UserService.hasAccess(["HEALTH_FACILITY_SUPERVISOR"]);
  const isCommunitySupervisor = Digit.UserService.hasAccess(["COMMUNITY_SUPERVISOR"]);

  const projectTypes = Digit.SessionStorage.get("projectTypes");
  const campaignData = Digit.SessionStorage.get("campaigns-info");

  // if (!isCommunitySupervisor && !isHealthFacilitySupervisor) {
  //   if (!campaignData || !projectTypes) {
  //     return null;
  //   }
  // }

  const generateLinks = (location, code) => {
    let links = [];
    Object.keys(campaignData)?.map((key) => {
      if (key === "LLIN-Default") {
        const campaignType = campaignData[key];
        campaignType.forEach((data) => {
          const locationParam = data.boundaries?.[location]?.[0];
          const url = projectTypes?.filter(project => project?.code === "LLIN-Default")[0]?.dashboardUrls?.[code]
          links.push({
            label: `${t(key)}  - ${Digit.Utils.locale.getTitleHeading(locationParam)}`,
            link: location === "province" ? `${url}?province=${locationParam}` : `${url}`,
          });
        })
      }
    });
    return links;
  };

  let links = [];

  // if (isNationalSupervisor) {
  //   links = Object.keys(campaignData)?.map((key) => {
  //     return {
  //       label: `${t(`${key}`)} - Mozambique`,
  //       link: projectTypes?.filter(project => project?.code === key)[0]?.dashboardUrls?.["NATIONAL_SUPERVISOR"]
  //     };
  //   });
  // } else if (isProvincialSupervisor) {
  //   links = generateLinks("province","PROVINCIAL_SUPERVISOR");
  // } else if (isDistrictSupervisor) {
  //   links = generateLinks("district","DISTRICT_SUPERVISOR");
  // }
  // links.push(...Object.values(healthDSSURLs)
  //   .filter((ele) => ele["active"] === true)
  //   .map((obj) => ({
  //     label: t(obj?.label),
  //     link:  `/digit-ui/employee/dss/${obj?.stateKey}`
  //   })));

  if (isNationalSupervisor) {
    links.push({
      label: t("NATIONAL_DASHBOARD"),
      link: "/digit-ui/employee/utilities/iframe/elastic/national"
    });
    // TODO: To remove these hardcoded urls and generate all of this from mdms config
    links.push({
      label: t("NDSS_DASHBOARD"),
      link: "/digit-ui/employee/dss/landing/national-health-dashboard"
    })
  } else if (isProvincialSupervisor) {
    links.push({
      label: t("PROVINCIAL_DASHBOARD"),
      link: "/digit-ui/employee/utilities/iframe/elastic/province?query=true"
    })
    links = [...links, ...generateLinks("province","PROVINCIAL_SUPERVISOR")]
  } else if (isDistrictSupervisor) {
    links.push({
      label: t("DISTRICT_DASHBOARD"),
      link: "/digit-ui/employee/utilities/iframe/elastic/district?query=true"
    })
    links = [...links, ...generateLinks("district","DISTRICT_SUPERVISOR")]
    console.log(links)
  }
  else if(isHealthFacilitySupervisor){
    links.push({
      label: t("REFERRAL_DASHBOARD"),
      link: "/digit-ui/employee/utilities/iframe/elastic/hf?query=true"
    })
  }

  // if(isNationalSupervisor || isProvincialSupervisor || isDistrictSupervisor){
  //   links.push({
  //     label: t('CUSTOM_REPORTS_LINK'),
  //     link:  Digit.SessionStorage.get("initData")?.stateInfo?.customReportsDriveLink,
  //     external: true
  //   })

  //   links.push({
  //     label: t('FEEDBACK_LINK'),
  //     link:  Digit.SessionStorage.get("initData")?.stateInfo?.feedbackLink,
  //     external: true
  //   })
  // }

  const propsForModuleCard = {
    Icon: <SVG.Dashboard fill="#fff" />,
    moduleName: t("DSS_CARD_HEADER_DASHBOARD"),
    links: [...links],
  };

  return <EmployeeModuleCard {...propsForModuleCard} />;
};

export { DSSCard, NDSSCard };