import { getFormattedStringForDebug, logger } from "../logger";

/* 
TODO: Update configObject with appropriate values.
This object contains configuration settings for delivery strategies and wait times.
*/


/* TODO: Update the logic to fetch the projecttype master */
const defaultProjectType: any = {
  /* 
    Define default project types with their respective properties.
    Each project type represents a specific type of campaign.
    */
  "MR-DN": {
    id: "b1107f0c-7a91-4c76-afc2-a279d8a7b76a",
    name: "configuration for Multi Round Campaigns",
    code: "MR-DN",
    group: "MALARIA",
    beneficiaryType: "INDIVIDUAL",
    resources: [],
    observationStrategy: "DOT1",
    validMinAge: 3,
    validMaxAge: 60,
    cycles: [],
  },
  "LLIN-mz": {
    id: "192a20d1-0edd-4108-925a-f37bf544d6c4",
    name: "Project type configuration for IRS - Nampula Campaigns",
    code: "LLIN-mz",
    group: "IRS - Nampula",
    beneficiaryType: "HOUSEHOLD",
    eligibilityCriteria: ["All households are eligible."],
    dashboardUrls: {
      NATIONAL_SUPERVISOR:
        "/digit-ui/employee/dss/landing/national-health-dashboard",
      PROVINCIAL_SUPERVISOR:
        "/digit-ui/employee/dss/dashboard/provincial-health-dashboard",
      DISTRICT_SUPERVISOR:
        "/digit-ui/employee/dss/dashboard/district-health-dashboard",
    },
    taskProcedure: [
      "1 DDT is to be distributed per house.",
      "1 Malathion is to be distributed per house.",
      "1 Pyrethroid is to be distributed per house.",
    ],
    resources: [],
  },
};

/* 
Convert campaign details to project details enriched with campaign information.
*/
export const projectTypeConversion = (
  campaignObject: any = {}
) => {
  const deliveryRules = campaignObject.deliveryRules;
  return deliveryRules?.[0] || defaultProjectType?.["LLIN-mz"];
};
/* 
Enrich project details from campaign details.
*/
export const enrichProjectDetailsFromCampaignDetails = (
  CampaignDetails: any = {},
  projectTypeObject: any = {}
) => {
  var { tenantId, projectType, startDate, endDate, campaignName } =
    CampaignDetails;
  logger.info("campaign transformation for project type : " + projectType);
  logger.debug(
    "project type : " + getFormattedStringForDebug(projectTypeObject)
  );
  const defaultProject = projectTypeConversion( CampaignDetails);
  return [
    {
      tenantId,
      projectType,
      startDate,
      endDate,
      projectSubType: projectType,
      department: defaultProject?.group,
      description:`${defaultProject?.name}, disease ${defaultProject?.group} campaign created through Admin Console with Campaign Id as ${CampaignDetails?.campaignNumber}`,
      projectTypeId: defaultProject?.id,
      name: campaignName,
      additionalDetails: {
        projectType: defaultProject,
      },
    },
  ];
};
