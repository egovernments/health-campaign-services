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
      minParametersForSearchForm: 0,
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
          minReqFields: 0,
          defaultValues: {
            ProjectId: "",
            measurementNumber: "",
            projectType: "",
            ingestionType: "",
            ingestionId: "",
            ingestionStatus: "",


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
                optionsKey: "name",
                optionsCustomStyle: {
                  top: "2.3rem",
                },
                options: [
                    {
                        code : "Facilities Ingestion",
                        name : "WORKBENCH_FACILITY_INGESTION"
                    },
                    {
                        code : "Users Ingestion",
                        name : "WORKBENCH_USERS_INGESTION"
                    },
                    {
                      code : "Project Ingestion",
                      name : "WORKBENCH_PROJECT_INGESTION"
                  },
                  {
                      code : "Boundary Ingestion",
                      name : "WORKBENCH_BOUNDARY_INGESTION"
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
                      code: "Started",
                      name: "WORKBENCH_STARTED",
                    },
                    {
                        code : "Failed",
                        name : "WORKBENCH_FAILED"
                    },
                    {
                        code : "Completed",
                        name : "WORKBENCH_COMPLETED"
                    },
                    {
                        code : "Partial Completed",
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
              text: "WORKBENCH_BOUNDARY",
              url: "/employee/hcmworkbench/boundary",
              roles: ["SYSTEM_ADMINISTRATOR","CAMPAIGN_ADMIN"],
            },
            {
              text: "WORKBENCH_PROJECT",
              url: "/employee/hcmworkbench/project",
              roles: ["SYSTEM_ADMINISTRATOR","CAMPAIGN_ADMIN"],
            },
            {
              text: "WORKBENCH_USER",
              url: "/employee/hcmworkbench/user",
              roles: ["SYSTEM_ADMINISTRATOR","CAMPAIGN_ADMIN"],
            },
            {
              text: "WORKBENCH_FACILITY",
              url: "/employee/hcmworkbench/facility",
              roles: ["SYSTEM_ADMINISTRATOR","CAMPAIGN_ADMIN"],
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
            SortBy: {
              code: "empty",
              name: "SortBy",
            },
          },
          fields: [
            {
              label: "",
              type: "radio",
              isMandatory: false,
              disable: false,
              populators: {
                name: "SortBy",
                options: [
                  {
                    code: "ASC",
                    name: "WORKBENCH_ASC",
                  },
                  {
                    code: "DESC",
                    name: "WORKBENCH_DESC",
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
        label: "Sort By",
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