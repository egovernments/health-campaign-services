export const CampaignConfig = (totalFormData) => {
  return [
    {
      form: [
        {
          stepCount: "1",
          key: "1",
          name: "HCM_CAMPAIGN_TYPE",
          body: [
            {
              isMandatory: true,
              key: "projectType",
              type: "component",
              component: "CampaignType",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
              },
              populators: {
                name: "projectType",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "1",
          key: "2",
          name: "HCM_CAMPAIGN_NAME",
          body: [
            {
              isMandatory: true,
              key: "campaignName",
              type: "component",
              component: "CampaignName",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
              },
              populators: {
                name: "campaignName",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "1",
          key: "3",
          name: "HCM_CAMPAIGN_DATE",
          body: [
            {
              isMandatory: true,
              key: "campaignDates",
              type: "component",
              component: "CampaignDates",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData
              },
              populators: {
                name: "campaignDates",
                // optionsKey: "code",
                // error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "2",
          key: "4",
          name: "HCM_CAMPAIGN_CYCLE_CONFIGURE",
          body: [
            {
              isMandatory: false,
              key: "cycleConfigure",
              type: "component",
              component: "CycleConfiguration",
              withoutLabelFieldPair: true,
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "cycleConfiguration",
                sessionData: totalFormData,
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "2",
          key: "5",
          name: "HCM_CAMPAIGN_DELIVERY_DATA",
          body: [
            {
              isMandatory: false,
              key: "deliveryRule",
              type: "component",
              component: "DeliveryRule",
              withoutLabelFieldPair: true,
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
              },
              populators: {
                name: "deliveryRule",
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "3",
          key: "6",
          body: [
            {
              isMandatory: false,
              key: "campaignName3",
              type: "text",
              withoutLabel: false,
              label: "boundary",
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "campaignName3",
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "4",
          key: "7",
          body: [
            {
              isMandatory: false,
              key: "uploadBoundary",
              type: "component",
              component: "UploadBoundaryData",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "uploadBoundary",
                // optionsKey: "code",
                // error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "5",
          key: "8",
          body: [
            {
              isMandatory: false,
              key: "uploadFacility",
              type: "component",
              component: "UploadFacilityData",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "uploadFacility",
                // optionsKey: "code",
                // error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "7",
          key: "9",
          isLast: true,
          body: [
            {
              isMandatory: false,
              key: "summary",
              type: "component",
              component: "CampaignSummary",
              withoutLabel: true,
              withoutLabelFieldPair: true,
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "summary",
                // optionsKey: "code",
                // error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
      ],
    },
  ];
};
