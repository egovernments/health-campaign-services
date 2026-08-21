import { createLogger, format, transports } from "winston"; // Importing necessary modules from Winston library
import config from "../../config";

// Custom log format for Winston logger
const myFormat = format.printf(({ level, message, label, timestamp }) => {
  return `${timestamp} [${label}] [${level}]: ${message}`; // Custom log message format
});

// Creating a logger instance with specified format and transports
const logger = createLogger({
  level: config.app.logLevel, // Set the minimum level to log, in this case, DEBUG
  format: format.combine(
    // Combining different log formats
    format.label({ label: "BFF" }), // Adding label to logs
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
