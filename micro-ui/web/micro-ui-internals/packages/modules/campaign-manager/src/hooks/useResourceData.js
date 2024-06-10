export const useResourceData = async (data, hierarchyType, type, tenantId, id) => {
  let Type;
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
      await new Promise((resolve) => setTimeout(resolve, 5000));
    }
  }
  if (Error.isError) {
    return Error;
  }
  return searchResponse?.ResourceDetails?.[0];
};
