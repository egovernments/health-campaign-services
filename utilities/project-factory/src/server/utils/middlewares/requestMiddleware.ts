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
      res.status(415).send("Unsupported Media Type: Content-Type should be 'application/json'");
      return;
    }
    if (!req?.body?.RequestInfo?.userInfo?.tenantId) {
      res.status(404).send("RequestInfo.userInfo.tenantId is missing");
      return;
    }
    requestSchema.validateSync(req.body.RequestInfo);
    next();
  } catch (error) {
    errorResponder(error, req, res);
  }
};

export default requestMiddleware;