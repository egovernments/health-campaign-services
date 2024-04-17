import utils from "../utils";
import { useSearchCampaign } from "./services/useSearchCampaign";
import useCreateCampaign from "./useCreateCampaign";
import { useProductList } from "./useProductList";
import useUpdateCampaign from "./useUpdateCampaign";
import { useGenerateIdCampaign } from "./useGenerateIdCampaign";
import { useResourceData } from "./useResourceData";

const UserService = {};

const workbench = {};

const contracts = {};

const campaign = {
  useProductList,
  useCreateCampaign,
  useSearchCampaign,
  useUpdateCampaign,
  useGenerateIdCampaign,
  useResourceData
};

const Hooks = {
  campaign,
};

const Utils = {
  browser: {
    sample: () => {},
  },
  workbench: {
    ...utils,
  },
};

export const CustomisedHooks = {
  Hooks,
  UserService,
  Utils,
};
