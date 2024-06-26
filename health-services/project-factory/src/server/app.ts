import express from 'express';
import * as bodyParser from 'body-parser';
import config from './config';
import { requestMiddleware } from './utils/middlewares';
import { errorLogger, errorResponder, invalidPathHandler } from './utils/genericUtils';
import { tracingMiddleware } from './tracing';
import { createProxyMiddleware } from 'http-proxy-middleware';

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
    this.app.use(tracingMiddleware);
    this.app.use(requestMiddleware);
    this.app.use(errorLogger);
    this.app.use(errorResponder);
    this.app.use('/tracing', createProxyMiddleware({
      target: 'http://localhost:16686',
      changeOrigin: true,
      pathRewrite: {
        '^/tracing': '/',
      },
    }));
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
