export const myCampaignConfig = {
  tenantId: "pb",
  moduleName: "commonSanitationUiConfig",
  showTab: true,
  myCampaignConfig: [
    {
      label: "Vendor Search",
      type: "search",
      apiDetails: {
        serviceName: "/vendor/v1/_search",
        requestParam: {},
        requestBody: {},
        minParametersForSearchForm: 0,
        minParametersForFilterForm: 0,
        masterName: "commonUiConfig",
        moduleName: "MyCampaignConfigAdmin",
        tableFormJsonPath: "requestBody.inbox",
        filterFormJsonPath: "requestBody.custom",
        searchFormJsonPath: "requestBody.custom",
      },
      sections: {
        search: {
          uiConfig: {
            headerLabel: "ES_COMMON_SEARCH",
            type: "search",
            typeMobile: "filter",
            searchWrapperStyles: {
              flexDirection: "column-reverse",
              marginTop: "1.4rem",
              alignItems: "center",
              justifyContent: "end",
              gridColumn: "3",
            },
            headerStyle: null,
            primaryLabel: "Search",
            secondaryLabel: "ES_COMMON_CLEAR_SEARCH",
            minReqFields: 0,
            defaultValues: {
              id: "",
              plantCodes: "",
            },
            fields: [
              {
                label: "TQM_TEST_ID",
                type: "text",
                isMandatory: false,
                disable: false,
                populators: {
                  name: "id",
                  error: "TQM_ERR_VALID_TEST_ID",
                  style: {
                    marginBottom: "0px",
                  },
                },
              },
              {
                label: "TQM_PLANT_NAME",
                type: "apidropdown",
                isMandatory: false,
                disable: false,
                populators: {
                  optionsCustomStyle: {
                    top: "2.3rem",
                  },
                  name: "plantCodes",
                  optionsKey: "i18nKey",
                  allowMultiSelect: false,
                  masterName: "commonUiConfig",
                  moduleName: "MyCampaignConfigAdmin",
                  customfn: "populatePlantUsersReqCriteria",
                },
              },
            ],
          },
          label: "",
          labelMobile: "ES_COMMON_SEARCH",
          children: {},
          show: true,
        },
        searchResult: {
          uiConfig: {
            columns: [
              {
                label: "TQM_TEST_ID",
                jsonPath: "id",
                additionalCustomization: true,
              },
              {
                label: "TQM_PLANT_NAME",
                jsonPath: "name",
                prefix: "PQM.Plant_",
                translate: true,
              },
              {
                label: "TQM_INBOX_STATUS",
                jsonPath: "status",
                prefix: "WF_STATUS_",
                translate: true,
              },
              {
                label: "TQM_INBOX_SLA",
                jsonPath: "agencyType",
                additionalCustomization: true,
                disableSortBy: true,
              },
            ],
            enableGlobalSearch: false,
            enableColumnSort: true,
            resultsJsonPath: "vendor",
            customDefaultPagination: {
              limit: 5,
              offset: 0,
            },
            customPageSizesArray: [5, 10, 15, 20],
            tableClassName: "table pqm-table",
          },
          children: {},
          show: true,
        },
        links: {
          show: false,
        },
        filter: {
          show: false,
        },
      },
      additionalSections: {},
      persistFormData: true,
      showAsRemovableTagsInMobile: true,
    },
    {
      label: "Vehicle Seach",
      type: "search",
      apiDetails: {
        serviceName: "/hshs/vendor/v1/_search",
        requestParam: {},
        requestBody: {},
        minParametersForSearchForm: 0,
        minParametersForFilterForm: 0,
        masterName: "commonUiConfig",
        moduleName: "TabVehicleConfigAdmin",
        tableFormJsonPath: "requestBody.inbox",
        filterFormJsonPath: "requestBody.custom",
        searchFormJsonPath: "requestBody.custom",
      },
      sections: {
        search: {
          uiConfig: {
            headerLabel: "ES_COMMON_SEARCH",
            type: "search",
            typeMobile: "filter",
            searchWrapperStyles: {
              flexDirection: "column-reverse",
              marginTop: "1.4rem",
              alignItems: "center",
              justifyContent: "end",
              gridColumn: "3",
            },
            headerStyle: null,
            primaryLabel: "Search",
            secondaryLabel: "ES_COMMON_CLEAR_SEARCH",
            minReqFields: 0,
            defaultValues: {
              id: "",
              plantCodes: "",
            },
            fields: [
              {
                label: "VEHICLE SEARCH",
                type: "text",
                isMandatory: false,
                disable: false,
                populators: {
                  name: "id",
                  error: "TQM_ERR_VALID_TEST_ID",
                  style: {
                    marginBottom: "0px",
                  },
                },
              },
              {
                label: "VEHICLE_NAME",
                type: "text",
                isMandatory: false,
                disable: false,
                populators: {
                  name: "name",
                  error: "TQM_ERR_VALID_TEST_ID",
                  style: {
                    marginBottom: "0px",
                  },
                },
              },
            ],
          },
          label: "",
          labelMobile: "ES_COMMON_SEARCH",
          children: {},
          show: true,
        },
        searchResult: {
          uiConfig: {
            columns: [
              {
                label: "TQM_TEST_ID",
                jsonPath: "id",
                additionalCustomization: true,
              },
              {
                label: "TQM_PLANT_NAME",
                jsonPath: "name",
                prefix: "PQM.Plant_",
                translate: true,
              },
              {
                label: "TQM_INBOX_STATUS",
                jsonPath: "status",
                prefix: "WF_STATUS_",
                translate: true,
              },
              {
                label: "TQM_INBOX_SLA",
                jsonPath: "agencyType",
                additionalCustomization: true,
                disableSortBy: true,
              },
            ],
            enableGlobalSearch: false,
            enableColumnSort: true,
            resultsJsonPath: "vendor",
            tableClassName: "table pqm-table",
          },
          children: {},
          show: true,
        },
        links: {
          show: false,
        },
        filter: {
          show: false,
        },
      },
      additionalSections: {},
      persistFormData: true,
      showAsRemovableTagsInMobile: false,
    },
    {
      label: "Driver Search",
      type: "search",
      apiDetails: {
        serviceName: "/vendor/v1/_search",
        requestParam: {},
        requestBody: {},
        minParametersForSearchForm: 0,
        minParametersForFilterForm: 0,
        masterName: "commonUiConfig",
        moduleName: "MyCampaignConfigAdmin",
        tableFormJsonPath: "requestBody.inbox",
        filterFormJsonPath: "requestBody.custom",
        searchFormJsonPath: "requestBody.custom",
      },
      sections: {
        search: {
          uiConfig: {
            headerLabel: "ES_COMMON_SEARCH",
            type: "search",
            typeMobile: "filter",
            searchWrapperStyles: {
              flexDirection: "column-reverse",
              marginTop: "1.4rem",
              alignItems: "center",
              justifyContent: "end",
              gridColumn: "3",
            },
            headerStyle: null,
            primaryLabel: "Search",
            secondaryLabel: "ES_COMMON_CLEAR_SEARCH",
            minReqFields: 0,
            defaultValues: {
              id: "",
              plantCodes: "",
            },
            fields: [
              {
                label: "TQM_TEST_ID",
                type: "text",
                isMandatory: false,
                disable: false,
                populators: {
                  name: "id",
                  error: "TQM_ERR_VALID_TEST_ID",
                  style: {
                    marginBottom: "0px",
                  },
                },
              },
              {
                label: "TQM_PLANT_NAME",
                type: "apidropdown",
                isMandatory: false,
                disable: false,
                populators: {
                  optionsCustomStyle: {
                    top: "2.3rem",
                  },
                  name: "plantCodes",
                  optionsKey: "i18nKey",
                  allowMultiSelect: false,
                  masterName: "commonUiConfig",
                  moduleName: "MyCampaignConfigAdmin",
                  customfn: "populatePlantUsersReqCriteria",
                },
              },
            ],
          },
          label: "",
          labelMobile: "ES_COMMON_SEARCH",
          children: {},
          show: true,
        },
        searchResult: {
          uiConfig: {
            columns: [
              {
                label: "TQM_TEST_ID",
                jsonPath: "id",
                additionalCustomization: true,
              },
              {
                label: "TQM_PLANT_NAME",
                jsonPath: "name",
                prefix: "PQM.Plant_",
                translate: true,
              },
              {
                label: "TQM_INBOX_STATUS",
                jsonPath: "status",
                prefix: "WF_STATUS_",
                translate: true,
              },
              {
                label: "TQM_INBOX_SLA",
                jsonPath: "agencyType",
                additionalCustomization: true,
                disableSortBy: true,
              },
            ],
            enableGlobalSearch: false,
            enableColumnSort: true,
            resultsJsonPath: "vendor",
            tableClassName: "table pqm-table",
          },
          children: {},
          show: true,
        },
        links: {
          show: false,
        },
        filter: {
          show: false,
        },
      },
      additionalSections: {},
      persistFormData: true,
      showAsRemovableTagsInMobile: true,
    },
  ],
};
