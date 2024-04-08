"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var index_1 = require("../index");
var cacheEnabled = false;
var cacheMiddleware = function (req, res, next) {
    try {
        var cacheData = index_1.appCache.get(req.headers.cachekey);
        if (cacheData && cacheEnabled) {
            return cacheData;
        }
        else {
            next();
        }
    }
    catch (error) {
        // error.status = 400;
        // error.code = "MISSING_PARAMETERS_IN_REQUESTINFO";
        (0, index_1.errorResponder)(error, req, res, next);
    }
};
exports.default = cacheMiddleware;
