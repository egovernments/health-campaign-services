export const useResourceData = async (data, hierarchyType, type) => {
  let fileStoreId;
  if (type === "facility") {
    fileStoreId = data?.uploadFacility?.[0]?.id;
  } else if (type === "user") {
    fileStoreId = data?.uploadUser?.[0]?.id;
  } else {
    fileStoreId = data?.uploadBoundary?.[0]?.id;
  }
  const response = await Digit.CustomService.getResponse({
    url: "/project-factory/v1/data/_create",
    body: {
      ResourceDetails: {
        type: type,
        hierarchyType: hierarchyType,
        tenantId: Digit.ULBService.getCurrentTenantId(),
        fileStoreId: fileStoreId,
        action: "validate",
        additionalDetails: {}
      },
    },
  });

  // const responseSearch = await  Digit.CustomService.getResponse({
  //   url: "/project-factory/v1/data/_search",
  //   body: {
  //     SearchCriteria: {
  //       id: [response?.ResourceDetails?.id],
  //       tenantId: "mz",
  //       type: type,
  //     },
  //   },
  // });

  // console.log("result", responseSearch);

  return response;
};
