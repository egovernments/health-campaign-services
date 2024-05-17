import * as express from "express";
import { logger } from "../../utils/logger";
import { httpRequest } from "../../utils/request";
import config from "../../config/index";
import { convertLocalisationResponseToMap } from "../../utils/localisationUtils";

let cachedResponse = {};

class localisationController {
  public path = "/localization/messages/v1";
  public router = express.Router();
  public dayInMilliSecond = 86400000;
  public cachedResponse: any = {}; // Property to store the cached response

  constructor() {
    this.intializeRoutes();
  }

  public intializeRoutes = () => {
    // this.router.post(`${this.path}/_search`, this.getLocalizedMessages);
  };

  getLocalisationMap = (): any => {//{
    return Object.values(this.cachedResponse).reduce((acc: any, curr: any) => {
      acc = { ...acc, ...curr };
      return acc;
    }, {})//
  }
  // search localization 
  getLocalisedData: any = async (module: string, locale: string, tenantId: string) => {
    if (!this?.cachedResponse?.[`${module}-${locale}`]) {
      await this.fetchLocalisationMessage(module, locale, tenantId);
    }
    return this.getLocalisationMap();
  }
  // fetch localization messages 
  fetchLocalisationMessage = async (module: string, locale: string, tenantId: string) => {
    logger.info(`Received Localisation fetch for module ${module}, locale ${locale}, tenantId ${tenantId}`);

    const params = {
      tenantId,
      locale,
      module,
    };
    const url =
      config.host.localizationHost + config.paths.localizationSearch;
    const localisationResponse = await httpRequest(url, {}, params);
    logger.info(`Fetched Localisation Message for module ${module}, locale ${locale}, tenantId ${tenantId} with count ${localisationResponse?.messages?.length}`);
    this.cachedResponse = { ...cachedResponse, ...this.cachedResponse, [`${module}-${locale}`]: { ...convertLocalisationResponseToMap(localisationResponse?.messages) } };
    cachedResponse = { ...this.cachedResponse };
  }



  /**
   * Create localisation entries by sending a POST request to the localization host.
   * @param messages - Array of localisation messages to be created.
   * @param request - Request object containing necessary information.
   */
  createLocalisation = async (messages: any[] = [], tenantId: string, request: any = {}) => {
    try {
      // Extract RequestInfo from request body
      const { RequestInfo } = request.body;
      // Construct request body with RequestInfo and localisation messages
      const requestBody = { RequestInfo, messages, tenantId };
      // Construct URL for localization create endpoint
      const url = config.host.localizationHost + config.paths.localizationCreate;
      // Log the start of the localisation messages creation process
      logger.info("Creating the localisation messages");
      // Send HTTP POST request to create localisation messages

      await httpRequest(url, requestBody);
      // Log the completion of the localisation messages creation process
      logger.info("Localisation messages created successfully");
    } catch (e: any) {
      // Log and handle any errors that occur during the process
      console.log(e);
      logger.error(String(e));
    }
  };
}

export default localisationController;
