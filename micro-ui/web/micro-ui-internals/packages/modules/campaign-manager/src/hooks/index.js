import utils from "../utils";
import { useSearchCampaign } from "./services/useSearchCampaign";
import useCreateCampaign from "./useCreateCampaign";
import { useProductList } from "./useProductList";

const UserService = {};

const workbench = {};

const contracts = {};

const campaign = {
  useProductList,
  useCreateCampaign,
  useSearchCampaign,
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
