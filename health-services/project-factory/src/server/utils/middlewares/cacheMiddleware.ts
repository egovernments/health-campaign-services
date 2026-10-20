import { errorResponder, appCache } from "../genericUtils";
import { NextFunction, Request, Response } from "express";

// Master kill-switch: caching is currently disabled regardless of a cache hit.
const cacheEnabled = false;

/** Serves a cached response by `cachekey` header when caching is enabled; otherwise falls through to the handler. */
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
