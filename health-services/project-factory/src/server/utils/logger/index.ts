import { createLogger, format,  transports } from "winston"; // Importing necessary modules from Winston library
import config from "../../config";
const path = require('path');

// Helper function to get the calling file name and line number
const getCallerFile = (count = 4) => {
  const originalFunc = Error.prepareStackTrace;
  Error.prepareStackTrace = (_, stack) => stack;
  const stack:any = new Error().stack;
  Error.prepareStackTrace = originalFunc;

  // Filter the stack to remove unwanted paths
  const filteredStack = stack.filter((callSite:any) => {
      const filePath = callSite.getFileName();
      return !filePath?.includes('node_modules') && !filePath?.includes('/utils/logger/') &&  !filePath?.includes('events.js')    ;
  })

  // Get the last 'count' valid callers from the filtered stack
  const callers = filteredStack.slice(-count).map((callSite:any) => callSite.getFileName()&&({
      file: path.basename(callSite.getFileName()),
      line: callSite.getLineNumber(),
  }));
  return callers?.map((e:any)=>`${e?.file?.replace(".ts","")}:${e?.line}`);
};

// Custom log format for Winston logger
const myFormat = format.printf(({ level, message, label, timestamp }) => {
  return `${timestamp} [${label}] ${level}: [${getCallerFile(3)}] ${message}`;
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

export const getFormattedStringForDebug = (obj: any) => {
  const convertedMessage=JSON.stringify(obj);
  return convertedMessage?.slice(0, DEFAULT_LOG_MESSAGE_COUNT) +
  (convertedMessage?.length > DEFAULT_LOG_MESSAGE_COUNT ? "\n ---more" : "");
}
