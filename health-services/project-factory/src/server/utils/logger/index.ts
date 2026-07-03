import { createLogger, format, transports } from "winston"; // Importing necessary modules from Winston library
import config from "../../config";
import { getRequestContext } from "../requestContext";

function getCallerInfo(): string {
  const lines = new Error().stack?.split('\n') ?? [];
  for (const line of lines.slice(1)) {
    const l = line.trim();
    if (
      l.includes('node_modules') ||
      l.includes('logger/index') ||
      l.includes('logger\\index')
    ) continue;
    const m = l.match(/\((.+):(\d+):\d+\)$/) ?? l.match(/at (.+):(\d+):\d+$/);
    if (m) return `${m[1]}:${m[2]}`;
  }
  return 'unknown';
}

// Custom log format for Winston logger
const myFormat = format.printf(({ level, message, timestamp }) => {
  const { correlationId, tenantId } = getRequestContext();
  const callerInfo = level.includes('debug') ? ` [${getCallerInfo()}]` : '';
  return `${timestamp} [${level}] [tenantId=${tenantId}] [correlationId=${correlationId}]${callerInfo}: ${message}`;
});

// Creating a logger instance with specified format and transports
const logger = createLogger({
  level: config.app.logLevel, // Set the minimum level to log, in this case, DEBUG
  format: format.combine(
    // Combining different log formats
    format.timestamp({ format: " YYYY-MM-DD HH:mm:ss.SSSZZ " }), // Adding timestamp to logs
    format.simple(), // Simplifying log format
    format.colorize(), // Adding color to logs for console output
    myFormat // Using custom log format defined above
  ),
  transports: [new transports.Console()], // Using Console transport for logging
});

// Exporting the logger instance for external use
export { logger };

const DEFAULT_LOG_MESSAGE_COUNT = config.app.debugLogCharLimit;

export const getFormattedStringForDebug = (obj: any): string => {
  try {
    const convertedMessage = JSON.stringify(obj);
    return convertedMessage.slice(0, DEFAULT_LOG_MESSAGE_COUNT) +
      (convertedMessage.length > DEFAULT_LOG_MESSAGE_COUNT ? "\n ---more" : "");
  } catch (error : any ) {
    if (error instanceof RangeError && error.message.includes("Invalid string length")) {
      logger.error("The object is too big to convert into a string.");
    } else {
      logger.error(`An unexpected error occurred while formatting the object into a string : ${error?.message}`);
    }
    return "Error: Unable to format object for debug.";
  }
};
