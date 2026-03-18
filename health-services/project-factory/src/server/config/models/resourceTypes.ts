/**
 * Represents a resource detail row transformed into the API response shape.
 * Returned by toResourceDetailsResponse() after reading from eg_cm_resource_details table.
 * Uses camelCase fileStoreId consistently.
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
 * Legacy resource item as it appears in the CampaignDetails.resources array
 * in the request body. Uses lowercase filestoreId per the original campaignDetailsSchema.
 * Some enrichment paths may also set fileStoreId (camelCase).
 */
export interface CampaignResource {
  type: string;
  filestoreId?: string;
  fileStoreId?: string;
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
