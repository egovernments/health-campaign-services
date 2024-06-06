export const CampaignConfig = (totalFormData, dataParams, isSubmitting, summaryErrors) => {
  return [
    {
      form: [
        {
          stepCount: "1",
          key: "1",
          name: "HCM_CAMPAIGN_TYPE",
          body: [
            {
              isMandatory: false,
              key: "projectType",
              type: "component",
              skipAPICall: true,
              component: "CampaignSelection",
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
                isSubmitting: isSubmitting,
              },
              populators: {
                name: "projectType",
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
              isMandatory: false,
              key: "campaignName",
              type: "component",
              component: "CampaignName",
              mandatoryOnAPI: true,
              withoutLabel: true,
              withoutLabelFieldPair: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
                isSubmitting: isSubmitting,
              },
              populators: {
                name: "campaignName",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "2",
          key: "3",
          name: "HCM_CAMPAIGN_SELECTING_BOUNDARY_DATA",
          body: [
            {
              isMandatory: false,
              key: "boundaryType",
              type: "component",
              component: "SelectingBoundaries",
              withoutLabelFieldPair: true,
              withoutLabel: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
                dataParams: dataParams,
              },
              populators: {
                name: "boundaryType",
                // optionsKey: "code",
                error: "ES__REQUIRED",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "3",
          key: "4",
          name: "HCM_CAMPAIGN_DATE",
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
                sessionData: totalFormData,
                isSubmitting: isSubmitting,
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
          stepCount: "3",
          key: "5",
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
          stepCount: "3",
          key: "6",
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
          stepCount: "4",
          key: "7",
          name: "HCM_CAMPAIGN_UPLOAD_BOUNDARY_DATA",
          body: [
            {
              isMandatory: false,
              key: "uploadBoundary",
              type: "component",
              component: "UploadData",
              withoutLabel: true,
              withoutLabelFieldPair: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
                type: "boundary",
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
          name: "HCM_CAMPAIGN_UPLOAD_FACILITY_DATA",
          body: [
            {
              isMandatory: false,
              key: "uploadFacility",
              type: "component",
              component: "UploadData",
              withoutLabel: true,
              withoutLabelFieldPair: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
                type: "facilityWithBoundary",
              },
              populators: {
                name: "uploadFacility",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "6",
          key: "9",
          name: "HCM_CAMPAIGN_UPLOAD_USER_DATA",
          body: [
            {
              isMandatory: false,
              key: "uploadUser",
              type: "component",
              component: "UploadData",
              withoutLabel: true,
              withoutLabelFieldPair: true,
              disable: false,
              customProps: {
                module: "HCM",
                sessionData: totalFormData,
                type: "userWithBoundary",
              },
              populators: {
                name: "uploadUser",
                required: true,
              },
            },
          ],
        },
        {
          stepCount: "7",
          key: "10",
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
                sessionData: totalFormData,
                summaryErrors: summaryErrors
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
