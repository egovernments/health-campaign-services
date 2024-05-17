import * as express from "express";
import { logger } from "../../utils/logger";
import { validateGenerateRequest } from "../../utils/validators/genericValidator";
import { enrichResourceDetails, errorResponder, processGenerate, sendResponse, getResponseFromDb, throwError, getLocalizedMessagesHandler } from "../../utils/genericUtils";
import { processGenericRequest } from "../../api/campaignApis";
import { createAndUploadFile, getBoundarySheetData } from "../../api/genericApis";
import { validateCreateRequest, validateDownloadRequest, validateSearchRequest } from "../../utils/validators/campaignValidators";
import { processDataSearchRequest } from "../../utils/campaignUtils";
import { getLocalisationModuleName } from "../../utils/localisationUtils";







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
        this.router.post(`${this.path}/_getboundarysheet`, this.getBoundaryData);
        this.router.post(`${this.path}/_create`, this.createData);
        this.router.post(`${this.path}/_search`, this.searchData);
    }
    /**
* Generates data based on the request and sends the response.
* @param request The Express request object.
* @param response The Express response object.
*/
    generateData = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`RECEIVED A DATA GENERATE REQUEST FOR TYPE :: ${request?.query?.type}`);
            // Validate the generate request
            await validateGenerateRequest(request);
            logger.info("VALIDATED THE DATA GENERATE REQUEST");
            const { hierarchyType } = request.query;
            // Process the data generation
            // localize the codes present in boundary modules
            request?.query?.hierarchyType && await getLocalizedMessagesHandler(request, request?.query?.tenantId, getLocalisationModuleName(hierarchyType));
            const localizationMap = await getLocalizedMessagesHandler(request, request?.query?.tenantId);
            await processGenerate(request, response, localizationMap);
            // Send response with generated resource details

            return sendResponse(response, { GeneratedResource: request?.body?.generatedResource }, request);
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
            // validate downlaod request body
            logger.info(`RECEIVED A DATA DOWNLOAD REQUEST FOR TYPE :: ${request?.query?.type}`);
            await validateDownloadRequest(request);
            logger.info("VALIDATED THE DATA DOWNLOAD REQUEST");

            const type = request.query.type;
            // Get response data from the database
            const responseData = await getResponseFromDb(request, response);
            // Check if response data is available
            if (!responseData || responseData.length === 0 && !request?.query?.id) {
                logger.error("No data of type  " + type + " with status Completed or with given id presnt in db ")
                // Throw error if data is not found
                throwError("CAMPAIGN", 500, "GENERATION_REQUIRE");
            }
            return sendResponse(response, { GeneratedResource: responseData }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e));
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

    /**
     * Retrieves boundary data based on the request.
     * @param request The Express request object.
     * @param response The Express response object.
     */
    getBoundaryData = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A REQUEST TO GENERATE THE BOUNDARY");
           
            const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId || request?.query?.tenantId || 'mz');
            // Retrieve boundary sheet data
            const boundarySheetData: any = await getBoundarySheetData(request, localizationMap);
            // Create and upload file
            const BoundaryFileDetails: any = await createAndUploadFile(boundarySheetData?.wb, request);
            // Return boundary file details
            logger.info("RETURNS THE BOUNDARY RESPONSE");

            return BoundaryFileDetails;
        }
        catch (e: any) {
            console.log(e)
            logger.error(String(e));
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    /**
   * Creates data based on the request and sends the response.
   * @param request The Express request object.
   * @param response The Express response object.
   */
    createData = async (request: any, response: any) => {
        try {
            logger.info(`RECEIVED A DATA CREATE REQUEST FOR TYPE :: ${request?.body?.ResourceDetails?.type}`);
            // Validate the create request
            await validateCreateRequest(request);
            logger.info("VALIDATED THE DATA CREATE REQUEST");


            const localizationMap = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);

            // Enrich resource details
            await enrichResourceDetails(request);


            // Process the generic request
            await processGenericRequest(request, localizationMap);

            // Send response with resource details
            return sendResponse(response, { ResourceDetails: request?.body?.ResourceDetails }, request);
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
            // Validate the search request
            await validateSearchRequest(request);
            logger.info("VALIDATED THE DATA GENERATE REQUEST");
            // Process the data search request
            await processDataSearchRequest(request);
            // Send response with resource details
            return sendResponse(response, { ResourceDetails: request?.body?.ResourceDetails }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }

};
export default dataManageController;



