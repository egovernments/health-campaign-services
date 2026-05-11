import { RequestInfo } from "../config/models/requestInfoSchema";
import config from "../config";
import { defaultheader, httpRequest } from "../utils/request";
import { getFormattedStringForDebug, logger } from "../utils/logger";

export type ServiceDefinition = any;

export async function searchServiceDefinitions(
  tenantId: string,
  codes: string[],
  includeDeleted: boolean = true,
  requestInfo?: RequestInfo
): Promise<ServiceDefinition[]> {
  if (!tenantId) throw Error("tenantId is required for service definition search");
  if (!Array.isArray(codes) || !codes.length) return [];

  const url = `${config.host.serviceRequestHost}${config.paths.serviceDefinitionSearch}`;
  const requestBody = {
    RequestInfo: requestInfo,
    tenantId,
    ServiceDefinitionCriteria: {
      tenantId,
      code: codes,
    },
    includeDeleted,
  };

  logger.info(
    `Searching service definitions for tenantId=${tenantId} (codes=${codes.length}, includeDeleted=${includeDeleted})`
  );
  logger.debug(`ServiceDefinition search payload: ${getFormattedStringForDebug(requestBody)}`);

  const res = await httpRequest(url, requestBody, undefined, "post", "", { ...defaultheader });
  // response shape: { serviceDefinition: [...] } (as per ServiceDefinitionResponse)
  return (res?.ServiceDefinitions || res?.ServiceDefinitions || []) as ServiceDefinition[];
}

export async function createServiceDefinition(
  tenantId: string,
  serviceDefinition: ServiceDefinition,
  requestInfo?: RequestInfo
): Promise<ServiceDefinition> {
  const url = `${config.host.serviceRequestHost}${config.paths.serviceDefinitionCreate}`;
  const requestBody = {
    RequestInfo: requestInfo,
    ServiceDefinition: {
      ...serviceDefinition,
      tenantId: serviceDefinition?.tenantId || tenantId,
    },
  };

  logger.info(`Creating service definition code=${serviceDefinition?.code}`);
  logger.debug(`ServiceDefinition create payload: ${getFormattedStringForDebug(requestBody)}`);

  try {
    const res = await httpRequest(url, requestBody, undefined, "post", "", { ...defaultheader });
    const created = res?.serviceDefinition?.[0] || res?.ServiceDefinition?.[0];
    return created || serviceDefinition;
  } catch (e: any) {
    // service-request throws SERVICE_DEFINITION_ALREADY_EXISTS_ERR_CODE for duplicates
    const msg = (e?.message || "").toString();
    if (msg.includes("SERVICE_DEFINITION_ALREADY_EXISTS")) {
      logger.warn(`Service definition already exists, skipping create: code=${serviceDefinition?.code}`);
      return serviceDefinition;
    }
    logger.error(`Error while creating service definition code=${serviceDefinition?.code}: ${msg}`, e);
    throw Error(msg || "Error while creating service definition");
  }
}


