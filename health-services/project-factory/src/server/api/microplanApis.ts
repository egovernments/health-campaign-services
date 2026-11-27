import { RequestInfo } from "../config/models/requestInfoSchema";
import config from "../config";
import { httpRequest } from "../utils/request";
import { logger } from "../utils/logger";

/**
 * Searches for facilities associated with a specific plan configuration.
 * @param planConfigId The unique identifier for the plan configuration.
 * @param tenantId The tenant identifier for which the search is performed.
 * @returns The response containing facility details for the specified plan configuration.
 */
export const searchPlanFacility = async (
  planConfigId: string,
  tenantId: string,
  requestInfo?: RequestInfo
) => {
  const searchBody = {
    PlanFacilitySearchCriteria: {
      tenantId: tenantId,
      planConfigurationId: planConfigId,
    },
    RequestInfo: requestInfo,
  };
  logger.info(
    `Received a search request for plan facility with ID: ${planConfigId}`
  );
  const planFacilityResponse = await httpRequest(
    config.host.planServiceHost + config.paths.planFacilitySearch,
    searchBody
  );
  return planFacilityResponse?.PlanFacility;
};

/**
 * Searches for plans based on configuration, tenant, and boundaries.
 * @param planConfigId The unique identifier for the plan configuration.
 * @param tenantId The tenant identifier for which the search is performed.
 * @param boundaries The jurisdiction or boundary information for the search.
 * @returns The response containing plan details for the specified criteria.
 */
export const searchPlan = async (
  planConfigId: string,
  tenantId: string,
  limit: number = 1,
  requestInfo?: RequestInfo
) => {
  const searchBody = {
    PlanSearchCriteria: {
      tenantId: tenantId,
      active: true,
      planConfigurationId: planConfigId,
      limit: limit,
      offset: 0,
    },
    RequestInfo: requestInfo,
  };
  logger.info(
    `Received a search request for plans with ID: ${planConfigId}`
  );
  const planResponse = await httpRequest(
    config.host.planServiceHost + config.paths.planSearch,
    searchBody
  );
  return planResponse?.Plan;
};

/**
 * Searches for census data related to a specific plan configuration and boundary codes.
 * @param planConfigId The unique identifier for the plan configuration.
 * @param tenantId The tenant identifier for which the search is performed.
 * @param boundaryCodes The area codes defining the search boundaries.
 * @returns The response containing census details for the specified criteria.
 */
export const searchPlanCensus = async (
  planConfigId: string,
  tenantId: string,
  limit: number = 1,
  requestInfo?: RequestInfo
) => {
  const searchBody = {
    CensusSearchCriteria: {
      tenantId: tenantId,
      source: planConfigId,
      offset: 0,
      limit: limit,
    },
    RequestInfo: requestInfo,
  };
  logger.info(
    `Received a search request for census data with ID: ${planConfigId}`
  );
  const planCensusResponse = await httpRequest(
    config.host.censusServiceHost + config.paths.censusSearch,
    searchBody
  );
  return planCensusResponse?.Census;
};

/**
 * Searches for plan configuration details based on configuration ID and tenant.
 * @param planConfigId The unique identifier for the plan configuration.
 * @param tenantId The tenant identifier for which the search is performed.
 * @returns The response containing configuration details for the specified plan configuration.
 */
export const searchPlanConfig = async (
  planConfigId: string,
  tenantId: string,
  requestInfo?: RequestInfo
) => {
  const searchBody = {
    PlanConfigurationSearchCriteria: {
      tenantId: tenantId,
      id: planConfigId,
    },
    RequestInfo: requestInfo,
  };
  logger.info(
    `Received a search request for plan configuration with ID: ${planConfigId}`
  );
  const planConfigResponse = await httpRequest(
    config.host.planServiceHost + config.paths.planConfigSearch,
    searchBody
  );
  return planConfigResponse;
};
