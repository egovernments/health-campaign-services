import * as express from "express";
import { logger } from "../../utils/logger";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { processEmployeeCreation, processProjectCreation } from "../../service/mainProcessService";



// Define the MeasurementController class
class mainProcessController {
    // Define class properties
    public path = "/v1/process";
    public router = express.Router();
    public dayInMilliSecond = 86400000;

    // Constructor to initialize routes
    constructor() {
        this.intializeRoutes();
    }

    // Initialize routes for MeasurementController
    public intializeRoutes() {
        this.router.post(`${this.path}/project-create`, this.startProjectCreation);
        this.router.post(`${this.path}/employee-create`, this.startEmployeeCreation);
    }


    
    startProjectCreation = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A REQUEST TO CREATE PROJECTS FOR CAMPAIGN");
            const projectCreationResponse = await processProjectCreation(request?.body);
            return sendResponse(response, { projectCreationResponse }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

    startEmployeeCreation = async (
        request: express.Request,
        response: express.Response
    ) => {
        try {
            logger.info("RECEIVED A REQUEST TO CREATE EMPLOYEE FOR CAMPAIGN");
            const employeeCreationResponse = await processEmployeeCreation(request?.body);
            return sendResponse(response, { employeeCreationResponse }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    };

};

export default mainProcessController;



