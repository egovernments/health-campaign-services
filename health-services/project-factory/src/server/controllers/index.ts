import { listener } from "../kafka/Listener";
import campaignManageController from "./campaignManage/campaignManage.controller";
import dataManageController from "./dataManage/dataManage.controller";

listener();

const controllers = [
  new campaignManageController(),
  new dataManageController()
]

export default controllers;