import { EmployeeModuleCard, ArrowRightInbox, WorksMgmtIcon } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useTranslation } from "react-i18next";

const ROLES = {
  LOCALISATION: ["EMPLOYEE", "SUPERUSER", "EMPLOYEE_COMMON", "LOC_ADMIN"],
  MDMS: ["MDMS_ADMIN", "EMPLOYEE", "SUPERUSER"],
  DSS: ["STADMIN"],
};

/**
 * The CampaignCard component renders a card with links related to campaign management, filtering out
 * links based on employee roles.
 * @returns The CampaignCard component is being returned. It contains a list of links related to
 * campaign actions, such as setting up a campaign and viewing personal campaigns. The links are
 * filtered based on employee roles before being displayed in the EmployeeModuleCard component.
 */
const CampaignCard = () => {
  // if (!Digit.Utils.didEmployeeHasAtleastOneRole(Object.values(ROLES).flatMap((e) => e))) {
  // return null;
  // }

  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();

  let links = [
    // {
    //   label: t("ACTION_TEST_CAMPAIGN_HOME"),
    //   link: `/${window?.contextPath}/employee/campaign/sample`,
    //   roles: [], // @nabeel roles to be added later
    // },
    // {
    //   label: t("ACTION_TEST_CAMPAIGN_CYCLE_CONFIGURE"),
    //   link: `/${window?.contextPath}/employee/campaign/create-campaign/cycle-configure`,
    //   roles: [], // @nabeel roles to be added later
    // },
    // {
    //   label: t("ACTION_TEST_CAMPAIGN_CYCLE"),
    //   link: `/${window?.contextPath}/employee/campaign/create-campaign/delivery-details`,
    //   roles: [], // @nabeel roles to be added later
    // },
    {
      label: t("ACTION_TEST_SETUP_CAMPAIGN"),
      link: `/${window?.contextPath}/employee/campaign/setup-campaign`,
      roles: [], // @nabeel roles to be added later
    },
    // {
    //   label: t("ACTION_TEST_PREVIEW_CAMPAIGN"),
    //   link: `/${window?.contextPath}/employee/campaign/preview`,
    //   roles: [], // @nabeel roles to be added later
    // },
    {
      label: t("ACTION_TEST_MY_CAMPAIGN"),
      link: `/${window?.contextPath}/employee/campaign/my-campaign`,
      roles: [], // @nabeel roles to be added later
    },
    // {
    // label: t("ACTION_TEST_MY_CAMPAIGN"),
    // link: `/${window?.contextPath}/employee/campaign/my-campaign`,
    // roles: [], // @nabeel roles to be added later
    // },
  ];

  links = links.filter((link) => (link?.roles && link?.roles?.length > 0 ? Digit.Utils.didEmployeeHasAtleastOneRole(link?.roles) : true));

  const propsForModuleCard = {
    Icon: <WorksMgmtIcon />,
    moduleName: t("ACTION_TEST_CAMPAIGN"),
    kpis: [],
    links: links,
  };
  return <EmployeeModuleCard {...propsForModuleCard} />;
};

export default CampaignCard;
