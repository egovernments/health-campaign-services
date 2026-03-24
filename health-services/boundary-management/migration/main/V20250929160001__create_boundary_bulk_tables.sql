CREATE TABLE eg_bm_generated_template
(
    id character varying(128) NOT NULL,
    filestoreid character varying(128),
    status character varying(128),
    tenantid character varying(128),
    hierarchytype character varying(128),
    locale VARCHAR(50),
    createdby character varying(128),
    createdtime bigint,
    lastmodifiedby character varying(128),
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    referenceid character varying(128),
    CONSTRAINT eg_bm_generated_template_pkey PRIMARY KEY (id)
);

CREATE TABLE eg_bm_processed_template
(
    id character varying(128) NOT NULL,
    status character varying(128) NOT NULL,
    tenantid character varying(128) NOT NULL,
    hierarchytype character varying(128),
    filestoreid character varying(128) NOT NULL,
    processedfilestoreid character varying(128),
    action character varying(128) NOT NULL,
    createdby character varying(128) NOT NULL,
    createdtime bigint NOT NULL,
    lastmodifiedby character varying(128),
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    referenceid character varying(128),
    CONSTRAINT eg_bm_processed_template_pkey PRIMARY KEY (id)
);