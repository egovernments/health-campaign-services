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
    # Example: next migration
    # {
    #     "version": 2,
    #     "description": "Add index on tenantId and reportName",
    #     "up": """
    #         CREATE INDEX IF NOT EXISTS idx_reports_metadata_tenant_report
    #         ON REPORTS_METADATA (tenantId, reportName);
    #     s""",
    # },
]

# --------------- Migration Engine ---------------

MIGRATIONS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS REPORTS_SCHEMA (
        version         VARCHAR(255) PRIMARY KEY,
        description     VARCHAR(500),
        applied_at      TIMESTAMP DEFAULT NOW()
    );
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
                "INSERT INTO REPORTS_SCHEMA (version, description) VALUES (%s, %s)",
                (m["version"], m["description"]),
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
