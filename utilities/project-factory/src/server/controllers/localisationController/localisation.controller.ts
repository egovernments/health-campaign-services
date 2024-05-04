import * as express from "express";
import { logger } from "../../utils/logger";
import { httpRequest } from "../../utils/request";
import config from '../../config/index';
import { errorResponder } from "../../utils/genericUtils";

class localisationController {
    public path = "/localization/messages/v1";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    constructor() {
        this.intializeRoutes();
    }

    public intializeRoutes = () => {
        this.router.post(`${this.path}/_search`, this.getLocalizedMessages);
    };

    getLocalizedMessages = async (request: any, response: any) => {
        try {
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
            return localisationResponse;
        } catch (e: any) {
            logger.error(String(e));
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }
    
}

export default localisationController;
