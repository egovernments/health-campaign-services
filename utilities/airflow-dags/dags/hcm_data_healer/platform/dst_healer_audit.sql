-- Audit tables for the hcm_data_healer DAG, written only by egov-persister
-- (dst-healer-audit-persister.yml). Run once per environment on the reporting DB.
--
--   dst_healer_run     one row per campaign window per run (RUNNING, then final status)
--   dst_healer_record  one row per problem record, with payload and outcome
--   dst_healer_report  view joining the two
--
-- Ids are deterministic, so re-sent events do not duplicate rows.
-- payload holds beneficiary data: restrict access.

CREATE TABLE IF NOT EXISTS dst_healer_run (
    -- Airflow DAG run id; same value in both tables
    dag_run_id        VARCHAR(250) NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    campaign_name     VARCHAR(256),
    window_start      VARCHAR(10)  NOT NULL,
    window_end        VARCHAR(10)  NOT NULL,
    error_tracer      BOOLEAN,
    -- RUNNING | COMPLETED | COMPLETED_WITH_FAILURES | CHECKED_ONLY | FAILED
    -- (a row stuck in RUNNING means the run was killed)
    status            VARCHAR(32)  NOT NULL,
    failed_step       VARCHAR(32),                  -- analyze | guard | push | audit
    reason            VARCHAR(500),
    found_total       INTEGER,
    created           INTEGER,
    reindexed         INTEGER,
    already_present   INTEGER,
    failed            INTEGER,
    unrecoverable     INTEGER,
    started_at_ms     BIGINT       NOT NULL,
    finished_at_ms    BIGINT,
    -- a window is tenant + window_start (campaign numbers are not in every index)
    PRIMARY KEY (dag_run_id, tenant_id, window_start)
);
CREATE INDEX IF NOT EXISTS idx_dst_healer_run_tenant ON dst_healer_run (tenant_id, window_start);
CREATE INDEX IF NOT EXISTS idx_dst_healer_run_status ON dst_healer_run (status, started_at_ms DESC);

CREATE TABLE IF NOT EXISTS dst_healer_record (
    record_id            VARCHAR(64)  PRIMARY KEY,
    dag_run_id           VARCHAR(250) NOT NULL,     -- with tenant_id + window_start: joins dst_healer_run
    tenant_id            VARCHAR(64)  NOT NULL,
    window_start         VARCHAR(10)  NOT NULL,
    entity               VARCHAR(64)  NOT NULL,     -- Household | HouseholdMember | Individual | ProjectBeneficiary | ProjectTask
    client_reference_id  VARCHAR(128) NOT NULL,
    -- MISSING_IN_DB | MISSING_IN_ES | MEMBER_WITHOUT_HOUSEHOLD | HOUSEHOLD_WITHOUT_MEMBERS |
    -- MEMBER_WITHOUT_INDIVIDUAL | HOUSEHOLD_NOT_ENROLLED | BENEFICIARY_WITHOUT_TASK |
    -- HOUSEHOLD_NOT_ENROLLED_NO_MEMBERS
    issue                VARCHAR(48)  NOT NULL,
    found_in             VARCHAR(16)  NOT NULL,     -- ELASTICSEARCH | DATABASE | ERROR_TRACER | NOWHERE
    action               VARCHAR(8)   NOT NULL,     -- CREATE | UPDATE | NONE
    -- CREATED | REINDEXED | ALREADY_EXISTS | FAILED | SKIPPED_NO_CLIENT_AUDIT |
    -- SKIPPED_NOT_A_CREATE | UNRECOVERABLE | PREVIOUS_PUSH_NOT_SAVED | DETECTED_ONLY |
    -- NOT_ATTEMPTED
    outcome              VARCHAR(32)  NOT NULL,
    outcome_reason       VARCHAR(1000),
    payload              JSONB,                      -- the record as found / as pushed
    source_error_code    VARCHAR(128),               -- source failed request (tracer)
    source_error_message VARCHAR(1000),
    source_error_time    VARCHAR(40),
    source_api_url       VARCHAR(300),
    source_doc_id        VARCHAR(128),
    api_status           VARCHAR(8),                 -- HCM's HTTP status for the push
    api_response         VARCHAR(2000),
    recorded_at_ms       BIGINT       NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_dst_healer_record_run   ON dst_healer_record (dag_run_id, tenant_id, window_start);
CREATE INDEX IF NOT EXISTS idx_dst_healer_record_crid  ON dst_healer_record (tenant_id, client_reference_id);
CREATE INDEX IF NOT EXISTS idx_dst_healer_record_issue ON dst_healer_record (tenant_id, issue, outcome);

-- Dropped first: CREATE OR REPLACE cannot remove view columns.
DROP VIEW IF EXISTS dst_healer_report;
CREATE VIEW dst_healer_report AS
SELECT r.dag_run_id, r.tenant_id, r.campaign_name, r.window_start, r.window_end,
       r.status AS run_status,
       to_timestamp(r.started_at_ms / 1000.0)  AS run_started_at,
       to_timestamp(r.finished_at_ms / 1000.0) AS run_finished_at,
       c.entity, c.client_reference_id, c.issue, c.found_in, c.action, c.outcome, c.outcome_reason,
       c.source_error_code, c.source_error_message, c.source_error_time, c.source_api_url,
       c.api_status, c.api_response, c.payload,
       to_timestamp(c.recorded_at_ms / 1000.0) AS recorded_at
FROM dst_healer_run r
JOIN dst_healer_record c
  ON c.dag_run_id = r.dag_run_id AND c.tenant_id = r.tenant_id AND c.window_start = r.window_start;
