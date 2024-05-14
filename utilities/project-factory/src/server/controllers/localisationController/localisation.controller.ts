import * as express from "express";
import { logger } from "../../utils/logger";
import { httpRequest } from "../../utils/request";
import config from "../../config/index";
import { errorResponder } from "../../utils/genericUtils";

class localisationController {
  public path = "/localization/messages/v1";
  public router = express.Router();
  public dayInMilliSecond = 86400000;
  public cachedResponse: any; // Property to store the cached response

  constructor() {
    this.intializeRoutes();
  }

  public intializeRoutes = () => {
    this.router.post(`${this.path}/_search`, this.getLocalizedMessages);
  };

  getLocalizedMessages = async (request: any, response: any) => {
    try {
      // If the response is already cached, return it directly
      if (this.cachedResponse) {
        return this.cachedResponse;
      }
      const { tenantId, locale, module } = request?.query; // Extract tenantId, locale, and module from request body
      const { RequestInfo } = request.body;
      const requestBody = { RequestInfo };
      const params = {
        tenantId: tenantId,
        locale: locale,
        module: module,
      };
      const url =
        config.host.localizationHost + config.paths.localizationSearch;
      const localisationResponse = await httpRequest(url, requestBody, params);
      this.cachedResponse = localisationResponse;
      return localisationResponse;
    } catch (e: any) {
      console.log(e);
      logger.error(String(e));
      return errorResponder(
        { message: String(e), code: e?.code, description: e?.description },
        request,
        response,
        e?.status || 500
      );
    }
  };
  /**
   * Create localisation entries by sending a POST request to the localization host.
   * @param messages - Array of localisation messages to be created.
   * @param request - Request object containing necessary information.
   */
  createLocalisation = async (messages: any[] = [], tenantId:string,request: any = {}) => {
    try {
      // Extract RequestInfo from request body
      const { RequestInfo } = request.body;
      // Construct request body with RequestInfo and localisation messages
      const requestBody = { RequestInfo, messages ,tenantId };
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
