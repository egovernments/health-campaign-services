import { checkGenerateFlowClasses } from './generateFlowClasses/generateFlowClassChecker';
import { checkProcessFlowClasses } from './processFlowClasses/processFlowClassChecker';

// Type-check the flow classes at startup and exit non-zero on any error, before the server binds.
checkGenerateFlowClasses();
checkProcessFlowClasses();

import App from './app';
import controllers from './controllers';
import config from "./config";

const app = new App(
  controllers,
  config.app.port,
);

app.listen();
