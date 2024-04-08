"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var _a = require("yup"), object = _a.object, string = _a.string;
var errorResponder = require("../index").errorResponder;
var requestSchema = object({
    apiId: string().nullable(),
    action: string().nullable(),
    msgId: string().required(),
    authToken: string().nullable(),
    userInfo: object().nonNullable()
});
var requestMiddleware = function (req, res, next) {
    try {
        requestSchema.validateSync(req.body.RequestInfo);
        next();
    }
    catch (error) {
        // error.status = 400;
        // error.code = "MISSING_PARAMETERS_IN_REQUESTINFO";
        errorResponder(error, req, res);
    }
};
exports.default = requestMiddleware;
