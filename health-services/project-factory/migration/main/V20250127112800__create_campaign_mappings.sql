CREATE TABLE eg_cm_campaign_mappings (
    id CHARACTER VARYING(128) PRIMARY KEY, -- Unique identifier for each mapping
    campaignnumber CHARACTER VARYING(128) NOT NULL, -- Campaign number
    mappingidentifier CHARACTER VARYING(64) NOT NULL, -- Mobile number or unique identifier
    mappingtype CHARACTER VARYING(64) NOT NULL, -- Type of mapping (e.g., staff, etc.)
    mappingcode CHARACTER VARYING(128), -- Optional mapping code
    "status" CHARACTER VARYING(64) NOT NULL, -- Status of the mapping (e.g., TO_BE_MAPPED)
    boundarycode CHARACTER VARYING(128) NOT NULL, -- Jurisdiction boundary code,
    additionaldetails JSONB, -- Additional details
    createdby CHARACTER VARYING(128) NOT NULL, -- Creator's identifier
    lastmodifiedby CHARACTER VARYING(128) NOT NULL, -- Modifier's identifier
    createdtime BIGINT NOT NULL, -- Creation timestamp
    lastmodifiedtime BIGINT NOT NULL -- Last modification timestamp
);

-- Adding index on campaignnumber
CREATE INDEX idx_campaignmappings_campaignnumber ON eg_cm_campaign_mappings (campaignnumber);

-- Adding index on mappingidentifier
CREATE INDEX idx_campaignmappings_mappingidentifier ON eg_cm_campaign_mappings (mappingidentifier);

-- Adding unique constraint for combination of campaignnumber, mappingidentifier, boundarycode, and status
CREATE UNIQUE INDEX uniq_campaign_mapping_boundary
ON eg_cm_campaign_mappings (campaignnumber, mappingidentifier, boundarycode);
