import { Link } from "react-router-dom";
import _ from "lodash";
import React from "react";
import { statusBasedNavigation } from "../utils/statusBasedNavigation";

import { useLocation } from "react-router-dom";
import { useParams } from "react-router-dom";

//create functions here based on module name set in mdms(eg->SearchProjectConfig)
//how to call these -> Digit?.Customizations?.[masterName]?.[moduleName]
// these functions will act as middlewares
// var Digit = window.Digit || {};

export const UICustomizations = {
  IngestionConfig: {
    preProcess: (data) => {
      let ingestionSearchCriteria = data.body.IngestionSearchCriteria;
      data.params = {
        recordCount: data.state.tableForm.limit,
        sortBy: "createdTime",
        order: data?.body?.IngestionSearchCriteria?.SortBy?.code == "ASC" ? "ASC" : "DESC",
        offset: data?.body?.IngestionSearchCriteria?.offset,
      };

      const { ingestionType, ingestionStatus } = ingestionSearchCriteria;
      if (ingestionType?.code) ingestionSearchCriteria.ingestionType = ingestionType.code;
      if (ingestionStatus?.code) ingestionSearchCriteria.ingestionStatus = ingestionStatus.code;

      return data;
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      switch (key) {
        case "WORKBENCH_INGESTION_ID":
          return <span className="link">{statusBasedNavigation(row?.executionStatus, row?.jobID, value)}</span>;

        case "WORKBENCH_CREATED_TIME":
          return <span>{Digit.DateUtils.ConvertEpochToDate(value)}</span>;

        default:
          return t("ES_COMMON_NA");
      }
    },
  },
  SearchDefaultConfig: {
    customValidationCheck: (data) => {
      //checking both to and from date are present
      const { createdFrom, createdTo } = data;
      if ((createdFrom === "" && createdTo !== "") || (createdFrom !== "" && createdTo === ""))
        return { warning: true, label: "ES_COMMON_ENTER_DATE_RANGE" };

      return false;
    },
    preProcess: (data) => {
      const location = useLocation();
      data.params = { ...data.params };
      const { masterName } = useParams();

      const searchParams = new URLSearchParams(location.search);
      const paths = {
        SearchProjectConfig: {
          basePath: "Projects",
          pathConfig: {
            // id: "id[0]",
            tenantId: "tenantId",
            boundary: "address.boundary"
          },
          dateConfig: {
            endDate: "dayend",
            startDate: "daystart",
          },
          selectConfig: {},
          textConfig: ["id", "tenantId", "name", "projectNumber", "boundary", "projectType"],
        },
        SearchProductConfig: {
          basePath: "Product",
          pathConfig: {
            id: "id[0]",
          },
          dateConfig: {},
          selectConfig: {},
          textConfig: ["id", "manufacturer", "name", "type"],
        },
        SearchHouseholdConfig: {
          basePath: "Household",
          pathConfig: {
            id: "id[0]",
            clientReferenceId: "clientReferenceId[0]",
          },
          dateConfig: {},
          selectConfig: {},
          textConfig: ["boundaryCode", "clientReferenceId", "id"],
        },
        SearchProductVariantConfig: {
          basePath: "ProductVariant",
          pathConfig: {
            id: "id[0]",
          },
          dateConfig: {},
          selectConfig: {},
          textConfig: ["productId", "sku", "variation" , "id"],
        },
        SearchProjectBeneficiaryConfig: {
          basePath: "ProjectBeneficiary",
          pathConfig: {
            id: "id[0]",
            clientReferenceId: "clientReferenceId[0]",
          },
          dateConfig: {
            dateOfRegistration: "daystart",
          },
          selectConfig: {},
          textConfig: ["beneficiaryId", "projectId" , "id" , "clientReferenceId"],
        },
        SearchProjectStaffConfig: {
          basePath: "ProjectStaff",
          pathConfig: {
            id: "id[0]",
          },
          dateConfig: {
            startDate: "daystart",
            endDate: "dayend",
          },
          selectConfig: {},
          textConfig: ["projectId", "userId" , "id"],
        },
        SearchProjectResourceConfig: {
          basePath: "ProjectResource",
          pathConfig: {
            id: "id[0]",
          },
          dateConfig: {},
          selectConfig: {},
          textConfig: ["id"],
        },
        SearchProjectTaskConfig: {
          basePath: "Task",
          pathConfig: {
            id: "id[0]",
            clientReferenceId: "clientReferenceId[0]",
          },
          dateConfig: {
            plannedEndDate: "dayend",
            plannedStartDate: "daystart",
            actualEndDate: "dayend",
            actualStartDate: "daystart",
          },
          selectConfig: {},
          textConfig: ["projectId", "localityCode", "projectBeneficiaryId", "status", "id", "clientReferenceId"],
        },
        SearchFacilityConfig: {
          basePath: "Facility",
          pathConfig: {
            id: "id[0]",
          },
          dateConfig: {},
          selectConfig: {},
          textConfig: ["faciltyUsage", "localityCode", "storageCapacity", "id"],
        },
        SearchProjectFacilityConfig: {
          basePath: "ProjectFacility",
          pathConfig: {
            id: "id[0]",
            projectId: "projectId[0]",
            facilityId: "facilityId[0]",
          },
          dateConfig: {},
          selectConfig: {},
          textConfig: ["id", "projectId", "facilityId"],
        },
      };

      const id = searchParams.get("config") || masterName;

      if (!paths || !paths?.[id]) {
        return data;
      }
      let requestBody = { ...data.body[paths[id]?.basePath] };
      const pathConfig = paths[id]?.pathConfig;
      const dateConfig = paths[id]?.dateConfig;
      const selectConfig = paths[id]?.selectConfig;
      const textConfig = paths[id]?.textConfig;

      if (paths[id].basePath == "Projects") {
        data.state.searchForm = { ...data.state.searchForm, tenantId: "mz" };
      }
      let Product = Object.keys(requestBody)
        .map((key) => {
          if (selectConfig[key]) {
            requestBody[key] = _.get(requestBody, selectConfig[key], null);
          } else if (typeof requestBody[key] == "object") {
            requestBody[key] = requestBody[key]?.code;
          } else if (textConfig?.includes(key)) {
            requestBody[key] = requestBody[key]?.trim();
          }
          return key;
        })
        .filter((key) => requestBody[key])
        .reduce((acc, curr) => {
          if (pathConfig[curr]) {
            _.set(acc, pathConfig[curr], requestBody[curr]);
          } else if (dateConfig[curr] && dateConfig[curr]?.includes("day")) {
            _.set(acc, curr, Digit.Utils.date.convertDateToEpoch(requestBody[curr], dateConfig[curr]));
          } else {
            _.set(acc, curr, requestBody[curr]);
          }
          return acc;
        }, {});

      if (paths[id].basePath == "Projects") {
        data.body[paths[id].basePath] = [{ ...Product }];
      } else data.body[paths[id].basePath] = { ...Product };
      return data;
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      
      //here we can add multiple conditions
      //like if a cell is link then we return link
      //first we can identify which column it belongs to then we can return relevant result
      switch (key) {
        case "PROJECT_NUMBER":
          
          return (
            <span className="link">
              <Link
                to={`/${window.contextPath}/employee/hcmworkbench/campaign-view?tenantId=${row?.tenantId}&projectNumber=${row?.projectNumber}`}
              >{row?.projectNumber}

              </Link>
            </span>
          );

          case "PROJECT_ID":
          return (
            <span className="link">
              <Link
                to={`/${window.contextPath}/employee/hcmworkbench/campaign-view?tenantId=${row?.tenantId}&projectId=${row?.projectId}`}
              >{row?.projectId}

              </Link>
            </span>
          );

        case "PROJECT_CREATED_TIME":
          const todayEpochStart = new Date();

          const createdEpochTime = row.auditDetails.createdTime;
          const createdDateString = Digit.Utils.date.convertEpochToDate(createdEpochTime);

          // Assuming createdDateString is in the format "DD/MM/YYYY"
          const [day, month, year] = createdDateString.split("/").map(Number);
          const currentTime = new Date(year, month - 1, day); 

          const timeDifference = todayEpochStart.getTime() - currentTime.getTime();

          const daysAgo = Math.floor(timeDifference / (1000 * 60 * 60 * 24));

          return (
            <div
              style={{
                display: "flex",
                flexDirection: "column",
              }}
            >
              {daysAgo < 1 && (
                <span
                  style={{
                    position: "relative",
                    top: "0",
                    right: "0",
                    background: "green",
                    color: "white",
                    padding: "4px 8px",
                    borderRadius: "4px",
                    fontSize: "12px",
                    marginRight: "1px",
                    marginLeft: "90px",
                    width: "fit-content",
                  }}
                >
                  {`${t("HCM_VIEW_NEW")}`}
                </span>
              )}
              <span>{Digit.Utils.date.convertEpochToDate(row.auditDetails.createdTime)}</span>
            </div>
          );
        default:
          return t("ES_COMMON_NA");
      }
    },
    MobileDetailsOnClick: (row, tenantId) => {
      let link;
      Object.keys(row).map((key) => {
        if (key === "MASTERS_WAGESEEKER_ID")
          link = `/${window.contextPath}/employee/masters/view-wageseeker?tenantId=${tenantId}&wageseekerId=${row[key]}`;
      });
      return link;
    },
    additionalValidations: (type, data, keys) => {
      if (type === "date") {
        return data[keys.start] && data[keys.end] ? () => new Date(data[keys.start]).getTime() <= new Date(data[keys.end]).getTime() : true;
      }
    },
  },
};
