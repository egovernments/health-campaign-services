import { errorResponder, appCache } from "../genericUtils";
import { NextFunction, Request, Response } from "express";

const cacheEnabled = false;

const cacheMiddleware = (req: Request, res: Response, next: NextFunction) => {
  try {
    const cacheData = appCache.get(req.headers.cachekey);
    if (cacheData && cacheEnabled) {
      res.send(cacheData);
    } else {
      next();
    }
  } catch (error) {
    errorResponder(error, req, res, next);
  }
};


export default cacheMiddleware;
