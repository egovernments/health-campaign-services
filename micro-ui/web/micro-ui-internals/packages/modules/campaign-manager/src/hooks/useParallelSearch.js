const useParallelSearch = ({ parentArray, tenantId, boundaryType, hierarchy, parentCode }) => {
  for (const parentCode of parentArray) {
    const reqCriteriaBoundaryTypeSearch = Digit.CustomService.getResponse({
      url: "/boundary-service/boundary-relationships/_search",
      params: {
        tenantId: tenantId,
        hierarchyType:hierarchy,
        boundaryType: boundaryType,
        parent:       parentCode,
      },
      body: {},
    });
    // setShowToast({ key: "info", label: t("HCM_PLEASE_WAIT_LOADING_BOUNDARY") });
    setLoaderEnabled(true);
    const boundaryTypeData = await reqCriteriaBoundaryTypeSearch;
    newData.push({ parentCode, boundaryTypeData });
  }
};

export default useParallelSearch;
