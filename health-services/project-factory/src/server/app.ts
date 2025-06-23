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
import { Server } from "http";

const histogram = monitorEventLoopDelay({ resolution: 20 });
histogram.enable();

class App {
  public app: express.Application;
  public port: number;
  private inflight = 0;
  private readonly MAX_INFLIGHT = parseInt(config.app.maxInFlight || "15");
  private readonly MAX_EVENT_LOOP_DELAY_MS = parseInt(config.app.maxEventLoopLagMs || "100");
  private readonly MEMORY_LOG_INTERVAL_MS = 60000; // Log memory every 60 seconds

  constructor(controllers: any, port: any) {
    this.app = express();
    this.port = port;

    this.initializeMiddlewares();
    this.initializeControllers(controllers);
    this.app.use(invalidPathHandler);

    // Start periodic memory usage logging
    this.startMemoryLogging();
  }

  private isMemoryCritical(): boolean {
    const stats = v8.getHeapStatistics();
    return stats.used_heap_size / stats.heap_size_limit > 0.5;
  }

  private logMemoryUsage(): void {
    const stats = v8.getHeapStatistics();
    const usedHeapMB = (stats.used_heap_size / 1024 / 1024).toFixed(2);
    const totalHeapMB = (stats.heap_size_limit / 1024 / 1024).toFixed(2);
    const heapUsageRatio = (stats.used_heap_size / stats.heap_size_limit).toFixed(2);
    const isCritical = this.isMemoryCritical();

    logger.info(
      `Memory Usage: ${usedHeapMB}MB / ${totalHeapMB}MB ` +
      `(Ratio: ${heapUsageRatio}, Critical: ${isCritical})`
    );
  }

  private startMemoryLogging(): void {
    setInterval(() => {
      this.logMemoryUsage();
    }, this.MEMORY_LOG_INTERVAL_MS);
  }

  private initializeMiddlewares() {
    // Health endpoint
    this.app.get("/health", (_req, res) => {
      const memCrit = this.isMemoryCritical();
      const eventLoopLagMs = Number(histogram.mean) / 1e6;
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

    // In-flight tracking
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
  }

  private initializeControllers(controllers: any) {
    controllers.forEach((ctr: any) => {
      this.app.use(config.app.contextPath, ctr.router);
    });
  }

  public listen() {
    const server: Server = this.app.listen(this.port, () => {
      logger.info(`App listening on port ${this.port}`);
    });

    // Configure server timeouts
    server.setTimeout(300000); // 300000 seconds for entire request
    server.keepAliveTimeout = 45000; // 45 seconds for keep-alive connections
    server.headersTimeout = 50000; // 50 seconds for headers
  }
}

export default App;
