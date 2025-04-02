import { listener } from "../kafka/Listener";
import ApplicationController from "./Application/application.controller";
import SampleController from "./SampleManage/sampleManage.controller";

listener();

const controllers = [
  new SampleController(),
  new ApplicationController()
]

export default controllers;