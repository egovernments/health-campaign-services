"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
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
var __rest = (this && this.__rest) || function (s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
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
exports.appendSheetsToWorkbook = exports.processBasedOnAction = exports.getCodeMappingsOfExistingBoundaryCodes = exports.processDataSearchRequest = exports.searchProjectCampaignResourcData = exports.extractCodesFromBoundaryRelationshipResponse = exports.prepareDataForExcel = exports.addBoundaryCodeToData = exports.getBoundaryTypeMap = exports.getChildParentMap = exports.modifyBoundaryData = exports.generateProcessedFileAndPersist = exports.matchData = exports.convertToTypeData = exports.getDataFromSheet = exports.enrichAndSaveResourceDetails = exports.matchFacilityData = exports.getFacilityIds = exports.enrichResourceDetails = exports.convertToFacilityExsistingData = exports.processGenerate = exports.processGenerateRequest = exports.sortCampaignDetails = exports.correctParentValues = exports.modifyData = exports.fullProcessFlowForNewEntry = exports.getFinalUpdatedResponse = exports.getOldEntryResponse = exports.getNewEntryResponse = exports.getModifiedResponse = exports.getSchemaAndProcessResult = exports.getCreationDetails = exports.callSearchApi = exports.getResponseFromDb = exports.generateActivityMessage = exports.generateResourceMessage = exports.generateAuditDetails = exports.generateXlsxFromJson = exports.getCachedResponse = exports.cacheResponse = exports.appCache = exports.sendResponse = exports.throwError = exports.getResponseInfo = exports.invalidPathHandler = exports.errorLogger = exports.errorResponder = void 0;
var request_1 = require("../utils/request");
var index_1 = __importDefault(require("../config/index"));
var uuid_1 = require("uuid");
var Listener_1 = require("../Kafka/Listener");
var campaignApis_1 = require("../api/campaignApis");
var genericApis_1 = require("../api/genericApis");
var XLSX = __importStar(require("xlsx"));
var form_data_1 = __importDefault(require("form-data"));
var pg_1 = require("pg");
var logger_1 = require("./logger");
var dataManage_controller_1 = __importDefault(require("../controllers/dataManage/dataManage.controller"));
var createAndSearch_1 = __importDefault(require("../config/createAndSearch"));
var dbPoolConfig_1 = __importDefault(require("../config/dbPoolConfig"));
// import * as xlsx from 'xlsx-populate';
var NodeCache = require("node-cache");
var _ = require('lodash');
var updateGeneratedResourceTopic = index_1.default.KAFKA_UPDATE_GENERATED_RESOURCE_DETAILS_TOPIC;
var createGeneratedResourceTopic = index_1.default.KAFKA_CREATE_GENERATED_RESOURCE_DETAILS_TOPIC;
/*
  stdTTL: (default: 0) the standard ttl as number in seconds for every generated
   cache element. 0 = unlimited

  checkperiod: (default: 600) The period in seconds, as a number, used for the automatic
   delete check interval. 0 = no periodic check.

   30 mins caching
*/
var appCache = new NodeCache({ stdTTL: 1800000, checkperiod: 300 });
exports.appCache = appCache;
/*
Send The Error Response back to client with proper response code
*/
var throwError = function (message, code, status) {
    if (message === void 0) { message = "Internal Server Error"; }
    if (code === void 0) { code = "INTERNAL_SERVER_ERROR"; }
    if (status === void 0) { status = 500; }
    var error = new Error(message);
    //   error.status = status;
    //   error.code = code;
    logger_1.logger.error("Error : " + error);
    throw error;
};
exports.throwError = throwError;
/*
Error Object
*/
var getErrorResponse = function (code, message) {
    if (code === void 0) { code = "INTERNAL_SERVER_ERROR"; }
    if (message === void 0) { message = "Some Error Occured!!"; }
    return ({
        ResponseInfo: null,
        Errors: [
            {
                code: code,
                message: message,
                description: null,
                params: null,
            },
        ],
    });
};
/*
Send The Response back to client with proper response code and response info
*/
var sendResponse = function (response, responseBody, req, code) {
    if (code === void 0) { code = 200; }
    /* if (code != 304) {
      appCache.set(req.headers.cachekey, { ...responseBody });
    } else {
      logger.info("CACHED RESPONSE FOR :: " + req.headers.cachekey);
    }
    */
    response.status(code).send(__assign(__assign({}, getResponseInfo(code)), responseBody));
};
exports.sendResponse = sendResponse;
/*
Sets the cahce response
*/
var cacheResponse = function (res, key) {
    if (key != null) {
        appCache.set(key, __assign({}, res));
        logger_1.logger.info("CACHED RESPONSE FOR :: " + key);
    }
};
exports.cacheResponse = cacheResponse;
/*
gets the cahce response
*/
var getCachedResponse = function (key) {
    if (key != null) {
        var data = appCache.get(key);
        if (data) {
            logger_1.logger.info("CACHE STATUS :: " + JSON.stringify(appCache.getStats()));
            logger_1.logger.info("RETURNS THE CACHED RESPONSE FOR :: " + key);
            return data;
        }
    }
    return null;
};
exports.getCachedResponse = getCachedResponse;
/*
Response Object
*/
var getResponseInfo = function (code) { return ({
    ResponseInfo: {
        apiId: "egov-bff",
        ver: "0.0.1",
        ts: new Date().getTime(),
        status: "successful",
        desc: code == 304 ? "cached-response" : "new-response",
    },
}); };
exports.getResponseInfo = getResponseInfo;
/*
Fallback Middleware function for returning 404 error for undefined paths
*/
var invalidPathHandler = function (request, response, next) {
    response.status(404);
    response.send(getErrorResponse("INVALID_PATH", "invalid path"));
};
exports.invalidPathHandler = invalidPathHandler;
/*
Error handling Middleware function for logging the error message
*/
var errorLogger = function (error, request, response, next) {
    logger_1.logger.error(error.stack);
    logger_1.logger.error("error ".concat(error.message));
    next(error); // calling next middleware
};
exports.errorLogger = errorLogger;
/*
Error handling Middleware function reads the error message and sends back a response in JSON format
*/
var errorResponder = function (error, request, response, next) {
    if (next === void 0) { next = null; }
    response.header("Content-Type", "application/json");
    var status = 500;
    response
        .status(status)
        .send(getErrorResponse("INTERNAL_SERVER_ERROR", error === null || error === void 0 ? void 0 : error.message));
};
exports.errorResponder = errorResponder;
function generateXlsxFromJson(request, response, simplifiedData) {
    var _a, _b, _c, _d, _e;
    return __awaiter(this, void 0, void 0, function () {
        var ws, wb, buffer, formData, fileCreationResult, responseData, e_1, errorMessage;
        return __generator(this, function (_f) {
            switch (_f.label) {
                case 0:
                    _f.trys.push([0, 2, , 3]);
                    ws = XLSX.utils.json_to_sheet(simplifiedData);
                    wb = XLSX.utils.book_new();
                    XLSX.utils.book_append_sheet(wb, ws, 'Sheet 1');
                    buffer = XLSX.write(wb, { bookType: 'xlsx', type: 'buffer' });
                    formData = new form_data_1.default();
                    formData.append('file', buffer, 'filename.xlsx');
                    formData.append('tenantId', (_c = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.RequestInfo) === null || _b === void 0 ? void 0 : _b.userInfo) === null || _c === void 0 ? void 0 : _c.tenantId);
                    formData.append('module', 'pgr');
                    logger_1.logger.info("File uploading url : " + index_1.default.host.filestore + index_1.default.paths.filestore);
                    return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.filestore + index_1.default.paths.filestore, formData, undefined, undefined, undefined, {
                            'Content-Type': 'multipart/form-data',
                            'auth-token': (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.RequestInfo) === null || _e === void 0 ? void 0 : _e.authToken
                        })];
                case 1:
                    fileCreationResult = _f.sent();
                    responseData = fileCreationResult === null || fileCreationResult === void 0 ? void 0 : fileCreationResult.files;
                    logger_1.logger.info("Response data after File Creation : " + JSON.stringify(responseData));
                    return [2 /*return*/, responseData];
                case 2:
                    e_1 = _f.sent();
                    errorMessage = "Error occurred while fetching the file store ID: " + e_1.message;
                    logger_1.logger.error(errorMessage);
                    return [2 /*return*/, errorResponder({ message: errorMessage + "    Check Logs" }, request, response)];
                case 3: return [2 /*return*/];
            }
        });
    });
}
exports.generateXlsxFromJson = generateXlsxFromJson;
function generateActivityMessage(tenantId, requestBody, requestPayload, responsePayload, type, url, status) {
    var _a, _b, _c, _d;
    return __awaiter(this, void 0, void 0, function () {
        var activityMessage;
        return __generator(this, function (_e) {
            activityMessage = {
                id: (0, uuid_1.v4)(),
                status: status,
                retryCount: 0,
                tenantId: tenantId,
                type: type,
                url: url,
                requestPayload: requestPayload,
                responsePayload: responsePayload,
                auditDetails: {
                    createdBy: (_b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo) === null || _a === void 0 ? void 0 : _a.userInfo) === null || _b === void 0 ? void 0 : _b.uuid,
                    lastModifiedBy: (_d = (_c = requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo) === null || _c === void 0 ? void 0 : _c.userInfo) === null || _d === void 0 ? void 0 : _d.uuid,
                    createdTime: Date.now(),
                    lastModifiedTime: Date.now()
                },
                additionalDetails: {},
                resourceDetailsId: null
            };
            return [2 /*return*/, activityMessage];
        });
    });
}
exports.generateActivityMessage = generateActivityMessage;
function modifyAuditdetailsAndCases(responseData) {
    responseData.forEach(function (item) {
        item.auditDetails = {
            lastModifiedTime: item.lastmodifiedtime,
            createdTime: item.createdtime,
            lastModifiedBy: item.lastmodifiedby,
            createdBy: item.createdby
        };
        item.tenantId = item.tenantid;
        item.additionalDetails = item.additionaldetails;
        item.fileStoreid = item.filestoreid;
        delete item.additionaldetails;
        delete item.lastmodifiedtime;
        delete item.createdtime;
        delete item.lastmodifiedby;
        delete item.createdby;
        delete item.filestoreid;
        delete item.tenantid;
    });
}
function getResponseFromDb(request, response) {
    return __awaiter(this, void 0, void 0, function () {
        var pool, type, queryString, status_1, queryResult, responseData, error_1, error_2;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    pool = new pg_1.Pool({
                        user: index_1.default.DB_USER,
                        host: index_1.default.DB_HOST,
                        database: index_1.default.DB_NAME,
                        password: index_1.default.DB_PASSWORD,
                        port: parseInt(index_1.default.DB_PORT)
                    });
                    _a.label = 1;
                case 1:
                    _a.trys.push([1, 3, 4, 8]);
                    type = request.query.type;
                    queryString = "SELECT * FROM health.eg_cm_generated_resource_details WHERE type = $1 AND status = $2";
                    status_1 = 'Completed';
                    return [4 /*yield*/, pool.query(queryString, [type, status_1])];
                case 2:
                    queryResult = _a.sent();
                    responseData = queryResult.rows;
                    modifyAuditdetailsAndCases(responseData);
                    return [2 /*return*/, responseData];
                case 3:
                    error_1 = _a.sent();
                    logger_1.logger.error('Error fetching data from the database:', error_1);
                    throw error_1;
                case 4:
                    _a.trys.push([4, 6, , 7]);
                    return [4 /*yield*/, pool.end()];
                case 5:
                    _a.sent();
                    return [3 /*break*/, 7];
                case 6:
                    error_2 = _a.sent();
                    logger_1.logger.error('Error closing the database connection pool:', error_2);
                    return [3 /*break*/, 7];
                case 7: return [7 /*endfinally*/];
                case 8: return [2 /*return*/];
            }
        });
    });
}
exports.getResponseFromDb = getResponseFromDb;
function getModifiedResponse(responseData) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            return [2 /*return*/, responseData.map(function (item) {
                    return __assign(__assign({}, item), { count: parseInt(item.count), auditDetails: __assign(__assign({}, item.auditDetails), { lastModifiedTime: parseInt(item.auditDetails.lastModifiedTime), createdTime: parseInt(item.auditDetails.createdTime) }) });
                })];
        });
    });
}
exports.getModifiedResponse = getModifiedResponse;
function getNewEntryResponse(modifiedResponse, request) {
    var _a, _b, _c, _d, _e;
    return __awaiter(this, void 0, void 0, function () {
        var type, newEntry;
        return __generator(this, function (_f) {
            type = request.query.type;
            newEntry = {
                id: (0, uuid_1.v4)(),
                fileStoreid: null,
                type: type,
                status: "In Progress",
                tenantId: (_a = request === null || request === void 0 ? void 0 : request.query) === null || _a === void 0 ? void 0 : _a.tenantId,
                auditDetails: {
                    lastModifiedTime: Date.now(),
                    createdTime: Date.now(),
                    createdBy: (_c = (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.RequestInfo) === null || _c === void 0 ? void 0 : _c.userInfo.uuid,
                    lastModifiedBy: (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.RequestInfo) === null || _e === void 0 ? void 0 : _e.userInfo.uuid,
                },
                additionalDetails: {}
            };
            return [2 /*return*/, [newEntry]];
        });
    });
}
exports.getNewEntryResponse = getNewEntryResponse;
function getOldEntryResponse(modifiedResponse, request) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            return [2 /*return*/, modifiedResponse.map(function (item) {
                    var _a, _b, _c;
                    var newItem = __assign({}, item);
                    newItem.status = "expired";
                    newItem.auditDetails.lastModifiedTime = Date.now();
                    newItem.auditDetails.lastModifiedBy = (_c = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.RequestInfo) === null || _b === void 0 ? void 0 : _b.userInfo) === null || _c === void 0 ? void 0 : _c.uuid;
                    return newItem;
                })];
        });
    });
}
exports.getOldEntryResponse = getOldEntryResponse;
function getFinalUpdatedResponse(result, responseData, request) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            return [2 /*return*/, responseData.map(function (item) {
                    var _a, _b, _c, _d, _e, _f, _g;
                    return __assign(__assign({}, item), { tenantId: (_a = request === null || request === void 0 ? void 0 : request.query) === null || _a === void 0 ? void 0 : _a.tenantId, count: parseInt(((_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.generatedResourceCount) ? (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.generatedResourceCount : null), auditDetails: __assign(__assign({}, item.auditDetails), { lastModifiedTime: Date.now(), createdTime: Date.now(), lastModifiedBy: (_f = (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.RequestInfo) === null || _e === void 0 ? void 0 : _e.userInfo) === null || _f === void 0 ? void 0 : _f.uuid }), fileStoreid: (_g = result === null || result === void 0 ? void 0 : result[0]) === null || _g === void 0 ? void 0 : _g.fileStoreId, status: "Completed" });
                })];
        });
    });
}
exports.getFinalUpdatedResponse = getFinalUpdatedResponse;
function callSearchApi(request, response) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    return __awaiter(this, void 0, void 0, function () {
        var result, type, filter, requestBody, responseData, host, url, queryParams, _i, _j, searchItem, countknown, responseDatas_1, searchPath, fetchedData, responseObject, count, noOfTimesToFetchApi, i, e_2;
        return __generator(this, function (_k) {
            switch (_k.label) {
                case 0:
                    _k.trys.push([0, 10, , 11]);
                    result = void 0;
                    type = request.query.type;
                    return [4 /*yield*/, (0, genericApis_1.searchMDMS)([type], index_1.default.SEARCH_TEMPLATE, request.body.RequestInfo, response)];
                case 1:
                    result = _k.sent();
                    filter = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.Filters;
                    requestBody = { "RequestInfo": (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.RequestInfo, filter: filter };
                    responseData = (_d = (_c = result === null || result === void 0 ? void 0 : result.mdms) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.data;
                    if (!responseData || responseData.length === 0) {
                        return [2 /*return*/, errorResponder({ message: "Invalid ApiResource Type. Check Logs" }, request, response)];
                    }
                    host = responseData === null || responseData === void 0 ? void 0 : responseData.host;
                    url = (_e = responseData === null || responseData === void 0 ? void 0 : responseData.searchConfig) === null || _e === void 0 ? void 0 : _e.url;
                    queryParams = {};
                    for (_i = 0, _j = (_f = responseData === null || responseData === void 0 ? void 0 : responseData.searchConfig) === null || _f === void 0 ? void 0 : _f.searchBody; _i < _j.length; _i++) {
                        searchItem = _j[_i];
                        if (searchItem.isInParams) {
                            queryParams[searchItem.path] = searchItem.value;
                        }
                        else if (searchItem.isInBody) {
                            _.set(requestBody, "".concat(searchItem.path), searchItem.value);
                        }
                    }
                    countknown = ((_g = responseData === null || responseData === void 0 ? void 0 : responseData.searchConfig) === null || _g === void 0 ? void 0 : _g.isCountGiven) === true;
                    responseDatas_1 = [];
                    searchPath = (_h = responseData === null || responseData === void 0 ? void 0 : responseData.searchConfig) === null || _h === void 0 ? void 0 : _h.keyName;
                    fetchedData = void 0;
                    responseObject = void 0;
                    if (!countknown) return [3 /*break*/, 7];
                    return [4 /*yield*/, (0, genericApis_1.getCount)(responseData, request, response)];
                case 2:
                    count = _k.sent();
                    noOfTimesToFetchApi = Math.ceil(count / queryParams.limit);
                    i = 0;
                    _k.label = 3;
                case 3:
                    if (!(i < noOfTimesToFetchApi)) return [3 /*break*/, 6];
                    return [4 /*yield*/, (0, request_1.httpRequest)(host + url, requestBody, queryParams, undefined, undefined, undefined)];
                case 4:
                    responseObject = _k.sent();
                    fetchedData = _.get(responseObject, searchPath);
                    fetchedData.forEach(function (item) {
                        responseDatas_1.push(item);
                    });
                    queryParams.offset = (parseInt(queryParams.offset) + parseInt(queryParams.limit)).toString();
                    _k.label = 5;
                case 5:
                    i++;
                    return [3 /*break*/, 3];
                case 6: return [3 /*break*/, 9];
                case 7:
                    if (!true) return [3 /*break*/, 9];
                    return [4 /*yield*/, (0, request_1.httpRequest)(host + url, requestBody, queryParams, undefined, undefined, undefined)];
                case 8:
                    responseObject = _k.sent();
                    fetchedData = _.get(responseObject, searchPath);
                    fetchedData.forEach(function (item) {
                        responseDatas_1.push(item);
                    });
                    queryParams.offset = (parseInt(queryParams.offset) + parseInt(queryParams.limit)).toString();
                    if (fetchedData.length < parseInt(queryParams.limit)) {
                        return [3 /*break*/, 9];
                    }
                    return [3 /*break*/, 7];
                case 9: return [2 /*return*/, responseDatas_1];
                case 10:
                    e_2 = _k.sent();
                    logger_1.logger.error(String(e_2));
                    return [2 /*return*/, errorResponder({ message: String(e_2) + "    Check Logs" }, request, response)];
                case 11: return [2 /*return*/];
            }
        });
    });
}
exports.callSearchApi = callSearchApi;
function fullProcessFlowForNewEntry(newEntryResponse, request, response) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        var type, generatedResource, dataManagerController, result, finalResponse, generatedResourceNew, finalResponse, generatedResourceNew, responseDatas, modifiedDatas, result, finalResponse, generatedResourceNew, error_3;
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    _c.trys.push([0, 12, , 13]);
                    type = (_a = request === null || request === void 0 ? void 0 : request.query) === null || _a === void 0 ? void 0 : _a.type;
                    generatedResource = { generatedResource: newEntryResponse };
                    (0, Listener_1.produceModifiedMessages)(generatedResource, createGeneratedResourceTopic);
                    if (!(type === 'boundary')) return [3 /*break*/, 3];
                    dataManagerController = new dataManage_controller_1.default();
                    return [4 /*yield*/, dataManagerController.getBoundaryData(request, response)];
                case 1:
                    result = _c.sent();
                    return [4 /*yield*/, getFinalUpdatedResponse(result, newEntryResponse, request)];
                case 2:
                    finalResponse = _c.sent();
                    generatedResourceNew = { generatedResource: finalResponse };
                    (0, Listener_1.produceModifiedMessages)(generatedResourceNew, updateGeneratedResourceTopic);
                    request.body.generatedResource = finalResponse;
                    return [3 /*break*/, 11];
                case 3:
                    if (!(type == "facilityWithBoundary")) return [3 /*break*/, 6];
                    return [4 /*yield*/, processGenerateRequest(request)];
                case 4:
                    _c.sent();
                    return [4 /*yield*/, getFinalUpdatedResponse((_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.fileDetails, newEntryResponse, request)];
                case 5:
                    finalResponse = _c.sent();
                    generatedResourceNew = { generatedResource: finalResponse };
                    (0, Listener_1.produceModifiedMessages)(generatedResourceNew, updateGeneratedResourceTopic);
                    request.body.generatedResource = finalResponse;
                    return [3 /*break*/, 11];
                case 6: return [4 /*yield*/, callSearchApi(request, response)];
                case 7:
                    responseDatas = _c.sent();
                    return [4 /*yield*/, modifyData(request, response, responseDatas)];
                case 8:
                    modifiedDatas = _c.sent();
                    return [4 /*yield*/, generateXlsxFromJson(request, response, modifiedDatas)];
                case 9:
                    result = _c.sent();
                    return [4 /*yield*/, getFinalUpdatedResponse(result, newEntryResponse, request)];
                case 10:
                    finalResponse = _c.sent();
                    generatedResourceNew = { generatedResource: finalResponse };
                    (0, Listener_1.produceModifiedMessages)(generatedResourceNew, updateGeneratedResourceTopic);
                    _c.label = 11;
                case 11: return [3 /*break*/, 13];
                case 12:
                    error_3 = _c.sent();
                    throw error_3;
                case 13: return [2 /*return*/];
            }
        });
    });
}
exports.fullProcessFlowForNewEntry = fullProcessFlowForNewEntry;
function modifyData(request, response, responseDatas) {
    var _a, _b, _c;
    return __awaiter(this, void 0, void 0, function () {
        var result, hostHcmBff, type, modifiedParsingTemplate, batchSize, totalBatches, allUpdatedData, i, batchData, batchRequestBody, processResult, error_4, e_3;
        return __generator(this, function (_d) {
            switch (_d.label) {
                case 0:
                    _d.trys.push([0, 8, , 9]);
                    result = void 0;
                    hostHcmBff = index_1.default.host.projectFactoryBff.endsWith('/') ? index_1.default.host.projectFactoryBff.slice(0, -1) : index_1.default.host.projectFactoryBff;
                    type = request.query.type;
                    return [4 /*yield*/, (0, genericApis_1.searchMDMS)([type], index_1.default.SEARCH_TEMPLATE, request.body.RequestInfo, response)];
                case 1:
                    result = _d.sent();
                    modifiedParsingTemplate = (_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.mdms) === null || _a === void 0 ? void 0 : _a[0]) === null || _b === void 0 ? void 0 : _b.data) === null || _c === void 0 ? void 0 : _c.modificationParsingTemplateName;
                    if (!request.body.HCMConfig) {
                        request.body.HCMConfig = {};
                    }
                    batchSize = 50;
                    totalBatches = Math.ceil(responseDatas.length / batchSize);
                    allUpdatedData = [];
                    i = 0;
                    _d.label = 2;
                case 2:
                    if (!(i < totalBatches)) return [3 /*break*/, 7];
                    batchData = responseDatas.slice(i * batchSize, (i + 1) * batchSize);
                    batchRequestBody = __assign({}, request.body);
                    batchRequestBody.HCMConfig.parsingTemplate = modifiedParsingTemplate;
                    batchRequestBody.HCMConfig.data = batchData;
                    _d.label = 3;
                case 3:
                    _d.trys.push([3, 5, , 6]);
                    return [4 /*yield*/, (0, request_1.httpRequest)("".concat(hostHcmBff).concat(index_1.default.app.contextPath, "/bulk/_process"), batchRequestBody, undefined, undefined, undefined, undefined)];
                case 4:
                    processResult = _d.sent();
                    if (processResult.Error) {
                        throw new Error(processResult.Error);
                    }
                    allUpdatedData.push.apply(allUpdatedData, processResult.updatedDatas);
                    return [3 /*break*/, 6];
                case 5:
                    error_4 = _d.sent();
                    throw error_4;
                case 6:
                    i++;
                    return [3 /*break*/, 2];
                case 7: return [2 /*return*/, allUpdatedData];
                case 8:
                    e_3 = _d.sent();
                    throw e_3;
                case 9: return [2 /*return*/];
            }
        });
    });
}
exports.modifyData = modifyData;
function generateAuditDetails(request) {
    var _a, _b, _c, _d, _e, _f;
    var createdBy = (_c = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.RequestInfo) === null || _b === void 0 ? void 0 : _b.userInfo) === null || _c === void 0 ? void 0 : _c.uuid;
    var lastModifiedBy = (_f = (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.RequestInfo) === null || _e === void 0 ? void 0 : _e.userInfo) === null || _f === void 0 ? void 0 : _f.uuid;
    var auditDetails = {
        createdBy: createdBy,
        lastModifiedBy: lastModifiedBy,
        createdTime: Date.now(),
        lastModifiedTime: Date.now()
    };
    return auditDetails;
}
exports.generateAuditDetails = generateAuditDetails;
function addRowDetails(processResultUpdatedDatas, updatedDatas) {
    if (!processResultUpdatedDatas)
        return;
    processResultUpdatedDatas.forEach(function (item, index) {
        if (index < updatedDatas.length) {
            item['#row!number#'] = updatedDatas[index]['#row!number#'];
        }
    });
}
function sortCampaignDetails(campaignDetails) {
    campaignDetails.sort(function (a, b) {
        // If a is a child of b, a should come after b
        if (a.parentBoundaryCode === b.boundaryCode)
            return 1;
        // If b is a child of a, a should come before b
        if (a.boundaryCode === b.parentBoundaryCode)
            return -1;
        // Otherwise, maintain the order
        return 0;
    });
    return campaignDetails;
}
exports.sortCampaignDetails = sortCampaignDetails;
// Function to correct the totals and target values of parents
function correctParentValues(campaignDetails) {
    // Create a map to store parent-child relationships and their totals/targets
    var parentMap = {};
    campaignDetails.forEach(function (detail) {
        if (!detail.parentBoundaryCode)
            return; // Skip if it's not a child
        if (!parentMap[detail.parentBoundaryCode]) {
            parentMap[detail.parentBoundaryCode] = { total: 0, target: 0 };
        }
        parentMap[detail.parentBoundaryCode].total += detail.targets[0].total;
        parentMap[detail.parentBoundaryCode].target += detail.targets[0].target;
    });
    // Update parent values with the calculated totals and targets
    campaignDetails.forEach(function (detail) {
        if (!detail.parentBoundaryCode)
            return; // Skip if it's not a child
        var parent = parentMap[detail.parentBoundaryCode];
        var target = detail.targets[0];
        target.total = parent.total;
        target.target = parent.target;
    });
    return campaignDetails;
}
exports.correctParentValues = correctParentValues;
function createFacilitySheet(allFacilities) {
    return __awaiter(this, void 0, void 0, function () {
        var headers, facilities, facilitySheetData;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    headers = ["Facility Code", "Facility Name", "Facility Type", "Facility Status", "Facility Capacity", "Boundary Code"];
                    facilities = allFacilities.map(function (facility) {
                        return [
                            facility === null || facility === void 0 ? void 0 : facility.id,
                            facility === null || facility === void 0 ? void 0 : facility.name,
                            facility === null || facility === void 0 ? void 0 : facility.usage,
                            (facility === null || facility === void 0 ? void 0 : facility.isPermanent) ? "Perm" : "Temp",
                            facility === null || facility === void 0 ? void 0 : facility.storageCapacity,
                            ""
                        ];
                    });
                    logger_1.logger.info("facilities : " + JSON.stringify(facilities));
                    return [4 /*yield*/, (0, genericApis_1.createExcelSheet)(facilities, headers, "List of Available Facilities")];
                case 1:
                    facilitySheetData = _a.sent();
                    return [2 /*return*/, facilitySheetData];
            }
        });
    });
}
function createFacilityAndBoundaryFile(facilitySheetData, boundarySheetData, request) {
    return __awaiter(this, void 0, void 0, function () {
        var workbook, fileDetails;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    workbook = XLSX.utils.book_new();
                    // Add facility sheet to the workbook
                    XLSX.utils.book_append_sheet(workbook, facilitySheetData.ws, 'List of Available Facilities');
                    // Add boundary sheet to the workbook
                    XLSX.utils.book_append_sheet(workbook, boundarySheetData.ws, 'List of Campaign Boundaries');
                    return [4 /*yield*/, (0, genericApis_1.createAndUploadFile)(workbook, request)];
                case 1:
                    fileDetails = _a.sent();
                    request.body.fileDetails = fileDetails;
                    return [2 /*return*/];
            }
        });
    });
}
function generateFacilityAndBoundarySheet(tenantId, request) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var allFacilities, facilitySheetData, boundarySheetData;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0: return [4 /*yield*/, (0, campaignApis_1.getAllFacilities)(tenantId, request.body)];
                case 1:
                    allFacilities = _b.sent();
                    request.body.generatedResourceCount = allFacilities.length;
                    return [4 /*yield*/, createFacilitySheet(allFacilities)];
                case 2:
                    facilitySheetData = _b.sent();
                    request.body.Filters = { tenantId: tenantId, hierarchyType: (_a = request === null || request === void 0 ? void 0 : request.query) === null || _a === void 0 ? void 0 : _a.hierarchyType, includeChildren: true };
                    return [4 /*yield*/, (0, genericApis_1.getBoundarySheetData)(request)];
                case 3:
                    boundarySheetData = _b.sent();
                    return [4 /*yield*/, createFacilityAndBoundaryFile(facilitySheetData, boundarySheetData, request)];
                case 4:
                    _b.sent();
                    return [2 /*return*/];
            }
        });
    });
}
function processGenerateRequest(request) {
    return __awaiter(this, void 0, void 0, function () {
        var _a, type, tenantId;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    _a = request.query, type = _a.type, tenantId = _a.tenantId;
                    if (!(type == "facilityWithBoundary")) return [3 /*break*/, 2];
                    return [4 /*yield*/, generateFacilityAndBoundarySheet(String(tenantId), request)];
                case 1:
                    _b.sent();
                    _b.label = 2;
                case 2: return [2 /*return*/];
            }
        });
    });
}
exports.processGenerateRequest = processGenerateRequest;
function updateAndPersistGenerateRequest(newEntryResponse, oldEntryResponse, responseData, request, response) {
    return __awaiter(this, void 0, void 0, function () {
        var forceUpdate, forceUpdateBool, generatedResource;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    forceUpdate = request.query.forceUpdate;
                    forceUpdateBool = forceUpdate === 'true';
                    if (forceUpdateBool && responseData.length > 0) {
                        generatedResource = { generatedResource: oldEntryResponse };
                        (0, Listener_1.produceModifiedMessages)(generatedResource, updateGeneratedResourceTopic);
                        request.body.generatedResource = oldEntryResponse;
                    }
                    if (!(responseData.length === 0 || forceUpdateBool)) return [3 /*break*/, 2];
                    return [4 /*yield*/, fullProcessFlowForNewEntry(newEntryResponse, request, response)];
                case 1:
                    _a.sent();
                    return [3 /*break*/, 3];
                case 2:
                    request.body.generatedResource = responseData;
                    _a.label = 3;
                case 3: return [2 /*return*/];
            }
        });
    });
}
function processGenerate(request, response) {
    return __awaiter(this, void 0, void 0, function () {
        var responseData, modifiedResponse, newEntryResponse, oldEntryResponse;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, getResponseFromDb(request, response)];
                case 1:
                    responseData = _a.sent();
                    return [4 /*yield*/, getModifiedResponse(responseData)];
                case 2:
                    modifiedResponse = _a.sent();
                    return [4 /*yield*/, getNewEntryResponse(modifiedResponse, request)];
                case 3:
                    newEntryResponse = _a.sent();
                    return [4 /*yield*/, getOldEntryResponse(modifiedResponse, request)];
                case 4:
                    oldEntryResponse = _a.sent();
                    return [4 /*yield*/, updateAndPersistGenerateRequest(newEntryResponse, oldEntryResponse, responseData, request, response)];
                case 5:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
exports.processGenerate = processGenerate;
function enrichResourceDetails(request) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_j) {
            request.body.ResourceDetails.id = (0, uuid_1.v4)();
            if (((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails) === null || _b === void 0 ? void 0 : _b.action) == "create") {
                request.body.ResourceDetails.status = "data-accepted";
            }
            else {
                request.body.ResourceDetails.status = "data-validated";
            }
            request.body.ResourceDetails.auditDetails = {
                createdBy: (_e = (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.RequestInfo) === null || _d === void 0 ? void 0 : _d.userInfo) === null || _e === void 0 ? void 0 : _e.uuid,
                createdTime: Date.now(),
                lastModifiedBy: (_h = (_g = (_f = request === null || request === void 0 ? void 0 : request.body) === null || _f === void 0 ? void 0 : _f.RequestInfo) === null || _g === void 0 ? void 0 : _g.userInfo) === null || _h === void 0 ? void 0 : _h.uuid,
                lastModifiedTime: Date.now()
            };
            return [2 /*return*/];
        });
    });
}
exports.enrichResourceDetails = enrichResourceDetails;
function getFacilityIds(data) {
    return data.map(function (obj) { return obj["id"]; });
}
exports.getFacilityIds = getFacilityIds;
function matchFacilityData(data, searchedFacilities) {
    var _a;
    var _loop_1 = function (dataFacility) {
        var searchedFacility = searchedFacilities.find(function (facility) { return facility.id === dataFacility.id; });
        if (!searchedFacility) {
            throw new Error("Facility with ID \"".concat(dataFacility.id, "\" not found in searched facilities."));
        }
        if ((_a = index_1.default === null || index_1.default === void 0 ? void 0 : index_1.default.values) === null || _a === void 0 ? void 0 : _a.matchFacilityData) {
            var keys = Object.keys(dataFacility);
            for (var _b = 0, keys_1 = keys; _b < keys_1.length; _b++) {
                var key = keys_1[_b];
                if (searchedFacility.hasOwnProperty(key) && searchedFacility[key] !== dataFacility[key]) {
                    throw new Error("Value mismatch for key \"".concat(key, "\" at index ").concat(dataFacility.originalIndex, ". Expected: \"").concat(dataFacility[key], "\", Found: \"").concat(searchedFacility[key], "\""));
                }
            }
        }
    };
    for (var _i = 0, data_1 = data; _i < data_1.length; _i++) {
        var dataFacility = data_1[_i];
        _loop_1(dataFacility);
    }
}
exports.matchFacilityData = matchFacilityData;
function matchData(request, datas, searchedDatas, createAndSearchConfig) {
    var _a, _b;
    var uid = createAndSearchConfig.uniqueIdentifier;
    var errors = [];
    var _loop_2 = function (data) {
        var searchData = searchedDatas.find(function (searchedData) { return searchedData[uid] == data[uid]; });
        if (!searchData) {
            errors.push({ status: "INVALID", rowNumber: data["!row#number!"], errorDetails: "Data with ".concat(uid, " ").concat(data[uid], " not found in searched data.") });
        }
        else if (createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.matchEachKey) {
            var keys = Object.keys(data);
            errorString = "";
            errorFound = false;
            for (var _c = 0, keys_2 = keys; _c < keys_2.length; _c++) {
                var key = keys_2[_c];
                if (searchData.hasOwnProperty(key) && searchData[key] !== data[key] && key != "!row#number!") {
                    errorString += "Value mismatch for key \"".concat(key, "\" at index ").concat(data["!row#number!"] - 1, ". Expected: \"").concat(data[key], "\", Found: \"").concat(searchData[key], "\"");
                    errorFound = true;
                }
            }
            if (errorFound) {
                errors.push({ status: "MISMATCHING", rowNumber: data["!row#number!"], errorDetails: errorString });
            }
            else {
                errors.push({ status: "VALID", rowNumber: data["!row#number!"], errorDetails: "" });
            }
        }
        else {
            errors.push({ status: "VALID", rowNumber: data["!row#number!"], errorDetails: "" });
        }
    };
    var errorString, errorFound;
    for (var _i = 0, datas_1 = datas; _i < datas_1.length; _i++) {
        var data = datas_1[_i];
        _loop_2(data);
    }
    request.body.sheetErrorDetails = ((_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.sheetErrorDetails) ? __spreadArray(__spreadArray([], (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.sheetErrorDetails, true), errors, true) : errors;
}
exports.matchData = matchData;
function modifyBoundaryData(boundaryData) {
    var withBoundaryCode = [];
    var withoutBoundaryCode = [];
    for (var _i = 0, boundaryData_1 = boundaryData; _i < boundaryData_1.length; _i++) {
        var obj = boundaryData_1[_i];
        var row = [];
        if (typeof obj === 'object' && obj !== null) {
            for (var _a = 0, _b = Object.values(obj); _a < _b.length; _a++) {
                var value = _b[_a];
                if (value !== null && value !== undefined) {
                    row.push(value.toString()); // Convert value to string and push to row
                }
            }
            if (obj.hasOwnProperty('Boundary Code')) {
                withBoundaryCode.push(row);
            }
            else {
                withoutBoundaryCode.push(row);
            }
        }
    }
    return [withBoundaryCode, withoutBoundaryCode];
}
exports.modifyBoundaryData = modifyBoundaryData;
function enrichAndSaveResourceDetails(requestBody) {
    var _a, _b, _c, _d, _e;
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_f) {
            if (!((_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.ResourceDetails) === null || _a === void 0 ? void 0 : _a.status)) {
                requestBody.ResourceDetails.status = "data-accepted";
            }
            if (!((_b = requestBody === null || requestBody === void 0 ? void 0 : requestBody.ResourceDetails) === null || _b === void 0 ? void 0 : _b.processedFileStoreId)) {
                requestBody.ResourceDetails.processedFileStoreId = null;
            }
            requestBody.ResourceDetails.id = (0, uuid_1.v4)();
            requestBody.ResourceDetails.auditDetails = {
                createdTime: Date.now(),
                createdBy: (_c = requestBody.RequestInfo.userInfo) === null || _c === void 0 ? void 0 : _c.uuid,
                lastModifiedTime: Date.now(),
                lastModifiedBy: (_d = requestBody.RequestInfo.userInfo) === null || _d === void 0 ? void 0 : _d.uuid
            };
            requestBody.ResourceDetails.additionalDetails = __assign(__assign({}, requestBody.ResourceDetails.additionalDetails), { atttemptedData: (_e = requestBody === null || requestBody === void 0 ? void 0 : requestBody.ResourceDetails) === null || _e === void 0 ? void 0 : _e.dataToCreate });
            delete requestBody.ResourceDetails.dataToCreate;
            (0, Listener_1.produceModifiedMessages)(requestBody, index_1.default.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
            return [2 /*return*/];
        });
    });
}
exports.enrichAndSaveResourceDetails = enrichAndSaveResourceDetails;
function getDataFromSheet(fileStoreId, tenantId, createAndSearchConfig) {
    var _a, _b, _c, _d, _e;
    return __awaiter(this, void 0, void 0, function () {
        var fileResponse;
        return __generator(this, function (_f) {
            switch (_f.label) {
                case 0: return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.filestore + index_1.default.paths.filestore + "/url", {}, { tenantId: tenantId, fileStoreIds: fileStoreId }, "get")];
                case 1:
                    fileResponse = _f.sent();
                    if (!((_b = (_a = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _a === void 0 ? void 0 : _a[0]) === null || _b === void 0 ? void 0 : _b.url)) {
                        throw new Error("Not any download url returned for given fileStoreId");
                    }
                    return [4 /*yield*/, (0, genericApis_1.getSheetData)((_d = (_c = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.url, (_e = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.parseArrayConfig) === null || _e === void 0 ? void 0 : _e.sheetName, true)];
                case 2: return [2 /*return*/, _f.sent()];
            }
        });
    });
}
exports.getDataFromSheet = getDataFromSheet;
function updateRange(range, desiredSheet) {
    var maxColumnIndex = 0;
    // Iterate through each row to find the last column with data
    for (var row = range.s.r; row <= range.e.r; row++) {
        for (var col = range.s.c; col <= range.e.c; col++) {
            var cellAddress = XLSX.utils.encode_cell({ r: row, c: col });
            if (desiredSheet[cellAddress]) {
                maxColumnIndex = Math.max(maxColumnIndex, col);
            }
        }
    }
    // Update the end column of the range with the maximum column index found
    range.e.c = maxColumnIndex;
}
function findColumns(desiredSheet) {
    var range = XLSX.utils.decode_range(desiredSheet['!ref']);
    // Check if the status column already exists in the first row
    var statusColumn;
    for (var col = range.s.c; col <= range.e.c; col++) {
        var cellAddress = XLSX.utils.encode_cell({ r: range.s.r, c: col });
        if (desiredSheet[cellAddress] && desiredSheet[cellAddress].v === '#status#') {
            statusColumn = String.fromCharCode(65 + col);
            for (var row = range.s.r; row <= range.e.r; row++) {
                var cellAddress_1 = XLSX.utils.encode_cell({ r: row, c: statusColumn.charCodeAt(0) - 65 });
                delete desiredSheet[cellAddress_1];
            }
            break;
        }
    }
    // Check if the errorDetails column already exists in the first row
    var errorDetailsColumn;
    for (var col = range.s.c; col <= range.e.c; col++) {
        var cellAddress = XLSX.utils.encode_cell({ r: range.s.r, c: col });
        if (desiredSheet[cellAddress] && desiredSheet[cellAddress].v === '#errorDetails#') {
            errorDetailsColumn = String.fromCharCode(65 + col);
            for (var row = range.s.r; row <= range.e.r; row++) {
                var cellAddress_2 = XLSX.utils.encode_cell({ r: row, c: errorDetailsColumn.charCodeAt(0) - 65 });
                delete desiredSheet[cellAddress_2];
            }
            break;
        }
    }
    updateRange(range, desiredSheet);
    logger_1.logger.info("Updated Range : " + JSON.stringify(range));
    // If the status column doesn't exist, calculate the next available column
    var emptyColumnIndex = range.e.c + 1;
    statusColumn = String.fromCharCode(65 + emptyColumnIndex);
    desiredSheet[statusColumn + '1'] = { v: '#status#', t: 's', r: '<t xml:space="preserve">#status#</t>', h: '#status#', w: '#status#' };
    // Calculate errorDetails column one column to the right of status column
    errorDetailsColumn = String.fromCharCode(statusColumn.charCodeAt(0) + 1);
    desiredSheet[errorDetailsColumn + '1'] = { v: '#errorDetails#', t: 's', r: '<t xml:space="preserve">#errorDetails#</t>', h: '#errorDetails#', w: '#errorDetails#' };
    return { statusColumn: statusColumn, errorDetailsColumn: errorDetailsColumn };
}
function processErrorData(request, createAndSearchConfig, workbook, sheetName) {
    var desiredSheet = workbook.Sheets[sheetName];
    var columns = findColumns(desiredSheet);
    var statusColumn = columns.statusColumn;
    var errorDetailsColumn = columns.errorDetailsColumn;
    var errorData = request.body.sheetErrorDetails;
    errorData.forEach(function (error) {
        var rowIndex = error.rowNumber;
        if (error.isUniqueIdentifier) {
            var uniqueIdentifierCell = createAndSearchConfig.uniqueIdentifierColumn + (rowIndex + 1);
            desiredSheet[uniqueIdentifierCell] = { v: error.uniqueIdentifier, t: 's', r: '<t xml:space="preserve">#uniqueIdentifier#</t>', h: error.uniqueIdentifier, w: error.uniqueIdentifier };
        }
        var statusCell = statusColumn + (rowIndex + 1);
        var errorDetailsCell = errorDetailsColumn + (rowIndex + 1);
        desiredSheet[statusCell] = { v: error.status, t: 's', r: '<t xml:space="preserve">#status#</t>', h: error.status, w: error.status };
        desiredSheet[errorDetailsCell] = { v: error.errorDetails, t: 's', r: '<t xml:space="preserve">#errorDetails#</t>', h: error.errorDetails, w: error.errorDetails };
    });
    desiredSheet['!ref'] = desiredSheet['!ref'].replace(/:[A-Z]+/, ':' + errorDetailsColumn);
    workbook.Sheets[sheetName] = desiredSheet;
}
function updateStatusFile(request) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o;
    return __awaiter(this, void 0, void 0, function () {
        var fileStoreId, tenantId, createAndSearchConfig, fileResponse, headers, fileUrl, sheetName, responseFile, workbook, responseData;
        return __generator(this, function (_p) {
            switch (_p.label) {
                case 0:
                    fileStoreId = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails) === null || _b === void 0 ? void 0 : _b.fileStoreId;
                    tenantId = (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.ResourceDetails) === null || _d === void 0 ? void 0 : _d.tenantId;
                    createAndSearchConfig = createAndSearch_1.default[(_f = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.ResourceDetails) === null || _f === void 0 ? void 0 : _f.type];
                    return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.filestore + index_1.default.paths.filestore + "/url", {}, { tenantId: tenantId, fileStoreIds: fileStoreId }, "get")];
                case 1:
                    fileResponse = _p.sent();
                    if (!((_h = (_g = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _g === void 0 ? void 0 : _g[0]) === null || _h === void 0 ? void 0 : _h.url)) {
                        throw new Error("No download URL returned for the given fileStoreId");
                    }
                    headers = {
                        'Content-Type': 'application/json',
                        Accept: 'application/pdf',
                    };
                    fileUrl = (_k = (_j = fileResponse === null || fileResponse === void 0 ? void 0 : fileResponse.fileStoreIds) === null || _j === void 0 ? void 0 : _j[0]) === null || _k === void 0 ? void 0 : _k.url;
                    sheetName = (_l = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.parseArrayConfig) === null || _l === void 0 ? void 0 : _l.sheetName;
                    return [4 /*yield*/, (0, request_1.httpRequest)(fileUrl, null, {}, 'get', 'arraybuffer', headers)];
                case 2:
                    responseFile = _p.sent();
                    workbook = XLSX.read(responseFile, { type: 'buffer' });
                    // Check if the specified sheet exists in the workbook
                    if (!workbook.Sheets.hasOwnProperty(sheetName)) {
                        throw new Error("Sheet with name \"".concat(sheetName, "\" is not present in the file."));
                    }
                    processErrorData(request, createAndSearchConfig, workbook, sheetName);
                    return [4 /*yield*/, (0, genericApis_1.createAndUploadFile)(workbook, request)];
                case 3:
                    responseData = _p.sent();
                    logger_1.logger.info('File updated successfully:' + JSON.stringify(responseData));
                    if ((_m = responseData === null || responseData === void 0 ? void 0 : responseData[0]) === null || _m === void 0 ? void 0 : _m.fileStoreId) {
                        request.body.ResourceDetails.processedFileStoreId = (_o = responseData === null || responseData === void 0 ? void 0 : responseData[0]) === null || _o === void 0 ? void 0 : _o.fileStoreId;
                    }
                    else {
                        throw new Error("Error in Creatring Status File");
                    }
                    return [2 /*return*/];
            }
        });
    });
}
function convertToType(dataToSet, type) {
    switch (type) {
        case "string":
            return String(dataToSet);
        case "number":
            return Number(dataToSet);
        case "boolean":
            // Convert to boolean assuming any truthy value should be true and falsy should be false
            return Boolean(dataToSet);
        // Add more cases if needed for other types
        default:
            // If type is not recognized, keep dataToSet as it is
            return dataToSet;
    }
}
function setTenantId(resultantElement, requestBody, createAndSearchConfig) {
    var _a, _b, _c, _d, _e;
    if ((_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.parseArrayConfig) === null || _a === void 0 ? void 0 : _a.tenantId) {
        var tenantId = _.get(requestBody, (_c = (_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.parseArrayConfig) === null || _b === void 0 ? void 0 : _b.tenantId) === null || _c === void 0 ? void 0 : _c.getValueViaPath);
        _.set(resultantElement, (_e = (_d = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.parseArrayConfig) === null || _d === void 0 ? void 0 : _d.tenantId) === null || _e === void 0 ? void 0 : _e.resultantPath, tenantId);
    }
}
function processData(dataFromSheet, createAndSearchConfig) {
    var _a;
    var parseLogic = (_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.parseArrayConfig) === null || _a === void 0 ? void 0 : _a.parseLogic;
    var requiresToSearchFromSheet = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.requiresToSearchFromSheet;
    var createData = [], searchData = [];
    for (var _i = 0, dataFromSheet_1 = dataFromSheet; _i < dataFromSheet_1.length; _i++) {
        var data = dataFromSheet_1[_i];
        var resultantElement = {};
        for (var _b = 0, parseLogic_1 = parseLogic; _b < parseLogic_1.length; _b++) {
            var element = parseLogic_1[_b];
            var dataToSet = _.get(data, element.sheetColumnName);
            if (element.conversionCondition) {
                dataToSet = element.conversionCondition[dataToSet];
            }
            if (element.type) {
                dataToSet = convertToType(dataToSet, element.type);
            }
            _.set(resultantElement, element.resultantPath, dataToSet);
        }
        resultantElement["!row#number!"] = data["!row#number!"];
        var addToCreate = true;
        for (var _c = 0, requiresToSearchFromSheet_1 = requiresToSearchFromSheet; _c < requiresToSearchFromSheet_1.length; _c++) {
            var key = requiresToSearchFromSheet_1[_c];
            if (data[key.sheetColumnName]) {
                searchData.push(resultantElement);
                addToCreate = false;
                break;
            }
        }
        if (addToCreate) {
            createData.push(resultantElement);
        }
    }
    return { searchData: searchData, createData: createData };
}
function setTenantIdAndSegregate(processedData, createAndSearchConfig, requestBody) {
    for (var _i = 0, _a = processedData.createData; _i < _a.length; _i++) {
        var resultantElement = _a[_i];
        setTenantId(resultantElement, requestBody, createAndSearchConfig);
    }
    for (var _b = 0, _c = processedData.searchData; _b < _c.length; _b++) {
        var resultantElement = _c[_b];
        setTenantId(resultantElement, requestBody, createAndSearchConfig);
    }
    return processedData;
}
// Original function divided into two parts
function convertToTypeData(dataFromSheet, createAndSearchConfig, requestBody) {
    var processedData = processData(dataFromSheet, createAndSearchConfig);
    return setTenantIdAndSegregate(processedData, createAndSearchConfig, requestBody);
}
exports.convertToTypeData = convertToTypeData;
function updateActivityResourceId(request) {
    var _a, _b, _c, _d, _e;
    if (((_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.Activities) && Array.isArray((_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.Activities)) {
        for (var _i = 0, _f = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.Activities; _i < _f.length; _i++) {
            var activity = _f[_i];
            activity.resourceDetailsId = (_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.ResourceDetails) === null || _e === void 0 ? void 0 : _e.id;
        }
    }
}
function generateProcessedFileAndPersist(request) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0: return [4 /*yield*/, updateStatusFile(request)];
                case 1:
                    _c.sent();
                    updateActivityResourceId(request);
                    logger_1.logger.info("ResourceDetails to persist : " + JSON.stringify((_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails));
                    logger_1.logger.info("Activities to persist : " + JSON.stringify((_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.Activities));
                    (0, Listener_1.produceModifiedMessages)(request === null || request === void 0 ? void 0 : request.body, index_1.default.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC);
                    return [4 /*yield*/, new Promise(function (resolve) { return setTimeout(resolve, 2000); })];
                case 2:
                    _c.sent();
                    (0, Listener_1.produceModifiedMessages)(request === null || request === void 0 ? void 0 : request.body, index_1.default.KAFKA_CREATE_RESOURCE_ACTIVITY_TOPIC);
                    return [2 /*return*/];
            }
        });
    });
}
exports.generateProcessedFileAndPersist = generateProcessedFileAndPersist;
function getRootBoundaryCode(boundaries) {
    for (var _i = 0, boundaries_1 = boundaries; _i < boundaries_1.length; _i++) {
        var boundary = boundaries_1[_i];
        if (boundary.isRoot) {
            return boundary.code;
        }
    }
    return "";
}
function enrichRootProjectId(requestBody) {
    var _a, _b, _c;
    var rootBoundary;
    for (var _i = 0, _d = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.CampaignDetails) === null || _a === void 0 ? void 0 : _a.boundaries; _i < _d.length; _i++) {
        var boundary = _d[_i];
        if (boundary === null || boundary === void 0 ? void 0 : boundary.isRoot) {
            rootBoundary = boundary === null || boundary === void 0 ? void 0 : boundary.code;
            break;
        }
    }
    if (rootBoundary) {
        requestBody.CampaignDetails.projectId = (_c = (_b = requestBody === null || requestBody === void 0 ? void 0 : requestBody.boundaryProjectMapping) === null || _b === void 0 ? void 0 : _b[rootBoundary]) === null || _c === void 0 ? void 0 : _c.projectId;
    }
}
function enrichAndPersistProjectCampaignRequest(request) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r;
    return __awaiter(this, void 0, void 0, function () {
        var action, _s;
        return __generator(this, function (_t) {
            switch (_t.label) {
                case 0:
                    action = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails) === null || _b === void 0 ? void 0 : _b.action;
                    _s = request.body.CampaignDetails;
                    return [4 /*yield*/, (0, genericApis_1.getCampaignNumber)(request.body, "CMP-[cy:yyyy-MM-dd]-[SEQ_EG_CMP_ID]", "campaign.number", (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.CampaignDetails) === null || _d === void 0 ? void 0 : _d.tenantId)];
                case 1:
                    _s.campaignNumber = _t.sent();
                    request.body.CampaignDetails.campaignDetails = { deliveryRules: (_f = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.CampaignDetails) === null || _f === void 0 ? void 0 : _f.deliveryRules, startDate: (_h = (_g = request === null || request === void 0 ? void 0 : request.body) === null || _g === void 0 ? void 0 : _g.CampaignDetails) === null || _h === void 0 ? void 0 : _h.startDate, endDate: (_k = (_j = request === null || request === void 0 ? void 0 : request.body) === null || _j === void 0 ? void 0 : _j.CampaignDetails) === null || _k === void 0 ? void 0 : _k.endDate };
                    request.body.CampaignDetails.status = action == "create" ? "started" : "drafted";
                    request.body.CampaignDetails.boundaryCode = getRootBoundaryCode(request.body.CampaignDetails.boundaries);
                    request.body.CampaignDetails.auditDetails = {
                        createdBy: (_o = (_m = (_l = request === null || request === void 0 ? void 0 : request.body) === null || _l === void 0 ? void 0 : _l.RequestInfo) === null || _m === void 0 ? void 0 : _m.userInfo) === null || _o === void 0 ? void 0 : _o.uuid,
                        createdTime: Date.now(),
                        lastModifiedBy: (_r = (_q = (_p = request === null || request === void 0 ? void 0 : request.body) === null || _p === void 0 ? void 0 : _p.RequestInfo) === null || _q === void 0 ? void 0 : _q.userInfo) === null || _r === void 0 ? void 0 : _r.uuid,
                        lastModifiedTime: Date.now(),
                    };
                    if (action == "create") {
                        enrichRootProjectId(request.body);
                    }
                    else {
                        request.body.CampaignDetails.projectId = null;
                    }
                    (0, Listener_1.produceModifiedMessages)(request === null || request === void 0 ? void 0 : request.body, index_1.default.KAFKA_SAVE_PROJECT_CAMPAIGN_DETAILS_TOPIC);
                    delete request.body.CampaignDetails.campaignDetails;
                    return [2 /*return*/];
            }
        });
    });
}
function getChildParentMap(modifiedBoundaryData) {
    var childParentMap = new Map();
    for (var i = 0; i < modifiedBoundaryData.length; i++) {
        var row = modifiedBoundaryData[i];
        for (var j = row.length - 1; j > 0; j--) {
            var child = row[j];
            var parent_1 = row[j - 1]; // Parent is the element to the immediate left
            childParentMap.set(child, parent_1);
        }
    }
    return childParentMap;
}
exports.getChildParentMap = getChildParentMap;
function getCodeMappingsOfExistingBoundaryCodes(withBoundaryCode) {
    console.log(withBoundaryCode, "withhhhhhhhhhhhhhhhhh");
    var countMap = new Map();
    var mappingMap = new Map();
    withBoundaryCode.forEach(function (row) {
        var len = row.length;
        if (len >= 3) {
            var grandParent = row[len - 3];
            if (mappingMap.has(grandParent)) {
                countMap.set(grandParent, (countMap.get(grandParent) || 0) + 1);
            }
            else {
                throw new Error("Insert boundary hierarchy level wise");
            }
        }
        mappingMap.set(row[len - 2], row[len - 1]);
        console.log(mappingMap, "mapppppp");
    });
    return { mappingMap: mappingMap, countMap: countMap };
}
exports.getCodeMappingsOfExistingBoundaryCodes = getCodeMappingsOfExistingBoundaryCodes;
function getBoundaryTypeMap(boundaryData, boundaryMap) {
    var boundaryTypeMap = {};
    boundaryData.forEach(function (boundary) {
        Object.entries(boundary).forEach(function (_a) {
            var key = _a[0], value = _a[1];
            if (typeof value === 'string' && key !== 'Boundary Code') {
                var boundaryCode = boundaryMap.get(value);
                if (boundaryCode !== undefined) {
                    boundaryTypeMap[boundaryCode] = key;
                }
            }
        });
    });
    return boundaryTypeMap;
}
exports.getBoundaryTypeMap = getBoundaryTypeMap;
function addBoundaryCodeToData(withBoundaryCode, withoutBoundaryCode, boundaryMap) {
    var boundaryDataWithBoundaryCode = withBoundaryCode;
    var boundaryDataForWithoutBoundaryCode = withoutBoundaryCode.map(function (row) {
        var boundaryName = row[row.length - 1]; // Get the last element of the row
        var boundaryCode = boundaryMap.get(boundaryName); // Fetch corresponding boundary code from map
        return __spreadArray(__spreadArray([], row, true), [boundaryCode], false); // Append boundary code to the row and return updated row
    });
    var boundaryDataForSheet = __spreadArray(__spreadArray([], boundaryDataWithBoundaryCode, true), boundaryDataForWithoutBoundaryCode, true);
    return boundaryDataForSheet;
}
exports.addBoundaryCodeToData = addBoundaryCodeToData;
function prepareDataForExcel(boundaryDataForSheet, hierarchy, boundaryMap) {
    var data = boundaryDataForSheet.map(function (boundary) {
        var boundaryCode = boundary.pop();
        var rowData = boundary.concat(Array(Math.max(0, hierarchy.length - boundary.length)).fill(''));
        var boundaryCodeIndex = hierarchy.length;
        rowData[boundaryCodeIndex] = boundaryCode;
        return rowData;
    });
    return data;
}
exports.prepareDataForExcel = prepareDataForExcel;
function extractCodesFromBoundaryRelationshipResponse(boundaries) {
    var codes = new Set();
    for (var _i = 0, boundaries_2 = boundaries; _i < boundaries_2.length; _i++) {
        var boundary = boundaries_2[_i];
        codes.add(boundary.code); // Add code to the Set
        if (boundary.children && boundary.children.length > 0) {
            var childCodes = extractCodesFromBoundaryRelationshipResponse(boundary.children); // Recursively get child codes
            childCodes.forEach(function (code) { return codes.add(code); }); // Add child codes to the Set
        }
    }
    return codes;
}
exports.extractCodesFromBoundaryRelationshipResponse = extractCodesFromBoundaryRelationshipResponse;
function searchProjectCampaignResourcData(request) {
    return __awaiter(this, void 0, void 0, function () {
        var CampaignDetails, tenantId, pagination, ids, searchFields, queryData, responseData;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    CampaignDetails = request.body.CampaignDetails;
                    tenantId = CampaignDetails.tenantId, pagination = CampaignDetails.pagination, ids = CampaignDetails.ids, searchFields = __rest(CampaignDetails, ["tenantId", "pagination", "ids"]);
                    queryData = buildSearchQuery(tenantId, pagination, ids, searchFields);
                    return [4 /*yield*/, executeSearchQuery(queryData.query, queryData.values)];
                case 1:
                    responseData = _a.sent();
                    request.body.CampaignDetails = responseData;
                    return [2 /*return*/];
            }
        });
    });
}
exports.searchProjectCampaignResourcData = searchProjectCampaignResourcData;
function buildSearchQuery(tenantId, pagination, ids, searchFields) {
    var conditions = [];
    var values = [tenantId];
    var index = 2;
    for (var field in searchFields) {
        if (searchFields[field] !== undefined) {
            conditions.push("".concat(field, " = $").concat(index));
            values.push(searchFields[field]);
            index++;
        }
    }
    var query = "\n      SELECT *\n      FROM health.eg_cm_campaign_details\n      WHERE tenantId = $1\n  ";
    if (ids && ids.length > 0) {
        var idParams = ids.map(function (id, i) { return "$".concat(index + i); });
        query += " AND id IN (".concat(idParams.join(', '), ")");
        values.push.apply(values, ids);
    }
    if (conditions.length > 0) {
        query += " AND ".concat(conditions.join(' AND '));
    }
    if (pagination) {
        query += '\n';
        if (pagination.sortBy) {
            query += "ORDER BY ".concat(pagination.sortBy);
            if (pagination.sortOrder) {
                query += " ".concat(pagination.sortOrder.toUpperCase());
            }
            query += '\n';
        }
        if (pagination.limit !== undefined) {
            query += "LIMIT ".concat(pagination.limit);
            if (pagination.offset !== undefined) {
                query += " OFFSET ".concat(pagination.offset);
            }
            query += '\n';
        }
    }
    return { query: query, values: values };
}
function executeSearchQuery(query, values) {
    return __awaiter(this, void 0, void 0, function () {
        var queryResult;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, dbPoolConfig_1.default.query(query, values)];
                case 1:
                    queryResult = _a.sent();
                    return [2 /*return*/, queryResult.rows.map(function (row) { return ({
                            id: row.id,
                            tenantId: row.tenantid,
                            status: row.status,
                            action: row.action,
                            campaignNumber: row.campaignnumber,
                            campaignName: row.campaignname,
                            projectType: row.projecttype,
                            hierarchyType: row.hierarchytype,
                            boundaryCode: row.boundarycode,
                            projectId: row.projectid,
                            createdBy: row.createdby,
                            lastModifiedBy: row.lastmodifiedby,
                            createdTime: Number(row === null || row === void 0 ? void 0 : row.createdtime),
                            lastModifiedTime: row.lastmodifiedtime ? Number(row.lastmodifiedtime) : null,
                            additionalDetails: row.additionaldetails,
                            campaignDetails: row.campaigndetails
                        }); })];
            }
        });
    });
}
function processDataSearchRequest(request) {
    return __awaiter(this, void 0, void 0, function () {
        var SearchCriteria, query, queryResult, results;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    SearchCriteria = request.body.SearchCriteria;
                    query = buildWhereClauseForDataSearch(SearchCriteria);
                    return [4 /*yield*/, dbPoolConfig_1.default.query(query.query, query.values)];
                case 1:
                    queryResult = _a.sent();
                    results = queryResult.rows.map(function (row) { return ({
                        id: row.id,
                        tenantId: row.tenantid,
                        status: row.status,
                        action: row.action,
                        fileStoreId: row.filestoreid,
                        processedFilestoreId: row.processedfilestoreid,
                        type: row.type,
                        createdBy: row.createdby,
                        lastModifiedBy: row.lastmodifiedby,
                        createdTime: Number(row === null || row === void 0 ? void 0 : row.createdtime),
                        lastModifiedTime: row.lastmodifiedtime ? Number(row.lastmodifiedtime) : null,
                        additionalDetails: row.additionaldetails
                    }); });
                    request.body.ResourceDetails = results;
                    return [2 /*return*/];
            }
        });
    });
}
exports.processDataSearchRequest = processDataSearchRequest;
function buildWhereClauseForDataSearch(SearchCriteria) {
    var id = SearchCriteria.id, tenantId = SearchCriteria.tenantId, type = SearchCriteria.type, status = SearchCriteria.status;
    var conditions = [];
    var values = [];
    if (id && id.length > 0) {
        conditions.push("id = ANY($".concat(values.length + 1, ")"));
        values.push(id);
    }
    if (tenantId) {
        conditions.push("tenantId = $".concat(values.length + 1));
        values.push(tenantId);
    }
    if (type) {
        conditions.push("type = $".concat(values.length + 1));
        values.push(type);
    }
    if (status) {
        conditions.push("status = $".concat(values.length + 1));
        values.push(status);
    }
    var whereClause = conditions.length > 0 ? "WHERE ".concat(conditions.join(' AND ')) : '';
    return {
        query: "\n  SELECT *\n  FROM health.eg_cm_resource_details\n  ".concat(whereClause, ";"),
        values: values
    };
}
function processBoundary(boundary, boundaryCodes, boundaries, request, parent) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l;
    return __awaiter(this, void 0, void 0, function () {
        var params, boundaryResponse, _i, _m, childBoundary;
        return __generator(this, function (_o) {
            switch (_o.label) {
                case 0:
                    if (!boundaryCodes.has(boundary.code)) {
                        boundaries.push({ code: boundary === null || boundary === void 0 ? void 0 : boundary.code, type: boundary === null || boundary === void 0 ? void 0 : boundary.boundaryType });
                        boundaryCodes.add(boundary === null || boundary === void 0 ? void 0 : boundary.code);
                    }
                    if (!((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.boundaryProjectMapping) === null || _b === void 0 ? void 0 : _b[boundary === null || boundary === void 0 ? void 0 : boundary.code])) {
                        request.body.boundaryProjectMapping[boundary === null || boundary === void 0 ? void 0 : boundary.code] = {
                            parent: parent ? parent : null,
                            projectId: null
                        };
                    }
                    else {
                        request.body.boundaryProjectMapping[boundary === null || boundary === void 0 ? void 0 : boundary.code].parent = parent;
                    }
                    if (!(boundary === null || boundary === void 0 ? void 0 : boundary.includeAllChildren)) return [3 /*break*/, 5];
                    params = {
                        tenantId: (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.CampaignDetails) === null || _d === void 0 ? void 0 : _d.tenantId,
                        codes: boundary === null || boundary === void 0 ? void 0 : boundary.code,
                        hierarchyType: (_f = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.CampaignDetails) === null || _f === void 0 ? void 0 : _f.hierarchyType,
                        includeChildren: true
                    };
                    logger_1.logger.info("Boundary relationship search url : " + index_1.default.host.boundaryHost + index_1.default.paths.boundaryRelationship);
                    logger_1.logger.info("Boundary relationship search params : " + JSON.stringify(params));
                    return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.boundaryHost + index_1.default.paths.boundaryRelationship, request.body, params)];
                case 1:
                    boundaryResponse = _o.sent();
                    if (!((_g = boundaryResponse === null || boundaryResponse === void 0 ? void 0 : boundaryResponse.TenantBoundary) === null || _g === void 0 ? void 0 : _g[0])) return [3 /*break*/, 5];
                    logger_1.logger.info("Boundary found " + JSON.stringify((_j = (_h = boundaryResponse === null || boundaryResponse === void 0 ? void 0 : boundaryResponse.TenantBoundary) === null || _h === void 0 ? void 0 : _h[0]) === null || _j === void 0 ? void 0 : _j.boundary));
                    _i = 0, _m = (_l = (_k = boundaryResponse.TenantBoundary[0]) === null || _k === void 0 ? void 0 : _k.boundary) === null || _l === void 0 ? void 0 : _l[0].children;
                    _o.label = 2;
                case 2:
                    if (!(_i < _m.length)) return [3 /*break*/, 5];
                    childBoundary = _m[_i];
                    return [4 /*yield*/, processBoundary(childBoundary, boundaryCodes, boundaries, request, boundary === null || boundary === void 0 ? void 0 : boundary.code)];
                case 3:
                    _o.sent();
                    _o.label = 4;
                case 4:
                    _i++;
                    return [3 /*break*/, 2];
                case 5: return [2 /*return*/];
            }
        });
    });
}
function addBoundaries(request) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var boundaries, boundaryCodes, _i, boundaries_3, boundary;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    boundaries = ((_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails).boundaries;
                    boundaryCodes = new Set(boundaries.map(function (boundary) { return boundary.code; }));
                    _i = 0, boundaries_3 = boundaries;
                    _b.label = 1;
                case 1:
                    if (!(_i < boundaries_3.length)) return [3 /*break*/, 4];
                    boundary = boundaries_3[_i];
                    return [4 /*yield*/, processBoundary(boundary, boundaryCodes, boundaries, request)];
                case 2:
                    _b.sent();
                    _b.label = 3;
                case 3:
                    _i++;
                    return [3 /*break*/, 1];
                case 4: return [2 /*return*/];
            }
        });
    });
}
function reorderBoundariesWithParentFirst(reorderedBoundaries, boundaryProjectMapping) {
    var _a;
    // Function to get the index of a boundary in the reordered boundaries array
    function getIndex(code) {
        return reorderedBoundaries.findIndex(function (boundary) { return boundary.code === code; });
    }
    // Reorder boundaries so that parents come first
    for (var _i = 0, reorderedBoundaries_1 = reorderedBoundaries; _i < reorderedBoundaries_1.length; _i++) {
        var boundary = reorderedBoundaries_1[_i];
        var parentCode = (_a = boundaryProjectMapping[boundary.code]) === null || _a === void 0 ? void 0 : _a.parent;
        if (parentCode) {
            var parentIndex = getIndex(parentCode);
            var boundaryIndex = getIndex(boundary.code);
            if (parentIndex !== -1 && boundaryIndex !== -1 && parentIndex > boundaryIndex) {
                // Move the boundary to be right after its parent
                reorderedBoundaries.splice(parentIndex + 1, 0, reorderedBoundaries.splice(boundaryIndex, 1)[0]);
            }
        }
    }
    return reorderedBoundaries;
}
// TODO: FIX THIS FUNCTION...NOT REORDERING CORRECTLY
function reorderBoundaries(request) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_j) {
            switch (_j.label) {
                case 0:
                    request.body.boundaryProjectMapping = {};
                    return [4 /*yield*/, addBoundaries(request)];
                case 1:
                    _j.sent();
                    logger_1.logger.info("Boundaries after addition " + JSON.stringify((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails) === null || _b === void 0 ? void 0 : _b.boundaries));
                    console.log("Boundary Project Mapping " + JSON.stringify((_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.boundaryProjectMapping));
                    reorderBoundariesWithParentFirst((_e = (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.CampaignDetails) === null || _e === void 0 ? void 0 : _e.boundaries, (_f = request === null || request === void 0 ? void 0 : request.body) === null || _f === void 0 ? void 0 : _f.boundaryProjectMapping);
                    logger_1.logger.info("Reordered Boundaries " + JSON.stringify((_h = (_g = request === null || request === void 0 ? void 0 : request.body) === null || _g === void 0 ? void 0 : _g.CampaignDetails) === null || _h === void 0 ? void 0 : _h.boundaries));
                    return [2 /*return*/];
            }
        });
    });
}
function createProject(request) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o;
    return __awaiter(this, void 0, void 0, function () {
        var _p, tenantId, boundaries, projectType, startDate, endDate, Projects, projectCreateBody, _i, boundaries_4, boundary, parent_2;
        return __generator(this, function (_q) {
            switch (_q.label) {
                case 0:
                    _p = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails, tenantId = _p.tenantId, boundaries = _p.boundaries, projectType = _p.projectType, startDate = _p.startDate, endDate = _p.endDate;
                    Projects = [{
                            tenantId: tenantId,
                            projectType: projectType,
                            startDate: startDate,
                            endDate: endDate,
                            "projectSubType": "Campaign",
                            "department": "Campaign",
                            "description": "Campaign ",
                        }];
                    projectCreateBody = {
                        RequestInfo: (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.RequestInfo,
                        Projects: Projects
                    };
                    return [4 /*yield*/, reorderBoundaries(request)];
                case 1:
                    _q.sent();
                    _i = 0, boundaries_4 = boundaries;
                    _q.label = 2;
                case 2:
                    if (!(_i < boundaries_4.length)) return [3 /*break*/, 6];
                    boundary = boundaries_4[_i];
                    Projects[0].address = { tenantId: tenantId, boundary: boundary === null || boundary === void 0 ? void 0 : boundary.code, boundaryType: boundary === null || boundary === void 0 ? void 0 : boundary.type };
                    if ((_e = (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.boundaryProjectMapping) === null || _d === void 0 ? void 0 : _d[boundary === null || boundary === void 0 ? void 0 : boundary.code]) === null || _e === void 0 ? void 0 : _e.parent) {
                        parent_2 = (_h = (_g = (_f = request === null || request === void 0 ? void 0 : request.body) === null || _f === void 0 ? void 0 : _f.boundaryProjectMapping) === null || _g === void 0 ? void 0 : _g[boundary === null || boundary === void 0 ? void 0 : boundary.code]) === null || _h === void 0 ? void 0 : _h.parent;
                        Projects[0].parent = (_l = (_k = (_j = request === null || request === void 0 ? void 0 : request.body) === null || _j === void 0 ? void 0 : _j.boundaryProjectMapping) === null || _k === void 0 ? void 0 : _k[parent_2]) === null || _l === void 0 ? void 0 : _l.projectId;
                    }
                    else {
                        Projects[0].parent = null;
                    }
                    Projects[0].referenceID = (_o = (_m = request === null || request === void 0 ? void 0 : request.body) === null || _m === void 0 ? void 0 : _m.CampaignDetails) === null || _o === void 0 ? void 0 : _o.id;
                    return [4 /*yield*/, (0, campaignApis_1.projectCreate)(projectCreateBody, request)];
                case 3:
                    _q.sent();
                    return [4 /*yield*/, new Promise(function (resolve) { return setTimeout(resolve, 3000); })];
                case 4:
                    _q.sent();
                    _q.label = 5;
                case 5:
                    _i++;
                    return [3 /*break*/, 2];
                case 6: return [2 /*return*/];
            }
        });
    });
}
function processBasedOnAction(request) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    request.body.CampaignDetails.id = (0, uuid_1.v4)();
                    if (!(((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails) === null || _b === void 0 ? void 0 : _b.action) == "create")) return [3 /*break*/, 4];
                    return [4 /*yield*/, (0, campaignApis_1.createProjectCampaignResourcData)(request)];
                case 1:
                    _c.sent();
                    return [4 /*yield*/, createProject(request)];
                case 2:
                    _c.sent();
                    return [4 /*yield*/, enrichAndPersistProjectCampaignRequest(request)];
                case 3:
                    _c.sent();
                    return [3 /*break*/, 6];
                case 4: return [4 /*yield*/, enrichAndPersistProjectCampaignRequest(request)];
                case 5:
                    _c.sent();
                    _c.label = 6;
                case 6: return [2 /*return*/];
            }
        });
    });
}
exports.processBasedOnAction = processBasedOnAction;
function appendSheetsToWorkbook(boundaryData) {
    return __awaiter(this, void 0, void 0, function () {
        var uniqueDistricts, uniqueDistrictsForMainSheet, workbook, mainSheetData, headersForMainSheet, _i, boundaryData_2, data, rowData, districtIndex, districtLevelRow, mainSheet, _a, boundaryData_3, item, _loop_3, _b, uniqueDistricts_1, district;
        return __generator(this, function (_c) {
            try {
                uniqueDistricts = [];
                uniqueDistrictsForMainSheet = [];
                workbook = XLSX.utils.book_new();
                mainSheetData = [];
                headersForMainSheet = Object.keys(boundaryData[0]);
                mainSheetData.push(headersForMainSheet);
                for (_i = 0, boundaryData_2 = boundaryData; _i < boundaryData_2.length; _i++) {
                    data = boundaryData_2[_i];
                    rowData = Object.values(data);
                    districtIndex = rowData.indexOf(data.District);
                    districtLevelRow = rowData.slice(0, districtIndex + 1);
                    if (!uniqueDistrictsForMainSheet.includes(districtLevelRow.join('_'))) {
                        uniqueDistrictsForMainSheet.push(districtLevelRow.join('_'));
                        mainSheetData.push(rowData);
                    }
                }
                mainSheet = XLSX.utils.aoa_to_sheet(mainSheetData);
                XLSX.utils.book_append_sheet(workbook, mainSheet, 'Sheet1');
                for (_a = 0, boundaryData_3 = boundaryData; _a < boundaryData_3.length; _a++) {
                    item = boundaryData_3[_a];
                    if (item.District && !uniqueDistricts.includes(item.District)) {
                        uniqueDistricts.push(item.District);
                    }
                }
                _loop_3 = function (district) {
                    var districtDataFiltered = boundaryData.filter(function (item) { return item.District === district; });
                    var districtIndex = Object.keys(districtDataFiltered[0]).indexOf('District');
                    var headers = Object.keys(districtDataFiltered[0]).slice(districtIndex);
                    var newSheetData = [headers];
                    for (var _d = 0, districtDataFiltered_1 = districtDataFiltered; _d < districtDataFiltered_1.length; _d++) {
                        var data = districtDataFiltered_1[_d];
                        var rowData = Object.values(data).slice(districtIndex).map(function (value) { return value === null ? '' : String(value); }); // Replace null with empty string
                        newSheetData.push(rowData);
                    }
                    var ws = XLSX.utils.aoa_to_sheet(newSheetData);
                    XLSX.utils.book_append_sheet(workbook, ws, district);
                };
                for (_b = 0, uniqueDistricts_1 = uniqueDistricts; _b < uniqueDistricts_1.length; _b++) {
                    district = uniqueDistricts_1[_b];
                    _loop_3(district);
                }
                return [2 /*return*/, workbook];
            }
            catch (error) {
                throw Error("An error occurred while appending sheets:");
            }
            return [2 /*return*/];
        });
    });
}
exports.appendSheetsToWorkbook = appendSheetsToWorkbook;
