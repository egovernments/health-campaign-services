import utils from "../utils";
import useCreateCampaign from "./useCreateCampaign";
import { useProductList } from "./useProductList";

const UserService = {};

const workbench = {};

const contracts = {};

const campaign = {
  useProductList,
  useCreateCampaign,
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
