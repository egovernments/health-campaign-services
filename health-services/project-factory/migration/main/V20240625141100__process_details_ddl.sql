CREATE TABLE eg_cm_campaign_process (
    id VARCHAR(128) PRIMARY KEY,
    campaignId VARCHAR(128) NOT NULL,
    type VARCHAR(128),
    status VARCHAR(128),
    details JSONB,
    createdtime BIGINT,
    lastmodifiedtime BIGINT,
    additionaldetails JSONB,
    CONSTRAINT fk_campaignId FOREIGN KEY (campaignId) REFERENCES eg_cm_campaign_details(id)
);
