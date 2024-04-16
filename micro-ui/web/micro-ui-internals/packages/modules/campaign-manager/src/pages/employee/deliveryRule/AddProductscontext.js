import { AddIcon, Button, CardText, Dropdown, DustbinIcon, Label, LabelFieldPair } from "@egovernments/digit-ui-react-components";
import React, { Fragment, useEffect, useState } from "react";
import PlusMinusInput from "../../../components/PlusMinusInput";
import { useTranslation } from "react-i18next";
import { TextInput } from "@egovernments/digit-ui-components";

function AddProducts({ stref, selectedDelivery }) {
  const { t } = useTranslation();
  const [products, setProducts] = useState([
    {
      key: 1,
      count: 1,
      value: null,
    },
  ]);
  const data = Digit.Hooks.campaign.useProductList();
  // const onDeleteProduct = (i, c) => {
  //   const updatedProducts = selectedDelivery.products.filter((product) => product.key !== i.key);
  //   const updatedProductsSequentialKeys = updatedProducts.map((product, index) => ({ ...product, key: index + 1 }));
  //   const updatedDelivery = { ...selectedDelivery, products: updatedProductsSequentialKeys };
  //   const updatedCampaignData = campaignData.map((cycle) => {
  //     if (cycle.active) {
  //       cycle.deliveries.forEach((delivery) => {
  //         if (delivery.active) {
  //           const deliveryRule = delivery.deliveryRules.find((rule) => rule.ruleKey === updatedDelivery.ruleKey);
  //           if (deliveryRule) {
  //             // Update the delivery rule with the updated delivery
  //             deliveryRule.products = updatedDelivery.products;
  //           }
  //         }
  //       });
  //     }
  //     return cycle;
  //   });
  //   setCampaignData(updatedCampaignData);
  // };

  // const incrementCount = (item, d) => {
  //   const updatedProducts = selectedDelivery.products.map((i) => {
  //     if (i.key === item.key) {
  //       return {
  //         ...i,
  //         count: d,
  //       };
  //     }
  //     return i;
  //   });
  //   const updatedDelivery = { ...selectedDelivery, products: updatedProducts };
  //   const updatedCampaignData = campaignData.map((cycle) => {
  //     if (cycle.active) {
  //       cycle.deliveries.forEach((delivery) => {
  //         if (delivery.active) {
  //           const deliveryRule = delivery.deliveryRules.find((rule) => rule.ruleKey === updatedDelivery.ruleKey);
  //           if (deliveryRule) {
  //             // Update the delivery rule with the updated delivery
  //             deliveryRule.products = updatedDelivery.products;
  //           }
  //         }
  //       });
  //     }
  //     return cycle;
  //   });
  //   setCampaignData(updatedCampaignData);
  //   setProducts(updatedProducts);
  // };

  // const updatedProductValue = (item, d) => {
  //   const updatedProducts = selectedDelivery.products.map((i) => {
  //     if (i.key === item.key) {
  //       return {
  //         ...i,
  //         value: d.id,
  //       };
  //     }
  //     return i;
  //   });
  //   const updatedDelivery = { ...selectedDelivery, products: updatedProducts };
  //   const updatedCampaignData = campaignData.map((cycle) => {
  //     if (cycle.active) {
  //       cycle.deliveries.forEach((delivery) => {
  //         if (delivery.active) {
  //           const deliveryRule = delivery.deliveryRules.find((rule) => rule.ruleKey === updatedDelivery.ruleKey);
  //           if (deliveryRule) {
  //             // Update the delivery rule with the updated delivery
  //             deliveryRule.products = updatedDelivery.products;
  //           }
  //         }
  //       });
  //     }
  //     return cycle;
  //   });
  //   setCampaignData(updatedCampaignData);
  //   setProducts(updatedProducts);
  // };

  // const addMoreResource = () => {
  //   const updatedState = campaignData.map((cycle) => {
  //     if (cycle.active) {
  //       const updatedDeliveries = cycle.deliveries.map((dd) => {
  //         if (dd.active) {
  //           const updatedRules = dd.deliveryRules.map((rule) => {
  //             if (rule.ruleKey === selectedDelivery.ruleKey) {
  //               const productToAdd = {
  //                 key: rule.products.length + 1,
  //                 value: null,
  //                 count: 1, // You can set the initial count as per your requirement
  //               };
  //               return {
  //                 ...rule,
  //                 products: [...rule.products, productToAdd],
  //               };
  //             }
  //             return rule;
  //           });
  //           return {
  //             ...dd,
  //             deliveryRules: updatedRules,
  //           };
  //         }
  //         return dd;
  //       });
  //       return {
  //         ...cycle,
  //         deliveries: updatedDeliveries,
  //       };
  //     }
  //     return cycle;
  //   });
  //   setCampaignData(updatedState);
  // };

  const add = () => {
    setProducts((prevState) => [
      ...prevState,
      {
        key: prevState.length + 1,
        value: null,
        count: 1,
      },
    ]);
  };

  const deleteItem = (data) => {
    const fil = products.filter((i) => i.key !== data.key);
    const up = fil.map((item, index) => ({ ...item, key: index + 1 }));
    setProducts(up);
  };

  const incrementC = (data, value) => {
    if (value?.target?.value.trim() === "") return;
    if (value === 0) return;
    if (value > 10) return;
    setProducts((prevState) => {
      return prevState.map((item) => {
        if (item.key === data.key) {
          return { ...item, count: value?.target?.value ? Number(value?.target?.value) : value };
        }
        return item;
      });
    });
  };

  const updateValue = (key, newValue) => {
    setProducts((prevState) => {
      return prevState.map((item) => {
        if (item.key === key.key) {
          return { ...item, value: newValue };
        }
        return item;
      });
    });
  };

  useEffect(() => {
    stref.current = products; // Update the ref with the latest child state
  }, [products, stref]);

  return (
    <div>
      {products.map((i, c) => (
        <div className="add-resource-container">
          <div className="header-container">
            <CardText>
              {t(`CAMPAIGN_RESOURCE`)} {c + 1}
            </CardText>
            <div className="delete-resource-icon" onClick={() => deleteItem(i, c)}>
              <DustbinIcon />
            </div>
          </div>
          <div className="add-resource-label-field-container">
            <LabelFieldPair>
              <Label>{t(`CAMPAIGN_ADD_PRODUCTS_LABEL`)}</Label>
              <Dropdown
                style={{ width: "100%", marginBottom: 0 }}
                className="form-field"
                selected={i?.value}
                disable={false}
                isMandatory={true}
                option={data}
                select={(d) => updateValue(i, d)}
                optionKey="displayName"
              />
            </LabelFieldPair>

            <LabelFieldPair style={{ display: "flex", flexDirection: "column", alignItems: "flex-start" }}>
              <Label>{t(`CAMPAIGN_COUNT_LABEL`)}</Label>
              <TextInput type="numeric" defaultValue={i?.count} value={i?.count} onChange={(d) => incrementC(i, d)} />
            </LabelFieldPair>
          </div>
        </div>
      ))}
      <Button
        variation="secondary"
        label={t(`CAMPAIGN_PRODUCTS_MODAL_SECONDARY_ACTION`)}
        className={"add-rule-btn"}
        icon={<AddIcon fill="#f47738" styles={{ height: "1.5rem", width: "1.5rem" }} />}
        onButtonClick={add}
      />
    </div>
  );
}

export default AddProducts;
