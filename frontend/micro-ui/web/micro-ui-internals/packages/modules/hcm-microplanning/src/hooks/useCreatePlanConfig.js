import { useMutation } from "react-query";
import CreatePlanConfig from "../services/CreatePlanConfig";

const useCreatePlanConfig = () => {
  return useMutation(data => CreatePlanConfig(data))
}

export default useCreatePlanConfig; 