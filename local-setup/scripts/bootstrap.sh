#!/usr/bin/env bash
# HCM local-setup bootstrap.
# One-shot: installs prerequisites, fetches the DB dump, brings the stack up,
# waits for it to be healthy, then runs the smoke test.
# Idempotent — safe to re-run.
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

# ── 3. Fetch the DB seed dump ──────────────────────────────────────────────
c_g "[3/7] Fetching DB seed dump (skipped if already present)"
DUMP=db/full-dump.sql
DUMP_ID=1l4Gxg3w6F1uj7d4vwHpdGfG3DPJI3-tu
mkdir -p db
SIZE=0; [ -f "$DUMP" ] && SIZE=$(stat -c%s "$DUMP" 2>/dev/null || stat -f%z "$DUMP")
if [ "$SIZE" -lt 100000000 ]; then
  command -v gdown >/dev/null 2>&1 || pip install --user --quiet --break-system-packages gdown 2>/dev/null || pip install --user --quiet gdown
  export PATH="$HOME/.local/bin:$PATH"
  gdown --id "$DUMP_ID" -O "$DUMP"
else
  echo "  ✓ $DUMP already present ($(numfmt --to=iec --suffix=B "$SIZE" 2>/dev/null || echo "$SIZE bytes"))"
fi
ls -lh "$DUMP"

# ── 4. Start the stack ─────────────────────────────────────────────────────
c_g "[4/7] Starting the stack (docker compose up -d)"
$DK compose up -d

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

c_g "✅ Bootstrap complete. Dashboard: http://localhost:28889"
