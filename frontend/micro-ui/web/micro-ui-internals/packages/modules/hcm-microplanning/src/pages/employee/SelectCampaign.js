import React, { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Header, InboxSearchComposer, InboxSearchComposerV2, Loader } from "@egovernments/digit-ui-react-components";
import { useHistory, useParams } from "react-router-dom";

const configs = {
  label: "SELECT_CAMPAIGN",
  type: "search",
  apiDetails: {
    serviceName: "/project-factory/v1/project-type/search",
    requestParam: {},
    requestBody: {},
    minParametersForSearchForm: 0,
    masterName: "commonUiConfig",
    moduleName: "SearchCampaign",
    tableFormJsonPath: "requestBody.CampaignDetails.pagination",
    searchFormJsonPath: "requestBody.CampaignDetails",
  },
  sections: {
    search: {
      uiConfig: {
        type: "search",
        // typeMobile: "filter",
        headerLabel: "SELECT_CAMPAIGN",
        headerStyle: null,
        primaryLabel: "ES_COMMON_SEARCH",
        secondaryLabel: "ES_COMMON_CLEAR_SEARCH",
        minReqFields: 1,
        // "showFormInstruction": "TQM_SEARCH_HINT",
        defaultValues: {
          campaignName: "",
          projectType: "",
          startDate: "",
          endDate: "",
          boundaryCode: "",
        },
        fields: [
          {
            label: "CAMPAIGN_NAME",
            type: "text",
            isMandatory: false,
            disable: false,
            populators: {
              name: "campaignName",
              style: {
                marginBottom: "0px",
              },
              error: "ERR_MIN_LENGTH_CAMPAIGN_NAME",
              validationErrorStyles: {
                marginTop: "0.3rem",
              },
              validation: {
                minLength: 2,
              },
            },
          },
          // {
          //   label: "CAMPAIGN_TYPE",
          //   type: "dropdown",
          //   isMandatory: false,
          //   disable: false,
          //   populators: {
          //     name: "projectType",
          //     optionsKey: "name",
          //     optionsCustomStyle: {
          //       top: "2.3rem",
          //     },
          //     mdmsConfig: {
          //       masterName: "projectTypes",
          //       moduleName: "HCM-PROJECT-TYPES",
          //       localePrefix: "CAMPAIGN_TYPE",
          //     },
          //   },
          // },
          {
            label: "CAMPAIGN_TYPE",
            type: "apidropdown",
            isMandatory: false,
            disable: false,
            populators: {
              name: "projectType",
              optionsKey: "i18nKey",
              optionsCustomStyle: {
                top: "2.3rem",
              },
              allowMultiSelect: false,
              masterName: "commonUiConfig",
              moduleName: "SearchCampaign",
              customfn: "populateProjectType",
            },
          },
          {
            label: "CAMPAIGN_START_DATE",
            type: "date",
            isMandatory: false,
            key: "startDate",
            disable: false,
            preProcess: {
              updateDependent: ["populators.max"],
            },
            populators: {
              name: "startDate",
              style: {
                marginBottom: "0px",
              },
              error: "DATE_VALIDATION_MSG",
            },
          },
          {
            label: "CAMPAIGN_END_DATE",
            type: "date",
            isMandatory: false,
            disable: false,
            key: "endDate",
            preProcess: {
              updateDependent: ["populators.max"],
            },
            populators: {
              name: "endDate",
              error: "DATE_VALIDATION_MSG",
              min: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
              style: {
                marginBottom: "0px",
              },
            },
          },
          // {
          //   label: "CAMPAIGN_BOUNDARY",
          //   type: "text",
          //   isMandatory: false,
          //   disable: false,
          //   populators: {
          //     name: "boundaryCode",
          //     style: {
          //       marginBottom: "0px",
          //     },
          //   },
          // },
        ],
      },
      label: "",
      children: {},
      show: true,
      // "labelMobile": "TQM_INBOX_SEARCH"
    },
    searchResult: {
      uiConfig: {
        columns: [
          {
            label: "CAMPAIGN_NAME",
            jsonPath: "campaignName",
            // "additionalCustomization": true
          },
          {
            label: "CAMPAIGN_TYPE",
            jsonPath: "projectType",
            // "additionalCustomization": false,
            prefix: "CAMPAIGN_TYPE_",
            translate: true,
          },
          {
            label: "CAMPAIGN_BOUNDARY_CAMP",
            jsonPath: "boundaryCode",
            // "additionalCustomization": false,
            prefix: "CAMPAIGN_BOUNDARY_",
            translate: true,
          },
          {
            label: "CAMPAIGN_BENEFICIARY_TYPE",
            jsonPath: "additionalDetails.beneficiaryType",
            prefix: "CAMPAIGN_BENEFICIARY_TYPE_",
            translate: true,
          },
          {
            label: "CAMPAIGN_DATE",
            jsonPath: "startDate",
            additionalCustomization: true,
          },
        ],
        showActionBarMobileCard: true,
        actionButtonLabelMobileCard: "TQM_VIEW_RESULTS",
        enableGlobalSearch: false,
        enableColumnSort: true,
        resultsJsonPath: "CampaignDetails",
        tableClassName: "table pqm-table",
        rowClassName: "table-row-mdms table-row-mdms-hover",
        noColumnBorder: true,
      },
      children: {},
      show: true,
    },
  },
  additionalSections: {},
  persistFormData: true,
  showAsRemovableTagsInMobile: false,
};
const SelectCampaign = () => {
  const { t } = useTranslation();
  const history = useHistory();

  const onClickRow = (row) => {
    // history.push(`/${window.contextPath}/employee/microplanning/help-guidelines?id=${row?.original?.id}`);
    history.push(`/${window.contextPath}/employee/microplanning/create-microplan?id=${row?.original?.id}`);
  };

  const SelectCampaignSession = Digit.Hooks.useSessionStorage("SELECT_CAMPAIGN_SESSION", {});

  return (
    <React.Fragment>
      <Header className="works-header-search">{t(configs?.label)}</Header>
      <div className="inbox-search-wrapper">
        <InboxSearchComposerV2
          configs={configs}
          browserSession={SelectCampaignSession}
          additionalConfig={{
            resultsTable: {
              onClickRow,
            },
          }}
        />
      </div>
    </React.Fragment>
  );
};

export default SelectCampaign;
