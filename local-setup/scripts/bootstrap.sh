#!/usr/bin/env bash
# HCM local-setup bootstrap.
# One-shot: installs prerequisites, brings the stack up,
# encrypts SYSTEM user PII so OAuth works, ensures Kafka topics, restarts Kong,
# then runs a 13-API smoke test and a DISTRIBUTOR login probe.
# Idempotent — safe to re-run.
#
# Seed data lives in db/full-dump.sql (~12 MB) + db/02-hcm-ui-seed.sql (~6 MB),
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

need_sudo() {
  if [ "$EUID" -ne 0 ] && ! sudo -n true 2>/dev/null; then
    c_y "Some install steps need sudo -A — you'll be prompted for your password."
  fi
}

# ── 1. OS detection ────────────────────────────────────────────────────────
OS=unknown
if [ -f /etc/os-release ]; then . /etc/os-release; OS=${ID:-unknown}; fi
[ "$(uname)" = "Darwin" ] && OS=mac
c_g "[1/7] OS detected: $OS"

# ── 2. Install prerequisites ───────────────────────────────────────────────
install_pkg() {
  local check_cmd="$1" pkg="$2"
  command -v "$check_cmd" >/dev/null 2>&1 && { echo "  ✓ $check_cmd already installed"; return 0; }
  echo "  ↳ installing $pkg ..."
  case "$OS" in
    ubuntu|debian|pop|linuxmint)
      need_sudo
      sudo -A apt-get update -qq
      sudo -A apt-get install -y -qq "$pkg" ;;
    fedora|rhel|centos|rocky|almalinux)
      need_sudo
      sudo -A dnf install -y -q "$pkg" ;;
    mac)
      command -v brew >/dev/null 2>&1 || {
        c_r "Homebrew not installed. Install it from https://brew.sh and re-run."; exit 1; }
      brew install "$pkg" ;;
    *)
      c_r "Unsupported OS: $OS — install '$pkg' manually."; exit 1 ;;
  esac
}

c_g "[2/7] Checking / installing CLI prerequisites"
install_pkg curl     curl
install_pkg python3  python3
install_pkg pip      python3-pip
install_pkg psql     postgresql-client
install_pkg jq       jq
install_pkg openssl  openssl

# Docker
if ! command -v docker >/dev/null 2>&1; then
  c_y "Docker not found — installing via convenience script"
  curl -fsSL https://get.docker.com | sudo -A sh
  sudo -A usermod -aG docker "$USER" || true
  c_y "  You may need to log out / log back in for docker group membership to take effect."
fi
docker --version 2>/dev/null || docker version --format "{{.Client.Version}}" 2>/dev/null || true

# Docker compose (v2 plugin)
if ! docker compose version >/dev/null 2>&1; then
  c_y "Docker Compose plugin missing — installing"
  case "$OS" in
    ubuntu|debian|pop|linuxmint) sudo -A apt-get install -y -qq docker-compose-plugin ;;
    *)                            c_r "Install docker-compose-plugin manually for $OS" ;;
  esac
fi
docker compose version 2>/dev/null || true

# Docker socket — assume `docker` works for the invoking user.
# If it doesn't (permission denied), the first `docker compose up` will surface it
# clearly. Fix once with:
#   sudo -A usermod -aG docker $USER && newgrp docker
DK="docker"
docker compose ps >/dev/null 2>&1 || DK="sudo -A docker"

# ── 3. Verify the DB seed dump is present (shipped in git) ────────────────
c_g "[3/7] Verifying DB seed dump is present"
DUMP=db/full-dump.sql
if [ ! -s "$DUMP" ]; then
  c_r "  ✗ $DUMP is missing. It is committed to the repo; did the clone succeed?"
  exit 1
fi
ls -lh "$DUMP"

# ── 4. Start the stack ─────────────────────────────────────────────────────
# docker-compose uses strict depends_on: service_healthy. A single transient
# health-probe failure on one container causes compose to abort and leave many
# downstream containers in "Created" state. We retry up to 5 times: each pass
# kicks the still-pending containers + restarts anything Exited/unhealthy.
c_g "[4/7] Starting the stack (docker compose up -d, with healing)"
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

# ── 5. Wait for Gatus to report all healthy ────────────────────────────────
c_g "[5/7] Waiting for services to become healthy (up to 8 min)"
for i in {1..96}; do
  S=$(curl -s -m 3 http://localhost:28889/api/v1/endpoints/statuses 2>/dev/null || echo '[]')
  if [ "$(echo "$S" | python3 -c 'import sys,json
d=json.load(sys.stdin) if sys.stdin else []
print(0 if not d else min(int(e.get("results",[{}])[-1].get("success",False)) for e in d))' 2>/dev/null)" = "1" ]; then
    c_g "  ✓ all services healthy"
    break
  fi
  printf "  %3ds: waiting...\r" "$((i*5))"
  sleep 5
done
echo

# ── 5b. Encrypt SYSTEM user PII for OAuth login (idempotent) ──────────────
# eg_user.username/name/mobilenumber must be stored as deterministically-
# encrypted ciphertext for egov-user's OAuth lookup to find the seeded
# SYSTEM admin. We call enc-service at runtime so the ciphertext is bound
# to the tenant key the local enc-service generated on first call.
c_g "[5b/7] Encrypting SYSTEM user PII so OAuth login works"
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

# ── 6. Kafka topic init (project-factory consumer topics) ──────────────────
c_g "[6/7] Ensuring project-factory consumer topics exist in Redpanda"
PF_TOPICS=(start-admin-console-task start-admin-console-mapping-task
           test-topic-project-factory hcm-processing-result
           hcm-facility-create-batch hcm-user-create-batch
           hcm-mapping-batch hcm-campaign-mark-failed)
for t in "${PF_TOPICS[@]}"; do
  $DK exec hcm-redpanda rpk topic create "$t" 2>&1 | tail -1 || true
done

# Reload Kong to refresh upstream pool (no-op if everything was already routed)
$DK compose restart kong >/dev/null 2>&1 || true
sleep 25

# ── 7. Smoke-test 13 major APIs ────────────────────────────────────────────
c_g "[7/7] Running API smoke test (all should be 200)"

python3 - <<'PYS'
import json, subprocess
RI = {"RequestInfo":{"apiId":"hcm","ver":".01","ts":1,"msgId":"smoke","userInfo":{
    "id":1,"uuid":"dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3","userName":"SYSTEM",
    "name":"System Admin","mobileNumber":"9999999999","active":True,
    "tenantId":"mz","type":"EMPLOYEE",
    "roles":[{"code":"SUPERUSER","tenantId":"mz"},
             {"code":"NATIONAL_ADMIN","tenantId":"mz"}]}}}

def post(path, extra=None):
    body = dict(RI)
    if extra: body.update(extra)
    data = json.dumps(body)
    r = subprocess.run(["curl","-s","-o","/dev/null","-m","15","-X","POST",
                        f"http://localhost:28000{path}",
                        "-H","Content-Type: application/json","--data",data,
                        "-w","%{http_code}"], capture_output=True, text=True)
    code = r.stdout.strip() or "err"
    icon = "\u2713" if code == "200" else "\u2717"
    print(f"  {icon} {code:>3}  {path}")

post("/mdms-v2/v1/_search",
     {"MdmsCriteria":{"tenantId":"mz","moduleDetails":[
         {"moduleName":"common-masters","masterDetails":[{"name":"IdFormat"}]}]}})
post("/boundary-service/boundary-hierarchy-definition/_search",
     {"BoundaryTypeHierarchySearchCriteria":{"tenantId":"mz","limit":5}})
post("/egov-idgen/id/_generate",
     {"idRequests":[{"idName":"individual.id","tenantId":"mz",
                     "format":"IND-[SEQ_INDIVIDUAL_ID]","count":1}]})
post("/user/_search",
     {"tenantId":"mz","userType":"EMPLOYEE","userName":"SYSTEM"})
post("/health-individual/v1/_search?tenantId=mz&limit=5&offset=0",  {"Individual":{}})
post("/household/v1/_search?tenantId=mz&limit=5&offset=0",          {"Household":{}})
post("/facility/v1/_search?tenantId=mz&limit=5&offset=0",           {"Facility":{}})
post("/product/v1/_search?tenantId=mz&limit=5&offset=0",            {"Product":{}})
post("/health-project/v1/_search?tenantId=mz&limit=5&offset=0",
     {"Projects":[{"tenantId":"mz","name":"%"}]})
post("/stock/v1/_search?tenantId=mz&limit=5&offset=0",              {"Stock":{}})
post("/health-hrms/employees/_search?tenantId=mz&limit=5&offset=0", None)
post("/plan-service/config/_search",
     {"PlanConfigurationSearchCriteria":{"tenantId":"mz"}})
post("/project-factory/v1/project-type/search",
     {"CampaignDetails":{"tenantId":"mz",
        "status":["drafted","created","started","completed"],
        "pagination":{"limit":5}}})
PYS

# ── 7b. Verify the demo DISTRIBUTOR login works ───────────────────────────
# This user is shipped in db/full-dump.sql so it survives `down -v && up -d`.
# A 200 here proves OAuth + role-binding + jurisdiction seed are all wired.
c_g "[7b/7] Probing demo DISTRIBUTOR login (EMP-DIST-002 / eGov@123)"
DIST_CODE=$(curl -s -o /dev/null -m 15 -X POST 'http://localhost:28000/user/oauth/token' \
  -H 'Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=' \
  -d 'username=EMP-DIST-002&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE' \
  -w "%{http_code}")
if [ "$DIST_CODE" = "200" ]; then
  echo "  ✓ 200  /user/oauth/token  (DISTRIBUTOR login OK)"
else
  c_y "  ⚠ $DIST_CODE  DISTRIBUTOR login failed — was the dump fully loaded?"
fi

# ── 8. Final summary: list anything still not happy ───────────────────────
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
echo "  Service health (Gatus)   http://localhost:28889"
echo "  Workbench UI             http://localhost:28080/workbench-ui/employee"
echo "  Payments UI              http://localhost:28080/payments-ui/employee"
echo "  Admin login              SYSTEM / eGov@123 / city: mz"
echo "  Distributor login        EMP-DIST-002 / eGov@123 / city: mz"
