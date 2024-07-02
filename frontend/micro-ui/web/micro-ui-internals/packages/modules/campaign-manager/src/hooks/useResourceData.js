import axios from 'axios';
import * as XLSX from 'xlsx';
import { baseTimeOut } from '../configs/baseTimeOut';
export const useResourceData = async (data, hierarchyType, type, tenantId, id , baseTimeOut) => {


  let Type;
  let jsonDataLength;
  let Error = {
    isError: false,
    error: {},
  };
  let response;
  if (type === "facilityWithBoundary") {
    Type = "facility";
  } else if (type === "userWithBoundary") {
    Type = "user";
  } else {
    Type = "boundaryWithTarget";
  }
  try {
    if(data){
      axios
      .get("/filestore/v1/files/id", {
        responseType: "arraybuffer",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "auth-token": Digit.UserService.getUser()?.["access_token"],
        },
        params: {
          tenantId: Digit.ULBService.getCurrentTenantId(),
          fileStoreId: data?.[0]?.filestoreId,
        },
      })
      .then(async (res) => {
        const fileData = res.data;
        const workbook = XLSX.read(fileData, { type: 'buffer' });
        const sheetName = workbook.SheetNames[1];
        const worksheet = workbook.Sheets[sheetName];
        const jsonData = XLSX.utils.sheet_to_json(worksheet);
        jsonDataLength = jsonData.length;
      });
    }
    const responseTemp = await Digit.CustomService.getResponse({
      url: "/project-factory/v1/data/_create",
      body: {
        ResourceDetails: {
          type: Type,
          hierarchyType: hierarchyType,
          tenantId: Digit.ULBService.getCurrentTenantId(),
          fileStoreId: data?.[0]?.filestoreId,
          action: "validate",
          campaignId: id,
          additionalDetails: {},
        },
      },
    });
    
    response = responseTemp;

  } catch (error) {
    if (error?.response && error?.response?.data) {
      const errorMessage = error?.response?.data?.Errors?.[0]?.message;
      const errorDescription = error?.response?.data?.Errors?.[0]?.description;
      if (errorDescription) {
        Error.error = `${errorMessage} : ${errorDescription}`;
        Error.isError = true;
        return Error;
      } else {
        Error = errorMessage;
        Error.isError = true;
        return Error;
      }
    }
  }

  let searchResponse;
  let status = "validation-started";
  const baseDelay = baseTimeOut?.baseTimeout?.[0]?.baseTimeOut;
  const maxTime = baseTimeOut?.baseTimeout?.[0]?.maxTime;
  let retryInterval = Math.min(baseDelay * jsonDataLength , maxTime);

  await new Promise((resolve) => setTimeout(resolve, retryInterval));

  // Retry until a response is received
  while (status !== "failed" && status !== "invalid" && status !== "completed") {
    searchResponse = await Digit.CustomService.getResponse({
      url: "/project-factory/v1/data/_search",
      body: {
        SearchCriteria: {
          id: [response?.ResourceDetails?.id],
          tenantId: tenantId,
          type: Type,
        },
      },
    });
    status = searchResponse?.ResourceDetails?.[0]?.status;
    if (status !== "failed" && status !== "invalid" && status !== "completed") {
      await new Promise((resolve) => setTimeout(resolve, retryInterval));
    }
  }
  if (Error.isError) {
    return Error;
  }
  return searchResponse?.ResourceDetails?.[0];
};
