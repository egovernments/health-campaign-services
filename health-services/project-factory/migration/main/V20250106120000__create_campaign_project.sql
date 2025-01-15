CREATE TABLE eg_cm_campaign_projects (
    id CHARACTER VARYING(128) PRIMARY KEY,
    projectid CHARACTER VARYING(128),
    campaignnumber CHARACTER VARYING(128) NOT NULL,
    boundarycode CHARACTER VARYING(128) NOT NULL,
    boundarytype CHARACTER VARYING(128) NOT NULL,
    parentBoundaryCode CHARACTER VARYING(128),
    additionaldetails JSONB,  -- Nullable JSONB column
    isactive BOOLEAN NOT NULL,
    createdby CHARACTER VARYING(128) NOT NULL,
    lastmodifiedby CHARACTER VARYING(128) NOT NULL,
    createdtime BIGINT NOT NULL,
    lastmodifiedtime BIGINT NOT NULL
);

-- Adding index on campaignnumber
CREATE INDEX idx_campaignprojects_campaignnumber 
ON eg_cm_campaign_projects (campaignnumber);

-- Adding index on combination of campaignnumber and boundarycode
CREATE INDEX idx_campaignprojects_campaignnumber_boundarycode 
ON eg_cm_campaign_projects (campaignnumber, boundarycode);

-- Adding index on combination of campaignnumber and parentBoundaryCode
CREATE INDEX idx_campaignprojects_campaignnumber_parentboundarycode 
ON eg_cm_campaign_projects (campaignnumber, parentBoundaryCode);