CREATE TABLE eg_cm_campaign_creation_process_status (
    id CHARACTER VARYING(128) PRIMARY KEY,
    campaignnumber VARCHAR(128) NOT NULL,
    processname VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    additionaldetails JSONB,
    createdby VARCHAR(128) NOT NULL,
    lastmodifiedby VARCHAR(128) NOT NULL,
    createdtime BIGINT NOT NULL,
    lastmodifiedtime BIGINT NOT NULL
);

-- Adding index on campaignnumber
CREATE INDEX idx_campaignprocessstatus_campaignnumber 
ON eg_cm_campaign_creation_process_status (campaignnumber);

-- Adding index on combination of campaignnumber and processname
CREATE INDEX idx_campaignprocessstatus_campaignnumber_processname 
ON eg_cm_campaign_creation_process_status (campaignnumber, processname);
