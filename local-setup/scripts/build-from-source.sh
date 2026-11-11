#!/bin/bash
# Builds the Java services from the local source checkouts and tags them hcm-local/<service>:source.
#
#   HCM services   <- health-campaign-services (this repo, current checkout)
#   DIGIT services <- ../../Digit-Core (current checkout)
#
# How: Maven builds the jar on the host (Java 17, shared ~/.m2), then the jar replaces the one in the
# published image that docker-compose.yml names for that service. The runtime (JRE, start.sh, wget for
# health checks) therefore stays the same; only the application code changes.
# docker-compose.source.yml points the services at the hcm-local/* tags.
#
# Usage:  scripts/build-from-source.sh            # everything
#         scripts/build-from-source.sh product    # one or more compose service names
set -uo pipefail
cd "$(dirname "$0")/.."
LOCAL_SETUP=$PWD
HCM=$(cd .. && pwd)
CORE=$(cd ../../Digit-Core/core-services && pwd)
export JAVA_HOME=${JAVA17_HOME:-$HOME/.asdf/installs/java/corretto-17.0.21.0.11}
export PATH=$JAVA_HOME/bin:$HOME/.asdf/installs/maven/3.9.6/bin:$PATH
LOG=$LOCAL_SETUP/build-logs; mkdir -p "$LOG"

# compose service name | source directory | (optional) service whose published image supplies the runtime
TABLE="
health-individual|$HCM/health-services/individual
household|$HCM/health-services/household
facility|$HCM/health-services/facility
product|$HCM/health-services/product
health-project|$HCM/health-services/project
health-hrms|$HCM/core-services/egov-hrms
beneficiary-idgen|$HCM/core-services/beneficiary-idgen
excel-ingestion|$HCM/health-services/excel-ingestion
health-service-request|$HCM/core-services/service-request|product
egov-user|$CORE/egov-user
mdms-backend|$CORE/mdms-v2
egov-idgen|$CORE/egov-idgen
egov-enc-service|$CORE/egov-enc-service
egov-accesscontrol|$CORE/egov-accesscontrol
egov-persister|$CORE/egov-persister
boundary-service|$CORE/boundary-service
egov-filestore|$CORE/egov-filestore
egov-localization|$CORE/egov-localization
gateway|$CORE/gateway|egov-accesscontrol
"

base_image() {  # image named in docker-compose.yml for a service
  docker compose -f docker-compose.yml --profile optional config --format json |
    python3 -c "import json,sys; print(json.load(sys.stdin)['services']['$1']['image'])"
}

echo "== HCM shared libraries (models, then common) -> ~/.m2"
for lib in health-services-models health-services-common; do
  (cd "$HCM/health-services/libraries/$lib" && mvn -q -DskipTests install > "$LOG/$lib.log" 2>&1) \
    && echo "  ok   $lib" || { echo "  FAIL $lib (see build-logs/$lib.log)"; exit 1; }
done

want=" $* "
echo "$TABLE" | while IFS='|' read -r svc dir runtime; do
  [ -z "$svc" ] && continue
  [ $# -gt 0 ] && [[ "$want" != *" $svc "* ]] && continue
  if ! (cd "$dir" && mvn -q -DskipTests clean package > "$LOG/$svc.log" 2>&1); then
    echo "  FAIL $svc  maven (see build-logs/$svc.log)"; continue
  fi
  jar=$(ls "$dir"/target/*.jar | grep -v original | head -1)
  base=$(base_image "${runtime:-$svc}")
  ctx=$(mktemp -d); cp "$jar" "$ctx/application.jar"
  printf 'FROM %s\nRUN rm -f /opt/egov/*.jar\nCOPY application.jar /opt/egov/application.jar\n' "$base" > "$ctx/Dockerfile"
  if docker build -q -t "hcm-local/$svc:source" "$ctx" >> "$LOG/$svc.log" 2>&1; then
    echo "  ok   $svc  ($(basename "$jar"), commit $(git -C "$dir" rev-parse --short HEAD))"
  else
    echo "  FAIL $svc  docker build (see build-logs/$svc.log)"
  fi
  rm -rf "$ctx"
done

# ---- Node services and web UIs: built with their own Dockerfiles, the same way CI does ----
FRONTEND=$(cd ../../DIGIT-Frontend && pwd)
node_build() {  # compose service | context | dockerfile | extra build args
  local svc=$1 ctx=$2 df=$3; shift 3
  [ -n "${want// /}" ] && [[ "$want" != *" $svc "* ]] && return
  if docker build -q -f "$df" "$@" -t "hcm-local/$svc:source" "$ctx" > "$LOG/$svc.log" 2>&1; then
    echo "  ok   $svc  (commit $(git -C "$ctx" rev-parse --short HEAD))"
  else
    echo "  FAIL $svc  docker build (see build-logs/$svc.log)"
  fi
}
node_build project-factory     "$HCM" "$HCM/health-services/project-factory/Dockerfile"     --build-arg WORK_DIR=health-services/project-factory
node_build boundary-management "$HCM" "$HCM/health-services/boundary-management/Dockerfile" --build-arg WORK_DIR=health-services/boundary-management
node_build workbench-ui "$FRONTEND" "$FRONTEND/health/micro-ui-react19/web/docker/Dockerfile" --build-arg BUILD_VARIANT=workbench-ui
node_build payments-ui  "$FRONTEND" "$FRONTEND/health/micro-ui-react19/web/docker/Dockerfile" --build-arg BUILD_VARIANT=payments-ui
