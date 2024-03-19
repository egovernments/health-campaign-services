import {
  LabelFieldPair,
  AddIcon,
  DustbinIcon,
  CardLabel,
  Dropdown,
  TextInput,
  Button,
  Card,
  CardHeader,
  Modal,
  CloseSvg,
} from "@egovernments/digit-ui-react-components";
import { SVG } from "@egovernments/digit-ui-react-components";
import React, { Fragment, useContext, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { attributeConfig } from "../../../configs/attributeConfig";
import { operatorConfig } from "../../../configs/operatorConfig";
import RemoveableTagNew from "../../../components/RemovableTagNew";
import AddProducts from "./AddProductscontext";
import { CycleContext } from ".";

const makeSequential = (jsonArray, keyName) => {
  return jsonArray.map((item, index) => ({
    ...item,
    [keyName]: index + 1,
  }));
};

const AddAttributeField = ({ deliveryRuleIndex, delivery, deliveryRules, setDeliveryRules, attribute, setAttributes, index, onDelete }) => {
  const [val, setVal] = useState("");
  const [showAttribute, setShowAttribute] = useState(null);
  const [showOperator, setShowOperator] = useState(null);
  const { t } = useTranslation();

  const selectValue = (e) => {
    // setAttributes((pre) => pre.map((item) => (item.key === attribute.key ? { ...item, value: e.target.value } : item)));
    const updatedData = deliveryRules.map((item, index) => {
      if (item.ruleKey === deliveryRuleIndex) {
        item.attributes.find((i) => i.key === attribute.key).value = e.target.value;
      }
      return item;
    });
    setDeliveryRules(updatedData);
  };

  const selectToFromValue = (e, range) => {
    if (range === "to") {
      const updatedData = deliveryRules.map((item, index) => {
        if (item.ruleKey === deliveryRuleIndex) {
          item.attributes.find((i) => i.key === attribute.key).toValue = e.target.value;
        }
        return item;
      });
    } else {
      const updatedData = deliveryRules.map((item, index) => {
        if (item.ruleKey === deliveryRuleIndex) {
          item.attributes.find((i) => i.key === attribute.key).fromValue = e.target.value;
        }
        return item;
      });
      setDeliveryRules(updatedData);
    }
  };

  const selectAttribute = (value) => {
    // setAttributes((pre) => pre.map((item) => (item.key === attribute.key ? { ...item, value: e.target.value } : item)));
    const updatedData = deliveryRules.map((item, index) => {
      if (item.ruleKey === deliveryRuleIndex) {
        item.attributes.find((i) => i.key === attribute.key).attribute = value;
      }
      return item;
    });
    setShowAttribute(value);
    setDeliveryRules(updatedData);
  };

  const selectOperator = (value) => {
    // setAttributes((pre) => pre.map((item) => (item.key === attribute.key ? { ...item, value: e.target.value } : item)));
    const updatedData = deliveryRules.map((item, index) => {
      if (item.ruleKey === deliveryRuleIndex) {
        item.attributes.find((i) => i.key === attribute.key).operator = value;
      }
      return item;
    });
    setShowOperator(value);
    setDeliveryRules(updatedData);
  };

  return (
    <div key={attribute?.key} className="attribute-field-wrapper">
      <LabelFieldPair>
        <CardLabel isMandatory={true} className="card-label-smaller">
          {t(`CAMPAIGN_ATTRIBUTE_LABEL`)}
        </CardLabel>
        <Dropdown
          className="form-field"
          selected={attribute?.attribute}
          disable={false}
          isMandatory={true}
          option={attributeConfig}
          select={(value) => selectAttribute(value)}
          optionKey="code"
          t={t}
        />
      </LabelFieldPair>
      <LabelFieldPair>
        <CardLabel isMandatory={true} className="card-label-smaller">
          {t(`CAMPAIGN_OPERATOR_LABEL`)}
        </CardLabel>
        <Dropdown
          className="form-field"
          selected={attribute?.operator}
          disable={false}
          isMandatory={true}
          option={operatorConfig}
          select={(value) => selectOperator(value)}
          optionKey="code"
          t={t}
        />
      </LabelFieldPair>
      <LabelFieldPair>
        <CardLabel className="card-label-smaller">{t(`CAMPAIGN_VALUE_LABEL`)}</CardLabel>
        <div className="field" style={{ display: "flex", width: "100%" }}>
          {attribute?.operator?.code === "IN_BETWEEN" ? (
            <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
              <TextInput
                className=""
                textInputStyle={{ width: "45%" }}
                value={attribute?.toValue}
                onChange={(e) => selectToFromValue(e, "to")}
                disable={false}
              />
              <TextInput
                className=""
                textInputStyle={{ width: "45%" }}
                value={attribute?.fromValue}
                onChange={(e) => selectToFromValue(e, "from")}
                disable={false}
              />
            </div>
          ) : (
            <TextInput className="" textInputStyle={{ width: "100%" }} value={attribute?.value} onChange={selectValue} disable={false} />
          )}
        </div>
      </LabelFieldPair>
      <div
        onClick={() => onDelete()}
        style={{
          cursor: "pointer",
          fontWeight: "600",
          fontSize: "1rem",
          color: "#f47738",
          display: "flex",
          gap: "0.5rem",
          alignItems: "center",
          marginTop: "1rem",
        }}
      >
        <DustbinIcon />
        {t(`CAMPAIGN_DELETE_ROW_TEXT`)}
      </div>
    </div>
  );
};

const AddAttributeWrapper = ({ deliveryRuleIndex, delivery, deliveryRules, setDeliveryRules, index, key }) => {
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const { t } = useTranslation();

  const [attributes, setAttributes] = useState([{ key: 1, deliveryRuleIndex, attribute: "", operator: "", value: "" }]);
  const reviseIndexKeys = () => {
    setAttributes((prev) => prev.map((unit, index) => ({ ...unit, key: index + 1 })));
  };

  const addMoreAttribute = () => {
    setDeliveryRules((prev) =>
      prev.map((item, index) =>
        index + 1 === deliveryRuleIndex
          ? {
              ...item,
              attributes: [...item.attributes, { key: item.attributes.length + 1, attribute: "", operator: "", value: "" }],
            }
          : item
      )
    );
  };

  const deleteAttribute = (_, d) => {
    // setAttributes((prev) => prev.filter((i) => i.key !== item.key));
    const newData = deliveryRules.map((item) => {
      if (item.ruleKey === deliveryRuleIndex) {
        // If ruleKey matches, remove the specified attribute from attributes array
        const updatedAttributes = item.attributes.filter((attribute) => attribute.key !== _.key);

        // Reassign keys in sequential order
        const updatedAttributesSequential = makeSequential(updatedAttributes, "key");

        return {
          ...item,
          attributes: updatedAttributesSequential,
        };
      }
      return item;
    });
    setDeliveryRules(newData);
  };

  return (
    <Card className="attribute-container">
      {delivery.attributes.map((item, index) => (
        <AddAttributeField
          deliveryRuleIndex={deliveryRuleIndex}
          delivery={delivery}
          deliveryRules={deliveryRules}
          setDeliveryRules={setDeliveryRules}
          attribute={item}
          setAttributes={setAttributes}
          key={index}
          index={index}
          onDelete={() => deleteAttribute(item, deliveryRuleIndex)}
        />
      ))}
      <Button
        variation="secondary"
        label={t(`CAMPAIGN_ADD_MORE_ATTRIBUTE_TEXT`)}
        className="add-attribute"
        icon={<AddIcon fill="#f47738" />}
        onButtonClick={addMoreAttribute}
      />
    </Card>
  );
};

const AddDeliveryRule = ({ targetedData, deliveryRules, setDeliveryRules, index, key, delivery, onDelete }) => {
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const [showModal, setShowModal] = useState(false);
  const { t } = useTranslation();
  const prodRef = useRef();

  const confirmResources = () => {
    dispatchCampaignData({
      type: "ADD_PRODUCT",
      payload: {
        productData: prodRef.current,
        delivery: delivery,
      },
    });
    setShowModal(false);
  };

  const removeProduct = (item) => {
    setDeliveryRules((prevState) => {
      const updatedDeliveryRules = prevState.map((delivery) => {
        if (delivery.ruleKey === delivery.ruleKey) {
          const updatedProducts = delivery.products
            .filter((product) => product.key !== item.key)
            .map((product, index) => ({ ...product, key: index + 1 }));
          return { ...delivery, products: updatedProducts };
        }
        return delivery;
      });
      return updatedDeliveryRules;
    });
  };

  return (
    <>
      <Card className="delivery-rule-container">
        <CardHeader>
          <p className="title">
            {t(`CAMPAIGN_DELIVERY_RULE_LABEL`)} {delivery.ruleKey}
          </p>
          <div
            onClick={() => onDelete()}
            style={{ fontWeight: "600", fontSize: "1rem", color: "#f47738", display: "flex", gap: "0.5rem", alignItems: "center" }}
          >
            <DustbinIcon /> {t(`CAMPAIGN_DELETE_CONDITION_LABEL`)}
          </div>
        </CardHeader>
        <AddAttributeWrapper
          deliveryRuleIndex={delivery.ruleKey}
          delivery={delivery}
          deliveryRules={deliveryRules}
          setDeliveryRules={setDeliveryRules}
          index={index}
          key={key}
        />

        {delivery?.products?.length > 0 &&
          delivery?.products?.map((i) => <RemoveableTagNew text={{ value: i.name }} onClick={() => removeProduct(i)} />)}
        <Button
          variation="secondary"
          label={t(`CAMPAIGN_ADD_PRODUCTS_BUTTON_TEXT`)}
          icon={<SVG.AppRegistration />}
          onButtonClick={() => setShowModal(true)}
        />
      </Card>
      {showModal && (
        <Modal
          formId="product-action"
          customClass={"campaign-product-wrapper"}
          popupStyles={{ width: "70%", paddingLeft: "1.5rem" }}
          headerBarMainStyle={{ fontWeight: 700, fontSize: "1.5rem" }}
          // popupModuleMianStyles={}
          // popupModuleActionBarStyles={}
          hideSubmit={false}
          actionSaveLabel={t(`CAMPAIGN_PRODUCTS_MODAL_SUBMIT_TEXT`)}
          actionSaveOnSubmit={confirmResources}
          headerBarMain={t(`CAMPAIGN_PRODUCTS_MODAL_HEADER_TEXT`)}
          headerBarEnd={
            <div className="icon-bg-secondary" onClick={() => setShowModal(false)}>
              <CloseSvg />
            </div>
          }
          children={<AddProducts stref={prodRef} selectedDelivery={delivery} confirmResources={confirmResources} />}
        />
      )}
    </>
  );
};

const AddDeliveryRuleWrapper = ({}) => {
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const [targetedData, setTargetedData] = useState(campaignData.find((i) => i.active === true).deliveries.find((d) => d.active === true));
  const [deliveryRules, setDeliveryRules] = useState(targetedData.deliveryRules);
  const { t } = useTranslation();

  useEffect(() => {
    const dd = campaignData.find((i) => i.active === true).deliveries.find((d) => d.active === true);
    setTargetedData(dd);
  }, [campaignData]);

  useEffect(() => {
    const tt = targetedData.deliveryRules;
    setDeliveryRules(tt);
  }, [targetedData]);

  useEffect(() => {
    dispatchCampaignData({
      type: "UPDATE_CAMPAIGN_DATA",
      payload: {
        currentDeliveryRules: deliveryRules,
      },
    });
  }, [deliveryRules]);

  const addMoreDelivery = () => {
    dispatchCampaignData({
      type: "ADD_DELIVERY_RULE",
      payload: {
        currentDeliveryRules: deliveryRules,
      },
    });
  };

  const deleteDeliveryRule = (item) => {
    dispatchCampaignData({
      type: "REMOVE_DELIVERY_RULE",
      payload: {
        item: item,
      },
    });
  };

  return (
    <>
      {deliveryRules.map((item, index) => (
        <AddDeliveryRule
          targetedData={targetedData}
          deliveryRules={deliveryRules}
          delivery={item}
          setDeliveryRules={setDeliveryRules}
          key={index}
          index={index}
          onDelete={() => deleteDeliveryRule(item)}
        />
      ))}
      <Button
        variation="secondary"
        label={`CAMPAIGN_ADD_MORE_DELIVERY_BUTTON`}
        className={"add-rule-btn"}
        icon={<AddIcon fill="#f47738" />}
        onButtonClick={addMoreDelivery}
      />
    </>
  );
};

export default AddDeliveryRuleWrapper;
