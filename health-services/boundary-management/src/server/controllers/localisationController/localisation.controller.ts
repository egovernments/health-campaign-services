import * as express from "express";
import { logger } from "../../utils/logger";
import { httpRequest } from "../../utils/request";
import config from "../../config/index";
import { convertLocalisationResponseToMap } from "../../utils/localisationUtils";
import { defaultRequestInfo } from "../../api/coreApis";

let cachedResponse = {};

class Localisation {
  public path = "/localization/messages/v1";
  public router = express.Router();
  public dayInMilliSecond = 86400000;
  private cachedResponse: any = {}; // Property to store the cached response
  private localizationHost;
  // Hold the single instance of the class
  private static instance: Localisation;
  constructor() {
    this.localizationHost = config.host.localizationHost;
  }
  // Public method to provide access to the single instance
  public static getInstance(): Localisation {
    if (!Localisation.instance) {
      Localisation.instance = new Localisation();
    }
    return Localisation.instance;
  }

  // private getLocalisationMap = (): any => {
  //   //{
  //   return Object.values(this.cachedResponse).reduce((acc: any, curr: any) => {
  //     acc = { ...acc, ...curr };
  //     return acc;
  //   }, {}); //
  // };
  // search localization
  public getLocalisedData: any = async (
    module: string,
    locale: string,
    tenantId: string,
    overrideCache: boolean
  ) => {
    logger.info(
      `Checks Localisation message is available in cache for module ${module}, locale ${locale}, tenantId ${tenantId}`
    );
    if (!this?.cachedResponse?.[`${module}-${locale}`] || overrideCache) {
      logger.info(`Not found in cache`);
      await this.fetchLocalisationMessage(module, locale, tenantId);
    }
    logger.info(`Found in cache`);
    return this?.cachedResponse?.[`${module}-${locale}`];
  };
  // fetch localization messages
  private fetchLocalisationMessage = async (
  module: string,
  locale: string,
  tenantId: string
) => {
  logger.info(
    `Received Localisation fetch for module ${module}, locale ${locale}, tenantId ${tenantId}`
  );

  const params = {
    tenantId,
    locale,
    module,
  };

  const url = this.localizationHost + config.paths.localizationSearch;
  const maxRetries = 3;
  const delayMs = 10000; // 10 seconds

  let attempt = 0;
  let localisationResponse: any = null;

  while (attempt < maxRetries) {
    try {
      localisationResponse = await httpRequest(url, {}, params);
      logger.info(
        `Fetched Localisation Message for module ${module}, locale ${locale}, tenantId ${tenantId} with count ${localisationResponse?.messages?.length}`
      );
      break; // Exit loop if successful
    } catch (error) {
      attempt++;
      logger.error(
        `Attempt ${attempt} failed to fetch localisation for module ${module}, locale ${locale}, tenantId ${tenantId}. Error: ${error}`
      );
      if (attempt < maxRetries) {
        logger.info(`Retrying in ${delayMs / 1000} seconds...`);
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      } else {
        logger.error(
          `All ${maxRetries} attempts failed for module ${module}, locale ${locale}, tenantId ${tenantId}`
        );
        throw error; // Re-throw after final attempt
      }
    }
  }

  // Proceed only if we have a successful response
  this.cachedResponse = {
    ...cachedResponse,
    ...this.cachedResponse,
    [`${module}-${locale}`]: {
      ...convertLocalisationResponseToMap(localisationResponse?.messages),
    },
  };

  logger.info(
    `Cached Localisation Message, now available modules in cache are :  ${JSON.stringify(
      Object.keys(this.cachedResponse)
    )}`
  );
  cachedResponse = { ...this.cachedResponse };
};

  public getLocalizationResponseMessages = async (
    module: string,
    locale: string,
    tenantId: string,
    overrideCache: boolean = false
  ) => {
    const cacheKey = `${module}-${locale}-message-cache`;
    logger.info(
      `Fetching message list for module ${module}, locale ${locale}, tenantId ${tenantId}`
    );

    if (!this.cachedResponse?.[cacheKey] || overrideCache) {
      logger.info(`Message list not found in cache. Fetching from server...`);

      const params = {
        tenantId,
        locale,
        module,
      };

      const url = config.host.localizationHost + config.paths.localizationSearch;
      const maxRetries = 3;
      const delayMs = 10000; // 10 seconds

      let attempt = 0;
      let localisationResponse: any = null;

      while (attempt < maxRetries) {
        try {
          localisationResponse = await httpRequest(url, {}, params);
          const messages = localisationResponse?.messages || [];

          logger.info(
            `Fetched ${messages.length} messages from server for module ${module}, locale ${locale}`
          );

          // Cache the raw message array using the new key
          this.cachedResponse[cacheKey] = messages;
          cachedResponse = { ...this.cachedResponse };

          break; // success, exit loop
        } catch (error) {
          attempt++;
          logger.error(
            `Attempt ${attempt} failed for fetching messages for ${cacheKey}. Error: ${error}`
          );
          if (attempt < maxRetries) {
            logger.info(`Retrying in ${delayMs / 1000} seconds...`);
            await new Promise((resolve) => setTimeout(resolve, delayMs));
          } else {
            logger.error(
              `All ${maxRetries} attempts failed to fetch messages for ${cacheKey}`
            );
            throw error; // Re-throw after final failure
          }
        }
      }
    } else {
      logger.info(`Message list found in cache for ${cacheKey}`);
    }

    return this.cachedResponse[cacheKey];
  };


  
  
  // Calls the cache burst API of localization
  public cacheBurst = async (
  ) => {
    logger.info(`Calling localization cache burst api`);
    const RequestInfo = defaultRequestInfo;
    const requestBody = {
      RequestInfo
    }
    await httpRequest(
      this.localizationHost + config.paths.cacheBurst,
      requestBody)
  };


  private checkCacheAndDeleteIfExists = (module: string, locale: "string") => {
    logger.info(
      `Received to checkCacheAndDeleteIfExists for module ${module}, locale ${locale}`
    );
    if (this.cachedResponse?.[`${module}-${locale}`]) {
      logger.info(`cache found to for module ${module}, locale ${locale}`);
      if (delete this.cachedResponse?.[`${module}-${locale}`]) {
        logger.info(
          `cache deleted for module ${module}, locale ${locale}, since new data has been created`
        );
      }
    }
  };

  /**
   * Create localisation entries by sending a POST request to the localization host.
   * @param messages - Array of localisation messages to be created.
   * @param request - Request object containing necessary information.
   */
  public createLocalisation = async (
    messages: any[] = [],
    tenantId: string,
    RequestInfo: any
  ) => {
    try {
      // Construct request body with RequestInfo and localisation messages
      const requestBody = { RequestInfo, messages, tenantId };
      // Construct URL for localization create endpoint
      const url = this.localizationHost + config.paths.localizationCreate;
      // Log the start of the localisation messages creation process
      logger.info(`Creating the localisation messages of count ${messages?.length}`);
      // Send HTTP POST request to create localisation messages

      await httpRequest(url, requestBody);

      messages &&
        messages?.length > 0 &&
        this.checkCacheAndDeleteIfExists(
          messages?.[0]?.module,
          messages?.[0]?.locale
        );
      // Log the completion of the localisation messages creation process
      logger.info("Localisation messages created successfully");
    } catch (e: any) {
      // Log and handle any errors that occur during the process
      console.log(e);
      logger.error(String(e));
      throw new Error(e);
    }
  };
}

export default Localisation;
