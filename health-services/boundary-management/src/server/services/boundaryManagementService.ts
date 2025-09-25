// import express from "express";
import {getLocalizedMessagesHandler,enrichResourceDetails} from "../utils/genericUtils";
import { getLocalisationModuleName } from "../utils/localisationUtils";
import { logger } from "../utils/logger";
import { validateProcessRequest } from "../validators/boundaryValidators";

const processBoundaryService = async (request: any) => {
  const hierarchyType = request?.body?.ResourceDetails?.hierarchyType;
  const localizationMapHierarchy = hierarchyType && await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId, getLocalisationModuleName(hierarchyType), true);
  const localizationMapModule = await getLocalizedMessagesHandler(request, request?.body?.ResourceDetails?.tenantId);
  const localizationMap = { ...(localizationMapHierarchy || {}), ...localizationMapModule };

  // Validate the create request
    logger.info("Validating data process request")
    await validateProcessRequest(request, localizationMap);
    logger.info("VALIDATED THE DATA PROCESS REQUEST");

    // Enrich resource details
    await enrichResourceDetails(request);
  

};

export { processBoundaryService };
