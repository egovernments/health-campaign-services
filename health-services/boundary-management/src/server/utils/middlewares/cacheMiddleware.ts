// Importing necessary modules from genericUtils
import { errorResponder,appCache } from "../genericUtils";

// Importing necessary modules from Express
import { NextFunction, Request, Response } from "express";

// Variable to indicate whether caching is enabled or not
const cacheEnabled = false;

// Middleware function to handle caching
const cacheMiddleware = (req: Request, res: Response, next: NextFunction) => {
  try {
    // Attempt to retrieve data from cache based on cache key in request headers
    const cacheData = appCache.get(req.headers.cachekey);

    // Check if cache data exists and caching is enabled
    if (cacheData && cacheEnabled) {
      // If cache data exists and caching is enabled, send the cached data as response
      res.send(cacheData);
    } else {
      // If cache data doesn't exist or caching is disabled, proceed to the next middleware/route handler
      next();
    }
  } catch (error) {
    // If an error occurs during caching process, handle the error using errorResponder function
    errorResponder(error, req, res, next);
  }
};

// Exporting the cacheMiddleware function for use in Express middleware chain
export default cacheMiddleware;
