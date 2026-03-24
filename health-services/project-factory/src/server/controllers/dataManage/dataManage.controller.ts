import * as express from "express";
import { createDataService, downloadDataService, generateDataService, searchDataService, searchCampaignDataService, searchMappingDataService } from "../../service/dataManageService";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";







// Define the MeasurementController class
class dataManageController {
    // Define class properties
    public path = "/v1/data";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    // Constructor to initialize routes
    constructor() {
        this.intializeRoutes();
    }

    // Initialize routes for MeasurementController
    public intializeRoutes() {
        this.router.post(`${this.path}/_generate`, this.generateData);
        this.router.post(`${this.path}/_download`, this.downloadData)
        this.router.post(`${this.path}/_create`, this.createData);
        this.router.post(`${this.path}/_search`, this.searchData);
        this.router.post(`${this.path}/campaign/_search`, this.searchCampaignData);
        this.router.post(`${this.path}/mapping/_search`, this.searchMappingData);
    }
    /**
* Generates data based on the request and sends the response.
* @param request The Express request object.
* @param response The Express response object.
*/
    generateData = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`RECEIVED A DATA GENERATE REQUEST FOR TYPE :: ${request?.query?.type}`);
            const GeneratedResource = await generateDataService(request);
            return sendResponse(response, { GeneratedResource }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    /**
    * Downloads data based on the request and sends the response.
    * @param request The Express request object.
    * @param response The Express response object.
    */
    downloadData = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`RECEIVED A DATA DOWNLOAD REQUEST FOR TYPE :: ${request?.query?.type}`);
            const GeneratedResource = await downloadDataService(request);
            return sendResponse(response, { GeneratedResource }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e));
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    /**
   * Creates data based on the request and sends the response.
   * @param request The Express request object.
   * @param response The Express response object.
   */
    createData = async (request: any, response: any) => {
        try {
            logger.info(`RECEIVED A DATA CREATE REQUEST FOR TYPE :: ${request?.body?.ResourceDetails?.type}`);
            const ResourceDetails = await createDataService(request);
            // Send response with resource details
            return sendResponse(response, { ResourceDetails }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    /**
         * Searches for data based on the request and sends the response.
         * @param request The Express request object.
         * @param response The Express response object.
         */
    searchData = async (request: any, response: any) => {
        try {
            logger.info(`RECEIVED A DATA SEARCH REQUEST FOR TYPE :: ${request?.body?.SearchCriteria?.type}`);
            const ResourceDetails = await searchDataService(request)
            return sendResponse(response, { ResourceDetails }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    /**
     * Searches for campaign data based on the request and sends the response.
     * @param request The Express request object.
     * @param response The Express response object.
     */
    searchCampaignData = async (request: any, response: any) => {
        try {
            logger.info(`RECEIVED A CAMPAIGN DATA SEARCH REQUEST FOR TYPE :: ${request?.body?.SearchCriteria?.type}`);
            const result = await searchCampaignDataService(request);
            return sendResponse(response, result, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    /**
     * Searches for mapping data based on the request and sends the response.
     * @param request The Express request object.
     * @param response The Express response object.
     */
    searchMappingData = async (request: any, response: any) => {
        try {
            logger.info(`RECEIVED A MAPPING DATA SEARCH REQUEST FOR TYPE :: ${request?.body?.SearchCriteria?.type}`);
            const result = await searchMappingDataService(request);
            return sendResponse(response, result, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

};
export default dataManageController;



