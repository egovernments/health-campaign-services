export const useGenerateIdCampaign = (type ,hierarchyType, filters) => {
  const reqCriteriaFacility = {
    url: `/project-factory/v1/data/_generate`,
    changeQueryName :`${type}${hierarchyType}${filters}`,
    params: {
      tenantId:  Digit.ULBService.getCurrentTenantId(),
      type: type,
      forceUpdate: true,
      hierarchyType: hierarchyType,
    },
    body: (type === 'boundary' ? (filters === undefined ? { "Filters": null } : { "Filters": { "boundaries": filters } }) : {}),
  };

  const { isLoading, data: Data } = Digit.Hooks.useCustomAPIHook(reqCriteriaFacility);

  return Data?.GeneratedResource?.[0]?.id;
};
