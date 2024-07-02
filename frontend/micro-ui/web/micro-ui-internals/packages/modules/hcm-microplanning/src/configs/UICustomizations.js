import _ from "lodash";

//create functions here based on module name set in mdms(eg->SearchProjectConfig)
//how to call these -> Digit?.Customizations?.[masterName]?.[moduleName]
// these functions will act as middlewares
// var Digit = window.Digit || {};

const businessServiceMap = {
  "muster roll": "MR",
};

const inboxModuleNameMap = {
  "muster-roll-approval": "muster-roll-service",
};

function filterUniqueByKey(arr, key) {
  const uniqueValues = new Set();
  const result = [];

  arr.forEach((obj) => {
    const value = obj[key];
    if (!uniqueValues.has(value)) {
      uniqueValues.add(value);
      result.push(obj);
    }
  });

  return result;
}

const epochTimeForTomorrow12 = () => {
  const now = new Date();

  // Create a new Date object for tomorrow at 12:00 PM
  const tomorrowNoon = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 12, 0, 0, 0);

  // Format the date as "YYYY-MM-DD"
  const year = tomorrowNoon.getFullYear();
  const month = String(tomorrowNoon.getMonth() + 1).padStart(2, "0"); // Months are 0-indexed
  const day = String(tomorrowNoon.getDate()).padStart(2, "0");

  return Digit.Utils.date.convertDateToEpoch(`${year}-${month}-${day}`);
};

function cleanObject(obj) {
  for (const key in obj) {
    if (Object.hasOwn(obj, key)) {
      if (Array.isArray(obj[key])) {
        if (obj[key].length === 0) {
          delete obj[key];
        }
      } else if (
        obj[key] === undefined ||
        obj[key] === null ||
        obj[key] === false ||
        obj[key] === "" || // Check for empty string
        (typeof obj[key] === "object" && Object.keys(obj[key]).length === 0)
      ) {
        delete obj[key];
      }
    }
  }
  return obj;
}

export const UICustomizations = {
  businessServiceMap,
  updatePayload: (applicationDetails, data, action, businessService) => {
    if (businessService === businessServiceMap.estimate) {
      const workflow = {
        comment: data.comments,
        documents: data?.documents?.map((document) => {
          return {
            documentType: action?.action + " DOC",
            fileName: document?.[1]?.file?.name,
            fileStoreId: document?.[1]?.fileStoreId?.fileStoreId,
            documentUid: document?.[1]?.fileStoreId?.fileStoreId,
            tenantId: document?.[1]?.fileStoreId?.tenantId,
          };
        }),
        assignees: data?.assignees?.uuid ? [data?.assignees?.uuid] : null,
        action: action.action,
      };
      //filtering out the data
      Object.keys(workflow).forEach((key, index) => {
        if (!workflow[key] || workflow[key]?.length === 0) delete workflow[key];
      });

      return {
        estimate: applicationDetails,
        workflow,
      };
    }
    if (businessService === businessServiceMap.contract) {
      const workflow = {
        comment: data?.comments,
        documents: data?.documents?.map((document) => {
          return {
            documentType: action?.action + " DOC",
            fileName: document?.[1]?.file?.name,
            fileStoreId: document?.[1]?.fileStoreId?.fileStoreId,
            documentUid: document?.[1]?.fileStoreId?.fileStoreId,
            tenantId: document?.[1]?.fileStoreId?.tenantId,
          };
        }),
        assignees: data?.assignees?.uuid ? [data?.assignees?.uuid] : null,
        action: action.action,
      };
      //filtering out the data
      Object.keys(workflow).forEach((key, index) => {
        if (!workflow[key] || workflow[key]?.length === 0) delete workflow[key];
      });

      return {
        contract: applicationDetails,
        workflow,
      };
    }
    if (businessService === businessServiceMap?.["muster roll"]) {
      const workflow = {
        comment: data?.comments,
        documents: data?.documents?.map((document) => {
          return {
            documentType: action?.action + " DOC",
            fileName: document?.[1]?.file?.name,
            fileStoreId: document?.[1]?.fileStoreId?.fileStoreId,
            documentUid: document?.[1]?.fileStoreId?.fileStoreId,
            tenantId: document?.[1]?.fileStoreId?.tenantId,
          };
        }),
        assignees: data?.assignees?.uuid ? [data?.assignees?.uuid] : null,
        action: action.action,
      };
      //filtering out the data
      Object.keys(workflow).forEach((key, index) => {
        if (!workflow[key] || workflow[key]?.length === 0) delete workflow[key];
      });

      return {
        musterRoll: applicationDetails,
        workflow,
      };
    }
    if (businessService === businessServiceMap?.["works.purchase"]) {
      const workflow = {
        comment: data.comments,
        documents: data?.documents?.map((document) => {
          return {
            documentType: action?.action + " DOC",
            fileName: document?.[1]?.file?.name,
            fileStoreId: document?.[1]?.fileStoreId?.fileStoreId,
            documentUid: document?.[1]?.fileStoreId?.fileStoreId,
            tenantId: document?.[1]?.fileStoreId?.tenantId,
          };
        }),
        assignees: data?.assignees?.uuid ? [data?.assignees?.uuid] : null,
        action: action.action,
      };
      //filtering out the data
      Object.keys(workflow).forEach((key, index) => {
        if (!workflow[key] || workflow[key]?.length === 0) delete workflow[key];
      });

      const additionalFieldsToSet = {
        projectId: applicationDetails.additionalDetails.projectId,
        invoiceDate: applicationDetails.billDate,
        invoiceNumber: applicationDetails.referenceId.split("_")?.[1],
        contractNumber: applicationDetails.referenceId.split("_")?.[0],
        documents: applicationDetails.additionalDetails.documents,
      };
      return {
        bill: { ...applicationDetails, ...additionalFieldsToSet },
        workflow,
      };
    }
  },
  enableModalSubmit: (businessService, action, setModalSubmit, data) => {
    if (businessService === businessServiceMap?.["muster roll"] && action.action === "APPROVE") {
      setModalSubmit(data?.acceptTerms);
    }
  },
  enableHrmsSearch: (businessService, action) => {
    if (businessService === businessServiceMap.estimate) {
      return action.action.includes("TECHNICALSANCTION") || action.action.includes("VERIFYANDFORWARD");
    }
    if (businessService === businessServiceMap.contract) {
      return action.action.includes("VERIFY_AND_FORWARD");
    }
    if (businessService === businessServiceMap?.["muster roll"]) {
      return action.action.includes("VERIFY");
    }
    if (businessService === businessServiceMap?.["works.purchase"]) {
      return action.action.includes("VERIFY_AND_FORWARD");
    }
    return false;
  },
  getBusinessService: (moduleCode) => {
    if (moduleCode?.includes("estimate")) {
      return businessServiceMap?.estimate;
    } else if (moduleCode?.includes("contract")) {
      return businessServiceMap?.contract;
    } else if (moduleCode?.includes("muster roll")) {
      return businessServiceMap?.["muster roll"];
    } else if (moduleCode?.includes("works.purchase")) {
      return businessServiceMap?.["works.purchase"];
    } else if (moduleCode?.includes("works.wages")) {
      return businessServiceMap?.["works.wages"];
    } else if (moduleCode?.includes("works.supervision")) {
      return businessServiceMap?.["works.supervision"];
    } else {
      return businessServiceMap;
    }
  },
  getInboxModuleName: (moduleCode) => {
    if (moduleCode?.includes("estimate")) {
      return inboxModuleNameMap?.estimate;
    } else if (moduleCode?.includes("contract")) {
      return inboxModuleNameMap?.contracts;
    } else if (moduleCode?.includes("attendence")) {
      return inboxModuleNameMap?.attendencemgmt;
    } else {
      return inboxModuleNameMap;
    }
  },
  SearchCampaign: {
    preProcess: (data, additionalDetails) => {
      const { campaignName = "", endDate = "", projectType = "", startDate = "" } = data?.state?.searchForm || {};
      data.body.CampaignDetails = {};
      data.body.CampaignDetails.pagination = data?.state?.tableForm;
      data.body.CampaignDetails.tenantId = Digit.ULBService.getCurrentTenantId();
      // data.body.CampaignDetails.boundaryCode = boundaryCode;
      data.body.CampaignDetails.createdBy = Digit.UserService.getUser().info.uuid;
      data.body.CampaignDetails.campaignName = campaignName;
      data.body.CampaignDetails.status = ["drafted"];
      if (startDate) {
        data.body.CampaignDetails.startDate = Digit.Utils.date.convertDateToEpoch(startDate);
      } else {
        data.body.CampaignDetails.startDate = epochTimeForTomorrow12();
      }
      if (endDate) {
        data.body.CampaignDetails.endDate = Digit.Utils.date.convertDateToEpoch(endDate);
      }
      data.body.CampaignDetails.projectType = projectType?.[0]?.code;

      cleanObject(data.body.CampaignDetails);

      return data;
    },
    populateProjectType: () => {
      const tenantId = Digit.ULBService.getCurrentTenantId();

      return {
        url: "/egov-mdms-service/v1/_search",
        params: { tenantId },
        body: {
          MdmsCriteria: {
            tenantId,
            moduleDetails: [
              {
                moduleName: "HCM-PROJECT-TYPES",
                masterDetails: [
                  {
                    name: "projectTypes",
                  },
                ],
              },
            ],
          },
        },
        changeQueryName: "projectType",
        config: {
          enabled: true,
          select: (data) => {
            const dropdownData = filterUniqueByKey(data?.MdmsRes?.["HCM-PROJECT-TYPES"]?.projectTypes, "code").map((row) => {
              return {
                ...row,
                i18nKey: Digit.Utils.locale.getTransformedLocale(`CAMPAIGN_TYPE_${row.code}`),
              };
            });
            return dropdownData;
          },
        },
      };
    },
    customValidationCheck: (data) => {
      //checking if both to and from date are present then they should be startDate<=endDate
      const { startDate, endDate } = data;
      const startDateEpoch = Digit.Utils.date.convertDateToEpoch(startDate);
      const endDateEpoch = Digit.Utils.date.convertDateToEpoch(endDate);

      if (startDate && endDate && startDateEpoch > endDateEpoch) {
        return { warning: true, label: "ES_COMMON_ENTER_DATE_RANGE" };
      }
      return false;
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      if (key === "CAMPAIGN_DATE") {
        return `${Digit.DateUtils.ConvertEpochToDate(value)} - ${Digit.DateUtils.ConvertEpochToDate(row?.endDate)}`;
      }
    },
  },
  SearchMicroplan: {
    preProcess: (data, additionalDetails) => {
      const { name, status } = data?.state?.searchForm || {};

      data.body.PlanConfigurationSearchCriteria = {};
      data.body.PlanConfigurationSearchCriteria.limit = data?.state?.tableForm?.limit;
      // data.body.PlanConfigurationSearchCriteria.limit = 10
      data.body.PlanConfigurationSearchCriteria.offset = data?.state?.tableForm?.offset;
      data.body.PlanConfigurationSearchCriteria.name = name;
      data.body.PlanConfigurationSearchCriteria.tenantId = Digit.ULBService.getCurrentTenantId();
      data.body.PlanConfigurationSearchCriteria.userUuid = Digit.UserService.getUser().info.uuid;
      // delete data.body.PlanConfigurationSearchCriteria.pagination
      data.body.PlanConfigurationSearchCriteria.status = status?.status;
      cleanObject(data.body.PlanConfigurationSearchCriteria);
      return data;
    },
    additionalCustomizations: (row, key, column, value, t, searchResult) => {
      if (key === "CAMPAIGN_DATE") {
        return `${Digit.DateUtils.ConvertEpochToDate(value)} - ${Digit.DateUtils.ConvertEpochToDate(row?.CampaignDetails?.endDate)}`;
      }
    },
  },
};
