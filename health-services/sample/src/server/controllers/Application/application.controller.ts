import * as express from "express";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import ApplicationService from "../../services/application/application.services";

class ApplicationController {
    public path = "/v1/application";
    public router = express.Router();

    constructor() {
        this.initializeRoutes();
    }

    public initializeRoutes() {
        this.router.post(`${this.path}/_apply`, this.createApplication);
        this.router.post(`${this.path}/_search`, this.getApplications);
        this.router.post(`${this.path}/_update`, this.updateApplication);
        this.router.delete(`${this.path}/:id`, this.deleteApplication);
    }

    /**
     * Handles creating a new application.
     */
    createApplication = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`Create Application Request Received :: ${JSON.stringify(request.body)}`);
            const newApplication = await ApplicationService.createApplication(request.body);
            return sendResponse(response, newApplication, request);
        } catch (e: any) {
            logger.error(`Error Creating Application: ${String(e)}`);
            return errorResponder({ message: e.message, code: e?.code }, request, response, e?.status || 500);
        }
    };

    /**
     * Handles retrieving applications based on search filters.
     */
    getApplications = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`Search Applications Request Received :: ${JSON.stringify(request.body)}`);
            const applications = await ApplicationService.getApplications(request.body);
            return sendResponse(response, applications, request);
        } catch (e: any) {
            logger.error(`Error Fetching Applications: ${String(e)}`);
            return errorResponder({ message: e.message, code: e?.code }, request, response, e?.status || 500);
        }
    };

    /**
     * Handles updating an existing application.
     */
    updateApplication = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`Update Application Request Received :: ${JSON.stringify(request.body)}`);
            const updatedApplication = await ApplicationService.updateApplication(request.body.id, request.body);
            
            if (!updatedApplication) {
                return errorResponder({ message: "Application not found", code: "NOT_FOUND" }, request, response, 404);
            }

            return sendResponse(response, updatedApplication, request);
        } catch (e: any) {
            logger.error(`Error Updating Application: ${String(e)}`);
            return errorResponder({ message: e.message, code: e?.code }, request, response, e?.status || 500);
        }
    };

    /**
     * Handles deleting an application by ID.
     */
    deleteApplication = async (request: express.Request, response: express.Response) => {
        try {
            const applicationId = request.params.id;
            logger.info(`Delete Application Request Received for ID :: ${applicationId}`);

            const result = await ApplicationService.deleteApplication(applicationId);
            if (result.message === "Application not found") {
                return errorResponder({ message: "Application not found", code: "NOT_FOUND" }, request, response, 404);
            }

            return sendResponse(response, result, request);
        } catch (e: any) {
            logger.error(`Error Deleting Application: ${String(e)}`);
            return errorResponder({ message: e.message, code: e?.code }, request, response, e?.status || 500);
        }
    };
}

export default ApplicationController;
