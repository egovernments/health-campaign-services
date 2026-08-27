-- One-time table creation for the DST report automation lifecycle events,
-- written by egov-persister (platform/dst-report-metadata-persister.yml)
-- from the save-dst-report-metadata Kafka topic. Run once per environment
-- against the reporting database (same DB that holds hcm_report_metadata).
--
-- One row per report attempt. Every column here VARIES between runs: the
-- previous version also carried report_name, trigger_frequency and dag_name
-- (the same literal in every row), status_order (always 40, because only
-- terminal events are emitted), event_timestamp (a string duplicate of
-- timestamp_ms) and error_message (two fixed strings). They were dropped
-- rather than kept as noise; the exception text lives in the Airflow task
-- log, reachable via dag_run_id.

CREATE TABLE IF NOT EXISTS dst_report_metadata (
    id                  SERIAL PRIMARY KEY,
    -- producer-generated UUID; the idempotency key for Kafka redelivery
    event_id            VARCHAR(64)  NOT NULL UNIQUE,
    tenant_id           VARCHAR(64)  NOT NULL,
    state_name          VARCHAR(128),
    campaign_identifier VARCHAR(128) NOT NULL,
    -- campaign_identifier is NOT unique per cycle: Chad cycles 1 and 2 both
    -- carry CMP-2026-06-27-000416, as do most Nigeria states. Without the
    -- cycle, two cycles of one campaign are indistinguishable in this table.
    cycle_index         VARCHAR(8),
    -- sheet tab / ES credential set that ran it, for fleet triage
    deployment_group    VARCHAR(64),
    -- both | internal | partner | cumulative  (cumulative = the whole-campaign
    -- report fired at 23:59 on the row's mopup_end_date)
    mode                VARCHAR(16),
    day                 VARCHAR(8),
    slot_date           VARCHAR(10),
    slot_time           VARCHAR(5),
    dag_run_id          VARCHAR(250) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    -- stage that failed (analyze | cdd_sync | report | notify), else empty
    step_failed         VARCHAR(64),
    -- the campaign's Drive FOLDER, not a single file: one run publishes two
    -- Word docs, three Sheets and a chart, plus checkpoints under temp/
    drive_folder_url    VARCHAR(500),
    timestamp_ms        BIGINT       NOT NULL
);

-- "latest runs for a campaign" and "did today's slots all succeed" are the
-- two read patterns.
CREATE INDEX IF NOT EXISTS idx_dst_report_metadata_campaign
    ON dst_report_metadata (tenant_id, campaign_identifier, cycle_index, slot_date);
CREATE INDEX IF NOT EXISTS idx_dst_report_metadata_status
    ON dst_report_metadata (status, timestamp_ms DESC);
