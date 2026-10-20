import config from "../config/index";


export const getBoundaryColumnName = () => {
  return config?.boundary?.boundaryCode;
};

export const getBoundaryTabName = () => {
  return config?.boundary?.boundaryTab;
};