import config from "../config";
import { defaultRequestInfo } from "./coreApis";
import { httpRequest } from "../utils/request";

export async function createEmployeesAndGetMobileNumbersAndUserServiceUuidMapping(employeesCreateBody: any[]) {
    const createEmployeeRequestBody = {
        RequestInfo: defaultRequestInfo.RequestInfo,
        Employees: employeesCreateBody
    }
    const url = config.host.hrmsHost + config.paths.hrmsEmployeeCreate;
    const mobileNumbersAndUserServiceUuidMapping: any = {};
    const response = await httpRequest(url, createEmployeeRequestBody);
    if (response?.Employees?.length > 0) {
        for (const employee of response?.Employees) {
            mobileNumbersAndUserServiceUuidMapping[employee?.user?.mobileNumber] = employee?.user?.userServiceUuid;
        }
    }
    else {
        throw new Error("Failed to create employees");
    }
    return mobileNumbersAndUserServiceUuidMapping;
}