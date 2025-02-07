import { addBoundariesInProjectCampaign, getCampaignProjects, updateTargetsInProjectCampaign } from "../utils/projectCampaignUtils";
import { getAllBoundariesForCampaign } from "../utils/boundaryUtils";
import { getTargetListForCampaign, persistForProjectProcess } from "../utils/targetUtils";
import { getRootBoundaryCode, getRootBoundaryType } from "../utils/campaignUtils";
import { campaignProcessStatus, mappingTypes, processNamesConstantsInOrder } from "../config/constants";
import { markProcessStatus } from "../utils/processTrackUtils";
import { getAllCampaignEmployeesWithJustMobileNumbers, getCampaignEmployees, getEmployeeListForCampaignDetails, getMobileNumbersAndCampaignEmployeeMappingFromCampaignEmployees, persistForActiveBoundariesFromEmployeeList, persistForActiveEmployees, persistForEmployeeCreationProcess, persistForInActiveBoundariesFromEmployeeList, persistForInactiveEmployees } from "../utils/campaignEmployeesUtils";
import { getCampaignMappings } from "../utils/campaignMappingUtils";
import { logger } from "../utils/logger";




const processProjectCreation = async (campaignDetailsAndRequestInfo: any) => {
    try {
        const { CampaignDetails, RequestInfo } = campaignDetailsAndRequestInfo;
        const userUuid = RequestInfo?.userInfo?.uuid;
        const { campaignNumber, boundaries, tenantId } = CampaignDetails;
        await markProcessStatus(campaignNumber, processNamesConstantsInOrder.projectCreation, campaignProcessStatus.started);
        const allTargetList = await getTargetListForCampaign(CampaignDetails);
        const allBoundaries = await getAllBoundariesForCampaign(CampaignDetails);
        const campaignProjects: any[] = await getCampaignProjects(campaignNumber, true);
        await updateTargetsInProjectCampaign(allTargetList, campaignProjects);
        await addBoundariesInProjectCampaign(allBoundaries, allTargetList, campaignNumber, userUuid, campaignProjects);
        const rootBoundaryCode = getRootBoundaryCode(boundaries);
        await persistForProjectProcess([rootBoundaryCode], campaignNumber, tenantId, userUuid, null);
        // wait for 5 seconds for confirmed persistence
        logger.info("Waiting for 5 seconds for confirmed persistence to start project creation...");
        await new Promise(resolve => setTimeout(resolve, 5000));
    } catch (error: any) {
        console.log(error);
        await markProcessStatus(campaignDetailsAndRequestInfo?.CampaignDetails?.campaignNumber, processNamesConstantsInOrder.projectCreation, campaignProcessStatus.failed, error?.message);
    }
};

const processEmployeeCreation = async (campaignDetailsAndRequestInfo: any) => {
    try {
        const { CampaignDetails, RequestInfo } = campaignDetailsAndRequestInfo;
        const userUuid = RequestInfo?.userInfo?.uuid;
        const { campaignNumber, tenantId, hierarchyType, boundaries } = CampaignDetails;
        await markProcessStatus(campaignNumber, processNamesConstantsInOrder.employeeCreation, campaignProcessStatus.started);
        const allEmployeeList = await getEmployeeListForCampaignDetails(CampaignDetails);
        const campaignEmployees = await getCampaignEmployees(campaignNumber, false);
        const campaignEmployeesWithJustMobileNumbers = await getAllCampaignEmployeesWithJustMobileNumbers(allEmployeeList?.map((employee: any) => employee?.user?.mobileNumber));
        const mobileNumbersAndCampaignEmployeeMapping = getMobileNumbersAndCampaignEmployeeMappingFromCampaignEmployees(campaignEmployeesWithJustMobileNumbers);
        await persistForActiveEmployees(allEmployeeList, campaignEmployees, campaignNumber, userUuid, mobileNumbersAndCampaignEmployeeMapping);
        await persistForInactiveEmployees(allEmployeeList, campaignEmployees, campaignNumber, userUuid);
        const campaignMappings = await getCampaignMappings(campaignNumber, mappingTypes.staff);
        await persistForActiveBoundariesFromEmployeeList(allEmployeeList, campaignMappings, campaignNumber, userUuid);
        await persistForInActiveBoundariesFromEmployeeList(allEmployeeList, campaignMappings, campaignNumber, userUuid);
        const rootBoundaryCode = getRootBoundaryCode(boundaries);
        const rootBoundaryType = getRootBoundaryType(boundaries);
        await persistForEmployeeCreationProcess(allEmployeeList, campaignNumber, tenantId, userUuid, hierarchyType, rootBoundaryCode, rootBoundaryType);
        // wait for 5 seconds for confirmed persistence
        logger.info("Waiting for 5 seconds for confirmed persistence to start employee creation...");
        await new Promise(resolve => setTimeout(resolve, 5000));
    } catch (error: any) {
        console.log(error);
        await markProcessStatus(campaignDetailsAndRequestInfo?.CampaignDetails?.campaignNumber, processNamesConstantsInOrder.employeeCreation, campaignProcessStatus.failed, error?.message);
    }
}
export {
    processProjectCreation,
    processEmployeeCreation
}
