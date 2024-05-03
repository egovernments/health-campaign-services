import { createLogger, format, transports } from "winston"; // Importing necessary modules from Winston library

// Custom log format for Winston logger
const myFormat = format.printf(({ level, message, label, timestamp }) => {
  return `${timestamp} [${label}] [${level}]: ${message}`; // Custom log message format
});

// Creating a logger instance with specified format and transports
const logger = createLogger({
  format: format.combine( // Combining different log formats
    format.label({ label: 'BFF' }), // Adding label to logs
    format.timestamp({ format: " YYYY-MM-DD HH:mm:ss.SSSZZ " }), // Adding timestamp to logs
    format.simple(), // Simplifying log format
    format.colorize(), // Adding color to logs for console output
    myFormat // Using custom log format defined above
  ),
  transports: [new transports.Console()], // Using Console transport for logging
});

// Exporting the logger instance for external use
export { logger };