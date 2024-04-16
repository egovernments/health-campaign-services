import * as express from "express";
import { logger } from "../../utils/logger";
import {
    errorResponder,
    sendResponse,
} from "../../utils/genericUtils";
import {
    processBasedOnAction,
    searchProjectCampaignResourcData
} from "../../utils/campaignUtils"
import { validateCampaignRequest } from "../../utils/validators/genericValidator";
import { enrichCampaign } from "../../api/campaignApis";
import { validateProjectCampaignRequest, validateSearchProjectCampaignRequest } from "../../utils/validators/campaignValidators";
import { createProjectIfNotExists, createRelatedResouce } from "../../api/genericApis";



// Define the MeasurementController class
class campaignManageController {
    // Define class properties
    public path = "/v1/project-type";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    // Constructor to initialize routes
    constructor() {
        this.intializeRoutes();
    }

    // Initialize routes for MeasurementController
    public intializeRoutes() {
        this.router.post(`${this.path}/create`, this.createProjectTypeCampaign);
        this.router.post(`${this.path}/update`, this.updateProjectTypeCampaign);
        this.router.post(`${this.path}/search`, this.searchProjectTypeCampaign);
        this.router.post(`${this.path}/createCampaign`, this.createCampaign);
    }
    createProjectTypeCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            await validateProjectCampaignRequest(request, "create");
            await processBasedOnAction(request, "create")
            return sendResponse(response, { CampaignDetails: request?.body?.CampaignDetails }, request);
        } catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code }, request, response, e?.status || 500);
        }
    };

    updateProjectTypeCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            await validateProjectCampaignRequest(request, "update");
            await processBasedOnAction(request, "update")
            return sendResponse(response, { CampaignDetails: request?.body?.CampaignDetails }, request);
        } catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code }, request, response, e?.status || 500);
        }
    };

    searchProjectTypeCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            await validateSearchProjectCampaignRequest(request);
            await searchProjectCampaignResourcData(request);
            return sendResponse(response, { CampaignDetails: request?.body?.CampaignDetails, totalCount: request?.body?.totalCount }, request);
        } catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code }, request, response, e?.status || 500);
        }
    };

    createCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            await validateCampaignRequest(request.body)
            await createProjectIfNotExists(request.body)
            await createRelatedResouce(request.body)
            await enrichCampaign(request.body)
            return sendResponse(response, { Campaign: request?.body?.Campaign }, request);
        }
        catch (e: any) {
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code }, request, response, e?.status || 500);
        }
    };
};
export default campaignManageController;



