import config from "../config/index";



export const getBoundaryColumnName = () => {
  // Construct Boundary column name from the config
  return config?.boundary?.boundaryCode;
};

// Function to generate localisation module name based on hierarchy type
export const getBoundaryTabName = () => {
  // Construct Boundary tab name from the config
  return config?.boundary?.boundaryTab;
};

