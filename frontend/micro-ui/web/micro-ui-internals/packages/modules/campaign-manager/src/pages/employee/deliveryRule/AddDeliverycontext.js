import {
  LabelFieldPair,
  AddIcon,
  CardLabel,
  Dropdown,
  // TextInput,
  Button,
  Card,
  CardHeader,
  Modal,
  CloseSvg,
} from "@egovernments/digit-ui-react-components";
import { SVG } from "@egovernments/digit-ui-react-components";
import React, { Fragment, useContext, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
// import { attributeConfig } from "../../../configs/attributeConfig";
// import { operatorConfig } from "../../../configs/operatorConfig";
import RemoveableTagNew from "../../../components/RemovableTagNew";
import AddProducts from "./AddProductscontext";
import { CycleContext } from ".";
import { RadioButtons, TextInput } from "@egovernments/digit-ui-components";
import { PRIMARY_COLOR } from "../../../utils";

const DustbinIcon = () => (
  <svg width="12" height="16" viewBox="0 0 12 16" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path
      d="M0.999837 13.8333C0.999837 14.75 1.74984 15.5 2.6665 15.5L9.33317 15.5C10.2498 15.5 10.9998 14.75 10.9998 13.8333L10.9998 3.83333L0.999837 3.83333L0.999837 13.8333ZM11.8332 1.33333L8.9165 1.33333L8.08317 0.5L3.9165 0.5L3.08317 1.33333L0.166504 1.33333L0.166504 3L11.8332 3V1.33333Z"
      fill={PRIMARY_COLOR}
    />
  </svg>
);

const makeSequential = (jsonArray, keyName) => {
  return jsonArray.map((item, index) => ({
    ...item,
    [keyName]: index + 1,
  }));
};

const AddAttributeField = ({
  config,
  deliveryRuleIndex,
  delivery,
  deliveryRules,
  setDeliveryRules,
  attribute,
  setAttributes,
  index,
  onDelete,
  attributeConfig,
  operatorConfig,
  genderConfig,
}) => {
  const [val, setVal] = useState("");
  const [showAttribute, setShowAttribute] = useState(null);
  const [showOperator, setShowOperator] = useState(null);
  const [addedOption, setAddedOption] = useState(null);
  const { t } = useTranslation();

  useEffect(() => {
    setAddedOption(delivery?.attributes?.map((i) => i?.attribute?.code)?.filter((i) => i));
  }, [delivery, deliveryRules]);

  const selectValue = (e) => {
    let val = e.target.value;
    val = val.replace(/[^\d.]/g, "");
    val = val.match(/^\d*\.?\d{0,2}/)[0] || "";
    // if (val.startsWith("-")) {
    //   val = val.slice(1); // Remove the negative sign
    // }
    if (isNaN(val) || [" ", "e", "E"].some((f) => val.includes(f))) {
      val = val.slice(0, -1);
      return;
    }
    // setAttributes((pre) => pre.map((item) => (item.key === attribute.key ? { ...item, value: e.target.value } : item)));
    const updatedData = deliveryRules.map((item, index) => {
      if (item.ruleKey === deliveryRuleIndex) {
        item.attributes.find((i) => i.key === attribute.key).value = val;
      }
      return item;
    });
    setDeliveryRules(updatedData);
  };

  const selectGender = (value) => {
    // setAttributes((pre) => pre.map((item) => (item.key === attribute.key ? { ...item, value: e.target.value } : item)));
    const updatedData = deliveryRules.map((item, index) => {
      if (item.ruleKey === deliveryRuleIndex) {
        item.attributes.find((i) => i.key === attribute.key).value = value?.code;
      }
      return item;
    });
    setDeliveryRules(updatedData);
  };

  const selectToFromValue = (e, range) => {
    let val = e.target.value;
    val = val.replace(/[^\d.]/g, "");
    val = val.match(/^\d*\.?\d{0,2}/)[0] || "";
    // if (val.startsWith("-")) {
    //   val = val.slice(1); // Remove the negative sign
    // }

    if (isNaN(val) || [" ", "e", "E"].some((f) => val.includes(f))) {
      val = val.slice(0, -1);
      return;
    }
    if (range === "to") {
      const updatedData = deliveryRules.map((item, index) => {
        if (item.ruleKey === deliveryRuleIndex) {
          item.attributes.find((i) => i.key === attribute.key).toValue = val;
        }
        return item;
      });
      setDeliveryRules(updatedData);
    } else {
      const updatedData = deliveryRules.map((item, index) => {
        if (item.ruleKey === deliveryRuleIndex) {
          item.attributes.find((i) => i.key === attribute.key).fromValue = val;
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
        item.attributes.find((i) => i.key === attribute.key).value = "";
        item.attributes.find((i) => i.key === attribute.key).toValue = "";
        item.attributes.find((i) => i.key === attribute.key).fromValue = "";
        if (value.code === "Gender") {
          item.attributes.find((i) => i.key === attribute.key).operator = {
            code: "EQUAL_TO",
          };
        }
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
        delete item.attributes.find((i) => i.key === attribute.key).toValue;
        delete item.attributes.find((i) => i.key === attribute.key).fromValue;
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
          selected={attributeConfig?.find((item) => item?.code === attribute?.attribute?.code)}
          disable={false}
          isMandatory={true}
          option={addedOption ? attributeConfig?.filter((item) => !addedOption.includes(item.code)) : attributeConfig}
          select={(value) => selectAttribute(value)}
          optionKey="i18nKey"
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
          disable={attribute?.attribute?.code === "Gender" ? true : false}
          isMandatory={true}
          option={operatorConfig}
          select={(value) => selectOperator(value)}
          optionKey="code"
          t={t}
        />
      </LabelFieldPair>

      {attribute?.operator?.code === "IN_BETWEEN" ? (
        <div style={{ marginBottom: "24px", display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
          <LabelFieldPair>
            <CardLabel className="card-label-smaller">{t(`CAMPAIGN_FROM_LABEL`)}</CardLabel>
            <div className="field" style={{ display: "flex", width: "100%" }}>
              <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                <TextInput
                  className=""
                  // textInputStyle={{ width: "80%" }}
                  value={attribute?.toValue}
                  onChange={(e) => selectToFromValue(e, "to")}
                  disable={false}
                />
              </div>
            </div>
          </LabelFieldPair>
          <LabelFieldPair>
            <CardLabel className="card-label-smaller">{t(`CAMPAIGN_TO_LABEL`)}</CardLabel>
            <div className="field" style={{ display: "flex", width: "100%" }}>
              <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                <TextInput
                  className=""
                  // textInputStyle={{ width: "80%" }}
                  value={attribute?.fromValue}
                  onChange={(e) => selectToFromValue(e, "from")}
                  disable={false}
                />
              </div>
            </div>
          </LabelFieldPair>
        </div>
      ) : (
        <LabelFieldPair>
          <CardLabel className="card-label-smaller">{t(`CAMPAIGN_VALUE_LABEL`)}</CardLabel>
          <div className="field" style={{ display: "flex", width: "100%", marginBottom: attribute?.attribute?.code === "Gender" ? null : "24px" }}>
            {attribute?.attribute?.code === "Gender" ? (
              <Dropdown
                className="form-field"
                selected={attribute?.value?.code ? attribute?.value : { code: attribute?.value }}
                disable={false}
                isMandatory={true}
                option={genderConfig}
                select={(value) => selectGender(value)}
                optionKey="code"
                t={t}
              />
            ) : (
              <TextInput textInputStyle={{ width: "100%" }} value={attribute?.value ? attribute?.value : ""} onChange={selectValue} disable={false} />
            )}
          </div>
        </LabelFieldPair>
      )}
      {delivery.attributes.length !== 1 && (
        <div
          onClick={() => onDelete()}
          style={{
            cursor: "pointer",
            fontWeight: "600",
            marginLeft: "1rem",
            fontSize: "1rem",
            color: PRIMARY_COLOR,
            display: "flex",
            gap: "0.5rem",
            alignItems: "center",
            marginTop: "1rem",
          }}
        >
          <DustbinIcon />
          {t(`CAMPAIGN_DELETE_ROW_TEXT`)}
        </div>
      )}
    </div>
  );
};

const AddCustomAttributeField = ({
  config,
  deliveryRuleIndex,
  delivery,
  deliveryRules,
  setDeliveryRules,
  attribute,
  setAttributes,
  index,
  onDelete,
  operatorConfig,
  genderConfig,
}) => {
  const [val, setVal] = useState("");
  const [showAttribute, setShowAttribute] = useState(null);
  const [showOperator, setShowOperator] = useState(null);
  const [addedOption, setAddedOption] = useState(null);
  const { t } = useTranslation();
  const { attrConfig } = useContext(CycleContext);

  useEffect(() => {
    setAddedOption(delivery?.attributes?.map((i) => i?.attribute?.code)?.filter((i) => i));
  }, [delivery]);

  const selectValue = (e) => {
    let val = e.target.value;
    val = val.replace(/[^\d.]/g, "");
    val = val.match(/^\d*\.?\d{0,2}/)[0] || "";
    // if (val.startsWith("-")) {
    //   val = val.slice(1); // Remove the negative sign
    // }
    if (isNaN(val) || [" ", "e", "E"].some((f) => val.includes(f))) {
      val = val.slice(0, -1);
    }
    // setAttributes((pre) => pre.map((item) => (item.key === attribute.key ? { ...item, value: e.target.value } : item)));
    const updatedData = deliveryRules.map((item, index) => {
      if (item.ruleKey === deliveryRuleIndex) {
        item.attributes.find((i) => i.key === attribute.key).value = val;
      }
      return item;
    });
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

  const selectToFromValue = (e, range) => {
    let val = e.target.value;
    val = val.replace(/[^\d.]/g, "");
    val = val.match(/^\d*\.?\d{0,2}/)[0] || "";
    // if (val.startsWith("-")) {
    //   val = val.slice(1); // Remove the negative sign
    // }
    if (isNaN(val) || [" ", "e", "E"].some((f) => val.includes(f))) {
      val = val.slice(0, -1);
      return;
    }
    if (range === "to") {
      const updatedData = deliveryRules.map((item, index) => {
        if (item.ruleKey === deliveryRuleIndex) {
          item.attributes.find((i) => i.key === attribute.key).toValue = val;
        }
        return item;
      });
      setDeliveryRules(updatedData);
    } else {
      const updatedData = deliveryRules.map((item, index) => {
        if (item.ruleKey === deliveryRuleIndex) {
          item.attributes.find((i) => i.key === attribute.key).fromValue = val;
        }
        return item;
      });
      setDeliveryRules(updatedData);
    }
  };

  return (
    <div key={attribute?.key} className="attribute-field-wrapper">
      <LabelFieldPair>
        <CardLabel isMandatory={true} className="card-label-smaller">
          {t(`CAMPAIGN_ATTRIBUTE_LABEL`)}
        </CardLabel>
        <div className="field" style={{ display: "flex", width: "100%", marginBottom: "24px" }}>
          <TextInput type="text" textInputStyle={{ width: "100%" }} value={t(attribute?.attribute?.code)} disabled={true} />
        </div>
        {/* <Dropdown
          className="form-field"
          selected={attribute?.attribute}
          disable={false}
          isMandatory={true}
          option={addedOption ? attributeConfig.filter((item) => !addedOption.includes(item.code)) : attributeConfig}
          select={(value) => selectAttribute(value)}
          optionKey="code"
          t={t}
        /> */}
      </LabelFieldPair>
      <LabelFieldPair>
        <CardLabel isMandatory={true} className="card-label-smaller">
          {t(`CAMPAIGN_OPERATOR_LABEL`)}
        </CardLabel>
        <Dropdown
          className="form-field"
          selected={attribute?.operator}
          disable={attribute?.attribute?.code === "Gender" ? true : false}
          isMandatory={true}
          option={operatorConfig}
          select={(value) => selectOperator(value)}
          optionKey="code"
          t={t}
        />
      </LabelFieldPair>
      {attribute?.operator?.code === "IN_BETWEEN" ? (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem", marginBottom: "24px" }}>
          <LabelFieldPair>
            <CardLabel className="card-label-smaller">{t(`CAMPAIGN_FROM_LABEL`)}</CardLabel>
            <div className="field" style={{ display: "flex", width: "100%" }}>
              <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                <TextInput
                  className=""
                  // textInputStyle={{ width: "80%" }}
                  value={attribute?.toValue}
                  onChange={(e) => selectToFromValue(e, "to")}
                  disable={false}
                />
              </div>
            </div>
          </LabelFieldPair>
          <LabelFieldPair>
            <CardLabel className="card-label-smaller">{t(`CAMPAIGN_TO_LABEL`)}</CardLabel>
            <div className="field" style={{ display: "flex", width: "100%" }}>
              <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                <TextInput
                  className=""
                  // textInputStyle={{ width: "80%" }}
                  value={attribute?.fromValue}
                  onChange={(e) => selectToFromValue(e, "from")}
                  disable={false}
                />
              </div>
            </div>
          </LabelFieldPair>
        </div>
      ) : (
        <LabelFieldPair>
          <CardLabel className="card-label-smaller">{t(`CAMPAIGN_VALUE_LABEL`)}</CardLabel>
          <div className="field" style={{ display: "flex", width: "100%", marginBottom: "24px" }}>
            {attribute?.attribute?.code === "Gender" ? (
              <Dropdown
                className="form-field"
                selected={{ code: attribute?.value }}
                disable={false}
                isMandatory={true}
                option={genderConfig}
                select={(value) => selectGender(value)}
                optionKey="code"
                t={t}
              />
            ) : (
              <TextInput textInputStyle={{ width: "100%" }} value={attribute?.value ? attribute?.value : ""} onChange={selectValue} disable={false} />
            )}
          </div>
        </LabelFieldPair>
      )}
    </div>
  );
};

const AddAttributeWrapper = ({ targetedData, deliveryRuleIndex, delivery, deliveryRules, setDeliveryRules, index, key }) => {
  const { campaignData, dispatchCampaignData, filteredDeliveryConfig } = useContext(CycleContext);
  const { t } = useTranslation();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const { isLoading: attributeConfigLoading, data: attributeConfig } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "attributeConfig" }],
    {
      select: (data) => {
        return data?.["HCM-ADMIN-CONSOLE"]?.attributeConfig;
      },
    }
  );
  const { isLoading: operatorConfigLoading, data: operatorConfig } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "operatorConfig" }],
    {
      select: (data) => {
        return data?.["HCM-ADMIN-CONSOLE"]?.operatorConfig;
      },
    }
  );
  const { isLoading: genderConfigLoading, data: genderConfig } = Digit.Hooks.useCustomMDMS(tenantId, "common-masters", [{ name: "GenderType" }], {
    select: (data) => {
      return data?.["common-masters"]?.GenderType?.filter((i) => i.active !== false);
    },
  });
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
      {filteredDeliveryConfig?.customAttribute && filteredDeliveryConfig?.projectType === "LLIN-mz"
        ? delivery.attributes.map((item, index) => (
            <AddCustomAttributeField
              deliveryRuleIndex={deliveryRuleIndex}
              delivery={delivery}
              deliveryRules={deliveryRules}
              setDeliveryRules={setDeliveryRules}
              attribute={item}
              setAttributes={setAttributes}
              config={
                filteredDeliveryConfig?.deliveryConfig?.[targetedData?.deliveryIndex - 1]?.conditionConfig?.[delivery?.ruleKey - 1]
                  ?.attributeConfig?.[index]
              }
              key={index}
              index={index}
              onDelete={() => deleteAttribute(item, deliveryRuleIndex)}
              operatorConfig={operatorConfig}
              genderConfig={genderConfig}
            />
          ))
        : delivery.attributes.map((item, index) => (
            <AddAttributeField
              config={filteredDeliveryConfig?.attributeConfig?.[index]}
              deliveryRuleIndex={deliveryRuleIndex}
              delivery={delivery}
              deliveryRules={deliveryRules}
              setDeliveryRules={setDeliveryRules}
              attribute={item}
              setAttributes={setAttributes}
              key={index}
              index={index}
              onDelete={() => deleteAttribute(item, deliveryRuleIndex)}
              attributeConfig={attributeConfig}
              operatorConfig={operatorConfig}
              genderConfig={genderConfig}
            />
          ))}
      {!filteredDeliveryConfig?.attrAddDisable && delivery.attributes.length !== attributeConfig?.length && (
        <Button
          variation="secondary"
          label={t(`CAMPAIGN_ADD_MORE_ATTRIBUTE_TEXT`)}
          className="add-attribute hover"
          icon={<AddIcon styles={{ height: "1.5rem", width: "1.5rem" }} fill={PRIMARY_COLOR} width="20" height="20" />}
          onButtonClick={addMoreAttribute}
        />
      )}
    </Card>
  );
};

const AddDeliveryRule = ({ targetedData, deliveryRules, setDeliveryRules, index, key, delivery, onDelete }) => {
  const { campaignData, dispatchCampaignData, filteredDeliveryConfig } = useContext(CycleContext);
  const [showModal, setShowModal] = useState(false);
  const [showToast, setShowToast] = useState(null);
  const { t } = useTranslation();
  const prodRef = useRef();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const closeToast = () => setShowToast(null);
  const { isLoading: deliveryTypeConfigLoading, data: deliveryTypeConfig } = Digit.Hooks.useCustomMDMS(
    tenantId,
    "HCM-ADMIN-CONSOLE",
    [{ name: "deliveryTypeConfig" }],
    {
      select: (data) => {
        return data?.["HCM-ADMIN-CONSOLE"]?.deliveryTypeConfig;
      },
    }
  );
  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);

  const confirmResources = () => {
    const isValid = prodRef.current?.every((item) => item?.count !== null && item?.value !== null);
    if (!isValid) {
      setShowToast({ key: "error", label: "CAMPAIGN_PRODUCT_MISSING_ERROR" });
      return;
    }
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
    const temp = delivery;
    setDeliveryRules((prevState) => {
      const updatedDeliveryRules = prevState.map((delivery) => {
        if (delivery.ruleKey === temp.ruleKey) {
          const updatedProducts = delivery.products
            .filter((product) => product.value !== item.value)
            .map((product, index) => ({ ...product, key: index + 1 }));
          return { ...delivery, products: updatedProducts };
        }
        return delivery;
      });
      return updatedDeliveryRules;
    });
  };

  const updateDeliveryType = (value) => {
    const temp = delivery;
    setDeliveryRules((prevState) => {
      const updatedDeliveryRules = prevState.map((delivery) => {
        if (delivery.ruleKey === temp.ruleKey) {
          return { ...delivery, deliveryType: value?.code };
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
          {deliveryRules.length !== 1 && (
            <div
              className="hover"
              onClick={() => onDelete()}
              style={{
                fontWeight: "600",
                fontSize: "1rem",
                color: PRIMARY_COLOR,
                display: "flex",
                gap: "0.5rem",
                alignItems: "center",
                cursor: "pointer",
              }}
            >
              <DustbinIcon /> {t(`CAMPAIGN_DELETE_CONDITION_LABEL`)}
            </div>
          )}
        </CardHeader>
        {filteredDeliveryConfig?.customAttribute && filteredDeliveryConfig?.projectType !== "LLIN-mz" && (
          <LabelFieldPair style={{ marginTop: "1.5rem", marginBottom: "1.5rem"}} className="delivery-type-radio">
            <div className="deliveryType-labelfield">
              <span className="bold">{`${t("HCM_DELIVERY_TYPE")}`}</span>
            </div>
            <RadioButtons
              options={deliveryTypeConfig}
              selectedOption={deliveryTypeConfig?.find((i) => i?.code === delivery?.deliveryType)}
              optionsKey="code"
              onSelect={(value) => updateDeliveryType(value)}
              t={t}
              disabled={
                filteredDeliveryConfig?.deliveryConfig?.find((i, n) => n === targetedData?.deliveryIndex - 1)?.conditionConfig?.[delivery?.ruleKey - 1]
                  ?.disableDeliveryType
                  ? true
                  : false
              }
            />
          </LabelFieldPair>
        )}
        <AddAttributeWrapper
          targetedData={targetedData}
          deliveryRuleIndex={delivery.ruleKey}
          delivery={delivery}
          deliveryRules={deliveryRules}
          setDeliveryRules={setDeliveryRules}
          index={index}
          key={key}
        />
        <div className="product-tag-container">
          {delivery?.products?.length > 0 &&
            delivery?.products?.map((i) => {
              return i?.value && i?.count ? (
                <RemoveableTagNew
                  extraStyles={{
                    closeIconStyles: {
                      fill: "#505A5F",
                    },
                  }}
                  text={{ value: i?.name }}
                  onClick={() => removeProduct(i)}
                />
              ) : null;
            })}
        </div>
        <Button
          variation="secondary"
          className={"add-product-btn hover"}
          label={t(`CAMPAIGN_ADD_PRODUCTS_BUTTON_TEXT`)}
          icon={<SVG.AppRegistration fill="#c84c0e" />}
          onButtonClick={() => setShowModal(true)}
        />
      </Card>
      {showModal && (
        <Modal
          formId="product-action"
          customClass={"campaign-product-wrapper"}
          popupStyles={{ width: "70%", paddingLeft: "1.5rem", borderRadius: "4px" }}
          headerBarMainStyle={{ fontWeight: 700, fontSize: "1.5rem", alignItems: "baseline" }}
          // popupModuleMianStyles={}
          // popupModuleActionBarStyles={}
          hideSubmit={false}
          actionSaveLabel={t(`CAMPAIGN_PRODUCTS_MODAL_SUBMIT_TEXT`)}
          actionSaveOnSubmit={confirmResources}
          headerBarMain={t(`CAMPAIGN_PRODUCTS_MODAL_HEADER_TEXT`)}
          headerBarEnd={
            <div onClick={() => setShowModal(false)}>
              <CloseSvg />
            </div>
          }
          children={
            <AddProducts
              stref={prodRef}
              selectedDelivery={delivery}
              confirmResources={confirmResources}
              showToast={showToast}
              closeToast={closeToast}
              selectedProducts={delivery?.products}
            />
          }
        />
      )}
    </>
  );
};

const AddDeliveryRuleWrapper = ({}) => {
  const { campaignData, dispatchCampaignData, filteredDeliveryConfig } = useContext(CycleContext);
  const [targetedData, setTargetedData] = useState(campaignData?.find((i) => i?.active === true)?.deliveries?.find((d) => d?.active === true));
  const [deliveryRules, setDeliveryRules] = useState(targetedData?.deliveryRules);
  const { t } = useTranslation();

  useEffect(() => {
    const dd = campaignData?.find((i) => i?.active === true)?.deliveries?.find((d) => d?.active === true);
    setTargetedData(dd);
  }, [campaignData]);

  useEffect(() => {
    const tt = targetedData?.deliveryRules;
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
      {deliveryRules?.map((item, index) => (
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
      {!filteredDeliveryConfig?.deliveryAddDisable && deliveryRules?.length < 5 && (
        <Button
          variation="secondary"
          label={t(`CAMPAIGN_ADD_MORE_DELIVERY_BUTTON`)}
          className={"add-rule-btn hover"}
          icon={<AddIcon styles={{ height: "1.5rem", width: "1.5rem" }} fill={PRIMARY_COLOR} />}
          onButtonClick={addMoreDelivery}
        />
      )}
    </>
  );
};

export default AddDeliveryRuleWrapper;
