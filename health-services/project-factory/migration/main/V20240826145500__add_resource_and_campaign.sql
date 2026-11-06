CREATE TABLE eg_cm_campaign_details
(
    id character varying(128) NOT NULL,
    tenantid character varying(64) NOT NULL,
    status character varying(128) NOT NULL,
    action character varying(64) NOT NULL,
    campaignnumber character varying(128) NOT NULL,
    hierarchytype character varying(128),
    boundarycode character varying(64),
    projectid character varying(128),
    createdby character varying(128) NOT NULL,
    lastmodifiedby character varying(128),
    createdtime bigint NOT NULL,
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    campaigndetails jsonb,
    campaignname character varying(250),
    projecttype character varying(128),
    startdate bigint,
    enddate bigint,
    CONSTRAINT eg_cm_campaign_details_pkey PRIMARY KEY (id),
    CONSTRAINT eg_cm_campaign_details_campaignname_key UNIQUE (campaignname)
);

CREATE TABLE eg_cm_campaign_process
(
    id character varying(128) NOT NULL,
    campaignid character varying(128) NOT NULL,
    type character varying(128),
    status character varying(128),
    details jsonb,
    createdtime bigint,
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    CONSTRAINT eg_cm_campaign_process_pkey PRIMARY KEY (id),
    CONSTRAINT uq_campaignid_type UNIQUE (campaignid, type)
);

CREATE TABLE eg_cm_generated_resource_details
(
    id character varying(128) NOT NULL,
    filestoreid character varying(128),
    status character varying(128),
    type character varying(128),
    tenantid character varying(128),
    count bigint,
    createdby character varying(128),
    createdtime bigint,
    lastmodifiedby character varying(128),
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    hierarchytype character varying(128),
    campaignid character varying(128),
    CONSTRAINT eg_cm_generated_resource_details_pkey PRIMARY KEY (id)
);

CREATE TABLE eg_cm_resource_activity
(
    id character varying(128) NOT NULL,
    retrycount integer,
    type character varying(64),
    url character varying(128),
    requestpayload jsonb,
    tenantid character varying(128) NOT NULL,
    responsepayload jsonb,
    status bigint,
    createdby character varying(128),
    createdtime bigint,
    lastmodifiedby character varying(128),
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    resourcedetailsid character varying(128),
    CONSTRAINT eg_cm_resource_activity_pkey PRIMARY KEY (id)
);

CREATE TABLE eg_cm_resource_details
(
    id character varying(128) NOT NULL,
    status character varying(128) NOT NULL,
    tenantid character varying(128) NOT NULL,
    filestoreid character varying(128) NOT NULL,
    processedfilestoreid character varying(128),
    action character varying(128) NOT NULL,
    type character varying(64) NOT NULL,
    createdby character varying(128) NOT NULL,
    createdtime bigint NOT NULL,
    lastmodifiedby character varying(128),
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    campaignid character varying(128),
    CONSTRAINT eg_cm_resource_details_pkey PRIMARY KEY (id)
);