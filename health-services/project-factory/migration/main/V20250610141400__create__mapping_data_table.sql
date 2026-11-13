CREATE TABLE eg_cm_campaign_mapping_data (
    campaignNumber character varying(128) NOT NULL,
    "type" character varying(128) NOT NULL,
    uniqueIdentifierForData TEXT NOT NULL,
    boundaryCode character varying(128) NOT NULL,
    mappingId character varying(256),
    status character varying(128),
    PRIMARY KEY (campaignNumber, uniqueIdentifierForData, boundaryCode, "type")
);