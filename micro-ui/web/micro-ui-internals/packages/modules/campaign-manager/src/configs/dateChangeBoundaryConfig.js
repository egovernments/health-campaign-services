export const dateChangeBoundaryConfig = [
  {
    body: [
      {
        type: "component",
        component: "DateWithBoundary",
        withoutLabel: true,
        withoutLabelFieldPair: true,
        key: "dateWithBoundary",
        validation: {},
        populators: {
          validation: {},
        },
        customProps: {
          module: "Campaign",
        },
      },
      // {
      //   type: "component",
      //   component: "UpdateCampaignDates",
      //   withoutLabel: true,
      //   key: "campaignDates",
      //   validation: {},
      //   populators: {
      //     validation: {},
      //   },
      //   customProps: {
      //     module: "Campaign",
      //   },
      // },
    ],
  },
];

export const dateChangeConfig = [
  {
    body: [
      {
        type: "component",
        component: "DateAndCycleUpdate",
        withoutLabel: true,
        withoutLabelFieldPair: true,
        key: "dateAndCycle",
        validation: {},
        populators: {
          validation: {},
        },
        customProps: {
          module: "Campaign",
        },
      },
      // {
      //   type: "component",
      //   component: "UpdateCampaignDates",
      //   withoutLabel: true,
      //   key: "campaignDates",
      //   validation: {},
      //   populators: {
      //     validation: {},
      //   },
      //   customProps: {
      //     module: "Campaign",
      //   },
      // },
    ],
  },
];
