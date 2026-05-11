# HCM Local Setup

Standalone Docker Compose for running the **Health Campaign Management (HCM)** platform locally. Everything needed is in this directory — no external dependencies.

## Prerequisites

| Requirement | Version |
|---|---|
| Docker Engine | 24+ |
| Docker Compose | v2.20+ (plugin, not standalone) |
| RAM | 12 GB available to Docker |
| Disk | 5 GB free |
| Ports | See port table below |

## Quick Start

```bash
docker compose up -d
```

Watch services come online at **http://localhost:28889** (Gatus dashboard).

All services are healthy when every row in Gatus turns green — typically 3–5 minutes on first boot.

## Ports

| Port | Service | Purpose |
|---|---|---|
| 25432 | PostgreSQL | Direct DB access (psql, DBeaver) |
| 26379 | Redis | Cache inspection |
| 28000 | Kong (proxy) | All API calls go here |
| 28001 | Kong (admin) | Kong Admin API |
| 28082 | Redpanda Console | Kafka topic browser |
| 28889 | **Gatus** | Service health dashboard |
| 29000 | MinIO | Object storage UI (filestore) |
| 29001 | MinIO Console | MinIO admin UI |
| 29009 | Portainer | Container management UI |
| 13000 | Grafana | Distributed traces (Tempo) |

### HCM Service Ports (direct — bypass Kong)

| Port | Service |
|---|---|
| 28093 | health-hrms |
| 28095 | beneficiary-idgen |
| 28101 | health-individual |
| 28102 | household |
| 28103 | facility |
| 28104 | product |
| 28105 | health-project |
| 28106 | stock |
| 28108 | referral-management |
| 28110 | health-expense-calculator |
| 28111 | health-muster-roll |
| 28112 | health-attendance |
| 28113 | health-expense |

## Directory Structure

```
hcm-setup/
├── docker-compose.yml          # Main compose file
├── .env                        # Environment overrides (optional)
│
├── db/
│   └── full-dump.sql           # PostgreSQL seed — schemas + master data
│
├── nginx/
│   └── mdms-proxy.conf         # Nginx reverse-proxy: /egov-mdms-service/* → /mdms-v2/*
│
├── kong/
│   └── kong.yml                # Kong declarative config — all routes and consumers
│
├── configs/
│   └── persister/              # Persister YAML mappings (one file per domain)
│       ├── individual-persister.yml
│       ├── household-persister.yml
│       ├── facility-persister.yml
│       ├── product-persister.yml
│       ├── project-persister.yml
│       ├── project-task-persister.yml
│       ├── stock-persister.yml
│       ├── referral-management-persister.yml
│       ├── hrms-employee-persister.yml
│       ├── hrms-employee-health-persister.yml
│       ├── id-pool-persister.yml
│       ├── id-pool-dispatch-log-persister.yml
│       └── ... (core DIGIT persisters)
│
├── otel/
│   ├── otel-collector-config.yaml   # OpenTelemetry collector pipeline
│   ├── tempo-config.yaml            # Tempo trace storage config
│   └── grafana/
│       └── provisioning/
│           └── datasources/
│               └── tempo.yaml       # Grafana → Tempo datasource
│
└── gatus/
    └── config.yaml             # Gatus endpoint definitions (what to monitor)
```

## Where Data Comes From

### MDMS (Master Data Management)

MDMS data is stored **in PostgreSQL** in the `eg_mdms_data` table. It is seeded at first startup from `db/full-dump.sql`.

- No git repository or remote fetch — it is fully self-contained in the dump.
- The `mdms-v2` service reads and serves this data via the `/mdms-v2/v1/_search` API.
- The `egov-mdms-service` container is an nginx reverse proxy in front of `mdms-v2` that also handles the legacy `/egov-mdms-service/*` path.

**To modify MDMS data**: POST to `http://localhost:28000/mdms-v2/v1/_create` (or `_update`) through Kong, or connect to Postgres at `localhost:25432` and edit `eg_mdms_data` directly.

### Boundary Data

Boundary hierarchy (states, districts, localities) is also stored in PostgreSQL and seeded from `db/full-dump.sql`.

Tables:
- `boundary` — boundary geometries (currently placeholder polygons)
- `boundary_hierarchy` — hierarchy definition (e.g. state → district → block)
- `boundary_relationship` — parent-child relationships between boundaries

The pre-seeded tenant is `mz` (Mozambique). The seed is sourced from the unified UAT pg_dump (`seed_data_dump_v2.0.sql`) and contains ~41K boundaries, ~32K boundary relationships, and ~6.3K MDMS rows across 44 modules (HCM-*, Workflow, ACCESSCONTROL-*, etc.). User/role/HRMS tables are not pre-seeded — Flyway creates empty schemas on first boot.

**To add real boundaries**: Use the boundary-service API at `http://localhost:28000/boundary-service/boundary/v1/_create`.

### Kafka Topics (Redpanda)

Redpanda is Kafka-compatible. HCM services publish events to topics; `egov-persister` consumes them and writes to Postgres. Topic names are defined in each service's environment variables (`SAVE_*_TOPIC`, `UPDATE_*_TOPIC`).

Browse topics at **http://localhost:28082** (Redpanda Console).

## What to Modify

### Change a service image version

Edit `docker-compose.yml`, find the service, update its `image:` tag:

```yaml
health-individual:
  image: egovio/health-individual:NEW_TAG   # ← change here
```

Then: `docker compose up -d --no-deps health-individual`

### Change the Kong API key

Edit `kong/kong.yml`, update the key under `consumers`:

```yaml
consumers:
- username: digit-dev
  keyauth_credentials:
  - key: YOUR_NEW_KEY_HERE
```

Then: `docker compose up -d --force-recreate kong`

### Add a new Kong route

Add a new entry under `services:` in `kong/kong.yml`:

```yaml
- name: my-new-service
  url: http://my-service:8080
  routes:
  - name: my-new-route
    paths: [/my-path]
    strip_path: false
```

### Add a new persister mapping

Create a new YAML file in `configs/persister/` following the same structure as existing files. The persister auto-loads all `.yml` files in that directory on startup.

Restart persister: `docker compose restart egov-persister`

### Add a service to Gatus monitoring

Edit `gatus/config.yaml`, add a new endpoint block:

```yaml
- name: My New Service
  group: HCM Services
  url: "http://my-service:8080/my-service/actuator/health"
  interval: 30s
  conditions:
    - "[STATUS] == 200"
```

Restart Gatus: `docker compose restart gatus`

### Change Postgres password

Set it before first startup via `.env`:

```
POSTGRES_PASSWORD=mysecretpassword
```

### Wipe and start fresh

```bash
docker compose down -v   # -v removes volumes (deletes all data)
docker compose up -d
```

## Services Reference

### Infrastructure

| Service | Image | Role |
|---|---|---|
| postgres | postgres:16 | Primary database |
| pgbouncer | edoburu/pgbouncer | Connection pooler (services connect through this) |
| redis | redis:7.2.4 | Session cache for user-service |
| redpanda | redpandadata/redpanda:v24.1.1 | Kafka-compatible message broker |
| minio | minio/minio | S3-compatible object storage (used by filestore) |

### Core DIGIT Services

| Service | Image | Role |
|---|---|---|
| mdms-v2 (mdms-backend) | egovio/mdms-v2 | Master data management API |
| egov-mdms-service | nginx:alpine | Proxy: routes legacy `/egov-mdms-service/*` → `/mdms-v2/*` |
| egov-enc-service | egovio/egov-enc-service | Encryption/decryption of PII fields |
| egov-idgen | egovio/egov-idgen | Unique ID generation with configurable formats |
| egov-user | egovio/egov-user | User management + OAuth2 token endpoint |
| egov-workflow-v2 | egovio/egov-workflow-v2 | Business process workflow engine |
| egov-localization | egovio/egov-localization | i18n string lookup |
| boundary-service | egovio/boundary-service | Geographic boundary hierarchy |
| egov-accesscontrol | egovio/egov-accesscontrol | Role-based access control |
| egov-persister | egovio/egov-persister | Async Kafka → Postgres writer |
| egov-filestore | egovio/egov-filestore | File upload/download (backed by MinIO) |
| egov-url-shortening | egovio/egov-url-shortening | Short URL generation |

### HCM Services

| Service | Image | Role |
|---|---|---|
| health-hrms | egovio/health-hrms | Health campaign staff registry |
| beneficiary-idgen | egovio/beneficiary-idgen | Offline-capable beneficiary ID generation |
| health-individual | egovio/health-individual | Individual beneficiary registry |
| household | egovio/household | Household registry |
| facility | egovio/facility | Health facility registry |
| product | egovio/product | Health product and variant catalog |
| health-project | egovio/health-project | Campaign project management |
| stock | egovio/stock | Stock/inventory tracking |
| referral-management | egovio/referral-management | Referral tracking between facilities |

### Observability

| Service | Role | URL |
|---|---|---|
| gatus | Service health dashboard | http://localhost:28889 |
| portainer | Container management UI | http://localhost:29009 |
| otel-collector | Collects traces from services | — |
| tempo | Stores and serves traces | — |
| grafana | Visualizes traces from Tempo | http://localhost:13000 |

### API Gateway

| Service | Role |
|---|---|
| kong | Routes all external requests, handles API key auth |

All API calls should go through Kong on port **28000**. Include the header `X-API-Key: digit-dev-api-key-change-me` on requests.

## Default Credentials

| Service | Username | Password / Key |
|---|---|---|
| PostgreSQL | egov | egov123 |
| MinIO | minioadmin | minioadmin |
| Kong API Key | — | `digit-dev-api-key-change-me` |
| Portainer | (set on first visit) | — |
| Grafana | (no login — anonymous admin) | — |
