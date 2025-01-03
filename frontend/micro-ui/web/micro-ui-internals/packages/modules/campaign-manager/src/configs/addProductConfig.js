export const addProductConfig = [
  {
    body: [
      {
        type: "component",
        component: "AddProductField",
        withoutLabel: true,
        key: "addProduct",
        validation: {},
        populators: {
          validation: {},
        },
        customProps: {
          module: "Campaign",
        },
      },
    ],
  },
];
