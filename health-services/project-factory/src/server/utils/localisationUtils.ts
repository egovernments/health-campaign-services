import config from "../config/index";

// Function to extract locale from request object
export const getLocaleFromRequest = (request: any) => {
  // Extract msgId from request body
  const msgId = request?.body?.RequestInfo?.msgId;
  // Split msgId by '|' delimiter and get the second part (index 1)
  // If splitting fails or no second part is found, use default locale from config
  return msgId?.split("|")?.[1] || config?.localisation?.defaultLocale;
};

export const getLocaleFromRequestInfo = (RequestInfo: any) => {
  // Extract msgId from request body
  const msgId = RequestInfo?.msgId;
  // Split msgId by '|' delimiter and get the second part (index 1)
  // If splitting fails or no second part is found, use default locale from config
  return msgId?.split("|")?.[1] || config?.localisation?.defaultLocale;
};

// Function to generate localisation module name based on hierarchy type
export const getLocalisationModuleName = (hierarchyType: any) => {
  // Construct module name using boundary prefix from config and hierarchy type
  // Convert module name to lowercase
  return `${config.localisation.boundaryPrefix}-${getTransformedLocale(hierarchyType)}`?.toLowerCase();
};

/**
 * Transforms a label into a formatted locale string.
 * @param label - The label to be transformed.
 * @returns The transformed locale string.
 */
export const getTransformedLocale = (label: string) => {
  // Trim leading and trailing whitespace from the label
  label = label?.trim();
  // If label is not empty, convert to uppercase and replace special characters with underscores
  return label && label.toUpperCase().replace(/[.:-\s\/]/g, "_");
};


export const convertLocalisationResponseToMap = (messages: any = []) => {
  const localizationMap: any = {};
  messages.forEach((message: any) => {
    localizationMap[message.code] = message.message;
  });
  return localizationMap;
}