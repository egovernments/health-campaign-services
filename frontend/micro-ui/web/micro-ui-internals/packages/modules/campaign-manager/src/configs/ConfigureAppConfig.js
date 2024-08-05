export const ConfigureAppConfig = (totalFormData) => {
    return [
      {
        form: [
          {
            key: "1",
            name: "HCM_CAMPAIGN_SIDE_EFFECTS",
            body: [
              {
                isMandatory: false,
                key: "projectType",
                type: "component",
                // skipAPICall: true,
                component: "SideEffects",
                withoutLabel: true,
                disable: false,
                customProps: {
                  module: "HCM",
                  sessionData: totalFormData,
                },
                populators: {
                  name: "SideEffects",
                },
              },
              
            ],
          },
          {
            key: "2",
            name: "HCM_SIDE_EFFECT_TYPE",
            body: [
                {
                    isMandatory: false,
                    key: "sideEffectType",
                    type: "component",
                    // skipAPICall: true,
                    component: "SideEffectType",
                    withoutLabel: true,
                    disable: false,
                    customProps: {
                      module: "HCM",
                      sessionData: totalFormData,
                    },
                    populators: {
                      name: "sideEffectType",
                    },
                  },
              
            ],
          },
        ],
      },
    ];
  };
  