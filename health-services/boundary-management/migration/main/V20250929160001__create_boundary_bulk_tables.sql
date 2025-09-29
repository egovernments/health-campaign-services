CREATE TABLE eg_bm_generated_template
(
    id character varying(128) NOT NULL,
    filestoreid character varying(128),
    status character varying(128),
    tenantid character varying(128),
    createdby character varying(128),
    createdtime bigint,
    lastmodifiedby character varying(128),
    lastmodifiedtime bigint,
    additionaldetails jsonb,
    hierarchytype character varying(128),
    locale VARCHAR(50),
    referenceid character varying(128),
    CONSTRAINT eg_bm_generated_template_pkey PRIMARY KEY (id)
);