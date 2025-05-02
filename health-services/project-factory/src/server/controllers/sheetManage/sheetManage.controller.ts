import * as express from "express";
import { generateDataService } from "../../service/sheetManageService";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { logger } from "../../utils/logger";
import { validateGenerateRequest } from "../../validators/genericValidator";







// Define the MeasurementController class
class sheetManageController {
    // Define class properties
    public path = "/v1/sheet";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    // Constructor to initialize routes
    constructor() {
        this.intializeRoutes();
    }

    // Initialize routes for MeasurementController
    public intializeRoutes() {
        this.router.post(`${this.path}/_generate`, this.generateData);
    }
    /**
* Generates data based on the request and sends the response.
* @param request The Express request object.
* @param response The Express response object.
*/
    generateData = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`RECEIVED A DATA GENERATE REQUEST FOR TYPE :: ${request?.query?.type}`);
            await validateGenerateRequest(request);
            const GeneratedResource = await generateDataService(request);
            return sendResponse(response, { GeneratedResource }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };


};
export default sheetManageController;



