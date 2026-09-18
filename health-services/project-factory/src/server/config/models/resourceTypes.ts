/**
 * Represents a resource detail row transformed into the API response shape.
 * Returned by toResourceDetailsResponse() after reading from eg_cm_resource_details table.
 * Uses camelCase fileStoreId — this is the resource-details API contract.
 */
export interface ResourceDetailsResponse {
  id: string;
  tenantId: string;
  campaignId: string;
  type: string;
  parentResourceId: string | null;
  fileStoreId: string;
  processedFileStoreId: string | null;
  filename: string | null;
  status: string;
  action: string;
  isActive: boolean;
  hierarchyType: string | null;
  additionalDetails: Record<string, any>;
  auditDetails: {
    createdBy: string;
    lastModifiedBy: string;
    createdTime: number;
    lastModifiedTime: number;
  };
}

/**
 * Resource item as it appears in the CampaignDetails.resources array.
 * Uses filestoreId (lowercase 's') per the campaign API swagger contract.
 * All code reading CampaignDetails.resources should use this field name.
 */
export interface CampaignResource {
  type: string;
  filestoreId?: string;
  filename?: string;
  status?: string;
  processedFileStoreId?: string;
  error?: string;
  errorMessage?: string;
  resourceId?: string;
  parentResourceId?: string;
  additionalDetails?: Record<string, any>;
  createResourceId?: string;
}

/**
 * Convert a ResourceDetailsResponse (table row, camelCase fileStoreId) into a
 * CampaignResource (campaign body contract, lowercase filestoreId).
 * Use this at the enrichment boundary when injecting table data into CampaignDetails.resources.
 */
export function toCampaignResource(r: ResourceDetailsResponse): CampaignResource {
  return {
    type: r.type,
    filestoreId: r.fileStoreId,
    filename: r.filename || undefined,
    status: r.status,
    processedFileStoreId: r.processedFileStoreId || undefined,
    additionalDetails: r.additionalDetails,
    parentResourceId: r.parentResourceId || undefined,
  };
}
