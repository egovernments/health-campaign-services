-- Step 1: Remove duplicate rows
DELETE FROM eg_cm_campaign_process a
USING eg_cm_campaign_process b
WHERE a.id < b.id
AND a.campaignId = b.campaignId
AND a.type = b.type;

-- Step 2: Add the unique constraint
ALTER TABLE eg_cm_campaign_process
ADD CONSTRAINT uq_campaignId_type UNIQUE (campaignId, type);
