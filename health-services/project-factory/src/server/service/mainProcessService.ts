import { addBoundariesInProjectCampaign, getCampaignProjects, updateTargetsInProjectCampaign } from "../utils/projectCampaignUtils";
import { getAllBoundariesForCampaign } from "../utils/boundaryUtils";
import {  getTargetListForCampaign, persistForProjectProcess } from "../utils/targetUtils";
import { getRootBoundaryCode, markProcessStatus } from "../utils/campaignUtils";
import { campaignProcessStatus, processNamesConstants } from "../config/constants";




const processProjectCreation = async (requestBody: any) => {
    try {
        const { CampaignDetails, RequestInfo } = requestBody;
        const { campaignNumber, boundaries, tenantId } = CampaignDetails;
        await markProcessStatus(campaignNumber, processNamesConstants.projectCreation, campaignProcessStatus.started);
        const allTargetList = await getTargetListForCampaign(CampaignDetails);
        const allBoundaries = await getAllBoundariesForCampaign(CampaignDetails);
        const campaignProjects: any[] = await getCampaignProjects(campaignNumber, true);
        await updateTargetsInProjectCampaign(allTargetList, campaignProjects);
        await addBoundariesInProjectCampaign(allBoundaries, allTargetList, campaignNumber, RequestInfo?.userInfo?.uuid, campaignProjects);
        const rootBoundaryCode = getRootBoundaryCode(boundaries);
        await persistForProjectProcess([rootBoundaryCode], campaignNumber, tenantId, null);
    } catch (error: any) {
        console.log(error);
        throw error;
    }
};

export {
    processProjectCreation
}
