import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";

export const data = (project) => {
  const { t } = useTranslation();
  // console.log("data",project?.Project?.[0]?.id);

  return {
    cards: [
      {
        sections: [
          {
            type: "DATA",
            values: [
              {
                key: "WORKBENCH_PROJECT_NUMBER",
                value: project?.Project?.[0]?.projectNumber,
              },
              {
                key: "WORKBENCH_PROJECT_NAME",
                value: project?.Project?.[0]?.name,
              },
              {
                key: "WORKBENCH_PROJECT_TYPE",
                value: project?.Project?.[0]?.projectType,
              },
              {
                key: "WORKBENCH_PROJECT_START_DATE",
                value: Digit.DateUtils.ConvertEpochToDate(project?.Project?.[0]?.startDate),
              },
              {
                key: "WORKBENCH_PROJECT_END_DATE",
                value: Digit.DateUtils.ConvertEpochToDate(project?.Project?.[0]?.endDate),
              },
              {
                key: "WORKBENCH_PROJECT_PRIMARY",
                value: project?.Project?.[0]?.targets?.[0]?.beneficiaryType,
              },
              {
                key: "WORKBENCH_PROJECT_PARENT_PROJECT_NUMBER",
                value: project?.Project?.[0]?.ancestors?.[0]?.projectNumber || "null",
              },
              {
                key: "WORKBENCH_PROJECT_PRIMARY_TARGET_NO",
                value: project?.Project?.[0]?.targets?.[0]?.targetNo,
              },
              {
                key: "WORKBENCH_PROJECT_PRIMARY_TOTAL_NO",
                value: project?.Project?.[0]?.targets?.[0]?.totalNo,
              },
            ],
          },
        ],
      },

      {
        navigationKey: "card2",
        sections: [
          {
            navigationKey: "card2",
            type: "COMPONENT",
            component: "ProjectBeneficiaryComponent",
            props: { projectId: project?.Project?.[0]?.id },
            // values: [
            //   {
            //     navigationKey: "card1",
            //     sections: [
            //       {
            //         type: "DATA",
            //         sectionHeader: { value: "WORKS_PROJECT_DETAILS", inlineStyles: {marginBottom : "16px", marginTop:"32px", fontSize: "24px"} },
            //         values: [
            //           {
            //             key: "WORKS_ESTIMATE_TYPE",
            //             value:  "ORIGINAL_ESTIMATE",
            //           }
            //         ],
            //       },
            //       {
            //         type: "DATA",
            //         sectionHeader: { value: "WORKS_LOCATION_DETAILS", inlineStyles: {marginBottom : "16px", marginTop:"32px", fontSize: "24px"} },
            //         values: [
            //           {
            //             key: "WORKS_ESTIMATE_TYPE",
            //             value:  "ORIGINAL_ESTIMATE",
            //           }
            //           ],
            //       },
            //     ],
            //   }
            // ]
          },
        ],
      },
      {
        navigationKey: "card1",
        sections: [
          {
            navigationKey: "card1",

            type: "COMPONENT",
            component: "ProjectStaffComponent",
            props: { projectId: project?.Project?.[0]?.id },
          },
        ],
      },
      {
        navigationKey: "card3",
        sections: [
          {
            navigationKey: "card3",

            type: "COMPONENT",
            component: "ProjectChildrenComponent",
            props: { projectId: project?.Project?.[0]?.id },
          },
        ],
      },
    ],
    apiResponse: {},
    additionalDetails: {},
    horizontalNav: {
      showNav: true,
      configNavItems: [
        {
          name: "card2",
          active: true,
          code: "Project Resource",
        },
        {
          name: "card1",
          active: true,
          code: "Project Staff",
        },
        {
          name: "card3",
          active: true,
          code: "Children",
        },
      ],
      activeByDefault: "card1",
    },
  };
};
