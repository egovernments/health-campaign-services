-- Create the table
CREATE TABLE eg_cm_campaign_facilities (
    id CHARACTER VARYING(128) PRIMARY KEY,
    campaignnumber CHARACTER VARYING(128) NOT NULL,
    "name" CHARACTER VARYING(2000) NOT NULL, -- size matching with facility table column
    facilityId CHARACTER VARYING(128),
    facilityUsage CHARACTER VARYING(200) NOT NULL, -- size matching with facility table column
    isPermanent BOOLEAN NOT NULL,
    storageCapacity BIGINT,
    additionaldetails JSONB,  -- Nullable JSONB column
    isactive BOOLEAN NOT NULL,
    createdby CHARACTER VARYING(128) NOT NULL,
    lastmodifiedby CHARACTER VARYING(128) NOT NULL,
    createdtime BIGINT NOT NULL,
    lastmodifiedtime BIGINT NOT NULL
);

-- Adding index on campaignnumber
CREATE INDEX idx_campaignfacilities_campaignnumber ON eg_cm_campaign_facilities (campaignnumber);

-- Adding unique constraint and index on (name, campaignnumber)
CREATE UNIQUE INDEX idx_name_campaign_unique ON eg_cm_campaign_facilities (campaignnumber, "name");
