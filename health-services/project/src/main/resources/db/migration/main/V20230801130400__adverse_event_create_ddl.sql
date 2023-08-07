CREATE TABLE ADVERSE_EVENT(
	id		                character varchar(64),
	clientReferenceId		character varchar(64),
	tenantId		        character varchar(1000),
    taskId		            character varchar(64),
    taskClientReferenceId	character varchar(64),
    symptoms                array,
    reAttempts              bigint,
	createdBy		        character varchar(64),
    createdTime             bigint,
	lastModifiedBy		    character varchar(64),
    lastModifiedTime        bigint,
	clientCreatedTime       bigint,
    clientLastModifiedTime  bigint,
	rowVersion              bigint,
	isDeleted               bool,
	CONSTRAINT uk_adverse_event PRIMARY KEY (id),
	CONSTRAINT uk_adverse_event_clientReference_id unique (clientReferenceId)
);
