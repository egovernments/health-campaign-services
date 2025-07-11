#!/bin/sh

# Print the base DB URL
baseurl="$DB_URL"
echo "Base URL: $baseurl"

# Append schema param to URL
schemasetter="?currentSchema="

# Get schema list
schemas="$SCHEMA_NAME"
echo "Schemas: $schemas"

# Loop through comma-separated schemas
for schemaname in $(echo "$schemas" | tr ',' ' ')
do
    echo "Processing schema: ${schemaname}"
    fullurl="${baseurl}${schemasetter}${schemaname}"
    echo "Flyway DB URL: ${fullurl}"

    # Run Flyway migrate
    flyway -url="$fullurl" \
           -table="$SCHEMA_TABLE" \
           -user="$FLYWAY_USER" \
           -password="$FLYWAY_PASSWORD" \
           -locations="$FLYWAY_LOCATIONS" \
           -baselineOnMigrate=true \
           -outOfOrder=true \
           migrate
done
