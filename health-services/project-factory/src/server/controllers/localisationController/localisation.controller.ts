import * as express from "express";
import { logger } from "../../utils/logger";
import { httpRequest } from "../../utils/request";
import config from "../../config/index";
import { convertLocalisationResponseToMap } from "../../utils/localisationUtils";

let cachedResponse = {};

class Localisation {
  public path = "/localization/messages/v1";
  public router = express.Router();
  public dayInMilliSecond = 86400000;
  private cachedResponse: any = {};
  private localizationHost;
  private static instance: Localisation;
  constructor() {
    this.localizationHost = config.host.localizationHost;
  }
  public static getInstance(): Localisation {
    if (!Localisation.instance) {
      Localisation.instance = new Localisation();
    }
    return Localisation.instance;
  }

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
      break;
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
        throw error;
      }
    }
  }

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

          this.cachedResponse[cacheKey] = messages;
          cachedResponse = { ...this.cachedResponse };

          break;
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
            throw error;
          }
        }
      }
    } else {
      logger.info(`Message list found in cache for ${cacheKey}`);
    }

    return this.cachedResponse[cacheKey];
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
      const requestBody = { RequestInfo, messages, tenantId };
      const url = this.localizationHost + config.paths.localizationCreate;
      logger.info(`Creating the localisation messages of count ${messages?.length}`);

      await httpRequest(url, requestBody);

      messages &&
        messages?.length > 0 &&
        this.checkCacheAndDeleteIfExists(
          messages?.[0]?.module,
          messages?.[0]?.locale
        );
      logger.info("Localisation messages created successfully");
    } catch (e: any) {
      console.log(e);
      logger.error(String(e));
      throw new Error(e);
    }
  };
}

export default Localisation;
