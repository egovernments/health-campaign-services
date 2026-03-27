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
import { executeQuery, getTableName } from "../utils/db";
import { getResourceConfigOrDefault, isRegisteredType } from "../config/resourceTypeRegistry";
import { campaignStatuses, resourceStatuses } from "../config/constants";
import { ResourceDetailsCreateInput } from "../config/models/resourceDetailsCreateSchema";
import { ResourceDetailsUpdateInput } from "../config/models/resourceDetailsUpdateSchema";
import { ResourceDetailsCriteria, Pagination } from "../config/models/resourceDetailsCriteria";

export async function getCampaignStatusFromDB(campaignId: string, tenantId: string): Promise<{ status: string | null; campaignNumber: string | null }> {
  const tableName = getTableName(config.DB_CONFIG.DB_CAMPAIGN_DETAILS_TABLE_NAME, tenantId);
  const result = await executeQuery(
    `SELECT status, campaignnumber FROM ${tableName} WHERE id = $1 AND tenantid = $2 LIMIT 1`,
    [campaignId, tenantId]
  );
  const row = result?.rows?.[0];
  return { status: row?.status || null, campaignNumber: row?.campaignnumber || null };
}

export async function getCampaignStatusByNumber(campaignNumber: string, tenantId: string): Promise<{ status: string | null; campaignId: string | null }> {
  const tableName = getTableName(config.DB_CONFIG.DB_CAMPAIGN_DETAILS_TABLE_NAME, tenantId);
  const result = await executeQuery(
    `SELECT id, status FROM ${tableName} WHERE campaignnumber = $1 AND tenantid = $2 LIMIT 1`,
    [campaignNumber, tenantId]
  );
  const row = result?.rows?.[0];
  return { status: row?.status || null, campaignId: row?.id || null };
}

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
  const { tenantId, campaignId, campaignNumber: inputCampaignNumber, type, parentResourceId, fileStoreId, filename, additionalDetails } = input;

  if (!campaignId && !inputCampaignNumber) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "Either campaignId or campaignNumber must be provided");
  }

  // Resolve both campaignId and campaignNumber — all resources always store campaignNumber
  // Priority: campaignNumber when provided — avoids redundant getCampaignStatusFromDB when caller
  // (e.g. campaignManageService) has already resolved it, reducing N+1 DB calls.
  let campaignStatus: string | null;
  let resolvedCampaignId: string | null = campaignId || null;
  let resolvedCampaignNumber: string | null = inputCampaignNumber || null;
  if (inputCampaignNumber) {
    const result = await getCampaignStatusByNumber(inputCampaignNumber, tenantId);
    campaignStatus = result.status;
    resolvedCampaignId = campaignId || result.campaignId;
    // Campaign not found: only when no campaignId provided (pure campaignNumber path)
    if (!campaignId && !result.campaignId) {
      throwError("COMMON", 400, "CAMPAIGN_NOT_FOUND",
        `Campaign with campaignNumber '${inputCampaignNumber}' not found`);
    }
  } else {
    // Only campaignId provided — resolve campaignNumber from DB
    const result = await getCampaignStatusFromDB(campaignId!, tenantId);
    campaignStatus = result.status;
    resolvedCampaignNumber = result.campaignNumber;
    resolvedCampaignId = campaignId!;
  }
  if (!resolvedCampaignNumber) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "campaignNumber could not be resolved for the given campaignId");
  }
  // Safe: throwError above guarantees non-null if execution reaches this point
  const campaignNumberForQuery = resolvedCampaignNumber as string;

  if (campaignStatus === campaignStatuses.started) {
    throwError("COMMON", 400, "RESOURCE_ADD_NOT_ALLOWED", "Cannot add/update resources while campaign is processing");
  }
  if (campaignStatus === campaignStatuses.cancelled) {
    throwError("COMMON", 400, "RESOURCE_ADD_NOT_ALLOWED", "Cannot add resources to a cancelled campaign");
  }

  // Upsert check: find existing active resource with same key (always by campaignNumber)
  // Done before parent validation so result can be reused for the inprogress guard below
  const existing = await findActiveResourceByUpsertKey(tenantId, campaignNumberForQuery, type, parentResourceId);

  if (campaignStatus === campaignStatuses.inprogress) {
    // "created" campaign: reject if a toCreate active resource is already queued for same key
    if (existing && existing.status === resourceStatuses.toCreate) {
      throwError("COMMON", 409, "RESOURCE_ALREADY_QUEUED", `Resource of type '${type}' is already queued for processing`);
    }
  }

  // Validate parent if type is registered with parentType
  const typeConfig = getResourceConfigOrDefault(type);
  if (isRegisteredType(type) && typeConfig.parentType) {
    if (!parentResourceId) {
      throwError("COMMON", 400, "VALIDATION_ERROR", `parentResourceId is required for resource type '${type}'`);
    }
    // parentResourceId is an external reference (e.g. attendance register ID from the attendance service).
    // Validate by confirming an active resource of the expected parentType exists for this campaign.
    const parentResource = await findActiveResourceByUpsertKey(tenantId, campaignNumberForQuery, typeConfig.parentType, null);
    if (!parentResource) {
      throwError("COMMON", 400, "VALIDATION_ERROR",
        `No active resource of parent type '${typeConfig.parentType}' found for campaign '${campaignNumberForQuery}'`);
    }
    // If not allowMultiplePerParent, check no other active resource of same type+parent
    if (!typeConfig.allowMultiplePerParent) {
      const existingCount = await countResourcesByType(tenantId, campaignNumberForQuery, type, parentResourceId);
      // existingCount check will be overridden by upsert below, so just log
      if (existingCount > 0) {
        logger.info(`Found existing resource for type ${type} and parent ${parentResourceId}, will upsert`);
      }
    }
  }

  // Use existing resource found above for upsert

  if (existing) {
    if (existing.status === resourceStatuses.creating) {
      throwError("COMMON", 409, "RESOURCE_PROCESSING", `Resource of type '${type}' is currently being processed`);
    }
    // Deactivate old resource
    await deactivateResource(existing, userUuid, tenantId);
  }

  // Create new resource — always store campaignNumber; store campaignId when provided
  const now = Date.now();
  const newResource = {
    id: uuidv4(),
    tenantId,
    campaignId: campaignId || null,
    campaignNumber: resolvedCampaignNumber,
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

  logger.info(`Created resource detail id=${newResource.id} type=${type} campaignNumber=${resolvedCampaignNumber}`);
  // _resolvedCampaignId is internal-only — used by controller for trigger, not persisted
  return { ...newResource, _resolvedCampaignId: resolvedCampaignId };
}

/**
 * Update a resource detail by replacing the file (creates a new record, deactivates old).
 */
export async function updateResourceDetail(
  input: ResourceDetailsUpdateInput,
  userUuid: string
): Promise<any> {
  const { id, tenantId, campaignId, campaignNumber: inputCampaignNumber, fileStoreId, filename } = input;

  if (!campaignId && !inputCampaignNumber) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "Either campaignId or campaignNumber must be provided");
  }

  // Resolve both campaignId and campaignNumber
  // Priority: campaignNumber when provided — avoids redundant getCampaignStatusFromDB
  let campaignStatus: string | null;
  let resolvedCampaignId: string | null = campaignId || null;
  let resolvedCampaignNumber: string | null = inputCampaignNumber || null;
  if (inputCampaignNumber) {
    const result = await getCampaignStatusByNumber(inputCampaignNumber, tenantId);
    campaignStatus = result.status;
    resolvedCampaignId = campaignId || result.campaignId;
    if (!campaignId && !result.campaignId) {
      throwError("COMMON", 400, "CAMPAIGN_NOT_FOUND",
        `Campaign with campaignNumber '${inputCampaignNumber}' not found`);
    }
  } else {
    const result = await getCampaignStatusFromDB(campaignId!, tenantId);
    campaignStatus = result.status;
    resolvedCampaignNumber = result.campaignNumber;
    resolvedCampaignId = campaignId!;
  }
  if (!resolvedCampaignNumber) {
    throwError("COMMON", 400, "VALIDATION_ERROR", "campaignNumber could not be resolved for the given campaignId");
  }
  if (campaignStatus === campaignStatuses.started) {
    throwError("COMMON", 400, "RESOURCE_ADD_NOT_ALLOWED", "Cannot update resources while campaign is processing");
  }
  if (campaignStatus === campaignStatuses.cancelled) {
    throwError("COMMON", 400, "RESOURCE_ADD_NOT_ALLOWED", "Cannot update resources on a cancelled campaign");
  }

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

  // Inprogress guard: same rule as createResourceDetail — reject if resource is already queued
  if (campaignStatus === campaignStatuses.inprogress) {
    if (existing!.status === resourceStatuses.toCreate) {
      throwError("COMMON", 409, "RESOURCE_ALREADY_QUEUED",
        `Resource '${id}' of type '${existing!.type}' is already queued for processing. Wait for it to complete or fail before updating.`);
    }
  }

  // Deactivate old resource
  await deactivateResource(existing!, userUuid, tenantId);

  // Create replacement resource — inherit campaignNumber from existing row
  const now = Date.now();
  const newResource = {
    id: uuidv4(),
    tenantId,
    campaignId: campaignId || existing!.campaignid || null,
    campaignNumber: resolvedCampaignNumber,  // null guard above ensures this is always non-null
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
  return { ...newResource, _resolvedCampaignId: resolvedCampaignId };
}

/**
 * Search resource details with pagination.
 * When campaignId is provided without campaignNumber, resolves campaignNumber and searches by it.
 */
export async function searchResourceDetails(
  criteria: ResourceDetailsCriteria,
  pagination?: Pagination
): Promise<{ ResourceDetails: any[]; TotalCount: number }> {
  // Always search by campaignNumber — resolve it from campaignId if needed
  if (criteria.campaignId && !criteria.campaignNumber) {
    const { campaignNumber } = await getCampaignStatusFromDB(criteria.campaignId, criteria.tenantId);
    if (campaignNumber) {
      criteria = { ...criteria, campaignNumber };
    }
  }
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
    campaignId: resource.campaignid || null,
    campaignNumber: resource.campaignnumber || null,
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
