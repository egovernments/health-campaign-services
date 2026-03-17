import { listener } from "../kafka/Listener";
import campaignManageController from "./campaignManage/campaignManage.controller";
import dataManageController from "./dataManage/dataManage.controller";
import sheetManageController from "./sheetManage/sheetManage.controller";
import CryptoController from "./cryptoController/crypto.controller";
import ResourceDetailsController from "./resourceDetails/resourceDetails.controller";

listener();

const controllers = [
  new campaignManageController(),
  new dataManageController(),
  new sheetManageController(),
  new CryptoController(),
  new ResourceDetailsController()
]

export default controllers;