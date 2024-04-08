"use strict";
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
exports.projectCreate = exports.processCreate = exports.createProjectCampaignResourcData = exports.processGenericRequest = exports.changeBodyViaElements = exports.getParamsViaElements = exports.confirmCreation = exports.getFacilitiesViaIds = exports.getAllFacilities = exports.enrichCampaign = void 0;
var config_1 = __importDefault(require("../config"));
var uuid_1 = require("uuid");
var request_1 = require("../utils/request");
var logger_1 = require("../utils/logger");
var createAndSearch_1 = __importDefault(require("../config/createAndSearch"));
var index_1 = require("../utils/index");
var campaignValidators_1 = require("../utils/validators/campaignValidators");
var genericApis_1 = require("./genericApis");
// import { json } from 'body-parser';
var _ = require('lodash');
function enrichCampaign(requestBody) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        var _c, _i, _d, campaignDetails;
        return __generator(this, function (_e) {
            switch (_e.label) {
                case 0:
                    if (!(requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign)) return [3 /*break*/, 2];
                    requestBody.Campaign.id = (0, uuid_1.v4)();
                    _c = requestBody.Campaign;
                    return [4 /*yield*/, (0, genericApis_1.getCampaignNumber)(requestBody, config_1.default.values.idgen.format, config_1.default.values.idgen.idName, (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.tenantId)];
                case 1:
                    _c.campaignNo = _e.sent();
                    for (_i = 0, _d = (_b = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _b === void 0 ? void 0 : _b.CampaignDetails; _i < _d.length; _i++) {
                        campaignDetails = _d[_i];
                        campaignDetails.id = (0, uuid_1.v4)();
                    }
                    _e.label = 2;
                case 2: return [2 /*return*/];
            }
        });
    });
}
exports.enrichCampaign = enrichCampaign;
function getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody) {
    return __awaiter(this, void 0, void 0, function () {
        var response;
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0: return [4 /*yield*/, new Promise(function (resolve) { return setTimeout(resolve, 3000); })];
                case 1:
                    _a.sent(); // Wait for 3 seconds
                    logger_1.logger.info("facilitySearchParams : " + JSON.stringify(facilitySearchParams));
                    return [4 /*yield*/, (0, request_1.httpRequest)(config_1.default.host.facilityHost + config_1.default.paths.facilitySearch, facilitySearchBody, facilitySearchParams)];
                case 2:
                    response = _a.sent();
                    if (Array.isArray(response === null || response === void 0 ? void 0 : response.Facilities)) {
                        searchedFacilities.push.apply(searchedFacilities, response === null || response === void 0 ? void 0 : response.Facilities);
                        return [2 /*return*/, response.Facilities.length >= 50]; // Return true if there are more facilities to fetch, false otherwise
                    }
                    else {
                        throw new Error("Search failed for Facility. Check Logs");
                    }
                    return [2 /*return*/];
            }
        });
    });
}
function getAllFacilities(tenantId, requestBody) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var facilitySearchBody, facilitySearchParams, searchedFacilities, searchAgain;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    facilitySearchBody = {
                        RequestInfo: requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo,
                        Facility: { isPermanent: true }
                    };
                    facilitySearchParams = {
                        limit: 50,
                        offset: 0,
                        tenantId: (_a = tenantId === null || tenantId === void 0 ? void 0 : tenantId.split('.')) === null || _a === void 0 ? void 0 : _a[0]
                    };
                    logger_1.logger.info("Facility search url : " + config_1.default.host.facilityHost + config_1.default.paths.facilitySearch);
                    logger_1.logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
                    searchedFacilities = [];
                    searchAgain = true;
                    _b.label = 1;
                case 1:
                    if (!searchAgain) return [3 /*break*/, 3];
                    return [4 /*yield*/, getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody)];
                case 2:
                    searchAgain = _b.sent();
                    facilitySearchParams.offset += 50;
                    return [3 /*break*/, 1];
                case 3: return [2 /*return*/, searchedFacilities];
            }
        });
    });
}
exports.getAllFacilities = getAllFacilities;
function getFacilitiesViaIds(tenantId, ids, requestBody) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var facilitySearchBody, facilitySearchParams, searchedFacilities, i, chunkIds;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    facilitySearchBody = {
                        RequestInfo: requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo,
                        Facility: {}
                    };
                    facilitySearchParams = {
                        limit: 50,
                        offset: 0,
                        tenantId: (_a = tenantId === null || tenantId === void 0 ? void 0 : tenantId.split('.')) === null || _a === void 0 ? void 0 : _a[0]
                    };
                    logger_1.logger.info("Facility search url : " + config_1.default.host.facilityHost + config_1.default.paths.facilitySearch);
                    searchedFacilities = [];
                    i = 0;
                    _b.label = 1;
                case 1:
                    if (!(i < ids.length)) return [3 /*break*/, 4];
                    chunkIds = ids.slice(i, i + 50);
                    facilitySearchBody.Facility.id = chunkIds;
                    logger_1.logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
                    return [4 /*yield*/, getAllFacilitiesInLoop(searchedFacilities, facilitySearchParams, facilitySearchBody)];
                case 2:
                    _b.sent();
                    _b.label = 3;
                case 3:
                    i += 50;
                    return [3 /*break*/, 1];
                case 4: return [2 /*return*/, searchedFacilities];
            }
        });
    });
}
exports.getFacilitiesViaIds = getFacilitiesViaIds;
function getParamsViaElements(elements, request) {
    var params = {};
    if (!elements) {
        return params;
    }
    for (var _i = 0, elements_1 = elements; _i < elements_1.length; _i++) {
        var element = elements_1[_i];
        if (element === null || element === void 0 ? void 0 : element.isInParams) {
            if (element === null || element === void 0 ? void 0 : element.value) {
                _.set(params, element === null || element === void 0 ? void 0 : element.keyPath, element === null || element === void 0 ? void 0 : element.value);
            }
            else if (element === null || element === void 0 ? void 0 : element.getValueViaPath) {
                _.set(params, element === null || element === void 0 ? void 0 : element.keyPath, _.get(request.body, element === null || element === void 0 ? void 0 : element.getValueViaPath));
            }
        }
    }
    return params;
}
exports.getParamsViaElements = getParamsViaElements;
function changeBodyViaElements(elements, request) {
    if (!elements) {
        return;
    }
    for (var _i = 0, elements_2 = elements; _i < elements_2.length; _i++) {
        var element = elements_2[_i];
        if (element === null || element === void 0 ? void 0 : element.isInBody) {
            if (element === null || element === void 0 ? void 0 : element.value) {
                _.set(request.body, element === null || element === void 0 ? void 0 : element.keyPath, element === null || element === void 0 ? void 0 : element.value);
            }
            else if (element === null || element === void 0 ? void 0 : element.getValueViaPath) {
                _.set(request.body, element === null || element === void 0 ? void 0 : element.keyPath, _.get(request.body, element === null || element === void 0 ? void 0 : element.getValueViaPath));
            }
            else {
                _.set(request.body, element === null || element === void 0 ? void 0 : element.keyPath, {});
            }
        }
    }
}
exports.changeBodyViaElements = changeBodyViaElements;
function changeBodyViaSearchFromSheet(elements, request, dataFromSheet) {
    if (!elements) {
        return;
    }
    for (var _i = 0, elements_3 = elements; _i < elements_3.length; _i++) {
        var element = elements_3[_i];
        var arrayToSearch = [];
        for (var _a = 0, dataFromSheet_1 = dataFromSheet; _a < dataFromSheet_1.length; _a++) {
            var data = dataFromSheet_1[_a];
            if (data[element.sheetColumnName]) {
                arrayToSearch.push(data[element.sheetColumnName]);
            }
        }
        _.set(request.body, element === null || element === void 0 ? void 0 : element.searchPath, arrayToSearch);
    }
}
function updateErrors(newCreatedData, newSearchedData, errors, createAndSearchConfig, activity) {
    newCreatedData.forEach(function (createdElement) {
        var foundMatch = false;
        for (var _i = 0, newSearchedData_1 = newSearchedData; _i < newSearchedData_1.length; _i++) {
            var searchedElement = newSearchedData_1[_i];
            var match = true;
            for (var key in createdElement) {
                if (createdElement.hasOwnProperty(key) && !searchedElement.hasOwnProperty(key) && key != '!row#number!') {
                    match = false;
                    break;
                }
                if (createdElement[key] !== searchedElement[key] && key != '!row#number!') {
                    match = false;
                    break;
                }
            }
            if (match) {
                foundMatch = true;
                newSearchedData.splice(newSearchedData.indexOf(searchedElement), 1);
                errors.push({ status: "PERSISTED", rowNumber: createdElement["!row#number!"], isUniqueIdentifier: true, uniqueIdentifier: searchedElement[createAndSearchConfig.uniqueIdentifier], errorDetails: "" });
                break;
            }
        }
        if (!foundMatch) {
            errors.push({ status: "NOT_PERSISTED", rowNumber: createdElement["!row#number!"], errorDetails: "Can't confirm persistence of this data" });
            activity.status = 2001; // means not persisted
            logger_1.logger.info("Can't confirm persistence of this data of row number : " + createdElement["!row#number!"]);
        }
    });
}
function matchCreatedAndSearchedData(createdData, searchedData, request, createAndSearchConfig, activity) {
    var _a, _b, _c, _d;
    var newCreatedData = JSON.parse(JSON.stringify(createdData));
    var newSearchedData = JSON.parse(JSON.stringify(searchedData));
    var uid = createAndSearchConfig.uniqueIdentifier;
    newCreatedData.forEach(function (element) {
        delete element[uid];
    });
    var errors = [];
    updateErrors(newCreatedData, newSearchedData, errors, createAndSearchConfig, activity);
    request.body.sheetErrorDetails = ((_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.sheetErrorDetails) ? __spreadArray(__spreadArray([], (_b = request === null || request === void 0 ? void 0 : request.body) === null || _b === void 0 ? void 0 : _b.sheetErrorDetails, true), errors, true) : errors;
    request.body.Activities = __spreadArray(__spreadArray([], (((_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.Activities) ? (_d = request === null || request === void 0 ? void 0 : request.body) === null || _d === void 0 ? void 0 : _d.Activities : []), true), [activity], false);
}
function matchViaUserIdAndCreationTime(createdData, searchedData, request, creationTime, createAndSearchConfig, activity) {
    var _a, _b, _c, _d, _e;
    var matchingSearchData = [];
    var userUuid = (_c = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.RequestInfo) === null || _b === void 0 ? void 0 : _b.userInfo) === null || _c === void 0 ? void 0 : _c.uuid;
    var count = 0;
    for (var _i = 0, searchedData_1 = searchedData; _i < searchedData_1.length; _i++) {
        var data = searchedData_1[_i];
        if (((_d = data === null || data === void 0 ? void 0 : data.auditDetails) === null || _d === void 0 ? void 0 : _d.createdBy) == userUuid && ((_e = data === null || data === void 0 ? void 0 : data.auditDetails) === null || _e === void 0 ? void 0 : _e.createdTime) >= creationTime) {
            matchingSearchData.push(data);
            count++;
        }
    }
    if (count < createdData.length) {
        request.body.ResourceDetails.status = "PERSISTER_ERROR";
    }
    matchCreatedAndSearchedData(createdData, matchingSearchData, request, createAndSearchConfig, activity);
    logger_1.logger.info("New created resources count : " + count);
}
function processSearch(createAndSearchConfig, request, params) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var arraysToMatch;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    setSearchLimits(createAndSearchConfig, request, params);
                    logger_1.logger.info("Search url : " + ((_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _a === void 0 ? void 0 : _a.url));
                    return [4 /*yield*/, performSearch(createAndSearchConfig, request, params)];
                case 1:
                    arraysToMatch = _b.sent();
                    return [2 /*return*/, arraysToMatch];
            }
        });
    });
}
function setSearchLimits(createAndSearchConfig, request, params) {
    var _a, _b;
    setLimitOrOffset((_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _a === void 0 ? void 0 : _a.searchLimit, params, request.body);
    setLimitOrOffset((_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _b === void 0 ? void 0 : _b.searchOffset, params, request.body);
}
function setLimitOrOffset(limitOrOffsetConfig, params, requestBody) {
    if (limitOrOffsetConfig) {
        if (limitOrOffsetConfig === null || limitOrOffsetConfig === void 0 ? void 0 : limitOrOffsetConfig.isInParams) {
            _.set(params, limitOrOffsetConfig === null || limitOrOffsetConfig === void 0 ? void 0 : limitOrOffsetConfig.keyPath, parseInt(limitOrOffsetConfig === null || limitOrOffsetConfig === void 0 ? void 0 : limitOrOffsetConfig.value));
        }
        if (limitOrOffsetConfig === null || limitOrOffsetConfig === void 0 ? void 0 : limitOrOffsetConfig.isInBody) {
            _.set(requestBody, limitOrOffsetConfig === null || limitOrOffsetConfig === void 0 ? void 0 : limitOrOffsetConfig.keyPath, parseInt(limitOrOffsetConfig === null || limitOrOffsetConfig === void 0 ? void 0 : limitOrOffsetConfig.value));
        }
    }
}
function performSearch(createAndSearchConfig, request, params) {
    var _a, _b, _c, _d, _e;
    return __awaiter(this, void 0, void 0, function () {
        var arraysToMatch, searchAgain, response, resultArray;
        return __generator(this, function (_f) {
            switch (_f.label) {
                case 0:
                    arraysToMatch = [];
                    searchAgain = true;
                    _f.label = 1;
                case 1:
                    if (!searchAgain) return [3 /*break*/, 4];
                    logger_1.logger.info("Search url : " + ((_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _a === void 0 ? void 0 : _a.url));
                    logger_1.logger.info("Search params : " + JSON.stringify(params));
                    logger_1.logger.info("Search body : " + JSON.stringify(request.body));
                    return [4 /*yield*/, (0, request_1.httpRequest)((_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _b === void 0 ? void 0 : _b.url, request.body, params)];
                case 2:
                    response = _f.sent();
                    resultArray = _.get(response, (_c = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _c === void 0 ? void 0 : _c.searchPath);
                    if (resultArray && Array.isArray(resultArray)) {
                        arraysToMatch.push.apply(arraysToMatch, resultArray);
                        if (resultArray.length < parseInt((_e = (_d = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _d === void 0 ? void 0 : _d.searchLimit) === null || _e === void 0 ? void 0 : _e.value)) {
                            searchAgain = false;
                        }
                    }
                    else {
                        searchAgain = false;
                    }
                    updateOffset(createAndSearchConfig, params, request.body);
                    return [4 /*yield*/, new Promise(function (resolve) { return setTimeout(resolve, 5000); })];
                case 3:
                    _f.sent();
                    return [3 /*break*/, 1];
                case 4: return [2 /*return*/, arraysToMatch];
            }
        });
    });
}
function updateOffset(createAndSearchConfig, params, requestBody) {
    var _a, _b, _c;
    var offsetConfig = (_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _a === void 0 ? void 0 : _a.searchOffset;
    var limit = (_c = (_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _b === void 0 ? void 0 : _b.searchLimit) === null || _c === void 0 ? void 0 : _c.value;
    if (offsetConfig) {
        if (offsetConfig === null || offsetConfig === void 0 ? void 0 : offsetConfig.isInParams) {
            _.set(params, offsetConfig === null || offsetConfig === void 0 ? void 0 : offsetConfig.keyPath, parseInt(_.get(params, offsetConfig === null || offsetConfig === void 0 ? void 0 : offsetConfig.keyPath) + parseInt(limit)));
        }
        if (offsetConfig === null || offsetConfig === void 0 ? void 0 : offsetConfig.isInBody) {
            _.set(requestBody, offsetConfig === null || offsetConfig === void 0 ? void 0 : offsetConfig.keyPath, parseInt(_.get(requestBody, offsetConfig === null || offsetConfig === void 0 ? void 0 : offsetConfig.keyPath) + parseInt(limit)));
        }
    }
}
function processSearchAndValidation(request, createAndSearchConfig, dataFromSheet) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        var params, arraysToMatch;
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    params = getParamsViaElements((_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _a === void 0 ? void 0 : _a.searchElements, request);
                    changeBodyViaElements((_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _b === void 0 ? void 0 : _b.searchElements, request);
                    changeBodyViaSearchFromSheet(createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.requiresToSearchFromSheet, request, dataFromSheet);
                    return [4 /*yield*/, processSearch(createAndSearchConfig, request, params)];
                case 1:
                    arraysToMatch = _c.sent();
                    (0, index_1.matchData)(request, request.body.dataToSearch, arraysToMatch, createAndSearchConfig);
                    return [2 /*return*/];
            }
        });
    });
}
function confirmCreation(createAndSearchConfig, request, facilityCreateData, creationTime, activity) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        var params, arraysToMatch;
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0: 
                // wait for 5 seconds
                return [4 /*yield*/, new Promise(function (resolve) { return setTimeout(resolve, 5000); })];
                case 1:
                    // wait for 5 seconds
                    _c.sent();
                    params = getParamsViaElements((_a = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _a === void 0 ? void 0 : _a.searchElements, request);
                    changeBodyViaElements((_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.searchDetails) === null || _b === void 0 ? void 0 : _b.searchElements, request);
                    return [4 /*yield*/, processSearch(createAndSearchConfig, request, params)];
                case 2:
                    arraysToMatch = _c.sent();
                    matchViaUserIdAndCreationTime(facilityCreateData, arraysToMatch, request, creationTime, createAndSearchConfig, activity);
                    return [2 /*return*/];
            }
        });
    });
}
exports.confirmCreation = confirmCreation;
function processValidate(request) {
    var _a, _b, _c, _d;
    return __awaiter(this, void 0, void 0, function () {
        var type, createAndSearchConfig, dataFromSheet, typeData;
        return __generator(this, function (_e) {
            switch (_e.label) {
                case 0:
                    type = request.body.ResourceDetails.type;
                    createAndSearchConfig = createAndSearch_1.default[type];
                    return [4 /*yield*/, (0, index_1.getDataFromSheet)((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails) === null || _b === void 0 ? void 0 : _b.fileStoreId, (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.ResourceDetails) === null || _d === void 0 ? void 0 : _d.tenantId, createAndSearchConfig)];
                case 1:
                    dataFromSheet = _e.sent();
                    return [4 /*yield*/, (0, campaignValidators_1.validateSheetData)(dataFromSheet, request, createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.sheetSchema, createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.boundaryValidation)];
                case 2:
                    _e.sent();
                    typeData = (0, index_1.convertToTypeData)(dataFromSheet, createAndSearchConfig, request.body);
                    request.body.dataToSearch = typeData.searchData;
                    return [4 /*yield*/, processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)];
                case 3:
                    _e.sent();
                    return [2 /*return*/];
            }
        });
    });
}
function performAndSaveResourceActivity(request, createAndSearchConfig, params, type) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k;
    return __awaiter(this, void 0, void 0, function () {
        var limit, dataToCreate, chunks, i, start, end, chunkData, creationTime, newRequestBody, responsePayload, activity;
        return __generator(this, function (_l) {
            switch (_l.label) {
                case 0:
                    logger_1.logger.info(type + " create data : " + JSON.stringify((_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.dataToCreate));
                    logger_1.logger.info(type + " bulk create url : " + ((_b = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _b === void 0 ? void 0 : _b.url), params);
                    if (!((_c = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _c === void 0 ? void 0 : _c.limit)) return [3 /*break*/, 6];
                    limit = (_d = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _d === void 0 ? void 0 : _d.limit;
                    dataToCreate = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.dataToCreate;
                    chunks = Math.ceil(dataToCreate.length / limit);
                    i = 0;
                    _l.label = 1;
                case 1:
                    if (!(i < chunks)) return [3 /*break*/, 6];
                    start = i * limit;
                    end = (i + 1) * limit;
                    chunkData = dataToCreate.slice(start, end);
                    creationTime = Date.now();
                    newRequestBody = JSON.parse(JSON.stringify(request.body));
                    _.set(newRequestBody, (_f = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _f === void 0 ? void 0 : _f.createPath, chunkData);
                    return [4 /*yield*/, (0, request_1.httpRequest)((_g = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _g === void 0 ? void 0 : _g.url, newRequestBody, params, "post", undefined, undefined, true)];
                case 2:
                    responsePayload = _l.sent();
                    return [4 /*yield*/, (0, index_1.generateActivityMessage)((_j = (_h = request === null || request === void 0 ? void 0 : request.body) === null || _h === void 0 ? void 0 : _h.ResourceDetails) === null || _j === void 0 ? void 0 : _j.tenantId, request.body, newRequestBody, responsePayload, type, (_k = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _k === void 0 ? void 0 : _k.url, responsePayload === null || responsePayload === void 0 ? void 0 : responsePayload.statusCode)];
                case 3:
                    activity = _l.sent();
                    return [4 /*yield*/, confirmCreation(createAndSearchConfig, request, chunkData, creationTime, activity)];
                case 4:
                    _l.sent();
                    logger_1.logger.info("Activity : " + JSON.stringify(activity));
                    _l.label = 5;
                case 5:
                    i++;
                    return [3 /*break*/, 1];
                case 6: return [2 /*return*/];
            }
        });
    });
}
function processGenericRequest(request) {
    var _a, _b;
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    if (!(((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails) === null || _b === void 0 ? void 0 : _b.action) == "create")) return [3 /*break*/, 2];
                    return [4 /*yield*/, processCreate(request)];
                case 1:
                    _c.sent();
                    return [3 /*break*/, 4];
                case 2: return [4 /*yield*/, processValidate(request)];
                case 3:
                    _c.sent();
                    _c.label = 4;
                case 4: return [2 /*return*/];
            }
        });
    });
}
exports.processGenericRequest = processGenericRequest;
function processCreate(request) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    return __awaiter(this, void 0, void 0, function () {
        var type, createAndSearchConfig, dataFromSheet, typeData, params;
        return __generator(this, function (_j) {
            switch (_j.label) {
                case 0:
                    type = request.body.ResourceDetails.type;
                    createAndSearchConfig = createAndSearch_1.default[type];
                    return [4 /*yield*/, (0, index_1.getDataFromSheet)((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.ResourceDetails) === null || _b === void 0 ? void 0 : _b.fileStoreId, (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.ResourceDetails) === null || _d === void 0 ? void 0 : _d.tenantId, createAndSearchConfig)];
                case 1:
                    dataFromSheet = _j.sent();
                    return [4 /*yield*/, (0, campaignValidators_1.validateSheetData)(dataFromSheet, request, createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.sheetSchema, createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.boundaryValidation)];
                case 2:
                    _j.sent();
                    typeData = (0, index_1.convertToTypeData)(dataFromSheet, createAndSearchConfig, request.body);
                    request.body.dataToCreate = typeData.createData;
                    request.body.dataToSearch = typeData.searchData;
                    return [4 /*yield*/, processSearchAndValidation(request, createAndSearchConfig, dataFromSheet)];
                case 3:
                    _j.sent();
                    if (!(createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails)) return [3 /*break*/, 5];
                    _.set(request.body, (_e = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _e === void 0 ? void 0 : _e.createPath, (_f = request === null || request === void 0 ? void 0 : request.body) === null || _f === void 0 ? void 0 : _f.dataToCreate);
                    params = getParamsViaElements((_g = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _g === void 0 ? void 0 : _g.createElements, request);
                    changeBodyViaElements((_h = createAndSearchConfig === null || createAndSearchConfig === void 0 ? void 0 : createAndSearchConfig.createBulkDetails) === null || _h === void 0 ? void 0 : _h.createElements, request);
                    return [4 /*yield*/, performAndSaveResourceActivity(request, createAndSearchConfig, params, type)];
                case 4:
                    _j.sent();
                    _j.label = 5;
                case 5: return [2 /*return*/];
            }
        });
    });
}
exports.processCreate = processCreate;
function createProjectCampaignResourcData(request) {
    var _a, _b, _c, _d, _e, _f;
    return __awaiter(this, void 0, void 0, function () {
        var _i, _g, resource, resourceDetails, error_1;
        return __generator(this, function (_h) {
            switch (_h.label) {
                case 0:
                    if (!(((_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails) === null || _b === void 0 ? void 0 : _b.action) == "create")) return [3 /*break*/, 6];
                    _i = 0, _g = request.body.CampaignDetails.resources;
                    _h.label = 1;
                case 1:
                    if (!(_i < _g.length)) return [3 /*break*/, 6];
                    resource = _g[_i];
                    resourceDetails = {
                        type: resource.type,
                        fileStoreId: resource.filestoreId,
                        tenantId: (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.CampaignDetails) === null || _d === void 0 ? void 0 : _d.tenantId,
                        action: "create",
                        hierarchyType: (_f = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.CampaignDetails) === null || _f === void 0 ? void 0 : _f.hierarchyType,
                        additionalDetails: {}
                    };
                    _h.label = 2;
                case 2:
                    _h.trys.push([2, 4, , 5]);
                    return [4 /*yield*/, (0, request_1.httpRequest)("http://localhost:8080/project-factory/v1/data/_create", { RequestInfo: request.body.RequestInfo, ResourceDetails: resourceDetails })];
                case 3:
                    _h.sent();
                    return [3 /*break*/, 5];
                case 4:
                    error_1 = _h.sent();
                    // Handle error for individual resource creation
                    logger_1.logger.error("Error creating resource: ".concat(error_1));
                    throw new Error(String(error_1));
                case 5:
                    _i++;
                    return [3 /*break*/, 1];
                case 6: return [2 /*return*/];
            }
        });
    });
}
exports.createProjectCampaignResourcData = createProjectCampaignResourcData;
function projectCreate(projectCreateBody, request) {
    var _a, _b, _c, _d, _e, _f;
    return __awaiter(this, void 0, void 0, function () {
        var projectCreateResponse;
        return __generator(this, function (_g) {
            switch (_g.label) {
                case 0:
                    logger_1.logger.info("Project creation url " + config_1.default.host.projectHost + config_1.default.paths.projectCreate);
                    logger_1.logger.info("Project creation body " + JSON.stringify(projectCreateBody));
                    return [4 /*yield*/, (0, request_1.httpRequest)(config_1.default.host.projectHost + config_1.default.paths.projectCreate, projectCreateBody)];
                case 1:
                    projectCreateResponse = _g.sent();
                    logger_1.logger.info("Project creation response" + JSON.stringify(projectCreateResponse));
                    if ((_a = projectCreateResponse === null || projectCreateResponse === void 0 ? void 0 : projectCreateResponse.Project[0]) === null || _a === void 0 ? void 0 : _a.id) {
                        logger_1.logger.info("Project created successfully with id " + JSON.stringify((_b = projectCreateResponse === null || projectCreateResponse === void 0 ? void 0 : projectCreateResponse.Project[0]) === null || _b === void 0 ? void 0 : _b.id));
                        request.body.boundaryProjectMapping[(_e = (_d = (_c = projectCreateBody === null || projectCreateBody === void 0 ? void 0 : projectCreateBody.Projects) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.address) === null || _e === void 0 ? void 0 : _e.boundary].projectId = (_f = projectCreateResponse === null || projectCreateResponse === void 0 ? void 0 : projectCreateResponse.Project[0]) === null || _f === void 0 ? void 0 : _f.id;
                    }
                    else {
                        throw new Error("Project creation failed, for the request: " + JSON.stringify(projectCreateBody));
                    }
                    return [2 /*return*/];
            }
        });
    });
}
exports.projectCreate = projectCreate;
