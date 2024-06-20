export const useGenerateIdCampaign = ({ type, hierarchyType, filters, campaignId, config = {}, forceUpdate = "true" }) => {
  const updatedFilters = filters?.map(({ type, ...rest }) => ({
    ...rest,
    boundaryType: type,
  }));
  const reqCriteria = {
    url: `/project-factory/v1/data/_generate`,
    changeQueryName: `${type}${hierarchyType}${filters}`,
    params: {
      tenantId: Digit.ULBService.getCurrentTenantId(),
      type: type,
      forceUpdate: forceUpdate,
      hierarchyType: hierarchyType,
      campaignId: campaignId,
    },
    body: type === "boundary" ? (updatedFilters === undefined ? { Filters: null } : { Filters: { boundaries: updatedFilters } }) : {},
    config: {
      ...config,
      cacheTime: 0,
      staleTime: 0,
    },
  };
  const { data: Data, refetch, isLoading } = Digit.Hooks.useCustomAPIHook(reqCriteria);

  return { isLoading: isLoading, data: Data?.GeneratedResource?.[0]?.id, refetch };
};
