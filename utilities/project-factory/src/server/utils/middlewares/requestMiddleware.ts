import { NextFunction, Request, Response } from "express";

const { object, string } = require("yup");
import { errorResponder } from "../genericUtils";

const requestSchema = object({
  apiId: string().nullable(),
  action: string().nullable(),
  msgId: string().required(),
  authToken: string().nullable(),
  userInfo: object().nonNullable()
});

const requestMiddleware = (req: Request, res: Response, next: NextFunction) => {
  try {
    const contentType = req.headers['content-type'];
    if (!contentType || !contentType.split(';').map(part => part.trim()).includes('application/json')) {
      let e: any = new Error("Unsupported Media Type: Content-Type should be 'application/json'");
      e = Object.assign(e, { status: 415, code: "UNSUPPORTED_MEDIA_TYPE" });
      errorResponder(e, req, res, 415)
      return;
    }
    if (!req?.body?.RequestInfo?.userInfo?.tenantId) {
      let e: any = new Error("RequestInfo.userInfo.tenantId is missing");
      e = Object.assign(e, { status: 400, code: "VALIDATION_ERROR" });
      errorResponder(e, req, res, 400)
      return;
    }
    requestSchema.validateSync(req.body.RequestInfo);
    next();
  } catch (error) {
    errorResponder(error, req, res);
  }
};

export default requestMiddleware;
