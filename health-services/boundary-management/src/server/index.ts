import App from './app';
import config from "./config";
import controllers  from "./controllers";

const app = new App(
  controllers,
  config.app.port,
);

app.listen();
