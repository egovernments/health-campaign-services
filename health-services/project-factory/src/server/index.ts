// Increase libuv thread pool for parallel async PBKDF2 decryption (must be set before any I/O)
process.env.UV_THREADPOOL_SIZE = '16';

import { checkGenerateFlowClasses } from './generateFlowClasses/generateFlowClassChecker';
import { checkProcessFlowClasses } from './processFlowClasses/processFlowClassChecker';
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
