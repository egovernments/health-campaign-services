import config from "../config";
import { executeQuery, getTableName } from "./db";
import { ResourceDetailsCriteria, Pagination } from "../config/models/resourceDetailsCriteria";
import { ResourceDetailsResponse } from "../config/models/resourceTypes";

export interface ResourceDetailRow {
  id: string;
  tenantid: string;
  campaignid: string;
  type: string;
  parentresourceid: string | null;
  filestoreid: string;
  processedfilestoreid: string | null;
  filename: string | null;
  status: string;
  action: string;
  isactive: boolean;
  hierarchytype: string | null;
  additionaldetails: any;
  createdby: string;
  lastmodifiedby: string;
  createdtime: number;
  lastmodifiedtime: number;
}

export function toResourceDetailsResponse(row: ResourceDetailRow): ResourceDetailsResponse {
  return {
    id: row.id,
    tenantId: row.tenantid,
    campaignId: row.campaignid,
    type: row.type,
    parentResourceId: row.parentresourceid || null,
    fileStoreId: row.filestoreid,
    processedFileStoreId: row.processedfilestoreid || null,
    filename: row.filename || null,
    status: row.status,
    action: row.action,
    isActive: row.isactive,
    hierarchyType: row.hierarchytype || null,
    additionalDetails: row.additionaldetails || {},
    auditDetails: {
      createdBy: row.createdby,
      lastModifiedBy: row.lastmodifiedby,
      createdTime: Number(row.createdtime),
      lastModifiedTime: Number(row.lastmodifiedtime)
    }
  };
}

export async function searchResourceDetailsFromDB(
  criteria: ResourceDetailsCriteria,
  pagination?: Pagination
): Promise<ResourceDetailRow[]> {
  const { tenantId, campaignId, type, ids, parentResourceId, status, isActive, excludeTypes } = criteria;
  const conditions: string[] = [];
  const values: any[] = [];
  let idx = 1;

  conditions.push(`tenantid = $${idx++}`);
  values.push(tenantId);

  conditions.push(`campaignid = $${idx++}`);
  values.push(campaignId);

  if (type && type.length > 0) {
    conditions.push(`type = ANY($${idx++})`);
    values.push(type);
  }

  if (excludeTypes && excludeTypes.length > 0) {
    conditions.push(`type != ALL($${idx++})`);
    values.push(excludeTypes);
  }

  if (ids && ids.length > 0) {
    conditions.push(`id = ANY($${idx++})`);
    values.push(ids);
  }

  if (parentResourceId !== undefined) {
    if (parentResourceId === null) {
      conditions.push(`parentresourceid IS NULL`);
    } else {
      conditions.push(`parentresourceid = $${idx++}`);
      values.push(parentResourceId);
    }
  }

  if (status && status.length > 0) {
    conditions.push(`status = ANY($${idx++})`);
    values.push(status);
  }

  if (isActive !== undefined) {
    conditions.push(`isactive = $${idx++}`);
    values.push(isActive);
  } else {
    conditions.push(`isactive = true`);
  }

  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  let query = `SELECT * FROM ${tableName} WHERE ${conditions.join(" AND ")}`;

  if (pagination?.sortBy) {
    const allowedSortCols = ["createdtime", "lastmodifiedtime", "type", "status"];
    const sortCol = allowedSortCols.includes(pagination.sortBy.toLowerCase())
      ? pagination.sortBy.toLowerCase()
      : "createdtime";
    const sortOrder = pagination.sortOrder === "ASC" ? "ASC" : "DESC";
    query += ` ORDER BY ${sortCol} ${sortOrder}`;
  } else {
    query += ` ORDER BY createdtime DESC`;
  }

  if (pagination?.limit) {
    query += ` LIMIT $${idx++}`;
    values.push(pagination.limit);
  }

  if (pagination?.offset) {
    query += ` OFFSET $${idx++}`;
    values.push(pagination.offset);
  }

  const result = await executeQuery(query, values);
  return result?.rows || [];
}

export async function getResourceDetailById(id: string, tenantId: string): Promise<ResourceDetailRow | null> {
  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  const query = `SELECT * FROM ${tableName} WHERE id = $1 LIMIT 1`;
  const result = await executeQuery(query, [id]);
  return result?.rows?.[0] || null;
}

export async function findActiveResourceByUpsertKey(
  tenantId: string,
  campaignId: string,
  type: string,
  parentResourceId: string | null | undefined
): Promise<ResourceDetailRow | null> {
  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  let query: string;
  let values: any[];

  if (parentResourceId == null) {
    query = `SELECT * FROM ${tableName} WHERE tenantid = $1 AND campaignid = $2 AND type = $3 AND parentresourceid IS NULL AND isactive = true LIMIT 1`;
    values = [tenantId, campaignId, type];
  } else {
    query = `SELECT * FROM ${tableName} WHERE tenantid = $1 AND campaignid = $2 AND type = $3 AND parentresourceid = $4 AND isactive = true LIMIT 1`;
    values = [tenantId, campaignId, type, parentResourceId];
  }

  const result = await executeQuery(query, values);
  return result?.rows?.[0] || null;
}

export async function countResourcesByType(
  tenantId: string,
  campaignId: string,
  type: string,
  parentResourceId: string | null | undefined
): Promise<number> {
  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  let query: string;
  let values: any[];

  if (parentResourceId == null) {
    query = `SELECT COUNT(*) FROM ${tableName} WHERE tenantid = $1 AND campaignid = $2 AND type = $3 AND parentresourceid IS NULL AND isactive = true`;
    values = [tenantId, campaignId, type];
  } else {
    query = `SELECT COUNT(*) FROM ${tableName} WHERE tenantid = $1 AND campaignid = $2 AND type = $3 AND parentresourceid = $4 AND isactive = true`;
    values = [tenantId, campaignId, type, parentResourceId];
  }

  const result = await executeQuery(query, values);
  return parseInt(result?.rows?.[0]?.count || "0", 10);
}

export async function countTotalResourceDetails(
  criteria: ResourceDetailsCriteria
): Promise<number> {
  const { tenantId, campaignId, type, ids, parentResourceId, status, isActive } = criteria;
  const conditions: string[] = [];
  const values: any[] = [];
  let idx = 1;

  conditions.push(`tenantid = $${idx++}`);
  values.push(tenantId);

  conditions.push(`campaignid = $${idx++}`);
  values.push(campaignId);

  if (type && type.length > 0) {
    conditions.push(`type = ANY($${idx++})`);
    values.push(type);
  }

  if (ids && ids.length > 0) {
    conditions.push(`id = ANY($${idx++})`);
    values.push(ids);
  }

  if (parentResourceId !== undefined) {
    if (parentResourceId === null) {
      conditions.push(`parentresourceid IS NULL`);
    } else {
      conditions.push(`parentresourceid = $${idx++}`);
      values.push(parentResourceId);
    }
  }

  if (status && status.length > 0) {
    conditions.push(`status = ANY($${idx++})`);
    values.push(status);
  }

  if (isActive !== undefined) {
    conditions.push(`isactive = $${idx++}`);
    values.push(isActive);
  } else {
    conditions.push(`isactive = true`);
  }

  const tableName = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId);
  const query = `SELECT COUNT(*) FROM ${tableName} WHERE ${conditions.join(" AND ")}`;
  const result = await executeQuery(query, values);
  return parseInt(result?.rows?.[0]?.count || "0", 10);
}
