import { NextFunction, Request, Response } from "express"; // Importing necessary modules from Express

// Defining a middleware function that wraps async route handlers
const asyncMiddleware = (fn: (req: Request, res: Response, next: NextFunction) => any) =>
  (req: Request, res: Response, next: NextFunction) => {
    // Wrapping the asynchronous route handler in a Promise to handle errors
    Promise.resolve(fn(req, res, next))
      .catch(next); // Catching any errors and passing them to the error handling middleware
  };

export default asyncMiddleware; // Exporting the async middleware function for use in Express routes
