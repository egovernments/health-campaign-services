import utils from "../utils";
import { useProductList } from "./useProductList";

const UserService = {};

const workbench = {};

const contracts = {};

const campaign = {
  useProductList,
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
