import config from "../config/index";
import { getLocalizedMessagesHandlerViaLocale } from "./genericUtils";

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

export const checkExistingLocalisations = async () => {
  const localizationMapModule: any = await getLocalizedMessagesHandlerViaLocale(
    config.localisation.defaultLocale,
    config.app.defaultTenantId
  );

  const valueSet = new Set<string>();
  const duplicates: { key: string; value: string }[] = [];

  for (const entry of Object.entries(localizationMapModule)) {
    const [key, value]: any = entry;
    if (valueSet.has(value)) {
      duplicates.push({ key, value });
    } else {
      valueSet.add(value);
    }
  }

  if (duplicates.length > 0) {
    console.error("Duplicate values found in localisations:");
    duplicates.forEach(dup => {
      console.error(`Key: ${dup.key}, Value: ${dup.value}`);
    });
    throw new Error("Duplicate localisation values detected.");
  } else {
    console.log("✅ No duplicate values in localisations.");
  }
};
