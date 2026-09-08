import { NextFunction, Request, Response } from "express";

/** Wraps an async route handler so rejected promises reach Express's error middleware instead of hanging the request. */
const asyncMiddleware = (fn: (req: Request, res: Response, next: NextFunction) => any) =>
  (req: Request, res: Response, next: NextFunction) => {
    Promise.resolve(fn(req, res, next))
      .catch(next);
  };

export default asyncMiddleware;
