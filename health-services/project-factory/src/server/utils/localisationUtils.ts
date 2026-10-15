import config from "../config/index";

/** Locale is encoded as the second '|'-delimited segment of msgId; falls back to the config default. */
export const getLocaleFromRequest = (request: any) => {
  const msgId = request?.body?.RequestInfo?.msgId;
  return msgId?.split("|")?.[1] || config?.localisation?.defaultLocale;
};

/** Locale is encoded as the second '|'-delimited segment of msgId; falls back to the config default. */
export const getLocaleFromRequestInfo = (RequestInfo: any) => {
  const msgId = RequestInfo?.msgId;
  return msgId?.split("|")?.[1] || config?.localisation?.defaultLocale;
};

/** Boundary localisation module name is derived per hierarchy type so each hierarchy has its own key namespace. */
export const getLocalisationModuleName = (hierarchyType: any) => {
  return `${config.localisation.boundaryPrefix}-${getTransformedLocale(hierarchyType)}`?.toLowerCase();
};

/**
 * Transforms a label into a formatted locale string.
 * @param label - The label to be transformed.
 * @returns The transformed locale string.
 */
export const getTransformedLocale = (label: string) => {
  label = label?.trim();
  return label && label.toUpperCase().replace(/[.:-\s\/]/g, "_");
};


/** Flattens the localization search response into a code→message lookup used everywhere as localizationMap. */
export const convertLocalisationResponseToMap = (messages: any = []) => {
  const localizationMap: any = {};
  messages.forEach((message: any) => {
    localizationMap[message.code] = message.message;
  });
  return localizationMap;
}