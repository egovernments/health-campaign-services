# HCM Local Setup — Startup Guide

Run these steps after a fresh `git clone`. Everything boots from this directory.

The normal path is in **[README.md](./README.md)**: clone the three repositories, build the images from source, run `scripts/bootstrap.sh`, then apply the source migrations. Read this file if you want to run the steps by hand, or for the longer troubleshooting table at the end.

---

## 0. Prerequisites

- Docker Desktop with **8 GB of memory**. The default stack (29 containers) uses about 5 GB.
- About 20 GB of free disk.
- Java 17 and Maven 3.9 for the source build (see README, "Java and Maven").

| Tool | Why | Install |
|---|---|---|
| Docker Engine 24+ | run the stack | Docker Desktop |
| Docker Compose v2.20+ | orchestration | bundled with Docker Desktop |
| `curl` | smoke tests | usually present |
| `python3` | used by the build and bootstrap scripts | usually present |
| `psql` (PostgreSQL client) | optional, for querying the DB from the host | `brew install libpq && brew link --force libpq` |
| `jq` | optional, pretty-print JSON | `brew install jq` |

## 1. Clone, enter, build

`health-campaign-services`, `Digit-Core` and `DIGIT-Frontend` must be in the same parent folder (see README, step 1).

```bash
cd health-campaign-services/local-setup
scripts/build-from-source.sh
```

## 2. DB seed dumps are in the repo

Two SQL files in `db/` are committed and auto-loaded by Postgres on first init:

| File | Size | Purpose |
|---|---|---|
| `db/full-dump.sql` | ~34 MB | Core platform seed — schema, MDMS, boundary (TCHAD + MICROPLAN), ~133 k localization rows (locale `en_IN`), OAuth client, 1 SYSTEM admin + ~1000 sample users, 501 HRMS employees, 31 campaigns, role/access-control rows. Includes: **17 HCM roles seeded into `eg_role` for tenant mz** (DISTRIBUTOR, WAREHOUSE_MANAGER, etc.), **demo DISTRIBUTOR user** `EMP-DIST-002` (4 INSERTs at the end), **`health.project` NULL relaxation** on `projectsubtype`/`department`/`description`/`referenceid`, and a `DO $$ … $$` block that anchors campaign `startdate`/`enddate` to `NOW()` / `NOW()+90d` on first load. |
| `db/02-hcm-ui-seed.sql` | ~6 MB | UI-supporting seed — grants 9 functional admin roles to SYSTEM user, seeds 6,288 `ACCESSCONTROL-ACTIONS-TEST.actions-test` rows so the Workbench home renders cards, and 19,551 localization rows for `rainmaker-common` + `digit-ui` × 4 locales. Idempotent. |

Postgres applies them in name order (`01-`, `02-`). If anyone trims/regenerates either file, ensure each stays **< 100 MB** (GitHub's hard limit per file).

## 3. Bring the stack up

```bash
docker compose up -d
```

This starts the 29 default containers. First boot pulls the infrastructure images and seeds the DB. Subsequent boots are about 2 minutes (JVM warm-up). Add `--profile optional` to start the other 17 services as well.

Then apply the migrations that the seed dump lacks (after the first start and after every `down -v`):

```bash
scripts/apply-source-migrations.sh
docker compose restart project-factory egov-user
```

Starting the stack by hand skips one thing `scripts/bootstrap.sh` does: encrypting the seeded `SYSTEM` user. UI and OAuth login for `SYSTEM` only work after the bootstrap script has run once on this database.

## 4. Verify

```bash
docker compose ps
```

Every container shows `healthy` → ready.

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
p '/health-hrms/employees/_search?tenantId=mz&limit=5&offset=0'         "${RI}"
p /project-factory/v1/project-type/search                      "${RI},\"CampaignDetails\":{\"tenantId\":\"mz\",\"status\":[\"drafted\",\"created\",\"started\",\"completed\"],\"pagination\":{\"limit\":5}}}"
```

All 11 endpoints should return `200`. If any returns `400` with `msgId is required`, your `RI` variable lost its closing `}` — re-copy the line.

> **Note on `RequestInfo.userInfo`** — every HCM API validates that the caller has identity context. The seeded `SYSTEM` user (uuid `dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3`, tenant `mz`) is in the bootstrap SQL with `SUPERUSER` + 8 other roles; you can paste that `userInfo` block into any Postman request body to bypass OAuth login during testing.

## Useful URLs (browser)

| URL | Purpose |
|---|---|
| http://localhost:28080 | Frontend proxy (UI entrypoint — redirects to Workbench) |
| http://localhost:28080/workbench-ui/employee | Workbench UI |
| http://localhost:28080/payments-ui/employee | Payments UI |
| http://localhost:28889 | Gatus — service health (optional profile) |
| http://localhost:13000 | Grafana — traces (admin/admin) (optional profile) |
| http://localhost:29009 | Portainer — container UI (optional profile) |
| http://localhost:29333 | SeaweedFS master UI (file storage) |
| http://localhost:28002 | Kong Manager |

### UI login

| Field | Value |
|---|---|
| Username | `SYSTEM` |
| Password | `eGov@123` |
| City | `mz` |

**Secondary test account** (DISTRIBUTOR role, for non-superuser flows):

| Field | Value |
|---|---|
| Username | `EMP-DIST-002` |
| Password | `eGov@123` |
| City | `mz` |
| Role | `DISTRIBUTOR` (jurisdiction: TCHAD / ADMIN_TC) |

If the UI looks blank or shows raw codes (`HCM_…`) after seed changes, the SPA is using cached localStorage. In DevTools console:

```js
localStorage.clear(); sessionStorage.clear(); location.reload(true);
```

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
| Containers say `healthy` but API hangs | `docker compose restart pgbouncer egov-user` — pgbouncer caches DNS failures, JVM caches negative DNS for enc-service |
| `eg_user` / `oauth_client_details` missing | DB dump did not load — re-fetch `db/full-dump.sql`, then `docker compose down -v && docker compose up -d` |
| Workbench home blank after login | `02-hcm-ui-seed.sql` didn't apply, or browser cache is stale. Apply seed manually: `cat db/02-hcm-ui-seed.sql \| docker exec -i hcm-postgres psql -U egov -d egov`, then flush redis (`docker exec hcm-redis redis-cli FLUSHALL`) and clear browser localStorage. |
| UI labels show as raw codes (`TENANT_TENANTS_MZ`) | Localization cache miss. Flush redis as above and hard-reload after clearing site data. |
| `/access/v1/actions/mdms/_get` returns 400 `Missing property MdmsRes.ACCESSCONTROL-ACTIONS` | The 3 env vars on `egov-accesscontrol` (`MDMS_ACTIONSMODULE_NAME`, `MDMS_ACTIONMASTER_NAMES`, `MDMS_ACTIONS_PATH`) were stripped. Re-add per `docker-compose.yml`. |
| Port already in use | Another local stack is running — `docker compose down` it first, or change the mapped port in `docker-compose.yml` |
| First boot stuck > 10 min | Check `docker compose logs egov-user` — usually slow JVM startup, not a real error |
| `BOUNDARY_SERVICE_SEARCH_ERROR — argument "content" is null` on HCM create payloads | Caller passed `address.locality.code` that doesn't exist in `public.boundary`. Local DB has `ADMIN_TC*` (TCHAD tree) and `MICROPLAN_MZ` — **not** `mz`. Either omit `address` or use a valid code (`ADMIN_TC`). |
| HRMS create fails with `ERR_HRMS_USER_CREATION_FAILED` | egov-user wasn't fully started when HRMS called it (slow JVM warm-up). Wait ~2 min after compose up and retry. If persistent, `docker compose restart kong` to clear DNS cache. |
| HRMS create fails with `ERR_HRMS_INVALID_ROLE` | The role you passed isn't in `eg_role` for tenant `mz`. The dump seeds 18 (SUPERUSER + 17 HCM roles). For new custom roles, add to `eg_role` first. |
| Project create returns 200 but search returns nothing | Project field is missing one of `projectsubtype`/`department`/`description`/`referenceid`. Pre-fix, the dump had `NOT NULL` on those four. Current dump has them nullable. If you imported an older dump, run `ALTER TABLE health.project ALTER COLUMN description DROP NOT NULL;` (and the other three). |
| `/health-attendance/log/v1/_create` or muster-roll create returns `ResourceAccessError` | Docker-compose `*_SEARCH_ENDPOINT` env vars on attendance / muster-roll services are missing leading `/`. See README "Known Issues" section. |

## Next steps

- Detailed API examples and Kong routes: see `README.md`
- Adding a new service: copy an existing block in `docker-compose.yml`, add a Kong route in `kong/kong.yml`, add a health check in `gatus/config.yaml`
