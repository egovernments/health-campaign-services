import { WorksMgmtIcon } from "@egovernments/digit-ui-react-components";
import React from "react";
import { useTranslation } from "react-i18next";
import { EmployeeModuleCard } from "@egovernments/digit-ui-react-components";

const ROLES = {
  LOCALISATION: ["EMPLOYEE", "SUPERUSER", "EMPLOYEE_COMMON", "LOC_ADMIN", "SYSTEM_ADMINISTRATOR"],
  MDMS: ["MDMS_ADMIN", "EMPLOYEE", "SUPERUSER", "SYSTEM_ADMINISTRATOR"],
  DSS: ["STADMIN", "SYSTEM_ADMINISTRATOR"],
};

// Mukta Overrriding the Works Home screen card
const HCMWORKBENCHCard = () => {
  if (!Digit.Utils.didEmployeeHasAtleastOneRole(Object.values(ROLES).flatMap((e) => e))) {
    return null;
  }

  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();

  let links = [
    {
      label: t("WORKBENCH_INBOX"),
      link: `/${window?.contextPath}/employee/hcmworkbench/inbox`,
      roles: ROLES.MDMS,
    },
    {
      label: t("WORKBENCH_FACILITY"),
      link: `/${window?.contextPath}/employee/hcmworkbench/facility`,
      roles: ROLES.MDMS,
    },
    {
      label: t("WORKBENCH_USER"),
      link: `/${window?.contextPath}/employee/hcmworkbench/user`,
      roles: ROLES.MDMS,
    },
    {
      label: t("WORKBENCH_OU"),
      link: `/${window?.contextPath}/employee/hcmworkbench/ou`,
      roles: ROLES.MDMS,
    },
  
    {
      label: t("WORKBENCH_BOUNDARY"),
      link: `/${window?.contextPath}/employee/hcmworkbench/boundary`,
      roles: ROLES.MDMS,
    },
    {
      label: t("WORKBENCH_PROJECT"),
      link: `/${window?.contextPath}/employee/hcmworkbench/project`,
      roles: ROLES.MDMS,
    },
    // {
    //   label: t("WORKBENCH_MICROPLAN"),
    //   link: `/${window?.contextPath}/employee/hcmworkbench/microplan`,
    //   roles: ROLES.MDMS,
    // },
  ];


  links = links.filter((link) => (link?.roles && link?.roles?.length > 0 ? Digit.Utils.didEmployeeHasAtleastOneRole(link?.roles) : true));

  const propsForModuleCard = {
    Icon: <WorksMgmtIcon />,
    moduleName: t("WORKBENCH_HCM_WORKBENCH"),
    kpis: [],
    links: links,
  };
  return <EmployeeModuleCard {...propsForModuleCard} />;
};

export default HCMWORKBENCHCard;
