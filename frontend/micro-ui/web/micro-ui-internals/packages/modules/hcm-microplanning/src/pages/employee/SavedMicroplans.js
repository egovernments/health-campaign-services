import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { Header, InboxSearchComposerV2, Loader } from "@egovernments/digit-ui-react-components";
import { useHistory } from "react-router-dom";
import { updateSessionUtils } from "../../utils/updateSessionUtils";
import { useMyContext } from "../../utils/context";

const configs = {
  label: "SAVED_MICROPLANS",
  type: "search",
  apiDetails: {
    serviceName: "/plan-service/config/_search",
    requestParam: {},
    requestBody: {},
    minParametersForSearchForm: 0,
    masterName: "commonUiConfig",
    moduleName: "SearchMicroplan",
    tableFormJsonPath: "requestBody.PlanConfigurationSearchCriteria.pagination",
    searchFormJsonPath: "requestBody.PlanConfigurationSearchCriteria",
  },
  sections: {
    search: {
      uiConfig: {
        type: "search",
        typeMobile: "filter",
        headerLabel: "SAVED_MICROPLANS",
        headerStyle: null,
        primaryLabel: "ES_COMMON_SEARCH",
        secondaryLabel: "ES_COMMON_CLEAR_SEARCH",
        minReqFields: 0,
        // "showFormInstruction": "TQM_SEARCH_HINT",
        defaultValues: {
          name: "",
          status: "",
        },
        fields: [
          {
            label: "MICROPLAN_NAME",
            type: "text",
            isMandatory: false,
            disable: false,
            populators: {
              name: "name",
              style: {
                marginBottom: "0px",
              },
            },
          },
          {
            label: "MICROPLAN_STATUS",
            type: "dropdown",
            isMandatory: false,
            disable: false,
            populators: {
              name: "status",
              optionsKey: "status",
              optionsCustomStyle: {
                top: "2.3rem",
              },
              mdmsConfig: {
                masterName: "MicroplanStatus",
                moduleName: "hcm-microplanning",
                localePrefix: "MICROPLAN_STATUS",
              },
            },
          },
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
            label: "MICROPLAN_NAME",
            jsonPath: "name",
          },
          {
            label: "MICROPLAN_STATUS",
            jsonPath: "status",
            prefix: "MICROPLAN_STATUS_COLUMN_",
            translate: true,
          },
          {
            label: "CAMPAIGNS_ASSIGNED",
            jsonPath: "CampaignDetails.campaignName",
          },
          {
            label: "CAMPAIGN_DATE",
            jsonPath: "CampaignDetails.startDate",
            additionalCustomization: true,
          },
        ],
        showActionBarMobileCard: true,
        actionButtonLabelMobileCard: "TQM_VIEW_RESULTS",
        enableGlobalSearch: false,
        enableColumnSort: true,
        resultsJsonPath: "PlanConfiguration",
        tableClassName: "table pqm-table",
        noColumnBorder: true,
        rowClassName: "table-row-mdms table-row-mdms-hover",
      },
      children: {},
      show: true,
    },
  },
  additionalSections: {},
  persistFormData: true,
  showAsRemovableTagsInMobile: false,
  customHookName: "microplan.useSavedMicroplans",
};

const SavedMicroplans = () => {
  const [showLoader, setShowLoader] = useState(false);
  const { state } = useMyContext();
  const history = useHistory();
  const { t } = useTranslation();

  const fetchHierarchyData = async (hierarchyType) => {
    const response = await Digit.CustomService.getResponse({
      url: "/boundary-service/boundary-hierarchy-definition/_search",
      useCache: false,
      method: "POST",
      userService: false,
      body: {
        BoundaryTypeHierarchySearchCriteria: {
          tenantId: Digit.ULBService.getStateId(),
          hierarchyType,
        },
      },
    });
    if (response?.BoundaryHierarchy?.length) {
      return response.BoundaryHierarchy[0].boundaryHierarchy.map((item) => item.boundaryType);
    }
    console.error("Invalid response structure");
  };

  const computeAdditionalProps = (row, state, t, hierarchyData) => {
    const campaignDetails = row?.original?.CampaignDetails;
    const readMeSheetName = state?.CommonConstants?.find((item) => item?.name === "readMeSheetName")?.value;
    return {
      hierarchyData,
      t,
      campaignType: campaignDetails?.projectType,
      campaignData: campaignDetails,
      readMeSheetName,
    };
  };

  const onClickRow = (row) => {
    const handleClick = async () => {
      setShowLoader(true);
      try {
        const campaignType = row?.original?.CampaignDetails?.projectType;
        const hierarchyData = await fetchHierarchyData(row?.original?.CampaignDetails?.hierarchyType);
        const additionalProps = computeAdditionalProps(row, state, t, hierarchyData);

        // Compute the session object based on the row?.original data and then re-route
        const computedSession = await updateSessionUtils.computeSessionObject(row.original, state, additionalProps);
        Digit.SessionStorage.set("microplanData", computedSession);

        setShowLoader(false);
        history.push(`/${window.contextPath}/employee/microplanning/create-microplan?id=${row?.original?.executionPlanId}`);
      } catch (error) {
        console.error(`Failed to process the request: ${error.message}`);
        setShowLoader(false);
      }
    };

    handleClick();
  };

  const savedMircoplanSession = Digit.Hooks.useSessionStorage("SAVED_MICROPLAN_SESSION", {});

  if (showLoader) {
    return <Loader />;
  }

  return (
    <React.Fragment>
      <Header className="works-header-search">{t(configs?.label)}</Header>
      <div className="inbox-search-wrapper">
        <InboxSearchComposerV2
          configs={configs}
          browserSession={savedMircoplanSession}
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

export default SavedMicroplans;
