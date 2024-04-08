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
var __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var express = __importStar(require("express"));
var logger_1 = require("../../utils/logger");
var genericValidator_1 = require("../../utils/validators/genericValidator");
var index_1 = require("../../utils/index");
var campaignApis_1 = require("../../api/campaignApis");
var genericApis_1 = require("../../api/genericApis");
var config_1 = __importDefault(require("../../config"));
var request_1 = require("../../utils/request");
var campaignValidators_1 = require("../../utils/validators/campaignValidators");
// Define the MeasurementController class
var dataManageController = /** @class */ (function () {
    // Constructor to initialize routes
    function dataManageController() {
        var _this = this;
        // Define class properties
        this.path = "/v1/data";
        this.router = express.Router();
        this.dayInMilliSecond = 86400000;
        this.generateData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var e_1;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 2, , 3]);
                        (0, genericValidator_1.validateGenerateRequest)(request);
                        return [4 /*yield*/, (0, index_1.processGenerate)(request, response)];
                    case 1:
                        _b.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { GeneratedResource: (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.generatedResource }, request)];
                    case 2:
                        e_1 = _b.sent();
                        logger_1.logger.error(String(e_1));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_1) }, request, response)];
                    case 3: return [2 /*return*/];
                }
            });
        }); };
        this.downloadData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var type_1, responseData, auditDetails_1, transformedResponse, fileStoreId, fileResponse, boundaryData, updatedWorkbook, boundaryDetails, e_2;
            var _a, _b, _c, _d, _e, _f, _g;
            return __generator(this, function (_h) {
                switch (_h.label) {
                    case 0:
                        _h.trys.push([0, 8, , 9]);
                        type_1 = request.query.type;
                        return [4 /*yield*/, (0, index_1.getResponseFromDb)(request, response)];
                    case 1:
                        responseData = _h.sent();
                        if (!responseData || responseData.length === 0) {
                            logger_1.logger.error("No data of type  " + type_1 + " with status Completed present in db");
                            throw new Error('First Generate then Download');
                        }
                        auditDetails_1 = (0, index_1.generateAuditDetails)(request);
                        transformedResponse = responseData.map(function (item) {
                            return {
                                fileStoreId: item.fileStoreid,
                                additionalDetails: {},
                                type: type_1,
                                auditDetails: auditDetails_1
                            };
                        });
                        if (!(type_1 == "boundaryWithTarget")) return [3 /*break*/, 6];
                        fileStoreId = transformedResponse === null || transformedResponse === void 0 ? void 0 : transformedResponse[0].fileStoreId;
                        return [4 /*yield*/, (0, request_1.httpRequest)(config_1.default.host.filestore + config_1.default.paths.filestore + "/url", {}, { tenantId: (_a = request === null || request === void 0 ? void 0 : request.query) === null || _a === void 0 ? void 0 : _a.tenantId, fileStoreIds: fileStoreId }, "get")];
                    case 2:
                        fileResponse = _h.sent();
                        if (!((_c = (_b = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _b === void 0 ? void 0 : _b[0]) === null || _c === void 0 ? void 0 : _c.url)) {
                            throw new Error("Invalid file");
                        }
                        console.log((_e = (_d = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _d === void 0 ? void 0 : _d[0]) === null || _e === void 0 ? void 0 : _e.url, "ggggggggggg");
                        return [4 /*yield*/, (0, genericApis_1.getSheetData)((_g = (_f = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _f === void 0 ? void 0 : _f[0]) === null || _g === void 0 ? void 0 : _g.url, "Sheet1")];
                    case 3:
                        boundaryData = _h.sent();
                        return [4 /*yield*/, (0, index_1.appendSheetsToWorkbook)(boundaryData)];
                    case 4:
                        updatedWorkbook = _h.sent();
                        return [4 /*yield*/, (0, genericApis_1.createAndUploadFile)(updatedWorkbook, request)];
                    case 5:
                        boundaryDetails = _h.sent();
                        transformedResponse[0].fileStoreId = boundaryDetails[0].fileStoreId;
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { fileStoreIds: transformedResponse }, request)];
                    case 6: return [2 /*return*/, (0, index_1.sendResponse)(response, { fileStoreIds: transformedResponse }, request)];
                    case 7: return [3 /*break*/, 9];
                    case 8:
                        e_2 = _h.sent();
                        logger_1.logger.error(String(e_2));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_2) + "    Check Logs" }, request, response)];
                    case 9: return [2 /*return*/];
                }
            });
        }); };
        this.getBoundaryData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var boundarySheetData, BoundaryFileDetails, error_1;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, (0, genericApis_1.getBoundarySheetData)(request)];
                    case 1:
                        boundarySheetData = _a.sent();
                        return [4 /*yield*/, (0, genericApis_1.createAndUploadFile)(boundarySheetData === null || boundarySheetData === void 0 ? void 0 : boundarySheetData.wb, request)];
                    case 2:
                        BoundaryFileDetails = _a.sent();
                        return [2 /*return*/, BoundaryFileDetails];
                    case 3:
                        error_1 = _a.sent();
                        logger_1.logger.error(String(error_1));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(error_1) + "    Check Logs" }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.autoGenerateBoundaryCodes = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var fileResponse, boundaryData, _a, withBoundaryCode, withoutBoundaryCode, _b, mappingMap, countMap, childParentMap, boundaryMap_1, boundaryTypeMap, modifiedMap_1, boundaryDataForSheet, hierarchy, headers, data, boundarySheetData, BoundaryFileDetails, error_2;
            var _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p;
            return __generator(this, function (_q) {
                switch (_q.label) {
                    case 0:
                        _q.trys.push([0, 9, , 10]);
                        return [4 /*yield*/, (0, request_1.httpRequest)(config_1.default.host.filestore + config_1.default.paths.filestore + "/url", {}, { tenantId: (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.ResourceDetails) === null || _d === void 0 ? void 0 : _d.tenantId, fileStoreIds: (_f = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.ResourceDetails) === null || _f === void 0 ? void 0 : _f.fileStoreId }, "get")];
                    case 1:
                        fileResponse = _q.sent();
                        if (!((_h = (_g = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _g === void 0 ? void 0 : _g[0]) === null || _h === void 0 ? void 0 : _h.url)) {
                            throw new Error("Invalid file");
                        }
                        return [4 /*yield*/, (0, genericApis_1.getSheetData)((_k = (_j = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _j === void 0 ? void 0 : _j[0]) === null || _k === void 0 ? void 0 : _k.url, "Sheet1", false)];
                    case 2:
                        boundaryData = _q.sent();
                        _a = (0, index_1.modifyBoundaryData)(boundaryData), withBoundaryCode = _a[0], withoutBoundaryCode = _a[1];
                        _b = (0, index_1.getCodeMappingsOfExistingBoundaryCodes)(withBoundaryCode), mappingMap = _b.mappingMap, countMap = _b.countMap;
                        childParentMap = (0, index_1.getChildParentMap)(withoutBoundaryCode);
                        return [4 /*yield*/, (0, genericApis_1.getBoundaryCodesHandler)(withoutBoundaryCode, childParentMap, mappingMap, countMap)];
                    case 3:
                        boundaryMap_1 = _q.sent();
                        boundaryTypeMap = (0, index_1.getBoundaryTypeMap)(boundaryData, boundaryMap_1);
                        return [4 /*yield*/, (0, genericApis_1.createBoundaryEntities)(request, boundaryMap_1)];
                    case 4:
                        _q.sent();
                        modifiedMap_1 = new Map();
                        childParentMap.forEach(function (value, key) {
                            var modifiedKey = boundaryMap_1.get(key);
                            var modifiedValue = boundaryMap_1.get(value);
                            modifiedMap_1.set(modifiedKey, modifiedValue);
                        });
                        return [4 /*yield*/, (0, genericApis_1.createBoundaryRelationship)(request, boundaryTypeMap, modifiedMap_1)];
                    case 5:
                        _q.sent();
                        boundaryDataForSheet = (0, index_1.addBoundaryCodeToData)(withBoundaryCode, withoutBoundaryCode, boundaryMap_1);
                        return [4 /*yield*/, (0, genericApis_1.getHierarchy)(request, (_m = (_l = request === null || request === void 0 ? void 0 : request.body) === null || _l === void 0 ? void 0 : _l.ResourceDetails) === null || _m === void 0 ? void 0 : _m.tenantId, (_p = (_o = request === null || request === void 0 ? void 0 : request.body) === null || _o === void 0 ? void 0 : _o.ResourceDetails) === null || _p === void 0 ? void 0 : _p.hierarchyType)];
                    case 6:
                        hierarchy = _q.sent();
                        headers = __spreadArray(__spreadArray([], hierarchy, true), ["Boundary Code", "Target at the Selected Boundary level", "Start Date of Campaign (Optional Field)", "End Date of Campaign (Optional Field)"], false);
                        data = (0, index_1.prepareDataForExcel)(boundaryDataForSheet, hierarchy, boundaryMap_1);
                        return [4 /*yield*/, (0, genericApis_1.createExcelSheet)(data, headers)];
                    case 7:
                        boundarySheetData = _q.sent();
                        return [4 /*yield*/, (0, genericApis_1.createAndUploadFile)(boundarySheetData === null || boundarySheetData === void 0 ? void 0 : boundarySheetData.wb, request)];
                    case 8:
                        BoundaryFileDetails = _q.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { BoundaryFileDetails: BoundaryFileDetails }, request)];
                    case 9:
                        error_2 = _q.sent();
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(error_2) + "    Check Logs" }, request, response)];
                    case 10: return [2 /*return*/];
                }
            });
        }); };
        this.createData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var e_3;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 5, , 6]);
                        return [4 /*yield*/, (0, campaignValidators_1.validateCreateRequest)(request)];
                    case 1:
                        _b.sent();
                        return [4 /*yield*/, (0, campaignApis_1.processGenericRequest)(request)];
                    case 2:
                        _b.sent();
                        return [4 /*yield*/, (0, index_1.enrichResourceDetails)(request)];
                    case 3:
                        _b.sent();
                        return [4 /*yield*/, (0, index_1.generateProcessedFileAndPersist)(request)];
                    case 4:
                        _b.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { ResourceDetails: (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails }, request)];
                    case 5:
                        e_3 = _b.sent();
                        logger_1.logger.error(String(e_3));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_3) }, request, response)];
                    case 6: return [2 /*return*/];
                }
            });
        }); };
        this.searchData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var e_4;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, (0, campaignValidators_1.validateSearchRequest)(request)];
                    case 1:
                        _b.sent();
                        return [4 /*yield*/, (0, index_1.processDataSearchRequest)(request)];
                    case 2:
                        _b.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { ResourceDetails: (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails }, request)];
                    case 3:
                        e_4 = _b.sent();
                        logger_1.logger.error(String(e_4));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_4) }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.intializeRoutes();
    }
    // Initialize routes for MeasurementController
    dataManageController.prototype.intializeRoutes = function () {
        this.router.post("".concat(this.path, "/_generate"), this.generateData);
        this.router.post("".concat(this.path, "/_download"), this.downloadData);
        this.router.post("".concat(this.path, "/_getboundarysheet"), this.getBoundaryData);
        this.router.post("".concat(this.path, "/_autoGenerateBoundaryCode"), this.autoGenerateBoundaryCodes);
        this.router.post("".concat(this.path, "/_create"), this.createData);
        this.router.post("".concat(this.path, "/_search"), this.searchData);
    };
    return dataManageController;
}());
;
exports.default = dataManageController;
