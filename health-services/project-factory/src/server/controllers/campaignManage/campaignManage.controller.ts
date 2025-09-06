import * as express from "express";
import {
  cancelCampaignService,
  createCampaignService,
  createProjectTypeCampaignService,
  fetchFromMicroplanService,
  searchProjectTypeCampaignService,
  updateProjectTypeCampaignService
} from "../../service/campaignManageService";
import { getCampaignStatusService } from "../../service/campaignStatusService";
import { logger } from "../../utils/logger";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { validateSearchProjectCampaignRequest } from "../../validators/campaignValidators";



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
        this.router.post(`${this.path}/fetch-from-microplan`, this.fetchFromMicroplan);
        this.router.post(`${this.path}/cancel-campaign`, this.cancelCampaign);
        this.router.post(`${this.path}/status`, this.getCampaignStatus);
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
            const CampaignDetails = await createProjectTypeCampaignService(request);
            return sendResponse(response, { CampaignDetails }, request);
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
            const CampaignDetails = await updateProjectTypeCampaignService(request);
            return sendResponse(response, { CampaignDetails }, request);
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
            const responseBody = await searchProjectTypeCampaignService(request?.body?.CampaignDetails ,request);
            // Send response with campaign details and total count
            return sendResponse(response, responseBody, request);
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
            logger.info("RECEIVED A CAMPAIGN CREATE REQUEST");
            const Campaign = await createCampaignService(request?.body);
            // Send response with campaign details
            return sendResponse(response, { Campaign }, request);
        }
        catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    fetchFromMicroplan = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A FETCH FROM MICROPLAN REQUEST");
            const CampaignDetails = await fetchFromMicroplanService(request);
            return sendResponse(response, { CampaignDetails }, request);
        }
        catch (e: any) {
            console.log(e)
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    cancelCampaign = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A CAMPAIGN CANCEL REQUEST");
            const CampaignDetails = await cancelCampaignService(request);
            return sendResponse(response, { CampaignDetails }, request);
        }
        catch (e: any) {
            console.log(e)
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    getCampaignStatus = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A CAMPAIGN STATUS REQUEST");
            const campaignNumber = request?.body?.CampaignDetails?.campaignNumber;
            const tenantId = request?.body?.CampaignDetails?.tenantId;
            
            if (!campaignNumber) {
                return errorResponder({ 
                    message: "Campaign number is required", 
                    code: "CAMPAIGN_NUMBER_REQUIRED", 
                    description: "Please provide campaignNumber in request body" 
                }, request, response, 400);
            }

            if (!tenantId) {
                return errorResponder({ 
                    message: "TenantId is required", 
                    code: "TENANT_ID_REQUIRED", 
                    description: "Please provide tenantId in request body" 
                }, request, response, 400);
            }

            const statusResponse = await getCampaignStatusService(campaignNumber, tenantId, request);
            return sendResponse(response, { CampaignStatus: statusResponse }, request);
        } catch (e: any) {
            console.log(e);
            logger.error(String(e));
            return errorResponder({ 
                message: String(e), 
                code: e?.code, 
                description: e?.description 
            }, request, response, e?.status || 500);
        }
    };

};

export default campaignManageController;



