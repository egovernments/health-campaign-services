# HCM Local Setup (DIGIT 2.9, for the DIGIT 3.0 migration)

Runs **Health Campaign Management (HCM)** on **DIGIT 2.9** on a Mac with Docker. This is the "before" stack for the DIGIT 3.0 migration: the behaviour that the migrated HCM is compared against.

The services are **built from the `master` of three repositories**, not pulled as published images, so you know exactly which code is running:

| Repository | Supplies |
|---|---|
| `health-campaign-services` (this one) | HCM services, and this `local-setup` folder |
| `Digit-Core` | DIGIT platform services (gateway, user, MDMS, idgen, encryption, access control, persister, boundary, filestore, localization) |
| `DIGIT-Frontend` | The two web UIs (Workbench and Payments) |

This folder started as a copy of `local-setup` from the `hcm-docker-compose-uat-env` branch. That branch is not needed any more. What changed here:

- 23 services run images built from local source (`scripts/build-from-source.sh`, `docker-compose.source.yml`).
- The API entry point is the **real DIGIT gateway** from `Digit-Core`, not Kong. It checks the login token and the user's roles on every call, as a deployed 2.9 does. Kong and its rules that returned made-up responses are gone.
- `health-service-request` (checklists) is added, built from `core-services/service-request` in this repository.
- Only the 30 containers needed for the Workbench flows start by default. 12 more HCM services are in an `optional` profile. The default stack uses about **5 GB of RAM**.
- MinIO is replaced by SeaweedFS (same S3 API, port and keys), because the MinIO images are no longer published.
- `egov-localization` runs on a 512 MB heap instead of 4 GB. A rule in `nginx/frontend-proxy.conf` answers the one request that needed the large heap. This is the only place the stack returns a made-up response.
- The monitoring tools (Gatus, Grafana, Tempo, OpenTelemetry collector, Portainer) are removed.
- The guide and scripts cover macOS only.

---

## 1. What you need

| | Required |
|---|---|
| **Docker Desktop** | Running, with **8 GB of memory** (Settings → Resources) |
| **Free disk space** | About 20 GB |
| **Git** | [git-scm.com/downloads](https://git-scm.com/downloads) or `brew install git` |
| **Java 17 and Maven 3.9** | To build the Java services. See below |

### Java and Maven

The repository root has a `.tool-versions` file (Java `corretto-17.0.21.0.11`, Maven `3.9.6`). With [asdf](https://asdf-vm.com):

```bash
cd health-campaign-services
asdf plugin add java && asdf plugin add maven
asdf install
```

The build script looks for Java and Maven in `~/.asdf/installs/`. If you do not use asdf, set `JAVA17_HOME` to a Java 17 home and make sure `mvn` is on your `PATH`.

---

## 2. Step-by-step setup

### Step 1 — Clone the three repositories side by side

The scripts find `Digit-Core` and `DIGIT-Frontend` by relative path, so all three must be in the same parent folder, with these exact folder names:

```bash
mkdir egov && cd egov
git clone -b HCMPRE-2431-migration-local-setup https://github.com/egovernments/health-campaign-services.git
git clone https://github.com/egovernments/Digit-Core.git
git clone https://github.com/egovernments/DIGIT-Frontend.git
```

```
egov/
├── health-campaign-services/    branch HCMPRE-2431-migration-local-setup (master + this folder)
├── Digit-Core/                  master
└── DIGIT-Frontend/              master
```

The stack was last built and checked with these commits:

| Repository | Commit | Date |
|---|---|---|
| `health-campaign-services` master | `c1dc73899d` | 2026-11-06 |
| `Digit-Core` master | `6077039d7e` | 2026-10-12 |
| `DIGIT-Frontend` master | `664fab0fe3` | 2026-10-16 |

If a newer `master` does not build or behaves differently, check out these commits.

### Step 2 — Enter this folder

```bash
cd health-campaign-services/local-setup
```

From here onwards, **every command assumes you are inside this folder.**

### Step 3 — Build the images from source

```bash
scripts/build-from-source.sh
```

This builds 23 images tagged `hcm-local/<service>:source`:

- **19 Java services** (9 from this repository, 10 from `Digit-Core`): Maven builds the jar on your Mac, and the jar replaces the one in the published image for that service. The runtime stays the same; only the application code changes.
- **2 Node services** (`project-factory`, `boundary-management`) and the **2 web UIs** from `DIGIT-Frontend`: built with their own Dockerfiles.

It prints `ok` or `FAIL` for each service. Logs are in `build-logs/`. To rebuild only some services, name them: `scripts/build-from-source.sh product household`.

This step must come before step 4. `.env` makes Compose read `docker-compose.source.yml`, which points 21 services at the `hcm-local/*` images (the gateway and `health-service-request` name theirs directly in `docker-compose.yml`), so the stack cannot start until they exist.

### Step 4 — Start the stack

```bash
./scripts/bootstrap.sh
```

The script starts the stack, waits for every container to be healthy, encrypts the seeded `SYSTEM` user so login works, creates Kafka topics, and runs an API smoke test through the gateway. It is safe to run again.

```
[1/6] Checking prerequisites
[2/6] Verifying DB seed dump is present
[3/6] Starting the stack
[4/6] Waiting for services to become healthy
[4b/6] Encrypting SYSTEM user PII so OAuth login works
[5/6] Ensuring project-factory consumer topics exist
[6/6] Running API smoke test
✅ Bootstrap complete.
```

> If it says "Permission denied", run `chmod +x ./scripts/bootstrap.sh` once and retry.

### Step 5 — Apply the migrations the seed data lacks

The seed dump (`db/full-dump.sql`) is older than the source code. This adds the tables and columns that current `master` expects:

```bash
scripts/apply-source-migrations.sh
docker compose restart project-factory egov-user
scripts/seed-missing-masters.sh
```

The last script adds master data the web UI needs and the seed dump lacks (the product types offered when adding a resource). It prints `400` for anything that already exists.

Run these after the first start, and again after every `docker compose down -v`. They are safe to run more than once.

### Step 6 — Check that everything is healthy

```bash
docker compose ps
```

All 30 containers should show `healthy`.

### Step 7 — Log in to the Admin (Workbench) UI

Open this URL in your web browser:

> 🔗 **[http://localhost:28080/workbench-ui/employee](http://localhost:28080/workbench-ui/employee)**

| Field | Value |
|---|---|
| **Username** | `SYSTEM` |
| **Password** | `eGov@123` |
| **City** | `mz` |

Click **Continue / Login**. You should land on a page with 12 cards (Manage Campaign, MDMS, Boundary, Localisation, etc.).

### Step 8 *(optional)* — Log in to the Payments UI as a field worker

Open this URL:

> 🔗 **[http://localhost:28080/payments-ui/employee](http://localhost:28080/payments-ui/employee)**

| Field | Value |
|---|---|
| **Username** | `EMP-DIST-002` |
| **Password** | `eGov@123` |
| **City** | `mz` |

This account has only the `DISTRIBUTOR` role (not admin) — useful for seeing how the UI behaves for a real field worker.

### Optional services

These 12 do not start by default: `census-service`, `egov-url-shortening`, `egov-workflow-v2`, `health-attendance`, `health-expense`, `health-expense-calculator`, `health-muster-roll`, `pgr-services`, `plan-service`, `referralmanagement`, `resource-generator`, `stock`.

To start them as well (give Docker more than 8 GB of memory first):

```bash
docker compose --profile optional up -d
```

They run the published images, not source builds.

---

## 3. Common errors and quick fixes

> If something didn't work, find your error message below and copy-paste the fix. Almost every problem is fixed by re-running `./scripts/bootstrap.sh`.

### "git: command not found"

You haven't installed Git yet. Get it from [git-scm.com/downloads](https://git-scm.com/downloads), or if you use Homebrew run:

```bash
brew install git
```

Then retry **step 1**.

### "docker: command not found"

You haven't installed Docker Desktop yet, or it isn't running.

1. Install it from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/).
2. Open the Docker Desktop application.
3. Wait until the whale icon at the bottom-left says **"Engine running"** (green).
4. Re-run `./scripts/bootstrap.sh`.

### "Cannot connect to the Docker daemon at unix:///var/run/docker.sock"

Docker is installed but not running. Open the Docker Desktop app and wait for the whale icon to turn green.

Then re-run `./scripts/bootstrap.sh`.

### "Permission denied" on `./scripts/bootstrap.sh`

The script lost its executable bit. Fix:

```bash
chmod +x ./scripts/bootstrap.sh
./scripts/bootstrap.sh
```

### Compose says an `hcm-local/...` image cannot be pulled

The source images are not built yet, or a build failed. Run `scripts/build-from-source.sh` and check `build-logs/<service>.log` for any service that printed `FAIL`.

### "Port already in use" during bootstrap

Another copy of the stack (or another program using the same port) is already running.

```bash
docker compose down
./scripts/bootstrap.sh
```

### Bootstrap says "some containers still need attention"

A few containers were slow to wake up. Just re-run the script — it only retries the ones that failed:

```bash
./scripts/bootstrap.sh
```

### Browser shows raw codes like `HCM_LOGIN_TITLE` instead of "Login"

The browser has cached a stale translation file. Open the browser's developer tools, then clear the cache for the page:

1. **Open DevTools:**
   - **Chrome, Edge, Firefox:** press **`Cmd + Option + I`**
   - **Safari:** enable the Develop menu first (`Safari → Settings → Advanced → Show Develop menu`), then press **`Cmd + Option + I`**
2. Click the **Console** tab at the top of the DevTools panel.
3. Paste this line and press Enter:
   ```js
   localStorage.clear(); sessionStorage.clear(); location.reload(true);
   ```

<p align="center">
  <img src="./docs/screenshots/devtools-console-paste.png" alt="DevTools Console with the localStorage.clear() command pasted in" width="800">
</p>

The page will reload with proper text labels.

> Yes, this line is needed — the UI saves translations in the browser, and a stale save can leave you stuck on `HCM_LOGIN_TITLE` codes. The line empties that cache and reloads the page.

### Login error: "Invalid login credentials"

The bootstrap didn't finish the encryption step. Just re-run:

```bash
./scripts/bootstrap.sh
```

### Workbench page is blank (no cards after login)

The UI seed didn't apply. Run:

```bash
docker exec -i hcm-postgres psql -U egov -d egov < db/02-hcm-ui-seed.sql
docker exec hcm-redis redis-cli FLUSHALL
```

Then refresh the browser (and clear the browser cache as in the previous fix if needed).

### You want a completely clean reset

This **wipes the database** and starts over (adds about 5 minutes):

```bash
docker compose down -v
./scripts/bootstrap.sh
scripts/apply-source-migrations.sh
docker compose restart project-factory egov-user
scripts/seed-missing-masters.sh
```

### Other symptoms

| Symptom | Fix |
|---|---|
| Containers say `healthy` but API hangs | `docker compose restart pgbouncer egov-user` — pgbouncer caches DNS failures, JVM caches negative DNS for enc-service |
| `eg_user` / `oauth_client_details` missing | DB dump did not load — re-fetch `db/full-dump.sql`, then `docker compose down -v && docker compose up -d` |
| UI labels show as raw codes (`TENANT_TENANTS_MZ`) | Localization cache miss. Flush redis as above and hard-reload after clearing site data. |
| `/access/v1/actions/mdms/_get` returns 400 `Missing property MdmsRes.ACCESSCONTROL-ACTIONS` | The 3 env vars on `egov-accesscontrol` (`MDMS_ACTIONSMODULE_NAME`, `MDMS_ACTIONMASTER_NAMES`, `MDMS_ACTIONS_PATH`) were stripped. Re-add per `docker-compose.yml`. |
| First boot stuck > 10 min | Check `docker compose logs egov-user` — usually slow JVM startup, not a real error |
| `BOUNDARY_SERVICE_SEARCH_ERROR — argument "content" is null` on HCM create payloads | Caller passed `address.locality.code` that doesn't exist in `public.boundary`. Local DB has `ADMIN_TC*` (TCHAD tree) and `MICROPLAN_MZ` — **not** `mz`. Either omit `address` or use a valid code (`ADMIN_TC`). |
| HRMS create fails with `ERR_HRMS_USER_CREATION_FAILED` | egov-user wasn't fully started when HRMS called it (slow JVM warm-up). Wait ~2 min after compose up and retry. If persistent, `docker compose restart health-hrms`. |
| HRMS create fails with `ERR_HRMS_INVALID_ROLE` | The role you passed isn't in `eg_role` for tenant `mz`. The dump seeds 18 (SUPERUSER + 17 HCM roles). For new custom roles, add to `eg_role` first. |
| Project create returns 200 but search returns nothing | Project field is missing one of `projectsubtype`/`department`/`description`/`referenceid`. Pre-fix, the dump had `NOT NULL` on those four. Current dump has them nullable. If you imported an older dump, run `ALTER TABLE health.project ALTER COLUMN description DROP NOT NULL;` (and the other three). |
| `/health-attendance/log/v1/_create` or muster-roll create returns `ResourceAccessError` | Docker-compose `*_SEARCH_ENDPOINT` env vars on attendance / muster-roll services are missing leading `/`. See "Known issues" below. |

---

## 4. URLs to bookmark

All links below open in your browser. Click them.

| What it is | URL | Login |
|---|---|---|
| **Admin (Workbench) UI** | 🔗 [http://localhost:28080/workbench-ui/employee](http://localhost:28080/workbench-ui/employee) | `SYSTEM` / `eGov@123` / city `mz` |
| **Payments UI** (grievances, HR) | 🔗 [http://localhost:28080/payments-ui/employee](http://localhost:28080/payments-ui/employee) | `SYSTEM` / `eGov@123` / city `mz` |
| **Redpanda HTTP proxy** (Kafka topics as JSON) | 🔗 [http://localhost:28082/topics](http://localhost:28082/topics) | no login |
| **SeaweedFS** (file storage) | 🔗 [http://localhost:29333](http://localhost:29333) | no login |
| PostgreSQL (DBeaver / psql) | `localhost:25432`, db `egov` | user `egov` / pass `egov123` |

---

## 5. Day-to-day commands

```bash
# Stop the stack (keeps your data)
docker compose down

# Start it again later
docker compose up -d

# See which containers are running
docker compose ps

# Watch logs from one service (Ctrl + C to exit)
docker compose logs -f egov-user
```

After running `docker compose up -d`, give it about 2 minutes, then run `docker compose ps` to confirm everything is healthy again.

---

## 6. Resource usage — measured

Measured with `docker stats --no-stream` on 2026-11-11, on the default stack (30 containers, no optional services):

| Metric | Value |
|---|---|
| RAM in use | **about 5.1 GB** total |
| Memory given to Docker Desktop | 8 GB |

To see live numbers on your own machine:

```bash
docker stats --no-stream
```

---

## 7. For developers — deeper reference

Reference material for people building on top of the stack.

### Ports

#### Infrastructure
| Port | Service | Purpose |
|---|---|---|
| 25432 | PostgreSQL | Direct DB access (psql, DBeaver) |
| 26379 | Redis | Cache inspection |
| 28000 | **DIGIT gateway** | All API calls. Checks login token and roles |
| 28080 | **Frontend proxy** | Single-origin entrypoint for browser — UIs + API. Lands on Workbench |
| 28082 | Redpanda HTTP proxy | Kafka topics as JSON |
| 29000 | SeaweedFS (S3 API) | Object storage (filestore) |
| 29333 | SeaweedFS master UI | Object storage status |

#### HCM service ports (direct — bypass the gateway)

Ports of services in the optional profile only answer after `docker compose --profile optional up -d`.

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

> API calls from clients and tests go through the **gateway on port 28000**. The per-service ports above skip the gateway, so they skip the login and role checks too.

### Directory structure

```
local-setup/
├── docker-compose.yml          # Main compose file — 45 services, 12 of them in the "optional" profile
├── docker-compose.source.yml   # Override: points 21 services at the hcm-local/*:source images
├── .env                        # Makes Compose read both files above
│
├── db/
│   ├── full-dump.sql           # Seed: schema + master data + PGR seed + MICROPLAN
│   │                           # boundary + DROPs + ALTER ROLE/DB search_path
│   │                           # + sequence resets. ~34 MB. Loaded on first init.
│   └── 02-hcm-ui-seed.sql      # Supplementary UI seed (~6 MB) — applied after
│                               # full-dump.sql. Adds 9 role grants to SYSTEM user,
│                               # 6,288 ACCESSCONTROL-ACTIONS-TEST.actions-test
│                               # MDMS rows (so workbench home renders cards),
│                               # and 19,551 localization rows.
│
├── nginx/                      # Frontend proxy + MDMS rewrite + UI globalConfigs
├── gateway/                    # DIGIT gateway: routes.properties (path -> service), gateway.env (open endpoints)
├── configs/persister/          # Persister YAML mappings per domain
│
├── scripts/
│   ├── build-from-source.sh        # Step 3: builds the hcm-local/*:source images
│   ├── bootstrap.sh                # Step 4: starts the stack and prepares it
│   ├── apply-source-migrations.sh  # Step 5: migrations the seed dump lacks
│   └── seed-missing-masters.sh     # Step 5: master data the seed dump lacks
│
├── build-logs/                 # Output of build-from-source.sh (not committed)
│
└── docs/screenshots/           # PNG snapshots referenced from this README
```

### Where data comes from

`db/full-dump.sql` + `db/02-hcm-ui-seed.sql` are mounted into Postgres' init dir and applied **in order** on first DB init. Self-contained — no remote fetch, no manual restore. Contains: schema, MDMS, ~607 TCHAD boundary rows, MICROPLAN boundary, 19 HCM roles, 1 SYSTEM admin, ~1000 sample users (only the ones under "Test users" can log in), 501 HRMS employees, 124 products, NULL-relaxation patch on `health.project`.

### Adding a service

Copy an existing block in `docker-compose.yml` and add a route in `gateway/routes.properties`.

### The gateway: login and permissions

Port 28000 is `Digit-Core/core-services/gateway`, built from source. For each call it does what a deployed DIGIT 2.9 does:

1. **Open endpoints** (login, localization search, `/egov-mdms-service/v1/_search`, and the rest of the list in `gateway/gateway.env`) pass without a login.
2. **Every other call needs `RequestInfo.authToken`.** Without one the answer is `401`. The gateway looks the token up in `egov-user` and writes the user into `RequestInfo.userInfo`. A `userInfo` sent by the caller is replaced.
3. **The user's roles must be allowed to call the endpoint.** The gateway asks `egov-accesscontrol`, which reads the role-to-endpoint mappings in the seed data (MDMS `ACCESSCONTROL-ROLEACTIONS.roleactions` and `ACCESSCONTROL-ACTIONS-TEST.actions-test`, tenant `mz`). If no role of the user is mapped to the endpoint, the answer is `401 You are not authorized to access this resource`.

The open and mixed-mode endpoint lists are copied from the HCM deployment configuration (`egovernments/health-campaign-devops`, `environments/health-demo.yaml`). Those deployments run the older `zuul` gateway with the same lists; `Digit-Core` master has replaced `zuul` with this gateway.

Differences from a deployed gateway:

- **No rate limiting.** Every local request comes from one address, so a per-address limit would only get in the way of tests.
- **MDMS is reachable as `/egov-mdms-service` only for permission purposes.** The seed has no role mappings for `/mdms-v2/...` search, so those calls are refused for every user. The UIs use `/egov-mdms-service`.
- **Calls between services do not pass through the gateway.** Each service is given the direct address of the services it calls.

No single user may call everything, so pick the login whose role fits the flow (see "Test users"). Examples:

| Endpoint | Logins that may call it |
|---|---|
| Product search, facility search | `SYSTEM`, `EMP-DIST-002`, `EMP-SYSADMIN-001`, `EMP-WHM-001` (not `EMP-REG-001`) |
| Campaign (project-factory) APIs, HRMS, user search, boundary | `SYSTEM` |
| Product and product variant create | `SYSTEM`, `EMP-SYSADMIN-001` |
| Product and product variant update | `EMP-SYSADMIN-001` |
| Facility create | `SYSTEM`, `EMP-WHM-001`, `EMP-SYSADMIN-001` |
| Facility update / delete / bulk create | `EMP-WHM-001`, `EMP-SYSADMIN-001` |
| Household APIs | `EMP-REG-001`, `EMP-DIST-002`, `EMP-SYSADMIN-001` |
| Checklist responses | `EMP-DIST-002` |

The gateway checks the role against the tenant in the request, so a call with no `tenantId` in the body or query is refused whatever the role.

### Known issues (not fixed in this stack)

Found during a full manual CRUD sweep — documented so demo flows steer around them.

| # | Symptom | Affected endpoints | Workaround |
|---|---|---|---|
| A | `MalformedURLException: 8080health-individual/...` | `/health-attendance/log/v1/_create`, `/health-muster-roll/v1/_create` | Prepend leading `/` to the `*_SEARCH_ENDPOINT` env vars in `docker-compose.yml`, then `docker compose up -d --no-deps <service>` |
| B | API returns 200 but row never lands in DB | `/health-project/user-action/v1/_create`, `/health-project/user-location/v1/_create` | Add `save-user-action-project-bulk-topic` to the persister consumer subscription |
| C | `Map.get(Object) is null` | `/health-expense/bill/v1/_create` | Seed `egf-master.BusinessService` MDMS rows for tenant `mz` |
| D | `JSONPath null` retried 10x | `/pgr/v2/request/_create` (when `address.geoLocation` is missing) | Always pass `address.geoLocation.latitude/longitude` |
| E | NPE (no message) | `/health-project/task/v1/_create` (when `address` is missing) | Always include `address` in Task payload |
| F | `BOUNDARY_SERVICE_SEARCH_ERROR` | Anything passing `address.locality.code` not in `public.boundary` | Omit `address` or use `ADMIN_TC` |
| G | OAuth fails for users copied from another environment (usernames starting `317304\|` or `688203\|`; the dump does not have those encryption keys) | All ~790 sample users in `mz` | Use the logins under "Test users" (encrypted with the key in the dump) |

### Test users

| Username | Password | Role | Notes |
|---|---|---|---|
| `SYSTEM` | `eGov@123` | SUPERUSER + 8 admin roles | Workbench admin |
| `EMP-DIST-002` | `eGov@123` | DISTRIBUTOR | Field worker; checklist flows |
| `EMP-SYSADMIN-001` | `eGov@123` | SYSTEM_ADMINISTRATOR | Product flows |
| `EMP-WHM-001` | `eGov@123` | WAREHOUSE_MANAGER | Facility flows |
| `EMP-REG-001` | `eGov@123` | REGISTRAR | Household flows |
| `EMP-HFW-001` | `eGov@123` | HEALTH_FACILITY_WORKER | Referral flows |
| `EMP-DSUP-001` | `eGov@123` | DISTRICT_SUPERVISOR | Checklist answers by a supervisor; creating and searching users (HRMS) |
| `EMP-COMM-001` | `eGov@123` | COMMUNITY_CREATOR + REGISTRAR | Community household (the gateway needs REGISTRAR, the household service needs COMMUNITY_CREATOR) |

All are tenant `mz`, type `EMPLOYEE`, and are inserted at the end of `db/full-dump.sql`, so they survive `down -v && up -d`.

### OAuth login (for API testing)

```bash
curl -s -X POST http://localhost:28000/user/oauth/token \
  -H 'Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=' \
  -d 'username=SYSTEM&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE'
```

Put the `access_token` from the answer into `RequestInfo.authToken` of every later call.

### Default credentials reference

| Service | Username | Password |
|---|---|---|
| PostgreSQL | `egov` | `egov123` |
| SeaweedFS S3 keys | `minioadmin` | `minioadmin` |

