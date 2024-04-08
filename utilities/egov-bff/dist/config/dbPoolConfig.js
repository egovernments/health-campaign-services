"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var pg_1 = require("pg");
var _1 = __importDefault(require("."));
var pool = new pg_1.Pool({
    user: _1.default.DB_USER,
    host: _1.default.DB_HOST,
    database: _1.default.DB_NAME,
    password: _1.default.DB_PASSWORD,
    port: parseInt(_1.default.DB_PORT)
});
exports.default = pool;
