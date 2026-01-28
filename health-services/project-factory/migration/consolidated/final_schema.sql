--
-- Name: eg_cm_campaign_data; Type: TABLE; 
--

CREATE TABLE eg_cm_campaign_data (
    campaignnumber character varying(128) NOT NULL,
    type character varying(128) NOT NULL,
    uniqueidentifier text NOT NULL,
    data jsonb,
    uniqueidafterprocess character varying(256),
    status character varying(128)
);


--
-- Name: eg_cm_campaign_details; Type: TABLE; 
--

CREATE TABLE eg_cm_campaign_details (
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
    parentid character varying(128),
    isactive boolean
);


--
-- Name: eg_cm_campaign_mapping_data; Type: TABLE; 
--

CREATE TABLE eg_cm_campaign_mapping_data (
    campaignnumber character varying(128) NOT NULL,
    type character varying(128) NOT NULL,
    uniqueidentifierfordata text NOT NULL,
    boundarycode character varying(128) NOT NULL,
    mappingid character varying(256),
    status character varying(128)
);


--
-- Name: eg_cm_campaign_process_data; Type: TABLE; 
--

CREATE TABLE eg_cm_campaign_process_data (
    campaignnumber character varying(128) NOT NULL,
    processname character varying(128) NOT NULL,
    status character varying(128)
);


--
-- Name: eg_cm_generated_resource_details; Type: TABLE; 
--

CREATE TABLE eg_cm_generated_resource_details (
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
    locale character varying(50)
);


--
-- Name: eg_cm_resource_details; Type: TABLE; 
--

CREATE TABLE eg_cm_resource_details (
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
    hierarchytype character varying(128)
);


--
-- Name: eg_cm_campaign_data eg_cm_campaign_data_pkey; Type: CONSTRAINT; 
--

ALTER TABLE ONLY eg_cm_campaign_data
    ADD CONSTRAINT eg_cm_campaign_data_pkey PRIMARY KEY (campaignnumber, uniqueidentifier, type);


--
-- Name: eg_cm_campaign_details eg_cm_campaign_details_pkey; Type: CONSTRAINT; 
--

ALTER TABLE ONLY eg_cm_campaign_details
    ADD CONSTRAINT eg_cm_campaign_details_pkey PRIMARY KEY (id);


--
-- Name: eg_cm_campaign_mapping_data eg_cm_campaign_mapping_data_pkey; Type: CONSTRAINT; 
--

ALTER TABLE ONLY eg_cm_campaign_mapping_data
    ADD CONSTRAINT eg_cm_campaign_mapping_data_pkey PRIMARY KEY (campaignnumber, uniqueidentifierfordata, boundarycode, type);


--
-- Name: eg_cm_campaign_process_data eg_cm_campaign_process_data_pkey; Type: CONSTRAINT; 
--

ALTER TABLE ONLY eg_cm_campaign_process_data
    ADD CONSTRAINT eg_cm_campaign_process_data_pkey PRIMARY KEY (campaignnumber, processname);


--
-- Name: eg_cm_generated_resource_details eg_cm_generated_resource_details_pkey; Type: CONSTRAINT; 
--

ALTER TABLE ONLY eg_cm_generated_resource_details
    ADD CONSTRAINT eg_cm_generated_resource_details_pkey PRIMARY KEY (id);


--
-- Name: eg_cm_resource_details eg_cm_resource_details_pkey; Type: CONSTRAINT; 
--

ALTER TABLE ONLY eg_cm_resource_details
    ADD CONSTRAINT eg_cm_resource_details_pkey PRIMARY KEY (id);


--
-- Name: idx_eg_cm_campaign_details_campaignname; Type: INDEX; 
--

CREATE INDEX idx_eg_cm_campaign_details_campaignname ON eg_cm_campaign_details USING btree (campaignname);


--
-- Name: idx_eg_cm_campaign_details_status; Type: INDEX; 
--

CREATE INDEX idx_eg_cm_campaign_details_status ON eg_cm_campaign_details USING btree (status);


--
-- Name: idx_eg_cm_generated_resource_details_campaignid; Type: INDEX; 
--

CREATE INDEX idx_eg_cm_generated_resource_details_campaignid ON eg_cm_generated_resource_details USING btree (campaignid);


--
-- Name: idx_eg_cm_resource_details_campaignid; Type: INDEX; 
--

CREATE INDEX idx_eg_cm_resource_details_campaignid ON eg_cm_resource_details USING btree (campaignid);


--
-- PostgreSQL database dump complete
--
