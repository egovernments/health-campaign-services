"""
Versioned database migration script.

Tracks applied migrations in a REPORTS_SCHEMA table.
Each migration runs exactly once per schema, in order.

To add a new migration:
  1. Add a new entry to the MIGRATIONS list with the next version number
  2. Provide the 'up' SQL (forward migration)
  3. Add a description

Requires environment variables:
  DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
Optional:
  DB_SCHEMAS - comma-separated list of schemas to run migration on.
               If not set, runs on public schema only.
"""

import os
import sys

import psycopg2

# --------------- Migration Registry ---------------
# Each migration has: version (int), description (str), up (str SQL)
# Migrations are applied in order and tracked in REPORTS_SCHEMA table.

MIGRATIONS = [
    {
        "version": "2026_03_31_16_39_migration",
        "description": "Create REPORTS_METADATA table",
        "up": """
            CREATE TABLE IF NOT EXISTS REPORTS_METADATA (
                id              SERIAL PRIMARY KEY,
                dagRunId        VARCHAR(255) NOT NULL,
                dagName         VARCHAR(255),
                campaignIdentifier VARCHAR(255),
                reportName      VARCHAR(255),
                triggerFrequency VARCHAR(100),
                fileStoreId     VARCHAR(255),
                triggerTime     VARCHAR(255),
                tenantId        VARCHAR(255) NOT NULL,
                createdTime     TIMESTAMP DEFAULT NOW(),
                reportRange     VARCHAR(255)
            );
            CREATE INDEX IF NOT EXISTS idx_reports_tenant
                ON REPORTS_METADATA (tenantId);
            CREATE INDEX IF NOT EXISTS idx_reports_tenant_campaign
                ON REPORTS_METADATA (tenantId, campaignIdentifier);
            CREATE INDEX IF NOT EXISTS idx_reports_tenant_report
                ON REPORTS_METADATA (tenantId, reportName);
        """,
    },
    {
        "version": "2026_07_10_00_01_migration",
        "description": "Add fileSizeBytes, reportGenerationTimeSeconds and rowCount columns to REPORTS_METADATA",
        "up": """
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS fileSizeBytes BIGINT;
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS reportGenerationTimeSeconds NUMERIC;
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS rowCount INT;
        """,
    },
    {
        "version": "2026_07_10_00_04_migration",
        "description": (
            "Switch createdTime on REPORTS_METADATA from native TIMESTAMP to BIGINT epoch millis, "
            "matching the epoch-millis convention used for audit timestamps elsewhere in DIGIT."
        ),
        "up": """
            ALTER TABLE REPORTS_METADATA ALTER COLUMN createdTime DROP DEFAULT;
            ALTER TABLE REPORTS_METADATA ALTER COLUMN createdTime TYPE BIGINT
                USING (EXTRACT(EPOCH FROM createdTime) * 1000)::BIGINT;
            ALTER TABLE REPORTS_METADATA ALTER COLUMN createdTime SET DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
        """,
    },
    {
        "version": "2026_07_10_00_05_migration",
        "description": (
            "Add reportTriggeredTimeMs (BIGINT, authoritative) and reportTriggeredTime (VARCHAR, "
            "human-readable) to REPORTS_METADATA - the actual wall-clock moment a run was triggered, "
            "distinct from triggerTime (the MDMS-configured time-of-day, or whatever the requester "
            "sent for a CUSTOM report)."
        ),
        "up": """
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS reportTriggeredTimeMs BIGINT;
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS reportTriggeredTime VARCHAR(50);
        """,
    },
    {
        "version": "2026_07_10_00_06_migration",
        "description": (
            "Add every lifecycle/status column to REPORTS_METADATA so it becomes append-only "
            "(one row per status event, not just terminal outcomes) - eventId, identifierType, "
            "status, statusOrder, errorMessage, errorType, eventTimestamp, eventTimestampMs."
        ),
        "up": """
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS eventId VARCHAR(64);
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS identifierType VARCHAR(50);
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS status VARCHAR(50);
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS statusOrder INT;
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS errorMessage TEXT;
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS errorType VARCHAR(255);
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS eventTimestamp VARCHAR(50);
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS eventTimestampMs BIGINT;

            -- Plain (non-partial) unique index - lets the persister use
            -- ON CONFLICT (eventId) DO NOTHING for idempotent inserts under Kafka's
            -- at-least-once redelivery. Multiple NULLs (existing rows, which predate
            -- eventId) never conflict with each other under standard SQL unique-constraint
            -- semantics - verified against a real Postgres 15 instance, not assumed.
            CREATE UNIQUE INDEX IF NOT EXISTS idx_reports_metadata_event_id
                ON REPORTS_METADATA (eventId);

            CREATE INDEX IF NOT EXISTS idx_reports_metadata_timestamp_ms
                ON REPORTS_METADATA (eventTimestampMs);
        """,
    },
    {
        "version": "2026_07_15_00_01_migration",
        "description": (
            "Add expectedRows and expectedGenerationTimeSeconds to REPORTS_METADATA - "
            "computed once at trigger time and threaded through Airflow's conf/env vars so "
            "every lifecycle event for a run carries the same value (not just the "
            "TRIGGERED_ON_UI bootstrap row), letting the UI show an estimate throughout."
        ),
        "up": """
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS expectedRows INT;
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS expectedGenerationTimeSeconds NUMERIC;
        """,
    },
    {
        "version": "2026_07_15_00_02_migration",
        "description": (
            "Add secondsSinceTriggered to REPORTS_METADATA - how many seconds after "
            "reportTriggeredTimeMs this specific event happened (eventTimestampMs - "
            "reportTriggeredTimeMs)/1000, computed by whichever producer pushes the event. "
            "Gives a per-stage timeline (e.g. TRIGGERED at +5s, POD_STARTED at +52s) for "
            "every row, not just completed runs."
        ),
        "up": """
            ALTER TABLE REPORTS_METADATA ADD COLUMN IF NOT EXISTS secondsSinceTriggered NUMERIC;
        """,
    },
]

# --------------- Migration Engine ---------------

MIGRATIONS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS REPORTS_SCHEMA (
        version         VARCHAR(255) PRIMARY KEY,
        description     VARCHAR(500),
        applied_at      TIMESTAMP DEFAULT NOW()
    );
    -- Records exactly what SQL ran for each migration, so "what did this version actually
    -- do to the DB" is always answerable from the DB itself - never dependent on git
    -- history or the current state of this file, which can (and did) move on. ADD COLUMN
    -- IF NOT EXISTS so this also backfills the column on a REPORTS_SCHEMA that already
    -- existed before this line was added.
    ALTER TABLE REPORTS_SCHEMA ADD COLUMN IF NOT EXISTS sql_executed TEXT;
"""


def get_applied_versions(cur):
    """Return set of already-applied migration versions."""
    cur.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'reports_schema')"
    )
    if not cur.fetchone()[0]:
        return set()
    cur.execute("SELECT version FROM REPORTS_SCHEMA ORDER BY version")
    return {row[0] for row in cur.fetchall()}


def run_migration():
    conn = psycopg2.connect(
        host=os.environ["DB_HOST"],
        port=os.environ.get("DB_PORT", "5432"),
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
    )
    conn.autocommit = True
    cur = conn.cursor()

    schemas_env = os.environ.get("DB_SCHEMAS", "").strip()
    schemas = [s.strip() for s in schemas_env.split(",") if s.strip()] if schemas_env else ["public"]

    for schema in schemas:
        print(f"\n{'=' * 50}")
        print(f"Schema: {schema}")
        print(f"{'=' * 50}")

        cur.execute(f"CREATE SCHEMA IF NOT EXISTS {schema};")
        cur.execute(f"SET search_path TO {schema};")

        # Ensure migrations tracking table exists
        cur.execute(MIGRATIONS_TABLE_SQL)

        applied = get_applied_versions(cur)
        pending = [m for m in MIGRATIONS if m["version"] not in applied]

        if not pending:
            print(f"  Up to date (v{max(applied) if applied else 0})")
            continue

        # Apply pending migrations in order
        for m in sorted(pending, key=lambda x: x["version"]):
            print(f"  Applying v{m['version']}: {m['description']}...")
            cur.execute(m["up"])
            cur.execute(
                "INSERT INTO REPORTS_SCHEMA (version, description, sql_executed) VALUES (%s, %s, %s)",
                (m["version"], m["description"], m["up"]),
            )
            print(f"  Applied v{m['version']}")

        print(f"  Schema {schema} migrated to v{max(m['version'] for m in MIGRATIONS)}")

    cur.close()
    conn.close()
    print("\nAll migrations complete.")


if __name__ == "__main__":
    try:
        run_migration()
    except Exception as e:
        print(f"Migration failed: {e}", file=sys.stderr)
        sys.exit(1)
