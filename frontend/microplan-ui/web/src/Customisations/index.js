var Digit = window.Digit || {};

const customisedComponent = {};

export const initCustomisationComponents = () => {
  Object.entries(customisedComponent).forEach(([key, value]) => {
    Digit.ComponentRegistryService.setComponent(key, value);
  });
};
