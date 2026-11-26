const CAMPAIGN_SORT_COLUMNS: ReadonlySet<string> = new Set([
  "id", "tenantid", "status", "action", "campaignnumber", "hierarchytype", "boundarycode", "projectid",
  "createdby", "lastmodifiedby", "createdtime", "lastmodifiedtime", "campaignname", "projecttype",
  "startdate", "enddate", "parentid", "isactive",
]);

/** Builds ORDER BY only from known eg_cm_campaign_details columns and ASC/DESC so request text never reaches SQL; an unknown column yields no ordering, as if sortBy were absent. */
export function buildCampaignOrderByClause(sortBy: unknown, sortOrder: unknown): string {
  if (typeof sortBy !== "string") return "";
  const column = sortBy.trim().toLowerCase();
  if (!CAMPAIGN_SORT_COLUMNS.has(column)) return "";
  const direction = typeof sortOrder === "string" ? sortOrder.trim().toUpperCase() : "";
  return direction === "ASC" || direction === "DESC" ? `ORDER BY ${column} ${direction}\n` : `ORDER BY ${column}\n`;
}
