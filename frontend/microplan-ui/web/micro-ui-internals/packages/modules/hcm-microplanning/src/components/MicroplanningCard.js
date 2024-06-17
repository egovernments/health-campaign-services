import { EmployeeModuleCard, WorksMgmtIcon } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useTranslation } from "react-i18next";

const ROLES = {
  MICROPLAN:['MICROPLAN_ADMIN']
};

const MicroplanningCard = () => {
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();

  let links = [
    // {
    //   label: t("Upload Page"),
    //   link: `/${window?.contextPath}/employee/microplanning/upload`,
    //   roles: ROLES.MICROPLAN,
    // },
    // {
    //   label: t("Hypothesis Page"),
    //   link: `/${window?.contextPath}/employee/microplanning/hypothesis`,
    //   roles: ROLES.MICROPLAN,
    // },
    // {
    //   label: t("Rule Engine Page"),
    //   link: `/${window?.contextPath}/employee/microplanning/rule-engine`,
    //   roles: ROLES.MICROPLAN,
    // },
    {
      label: t("CREATE_NEW_MICROPLAN"),
      link: `/${window?.contextPath}/employee/microplanning/select-campaign`,
      roles: ROLES.MICROPLAN,
    },
    {
      label: t("OPEN_SAVED_MICROPLANS"),
      link: `/${window?.contextPath}/employee/microplanning/saved-microplans`,
      roles: ROLES.MICROPLAN,
    },
    
  ];
  
  links = links.filter((link) => (link?.roles && link?.roles?.length > 0 ? Digit.Utils.didEmployeeHasAtleastOneRole(link?.roles) : true));

  const propsForModuleCard = {
    Icon: <WorksMgmtIcon />,
    moduleName: t("Microplanning"),
    kpis: [],
    links: links,
  };
  return <EmployeeModuleCard {...propsForModuleCard} />;
};

export default MicroplanningCard;
