r"""
Independent service-name verifier for the HCM Data Healer .env.
Makes a read-only _search (limit=1) against every DIGIT service the tool uses,
resolving hosts from .env exactly like core/entities.py. No _create/_update.

Run from a shell:   python verify_services.py [tenant] [path/to/.env]
Run in a Jupyter cell: just edit TENANT / ENV_PATH below and run the cell
(argv is ignored under ipykernel, so Jupyter's own -f flag can't hijack it).

A reachable service (HTTP 200, or even a 400 DIGIT error body) => the .env name
is correct. A ConnectionError / timeout / DNS failure => name wrong OR you're not
on a network that can resolve in-cluster DNS.
"""
import sys, os, time, base64
import requests
requests.packages.urllib3.disable_warnings()

# ---- EDIT THESE (used in Jupyter, or as defaults on the shell) ----
TENANT   = "bo"                                        # a real tenant with data
ENV_PATH = os.getenv("HEALER_ENV_FILE", ".env")         # the tool's .env
TIMEOUT  = 30                                          # member search on a big tenant can take several seconds
TEMPLATE_DEFAULT = "http://{service}.egov:8080"        # used if .env has no template

# Honour shell args ONLY when not running under Jupyter/ipykernel (where argv is
# the kernel launcher, e.g. "-f /.../kernel.json"). This stops tenant=-f.
_in_jupyter = ("ipykernel" in sys.modules) or any("kernel" in a for a in sys.argv)
if not _in_jupyter:
    if len(sys.argv) > 1:
        TENANT = sys.argv[1]
    if len(sys.argv) > 2:
        ENV_PATH = sys.argv[2]

# -- locate .env (laptop path won't exist in-cluster; search common spots) --
def _find_env(path):
    cands = [path, os.path.join(os.getcwd(), ".env"),
             os.path.join(os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd(), ".env"),
             os.path.expanduser("~/.env")]
    for c in cands:
        if c and os.path.exists(c):
            return c
    return None

# -- parse .env (no python-dotenv dependency); tolerate a missing file --
env = {}
_env_used = _find_env(ENV_PATH)
if _env_used:
    with open(_env_used, encoding="utf-8") as f:
        for ln in f:
            ln = ln.strip()
            if not ln or ln.startswith("#") or "=" not in ln:
                continue
            k, v = ln.split("=", 1)
            env[k.strip()] = v.split("#", 1)[0].strip()   # strip trailing inline comment
    ENV_PATH = _env_used
else:
    print(f"WARNING: no .env found (looked for {ENV_PATH}). "
          f"Using default host template '{TEMPLATE_DEFAULT}' for services; "
          f"ES test skipped (set ES_* below or fix ENV_PATH to include it).\n")

TEMPLATE  = env.get("HCM_SERVICE_TEMPLATE", TEMPLATE_DEFAULT)
OVERRIDES = {k[len("HCM_SVC_"):].lower(): v.rstrip("/")
             for k, v in env.items() if k.startswith("HCM_SVC_") and v}

# -- every entity endpoint (path, search_key, resp_key) --
ENTITIES = [
    ("Household",          "/household/v1/_search",                      "Household",          "Households"),
    ("HouseholdMember",    "/household/member/v1/_search",               "HouseholdMember",    "HouseholdMembers"),
    ("Individual",         "/individual/v1/_search",                     "Individual",         "Individual"),
    ("ProjectBeneficiary", "/project/beneficiary/v1/_search",            "ProjectBeneficiary", "ProjectBeneficiaries"),
    ("ProjectTask",        "/project/task/v1/_search",                   "Task",               "Tasks"),
    ("HFReferral",         "/referralmanagement/hf-referral/v1/_search", "HFReferral",         "HFReferrals"),
]

def host_for(path):
    svc = path.strip("/").split("/", 1)[0]                # "/household/v1/_search" -> "household"
    return svc, (OVERRIDES.get(svc) or TEMPLATE.format(service=svc)).rstrip("/")

def req_info():
    return {"apiId": "verify", "ver": "1.0", "ts": 0, "msgId": "verify",
            "userInfo": {"id": 0, "tenantId": TENANT, "uuid": "verify",
                         "userName": "verify", "type": "EMPLOYEE",
                         "roles": [{"code": "SYSTEM_ADMINISTRATOR", "tenantId": TENANT},
                                   {"code": "SUPERUSER", "tenantId": TENANT}]}}

def out(name, ok, detail):
    print(f"[{'OK ' if ok else 'ERR'}] {name:<55} {detail}")

print(f"=== verify .env services (tenant={TENANT}) ===")
print(f".env: {ENV_PATH}\n")

# -- ES base URL reachability --
es = env.get("ES_BASE_URL", "").rstrip("/")
if es:
    tok = base64.b64encode(f"{env.get('ES_USERNAME','')}:{env.get('ES_PASSWORD','')}".encode()).decode()
    t = time.time()
    try:
        r = requests.get(f"{es}/_cluster/health",
                         headers={"Authorization": f"Basic {tok}"}, verify=False, timeout=TIMEOUT)
        out(f"ES  {es}", r.status_code == 200,
            f"HTTP {r.status_code} {r.text[:80]} ({time.time()-t:.1f}s)")
    except Exception as ex:
        out(f"ES  {es}", False, f"{type(ex).__name__}: {str(ex)[:100]} ({time.time()-t:.1f}s)")

# -- each service _search --
print("\n-- service _search (read-only, limit=1) --")
hdrs = {"Content-Type": "application/json"}
for name, path, skey, rkey in ENTITIES:
    svc, host = host_for(path)
    url = host + path
    payload = {"RequestInfo": req_info(), skey: {"tenantId": TENANT}}
    t = time.time()
    try:
        r = requests.post(url, json=payload,
                          params={"tenantId": TENANT, "limit": 1, "offset": 0},
                          headers=hdrs, verify=False, timeout=TIMEOUT)
        dt = time.time() - t
        try:
            has_key = rkey in r.json()
        except Exception:
            has_key = False
        detail = f"[{svc}] HTTP {r.status_code}"
        if r.status_code == 200:
            detail += f"  resp_key '{rkey}' present={has_key}"
        else:
            detail += f": {r.text[:90]}"
        out(f"{name:<18}{url}", r.status_code == 200, f"{detail} ({dt:.1f}s)")
    except Exception as ex:
        out(f"{name:<18}{url}", False, f"[{svc}] {type(ex).__name__}: {str(ex)[:90]} ({time.time()-t:.1f}s)")

print("\n=== done (no writes) ===")
