#!/bin/sh

if [ -z "${JAVA_OPTS}" ]; then
    export JAVA_OPTS="-Xmx256m -Xms256m"
fi

# shellcheck disable=SC2086 # JAVA_OPTS is expected to expand into separate JVM arguments.
exec java ${JAVA_OPTS} -jar /opt/egov/egov-notification-push.jar
