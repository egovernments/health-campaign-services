import { useQuery } from "react-query";

const searchCampaignService = async ({ tenantId, filter, pagination }) => {
  const response = await Digit.CustomService.getResponse({
    url: "/project-factory/v1/project-type/search",
    body: {
      CampaignDetails: {
        tenantId: tenantId,
        ...filter,
        pagination: {
          ...pagination,
        },
      },
    },
  });
  return response?.CampaignDetails;
};

export const useSearchCampaign = ({ tenantId, filter, pagination, config = {} }) => {
  return useQuery(["SEARCH_CAMPAIGN", tenantId, filter, pagination], () => searchCampaignService({ tenantId, filter, pagination }), config);
};
