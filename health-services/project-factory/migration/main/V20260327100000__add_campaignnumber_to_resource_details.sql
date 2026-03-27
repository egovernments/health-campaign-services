-- Add campaignnumber column to eg_cm_resource_details for attendance-type resources
ALTER TABLE eg_cm_resource_details
  ADD COLUMN IF NOT EXISTS campaignnumber character varying(128) DEFAULT NULL;

-- Index for queries filtering by campaignnumber + type (attendance register resources)
CREATE INDEX IF NOT EXISTS idx_resource_details_campaignnumber_type
  ON eg_cm_resource_details(campaignnumber, type)
  WHERE isactive = true;
