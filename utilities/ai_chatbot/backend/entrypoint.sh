#!/bin/bash
#exec >>/tmp/logfile.txt 2>&1
# Exit immediately if a command exits with a non-zero status
set -e
set -x

echo "Starting entrypoint script..."

echo "Running database migrations..."
# Run the database migrations
flask db upgrade

echo "Starting Gunicorn..."
# Start Gunicorn
exec gunicorn -b 0.0.0.0:5000 -w 1 --threads 5 --timeout 36000 app:app
