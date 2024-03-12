import utils from "../utils";

const UserService = {};

const workbench = {};

const contracts = {};

const Hooks = {};

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
