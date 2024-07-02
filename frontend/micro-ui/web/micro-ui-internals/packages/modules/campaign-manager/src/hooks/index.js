import utils from "../utils";
import { useSearchCampaign } from "./services/useSearchCampaign";
import useCreateCampaign from "./useCreateCampaign";
import { useProductList } from "./useProductList";
import useUpdateCampaign from "./useUpdateCampaign";
import { useGenerateIdCampaign } from "./useGenerateIdCampaign";
import { useResourceData } from "./useResourceData";
import useCreateProductVariant from "./useCreateProductVariant";
import useCreateProduct from "./useCreateProduct";
import useParallelSearch from "./useParallelSearch";

const UserService = {};

const workbench = {};

const contracts = {};

const campaign = {
  useProductList,
  useCreateCampaign,
  useSearchCampaign,
  useUpdateCampaign,
  useGenerateIdCampaign,
  useResourceData,
  useCreateProduct,
  useCreateProductVariant,
  useParallelSearch,
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
