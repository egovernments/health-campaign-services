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
var utils_1 = require("../../utils");
var form_data_1 = __importDefault(require("form-data"));
var index_1 = __importDefault(require("../../config/index"));
var XLSX = __importStar(require("xlsx"));
var logger_1 = require("../../utils/logger");
var index_2 = require("../../api/index");
var index_3 = require("../../utils/index");
var request_1 = require("../../utils/request");
// Define the MeasurementController class
var BulkUploadController = /** @class */ (function () {
    // Constructor to initialize routes
    function BulkUploadController() {
        var _this = this;
        // Define class properties
        this.path = "/bulk";
        this.router = express.Router();
        this.dayInMilliSecond = 86400000;
        // This function handles the HTTP request for retrieving all measurements.
        this.getTransformedData = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var _a, fileStoreId, startRow, endRow, transformTemplate, parsingTemplates, result, parseResult, TransformConfig, parsingConfig, url, updatedDatas, mdmsArray, _i, mdmsArray_1, mdmsElement, data, updatedData, e_1;
            var _b, _c, _d, _e, _f, _g, _h, _j, _k, _l;
            return __generator(this, function (_m) {
                switch (_m.label) {
                    case 0:
                        _m.trys.push([0, 9, , 10]);
                        _a = request.body, fileStoreId = _a.fileStoreId, startRow = _a.startRow, endRow = _a.endRow, transformTemplate = _a.transformTemplate, parsingTemplates = _a.parsingTemplates;
                        logger_1.logger.info("Transform Template :" + transformTemplate);
                        return [4 /*yield*/, (0, index_2.getTemplate)(transformTemplate, request.body.RequestInfo, response)];
                    case 1:
                        result = _m.sent();
                        return [4 /*yield*/, (0, index_2.getParsingTemplate)(parsingTemplates, request.body.RequestInfo, response)];
                    case 2:
                        parseResult = _m.sent();
                        if (((_b = result === null || result === void 0 ? void 0 : result.mdms) === null || _b === void 0 ? void 0 : _b.length) > 0) {
                            TransformConfig = result.mdms[0];
                        }
                        else {
                            logger_1.logger.info("No Transform Template found");
                            return [2 /*return*/, (0, index_3.errorResponder)({ message: "No Transform Template found " }, request, response)];
                        }
                        logger_1.logger.info("Transform Config : ", TransformConfig);
                        url = index_1.default.host.filestore + index_1.default.paths.filestore + "/url?tenantId=".concat((_e = (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.RequestInfo) === null || _d === void 0 ? void 0 : _d.userInfo) === null || _e === void 0 ? void 0 : _e.tenantId, "&fileStoreIds=").concat(fileStoreId);
                        logger_1.logger.info("File fetching url : " + url);
                        updatedDatas = [];
                        if (!(((_f = parseResult === null || parseResult === void 0 ? void 0 : parseResult.mdms) === null || _f === void 0 ? void 0 : _f.length) > 0)) return [3 /*break*/, 7];
                        mdmsArray = parseResult.mdms;
                        _i = 0, mdmsArray_1 = mdmsArray;
                        _m.label = 3;
                    case 3:
                        if (!(_i < mdmsArray_1.length)) return [3 /*break*/, 6];
                        mdmsElement = mdmsArray_1[_i];
                        parsingConfig = (_g = mdmsElement === null || mdmsElement === void 0 ? void 0 : mdmsElement.data) === null || _g === void 0 ? void 0 : _g.path;
                        return [4 /*yield*/, (0, index_2.getSheetData)(url, startRow, endRow, (_h = TransformConfig === null || TransformConfig === void 0 ? void 0 : TransformConfig.data) === null || _h === void 0 ? void 0 : _h.Fields, (_j = TransformConfig === null || TransformConfig === void 0 ? void 0 : TransformConfig.data) === null || _j === void 0 ? void 0 : _j.sheetName)];
                    case 4:
                        data = _m.sent();
                        // Check if data is an array before using map
                        if (Array.isArray(data)) {
                            updatedData = data.map(function (element) {
                                return (0, utils_1.convertObjectForMeasurment)(element, parsingConfig);
                            });
                            // Add updatedData to the array
                            updatedDatas.push(updatedData);
                        }
                        _m.label = 5;
                    case 5:
                        _i++;
                        return [3 /*break*/, 3];
                    case 6:
                        logger_1.logger.info("Updated Datas : " + updatedDatas);
                        // After processing all mdms elements, send the response
                        return [2 /*return*/, (0, index_3.sendResponse)(response, { updatedDatas: updatedDatas }, request)];
                    case 7: return [2 /*return*/, (0, index_3.errorResponder)({ message: "No Parsing Template found " }, request, response)];
                    case 8: return [3 /*break*/, 10];
                    case 9:
                        e_1 = _m.sent();
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: (_l = (_k = e_1 === null || e_1 === void 0 ? void 0 : e_1.response) === null || _k === void 0 ? void 0 : _k.data) === null || _l === void 0 ? void 0 : _l.Errors[0].message }, request, response)];
                    case 10: return [2 /*return*/];
                }
            });
        }); };
        this.getTransformedXlsx = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var result, hostHcmBff, e_2, datas, Job, _i, datas_1, data, simplifiedData, areKeysSame, ws, wb, buffer, formData, fileCreationResult, error_1, responseData, error_2, e_3, fileStoreId, tenantId;
            var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p;
            return __generator(this, function (_q) {
                switch (_q.label) {
                    case 0:
                        _q.trys.push([0, 16, , 17]);
                        _q.label = 1;
                    case 1:
                        _q.trys.push([1, 3, , 4]);
                        hostHcmBff = index_1.default.host.hcmBff.endsWith('/') ? index_1.default.host.hcmBff.slice(0, -1) : index_1.default.host.hcmBff;
                        return [4 /*yield*/, (0, request_1.httpRequest)("".concat(hostHcmBff).concat(index_1.default.app.contextPath).concat(this.path, "/_transform"), request.body, undefined, undefined, undefined, undefined)];
                    case 2:
                        result = _q.sent();
                        return [3 /*break*/, 4];
                    case 3:
                        e_2 = _q.sent();
                        logger_1.logger.error(String(e_2));
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: String(e_2) + "    Check Logs" }, request, response)];
                    case 4:
                        datas = result === null || result === void 0 ? void 0 : result.updatedDatas;
                        Job = { ingestionDetails: [] };
                        _i = 0, datas_1 = datas;
                        _q.label = 5;
                    case 5:
                        if (!(_i < datas_1.length)) return [3 /*break*/, 15];
                        data = datas_1[_i];
                        if (!Array.isArray(data)) return [3 /*break*/, 14];
                        simplifiedData = data.map(function (originalObject) {
                            // Initialize acc with an explicit type annotation
                            var acc = {};
                            // Extract key-value pairs where values are not arrays or objects
                            var simplifiedObject = Object.entries(originalObject).reduce(function (acc, _a) {
                                var key = _a[0], value = _a[1];
                                if (!Array.isArray(value) && typeof value !== 'object') {
                                    acc[key] = value;
                                }
                                return acc;
                            }, acc);
                            return simplifiedObject;
                        });
                        areKeysSame = simplifiedData.every(function (obj, index, array) {
                            return Object.keys(obj).length === Object.keys(array[0]).length &&
                                Object.keys(obj).every(function (key) { return Object.keys(array[0]).includes(key); });
                        });
                        if (!areKeysSame) return [3 /*break*/, 13];
                        ws = XLSX.utils.json_to_sheet(simplifiedData);
                        wb = XLSX.utils.book_new();
                        XLSX.utils.book_append_sheet(wb, ws, 'Sheet 1');
                        buffer = XLSX.write(wb, { bookType: 'xlsx', type: 'buffer' });
                        formData = new form_data_1.default();
                        formData.append('file', buffer, 'filename.xlsx');
                        formData.append('tenantId', (_c = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.RequestInfo) === null || _b === void 0 ? void 0 : _b.userInfo) === null || _c === void 0 ? void 0 : _c.tenantId);
                        formData.append('module', 'pgr');
                        _q.label = 6;
                    case 6:
                        _q.trys.push([6, 11, , 12]);
                        _q.label = 7;
                    case 7:
                        _q.trys.push([7, 9, , 10]);
                        logger_1.logger.info("File uploading url : " + index_1.default.host.filestore + index_1.default.paths.filestore);
                        return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.filestore + index_1.default.paths.filestore, formData, undefined, undefined, undefined, {
                                'Content-Type': 'multipart/form-data',
                                'auth-token': (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.RequestInfo) === null || _e === void 0 ? void 0 : _e.authToken
                            })];
                    case 8:
                        fileCreationResult = _q.sent();
                        return [3 /*break*/, 10];
                    case 9:
                        error_1 = _q.sent();
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: (_h = (_g = (_f = error_1 === null || error_1 === void 0 ? void 0 : error_1.response) === null || _f === void 0 ? void 0 : _f.data) === null || _g === void 0 ? void 0 : _g.Errors[0]) === null || _h === void 0 ? void 0 : _h.message }, request, response)];
                    case 10:
                        responseData = fileCreationResult === null || fileCreationResult === void 0 ? void 0 : fileCreationResult.files;
                        logger_1.logger.info("Response data after File Creation : " + JSON.stringify(responseData));
                        if (Array.isArray(responseData) && responseData.length > 0) {
                            Job.ingestionDetails.push({ id: responseData[0].fileStoreId, tenanId: responseData[0].tenantId, state: "not-started", type: "xlsx" });
                        }
                        return [3 /*break*/, 12];
                    case 11:
                        error_2 = _q.sent();
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: "Error in creating FileStoreId" }, request, response)];
                    case 12: return [3 /*break*/, 14];
                    case 13: return [2 /*return*/, (0, index_3.errorResponder)({ message: 'Keys are not the same' }, request, response)];
                    case 14:
                        _i++;
                        return [3 /*break*/, 5];
                    case 15: return [3 /*break*/, 17];
                    case 16:
                        e_3 = _q.sent();
                        return [2 /*return*/, (0, index_3.errorResponder)({ message: (_k = (_j = e_3 === null || e_3 === void 0 ? void 0 : e_3.response) === null || _j === void 0 ? void 0 : _j.data) === null || _k === void 0 ? void 0 : _k.Errors[0].message }, request, response)];
                    case 17:
                        fileStoreId = (_l = request === null || request === void 0 ? void 0 : request.body) === null || _l === void 0 ? void 0 : _l.fileStoreId;
                        tenantId = (_p = (_o = (_m = request === null || request === void 0 ? void 0 : request.body) === null || _m === void 0 ? void 0 : _m.RequestInfo) === null || _o === void 0 ? void 0 : _o.userInfo) === null || _p === void 0 ? void 0 : _p.tenantId;
                        Job.tenantId = tenantId;
                        (0, utils_1.produceIngestion)({ Job: Job }, fileStoreId, request.body.RequestInfo);
                        return [2 /*return*/, (0, index_3.sendResponse)(response, { Job: Job }, request)];
                }
            });
        }); };
        this.intializeRoutes();
    }
    // Initialize routes for MeasurementController
    BulkUploadController.prototype.intializeRoutes = function () {
        this.router.post("".concat(this.path, "/_transform"), this.getTransformedData);
        this.router.post("".concat(this.path, "/_getxlsx"), this.getTransformedXlsx);
    };
    return BulkUploadController;
}());
// Export the MeasurementController class
exports.default = BulkUploadController;
