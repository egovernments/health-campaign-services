import { listener } from "../kafka/Listener";
import campaignManageController from "./campaignManage/campaignManage.controller";
import dataManageController from "./dataManage/dataManage.controller";
import sheetManageController from "./sheetManage/sheetManage.controller";
import CryptoController from "./cryptoController/crypto.controller";

listener();

const controllers = [
  new campaignManageController(),
  new dataManageController(),
  new sheetManageController(),
  new CryptoController()
]

export default controllers;