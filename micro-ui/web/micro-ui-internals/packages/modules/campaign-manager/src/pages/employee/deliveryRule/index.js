import React, { createContext, useContext, useEffect, useReducer, useState } from "react";
import MultiTab from "./MultiTabcontext";

const CycleContext = createContext();

function makeSequential(jsonArray, keyName) {
  return jsonArray.map((item, index) => ({
    ...item,
    [keyName]: index + 1,
  }));
}

function DeliverySetup({ tabCount = 3, subTabCount = 2 }) {
  // Campaign Tab Skeleton function
  const generateTabsData = (tabs, subTabs) => {
    return [...Array(tabCount)].map((_, tabIndex) => ({
      cycleIndex: `${tabIndex + 1}`,
      active: tabIndex === 0 ? true : false,
      deliveries: [...Array(subTabCount)].map((_, subTabIndex) => ({
        deliveryIndex: `${subTabIndex + 1}`,
        active: subTabIndex === 0 ? true : false,
        deliveryRules: [
          {
            ruleKey: 1,
            delivery: {},
            attributes: [{ key: 1, attribute: null, operator: null, value: "" }],
            products: [],
          },
        ],
      })),
    }));
  };

  // Reducer function
  const campaignDataReducer = (state, action) => {
    switch (action.type) {
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
                      products: prodTemp,
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

  const [campaignData, dispatchCampaignData] = useReducer(campaignDataReducer, generateTabsData());

  return (
    <CycleContext.Provider value={{ campaignData, dispatchCampaignData }}>
      <MultiTab />
    </CycleContext.Provider>
  );
}

export default DeliverySetup;
export { CycleContext };
