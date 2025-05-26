import { checkGenerateFlowClasses } from './generateFlowClasses/generateFlowClassChecker';
import { checkProcessFlowClasses } from './processFlowClasses/processFlowClassChecker';
import { checkExistingLocalisations } from './utils/localisationUtils';

import App from './app';
import controllers from './controllers';
import config from "./config";

(async () => {
  checkGenerateFlowClasses();
  checkProcessFlowClasses();
  await checkExistingLocalisations(); // Await the async function

  const app = new App(
    controllers,
    config.app.port,
  );

  app.listen();
})();

