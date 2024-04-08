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
var index_1 = __importDefault(require("../../config/index"));
var logger_1 = require("../../utils/logger");
var index_2 = require("../../utils/index");
var index_3 = require("../../api/index");
var index_4 = require("../../utils/index");
var validator_1 = require("../../utils/validator");
var request_1 = require("../../utils/request");
var index_5 = require("../../utils/index");
var index_6 = require("../../utils/index");
var Listener_1 = require("../../Kafka/Listener");
var updateGeneratedResourceTopic = index_1.default.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC;
// Define the MeasurementController class
var genericAPIController = /** @class */ (function () {
    // Constructor to initialize routes
    function genericAPIController() {
        var _this = this;
        // Define class properties
        this.path = "/hcm";
        this.router = express.Router();
        this.dayInMilliSecond = 86400000;
        this.createData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var _a, type, fileStoreId, hostHcmBff, result, finalResponse, failedMessage, finalResponse, failedMessage, finalResponse, error_1;
            var _b, _c;
            return __generator(this, function (_d) {
                switch (_d.label) {
                    case 0:
                        _d.trys.push([0, 9, , 10]);
                        _a = (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.ResourceDetails, type = _a.type, fileStoreId = _a.fileStoreId;
                        hostHcmBff = index_1.default.host.hcmBff.endsWith('/') ? index_1.default.host.hcmBff.slice(0, -1) : index_1.default.host.hcmBff;
                        return [4 /*yield*/, (0, request_1.httpRequest)("".concat(hostHcmBff).concat(index_1.default.app.contextPath).concat('/hcm', "/_validate"), request.body, undefined, undefined, undefined, undefined)];
                    case 1:
                        result = _d.sent();
                        if (!((result === null || result === void 0 ? void 0 : result.validationResult) == "VALID_DATA" || (result === null || result === void 0 ? void 0 : result.validationResult) == "NO_VALIDATION_SCHEMA_FOUND")) return [3 /*break*/, 4];
                        return [4 /*yield*/, (0, index_3.processCreateData)(result, type, request, response)];
                    case 2:
                        finalResponse = _d.sent();
                        return [4 /*yield*/, (0, index_3.updateFile)(fileStoreId, finalResponse, (_c = result === null || result === void 0 ? void 0 : result.creationDetails) === null || _c === void 0 ? void 0 : _c.sheetName, request)];
                    case 3:
                        _d.sent();
                        (0, Listener_1.produceModifiedMessages)(finalResponse, index_1.default.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
                        (0, Listener_1.produceModifiedMessages)(finalResponse, index_1.default.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC);
                        return [2 /*return*/, (0, index_4.sendResponse)(response, { ResponseDetails: finalResponse.ResponseDetails }, request)];
                    case 4:
                        if (!((result === null || result === void 0 ? void 0 : result.validationResult) == "INVALID_DATA")) return [3 /*break*/, 6];
                        return [4 /*yield*/, (0, index_4.generateResourceMessage)(request.body, "INVALID_DATA")];
                    case 5:
                        failedMessage = _d.sent();
                        failedMessage.error = (result === null || result === void 0 ? void 0 : result.errors) || "Error during validation of data, Check Logs";
                        finalResponse = { ResponseDetails: [failedMessage] };
                        (0, Listener_1.produceModifiedMessages)(finalResponse, index_1.default.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
                        return [2 /*return*/, (0, index_4.sendResponse)(response, finalResponse, request)];
                    case 6: return [4 /*yield*/, (0, index_4.generateResourceMessage)(request.body, "OTHER_ERROR")];
                    case 7:
                        failedMessage = _d.sent();
                        failedMessage.error = "Some other error, Check Logs";
                        finalResponse = { ResponseDetails: [failedMessage] };
                        (0, Listener_1.produceModifiedMessages)(finalResponse, index_1.default.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
                        return [2 /*return*/, (0, index_4.sendResponse)(response, finalResponse, request)];
                    case 8: return [3 /*break*/, 10];
                    case 9:
                        error_1 = _d.sent();
                        logger_1.logger.error(error_1);
                        return [2 /*return*/, (0, index_4.sendResponse)(response, { "error": error_1.message }, request)];
                    case 10: return [2 /*return*/];
                }
            });
        }); };
        this.validateData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var _a, type, fileStoreId, APIResourceName, APIResource, _b, transformTemplate, parsingTemplate, _c, sheetName, processResult, schemaDef, error_2;
            var _d;
            return __generator(this, function (_e) {
                switch (_e.label) {
                    case 0:
                        _e.trys.push([0, 4, , 5]);
                        _a = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.ResourceDetails, type = _a.type, fileStoreId = _a.fileStoreId;
                        APIResourceName = type;
                        return [4 /*yield*/, (0, index_3.searchMDMS)([APIResourceName], index_1.default.values.APIResource, request.body.RequestInfo, response)];
                    case 1:
                        APIResource = _e.sent();
                        return [4 /*yield*/, (0, validator_1.getTransformAndParsingTemplates)(APIResource, request, response)];
                    case 2:
                        _b = _e.sent(), transformTemplate = _b.transformTemplate, parsingTemplate = _b.parsingTemplate;
                        return [4 /*yield*/, (0, index_2.fetchDataAndUpdate)(transformTemplate, parsingTemplate, fileStoreId, APIResource, request, response)];
                    case 3:
                        _c = _e.sent(), sheetName = _c.sheetName, processResult = _c.processResult, schemaDef = _c.schemaDef;
                        return [2 /*return*/, (0, index_2.processValidationResultsAndSendResponse)(sheetName, processResult, schemaDef, APIResource, response, request)];
                    case 4:
                        error_2 = _e.sent();
                        logger_1.logger.error(error_2);
                        return [2 /*return*/, (0, index_4.sendResponse)(response, { "validationResult": "ERROR", "errorDetails": error_2.message }, request)];
                    case 5: return [2 /*return*/];
                }
            });
        }); };
        this.generateData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var responseData, modifiedResponse, newEntryResponse, oldEntryResponse, _a, forceUpdate, type, forceUpdateBool, generatedResource, e_1;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 7, , 8]);
                        return [4 /*yield*/, (0, index_2.getResponseFromDb)(request, response)];
                    case 1:
                        responseData = _b.sent();
                        return [4 /*yield*/, (0, index_2.getModifiedResponse)(responseData)];
                    case 2:
                        modifiedResponse = _b.sent();
                        return [4 /*yield*/, (0, index_2.getNewEntryResponse)(modifiedResponse, request)];
                    case 3:
                        newEntryResponse = _b.sent();
                        return [4 /*yield*/, (0, index_2.getOldEntryResponse)(modifiedResponse, request)];
                    case 4:
                        oldEntryResponse = _b.sent();
                        _a = request.query, forceUpdate = _a.forceUpdate, type = _a.type;
                        forceUpdateBool = forceUpdate === 'true';
                        generatedResource = void 0;
                        if (forceUpdateBool) {
                            if (responseData.length > 0) {
                                generatedResource = { generatedResource: oldEntryResponse };
                                (0, Listener_1.produceModifiedMessages)(generatedResource, updateGeneratedResourceTopic);
                            }
                        }
                        if (!(responseData.length === 0 || forceUpdateBool)) return [3 /*break*/, 6];
                        return [4 /*yield*/, (0, index_2.fullProcessFlowForNewEntry)(newEntryResponse, request, response)];
                    case 5:
                        _b.sent();
                        _b.label = 6;
                    case 6: return [2 /*return*/, (0, index_4.sendResponse)(response, { ResponseDetails: { type: type, status: 'Table up to date' } }, request)];
                    case 7:
                        e_1 = _b.sent();
                        logger_1.logger.error(String(e_1));
                        return [2 /*return*/, (0, index_5.errorResponder)({ message: String(e_1) }, request, response)];
                    case 8: return [2 /*return*/];
                }
            });
        }); };
        this.downloadData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var type_1, responseData, auditDetails_1, transformedResponse, e_2;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 3, , 4]);
                        type_1 = request.query.type;
                        return [4 /*yield*/, (0, index_2.getResponseFromDb)(request, response)];
                    case 1:
                        responseData = _a.sent();
                        if (!responseData || responseData.length === 0) {
                            logger_1.logger.error("No data of type  " + type_1 + " with status Completed present in db");
                            throw new Error('First Generate then Download');
                        }
                        return [4 /*yield*/, (0, index_6.generateAuditDetails)(request)];
                    case 2:
                        auditDetails_1 = _a.sent();
                        transformedResponse = responseData.map(function (item) {
                            return {
                                fileStoreId: item.filestoreid,
                                additionalDetails: {},
                                type: type_1,
                                auditDetails: auditDetails_1
                            };
                        });
                        return [2 /*return*/, (0, index_4.sendResponse)(response, { fileStoreIds: transformedResponse }, request)];
                    case 3:
                        e_2 = _a.sent();
                        logger_1.logger.error(String(e_2));
                        return [2 /*return*/, (0, index_5.errorResponder)({ message: String(e_2) + "    Check Logs" }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.intializeRoutes();
    }
    // Initialize routes for MeasurementController
    genericAPIController.prototype.intializeRoutes = function () {
        this.router.post("".concat(this.path, "/_create"), this.createData);
        this.router.post("".concat(this.path, "/_validate"), this.validateData);
        this.router.post("".concat(this.path, "/_download"), this.downloadData);
        this.router.post("".concat(this.path, "/_generate"), this.generateData);
    };
    return genericAPIController;
}());
;
exports.default = genericAPIController;
