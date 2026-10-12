import { createRequestValidation } from "@egovernments/request-validation";
import { logger } from "./logger";

export const requestValidation = createRequestValidation({
  // ENFORCE: reject requests carrying script/markup (HTTP 400 REQUEST_CONTENT_NOT_ALLOWED). EGOV_REQUEST_VALIDATION_* env vars override these.
  enabled: true,
  structuredDefault: true,
  activation: "ALL",
  mode: "ENFORCE",
  logger: { warn: (line: string) => { logger.warn(line); }, info: (line: string) => { logger.info(line); } },
  excludePaths: [{ path: "/tracing", reason: "Jaeger UI reverse proxy (http-proxy-middleware), not an API handler" }],
});
