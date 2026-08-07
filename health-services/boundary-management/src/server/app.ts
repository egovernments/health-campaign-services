import express from "express";
import * as bodyParser from "body-parser";
import config from "./config";
import { requestMiddleware } from "./utils/middlewares";
import {
  errorLogger,
  errorResponder,
  invalidPathHandler,
} from "./utils/genericUtils";

import { createProxyMiddleware } from "http-proxy-middleware";
import * as v8 from "v8";
import { logger } from "./utils/logger";
import { Server } from "http";

class App {
  public app: express.Application;
  public port: number;
  private readonly MEMORY_LOG_INTERVAL_MS = 30000; // Log memory every 30 seconds

  constructor(controllers: any, port: any) {
    this.app = express();
    this.port = port;

    this.initializeMiddlewares();
    this.initializeControllers(controllers);
    this.app.use(invalidPathHandler);

    // Start periodic memory usage logging
    this.startMemoryLogging();

    // Global error handling for uncaught exceptions
    process.on("uncaughtException", (err) => {
      console.error("Unhandled Exception:", err);
    });

    // Global error handling for unhandled promise rejections
    process.on("unhandledRejection", (reason, promise) => {
      console.error("Unhandled Rejection at:", promise, "reason:", reason);
    });
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
    this.app.use(
      bodyParser.json({ limit: config.app.incomingRequestPayloadLimit })
    );
    this.app.use(
      bodyParser.urlencoded({
        limit: config.app.incomingRequestPayloadLimit,
        extended: true,
      })
    );
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

  public async listen() {
    const server: Server = await new Promise((resolve) => {
      const serverInstance = this.app.listen(this.port, () => {
        logger.info(`App listening on port ${this.port}`);
        resolve(serverInstance);
      });
    });

    // Configure server timeouts
    server.setTimeout(480000); // 480 seconds
    server.keepAliveTimeout = 90000; // 90 seconds
    server.headersTimeout = 120000; // 120 seconds
  }
  
}
export default App;
