"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.logger = void 0;
var winston_1 = require("winston");
var myFormat = winston_1.format.printf(function (_a) {
    var level = _a.level, message = _a.message, label = _a.label, timestamp = _a.timestamp;
    return "".concat(timestamp, " [").concat(label, "] [").concat(level, "]: ").concat(message);
});
var logger = (0, winston_1.createLogger)({
    format: winston_1.format.combine(winston_1.format.label({ label: 'BFF' }), winston_1.format.timestamp({ format: " YYYY-MM-DD HH:mm:ss.SSSZZ " }), winston_1.format.simple(), winston_1.format.colorize(), myFormat),
    transports: [new winston_1.transports.Console()],
});
exports.logger = logger;
