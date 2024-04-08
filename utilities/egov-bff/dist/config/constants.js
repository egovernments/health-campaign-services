"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getErrorCodes = exports.CONSTANTS = void 0;
exports.CONSTANTS = {
    ERROR_CODES: {
        WORKS: {
            NO_MUSTER_ROLL_FOUND: "No Muster Roll Found for given Criteria",
            NO_CONTRACT_FOUND: "No Contract Found for given Criteria",
            NO_ESTIMATE_FOUND: "No Estimate Found for given Criteria",
            NO_MEASUREMENT_ROLL_FOUND: "No Measurement Found for given Criteria"
        }
    }
};
var getErrorCodes = function (module, key) {
    var _a, _b;
    return {
        code: key,
        notFound: true,
        message: (_b = (_a = exports.CONSTANTS.ERROR_CODES) === null || _a === void 0 ? void 0 : _a[module]) === null || _b === void 0 ? void 0 : _b[key]
    };
};
exports.getErrorCodes = getErrorCodes;
