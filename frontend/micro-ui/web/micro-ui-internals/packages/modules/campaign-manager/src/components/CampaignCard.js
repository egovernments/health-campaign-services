import { EmployeeModuleCard, SVG } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useTranslation } from "react-i18next";

const ROLES = {
  CAMPAIGN_MANAGER:["CAMPAIGN_MANAGER"]
};

/**
 * The CampaignCard component renders a card with links related to campaign management, filtering out
 * links based on employee roles.
 * @returns The CampaignCard component is being returned. It contains a list of links related to
 * campaign actions, such as setting up a campaign and viewing personal campaigns. The links are
 * filtered based on employee roles before being displayed in the EmployeeModuleCard component.
 */
const CampaignCard = () => {
  if (!Digit.Utils.didEmployeeHasAtleastOneRole(Object.values(ROLES).flatMap((e) => e))) {
  return null;
  }

  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  // const reqCriteria = {
  //   url: "/project-factory/v1/project-type/search",
  //   params: {},
  //   body: { CampaignDetails:{
  //     tenantId,
  //     createdBy: Digit.UserService.getUser().info.uuid,
  //     pagination: {
  //       "sortBy": "createdTime",
  //       "sortOrder": "desc",
  //       "limit": 1,
  //       "offset": 0
  //     }
  //   } },
  //   config: {
  //     select: (data) => {
  //       return data?.totalCount;
  //     },
  //   },
  // };
  // const { isLoading, data } = Digit.Hooks.useCustomAPIHook(
  //   reqCriteria
  // );
  let links = [

    {
      label: t("ACTION_TEST_SETUP_CAMPAIGN"),
      link: `/${window?.contextPath}/employee/campaign/setup-campaign`,
      roles: ROLES.CAMPAIGN_MANAGER
    },
    {
      label: t("ACTION_TEST_MY_CAMPAIGN"),
      link: `/${window?.contextPath}/employee/campaign/my-campaign`,
      roles: ROLES.CAMPAIGN_MANAGER,
      // count: isLoading?"-":data
    },
  ];

  links = links.filter((link) => (link?.roles && link?.roles?.length > 0 ? Digit.Utils.didEmployeeHasAtleastOneRole(link?.roles) : true));

  const propsForModuleCard = {
    Icon: <SVG.Support fill="white" height="36" width="36"/>,
    moduleName: t("ACTION_TEST_CAMPAIGN"),
    kpis: [],
    links: links,
  };
  return <EmployeeModuleCard {...propsForModuleCard} />;
};

export default CampaignCard;
