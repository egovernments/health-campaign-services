import * as express from "express";
import { logger } from "../../utils/logger";
import { httpRequest } from "../../utils/request";
import config from '../../config/index';
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
                "tenantId": tenantId,
                "locale": locale,
                "module": module
            }
            const url = config.host.localizationHost + config.paths.localizationSearch;
            const localisationResponse = await httpRequest(url, requestBody, params);
            this.cachedResponse = localisationResponse;
            return localisationResponse;
        } catch (e: any) {
            console.log(e)
            logger.error(String(e));
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

}

export default localisationController;
