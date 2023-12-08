const IngestionInboxConfig = () => {
  return {
    label: "WORKBENCH_INBOX",
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
              label: "WORKBENCH_INGESTION_ID",
              type: "text",
              isMandatory: false,
              disable: false,
              populators: {
                name: "ingestionId",
                validation: { minlength: 2 },
              },
            },
            {
              label: "WORKBENCH_INGESTION_TYPE",
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
                      code: "WORKBENCH_OU_INGESTION",
                      name: "WORKBENCH_OU_INGESTION"
                    },
                    {
                        code : "WORKBENCH_FACILITY_INGESTION",
                        name : "WORKBENCH_FACILITY_INGESTION"
                    },
                    {
                        code : "WORKBENCH_USERS_INGESTION",
                        name : "WORKBENCH_USERS_INGESTION"
                    },
                ]
              },
            },
            {
              label: "WORKBENCH_INGESTION_STATUS",
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
                      code: "WORKBENCH_STARTED",
                      name: "WORKBENCH_STARTED",
                    },
                    {
                        code : "WORKBENCH_FAILED",
                        name : "WORKBENCH_FAILED"
                    },
                    {
                        code : "WORKBENCH_COMPLETED",
                        name : "WORKBENCH_COMPLETED"
                    },
                    {
                        code : "WORKBENCH_PARTIAL_COMPLETED",
                        name : "WORKBENCH_PARTIAL_COMPLETED"
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
              text: "WORKBENCH_OU_INGESTION",
              url: "/employee/hcmworkbench/ou",
              roles: ["SYSTEM_ADMINISTRATOR"],
            },
            {
              text: "WORKBENCH_USERS_INGESTION",
              url: "/employee/hcmworkbench/user",
              roles: ["SYSTEM_ADMINISTRATOR"],
            },
            {
              text: "WORKBENCH_FACILITY_INGESTION",
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
          primaryLabel: "WORKBENCH_APPLY",
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
        label: "WORKBENCH_FILTER",
        show: true,
      },
      searchResult: {
        label: "",
        uiConfig: {
          columns: [
            {
              label: "WORKBENCH_INGESTION_ID",
              jsonPath: "ingestionNumber",

              additionalCustomization: true,
            },
            {
              label: "WORKBENCH_INGESTION_TYPE",
              jsonPath: "jobName",
            },
            {
              label: "WORKBENCH_CREATED_TIME",
              jsonPath: "auditDetails.createdTime",
              additionalCustomization: true,
            },
            {
              label: "WORKBENCH_INGESTION_STATUS",
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