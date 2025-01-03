import { useMutation } from "react-query";
import UpdatePlanConfig from "../services/UpdatePlanConfig";

const useUpdatePlanConfig = () => {
  return useMutation(data => UpdatePlanConfig(data))
}

export default useUpdatePlanConfig; 