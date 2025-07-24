#!/bin/bash

baseurl=$DB_URL
echo "Base URL: $baseurl"

# Read comma-separated environment variables into arrays
IFS=',' read -r -a schemas <<< "$SCHEMA_NAME"
IFS=',' read -r -a tables <<< "$SCHEMA_TABLE"
IFS=',' read -r -a folders <<< "${MIGRATION_FOLDERS:-household,individual}"  # default folders

echo "Schemas to migrate: ${schemas[*]}"
echo "Flyway tables: ${tables[*]}"
echo "Migration folders: ${folders[*]}"

# Loop through schemas
for i in "${!schemas[@]}"; do
  schemaname="${schemas[$i]}"
  table="${tables[$i]}"

  # Build JDBC URL correctly
  if [[ "$baseurl" == *"currentSchema="* ]]; then
    # Already specifies a schema — don't append anything
    jdbc_url="$baseurl"
  elif [[ "$baseurl" == *"?"* ]]; then
    # Has other query params but not currentSchema
    jdbc_url="${baseurl}&currentSchema=${schemaname}"
  else
    # No query params yet
    jdbc_url="${baseurl}?currentSchema=${schemaname}"
  fi

  echo ""
  echo ">>> Starting migrations for schema: ${schemaname} using history table: ${table}"
  echo "Using JDBC URL: ${jdbc_url}"

  # Loop through all folders for this schema
  for folder in "${folders[@]}"; do
    echo "-- Applying migrations from folder: ${folder}"

    flyway \
      -url="${jdbc_url}" \
      -table="${table}" \
      -user="$FLYWAY_USER" \
      -password="$FLYWAY_PASSWORD" \
      -locations="${FLYWAY_LOCATIONS}/${folder}" \
      -baselineOnMigrate=true \
      -outOfOrder=true \
      migrate
  done
done
