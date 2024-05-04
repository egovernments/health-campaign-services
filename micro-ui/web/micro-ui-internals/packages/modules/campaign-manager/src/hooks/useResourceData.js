export const useResourceData = async (data, hierarchyType, type, tenantId) => {
  let Type;
  if (type === "facilityWithBoundary") {
    Type = "facility";
  } else if (type === "userWithBoundary") {
    Type = "user";
  } else {
    Type = "boundaryWithTarget";
  }
  const response = await Digit.CustomService.getResponse({
    url: "/project-factory/v1/data/_create",
    body: {
      ResourceDetails: {
        type: Type,
        hierarchyType: hierarchyType,
        tenantId: Digit.ULBService.getCurrentTenantId(),
        fileStoreId: data?.[0]?.id,
        action: "validate",
        additionalDetails: {},
      },
    },
  });
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
      await new Promise((resolve) => setTimeout(resolve, 10000));
    }
  }

  return searchResponse?.ResourceDetails?.[0];
};
