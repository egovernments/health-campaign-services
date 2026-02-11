CREATE TABLE eg_cm_campaign_data (
    campaignNumber character varying(128) NOT NULL,
    type character varying(128) NOT NULL,
    uniqueIdentifier TEXT NOT NULL,
    data JSONB,
    uniqueIdAfterProcess character varying(256),
    status character varying(128),
    PRIMARY KEY (campaignNumber, uniqueIdentifier)
);

