-- Append-only enforcement: no row in any audit table can ever be deleted
CREATE OR REPLACE FUNCTION prevent_delete_on_audit_table()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Delete operations are not permitted on audit table %', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- Job-level table: one row per generation run
CREATE TABLE IF NOT EXISTS downsync_generation_job (
    id               character varying(64),
    tenantId         character varying(1000),
    projectId        character varying(64),
    totalRequested   integer,
    totalSucceeded   integer,
    totalFailed      integer,
    status           character varying(20),
    createdBy        character varying(64),
    createdTime      bigint,
    lastModifiedBy   character varying(64),
    lastModifiedTime bigint,
    rowVersion       bigint,
    CONSTRAINT pk_downsync_generation_job PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_dsj_tenant_project ON downsync_generation_job(tenantId, projectId);
CREATE INDEX IF NOT EXISTS idx_dsj_status         ON downsync_generation_job(status);
CREATE INDEX IF NOT EXISTS idx_dsj_created_time   ON downsync_generation_job(createdTime);

CREATE TRIGGER no_delete_downsync_generation_job
    BEFORE DELETE ON downsync_generation_job
    FOR EACH ROW EXECUTE FUNCTION prevent_delete_on_audit_table();

-- Locality-level table: one row per locality per job
CREATE TABLE IF NOT EXISTS downsync_generation_locality (
    id            character varying(64),
    jobId         character varying(64),
    tenantId      character varying(1000),
    projectId     character varying(64),
    locality      character varying(256),
    status        character varying(20),
    failureReason character varying(2000),
    startTime     bigint,
    endTime       bigint,
    createdTime   bigint,
    CONSTRAINT pk_downsync_generation_locality PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_dsl_job_id          ON downsync_generation_locality(jobId);
CREATE INDEX IF NOT EXISTS idx_dsl_tenant_proj_loc ON downsync_generation_locality(tenantId, projectId, locality);
CREATE INDEX IF NOT EXISTS idx_dsl_status          ON downsync_generation_locality(status);

CREATE TRIGGER no_delete_downsync_generation_locality
    BEFORE DELETE ON downsync_generation_locality
    FOR EACH ROW EXECUTE FUNCTION prevent_delete_on_audit_table();

-- File-level table: one row per file per locality
CREATE TABLE IF NOT EXISTS downsync_locality_file (
    id            character varying(64),
    localityRowId character varying(64),
    jobId         character varying(64),
    fileType      character varying(50),
    status        character varying(20),
    s3Key         character varying(1000),
    recordCount   bigint,
    failureReason character varying(2000),
    startTime     bigint,
    endTime       bigint,
    CONSTRAINT pk_downsync_locality_file PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_dlf_locality   ON downsync_locality_file(localityRowId);
CREATE INDEX IF NOT EXISTS idx_dlf_job_status ON downsync_locality_file(jobId, status);

CREATE TRIGGER no_delete_downsync_locality_file
    BEFORE DELETE ON downsync_locality_file
    FOR EACH ROW EXECUTE FUNCTION prevent_delete_on_audit_table();
