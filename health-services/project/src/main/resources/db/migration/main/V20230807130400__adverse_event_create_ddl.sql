CREATE TABLE ADVERSE_EVENT(
	id		                character varying(64),
	clientReferenceId		character varying(64),
	tenantId		        character varying(1000),
    taskId		            character varying(64),
    taskClientReferenceId	character varying(64),
    symptoms                jsonb,
    reAttempts              bigint,
	createdBy		        character varying(64),
    createdTime             bigint,
	lastModifiedBy		    character varying(64),
    lastModifiedTime        bigint,
	clientCreatedTime       bigint,
    clientLastModifiedTime  bigint,
	rowVersion              bigint,
	isDeleted               bool,
	CONSTRAINT uk_adverse_event PRIMARY KEY (id),
	CONSTRAINT uk_adverse_event_clientReference_id unique (clientReferenceId)
);
