-- Project Factory (Saga) — baseline infrastructure tables.
--
-- Additive only (per 04-database-design.md §3 and 08-migration-strategy §1): new tables,
-- no changes to existing eg_cm_* tables. DDL is unqualified (runs in the connection's
-- default schema); per-tenant schema provisioning is handled externally by DIGIT platform
-- tooling (see context/06-saas-multitenancy). Runtime queries inject {schema}. via
-- MultiStateInstanceUtil when central-instance mode is on.

-- Saga execution: one row per campaign-creation (or template/attendance) saga instance.
-- The SagaCoordinator is the single writer (LLD §2 invariant).
CREATE TABLE IF NOT EXISTS eg_cm_saga_execution (
    id                  varchar(64)   NOT NULL,
    campaignnumber      varchar(128)  NOT NULL,
    tenantid            varchar(128)  NOT NULL,
    sagatype            varchar(64)   NOT NULL,   -- CAMPAIGN_CREATE | TEMPLATE_GENERATE | ATTENDANCE_PROVISION
    status              varchar(64)   NOT NULL,   -- PENDING | VALIDATED | CREATING | MAPPING | COMPENSATING | COMPLETED | FAILED
    currentstep         varchar(64),
    lasterror           text,
    createdtime         bigint        NOT NULL,
    lastmodifiedtime    bigint        NOT NULL,
    CONSTRAINT pk_eg_cm_saga_execution PRIMARY KEY (id),
    CONSTRAINT uq_eg_cm_saga_execution_campaign UNIQUE (campaignnumber, sagatype)
);

CREATE INDEX IF NOT EXISTS idx_eg_cm_saga_execution_status ON eg_cm_saga_execution (status);
CREATE INDEX IF NOT EXISTS idx_eg_cm_saga_execution_campaign ON eg_cm_saga_execution (campaignnumber);

-- Saga step: per-step progress within a saga (LLD §3/§4 sub-step granularity).
CREATE TABLE IF NOT EXISTS eg_cm_saga_step (
    id                  varchar(64)   NOT NULL,
    sagaid              varchar(64)   NOT NULL,
    campaignnumber      varchar(128)  NOT NULL,
    stepname            varchar(64)   NOT NULL,   -- VALIDATE_INPUT | CREATE_USERS | CREATE_FACILITIES | CREATE_PROJECTS | MAP_ENTITIES | FINALIZE ...
    status              varchar(64)   NOT NULL,   -- PENDING | RUNNING | DONE | FAILED | COMPENSATED
    attempt             integer       NOT NULL DEFAULT 0,
    lasterror           text,
    createdtime         bigint        NOT NULL,
    lastmodifiedtime    bigint        NOT NULL,
    CONSTRAINT pk_eg_cm_saga_step PRIMARY KEY (id),
    CONSTRAINT fk_eg_cm_saga_step_saga FOREIGN KEY (sagaid) REFERENCES eg_cm_saga_execution (id),
    CONSTRAINT uq_eg_cm_saga_step UNIQUE (sagaid, stepname)
);

CREATE INDEX IF NOT EXISTS idx_eg_cm_saga_step_saga ON eg_cm_saga_step (sagaid);

-- Transactional outbox (ADR-004): rows written in the same local TX as the effect;
-- the outbox relay polls NEW rows and publishes to Kafka after commit.
CREATE TABLE IF NOT EXISTS eg_cm_outbox (
    id                  varchar(64)   NOT NULL,
    campaignnumber      varchar(128),
    tenantid            varchar(128)  NOT NULL,
    topic               varchar(256)  NOT NULL,   -- base topic; relay applies tenant prefix at publish
    partitionkey        varchar(256),             -- usually campaignnumber
    eventtype           varchar(128)  NOT NULL,
    payload             jsonb         NOT NULL,
    status              varchar(32)   NOT NULL DEFAULT 'NEW',  -- NEW | SENT
    createdtime         bigint        NOT NULL,
    sentat              bigint,
    CONSTRAINT pk_eg_cm_outbox PRIMARY KEY (id)
);

-- Partial index keeps the relay poll tiny even as SENT rows accumulate (04 §2.2).
CREATE INDEX IF NOT EXISTS idx_eg_cm_outbox_new
    ON eg_cm_outbox (status, createdtime) WHERE status = 'NEW';

-- Inbox (ADR-004 / LLD §5): dedup markers for effectively-once consumption.
CREATE TABLE IF NOT EXISTS eg_cm_inbox (
    idempotencykey      varchar(256)  NOT NULL,
    consumer            varchar(128)  NOT NULL,
    processedtime       bigint        NOT NULL,
    CONSTRAINT pk_eg_cm_inbox PRIMARY KEY (idempotencykey, consumer)
);
