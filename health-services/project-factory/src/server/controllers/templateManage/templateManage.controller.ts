import * as express from "express";
import { logger } from "../../utils/logger";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { validateCreateTemplateRequest, validateSearchTemplateRequest } from "../../validators/templateValidators";
import { createTemplateService, searchTemplateService } from "../../service/templateManageService";

class templateManageController {
    // Define class properties
    public path = "/v1/template";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    // Constructor to initialize routes
    constructor() {
        this.intializeRoutes();
    }

    public intializeRoutes() {
        this.router.post(`${this.path}/create`, this.createDefaultTemplate);
        this.router.post(`${this.path}/search`, this.searchDefaultTemplate);
    }

    /**
    * Handles the creation of a default template
    * @param request The Express request object.
    * @param response The Express response object.
    */

    createDefaultTemplate = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A TEMPLATE SEARCH REQUEST");
            // Validate the create request for template creation
            await validateCreateTemplateRequest(request);
            const responseBody = await createTemplateService(request?.body);
            return sendResponse(response, responseBody, request);
        } catch (e: any) {
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    /**
        * Handles the search for project type campaigns.
        * @param request The Express request object.
        * @param response The Express response object.
        */
    searchDefaultTemplate = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A TEMPLATE SEARCH REQUEST");
            // Validate the search request for project type campaigns
            await validateSearchTemplateRequest(request);
            const responseBody = await searchTemplateService(request?.body?.templateSearchCriteria);
            // Send response with campaign details and total count
            return sendResponse(response, responseBody, request);
        } catch (e: any) {
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }
}

export default templateManageController;


