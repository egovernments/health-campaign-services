import { addBoundariesInProjectCampaign, getCampaignProjects, updateTargetsInProjectCampaign } from "../utils/projectCampaignUtils";
import { getAllBoundariesForCampaign } from "../utils/boundaryUtils";
import { getTargetListForCampaign, persistForProjectProcess } from "../utils/targetUtils";
import { getRootBoundaryCode, getRootBoundaryType } from "../utils/campaignUtils";
import { campaignProcessStatus, mappingTypes, processNamesConstantsInOrder } from "../config/constants";
import { markProcessStatus } from "../utils/processTrackUtils";
import { getAllCampaignEmployeesWithJustMobileNumbers, getCampaignEmployees, getEmployeeListForCampaignDetails, getMobileNumbersAndCampaignEmployeeMappingFromCampaignEmployees, persistForActiveBoundariesFromEmployeeList, persistForActiveEmployees, persistForEmployeeCreationProcess, persistForInActiveBoundariesFromEmployeeList, persistForInactiveEmployees } from "../utils/campaignEmployeesUtils";
import { getCampaignMappings, getPvarIds } from "../utils/campaignMappingUtils";
import { logger } from "../utils/logger";
import { getCampaignFacilities, getFacilityListFromCampaignDetails, persistForActiveBoundariesFromFacilityList, persistForActiveFacilities, persistForFacilityCreationProcess, persistForInActiveBoundariesFromFacilityList, persistForInactiveFacilities } from "../utils/campaignFacilitiesUtils";
import { modifyAndPushInKafkaForFacilityMapping, modifyAndPushInKafkaForResourceMapping, modifyAndPushInKafkaForStaffMapping, persistNewActiveCampaignMappingForResources } from "../utils/campaignMappingProcessUtils";




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
        const campaignMappingsForResources = await getCampaignMappings(campaignNumber, mappingTypes.resource);
        const pvarIds = getPvarIds(campaignDetailsAndRequestInfo);
        await persistNewActiveCampaignMappingForResources(campaignMappingsForResources, allBoundaries, campaignNumber, userUuid, pvarIds);
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
        await persistForInactiveEmployees(allEmployeeList, campaignEmployees, mobileNumbersAndCampaignEmployeeMapping, campaignNumber, userUuid);
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

const processFacilityCreation = async (campaignDetailsAndRequestInfo: any) => {
    try {
        const { CampaignDetails, RequestInfo } = campaignDetailsAndRequestInfo;
        const { tenantId } = CampaignDetails;
        const userUuid = RequestInfo?.userInfo?.uuid;
        const { campaignNumber } = CampaignDetails;
        await markProcessStatus(campaignNumber, processNamesConstantsInOrder.facilityCreation, campaignProcessStatus.started);
        const allFacilityList = await getFacilityListFromCampaignDetails(CampaignDetails);
        const campaignFacilities = await getCampaignFacilities(campaignNumber, false);
        await persistForActiveFacilities(allFacilityList, campaignFacilities, campaignNumber, userUuid);
        await persistForInactiveFacilities(allFacilityList, campaignFacilities, campaignNumber, userUuid);
        const campaignMappings = await getCampaignMappings(campaignNumber, mappingTypes.facility);
        await persistForActiveBoundariesFromFacilityList(allFacilityList, campaignMappings, campaignNumber, userUuid);
        await persistForInActiveBoundariesFromFacilityList(allFacilityList, campaignMappings, campaignNumber, userUuid);
        await persistForFacilityCreationProcess(allFacilityList, campaignNumber, tenantId, userUuid);
        // // wait for 5 seconds for confirmed persistence
        logger.info("Waiting for 5 seconds for confirmed persistence to start facility creation...");
        await new Promise(resolve => setTimeout(resolve, 5000));
    } catch (error: any) {
        console.log(error);
        await markProcessStatus(campaignDetailsAndRequestInfo?.CampaignDetails?.campaignNumber, processNamesConstantsInOrder.facilityCreation, campaignProcessStatus.failed, error?.message);
    }
}

const processCampaignMappings = async (campaignDetailsAndRequestInfo: any) => {
    try {
        // wait for 5 seconds before processing the mappings
        logger.info("Waiting for 5 seconds before processing the mappings...");
        await new Promise((resolve) => setTimeout(resolve, 5000));
        const { CampaignDetails , RequestInfo} = campaignDetailsAndRequestInfo;
        const userUuid = RequestInfo?.userInfo?.uuid;
        const { campaignNumber, tenantId } = CampaignDetails;
        const campaignProjects = await getCampaignProjects(campaignNumber, false);
        const boundaryCodeAndProjectIdMapping = campaignProjects?.reduce((acc: any, curr: any) => {
            acc[curr?.boundaryCode] = curr?.projectId;
            return acc;
        }, {})

        // resource mapping
        logger.info("Processing resource mapping...");
        const campaignMappingsForResources = await getCampaignMappings(campaignNumber, mappingTypes.resource);
        await modifyAndPushInKafkaForResourceMapping(campaignMappingsForResources, boundaryCodeAndProjectIdMapping, campaignNumber, tenantId, userUuid);

        // staff mapping
        logger.info("Processing staff mapping...");
        const campaignMappingsForStaff = await getCampaignMappings(campaignNumber, mappingTypes.staff);
        const campaignEmployees = await getCampaignEmployees(campaignNumber, false);
        await modifyAndPushInKafkaForStaffMapping(campaignMappingsForStaff, campaignEmployees, boundaryCodeAndProjectIdMapping, campaignNumber, tenantId, userUuid);

        // facility mapping
        logger.info("Processing facility mapping...");
        const campaignMappingsForFacility = await getCampaignMappings(campaignNumber, mappingTypes.facility);
        const campaignFacilities = await getCampaignFacilities(campaignNumber, false);
        await modifyAndPushInKafkaForFacilityMapping(campaignMappingsForFacility, campaignFacilities, boundaryCodeAndProjectIdMapping, campaignNumber, tenantId, userUuid);
    } catch (error: any) {
        console.log(error);
        await markProcessStatus(campaignDetailsAndRequestInfo?.CampaignDetails?.campaignNumber, processNamesConstantsInOrder.employeeCreation, campaignProcessStatus.failed, error?.message);
    }
}

export {
    processProjectCreation,
    processEmployeeCreation,
    processFacilityCreation,
    processCampaignMappings
}
