"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var express_1 = __importDefault(require("express"));
var bodyParser = __importStar(require("body-parser"));
var config_1 = __importDefault(require("./config"));
var middlewares_1 = require("./utils/middlewares");
var utils_1 = require("./utils");
var App = /** @class */ (function () {
    function App(controllers, port) {
        this.app = (0, express_1.default)();
        this.port = port;
        this.initializeMiddlewares();
        this.initializeControllers(controllers);
        this.app.use(utils_1.invalidPathHandler);
    }
    App.prototype.initializeMiddlewares = function () {
        this.app.use(bodyParser.json());
        this.app.use(middlewares_1.requestMiddleware);
        // this.app.use(cacheMiddleware);
        // Attach the first Error handling Middleware
        // function defined above (which logs the error)
        this.app.use(utils_1.errorLogger);
        // Attach the second Error handling Middleware
        // function defined above (which sends back the response)
        this.app.use(utils_1.errorResponder);
        // Attach the fallback Middleware
        // function which sends back the response for invalid paths)
    };
    App.prototype.initializeControllers = function (controllers) {
        var _this = this;
        controllers.forEach(function (controller) {
            var _a;
            _this.app.use((_a = config_1.default.app) === null || _a === void 0 ? void 0 : _a.contextPath, controller.router);
        });
    };
    App.prototype.listen = function () {
        var _this = this;
        this.app.listen(this.port, function () {
            console.log("App listening on the port ".concat(_this.port));
        });
    };
    return App;
}());
exports.default = App;
