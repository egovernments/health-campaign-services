import express from 'express';
import * as bodyParser from 'body-parser';
import config from './config';
import {  requestMiddleware } from './utils/middlewares';
import { errorLogger, errorResponder, invalidPathHandler } from './utils/genericUtils';

class App {
  public app: express.Application;
  public port: number;

  constructor(controllers: any, port: any) {
    this.app = express();
    this.port = port;

    this.initializeMiddlewares();
    this.initializeControllers(controllers);
    this.app.use(invalidPathHandler);
  }

  private initializeMiddlewares() {
    this.app.use(bodyParser.json());
    this.app.use(requestMiddleware);

    // this.app.use(cacheMiddleware);
    // Attach the first Error handling Middleware
    // function defined above (which logs the error)
    this.app.use(errorLogger);

    // Attach the second Error handling Middleware
    // function defined above (which sends back the response)
    this.app.use(errorResponder);

    // Attach the fallback Middleware
    // function which sends back the response for invalid paths)
  }

  private initializeControllers(controllers: any) {
    controllers.forEach((controller: any) => {
      this.app.use(config.app?.contextPath, controller.router);
    });
  }

  public listen() {
    this.app.listen(this.port, () => {
      console.log(`App listening on the port ${this.port}`);
    });
  }
}

export default App;

