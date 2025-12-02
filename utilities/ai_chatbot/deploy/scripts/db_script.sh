#!/bin/bash
exec >>/tmp/logfile.txt 2>&1
set -e
set -u
echo $POSTGRES_USER $DB_USER $DB_USER_PASS $DB_NAME
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
            CREATE USER $DB_USER WITH PASSWORD '$DB_USER_PASS';
            CREATE DATABASE $DB_NAME;
            GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
EOSQL