-- Migration script to remove the foreign key constraint
ALTER TABLE eg_cm_campaign_process DROP CONSTRAINT IF EXISTS fk_campaignId;
ALTER TABLE eg_cm_resource_activity DROP CONSTRAINT IF EXISTS eg_cm_resource_activity_resourceDetailsId_fkey;