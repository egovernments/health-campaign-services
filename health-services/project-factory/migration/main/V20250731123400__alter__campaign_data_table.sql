-- Step 1: Drop existing primary key constraint if it exists
ALTER TABLE eg_cm_campaign_data
DROP CONSTRAINT IF EXISTS eg_cm_campaign_data_pkey;

-- Step 2: Add new primary key constraint including 'type'
ALTER TABLE eg_cm_campaign_data
ADD CONSTRAINT eg_cm_campaign_data_pkey PRIMARY KEY (campaignnumber, uniqueidentifier, type);