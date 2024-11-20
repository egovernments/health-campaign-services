

import { callMdmsTypeSchema } from "../api/genericApis";
import { getFormattedStringForDebug, logger } from "./logger";


import { createDataService } from "../service/dataManageService";
import { updateProjectTypeCampaignService } from "../service/campaignManageService";
import { searchPlan, searchPlanCensus, searchPlanFacility } from "../api/microplanApis";
import { getTheGeneratedResource } from "./pollUtils";
import { getExcelWorkbookFromFileURL } from "./excelUtils";
import { fetchFileFromFilestore } from "../api/coreApis";



// function getPlanFacilityMap(planFacility: any) {
//     const planFacilityMap = new Map();
//     planFacility.forEach((facility: any) => {
//         planFacilityMap.set(facility.facilityId, facility.serviceBoundaries.join(','));
//     });
//     return planFacilityMap;
// }

 
// const getSheetDataMP = async (
//     fileUrl: string,
//     sheetName: string,
//     getRow = false,
//     createAndSearchConfig?: any,
//     localizationMap?: { [key: string]: string }
// ) => {
//     // Retrieve workbook using the getExcelWorkbookFromFileURL function
//     const localizedSheetName = getLocalizedName(sheetName, localizationMap);
//     const workbook: any = await getExcelWorkbookFromFileURL(fileUrl, localizedSheetName);

//     const worksheet: any = workbook.getWorksheet(localizedSheetName);

//     // If parsing array configuration is provided, validate first row of each column
//     // validateFirstRowColumn(createAndSearchConfig, worksheet, localizationMap);

//     // Collect sheet data by iterating through rows and cells
//     const sheetData = getSheetDataFromWorksheet(worksheet);
//     return sheetData;
// };




const getPlanFacilityMapByFacilityId =(planFacilityArray:any=[])=>{
    return planFacilityArray?.reduce((acc:any,curr:any)=>{
        acc[curr?.facilityId]=curr;
        return acc;
    },{})
}



export  const fetchFacilityData=async(request:any,localizationMap:any)=>{
    const { tenantId ,planConfigurationId,campaignId} = request.body.MicroplanDetails;
    const planFacilityResponse = await searchPlanFacility(planConfigurationId,tenantId);
    console.log(planFacilityResponse,'planFacilityResponse');
    const facilityBoundaryMap=getPlanFacilityMapByFacilityId(planFacilityResponse);
    logger.debug(`created facilityBoundaryMap :${getFormattedStringForDebug(facilityBoundaryMap)}`)
    

    const generatedFacilityTemplateFileStoreId=await getTheGeneratedResource(campaignId,tenantId,"facilityWithBoundary",request.body.CampaignDetails?.hierarchyType)
    logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(generatedFacilityTemplateFileStoreId)}`)
    const fileUrl=await fetchFileFromFilestore(generatedFacilityTemplateFileStoreId,tenantId);
    logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(fileUrl)}`);
    const workbook=getExcelWorkbookFromFileURL(fileUrl);
    console.log(workbook,'workbook');

    const facilitySheetId = request.body.planConfig.files.find((file: { templateIdentifier: string; }) => file.templateIdentifier === "Facilities")?.filestoreId;
    logger.info(`found facilitySheetId is ${facilitySheetId}`);
    
    const schema = await callMdmsTypeSchema(request, tenantId, true, "facility", "all");
    request.query.type = "facilityWithBoundary";
    request.query.tenantId = tenantId;
    console.log(schema,'schema');
    
    
    // const resourceDetails = await validateFacilitySheet(request);
    // logger.info(`updated the resources of facility resource id ${resourceDetails?.id}`);
    // await updateCampaignDetails(request, resourceDetails?.id);
    logger.info(`updated the resources of facility`);
}



export  const fetchTargetData=async(request:any,localizationMap:any)=>{
    const { tenantId ,planConfigurationId,campaignId} = request.body.MicroplanDetails;
    const planCensusResponse = await searchPlanCensus(planConfigurationId,tenantId,getBoundariesFromCampaign(request.body.CampaignDetails)?.length);
    console.log(planCensusResponse,'planCensusResponse');
    const facilityBoundaryMap=getPlanFacilityMapByFacilityId(planCensusResponse);
    logger.debug(`created facilityBoundaryMap :${getFormattedStringForDebug(facilityBoundaryMap)}`)
    

    const generatedFacilityTemplateFileStoreId=await getTheGeneratedResource(campaignId,tenantId,"boundary",request.body.CampaignDetails?.hierarchyType)
    logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(generatedFacilityTemplateFileStoreId)}`)
    const fileUrl=await fetchFileFromFilestore(generatedFacilityTemplateFileStoreId,tenantId);
    logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(fileUrl)}`);
    const workbook=getExcelWorkbookFromFileURL(fileUrl);
    console.log(workbook,'workbook');



    const facilitySheetId = request.body.planConfig.files.find((file: { templateIdentifier: string; }) => file.templateIdentifier === "Facilities")?.filestoreId;
    logger.info(`found facilitySheetId is ${facilitySheetId}`);
    
    const schema = await callMdmsTypeSchema(request, tenantId, true, "facility", "all");
    request.query.type = "facilityWithBoundary";
    request.query.tenantId = tenantId;
    console.log(schema,'schema');

    // const resourceDetails = await validateFacilitySheet(request);
    // logger.info(`updated the resources of facility resource id ${resourceDetails?.id}`);
    // await updateCampaignDetails(request, resourceDetails?.id);
    logger.info(`updated the resources of facility`);
}

const getBoundariesFromCampaign=(CampaignDetails:any={})=>{
    logger.info("fetching all boundaries in that CampaignDetails");
    const boundaries=CampaignDetails?.boundaries?.map((obj:any)=>obj?.code)
    logger.debug(`boundaries in that CampaignDetails are :${getFormattedStringForDebug(boundaries)}`)
    return boundaries;
}


export  const fetchUserData=async(request:any,localizationMap:any)=>{
    const { tenantId ,planConfigurationId,campaignId} = request.body.MicroplanDetails;

    const planResponse = await searchPlan(planConfigurationId,tenantId,getBoundariesFromCampaign(request.body.CampaignDetails)?.length);
    console.log(planResponse,'planResponse');
    const facilityBoundaryMap=getPlanFacilityMapByFacilityId(planResponse);
    logger.debug(`created facilityBoundaryMap :${getFormattedStringForDebug(facilityBoundaryMap)}`)
    




    const generatedFacilityTemplateFileStoreId=await getTheGeneratedResource(campaignId,tenantId,"userWithBoundary",request.body.CampaignDetails?.hierarchyType)
    logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(generatedFacilityTemplateFileStoreId)}`)


    const facilitySheetId = request.body.planConfig.files.find((file: { templateIdentifier: string; }) => file.templateIdentifier === "Facilities")?.filestoreId;
    logger.info(`found facilitySheetId is ${facilitySheetId}`);




    
    const schema = await callMdmsTypeSchema(request, tenantId, true, "facility", "all");
    request.query.type = "facilityWithBoundary";
    request.query.tenantId = tenantId;
    console.log(schema,'schema');
    // const downloadresponse=await getTheGeneratedResource(campaignId,tenantId,"userWithBoundary",request.body.CampaignDetails?.hierarchyType)
    // logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(downloadresponse)}`)
    

    // const resourceDetails = await validateFacilitySheet(request);
    // logger.info(`updated the resources of facility resource id ${resourceDetails?.id}`);
    // await updateCampaignDetails(request, resourceDetails?.id);
    logger.info(`updated the resources of facility`);
}










export async function updateCampaignDetails(request: any, resourceDetailsIdForFacility: any) {
    const { resources } = request.body.CampaignDetails || {};
    const { fileDetails } = request.body;

    if (Array.isArray(resources) && Array.isArray(fileDetails) && fileDetails[0]?.fileStoreId) {
        let facilityFound = false;
        /* The above TypeScript code initializes a variable `targetFound` with a value of `false`. */
        // let targetFound = false;

    resources.forEach((resource: any) => {
        if (resource.type === 'facility') {
            resource.filestoreId = fileDetails[0].fileStoreId;
            resource.resourceId = resourceDetailsIdForFacility;
            console.log(`Updated facility resource with filestoreId: ${resource.filestoreId}`);
            facilityFound = true;
        }

        // if(resource.type === 'boundaryWithTarget'){
        //     resource.filestoreId = request.body.targetFileId;
        //     resource.resourceId = resourseDetailsForTarget;
        //     console.log(`Updated facility resource with filestoreId: ${resource.filestoreId}`);
        //     targetFound = true;
        // }
    });

    if (!facilityFound) {
        // Append a new object if no 'facility' resource was found
        resources.push({
            type: 'facility',
            filename: 'Facility Template (29).xlsx',
            filestoreId: fileDetails[0].fileStoreId,
            resourceId: resourceDetailsIdForFacility
        });
        console.log('Appended new facility resource');
    }
    // if(!targetFound){
    //     resources.push({
    //         type: 'boundaryWithTarget',
    //         filename: 'Boundary Template (29).xlsx',
    //         filestoreId: request.body.targetFileId,
    //         resourceId: resourseDetailsForTarget
    //     })
    // }
    } else {
        console.error("Invalid structure in CampaignDetails or fileDetails. Ensure both are non-empty arrays.");
    }
    // Get the current date
    const currentDate = new Date();

    // Set to the next day
    const nextDay = new Date(currentDate);
    nextDay.setDate(currentDate.getDate() + 1);

    const newEndDate = new Date(currentDate);
    newEndDate.setDate(currentDate.getDate() + 3);

    // Convert to epoch time in milliseconds
    const nextDayEpoch = nextDay.getTime();
    const newEndDateEpoch = newEndDate.getTime();
    request.body.CampaignDetails.startDate = nextDayEpoch;
    request.body.CampaignDetails.endDate = newEndDateEpoch;
    await updateProjectTypeCampaignService(request);

}

export async function validateFacilitySheet(request: any) {
    const { tenantId } = request.body.MicroplanDetails;
    request.body.ResourceDetails = {
        tenantId: tenantId,
        type: "facility",
        fileStoreId: request.body.fileDetails[0].fileStoreId,
        action: "validate",
        campaignId: request.body.MicroplanDetails.campaignId,
        hierarchyType: request.body.CampaignDetails.hierarchyType,
        additionalDetails: {}
    };
    const resourceDetails = await createDataService(request);
    return resourceDetails;
}
