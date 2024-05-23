//migrated to mdms
export const deliveryConfig = [
  {
    projectType: "LLIN-mz",
    attrAddDisable: true,
    deliveryAddDisable: true,
    customAttribute: true,
    // attributeConfig: [
    //   {
    //     key: 1,
    //     label: "Custom",
    //     attrType: "text",
    //     attrValue: "CAMPAIGN_BEDNET_INDIVIDUAL_LABEL",
    //   },
    //   {
    //     key: 2,
    //     label: "Custom",
    //     attrType: "text",
    //     attrValue: "CAMPAIGN_BEDNET_HOUSEHOLD_LABEL",
    //   },
    // ],
    cycleConfig: {
      cycle: 1,
      deliveries: 1,
    },
    deliveryConfig: [
      {
        attributeConfig: [
          {
            key: 1,
            label: "Custom",
            attrType: "text",
            attrValue: "CAMPAIGN_BEDNET_INDIVIDUAL_LABEL",
            // operatorValue: "GREATER_THAN",
            // value: 2,
          },
          {
            key: 2,
            label: "Custom",
            attrType: "text",
            attrValue: "CAMPAIGN_BEDNET_HOUSEHOLD_LABEL",
            // operatorValue: "LESS_THAN",
            // value: 3,
          },
        ],
        productConfig: [
          {
            key: 1,
            count: 1,
            value: "PVAR-2024-02-09-000150",
            name: "Bednet - 500mg",
          },
        ],
      },
    ],
  },
  {
    projectType: "MR-DN",
    attrAddDisable: false,
    deliveryAddDisable: false,
    customAttribute: true,
    cycleConfig: {
      cycle: 3,
      deliveries: 2,
    },
    deliveryConfig: [
      {
        attributeConfig: [
          {
            key: 1,
            label: "Custom",
            attrType: "dropdown",
            attrValue: "Age",
            operatorValue: "GREATER_THAN",
            value: 10,
          },
          {
            key: 2,
            label: "Custom",
            attrType: "dropdown",
            attrValue: "Height",
            operatorValue: "LESS_THAN",
            value: 50,
          },
        ],
        productConfig: [
          {
            key: 1,
            count: 1,
            value: "PVAR-2024-02-19-000224",
            name: "paracetamol - 250mg",
          },
        ],
      },
      {
        attributeConfig: [
          {
            key: 1,
            label: "Custom",
            attrType: "dropdown",
            attrValue: "Age",
            operatorValue: "IN_BETWEEN",
            toValue: 10,
            fromValue: 20,
          },
        ],
        productConfig: [
          {
            key: 1,
            count: 1,
            value: "PVAR-2024-02-19-000224",
            name: "paracetamol - 250mg",
          },
        ],
      },
    ],
  },
];
