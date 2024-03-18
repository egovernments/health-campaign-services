export const CampaignConfig = (totalFormData) => {
  return [
    {
      form: [
        {
          stepCount: "1",
          key: "1",
          body: [
            {
              isMandatory: false,
              key: "campaignDates",
              type: "component",
              component: "CampaignDates",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "campaignDates",
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "1",
          key: "2",
          body: [
            {
              isMandatory: false,
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
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "1",
          key: "3",
          name: "HCM_CAMPAIGN_NAME",
          body: [
            {
              isMandatory: false,
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
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "2",
          key: "4",
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
                error: "ES__REQUIRED",
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
              key: "campaignType",
              type: "component",
              component: "CampaignType",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
              },
              populators: {
                name: "campaignType",
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
      ],
    },
  ];
};
