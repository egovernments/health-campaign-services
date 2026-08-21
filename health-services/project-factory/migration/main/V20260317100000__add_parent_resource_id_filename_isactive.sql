-- Add parentResourceId, filename, isActive columns to eg_cm_resource_details
ALTER TABLE eg_cm_resource_details
  ADD COLUMN IF NOT EXISTS parentresourceid character varying(64) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS filename character varying(256) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS isactive boolean DEFAULT true;

-- Indexes for new query patterns
CREATE INDEX IF NOT EXISTS idx_resource_details_campaign_type
  ON eg_cm_resource_details(campaignid, type)
  WHERE isactive = true;

CREATE INDEX IF NOT EXISTS idx_resource_details_parent
  ON eg_cm_resource_details(parentresourceid)
  WHERE parentresourceid IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_resource_details_tenant_campaign
  ON eg_cm_resource_details(tenantid, campaignid)
  WHERE isactive = true;
