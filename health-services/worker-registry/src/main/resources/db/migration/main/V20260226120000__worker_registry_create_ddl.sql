CREATE TABLE IF NOT EXISTS eg_hcm_worker_registry (
    id                  character varying(64),
    tenantId            character varying(1000) NOT NULL,
    name                character varying(256) NOT NULL,
    payeePhoneNumber    character varying(64),
    paymentProvider     character varying(128) NOT NULL,
    payeeName           character varying(256),
    bankAccount         character varying(256),
    bankCode            character varying(64),
    photoId             character varying(128),
    signatureId         character varying(128),
    additionalDetails   jsonb,
    isDeleted           boolean DEFAULT FALSE,
    createdBy           character varying(64),
    lastModifiedBy      character varying(64),
    createdTime         bigint,
    lastModifiedTime    bigint,
    rowVersion          bigint,
    CONSTRAINT uk_eg_hcm_worker_registry_id PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS eg_hcm_worker_individual_map (
    id                  character varying(64),
    workerId            character varying(64) NOT NULL,
    individualId        character varying(64) NOT NULL,
    tenantId            character varying(1000) NOT NULL,
    isDeleted           boolean DEFAULT FALSE,
    createdBy           character varying(64),
    lastModifiedBy      character varying(64),
    createdTime         bigint,
    lastModifiedTime    bigint,
    CONSTRAINT pk_eg_hcm_worker_individual_map_id PRIMARY KEY (id),
    CONSTRAINT fk_eg_hcm_worker_individual_map_workerId FOREIGN KEY (workerId) REFERENCES eg_hcm_worker_registry(id)
);

CREATE INDEX IF NOT EXISTS idx_eg_hcm_worker_registry_tenant ON eg_hcm_worker_registry(tenantId);
CREATE INDEX IF NOT EXISTS idx_eg_hcm_worker_registry_tenant_deleted ON eg_hcm_worker_registry(tenantId, isDeleted);
CREATE INDEX IF NOT EXISTS idx_eg_hcm_worker_individual_map_worker ON eg_hcm_worker_individual_map(workerId);
CREATE INDEX IF NOT EXISTS idx_eg_hcm_worker_individual_map_individual ON eg_hcm_worker_individual_map(individualId);
CREATE INDEX IF NOT EXISTS idx_eg_hcm_worker_individual_map_tenant ON eg_hcm_worker_individual_map(tenantId);
