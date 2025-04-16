##!/bin/sh
#
#flyway -url=$DB_URL -table=$SCHEMA_TABLE -user=$FLYWAY_USER -password=$FLYWAY_PASSWORD -locations=$FLYWAY_LOCATIONS -baselineOnMigrate=true   -outOfOrder=true migrate
#!/bin/bash

set -e

# Path to your Spring Boot application.properties file
APP_PROPS_FILE="./../application.properties"
MIGRATIONS_FILE_PATH="./migration/main"

# Extract properties from application.properties
DB_URL=$(grep "^spring.flyway.url" "$APP_PROPS_FILE" | cut -d '=' -f2 | tr -d ' ')
FLYWAY_USER=$(grep "^spring.flyway.user" "$APP_PROPS_FILE" | cut -d '=' -f2 | tr -d ' ')
FLYWAY_PASSWORD=$(grep "^spring.flyway.password" "$APP_PROPS_FILE" | cut -d '=' -f2 | tr -d ' ')
FLYWAY_LOCATIONS="filesystem:$MIGRATIONS_FILE_PATH"
SCHEMA_TABLE="household_schema"   # Default table name
SCHEMA_NAME="mz,default,in"          # Set this or override when calling script

echo "Database URL      : $DB_URL"
echo "Flyway User       : $FLYWAY_USER"
echo "Flyway Locations  : $FLYWAY_LOCATIONS"
echo "Target Schemas    : $SCHEMA_NAME"
echo "Schema History Table: $SCHEMA_TABLE"

# Loop through each schema and apply migration
for SCHEMA in ${SCHEMA_NAME//,/ }
do
    echo "--------------------------------------------------------"
    echo "Running Flyway migration for schema: $SCHEMA"
    flyway \
      -url="${DB_URL}?currentSchema=${SCHEMA}" \
      -user="$FLYWAY_USER" \
      -password="$FLYWAY_PASSWORD" \
      -locations="$FLYWAY_LOCATIONS" \
      -table="$SCHEMA_TABLE" \
      -baselineOnMigrate=true \
      -outOfOrder=true \
      -ignoreMissingMigrations=true \
      migrate
done

echo " Flyway migration completed for all schemas."
