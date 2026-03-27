-- Backfill campaignnumber in eg_cm_resource_details from eg_cm_campaign_details
-- for all rows where campaignnumber is NULL but campaignid is set.
-- This fixes lookup failures introduced by the campaignNumber migration
-- (findActiveResourceByUpsertKey uses WHERE campaignnumber = $val which never matches NULL).
UPDATE eg_cm_resource_details rd
SET campaignnumber = cd.campaignnumber
FROM eg_cm_campaign_details cd
WHERE rd.campaignid = cd.id
  AND rd.campaignnumber IS NULL
  AND cd.campaignnumber IS NOT NULL;
