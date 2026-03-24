import config from "../config"; // Import configuration settings
import { httpRequest } from "../utils/request"; // Import httpRequest function for making HTTP requests
import { logger } from "../utils/logger"; // Import logger for logging information and errors
import { defaultRequestInfo } from "./coreApis"; // Import default request information

/**
 * Searches for facilities associated with a specific plan configuration.
 * @param planConfigId The unique identifier for the plan configuration.
 * @param tenantId The tenant identifier for which the search is performed.
 * @returns The response containing facility details for the specified plan configuration.
 */
export const searchPlanFacility = async (
  planConfigId: string,
  tenantId: string
) => {
  const searchBody = {
    PlanFacilitySearchCriteria: {
      tenantId: tenantId,
      planConfigurationId: planConfigId,
    },
    ...defaultRequestInfo, // Include default request metadata
  };
  logger.info(
    `Received a search request for plan facility with ID: ${planConfigId}`
  );
  const planFacilityResponse = await httpRequest(
    config.host.planServiceHost + config.paths.planFacilitySearch, // Construct the request URL
    searchBody // Pass the request body
  );
  return planFacilityResponse?.PlanFacility; // Return the response from the facility search
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
  limit:number=1
) => {
  const searchBody = {
    PlanSearchCriteria: {
      tenantId: tenantId,
      active: true, // Search only active plans
      // jurisdiction: boundaries, // Specify jurisdiction for the search
      planConfigurationId: planConfigId,
      limit: limit, // Limit the response to 1 result
      offset: 0, // Start from the first result
    },
    ...defaultRequestInfo, // Include default request metadata
  };
  logger.info(
    `Received a search request for plans with ID: ${planConfigId}`
  );
  const planResponse = await httpRequest(
    config.host.planServiceHost + config.paths.planSearch, // Construct the request URL
    searchBody // Pass the request body
  );
  return planResponse?.Plan; // Return the response from the plan search
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
  limit:number=1
) => {
  const searchBody = {
    CensusSearchCriteria: {
      tenantId: tenantId,
      source: planConfigId, // Use planConfigId as the source of the census data
      // areaCodes: boundaryCodes, // Specify area codes for the search
      offset:0,
      limit:limit,
    },

    ...defaultRequestInfo, // Include default request metadata
  };
  logger.info(
    `Received a search request for census data with ID: ${planConfigId}`
  );
  const planCensusResponse = await httpRequest(
    config.host.censusServiceHost + config.paths.censusSearch, // Construct the request URL
    searchBody // Pass the request body
  );
  return planCensusResponse?.Census; // Return the response from the census search
};

/**
 * Searches for plan configuration details based on configuration ID and tenant.
 * @param planConfigId The unique identifier for the plan configuration.
 * @param tenantId The tenant identifier for which the search is performed.
 * @returns The response containing configuration details for the specified plan configuration.
 */
export const searchPlanConfig = async (
  planConfigId: string,
  tenantId: string
) => {
  const searchBody = {
    PlanConfigurationSearchCriteria: {
      tenantId: tenantId,
      id: planConfigId, // Specify the plan configuration ID
    },
    ...defaultRequestInfo, // Include default request metadata
  };
  logger.info(
    `Received a search request for plan configuration with ID: ${planConfigId}`
  );
  const planConfigResponse = await httpRequest(
    config.host.planServiceHost + config.paths.planConfigSearch, // Construct the request URL
    searchBody // Pass the request body
  );
  return planConfigResponse; // Return the response from the plan configuration search
};
