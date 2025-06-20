import express from "express";
import * as bodyParser from "body-parser";
import config from "./config";
import { requestMiddleware } from "./utils/middlewares";
import {
  errorLogger,
  errorResponder,
  invalidPathHandler,
} from "./utils/genericUtils";
import { tracingMiddleware } from "./tracing";
import { createProxyMiddleware } from "http-proxy-middleware";
import * as v8 from "v8";
import { logger } from "./utils/logger";
import { monitorEventLoopDelay } from "perf_hooks";

const histogram = monitorEventLoopDelay({ resolution: 20 });
histogram.enable();

class App {
  public app: express.Application;
  public port: number;
  private inflight = 0;
  private readonly MAX_INFLIGHT = parseInt(config.app.maxInFlight || "5");
  private readonly MAX_EVENT_LOOP_DELAY_MS = parseInt(config.app.maxEventLoopLagMs || "100");

  constructor(controllers: any, port: any) {
    this.app = express();
    this.port = port;

    this.initializeMiddlewares();
    this.initializeControllers(controllers);
    this.app.use(invalidPathHandler);
  }

  private isMemoryCritical(): boolean {
    const stats = v8.getHeapStatistics();
    return stats.used_heap_size / stats.heap_size_limit > 0.6;
  }

  private initializeMiddlewares() {
    this.app.use((req, res, next) => {
      this.inflight++;
      res.on("finish", () => this.inflight--);
      next();
    });

    this.app.use(bodyParser.json({ limit: config.app.incomingRequestPayloadLimit }));
    this.app.use(
      bodyParser.urlencoded({
        limit: config.app.incomingRequestPayloadLimit,
        extended: true,
      })
    );
    this.app.use(tracingMiddleware);
    this.app.use(requestMiddleware);
    this.app.use(errorLogger);
    this.app.use(errorResponder);

    this.app.use(
      "/tracing",
      createProxyMiddleware({
        target: "http://localhost:16686",
        changeOrigin: true,
        pathRewrite: { "^/tracing": "/" },
      })
    );

    // Enhanced /health endpoint
    this.app.get("/health", (_req, res) => {
      const memCrit = this.isMemoryCritical();
      const eventLoopLagMs = Number(histogram.mean) / 1e6; // convert ns to ms
      const lagCrit = eventLoopLagMs > this.MAX_EVENT_LOOP_DELAY_MS;

      if (this.inflight >= this.MAX_INFLIGHT || memCrit || lagCrit) {
        const reason = memCrit
          ? "memory critical"
          : lagCrit
            ? `event-loop lag high (${eventLoopLagMs.toFixed(1)}ms)`
            : "too many in-flight requests";
        logger.warn(`/health failed: ${reason} (inflight=${this.inflight})`);
        res.status(500).send("NOT ALIVE");
      } else {
        res.status(200).send("ALIVE");
      }
    });
  }

  private initializeControllers(controllers: any) {
    controllers.forEach((ctr: any) => {
      this.app.use(config.app.contextPath, ctr.router);
    });
  }

  public listen() {
    this.app.listen(this.port, () => {
      logger.info(`App listening on port ${this.port}`);
    });
  }
}

export default App;
