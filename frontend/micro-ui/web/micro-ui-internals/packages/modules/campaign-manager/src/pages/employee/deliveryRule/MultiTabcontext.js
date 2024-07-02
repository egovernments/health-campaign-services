import React, { Fragment, useContext, useState } from "react";
import { Card, Header, Paragraph, CardHeader, CardSubHeader, CardText } from "@egovernments/digit-ui-react-components";
import AddDeliveryRuleWrapper from "./AddDeliverycontext";
import { CycleContext } from ".";
import { useTranslation } from "react-i18next";
import { InfoCard } from "@egovernments/digit-ui-components";
//just pass campaign data here
// function restructureData(data) {
//   const restructuredData = [];

//   data.forEach((cycle) => {
//     cycle.deliveries.forEach((delivery) => {
//       delivery.deliveryRules.forEach((rule) => {
//         const restructuredRule = {
//           startDate: 1665497225000, // Hardcoded for now
//           endDate: 1665497225000, // Hardcoded for now
//           cycleNumber: parseInt(cycle.cycleIndex),
//           deliveryNumber: parseInt(delivery.deliveryIndex),
//           deliveryRuleNumber: parseInt(rule.ruleKey), // New key added
//           products: [],
//           conditions: [],
//         };

//         rule.attributes.forEach((attribute) => {
//           restructuredRule.conditions.push({
//             attribute: attribute.attribute ? attribute.attribute.code : null,
//             operator: attribute.operator ? attribute.operator.code : null,
//             value: parseInt(attribute.value),
//           });
//         });

//         restructuredData.push(restructuredRule);
//       });
//     });
//   });

//   return restructuredData;
// }

//with between logic
// function restructureData(data) {
//   const restructuredData = [];

//   data.forEach((cycle) => {
//     cycle.deliveries.forEach((delivery) => {
//       delivery.deliveryRules.forEach((rule) => {
//         const restructuredRule = {
//           startDate: 1665497225000, // Hardcoded for now
//           endDate: 1665497225000, // Hardcoded for now
//           cycleNumber: parseInt(cycle.cycleIndex),
//           deliveryNumber: parseInt(delivery.deliveryIndex),
//           deliveryRuleNumber: parseInt(rule.ruleKey),
//           products: [],
//           conditions: [],
//         };

//         rule.attributes.forEach((attribute) => {
//           if (attribute.operator && attribute.operator.code === "IN_BETWEEN") {
//             // Replace "IN_BETWEEN" with "LESS_THAN" and "GREATER_THAN"
//             restructuredRule.conditions.push({
//               attribute: attribute.attribute ? attribute.attribute.code : null,
//               operator: "LESS_THAN",
//               value: parseInt(attribute.fromValue),
//             });
//             restructuredRule.conditions.push({
//               attribute: attribute.attribute ? attribute.attribute.code : null,
//               operator: "GREATER_THAN",
//               value: parseInt(attribute.toValue),
//             });
//           } else {
//             restructuredRule.conditions.push({
//               attribute: attribute.attribute ? attribute.attribute.code : null,
//               operator: attribute.operator ? attribute.operator.code : null,
//               value: parseInt(attribute.value),
//             });
//           }
//         });

//         restructuredData.push(restructuredRule);
//       });
//     });
//   });

//   return restructuredData;
// }

// for retransform just pass the deliveries
// function bbb(data) {
//   const reversedData = [];
//   let currentCycleIndex = null;
//   let currentDeliveryIndex = null;
//   let currentCycle = null;
//   let currentDelivery = null;

//   data.forEach((item, index) => {
//     if (currentCycleIndex !== item.cycleNumber) {
//       currentCycleIndex = item.cycleNumber;
//       currentCycle = {
//         cycleIndex: currentCycleIndex.toString(),
//         active: index === 0, // Set active to true only for the first index
//         deliveries: [],
//       };
//       reversedData.push(currentCycle);
//     }

//     if (currentDeliveryIndex !== item.deliveryNumber) {
//       currentDeliveryIndex = item.deliveryNumber;
//       currentDelivery = {
//         deliveryIndex: currentDeliveryIndex.toString(),
//         active: index === 0, // Set active to true only for the first index
//         deliveryRules: [],
//       };
//       currentCycle.deliveries.push(currentDelivery);
//     }

//     currentDelivery.deliveryRules.push({
//       ruleKey: currentDelivery.deliveryRules.length + 1,
//       delivery: {},
//       attributes: item.conditions.map((i, c) => ({ key: c + 1, ...i })),
//       products: [...item.products],
//     });
//   });

//   return reversedData;
// }

const Tabs = ({ onTabChange }) => {
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const { t } = useTranslation();

  return (
    <div className="campaign-tabs">
      {campaignData.map((_, index) => (
        <button
          key={index}
          type="button"
          className={`campaign-tab-head ${_.active === true ? "active" : ""} hover`}
          onClick={() => onTabChange(_.cycleIndex, index)}
        >
          <p style={{ margin: 0, position: "relative", top: "-0.1rem" }}>
            {t(`CAMPAIGN_CYCLE`)} {index + 1}
          </p>
        </button>
      ))}
    </div>
  );
};

const TabContent = ({ activeSubTab, subTabCount = 3, onSubTabChange, project }) => {
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const { t } = useTranslation();

  return (
    <Card className="sub-tab-container">
      <SubTabs campaignData={campaignData} subTabCount={subTabCount} activeSubTab={activeSubTab} onSubTabChange={onSubTabChange} />
      <div>
        <CardSubHeader className="tab-content-header">{t(`CAMPAIGN_TAB_TEXT`)}</CardSubHeader>
        <CardText>{t(`CAMPAIGN_TAB_SUB_TEXT_${project?.code ? project?.code?.toUpperCase() : project?.toUpperCase()}`)} </CardText>
      </div>
      {/* Add content specific to each tab as needed */}
      <InfoCard
        populators={{
          name: "infocard",
        }}
        variant="default"
        style={{ marginTop: "1.5rem", marginLeft: "0rem" , marginBottom: "0rem", maxWidth: "100%" }}
        className= {"infoClass"}
        headerWrapperClassName = {"headerWrapperClassName"}
        additionalElements={[
          <img className="whoLogo"
            // style="display: block;-webkit-user-select: none;margin: auto;cursor: zoom-in;background-color: hsl(0, 0%, 90%);transition: background-color 300ms;"
            src="https://cdn.worldvectorlogo.com/logos/world-health-organization-logo-1.svg"
            alt="WHO Logo"
            width="164"
            height="90"
          ></img>,
          <span style={{ color: "#505A5F" }}>
            {t(`CAMPAIGN_TAB_INFO_TEXT_${project?.code ? project?.code?.toUpperCase() : project?.toUpperCase()}`)}
          </span>
        ]}
        label={"Info"}
      />
    </Card>
  );
};

const SubTabs = ({ onSubTabChange }) => {
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const { t } = useTranslation();

  return (
    <div>
      {campaignData
        ?.find((i) => i?.active === true)
        ?.deliveries.map((_, index) => (
          <button
            key={index}
            type="button"
            className={`campaign-sub-tab-head ${_.active === true ? "active" : ""} hover`}
            onClick={() => onSubTabChange(_.deliveryIndex, index)}
          >
            {t(`CAMPAIGN_DELIVERY`)} {index + 1}
          </button>
        ))}
    </div>
  );
};

const MultiTab = ({ tabCount = 3, subTabCount = 2 }) => {
  const [activeTab, setActiveTab] = useState(0);
  const [activeSubTab, setActiveSubTab] = useState(0);
  const { campaignData, dispatchCampaignData } = useContext(CycleContext);
  const { t } = useTranslation();
  const tempSession = Digit.SessionStorage.get("HCM_CAMPAIGN_MANAGER_FORM_DATA");
  const handleTabChange = (tabIndex, index) => {
    dispatchCampaignData({
      type: "TAB_CHANGE_UPDATE",
      payload: { tabIndex: tabIndex, index: index }, // Your updated campaign data
    });
    setActiveTab(index);
    setActiveSubTab(0); // Reset sub-tab when changing the main tab
  };

  const handleSubTabChange = (subTabIndex, itemIndex) => {
    dispatchCampaignData({
      type: "SUBTAB_CHANGE_UPDATE",
      payload: { subTabIndex: subTabIndex }, // Your updated campaign data
    });
  };

  return (
    <>
      <Header>
        {t(
          `CAMPAIGN_PROJECT_${
            tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code
              ? tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.code?.toUpperCase()
              : tempSession?.HCM_CAMPAIGN_TYPE?.projectType?.toUpperCase()
          }`
        )}
      </Header>
      <Paragraph
        customClassName="cycle-paragraph"
        value={`(${tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.startDate
          ?.split("-")
          ?.reverse()
          ?.join("/")} - ${tempSession?.HCM_CAMPAIGN_DATE?.campaignDates?.endDate?.split("-")?.reverse()?.join("/")})`}
      />
      <div className="campaign-cycle-container">
        <div className="campaign-tabs-container">
          <Tabs tabCount={tabCount} activeTab={activeTab} onTabChange={handleTabChange} />
        </div>
        <TabContent
          activeTab={activeTab}
          project={tempSession?.HCM_CAMPAIGN_TYPE?.projectType}
          activeSubTab={activeSubTab}
          onSubTabChange={handleSubTabChange}
        />
        <AddDeliveryRuleWrapper />
      </div>
    </>
  );
};

export default MultiTab;
