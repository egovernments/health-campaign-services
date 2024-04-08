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
Object.defineProperty(exports, "__esModule", { value: true });
var express = __importStar(require("express"));
var logger_1 = require("../../utils/logger");
var index_1 = require("../../utils/index");
var genericValidator_1 = require("../../utils/validators/genericValidator");
var campaignApis_1 = require("../../api/campaignApis");
var campaignValidators_1 = require("../../utils/validators/campaignValidators");
var genericApis_1 = require("../../api/genericApis");
// Define the MeasurementController class
var campaignManageController = /** @class */ (function () {
    // Constructor to initialize routes
    function campaignManageController() {
        var _this = this;
        // Define class properties
        this.path = "/v1/project-type";
        this.router = express.Router();
        this.dayInMilliSecond = 86400000;
        this.createProjectTypeCampaign = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var e_1;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, (0, campaignValidators_1.validateProjectCampaignRequest)(request)];
                    case 1:
                        _b.sent();
                        return [4 /*yield*/, (0, index_1.processBasedOnAction)(request)];
                    case 2:
                        _b.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { CampaignDetails: (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails }, request)];
                    case 3:
                        e_1 = _b.sent();
                        logger_1.logger.error(String(e_1));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_1) }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.searchProjectTypeCampaign = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var e_2;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 3, , 4]);
                        return [4 /*yield*/, (0, campaignValidators_1.validateSearchProjectCampaignRequest)(request)];
                    case 1:
                        _b.sent();
                        return [4 /*yield*/, (0, index_1.searchProjectCampaignResourcData)(request)];
                    case 2:
                        _b.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { CampaignDetails: (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.CampaignDetails }, request)];
                    case 3:
                        e_2 = _b.sent();
                        logger_1.logger.error(String(e_2));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_2) }, request, response)];
                    case 4: return [2 /*return*/];
                }
            });
        }); };
        this.createCampaign = function (request, response) { return __awaiter(_this, void 0, void 0, function () {
            var e_3;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 5, , 6]);
                        return [4 /*yield*/, (0, genericValidator_1.validateCampaignRequest)(request.body)];
                    case 1:
                        _b.sent();
                        return [4 /*yield*/, (0, genericApis_1.createProjectIfNotExists)(request.body)];
                    case 2:
                        _b.sent();
                        return [4 /*yield*/, (0, genericApis_1.createRelatedResouce)(request.body)];
                    case 3:
                        _b.sent();
                        return [4 /*yield*/, (0, campaignApis_1.enrichCampaign)(request.body)];
                    case 4:
                        _b.sent();
                        return [2 /*return*/, (0, index_1.sendResponse)(response, { Campaign: (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.Campaign }, request)];
                    case 5:
                        e_3 = _b.sent();
                        logger_1.logger.error(String(e_3));
                        return [2 /*return*/, (0, index_1.errorResponder)({ message: String(e_3) }, request, response)];
                    case 6: return [2 /*return*/];
                }
            });
        }); };
        this.intializeRoutes();
    }
    // Initialize routes for MeasurementController
    campaignManageController.prototype.intializeRoutes = function () {
        this.router.post("".concat(this.path, "/create"), this.createProjectTypeCampaign);
        this.router.post("".concat(this.path, "/search"), this.searchProjectTypeCampaign);
        this.router.post("".concat(this.path, "/createCampaign"), this.createCampaign);
    };
    return campaignManageController;
}());
;
exports.default = campaignManageController;
