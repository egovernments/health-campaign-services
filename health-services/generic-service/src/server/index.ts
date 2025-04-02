import App from './app';
import controllers from './controllers';
import config from "./config";

const app = new App(
  controllers,
  config.app.port,
);

app.listen();
