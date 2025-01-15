import { listener } from "../kafka/Listener";
import campaignManageController from "./campaignManage/campaignManage.controller";
import dataManageController from "./dataManage/dataManage.controller";
import mainProcessController from "./mainProcessController/mainProcess.controller";

listener();

const controllers = [
  new campaignManageController(),
  new dataManageController(),
  new mainProcessController()
]

export default controllers;