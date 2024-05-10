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
import { createRelatedResouce } from "../../api/genericApis";



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
    /**
 * Handles the creation of a project type campaign.
 * @param request The Express request object.
 * @param response The Express response object.
 */
    createProjectTypeCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A PROJECT TYPE CREATE REQUEST");
            // Validate the request for creating a project type campaign
            await validateProjectCampaignRequest(request, "create");
            logger.info("VALIDATED THE PROJECT TYPE CREATE REQUEST");

            // Process the action based on the request type
            await processBasedOnAction(request, "create");

            // Send response with campaign details
            return sendResponse(response, { CampaignDetails: request?.body?.CampaignDetails }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    /**
     * Handles the update of a project type campaign.
     * @param request The Express request object.
     * @param response The Express response object.
     */
    updateProjectTypeCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A PROJECT TYPE UPDATE REQUEST");
            // Validate the request for updating a project type campaign
            await validateProjectCampaignRequest(request, "update");
            logger.info("VALIDATED THE PROJECT TYPE UPDATE REQUEST");

            // Process the action based on the request type
            await processBasedOnAction(request, "update");

            // Send response with campaign details
            return sendResponse(response, { CampaignDetails: request?.body?.CampaignDetails }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    /**
     * Handles the search for project type campaigns.
     * @param request The Express request object.
     * @param response The Express response object.
     */
    searchProjectTypeCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A PROJECT TYPE SEARCH REQUEST");
            // Validate the search request for project type campaigns
            await validateSearchProjectCampaignRequest(request);
            logger.info("VALIDATED THE PROJECT TYPE SEARCH REQUEST");

            // Search for project campaign resource data
            await searchProjectCampaignResourcData(request);

            // Send response with campaign details and total count
            return sendResponse(response, { CampaignDetails: request?.body?.CampaignDetails, totalCount: request?.body?.totalCount }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    /**
     * Handles the creation of a campaign.
     * @param request The Express request object.
     * @param response The Express response object.
     */
    createCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            // Validate the request for creating a campaign
            logger.info("RECEIVED A CAMPAIGN CREATE REQUEST");
            await validateCampaignRequest(request.body)
            logger.info("VALIDATED THE CAMPAIGN CREATE REQUEST");

            // Create related resource
            await createRelatedResouce(request.body)

            // Enrich the campaign
            await enrichCampaign(request.body)
            // Send response with campaign details
            return sendResponse(response, { Campaign: request?.body?.Campaign }, request);
        }
        catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

};
export default campaignManageController;



