import React, { Fragment, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useHistory } from "react-router-dom";
import { EditIcon, Header, Loader, ViewComposer } from "@egovernments/digit-ui-react-components";
import { Toast } from "@egovernments/digit-ui-components";

function mergeObjects(item) {
  const arr = item;
  const mergedArr = [];
  const mergedAttributes = new Set();

  arr.forEach((obj) => {
    if (!mergedAttributes.has(obj.attribute)) {
      const sameAttrObjs = arr.filter((o) => o.attribute === obj.attribute);

      if (sameAttrObjs.length > 1) {
        const fromValue = Math.min(...sameAttrObjs.map((o) => o.value));
        const toValue = Math.max(...sameAttrObjs.map((o) => o.value));

        mergedArr.push({
          fromValue,
          toValue,
          value: fromValue > 0 && toValue > 0 ? `${fromValue} to ${toValue}` : null,
          operator: "IN_BETWEEN",
          attribute: obj.attribute,
        });

        mergedAttributes.add(obj.attribute);
      } else {
        mergedArr.push(obj);
      }
    }
  });

  return mergedArr;
}

function loopAndReturn(dataa, t) {
  let newArray = [];
  const data = dataa?.map((i) => ({ ...i, operator: i?.operator, attribute: i?.attribute }));

  data.forEach((item) => {
    // Check if an object with the same attribute already exists in the newArray
    const existingIndex = newArray.findIndex((element) => element.attribute === item.attribute);
    if (existingIndex !== -1) {
      // If an existing item is found, replace it with the new object
      const existingItem = newArray[existingIndex];
      newArray[existingIndex] = {
        attribute: existingItem.attribute,
        operator: "IN_BETWEEN",
        toValue: existingItem.value && item.value ? Math.min(existingItem.value, item.value) : null,
        fromValue: existingItem.value && item.value ? Math.max(existingItem.value, item.value) : null,
      };
    } else if (item?.operator === "EQUAL_TO") {
      newArray.push({
        ...item,
        value: item?.value ? t(item?.value) : null,
      });
    } else {
      newArray.push(item);
    }
  });

  const withKey = newArray.map((i, c) => ({ key: c + 1, ...i }));
  const format = withKey.map((i) => {
    if (i.operator === "IN_BETWEEN") {
      return {
        ...i,
        value: `${i?.toValue} to ${i?.fromValue}`,
      };
    }
    return {
      ...i,
    };
  });
  return format;
}

function reverseDeliveryRemap(data, t) {
  if (!data) return null;
  const reversedData = [];
  let currentCycleIndex = null;
  let currentCycle = null;

  data.forEach((item, index) => {
    if (currentCycleIndex !== item.cycleNumber) {
      currentCycleIndex = item.cycleNumber;
      currentCycle = {
        cycleIndex: currentCycleIndex.toString(),
        startDate: item?.startDate ? Digit.Utils.date.convertEpochToDate(item?.startDate) : null,
        endDate: item?.endDate ? Digit.Utils.date.convertEpochToDate(item?.endDate) : null,
        active: index === 0, // Initialize active to false
        deliveries: [],
      };
      reversedData.push(currentCycle);
    }

    const deliveryIndex = item.deliveryNumber.toString();

    let delivery = currentCycle.deliveries.find((delivery) => delivery.deliveryIndex === deliveryIndex);

    if (!delivery) {
      delivery = {
        deliveryIndex: deliveryIndex,
        active: item.deliveryNumber === 1, // Set active to true only for the first delivery
        deliveryRules: [],
      };
      currentCycle.deliveries.push(delivery);
    }

    delivery.deliveryRules.push({
      ruleKey: item.deliveryRuleNumber,
      delivery: {},
      attributes: loopAndReturn(item.conditions, t),
      products: [...item.products],
    });
  });

  return reversedData;
}

const CampaignSummary = () => {
  const { t } = useTranslation();
  const history = useHistory();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const searchParams = new URLSearchParams(location.search);
  const id = searchParams.get("id");
  const noAction = searchParams.get("action");
  const [showToast, setShowToast] = useState(null);

  const { isLoading, data, error } = Digit.Hooks.campaign.useSearchCampaign({
    tenantId: tenantId,
    filter: {
      ids: [id],
    },
    config: {
      select: (data) => {
        const resourceIdArr = [];
        data?.[0]?.resources?.map((i) => {
          if (i?.createResourceId && i?.type === "user") {
            resourceIdArr.push(i?.createResourceId);
          }
        });
        const target = data?.[0]?.deliveryRules;
        const cycleData = reverseDeliveryRemap(target, t);
        return {
          cards: [
            {
              sections: [
                {
                  type: "DATA",
                  cardHeader: { value: t("CAMPAIGN_DETAILS"), inlineStyles: { marginTop: 0 } },
                  cardSecondaryAction: noAction !== "false" && (
                    <div className="campaign-preview-edit-container" onClick={() => handleRedirect(1)}>
                      <span>{t(`CAMPAIGN_EDIT`)}</span>
                      <EditIcon />
                    </div>
                  ),
                  values: [
                    {
                      key: "CAMPAIGN_TYPE",
                      value: data?.[0]?.projectType ? t(`CAMPAIGN_PROJECT_${data?.[0]?.projectType?.toUpperCase()}`) : t("CAMPAIGN_SUMMARY_NA"),
                    },
                    {
                      key: "CAMPAIGN_NAME",
                      value: data?.[0]?.campaignName || t("CAMPAIGN_SUMMARY_NA"),
                    },
                    {
                      key: "CAMPAIGN_START_DATE",
                      value: Digit.Utils.date.convertEpochToDate(data?.[0]?.startDate) || t("CAMPAIGN_SUMMARY_NA"),
                    },
                    {
                      key: "CAMPAIGN_END_DATE",
                      value: Digit.Utils.date.convertEpochToDate(data?.[0]?.endDate) || t("CAMPAIGN_SUMMARY_NA"),
                    },
                  ],
                },
              ],
            },
            data?.[0]?.resources?.find((i) => i?.type === "boundaryWithTarget")
              ? {
                  sections: [
                    {
                      type: "COMPONENT",
                      component: "CampaignDocumentsPreview",
                      props: {
                        documents: data?.[0]?.resources?.filter((i) => i.type === "boundaryWithTarget"),
                      },
                      cardHeader: { value: t("TARGET_DETAILS"), inlineStyles: { marginTop: 0 } },
                      cardSecondaryAction: noAction !== "false" && (
                        <div className="campaign-preview-edit-container" onClick={() => handleRedirect(7)}>
                          <span>{t(`CAMPAIGN_EDIT`)}</span>
                          <EditIcon />
                        </div>
                      ),
                    },
                  ],
                }
              : {},
            data?.[0]?.resources?.find((i) => i?.type === "facility")
              ? {
                  sections: [
                    {
                      type: "COMPONENT",
                      component: "CampaignDocumentsPreview",
                      props: {
                        documents: data?.[0]?.resources?.filter((i) => i.type === "facility"),
                      },
                      cardHeader: { value: t("FACILITY_DETAILS"), inlineStyles: { marginTop: 0 } },
                      cardSecondaryAction: noAction !== "false" && (
                        <div className="campaign-preview-edit-container" onClick={() => handleRedirect(8)}>
                          <span>{t(`CAMPAIGN_EDIT`)}</span>
                          <EditIcon />
                        </div>
                      ),
                    },
                  ],
                }
              : {},
            data?.[0]?.resources?.find((i) => i?.type === "user")
              ? {
                  sections: [
                    {
                      type: "COMPONENT",
                      component: "CampaignDocumentsPreview",
                      props: {
                        documents: data?.[0]?.resources?.filter((i) => i.type === "user"),
                      },
                      cardHeader: { value: t("USER_DETAILS"), inlineStyles: { marginTop: 0 } },
                      cardSecondaryAction: noAction !== "false" && (
                        <div className="campaign-preview-edit-container" onClick={() => handleRedirect(9)}>
                          <span>{t(`CAMPAIGN_EDIT`)}</span>
                          <EditIcon />
                        </div>
                      ),
                    },
                  ],
                }
              : {},
            resourceIdArr?.length > 0
              ? {
                  sections: [
                    {
                      type: "COMPONENT",
                      component: "CampaignResourceDocuments",
                      props: {
                        isUserGenerate: true,
                        resources: resourceIdArr,
                      },
                      cardHeader: { value: t("USER_GENERATE_DETAILS"), inlineStyles: { marginTop: 0 } },
                    },
                  ],
                }
              : {},
            {
              sections: [
                {
                  type: "DATA",
                  cardHeader: { value: t("CAMPAIGN_DELIVERY_DETAILS"), inlineStyles: { marginTop: 0 } },
                  cardSecondaryAction: noAction !== "false" && (
                    <div className="campaign-preview-edit-container" onClick={() => handleRedirect(4)}>
                      <span>{t(`CAMPAIGN_EDIT`)}</span>
                      <EditIcon />
                    </div>
                  ),
                  values: [
                    {
                      key: "CAMPAIGN_NO_OF_CYCLES",
                      value: data?.[0]?.deliveryRules
                        ? Math.max(...data?.[0]?.deliveryRules.map((item) => item.cycleNumber))
                        : t("CAMPAIGN_SUMMARY_NA"),
                    },
                    {
                      key: "CAMPAIGN_NO_OF_DELIVERIES",
                      value: data?.[0]?.deliveryRules
                        ? Math.max(...data?.[0]?.deliveryRules.map((item) => item.deliveryNumber))
                        : t("CAMPAIGN_SUMMARY_NA"),
                    },
                  ],
                },
              ],
            },
            ...cycleData?.map((item, index) => {
              return {
                sections: [
                  {
                    type: "COMPONENT",
                    cardHeader: { value: `${t("CYCLE")} ${item?.cycleIndex}`, inlineStyles: { marginTop: 0, fontSize: "1.5rem" } },
                    cardSecondaryAction: noAction !== "false" && (
                      <div className="campaign-preview-edit-container" onClick={() => handleRedirect(5)}>
                        <span>{t(`CAMPAIGN_EDIT`)}</span>
                        <EditIcon />
                      </div>
                    ),
                    component: "CycleDataPreview",
                    props: {
                      data: item,
                    },
                  },
                ],
              };
            }),
            // ...data?.[0]?.campaignDetails?.deliveryRules?.map((item, index) => {
            //   return {
            //     sections: [
            //       {
            //         type: "COMPONENT",
            //         cardHeader: { value: t("DELIVERY_CYCLE_DETAILS"), inlineStyles: { marginTop: 0 } },
            //         cardSecondaryAction: (
            //           <div className="campaign-preview-edit-container" onClick={() => handleRedirect(4)}>
            //             <span>{t(`CAMPAIGN_EDIT`)}</span>
            //             <EditIcon />
            //           </div>
            //         ),
            //         component: "CycleDetaisPreview",
            //         props: {
            //           data: data?.[0],
            //           items: item,
            //           index: index,
            //         },
            //       },
            //     ],
            //   };
            // }),
          ],
          error: data?.[0]?.additionalDetails?.error,
        };
      },
      enabled: id ? true : false,
      staleTime: 0,
      cacheTime: 0,
    },
  });

  const handleRedirect = (step) => {
    const urlParams = new URLSearchParams(window.location.search);

    // Get the values of other parameters
    const id = urlParams.get("id");
    // If there are more parameters, you can get them similarly

    // Modify the 'key' parameter
    urlParams.set("key", step);

    // Reconstruct the URL with the modified parameters
    const newUrl = `${window.location.pathname}?${urlParams.toString()}`;

    // Push the new URL to history
    history.push(newUrl);
    // history.push(`/${window?.contextPath}/employee/campaign/setup-campaign?key=${step}`);
  };

  if (isLoading) {
    return <Loader />;
  }
  const closeToast = () => {
    setShowToast(null);
  };
  useEffect(() => {
    if (showToast) {
      setTimeout(closeToast, 5000);
    }
  }, [showToast]);
  useEffect(() => {
    if (data?.error) {
      setShowToast({ label: data?.error, key: "error" });
    }
  }, [data]);
  return (
    <>
      <Header>{t("ES_TQM_SUMMARY_HEADING")}</Header>
      <div className="campaign-summary-container">
        <ViewComposer data={data} />
        {showToast && <Toast error={showToast?.key === "error" ? true : false} label={showToast?.label} onClose={closeToast} />}
      </div>
    </>
  );
};

export default CampaignSummary;
