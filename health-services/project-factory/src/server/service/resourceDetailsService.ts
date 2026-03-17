import { v4 as uuidv4 } from "uuid";
import config from "../config";
import { produceModifiedMessages } from "../kafka/Producer";
import { throwError } from "../utils/genericUtils";
import { logger } from "../utils/logger";
import {
  searchResourceDetailsFromDB,
  getResourceDetailById,
  findActiveResourceByUpsertKey,
  countResourcesByType,
  countTotalResourceDetails,
  toResourceDetailsResponse,
  ResourceDetailRow
} from "../utils/resourceDetailsUtils";
import { getResourceConfigOrDefault, isRegisteredType } from "../config/resourceTypeRegistry";
import { resourceStatuses } from "../config/constants";
import { ResourceDetailsCreateInput } from "../config/models/resourceDetailsCreateSchema";
import { ResourceDetailsUpdateInput } from "../config/models/resourceDetailsUpdateSchema";
import { ResourceDetailsCriteria, Pagination } from "../config/models/resourceDetailsCriteria";

/**
 * Create or upsert a resource detail.
 * - If same (campaignId, type, parentResourceId) active resource exists with status=creating → reject 409
 * - If exists with other status → deactivate old, create new
 * - If not exists → create new
 */
export async function createResourceDetail(
  input: ResourceDetailsCreateInput,
  userUuid: string
): Promise<any> {
  const { tenantId, campaignId, type, parentResourceId, fileStoreId, filename, additionalDetails } = input;

  // Validate parent if type is registered with parentType
  const typeConfig = getResourceConfigOrDefault(type);
  if (isRegisteredType(type) && typeConfig.parentType) {
    if (!parentResourceId) {
      throwError("COMMON", 400, "VALIDATION_ERROR", `parentResourceId is required for resource type '${type}'`);
    }
    // Validate parent resource exists and is active
    const parentResource = await getResourceDetailById(parentResourceId!, tenantId);
    if (!parentResource || !parentResource.isactive) {
      throwError("COMMON", 400, "VALIDATION_ERROR", `Parent resource '${parentResourceId}' not found or inactive`);
    }
    if (parentResource!.type !== typeConfig.parentType) {
      throwError("COMMON", 400, "VALIDATION_ERROR", `Parent resource must be of type '${typeConfig.parentType}'`);
    }
    // If not allowMultiplePerParent, check no other active resource of same type+parent
    if (!typeConfig.allowMultiplePerParent) {
      const existingCount = await countResourcesByType(tenantId, campaignId, type, parentResourceId);
      // existingCount check will be overridden by upsert below, so just log
      if (existingCount > 0) {
        logger.info(`Found existing resource for type ${type} and parent ${parentResourceId}, will upsert`);
      }
    }
  }

  // Upsert check: find existing active resource with same key
  const existing = await findActiveResourceByUpsertKey(tenantId, campaignId, type, parentResourceId);

  if (existing) {
    if (existing.status === resourceStatuses.creating) {
      throwError("COMMON", 409, "RESOURCE_PROCESSING", `Resource of type '${type}' is currently being processed`);
    }
    // Deactivate old resource
    await deactivateResource(existing, userUuid, tenantId);
  }

  // Create new resource
  const now = Date.now();
  const newResource = {
    id: uuidv4(),
    tenantId,
    campaignId,
    type,
    parentResourceId: parentResourceId || null,
    fileStoreId,
    processedFileStoreId: null,
    filename: filename || null,
    status: resourceStatuses.toCreate,
    action: "create",
    isActive: true,
    hierarchyType: null,
    additionalDetails: additionalDetails || {},
    auditDetails: {
      createdBy: userUuid,
      createdTime: now,
      lastModifiedBy: userUuid,
      lastModifiedTime: now
    }
  };

  await produceModifiedMessages(
    { ResourceDetails: newResource },
    config.kafka.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC,
    tenantId
  );

  logger.info(`Created resource detail id=${newResource.id} type=${type} campaignId=${campaignId}`);
  return newResource;
}

/**
 * Update a resource detail by replacing the file (creates a new record, deactivates old).
 */
export async function updateResourceDetail(
  input: ResourceDetailsUpdateInput,
  userUuid: string
): Promise<any> {
  const { id, tenantId, campaignId, fileStoreId, filename } = input;

  const existing = await getResourceDetailById(id, tenantId);
  if (!existing) {
    throwError("COMMON", 404, "NOT_FOUND", `Resource detail '${id}' not found`);
  }
  if (!existing!.isactive) {
    throwError("COMMON", 400, "VALIDATION_ERROR", `Resource detail '${id}' is inactive. Create a new resource instead.`);
  }
  if (existing!.status === resourceStatuses.creating) {
    throwError("COMMON", 409, "RESOURCE_PROCESSING", `Resource '${id}' is currently being processed`);
  }

  // Deactivate old resource
  await deactivateResource(existing!, userUuid, tenantId);

  // Create replacement resource
  const now = Date.now();
  const newResource = {
    id: uuidv4(),
    tenantId,
    campaignId,
    type: existing!.type,
    parentResourceId: existing!.parentresourceid || null,
    fileStoreId,
    processedFileStoreId: null,
    filename: filename !== undefined ? filename : existing!.filename,
    status: resourceStatuses.toCreate,
    action: "update",
    isActive: true,
    hierarchyType: existing!.hierarchytype || null,
    additionalDetails: existing!.additionaldetails || {},
    auditDetails: {
      createdBy: userUuid,
      createdTime: now,
      lastModifiedBy: userUuid,
      lastModifiedTime: now
    }
  };

  await produceModifiedMessages(
    { ResourceDetails: newResource },
    config.kafka.KAFKA_CREATE_RESOURCE_DETAILS_TOPIC,
    tenantId
  );

  logger.info(`Updated resource detail: deactivated id=${id}, created id=${newResource.id}`);
  return newResource;
}

/**
 * Search resource details with pagination.
 */
export async function searchResourceDetails(
  criteria: ResourceDetailsCriteria,
  pagination?: Pagination
): Promise<{ ResourceDetails: any[]; TotalCount: number }> {
  const [rows, total] = await Promise.all([
    searchResourceDetailsFromDB(criteria, pagination),
    countTotalResourceDetails(criteria)
  ]);

  return {
    ResourceDetails: rows.map(toResourceDetailsResponse),
    TotalCount: total
  };
}

async function deactivateResource(resource: ResourceDetailRow, userUuid: string, tenantId: string): Promise<void> {
  const now = Date.now();
  const updated = {
    id: resource.id,
    tenantId: resource.tenantid,
    campaignId: resource.campaignid,
    type: resource.type,
    parentResourceId: resource.parentresourceid || null,
    fileStoreId: resource.filestoreid,
    processedFileStoreId: resource.processedfilestoreid || null,
    filename: resource.filename || null,
    status: resource.status,
    action: resource.action,
    isActive: false,
    hierarchyType: resource.hierarchytype || null,
    additionalDetails: resource.additionaldetails || {},
    auditDetails: {
      createdBy: resource.createdby,
      createdTime: Number(resource.createdtime),
      lastModifiedBy: userUuid,
      lastModifiedTime: now
    }
  };

  await produceModifiedMessages(
    { ResourceDetails: updated },
    config.kafka.KAFKA_UPDATE_RESOURCE_DETAILS_TOPIC,
    tenantId
  );
}
