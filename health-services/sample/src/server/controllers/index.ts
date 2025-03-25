import { listener } from "../kafka/Listener";
import SampleController from "./SampleManage/sampleManage.controller";

listener();

const controllers = [
  new SampleController()
]

export default controllers;