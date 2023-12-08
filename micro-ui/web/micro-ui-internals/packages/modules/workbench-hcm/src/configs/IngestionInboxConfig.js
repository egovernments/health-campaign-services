const IngestionInboxConfig = () => {
  return {
    label: "HCM_WORKBENCH_INBOX",
    type: "inbox",
    apiDetails: {
      serviceName: "/hcm-moz-impl/v1/jobrecord/_search",
      requestParam: {
      },
      requestBody: {
        tenantId: Digit.ULBService.getCurrentTenantId(),
        IngestionSearchCriteria: {
            userId : Digit.UserService.getUser().info.uuid
        },
      },
      minParametersForSearchForm: 1,
      masterName: "commonUiConfig",
      moduleName: "IngestionConfig",
      tableFormJsonPath: "requestBody.IngestionSearchCriteria",
      filterFormJsonPath: "requestBody.IngestionSearchCriteria",
      searchFormJsonPath: "requestBody.IngestionSearchCriteria",
    },
    sections: {
      search: {
        uiConfig: {
          headerStyle: null,
          formClassName: "custom-both-clear-search",
          primaryLabel: "ES_COMMON_SEARCH",
          secondaryLabel: "ES_COMMON_CLEAR_SEARCH",
          minReqFields: 1,
          defaultValues: {
            ProjectId: "",
            measurementNumber: "",
            projectType: "",
          },
          fields: [
            {
              label: "Ingestion Id",
              type: "text",
              isMandatory: false,
              disable: false,
              populators: {
                name: "ingestionId",
                validation: { minlength: 2 },
              },
            },
            {
              label: "Ingestion Type",
              type: "dropdown",
              isMandatory: false,
              disable: false,
              populators: {
                name: "ingestionType",
                optionsKey: "code",
                optionsCustomStyle: {
                  top: "2.3rem",
                },
                options: [
                    {
                      code: "Organisation Units Ingestion",
                      name: "Organisation Units Ingestion"
                    },
                    {
                        code : "Facilities Ingestion",
                        name : "Facilities Ingestion"
                    },
                    {
                        code : "Users Ingestion",
                        name : "Users Ingestion"
                    },
                ]
              },
            },
            {
              label: "Ingestion Status",
              type: "dropdown",
              isMandatory: false,
              disable: false,
              populators: {
                name: "ingestionStatus",
                optionsKey: "name",
                optionsCustomStyle: {
                  top: "2.3rem",
                },
                options: [
                    {
                      code: "Started",
                      name: "Started",
                    },
                    {
                        code : "Failed",
                        name : "Failed"
                    },
                    {
                        code : "Completed",
                        name : "Completed"
                    },
                    {
                        code : "Partial Completed",
                        name : "Partial Completed"
                    }
                ]
              },
            },
          ],
        },
        label: "",
        children: {},
        show: true,
      },
      links: {
        uiConfig: {
          links: [
            {
              text: "OU Ingestion",
              url: "/employee/hcmworkbench/ou",
              roles: ["SYSTEM_ADMINISTRATOR"],
            },
            {
              text: "User Ingestion",
              url: "/employee/hcmworkbench/user",
              roles: ["SYSTEM_ADMINISTRATOR"],
            },
            {
              text: "Facility Ingestion",
              url: "/employee/hcmworkbench/facility",
              roles: ["SYSTEM_ADMINISTRATOR"],
            }
          ],
        },
        children: {},
        show: true,
      },
      filter: {
        uiConfig: {
          type: "filter",
          headerStyle: null,
          primaryLabel: "Apply",
          secondaryLabel: "",
          minReqFields: 1,
          defaultValues: {
            state: "",
            ward: [],
            locality: [],
            assignee: {
              code: "ASSIGNED_TO_ALL",
              name: "ASSIGNED_TO_ALL",
            },
          },
          fields: [
            {
              label: "",
              type: "radio",
              isMandatory: false,
              disable: false,
              populators: {
                name: "assignee",
                options: [
                  {
                    code: "ASSIGNED TO ME",
                    name: "MB_ASSIGNED_TO_ME",
                  },
                  {
                    code: "ASSIGNED_TO_ALL",
                    name: "MB_ASSIGNED_TO_ALL",
                  },
                ],
                optionsKey: "name",
                styles: {
                  gap: "1rem",
                  flexDirection: "column",
                },
                innerStyles: {
                  display: "flex",
                },
              },
            },
          ],
        },
        label: "Filter",
        show: true,
      },
      searchResult: {
        label: "",
        uiConfig: {
          columns: [
            {
              label: "Ingestion Id",
              jsonPath: "ingestionNumber",
              additionalCustomization: true,
            },
            {
              label: "Ingestion Type",
              jsonPath: "jobName",
            },
            {
              label: "Created Time",
              jsonPath: "auditDetails.createdTime",
              additionalCustomization: true,
            },
            {
              label: "Ingestion Status",
              jsonPath: "executionStatus",
            //   additionalCustomization: true,
            },
          ],
          enableGlobalSearch: false,
          enableColumnSort: true,
          resultsJsonPath: "job",
        },
        children: {},
        show: true,
      },
    },
  };
};

export default IngestionInboxConfig;