"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var app_1 = __importDefault(require("./app"));
var controllers_1 = __importDefault(require("./controllers"));
var config_1 = __importDefault(require("./config"));
var app = new app_1.default(controllers_1.default, config_1.default.app.port);
app.listen();
