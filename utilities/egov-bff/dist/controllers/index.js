"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var campaignManage_controller_1 = __importDefault(require("./campaignManage/campaignManage.controller"));
var dataManage_controller_1 = __importDefault(require("./dataManage/dataManage.controller"));
var controllers = [
    new campaignManage_controller_1.default(),
    new dataManage_controller_1.default()
];
exports.default = controllers;
