import { Link, useHistory } from "react-router-dom";
import _ from "lodash";
import React from "react";

//create functions here based on module name set in mdms(eg->SearchProjectConfig)
//how to call these -> Digit?.Customizations?.[masterName]?.[moduleName]
// these functions will act as middlewares
// var Digit = window.Digit || {};

const businessServiceMap = {};

const inboxModuleNameMap = {};

export const UICustomizations = {
  MyCampaignConfigOngoing: {
    preProcess: (data, additionalDetails) => {
      const tenantId = Digit.ULBService.getCurrentTenantId();
      data.body = { RequestInfo: data.body.RequestInfo };
      const { limit, offset } = data?.state?.tableForm || {};
      const { campaignName, campaignType } = data?.state?.searchForm || {};
      data.body.CampaignDetails = {
        tenantId: tenantId,
        status: "started",
        pagination: {
          sortBy: "campaignNumber",
          sortOrder: "asc",
          limit: limit,
          offset: offset,
        },
      };
      if (campaignName) {
        data.body.CampaignDetails.campaignName = campaignName;
      }
      if (campaignType) {
        data.body.CampaignDetails.projectType = campaignType;
      }
      delete data.body.custom;
      delete data.body.inbox;
      delete data.params;
      return data;
    },
    populateCampaignTypeReqCriteria: () => {
      const tenantId = Digit.ULBService.getCurrentTenantId();

      return {
        url: "/egov-workflow-v2/egov-wf/businessservice/_search",
        params: { tenantId, businessServices: businessServiceMap?.tqm },
        body: {},
        changeQueryName: "setWorkflowStatus",
        config: {
          enabled: true,
          select: (data) => {
            const wfStates = data?.BusinessServices?.[0]?.states
              ?.filter((state) => state.applicationStatus)
              ?.map((state) => {
                return {
                  i18nKey: `WF_STATUS_${businessServiceMap?.tqm}_${state?.applicationStatus}`,
                  ...state,
                };
              });
            return wfStates;
          },
        },
      };
    },
    getCustomActionLabel: (obj, row) => {
      return "";
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      switch (key) {
        case "CAMPAIGN_NAME":
          return (
            <span className="link">
              <Link to={`/${window.contextPath}/employee/campaign/setup-campaign?id=${row.id}`}>
                {String(value ? (column.translate ? t(column.prefix ? `${column.prefix}${value}` : value) : value) : t("ES_COMMON_NA"))}
              </Link>
            </span>
          );

        case "CAMPAIGN_START_DATE":
          return Digit.DateUtils.ConvertEpochToDate(value);
        case "CAMPAIGN_END_DATE":
          return Digit.DateUtils.ConvertEpochToDate(value);
        default:
          return "case_not_found";
      }
    },
    onCardClick: (obj) => {
      return `view-test-results?tenantId=${obj?.apiResponse?.businessObject?.tenantId}&id=${obj?.apiResponse?.businessObject?.testId}&from=TQM_BREAD_INBOX`;
    },
    onCardActionClick: (obj) => {
      return `view-test-results?tenantId=${obj?.apiResponse?.businessObject?.tenantId}&id=${obj?.apiResponse?.businessObject?.testId}&from=TQM_BREAD_INBOX`;
    },
    getCustomActionLabel: (obj, row) => {
      return "TQM_VIEW_TEST_DETAILS";
    },
  },
  MyCampaignConfigCompleted: {
    preProcess: (data, additionalDetails) => {
      const tenantId = Digit.ULBService.getCurrentTenantId();
      data.body = { RequestInfo: data.body.RequestInfo };
      const { limit, offset } = data?.state?.tableForm || {};
      data.body.CampaignDetails = {
        tenantId: tenantId,
        status: "started",
        pagination: {
          sortBy: "campaignNumber",
          sortOrder: "asc",
          limit: limit,
          offset: offset,
        },
      };
      delete data.body.custom;
      delete data.body.inbox;
      delete data.params;
      return data;
    },
    populateCampaignTypeReqCriteria: () => {
      const tenantId = Digit.ULBService.getCurrentTenantId();

      return {
        url: "/egov-workflow-v2/egov-wf/businessservice/_search",
        params: { tenantId, businessServices: businessServiceMap?.tqm },
        body: {},
        changeQueryName: "setWorkflowStatus",
        config: {
          enabled: true,
          select: (data) => {
            const wfStates = data?.BusinessServices?.[0]?.states
              ?.filter((state) => state.applicationStatus)
              ?.map((state) => {
                return {
                  i18nKey: `WF_STATUS_${businessServiceMap?.tqm}_${state?.applicationStatus}`,
                  ...state,
                };
              });
            return wfStates;
          },
        },
      };
    },
    getCustomActionLabel: (obj, row) => {
      return "";
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      switch (key) {
        case "CAMPAIGN_NAME":
          return (
            <span className="link">
              <Link to={`/${window.contextPath}/employee/campaign/setup-campaign?id=${row.id}`}>
                {String(value ? (column.translate ? t(column.prefix ? `${column.prefix}${value}` : value) : value) : t("ES_COMMON_NA"))}
              </Link>
            </span>
          );

        case "CAMPAIGN_START_DATE":
          return Digit.DateUtils.ConvertEpochToDate(value);
        case "CAMPAIGN_END_DATE":
          return Digit.DateUtils.ConvertEpochToDate(value);
        default:
          return "case_not_found";
      }
    },
    onCardClick: (obj) => {
      return `view-test-results?tenantId=${obj?.apiResponse?.businessObject?.tenantId}&id=${obj?.apiResponse?.businessObject?.testId}&from=TQM_BREAD_INBOX`;
    },
    onCardActionClick: (obj) => {
      return `view-test-results?tenantId=${obj?.apiResponse?.businessObject?.tenantId}&id=${obj?.apiResponse?.businessObject?.testId}&from=TQM_BREAD_INBOX`;
    },
    getCustomActionLabel: (obj, row) => {
      return "TQM_VIEW_TEST_DETAILS";
    },
  },
  MyCampaignConfigDrafts: {
    preProcess: (data, additionalDetails) => {
      const tenantId = Digit.ULBService.getCurrentTenantId();
      data.body = { RequestInfo: data.body.RequestInfo };
      const { limit, offset } = data?.state?.tableForm || {};
      data.body.CampaignDetails = {
        tenantId: tenantId,
        status: "started",
        pagination: {
          sortBy: "campaignNumber",
          sortOrder: "asc",
          limit: limit,
          offset: offset,
        },
      };
      delete data.body.custom;
      delete data.body.inbox;
      delete data.params;
      return data;
    },
    populateCampaignTypeReqCriteria: () => {
      const tenantId = Digit.ULBService.getCurrentTenantId();

      return {
        url: "/egov-workflow-v2/egov-wf/businessservice/_search",
        params: { tenantId, businessServices: businessServiceMap?.tqm },
        body: {},
        changeQueryName: "setWorkflowStatus",
        config: {
          enabled: true,
          select: (data) => {
            const wfStates = data?.BusinessServices?.[0]?.states
              ?.filter((state) => state.applicationStatus)
              ?.map((state) => {
                return {
                  i18nKey: `WF_STATUS_${businessServiceMap?.tqm}_${state?.applicationStatus}`,
                  ...state,
                };
              });
            return wfStates;
          },
        },
      };
    },
    getCustomActionLabel: (obj, row) => {
      return "";
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      switch (key) {
        case "CAMPAIGN_NAME":
          return (
            <span className="link">
              <Link to={`/${window.contextPath}/employee/campaign/setup-campaign?id=${row.id}`}>
                {String(value ? (column.translate ? t(column.prefix ? `${column.prefix}${value}` : value) : value) : t("ES_COMMON_NA"))}
              </Link>
            </span>
          );

        case "CAMPAIGN_START_DATE":
          return Digit.DateUtils.ConvertEpochToDate(value);
        case "CAMPAIGN_END_DATE":
          return Digit.DateUtils.ConvertEpochToDate(value);
        default:
          return "case_not_found";
      }
    },
    onCardClick: (obj) => {
      return `view-test-results?tenantId=${obj?.apiResponse?.businessObject?.tenantId}&id=${obj?.apiResponse?.businessObject?.testId}&from=TQM_BREAD_INBOX`;
    },
    onCardActionClick: (obj) => {
      return `view-test-results?tenantId=${obj?.apiResponse?.businessObject?.tenantId}&id=${obj?.apiResponse?.businessObject?.testId}&from=TQM_BREAD_INBOX`;
    },
    getCustomActionLabel: (obj, row) => {
      return "TQM_VIEW_TEST_DETAILS";
    },
  },
};
