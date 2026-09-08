import * as express from "express";
import { errorResponder, sendResponse } from "../../utils/genericUtils";
import { processBoundaryService ,generateDataService,downloadDataService,searchDataService} from "../../services/boundaryManagementService";
import { logger } from "../../utils/logger";

class boundaryManagementController {
  //define class properties
  public path = "/v1";
  public router = express.Router();

  //constructor to initialize routes
  constructor() {
    this.intializeRoutes();
  }
  //method to initialize all the routes
  public intializeRoutes() {
    this.router.post(`${this.path}/_process`, this.processBoundary);
    this.router.post(`${this.path}/_generate`, this.generateBoundary);
    this.router.post(`${this.path}/_generate-search`, this.generateBoundarySearch);
    this.router.post(`${this.path}/_process-search`,this.processBoundarySearch);
}

  /**
   * Handles incoming requests to process boundary data based on the specified hierarchy type.
   *
   * Logs the hierarchy type received in the request query and processes the boundary data accordingly.
   * In case of errors, logs the error and sends an appropriate error response to the client.
   *
   * @param request - The Express request object containing query parameters and other request data.
   * @param response - The Express response object used to send responses to the client.
   * @returns A Promise that resolves when the request has been processed and a response has been sent.
   */
  processBoundary = async (
    request: express.Request,
    response: express.Response
  ) => {
    try {
      logger.info(
        `RECEIVED A DATA PROCESS REQUEST FOR HIERARCHY TYPE :: ${request?.body?.ResourceDetails?.hierarchyType}`
      );
      const ResourceDetails = await processBoundaryService(request);
      // Send response with resource details
      return sendResponse(response, { ResourceDetails }, request);
    } catch (e: any) {
      console.log(e);
      logger.error(String(e));
      // Handle errors and send error response
      return errorResponder(
        { message: String(e), code: e?.code, description: e?.description },
        request,
        response,
        e?.status || 500
      );
    }
  };


generateBoundary = async (
    request: express.Request,
    response: express.Response
  ) => {
    try {
      logger.info(
        `RECEIVED A DATA GENERATE REQUEST FOR HIERARCHY TYPE :: ${request?.body?.ResourceDetails?.hierarchyType}`
      );
      const ResourceDetails = await generateDataService(request);
      // Send response with resource details
      return sendResponse(response, { ResourceDetails }, request);
    } catch (e: any) {
      console.log(e);
      logger.error(String(e));
      // Handle errors and send error response
      return errorResponder(
        { message: String(e), code: e?.code, description: e?.description },
        request,
        response,
        e?.status || 500
      );
    }
  };

 generateBoundarySearch = async (request: express.Request, response: express.Response) => {
        try {
            logger.info(`RECEIVED A DATA DOWNLOAD REQUEST FOR TENANT ID :: ${request?.query?.tenantId}`);
            const GeneratedResource = await downloadDataService(request);
            return sendResponse(response, { GeneratedResource }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e));
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }


  processBoundarySearch = async (request: any, response: any) => {
        try {
            logger.info(`RECEIVED A DATA SEARCH REQUEST FOR TENANT ID :: ${request?.query?.tenantId}`);
            const ResourceDetails = await searchDataService(request)
            return sendResponse(response, { ResourceDetails }, request);
        } catch (e: any) {
            console.log(e)
            logger.error(String(e))
            // Handle errors and send error response
            return errorResponder({ message: String(e), code: e?.code, description: e?.description }, request, response, e?.status || 500);
        }
    }
  };



export default boundaryManagementController;
