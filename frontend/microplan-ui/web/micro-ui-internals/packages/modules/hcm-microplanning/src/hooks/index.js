import { logoutV1 } from "./logout";
import utils from "../utils";
import useCreatePlanConfig from "./useCreatePlanConfig";
import useSearchPlanConfig from "./useSearchPlanConfig";
import useUpdatePlanConfig from "./useUpdatePlanConfig";
import useSavedMicroplans from "./useSavedMicroplans";
import useSearchCampaign from "./useSearchCampaign";
import { useGenerateIdCampaign } from "./useGenerateIdCampaign";
const UserService = {
  logoutV1,
};

const microplan = {
  useCreatePlanConfig,
  useSearchPlanConfig,
  useUpdatePlanConfig,
  useSavedMicroplans,
  useSearchCampaign,
  useGenerateIdCampaign,
};

const contracts = {};

const Hooks = {
  attendance: {
    update: () => {},
  },
  microplan,
  contracts,
};

const Utils = {
  browser: {
    sample: () => {},
  },
  microplan: {
    ...utils,
  },
};

export const CustomisedHooks = {
  Hooks,
  UserService,
  Utils,
};
