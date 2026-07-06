import App from './app';
import config from "./config";
import controllers  from "./controllers";
import { startBoundaryJobConsumer } from './kafka/boundaryJobQueue';
import { logger } from './utils/logger';

const app = new App(
  controllers,
  config.app.port,
);

app.listen();

if (config.boundaryJobs.enabled) {
  startBoundaryJobConsumer().catch((e) => {
    // A pod that cannot consume jobs must not run silently — jobs it was meant to share
    // would pile onto the other pods with no signal.
    logger.error(`Boundary jobs :: consumer failed to start: ${e?.message}`);
    process.exit(1);
  });
}
