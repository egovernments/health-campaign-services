import { logoutV1 } from "./logout";
import utils from "../utils";

const UserService = {
  logoutV1,
};

const microplan = {
  
};

const contracts = {};

const Hooks = {
  attendance: {
    update: () => { },
  },
  microplan,
  contracts,
};

const Utils = {
  browser: {
    sample: () => { },
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
