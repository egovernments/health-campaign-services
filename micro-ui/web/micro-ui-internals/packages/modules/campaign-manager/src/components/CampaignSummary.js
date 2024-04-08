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
                    <div onClick={() => handleRedirect(1)}>
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
                      value: Digit.Utils.date.convertEpochToDate(data?.[0]?.campaignDetails?.startDate) || t("CAMPAIGN_SUMMARY_NA"),
                    },
                    {
                      key: "CAMPAIGN_END_DATE",
                      value: Digit.Utils.date.convertEpochToDate(data?.[0]?.campaignDetails?.endDate) || t("CAMPAIGN_SUMMARY_NA"),
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
                    <div onClick={() => handleRedirect(2)}>
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
            ...data?.[0]?.campaignDetails?.deliveryRules?.map((item, index) => ({
              sections: [
                {
                  type: "COMPONENT",
                  cardHeader: { value: t("DELIVERY_CYCLE_DETAILS"), inlineStyles: { marginTop: 0 } },
                  cardSecondaryAction: <EditIcon />,
                  component: "CycleDetaisPreview",
                  props: {
                    data: data?.[0],
                    item: item,
                    index: index,
                  },
                },
              ],
            })),
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

  const DUMMY_DATA = [
    {
      id: "899e6cdf-93b5-4fac-b8bb-4d6404622227",
      tenantId: "mz",
      status: "started",
      action: "create",
      campaignNumber: "CMP-2024-03-21-000070",
      campaignName: null,
      projectType: null,
      hierarchyType: "ADMIN",
      boundaryCode: "mz",
      projectId: null,
      createdBy: "867ba408-1b82-4746-8274-eb916e625fea",
      lastModifiedBy: "867ba408-1b82-4746-8274-eb916e625fea",
      createdTime: 1711020182790,
      lastModifiedTime: 1711020182790,
      additionalDetails: {},
      campaignDetails: {
        endDate: 1665929225005,
        startDate: 1665497225000,
        deliveryRules: [
          {
            endDate: 1665497225000,
            products: [
              {
                product: "string",
                count: 2,
              },
              {
                product: "skljsdlk",
                count: 2,
              },
              {
                product: "d38732897",
                count: 3,
              },
              {
                product: "jkhdskdsj",
                count: 4,
              },
            ],
            startDate: 1665497225000,
            conditions: [
              {
                value: 0,
                operator: "string",
                attribute: "string",
              },
            ],
            cycleNumber: 1,
            deliveryNumber: 1,
            deliveryRuleNumber: 1,
          },
          {
            endDate: 1665497225000,
            products: [
              {
                product: "string",
                count: 2,
              },
              {
                product: "skljsdlk",
                count: 2,
              },
              {
                product: "d38732897",
                count: 3,
              },
              {
                product: "jkhdskdsj",
                count: 4,
              },
            ],
            startDate: 1665497225000,
            conditions: [
              {
                value: 0,
                operator: "string",
                attribute: "string",
              },
            ],
            cycleNumber: 1,
            deliveryNumber: 2,
            deliveryRuleNumber: 1,
          },
          {
            endDate: 1665497225000,
            products: [
              {
                product: "string",
                count: 2,
              },
              {
                product: "skljsdlk",
                count: 2,
              },
              {
                product: "d38732897",
                count: 3,
              },
              {
                product: "jkhdskdsj",
                count: 4,
              },
            ],
            startDate: 1665497225000,
            conditions: [
              {
                value: 0,
                operator: "string",
                attribute: "string",
              },
            ],
            cycleNumber: 2,
            deliveryNumber: 1,
            deliveryRuleNumber: 1,
          },
          {
            endDate: 1665497225000,
            products: [
              {
                product: "string",
                count: 2,
              },
              {
                product: "skljsdlk",
                count: 2,
              },
              {
                product: "d38732897",
                count: 3,
              },
              {
                product: "jkhdskdsj",
                count: 4,
              },
            ],
            startDate: 1665497225000,
            conditions: [
              {
                value: 0,
                operator: "dasjhdasjh",
                attribute: "ASSS",
              },
              {
                value: 0,
                operator: "akaskjasklj",
                attribute: "uyeewquy",
              },
              {
                value: 0,
                operator: "jasdkjsda",
                attribute: "jdkjdask",
              },
            ],
            cycleNumber: 2,
            deliveryNumber: 2,
            deliveryRuleNumber: 1,
          },
        ],
      },
    },
  ];

  const config = {
    cards: [
      {
        sections: [
          {
            type: "DATA",
            cardHeader: { value: t("CAMPAIGN_DETAILS"), inlineStyles: { marginTop: 0 } },
            cardSecondaryAction: (
              <div onClick={() => handleRedirect(1)}>
                <EditIcon />
              </div>
            ),
            values: [
              {
                key: "CAMPAIGN_TYPE",
                value: DUMMY_DATA?.[0]?.projectType || t("CAMPAIGN_SUMMARY_NA"),
              },
              {
                key: "CAMPAIGN_NAME",
                value: DUMMY_DATA?.[0]?.campaignName || t("CAMPAIGN_SUMMARY_NA"),
              },
              {
                key: "CAMPAIGN_START_DATE",
                value: Digit.Utils.date.convertEpochToDate(DUMMY_DATA?.[0]?.campaignDetails?.startDate) || t("CAMPAIGN_SUMMARY_NA"),
              },
              {
                key: "CAMPAIGN_END_DATE",
                value: Digit.Utils.date.convertEpochToDate(DUMMY_DATA?.[0]?.campaignDetails?.endDate) || t("CAMPAIGN_SUMMARY_NA"),
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
              <div onClick={() => handleRedirect(2)}>
                <EditIcon />
              </div>
            ),
            values: [
              {
                key: "CAMPAIGN_TYPE",
                value: DUMMY_DATA?.[0]?.hierarchyType || t("CAMPAIGN_SUMMARY_NA"),
              },
            ],
          },
        ],
      },
      ...DUMMY_DATA?.[0]?.campaignDetails?.deliveryRules?.map((item, index) => ({
        sections: [
          {
            type: "COMPONENT",
            cardHeader: { value: t("DELIVERY_CYCLE_DETAILS"), inlineStyles: { marginTop: 0 } },
            cardSecondaryAction: <EditIcon />,
            component: "CycleDetaisPreview",
            props: {
              DUMMY_DATA: DUMMY_DATA?.[0],
              item: item,
              index: index,
            },
            // values: [
            //   {
            //     key: "CAMPAIGN_TYPE",
            //     value: t("CAMPAIGN_SUMMARY_NA"),
            //   },
            //   {
            //     key: "CAMPAIGN_NAME",
            //     value: t("CAMPAIGN_SUMMARY_NA"),
            //   },
            //   {
            //     key: "CAMPAIGN_START_DATE",
            //     value: t("CAMPAIGN_SUMMARY_NA"),
            //   },
            //   {
            //     key: "CAMPAIGN_END_DATE",
            //     value: t("CAMPAIGN_SUMMARY_NA"),
            //   },
            // ],
          },
        ],
      })),
    ],
  };

  if (isLoading) {
    return <Loader />;
  }

  if (!id) {
    return (
      <React.Fragment>
        <div className="cardHeaderWithOptions">
          <Header>{t("ES_TQM_SUMMARY_HEADING")}</Header>
        </div>

        <ViewComposer data={config} />
      </React.Fragment>
    );
  }

  return (
    <>
      {/* <div className="cardHeaderWithOptions"> */}
      <div className="madarchod">
        <Header>{t("ES_TQM_SUMMARY_HEADING")}</Header>
      </div>
      {/* </div> */}

      <div className="laudalelo">
        {/* {!isLoading && <ViewComposer data={testData} isLoading={isLoading} />} */}
        <ViewComposer data={data} />
      </div>
    </>
  );
};

export default CampaignSummary;
