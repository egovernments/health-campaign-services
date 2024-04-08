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
exports.httpRequest = exports.defaultheader = void 0;
var logger_1 = require("./logger");
var _1 = require(".");
var Axios = require("axios").default;
var get = require("lodash/get");
Axios.interceptors.response.use(function (res) {
    return res;
}, function (err) {
    if (err && !err.response) {
        err.response = {
            status: 400,
        };
    }
    if (err && err.response && !err.response.data) {
        err.response.data = {
            Errors: [{ code: err.message }],
        };
    }
    throw err;
});
exports.defaultheader = {
    "content-type": "application/json;charset=UTF-8",
    accept: "application/json, text/plain, */*",
};
var getServiceName = function (url) {
    if (url === void 0) { url = ""; }
    return url && url.slice && url.slice(url.lastIndexOf(url.split("/")[3]));
};
var cacheEnabled = true;
/*
 
Used to Make API call through axios library

  @author jagankumar-egov

 * @param {string} _url
 * @param {Object} _requestBody
 * @param {Object} _params
 * @returns {string} _method default to post
 * @returns {string} responseType
 * @param {Object} headers

*/
var httpRequest = function (_url, _requestBody, _params, _method, responseType, headers, sendStatusCode) {
    if (_params === void 0) { _params = {}; }
    if (_method === void 0) { _method = "post"; }
    if (responseType === void 0) { responseType = ""; }
    if (headers === void 0) { headers = exports.defaultheader; }
    if (sendStatusCode === void 0) { sendStatusCode = false; }
    return __awaiter(void 0, void 0, void 0, function () {
        var cacheData, response, responseStatus, error_1, errorResponse;
        var _a, _b, _c, _d, _e, _f;
        return __generator(this, function (_g) {
            switch (_g.label) {
                case 0:
                    _g.trys.push([0, 2, , 3]);
                    if (headers && headers.cachekey && cacheEnabled) {
                        cacheData = (0, _1.getCachedResponse)(headers.cachekey);
                        if (cacheData) {
                            return [2 /*return*/, cacheData];
                        }
                        logger_1.logger.info("NO CACHE FOUND :: REQUEST :: " +
                            JSON.stringify(headers.cachekey));
                    }
                    logger_1.logger.info("INTER-SERVICE :: REQUEST :: " +
                        getServiceName(_url) +
                        " CRITERIA :: " +
                        JSON.stringify(_params));
                    logger_1.logger.debug(JSON.stringify(_requestBody));
                    return [4 /*yield*/, Axios({
                            method: _method,
                            url: _url,
                            data: _requestBody,
                            params: _params,
                            headers: __assign(__assign({}, exports.defaultheader), headers),
                            responseType: responseType,
                        })];
                case 1:
                    response = _g.sent();
                    responseStatus = parseInt(get(response, "status"), 10);
                    logger_1.logger.info("INTER-SERVICE :: SUCCESS :: " +
                        getServiceName(_url) +
                        ":: CODE :: " +
                        responseStatus);
                    if (responseStatus === 200 || responseStatus === 201 || responseStatus === 202) {
                        if (headers && headers.cachekey) {
                            (0, _1.cacheResponse)(response.data, headers.cachekey);
                        }
                        if (!sendStatusCode)
                            return [2 /*return*/, response.data];
                        else
                            return [2 /*return*/, __assign(__assign({}, response.data), { "statusCode": responseStatus })];
                    }
                    return [3 /*break*/, 3];
                case 2:
                    error_1 = _g.sent();
                    errorResponse = error_1 === null || error_1 === void 0 ? void 0 : error_1.response;
                    logger_1.logger.error("INTER-SERVICE :: FAILURE :: " +
                        getServiceName(_url) +
                        ":: CODE :: " +
                        (errorResponse === null || errorResponse === void 0 ? void 0 : errorResponse.status) +
                        ":: ERROR :: " +
                        ((_c = (_b = (_a = errorResponse === null || errorResponse === void 0 ? void 0 : errorResponse.data) === null || _a === void 0 ? void 0 : _a.Errors) === null || _b === void 0 ? void 0 : _b[0]) === null || _c === void 0 ? void 0 : _c.code) || error_1);
                    logger_1.logger.error(":: ERROR STACK :: " + (error_1 === null || error_1 === void 0 ? void 0 : error_1.stack) || error_1);
                    (0, _1.throwError)("error occured while making request to " +
                        getServiceName(_url) +
                        ": error response :" +
                        (errorResponse ? parseInt(errorResponse === null || errorResponse === void 0 ? void 0 : errorResponse.status, 10) : error_1 === null || error_1 === void 0 ? void 0 : error_1.message), (_f = (_e = (_d = errorResponse === null || errorResponse === void 0 ? void 0 : errorResponse.data) === null || _d === void 0 ? void 0 : _d.Errors) === null || _e === void 0 ? void 0 : _e[0]) === null || _f === void 0 ? void 0 : _f.code, errorResponse === null || errorResponse === void 0 ? void 0 : errorResponse.status);
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
};
exports.httpRequest = httpRequest;
