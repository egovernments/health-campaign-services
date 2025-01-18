import { addBoundariesInProjectCampaign, getCampaignProjects, updateTargetsInProjectCampaign } from "../utils/projectCampaignUtils";
import { getAllBoundariesForCampaign } from "../utils/boundaryUtils";
import {  getTargetListForCampaign, persistForProjectProcess } from "../utils/targetUtils";
import { getRootBoundaryCode } from "../utils/campaignUtils";
import { campaignProcessStatus, processNamesConstantsInOrder } from "../config/constants";
import { markProcessStatus } from "../utils/processTrackUtils";




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
    } catch (error: any) {
        console.log(error);
        await markProcessStatus(campaignDetailsAndRequestInfo?.CampaignDetails?.campaignNumber, processNamesConstantsInOrder.projectCreation, campaignProcessStatus.failed, error?.message);
    }
};

export {
    processProjectCreation
}
