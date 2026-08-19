#!/bin/sh

baseurl=$DB_URL
schemasetter="?currentSchema="
for schemaname in $(echo "$SCHEMA_NAME" | tr ',' ' ')
do
    flyway -url="${baseurl}${schemasetter}${schemaname}" -table="$SCHEMA_TABLE" -user="$FLYWAY_USER" -password="$FLYWAY_PASSWORD" -locations="$FLYWAY_LOCATIONS" -baselineOnMigrate=true -outOfOrder=true migrate
done