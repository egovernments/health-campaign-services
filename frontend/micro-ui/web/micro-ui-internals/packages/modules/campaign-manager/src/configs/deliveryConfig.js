//migrated to mdms
export const deliveryConfig = [
  {
    projectType: "LLIN-mz",
    attrAddDisable: true,
    deliveryAddDisable: true,
    customAttribute: true,
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
          },
          {
            key: 2,
            label: "Custom",
            attrType: "text",
            attrValue: "CAMPAIGN_BEDNET_HOUSEHOLD_LABEL",
          },
        ],
        productConfig: [
          {
            key: 1,
            count: 1,
            value: "PVAR-2024-05-03-000305",
            name: "SP - 250mg",
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
      deliveries: 3,
    },
    deliveryConfig: [
      {
        delivery: 1,
        conditionConfig: [
          {
            deliveryType: "DIRECT",
            disableDeliveryType: true,
            attributeConfig: [
              {
                key: 1,
                label: "Custom",
                attrType: "dropdown",
                attrValue: "Age",
                operatorValue: "IN_BETWEEN",
                fromValue: 3,
                toValue: 11,
              },
            ],
            productConfig: [
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-01-24-000079",
                name: "AQ - 75mg",
              },
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-05-03-000305",
                name: "SP - 250mg",
              },
            ],
          },
          {
            disableDeliveryType: true,
            deliveryType: "DIRECT",
            attributeConfig: [
              {
                key: 1,
                label: "Custom",
                attrType: "dropdown",
                attrValue: "Age",
                operatorValue: "IN_BETWEEN",
                fromValue: 12,
                toValue: 59,
              },
            ],
            productConfig: [
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-01-24-000078",
                name: "AQ - 150mg",
              },
            ],
          },
        ],
      },
      {
        delivery: 2,
        conditionConfig: [
          {
            deliveryType: "INDIRECT",
            attributeConfig: [
              {
                key: 1,
                label: "Custom",
                attrType: "dropdown",
                attrValue: "Age",
                operatorValue: "IN_BETWEEN",
                fromValue: 3,
                toValue: 11,
              },
            ],
            productConfig: [
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-01-24-000079",
                name: "AQ - 75mg",
              },
            ],
          },
          {
            deliveryType: "INDIRECT",
            attributeConfig: [
              {
                key: 1,
                label: "Custom",
                attrType: "dropdown",
                attrValue: "Age",
                operatorValue: "IN_BETWEEN",
                fromValue: 12,
                toValue: 59,
              },
            ],
            productConfig: [
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-01-24-000078",
                name: "AQ - 150mg",
              },
            ],
          },
        ],
      },
      {
        delivery: 3,
        conditionConfig: [
          {
            deliveryType: "INDIRECT",
            attributeConfig: [
              {
                key: 1,
                label: "Custom",
                attrType: "dropdown",
                attrValue: "Age",
                operatorValue: "IN_BETWEEN",
                fromValue: 3,
                toValue: 11,
              },
            ],
            productConfig: [
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-01-24-000079",
                name: "AQ - 75mg",
              },
            ],
          },
          {
            deliveryType: "INDIRECT",
            attributeConfig: [
              {
                key: 1,
                label: "Custom",
                attrType: "dropdown",
                attrValue: "Age",
                operatorValue: "IN_BETWEEN",
                fromValue: 12,
                toValue: 59,
              },
            ],
            productConfig: [
              {
                key: 1,
                count: 1,
                value: "PVAR-2024-01-24-000078",
                name: "AQ - 150mg",
              },
            ],
          },
        ],
      },
    ],
  },
];
