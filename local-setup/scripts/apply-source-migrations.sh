#!/bin/bash
# The seed dump (db/full-dump.sql) is older than the source the images are now built from.
# This applies the migrations that the source has and the dump lacks. Every statement in them is
# idempotent (IF NOT EXISTS / NOT EXISTS), so it is safe to run again.
# Run it after the first `docker compose up -d`, and again after `docker compose down -v`.
#
# How the list was found: all migrations of each service were applied to an empty database and the
# resulting tables and columns compared with the seeded database. Only these were missing.
set -euo pipefail
cd "$(dirname "$0")/.."
HCM=$(cd .. && pwd)
CORE=$(cd ../../Digit-Core/core-services && pwd)

apply() {  # schema | file
  echo "  $1 <- ${2##*/}"
  sed -E 's/"?\$\{schema\}"?\.//g' "$2" |
    docker exec -i -e PGOPTIONS="-c search_path=$1" hcm-postgres psql -U egov -d egov -q -v ON_ERROR_STOP=1
}

PF=$HCM/health-services/project-factory/migration/main
apply health "$PF/V20260317100000__add_parent_resource_id_filename_isactive.sql"
apply health "$PF/V20260317100001__migrate_resources_jsonb_to_table.sql"
apply health "$PF/V20260612120000__add_retry_columns_mapping_data.sql"
apply health "$PF/V20260820140000__add_attendance_sync_state_campaign_data.sql"
apply public "$CORE/egov-user/src/main/resources/db/migration/ddl/V20260309120000__alter_eg_user_add_countrycode.sql"
echo "done. Restart the affected services: docker compose restart project-factory egov-user"
