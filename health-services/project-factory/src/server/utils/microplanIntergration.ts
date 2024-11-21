import {
  callMdmsTypeSchema,
  createAndUploadFile,
  getSheetData,
} from "../api/genericApis";
import { getFormattedStringForDebug, logger } from "./logger";

import { createDataService } from "../service/dataManageService";
import { updateProjectTypeCampaignService } from "../service/campaignManageService";
import {
  searchPlan,
  searchPlanCensus,
  searchPlanFacility,
} from "../api/microplanApis";
import { getTheGeneratedResource } from "./pollUtils";
import { getExcelWorkbookFromFileURL } from "./excelUtils";
import { fetchFileFromFilestore } from "../api/coreApis";
import { getLocalizedName } from "./campaignUtils";
import config from "../config";
import { throwError } from "./genericUtils";

const getPlanFacilityMapByFacilityId = (planFacilityArray: any = []) => {
  return planFacilityArray?.reduce((acc: any, curr: any) => {
    acc[curr?.facilityId] = curr;
    return acc;
  }, {});
};

const getPlanCensusMapByBoundaryCode = (censusArray: any = []) => {
    return censusArray?.reduce((acc: any, curr: any) => {
      acc[curr?.boundaryCode] = curr;
      return acc;
    }, {});
  };
  
export const fetchFacilityData = async (request: any, localizationMap: any) => {
  const { tenantId, planConfigurationId, campaignId } =
    request.body.MicroplanDetails;
  const planFacilityResponse = await searchPlanFacility(
    planConfigurationId,
    tenantId
  );
  const facilityBoundaryMap =
    getPlanFacilityMapByFacilityId(planFacilityResponse);
  logger.debug(
    `created facilityBoundaryMap :${getFormattedStringForDebug(
      facilityBoundaryMap
    )}`
  );

  const generatedFacilityTemplateFileStoreId = await getTheGeneratedResource(
    campaignId,
    tenantId,
    "facilityWithBoundary",
    request.body.CampaignDetails?.hierarchyType
  );
  logger.debug(
    `downloadresponse fetchFacilityData ${getFormattedStringForDebug(
      generatedFacilityTemplateFileStoreId
    )}`
  );
  const fileUrl = await fetchFileFromFilestore(
    generatedFacilityTemplateFileStoreId,
    tenantId
  );
  logger.debug(
    `downloadresponse fileUrl ${getFormattedStringForDebug(fileUrl)}`
  );
  const workbook = await getExcelWorkbookFromFileURL(
    fileUrl,
    getLocalizedName(config?.facility?.facilityTab, localizationMap)
  );

  const updatedWorksheet = await findAndChangeFacilityData(
    workbook.getWorksheet(
      getLocalizedName(config?.facility?.facilityTab, localizationMap)
    ),
    facilityBoundaryMap
  );
  const responseData =
    updatedWorksheet && (await createAndUploadFile(workbook, request));
  logger.info("facility File updated successfully:" + JSON.stringify(responseData));
  if (responseData?.[0]?.fileStoreId) {
      logger.info("facility File updated successfully:" + JSON.stringify(responseData?.[0]?.fileStoreId));

  } else {
    throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
  }

  // const resourceDetails = await validateFacilitySheet(request);
  // logger.info(`updated the resources of facility resource id ${resourceDetails?.id}`);
  // await updateCampaignDetails(request, resourceDetails?.id);
  logger.info(`updated the resources of facility`);
};

export const fetchTargetData = async (request: any, localizationMap: any) => {
  const { tenantId, planConfigurationId, campaignId } =
    request.body.MicroplanDetails;
  const planCensusResponse = await searchPlanCensus(
    planConfigurationId,
    tenantId,
    getBoundariesFromCampaign(request.body.CampaignDetails)?.length
  );
  console.log(planCensusResponse, "planCensusResponse");
  const targetBoundaryMap =
  getPlanCensusMapByBoundaryCode(planCensusResponse);
  logger.debug(
    `created targetBoundaryMap :${getFormattedStringForDebug(
        targetBoundaryMap
    )}`
  );
  

  const generatedTargetTemplateFileStoreId = await getTheGeneratedResource(
    campaignId,
    tenantId,
    "boundary",
    request.body.CampaignDetails?.hierarchyType
  );
  logger.debug(
    `downloadresponse target ${getFormattedStringForDebug(
        generatedTargetTemplateFileStoreId
    )}`
  );
  const fileUrl = await fetchFileFromFilestore(
    generatedTargetTemplateFileStoreId,
    tenantId
  );
  logger.debug(
    `downloadresponse target ${getFormattedStringForDebug(fileUrl)}`
  );


  

  const workbook = await getExcelWorkbookFromFileURL(fileUrl);

  await workbook.worksheets.forEach(async (worksheet) => {
    console.log(`Processing worksheet: ${worksheet.name}`);
      
    if(worksheet.name!="Read Me" && worksheet.name!="Boundary Data"){
    // Iterate over rows (skip the header row)
     await findAndChangeTargetData(
        worksheet,
        targetBoundaryMap
      );
    }
  });


  const responseData = (await createAndUploadFile(workbook, request));

logger.info("Target File updated successfully:" + JSON.stringify(responseData));
if (responseData?.[0]?.fileStoreId) {
    logger.info("Target File updated successfully:" + JSON.stringify(responseData?.[0]?.fileStoreId));

} else {
  throwError("FILE", 500, "STATUS_FILE_CREATION_ERROR");
}
  // const resourceDetails = await validateFacilitySheet(request);
  // logger.info(`updated the resources of facility resource id ${resourceDetails?.id}`);
  // await updateCampaignDetails(request, resourceDetails?.id);
  logger.info(`updated the resources of target`);
};

function findAndChangeFacilityData(worksheet: any, mappingData: any) {
  logger.info(
    `Received for facility mapping, enitity count : ${Object.keys(mappingData)?.length}`
  );
  const mappedData: any = {};
  // Iterate through rows in Sheet1 (starting from row 2 to skip the header)
  worksheet.eachRow((row: any, rowIndex: number) => {
    if (rowIndex === 1) return; // Skip the header row
    const column1Value = row.getCell(1).value; // Get the value from column 1
    if (mappingData?.[column1Value]) {
      // Update columns 5 and 6 if column 1 value matches
      row.getCell(6).value =
        mappingData?.[column1Value]?.["serviceBoundaries"]?.join(","); // Set "BoundaryCode" in column 5
      row.getCell(7).value = "Active"; // Set "Status" in column 6
      mappedData[column1Value] = rowIndex;
    } else {
      // Default values for other rows
      row.getCell(6).value = "";
      row.getCell(7).value = "Inactive";
    }
  });
  logger.info(
    `Updated the boundary & active/inactive status information in facility received from the microplan`
  );
  logger.info(
    `mapping completed for facility enitity count : ${Object.keys(mappedData)?.length}`
  );
  logger.info(
    `mapping not found for facility entity count : ${
      Object.keys(mappingData)?.length - Object.keys(mappedData)?.length
    }`
  );
  return worksheet;
}
function findAndChangeTargetData(worksheet: any, mappingData: any) {
    logger.info(
      `Received for Target mapping, enitity count : ${Object.keys(mappingData)?.length}`
    );
    const mappedData: any = {};
    // Iterate through rows in Sheet1 (starting from row 2 to skip the header)
    worksheet.eachRow((row: any, rowIndex: number) => {
      if (rowIndex === 1) return; // Skip the header row      
      const column1Value = row.getCell(5).value; // Get the value from column 1
      if (mappingData?.[column1Value]) {
        // Update columns 5 and 6 if column 1 value matches
        row.getCell(6).value =
          mappingData?.[column1Value]?.["additionalDetails"]?.["CONFIRMED_HCM_ADMIN_CONSOLE_TARGET_POPULATION"]; // Set "BoundaryCode" in column 5
        // row.getCell(7).value = "Active"; // Set "Status" in column 6
        mappedData[column1Value] = rowIndex;
      } else {
        logger.info(
            `not doing anything if taregt cel not found`
          );
        // Default values for other rows
        // row.getCell(6).value = "";
        // row.getCell(7).value = "Inactive";
      }
    });
    logger.info(
      `Updated the boundary & active/inactive status information in Target received from the microplan`
    );
    logger.info(
      `mapping completed for Target enitity count : ${Object.keys(mappedData)?.length}`
    );
    logger.info(
      `mapping not found for Target entity count : ${
        Object.keys(mappingData)?.length - Object.keys(mappedData)?.length
      }`
    );
    return worksheet;
  }

const getBoundariesFromCampaign = (CampaignDetails: any = {}) => {
  logger.info("fetching all boundaries in that CampaignDetails");
  const boundaries = CampaignDetails?.boundaries?.map((obj: any) => obj?.code);
  logger.debug(
    `boundaries in that CampaignDetails are :${getFormattedStringForDebug(
      boundaries
    )}`
  );
  return boundaries;
};

export const fetchUserData = async (request: any, localizationMap: any) => {
  const { tenantId, planConfigurationId, campaignId } =
    request.body.MicroplanDetails;

  const planResponse = await searchPlan(
    planConfigurationId,
    tenantId,
    getBoundariesFromCampaign(request.body.CampaignDetails)?.length
  );
  console.log(planResponse, "planResponse");
  const facilityBoundaryMap = getPlanFacilityMapByFacilityId(planResponse);
  logger.debug(
    `created facilityBoundaryMap :${getFormattedStringForDebug(
      facilityBoundaryMap
    )}`
  );

  const generatedFacilityTemplateFileStoreId = await getTheGeneratedResource(
    campaignId,
    tenantId,
    "userWithBoundary",
    request.body.CampaignDetails?.hierarchyType
  );
  logger.debug(
    `downloadresponse userWithBoundary ${getFormattedStringForDebug(
      generatedFacilityTemplateFileStoreId
    )}`
  );
  const fileUrl = await fetchFileFromFilestore(
    generatedFacilityTemplateFileStoreId,
    tenantId
  );
  logger.debug(
    `downloadresponse userWithBoundary ${getFormattedStringForDebug(fileUrl)}`
  );
  const workbook = await getExcelWorkbookFromFileURL(
    fileUrl,
    getLocalizedName(config?.user.userTab, localizationMap)
  );
  console.log(workbook, "workbook fetchUserData");

  const facilitySheetId = request.body.planConfig.files.find(
    (file: { templateIdentifier: string }) =>
      file.templateIdentifier === "Facilities"
  )?.filestoreId;
  logger.info(`found facilitySheetId is ${facilitySheetId}`);

  var facilitySheetIdboundaryData = await getSheetData(
    fileUrl,
    getLocalizedName(config?.user.userTab, localizationMap),
    false,
    undefined,
    localizationMap
  );
  console.log(facilitySheetIdboundaryData);

  const schema = await callMdmsTypeSchema(
    request,
    tenantId,
    true,
    "facility",
    "all"
  );
  request.query.type = "facilityWithBoundary";
  request.query.tenantId = tenantId;
  console.log(schema, "schema");
  // const downloadresponse=await getTheGeneratedResource(campaignId,tenantId,"userWithBoundary",request.body.CampaignDetails?.hierarchyType)
  // logger.debug(`downloadresponse userWithBoundary ${getFormattedStringForDebug(downloadresponse)}`)

  // const resourceDetails = await validateFacilitySheet(request);
  // logger.info(`updated the resources of facility resource id ${resourceDetails?.id}`);
  // await updateCampaignDetails(request, resourceDetails?.id);
  logger.info(`updated the resources of facility`);
};

export async function updateCampaignDetails(
  request: any,
  resourceDetailsIdForFacility: any
) {
  const { resources } = request.body.CampaignDetails || {};
  const { fileDetails } = request.body;

  if (
    Array.isArray(resources) &&
    Array.isArray(fileDetails) &&
    fileDetails[0]?.fileStoreId
  ) {
    let facilityFound = false;
    /* The above TypeScript code initializes a variable `targetFound` with a value of `false`. */
    // let targetFound = false;

    resources.forEach((resource: any) => {
      if (resource.type === "facility") {
        resource.filestoreId = fileDetails[0].fileStoreId;
        resource.resourceId = resourceDetailsIdForFacility;
        console.log(
          `Updated facility resource with filestoreId: ${resource.filestoreId}`
        );
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
        type: "facility",
        filename: "Facility Template (29).xlsx",
        filestoreId: fileDetails[0].fileStoreId,
        resourceId: resourceDetailsIdForFacility,
      });
      console.log("Appended new facility resource");
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
    console.error(
      "Invalid structure in CampaignDetails or fileDetails. Ensure both are non-empty arrays."
    );
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
    additionalDetails: {},
  };
  const resourceDetails = await createDataService(request);
  return resourceDetails;
}
