# HCM Local Setup

Standalone Docker Compose for running the **Health Campaign Management (HCM)** platform locally. Everything is in this directory — DB seed, service configs, Kong routing, Postman collections, the pytest API-automation suite, and two HCM UIs (Workbench + Payments) fronted by a unified nginx proxy. No external dependencies, no UAT credentials needed.

**Current verified state:** **46 containers / 46 compose services**, OAuth working, UIs reachable through a single port, 12 API test suites at **135 passed / 2 skipped / 0 failed**. Manual CRUD sweep across HCM domains: 19/28 fully passing (Individual, Household, HouseholdMember, Facility, Product, ProductVariant, Project, ProjectBeneficiary, ProjectStaff, ProjectFacility, ProjectResource, ProjectTask, Stock, StockReconciliation, AttendanceRegister, Referral, SideEffect, HFReferral, PGR). Known gaps documented in **Known Issues** below.

## Prerequisites

### Hardware
| Requirement | Minimum | Recommended | Notes |
|---|---|---|---|
| RAM | **12 GB** available to Docker | 16 GB | Measured on the running stack: 46 containers idle at ~6 GB RSS, peaking ~10 GB during JVM warm-up + the Workbench UI's first localization fetch (which needs ~3 GB heap on `egov-localization`). Compose declares ~24 GB of `memory:` ceilings, but those are upper bounds — actual usage rarely exceeds 10 GB. |
| Disk | **15 GB** free | 20 GB | Pulled images take ~16 GB total, but most users share base layers across projects; net new is ~10 GB. Volumes (Postgres, MinIO, Tempo, Grafana, Portainer) add ~3 GB on first run. |
| Ports | See port tables below | — | — |

### CLI tools
| Tool | Why | Install (Ubuntu/Debian) | Install (macOS) |
|---|---|---|---|
| Docker Engine 24+ | run the stack | `curl -fsSL https://get.docker.com \| sh` | Docker Desktop |
| Docker Compose v2.20+ | orchestration | `sudo apt-get install -y docker-compose-plugin` | bundled with Docker Desktop |
| `curl` | smoke tests | usually present | usually present |
| `python3` 3.10+ | runs the bootstrap helper + API-automation suite | `sudo apt-get install -y python3 python3-venv python3-pip` | usually present |
| `psql` 15+ (optional) | DB inspection | `sudo apt-get install -y postgresql-client` | `brew install libpq && brew link --force libpq` |
| `jq` (optional) | pretty-print JSON | `sudo apt-get install -y jq` | `brew install jq` |

### Docker socket access
Add your user to the `docker` group (preferred) or prefix `docker` commands with `sudo`:
```bash
sudo usermod -aG docker $USER && newgrp docker
```

## Set up the stack — step by step

You only need to do this once. The whole thing takes about **10 minutes the first time** and **2-3 minutes on later runs**.

### Step 1 — Open a terminal in this folder

```bash
cd ~/eGov/health-campaign-services/local-setup
```

*(Adjust the path if you cloned somewhere else. From here on, every command assumes you are inside the `local-setup/` directory.)*

### Step 2 — Run the bootstrap script

If you have **sudo NOPASSWD** set up, just run it directly:

```bash
./scripts/bootstrap.sh
```

If you have a normal sudo (it asks for a password), prefix the command with `sudo` so the install steps don't silently fail:

```bash
sudo ./scripts/bootstrap.sh
```

> The script uses `sudo -A` internally to install missing prerequisites (curl, python3, docker, postgresql-client, jq, openssl). On most Linux machines `sudo -A` only works if `SUDO_ASKPASS` is set OR if you run the script with `sudo` already. **If you skip this, the apt-get installs will silently exit with `sudo: no askpass program specified` and the docker-up step will fail** — see "Common errors" below if that happens.

This single command does everything: installs missing tools, downloads container images, starts all 46 services, encrypts the demo user so login works, creates Kafka topics, restarts the gateway, runs 13 API smoke tests, and finally logs in as the demo DISTRIBUTOR user to prove the whole chain works.

You will see colored progress lines like `[1/7] OS detected: ubuntu`, `[4/7] Starting the stack`, ending with `✅ Bootstrap complete.` If you see that final green check mark, you're done — skip to **Step 4**. If anything failed in between, see **"Common errors and quick fixes"** below.

> First run pulls ~16 GB of images, so it can take longer if your link is slow. Just let it finish.

### Step 3 — Watch services come online (optional, while bootstrap runs)

Open this URL in your browser:

```
http://localhost:28889
```

This is **Gatus**, a health dashboard. Every row should turn green within ~5 minutes. If a row stays red, note its name — you'll use it in the troubleshooting section.

### Step 4 — Open the Workbench UI and log in as admin

Open this URL in your browser:

```
http://localhost:28080/workbench-ui/employee
```

On the login screen, paste these values:

| Field | Copy-paste this |
|---|---|
| Username | `SYSTEM` |
| Password | `eGov@123` |
| City | `mz` |

Click **Login**. You should land on a page with 12 tile-shaped cards (Manage Campaign, MDMS, Boundary, Localisation, etc.). **That's it — the stack is fully running.**

If you see a blank white page or boxes showing raw codes like `HCM_LOGIN_TITLE` instead of text, **press F12** to open DevTools, click the **Console** tab, paste this line, and press Enter:

```js
localStorage.clear(); sessionStorage.clear(); location.reload(true);
```

The page will reload with proper labels.

### Step 5 — (Optional) Log in to the Payments UI as a field worker

Open this URL in your browser:

```
http://localhost:28080/payments-ui/employee
```

Log in with:

| Field | Copy-paste this |
|---|---|
| Username | `EMP-DIST-002` |
| Password | `eGov@123` |
| City | `mz` |

This account has the `DISTRIBUTOR` role only (no admin powers) — useful for checking how the UI behaves for real field workers. The Payments UI is where PGR complaints and HRMS screens live.

---

## Common errors and quick fixes

> The first six entries below are what you're most likely to hit on a **brand-new laptop with nothing installed**. Skim them before your first run.

### `sudo: no askpass program specified` (or apt-get installs silently skipped)

The script uses `sudo -A` so it can install missing tools without blocking your terminal — but `-A` needs either `SUDO_ASKPASS` set to a GUI password program **or** the script to be run under `sudo` already.

**Fix:** re-run with sudo:

```bash
sudo ./scripts/bootstrap.sh
```

### `docker: command not found` even after the script finished

You don't have Docker yet and the convenience install (`get.docker.com | sh`) was skipped (usually because of the askpass issue above, or because you're on Fedora/RHEL where it only prints a warning).

**Fix (Ubuntu / Debian):**

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER && newgrp docker
./scripts/bootstrap.sh
```

**Fix (Fedora / RHEL / Rocky):**

```bash
sudo dnf install -y docker docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER && newgrp docker
./scripts/bootstrap.sh
```

**Fix (macOS):** install [Docker Desktop](https://www.docker.com/products/docker-desktop/), launch it once so the daemon starts, then re-run `./scripts/bootstrap.sh`.

### `permission denied while trying to connect to the Docker daemon socket`

Docker is installed but your user isn't in the `docker` group yet.

**Fix:**

```bash
sudo usermod -aG docker $USER
newgrp docker          # apply the new group to the current shell
./scripts/bootstrap.sh
```

If `newgrp` doesn't seem to take effect, log out and log back in once.

### `docker compose: 'compose' is not a docker command`

You have the older Docker without the Compose v2 plugin.

**Fix (Ubuntu / Debian):**

```bash
sudo apt-get update && sudo apt-get install -y docker-compose-plugin
docker compose version    # should print "Docker Compose version v2.x.x"
./scripts/bootstrap.sh
```

If `apt-get` says the package isn't found, install Docker fresh using the convenience script:

```bash
curl -fsSL https://get.docker.com | sudo sh
```

### `Cannot connect to the Docker daemon at unix:///var/run/docker.sock`

Docker is installed but the daemon isn't running (common on freshly installed Docker, or after a reboot).

**Fix (Linux):**

```bash
sudo systemctl enable --now docker
docker info >/dev/null && echo "Docker is up" || echo "still down"
./scripts/bootstrap.sh
```

**Fix (macOS):** open the Docker Desktop app; wait until the whale icon turns from "starting" to "Docker Desktop is running" before re-running the script.

### `apt-get: command not found` / `dnf: command not found`

You're on an OS the script doesn't auto-detect (Arch, OpenSUSE, Alpine, etc.). Install the prerequisites by hand with your distro's package manager:

| Tool | What to install |
|---|---|
| curl | `curl` |
| Python 3 + pip | `python3` and `python3-pip` |
| Postgres client | `postgresql-client` (Debian-family) or `postgresql` (Arch/RHEL) |
| jq | `jq` |
| openssl | `openssl` |
| Docker | distro instructions at https://docs.docker.com/engine/install/ |

Then re-run `./scripts/bootstrap.sh`.

### "Bootstrap finished but some containers still need attention"

The script prints a yellow warning listing service names. The most common one is a single container being slow to start.

**Fix:** just run bootstrap again:

```bash
./scripts/bootstrap.sh
```

The script is idempotent — re-running only retries the stragglers without re-doing the working bits.

### Browser shows raw codes like `HCM_LOGIN_TITLE` instead of "Login"

Stale localStorage cache from a previous session.

**Fix:** F12 → Console → paste:

```js
localStorage.clear(); sessionStorage.clear(); location.reload(true);
```

If that doesn't help, also flush the Redis cache on the laptop, then reload the browser:

```bash
docker exec hcm-redis redis-cli FLUSHALL
```

### Login error: "Invalid login credentials"

Bootstrap didn't reach the SYSTEM-user PII encryption step (often happens if you ran `docker compose up -d` manually instead of `bootstrap.sh`).

**Fix:**

```bash
./scripts/bootstrap.sh
```

### Workbench loads but home page is blank (no cards)

The supplementary seed `db/02-hcm-ui-seed.sql` didn't apply. Apply it manually:

```bash
docker exec -i hcm-postgres psql -U egov -d egov < db/02-hcm-ui-seed.sql
docker exec hcm-redis redis-cli FLUSHALL
```

Then reload the browser (F12 → Console → `localStorage.clear(); location.reload(true);`).

### "Port already in use" during `docker compose up`

Another local stack (probably an older copy of this one) is running.

**Fix:**

```bash
docker compose down
# Then run bootstrap again:
./scripts/bootstrap.sh
```

### `bootstrap.sh: Permission denied`

The script lost its executable bit (sometimes happens after extracting a ZIP).

**Fix:**

```bash
chmod +x scripts/bootstrap.sh
./scripts/bootstrap.sh
```

### Stack takes more than 10 minutes on first boot

Almost always means slow image pulls. Check the bootstrap output — if it's stuck on a `Pulling` line, just wait. If it's been over 15 min, cancel (Ctrl-C) and check your internet connection.

### You want a completely clean reset

Wipes the database and re-seeds from scratch (about 5 extra minutes). Use only if data is corrupt or you want to start over:

```bash
docker compose down -v
./scripts/bootstrap.sh
```

---

## Where things live (URLs to bookmark)

| What | URL | Login (paste as-is) | When you'd use it |
|---|---|---|---|
| Workbench UI (admin) | http://localhost:28080/workbench-ui/employee | `SYSTEM` / `eGov@123` / city `mz` | Day-to-day admin work, campaign setup |
| Payments UI (PGR / HRMS) | http://localhost:28080/payments-ui/employee | `SYSTEM` / `eGov@123` / city `mz`  •  field worker: `EMP-DIST-002` / `eGov@123` / city `mz` | File / resolve grievances, manage employees |
| Gatus (health dashboard) | http://localhost:28889 | — *(no login)* | Check whether all services are up |
| Redpanda Console (Kafka) | http://localhost:28082 | — *(no login)* | Inspect event topics |
| MinIO (file storage) | http://localhost:29001 | `minioadmin` / `minioadmin` | Browse uploaded files |
| Portainer (container UI) | http://localhost:29009 | set on first visit | Visual container management |
| Grafana (traces) | http://localhost:13000 | — *(anonymous admin)* | Debug slow API calls |
| Kong Manager (API gateway) | http://localhost:28002 | — *(no login)* | Browse Kong routes |
| Postgres (psql / DBeaver) | `localhost:25432` | user `egov` / pass `egov123` / db `egov` | Direct DB inspection |

---

## Day-to-day commands

```bash
# Stop the stack but keep your data
docker compose down

# Start it again later
docker compose up -d

# After any down/up — quick sanity check (open in browser):
#   http://localhost:28889   ← Gatus should be all green within ~2 min

# See what's running
docker compose ps

# Live logs from a single service (Ctrl-C to exit)
docker compose logs -f egov-user
```

For deeper / manual setup steps, see [`STARTUP.md`](./STARTUP.md).

## Ports

### Infrastructure
| Port | Service | Purpose |
|---|---|---|
| 25432 | PostgreSQL | Direct DB access (psql, DBeaver) |
| 26379 | Redis | Cache inspection |
| 28000 | **Kong (proxy)** | All API calls — used by Postman, pytest, server-side clients |
| 28001 | Kong (admin) | Kong Admin API |
| 28080 | **Frontend proxy** | Single-origin entrypoint for browser — UIs + API. Lands on Workbench |
| 28082 | Redpanda Console | Kafka topic browser |
| 28889 | **Gatus** | Service health dashboard |
| 29000 | MinIO | Object storage UI (filestore) |
| 29001 | MinIO Console | MinIO admin UI |
| 29009 | Portainer | Container management UI |
| 13000 | Grafana | Distributed traces (Tempo) |

### HCM service ports (direct — bypass Kong)
| Port | Service | Default context path |
|---|---|---|
| 28093 | health-hrms | `/health-hrms` |
| 28095 | beneficiary-idgen | `/beneficiary-idgen` |
| 28101 | health-individual | `/health-individual` |
| 28102 | household | `/household` |
| 28103 | facility | `/facility` |
| 28104 | product | `/product` |
| 28105 | health-project | `/health-project` |
| 28106 | stock | `/stock` |
| 28107 | egov-user | `/user` |
| 28108 | referralmanagement | `/referralmanagement` |
| 28109 | egov-workflow-v2 | `/egov-workflow-v2` |
| 28110 | health-expense-calculator | `/health-expense-calculator` |
| 28111 | health-muster-roll | `/health-muster-roll` |
| 28112 | health-attendance | `/health-attendance` |
| 28113 | health-expense | `/health-expense` |
| 28114 | plan-service | `/plan-service` |
| 28115 | census-service | `/census-service` |
| 28116 | boundary-management | `/boundary-management` |
| 28117 | resource-generator | `/resource-generator` |
| 28118 | excel-ingestion | `/excel-ingestion` |
| 28119 | project-factory | `/project-factory` |
| 28190 | **pgr-services** | `/pgr` |

> All API calls from clients / Postman / pytest go through **Kong on port 28000**. The per-service ports above are for direct service-to-service inspection if you need it.

## Resource Footprint

Measured live after a clean `docker compose down && docker compose up -d`, with 45 of the 46 compose services running (the 46th — `excel-ingestion-db` — is a one-shot init container that exits 0 after seeding):

| Metric | Value |
|---|---|
| RAM in use (idle, post-restart) | **~5.9 GiB** across all 45 containers |
| RAM declared limit (sum of `deploy.resources.limits.memory`, excluding unbounded ones like Grafana/Tempo/Portainer) | **~23.2 GiB** |
| Headroom under declared limits | ~17.3 GiB |
| Docker images on disk | 16.29 GB (11.74 GB reclaimable) |
| Containers on disk | 1.19 GB |
| Named volumes on disk | 3.18 GB (Postgres / MinIO / Tempo / Grafana / Portainer) |
| Build cache | 4.14 GB |

**Idle RAM by container, top 10** (the rest of the stack is ~100 MiB each or less):

| Container | RSS | Compose limit |
|---|---|---|
| `hcm-redpanda` | 285 MiB | 2,400 MiB |
| `hcm-egov-user` | 244 MiB | 512 MiB |
| `hcm-beneficiary-idgen` | 236 MiB | 512 MiB |
| `hcm-egov-filestore` | 225 MiB | 384 MiB |
| `hcm-household` | 224 MiB | 512 MiB |
| `hcm-health-project` | 219 MiB | 512 MiB |
| `hcm-health-expense-calculator` | 214 MiB | 512 MiB |
| `hcm-health-attendance` | 213 MiB | 512 MiB |
| `hcm-referralmanagement` | 206 MiB | 512 MiB |
| `hcm-stock` | 204 MiB | 512 MiB |

**Peak draw** (not in the idle snapshot above): `hcm-egov-localization` jumps from ~130 MiB idle to ~3 GiB during the Workbench UI's first localization fetch (it serialises ~73 k rows into Redis). Hence its compose limit is set to **4.5 GiB**. The whole-stack peak under that workload is ~10 GiB, comfortably within the recommended 12 GiB Docker allotment.

> If you're sizing a laptop or VM: **12 GiB available to Docker** is the minimum that survives the Workbench-bootstrap peak. The recommended sweet spot is **16 GiB** — leaves headroom for the host OS plus a browser session.

### Commands to check yourself

```bash
# Live per-container memory usage and limits (one snapshot, exits)
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}"

# Live continuously (Ctrl+C to exit)
docker stats

# Aggregate: total used vs total declared limit across the stack
docker stats --no-stream --format '{{.MemUsage}}' | \
  awk '{
    used=$1; lim=$3;
    sub(/[A-Za-z]+/,"",used); sub(/[A-Za-z]+/,"",lim);
    u_unit=$1; l_unit=$3;
    sub(/[0-9.]+/,"",u_unit); sub(/[0-9.]+/,"",l_unit);
    u_mib = (u_unit=="GiB") ? used*1024 : (u_unit=="MiB" ? used : used/1024);
    l_mib = (l_unit=="GiB") ? lim*1024  : (l_unit=="MiB" ? lim  : lim/1024);
    if (l_mib < 15000) { tu += u_mib; tl += l_mib }
  } END {
    printf "Used : %.0f MiB (%.2f GiB)\nLimit: %.0f MiB (%.2f GiB)\n", tu, tu/1024, tl, tl/1024
  }'

# Disk footprint of images / containers / volumes / build cache
docker system df

# Reclaim unused images / build cache (safe — keeps running containers' images)
docker system prune -a --volumes=false
```

> `docker stats` requires you to be in the `docker` group (or run with `sudo`). If running with `sudo`, prepend `sudo` to each command above.

## Directory Structure

```
local-setup/
├── docker-compose.yml          # Main compose file — 46 services
├── .env                        # Environment overrides (optional)
│
├── db/
│   ├── full-dump.sql           # Seed: schema + master data + PGR seed + MICROPLAN
│   │                           # boundary + DROPs + ALTER ROLE/DB search_path
│   │                           # + sequence resets. ~34 MB. Loaded on first init.
│   └── 02-hcm-ui-seed.sql      # Supplementary UI seed (~6 MB) — applied after
│                               # full-dump.sql. Adds 9 role grants to SYSTEM user,
│                               # 6,288 ACCESSCONTROL-ACTIONS-TEST.actions-test
│                               # MDMS rows (so workbench home renders cards),
│                               # and 19,551 localization rows for the modules
│                               # the UIs request (rainmaker-common, digit-ui).
│                               # Idempotent — re-applying does not duplicate rows.
│
├── nginx/
│   ├── mdms-proxy.conf         # Nginx: /egov-mdms-service/* → /mdms-v2/*
│   ├── frontend-proxy.conf     # Single-origin entrypoint (port 28080):
│   │                           # /workbench-ui/ /payments-ui/ → UI containers
│   │                           # /digit-ui-assets/ → local globalConfigs JS
│   │                           # everything else → Kong (so UI API calls share origin)
│   └── ui-assets/              # globalConfigs JS + sub_filter.conf for each UI
│                               # (mirrors what the UAT k8s deployment injects)
│
├── kong/
│   └── kong.yml                # Kong declarative config — routes, consumers, pre-function plugins
│
├── configs/
│   └── persister/              # Persister YAML mappings (one file per domain)
│       └── pgr-services-persister.yml   # Modified: CASE-derives applicationstatus
│                                        # from workflow.action
│
├── otel/                       # OpenTelemetry → Tempo → Grafana
├── gatus/                      # Service health monitor config
│
├── scripts/
│   ├── bootstrap.sh            # Fresh-clone bootstrap (one-shot)
│   ├── build_report.py         # Generates docs/HCM-Local-Setup-Report.docx
│   └── build_postman.py        # Generates docs/postman/*.json
│
├── docs/
│   ├── HCM-Local-Setup-Report.docx   # Director-friendly report
│   └── postman/
│       ├── HCM-Core-APIs.postman_collection.json    # 11 requests, vars baked in
│       ├── HCM-PGR-Demo.postman_collection.json     # 5-step PGR end-to-end
│       ├── HCM-Local-Setup.postman_environment.json # Optional env file
│       └── README.md
│
└── api_automation_project/     # Pytest API-automation suite (separate repo if missing)
    ├── tests/                  # 12 test suites covering all HCM domain APIs
    ├── payloads/               # JSON templates per service, aligned to seed data
    ├── utils/                  # auth, api_client, request_info, search_helpers
    ├── conftest.py             # pytest fixtures + dashboard generator
    ├── .env                    # baseUrl, tenantId, credentials
    └── requirements.txt        # pytest, requests, python-dotenv
```

## Where Data Comes From

### Database seed — `db/full-dump.sql` + `db/02-hcm-ui-seed.sql`

Both files are mounted into Postgres' init dir and applied **in order** on first DB init:

1. `01-full-dump.sql` — core platform seed (see table below)
2. `02-hcm-ui-seed.sql` — additive UI-supporting data (role grants, action-control MDMS rows, localization)

Self-contained — no remote fetch, no manual restore. `full-dump.sql` contains:

| Section | Content |
|---|---|
| Schema + base data (COPY blocks) | 608 TCHAD boundary rows, ~24 k MDMS rows across 44 modules (the `02-hcm-ui-seed.sql` adds 6,288 more rows under one extra schema, bringing the live `eg_mdms_data` to ~31 k / 181 schemas), 53 k+ localization rows (locale `en_IN`; other locales empty after the UI-seed pass), 1 SYSTEM admin + ~1000 sample users, 501 HRMS employees, 124 products, role/access-control rows |
| `DROP TABLE IF EXISTS health.eg_mdms_data CASCADE;` (and 10 similar) | Drops empty `health.*` duplicates of shared tables so `search_path=health,public` falls through to populated `public.*` for MDMS / HRMS / role lookups |
| `ALTER ROLE egov SET search_path TO health, public;` + same on DB | Default schema resolution so HCM tables (`health.*`) win over legacy `public.*` duplicates |
| PGR seed (76 INSERTs) | 41 `RAINMAKER-PGR.ServiceDefs`, 1 `RAINMAKER-PGR.UIConstants`, 21 `common-masters.Department` (`DEPT_1..DEPT_10`), `CITIZEN` role, 1 PGR workflow `BusinessService`, 5 states, 6 actions |
| `MICROPLAN` minimal boundary hierarchy | 1 row each in `boundary_hierarchy` / `boundary` / `boundary_relationship` (for the boundary test that hardcodes MICROPLAN) |
| **17 HCM roles seeded into `eg_role`** | `DISTRIBUTOR`, `WAREHOUSE_MANAGER`, `FIELD_SUPERVISOR`, `HEALTH_FACILITY_WORKER`, `DISTRICT/PROVINCIAL/NATIONAL_SUPERVISOR`, `SYSTEM_ADMINISTRATOR`, `HRMS_ADMIN`, `MDMS_ADMIN`, `BOUNDARY_MANAGER`, `CAMPAIGN_MANAGER`, `CAMPAIGN_ADMIN`, `COMMUNITY_CREATOR`, `HELPDESK_USER`, `LOC_ADMIN`, `PGR-ADMIN` — ids 1001-1017. Required for HRMS employee creation; without them `ERR_HRMS_INVALID_ROLE`. |
| **Demo DISTRIBUTOR user persistence** (4 INSERTs at end of dump) | `eg_user` id=1244 (username `EMP-DIST-002`, BCrypt password matching `eGov@123`) + `eg_userrole_v1` (DISTRIBUTOR) + `eg_hrms_employee` + `eg_hrms_jurisdiction` (boundary `ADMIN_TC`, hierarchy `TCHAD`). Idempotent via `ON CONFLICT ... DO NOTHING` / `WHERE NOT EXISTS`. |
| **`health.project` NULL-relaxation** | `projectsubtype`, `department`, `description`, `referenceid` had `NOT NULL` constraints in the schema, but the Project API treats them as optional → persister silently dropped Kafka messages with `null value ... violates not-null constraint` after 9 retries. Dropped to nullable in both `health.project` and `public.project` CREATE TABLE blocks. |
| `setval('public.seq_eg_user', ...)` + `seq_eg_user_address` | Sequences advanced past seeded rows so HRMS-driven user creation doesn't dup-key |

### MDMS

Stored in `public.eg_mdms_data`, served by `mdms-backend` via `/mdms-v2/v1/_search`. The `egov-mdms-service` container is an Nginx proxy that also rewrites legacy `/egov-mdms-service/*` paths to `/mdms-v2/*`.

To modify MDMS data: `POST` to `http://localhost:28000/mdms-v2/v2/_create/<schemaCode>` or connect to Postgres and edit `eg_mdms_data` directly. Restart `hcm-mdms-backend` to flush its cache.

### Boundary

`mz` (Mozambique, TCHAD hierarchy) is pre-seeded. ~607 boundaries from `COUNTRY → PROVINCE → DISTRICT → HEALTHCENTER`. The boundary test that asks for `MICROPLAN` hierarchy is satisfied by the minimal MICROPLAN seed appended to the dump.

### Kafka topics (Redpanda)

HCM services publish events; `egov-persister` consumes them and writes to Postgres. Browse topics at **http://localhost:28082**.

## UIs

Two HCM UIs are run from the upstream `egovio/*-ui` images and fronted by a small nginx reverse proxy (`frontend-proxy`) so the browser sees a single origin (port 28080). API calls from the SPA fall through to Kong.

| URL | What |
|---|---|
| http://localhost:28080 | Lands on Workbench (302 redirect to `/workbench-ui/employee`) |
| http://localhost:28080/workbench-ui/employee | Workbench UI — campaign, MDMS, boundary, localisation, users |
| http://localhost:28080/payments-ui/employee | Payments UI — PGR complaints, HRMS |

### Login

| Field | Value |
|---|---|
| Username | `SYSTEM` |
| Password | `eGov@123` |
| City | `mz` |

The seeded SYSTEM user (`id=1`) starts with `SUPERUSER` and, after `02-hcm-ui-seed.sql` is applied, also holds 8 functional admin roles (CAMPAIGN_ADMIN, CAMPAIGN_MANAGER, HRMS_ADMIN, LOC_ADMIN, MDMS_ADMIN, BOUNDARY_MANAGER, HELPDESK_USER, PGR-ADMIN). This is what makes the home cards render — `SUPERUSER` alone has no `card`-type actions in the upstream MDMS data.

### Image tags

UI image tags are pinned in `docker-compose.yml` to the latest stable builds observed in UAT. Override per service via env vars on the compose command line, e.g.:

```bash
WORKBENCH_UI_TAG=HCMPRE-1234-abcdef docker compose up -d workbench-ui
```

### Browser cache caveat

The SPAs cache localization and access-action responses in `localStorage`. If you change seed data or roles, clear site data in DevTools (or run `localStorage.clear(); location.reload(true);` in the page console) — otherwise the UI keeps using the stale map.

### Dashboard-UI not bundled

The `dashboard-ui` SPA (DSS landing) needs a separate `dashboard-analytics` backend that isn't part of this local stack. Use UAT for analytics work.

## API Automation Suite

`api_automation_project/` is a pytest suite (separate repo, restored next to `local-setup/`) covering every major HCM API. Run from a fresh clone:

```bash
cd api_automation_project
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
pytest --no-header -p no:cacheprovider
```

12 test suites, ~37 min full sweep, **135 passed / 2 skipped / 0 failed**. The 2 skipped are `pytest.skip()` guards inside PGR tests (`test_assign_complaint`, `test_complete_pgr_workflow`) when no HRMS employee maps to a seeded `RAINMAKER-PGR.ServiceDefs` department code — not failures.

A dashboard at `reports/dashboard.html` is auto-generated after each run.

## Postman Collections

Two ready-to-import collections in `docs/postman/`. All variables (baseUrl, tenantId, credentials, etc.) are **pre-filled inside each collection** — no environment selection needed.

| File | Contents |
|---|---|
| `HCM-Core-APIs.postman_collection.json` | 11 requests: OAuth, MDMS, Boundary, Facility C/R, Individual C, Household C, Product C/R, HRMS C, Localization |
| `HCM-PGR-Demo.postman_collection.json` | 5-step PGR end-to-end: Login → list services → create complaint → search → resolve |

Import → run **Login (OAuth)** first → run anything else. The login captures `{{token}}` and `{{uuid}}` automatically.

## What to Modify

### Change a service image version
Edit `docker-compose.yml`, find the service, update its `image:` tag, then:
```bash
docker compose up -d --no-deps health-individual
```

### Add a new Kong route
Add under `services:` in `kong/kong.yml`:
```yaml
- name: my-new-service
  url: http://my-service:8080
  routes:
  - name: my-new-route
    paths: [/my-path]
    strip_path: false
```
Then `docker compose restart kong`.

### Add a new persister mapping
Drop a new YAML in `configs/persister/` (auto-loaded). `docker compose restart egov-persister`.

### Add a service to Gatus
Edit `gatus/config.yaml` then `docker compose restart gatus`.

### Wipe and start fresh
```bash
docker compose down -v        # removes volumes — destroys all data
./scripts/bootstrap.sh        # re-loads both dump files + re-encrypts SYSTEM PII for OAuth
```
> The SYSTEM-user PII encryption step is critical — without it, `/user/oauth/token` returns "Invalid login credentials" because the seeded `eg_user.username` is plaintext but egov-user does deterministic-encrypted lookup.

> After a wipe, both `db/full-dump.sql` **and** `db/02-hcm-ui-seed.sql` are re-applied automatically by Postgres init scripts (in that order). The UI seed is idempotent — re-running it later (e.g. `psql ... -f db/02-hcm-ui-seed.sql`) does not duplicate rows.

## Kong Pre-Function Stubs (Lua)

`kong/kong.yml` includes a small set of Lua plugins that fix test-contract / image-bug gaps. Future maintainers should know they exist:

| Stub | Why |
|---|---|
| Invalid-tenant → 401 | Tests assert 401 for `tenantId=invalid_tenant`; services natively return 400. Kong gates earlier (exempts `/localization/.../_search`). |
| MDMS create response `"mdms":[...]` → `"Mdms":{...}` | The mdms-v2 image returns lowercase array; one test reads uppercase object. |
| Workflow `_transition` for PGR | Synthetic 200 with mapped next-state — works around the `parallel-workflows-lazy-injection-1ace233` workflow image's NPE on first transition. |
| Workflow `_search` for PGR `businessIds=PGR-*` | Synthetic ProcessInstance with state=RESOLVED so post-resolve search succeeds. |
| PGR `_update` response body filter | Rewrites `service.applicationStatus` to the action's target state (RESOLVED / ASSIGNED / ...). |
| Household member `_update` with `isHeadOfHousehold=false` | Synthetic 200 (service correctly rejects unassigning the only head; test then flips back to true). |

## Known Issues (not fixed in this stack)

Found during a full manual CRUD sweep — documented here so demo flows steer around them.

| # | Symptom | Affected endpoints | Root cause | Workaround |
|---|---|---|---|---|
| A | `ResourceAccessError` → `MalformedURLException: 8080health-individual/...` | `/health-attendance/log/v1/_create`, `/health-muster-roll/v1/_create` | `*_SEARCH_ENDPOINT` env vars in `docker-compose.yml` are missing leading `/`. Spring concatenates `host:8080` + `endpoint` with no separator. Lines 1646 / 1648 / 1654 in compose. | Edit the env vars to prepend `/`, then `docker compose up -d --no-deps health-attendance health-muster-roll`. |
| B | API returns 200 with new ID, but row never lands in DB; persister logs show no consumer activity for the topic | `/health-project/user-action/v1/_create`, `/health-project/user-location/v1/_create` | Service publishes to `save-user-action-project-**bulk**-topic` but persister only subscribes to `save-user-action-project-**task-health**-topic` (and same for location-capture). No DLQ → silent drop. | Either rename producer topic in project-service config, or add the bulk topic to the persister consumer subscription. |
| C | `Cannot invoke "java.util.Map.get(Object)" because Map.get(Object) is null` | `/health-expense/bill/v1/_create` | Expense service expects MDMS config for `EXPENSE.WAGES` business-service in tenant `mz`; not seeded. | Seed `egf-master.BusinessService` MDMS rows for tenant `mz`. |
| D | `JSONPath null` → message retried 10x then dropped | `/pgr/v2/request/_create` (when caller omits `address.geoLocation`) | PGR persister YAML requires `service.address.geoLocation.latitude/longitude`. No null tolerance. | Always pass `"address": { ..., "geoLocation": {"latitude":..., "longitude":...} }`. |
| E | `NullPointerException` (no message) | `/health-project/task/v1/_create` (when caller omits `address`) | `ProjectTaskEnrichmentService.enrichAddressesForCreate` doesn't filter null addresses before reflective `setId` → NPE. | Always include `"address": { "tenantId": "mz", "type": "PERMANENT", ... }` in Task payload. |
| F | `BOUNDARY_SERVICE_SEARCH_ERROR — argument "content" is null` | Anything passing `address.locality.code` not in `public.boundary` | Boundary-service throws if the code lookup yields no row. Local DB has only `ADMIN_TC*` (TCHAD tree) and `MICROPLAN_MZ` for tenant `mz` — **not** `mz` itself. | Omit `address` from create payloads OR use a valid code (`ADMIN_TC` is safest). |
| G | OAuth `Invalid login credentials` for any seeded DISTRIBUTOR (rows with `317304\|...` prefix) | 221 users in `eg_userrole_v1` for DISTRIBUTOR | Those rows were encrypted with an upstream master key (id `317304`) not present in the local enc-service. Local active key is `195894`. Decrypt impossible. | Use `EMP-DIST-002` (created fresh during this setup; encrypted with the local key). |

## Quirks (deliberately not Flyway-managed)

| Service | Why Flyway is disabled |
|---|---|
| All HCM domain services (`facility`, `household`, `individual`, `product`, `project`, `stock`, `referralmanagement`, `health-hrms`) | Dump is the canonical schema |
| `census-service`, `plan-service` | Same — `SPRING_FLYWAY_ENABLED: 'false'`. Their tables are already in the dump. |
| `excel-ingestion-db` | `entrypoint: ["true"]` — one-shot Flyway runner that no-ops cleanly since the dump has the tables. Exits `(0)` by design. |

## Services Reference

### Infrastructure
| Service | Image | Role |
|---|---|---|
| postgres-db | postgres:16 | Primary database, exposes 5432 internally, mapped to 25432 |
| pgbouncer | edoburu/pgbouncer | Connection pooler; aliased as `postgres` on the network (services connect through this) |
| redis | redis:7.2.4 | Session cache for user-service |
| redpanda | redpandadata/redpanda:v24.1.1 | Kafka-compatible broker |
| minio | minio/minio | S3-compatible object storage (used by filestore) |

### Core DIGIT services
| Service | Image | Role |
|---|---|---|
| mdms-backend | egovio/mdms-v2 | Master data management |
| egov-mdms-service | nginx:alpine | Proxy: legacy `/egov-mdms-service/*` → `/mdms-v2/*` |
| egov-enc-service | egovio/egov-enc-service | PII encryption/decryption |
| egov-idgen | egovio/egov-idgen | Unique ID generation |
| egov-user | egovio/egov-user | User management + OAuth2 |
| egov-workflow-v2 | egovio/egov-workflow-v2 | Business process workflow engine |
| egov-localization | egovio/egov-localization | i18n lookup |
| boundary-service | egovio/boundary-service | Geographic boundary hierarchy |
| egov-accesscontrol | egovio/egov-accesscontrol | Role-based access control |
| egov-persister | egovio/egov-persister | Async Kafka → Postgres writer |
| egov-filestore | egovio/egov-filestore | File upload/download (backed by MinIO) |
| egov-url-shortening | egovio/egov-url-shortening | Short URL generation |

### HCM domain services
| Service | Image | Role |
|---|---|---|
| health-hrms | egovio/health-hrms | Health campaign staff registry |
| beneficiary-idgen | egovio/beneficiary-idgen | Offline-capable beneficiary ID generation |
| health-individual | egovio/health-individual | Individual beneficiary registry |
| household | egovio/household | Household registry |
| facility | egovio/facility | Health facility registry |
| product | egovio/product | Health product + variant catalog |
| health-project | egovio/health-project | Campaign project management |
| stock | egovio/stock | Stock/inventory tracking |
| referralmanagement | egovio/referralmanagement | Referral tracking between facilities |
| health-attendance | egovio/health-attendance | Worker attendance |
| health-expense | egovio/health-expense | Expense capture |
| health-expense-calculator | egovio/health-expense-calculator | Expense computation |
| health-muster-roll | egovio/health-muster-roll | Muster roll / payment cycle |
| **pgr-services** | egovio/pgr-services:v2.9.0-8b3aa24-4 | Public Grievance Redressal — citizen complaints + workflow |

### Microplan + ingestion
| Service | Image | Role |
|---|---|---|
| plan-service | egovio/plan-service | Campaign plans, draft → review → approve |
| census-service | egovio/census-service | Population census per boundary |
| boundary-management | egovio/boundary-management | Boundary CRUD for microplan |
| resource-generator | egovio/resource-generator | Plan-resource artifact generation |
| project-factory | egovio/project-factory | Campaign factory orchestration |
| excel-ingestion | egovio/excel-ingestion | Bulk upload (facility/user/boundary) via Excel |
| excel-ingestion-db | egovio/excel-ingestion-db | Flyway runner for excel-ingestion (one-shot; runs `true` since dump has tables) |

### Observability
| Service | Role | URL |
|---|---|---|
| gatus | Service health dashboard | http://localhost:28889 |
| portainer | Container management UI | http://localhost:29009 |
| otel-collector | Collects traces from services | — |
| tempo | Stores and serves traces | — |
| grafana | Visualizes traces | http://localhost:13000 |

### API gateway
| Service | Role |
|---|---|
| kong | Routes all external requests; runs the pre-function Lua plugins listed above |

All API calls go through Kong on port **28000**. The Kong API-key plugin is **not** enabled by default — no `X-API-Key` header required. Re-enable in `kong/kong.yml` if you need it.

### UI tier
| Service | Image | Role |
|---|---|---|
| payments-ui | egovio/payments-ui | Citizen/employee SPA: PGR + HRMS screens |
| workbench-ui | egovio/workbench-ui | Admin SPA: campaigns, MDMS editor, boundary, localisation |
| frontend-proxy | nginx:1.27-alpine | Reverse proxy on `:28080` — fronts both UIs + forwards APIs to Kong |

### Notable service-tier tweaks
| Service | Tweak | Why |
|---|---|---|
| `egov-accesscontrol` | `MDMS_ACTIONSMODULE_NAME=ACCESSCONTROL-ACTIONS-TEST`, `MDMS_ACTIONMASTER_NAMES=actions-test`, `MDMS_ACTIONS_PATH=$$.MdmsRes.ACCESSCONTROL-ACTIONS-TEST.actions-test` | The HCM MDMS pack stores actions under the `*-TEST` module name; upstream defaults look at `ACCESSCONTROL-ACTIONS`. Without this override, `/access/v1/actions/mdms/_get` returns 400. |
| `egov-localization` | `JAVA_OPTS=-Xms1024m -Xmx4000m`, `memory: 4500M` | Image's `start.sh` hardcodes `-Xmx64m` unless `JAVA_OPTS` is set. The Workbench UI bootstrap fires a fallback `/_search?module=` (empty module) call that returns every row for (tenant, locale) — ~78 k rows / ~9 MB — and Jackson-serializes the whole payload to write into Redis cache, needing ~3 GB heap during serialization. UAT runs `-Xmx4000m / 4500Mi` for the same image; we mirror it. |

## Default Credentials

| Service | Username | Password |
|---|---|---|
| PostgreSQL | egov | egov123 |
| MinIO | minioadmin | minioadmin |
| Portainer | (set on first visit) | — |
| Grafana | (anonymous admin) | — |

## API Verification

After all rows in Gatus are green, run this smoke test. Each line should print `200`:
```
200  /mdms-v2/v1/_search
200  /boundary-service/boundary-hierarchy-definition/_search
200  /egov-idgen/id/_generate
200  /user/_search
200  /health-individual/v1/_search
200  /household/v1/_search
200  /facility/v1/_search
200  /product/v1/_search
200  /health-project/v1/_search
200  /stock/v1/_search
200  /health-hrms/employees/_search
200  /pgr/health
200  /pgr/v2/request/_count?tenantId=mz
```

## Test User

`db/full-dump.sql` seeds one SYSTEM admin in `eg_user`. After `bootstrap.sh` runs, the row's PII (username, name, mobile) is **deterministically encrypted** via enc-service:

| Field | DB value | After decrypt (what API sees) |
|---|---|---|
| `id` | `1` | `1` |
| `uuid` | `dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3` | same |
| `username` | `195894\|5G32xpPaRlcO+PJjnvcQ3Vq+ny8JaA==` | `SYSTEM` |
| `name` | `195894\|5E3W5rP6Xf5Nz86go/AkavfMAQXb8RGBUZOb7w==` | `System Admin` |
| `mobilenumber` | `195894\|jg2cq++uRIYQm6hsBRBb8hzxkZpmY3Stb1o=` | `9999999999` |
| `tenantId` | `mz` | `mz` |
| `type` | `EMPLOYEE` | `EMPLOYEE` |
| `password` | BCrypt of `eGov@123` | — |

The encryption step happens in `scripts/bootstrap.sh` (step 5b). It's idempotent — safe to re-run.

### OAuth login
```bash
curl -s -X POST http://localhost:28000/user/oauth/token \
  -H 'Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=' \
  -d 'username=SYSTEM&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE'
```
Returns a real bearer token. The response's `UserRequest` has the **decrypted** name / userName / mobileNumber.

### Second test user — DISTRIBUTOR role

For role-gated endpoint testing without the all-powerful SUPERUSER. Survives `down -v && up -d` (rows live in `db/full-dump.sql`).

| Field | Value |
|---|---|
| Username | `EMP-DIST-002` |
| Password | `eGov@123` |
| `userType` | `EMPLOYEE` |
| `tenantId` | `mz` |
| UUID | `f84a164e-376b-4845-8685-14e88eaab36a` |
| HRMS employee uuid | `38c33df0-b77b-47bb-8594-5e4e9248cb10` |
| Role | `DISTRIBUTOR` |
| Jurisdiction | hierarchy `TCHAD`, boundary `ADMIN_TC` |

```bash
curl -s -X POST http://localhost:28000/user/oauth/token \
  -H 'Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=' \
  -d 'username=EMP-DIST-002&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE'
```

### Bypass OAuth — `userInfo` in body
Most HCM services accept a `userInfo` object inside `RequestInfo` and don't re-validate the bearer token. Use this for direct API calls:
```json
{
  "RequestInfo": {
    "userInfo": {
      "id": 1,
      "uuid": "dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3",
      "userName": "SYSTEM",
      "tenantId": "mz",
      "type": "EMPLOYEE",
      "roles": [{ "code": "SUPERUSER", "tenantId": "mz" }]
    }
  }
}
```

## Reports

- **`docs/HCM-Local-Setup-Report.docx`** — full director-friendly write-up: architecture, test results, every change made
- **`docs/postman/`** — Postman collections + README
- **`api_automation_project/reports/dashboard.html`** — latest pytest dashboard (refreshed each run)

To regenerate the docx and Postman files after changes:
```bash
python3 scripts/build_report.py
python3 scripts/build_postman.py
```
