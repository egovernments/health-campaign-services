CREATE TABLE eg_cm_campaign_projects (
    id CHARACTER VARYING(128) PRIMARY KEY,
    projectid CHARACTER VARYING(128) NOT NULL,
    campaignnumber CHARACTER VARYING(128) NOT NULL,
    boundarycode CHARACTER VARYING(128) NOT NULL,
    additionaldetails JSONB,  -- Nullable JSONB column
    isactive BOOLEAN NOT NULL,
    createdby CHARACTER VARYING(128) NOT NULL,
    lastmodifiedby CHARACTER VARYING(128) NOT NULL,
    createdtime BIGINT NOT NULL,
    lastmodifiedtime BIGINT NOT NULL
);
