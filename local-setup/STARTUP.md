# HCM Local Setup — Startup Guide

Run these steps after a fresh `git clone`. Everything boots from this directory.

## TL;DR — single command

After cloning, **one** bash command brings everything up (installs prerequisites, downloads the dump, starts the stack, waits for health, creates required Kafka topics, runs the 13-API smoke test):

```bash
cd local-setup && ./scripts/bootstrap.sh
```

Requires sudo only when a CLI prereq is missing (docker / python3 / pip / psql / jq / openssl). Idempotent — safe to re-run; previously-completed steps are skipped.

Read the rest of this doc if you want to understand each step or run them manually.

---

## 0. Prerequisites

### Hardware
- 12 GB RAM available to Docker
- 5 GB free disk

### Required CLI tools

| Tool | Why | Install (Ubuntu/Debian) | Install (macOS) |
|---|---|---|---|
| Docker Engine 24+ | run the stack | `curl -fsSL https://get.docker.com \| sh` | Docker Desktop |
| Docker Compose v2.20+ | orchestration | `sudo apt-get install -y docker-compose-plugin` | bundled with Docker Desktop |
| `curl` | smoke tests | usually present | usually present |
| `python3` + `pip` | runs the dump downloader `gdown` | `sudo apt-get install -y python3 python3-pip` | usually present |
| `psql` (PostgreSQL client) | optional, but verification queries below assume it | `sudo apt-get install -y postgresql-client` | `brew install libpq && brew link --force libpq` |
| `jq` | optional, pretty-print JSON | `sudo apt-get install -y jq` | `brew install jq` |

Verify:
```bash
docker --version
docker compose version
python3 --version && pip --version
psql --version    # only if you plan to query the DB from host
```

### Docker socket access
Either add yourself to the `docker` group (preferred):
```bash
sudo usermod -aG docker $USER
# log out / log back in (or: newgrp docker)
```
…or prefix every `docker` command with `sudo`. Without one, you'll see *"permission denied while trying to connect to the Docker daemon socket"*.

## 1. Clone & enter

```bash
git clone https://github.com/egovernments/health-campaign-services.git
cd health-campaign-services/local-setup
```

## 2. Fetch the DB seed dump (automatic)

`db/full-dump.sql` is **not in git** (size > 100 MB). It is hosted on Google Drive
(file id `1c3k_DBIP4s-Rhsdfu66s2TATGJCkTABz`, ~560 MB).

Paste the block below — it installs `gdown` if missing, downloads only when the file
isn't already present, and verifies the size.

```bash
mkdir -p db
if [ ! -s db/full-dump.sql ] || [ "$(stat -c%s db/full-dump.sql 2>/dev/null || stat -f%z db/full-dump.sql)" -lt 100000000 ]; then
  command -v gdown >/dev/null 2>&1 || pip install --user --quiet gdown
  export PATH="$HOME/.local/bin:$PATH"
  gdown --id 1c3k_DBIP4s-Rhsdfu66s2TATGJCkTABz -O db/full-dump.sql
fi
ls -lh db/full-dump.sql
```

Manual fallback: open https://drive.google.com/file/d/1c3k_DBIP4s-Rhsdfu66s2TATGJCkTABz/view,
click **Download anyway**, save as `local-setup/db/full-dump.sql`.

## 3. Bring the stack up

```bash
docker compose up -d
```

First boot pulls images and seeds the DB — typically **3–5 minutes**.

## 4. Verify

```bash
# Wait for everything to be healthy
docker compose ps

# Or open the dashboard
xdg-open http://localhost:28889    # Linux
# open  http://localhost:28889     # macOS
```

All rows green in Gatus → ready.

## 5. Quick smoke test

### One-line health probes

```bash
curl -s http://localhost:28000/mdms-v2/health
curl -s http://localhost:28000/boundary-service/actuator/health
```

Both should return `{"status":"UP"}` or `200`.

### Comprehensive API check

Saves a `RequestInfo` envelope as a shell var, then probes every major service. Every line should print `200`:

```bash
RI='{"RequestInfo":{"apiId":"hcm","ver":".01","ts":1,"msgId":"smoke","userInfo":{"id":1,"uuid":"dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3","userName":"SYSTEM","name":"System Admin","mobileNumber":"9999999999","active":true,"tenantId":"mz","type":"EMPLOYEE","roles":[{"code":"SUPERUSER","tenantId":"mz"},{"code":"NATIONAL_ADMIN","tenantId":"mz"}]}}'

p() { curl -s -o /dev/null -m 10 -X POST "http://localhost:28000$1" -H 'Content-Type: application/json' --data "$2" -w "%{http_code}  $1\n"; }

p /mdms-v2/v1/_search                                          "${RI},\"MdmsCriteria\":{\"tenantId\":\"mz\",\"moduleDetails\":[{\"moduleName\":\"common-masters\",\"masterDetails\":[{\"name\":\"IdFormat\"}]}]}}"
p /boundary-service/boundary-hierarchy-definition/_search      "${RI},\"BoundaryTypeHierarchySearchCriteria\":{\"tenantId\":\"mz\",\"limit\":5}}"
p /egov-idgen/id/_generate                                     "${RI},\"idRequests\":[{\"idName\":\"individual.id\",\"tenantId\":\"mz\",\"format\":\"IND-[SEQ_INDIVIDUAL_ID]\",\"count\":1}]}"
p '/user/_search'                                              "${RI},\"tenantId\":\"mz\",\"userType\":\"EMPLOYEE\",\"userName\":\"SYSTEM\"}"
p '/health-individual/v1/_search?tenantId=mz&limit=5&offset=0'          "${RI},\"Individual\":{}}"
p '/household/v1/_search?tenantId=mz&limit=5&offset=0'                  "${RI},\"Household\":{}}"
p '/facility/v1/_search?tenantId=mz&limit=5&offset=0'                   "${RI},\"Facility\":{}}"
p '/product/v1/_search?tenantId=mz&limit=5&offset=0'                    "${RI},\"Product\":{}}"
p '/health-project/v1/_search?tenantId=mz&limit=5&offset=0'             "${RI},\"Projects\":[{\"tenantId\":\"mz\",\"name\":\"%\"}]}"
p '/stock/v1/_search?tenantId=mz&limit=5&offset=0'                      "${RI},\"Stock\":{}}"
p '/health-hrms/employees/_search?tenantId=mz&limit=5&offset=0'         "${RI}"
p /plan-service/config/_search                                 "${RI},\"PlanConfigurationSearchCriteria\":{\"tenantId\":\"mz\"}}"
p /project-factory/v1/project-type/search                      "${RI},\"CampaignDetails\":{\"tenantId\":\"mz\",\"status\":[\"drafted\",\"created\",\"started\",\"completed\"],\"pagination\":{\"limit\":5}}}"
```

All 13 endpoints should return `200`. If any returns `400` with `msgId is required`, your `RI` variable lost its closing `}` — re-copy the line.

> **Note on `RequestInfo.userInfo`** — every HCM API validates that the caller has identity context. The seeded `SYSTEM` user (uuid `dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3`, tenant `mz`) is in the bootstrap SQL with `SUPERUSER` + 8 other roles; you can paste that `userInfo` block into any Postman request body to bypass OAuth login during testing.

## Useful URLs (browser)

| URL | Purpose |
|---|---|
| http://localhost:28889 | Gatus — service health |
| http://localhost:13000 | Grafana — traces (admin/admin) |
| http://localhost:29009 | Portainer — container UI |
| http://localhost:29001 | MinIO console |
| http://localhost:28002 | Kong Manager |

## Common commands

```bash
# Tail logs of one service
docker compose logs -f egov-user

# Restart one service
docker compose restart egov-user

# Stop everything (keeps data)
docker compose down

# Stop AND wipe DB / volumes (destructive)
docker compose down -v
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Containers say `healthy` but API hangs | `docker compose restart pgbouncer egov-user` — pgbouncer caches DNS failures |
| `eg_user` / `oauth_client_details` missing | DB dump did not load — re-fetch `db/full-dump.sql`, then `docker compose down -v && docker compose up -d` |
| Port already in use | Another local stack is running — `docker compose down` it first, or change the mapped port in `docker-compose.yml` |
| First boot stuck > 10 min | Check `docker compose logs egov-user` — usually slow JVM startup, not a real error |

## Next steps

- Detailed API examples and Kong routes: see `README.md`
- Adding a new service: copy an existing block in `docker-compose.yml`, add a Kong route in `kong/kong.yml`, add a health check in `gatus/config.yaml`
