import React, { createContext, useContext, useEffect, useReducer, useState } from "react";
import MultiTab from "./MultiTabcontext";
import { Loader } from "@egovernments/digit-ui-react-components";
// import { deliveryConfig } from "../../../configs/deliveryConfig";

const CycleContext = createContext();

function makeSequential(jsonArray, keyName) {
  return jsonArray.map((item, index) => ({
    ...item,
    [keyName]: index + 1,
  }));
}

function DeliverySetup({ onSelect, config, formData, control, tabCount = 2, subTabCount = 3, ...props }) {
  // Campaign Tab Skeleton function
  const [cycleData, setCycleData] = useState(config?.customProps?.sessionData?.["HCM_CAMPAIGN_CYCLE_CONFIGURE"]?.cycleConfigure);
  const saved = window.Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA")?.HCM_CAMPAIGN_DELIVERY_DATA?.deliveryRule;
  const selectedProjectType = window.Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA")?.HCM_CAMPAIGN_TYPE?.projectType?.code;
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const searchParams = new URLSearchParams(location.search);
  const activeCycle = searchParams.get("activeCycle");
  const { isLoading: deliveryConfigLoading, data: filteredDeliveryConfig } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "deliveryConfig" }],
    {
      select: (data) => {
        const temp = data?.["HCM-ADMIN-CONSOLE"]?.deliveryConfig;
        return temp?.find((i) => i?.projectType === selectedProjectType);
        // return deliveryConfig?.find((i) => i?.projectType === selectedProjectType);
      },
    }
  );
  // const [filteredDeliveryConfig, setFilteredDeliveryConfig] = useState(deliveryConfig?.find((i) => i?.projectType === selectedProjectType));
  // useEffect(() => {
  // if (!deliveryConfigLoading) {
  // const temp = deliveryConfig?.find((i) => i?.projectType === selectedProjectType);
  // setFilteredDeliveryConfig(temp);
  // }
  // }, [deliveryConfigLoading, filteredDeliveryConfig]);
  // const filteredDeliveryConfig = deliveryConfig.find((i) => i.projectType === selectedProjectType);
  useEffect(() => {
    setCycleData(config?.customProps?.sessionData?.["HCM_CAMPAIGN_CYCLE_CONFIGURE"]?.cycleConfigure);
  }, [config?.customProps?.sessionData?.["HCM_CAMPAIGN_CYCLE_CONFIGURE"]?.cycleConfigure]);

  const generateTabsData = (tabs, subTabs) => {
    if (!saved || saved?.length === 0) {
      return [...Array(tabs)].map((_, tabIndex) => ({
        cycleIndex: `${tabIndex + 1}`,
        active: activeCycle == tabIndex + 1 ? true : tabIndex === 0 ? true : false,
        deliveries: [...Array(subTabs || 1)].map((_, subTabIndex) => ({
          deliveryIndex: `${subTabIndex + 1}`,
          active: subTabIndex === 0 ? true : false,
          deliveryRules:
            filteredDeliveryConfig?.projectType === "LLIN-mz"
              ? filteredDeliveryConfig?.deliveryConfig?.map((item, index) => {
                  return {
                    ruleKey: index + 1,
                    delivery: {},
                    attributes: item?.attributeConfig
                      ? item?.attributeConfig?.map((i, c) => {
                          if (i?.operatorValue === "IN_BETWEEN") {
                            return {
                              key: c + 1,
                              attribute: { code: i?.attrValue },
                              operator: { code: i?.operatorValue },
                              toValue: i?.fromValue,
                              fromValue: i?.toValue,
                            };
                          }
                          return {
                            key: c + 1,
                            attribute: { code: i?.attrValue },
                            operator: { code: i?.operatorValue },
                            value: i?.value,
                          };
                        })
                      : [{ key: 1, attribute: null, operator: null, value: "" }],
                    // products: [],
                    products: item?.productConfig
                      ? item?.productConfig?.map((i, c) => ({
                          ...i,
                        }))
                      : [],
                  };
                })
              : filteredDeliveryConfig && filteredDeliveryConfig?.deliveryConfig?.[subTabIndex]
              ? filteredDeliveryConfig?.deliveryConfig?.[subTabIndex]?.conditionConfig?.map((item, index) => {
                  if (item) {
                    return {
                      ruleKey: index + 1,
                      delivery: {},
                      deliveryType: item?.deliveryType,
                      attributes: item?.attributeConfig
                        ? item?.attributeConfig?.map((i, c) => {
                            if (i?.operatorValue === "IN_BETWEEN") {
                              return {
                                key: c + 1,
                                attribute: { code: i?.attrValue },
                                operator: { code: i?.operatorValue },
                                toValue: i?.fromValue,
                                fromValue: i?.toValue,
                              };
                            }
                            return {
                              key: c + 1,
                              attribute: { code: i?.attrValue },
                              operator: { code: i?.operatorValue },
                              value: i?.value,
                            };
                          })
                        : [{ key: 1, attribute: null, operator: null, value: "" }],
                      // products: [],
                      products: item?.productConfig
                        ? item?.productConfig?.map((i, c) => ({
                            ...i,
                          }))
                        : [],
                    };
                  } else {
                    return {
                      ruleKey: index + 1,
                      delivery: {},
                      deliveryType: null,
                      attributes: [{ key: 1, attribute: null, operator: null, value: "" }],
                      products: [],
                    };
                  }
                })
              : [
                  {
                    ruleKey: 1,
                    delivery: {},
                    attributes:
                      filteredDeliveryConfig && filteredDeliveryConfig?.attributeConfig
                        ? filteredDeliveryConfig?.attributeConfig?.map((i, c) => ({
                            key: c + 1,
                            attribute: { code: i?.attrValue },
                            operator: { code: i?.operatorValue },
                            value: i?.value,
                          }))
                        : // : filteredDeliveryConfig?.projectType === "LLIN-mz"
                          // ? filteredDeliveryConfig?.attributeConfig?.map((i, c) => ({ key: c + 1, attribute: i.attrValue, operator: null, value: "" }))
                          [{ key: 1, attribute: null, operator: null, value: "" }],
                    products: [],
                  },
                ],
        })),
      }));
    }
    // if no change
    if (saved && saved?.length == tabs && saved?.[0]?.deliveries?.length === subTabs) {
      return saved.map((i, n) => {
        return {
          ...i,
          active: activeCycle ? (activeCycle == n + 1 ? true : false) : n === 0 ? true : false,
        };
      });
    }
    // if cycle number decrease
    if (saved?.length > tabs) {
      // const temp = saved;
      saved.splice(tabs);
      // return temp;
    }
    // if cycle number increase
    if (tabs > saved?.length) {
      // const temp = saved;
      for (let i = saved.length + 1; i <= tabs; i++) {
        const newIndex = i.toString();
        saved.push({
          cycleIndex: newIndex,
          active: false,
          deliveries: [...Array(subTabs || 1)].map((_, subTabIndex) => ({
            deliveryIndex: `${subTabIndex + 1}`,
            active: subTabIndex === 0,
            deliveryRules:
              filteredDeliveryConfig?.projectType === "LLIN-mz"
                ? filteredDeliveryConfig?.deliveryConfig?.map((item, index) => {
                    return {
                      ruleKey: index + 1,
                      delivery: {},
                      attributes: item?.attributeConfig
                        ? item?.attributeConfig?.map((i, c) => {
                            if (i?.operatorValue === "IN_BETWEEN") {
                              return {
                                key: c + 1,
                                attribute: { code: i?.attrValue },
                                operator: { code: i?.operatorValue },
                                toValue: i?.fromValue,
                                fromValue: i?.toValue,
                              };
                            }
                            return {
                              key: c + 1,
                              attribute: { code: i?.attrValue },
                              operator: { code: i?.operatorValue },
                              value: i?.value,
                            };
                          })
                        : [{ key: 1, attribute: null, operator: null, value: "" }],
                      // products: [],
                      products: item?.productConfig
                        ? item?.productConfig?.map((i, c) => ({
                            ...i,
                          }))
                        : [],
                    };
                  })
                : filteredDeliveryConfig && filteredDeliveryConfig?.deliveryConfig?.[subTabIndex]?.conditionConfig
                ? filteredDeliveryConfig?.deliveryConfig?.[subTabIndex]?.conditionConfig?.map((item, index) => {
                    if (item) {
                      return {
                        ruleKey: index + 1,
                        delivery: {},
                        deliveryType: item?.deliveryType,
                        attributes: item?.attributeConfig
                          ? item?.attributeConfig?.map((i, c) => {
                              if (i?.operatorValue === "IN_BETWEEN") {
                                return {
                                  key: c + 1,
                                  attribute: { code: i?.attrValue },
                                  operator: { code: i?.operatorValue },
                                  toValue: i?.fromValue,
                                  fromValue: i?.toValue,
                                };
                              }
                              return {
                                key: c + 1,
                                attribute: { code: i?.attrValue },
                                operator: { code: i?.operatorValue },
                                value: i?.value,
                              };
                            })
                          : [{ key: 1, attribute: null, operator: null, value: "" }],
                        // products: [],
                        products: item?.productConfig
                          ? item?.productConfig?.map((i, c) => ({
                              ...i,
                            }))
                          : [],
                      };
                    } else {
                      return {
                        ruleKey: index + 1,
                        delivery: {},
                        deliveryType: null,
                        attributes: [{ key: 1, attribute: null, operator: null, value: "" }],
                        products: [],
                      };
                    }
                  })
                : [
                    {
                      ruleKey: 1,
                      delivery: {},
                      deliveryType: null,
                      attributes:
                        // filteredDeliveryConfig?.projectType === "MR-DN"
                        //   ? filteredDeliveryConfig?.attributeConfig?.map((i, c) => ({
                        //       key: c + 1,
                        //       attribute: { code: i?.attrValue },
                        //       operator: { code: i?.operatorValue },
                        //       value: i?.value,
                        //     }))
                        //   : filteredDeliveryConfig?.projectType === "LLIN-mz"
                        //   ? filteredDeliveryConfig?.attributeConfig?.map((i, c) => ({
                        //       key: c + 1,
                        //       attribute: i.attrValue,
                        //       operator: null,
                        //       value: "",
                        //     }))
                        // :
                        [{ key: 1, attribute: null, operator: null, value: "" }],
                      // products: [],
                      products: [],
                    },
                  ],
          })),
        });
      }
      // return temp;
    }
    // if delivery number decrease

    saved.forEach((cycle) => {
      // Remove deliveries if there are more deliveries than the specified number
      if (cycle.deliveries.length > subTabs) {
        cycle.deliveries.splice(subTabs);
      }

      // Add deliveries if there are fewer deliveries than the specified number
      if (subTabs > cycle.deliveries.length) {
        for (let i = cycle.deliveries.length + 1; i <= subTabs; i++) {
          const newIndex = i.toString();
          cycle.deliveries.push({
            deliveryIndex: newIndex,
            active: false,
            deliveryRules:
              filteredDeliveryConfig?.projectType === "LLIN-mz"
                ? filteredDeliveryConfig?.deliveryConfig?.map((item, index) => {
                    return {
                      ruleKey: index + 1,
                      delivery: {},
                      attributes: item?.attributeConfig
                        ? item?.attributeConfig?.map((i, c) => {
                            if (i?.operatorValue === "IN_BETWEEN") {
                              return {
                                key: c + 1,
                                attribute: { code: i?.attrValue },
                                operator: { code: i?.operatorValue },
                                toValue: i?.fromValue,
                                fromValue: i?.toValue,
                              };
                            }
                            return {
                              key: c + 1,
                              attribute: { code: i?.attrValue },
                              operator: { code: i?.operatorValue },
                              value: i?.value,
                            };
                          })
                        : [{ key: 1, attribute: null, operator: null, value: "" }],
                      // products: [],
                      products: item?.productConfig
                        ? item?.productConfig?.map((i, c) => ({
                            ...i,
                          }))
                        : [],
                    };
                  })
                : [
                    {
                      ruleKey: 1,
                      delivery: {},
                      attributes: [{ key: 1, attribute: null, operator: null, value: "" }],
                      products: [],
                    },
                  ],
          });
        }
      }
    });

    return saved;
    // if delivery number increase

    //if no above case
  };

  // Reducer function
  const campaignDataReducer = (state, action) => {
    switch (action.type) {
      case "GENERATE_CAMPAIGN_DATA":
        return generateTabsData(action.cycle, action.deliveries);
      case "UPDATE_CAMPAIGN_DATA":
        const changeUpdate = state.map((i) => {
          if (i.active) {
            const activeDelivery = i.deliveries.find((j) => j.active === true);
            if (activeDelivery) {
              return {
                ...i,
                deliveries: i.deliveries.map((j) => ({
                  ...j,
                  deliveryRules: j.active ? action.payload.currentDeliveryRules : j.deliveryRules,
                })),
              };
            }
          }
          return i;
        });
        return changeUpdate;
      case "TAB_CHANGE_UPDATE":
        const temp = state.map((i) => ({
          ...i,
          active: i.cycleIndex == action.payload.tabIndex ? true : false,
        }));
        return temp;
      // return action.payload;
      case "SUBTAB_CHANGE_UPDATE":
        const tempSub = state.map((camp, index) => {
          if (camp.active === true) {
            return {
              ...camp,
              deliveries: camp.deliveries.map((deliver) => ({
                ...deliver,
                active: deliver.deliveryIndex == action.payload.subTabIndex ? true : false,
              })),
            };
          }
          return camp;
        });
        return tempSub;
      case "ADD_DELIVERY_RULE":
        const updatedDeliveryRules = [
          ...action.payload.currentDeliveryRules,
          {
            ruleKey: action.payload.currentDeliveryRules.length + 1,
            delivery: {},
            attributes: [{ key: 1, attribute: null, operator: null, value: "" }],
            products: [],
          },
        ];
        const updatedData = state.map((i) => {
          if (i.active) {
            const activeDelivery = i.deliveries.find((j) => j.active);
            if (activeDelivery) {
              return {
                ...i,
                deliveries: i.deliveries.map((j) => ({
                  ...j,
                  deliveryRules: j.active ? updatedDeliveryRules : j.deliveryRules,
                })),
              };
            }
          }
          return i;
        });
        return updatedData;
      case "REMOVE_DELIVERY_RULE":
        const updatedDeleted = state.map((i) => {
          if (i.active) {
            const activeDelivery = i.deliveries.find((j) => j.active);
            const w = makeSequential(
              activeDelivery.deliveryRules.filter((j) => j.ruleKey != action.payload.item.ruleKey),
              "ruleKey"
            );
            if (activeDelivery) {
              return {
                ...i,
                deliveries: i.deliveries.map((j) => ({
                  ...j,
                  deliveryRules: j.active ? w : j.deliveryRules,
                })),
              };
            }
          }
          return i;
        });
        return updatedDeleted;
      case "UPDATE_DELIVERY_RULE":
        return action.payload;
      case "ADD_ATTRIBUTE":
        return action.payload;
      case "REMOVE_ATTRIBUTE":
        return action.payload;
      case "UPDATE_ATTRIBUTE":
        return action.payload;
      case "ADD_PRODUCT":
        const prodTemp = action.payload.productData.map((i) => ({ ...i, value: i?.value?.id, name: i?.value?.displayName }));
        const updatedState = state.map((cycle) => {
          if (cycle.active) {
            const updatedDeliveries = cycle.deliveries.map((dd) => {
              if (dd.active) {
                const updatedRules = dd.deliveryRules.map((rule) => {
                  if (rule.ruleKey === action.payload.delivery.ruleKey) {
                    return {
                      ...rule,
                      products: [...rule.products, ...prodTemp],
                    };
                  }
                  return rule;
                });
                return {
                  ...dd,
                  deliveryRules: updatedRules,
                };
              }
              return dd;
            });
            return {
              ...cycle,
              deliveries: updatedDeliveries,
            };
          }
          return cycle;
        });
        return updatedState;
      case "REMOVE_PRODUCT":
        return action.payload;
      case "UPDATE_PRODUCT":
        return action.payload;
      default:
        return state;
    }
  };

  const [campaignData, dispatchCampaignData] = useReducer(
    campaignDataReducer,
    generateTabsData(cycleData?.cycleConfgureDate?.cycle, cycleData?.cycleConfgureDate?.deliveries)
  );
  const [executionCount, setExecutionCount] = useState(0);

  useEffect(() => {
    dispatchCampaignData({
      type: "GENERATE_CAMPAIGN_DATA",
      cycle: cycleData?.cycleConfgureDate?.cycle,
      deliveries: cycleData?.cycleConfgureDate?.deliveries,
    });
  }, [cycleData]);

  useEffect(() => {
    onSelect("deliveryRule", campaignData);
  }, [campaignData]);

  useEffect(() => {
    if (executionCount < 5) {
      onSelect("deliveryRule", campaignData);
      setExecutionCount((prevCount) => prevCount + 1);
    }
  });

  if (deliveryConfigLoading) {
    return <Loader />;
  }
  return (
    <CycleContext.Provider
      value={{
        campaignData,
        dispatchCampaignData,
        filteredDeliveryConfig,
      }}
    >
      <MultiTab />
    </CycleContext.Provider>
  );
}

export default DeliverySetup;
export { CycleContext };
