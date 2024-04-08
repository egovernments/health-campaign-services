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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateGenerateRequest = exports.validateProjectResourceResponse = exports.validateProjectFacilityResponse = exports.validateStaffResponse = exports.validatedProjectResponseAndUpdateId = exports.validateCampaignRequest = exports.getTransformAndParsingTemplates = exports.processValidationWithSchema = exports.validateDataWithSchema = void 0;
var logger_1 = require("../logger");
var ajv_1 = __importDefault(require("ajv"));
var index_1 = __importDefault(require("../../config/index"));
var request_1 = require("../request");
// import RequestCampaignDetails from "../config/interfaces/requestCampaignDetails.interface";
function validateDataWithSchema(data, schema) {
    var ajv = new ajv_1.default();
    var validate = ajv.compile(schema);
    var isValid = validate(data);
    if (!isValid) {
        logger_1.logger.error(JSON.stringify(validate.errors));
    }
    return { isValid: isValid, error: validate.errors };
}
exports.validateDataWithSchema = validateDataWithSchema;
function processValidationWithSchema(processResult, validationErrors, validatedData, schemaDef) {
    if (schemaDef) {
        processResult.updatedDatas.forEach(function (data) {
            var validationResult = validateDataWithSchema(data, schemaDef);
            if (!validationResult.isValid) {
                validationErrors.push({ data: data, error: validationResult.error });
            }
            else {
                validatedData.push(data);
            }
        });
    }
    else {
        logger_1.logger.info("Skipping Validation of Data as Schema is not defined");
        validationErrors.push("NO_VALIDATION_SCHEMA_FOUND");
        processResult.updatedDatas.forEach(function (data) {
            validatedData.push(data);
        });
    }
}
exports.processValidationWithSchema = processValidationWithSchema;
function getTransformAndParsingTemplates(APIResource, request, response) {
    var _a, _b, _c, _d, _e, _f;
    return __awaiter(this, void 0, void 0, function () {
        var errorMessage, transformTemplate, parsingTemplate;
        return __generator(this, function (_g) {
            if (!APIResource.mdms || Object.keys(APIResource.mdms).length === 0) {
                errorMessage = "Invalid APIResourceType Type";
                logger_1.logger.error(errorMessage);
                throw new Error(errorMessage);
            }
            transformTemplate = (_c = (_b = (_a = APIResource === null || APIResource === void 0 ? void 0 : APIResource.mdms) === null || _a === void 0 ? void 0 : _a[0]) === null || _b === void 0 ? void 0 : _b.data) === null || _c === void 0 ? void 0 : _c.transformTemplateName;
            parsingTemplate = (_f = (_e = (_d = APIResource === null || APIResource === void 0 ? void 0 : APIResource.mdms) === null || _d === void 0 ? void 0 : _d[0]) === null || _e === void 0 ? void 0 : _e.data) === null || _f === void 0 ? void 0 : _f.parsingTemplateName;
            return [2 /*return*/, { transformTemplate: transformTemplate, parsingTemplate: parsingTemplate }];
        });
    });
}
exports.getTransformAndParsingTemplates = getTransformAndParsingTemplates;
function validateBoundaries(requestBody) {
    var _a;
    var boundaryCode = (requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign).boundaryCode;
    if (!boundaryCode) {
        throw new Error("Enter BoundaryCode In Campaign");
    }
    for (var _i = 0, _b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.CampaignDetails; _i < _b.length; _i++) {
        var campaignDetails = _b[_i];
        var campaignBoundaryCode = campaignDetails.boundaryCode, parentBoundaryCode = campaignDetails.parentBoundaryCode;
        if (!parentBoundaryCode && boundaryCode != campaignBoundaryCode) {
            throw new Error("Enter ParentBoundaryCode In CampaignDetails");
        }
        if (!campaignBoundaryCode) {
            throw new Error("Enter BoundaryCode In CampaignDetails");
        }
    }
}
function validateUserId(resourceId, requestBody) {
    var _a, _b, _c, _d;
    return __awaiter(this, void 0, void 0, function () {
        var userSearchBody, response;
        return __generator(this, function (_e) {
            switch (_e.label) {
                case 0:
                    userSearchBody = {
                        RequestInfo: requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo,
                        tenantId: (_b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.tenantId.split('.')) === null || _b === void 0 ? void 0 : _b[0],
                        uuid: [resourceId]
                    };
                    logger_1.logger.info("User search url : " + index_1.default.host.userHost + index_1.default.paths.userSearch);
                    logger_1.logger.info("userSearchBody : " + JSON.stringify(userSearchBody));
                    return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.userHost + index_1.default.paths.userSearch, userSearchBody)];
                case 1:
                    response = _e.sent();
                    if (!((_d = (_c = response === null || response === void 0 ? void 0 : response.user) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.uuid)) {
                        throw new Error("Invalid resourceId for resource type staff with id " + resourceId);
                    }
                    return [2 /*return*/];
            }
        });
    });
}
function validateProductVariantId(resourceId, requestBody) {
    var _a, _b, _c, _d;
    return __awaiter(this, void 0, void 0, function () {
        var productVariantSearchBody, productVariantSearchParams, response;
        return __generator(this, function (_e) {
            switch (_e.label) {
                case 0:
                    productVariantSearchBody = {
                        RequestInfo: requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo,
                        ProductVariant: { id: [resourceId] }
                    };
                    productVariantSearchParams = {
                        limit: 10,
                        offset: 0,
                        tenantId: (_b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.tenantId.split('.')) === null || _b === void 0 ? void 0 : _b[0]
                    };
                    logger_1.logger.info("ProductVariant search url : " + index_1.default.host.productHost + index_1.default.paths.productVariantSearch);
                    logger_1.logger.info("productVariantSearchBody : " + JSON.stringify(productVariantSearchBody));
                    logger_1.logger.info("productVariantSearchParams : " + JSON.stringify(productVariantSearchParams));
                    return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.productHost + index_1.default.paths.productVariantSearch, productVariantSearchBody, productVariantSearchParams)];
                case 1:
                    response = _e.sent();
                    if (!((_d = (_c = response === null || response === void 0 ? void 0 : response.ProductVariant) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.id)) {
                        throw new Error("Invalid resourceId for resource type resource with id " + resourceId);
                    }
                    return [2 /*return*/];
            }
        });
    });
}
function validateProjectFacilityId(resourceId, requestBody) {
    var _a, _b, _c, _d, _e;
    return __awaiter(this, void 0, void 0, function () {
        var facilitySearchBody, facilitySearchParams, response;
        return __generator(this, function (_f) {
            switch (_f.label) {
                case 0:
                    facilitySearchBody = {
                        RequestInfo: requestBody === null || requestBody === void 0 ? void 0 : requestBody.RequestInfo,
                        Facility: {
                            id: [resourceId]
                        }
                    };
                    facilitySearchParams = {
                        limit: 10,
                        offset: 0,
                        tenantId: (_c = (_b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.tenantId) === null || _b === void 0 ? void 0 : _b.split('.')) === null || _c === void 0 ? void 0 : _c[0]
                    };
                    logger_1.logger.info("Facility search url : " + index_1.default.host.facilityHost + index_1.default.paths.facilitySearch);
                    logger_1.logger.info("facilitySearchBody : " + JSON.stringify(facilitySearchBody));
                    logger_1.logger.info("facilitySearchParams : " + JSON.stringify(facilitySearchParams));
                    return [4 /*yield*/, (0, request_1.httpRequest)(index_1.default.host.facilityHost + index_1.default.paths.facilitySearch, facilitySearchBody, facilitySearchParams)];
                case 1:
                    response = _f.sent();
                    if (!((_e = (_d = response === null || response === void 0 ? void 0 : response.Facilities) === null || _d === void 0 ? void 0 : _d[0]) === null || _e === void 0 ? void 0 : _e.id)) {
                        throw new Error("Invalid resourceId for resource type facility with id " + resourceId);
                    }
                    return [2 /*return*/];
            }
        });
    });
}
function validateResourceId(type, resourceId, requestBody) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    if (!(type == "staff")) return [3 /*break*/, 2];
                    return [4 /*yield*/, validateUserId(resourceId, requestBody)];
                case 1:
                    _a.sent();
                    return [3 /*break*/, 7];
                case 2:
                    if (!(type == "resource")) return [3 /*break*/, 4];
                    return [4 /*yield*/, validateProductVariantId(resourceId, requestBody)];
                case 3:
                    _a.sent();
                    return [3 /*break*/, 7];
                case 4:
                    if (!(type == "facility")) return [3 /*break*/, 6];
                    return [4 /*yield*/, validateProjectFacilityId(resourceId, requestBody)];
                case 5:
                    _a.sent();
                    return [3 /*break*/, 7];
                case 6: throw new Error("Invalid resource type " + type);
                case 7: return [2 /*return*/];
            }
        });
    });
}
function validateProjectResource(requestBody) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var _i, _b, campaignDetails, _c, _d, resource, type, _e, _f, resourceId;
        return __generator(this, function (_g) {
            switch (_g.label) {
                case 0:
                    _i = 0, _b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.CampaignDetails;
                    _g.label = 1;
                case 1:
                    if (!(_i < _b.length)) return [3 /*break*/, 8];
                    campaignDetails = _b[_i];
                    _c = 0, _d = campaignDetails === null || campaignDetails === void 0 ? void 0 : campaignDetails.resources;
                    _g.label = 2;
                case 2:
                    if (!(_c < _d.length)) return [3 /*break*/, 7];
                    resource = _d[_c];
                    type = resource === null || resource === void 0 ? void 0 : resource.type;
                    _e = 0, _f = resource === null || resource === void 0 ? void 0 : resource.resourceIds;
                    _g.label = 3;
                case 3:
                    if (!(_e < _f.length)) return [3 /*break*/, 6];
                    resourceId = _f[_e];
                    if (!type) {
                        throw new Error("Enter Type In Resources");
                    }
                    if (!resourceId) {
                        throw new Error("Enter ResourceId In Resources");
                    }
                    return [4 /*yield*/, validateResourceId(type, resourceId, requestBody)];
                case 4:
                    _g.sent();
                    _g.label = 5;
                case 5:
                    _e++;
                    return [3 /*break*/, 3];
                case 6:
                    _c++;
                    return [3 /*break*/, 2];
                case 7:
                    _i++;
                    return [3 /*break*/, 1];
                case 8: return [2 /*return*/];
            }
        });
    });
}
function validateCampaign(requestBody) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var _i, _b, campaignDetails, startDate, endDate;
        return __generator(this, function (_c) {
            switch (_c.label) {
                case 0:
                    for (_i = 0, _b = (_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.CampaignDetails; _i < _b.length; _i++) {
                        campaignDetails = _b[_i];
                        startDate = campaignDetails.startDate, endDate = campaignDetails.endDate;
                        startDate = parseInt(startDate);
                        endDate = parseInt(endDate);
                        // Check if startDate and endDate are valid integers
                        if (isNaN(startDate) || isNaN(endDate)) {
                            throw new Error("Start date or end date is not a valid epoch timestamp");
                        }
                    }
                    return [4 /*yield*/, validateProjectResource(requestBody)];
                case 1:
                    _c.sent();
                    return [2 /*return*/];
            }
        });
    });
}
function validateCampaignRequest(requestBody) {
    var _a;
    return __awaiter(this, void 0, void 0, function () {
        var projectType;
        return __generator(this, function (_b) {
            switch (_b.label) {
                case 0:
                    if (!(requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign)) return [3 /*break*/, 2];
                    if (!((_a = requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign) === null || _a === void 0 ? void 0 : _a.tenantId)) {
                        throw new Error("Enter TenantId");
                    }
                    validateBoundaries(requestBody);
                    projectType = (requestBody === null || requestBody === void 0 ? void 0 : requestBody.Campaign).projectType;
                    if (!projectType) {
                        throw new Error("Enter ProjectType");
                    }
                    return [4 /*yield*/, validateCampaign(requestBody)];
                case 1:
                    _b.sent();
                    return [3 /*break*/, 3];
                case 2: throw new Error("Campaign is required");
                case 3: return [2 /*return*/];
            }
        });
    });
}
exports.validateCampaignRequest = validateCampaignRequest;
function validatedProjectResponseAndUpdateId(projectResponse, projectBody, campaignDetails) {
    var _a, _b;
    if (((_a = projectBody === null || projectBody === void 0 ? void 0 : projectBody.Projects) === null || _a === void 0 ? void 0 : _a.length) != ((_b = projectResponse === null || projectResponse === void 0 ? void 0 : projectResponse.Project) === null || _b === void 0 ? void 0 : _b.length)) {
        throw new Error("Project creation failed. Check Logs");
    }
    else {
        for (var _i = 0, _c = projectResponse === null || projectResponse === void 0 ? void 0 : projectResponse.Project; _i < _c.length; _i++) {
            var project = _c[_i];
            if (!(project === null || project === void 0 ? void 0 : project.id)) {
                throw new Error("Project creation failed. Check Logs");
            }
            else {
                campaignDetails.projectId = project.id;
            }
        }
    }
}
exports.validatedProjectResponseAndUpdateId = validatedProjectResponseAndUpdateId;
function validateStaffResponse(staffResponse) {
    var _a;
    if (!((_a = staffResponse === null || staffResponse === void 0 ? void 0 : staffResponse.ProjectStaff) === null || _a === void 0 ? void 0 : _a.id)) {
        throw new Error("Project staff creation failed. Check Logs");
    }
}
exports.validateStaffResponse = validateStaffResponse;
function validateProjectResourceResponse(projectResouceResponse) {
    var _a;
    if (!((_a = projectResouceResponse === null || projectResouceResponse === void 0 ? void 0 : projectResouceResponse.ProjectResource) === null || _a === void 0 ? void 0 : _a.id)) {
        throw new Error("Project Resource creation failed. Check Logs");
    }
}
exports.validateProjectResourceResponse = validateProjectResourceResponse;
function validateProjectFacilityResponse(projectFacilityResponse) {
    var _a;
    if (!((_a = projectFacilityResponse === null || projectFacilityResponse === void 0 ? void 0 : projectFacilityResponse.ProjectFacility) === null || _a === void 0 ? void 0 : _a.id)) {
        throw new Error("Project Facility creation failed. Check Logs");
    }
}
exports.validateProjectFacilityResponse = validateProjectFacilityResponse;
function validateGenerateRequest(request) {
    var _a, _b, _c;
    var _d = request.query, tenantId = _d.tenantId, type = _d.type;
    if (!tenantId) {
        throw new Error("tenantId is required");
    }
    if (tenantId != ((_c = (_b = (_a = request === null || request === void 0 ? void 0 : request.body) === null || _a === void 0 ? void 0 : _a.RequestInfo) === null || _b === void 0 ? void 0 : _b.userInfo) === null || _c === void 0 ? void 0 : _c.tenantId)) {
        throw new Error("tenantId in userInfo and query should be same");
    }
    if (!["facility", "user", "boundary", "facilityWithBoundary"].includes(String(type))) {
        throw new Error("type should be facility, user, boundary or facilityWithBoundary");
    }
}
exports.validateGenerateRequest = validateGenerateRequest;
