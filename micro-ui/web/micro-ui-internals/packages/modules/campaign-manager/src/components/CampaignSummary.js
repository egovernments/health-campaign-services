import React, { Fragment, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useHistory } from "react-router-dom";
import { EditIcon, Header, Loader, ViewComposer } from "@egovernments/digit-ui-react-components";

const CampaignSummary = () => {
  const { t } = useTranslation();
  const history = useHistory();
  const tenantId = Digit.ULBService.getCurrentTenantId();
  const searchParams = new URLSearchParams(location.search);
  const id = searchParams.get("id");

  const { isLoading, data, error } = Digit.Hooks.campaign.useSearchCampaign({
    tenantId: tenantId,
    filter: {
      ids: [id],
    },
    config: {
      select: (data) => {
        return {
          cards: [
            {
              sections: [
                {
                  type: "DATA",
                  cardHeader: { value: t("CAMPAIGN_DETAILS"), inlineStyles: { marginTop: 0 } },
                  cardSecondaryAction: (
                    <div className="campaign-preview-edit-container" onClick={() => handleRedirect(1)}>
                      <span>{t(`CAMPAIGN_EDIT`)}</span>
                      <EditIcon />
                    </div>
                  ),
                  values: [
                    {
                      key: "CAMPAIGN_TYPE",
                      value: data?.[0]?.projectType || t("CAMPAIGN_SUMMARY_NA"),
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
            {
              sections: [
                {
                  type: "DATA",
                  cardHeader: { value: t("TARGET_DETAILS"), inlineStyles: { marginTop: 0 } },
                  cardSecondaryAction: (
                    <div className="campaign-preview-edit-container" onClick={() => handleRedirect(4)}>
                      <span>{t(`CAMPAIGN_EDIT`)}</span>
                      <EditIcon />
                    </div>
                  ),
                  values: [
                    {
                      key: "CAMPAIGN_TYPE",
                      value: data?.[0]?.hierarchyType || t("CAMPAIGN_SUMMARY_NA"),
                    },
                  ],
                },
              ],
            },
            ...data?.[0]?.campaignDetails?.deliveryRules?.map((item, index) => {
              return {
                sections: [
                  {
                    type: "COMPONENT",
                    cardHeader: { value: t("DELIVERY_CYCLE_DETAILS"), inlineStyles: { marginTop: 0 } },
                    cardSecondaryAction: (
                      <div className="campaign-preview-edit-container" onClick={() => handleRedirect(2)}>
                        <span>{t(`CAMPAIGN_EDIT`)}</span>
                        <EditIcon />
                      </div>
                    ),
                    component: "CycleDetaisPreview",
                    props: {
                      data: data?.[0],
                      items: item,
                      index: index,
                    },
                  },
                ],
              };
            }),
          ],
        };
      },
      enabled: id ? true : false,
      staleTime: 0,
      cacheTime: 0,
    },
  });

  const handleRedirect = (step) => {
    history.push(`/${window?.contextPath}/employee/campaign/setup-campaign?step=${step}`);
  };

  if (isLoading) {
    return <Loader />;
  }

  return (
    <>
      <Header>{t("ES_TQM_SUMMARY_HEADING")}</Header>
      <ViewComposer data={data} />
    </>
  );
};

export default CampaignSummary;
