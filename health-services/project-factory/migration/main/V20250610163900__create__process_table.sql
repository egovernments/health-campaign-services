CREATE TABLE eg_cm_campaign_process_data (
    campaignNumber character varying(128) NOT NULL,
    processName character varying(128) NOT NULL,
    status character varying(128),
    PRIMARY KEY (campaignNumber, processName)
);