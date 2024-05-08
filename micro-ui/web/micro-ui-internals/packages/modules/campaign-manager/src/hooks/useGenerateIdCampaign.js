export const useGenerateIdCampaign = (type ,hierarchyType, filters) => {
  const reqCriteria = {
    url: `/project-factory/v1/data/_generate`,
    changeQueryName :`${type}${hierarchyType}`,
    params: {
      tenantId:  Digit.ULBService.getCurrentTenantId(),
      type: type,
      forceUpdate: true,
      hierarchyType: hierarchyType,
    },
    body:{
      "Filters": null 
    }
    // body: (type === 'boundary' ? (filters === undefined ? { "Filters": null } : { "Filters": { "boundaries": filters } }) : {}),
  };

  const { data: Data } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  return Data?.GeneratedResource?.[0]?.id;
};
