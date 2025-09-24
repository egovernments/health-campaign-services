import { NextFunction, Request, Response } from "express"; // Importing necessary modules from Express
const { object, string } = require("yup"); // Importing object and string from yup for schema validation
import { errorResponder } from "../genericUtils"; // Importing errorResponder function from genericUtils
import {logger} from "../logger";


// Defining the request schema using yup
const requestSchema = object({
  apiId: string().nullable(), // Nullable string field for API ID
  action: string().nullable(), // Nullable string field for action
  msgId: string().required(), // Required string field for message ID
  authToken: string().nullable(), // Nullable string field for authentication token
  userInfo: object().nonNullable() // Non-nullable object field for user information
});

// Middleware function to validate request payload
const requestMiddleware = async (req: Request, res: Response, next: NextFunction) => {
  try {
    logger.info(`RECEIVED A HTTP REQUEST :: URI :: ${req.url}`);
    // Check if the content type is 'application/json'
    const contentType = req.headers['content-type'];
    if (!contentType || !contentType.split(';').map(part => part.trim()).includes('application/json') && !contentType.split(';').map(part => part.trim()).includes('application/gzip')) {
      // If content type is not 'application/json' or 'application/gzip', throw Unsupported Media Type error
      let e: any = new Error("Unsupported Media Type: Content-Type should be 'application/json' or 'application/gzip'");
      e = Object.assign(e, { status: 415, code: "UNSUPPORTED_MEDIA_TYPE" });
      errorResponder(e, req, res, 415)
      return;
    }
    // Check if tenantId is missing in RequestInfo.userInfo
    if (!req?.body?.RequestInfo?.userInfo?.tenantId) {
      // If tenantId is missing, throw Validation Error
      let e: any = new Error("RequestInfo.userInfo.tenantId is missing");
      e = Object.assign(e, { status: 400, code: "VALIDATION_ERROR" });
      errorResponder(e, req, res, 400)
      return;
    }
    // Validate request payload against the defined schema
    requestSchema.validateSync(req.body.RequestInfo);
    // If validation succeeds, proceed to the next middleware
    next();
  }
  catch (error) {
    // If an error occurs during validation process, handle the error using errorResponder function
    errorResponder(error, req, res);
  }
};

export default requestMiddleware; // Exporting the requestMiddleware function for use in Express middleware chain