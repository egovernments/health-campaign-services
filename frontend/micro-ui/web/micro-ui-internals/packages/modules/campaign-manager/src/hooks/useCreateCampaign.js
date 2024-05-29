import { useMutation } from "react-query";
import createCampaignService from "./services/createCampaignService";

const useCreateCampaign = (tenantId) => {
  return useMutation((reqData) => {
    return createCampaignService(reqData, tenantId);
  });
};

export default useCreateCampaign;
