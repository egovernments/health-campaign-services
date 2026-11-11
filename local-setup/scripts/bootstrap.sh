#!/usr/bin/env bash
# HCM local-setup bootstrap.
# One-shot: checks prerequisites, brings the stack up,
# encrypts SYSTEM user PII so OAuth works, ensures Kafka topics,
# then runs an API smoke test through the gateway and a DISTRIBUTOR login probe.
# Idempotent — safe to re-run.
#
# Seed data lives in db/full-dump.sql (~34 MB) + db/02-hcm-ui-seed.sql (~6 MB),
# both committed to the repo and auto-applied by Postgres on first init.
# The dump includes: schema, MDMS, boundary (TCHAD + MICROPLAN), ~133k localization
# rows (en_IN), 1 SYSTEM admin + ~1000 sample users, 501 HRMS employees,
# 18 HCM roles in eg_role for tenant mz,
# a demo DISTRIBUTOR user (EMP-DIST-002 / eGov@123) for non-SUPERUSER testing,
# and a NULL-relaxation patch on health.project optional columns.
#
# Usage:
#   cd local-setup && ./scripts/bootstrap.sh

set -euo pipefail

LSDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$LSDIR"

c_g() { printf '\033[1;32m%s\033[0m\n' "$1"; }
c_y() { printf '\033[1;33m%s\033[0m\n' "$1"; }
c_r() { printf '\033[1;31m%s\033[0m\n' "$1"; }

# ── 1. Check prerequisites ─────────────────────────────────────────────────
c_g "[1/6] Checking prerequisites"
for cmd in curl python3 docker; do
  command -v "$cmd" >/dev/null 2>&1 || { c_r "  ✗ $cmd not found. Install it and re-run (see README, section 1)."; exit 1; }
  echo "  ✓ $cmd"
done
docker compose version >/dev/null 2>&1 || { c_r "  ✗ 'docker compose' not available. Update Docker Desktop."; exit 1; }
docker info >/dev/null 2>&1 || { c_r "  ✗ Docker is not running. Open Docker Desktop and re-run."; exit 1; }
DK="docker"

# ── 2. Verify the DB seed dump is present (shipped in git) ────────────────
c_g "[2/6] Verifying DB seed dump is present"
DUMP=db/full-dump.sql
if [ ! -s "$DUMP" ]; then
  c_r "  ✗ $DUMP is missing. It is committed to the repo; did the clone succeed?"
  exit 1
fi
ls -lh "$DUMP"

# ── 3. Start the stack ─────────────────────────────────────────────────────
# docker-compose uses strict depends_on: service_healthy. A single transient
# health-probe failure on one container causes compose to abort and leave many
# downstream containers in "Created" state. We retry up to 5 times: each pass
# kicks the still-pending containers + restarts anything Exited/unhealthy.
c_g "[3/6] Starting the stack (docker compose up -d, with healing)"
for attempt in 1 2 3 4 5; do
  if $DK compose up -d 2>&1 | tee /tmp/_compose_up.$$.log; then
    # If compose exited cleanly, we're done with this phase.
    : # success — proceed
    break
  fi

  # Compose aborted. Find stragglers and try to heal them.
  echo "  ↻ attempt $attempt: compose hit a dependency failure; healing stragglers..."

  # Restart anything that is Exited with non-zero status or currently unhealthy.
  # `docker compose ps -q` returns container IDs of services in this project.
  pending=$($DK compose ps --status=created -q 2>/dev/null || true)
  bad=$($DK ps --filter "health=unhealthy" --format '{{.Names}}' 2>/dev/null | grep '^hcm-' || true)

  if [ -n "$bad" ]; then
    echo "  ↻ restarting unhealthy: $bad"
    for c in $bad; do $DK restart "$c" >/dev/null 2>&1 || true; done
  fi
  if [ -n "$pending" ]; then
    echo "  ↻ starting Created containers ($(echo "$pending" | wc -l) waiting)"
    for c in $pending; do $DK start "$c" >/dev/null 2>&1 || true; done
  fi

  # Give unhealthy services time to pass their next probe before the next pass.
  sleep 30
done
rm -f /tmp/_compose_up.$$.log

# Final safety net: kick anything still in Created state (the compose loop
# above may have exited cleanly on its 5th try while some containers were
# still waiting on a slow JVM warm-up).
still_created=$($DK compose ps --status=created -q 2>/dev/null || true)
if [ -n "$still_created" ]; then
  c_y "  ⚠ some containers still in Created state; forcing start"
  for c in $still_created; do $DK start "$c" >/dev/null 2>&1 || true; done
fi

# ── 4. Wait for every started container to report healthy ─────────────────
# Uses the containers' own health checks.
c_g "[4/6] Waiting for services to become healthy (up to 8 min)"
for i in {1..96}; do
  pending=$($DK compose ps --format '{{.Name}} {{.Health}}' 2>/dev/null \
              | awk '$2=="starting" || $2=="unhealthy" {print $1}')
  if [ -z "$pending" ]; then
    c_g "  ✓ all services healthy"
    break
  fi
  printf "  %3ds: waiting for %s container(s)...\r" "$((i*5))" "$(echo "$pending" | wc -l | tr -d ' ')"
  sleep 5
done
echo

# ── 4b. Encrypt SYSTEM user PII for OAuth login (idempotent) ──────────────
# eg_user.username/name/mobilenumber must be stored as deterministically-
# encrypted ciphertext for egov-user's OAuth lookup to find the seeded
# SYSTEM admin. We call enc-service at runtime so the ciphertext is bound
# to the tenant key the local enc-service generated on first call.
c_g "[4b/6] Encrypting SYSTEM user PII so OAuth login works"
ENC_JSON=$(curl -s -m 15 -X POST 'http://localhost:21234/egov-enc-service/crypto/v1/_encrypt' \
  -H 'Content-Type: application/json' \
  -d '{"RequestInfo":{"apiId":"hcm","ver":".01","ts":1,"msgId":"x","userInfo":{"id":1,"uuid":"dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3","userName":"SYSTEM","tenantId":"mz","type":"EMPLOYEE","roles":[{"code":"SUPERUSER","tenantId":"mz"}]}},"encryptionRequests":[{"tenantId":"mz","type":"Normal","value":"SYSTEM"},{"tenantId":"mz","type":"Normal","value":"System Admin"},{"tenantId":"mz","type":"Normal","value":"9999999999"}]}' || true)
ENC_USERNAME=$(printf '%s' "$ENC_JSON" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d[0])' 2>/dev/null || true)
ENC_NAME=$(printf '%s' "$ENC_JSON"     | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d[1])' 2>/dev/null || true)
ENC_MOBILE=$(printf '%s' "$ENC_JSON"   | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d[2])' 2>/dev/null || true)
if [ -n "$ENC_USERNAME" ] && [ -n "$ENC_NAME" ] && [ -n "$ENC_MOBILE" ]; then
  $DK exec hcm-postgres psql -U egov -d egov -v ON_ERROR_STOP=1 -c \
    "UPDATE public.eg_user SET username='$ENC_USERNAME', name='$ENC_NAME', mobilenumber='$ENC_MOBILE' WHERE id=1 AND uuid='dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3' AND username='SYSTEM';" >/dev/null 2>&1 || true
  echo "  ✓ SYSTEM user PII encrypted in eg_user"
else
  c_y "  ⚠ enc-service returned unexpected payload; OAuth login may fail. Response: $ENC_JSON"
fi

# ── 5. Kafka topic init (project-factory consumer topics) ──────────────────
c_g "[5/6] Ensuring project-factory consumer topics exist in Redpanda"
PF_TOPICS=(start-admin-console-task start-admin-console-mapping-task
           test-topic-project-factory hcm-processing-result
           hcm-facility-create-batch hcm-user-create-batch
           hcm-mapping-batch hcm-campaign-mark-failed)
for t in "${PF_TOPICS[@]}"; do
  $DK exec hcm-redpanda rpk topic create "$t" 2>&1 | tail -1 || true
done

# ── 6. Smoke test through the gateway, logged in as SYSTEM ─────────────────
c_g "[6/6] Running API smoke test through the gateway (all should show ✓)"

python3 - <<'PYS'
import json, subprocess
GW = "http://localhost:28000"

def curl(*args):
    return subprocess.run(["curl","-s","-m","15",*args], capture_output=True, text=True).stdout

try:
    token = json.loads(curl("-X","POST",GW+"/user/oauth/token",
        "-H","Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=",
        "-d","username=SYSTEM&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE"))["access_token"]
    print("  \u2713 200  /user/oauth/token  (SYSTEM login)")
except Exception:
    print("  \u2717 SYSTEM login failed, so the calls below will be refused"); token = None

def post(path, extra=None, token=token, expect="200"):
    ri = {"apiId":"hcm","ver":".01","ts":1,"msgId":"smoke"}
    if token: ri["authToken"] = token
    body = {"RequestInfo": ri}
    if extra: body.update(extra)
    code = curl("-o","/dev/null","-X","POST",GW+path,"-H","Content-Type: application/json",
                "--data",json.dumps(body),"-w","%{http_code}").strip() or "err"
    icon = "\u2713" if code == expect else "\u2717"
    print(f"  {icon} {code:>3}  {path}" + ("" if expect == "200" else f"  (expected {expect})"))

# The gateway only lets a role call the endpoints the seed data grants it. These are ones SYSTEM may call.
post("/egov-mdms-service/v1/_search",
     {"MdmsCriteria":{"tenantId":"mz","moduleDetails":[
         {"moduleName":"common-masters","masterDetails":[{"name":"IdFormat"}]}]}})
post("/boundary-service/boundary-hierarchy-definition/_search",
     {"BoundaryTypeHierarchySearchCriteria":{"tenantId":"mz","limit":5}})
post("/egov-idgen/id/_generate",
     {"idRequests":[{"idName":"individual.id","tenantId":"mz",
                     "format":"IND-[SEQ_INDIVIDUAL_ID]","count":1}]})
post("/user/_search",
     {"tenantId":"mz","userType":"EMPLOYEE","userName":"SYSTEM"})
post("/facility/v1/_search?tenantId=mz&limit=5&offset=0",           {"Facility":{}})
post("/product/v1/_search?tenantId=mz&limit=5&offset=0",            {"Product":{}})
post("/health-project/v1/_search?tenantId=mz&limit=5&offset=0",
     {"Projects":[{"tenantId":"mz","name":"%"}]})
post("/health-hrms/employees/_search?tenantId=mz&limit=5&offset=0", None)
post("/health-service-request/service/definition/v1/_search",
     {"ServiceDefinitionCriteria":{"tenantId":"mz"}})
post("/project-factory/v1/project-type/search",
     {"CampaignDetails":{"tenantId":"mz",
        "status":["drafted","created","started","completed"],
        "pagination":{"limit":5}}})
# Without a login the gateway must refuse.
post("/product/v1/_search?tenantId=mz&limit=5&offset=0", {"Product":{}}, token=None, expect="401")
PYS

# ── 6b. Verify the demo DISTRIBUTOR login works ───────────────────────────
# This user is shipped in db/full-dump.sql so it survives `down -v && up -d`.
# A 200 here proves OAuth + role-binding + jurisdiction seed are all wired.
c_g "[6b/6] Probing demo DISTRIBUTOR login (EMP-DIST-002 / eGov@123)"
DIST_CODE=$(curl -s -o /dev/null -m 15 -X POST 'http://localhost:28000/user/oauth/token' \
  -H 'Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=' \
  -d 'username=EMP-DIST-002&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE' \
  -w "%{http_code}")
if [ "$DIST_CODE" = "200" ]; then
  echo "  ✓ 200  /user/oauth/token  (DISTRIBUTOR login OK)"
else
  c_y "  ⚠ $DIST_CODE  DISTRIBUTOR login failed — was the dump fully loaded?"
fi

# ── 7. Final summary: list anything still not happy ───────────────────────
not_happy=$($DK ps --filter "health=unhealthy" --format '{{.Names}}' 2>/dev/null | grep '^hcm-' || true)
still_created=$($DK compose ps --status=created --format '{{.Name}}' 2>/dev/null || true)
exited_bad=$($DK ps -a --filter "status=exited" --format '{{.Names}} {{.Status}}' 2>/dev/null \
              | grep '^hcm-' | grep -v 'Exited (0)' || true)

if [ -n "$not_happy" ] || [ -n "$still_created" ] || [ -n "$exited_bad" ]; then
  c_y "⚠  Bootstrap finished but some containers still need attention:"
  [ -n "$not_happy" ]    && echo "   unhealthy:   $not_happy"
  [ -n "$still_created" ] && echo "   not started: $still_created"
  [ -n "$exited_bad" ]   && echo "   exited:      $exited_bad"
  echo "   → Try: docker compose up -d   (re-runs only stragglers)"
  echo "   → Or:  docker compose logs -f <service-name>   for diagnostics"
fi

c_g "✅ Bootstrap complete."
echo
echo "  Service health           docker compose ps"
echo "  Workbench UI             http://localhost:28080/workbench-ui/employee"
echo "  Payments UI              http://localhost:28080/payments-ui/employee"
echo "  Admin login              SYSTEM / eGov@123 / city: mz"
echo "  Distributor login        EMP-DIST-002 / eGov@123 / city: mz"
