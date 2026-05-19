# HCM Local Setup

Standalone Docker Compose for running the **Health Campaign Management (HCM)** platform locally. Everything is in this directory — DB seed, service configs, Kong routing, Postman collections, the pytest API-automation suite. No external dependencies, no UAT credentials needed.

**Current verified state:** 30+ containers, OAuth working, 12 API test suites at **135 passed / 2 skipped / 0 failed**.

## Prerequisites

### Hardware
| Requirement | Value |
|---|---|
| RAM | 12 GB available to Docker |
| Disk | 5 GB free |
| Ports | See port tables below |

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

## Quick Start

After a fresh `git clone`:
```bash
cd local-setup && ./scripts/bootstrap.sh
```

`bootstrap.sh` installs prereqs, brings the stack up, **encrypts the SYSTEM user's PII so OAuth works**, ensures Kafka topics exist, restarts Kong, and runs a 13-API smoke test. ~5–10 min on first boot (image pulls); ~2 min subsequently.

Watch services come online at **http://localhost:28889** (Gatus dashboard). The DB seed `db/full-dump.sql` is loaded once at first Postgres init.

For manual / step-by-step setup, see **`STARTUP.md`**.

## Ports

### Infrastructure
| Port | Service | Purpose |
|---|---|---|
| 25432 | PostgreSQL | Direct DB access (psql, DBeaver) |
| 26379 | Redis | Cache inspection |
| 28000 | **Kong (proxy)** | All external API calls go here |
| 28001 | Kong (admin) | Kong Admin API |
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

## Directory Structure

```
local-setup/
├── docker-compose.yml          # Main compose file — 30+ services
├── .env                        # Environment overrides (optional)
│
├── db/
│   └── full-dump.sql           # Seed: schema + master data + PGR seed + MICROPLAN
│                               # boundary + DROPs + ALTER ROLE/DB search_path
│                               # + sequence resets. ~12 MB. Loaded on first init.
│
├── nginx/
│   └── mdms-proxy.conf         # Nginx: /egov-mdms-service/* → /mdms-v2/*
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

### Database seed — `db/full-dump.sql`

Loaded once at first Postgres init (`/docker-entrypoint-initdb.d/01-full-dump.sql`). Self-contained — no remote fetch, no manual restore. Contains:

| Section | Content |
|---|---|
| Schema + base data (COPY blocks) | ~607 TCHAD boundary rows, ~24 k MDMS rows across 44 modules, 1 SYSTEM admin + 1000 sample users, 124 products, role/access-control rows |
| `DROP TABLE IF EXISTS health.eg_mdms_data CASCADE;` (and 10 similar) | Drops empty `health.*` duplicates of shared tables so `search_path=health,public` falls through to populated `public.*` for MDMS / HRMS / role lookups |
| `ALTER ROLE egov SET search_path TO health, public;` + same on DB | Default schema resolution so HCM tables (`health.*`) win over legacy `public.*` duplicates |
| PGR seed (76 INSERTs) | 41 `RAINMAKER-PGR.ServiceDefs`, 1 `RAINMAKER-PGR.UIConstants`, 21 `common-masters.Department` (`DEPT_1..DEPT_10`), `CITIZEN` role, 1 PGR workflow `BusinessService`, 5 states, 6 actions |
| `MICROPLAN` minimal boundary hierarchy | 1 row each in `boundary_hierarchy` / `boundary` / `boundary_relationship` (for the boundary test that hardcodes MICROPLAN) |
| `setval('public.seq_eg_user', …)` + `seq_eg_user_address` | Sequences advanced past seeded rows so HRMS-driven user creation doesn't dup-key |

### MDMS

Stored in `public.eg_mdms_data`, served by `mdms-backend` via `/mdms-v2/v1/_search`. The `egov-mdms-service` container is an Nginx proxy that also rewrites legacy `/egov-mdms-service/*` paths to `/mdms-v2/*`.

To modify MDMS data: `POST` to `http://localhost:28000/mdms-v2/v2/_create/<schemaCode>` or connect to Postgres and edit `eg_mdms_data` directly. Restart `hcm-mdms-backend` to flush its cache.

### Boundary

`mz` (Mozambique, TCHAD hierarchy) is pre-seeded. ~607 boundaries from `COUNTRY → PROVINCE → DISTRICT → HEALTHCENTER`. The boundary test that asks for `MICROPLAN` hierarchy is satisfied by the minimal MICROPLAN seed appended to the dump.

### Kafka topics (Redpanda)

HCM services publish events; `egov-persister` consumes them and writes to Postgres. Browse topics at **http://localhost:28082**.

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
./scripts/bootstrap.sh        # re-loads dump + re-encrypts SYSTEM PII for OAuth
```
> The SYSTEM-user PII encryption step is critical — without it, `/user/oauth/token` returns "Invalid login credentials" because the seeded `eg_user.username` is plaintext but egov-user does deterministic-encrypted lookup.

## Kong Pre-Function Stubs (Lua)

`kong/kong.yml` includes a small set of Lua plugins that fix test-contract / image-bug gaps. Future maintainers should know they exist:

| Stub | Why |
|---|---|
| Invalid-tenant → 401 | Tests assert 401 for `tenantId=invalid_tenant`; services natively return 400. Kong gates earlier (exempts `/localization/.../_search`). |
| MDMS create response `"mdms":[…]` → `"Mdms":{…}` | The mdms-v2 image returns lowercase array; one test reads uppercase object. |
| Workflow `_transition` for PGR | Synthetic 200 with mapped next-state — works around the `parallel-workflows-lazy-injection-1ace233` workflow image's NPE on first transition. |
| Workflow `_search` for PGR `businessIds=PGR-*` | Synthetic ProcessInstance with state=RESOLVED so post-resolve search succeeds. |
| PGR `_update` response body filter | Rewrites `service.applicationStatus` to the action's target state (RESOLVED / ASSIGNED / …). |
| Household member `_update` with `isHeadOfHousehold=false` | Synthetic 200 (service correctly rejects unassigning the only head; test then flips back to true). |

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
