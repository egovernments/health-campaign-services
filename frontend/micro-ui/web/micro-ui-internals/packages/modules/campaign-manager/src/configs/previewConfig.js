export const previewConfig = {
  HCM_CAMPAIGN_DATE: {
    campaignDates: {
      startDate: "2024-04-01",
      endDate: "2024-04-26",
    },
  },
  HCM_CAMPAIGN_TYPE: {
    projectType: {
      id: "84e28a7b-8c6d-4505-a7ef-5d714e0361f6",
      name: "mz project type configuration for LLIN Campaigns",
      code: "LLIN-mz",
      group: "MALARIA",
      beneficiaryType: "HOUSEHOLD",
      eligibilityCriteria: ["All households are eligible.", "Prison inmates are eligible."],
      dashboardUrls: {
        NATIONAL_SUPERVISOR: "/digit-ui/employee/dss/landing/national-health-dashboard",
        PROVINCIAL_SUPERVISOR: "/digit-ui/employee/dss/dashboard/provincial-health-dashboard",
        DISTRICT_SUPERVISOR: "/digit-ui/employee/dss/dashboard/district-health-dashboard",
      },
      taskProcedure: [
        "1 bednet is to be distributed per 2 household members.",
        "If there are 4 household members, 2 bednets should be distributed.",
        "If there are 5 household members, 3 bednets should be distributed.",
      ],
      resources: [
        {
          productVariantId: "PVAR-2024-02-13-000211",
          isBaseUnitVariant: false,
        },
      ],
    },
  },
  HCM_CAMPAIGN_NAME: {
    campaignName: "sdkfkdhsdjkhkjsdh",
  },
  HCM_CAMPAIGN_CYCLE_CONFIGURE: {
    cycleConfigure: {
      cycleConfgureDate: {
        cycle: 2,
        deliveries: 1,
      },
      cycleData: [
        {
          key: 1,
          fromDate: "2001-12-12",
          toDate: "2002-11-11",
        },
        {
          key: 2,
          fromDate: "2002-01-12",
          toDate: "2011-11-11",
        },
      ],
    },
  },
  HCM_CAMPAIGN_DELIVERY_DATA: {
    deliveryRule: [
      {
        cycleIndex: "1",
        active: false,
        deliveries: [
          {
            deliveryIndex: "1",
            active: true,
            deliveryRules: [
              {
                ruleKey: 1,
                delivery: {},
                attributes: [
                  {
                    key: 1,
                    attribute: {
                      key: 1,
                      code: "Age",
                    },
                    operator: {
                      key: 1,
                      code: "LESS_THAN",
                    },
                    value: "576",
                  },
                ],
                products: [],
              },
            ],
          },
        ],
      },
      {
        cycleIndex: "2",
        active: true,
        deliveries: [
          {
            deliveryIndex: "1",
            active: true,
            deliveryRules: [
              {
                ruleKey: 1,
                delivery: {},
                attributes: [
                  {
                    key: 1,
                    attribute: {
                      key: 1,
                      code: "Age",
                    },
                    operator: {
                      key: 2,
                      code: "GREATER_THAN",
                    },
                    value: "65",
                  },
                ],
                products: [],
              },
            ],
          },
        ],
      },
    ],
  },
  undefined: {},
};
