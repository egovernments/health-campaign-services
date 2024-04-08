"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.producer = void 0;
var config_1 = __importDefault(require("../config"));
var kafka_node_1 = require("kafka-node");
var kafkaClient = new kafka_node_1.KafkaClient({
    kafkaHost: config_1.default.KAFKA_BROKER_HOST,
    connectRetryOptions: { retries: 1 },
});
var producer = new kafka_node_1.Producer(kafkaClient, { partitionerType: 2 });
exports.producer = producer;
producer.on('ready', function () {
    console.log('Producer is ready');
});
producer.on('error', function (err) {
    console.log('Producer is in error state');
    console.log(err.stack || err);
});
