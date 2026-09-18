import { NextFunction, Request, Response } from "express";
const { object, string } = require("yup");
import { errorResponder } from "../genericUtils";
import { logger } from "../logger";
import { handleGzipRequest } from "../gzipHandler";
import { requestContextStore } from "../requestContext";

const requestSchema = object({
  apiId: string().nullable(),
  action: string().nullable(),
  msgId: string().required(),
  authToken: string().nullable(),
  userInfo: object().nonNullable()
});

// Validates RequestInfo/content-type on every inbound HTTP request and seeds the async request context.
const requestMiddleware = async (req: Request, res: Response, next: NextFunction) => {
  try {
    logger.info(`RECEIVED A HTTP REQUEST :: URI :: ${req.url}`);
    const contentType = req.headers['content-type'];
    if (!contentType || !contentType.split(';').map(part => part.trim()).includes('application/json') && !contentType.split(';').map(part => part.trim()).includes('application/gzip')) {
      let e: any = new Error("Unsupported Media Type: Content-Type should be 'application/json' or 'application/gzip'");
      e = Object.assign(e, { status: 415, code: "UNSUPPORTED_MEDIA_TYPE" });
      errorResponder(e, req, res, 415)
      return;
    }
    if (contentType === 'application/gzip') {
      await handleGzipRequest(req);
    }
    if (!req?.body?.RequestInfo?.userInfo?.tenantId) {
      let e: any = new Error("RequestInfo.userInfo.tenantId is missing");
      e = Object.assign(e, { status: 400, code: "VALIDATION_ERROR" });
      errorResponder(e, req, res, 400)
      return;
    }
    requestSchema.validateSync(req.body.RequestInfo);
    // Seed async context with correlationId and tenantId for all downstream logs
    const correlationId: string | null = req.body?.RequestInfo?.correlationId ?? null;
    const tenantId: string | null = req.body?.RequestInfo?.userInfo?.tenantId ?? null;
    requestContextStore.run({ correlationId, tenantId }, next);
  }
  catch (error) {
    errorResponder(error, req, res);
  }
};

export default requestMiddleware;