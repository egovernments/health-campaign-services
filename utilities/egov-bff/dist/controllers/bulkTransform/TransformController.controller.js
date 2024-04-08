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
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var express = __importStar(require("express"));
// import { produceIngestion } from "../../utils";
// import FormData from 'form-data';
var index_1 = __importDefault(require("../../config/index"));
// import * as XLSX from 'xlsx';
var logger_1 = require("../../utils/logger");
var index_2 = require("../../api/index");
var index_3 = require("../../utils/index");
// import { httpRequest } from "../../utils/request";
// Define the MeasurementController class
var TransformController = /** @class */ (function () {
    // Constructor to initialize routes
    function TransformController() {
        var _this = this;
        // Define class properties
        this.path = "/bulk";
        this.router = express.Router();
        this.dayInMilliSecond = 86400000;
        this.getTransformedData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var _a, fileStoreId, transformTemplate, selectedRows, result, TransformConfig, url, updatedDatas, error_1;
            var _b, _c, _d, _e, _f, _g, _h;
            return __generator(this, function (_j) {
                switch (_j.label) {
                    case 0:
                        _j.trys.push([0, 3, , 4]);
                        _a = (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.HCMConfig, fileStoreId = _a.fileStoreId, transformTemplate = _a.transformTemplate, selectedRows = _a.selectedRows;
                        logger_1.logger.info("TransformTemplate :" + transformTemplate);
                        return [4 /*yield*/, (0, index_2.searchMDMS)([transformTemplate], index_1.default.values.transfromTemplate, request.body.RequestInfo, response)];
                    case 1:
                        result = _j.sent();
                        if (((_c = result === null || result === void 0 ? void 0 : result.mdms) === null || _c === void 0 ? void 0 : _c.length) > 0) {
                            TransformConfig = result.mdms[0];
                            logger_1.logger.info("TransformConfig : " + JSON.stringify(TransformConfig));
                        }
                        else {
                            logger_1.logger.info("No Transform Template found");
                            return [2 /*return*/, (0, index_3.sendResponse)(response, { "Error": "Transform Template error" }, request)];
                        }
                        url = index_1.default.host.filestore + index_1.default.paths.filestore + "/url?tenantId=".concat((_f = (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.RequestInfo) === null || _e === void 0 ? void 0 : _e.userInfo) === null || _f === void 0 ? void 0 : _f.tenantId, "&fileStoreIds=").concat(fileStoreId);
                        logger_1.logger.info("File fetching url : " + url);
                        return [4 /*yield*/, (0, index_2.getSheetData)(url, selectedRows, (_g = TransformConfig === null || TransformConfig === void 0 ? void 0 : TransformConfig.data) === null || _g === void 0 ? void 0 : _g.Fields, (_h = TransformConfig === null || TransformConfig === void 0 ? void 0 : TransformConfig.data) === null || _h === void 0 ? void 0 : _h.sheetName)];
                    case 2:
                        updatedDatas = _j.sent();
                        logger_1.logger.info("Updated Datas : " + JSON.stringify(updatedDatas));
                        // After processing all mdms elements, send the response
                        return [2 /*return*/, (0, index_3.sendResponse)(response, { updatedDatas: updatedDatas }, request)];
                    case 3:
                        error_1 = _j.sent();
                        logger_1.logger.error(String(error_1));
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: String(error_1) + "    Check Logs" }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.process = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var _a, data, parsingTemplate, parsingResults, parsingConfig, updatedDatas, groupedData_1, uniqueKeys_1, error_2;
            var _b, _c, _d, _e, _f;
            return __generator(this, function (_g) {
                switch (_g.label) {
                    case 0:
                        _g.trys.push([0, 2, , 3]);
                        _a = (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.HCMConfig, data = _a.data, parsingTemplate = _a.parsingTemplate;
                        return [4 /*yield*/, (0, index_2.searchMDMS)([parsingTemplate], index_1.default.values.parsingTemplate, request.body.RequestInfo, response)];
                    case 1:
                        parsingResults = _g.sent();
                        if (((_c = parsingResults === null || parsingResults === void 0 ? void 0 : parsingResults.mdms) === null || _c === void 0 ? void 0 : _c.length) > 0) {
                            parsingConfig = (_f = (_e = (_d = parsingResults === null || parsingResults === void 0 ? void 0 : parsingResults.mdms) === null || _d === void 0 ? void 0 : _d[0]) === null || _e === void 0 ? void 0 : _e.data) === null || _f === void 0 ? void 0 : _f.path;
                            logger_1.logger.info("parsingConfig : " + JSON.stringify(parsingConfig));
                        }
                        else {
                            logger_1.logger.info("Parsing Template Error");
                            return [2 /*return*/, (0, index_3.sendResponse)(response, { "Error": "Parsing Template Error" }, request)];
                        }
                        updatedDatas = [];
                        if (Array.isArray(data)) {
                            updatedDatas = data.map(function (element) {
                                return (0, index_3.convertObjectForMeasurment)(element, parsingConfig);
                            });
                        }
                        groupedData_1 = [];
                        if (parsingConfig.some(function (configItem) { return configItem.unique; })) {
                            uniqueKeys_1 = new Set();
                            // Iterate through updatedDatas and group based on unique keys
                            updatedDatas.forEach(function (data) {
                                var uniqueValues = parsingConfig
                                    .filter(function (configItem) { return configItem.unique; })
                                    .map(function (configItem) { return data[configItem.path]; })
                                    .join('!|!');
                                var existingIndex = groupedData_1.findIndex(function (group) { return uniqueKeys_1.has(uniqueValues); });
                                if (existingIndex !== -1) {
                                    // Update consolidated fields
                                    parsingConfig.forEach(function (configItem) {
                                        if (configItem.isConsolidate) {
                                            var currentValue = groupedData_1[existingIndex][configItem.path];
                                            var newValue = data[configItem.path];
                                            // Consolidate based on data type
                                            if (typeof currentValue === 'number' && typeof newValue === 'number') {
                                                groupedData_1[existingIndex][configItem.path] = currentValue + newValue;
                                            }
                                            else if (typeof currentValue === 'string' && typeof newValue === 'string') {
                                                groupedData_1[existingIndex][configItem.path] = currentValue + newValue;
                                            }
                                            else if (typeof currentValue === 'boolean' && typeof newValue === 'boolean') {
                                                groupedData_1[existingIndex][configItem.path] = currentValue || newValue;
                                            }
                                        }
                                    });
                                }
                                else {
                                    // Add new group
                                    uniqueKeys_1.add(uniqueValues);
                                    groupedData_1.push(data);
                                }
                            });
                        }
                        else {
                            // If none have unique as true, skip the unique concept
                            groupedData_1.push.apply(groupedData_1, updatedDatas);
                        }
                        logger_1.logger.info("Grouped Data : " + JSON.stringify(groupedData_1));
                        return [2 /*return*/, (0, index_3.sendResponse)(response, { updatedDatas: groupedData_1 }, request)];
                    case 2:
                        error_2 = _g.sent();
                        logger_1.logger.error(String(error_2));
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: String(error_2) + "    Check Logs" }, request, response)];
                    case 3: return [2 /*return*/];
                }
            });
        }); };
        this.getBoundaryData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var _a, hierarchyType, tenantId, boundarySheetData, BoundaryFileDetails, error_3;
            var _b;
            return __generator(this, function (_c) {
                switch (_c.label) {
                    case 0:
                        _c.trys.push([0, 3, , 4]);
                        _a = (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.BoundaryDetails, hierarchyType = _a.hierarchyType, tenantId = _a.tenantId;
                        return [4 /*yield*/, (0, index_2.getBoundarySheetData)(hierarchyType, tenantId, request)];
                    case 1:
                        boundarySheetData = _c.sent();
                        return [4 /*yield*/, (0, index_2.createAndUploadFile)(boundarySheetData === null || boundarySheetData === void 0 ? void 0 : boundarySheetData.wb, request)];
                    case 2:
                        BoundaryFileDetails = _c.sent();
                        return [2 /*return*/, BoundaryFileDetails];
                    case 3:
                        error_3 = _c.sent();
                        logger_1.logger.error(String(error_3));
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: String(error_3) + "    Check Logs" }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.intializeRoutes();
    }
    // Initialize routes for MeasurementController
    TransformController.prototype.intializeRoutes = function () {
        this.router.post("".concat(this.path, "/_transform"), this.getTransformedData);
        this.router.post("".concat(this.path, "/_process"), this.process);
        this.router.post("".concat(this.path, "/_getboundarysheet"), this.getBoundaryData);
    };
    return TransformController;
}());
// Export the MeasurementController class
exports.default = TransformController;
