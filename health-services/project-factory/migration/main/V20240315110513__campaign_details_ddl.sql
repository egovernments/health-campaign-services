CREATE TABLE eg_cm_campaign_details (
  id character varying(128) PRIMARY KEY,
  tenantId character varying(64) NOT NULL,
  "status" character varying(128) NOT NULL,
  "action" character varying(64) NOT NULL,
  campaignNumber character varying(128) NOT NULL,
  hierarchyType character varying(128) NOT NULL,
  boundaryCode character varying(64),
  projectId character varying(128),
  createdBy character varying(128) NOT NULL,
  lastModifiedBy character varying(128),
  createdTime bigint NOT NULL,
  lastModifiedTime bigint,
  additionalDetails jsonb,
  campaignDetails jsonb
);
