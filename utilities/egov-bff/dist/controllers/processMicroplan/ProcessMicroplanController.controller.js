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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var express = __importStar(require("express"));
var utils_1 = require("../../utils");
var index_1 = __importDefault(require("../../config/index"));
var logger_1 = require("../../utils/logger");
var Listener_1 = require("../../Kafka/Listener");
var index_2 = require("../../utils/index");
var validator_1 = require("../../utils/validator");
var index_3 = require("../../utils/index");
var index_4 = require("../../api/index");
var index_5 = require("../../utils/index");
var request_1 = require("../../utils/request");
var pg_1 = require("pg");
var saveCampaignTopic = index_1.default.KAFKA_SAVE_CAMPAIGN_DETAILS_TOPIC;
var updateCampaignTopic = index_1.default.KAFKA_UPDATE_CAMPAIGN_DETAILS_TOPIC;
// Define the MeasurementController class
var BulkUploadController = /** @class */ (function () {
    // Constructor to initialize routes
    function BulkUploadController() {
        var _this = this;
        // Define class properties
        this.path = "/hcm";
        this.router = express.Router();
        this.dayInMilliSecond = 86400000;
        this.searchMicroplan = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var pool, criteria, campaignIds, campaignName, campaignType, campaignNumber, createdBy, projectTypeId, pagination, client, queryString, idList, sortingAndPaginationClauses, campaignResult, campaignDetails, idListForIngestion, ingestionQueryString, ingestionResult, ingestionDetails_1, responseData, error_1;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 6, , 7]);
                        pool = new pg_1.Pool({
                            user: index_1.default.DB_USER,
                            host: index_1.default.DB_HOST,
                            database: index_1.default.DB_NAME,
                            password: index_1.default.DB_PASSWORD,
                            port: parseInt(index_1.default.DB_PORT)
                        });
                        criteria = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails;
                        campaignIds = criteria.campaignIds, campaignName = criteria.campaignName, campaignType = criteria.campaignType, campaignNumber = criteria.campaignNumber, createdBy = criteria.createdBy, projectTypeId = criteria.projectTypeId, pagination = criteria.pagination;
                        return [4 /*yield*/, pool.connect()];
                    case 1:
                        client = _b.sent();
                        queryString = 'SELECT * FROM eg_campaign_details WHERE 1=1';
                        if (campaignIds && campaignIds.length > 0) {
                            idList = campaignIds.map(function (id) { return "'".concat(id, "'"); }).join(', ');
                            queryString += " AND id IN (".concat(idList, ")");
                        }
                        if (campaignName) {
                            queryString += " AND campaignName = '".concat(campaignName, "'");
                        }
                        if (campaignNumber) {
                            queryString += " AND campaignNumber = '".concat(campaignNumber, "'");
                        }
                        if (campaignType) {
                            queryString += " AND campaignType = '".concat(campaignType, "'");
                        }
                        if (createdBy) {
                            queryString += " AND createdBy = '".concat(createdBy, "'");
                        }
                        if (projectTypeId) {
                            queryString += " AND projectTypeId = '".concat(projectTypeId, "'");
                        }
                        sortingAndPaginationClauses = (0, index_3.generateSortingAndPaginationClauses)(pagination);
                        queryString += sortingAndPaginationClauses;
                        return [4 /*yield*/, client.query(queryString)];
                    case 2:
                        campaignResult = _b.sent();
                        campaignDetails = campaignResult.rows;
                        if (!(campaignDetails.length > 0)) return [3 /*break*/, 4];
                        idListForIngestion = campaignDetails.map(function (campaign) { return "'".concat(campaign.id, "'"); }).join(', ');
                        ingestionQueryString = "SELECT * FROM eg_campaign_ingestionDetails WHERE campaignId IN (".concat(idListForIngestion, ")");
                        return [4 /*yield*/, client.query(ingestionQueryString)];
                    case 3:
                        ingestionResult = _b.sent();
                        ingestionDetails_1 = ingestionResult.rows;
                        responseData = campaignDetails.map(function (campaign) {
                            var filteredIngestionDetails = ingestionDetails_1.filter(function (ingestion) {
                                return ingestion.campaignid === campaign.id;
                            });
                            return __assign(__assign({}, campaign), { ingestionDetails: filteredIngestionDetails });
                        });
                        return [2 /*return*/, (0, index_5.sendResponse)(response, { CampaignDetails: responseData }, request)];
                    case 4: return [2 /*return*/, (0, index_5.sendResponse)(response, { CampaignDetails: [] }, request)];
                    case 5: return [3 /*break*/, 7];
                    case 6:
                        error_1 = _b.sent();
                        logger_1.logger.error('Error searching campaigns:' + JSON.stringify(error_1));
                        return [2 /*return*/, (0, index_5.sendResponse)(response, { CampaignDetails: [] }, request)];
                    case 7: return [2 /*return*/];
                }
            });
        }); };
        this.processMicroplan = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var campaignDetails, result, Job, saveHistory, campaignType, campaign, parsingTemplates, hostHcmBff, e_1, updatedJob, e_2;
            var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q;
            return __generator(this, function (_r) {
                switch (_r.label) {
                    case 0:
                        _r.trys.push([0, 10, , 11]);
                        return [4 /*yield*/, (0, validator_1.validateProcessMicroplan)(request, response)];
                    case 1:
                        _r.sent();
                        return [4 /*yield*/, (0, index_2.getCampaignDetails)(request === null || request === void 0 ? void 0 : request.body)];
                    case 2:
                        campaignDetails = _r.sent();
                        if (campaignDetails == "INVALID_CAMPAIGN_NUMBER") {
                            throw new Error("Error during Campaign Number generation");
                        }
                        Job = { ingestionDetails: { userInfo: {}, projectType: (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.HCMConfig) === null || _b === void 0 ? void 0 : _b.projectType, projectTypeId: (_d = (_c = request === null || request === void 0 ? void 0 : request.body) === null || _c === void 0 ? void 0 : _c.HCMConfig) === null || _d === void 0 ? void 0 : _d.projectTypeId, projectName: (_f = (_e = request === null || request === void 0 ? void 0 : request.body) === null || _e === void 0 ? void 0 : _e.HCMConfig) === null || _f === void 0 ? void 0 : _f.campaignName, history: [], campaignDetails: campaignDetails } };
                        saveHistory = Job.ingestionDetails;
                        logger_1.logger.info("Saving campaign details : " + JSON.stringify(campaignDetails));
                        (0, Listener_1.produceModifiedMessages)(saveHistory, saveCampaignTopic);
                        _r.label = 3;
                    case 3:
                        _r.trys.push([3, 7, , 8]);
                        campaignType = ((_g = request === null || request === void 0 ? void 0 : request.body) === null || _g === void 0 ? void 0 : _g.HCMConfig).campaignType;
                        return [4 /*yield*/, (0, index_4.searchMDMS)([campaignType], index_1.default.values.campaignType, request.body.RequestInfo, response)];
                    case 4:
                        campaign = _r.sent();
                        if (!campaign.mdms || Object.keys(campaign.mdms).length === 0) {
                            throw new Error("Invalid Campaign Type");
                        }
                        request.body.HCMConfig['transformTemplate'] = (_k = (_j = (_h = campaign === null || campaign === void 0 ? void 0 : campaign.mdms) === null || _h === void 0 ? void 0 : _h[0]) === null || _j === void 0 ? void 0 : _j.data) === null || _k === void 0 ? void 0 : _k.transformTemplate;
                        parsingTemplates = (_o = (_m = (_l = campaign === null || campaign === void 0 ? void 0 : campaign.mdms) === null || _l === void 0 ? void 0 : _l[0]) === null || _m === void 0 ? void 0 : _m.data) === null || _o === void 0 ? void 0 : _o.parsingTemplates;
                        logger_1.logger.info("ParsingTemplates : " + JSON.stringify(parsingTemplates));
                        hostHcmBff = index_1.default.host.hcmBff.endsWith('/') ? index_1.default.host.hcmBff.slice(0, -1) : index_1.default.host.hcmBff;
                        return [4 /*yield*/, (0, request_1.httpRequest)("".concat(hostHcmBff).concat(index_1.default.app.contextPath).concat('/bulk', "/_transform"), request.body, undefined, undefined, undefined, undefined)];
                    case 5:
                        result = _r.sent();
                        if (result.updatedDatas.error) {
                            throw new Error(result.updatedDatas.error);
                        }
                        Job.ingestionDetails.campaignDetails.status = "Started";
                        Job.ingestionDetails.campaignDetails.auditDetails.lastModifiedTime = new Date().getTime();
                        (0, Listener_1.produceModifiedMessages)(Job.ingestionDetails, updateCampaignTopic);
                        (0, validator_1.validateTransformedData)(result === null || result === void 0 ? void 0 : result.updatedDatas);
                        return [4 /*yield*/, (0, utils_1.processFile)(request, parsingTemplates, result, hostHcmBff, Job)];
                    case 6:
                        _r.sent();
                        return [3 /*break*/, 8];
                    case 7:
                        e_1 = _r.sent();
                        logger_1.logger.error(String(e_1));
                        return [2 /*return*/, (0, index_5.errorResponder)({ message: String(e_1) + "    Check Logs" }, request, response)];
                    case 8:
                        Job.ingestionDetails.userInfo = (_q = (_p = request === null || request === void 0 ? void 0 : request.body) === null || _p === void 0 ? void 0 : _p.RequestInfo) === null || _q === void 0 ? void 0 : _q.userInfo;
                        Job.ingestionDetails.campaignDetails = campaignDetails;
                        return [4 /*yield*/, (0, utils_1.produceIngestion)({ Job: Job })];
                    case 9:
                        updatedJob = _r.sent();
                        return [2 /*return*/, (0, index_5.sendResponse)(response, { Job: updatedJob }, request)];
                    case 10:
                        e_2 = _r.sent();
                        logger_1.logger.error(String(e_2));
                        return [2 /*return*/, (0, index_5.errorResponder)({ message: String(e_2) }, request, response)];
                    case 11: return [2 /*return*/];
                }
            });
        }); };
        this.intializeRoutes();
    }
    // Initialize routes for MeasurementController
    BulkUploadController.prototype.intializeRoutes = function () {
        this.router.post("".concat(this.path, "/_processmicroplan"), this.processMicroplan);
        this.router.post("".concat(this.path, "/_searchmicroplan"), this.searchMicroplan);
    };
    return BulkUploadController;
}());
// Export the MeasurementController class
exports.default = BulkUploadController;
