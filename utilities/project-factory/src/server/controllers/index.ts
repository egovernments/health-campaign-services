import campaignManageController from "./campaignManage/campaignManage.controller";
import dataManageController from "./dataManage/dataManage.controller";

const controllers = [
  new campaignManageController(),
  new dataManageController()
]

export default controllers;