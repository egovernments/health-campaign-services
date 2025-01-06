import { listener } from "../kafka/Listener";
import campaignManageController from "./campaignManage/campaignManage.controller";
import dataManageController from "./dataManage/dataManage.controller";
import templateManageController from "./templateManage/templateManage.controller";

listener();

const controllers = [
  new campaignManageController(),
  new dataManageController(),
  new templateManageController()
]

export default controllers;